
 ## S3 compatibility tests

This is a set of integration tests for the S3 (AWS) interface of [RGW](http://docs.ceph.com/docs/mimic/radosgw/). 

It might also be useful for people implementing software
that exposes an S3-like API.

The test suite only covers the REST interface and uses [AWS Java SDK ](https://aws.amazon.com/sdk-for-java/) version 1.11.549 and [TestNG framework](https://testng.org/).

### Get the source code

Clone the repository

	git clone https://github.com/ceph/java_s3tests

### Edit Configuration

	cd java_s3tests
	cp config.properties.sample config.properties

The configuration file looks something like this:

	bucket_prefix = test-
	
	[s3main]
	
	access_key = 0555b35654ad1656d804
	access_secret = h7GhxuBLTrlhVUyxSPUKUV8r/2EI4ngqJxD7iBdBYLhwluN30JaT3Q==
	region = mexico
	endpoint = http://localhost:8000/
	port = 8000
	display_name = somename
	email = someone@gmail.com
	is_secure = false
	SSE = AES256
	kmskeyid = barbican_key_id

The credentials match the default S3 test user created by RGW.

#### RGW

The tests connect to the Ceph RGW, therefore one shoud start RGW beforehand and use the provided credentials. Details on building Ceph and starting RGW can be found in the [ceph repository](https://github.com/ceph/ceph).

The **s3tests.teuth.config.yaml** files is required for the Ceph test framework [Teuthology](http://docs.ceph.com/teuthology/docs/README.html). 
It is irrelevant for standalone testing.


### Install prerequisits
The **boostrap.sh** script will install **openjdk-8-jdk/java-1.8.0-openjdk, wget, unzip and gradle-4.7**. 
The default gradle intsall path is **/opt/gradle**. One can specify a custom location by passing it as an argument to the bootstrap.sh script

	./bootstrap.sh --path=/path/to/install/gradle

### Run the Tests
Run all tests with:

	gradle clean test

For more options check 

	gradle --help

There are three subsetests of tests: AWS4Test, BucketTest and ObjectTest. To run only one subset e.g. AWS4Test use:
	
	gradle clean test --tests AWS4Test

For a specific test in one of the subesets e.g. testMultipartUploadMultipleSizesLLAPIAWS4() from AWS4Test do:

	gradle clean test --tests AWS4Test.testMultipartUploadMultipleSizesLLAPIAWS4

### Debug output
It is possible to enable info/debug output from the tests as well as from the AWS API and the HTTP client. 
Edit the file 

	java_s3tests/src/main/resources/log4j.properties

in order to change the log levels.
