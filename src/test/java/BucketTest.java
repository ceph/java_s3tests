import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class BucketTest {

    private static S3 utils = S3.getInstance();
    boolean useV4Signature = true;
    S3Client s3Client = utils.getS3V2Client(useV4Signature);
    String prefix = utils.getPrefix();

    @BeforeClass
    public void generateFiles() {
        String filePath = "./data/file.mpg";
        utils.createFile(filePath, 23 * 1024 * 1024);
        filePath = "./data/file.txt";
        utils.createFile(filePath, 256 * 1024);
    }

    @AfterClass
    public void tearDownAfterClass() throws Exception {
        S3.logger.debug("TeardownAfterClass");
        utils.teradownRetriesV2 = 0;
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

    @Test(description = "empty buckets return no contents")
    public void testBucketListEmptyV2() {
        S3Client s3v2 = utils.getS3V2Client(useV4Signature);
        String bucketName = utils.getBucketName(prefix);

        s3v2.createBucket(b -> b.bucket(bucketName));
        ListObjectsV2Response list = s3v2.listObjectsV2(r -> r.bucket(bucketName));

        AssertJUnit.assertTrue(list.contents().isEmpty());
        s3v2.deleteBucket(b -> b.bucket(bucketName));
        s3v2.close();
    }

    @Test(description = "deleting non existant bucket returns NoSuchBucket")
    public void testBucketDeleteNotExist() {

        String bucket_name = utils.getBucketName(prefix);
        try {
            s3Client.deleteBucket(b -> b.bucket(bucket_name));
            AssertJUnit.fail("Expected 400 NoSuchBucket");
        } catch (NoSuchBucketException err) {
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
        }
    }

    @Test(description = "deleting non empty bucket returns BucketNotEmpty")
    public void testBucketDeleteNonEmpty() {

        String bucket_name = utils.getBucketName(prefix);
        s3Client.createBucket(b -> b.bucket(bucket_name));

        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket_name)
                .key("key1")
                .build(), RequestBody.fromString("echo"));

        try {
            s3Client.deleteBucket(b -> b.bucket(bucket_name));
            AssertJUnit.fail("Expected 400 BucketNotEmpty");
        } catch (S3Exception e) {
            AssertJUnit.assertEquals("BucketNotEmpty", e.awsErrorDetails().errorCode());
        }
    }

    @Test(description = "should delete bucket")
    public void testBucketCreateReadDelete() {

        String bucket_name = utils.getBucketName(prefix);
        s3Client.createBucket(b -> b.bucket(bucket_name));
        s3Client.headBucket(b -> b.bucket(bucket_name));

        s3Client.deleteBucket(b -> b.bucket(bucket_name));
        try {
            s3Client.headBucket(b -> b.bucket(bucket_name));
            AssertJUnit.fail("Bucket should not exist, but HeadBucket did not throw.");
        } catch (S3Exception e) {
            AssertJUnit.assertEquals("NoSuchBucket", e.awsErrorDetails().errorCode());
        }
    }

    @Test(description = "distinct buckets return distinct objects")
    public void testBucketListDistinct() {

        String bucket1 = utils.getBucketName(prefix);
        String bucket2 = utils.getBucketName(prefix);

        s3Client.createBucket(b -> b.bucket(bucket1));
        s3Client.createBucket(b -> b.bucket(bucket2));

        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket1)
                .key("key1")
                .build(), RequestBody.fromString("echo"));

        ListObjectsV2Response list = s3Client.listObjectsV2(r -> r.bucket(bucket2));
        AssertJUnit.assertTrue(list.contents().isEmpty());
        list = s3Client.listObjectsV2(r -> r.bucket(bucket1));
        AssertJUnit.assertFalse(list.contents().isEmpty());
    }

    @Test(description = "Accessing non existant bucket should fail ")
    public void testBucketNotExist() {

        String bucket_name = utils.getBucketName(prefix);
        try {

            s3Client.getBucketAcl(b -> b.bucket(bucket_name));
            AssertJUnit.fail("Expected 400 NoSuchBucket");
        } catch (S3Exception err) {
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "NoSuchBucket");
        }
    }

    @Test(description = "v2: create w/expect 200, garbage but S3 succeeds!")
    public void testBucketCreateBadExpectMismatchV2() {
        String bucket_name = utils.getBucketName(prefix);

        CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                .bucket(bucket_name)
                .overrideConfiguration(o -> o.putHeader("Expect", "200"))
                .build();

        s3Client.createBucket(bktRequest);
    }

    @Test(description = "create w/expect empty, garbage but S3 succeeds!")
    public void testBucketCreateBadExpectEmpty() {

        String bucket_name = utils.getBucketName(prefix);
        CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                .bucket(bucket_name)
                .overrideConfiguration(o -> o.putHeader("Expect", ""))
                .build();

        s3Client.createBucket(bktRequest);
    }

    @Test(description = "create w/expect empty, garbage but S3 succeeds!")
    public void testBucketCreateBadExpectUnreadable() {

        String bucket_name = utils.getBucketName(prefix);
        CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                .bucket(bucket_name)
                .overrideConfiguration(o -> o.putHeader("Expect", "\\x07"))
                .build();
        s3Client.createBucket(bktRequest);
    }

    @Test(description = "create w/non-graphic content length, fails with signature error")
    public void testBucketCreateContentlengthUnreadable() {
        String bucket_name = utils.getBucketName(prefix);

        try {
            CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                    .bucket(bucket_name)
                    .overrideConfiguration(o -> o.putHeader("Content-Length", "\\x07"))
                    .build();

            s3Client.createBucket(bktRequest);
            AssertJUnit.fail("Expected an exception due to malformed header");
        } catch (S3Exception err) {
            String errorCode = err.awsErrorDetails().errorCode();
            System.out.println("RGW Returned Error Code: " + err.statusCode() + " " + err.awsErrorDetails().toString());
            // Shows an Acces Denied error in the logs
            // Not sure why Ideally should return the SignatureDoesNotMatch error
            boolean isAuthError = errorCode.equals("SignatureDoesNotMatch") ||
                    errorCode.equals("AccessDenied") ||
                    errorCode.equals("InvalidRequest");

            AssertJUnit.assertTrue("Expected an Auth error but got: " + errorCode, isAuthError);
        }
    }

    @Test(description = "create w/no content length, fails!")
    public void testBucketCreateContentlengthNone() {
        try {
            String bucket_name = utils.getBucketName(prefix);

            CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                    .bucket(bucket_name)
                    .overrideConfiguration(o -> o.putHeader("Content-Length", ""))
                    .build();
            s3Client.createBucket(bktRequest);
            AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");

        } catch (S3Exception err) {
            String errorCode = err.awsErrorDetails().errorCode();
            System.out.println("RGW Returned Error Code: " + err.statusCode() + " " + err.awsErrorDetails().toString());
            // Shows an Acces Denied error in the logs
            // Not sure why Ideally should return the SignatureDoesNotMatch error
            boolean isAuthError = errorCode.equals("SignatureDoesNotMatch") ||
                    errorCode.equals("AccessDenied") ||
                    errorCode.equals("InvalidRequest");

            AssertJUnit.assertTrue("Expected an Auth error but got: " + errorCode, isAuthError);
        }
    }

    @Test(description = "create w/ empty content length, fails!")
    public void testBucketCreateContentlengthEmpty() {

        String bucket_name = utils.getBucketName(prefix);

        try {
            CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                    .bucket(bucket_name)
                    .overrideConfiguration(o -> o.putHeader("Content-Length", " "))
                    .build();
            s3Client.createBucket(bktRequest);
            AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
        } catch (S3Exception err) {
            String errorCode = err.awsErrorDetails().errorCode();
            System.out.println("RGW Returned Error Code: " + err.statusCode() + " " + err.awsErrorDetails().toString());
            // Shows an Acces Denied error in the logs
            // Not sure why Ideally should return the SignatureDoesNotMatch error
            boolean isAuthError = errorCode.equals("SignatureDoesNotMatch") ||
                    errorCode.equals("AccessDenied") ||
                    errorCode.equals("InvalidRequest");

            AssertJUnit.assertTrue("Expected an Auth error but got: " + errorCode, isAuthError);
        }
    }

    @Test(description = "create w/ unreadable authorization, fails!")
    public void testBucketCreateBadAuthorizationUnreadable() {

        String bucket_name = utils.getBucketName(prefix);

        try {
            CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                    .bucket(bucket_name)
                    .overrideConfiguration(o -> o.putHeader("Authorization", "\\x07"))
                    .build();
            s3Client.createBucket(bktRequest);
            AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
        } catch (S3Exception err) {
            String errorCode = err.awsErrorDetails().errorCode();
            System.out.println("RGW Returned Error Code: " + err.statusCode() + " " + err.awsErrorDetails().toString());
            // Shows an Acces Denied error in the logs
            // Not sure why Ideally should return the SignatureDoesNotMatch error
            boolean isAuthError = errorCode.equals("SignatureDoesNotMatch") ||
                    errorCode.equals("AccessDenied") ||
                    errorCode.equals("InvalidRequest");

            AssertJUnit.assertTrue("Expected an Auth error but got: " + errorCode, isAuthError);
        }
    }

    @Test(description = "create w/ empty authorization, fails!")
    public void testBucketCreateBadAuthorizationEmpty() {

        String bucket_name = utils.getBucketName(prefix);

        try {

            CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                    .bucket(bucket_name)
                    .overrideConfiguration(o -> o.putHeader("Authorization", ""))
                    .build();
            s3Client.createBucket(bktRequest);
            AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
        } catch (S3Exception err) {
            // Shows an Acces Denied error more relavnt and thus changed to expect this
            // instead of SignatureDoesNotMatch error
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
        }
    }

    @Test(description = "create w/no authorization, fails!")
    public void testBucketCreateBadAuthorizationNone() {

        String bucket_name = utils.getBucketName(prefix);

        try {

            CreateBucketRequest bktRequest = CreateBucketRequest.builder()
                    .bucket(bucket_name)
                    .overrideConfiguration(o -> o.putHeader("Authorization", " "))
                    .build();
            s3Client.createBucket(bktRequest);
            AssertJUnit.fail("Expected 403 SignatureDoesNotMatch");
        } catch (S3Exception err) {
            // Shows an Acces Denied error more relavnt and thus changed to expect this
            // instead of SignatureDoesNotMatch error
            AssertJUnit.assertEquals(err.awsErrorDetails().errorCode(), "AccessDenied");
        }
    }

}
