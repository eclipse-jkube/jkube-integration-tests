package org.eclipse.jkube.integrationtests.thorntail;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.util.Optional;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

@Tag(KUBERNETES)
@TestMethodOrder(OrderAnnotation.class)
public class ThorntailK8sITCase extends Thorntail {

  private KubernetesClient k;

  @BeforeEach
  void setUp() {
    k = new DefaultKubernetesClient();
  }

  @AfterEach
  void tearDown() {
    k.close();
    k = null;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertImageWasRecentlyBuilt("integration-tests", "thorntail-microprofile");
  }

  @Test
  @Order(2)
  @DisplayName("k8s:resource, should create manifests")
  void k8sResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:resource");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", PROJECT_THORNTAIL));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/thorntail-microprofile-deployment.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/kubernetes/thorntail-microprofile-service.yml"). exists(), equalTo(true));
  }

  @Test
  @Order(3)
  @DisplayName("k8s:apply, should deploy pod and service")
  @SuppressWarnings("unchecked")
  void k8sApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:apply");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldApplyResources(k);
    final Optional<Deployment> deployment = k.apps().deployments().list().getItems().stream()
      .filter(d -> d.getMetadata().getName().startsWith("thorntail-microprofile"))
      .findFirst();
    assertThat(deployment.isPresent(), equalTo(true));
    assertStandardLabels(deployment.get().getMetadata()::getLabels);
    final DeploymentSpec deploymentSpec = deployment.get().getSpec();
    assertThat(deploymentSpec.getReplicas(), equalTo(1));
    assertStandardLabels(deploymentSpec.getSelector()::getMatchLabels);
    final PodTemplateSpec ptSpec = deploymentSpec.getTemplate();
    assertStandardLabels(ptSpec.getMetadata()::getLabels);
    assertThat(ptSpec.getSpec().getContainers(), hasSize(1));
    final Container ptContainer = ptSpec.getSpec().getContainers().iterator().next();
    assertThat(ptContainer.getImage(), equalTo("integration-tests/thorntail-microprofile:latest"));
    assertThat(ptContainer.getName(), equalTo("thorntail-v2"));
    assertThat(ptContainer.getPorts(), hasSize(3));
    assertThat(ptContainer.getPorts(), hasItems(allOf(
      hasProperty("name", equalTo("http")),
      hasProperty("containerPort", equalTo(8080))
    )));
    assertThat(ptContainer.getEnv(), hasItems(allOf(
      hasProperty("name", equalTo("JAVA_OPTIONS")),
      hasProperty("value", equalTo("-Djava.net.preferIPv4Stack=true"))
    )));
  }

  @Test
  @Order(4)
  @DisplayName("k8s:undeploy, should delete all applied resources")
  void k8sUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:undeploy");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldDeleteAllAppliedResources(k);
    final boolean deploymentsExists = k.apps().deployments().list().getItems().stream()
      .anyMatch(d -> d.getMetadata().getName().startsWith("thorntail-microprofile"));
    assertThat(deploymentsExists, equalTo(false));
  }
}
