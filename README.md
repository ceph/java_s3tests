## S3 compatibility tests

This is a set of integration tests for the S3 (AWS) interface of [RGW](http://docs.ceph.com/docs/mimic/radosgw/). 

The test suite covers the REST interface and has been modernized to support **Java 21**. It currently maintains backward compatibility with **AWS Java SDK v1 (1.11.549)** while providing the infrastructure for **AWS Java SDK v2** migration.

### Prerequisites

* **Java**: OpenJDK 21
* **Build Tools**: Maven 3.9+ or Gradle 8.7+
* **RGW**: A running Ceph RadosGW instance (e.g., via `vstart.sh`)

### Get the source code

    git clone [https://github.com/ceph/java_s3tests](https://github.com/ceph/java_s3tests)
    cd java_s3tests

### Install Dependencies

The modernized **bootstrap.sh** script installs **OpenJDK 21**, **Maven**, and **Gradle 8.7**.

```
    chmod +x bootstrap.sh
    ./bootstrap.sh
```

### Edit Configuration

The tests require a configuration file to connect to an RGW instance. 

1. Go to the project directory:
   `cd java_s3tests`
2. Create your config file from the sample:
   `cp config.properties.sample config.properties`
3. Edit `config.properties` with your RGW credentials (access_key, secret_key, and host).

### Running the Tests

Either **Maven** (preferred for workunits) or **Gradle** , can be used for the testrun.

#### Using Maven
Run all tests:
```bash
mvn clean test
```
Run a specific test class:
```bash
mvn test -Dtest=BucketTest
```
Run a specific test method:
```bash
mvn test -Dtest=BucketTest#testBucketCreateReadDelete
```

#### Using Gradle
Run all tests:
```bash
gradle clean test
```
Run a specific subset:
```bash
gradle clean test --tests BucketTest
```
Run a specific method:
```bash
gradle clean test --tests BucketTest#testBucketCreateReadDelete
```

### Project Structure

* `src/main/java/S3.java`: The core Singleton provider. Use `getS3Client()` for SDK v1 or `getS3V2Client()` for SDK v2.
* `src/test/java/`: Contains the test suites (`AWS4Test`, `BucketTest`, `ObjectTest`).
* `src/test/resources/log4j.properties`: Shared logging configuration for both Maven and Gradle.

### Debugging
To change log levels for the AWS SDK or the TestNG execution, edit:
`src/test/resources/log4j.properties`

