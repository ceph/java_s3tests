import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartCopyRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyResponse;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
// S3 v2 Transfer Manager
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedCopy;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.DirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

public class S3 {

    final static Logger logger = LogManager.getRootLogger();

    private static S3 instance = null;

    public static S3 getInstance() {
        if (instance == null) {
            instance = new S3();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Helper: unwrap CompletionException layers to find the real cause.
    // Stops as soon as it hits something that is NOT a CompletionException or
    // a plain RuntimeException wrapper with a cause, so the actual S3Exception
    // (or IOException, etc.) surfaces directly.
    // -------------------------------------------------------------------------

    private Throwable unwrapCompletionException(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null
                && (cause instanceof CompletionException
                        || cause instanceof RuntimeException)) {
            cause = cause.getCause();
        }
        return cause;
    }

    private Properties loadProperties() {
        Properties prop = new Properties();
        try {
            InputStream input = new FileInputStream("config.properties");
            try {
                prop.load(input);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return prop;
    }

    private Properties prop = loadProperties();

    public S3Client getS3V2Client(Boolean isV4SignerType) {
        String accessKey = prop.getProperty("access_key").trim();
        String secretKey = prop.getProperty("access_secret").trim();
        String endpoint = prop.getProperty("endpoint").trim();
        String region = prop.getProperty("region", "us-east-1");

        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofMillis(900 * 1000))
                .socketTimeout(Duration.ofMillis(900 * 1000))
                .connectionMaxIdleTime(Duration.ofMillis(1000));

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(900 * 1000))
                .apiCallAttemptTimeout(Duration.ofMillis(60 * 1000))
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(Integer.MAX_VALUE)
                        .build())
                .build();

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .endpointOverride(java.net.URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(overrideConfig)
                .serviceConfiguration(s3Config)
                .build();
    }

    public String getPrefix() {
        String prefix;
        if (prop.getProperty("bucket_prefix") != null) {
            prefix = prop.getProperty("bucket_prefix");
        } else {
            prefix = "test-";
        }
        return prefix;
    }

    public String getBucketName(String prefix) {
        Random rand = new Random();
        int num = rand.nextInt(50);
        String randomStr = UUID.randomUUID().toString();
        return prefix + randomStr + num;
    }

    public String getBucketName() {
        String prefix = getPrefix();
        Random rand = new Random();
        int num = rand.nextInt(50);
        String randomStr = UUID.randomUUID().toString();
        return prefix + randomStr + num;
    }

    public String repeat(String str, int count) {
        if (count <= 0) {
            return "";
        }
        return new String(new char[count]).replace("\0", str);
    }

    public Boolean isEPSecure() {
        return Boolean.parseBoolean(prop.getProperty("is_secure"));
    }

    public int teradownRetriesV2 = 0;

    public void tearDownV2(S3Client s3Client) {
        if (teradownRetriesV2 > 0) {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            logger.info("TEARDOWN V2");
            ListBucketsResponse bucketsResponse = s3Client.listBuckets();
            List<Bucket> buckets = bucketsResponse.buckets();
            logger.info(String.format("Buckets list size: %d ", buckets.size()));
            String prefix = getPrefix();

            for (Bucket b : buckets) {
                String bucket_name = b.name();
                if (bucket_name.startsWith(prefix)) {
                    try {
                        ListObjectVersionsResponse versionListing = s3Client.listObjectVersions(
                                ListObjectVersionsRequest.builder().bucket(bucket_name).build());
                        while (true) {
                            for (ObjectVersion vs : versionListing.versions()) {
                                logger.info(String.format("Deleting version: %s / %s / %s",
                                        bucket_name, vs.key(), vs.versionId()));
                                try {
                                    s3Client.deleteObject(DeleteObjectRequest.builder()
                                            .bucket(bucket_name).key(vs.key()).versionId(vs.versionId()).build());
                                } catch (S3Exception e) {
                                    if (e.statusCode() != 404) {
                                        logger.warn(String.format("deleteVersion failed: %s/%s@%s | code=%s http=%d",
                                                bucket_name, vs.key(), vs.versionId(),
                                                e.awsErrorDetails().errorCode(), e.statusCode()));
                                    }
                                }
                            }
                            for (DeleteMarkerEntry dm : versionListing.deleteMarkers()) {
                                logger.info(String.format("Deleting delete-marker: %s / %s / %s",
                                        bucket_name, dm.key(), dm.versionId()));
                                try {
                                    s3Client.deleteObject(DeleteObjectRequest.builder()
                                            .bucket(bucket_name).key(dm.key()).versionId(dm.versionId()).build());
                                } catch (S3Exception e) {
                                    if (e.statusCode() != 404) {
                                        logger.warn(String.format("deleteMarker failed: %s/%s@%s | code=%s http=%d",
                                                bucket_name, dm.key(), dm.versionId(),
                                                e.awsErrorDetails().errorCode(), e.statusCode()));
                                    }
                                }
                            }
                            if (versionListing.isTruncated()) {
                                versionListing = s3Client.listObjectVersions(
                                        ListObjectVersionsRequest.builder()
                                                .bucket(bucket_name)
                                                .keyMarker(versionListing.nextKeyMarker())
                                                .versionIdMarker(versionListing.nextVersionIdMarker())
                                                .build());
                            } else {
                                break;
                            }
                        }
                    } catch (S3Exception e) {
                        logger.warn(String.format("listVersions failed: %s | code=%s http=%d",
                                bucket_name, e.awsErrorDetails().errorCode(), e.statusCode()));
                    }
                    try {
                        ListObjectsV2Response objectListing = s3Client.listObjectsV2(
                                ListObjectsV2Request.builder().bucket(bucket_name).build());
                        while (true) {
                            for (S3Object obj : objectListing.contents()) {
                                logger.info(String.format("Deleting object: %s / %s", bucket_name, obj.key()));
                                try {
                                    s3Client.deleteObject(DeleteObjectRequest.builder()
                                            .bucket(bucket_name).key(obj.key()).build());
                                } catch (S3Exception e) {
                                    if (e.statusCode() != 404) {
                                        logger.warn(String.format("deleteObject failed: %s/%s | code=%s http=%d",
                                                bucket_name, obj.key(),
                                                e.awsErrorDetails().errorCode(), e.statusCode()));
                                    }
                                }
                            }
                            if (objectListing.isTruncated()) {
                                objectListing = s3Client.listObjectsV2(
                                        ListObjectsV2Request.builder()
                                                .bucket(bucket_name)
                                                .continuationToken(objectListing.nextContinuationToken())
                                                .build());
                            } else {
                                break;
                            }
                        }
                    } catch (S3Exception e) {
                        logger.warn(String.format("listObjects failed: %s | code=%s http=%d",
                                bucket_name, e.awsErrorDetails().errorCode(), e.statusCode()));
                    }

                    try {
                        s3Client.deleteBucket(
                                DeleteBucketRequest.builder()
                                        .bucket(bucket_name).build());
                        logger.info(String.format("Deleted bucket: %s", bucket_name));
                    } catch (S3Exception e) {
                        logger.warn(String.format("deleteBucket failed: %s | code=%s http=%d",
                                bucket_name, e.awsErrorDetails().errorCode(), e.statusCode()));
                    }
                }
            }
        } catch (S3Exception e) {
            logger.error(String.format("tearDownV2 listBuckets failed: code=%s http=%d requestId=%s",
                    e.awsErrorDetails().errorCode(), e.statusCode(), e.requestId()));
        } catch (Exception e) {
            logger.warn("tearDownV2 unexpected error, retry " + teradownRetriesV2 + ": " + e.getMessage());
            if (teradownRetriesV2 < 10) {
                ++teradownRetriesV2;
                tearDownV2(s3Client);
            }
        }
    }

    public String[] EncryptionSseCustomerWriteV2(S3Client s3Client, int file_size) {
        String prefix = getPrefix();
        String bucket_name = getBucketName(prefix);
        String key = "key1";
        String data = repeat("testcontent", file_size);

        s3Client.createBucket(b -> b.bucket(bucket_name));

        String sseCustomerKey = "pO3upElrwuEXSoFwCfnZPdSsmt/xWeFa0N9KgDijwVs=";
        String sseCustomerKeyMd5 = "DWygnHRtgiJ77HCm+1rvHw==";
        String sseCustomerAlgorithm = "AES256";

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket_name).key(key).contentType("text/plain")
                .sseCustomerAlgorithm(sseCustomerAlgorithm)
                .sseCustomerKey(sseCustomerKey)
                .sseCustomerKeyMD5(sseCustomerKeyMd5)
                .build();
        s3Client.putObject(putRequest, RequestBody.fromString(data));

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket_name).key(key)
                .sseCustomerAlgorithm(sseCustomerAlgorithm)
                .sseCustomerKey(sseCustomerKey)
                .sseCustomerKeyMD5(sseCustomerKeyMd5)
                .build();

        ResponseInputStream<GetObjectResponse> responseStream = s3Client.getObject(getRequest);
        String rdata = null;
        try {
            rdata = new String(responseStream.readAllBytes());
        } catch (IOException e) {
            logger.error(String.format("EncryptionSseCustomerWriteV2: failed to read object %s/%s: %s",
                    bucket_name, key, e.getMessage()));
        }

        return new String[] { data, rdata };
    }

    public String createKeysV2(S3Client s3Client, String[] keys) {
        String prefix = prop.getProperty("bucket_prefix");
        String bucket_name = getBucketName(prefix);
        s3Client.createBucket(b -> b.bucket(bucket_name));
        for (String k : keys) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket_name).key(k).build(),
                    RequestBody.fromString(k));
        }
        return bucket_name;
    }

    public CompleteMultipartUploadRequest multipartUploadLLAPIV2(
            S3Client s3Client, String bucket, String key, long size, String filePath) {

        CreateMultipartUploadResponse initResponse = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(bucket).key(key).build());
        String uploadId = initResponse.uploadId();

        File file = new File(filePath);
        long contentLength = file.length();
        long partSize = size;
        long filePosition = 0;

        List<CompletedPart> completedParts = new ArrayList<>();

        for (int i = 1; filePosition < contentLength; i++) {
            partSize = Math.min(partSize, (contentLength - filePosition));
            try {
                FileInputStream fis = new FileInputStream(file);
                fis.skip(filePosition);
                byte[] partBytes = new byte[(int) partSize];
                fis.read(partBytes);
                fis.close();

                UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                        UploadPartRequest.builder()
                                .bucket(bucket).key(key).uploadId(uploadId)
                                .partNumber(i).contentLength(partSize).build(),
                        RequestBody.fromBytes(partBytes));

                completedParts.add(CompletedPart.builder()
                        .partNumber(i).eTag(uploadPartResponse.eTag()).build());
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("multipartUploadLLAPIV2: failed to read part %d of '%s' "
                                + "at offset %d (partSize=%d)", i, filePath, filePosition, partSize),
                        e);
            } catch (S3Exception e) {
                logger.error(String.format(
                        "[S3 ERROR] multipartUploadLLAPIV2 uploadPart: bucket=%s key=%s part=%d "
                                + "| code=%s http=%d requestId=%s",
                        bucket, key, i,
                        e.awsErrorDetails().errorCode(), e.statusCode(), e.requestId()));
                throw e;
            }
            filePosition += partSize;
        }

        return CompleteMultipartUploadRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();
    }

    public CompleteMultipartUploadRequest multipartCopyLLAPIV2(
            S3Client s3Client, String dstbkt, String dstkey, String srcbkt, String srckey, long size) {

        CreateMultipartUploadResponse initResult = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(dstbkt).key(dstkey).build());
        String uploadId = initResult.uploadId();

        HeadObjectResponse metadataResult = s3Client.headObject(
                HeadObjectRequest.builder().bucket(srcbkt).key(srckey).build());
        long objectSize = metadataResult.contentLength();
        long partSize = size;
        long bytePosition = 0;
        int partNum = 1;

        List<CompletedPart> completedParts = new ArrayList<>();
        while (bytePosition < objectSize) {
            long lastByte = Math.min(bytePosition + partSize - 1, objectSize - 1);
            String copySourceRange = "bytes=" + bytePosition + "-" + lastByte;

            try {
                UploadPartCopyResponse res = s3Client.uploadPartCopy(
                        UploadPartCopyRequest.builder()
                                .destinationBucket(dstbkt).destinationKey(dstkey)
                                .sourceBucket(srcbkt).sourceKey(srckey)
                                .uploadId(uploadId).copySourceRange(copySourceRange)
                                .partNumber(partNum).build());
                completedParts.add(CompletedPart.builder()
                        .partNumber(partNum).eTag(res.copyPartResult().eTag()).build());
            } catch (S3Exception e) {
                logger.error(String.format(
                        "[S3 ERROR] multipartCopyLLAPIV2 copyPart: src=%s/%s dst=%s/%s part=%d range=%s "
                                + "| code=%s http=%d requestId=%s",
                        srcbkt, srckey, dstbkt, dstkey, partNum, copySourceRange,
                        e.awsErrorDetails().errorCode(), e.statusCode(), e.requestId()));
                throw e;
            }

            partNum++;
            bytePosition += partSize;
        }

        return CompleteMultipartUploadRequest.builder()
                .bucket(dstbkt).key(dstkey).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();
    }

    public S3AsyncClient getS3V2AsyncClient() {
        String accessKey = prop.getProperty("access_key");
        String secretKey = prop.getProperty("access_secret");
        String endpoint = prop.getProperty("endpoint");
        String region = prop.getProperty("region", "us-east-1");

        return S3AsyncClient.builder()
                .endpointOverride(java.net.URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .multipartEnabled(true)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private S3TransferManager buildTransferManagerV2(S3AsyncClient s3AsyncClient) {
        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();
    }

    // -------------------------------------------------------------------------
    // V2 Transfer Manager HLAPI — all methods unwrap CompletionException and
    // rethrow the raw S3Exception so test code can do:
    //
    // S3Exception ex = assertThrows(S3Exception.class, () ->
    // utils.someMethod(...));
    // assertEquals(404, ex.statusCode());
    // assertEquals("NoSuchBucket", ex.awsErrorDetails().errorCode());
    // -------------------------------------------------------------------------

    public CompletedCopy multipartCopyHLAPIV2(S3AsyncClient s3AsyncClient,
            String dstbkt, String dstkey, String srcbkt, String srckey) {

        S3TransferManager tm = buildTransferManagerV2(s3AsyncClient);
        try {
            CopyObjectRequest copyReq = CopyObjectRequest.builder()
                    .sourceBucket(srcbkt).sourceKey(srckey)
                    .destinationBucket(dstbkt).destinationKey(dstkey)
                    .build();
            software.amazon.awssdk.transfer.s3.model.Copy copy = tm.copy(c -> c.copyObjectRequest(copyReq));
            return copy.completionFuture().join();

        } catch (CompletionException e) {
            Throwable root = unwrapCompletionException(e);
            if (root instanceof S3Exception) {
                S3Exception s3e = (S3Exception) root;
                logger.error(String.format(
                        "[S3 ERROR] multipartCopyHLAPIV2: src=%s/%s dst=%s/%s | code=%s http=%d requestId=%s",
                        srcbkt, srckey, dstbkt, dstkey,
                        s3e.awsErrorDetails().errorCode(), s3e.statusCode(), s3e.requestId()));
                throw s3e; // raw S3Exception — test catches this directly
            }
            throw new RuntimeException(
                    String.format("multipartCopyHLAPIV2 failed [src=%s/%s dst=%s/%s]: %s",
                            srcbkt, srckey, dstbkt, dstkey, root.getMessage()),
                    root);
        } finally {
            tm.close();
        }
    }

    public CompletedFileDownload downloadHLAPIV2(S3AsyncClient s3AsyncClient,
            String bucket, String key, File file) {

        S3TransferManager tm = buildTransferManagerV2(s3AsyncClient);
        try {
            DownloadFileRequest downloadReq = DownloadFileRequest.builder()
                    .getObjectRequest(b -> b.bucket(bucket).key(key))
                    .destination(file.toPath())
                    .build();
            FileDownload download = tm.downloadFile(downloadReq);
            return download.completionFuture().join();

        } catch (CompletionException e) {
            Throwable root = unwrapCompletionException(e);
            if (root instanceof S3Exception) {
                S3Exception s3e = (S3Exception) root;
                logger.error(String.format(
                        "[S3 ERROR] downloadHLAPIV2: bucket=%s key=%s | code=%s http=%d requestId=%s",
                        bucket, key, s3e.awsErrorDetails().errorCode(), s3e.statusCode(), s3e.requestId()));
                throw s3e;
            }
            throw new RuntimeException(
                    String.format("downloadHLAPIV2 failed [bucket=%s key=%s]: %s",
                            bucket, key, root.getMessage()),
                    root);
        } finally {
            tm.close();
        }
    }

    public CompletedDirectoryDownload multipartDownloadHLAPIV2(S3AsyncClient s3AsyncClient,
            String bucket, String prefix, File dstDir) {

        S3TransferManager tm = buildTransferManagerV2(s3AsyncClient);
        try {
            DownloadDirectoryRequest downloadDirReq = DownloadDirectoryRequest.builder()
                    .bucket(bucket)
                    .listObjectsV2RequestTransformer(l -> l.prefix(prefix))
                    .destination(dstDir.toPath())
                    .build();
            DirectoryDownload dirDownload = tm.downloadDirectory(downloadDirReq);
            return dirDownload.completionFuture().join();

        } catch (CompletionException e) {
            Throwable root = unwrapCompletionException(e);
            if (root instanceof S3Exception) {
                S3Exception s3e = (S3Exception) root;
                logger.error(String.format(
                        "[S3 ERROR] multipartDownloadHLAPIV2: bucket=%s prefix=%s | code=%s http=%d requestId=%s",
                        bucket, prefix, s3e.awsErrorDetails().errorCode(), s3e.statusCode(), s3e.requestId()));
                throw s3e;
            }
            throw new RuntimeException(
                    String.format("multipartDownloadHLAPIV2 failed [bucket=%s prefix=%s]: %s",
                            bucket, prefix, root.getMessage()),
                    root);
        } finally {
            tm.close();
        }
    }

    public CompletedFileUpload UploadFileHLAPIV2(S3AsyncClient s3AsyncClient,
            String bucket, String key, String filePath) {

        S3TransferManager tm = buildTransferManagerV2(s3AsyncClient);
        try {
            UploadFileRequest uploadReq = UploadFileRequest.builder()
                    .putObjectRequest(b -> b.bucket(bucket).key(key))
                    .source(Paths.get(filePath))
                    .build();
            FileUpload upload = tm.uploadFile(uploadReq);
            return upload.completionFuture().join();

        } catch (CompletionException e) {
            Throwable root = unwrapCompletionException(e);
            if (root instanceof S3Exception) {
                S3Exception s3e = (S3Exception) root;
                logger.error(String.format(
                        "[S3 ERROR] UploadFileHLAPIV2: bucket=%s key=%s file=%s | code=%s http=%d requestId=%s",
                        bucket, key, filePath,
                        s3e.awsErrorDetails().errorCode(), s3e.statusCode(), s3e.requestId()));
                throw s3e;
            }
            throw new RuntimeException(
                    String.format("UploadFileHLAPIV2 failed [bucket=%s key=%s file=%s]: %s",
                            bucket, key, filePath, root.getMessage()),
                    root);
        } finally {
            tm.close();
        }
    }

    public CompletedDirectoryUpload multipartUploadHLAPIV2(S3AsyncClient s3AsyncClient,
            String bucket, String s3target, String directory) {

        S3TransferManager tm = buildTransferManagerV2(s3AsyncClient);
        try {
            UploadDirectoryRequest uploadDirReq = UploadDirectoryRequest.builder()
                    .bucket(bucket)
                    .s3Prefix(s3target)
                    .source(Paths.get(directory))
                    .build();
            DirectoryUpload dirUpload = tm.uploadDirectory(uploadDirReq);
            return dirUpload.completionFuture().join();

        } catch (CompletionException e) {
            Throwable root = unwrapCompletionException(e);
            if (root instanceof S3Exception) {
                S3Exception s3e = (S3Exception) root;
                logger.error(String.format(
                        "[S3 ERROR] multipartUploadHLAPIV2: bucket=%s target=%s dir=%s | code=%s http=%d requestId=%s",
                        bucket, s3target, directory,
                        s3e.awsErrorDetails().errorCode(), s3e.statusCode(), s3e.requestId()));
                throw s3e;
            }
            throw new RuntimeException(
                    String.format("multipartUploadHLAPIV2 failed [bucket=%s target=%s dir=%s]: %s",
                            bucket, s3target, directory, root.getMessage()),
                    root);
        } finally {
            tm.close();
        }
    }

    public void createFile(String fname, long size) {
        Random rand = new Random();
        try {
            File f = new File(fname);
            if (f.exists() && !f.isDirectory()) {
                f.delete();
            }
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fname))) {
                long remaining = size;
                byte[] buffer = new byte[1024 * 1024]; // 1 MB buffer
                while (remaining > 0) {
                    int toWrite = (int) Math.min(remaining, buffer.length);
                    rand.nextBytes(buffer);
                    bos.write(buffer, 0, toWrite);
                    remaining -= toWrite;
                }
            }
        } catch (IOException e) {
            logger.error("createFile failed [fname=" + fname + " size=" + size + "]: " + e.getMessage(), e);
        }
    }
}