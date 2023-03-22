/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.integrationtests.jupiter.api.extension;

import io.fabric8.junit.jupiter.HasKubernetesClient;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistry;
import org.eclipse.jkube.integrationtests.jupiter.api.DockerRegistryHost;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.stream.Stream;

import static org.eclipse.jkube.integrationtests.cli.CliUtils.isWindows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

public class RegistryExtension implements HasKubernetesClient, BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

  private static final Logger log = LoggerFactory.getLogger(RegistryExtension.class);

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    final var annotation = context.getRequiredTestClass().getAnnotation(DockerRegistry.class);
    CliUtils.runCommand("docker rm -f " + getName(annotation));
    log.debug(() -> "Starting Docker Registry Extension");
    final RegistryInfo dockerRegistry;
    if (isWindows()) {
      dockerRegistry = startWindowsDockerRegistry(annotation);
    } else {
      dockerRegistry = startKubernetesDockerRegistry(context);
    }
    assertThat(dockerRegistry.assertionContext, dockerRegistry.host, notNullValue());
    getStore(context).put(RegistryInfo.class, dockerRegistry);
    log.debug(() -> "Docker Registry started successfully");
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    final var registryInfo = getStore(context).get(RegistryInfo.class, RegistryInfo.class);
    for (Field f : context.getRequiredTestClass().getDeclaredFields()) {
      if (f.isAnnotationPresent(DockerRegistryHost.class) && f.getType() == String.class) {
        setFieldValue(f, context.getRequiredTestInstance(), registryInfo.host + ":" + registryInfo.port);
      }
    }
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    log.debug(() -> "Closing Docker Registry");
    CliUtils.runCommand("docker stop " + getName(context.getRequiredTestClass().getAnnotation(DockerRegistry.class)));
    if (!isWindows()) {
      final var registryInfo = getStore(context).get(RegistryInfo.class, RegistryInfo.class);
      final var client = getClient(context);
      Stream.of(
        client.pods().withName(registryInfo.name),
        client.services().withName(registryInfo.name)
      ).forEach(r -> r.withGracePeriod(0L).delete());
    }
  }

  private RegistryInfo startKubernetesDockerRegistry(ExtensionContext context) throws IOException, InterruptedException {
    final var name = "registry" + UUID.randomUUID().toString().replace("-", "");
    final var client = getClient(context);
    final var ip = CliUtils.runCommand("minikube ip").getOutput().trim();
    final var dockerRegistry = client.run().withName(name).withImage("registry:2")
      .withNewRunConfig()
      .addToLabels("app", "docker-registry").addToLabels("group", "jkube-it").done();
    final var service = client.services().resource(new ServiceBuilder()
      .withNewMetadata().withName(name)
      .addToLabels("app", "docker-registry").addToLabels("group", "jkube-it").endMetadata()
      .withNewSpec().withType("NodePort").withSelector(dockerRegistry.getMetadata().getLabels())
      .addNewPort().withName("http").withPort(5000).withTargetPort(new IntOrString(5000)).endPort().endSpec()
      .build())
      .serverSideApply(); // Unsupported in K8s 1.12
    return new RegistryInfo(name, ip, service.getSpec().getPorts().get(0).getNodePort(), null);
  }

  private static RegistryInfo startWindowsDockerRegistry(DockerRegistry dockerRegistry) throws IOException, InterruptedException {
    log.debug(() -> "Starting Windows specific Docker Registry");
    final var registry = new File("C:\\registry");
    if (!registry.exists() && !registry.mkdirs()) {
      throw new IllegalStateException("Directory C:\\registry cannot be created");
    }
    final var result = CliUtils.runCommand("docker run --rm -d -p " + dockerRegistry.port() +":5000 --name " +
      getName(dockerRegistry) + " -v C:\\registry:C:\\registry marcnuri/docker-registry-windows:ltsc2022");
    if (result.getExitCode() != 0) {
      return new RegistryInfo(null, null, -1, result.getOutput());
    }
    return new RegistryInfo("windows-docker-registry", getDockerHost(), dockerRegistry.port(), result.getOutput());
  }

  private static String getName(DockerRegistry dockerRegistry) {
    return dockerRegistry.containerName() + "-" + dockerRegistry.port();
  }

  private static String getDockerHost() {
    final var dockerHost = System.getenv("DOCKER_HOST");
    if (dockerHost == null) {
      return "localhost";
    } else {
      return dockerHost.replaceAll("^tcp://", "")
        .replaceAll(":\\d+$", "");
    }
  }

  private static final class RegistryInfo {
    private final String name;
    private final String host;
    private final int port;
    private final String assertionContext;

    public RegistryInfo(String name, String host, int port, String assertionContext) {
      this.name = name;
      this.host = host;
      this.port = port;
      this.assertionContext = assertionContext;
    }
  }
}
