import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.transfer.Copy;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.MultipleFileDownload;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.PauseResult;
import com.amazonaws.services.s3.transfer.PersistableDownload;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.PersistableUpload;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferProgress;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.util.StringUtils;

public class ObjectTest {

	private static S3 utils =  S3.getInstance();
	AmazonS3 svc = utils.getCLI();
	String prefix = utils.getPrefix();

	@AfterMethod
	public  void tearDownAfterClass() throws Exception {
		
		utils.tearDown(svc);	
	}


	@BeforeMethod
	public void setUp() throws Exception {
	}

	@Test(description = "object write to non existant bucket, fails")
	public void testObjectWriteToNonExistantBucket() {
		
		String non_exixtant_bucket = utils.getBucketName(prefix);
		
		try {
			
			svc.putObject(non_exixtant_bucket, "key1", "echo");
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
	}
	
	@Test(description = "Reading non existant object, fails")
	public void testObjectReadNotExist() {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		try {
			
			svc.getObject(bucket_name, "key");
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchKey");
		}
	}
	
	@Test(description = "object read from non existant bucket, fails")
	public void testObjectReadFromNonExistantBucket() {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		try {
			
			svc.getObject(bucket_name, "key");
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchKey");
		}
	}
	
	@Test(description = "object read, update, write and delete, suceeds")
	public void testMultiObjectDelete() {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		svc.putObject(bucket_name, "key1", "echo");
		
		
		DeleteObjectsRequest multiObjectDeleteRequest = new DeleteObjectsRequest(bucket_name);
		List<KeyVersion> keys = new ArrayList<KeyVersion>();
		keys.add(new KeyVersion("key1"));
		keys.add(new KeyVersion("key2"));
		keys.add(new KeyVersion("key3"));    
		multiObjectDeleteRequest.setKeys(keys);
		svc.deleteObjects(multiObjectDeleteRequest);
		
		ObjectListing list = svc.listObjects(bucket_name);
		AssertJUnit.assertEquals(list.getObjectSummaries().size(), 0);
		    
	}
	
	@Test(description = "creating unreadable object, fails")
	public void testObjectCreateUnreadable() {
		
		try {
			
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		svc.putObject(bucket_name, "\\x0a", "bar");
		
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
	}
	
	@Test(description = "reading empty object, fails")
	public void testObjectHeadZeroBytes() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key";
		
		try {
			
			svc.createBucket(new CreateBucketRequest(bucket_name));
			
			svc.putObject(bucket_name, key, "");
			
			String result = svc.getObjectAsString(bucket_name, key);
			Assert.assertEquals(result.length(), 0);
		
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "XAmzContentSHA256Mismatch");
		}
		
	}
	
	@Test(description = "On object write and get with w/Etag, succeeds")
	public void testObjectWriteCheckEtag() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key";
		String Etag = "37b51d194a7513e45b56f6524f2d51f2";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		svc.putObject(bucket_name, key, "bar");
		
		S3Object resp = svc.getObject(new GetObjectRequest(bucket_name, key));
		Assert.assertEquals(resp.getObjectMetadata().getETag(), Etag);
		
	}
	
	@Test(description = "object write w/Cache-Control header, succeeds")
	public void testObjectWriteCacheControl() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String auth = "x07";
		String cache_control = "public, max-age=14400";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Cache-Control", cache_control );
		
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		
		S3Object resp = svc.getObject(new GetObjectRequest(bucket_name, key));
		Assert.assertEquals(resp.getObjectMetadata().getCacheControl(), cache_control);
		
	}
	
	@Test(description = "object write, read, update and delete, succeeds")
	public void testObjectWriteReadUpdateReadDelete() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		svc.putObject(bucket_name, key, content);
		String got = svc.getObjectAsString(bucket_name, key);
		Assert.assertEquals(got, content);
		
		//update
		String newContent = "charlie echo";
		svc.putObject(bucket_name, key, newContent);
		got = svc.getObjectAsString(bucket_name, key);
		Assert.assertEquals(got, newContent);
		
		svc.deleteObject(bucket_name, key);
		try {
			
			got = svc.getObjectAsString(bucket_name, key);
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchKey");
		}

		
	}
	
	@Test(description = "object copy w/non existant buckets, fails")
	public void testObjectCopyBucketNotFound() {
		
		String bucket1 = utils.getBucketName(prefix);
		String bucket2 = utils.getBucketName(prefix);
		String key ="key1";
		
		
		try {
			
			svc.copyObject(bucket1, key, bucket2, key);
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
		
	};
	
	@Test(description = "object copy w/no source key, fails")
	public void TestObjectCopyKeyNotFound() {
		
		String bucket1 = utils.getBucketName(prefix);
		String bucket2 = utils.getBucketName(prefix);
		String key ="key1";
		
		svc.createBucket(bucket1);
		svc.createBucket(bucket2);
		
		try {
			
			svc.copyObject(bucket1, key, bucket2, key);
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchKey");
		}
		
	}
	
	@Test(description = "object create w/empty content type, fails")
	public void testObjectCreateBadContenttypeEmpty() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String contType = " ";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		try {
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType(contType);
			metadata.setContentLength(contentBytes.length);
			
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
	}
	
	@Test(description = "object create w/no content type, fails")
	public void testObjectCreateBadContenttypeNone() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String contType = "";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		try {
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType(contType);
			metadata.setContentLength(contentBytes.length);
			
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
		
	}
	
	@Test(description = "object create w/unreadable content type, fails")
	public void testObjectCreateBadContenttypeUnreadable() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String contType = "\\x08";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		try {
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType(contType);
			metadata.setContentLength(contentBytes.length);
			
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
		
	}
	
	@Test(description = "object create w/Unreadable Authorization, succeeds")
	public void testObjectCreateBadAuthorizationUnreadable() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String auth = "x07";
			
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Authorization", auth );
			
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
		
	}
	
	@Test(description = "object create w/empty Authorization, succeeds")
	public void testObjectCreateBadAuthorizationEmpty() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String auth = " ";
			
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Authorization", auth );
			
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
		
	}
	
	@Test(description = "object create w/no Authorization, succeeds")
	public void testObjectCreateBadAuthorizationNone() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String auth = "";
			
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Authorization", auth );
			
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
		
	}
	
	@Test(description = "object create w/negative content length, fails")
	public void testObjectCreateBadContentlengthNegative() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			long contlength = -1;
			
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setHeader("Content-Length", contlength );
			
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (IllegalArgumentException err) {
			AssertJUnit.assertNotSame(err.getLocalizedMessage(), null);
		}
		
	}
	
	@Test(description = "object create w/empty Expect, succeeds")
	public void testObjectCreateBadExpectEmpty() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String expected = " ";
				
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
				
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Expect", expected);
				
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
		
	}
	
	@Test(description = "object create w/unreadable Expect, succeeds")
	public void testObjectCreateBadExpectUnreadable() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String expected = "\\x07";
				
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
				
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Expect", expected);
				
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
		
	}
	
	@Test(description = "object create w/mismatch Expect, fails")
	public void testObjectCreateBadExpectMismatch() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String expected = "200";
			
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
			
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Expect", expected);
			
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));	
	}
	
	@Test(description = "object create w/no Expect, fails")
	public void TestObjectCreateBadExpectNone() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String expected = "";
			
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
			
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Expect", expected);
			
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));	
	}
	
	@Test(description = "object create w/short MD5, fails")
	public void testObjectCreateBadMd5InvalidShort() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String md5 = "WJyYWNhZGFicmE=";
				
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
				
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Content-MD5", md5);
				
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidDigest");
		}
		
	}
	
	@Test(description = "object create w/empty MD5, fails")
	public void TestObjectCreateBadMd5Empty() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String md5 = " ";
				
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
				
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Content-MD5", md5);
				
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
		
	}
	
	@Test(description = "object create w/invalid MD5, fails")
	public void testObjectCreateBadMd5Ivalid() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String md5 = "rL0Y20zC+Fzt72VPzMSk2A==";
				
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
				
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Content-MD5", md5);
				
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "BadDigest");
		}
		
	}
	
	@Test(description = "object create w/short MD5, fails")
	public void testObjectCreateBadMd5Unreadable() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String md5 = "\\x07";
				
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
				
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Content-MD5", md5);
				
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "AccessDenied");
		}
		
	}
	
	@Test(description = "object create w/no MD5, fails")
	public void testObjectCreateBadMd5None() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key = "key1";
			String content = "echo lima golf";
			String md5 = "";
				
			svc.createBucket(new CreateBucketRequest(bucket_name));

			byte[] contentBytes = content.getBytes(StringUtils.UTF8);
			InputStream is = new ByteArrayInputStream(contentBytes);
				
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setHeader("Content-MD5", md5);
				
			svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidDigest");
		}
		
	}
	
	//...................................................... SSE tests...............................................................
	
	@Test(description = "object write(1b) w/SSE succeeds on https, fails on http")
	public void testEncryptedTransfer1b() {
		try {
			
			String arr[] = utils.EncryptionSseCustomerWrite(svc, 1);
			Assert.assertEquals(arr[0], arr[1]);
		} catch (IllegalArgumentException err) {
			String expected_error = "HTTPS must be used when sending customer encryption keys (SSE-C) to S3, in order to protect your encryption keys.";
			AssertJUnit.assertEquals(err.getMessage(), expected_error);
		}
	}
	
	@Test(description = "object write (1kb) w/SSE succeeds on https, fails on http")
	public void testEncryptedTransfer1kb() {
		
		try {
			
			String arr[] = utils.EncryptionSseCustomerWrite(svc, 1024);
			Assert.assertEquals(arr[0], arr[1]);
		} catch (IllegalArgumentException err) {
			String expected_error = "HTTPS must be used when sending customer encryption keys (SSE-C) to S3, in order to protect your encryption keys.";
			AssertJUnit.assertEquals(err.getMessage(), expected_error);
		}
	}
	
	@Test(description = "object write (1MB) w/SSE succeeds on https, fails on http")
	public void testEncryptedTransfer1MB() {
		
		try {
			
			String arr[] = utils.EncryptionSseCustomerWrite(svc, 1024*1024);
			Assert.assertEquals(arr[0], arr[1]);
		} catch (IllegalArgumentException err) {
			String expected_error = "HTTPS must be used when sending customer encryption keys (SSE-C) to S3, in order to protect your encryption keys.";
			AssertJUnit.assertEquals(err.getMessage(), expected_error);
		}
	}
	
	@Test(description = "object write w/key w/no SSE  succeeds on https, fails on http")
	public void testEncryptionKeyNoSSEC() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key ="key1";
			String data = utils.repeat("testcontent", 100);
			
			svc.createBucket(bucket_name);	
			
			PutObjectRequest putRequest = new PutObjectRequest(bucket_name, key, data);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-key", "pO3upElrwuEXSoFwCfnZPdSsmt/xWeFa0N9KgDijwVs=");
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-key-md5", "DWygnHRtgiJ77HCm+1rvHw==");
			putRequest.setMetadata(objectMetadata);
			svc.putObject(putRequest);
			
			String rdata = svc.getObjectAsString(bucket_name, key);
			Assert.assertEquals(rdata, data);
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "XAmzContentSHA256Mismatch");
		}
		
		
	}
	
	@Test(description = "object write (1kb) w/SSE, fails")
	public void testEncryptionKeySSECNoKey() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key ="key1";
			String data = utils.repeat("testcontent", 100);
			
			svc.createBucket(bucket_name);	
			
			PutObjectRequest putRequest = new PutObjectRequest(bucket_name, key, data);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			//objectMetadata.setContentLength(data.length());
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-algorithm", "AES256");
			putRequest.setMetadata(objectMetadata);
			svc.putObject(putRequest);
			
			try {
				
				String rdata = svc.getObjectAsString(bucket_name, key);
			} catch (AmazonServiceException err) {
				AssertJUnit.assertEquals(err.getErrorCode().isEmpty(), false);
			}
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidRequest");
		}
	}
	
	@Test(description = "object write w/SSE andno MD5, fails")
	public void testEncryptionKeySSECNoMd5() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key ="key1";
			String data = utils.repeat("testcontent", 100);
			
			svc.createBucket(bucket_name);	
			
			PutObjectRequest putRequest = new PutObjectRequest(bucket_name, key, data);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			//objectMetadata.setContentLength(data.length());
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-algorithm", "AES256");
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-key", "pO3upElrwuEXSoFwCfnZPdSsmt/xWeFa0N9KgDijwVs=");
			putRequest.setMetadata(objectMetadata);
			svc.putObject(putRequest);
			
			try {
				
				String rdata = svc.getObjectAsString(bucket_name, key);
			} catch (AmazonServiceException err) {
				AssertJUnit.assertEquals(err.getErrorCode().isEmpty(), false);
			}
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidDigest");
		}
	}
	
	@Test(description = "object write w/SSE and Invalid MD5, fails")
	public void testEncryptionKeySSECInvalidMd5() {
		
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key ="key1";
			String data = utils.repeat("testcontent", 100);
			
			svc.createBucket(bucket_name);	
			
			PutObjectRequest putRequest = new PutObjectRequest(bucket_name, key, data);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-algorithm", "AES256");
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-key", "pO3upElrwuEXSoFwCfnZPdSsmt/xWeFa0N9KgDijwVs=");
			objectMetadata.setHeader("x-amz-server-side-encryption-customer-key-md5", "AAAAAAAAAAAAAAAAAAAAAA==");
			putRequest.setMetadata(objectMetadata);
			svc.putObject(putRequest);
			
			try {
				
				String rdata = svc.getObjectAsString(bucket_name, key);
			} catch (AmazonServiceException err) {
				AssertJUnit.assertEquals(err.getErrorCode().isEmpty(), false);
			}
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidDigest");
		}
	}
	
	@Test(description = "object write w/KMS, suceeds with https")
	public void testSSEKMSPresent() {
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key ="key1";
			String data = utils.repeat("testcontent", 100);
			String keyId = "testkey-1";
			
			svc.createBucket(bucket_name);	
			
			PutObjectRequest putRequest = new PutObjectRequest(bucket_name, key, data);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			objectMetadata.setHeader("x-amz-server-side-encryption", "aws:kms");
			objectMetadata.setHeader("x-amz-server-side-encryption-aws-kms-key-id", keyId );
			putRequest.setMetadata(objectMetadata);
			svc.putObject(putRequest);
			
			String rdata = svc.getObjectAsString(bucket_name, key);
			Assert.assertEquals(rdata, data);
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "XAmzContentSHA256Mismatch");
		}
	}
	
	@Test(description = "object write w/KMS and no kmskeyid, fails")
	public void testSSEKMSNoKey() {
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key ="key1";
			String data = utils.repeat("testcontent", 100);
			
			svc.createBucket(bucket_name);	
			
			PutObjectRequest putRequest = new PutObjectRequest(bucket_name, key, data);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			objectMetadata.setHeader("x-amz-server-side-encryption", "aws:kms");
			putRequest.setMetadata(objectMetadata);
			svc.putObject(putRequest);
			
			try {
				
				String rdata = svc.getObjectAsString(bucket_name, key);
			} catch (AmazonServiceException err) {
				AssertJUnit.assertEquals(err.getErrorCode().isEmpty(), false);
			}
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidAccessKeyId");
		}
	}
	
	@Test(description = "object write w/no KMS and with kmskeyid, fails")
	public void testSSEKMSNotDeclared() {
		try {
			
			String bucket_name = utils.getBucketName(prefix);
			String key ="key1";
			String data = utils.repeat("testcontent", 100);
			String keyId = "testkey-1";
			
			svc.createBucket(bucket_name);	
			
			PutObjectRequest putRequest = new PutObjectRequest(bucket_name, key, data);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			objectMetadata.setHeader("x-amz-server-side-encryption-aws-kms-key-id", keyId );
			putRequest.setMetadata(objectMetadata);
			svc.putObject(putRequest);
			
			String rdata = svc.getObjectAsString(bucket_name, key);
			Assert.assertEquals(rdata, data);
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "XAmzContentSHA256Mismatch");
		}
	}

	//......................................prefixes, delimeter, markers........................................
	
	@Test(description = "object list) w/ percentage delimeter, suceeds")
	public void testObjectListDelimiterPercentage() {
		
		String [] keys = {"b%ar", "b%az", "c%ab", "foo"};
		String delim = "%";
		java.util.List<String> expected_prefixes = Arrays.asList("b%", "c%");
		java.util.List<String> expected_keys = Arrays.asList("foo");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals(result.getCommonPrefixes(), expected_prefixes);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ whitespace delimeter, suceeds")
	public void testObjectListDelimiterWhitespace() {
		
		String [] keys = {"bar", "baz", "cab", "foo"};
		String delim = " ";
        try {
        	
    		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
    		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withDelimiter(delim);
            ListObjectsV2Result result = svc.listObjectsV2(req);
            
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
		
	}
	
	@Test(description = "object list) w/dot delimeter, suceeds")
	public void testObjectListDelimiterDot() {
		
		String [] keys = {"b.ar", "b.az", "c.ab", "foo"};
		String delim = ".";
		java.util.List<String> expected_prefixes = Arrays.asList("b.", "c.");
		java.util.List<String> expected_keys = Arrays.asList("foo");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals(result.getCommonPrefixes(), expected_prefixes);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/unreadable delimeter, succeeds")
	public void testObjectListDelimiterUnreadable() {
		
		String [] keys = {"bar", "baz", "cab", "foo"};
		String delim = "\\x0a";
        try {
        	
    		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
    		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withDelimiter(delim);
            ListObjectsV2Result result = svc.listObjectsV2(req);
            
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidArgument");
		}
		
	}
	
	@Test(description = "object list) w/ non existant delimeter, suceeds")
	public void testObjectListDelimiterNotExist() {
		
		String [] keys = {"bar", "baz", "cab", "foo"};
		String delim = "/";
		java.util.List<String> expected_keys = Arrays.asList("bar", "baz", "cab", "foo");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ basic prefix, suceeds")
	public void testObjectListPrefixBasic() {
		
		String [] keys = {"foo/bar", "foo/baz", "quux"};
		String prefix = "foo/";
		java.util.List<String> expected_keys = Arrays.asList("foo/bar", "foo/baz");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ alt prefix, suceeds")
	public void testObjectListPrefixAlt() {
		
		String [] keys = {"bar", "baz", "foo"};
		String prefix = "ba";
		java.util.List<String> expected_keys = Arrays.asList("bar", "baz");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ empty prefix, suceeds")
	public void testObjectListPrefixEmpty() {
		
		String [] keys = {"foo/bar", "foo/baz", "quux"};
		String prefix = "";
		java.util.List<String> expected_keys = Arrays.asList("foo/bar", "foo/baz", "quux");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ empty prefix, suceeds")
	public void testObjectListPrefixNone() {
		
		String [] keys = {"foo/bar", "foo/baz", "quux"};
		String prefix = "";
		java.util.List<String> expected_keys = Arrays.asList("foo/bar", "foo/baz", "quux");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ non existant prefix, suceeds")
	public void testObjectListPrefixNotExist() {
		
		String [] keys = {"foo/bar", "foo/baz", "quux"};
		String prefix = "d";
		java.util.List<String> expected_keys = Arrays.asList();
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ unreadable prefix, suceeds")
	public void testObjectListPrefixUnreadable() {
		
		String [] keys = {"foo/bar", "foo/baz", "quux"};
		String prefix = "\\x0a";
		java.util.List<String> expected_keys = Arrays.asList();
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ basic prefix and delimeter, suceeds")
	public void testObjectListPrefixDelimiterBasic() {
		
		String [] keys = {"foo/bar", "foo/baz/xyzzy", "quux/thud", "asdf"};
		String prefix = "foo/";
		String delim = "/";
		java.util.List<String> expected_keys = Arrays.asList("foo/bar");
		java.util.List<String> expected_prefixes = Arrays.asList( "foo/baz/");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals((result.getCommonPrefixes()), expected_prefixes);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ alt prefix and delimeter, suceeds")
	public void testObjectListPrefixDelimiterAlt() {
		
		String [] keys = {"bar", "bazar", "cab", "foo"};
		String prefix = "ba";
		String delim = "a";
		java.util.List<String> expected_keys = Arrays.asList("bar");
		java.util.List<String> expected_prefixes = Arrays.asList( "baza");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals((result.getCommonPrefixes()), expected_prefixes);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ non existant prefix and delimeter, suceeds")
	public void testObjectListPrefixDelimiterPrefixNotExist() {
		
		String [] keys = {"b/a/r", "b/a/c", "b/a/g", "g"};
		String prefix = "d";
		String delim = "/";
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list.isEmpty(), true);
		
	}
	
	@Test(description = "object list) w/ prefix and delimeter non existant, suceeds")
	public void testObjectListPrefixDelimiterDelimiterNotExist() {
		
		String [] keys = {"b/a/c", "b/a/g", "b/a/r", "g"};
		String prefix = "b";
		String delim = "z";
		java.util.List<String> expected_keys = Arrays.asList("b/a/c", "b/a/g", "b/a/r");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ prefix and delimeter and delimeter non existant, suceeds")
	public void testObjectListPrefixDelimiterPrefixDelimiterNotExist() {
		
		String [] keys = {"b/a/c", "b/a/g", "b/a/r", "g"};
		String prefix = "y";
		String delim = "z";
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withPrefix(prefix).withDelimiter(delim);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getPrefix(), prefix);
        Assert.assertEquals(result.getDelimiter(), delim);
        
        Assert.assertEquals((result.getCommonPrefixes()).isEmpty(), true);
        
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list.isEmpty(), true);
		
	}
	
	@Test(description = "object list) w/ negative maxkeys, suceeds")
	public void testObjectListMaxkeysNegative() {
		
		//passes..wonder blandar
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withMaxKeys(-1);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getMaxKeys(), -1);
        Assert.assertEquals(result.isTruncated(), true);
       
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list.isEmpty(), true);
		
	}
	
	@Test(description = "object list) w/maxkeys=1 , suceeds")
	public void testObjectListMaxkeysOne() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		java.util.List<String> expected_keys  = Arrays.asList("bar");
		
		int max_keys = 1;
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withMaxKeys(max_keys);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getMaxKeys(), max_keys);
        Assert.assertEquals(result.isTruncated(), true);
       
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/maxkeys=0, suceeds")
	public void testObjectListMaxkeysZero() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		
		int max_keys = 0;
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName()).withMaxKeys(max_keys);
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getMaxKeys(), max_keys);
        Assert.assertEquals(result.isTruncated(), false);
       
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list.isEmpty(), true);
		
	}
	
	@Test(description = "object list) w/ no maxkeys, suceeds")
	public void testObjectListMaxkeysNone() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		java.util.List<String> expected_keys = Arrays.asList("bar", "baz", "foo", "quxx");
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket.getName());
        ListObjectsV2Result result = svc.listObjectsV2(req);
        
        Assert.assertEquals(result.getMaxKeys(), 1000);
        Assert.assertEquals(result.isTruncated(), false);
       
		Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/ empty marker, fails")
	public void testObjectListMarkerEmpty() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		String marker = " ";
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		
		try {
			
			ListObjectsRequest req = new ListObjectsRequest();
			req.setBucketName(bucket.getName());
			req.setMarker(marker);
			
			ObjectListing result = svc.listObjects(req);
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test(description = "object list) w/ unreadable marker, succeeds")
	public void testObjectListMarkerUnreadable() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		java.util.List<String> expected_keys = Arrays.asList("bar", "baz", "foo", "quxx");
		String marker = "\\x0a";
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		ListObjectsRequest req = new ListObjectsRequest();
		req.setBucketName(bucket.getName());
		req.setMarker(marker);
		ObjectListing result = svc.listObjects(req); 

        Assert.assertEquals(result.getMarker(), marker);
        Assert.assertEquals(result.isTruncated(), false);
        
        
        Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/marker not in list, succeds")
	public void testObjectListMarkerNotInList() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		java.util.List<String> expected_keys = Arrays.asList("foo", "quxx");
		String marker = "blah";
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		ListObjectsRequest req = new ListObjectsRequest();
		req.setBucketName(bucket.getName());
		req.setMarker(marker);
		ObjectListing result = svc.listObjects(req); 

        Assert.assertEquals(result.getMarker(), marker);
        
        Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	@Test(description = "object list) w/marker after list, fails")
	public void testObjectListMarkerAfterList() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		String marker = "zzz";
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		ListObjectsRequest req = new ListObjectsRequest();
		req.setBucketName(bucket.getName());
		req.setMarker(marker);
		ObjectListing result = svc.listObjects(req); 

        Assert.assertEquals(result.getMarker(), marker);
        Assert.assertEquals(result.isTruncated(), false);
        
        
        Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list.isEmpty(), true);
		
	}
	
	@Test(description = "object list) w/marker before list, fails")
	public void testObjectListMarkerBeforeList() {
		
		String [] keys = {"bar", "baz", "foo", "quxx"};
		java.util.List<String> expected_keys = Arrays.asList("bar", "baz", "foo", "quxx");
		String marker = "aaa";
		
		com.amazonaws.services.s3.model.Bucket bucket = utils.createKeys(svc, keys);
		ListObjectsRequest req = new ListObjectsRequest();
		req.setBucketName(bucket.getName());
		req.setMarker(marker);
		ObjectListing result = svc.listObjects(req); 

        Assert.assertEquals(result.getMarker(), marker);
        Assert.assertEquals(result.isTruncated(), false);
        
        
        Object[] k = new Object[] {};
		ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(k));
		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
			list.add(objectSummary.getKey());
        }
		Assert.assertEquals(list, expected_keys);
		
	}
	
	//.......................................Get Ranged Object in Range....................................................
	@Test(description = "get object w/range -> return trailing bytes, suceeds")
	public void testRangedReturnTrailingBytesResponseCode() throws IOException {
			
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "testcontent";
			
		try {


		svc.createBucket(new CreateBucketRequest(bucket_name));
		svc.putObject(bucket_name, key, content);
			
		GetObjectRequest request = new GetObjectRequest(bucket_name, key);
	    request.withRange(4, 10);
	    S3Object obj = svc.getObject(request);
	        
	    BufferedReader reader = new BufferedReader(new InputStreamReader(obj.getObjectContent()));
	    	while (true) {
	        	String line = reader.readLine();
	            if (line == null) break;
	            String str = content.substring(4);
	            Assert.assertEquals(line, str);
	   }

	   } catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
	        	
	}
		
	@Test(description = "get object w/range -> leading bytes, suceeds")
	public void testRangedSkipLeadingBytesResponseCode() throws IOException {
			
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "testcontent";
			
		svc.createBucket(new CreateBucketRequest(bucket_name));
		svc.putObject(bucket_name, key, content);
			
		GetObjectRequest request = new GetObjectRequest(bucket_name, key);
	    request.withRange(4, 10);
	    S3Object obj = svc.getObject(request);
	        
	    BufferedReader reader = new BufferedReader(new InputStreamReader(obj.getObjectContent()));
	    	while (true) {
	        	String line = reader.readLine();
	            if (line == null) break;
	            String str = content.substring(4);
	            Assert.assertEquals(line, str);
	       
	       }	
	}
		
	@Test(description = "get object w/range, suceeds")
	public void testRangedrequestResponseCode() throws IOException {
			
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "testcontent";
			
		svc.createBucket(new CreateBucketRequest(bucket_name));
		svc.putObject(bucket_name, key, content);
			
		GetObjectRequest request = new GetObjectRequest(bucket_name, key);
	    request.withRange(4, 7);
	    S3Object obj = svc.getObject(request);
	        
	    BufferedReader reader = new BufferedReader(new InputStreamReader(obj.getObjectContent()));
	      while (true) {
	        	String line = reader.readLine();
	            if (line == null) break;
	            String str = content.substring(4,8);
	            Assert.assertEquals(line, str);
	      }	
				
	}
	
	@Test(description = "multipart uploads for small to big sizes using LLAPI, succeeds!")
	public void testMultipartUploadMultipleSizesLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/file.mpg";
		
		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, 5 * 1024 * 1024, filePath);
		svc.completeMultipartUpload(resp);
		
		CompleteMultipartUploadRequest resp2 = utils.multipartUploadLLAPI(svc, bucket_name, key, 5 * 1024 * 1024 + 100 * 1024, filePath);
		svc.completeMultipartUpload(resp2);
		
		CompleteMultipartUploadRequest resp3 = utils.multipartUploadLLAPI(svc, bucket_name, key, 5 * 1024 * 1024 + 600 * 1024, filePath);
		svc.completeMultipartUpload(resp3);
		
		CompleteMultipartUploadRequest resp4 = utils.multipartUploadLLAPI(svc, bucket_name, key, 10 * 1024 * 1024 + 100 * 1024, filePath);
		svc.completeMultipartUpload(resp4);
		
		CompleteMultipartUploadRequest resp5 = utils.multipartUploadLLAPI(svc, bucket_name, key, 10 * 1024 * 1024 + 600 * 1024, filePath);
		svc.completeMultipartUpload(resp5);
		
		CompleteMultipartUploadRequest resp6 = utils.multipartUploadLLAPI(svc, bucket_name, key, 10 * 1024 * 1024, filePath);
		svc.completeMultipartUpload(resp6);
	}
	
	
	@Test(description = "multipart uploads for small file using LLAPI, succeeds!")
	public void testMultipartUploadSmallLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/file.mpg";
		long size = 5242880;
			
		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
		svc.completeMultipartUpload(resp);
		
	}
	
	@Test(description = "multipart uploads w/missing part using LLAPI, fails!")
	public void testMultipartUploadIncorrectMissingPartLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/file.mpg";
		long size = 5242880;
			
		List<PartETag> partETags = new ArrayList<PartETag>();

		InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucket_name, key);
		InitiateMultipartUploadResult initResponse = svc.initiateMultipartUpload(initRequest);

		File file = new File(filePath);
		long contentLength = file.length();
		long partSize = size;
		
		long filePosition = 0;
		for (int i = 1; filePosition < contentLength; i++) {
		    	
		   partSize = Math.min(partSize, (contentLength - filePosition));
		   UploadPartRequest uploadRequest = new UploadPartRequest()
				   .withBucketName(bucket_name).withKey(key)
		           .withUploadId(initResponse.getUploadId()).withPartNumber(i)
		           .withFileOffset(filePosition)
		           .withFile(file)
		           .withPartSize(partSize)
		           ;
		   svc.uploadPart(uploadRequest).setPartNumber(9999);
		   partETags.add((PartETag) svc.uploadPart(uploadRequest).getPartETag());

		   filePosition += partSize;
		}
		
		CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucket_name, key, 
                initResponse.getUploadId(), 
                (List<com.amazonaws.services.s3.model.PartETag>) partETags);

		try {
			
			svc.completeMultipartUpload(compRequest);
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "InvalidPart");
		}
		
	}
	
	
	@Test(description = "multipart uploads w/non existant upload using LLAPI, fails!")
	public void testAbortMultipartUploadNotFoundLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/file.mpg";
		long size = 5242880;
		
		try {
			
			svc.abortMultipartUpload( new AbortMultipartUploadRequest(bucket_name, key, "1"));
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchUpload");
		}
		
	}
	
	@Test(description = "multipart uploads abort using LLAPI, succeeds!")
	public void testAbortMultipartUploadLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/file.mpg";
		long size = 5242880;
		
		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, 5 * 1024 * 1024, filePath);
		svc.abortMultipartUpload( new AbortMultipartUploadRequest(bucket_name, key, resp.getUploadId()));
		
	}
	
	@Test(description = "multipart uploads overwrite using LLAPI, succeeds!")
	public void testMultipartUploadOverwriteExistingObjectLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/file.mpg";
		long size = 5242880;
		
		svc.putObject(bucket_name, key, "foo");
		
		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
		svc.completeMultipartUpload(resp);
		
		Assert.assertNotEquals(svc.getObjectAsString(bucket_name, key), "foo");
		
	}
	
	@Test(description = "multipart uploads for a very small file using LLAPI, fails!")
	public void testMultipartUploadFileTooSmallFileLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/sample.txt";
		long size = 5242880;
			
		try {
			
			CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
			svc.completeMultipartUpload(resp);
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "EntityTooSmall");
		}
		
	}
	
	@Test(description = "multipart copy for small file using LLAPI, succeeds!")
	public void testMultipartCopyMultipleSizesLLAPI() {
		
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String dir = "./data";
		String key = "key1";
		
		svc.createBucket(new CreateBucketRequest(src_bkt));
		svc.createBucket(new CreateBucketRequest(dst_bkt));
		
		String filePath = "./data/file.mpg";
		Upload upl = utils.UploadFileHLAPI(svc, src_bkt, key, filePath );
		Assert.assertEquals(upl.isDone(), true);
		
		
		CompleteMultipartUploadRequest resp = utils.multipartCopyLLAPI(svc, dst_bkt, key, src_bkt, key, 5 * 1024 * 1024);
		svc.completeMultipartUpload(resp);
		
		CompleteMultipartUploadRequest resp2 = utils.multipartCopyLLAPI(svc, dst_bkt, key, src_bkt, key, 5 * 1024 * 1024 + 100 * 1024);
		svc.completeMultipartUpload(resp2);
		
		CompleteMultipartUploadRequest resp3 = utils.multipartCopyLLAPI(svc, dst_bkt, key, src_bkt, key, 5 * 1024 * 1024 + 600 * 1024);
		svc.completeMultipartUpload(resp3);
		
		CompleteMultipartUploadRequest resp4 = utils.multipartCopyLLAPI(svc, dst_bkt, key, src_bkt, key, 10 * 1024 * 1024 + 100 * 1024);
		svc.completeMultipartUpload(resp4);
		
		CompleteMultipartUploadRequest resp5 = utils.multipartCopyLLAPI(svc, dst_bkt, key, src_bkt, key, 10 * 1024 * 1024 + 600 * 1024);
		svc.completeMultipartUpload(resp5);
		
		CompleteMultipartUploadRequest resp6 = utils.multipartCopyLLAPI(svc, dst_bkt, key, src_bkt, key, 10 * 1024 * 1024);
		svc.completeMultipartUpload(resp6);
		
	}
	
	
	@Test(description = "Upload of a  file using HLAPI, succeeds!")
	public void testUploadFileHLAPIBigFile() {
	
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = "./data/file.mpg";
		
		Upload upl = utils.UploadFileHLAPI(svc, bucket_name, key, filePath );
		
		Assert.assertEquals(upl.isDone(), true);
		
	}
	
	
	@Test(description = "Upload of a file to non existant bucket using HLAPI, fails!")
	public void testUploadFileHLAPINonExistantBucket() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		
		String filePath = "./data/sample.txt";
		
		try {
			Upload upl = utils.UploadFileHLAPI(svc, bucket_name, key, filePath );
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
		
	}
	
	@Test(description = "Multipart Upload for file using HLAPI, succeeds!")
	public void testMultipartUploadHLAPIAWS4() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
	
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String dir = "./data";
		
		Transfer upl = utils.multipartUploadHLAPI(svc, bucket_name, null, dir);
		
		Assert.assertEquals(upl.isDone(), true);
		
	}
	
	@Test(description = "Multipart Upload of a file to nonexistant bucket using HLAPI, fails!")
	public void testMultipartUploadHLAPINonEXistantBucket() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		
		String dir = "./data";
		
		try {
			
			Transfer upl = utils.multipartUploadHLAPI(svc, bucket_name, null, dir);
			
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
		
	}
	
	@Test(description = "Multipart Upload of a file with pause and resume using HLAPI, succeeds!")
	public void testMultipartUploadWithPause() throws AmazonServiceException, AmazonClientException, InterruptedException, IOException {
		
		String bucket_name = utils.getBucketName(prefix);
		
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String dir = "./data/file.mpg";
		String key = "key1";
		
		TransferManager tm = new TransferManager(svc);
		Upload myUpload = tm.upload(bucket_name, key, new File(dir));
		
		//pause upload
		long MB = 10;
		TransferProgress progress = myUpload.getProgress();
		while( progress.getBytesTransferred() < MB ) Thread.sleep(2000);
		boolean forceCancel = true;
		PauseResult<PersistableUpload> pauseResult = myUpload.tryPause(forceCancel);
		Assert.assertEquals(pauseResult.getPauseStatus().isPaused(), true);
		
		//persist PersistableUpload info to a file
		PersistableUpload persistableUpload = pauseResult.getInfoToResume();
		File f = new File("resume-upload");
		if( !f.exists() ) f.createNewFile();
		FileOutputStream fos = new FileOutputStream(f);
		persistableUpload.serialize(fos);
		fos.close(); 
		
		// Resume upload
		FileInputStream fis = new FileInputStream(new File("resume-upload"));
		PersistableUpload persistableUpload1 = PersistableTransfer.deserializeFrom(fis);
		tm.resumeUpload(persistableUpload1);
		fis.close();
		
	}
	
	@Test(description = "Multipart copy using HLAPI, succeeds!")
	public void testMultipartCopyHLAPIA() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String dir = "./data";
		String key = "key1";
		
		svc.createBucket(new CreateBucketRequest(src_bkt));
		svc.createBucket(new CreateBucketRequest(dst_bkt));
		
		String filePath = "./data/sample.txt";
		Upload upl = utils.UploadFileHLAPI(svc, src_bkt, key, filePath );
		Assert.assertEquals(upl.isDone(), true);
		
		Copy cpy = utils.multipartCopyHLAPI(svc, dst_bkt, key, src_bkt, key );
		Assert.assertEquals(cpy.isDone(), true);
	}
	
	@Test(description = "Multipart copy for file with non existant destination bucket using HLAPI, fails!")
	public void testMultipartCopyNoDSTBucketHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String dir = "./data";
		String key = "key1";
		
		svc.createBucket(new CreateBucketRequest(src_bkt));
		
		String filePath = "./data/sample.txt";
		Upload upl = utils.UploadFileHLAPI(svc, src_bkt, key, filePath );
		Assert.assertEquals(upl.isDone(), true);
		
		try {
			
			Copy cpy = utils.multipartCopyHLAPI(svc, dst_bkt, key, src_bkt, key );
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
		
	}
	
	@Test(description = "Multipart copy w/non existant source bucket using HLAPI, fails!")
	public void testMultipartCopyNoSRCBucketHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String dir = "./data";
		String key = "key1";
		
		svc.createBucket(new CreateBucketRequest(dst_bkt));
		
		try {
			
			Copy cpy = utils.multipartCopyHLAPI(svc, dst_bkt, key, src_bkt, key );
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "404 Not Found");
		}
	}
	
	@Test(description = "Multipart copy w/non existant source key using HLAPI, fails!")
	public void testMultipartCopyNoSRCKeyHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String dir = "./data";
		String key = "key1";
		
		svc.createBucket(new CreateBucketRequest(src_bkt));
		svc.createBucket(new CreateBucketRequest(dst_bkt));
		
		try {
			
			Copy cpy = utils.multipartCopyHLAPI(svc, dst_bkt, key, src_bkt, key );
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "404 Not Found");
		}
	}
	
	@Test(description = "Download using HLAPI, suceeds!")
	public void testDownloadHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		String key = "key1";
		
		String filePath = "./data/sample.txt";
		Upload upl = utils.UploadFileHLAPI(svc, bucket_name, key, filePath );
		Assert.assertEquals(upl.isDone(), true);
		
		Download download = utils.downloadHLAPI(svc, bucket_name, key, new File(filePath));
		Assert.assertEquals(download.isDone(), true);
		
	}
	
	@Test(description = "Download from non existant bucket using HLAPI, fails!")
	public void testDownloadNoBucketHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String filePath = "./data/sample.txt";
		
		try {
			
			Download download = utils.downloadHLAPI(svc, bucket_name, key, new File(filePath));
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "404 Not Found");
		}
		
	}
	
	@Test(description = "Download w/no key using HLAPI, suceeds!")
	public void testDownloadNoKeyHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		String key = "key1";
		
		String filePath = "./data/sample.txt";
		
		try {
			
			Download download = utils.downloadHLAPI(svc, bucket_name, key, new File(filePath));
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "404 Not Found");
		}
		
	}
	
	@Test(description = "Multipart Download using HLAPI, suceeds!")
	public void testMultipartDownloadHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		String key = "key1";
		String dstDir = "./downloads";
		
		String filePath = "./data/file.mpg";
		Upload upl = utils.UploadFileHLAPI(svc, bucket_name, key, filePath );
		Assert.assertEquals(upl.isDone(), true);
		
		MultipleFileDownload download = utils.multipartDownloadHLAPI(svc, bucket_name, key, new File(dstDir));
		Assert.assertEquals(download.isDone(), true);
	}
	
	@Test(description = "Multipart Download with pause and resume using HLAPI, suceeds!")
	public void testMultipartDownloadWithPauseHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException, IOException {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		String key = "key1";
		String filePath = "./data/file.mpg";
		
		TransferManager tm = new TransferManager(svc);
		
		Upload upl = utils.UploadFileHLAPI(svc, bucket_name, key, filePath );
		Assert.assertEquals(upl.isDone(), true);
		
		Download myDownload = tm.download(bucket_name, key, new File(filePath));

		long MB = 20;
		TransferProgress progress = myDownload.getProgress();
		while( progress.getBytesTransferred() < MB ) Thread.sleep(2000);

		// Pause the download and create file to store download info
		PersistableDownload persistableDownload = myDownload.pause();
		File f = new File("resume-download");
		if( !f.exists() ) f.createNewFile();
		FileOutputStream fos = new FileOutputStream(f);
		persistableDownload.serialize(fos);
		fos.close();
		
		//resume download
		FileInputStream fis = new FileInputStream(new File("resume-download"));
		PersistableDownload persistDownload = PersistableTransfer.deserializeFrom(fis);
		tm.resumeDownload(persistDownload);

		fis.close();
		
		
	}
	
	@Test(description = "Multipart Download from non existant bucket using HLAPI, fails!")
	public void testMultipartDownloadNoBucketHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		
		String key = "key1";
		String dstDir = "./downloads";
		
		try {
			
			MultipleFileDownload download = utils.multipartDownloadHLAPI(svc, bucket_name, key, new File(dstDir));
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
	}
	
	@Test(description = "Multipart Download w/no key using HLAPI, fails!")
	public void testMultipartDownloadNoKeyHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		svc.createBucket(new CreateBucketRequest(bucket_name));
		String key = "key1";
		String dstDir = "./downloads";
		
		
		try {
			
			MultipleFileDownload download = utils.multipartDownloadHLAPI(svc, bucket_name, key, new File(dstDir));
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "404 Not Found");
		}
	}
	
	@Test(description = "Upload of list of files using HLAPI, suceeds!")
	public void testUploadFileListHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		try {

			String bucket_name = utils.getBucketName(prefix);
			svc.createBucket(new CreateBucketRequest(bucket_name));
			String key = "key1";
			String dstDir = "./downloads";
			
			MultipleFileUpload upl= utils.UploadFileListHLAPI(svc, bucket_name, key);
			Assert.assertEquals(upl.isDone(), true);
			
			ObjectListing listing = svc.listObjects( bucket_name);
			List<S3ObjectSummary> summaries = listing.getObjectSummaries();
			while (listing.isTruncated()) {
			   listing = svc.listNextBatchOfObjects (listing);
			   summaries.addAll (listing.getObjectSummaries());
			}
			Assert.assertEquals(summaries.size(), 2);

		
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
		
	}
	
	
}
