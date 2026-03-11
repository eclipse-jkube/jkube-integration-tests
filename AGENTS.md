# JKube Integration Tests - AI Agents Instructions

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

This file provides guidance to AI coding agents (GitHub Copilot, Claude Code, etc.) when working with code in this repository.

## Project Overview

E2E/integration/regression test suite for Eclipse JKube (https://github.com/eclipse-jkube/jkube).
Tests verify JKube Maven and Gradle plugin goals (`k8s:build`, `k8s:resource`, `k8s:apply`, `k8s:undeploy`, etc.) against real Kubernetes and OpenShift clusters using sample projects.
Built with Java 11, Maven, JUnit 5, Hamcrest, and Fabric8 Kubernetes Client.

## Working Effectively

### Prerequisites

- Java 11+
- Maven (via `./mvnw` wrapper)
- A running Kubernetes cluster (e.g., Minikube) or OpenShift cluster
- Docker daemon running (for image build tests)
- Gradle (for Gradle-based tests, uses local installation)

### Build Commands

Download dependencies and build all sample projects (no tests):
```shell
./mvnw -B -DskipTests clean install
```

### Testing

Tests require a running Kubernetes/OpenShift cluster. They are long-running operations that deploy real workloads.

**IMPORTANT: Tests interact with a live cluster and can take a very long time. NEVER cancel a running test - it may leave resources deployed on the cluster.**

Run a specific test suite on Kubernetes:
```shell
./mvnw -B -PKubernetes,springboot clean verify
```

Run a specific test suite on OpenShift:
```shell
./mvnw -B -POpenShift,springboot clean verify
```

Override the JKube version under test:
```shell
./mvnw -B -PKubernetes,springboot clean verify -Djkube.version=1.18.0
```

Run a specific test class:
```shell
./mvnw -B -PKubernetes,other clean verify -Dit.test="*Vertx*"
```

Available test suite profiles: `dockerfile`, `other`, `quarkus`, `quarkus-native`, `springboot`, `webapp`

Platform profiles: `Kubernetes`, `OpenShift`, `OpenShiftOSCI`, `Windows`

**Warning:** If you've run any other suite in your local environment, run `./mvnw clean` (without profiles) first to delete all `target` directories, else other profiles may get auto-activated.

## Architecture

### Technical Structure

```
jkube-integration-tests/
├── it/                              # Integration test module
│   ├── src/main/java/               # Test infrastructure & utilities
│   │   └── org/eclipse/jkube/integrationtests/
│   │       ├── JKubeCase.java       # Core test interface
│   │       ├── OpenShiftCase.java   # OpenShift test interface
│   │       ├── Project.java         # Project info interface
│   │       ├── assertions/          # Custom assertion classes (JKubeAssertions)
│   │       ├── cli/                 # CLI utilities
│   │       ├── docker/              # Docker utilities (DockerUtils)
│   │       ├── gradle/              # Gradle runner utilities
│   │       └── jupiter/api/         # JUnit 5 extensions (@Application, @Report, @Gradle, @DockerRegistry)
│   └── src/test/java/               # Test cases
│       └── org/eclipse/jkube/integrationtests/
│           ├── Tags.java            # Platform tag constants (KUBERNETES, OPEN_SHIFT, etc.)
│           ├── Locks.java           # Resource lock constants for parallel test control
│           ├── springboot/          # Spring Boot tests (complete, zeroconfig, watch, etc.)
│           ├── webapp/              # Web app tests (jetty, wildfly, tomcat, etc.)
│           ├── quarkus/             # Quarkus tests (rest, native)
│           ├── vertx/               # Vert.x tests
│           ├── karaf/               # Karaf/Camel tests
│           ├── dockerfile/          # Dockerfile configuration tests
│           ├── dsl/                 # Gradle DSL tests
│           ├── buildpacks/          # Buildpacks tests
│           ├── openliberty/         # OpenLiberty tests
│           ├── wildfly/jar/         # WildFly JAR tests
│           ├── windows/             # Windows platform tests
│           └── assertions/          # Test-scoped assertion classes
├── projects-to-be-tested/           # Sample projects deployed during tests
│   ├── maven/                       # Maven-based sample projects
│   │   ├── spring/                  # Spring Boot variants (8 projects)
│   │   ├── webapp/                  # Web app variants (10 projects)
│   │   ├── quarkus/                 # Quarkus variants (3 projects)
│   │   ├── vertx/                   # Vert.x project
│   │   ├── karaf/                   # Karaf/Camel project
│   │   ├── dockerfile/              # Dockerfile variants (5 projects)
│   │   ├── buildpacks/              # Buildpacks project
│   │   └── ...                      # Other framework projects
│   └── gradle/                      # Gradle-based sample projects
│       ├── sb-zero-config/          # Spring Boot zero-config
│       └── dsl/                     # Gradle DSL project
└── pom.xml                          # Parent POM (profiles, dependency management)
```

### Design Patterns

- **Interface-based test hierarchy**: Tests implement `JKubeCase` and framework-specific interfaces (`MavenCase`, etc.). Abstract base classes (e.g., `Complete`) provide shared logic, with concrete classes per platform (`CompleteK8sITCase`, `CompleteOcDockerITCase`).
- **Ordered test methods**: Tests use `@TestMethodOrder(OrderAnnotation.class)` with `@Order(n)` because JKube goals must execute in sequence: build -> resource -> apply -> verify -> undeploy.
- **Resource locking**: JUnit 5 `@ResourceLock` prevents cluster-intensive tests from running concurrently.
- **Custom annotations**: `@Application("name")` specifies the application name, `@Report` enables test reporting, `@Gradle` injects the Gradle runner, `@DockerRegistry` sets up a Docker registry.
- **Profile-based test selection**: Maven profiles control which test suites and platform-specific tests run. Test suite profiles auto-activate based on `target/` directory existence.
- **Fluent custom assertions**: Domain-specific assertion classes (`DeploymentAssertion`, `PodAssertion`, `ServiceAssertion`, `DockerAssertion`, `YamlAssertion`, etc.) provide fluent APIs for verifying Kubernetes resources.

## Code Style

- **License header**: All Java files must include the EPL-2.0 license header. Run `./mvnw license:check` to verify, `./mvnw license:format` to apply.
- **Java version**: Java 11 for the test module (`it/`), Java 8 for sample projects.
- **Test class naming**: `*ITCase.java` suffix (required by Maven Failsafe Plugin).
- **Test method naming**: Named after the JKube goal being tested (e.g., `k8sBuild()`, `k8sResource()`, `k8sApply()`, `k8sUndeploy()`).
- **Assertions**: Hamcrest matchers with `assertThat()`. Custom assertion classes for Kubernetes resources.
- **Imports**: Static imports for assertion methods, Hamcrest matchers, and constants.

## Testing Guidelines

### Test Structure

1. **Ordered integration tests**: Tests follow a sequential lifecycle matching JKube plugin goals. Use `@Order` annotations to ensure correct execution order: build (1) -> resource (2) -> apply (3) -> log (4) -> undeploy (last).

2. **Platform inheritance**: Create an abstract base class per project (e.g., `Complete`) that extends `BaseMavenCase` and implements project-specific interfaces. Then create concrete classes per platform:
   - `*K8sITCase` - Kubernetes tests tagged with `@Tag(KUBERNETES)`
   - `*OcITCase` / `*OcDockerITCase` - OpenShift tests tagged with `@Tag(OPEN_SHIFT)`

3. **Resource locks**: Use `@ResourceLock` for cluster-intensive operations to prevent conflicts:
   ```java
   @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
   ```

4. **Assertions**: Use the custom fluent assertion classes rather than raw Kubernetes client calls:
   ```java
   awaitDeployment(this, namespace)
     .assertReplicas(equalTo(1))
     .assertContainers(hasSize(1));
   ```

5. **Given/When/Then**: Test methods follow this pattern:
   ```java
   @Test
   @Order(1)
   @DisplayName("k8s:build, should create image")
   void k8sBuild() throws Exception {
     // When
     final InvocationResult invocationResult = maven("k8s:build");
     // Then
     assertInvocation(invocationResult);
     assertImageWasRecentlyBuilt("integration-tests", getApplication());
   }
   ```

### Adding New Tests

1. Create sample project under `projects-to-be-tested/maven/<framework>/` or `projects-to-be-tested/gradle/`
2. Add the module to the appropriate profile in root `pom.xml`
3. Create abstract base test class under `it/src/test/java/.../` implementing `JKubeCase` and `MavenCase`
4. Create platform-specific concrete test classes (`*K8sITCase`, `*OcITCase`)
5. Ensure the test class is included in the failsafe plugin's `<includes>` pattern in root `pom.xml`

## Common Tasks

### Adding a new sample project to test
1. Create project directory under `projects-to-be-tested/maven/<category>/`
2. Add the module to the matching profile's `<modules>` in root `pom.xml`
3. Add license headers: `./mvnw license:format`

### Running license checks
```shell
./mvnw license:check
./mvnw license:format  # auto-fix missing headers
```

### Cleaning up cluster resources after a failed test
If a test fails mid-execution, resources may remain deployed. Clean up manually:
```shell
kubectl delete all -l app=<application-name>
```

## Troubleshooting

- **Profile auto-activation**: Profiles activate based on `target/` directory existence. Run `./mvnw clean` without profiles to reset.
- **Docker daemon**: Tests that build images require a Docker daemon. With Minikube, use `eval $(minikube docker-env)` to point Docker CLI at Minikube's daemon.
- **Gradle tests**: Use local Gradle installation (not wrapper) due to CI pipeline compatibility issues.
- **Flaky tests**: Some tests depend on cluster timing (pod readiness, service availability). The test framework includes await/retry logic via custom assertions.
