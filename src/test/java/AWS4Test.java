import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.util.StringUtils;

public class AWS4Test {
	
	//To Do... provide singleton to these instances
	private static S3 utils =  new S3();
	AmazonS3 svc = utils.getAWS4CLI();
	String prefix = utils.getPrefix();
	static Properties prop = new Properties();
	
	@AfterMethod
	public  void tearDownAfterClass() throws Exception {
		
		utils.tearDown();	
	}

	@BeforeMethod
	public void setUp() throws Exception {
	}
	
	@Test
	//@Description("create w/x-amz-date after 9999, fails")
	public void testObjectCreateBadamzDateAfterEndAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "99990707T215304Z";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "RequestTimeTooSkewed");
		}
	}
	
	@Test
	//@Description("create w/date after 9999, fails")
	public void testObjectCreateBadDateAfterEndAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "Tue, 07 Jul 9999 21:53:04 GMT";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "RequestTimeTooSkewed");
		}
	}
	
	@Test
	//@Description("create w/x-amz-date before epoch, fails")
	public void testObjectCreateBadamzDateBeforeEpochAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "9500707T215304Z";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test
	//@Description("create w/date before epoch, suceeds")
	public void testObjectCreateBadDateBeforeEpochAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "Tue, 07 Jul 1950 21:53:04 GMT";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
	}
	
	@Test
	//@Description("create w/x-amz-date in future, fails")
	public void testObjectCreateBadAmzDateAfterTodayAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "20300707T215304Z";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "RequestTimeTooSkewed");
		}
	}
	
	@Test
	//@Description("create w/date in future, suceeds")
	public void testObjectCreateBadDateAfterToday4AWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "Tue, 07 Jul 2030 21:53:04 GMT";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		
	}
	
	@Test
	//@Description("create w/x-amz-date in the past, fails")
	public void testObjectCreateBadAmzDateBeforeTodayAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "20100707T215304Z";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "RequestTimeTooSkewed");
		}
	}
	
	@Test
	//@Description("create w/date in past, suceeds")
	public void testObjectCreateBadDateBeforeToday4AWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "Tue, 07 Jul 2010 21:53:04 GMT";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		
	}
	
	@Test
	//@Description("create w/no x-amz-date, fails")
	public void testObjectCreateBadAmzDateNoneAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "RequestTimeTooSkewed");
		}
	}
	
	@Test
	//@Description("create w/no date, suceeds")
	public void testObjectCreateBadDateNoneAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		
	}
	
	@Test
	//@Description("create w/non-graphic x-amz-date, fails")
	public void testObjectCreateBadamzDateUnreadableAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "\\x07";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test
	//@Description("create w/non-graphic date, fails")
	public void testObjectCreateBadDateUnreadableAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "\\x07";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "RequestTimeTooSkewed");
		}
	}
	
	@Test
	//@Description("create w/empty x-amz-date, fails")
	public void testObjectCreateBadamzDateEmptyAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test
	//@Description("create w/empty date, suceeds")
	public void testObjectCreateBadDateEmptyAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		
	}
	
	@Test
	//@Description("create w/invalid x-amz-date, fails")
	public void testObjectCreateBadamzDateInvalidAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "Bad date";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("X-Amz-Date", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test
	//@Description("create w/invalid date, suceeds")
	public void testObjectCreateBadDateInvalidAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "Bad date";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Date", value);
		
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		
	}
	
	@Test
	//@Description("create w/no user agent, fails")
	public void testObjectCreateBadUANoneAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("User-Agent", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test
	//@Description("create w/non-graphic user agent, fails")
	public void testObjectCreateBadUAUnreadableAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "\\x07";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("User-Agent", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test
	//@Description("create w/empty user agent, fails")
	public void testObjectCreateBadUAEmptyAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("User-Agent", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "SignatureDoesNotMatch");
		}
	}
	
	@Test
	//@Description("create w/invalid authorization, fails")
	public void testObjectCreateBadAuthorizationInvalidAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "AWS4-HMAC-SHA256 Credential=HAHAHA";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Authorization", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
	}
	
	@Test
	//@Description("create w/incorrect authorization, fails")
	public void testObjectCreateBadAuthorizationIncorrectAWS4() {
		
		String bucket_name = utils.getBucketName();
		String key = "key1";
		String content = "echo lima golf";
		String value = "AWS4-HMAC-SHA256 Credential=HAHAHA";
		
		svc.createBucket(new CreateBucketRequest(bucket_name));

		byte[] contentBytes = content.getBytes(StringUtils.UTF8);
		InputStream is = new ByteArrayInputStream(contentBytes);
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentBytes.length);
		metadata.setHeader("Authorization", value);
		
		try {
		svc.putObject(new PutObjectRequest(bucket_name, key, is, metadata));
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "400 Bad Request");
		}
	}

	@Test
	public void testMultipartUploadMultipleSizesLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		
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
	
	@Test
	public void testMultipartCopyMultipleSizesLLAPI() {
		
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String src_key = "key1";
		String dst_key = "key1";
		
		svc.createBucket(new CreateBucketRequest(src_bkt));
		svc.createBucket(new CreateBucketRequest(dst_bkt));
		
		CompleteMultipartUploadRequest completeRequest1 = utils.multipartCopy(svc, dst_bkt, dst_key, src_bkt, src_key, 5 * 1024 * 1024);
		svc.completeMultipartUpload(completeRequest1);
		
		CompleteMultipartUploadRequest completeRequest2 = utils.multipartCopy(svc, dst_bkt, dst_key, src_bkt, src_key, 5 * 1024 * 1024 + 100 * 1024);
		svc.completeMultipartUpload(completeRequest2);
		
		CompleteMultipartUploadRequest completeRequest3 = utils.multipartCopy(svc, dst_bkt, dst_key, src_bkt, src_key, 5 * 1024 * 1024 + 600 * 1024);
		svc.completeMultipartUpload(completeRequest3);
		
		CompleteMultipartUploadRequest completeRequest4 = utils.multipartCopy(svc, dst_bkt, dst_key, src_bkt, src_key, 10 * 1024 * 1024 + 100 * 1024);
		svc.completeMultipartUpload(completeRequest4);
		
		CompleteMultipartUploadRequest completeRequest5 = utils.multipartCopy(svc, dst_bkt, dst_key, src_bkt, src_key, 10 * 1024 * 1024 + 600 * 1024);
		svc.completeMultipartUpload(completeRequest5);
		
		CompleteMultipartUploadRequest completeRequest6 = utils.multipartCopy(svc, dst_bkt, dst_key, src_bkt, src_key, 10 * 1024 * 1024);
		svc.completeMultipartUpload(completeRequest6);
		
	}
	
	@Test
	public void testMultipartUploadEmptyLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		long size = 0;
		
		try {
			
			CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
			svc.completeMultipartUpload(resp);
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "MalformedXML");
		}
		
	}
	
	@Test
	public void testMultipartUploadSmallLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		long size = 5242880;
			
		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
		svc.completeMultipartUpload(resp);
		
	}
	
	@Test
	public void testMultipartUploadIncorrectEtagLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
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
		   svc.uploadPart(uploadRequest).setETag("ffffffffffffffffffffffffffffffff");
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
	
	@Test
	public void testMultipartUploadIncorrectMissingPartLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
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
	
	
	@Test
	public void testAbortMultipartUploadNotFoundLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		long size = 5242880;
		
		try {
			
			svc.abortMultipartUpload( new AbortMultipartUploadRequest(bucket_name, key, "1"));
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchUpload");
		}
			
		
		
	}
	
	@Test
	public void testAbortMultipartUploadLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		long size = 5242880;
		
		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
		svc.abortMultipartUpload( new AbortMultipartUploadRequest(bucket_name, key, "1"));
		
	}
	
	@Test
	public void testMultipartUploadOverwriteExistingObjectLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		long size = 5242880;
		
		svc.putObject(bucket_name, key, "foo");
		
		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
		svc.completeMultipartUpload(resp);
		
		Assert.assertNotEquals(svc.getObjectAsString(bucket_name, key), "foo");
		
	}
	
	@Test
	public void testMultipartUploadFileTooSmallFileLLAPI() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		long size = 5242880;
			
		try {
			
			CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPI(svc, bucket_name, key, size, filePath);
			svc.completeMultipartUpload(resp);
		}catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "EntityTooSmall");
		}
		
	}
	
	@Test
	public void testMultipartUploadFileHLAPIBigFile() {
	
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("bigFile");
		
		Upload upl = utils.multipartUploadFileHLAPI(svc, bucket_name, key, filePath );
		
		Assert.assertEquals(upl.isDone(), true);
		
	}
	
	@Test
	public void testMultipartUploadFileHLAPISmallFile() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		svc.createBucket(new CreateBucketRequest(bucket_name));
		
		String filePath = prop.getProperty("smallFile");
		
		Upload upl = utils.multipartUploadFileHLAPI(svc, bucket_name, key, filePath );
		
		Assert.assertEquals(upl.isDone(), true);
		
	}
	
	@Test
	public void testMultipartUploadFileHLAPINonExistantBucket() {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		
		String filePath = prop.getProperty("smallFile");
		
		try {
			Upload upl = utils.multipartUploadFileHLAPI(svc, bucket_name, key, filePath );
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
		
	}
	
	@Test
	public void testMultipartUploadDirectoryHLAPI() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		
		String filePath = prop.getProperty("smallFile");
		String s3dir = prop.getProperty("s3dir");
		
		Transfer upl = utils.multipartUploadDirectoryHLAPI(svc, bucket_name, s3dir, filePath);
		
		Assert.assertEquals(upl.isDone(), true);
		
	}
	
	@Test
	public void testMultipartUploadDirectoryHLAPINonExistantBucket() throws AmazonServiceException, AmazonClientException, InterruptedException {
		
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		
		String filePath = prop.getProperty("smallFile");
		String s3dir = prop.getProperty("s3dir");
		
		try {

			Transfer upl = utils.multipartUploadDirectoryHLAPI(svc, bucket_name, s3dir, filePath);
		} catch (AmazonServiceException err) {
			AssertJUnit.assertEquals(err.getErrorCode(), "NoSuchBucket");
		}
		
	}
	
	
	
}
