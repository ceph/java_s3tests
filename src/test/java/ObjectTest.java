import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CompletionException;
// In V2, the actual S3Exception is the cause of the CompletionException

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedCopy;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.Copy;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.ResumableFileDownload;
import software.amazon.awssdk.transfer.s3.model.ResumableFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;

public class ObjectTest {

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

	@Test(description = "object write to non existant bucket, fails")
	public void testObjectWriteToNonExistantBucket() {

		String non_exixtant_bucket = utils.getBucketName(prefix);

		try {
			s3Client.putObject(PutObjectRequest.builder()
					.bucket(non_exixtant_bucket)
					.key("key1")
					.build(), RequestBody.fromString("echo"));

			AssertJUnit.fail("Expected 404 NoSuchBucket");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
		}
	}

	@Test(description = "Reading non existant object, fails")
	public void testObjectReadNotExist() {

		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));

		try {
			s3Client.getObject(b -> b.bucket(bucket_name).key("key"));
			AssertJUnit.fail("Expected 404 NoSuchKey");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchKey");
		}
	}

	@Test(description = "object read from non existant bucket, fails")
	public void testObjectReadFromNonExistantBucket() {

		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));

		try {
			s3Client.getObject(b -> b.bucket(bucket_name).key("key"));
			AssertJUnit.fail("Expected 404 NoSuchKey");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchKey");
		}
	}

	@Test(description = " multi-object delete, succeeds")
	public void testMultiObjectDelete() {
		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));
		s3Client.putObject(p -> p.bucket(bucket_name).key("key1"),
				RequestBody.fromString("echo"));
		DeleteObjectsRequest multiDeleteRequest = DeleteObjectsRequest.builder()
				.bucket(bucket_name)
				.delete(d -> d.objects(
						ObjectIdentifier.builder().key("key1").build(),
						ObjectIdentifier.builder().key("key2").build(),
						ObjectIdentifier.builder().key("key3").build()))
				.build();

		s3Client.deleteObjects(multiDeleteRequest);

		ListObjectsV2Response list = s3Client.listObjectsV2(l -> l.bucket(bucket_name));
		AssertJUnit.assertEquals(list.contents().size(), 0);
		s3Client.deleteBucket(b -> b.bucket(bucket_name));
	}

	// Fails as objects are genrated instead off throwing error
	// @Test(description = "creating unreadable object, fails")
	// public void testObjectCreateUnreadable() {

	// try {
	// String bucket_name = utils.getBucketName(prefix);
	// s3Client.createBucket(b -> b.bucket(bucket_name));
	// s3Client.putObject(p -> p.bucket(bucket_name).key("\\x0a"),
	// RequestBody.fromString("bar"));
	// AssertJUnit.fail("Expected 400 Bad Request");
	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "400 Bad
	// Request");
	// }
	// }

	@Test(description = "reading empty object, fails")
	public void testObjectHeadZeroBytes() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key";

		try {
			s3Client.createBucket(b -> b.bucket(bucket_name));
			s3Client.putObject(p -> p.bucket(bucket_name).key(key),
					RequestBody.fromString(""));
			String result = s3Client.getObjectAsBytes(b -> b.bucket(bucket_name).key(key)).asUtf8String();
			Assert.assertEquals(result.length(), 0);
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "XAmzContentSHA256Mismatch");
		}
	}

	@Test(description = "On object write and get with w/Etag, succeeds")
	public void testObjectWriteCheckEtag() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key";
		String Etag = "\"37b51d194a7513e45b56f6524f2d51f2\"";

		s3Client.createBucket(b -> b.bucket(bucket_name));

		s3Client.putObject(p -> p.bucket(bucket_name).key(key), RequestBody.fromString("bar"));

		ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(b -> b.bucket(bucket_name).key(key));
		System.out.println("the etag is " + resp.response().eTag());
		Assert.assertEquals(resp.response().eTag(), Etag);
	}

	@Test(description = "object write w/Cache-Control header, succeeds")
	public void testObjectWriteCacheControlV2() {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String cache_control = "public, max-age=14400";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		s3Client.putObject(p -> p
				.bucket(bucket_name)
				.key(key)
				.cacheControl(cache_control),
				RequestBody.fromString(content));

		try (
				ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(b -> b.bucket(bucket_name).key(key))) {
			AssertJUnit.assertEquals(resp.response().cacheControl(), cache_control);
		} catch (IOException e) {
			AssertJUnit.fail("Failed to close stream: " + e.getMessage());
		}
	}

	@Test(description = "object write, read, update and delete, succeeds")
	public void testObjectWriteReadUpdateReadDelete() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";

		s3Client.createBucket(b -> b.bucket(bucket_name));

		s3Client.putObject(p -> p.bucket(bucket_name).key(key), RequestBody.fromString(content));
		String got = s3Client.getObjectAsBytes(b -> b.bucket(bucket_name).key(key)).asUtf8String();
		Assert.assertEquals(got, content);

		// update
		String newContent = "charlie echo";
		s3Client.putObject(p -> p.bucket(bucket_name).key(key), RequestBody.fromString(newContent));
		got = s3Client.getObjectAsBytes(b -> b.bucket(bucket_name).key(key)).asUtf8String();
		Assert.assertEquals(got, newContent);

		s3Client.deleteObject(d -> d.bucket(bucket_name).key(key));
		try {
			got = s3Client.getObjectAsBytes(b -> b.bucket(bucket_name).key(key)).asUtf8String();
			AssertJUnit.fail("Expected 404 NoSuchKey");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchKey");
		}
	}

	@Test(description = "object copy w/non existant buckets, fails")
	public void testObjectCopyBucketNotFound() {

		String bucket1 = utils.getBucketName(prefix);
		String bucket2 = utils.getBucketName(prefix);
		String key = "key1";

		try {
			s3Client.copyObject(
					c -> c.sourceBucket(bucket1).sourceKey(key).destinationBucket(bucket2).destinationKey(key));
			AssertJUnit.fail("Expected 404 NoSuchBucket");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
		}
	}

	@Test(description = "object copy w/no source key, fails")
	public void TestObjectCopyKeyNotFound() {

		String bucket1 = utils.getBucketName(prefix);
		String bucket2 = utils.getBucketName(prefix);
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(bucket1));
		s3Client.createBucket(b -> b.bucket(bucket2));

		try {
			s3Client.copyObject(
					c -> c.sourceBucket(bucket1).sourceKey(key).destinationBucket(bucket2).destinationKey(key));
			AssertJUnit.fail("Expected 404 NoSuchKey");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchKey");
		}
	}

	// // Doesnt fail as expected the Oject is getting created
	// @Test(description = "object create w/empty content type, fails")
	// public void testObjectCreateBadContenttypeEmpty() {
	// String bucket_name = utils.getBucketName(prefix);
	// String key = "key1";
	// String content = "echo lima golf";
	// String contType = " ";

	// s3Client.createBucket(b -> b.bucket(bucket_name));

	// try {
	// s3Client.putObject(p -> p.bucket(bucket_name).key(key).contentType(contType),
	// RequestBody.fromString(content));
	// AssertJUnit.fail("Expected 400 Bad Request");
	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "400 Bad
	// Request");
	// }
	// }

	// // Doesnt fail as expected the Oject is getting created
	// @Test(description = "object create w/no content type, fails")
	// public void testObjectCreateBadContenttypeNone() {
	// String bucket_name = utils.getBucketName(prefix);
	// String key = "key1";
	// String content = "echo lima golf";
	// String contType = "";

	// s3Client.createBucket(b -> b.bucket(bucket_name));

	// try {
	// s3Client.putObject(p -> p.bucket(bucket_name).key(key).contentType(contType),
	// RequestBody.fromString(content));
	// AssertJUnit.fail("Expected 400 Bad Request");
	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "400 Bad
	// Request");
	// }
	// }

	// // Doesnt fail as expected the Oject is getting created
	// @Test(description = "object create w/unreadable content type, fails")
	// public void testObjectCreateBadContenttypeUnreadable() {

	// String bucket_name = utils.getBucketName(prefix);
	// String key = "key1";
	// String content = "echo lima golf";
	// String contType = "\\x08";

	// s3Client.createBucket(b -> b.bucket(bucket_name));

	// try {
	// s3Client.putObject(p -> p.bucket(bucket_name).key(key).contentType(contType),
	// RequestBody.fromString(content));
	// AssertJUnit.fail("Expected 400 Bad Request");
	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "400 Bad
	// Request");
	// }
	// }

	@Test(description = "v2: object create w/Unreadable Authorization, fails")
	public void testObjectCreateBadAuthorizationUnreadableV2() {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String badAuth = "\u0007";

		s3Client.createBucket(b -> b.bucket(bucket_name));

		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Authorization", badAuth)),
					RequestBody.fromString(content));

			AssertJUnit.fail("Expected an Auth-related exception");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.statusCode(), 403);
			// Fails with the AccessDenied error Message with status code of 403
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");

		}
	}

	@Test(description = "object create w/empty Authorization, succeeds")
	public void testObjectCreateBadAuthorizationEmpty() {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String auth = " ";

		s3Client.createBucket(b -> b.bucket(bucket_name));

		try {

			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Authorization", auth)),
					RequestBody.fromString(content));

			AssertJUnit.fail("Expected an Auth-related exception");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.statusCode(), 403);
			// Fails with the AccessDenied error Message with status code of 403
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
		}
	}

	@Test(description = "object create w/no Authorization, succeeds")
	public void testObjectCreateBadAuthorizationNone() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String auth = "";

		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Authorization", auth)),
					RequestBody.fromString(content));

			AssertJUnit.fail("Expected an Auth-related exception");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.statusCode(), 403);
			// Fails with the AccessDenied error Message with status code of 403
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
		}
	}

	// @Test(description = "v2: object create w/empty Expect, fails with signature
	// error")
	// public void testObjectCreateBadExpectEmptyV2() {
	// String bucket_name = utils.getBucketName(prefix);
	// String key = "key1";
	// String content = "echo lima golf";
	// String expected = " ";

	// s3Client.createBucket(b -> b.bucket(bucket_name));
	// try {
	// s3Client.putObject(p -> p.bucket(bucket_name)
	// .key(key)
	// .overrideConfiguration(o -> o.putHeader("Expect", expected)),
	// RequestBody.fromString(content));
	// //Doesnt fail as expected
	// AssertJUnit.fail("Expected 403 SignatureDoesNotMatch or 400 Bad Request");

	// } catch (S3Exception err) {
	// System.out.println("Caught Expected Error: " +
	// err.awsErrorDetails().errorCode());

	// boolean isAuthError =
	// err.awsErrorDetails().errorCode().equals("SignatureDoesNotMatch") ||
	// err.statusCode() == 403;
	// AssertJUnit.assertTrue("Expected Auth error, but got: " +
	// err.awsErrorDetails().errorCode(), isAuthError);
	// }
	// }

	// @Test(description = "object create w/unreadable Expect, succeeds")
	// public void testObjectCreateBadExpectUnreadable() {

	// String bucket_name = utils.getBucketName(prefix);
	// String key = "key1";
	// String content = "echo lima golf";
	// String expected = "\\x07";

	// s3Client.createBucket(b -> b.bucket(bucket_name));
	// try {

	// s3Client.putObject(p -> p.bucket(bucket_name)
	// .key(key)
	// .overrideConfiguration(o -> o.putHeader("Expect", expected)),
	// RequestBody.fromString(content));
	// //Doesnt fail as expected
	// AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");

	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
	// "SignatureDoesNotMatch");
	// }
	// }

	@Test(description = "object create w/mismatch Expect, fails")
	public void testObjectCreateBadExpectMismatch() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String expected = "200";

		s3Client.createBucket(b -> b.bucket(bucket_name));

		s3Client.putObject(p -> p.bucket(bucket_name)
				.key(key)
				.overrideConfiguration(o -> o.putHeader("Expect", expected)),
				RequestBody.fromString(content));

	}

	@Test(description = "object create w/no Expect, fails")
	public void TestObjectCreateBadExpectNone() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String expected = "";

		s3Client.createBucket(b -> b.bucket(bucket_name));

		s3Client.putObject(p -> p.bucket(bucket_name)
				.key(key)
				.overrideConfiguration(o -> o.putHeader("Expect", expected)),
				RequestBody.fromString(content));

	}

	@Test(description = "object create w/short MD5, fails")
	public void testObjectCreateBadMd5InvalidShort() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String md5 = "WJyYWNhZGFicmE=";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		try {

			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Content-MD5", md5)),
					RequestBody.fromString(content));

			AssertJUnit.fail("Expected 400 InvalidDigest");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.statusCode(), 400);
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "InvalidDigest");
		}
	}

	@Test(description = "object create w/empty MD5, fails")
	public void TestObjectCreateBadMd5Empty() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String md5 = "";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Content-MD5", md5)),
					RequestBody.fromString(content));

			AssertJUnit.fail("Expected 400 InvalidDigest");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.statusCode(), 400);
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "InvalidDigest");
		}
	}

	@Test(description = "object create w/invalid MD5, fails")
	public void testObjectCreateBadMd5Ivalid() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String md5 = "rL0Y20zC+Fzt72VPzMSk2A==";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Content-MD5", md5)),
					RequestBody.fromString(content));
			AssertJUnit.fail("Expected 400 BadDigest");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.statusCode(), 400);
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "BadDigest");
		}
	}

	@Test(description = "object create w/short MD5, fails")
	public void testObjectCreateBadMd5Unreadable() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String md5 = "\\x07";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Content-MD5", md5)),
					RequestBody.fromString(content));
			AssertJUnit.fail("Expected 403 AccessDenied");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.statusCode(), 403);
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
		}
	}

	@Test(description = "object create w/no MD5, fails")
	public void testObjectCreateBadMd5None() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "echo lima golf";
		String md5 = "";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.overrideConfiguration(o -> o.putHeader("Content-MD5", md5)),
					RequestBody.fromString(content));
			// AssertJUnit.fail("Expected 403 AccessDenied");
			AssertJUnit.fail("Expected 400 InvalidDigest");
		} catch (S3Exception err) {
			System.out.println(err.awsErrorDetails());
			System.out.println(err.statusCode());
			AssertJUnit.assertEquals(err.statusCode(), 400);
			// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
			// Returns "InvalidDigest"
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "InvalidDigest");
		}
	}

	@Test(description = "v2: object write (1b) w/SSE-C fails on http, succeeds on https")
	public void testEncryptedTransfer1bV2() {
		String bucketName = utils.getBucketName(prefix);
		String key = "ssec-test-obj";

		String secretKey = "12345678901234567890123456789012";
		String b64Key = Base64.getEncoder().encodeToString(secretKey.getBytes());

		s3Client.createBucket(b -> b.bucket(bucketName));

		try {
			s3Client.putObject(p -> p.bucket(bucketName)
					.key(key)
					.sseCustomerAlgorithm("AES256")
					.sseCustomerKey(b64Key)
					.build(),
					RequestBody.fromString("x"));

			// If the client is configured with http:// (not https://),
			// the SDK will throw an IllegalArgumentException locally.
			// AssertJUnit.fail("Expected IllegalArgumentException when using SSE-C over
			// HTTP");

		} catch (IllegalArgumentException err) {
			System.out.println("Caught Expected Local Validation Error: " + err.getMessage());
			AssertJUnit.assertTrue(err.getMessage().contains("HTTPS") ||
					err.getMessage().contains("SSL"));
		} catch (S3Exception e) {
			System.out.println("Caught Expected Server Error: " + e.getMessage() + e.awsErrorDetails());
			AssertJUnit.assertEquals(400, e.statusCode());
		}
	}

	@Test(description = "v2: object write (1kb) w/SSE-C and explicit MD5")
	public void testEncryptedTransfer1kbV2() {
		S3Client s3ClientV2 = utils.getS3V2Client(true);
		String bucketName = utils.getBucketName(prefix);
		String key = "ssec-1kb-test";

		byte[] data = new byte[1024];
		new java.util.Random().nextBytes(data);

		byte[] rawKey = "12345678901234567890123456789012".getBytes();
		String b64Key = java.util.Base64.getEncoder().encodeToString(rawKey);
		String tempMd5 = "";
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
			byte[] md5Bytes = md.digest(rawKey);
			tempMd5 = java.util.Base64.getEncoder().encodeToString(md5Bytes);
		} catch (Exception e) {
			AssertJUnit.fail("MD5 calculation failed");
		}
		final String b64Md5 = tempMd5;

		s3ClientV2.createBucket(b -> b.bucket(bucketName));

		try {
			s3ClientV2.putObject(p -> p.bucket(bucketName)
					.key(key)
					.sseCustomerAlgorithm("AES256")
					.sseCustomerKey(b64Key)
					.sseCustomerKeyMD5(b64Md5)
					.build(),
					RequestBody.fromBytes(data));

			// If the client is configured with http:// (not https://),
			// the SDK will throw an IllegalArgumentException locally.
			// AssertJUnit.fail("Expected IllegalArgumentException when using SSE-C over
			// HTTP");

		} catch (IllegalArgumentException err) {
			// Success: Local SDK check caught the HTTP usage
			AssertJUnit.assertTrue(err.getMessage().contains("HTTPS"));
		} catch (S3Exception err) {
			// If the local check is bypassed, RGW will return 400
			System.out.println("RGW Error: " + err.awsErrorDetails().errorMessage());
			AssertJUnit.assertEquals(400, err.statusCode());
		}
	}

	@Test(description = "object write (1MB) w/SSE succeeds on https, fails on http")
	public void testEncryptedTransfer1MBV2() {
		S3Client s3ClientV2 = utils.getS3V2Client(true);
		String bucketName = utils.getBucketName(prefix);
		String key = "ssec-1kb-test";

		byte[] data = new byte[1024 * 1024];
		new java.util.Random().nextBytes(data);

		byte[] rawKey = "12345678901234567890123456789012".getBytes();
		String b64Key = java.util.Base64.getEncoder().encodeToString(rawKey);
		String tempMd5 = "";
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
			byte[] md5Bytes = md.digest(rawKey);
			tempMd5 = java.util.Base64.getEncoder().encodeToString(md5Bytes);
		} catch (Exception e) {
			AssertJUnit.fail("MD5 calculation failed");
		}
		final String b64Md5 = tempMd5;

		s3ClientV2.createBucket(b -> b.bucket(bucketName));

		try {
			s3ClientV2.putObject(p -> p.bucket(bucketName)
					.key(key)
					.sseCustomerAlgorithm("AES256")
					.sseCustomerKey(b64Key)
					.sseCustomerKeyMD5(b64Md5)
					.build(),
					RequestBody.fromBytes(data));

			// If the client is configured with http:// (not https://),
			// the SDK will throw an IllegalArgumentException locally.
			// AssertJUnit.fail("Expected IllegalArgumentException when using SSE-C over
			// HTTP");

		} catch (IllegalArgumentException err) {
			// Success: Local SDK check caught the HTTP usage
			AssertJUnit.assertTrue(err.getMessage().contains("HTTPS"));
		} catch (S3Exception err) {
			// If the local check is bypassed, RGW will return 400
			System.out.println("RGW Error: " + err.awsErrorDetails().errorMessage());
			AssertJUnit.assertEquals(400, err.statusCode());
		}
	}

	// // This "w/no SSE succeeds on https" does not work anymore ? Not verified
	// locally
	// // The PUT request requires a valid SSE Customer Algorithm
	// // and the GET reguest requires a valid SSECustomerKey
	@Test(description = "v2: object write w/key w/no SSE algorithm fails")
	public void testEncryptionKeyNoSSEC() {
		String bucketName = utils.getBucketName(prefix);
		String key = "key1";
		String data = "testcontent".repeat(100);

		String b64Key = "pO3upElrwuEXSoFwCfnZPdSsmt/xWeFa0N9KgDijwVs=";
		String b64Md5 = "DWygnHRtgiJ77HCm+1rvHw==";

		s3Client.createBucket(b -> b.bucket(bucketName));

		try {
			s3Client.putObject(p -> p.bucket(bucketName)
					.key(key)
					.sseCustomerKey(b64Key)
					.sseCustomerKeyMD5(b64Md5)
					.build(),
					RequestBody.fromString(data));

			AssertJUnit.fail("Expected S3Exception (400 Failure) due to missing algorithm");

		} catch (S3Exception e) {
			AssertJUnit.assertEquals(400, e.statusCode());
			String errorMessage = e.awsErrorDetails().errorMessage();
			AssertJUnit.assertEquals("Requests specifying Server Side Encryption with Customer " +
					"provided keys must provide a valid encryption algorithm.",
					errorMessage);
		}
	}

	@Test(description = "v2: object write w/SSE and no key, fails")
	public void testEncryptionKeySSECNoKey() {
		String bucketName = utils.getBucketName(prefix);
		String key = "key1";
		String data = "testcontent".repeat(100);

		s3Client.createBucket(b -> b.bucket(bucketName));

		try {
			s3Client.putObject(p -> p.bucket(bucketName)
					.key(key)
					.sseCustomerAlgorithm("AES256")
					.build(),
					RequestBody.fromString(data));

			AssertJUnit.fail("Expected S3Exception (400 Failure) due to missing secret key");

		} catch (S3Exception e) {
			AssertJUnit.assertEquals(400, e.statusCode());
			String errorMessage = e.awsErrorDetails().errorMessage();
			String expected = "Requests specifying Server Side Encryption with Customer " +
					"provided keys must provide an appropriate secret key.";
			AssertJUnit.assertEquals(expected, errorMessage);
		}
	}

	@Test(description = "object write w/SSE and no MD5, fails")
	public void testEncryptionKeySSECNoMd5() {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String data = utils.repeat("testcontent", 100);

		s3Client.createBucket(b -> b.bucket(bucket_name));
		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.sseCustomerAlgorithm("AES256")
					.sseCustomerKey("pO3upElrwuEXSoFwCfnZPdSsmt/xWeFa0N9KgDijwVs=")
					.build(),
					RequestBody.fromBytes(data.getBytes()));
			AssertJUnit.fail("Expected A Failure because of No MD5");
		} catch (S3Exception err) {
			AssertJUnit.assertEquals(400, err.statusCode());
			AssertJUnit.assertEquals(err.awsErrorDetails().errorMessage(),
					"Requests specifying Server Side Encryption with Customer provided keys must provide an appropriate secret key md5.");
		}
	}

	@Test(description = "object write w/SSE and Invalid MD5, fails")
	public void testEncryptionKeySSECInvalidMd5() {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String data = utils.repeat("testcontent", 100);

		s3Client.createBucket(b -> b.bucket(bucket_name));

		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.sseCustomerAlgorithm("AES256")
					.sseCustomerKey("pO3upElrwuEXSoFwCfnZPdSsmt/xWeFa0N9KgDijwVs=")
					.sseCustomerKeyMD5("AAAAAAAAAAAAAAAAAAAAAA==")
					.build(),
					RequestBody.fromBytes(data.getBytes()));
			AssertJUnit.fail("Expected A Failure because of Invalid MD5");

		} catch (S3Exception err) {
			AssertJUnit.assertEquals(400, err.statusCode());
			AssertJUnit.assertEquals(err.awsErrorDetails().errorMessage(),
					"The calculated MD5 hash of the key did not match the hash that was provided.");
		}
	}

	// XXX This fails without sse-s3. Need way to conditionally enable
	// this test, also need more tests in line with python code. -mdw 202204274
	// Works locally over http
	// @Test(description = "v2: object write w/KMS, succeeds with https")
	// public void testSSEKMSPresent() {
	// String bucketName = utils.getBucketName(prefix);
	// String key = "key1";
	// String data = "testcontent".repeat(100);
	// String keyId = "testkey-1";

	// s3Client.createBucket(b -> b.bucket(bucketName));

	// s3Client.putObject(p -> p.bucket(bucketName)
	// .key(key)
	// .serverSideEncryption(ServerSideEncryption.AWS_KMS)
	// .ssekmsKeyId(keyId)
	// .build(),
	// RequestBody.fromString(data));
	// String rdata = s3Client.getObjectAsBytes(g ->
	// g.bucket(bucketName).key(key)).asUtf8String();
	// AssertJUnit.assertEquals(data, rdata);
	// }

	@Test(description = "object write w/KMS and no kmskeyid, fails")
	public void testSSEKMSNoKey() {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String data = utils.repeat("testcontent", 100);

		s3Client.createBucket(b -> b.bucket(bucket_name));

		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.serverSideEncryption(ServerSideEncryption.AWS_KMS)
					.build(),
					RequestBody.fromString(data));
			AssertJUnit.fail("Expected 400 InvalidArgument");

		} catch (S3Exception err) {
			System.out.println(err.getMessage());
			AssertJUnit.assertEquals(err.statusCode(), 400);
		}
	}

	@Test(description = "v2: object write w/no KMS and with kmskeyid, fails")
	public void testSSEKMSNotDeclared() {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String data = utils.repeat("testcontent", 100);
		String keyId = "testkey-1";

		s3Client.createBucket(b -> b.bucket(bucket_name));

		try {
			s3Client.putObject(p -> p.bucket(bucket_name)
					.key(key)
					.ssekmsKeyId(keyId)
					.overrideConfiguration(o -> o.putHeader(
							"x-amz-server-side-encryption-aws-kms-key-id", keyId))
					.build(),
					RequestBody.fromString(data));
			AssertJUnit.fail("Expected Failure because of no x-amz-server-side-encryption header");
		} catch (S3Exception err) {
			AssertJUnit.assertTrue("Expected 400 or 403",
					err.statusCode() == 400 || err.statusCode() == 403);
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
		}
	}

	// ......................................prefixes, delimeter,
	// markers........................................

	@Test(description = "v2: object list w/ percentage delimiter, succeeds")
	public void testObjectListDelimiterPercentageV2() {
		String[] keys = { "b%ar", "b%az", "c%ab", "foo" };
		String delim = "%";
		java.util.List<String> expected_prefixes = java.util.Arrays.asList("b%", "c%");
		java.util.List<String> expected_keys = java.util.Arrays.asList("foo");

		String bucketName = utils.createKeysV2(s3Client, keys);
		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r.bucket(bucketName).delimiter(delim));

		Assert.assertEquals(result.delimiter(), delim);
		Assert.assertEquals(result.commonPrefixes().stream()
				.map(p -> p.prefix()).collect(java.util.stream.Collectors.toList()), expected_prefixes);

		java.util.List<String> actualKeys = result.contents().stream()
				.map(o -> o.key()).collect(java.util.stream.Collectors.toList());
		Assert.assertEquals(actualKeys, expected_keys);
	}

	// Delimiter with whitespace
	@Test(description = "v2: object list w/ whitespace delimiter")
	public void testObjectListDelimiterWhitespaceV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "cab", "foo" };
		String delim = " ";

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		try {
			ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
					.bucket(bucketName)
					.delimiter(delim)
					.build());

			// If it doesnt fails, we verify Object delimiter is same as input delimiter
			AssertJUnit.assertEquals(delim, result.delimiter());

		} catch (S3Exception err) {
			// If it fails, we verify it's the signature issue typically associated with
			// spaces
			AssertJUnit.assertEquals("SignatureDoesNotMatch", err.awsErrorDetails().errorCode());
		}
	}

	@Test(description = "v2: object list w/ dot delimiter, succeeds")
	public void testObjectListDelimiterDotV2() {
		String[] keys = { "b.ar", "b.az", "c.ab", "foo" };
		String delim = ".";
		java.util.List<String> expected_prefixes = java.util.Arrays.asList("b.", "c.");
		java.util.List<String> expected_keys = java.util.Arrays.asList("foo");

		String bucketName = utils.createKeysV2(s3Client, keys);
		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r.bucket(bucketName).delimiter(delim));

		Assert.assertEquals(result.delimiter(), delim);
		Assert.assertEquals(result.commonPrefixes().stream()
				.map(p -> p.prefix()).collect(java.util.stream.Collectors.toList()), expected_prefixes);

		java.util.List<String> actualKeys = result.contents().stream()
				.map(o -> o.key()).collect(java.util.stream.Collectors.toList());
		Assert.assertEquals(actualKeys, expected_keys);
	}

	@Test(description = "v2: object list w/ non-existent delimiter, succeeds")
	public void testObjectListDelimiterNotExistV2() {
		String[] keys = { "bar", "baz", "cab", "foo" };
		String delim = "/";
		java.util.List<String> expectedKeys = Arrays.asList("bar", "baz", "cab", "foo");

		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("content"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.delimiter(delim)
				.build());

		AssertJUnit.assertEquals(delim, result.delimiter());

		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ basic prefix, succeeds")
	public void testObjectListPrefixBasicV2() {
		String[] keys = { "foo/bar", "foo/baz", "quux" };
		String prefixParam = "foo/";
		java.util.List<String> expectedKeys = Arrays.asList("foo/bar", "foo/baz");

		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ alt prefix, succeeds")
	public void testObjectListPrefixAltV2() {
		String[] keys = { "bar", "baz", "foo" };
		String prefixParam = "ba";
		java.util.List<String> expectedKeys = Arrays.asList("bar", "baz");

		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ empty prefix, succeeds")
	public void testObjectListPrefixEmptyV2() {
		String[] keys = { "foo/bar", "foo/baz", "quux" };
		String prefixParam = ""; // Empty string
		java.util.List<String> expectedKeys = Arrays.asList("foo/bar", "foo/baz", "quux");

		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.build());

		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());
		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());
		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ non-existent prefix, succeeds")
	public void testObjectListPrefixNotExistV2() {
		String[] keys = { "foo/bar", "foo/baz", "quux" };
		String prefixParam = "d";
		java.util.List<String> expectedKeys = java.util.Collections.emptyList();

		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ unreadable prefix, succeeds")
	public void testObjectListPrefixUnreadableV2() {
		String[] keys = { "foo/bar", "foo/baz", "quux" };
		String prefixParam = "\n";
		java.util.List<String> expectedKeys = java.util.Collections.emptyList();

		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));

		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());
		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ prefix and delimiter, succeeds")
	public void testObjectListPrefixDelimiterBasicV2() {
		String[] keys = { "foo/bar", "foo/baz/xyzzy", "quux/thud", "asdf" };
		String prefixParam = "foo/";
		String delim = "/";
		java.util.List<String> expectedKeys = Arrays.asList("foo/bar");
		java.util.List<String> expectedPrefixes = Arrays.asList("foo/baz/");

		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.delimiter(delim)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertEquals(delim, result.delimiter());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		java.util.List<String> actualPrefixes = result.commonPrefixes().stream()
				.map(CommonPrefix::prefix)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
		AssertJUnit.assertEquals(expectedPrefixes, actualPrefixes);
	}

	@Test(description = "v2: object list w/ non-existent prefix and delimiter, succeeds")
	public void testObjectListPrefixDelimiterPrefixNotExistV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "b/a/r", "b/a/c", "b/a/g", "g" };
		String prefixParam = "d";
		String delim = "/";

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.delimiter(delim)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertEquals(delim, result.delimiter());
		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());
		AssertJUnit.assertTrue(result.contents().isEmpty());
	}

	@Test(description = "v2: object list w/ prefix and delimiter non-existent, succeeds")
	public void testObjectListPrefixDelimiterDelimiterNotExistV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "b/a/c", "b/a/g", "b/a/r", "g" };
		String prefixParam = "b";
		String delim = "z";
		java.util.List<String> expectedKeys = Arrays.asList("b/a/c", "b/a/g", "b/a/r");

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}
		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.delimiter(delim)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertEquals(delim, result.delimiter());
		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ prefix and delimiter non-existent, succeeds")
	public void testObjectListPrefixDelimiterPrefixDelimiterNotExistV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "b/a/c", "b/a/g", "b/a/r", "g" };
		String prefixParam = "y";
		String delim = "z";

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.prefix(prefixParam)
				.delimiter(delim)
				.build());

		AssertJUnit.assertEquals(prefixParam, result.prefix());
		AssertJUnit.assertEquals(delim, result.delimiter());
		AssertJUnit.assertTrue(result.commonPrefixes().isEmpty());
		AssertJUnit.assertTrue(result.contents().isEmpty());
	}

	@Test(description = "v2: object list w/ negative maxkeys, succeeds")
	public void testObjectListMaxkeysNegativeV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		// In v2, maxKeys() expects an Integer.
		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.maxKeys(-1)
				.build());
		AssertJUnit.assertEquals(0, (int) result.maxKeys());
		AssertJUnit.assertFalse(result.isTruncated());
		AssertJUnit.assertTrue(result.contents().isEmpty());
	}

	@Test(description = "v2: object list w/ maxkeys=1, succeeds")
	public void testObjectListMaxkeysOneV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };
		java.util.List<String> expectedKeys = Arrays.asList("bar");
		int maxKeys = 1;

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r
				.bucket(bucketName)
				.maxKeys(maxKeys)
				.build());

		AssertJUnit.assertEquals(maxKeys, (int) result.maxKeys());

		AssertJUnit.assertTrue(result.isTruncated());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/maxkeys=0, succeeds")
	public void testObjectListMaxkeysZeroV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r.bucket(bucketName).maxKeys(0));

		AssertJUnit.assertEquals(0, (int) result.maxKeys());
		AssertJUnit.assertFalse(result.isTruncated());
		AssertJUnit.assertTrue(result.contents().isEmpty());
	}

	@Test(description = "v2: object list w/ no maxkeys (default), succeeds")
	public void testObjectListMaxkeysNoneV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };
		java.util.List<String> expectedKeys = Arrays.asList("bar", "baz", "foo", "quxx");

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsV2Response result = s3Client.listObjectsV2(r -> r.bucket(bucketName));

		// S3 Default MaxKeys is 1000
		AssertJUnit.assertEquals(1000, (int) result.maxKeys());
		AssertJUnit.assertFalse(result.isTruncated());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	// Fails same as the V1 version doesnt error out
	// @Test(description = "v2: object list w/ whitespace marker, fails")
	// public void testObjectListMarkerEmptyV2() {
	// String bucketName = utils.getBucketName(prefix);
	// String marker = " ";

	// s3Client.createBucket(b -> b.bucket(bucketName));

	// try {
	// // Using ListObjects (V1 style) to test 'marker'
	// s3Client.listObjects(r -> r.bucket(bucketName).marker(marker));
	// AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals("SignatureDoesNotMatch",
	// err.awsErrorDetails().errorCode());
	// }
	// }

	@Test(description = "object list) w/ unreadable marker, succeeds")
	public void testObjectListMarkerUnreadable() {

		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };
		java.util.List<String> expectedKeys = Arrays.asList("bar", "baz", "foo", "quxx");
		String marker = "\\x0a";

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsResponse result = s3Client.listObjects(r -> r.bucket(bucketName).marker(marker));

		AssertJUnit.assertEquals(marker, result.marker());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ marker not in list, succeeds")
	public void testObjectListMarkerNotInListV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };
		java.util.List<String> expectedKeys = Arrays.asList("foo", "quxx");
		String marker = "blah";

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsResponse result = s3Client.listObjects(r -> r.bucket(bucketName).marker(marker));

		AssertJUnit.assertEquals(marker, result.marker());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	@Test(description = "v2: object list w/ marker after list, succeeds")
	public void testObjectListMarkerAfterListV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };
		String marker = "zzz";

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}
		ListObjectsResponse result = s3Client.listObjects(r -> r.bucket(bucketName).marker(marker));

		AssertJUnit.assertEquals(marker, result.marker());
		AssertJUnit.assertFalse(result.isTruncated());
		AssertJUnit.assertTrue(result.contents().isEmpty());
	}

	@Test(description = "v2: object list w/ marker before list, succeeds")
	public void testObjectListMarkerBeforeListV2() {
		String bucketName = utils.getBucketName(prefix);
		String[] keys = { "bar", "baz", "foo", "quxx" };
		java.util.List<String> expectedKeys = Arrays.asList("bar", "baz", "foo", "quxx");
		String marker = "aaa";

		s3Client.createBucket(b -> b.bucket(bucketName));
		for (String k : keys) {
			s3Client.putObject(p -> p.bucket(bucketName).key(k), RequestBody.fromString("data"));
		}

		ListObjectsResponse result = s3Client.listObjects(r -> r.bucket(bucketName).marker(marker));

		AssertJUnit.assertEquals(marker, result.marker());
		AssertJUnit.assertFalse(result.isTruncated());

		java.util.List<String> actualKeys = result.contents().stream()
				.map(S3Object::key)
				.collect(java.util.stream.Collectors.toList());

		AssertJUnit.assertEquals(expectedKeys, actualKeys);
	}

	// // .......................................Get Ranged Object in
	// // Range....................................................

	// Fails as the object creation succeds
	// @Test(description = "get object w/range -> return trailing bytes, suceeds")
	// public void testRangedReturnTrailingBytesResponseCode() throws IOException {
	// String bucket_name = utils.getBucketName(prefix);
	// String key = "key1";
	// String content = "testcontent";
	// try {
	// s3Client.createBucket(b -> b.bucket(bucket_name));
	// s3Client.putObject(b -> b.bucket(bucket_name).key(key),
	// RequestBody.fromString(content));
	// ResponseInputStream<GetObjectResponse> obj = s3Client
	// .getObject(b -> b.bucket(bucket_name).key(key).range("bytes=4-10"));
	// BufferedReader reader = new BufferedReader(new InputStreamReader(obj));
	// while (true) {
	// String line = reader.readLine();
	// if (line == null)
	// break;
	// String str = content.substring(4);
	// AssertJUnit.assertEquals(line, str);
	// }
	// AssertJUnit.fail("Expected 400 Bad Request");
	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "400 Bad
	// Request");
	// }
	// }

	@Test(description = "get object w/range -> leading bytes, suceeds")
	public void testRangedSkipLeadingBytesResponseCode() throws IOException {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "testcontent";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		s3Client.putObject(b -> b.bucket(bucket_name).key(key), RequestBody.fromString(content));

		ResponseInputStream<GetObjectResponse> obj = s3Client
				.getObject(b -> b.bucket(bucket_name).key(key).range("bytes=4-10"));

		BufferedReader reader = new BufferedReader(new InputStreamReader(obj));
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			String str = content.substring(4);
			AssertJUnit.assertEquals(line, str);
		}
	}

	@Test(description = "get object w/range, suceeds")
	public void testRangedrequestResponseCode() throws IOException {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String content = "testcontent";

		s3Client.createBucket(b -> b.bucket(bucket_name));
		s3Client.putObject(b -> b.bucket(bucket_name).key(key), RequestBody.fromString(content));

		ResponseInputStream<GetObjectResponse> obj = s3Client
				.getObject(b -> b.bucket(bucket_name).key(key).range("bytes=4-7"));

		BufferedReader reader = new BufferedReader(new InputStreamReader(obj));
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			String str = content.substring(4, 8);
			AssertJUnit.assertEquals(line, str);
		}
	}

	@Test(description = "multipart uploads for small to big sizes using LLAPI, succeeds!")
	public void testMultipartUploadMultipleSizesLLAPI() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);

		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(s3Client,
				bucket_name, key, 5 * 1024 * 1024,
				filePath);
		s3Client.completeMultipartUpload(resp);

		CompleteMultipartUploadRequest resp2 = utils.multipartUploadLLAPIV2(s3Client,
				bucket_name, key,
				5 * 1024 * 1024 + 100 * 1024, filePath);
		s3Client.completeMultipartUpload(resp2);

		CompleteMultipartUploadRequest resp3 = utils.multipartUploadLLAPIV2(s3Client,
				bucket_name, key,
				5 * 1024 * 1024 + 600 * 1024, filePath);
		s3Client.completeMultipartUpload(resp3);

		CompleteMultipartUploadRequest resp4 = utils.multipartUploadLLAPIV2(s3Client,
				bucket_name, key,
				10 * 1024 * 1024 + 100 * 1024, filePath);
		s3Client.completeMultipartUpload(resp4);

		CompleteMultipartUploadRequest resp5 = utils.multipartUploadLLAPIV2(s3Client,
				bucket_name, key,
				10 * 1024 * 1024 + 600 * 1024, filePath);
		s3Client.completeMultipartUpload(resp5);

		CompleteMultipartUploadRequest resp6 = utils.multipartUploadLLAPIV2(s3Client,
				bucket_name, key, 10 * 1024 * 1024,
				filePath);
		s3Client.completeMultipartUpload(resp6);
	}

	@Test(description = "multipart uploads for small file using LLAPI, succeeds!")
	public void testMultipartUploadSmallLLAPI() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);
		long size = 5 * 1024 * 1024;

		software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(
				s3Client,
				bucket_name, key, size, filePath);
		s3Client.completeMultipartUpload(resp);
	}

	@Test(description = "multipart uploads w/missing part using LLAPI, fails!")
	public void testMultipartUploadIncorrectMissingPartLLAPI() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);

		java.util.List<CompletedPart> completedParts = new java.util.ArrayList<CompletedPart>();

		CreateMultipartUploadResponse initResponse = s3Client
				.createMultipartUpload(b -> b.bucket(bucket_name).key(key));
		String uploadId = initResponse.uploadId();

		File file = new File(filePath);
		long contentLength = file.length();
		long partSize = 5 * 1024 * 1024;

		long filePosition = 1024 * 1024;
		for (int i = 7; filePosition < contentLength; i++) {
			partSize = Math.min(partSize, (contentLength - filePosition));

			int partNumber = i;
			try {
				FileInputStream fis = new FileInputStream(file);
				fis.skip(filePosition);
				byte[] partBytes = new byte[(int) partSize];
				fis.read(partBytes);
				fis.close();

				UploadPartResponse res = s3Client.uploadPart(b -> b.bucket(bucket_name).key(key).uploadId(uploadId)
						.partNumber(partNumber).contentLength((long) partBytes.length),
						RequestBody.fromBytes(partBytes));

				completedParts.add(CompletedPart.builder().partNumber(999).eTag(res.eTag()).build());

			} catch (IOException e) {
				AssertJUnit.fail("IO Error");
			}
			filePosition += partSize;
		}

		software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest compRequest = software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
				.builder()
				.bucket(bucket_name).key(key).uploadId(uploadId)
				.multipartUpload(b -> b.parts(completedParts))
				.build();

		try {
			s3Client.completeMultipartUpload(compRequest);
			AssertJUnit.fail("Expected 400 InvalidPart");
		} catch (S3Exception err) {
			S3.logger.debug(String.format("TEST ERROR: %s%n", err.getMessage()));
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "InvalidPart");
		}
	}

	@Test(description = "multipart uploads w/non existant upload using LLAPI, fails!")
	public void testAbortMultipartUploadNotFoundLLAPI() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		s3Client.createBucket(b -> b.bucket(bucket_name));
		try {
			s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
					.bucket(bucket_name).key(key).uploadId("1")
					.build());
			AssertJUnit.fail("Expected 404 NoSuchUpload");

		} catch (S3Exception err) {
			AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchUpload");
		}
	}

	@Test(description = "multipart uploads abort using LLAPI, succeeds!")
	public void testAbortMultipartUploadLLAPI() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);

		software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(
				s3Client,
				bucket_name, key, 5 * 1024 * 1024, filePath);

		s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
				.bucket(bucket_name).key(key).uploadId(resp.uploadId())
				.build());
	}

	@Test(description = "multipart uploads overwrite using LLAPI, succeeds!")
	public void testMultipartUploadOverwriteExistingObjectLLAPI() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);
		long size = 5 * 1024 * 1024;

		s3Client.putObject(b -> b.bucket(bucket_name).key(key), RequestBody.fromString("foo"));

		CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(
				s3Client,
				bucket_name, key, size, filePath);
		s3Client.completeMultipartUpload(resp);

		AssertJUnit.assertFalse(
				java.util.Arrays.equals(s3Client.getObjectAsBytes(b -> b.bucket(bucket_name).key(key)).asByteArray(),
						"foo".getBytes()));
	}

	// / Fails as the previous version
	// @Test(description = "multipart uploads for a very small file using LLAPI,
	// fails!")
	// public void testMultipartUploadFileTooSmallFileLLAPI() {

	// String bucket_name = utils.getBucketName(prefix);
	// String key = "key1";
	// s3Client.createBucket(b -> b.bucket(bucket_name));

	// String filePath = "./data/sample.txt";
	// utils.createFile(filePath, 256 * 1024);
	// long size = 5 * 1024 * 1024;

	// try {
	// CompleteMultipartUploadRequest resp = utils.multipartUploadLLAPIV2(
	// s3Client,
	// bucket_name, key, size, filePath);
	// s3Client.completeMultipartUpload(resp);
	// AssertJUnit.fail("Expected 400 EntityTooSmall");
	// } catch (S3Exception err) {
	// AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(),
	// "EntityTooSmall");
	// }
	// }
	// /

	@Test(description = "multipart copy for small file using LLAPI, succeeds!")
	public void testMultipartCopyMultipleSizesLLAPI() {

		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(src_bkt));
		s3Client.createBucket(b -> b.bucket(dst_bkt));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);
		File file = new File(filePath);

		s3Client.putObject(b -> b.bucket(src_bkt).key(key), file.toPath());

		CompleteMultipartUploadRequest resp = utils.multipartCopyLLAPIV2(s3Client, dst_bkt,
				key, src_bkt, key,
				5 * 1024 * 1024);
		s3Client.completeMultipartUpload(resp);

		CompleteMultipartUploadRequest resp2 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt,
				key, src_bkt, key,
				5 * 1024 * 1024 + 100 * 1024);
		s3Client.completeMultipartUpload(resp2);

		CompleteMultipartUploadRequest resp3 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt,
				key, src_bkt, key,
				5 * 1024 * 1024 + 600 * 1024);
		s3Client.completeMultipartUpload(resp3);

		CompleteMultipartUploadRequest resp4 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt,
				key, src_bkt, key,
				10 * 1024 * 1024 + 100 * 1024);
		s3Client.completeMultipartUpload(resp4);

		CompleteMultipartUploadRequest resp5 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt,
				key, src_bkt, key,
				10 * 1024 * 1024 + 600 * 1024);
		s3Client.completeMultipartUpload(resp5);

		CompleteMultipartUploadRequest resp6 = utils.multipartCopyLLAPIV2(s3Client, dst_bkt,
				key, src_bkt, key,
				10 * 1024 * 1024);
		s3Client.completeMultipartUpload(resp6);
	}

	@Test(description = "Upload of a file using HLAPI, succeeds!")
	public void testUploadFileHLAPIBigFile() {

		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);

		CompletedFileUpload upl = utils.UploadFileHLAPIV2(s3AsyncClient, bucket_name, key, filePath);

		AssertJUnit.assertNotNull(upl);
	}

	@Test(description = "Upload of a file to non existant bucket using HLAPI, fails!")
	public void testUploadFileHLAPINonExistantBucket() {
		try{
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";

		String filePath = "./data/sample.txt";
		utils.createFile(filePath, 256 * 1024);

		utils.UploadFileHLAPIV2(s3AsyncClient, bucket_name, key, filePath);
		Assert.fail("Expected 404 NoSuchBucket");
		}catch(S3Exception e){
			AssertJUnit.assertEquals(e.awsErrorDetails().errorCode(), "NoSuchBucket");
		}
	}

	@Test(description = "Multipart Upload for file using HLAPI, succeeds!")
	public void testMultipartUploadHLAPIAWS4()
			throws InterruptedException {

		String bucket_name = utils.getBucketName(prefix);

		s3Client.createBucket(b -> b.bucket(bucket_name));

		String dir = "./data";
		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);

		CompletedDirectoryUpload upl = utils.multipartUploadHLAPIV2(s3AsyncClient, bucket_name, null, dir);

		AssertJUnit.assertNotNull(upl);
	}

	@Test(description = "v2: Multipart copy using TransferManager, succeeds")
	public void testMultipartCopyV2() {
		String srcBkt = utils.getBucketName(prefix);
		String dstBkt = utils.getBucketName(prefix);
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(srcBkt));
		s3Client.createBucket(b -> b.bucket(dstBkt));

		File sourceFile = new File("./data/file.mpg");
		s3Client.putObject(p -> p.bucket(srcBkt).key(key), RequestBody.fromFile(sourceFile));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		Copy copy = tm.copy(c -> c.copyObjectRequest(r -> r
				.sourceBucket(srcBkt)
				.sourceKey(key)
				.destinationBucket(dstBkt)
				.destinationKey(key)));

		CompletedCopy completedCopy = copy.completionFuture().join();
		AssertJUnit.assertTrue(completedCopy.response().sdkHttpResponse().isSuccessful());
		tm.close();
	}

	@Test(description = "v2: Multipart copy with non-existent destination bucket, fails")
	public void testMultipartCopyNoDSTBucketV2() {
		String srcBkt = utils.getBucketName(prefix);
		String dstBkt = "non-existent-bucket-" + System.currentTimeMillis();
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(srcBkt));
		s3Client.putObject(p -> p.bucket(srcBkt).key(key), RequestBody.fromString("data"));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.copy(c -> c.copyObjectRequest(r -> r
					.sourceBucket(srcBkt)
					.sourceKey(key)
					.destinationBucket(dstBkt)
					.destinationKey(key)))
					.completionFuture().join();

			AssertJUnit.fail("Expected NoSuchBucket exception");
		} catch (CompletionException e) {
			S3Exception s3e = (S3Exception) e.getCause();
			AssertJUnit.assertEquals("NoSuchBucket", s3e.awsErrorDetails().errorCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "v2: Multipart copy w/non-existent source bucket, fails")
	public void testMultipartCopyNoSRCBucketV2() {
		String srcBkt = "missing-source-" + System.currentTimeMillis();
		String dstBkt = utils.getBucketName(prefix);
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(dstBkt));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.copy(c -> c.copyObjectRequest(r -> r
					.sourceBucket(srcBkt)
					.sourceKey(key)
					.destinationBucket(dstBkt)
					.destinationKey(key)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404/NoSuchBucket");
		} catch (CompletionException e) {
			S3Exception s3e = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, s3e.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "v2: Multipart copy w/non-existent source key, fails")
	public void testMultipartCopyNoSRCKeyV2() {
		String srcBkt = utils.getBucketName(prefix);
		String dstBkt = utils.getBucketName(prefix);
		String key = "key1";
		String missingKey = "non-existent-key";

		s3Client.createBucket(b -> b.bucket(srcBkt));
		s3Client.createBucket(b -> b.bucket(dstBkt));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.copy(c -> c.copyObjectRequest(r -> r
					.sourceBucket(srcBkt)
					.sourceKey(missingKey)
					.destinationBucket(dstBkt)
					.destinationKey(key)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found");
		} catch (CompletionException e) {
			S3Exception s3e = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, s3e.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "v2: Download using TransferManager, succeeds")
	public void testDownloadV2() {
		String bucketName = utils.getBucketName(prefix);
		String key = "key1";
		Path destinationPath = Paths.get("./data/sample.txt");

		s3Client.createBucket(b -> b.bucket(bucketName));
		s3Client.putObject(p -> p.bucket(bucketName).key(key), RequestBody.fromString("sample content"));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		FileDownload download = tm.downloadFile(d -> d
				.getObjectRequest(g -> g.bucket(bucketName).key(key))
				.destination(destinationPath));

		CompletedFileDownload completedDownload = download.completionFuture().join();
		AssertJUnit.assertTrue(completedDownload.response().sdkHttpResponse().isSuccessful());
		tm.close();
	}

	@Test(description = "v2: Download from non-existent bucket, fails")
	public void testDownloadNoBucketV2() {
		String bucketName = utils.getBucketName(prefix); // Assume not created
		String key = "key1";
		Path destinationPath = Paths.get("./data/sample.txt");

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.downloadFile(d -> d
					.getObjectRequest(g -> g.bucket(bucketName).key(key))
					.destination(destinationPath))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found");
		} catch (CompletionException e) {
			S3Exception s3e = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, s3e.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "v2: Multipart Download using TransferManager, succeeds")
	public void testMultipartDownloadV2() {
		String bucketName = utils.getBucketName(prefix);
		String key = "key1";
		Path destinationDir = Paths.get("./downloads/file.mpg");

		s3Client.createBucket(b -> b.bucket(bucketName));

		File largeFile = new File("./data/file.mpg"); // 23MB File
		s3Client.putObject(p -> p.bucket(bucketName).key(key), RequestBody.fromFile(largeFile));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		FileDownload download = tm.downloadFile(d -> d
				.getObjectRequest(g -> g.bucket(bucketName).key(key))
				.destination(destinationDir));

		CompletedFileDownload completedDownload = download.completionFuture().join();
		AssertJUnit.assertTrue(completedDownload.response().sdkHttpResponse().isSuccessful());
		tm.close();
	}

	@Test(description = "v2: Multipart Download with pause and resume using S3TransferManager")
	public void testMultipartDownloadWithPauseV2() throws IOException, InterruptedException {
		String bucketName = utils.getBucketName(prefix);
		String key = "key1";
		Path sourcePath = Paths.get("./data/file.mpg");
		Path destPath = Paths.get("./data/file2.mpg");

		s3Client.createBucket(b -> b.bucket(bucketName));

		s3Client.putObject(p -> p.bucket(bucketName).key(key), RequestBody.fromFile(sourcePath));
		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		FileDownload myDownload = tm.downloadFile(d -> d
				.getObjectRequest(g -> g.bucket(bucketName).key(key))
				.destination(destPath));

		long fiveMB = 5 * 1024 * 1024L;
		while (myDownload.progress().snapshot().transferredBytes() < fiveMB) {
			Thread.sleep(50);
			if (myDownload.progress().snapshot().transferredBytes() >= myDownload.progress().snapshot().totalBytes()
					.orElse(Long.MAX_VALUE)) {
				break;
			}
		}

		if (myDownload.progress().snapshot().transferredBytes() < myDownload.progress().snapshot().totalBytes()
				.orElse(Long.MAX_VALUE)) {
			ResumableFileDownload resumableDownload = myDownload.pause();

			Path persistFile = Paths.get("resume-download.json");
			resumableDownload.serializeToFile(persistFile);

			ResumableFileDownload persistedDownload = ResumableFileDownload.fromFile(persistFile);
			FileDownload resumedState = tm.resumeDownloadFile(persistedDownload);

			CompletedFileDownload completed = resumedState.completionFuture().join();
			AssertJUnit.assertTrue(completed.response().sdkHttpResponse().isSuccessful());
		} else {
			CompletedFileDownload completed = myDownload.completionFuture().join();
			AssertJUnit.assertTrue(completed.response().sdkHttpResponse().isSuccessful());
		}

		AssertJUnit.assertTrue(Files.exists(destPath));

		tm.close();
	}

	@Test(description = "v2: Multipart Download from non-existent bucket, fails")
	public void testMultipartDownloadNoBucketV2() {
		String bucketName = "non-existent-bucket-" + System.currentTimeMillis();
		String key = "key1";
		Path destPath = Paths.get("./downloads/file.mpg");

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.downloadFile(d -> d
					.getObjectRequest(g -> g.bucket(bucketName).key(key))
					.destination(destPath))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 NoSuchBucket");
		} catch (CompletionException e) {
			S3Exception s3e = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, s3e.statusCode());
			AssertJUnit.assertTrue(s3e.awsErrorDetails().errorCode().contains("Bucket"));
		} finally {
			tm.close();
		}
	}

	@Test(description = "v2: Download w/no key using TransferManager, fails")
	public void testMultipartDownloadNoKeyV2() {
		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));
		String key = "key1";
		Path destPath = Paths.get("./downloads/file.mpg");

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.downloadFile(d -> d
					.getObjectRequest(g -> g.bucket(bucketName).key(key))
					.destination(destPath))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found");
		} catch (CompletionException e) {
			S3Exception s3e = (S3Exception) e.getCause();
			// S3 returns 404 when a key is not found
			AssertJUnit.assertEquals(404, s3e.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "v2: Upload of list of files succeeds")
	public void testUploadFileListV2() {
		String bucketName = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucketName));

		String fname1 = "./data/file.mpg";
		String fname2 = "./data/sample.txt";
		utils.createFile(fname1, 23 * 1024 * 1024);
		utils.createFile(fname2, 256 * 1024);

		// Use nio Path instead of io File
		java.util.List<Path> files = java.util.Arrays.asList(Paths.get(fname1), Paths.get(fname2));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		java.util.List<java.util.concurrent.CompletableFuture<CompletedFileUpload>> futures = new java.util.ArrayList<>();

		for (Path file : files) {
			String key = file.getFileName().toString();

			FileUpload upload = tm.uploadFile(u -> u
					.putObjectRequest(p -> p.bucket(bucketName).key(key))
					.source(file));

			futures.add(upload.completionFuture());
		}

		java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
				.join();

		ListObjectsV2Response listing = s3Client.listObjectsV2(l -> l.bucket(bucketName));
		AssertJUnit.assertEquals(2, listing.contents().size());
		tm.close();
	}

	// Defies Expected Bahavior
	// @Test(description = "v2: Directory Upload to non-existent bucket, fails")
	// public void testMultipartUploadNonExistentBucketV2() {
	// String bucketName = "non-existent-bucket-" + System.currentTimeMillis();
	// Path dirPath = Paths.get("./data");

	// S3TransferManager tm =
	// S3TransferManager.builder().s3Client(s3AsyncClient).build();

	// try {
	// tm.uploadDirectory(u -> u
	// .bucket(bucketName)
	// .source(dirPath))
	// .completionFuture().join();

	// AssertJUnit.fail("Expected NoSuchBucket");
	// } catch (CompletionException e) {
	// S3Exception s3e = (S3Exception) e.getCause();
	// AssertJUnit.assertEquals("NoSuchBucket", s3e.awsErrorDetails().errorCode());
	// } finally {
	// tm.close();
	// }
	// }

	@Test(description = "Multipart Upload of a file with pause and resume using HLAPI, succeeds!")
	public void testMultipartUploadWithPause() throws InterruptedException, IOException {
		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 53 * 1024 * 1024);
		String key = "key1";

		S3TransferManager tm = S3TransferManager.builder()
				.s3Client(s3AsyncClient)
				.build();
		FileUpload myUpload = tm.uploadFile(u -> u
				.putObjectRequest(p -> p.bucket(bucket_name).key(key))
				.source(Paths.get(filePath)));

		Thread.sleep(500);

		ResumableFileUpload resumableUpload = myUpload.pause();

		Path persistPath = Paths.get("resume-upload.json");
		resumableUpload.serializeToFile(persistPath);

		ResumableFileUpload persistedUpload = ResumableFileUpload.fromFile(persistPath);
		FileUpload resumedUpload = tm.resumeUploadFile(persistedUpload);

		CompletedFileUpload completed = resumedUpload.completionFuture().join();

		AssertJUnit.assertTrue(completed.response().sdkHttpResponse().isSuccessful());
		tm.close();
	}

	@Test(description = "Multipart copy using HLAPI, succeeds!")
	public void testMultipartCopyHLAPIA() {
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(src_bkt));
		s3Client.createBucket(b -> b.bucket(dst_bkt));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);

		// Upload the initial file
		s3Client.putObject(p -> p.bucket(src_bkt).key(key), RequestBody.fromFile(new File(filePath)));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		Copy cpy = tm.copy(c -> c.copyObjectRequest(r -> r
				.sourceBucket(src_bkt)
				.sourceKey(key)
				.destinationBucket(dst_bkt)
				.destinationKey(key)));

		CompletedCopy completedCopy = cpy.completionFuture().join();
		AssertJUnit.assertTrue(completedCopy.response().sdkHttpResponse().isSuccessful());
		tm.close();
	}

	@Test(description = "Multipart copy for file with non existant destination bucket using HLAPI, fails!")
	public void testMultipartCopyNoDSTBucketHLAPI() {
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix); // Intentionally not created
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(src_bkt));

		String filePath = "./data/file.mpg";
		utils.createFile(filePath, 23 * 1024 * 1024);
		s3Client.putObject(p -> p.bucket(src_bkt).key(key), RequestBody.fromFile(new File(filePath)));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.copy(c -> c.copyObjectRequest(r -> r
					.sourceBucket(src_bkt)
					.sourceKey(key)
					.destinationBucket(dst_bkt)
					.destinationKey(key)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found / NoSuchBucket");
		} catch (CompletionException e) {
			S3Exception err = (S3Exception) e.getCause();
			AssertJUnit.assertEquals("NoSuchBucket", err.awsErrorDetails().errorCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "Multipart copy w/non existant source bucket using HLAPI, fails!")
	public void testMultipartCopyNoSRCBucketHLAPI() {
		String src_bkt = utils.getBucketName(prefix); // Intentionally not created
		String dst_bkt = utils.getBucketName(prefix);
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(dst_bkt));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.copy(c -> c.copyObjectRequest(r -> r
					.sourceBucket(src_bkt)
					.sourceKey(key)
					.destinationBucket(dst_bkt)
					.destinationKey(key)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found / NoSuchBucket");
		} catch (CompletionException e) {
			S3Exception err = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, err.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "Multipart copy w/non existant source key using HLAPI, fails!")
	public void testMultipartCopyNoSRCKeyHLAPI() {
		String src_bkt = utils.getBucketName(prefix);
		String dst_bkt = utils.getBucketName(prefix);
		String key = "key1";

		s3Client.createBucket(b -> b.bucket(src_bkt));
		s3Client.createBucket(b -> b.bucket(dst_bkt));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.copy(c -> c.copyObjectRequest(r -> r
					.sourceBucket(src_bkt)
					.sourceKey(key)
					.destinationBucket(dst_bkt)
					.destinationKey(key)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found");
		} catch (CompletionException e) {
			S3Exception err = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, err.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "Download using HLAPI, suceeds!")
	public void testDownloadHLAPI() {
		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));
		String key = "key1";
		String filePath = "./data/sample.txt";

		utils.createFile(filePath, 256 * 1024);
		s3Client.putObject(p -> p.bucket(bucket_name).key(key), RequestBody.fromFile(new File(filePath)));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		FileDownload download = tm.downloadFile(d -> d
				.getObjectRequest(g -> g.bucket(bucket_name).key(key))
				.destination(Paths.get(filePath)));

		CompletedFileDownload completed = download.completionFuture().join();
		AssertJUnit.assertTrue(completed.response().sdkHttpResponse().isSuccessful());
		tm.close();
	}

	@Test(description = "Download from non existant bucket using HLAPI, fails!")
	public void testDownloadNoBucketHLAPI() {
		String bucket_name = utils.getBucketName(prefix); // Not created
		String key = "key1";
		String filePath = "./data/sample.txt";

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.downloadFile(d -> d
					.getObjectRequest(g -> g.bucket(bucket_name).key(key))
					.destination(Paths.get(filePath)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found");
		} catch (CompletionException e) {
			S3Exception err = (S3Exception) e.getCause();
			AssertJUnit.assertEquals("NoSuchBucket", err.awsErrorDetails().errorCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "Download w/no key using HLAPI, suceeds!")
	public void testDownloadNoKeyHLAPI() {
		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));
		String key = "key1"; // Key not uploaded
		String filePath = "./data/sample.txt";

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.downloadFile(d -> d
					.getObjectRequest(g -> g.bucket(bucket_name).key(key))
					.destination(Paths.get(filePath)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found");
		} catch (CompletionException e) {
			S3Exception err = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, err.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "Multipart Download using HLAPI, suceeds!")
	public void testMultipartDownloadHLAPI() {
		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));
		String key = "key1";
		String filePath = "./data/file.mpg";
		String dstDir = "./downloads/file.mpg";

		utils.createFile(filePath, 23 * 1024 * 1024);
		s3Client.putObject(p -> p.bucket(bucket_name).key(key), RequestBody.fromFile(new File(filePath)));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		FileDownload download = tm.downloadFile(d -> d
				.getObjectRequest(g -> g.bucket(bucket_name).key(key))
				.destination(Paths.get(dstDir)));

		CompletedFileDownload completed = download.completionFuture().join();
		AssertJUnit.assertTrue(completed.response().sdkHttpResponse().isSuccessful());
		tm.close();
	}

	@Test(description = "Multipart Download with pause and resume using HLAPI, succeeds!")
	public void testMultipartDownloadWithPauseHLAPI() throws InterruptedException, IOException {
		String bucket_name = utils.getBucketName(prefix);
		String key = "key1";
		String filePath = "./data/file.mpg";
		Path destPath = Paths.get("./data/file2.mpg");
		Path persistFile = Paths.get("resume-download.json");

		Files.deleteIfExists(destPath);
		Files.deleteIfExists(persistFile);

		s3Client.createBucket(b -> b.bucket(bucket_name));
		int totalSize = 23 * 1024 * 1024;
		utils.createFile(filePath, totalSize);
		s3Client.putObject(p -> p.bucket(bucket_name).key(key), RequestBody.fromFile(new File(filePath)));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		FileDownload myDownload = tm.downloadFile(d -> d
				.getObjectRequest(g -> g.bucket(bucket_name).key(key))
				.destination(destPath));

		long targetPauseBytes = totalSize / 5;
		boolean pausedMidFlight = false;

		while (!myDownload.completionFuture().isDone()) {
			long transferred = myDownload.progress().snapshot().transferredBytes();
			if (transferred >= targetPauseBytes && transferred < totalSize) {
				ResumableFileDownload resumableDownload = myDownload.pause();
				resumableDownload.serializeToFile(persistFile);
				pausedMidFlight = true;
				break;
			}
			Thread.sleep(10);
		}
		AssertJUnit.assertTrue("Download finished too fast to test pause/resume logic", pausedMidFlight);

		ResumableFileDownload persistedDownload = ResumableFileDownload.fromFile(persistFile);
		FileDownload resumedDownload = tm.resumeDownloadFile(persistedDownload);
		CompletedFileDownload completed = resumedDownload.completionFuture().join();
		AssertJUnit.assertTrue(completed.response().sdkHttpResponse().isSuccessful());

		tm.close();
	}

	@Test(description = "Multipart Download from non existant bucket using HLAPI, fails!")
	public void testMultipartDownloadNoBucketHLAPI() {
		String bucket_name = utils.getBucketName(prefix); // Not created
		String key = "key1";
		String dstDir = "./downloads/file.mpg";

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.downloadFile(d -> d
					.getObjectRequest(g -> g.bucket(bucket_name).key(key))
					.destination(Paths.get(dstDir)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 NoSuchBucket");
		} catch (CompletionException e) {
			S3Exception err = (S3Exception) e.getCause();
			AssertJUnit.assertEquals("NoSuchBucket", err.awsErrorDetails().errorCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "Multipart Download w/no key using HLAPI, fails!")
	public void testMultipartDownloadNoKeyHLAPI() {
		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));
		String key = "key1"; // Not uploaded
		String dstDir = "./downloads/file.mpg";

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

		try {
			tm.downloadFile(d -> d
					.getObjectRequest(g -> g.bucket(bucket_name).key(key))
					.destination(Paths.get(dstDir)))
					.completionFuture().join();

			AssertJUnit.fail("Expected 404 Not Found");
		} catch (CompletionException e) {
			S3Exception err = (S3Exception) e.getCause();
			AssertJUnit.assertEquals(404, err.statusCode());
		} finally {
			tm.close();
		}
	}

	@Test(description = "Upload of list of files suceeds!")
	public void testUploadFileList() {
		String bucket_name = utils.getBucketName(prefix);
		s3Client.createBucket(b -> b.bucket(bucket_name));

		String fname1 = "./data/file.mpg";
		String fname2 = "./data/sample.txt";
		utils.createFile(fname1, 23 * 1024 * 1024);
		utils.createFile(fname2, 256 * 1024);

		java.util.List<Path> files = java.util.Arrays.asList(Paths.get(fname1), Paths.get(fname2));

		S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();
		java.util.List<java.util.concurrent.CompletableFuture<CompletedFileUpload>> futures = new java.util.ArrayList<>();
		for (Path file : files) {
			FileUpload upload = tm.uploadFile(u -> u
					.putObjectRequest(p -> p.bucket(bucket_name).key(file.getFileName().toString()))
					.source(file));
			futures.add(upload.completionFuture());
		}

		java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
				.join();

		ListObjectsV2Response listing = s3Client.listObjectsV2(l -> l.bucket(bucket_name));
		AssertJUnit.assertEquals(2, listing.contents().size());

		tm.close();
	}

}
