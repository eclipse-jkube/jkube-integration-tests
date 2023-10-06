package org.eclipse.jkube.integrationtests.springboot.zeroconfigfatjar;

import io.fabric8.openshift.api.model.ImageStream;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.OpenShiftCase;
import org.eclipse.jkube.integrationtests.maven.MavenCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;


@Tag(OPEN_SHIFT)
class ZeroConfigFatJarOcDockerITCase extends ZeroConfigFatJar implements MavenCase, OpenShiftCase {
  @Test
  @Order(1)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:build, with jkube.build.strategy=docker, should create image")
  void ocBuild() throws Exception {
    // Given
    Properties properties = new Properties();
    properties.put("jkube.build.strategy", "docker");
    // When
    final InvocationResult invocationResult = maven("oc:build", properties);
    // Then
    assertInvocation(invocationResult);
    final ImageStream is = getOpenShiftClient().imageStreams().withName(getApplication()).get();
    assertThat(is, notNullValue());
    assertThat(is.getStatus().getTags().iterator().next().getTag(), equalTo("latest"));
    assertOpenShiftDockerBuildCompletedWithLogs(
      "FROM quay.io/jkube/jkube-java:",
      "ENV JAVA_APP_DIR=/deployments",
      "EXPOSE 8080 8778 9779",
      "COPY /deployments /deployments/",
      "WORKDIR /deployment");
    getOpenShiftClient().buildConfigs().withLabel("app", getApplication()).delete();
  }
}
