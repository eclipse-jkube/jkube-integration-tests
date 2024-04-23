# JKube Integration Tests

This project hosts Integration test suites for https://github.com/eclipse-jkube/jkube.

## Test Structure

In order to be able to run the tests in a CI environment without hogging the resources
and to provide specific tests for OpenShift clusters, test suites are divided in the following
way.

### Tags

There are three [tags](src/test/java/org/eclipse/jkube/integrationtests/Tags.java) which match with a maven profile
(`Kubernetes`, `OpenShift`, `OpenShiftOSCI` & `Windows`).

Each of these tags should be applied to a specific test case suite in order to execute
the given tests only when that profile is specified.

e.g. when running with a standard k8s cluster `mvn verify -PKubernetes`

### Suite groups

Tests are divided into several groups in order to run only a set of tests.

As of now, test groups are: `dockerfile`, `other`, `quarkus`, `quarkus-native`, `springboot`, `webapp`

Following the same principle as with tags, in order to activate a given set of tests,
a profile matching the group name defined above must be specified-

e.g. Spring Boot tests when running in an Open Shift cluster `mvn verify -POpenShift,springboot`

**Warning:** If you've run any other suite in your local environment run a "profile-less" `mvn clean`
command prior to anything else so that all `target` directories get deleted,
else other profiles will get triggered.

### Running a specific test

In addition to selecting a test group, you can run a specific test, or a set of tests, by providing
the `it.test` [Maven Failsafe Plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html)
configuration property.

For example, to run just the Vert.x tests you can run:

```shell
mvn verify -PKubernetes,other -Dit.test="*Vertx*"
```

## Gradle

The Gradle tests run using the local Gradle installation. This approach was selected due to issues when running the
tests on the GitHub Actions CI pipeline environment.
