package org.eclipse.jkube.integrationtests.docker;

import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class RegistryExtension implements BeforeAllCallback, CloseableResource {

  private static boolean started = false;

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    if (!started) {
      started = true;
      context.getRoot().getStore(GLOBAL).put("Docker Registry Callback Hook", this);
      final CliUtils.CliResult dockerRegistry = CliUtils.runCommand("docker run -d -p 5000:5000 --name registry registry:2");
      assertThat(dockerRegistry.getExitCode(), Matchers.equalTo(0));
    }
  }

  @Override
  public void close() throws Exception {
    CliUtils.runCommand("docker rm -f registry");
  }
}