package org.eclipse.jkube.integrationtests.vertx;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.client.OpenShiftClient;
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

import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Tag(OPEN_SHIFT)
@TestMethodOrder(OrderAnnotation.class)
public class VertxOcITCase extends Vertx {

  private OpenShiftClient oc;

  @BeforeEach
  void setUp() {
    oc = new DefaultKubernetesClient().adapt(OpenShiftClient.class);
  }

  @AfterEach
  void tearDown() {
    oc.close();
    oc = null;
  }

  @Test
  @Order(1)
  @DisplayName("oc:build, in docker mode, should create image")
  void ocBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:build");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final ImageStream is = oc.imageStreams().withName("vertx-simplest").get();
    assertThat(is, notNullValue());
    assertThat(is.getStatus().getTags().iterator().next().getTag(), equalTo("latest"));
  }

  @Test
  @Order(2)
  @DisplayName("oc:resource, should create manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", PROJECT_ZERO_CONFIG));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/vertx-simplest-deploymentconfig.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/vertx-simplest-route.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/vertx-simplest-service.yml"). exists(), equalTo(true));
  }

  @Test
  @Order(3)
  @DisplayName("oc:apply, should deploy pod and service")
  void ocApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:apply");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldApplyResources(oc);
  }

  @Test
  @Order(4)
  @DisplayName("oc:undeploy, should delete all applied resources")
  void ocUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:undeploy");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldDeleteAllAppliedResources(oc);
  }
}
