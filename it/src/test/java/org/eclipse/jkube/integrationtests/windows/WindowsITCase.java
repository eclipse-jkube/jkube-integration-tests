package org.eclipse.jkube.integrationtests.windows;

import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.Collections;

import static org.eclipse.jkube.integrationtests.Tags.WINDOWS;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.hamcrest.MatcherAssert.assertThat;

@Tag(WINDOWS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WindowsITCase extends BaseMavenCase {

  private static final String PROJECT_ZERO_CONFIG = "projects-to-be-tested/spring-boot/zero-config";

  @Override
  public String getProject() {
    return PROJECT_ZERO_CONFIG;
  }

  @Test
  @Order(1)
  @DisplayName("k8s:build, should create image")
  void k8sBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("k8s:build");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertImageWasRecentlyBuilt("integration-tests", "spring-boot-zero-config");
  }

  @Override
  protected InvocationResult maven(String goal) throws IOException, InterruptedException, MavenInvocationException {
    return super.maven(goal, null, ir -> ir.setProfiles(Collections.singletonList("Windows")));
  }
}
