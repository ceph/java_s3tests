import java.io.File;
import java.util.ArrayList;
import java.util.List;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedCopy;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.ResumableFileDownload;

public class AWS4Test {

    private static S3 utils = S3.getInstance();
    boolean useV4Signature = true;
    S3Client s3Client = utils.getS3V2Client(useV4Signature);
    S3AsyncClient s3AsyncClient = utils.getS3V2AsyncClient();
    String prefix = utils.getPrefix();

    @BeforeClass
    public void generateFiles() {
        new java.io.File("./downloads").mkdirs();
        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        filePath = "./data/file.txt";
        utils.createFile(filePath, 256 * 1024);
    }

    @AfterClass
    public void tearDownAfterClass() throws Exception {
        S3.logger.debug("TeardownAfterClass");
        utils.teradownRetriesV2 = 0;
        utils.tearDownV2(s3Client);
        s3AsyncClient.close();
    }

    @AfterMethod
    public void tearDownAfterMethod() throws Exception {
        S3.logger.debug("TeardownAfterMethod");
        utils.teradownRetriesV2 = 0;
        utils.tearDownV2(s3Client);

    }

    @BeforeMethod
    public void setUp() throws Exception {
        S3.logger.debug("TeardownBeforeMethod");
        utils.teradownRetriesV2 = 0;
        utils.tearDownV2(s3Client);
    }

    // @Test(description = "object create w/bad X-Amz-Date, fails!")
    // public void testObjectCreateBadamzDateAfterEndAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "99990707T215304Z";
    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // try {
    // PutObjectRequest putRequest = PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("X-Amz-Date", value))
    // .build();

    // s3Client.putObject(putRequest, RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 RequestTimeTooSkewed");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "RequestTimeTooSkewed");
    // }
    // }

    // @Test(description = "object create w/Date after, fails!")
    // public void testObjectCreateBadDateAfterEndAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "Tue, 07 Jul 9999 21:53:04 GMT";
    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // try {
    // PutObjectRequest putRequest = PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("Date", value))
    // .build();

    // s3Client.putObject(putRequest, RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 RequestTimeTooSkewed");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "RequestTimeTooSkewed");
    // }
    // }

    // @Test(description = "object create w/Date before, fails!")
    // public void testObjectCreateBadamzDateBeforeEpochAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "9500707T215304Z";

    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // ObjectMetadata metadata = new ObjectMetadata();
    // metadata.setContentLength(content.length());
    // metadata.setHeader("X-Amz-Date", value);

    // try {
    // PutObjectRequest putRequest = PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("X-Amz-Date", value))
    // .build();

    // s3Client.putObject(putRequest, RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "SignatureDoesNotMatch");
    // }
    // }

    @Test(description = "object create w/Date before epoch, fails!")
    public void testObjectCreateBadDateBeforeEpochAWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "Tue, 07 Jul 1950 21:53:04 GMT";

        s3Client.createBucket(b -> b.bucket(bucket_name));
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket_name)
                .key(key)
                .contentLength((long) content.length())
                .overrideConfiguration(o -> o.putHeader("Date", value))
                .build();

        s3Client.putObject(putRequest, RequestBody.fromString(content));
    }

    // @Test(description = "object create w/X-Amz-Date after today, fails!")
    // public void testObjectCreateBadAmzDateAfterTodayAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "20300707T215304Z";
    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("X-Amz-Date", value))
    // .build(), RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 RequestTimeTooSkewed");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "RequestTimeTooSkewed");
    // }
    // }

    @Test(description = "object create w/Date before today, suceeds!")
    public void testObjectCreateBadDateBeforeToday4AWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "Tue, 07 Jul 2010 21:53:04 GMT";

        s3Client.createBucket(b -> b.bucket(bucket_name));

        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket_name)
                .key(key)
                .contentLength((long) content.length())
                .overrideConfiguration(o -> o.putHeader("Date", value))
                .build(), RequestBody.fromString(content));

    }

    // @Test(description = "object create w/no X-Amz-Date, fails!")
    // public void testObjectCreateBadAmzDateNoneAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "";

    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("X-Amz-Date", value))
    // .build(), RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 RequestTimeTooSkewed");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "RequestTimeTooSkewed");
    // }
    // }

    @Test(description = "object create w/no Date, suceeds!")
    public void testObjectCreateBadDateNoneAWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "";

        s3Client.createBucket(b -> b.bucket(bucket_name));
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket_name)
                .key(key)
                .contentLength((long) content.length())
                .overrideConfiguration(o -> o.putHeader("Date", value))
                .build(), RequestBody.fromString(content));

    }

    // @Test(description = "object create w/unreadable X-Amz-Date, fails!")
    // public void testObjectCreateBadamzDateUnreadableAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "\\x07";

    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("X-Amz-Date", value))
    // .build(), RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
    // } catch (AmazonServiceException err) {
    // AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
    // }
    // }

    // @Test(description = "object create w/unreadable Date, fails!")
    // public void testObjectCreateBadDateUnreadableAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "\\x07";

    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("Date", value))
    // .build(), RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 RequestTimeTooSkewed");
    // } catch (AmazonServiceException err) {
    // AssertJUnit.assertEquals(err.getErrorCode(), "RequestTimeTooSkewed");
    // }
    // }

    // @Test(description = "object create w/empty X-Amz-Date, fails!")
    // public void testObjectCreateBadamzDateEmptyAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "";

    // s3Client.createBucket(b -> b.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("X-Amz-Date", value))
    // .build(), RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 404 SignatureDoesNotMatch");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 404);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "SignatureDoesNotMatch");
    // }
    // }

    @Test(description = "object create w/empty Date, suceeds!")
    public void testObjectCreateBadDateEmptyAWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "";

        s3Client.createBucket(p -> p.bucket(bucket_name));
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket_name)
                .key(key)
                .contentLength((long) content.length())
                .overrideConfiguration(o -> o.putHeader("Date", value))
                .build(), RequestBody.fromString(content));

    }

    // @Test(description = "object create w/invalid X-Amz-Date, fails!")
    // public void testObjectCreateBadamzDateInvalidAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "Bad date";

    // s3Client.createBucket(p -> p.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("X-Amz-Date", value))
    // .build(), RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "SignatureDoesNotMatch");
    // }
    // }

    @Test(description = "object create w/invalid Date, suceeds..lies!!")
    public void testObjectCreateBadDateInvalidAWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "Bad date";

        s3Client.createBucket(b -> b.bucket(bucket_name));

        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket_name)
                .key(key)
                .contentLength((long) content.length())
                .overrideConfiguration(o -> o.putHeader("Date", value))
                .build(), RequestBody.fromString(content));
    }

    // @Test(description = "object create w/no User-Agent, fails!")
    // public void testObjectCreateBadUANoneAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "";

    // s3Client.createBucket(p -> p.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("User-Agent", value))
    // .build(), RequestBody.fromString(content));
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "SignatureDoesNotMatch");
    // }
    // }

    // @Test(description = "object create w/unreadable User-Agent, fails!")
    // public void testObjectCreateBadUAUnreadableAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "\\x07";

    // s3Client.createBucket(p -> p.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("User-Agent", value))
    // .build(), RequestBody.fromString(content));

    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "SignatureDoesNotMatch");
    // }
    // }

    // @Test(description = "object create w/empty User-Agent, fails!")
    // public void testObjectCreateBadUAEmptyAWS4() {

    // String bucket_name = utils.getBucketName();
    // String key = "key1";
    // String content = "echo lima golf";
    // String value = "";

    // s3Client.createBucket(p -> p.bucket(bucket_name));

    // try {
    // s3Client.putObject(PutObjectRequest.builder()
    // .bucket(bucket_name)
    // .key(key)
    // .contentLength((long) content.length())
    // .overrideConfiguration(o -> o.putHeader("User-Agent", value))
    // .build(), RequestBody.fromString(content));

    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
    // }catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.statusCode(), 403);
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "SignatureDoesNotMatch");
    // }
    // }

    @Test(description = "object create w/Invalid Authorization, fails!")
    public void testObjectCreateBadAuthorizationInvalidAWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "AWS4-HMAC-SHA256 Credential=HAHAHA";

        s3Client.createBucket(p -> p.bucket(bucket_name));

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket_name)
                    .key(key)
                    .contentLength((long) content.length())
                    .overrideConfiguration(o -> o.putHeader("Authorization", value))
                    .build(), RequestBody.fromString(content));
            AssertJUnit.fail("Expected 400 Access Denied");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 403);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
        }
    }

    @Test(description = "object create w/Incorrect Authorization, fails!")
    public void testObjectCreateBadAuthorizationIncorrectAWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "AWS4-HMAC-SHA256 Credential=HAHAHA";

        s3Client.createBucket(p -> p.bucket(bucket_name));

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket_name)
                    .key(key)
                    .contentLength((long) content.length())
                    .overrideConfiguration(o -> o.putHeader("Authorization", value))
                    .build(), RequestBody.fromString(content));
            AssertJUnit.fail("Expected 400 Access Denied");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 403);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
        }
    }

    @Test(description = "object create w/invalid MD5, fails!")
    public void testObjectCreateBadMd5InvalidGarbageAWS4() {

        String bucket_name = utils.getBucketName();
        String key = "key1";
        String content = "echo lima golf";
        String value = "AWS4 HAHAHA";

        s3Client.createBucket(p -> p.bucket(bucket_name));

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket_name)
                    .key(key)
                    .contentLength((long) content.length())
                    .overrideConfiguration(o -> o.putHeader("Content-MD5", value))
                    .build(), RequestBody.fromString(content));
            AssertJUnit.fail("Expected 400 InvalidDigest");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 400);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "InvalidDigest");
        }
    }

    @Test(description = "multipart uploads for small to big sizes using LLAPI, succeeds!")
    public void testMultipartUploadMultipleSizesLLAPIAWS4() {
        String bucketName = utils.getBucketName(prefix);
        String key = "key1";

        s3Client.createBucket(p -> p.bucket(bucketName).build());

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 53 * 1024 * 1024);

        long[] partSizes = {
                5 * 1024 * 1024,
                5 * 1024 * 1024 + 100 * 1024,
                5 * 1024 * 1024 + 600 * 1024,
                10 * 1024 * 1024 + 100 * 1024,
                10 * 1024 * 1024 + 600 * 1024,
                10 * 1024 * 1024
        };

        for (long partSize : partSizes) {
            CompleteMultipartUploadRequest compRequest = utils.multipartUploadLLAPIV2(
                    s3Client,
                    bucketName,
                    key,
                    partSize,
                    filePath);

            s3Client.completeMultipartUpload(compRequest);
        }
    }

    @Test(description = "multipart uploads for small file using LLAPI, succeeds!")
    public void testMultipartUploadSmallLLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        String key = "key1";
        s3Client.createBucket(p -> p.bucket(bucket_name));

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        long size = 5 * 1024 * 1024;

        CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(s3Client, bucket_name, key, size, filePath);
        s3Client.completeMultipartUpload(resp);

    }

    @Test(description = "multipart uploads w/missing part using LLAPI, fails!")
    public void testMultipartUploadIncorrectMissingPartLLAPIAWS4() {
        String bucketName = utils.getBucketName(prefix);
        String key = "key1";

        s3Client.createBucket(b -> b.bucket(bucketName));

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 13 * 1024 * 1024);
        List<CompletedPart> completedParts = new ArrayList<>();

        CreateMultipartUploadResponse initResponse = s3Client.createMultipartUpload(b -> b
                .bucket(bucketName)
                .key(key));

        File file = new File(filePath);
        long contentLength = file.length();
        long partSize = 5 * 1024 * 1024;
        long filePosition = 1024 * 1024;
        String uploadId = initResponse.uploadId();

        for (int i = 7; filePosition < contentLength; i += 3) {
            long currentPartSize = Math.min(partSize, (contentLength - filePosition));

            final int currentPartNumber = i;

            UploadPartResponse uploadPartResponse = s3Client.uploadPart(b -> b
                    .bucket(bucketName)
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(currentPartNumber),
                    RequestBody.fromFile(file.toPath()));

            completedParts.add(CompletedPart.builder()
                    .partNumber(999)
                    .eTag(uploadPartResponse.eTag())
                    .build());

            filePosition += currentPartSize + 512 * 1024;
        }

        CompleteMultipartUploadRequest compRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build())
                .build();

        try {
            s3Client.completeMultipartUpload(compRequest);
            AssertJUnit.fail("Expected S3Exception with error code InvalidPart");
        } catch (S3Exception e) {
            AssertJUnit.assertEquals(e.awsErrorDetails().errorCode(), "InvalidPart");
        }
    }

    @Test(description = "multipart uploads w/non existant upload using LLAPI, fails!")
    public void testAbortMultipartUploadNotFoundLLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        String key = "key1";
        s3Client.createBucket(p -> p.bucket(bucket_name));

        try {
            s3Client.abortMultipartUpload(b -> b.bucket(bucket_name).key(key).uploadId("1"));
            AssertJUnit.fail("Expected 404 NoSuchUpload"); // 404 code
        } catch (S3Exception err) {
            System.out.println(err.awsErrorDetails().errorCode());
            System.out.println(err.statusCode());
            AssertJUnit.assertEquals(err.statusCode(), 404);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchUpload");
        }
    }

    @Test(description = "multipart uploads abort using LLAPI, succeeds!")
    public void testAbortMultipartUploadLLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        String key = "key1";
        s3Client.createBucket(p -> p.bucket(bucket_name));

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        long size = 5 * 1024 * 1024;

        CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(s3Client, bucket_name, key, size, filePath);
        s3Client.abortMultipartUpload(b -> b.bucket(bucket_name).key(key).uploadId(resp.uploadId()));

    }

    @Test(description = "multipart uploads overwrite using LLAPI, succeeds!")
    public void testMultipartUploadOverwriteExistingObjectLLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        String key = "key1";
        s3Client.createBucket(p -> p.bucket(bucket_name));

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        long size = 5 * 1024 * 1024;

        s3Client.putObject(p -> p.bucket(bucket_name).key(key), RequestBody.fromString("foo"));

        CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(s3Client, bucket_name, key, size, filePath);
        s3Client.completeMultipartUpload(resp);
        Assert.assertNotEquals(s3Client.getObject(b -> b.bucket(bucket_name).key(key)).response().contentLength(),
                "foo".length());

    }

    // @Test(description = "multipart uploads for a very small file using LLAPI,
    // fails!")
    // public void testMultipartUploadFileTooSmallFileLLAPIAWS4() {

    // String bucket_name = utils.getBucketName(prefix);
    // String key = "key1";
    // s3Client.createBucket(p -> p.bucket(bucket_name));

    // String filePath = "./data/sample.txt";
    // utils.createFile(filePath, 256 * 1024);
    // long size = 5 * 1024 * 1024;

    // try {
    // CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(s3Client,
    // bucket_name, key, size,
    // filePath);
    // s3Client.completeMultipartUpload(resp);
    // AssertJUnit.fail("Expected 400 EntityTooSmall");
    // } catch (S3Exception err) {
    // // Does not fail as intended , object creation succeeds
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
    // "EntityTooSmall");
    // }
    // }

    @Test(description = "multipart copy for small file using LLAPI, succeeds!")
    public void testMultipartCopyMultipleSizesLLAPIAWS4() {

        String src_bkt = utils.getBucketName(prefix);
        String dst_bkt = utils.getBucketName(prefix);
        String key = "key1";

        s3Client.createBucket(p -> p.bucket(src_bkt));
        s3Client.createBucket(p -> p.bucket(dst_bkt));

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        File file = new File(filePath);

        s3Client.putObject(p -> p.bucket(src_bkt).key(key), RequestBody.fromFile(file));

        CompleteMultipartUploadRequest resp = utils.multipartCopyLLAPIV2(s3Client, dst_bkt, key, src_bkt, key,
                5 * 1024 * 1024);
        s3Client.completeMultipartUpload(resp);

        CompleteMultipartUploadRequest resp2 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt, key, src_bkt, key,
                5 * 1024 * 1024 + 100 * 1024);
        s3Client.completeMultipartUpload(resp2);

        CompleteMultipartUploadRequest resp3 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt, key, src_bkt, key,
                5 * 1024 * 1024 + 600 * 1024);
        s3Client.completeMultipartUpload(resp3);

        CompleteMultipartUploadRequest resp4 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt, key, src_bkt, key,
                10 * 1024 * 1024 + 100 * 1024);
        s3Client.completeMultipartUpload(resp4);

        CompleteMultipartUploadRequest resp5 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt, key, src_bkt, key,
                10 * 1024 * 1024 + 600 * 1024);
        s3Client.completeMultipartUpload(resp5);

        CompleteMultipartUploadRequest resp6 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt, key, src_bkt, key,
                10 * 1024 * 1024);
        s3Client.completeMultipartUpload(resp6);

    }

    @Test(description = "Upload of a file using HLAPI, succeeds!")
    public void testUploadFileHLAPIBigFileAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        String key = "key1";
        s3AsyncClient.createBucket(p -> p.bucket(bucket_name)).join();

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 53 * 1024 * 1024);

        CompletedFileUpload completedFileUpload = utils.UploadFileHLAPIV2(s3AsyncClient, bucket_name, key, filePath);

        Assert.assertNotNull(completedFileUpload.response().eTag());
    }

    // @Test(description = "Upload of a file to non existant bucket using HLAPI,
    // fails!")
    // public void testUploadFileHLAPINonExistantBucketAWS4() {

    // String bucket_name = utils.getBucketName(prefix);
    // String key = "key1";

    // String filePath = "./data/sample.txt";
    // utils.createFile(filePath, 256 * 1024);

    // try {
    // utils.UploadFileHLAPIV2(s3AsyncClient, bucket_name, key, filePath);
    // // Does not fail as intended , object is created
    // AssertJUnit.fail("Expected 400 NoSuchBucket");
    // } catch (S3Exception err) {
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
    // }

    // }

    @Test(description = "Multipart Upload for file using HLAPI, succeeds!")
    public void testMultipartUploadHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);

        s3AsyncClient.createBucket(p -> p.bucket(bucket_name)).join();

        String dir = "./data";
        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);

        CompletedDirectoryUpload completedDirectoryUpload = utils.multipartUploadHLAPIV2(s3AsyncClient, bucket_name,
                null, dir);

        Assert.assertNotNull(completedDirectoryUpload);
        Assert.assertTrue(completedDirectoryUpload.failedTransfers().isEmpty());

    }

    // @Test(description =
    // "Multipart Upload of a file to nonexistant bucket using HLAPI, fails!")
    // public void testMultipartUploadHLAPINonEXistantBucketAWS4()
    // {

    // String bucket_name = utils.getBucketName(prefix);
    // s3Client.createBucket(p -> p.bucket(bucket_name));

    // String dir = "./data";
    // String filePath = "./data/file.mpg";
    // utils.createFile(filePath, 23 * 1024 * 1024);

    // try {
    // utils.multipartUploadHLAPIV2(s3AsyncClient, bucket_name, null, dir);
    // AssertJUnit.fail("Expected 400 NoSuchBucket");
    // } catch (S3Exception err) {
    // // Does not fail as intended , object is created
    // AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
    // }

    // }

    // *** DOESNT map the pause and resume to HLAPI in v2 ***
    // @Test(description = "Multipart Upload of a file with pause and resume using
    // HLAPI, succeeds!")
    // public void testMultipartUploadWithPauseAWS4()
    // ,
    // IOException {

    // String bucket_name = utils.getBucketName(prefix);

    // s3Client.createBucket(p -> p.bucket(bucket_name));

    // String filePath = "./data/file.mpg";
    // utils.createFile(filePath, 23 * 1024 * 1024);
    // String key = "key1";

    // // sets small upload threshold and upload parts size in order to keep the
    // first
    // // part smaller than the whole file. Otherwise, the upload throws an
    // exception
    // // when trying to pause it
    // TransferManager tm = TransferManagerBuilder.standard().withS3Client(svc)
    // .withMultipartUploadThreshold(256 * 1024l).withMinimumUploadPartSize(256 *
    // 1024l).build();
    // Upload myUpload = tm.upload(bucket_name, key, new File(filePath));

    // // pause upload
    // TransferProgress progress = myUpload.getProgress();
    // long MB = 5 * 1024 * 1024l;
    // while (progress.getBytesTransferred() < MB) {
    // Thread.sleep(200);
    // }
    // if (progress.getBytesTransferred() < progress.getTotalBytesToTransfer()) {
    // boolean forceCancel = true;
    // PauseResult<PersistableUpload> pauseResult = myUpload.tryPause(forceCancel);
    // Assert.assertEquals(pauseResult.getPauseStatus().isPaused(), true);

    // // persist PersistableUpload info to a file
    // PersistableUpload persistableUpload = pauseResult.getInfoToResume();
    // File f = new File("resume-upload");
    // if (!f.exists())
    // f.createNewFile();
    // FileOutputStream fos = new FileOutputStream(f);
    // persistableUpload.serialize(fos);
    // fos.close();

    // // Resume upload
    // FileInputStream fis = new FileInputStream(new File("resume-upload"));
    // PersistableUpload persistableUpload1 =
    // PersistableTransfer.deserializeFrom(fis);
    // tm.resumeUpload(persistableUpload1);
    // fis.close();
    // }
    // }

    @Test(description = "Multipart copy using HLAPI, succeeds!")
    public void testMultipartCopyHLAPIAWS4() {

        String src_bkt = utils.getBucketName(prefix);
        String dst_bkt = utils.getBucketName(prefix);
        String key = "key1";

        s3AsyncClient.createBucket(p -> p.bucket(src_bkt)).join();
        s3AsyncClient.createBucket(p -> p.bucket(dst_bkt)).join();

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        CompletedFileUpload completedFileUpload = utils.UploadFileHLAPIV2(s3AsyncClient, src_bkt, key, filePath);
        Assert.assertNotNull(completedFileUpload.response().eTag());

        CompletedCopy completedCopy = utils.multipartCopyHLAPIV2(s3AsyncClient, dst_bkt, key, src_bkt, key);
        Assert.assertNotNull(completedCopy.response().copyObjectResult().eTag());
    }

    @Test(description = "Multipart copy for file with non existant destination bucket using HLAPI, fails!")
    public void testMultipartCopyNoDSTBucketHLAPIAWS4() {

        String src_bkt = utils.getBucketName(prefix);
        String dst_bkt = utils.getBucketName(prefix);
        String key = "key1";

        s3AsyncClient.createBucket(p -> p.bucket(src_bkt)).join();

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);

        CompletedFileUpload completedFileUpload = utils.UploadFileHLAPIV2(s3AsyncClient, src_bkt, key, filePath);
        Assert.assertNotNull(completedFileUpload.response().eTag());

        try {
            utils.multipartCopyHLAPIV2(s3AsyncClient, dst_bkt, key, src_bkt, key);
            AssertJUnit.fail("Expected 404 NoSuchBucket");

        } catch (S3Exception s3Err) {
            Assert.assertEquals(s3Err.statusCode(), 404);
            AssertJUnit.assertEquals(s3Err.awsErrorDetails().errorCode(), "NoSuchBucket");

        } catch (software.amazon.awssdk.core.exception.SdkClientException netErr) {
            netErr.printStackTrace();
            AssertJUnit.fail("Caught a network/DNS error. Ensure forcePathStyle(true) is set on your S3AsyncClient!");
        }
    }

    @Test(description = "Multipart copy w/non existant source bucket using HLAPI, fails!")
    public void testMultipartCopyNoSRCBucketHLAPIAWS4() {

        String src_bkt = utils.getBucketName(prefix);
        String dst_bkt = utils.getBucketName(prefix);
        String key = "key1";

        s3AsyncClient.createBucket(p -> p.bucket(dst_bkt)).join();

        try {
            utils.multipartCopyHLAPIV2(s3AsyncClient, dst_bkt, key, src_bkt, key);
            AssertJUnit.fail("Expected 404 Not Found");

        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 404);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchKey");
        }
    }

    @Test(description = "Multipart copy w/non existant source key using HLAPI, fails!")
    public void testMultipartCopyNoSRCKeyHLAPIAWS4() {

        String src_bkt = utils.getBucketName(prefix);
        String dst_bkt = utils.getBucketName(prefix);
        String key = "key1";

        s3AsyncClient.createBucket(p -> p.bucket(src_bkt));
        s3AsyncClient.createBucket(p -> p.bucket(dst_bkt));

        try {
            utils.multipartCopyHLAPIV2(s3AsyncClient, dst_bkt, key, src_bkt, key);
            AssertJUnit.fail("Expected 404 Not Found");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 404);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchKey");
        }
    }

    @Test(description = "Download using HLAPI, suceeds!")
    public void testDownloadHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        s3AsyncClient.createBucket(p -> p.bucket(bucket_name)).join();
        String key = "key1";

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        CompletedFileUpload completedFileUpload = utils.UploadFileHLAPIV2(s3AsyncClient, bucket_name, key, filePath);
        Assert.assertNotNull(completedFileUpload.response().eTag());

        CompletedFileDownload completedDownload = utils.downloadHLAPIV2(s3AsyncClient, bucket_name, key,
                new File(filePath));
        Assert.assertNotNull(completedDownload.response().eTag());

    }

    @Test(description = "Download from non existant bucket using HLAPI, fails!")
    public void testDownloadNoBucketHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        String key = "key1";
        String filePath = "./data/sample.txt";

        try {
            utils.downloadHLAPIV2(s3AsyncClient, bucket_name, key, new File(filePath));
            AssertJUnit.fail("Expected 404 Not Found");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 404);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
        }
    }

    @Test(description = "Download w/no key using HLAPI, suceeds!")
    public void testDownloadNoKeyHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        s3AsyncClient.createBucket(p -> p.bucket(bucket_name)).join();
        String key = "key1";

        String filePath = "./data/sample.txt";

        try {
            utils.downloadHLAPIV2(s3AsyncClient, bucket_name, key, new File(filePath));
            AssertJUnit.fail("Expected 404 Not Found");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 404);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchKey");
        }
    }

    @Test(description = "Multipart Download using HLAPI, suceeds!")
    public void testMultipartDownloadHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        s3AsyncClient.createBucket(p -> p.bucket(bucket_name)).join();
        String key = "key1";

        String dstDir = "./downloads";
        File dir = new File(dstDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File destinationFile = new File(dir, key);

        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);

        CompletedFileUpload completedFileUpload = utils.UploadFileHLAPIV2(s3AsyncClient, bucket_name, key, filePath);
        Assert.assertNotNull(completedFileUpload.response().eTag());
        CompletedFileDownload completedDownload = utils.downloadHLAPIV2(s3AsyncClient, bucket_name, key,
                destinationFile);

        Assert.assertNotNull(completedDownload.response().eTag());
    }

    @Test(description = "Multipart Download with pause and resume using HLAPI, suceeds!")
    public void testMultipartDownloadWithPauseHLAPIAWS4() throws Exception {

        String bucket_name = utils.getBucketName(prefix);
        s3AsyncClient.createBucket(p -> p.bucket(bucket_name)).join();
        String key = "key1";
        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        String destPath = "./data/file2.mpg";

        try (S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build()) {
            tm.uploadFile(u -> u
                    .putObjectRequest(p -> p.bucket(bucket_name).key(key))
                    .source(Paths.get(filePath)))
                    .completionFuture().join();

            FileDownload myDownload = tm.downloadFile(d -> d
                    .getObjectRequest(g -> g.bucket(bucket_name).key(key))
                    .destination(Paths.get(destPath)));

            long MB = 2 * 1024 * 1024;
            while (myDownload.progress().snapshot().transferredBytes() < MB) {
                Thread.sleep(100);
            }

            long totalBytes = myDownload.progress().snapshot().totalBytes().orElse(0L);

            if (myDownload.progress().snapshot().transferredBytes() < totalBytes) {
                ResumableFileDownload resumable = myDownload.pause();

                File f = new File("resume-download.json");
                String serializedData = resumable.serializeToString();
                Files.write(f.toPath(), serializedData.getBytes());

                String readData = new String(Files.readAllBytes(f.toPath()));
                ResumableFileDownload persistDownload = ResumableFileDownload.fromString(readData);

                FileDownload resumedDownload = tm.resumeDownloadFile(persistDownload);

                resumedDownload.completionFuture().join();
            } else {
                myDownload.completionFuture().join();
            }
            AssertJUnit.assertTrue("Downloaded file should exist", new File(destPath).exists());
            AssertJUnit.assertEquals(new File(filePath).length(), new File(destPath).length());
        } catch (Exception e) {
            e.printStackTrace();
            AssertJUnit.fail("Expected no exception");
        }
    }

    @Test(description = "Multipart Download from non existant bucket using HLAPI, fails!")
    public void testMultipartDownloadNoBucketHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);

        String key = "key1";
        String dstDir = "./downloads";
        System.out.println("Testing with bucket: " + bucket_name);

        try {
            s3AsyncClient.deleteBucket(p -> p.bucket(bucket_name)).join();
        } catch (Exception ignored) {
        }

        try {
            utils.multipartDownloadHLAPIV2(s3AsyncClient, bucket_name, key, new File(dstDir));
            AssertJUnit.fail("Expected 404 NoSuchBucket");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 404);
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
        }
    }

    @Test(description = "Multipart Download w/no key using HLAPI, fails!")
    public void testMultipartDownloadNoKeyHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        s3AsyncClient.createBucket(p -> p.bucket(bucket_name));
        String key = "key1";
        String dstDir = "./downloads";

        try {
            utils.multipartDownloadHLAPIV2(s3AsyncClient, bucket_name, key, new File(dstDir));
            AssertJUnit.fail("Expected 404 Not Found");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.statusCode(), 404);
            System.out.println(err.awsErrorDetails().errorCode());
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
        }
    }

    @Test(description = "Upload of list of files using HLAPI, suceeds!")
    public void testUploadFileListHLAPIAWS4() {

        String bucket_name = utils.getBucketName(prefix);
        s3AsyncClient.createBucket(p -> p.bucket(bucket_name)).join();
        String targetPrefix = "my-uploads";
        String uploadDir = "./data/uploadList";
        new File(uploadDir).mkdirs();
        utils.createFile(uploadDir + "/file1.txt", 1024);
        utils.createFile(uploadDir + "/file2.txt", 1024);

        try {
            CompletedDirectoryUpload completedUpload = utils.multipartUploadHLAPIV2(
                    s3AsyncClient,
                    bucket_name,
                    targetPrefix,
                    uploadDir);

            AssertJUnit.assertTrue(completedUpload.failedTransfers().isEmpty());
            int objectCount = 0;
            String continuationToken = null;

            do {
                final String currentToken = continuationToken;

                ListObjectsV2Response listing = s3AsyncClient.listObjectsV2(b -> b
                        .bucket(bucket_name)
                        .prefix(targetPrefix)
                        .continuationToken(currentToken)).join();

                objectCount += listing.contents().size();
                continuationToken = listing.nextContinuationToken();

            } while (continuationToken != null);
            AssertJUnit.assertEquals(2, objectCount);

        } catch (S3Exception s3Err) {
            s3Err.printStackTrace();
            AssertJUnit.fail("Expected upload to succeed, but got: " + s3Err.awsErrorDetails().errorCode());
        } catch (software.amazon.awssdk.core.exception.SdkClientException netErr) {
            netErr.printStackTrace();
            AssertJUnit.fail("Network or DNS error occurred. Check path-style access settings.");
        }
    }
}