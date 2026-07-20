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
package org.eclipse.jkube.integrationtests.springboot.watch;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

abstract class Watch implements JKubeCase {

  private static final Logger log = LoggerFactory.getLogger(Watch.class);
  private static final Pattern PORT_FORWARD_URL_PATTERN = Pattern.compile("http://localhost:(\\d+)");

  static final String MAVEN_APPLICATION = "spring-boot-watch";
  static final String GRADLE_APPLICATION = "sb-watch";

  static KubernetesClient kubernetesClient;
  File fileToChange;
  String originalFileContent;
  Pod originalPod;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  final Pod assertThatShouldApplyResources(String expectedMessage) throws Exception {
    final Pod pod = awaitPod(this)
      .logContains("Started SpringBootWatchApplication in", 120)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http", equalTo(expectedMessage));
    return pod;
  }

  // The Fabric8 port-forward WebSocket has no keepalive pings, so the tunnel dies during
  // the idle period between RemoteSpringApplication startup and the DevTools file upload.
  // On CI the build step takes long enough for the idle timeout to fire, causing
  // SocketException in ClassPathChangeUploader. Periodic HTTP GETs keep the tunnel alive.
  static ScheduledFuture<?> startPortForwardKeepalive(String watchOutput) {
    final Matcher matcher = PORT_FORWARD_URL_PATTERN.matcher(watchOutput);
    if (!matcher.find()) {
      log.warn("Could not extract port-forward URL from watch output, skipping keepalive");
      return null;
    }
    final String url = "http://localhost:" + matcher.group(1) + "/";
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      final Thread t = new Thread(r, "port-forward-keepalive");
      t.setDaemon(true);
      return t;
    });
    return scheduler.scheduleAtFixedRate(() -> {
      try {
        final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.getResponseCode();
        conn.disconnect();
      } catch (Exception ignored) {
      }
    }, 0, 5, TimeUnit.SECONDS);
  }

  static void stopKeepalive(ScheduledFuture<?> keepalive) {
    if (keepalive != null) {
      keepalive.cancel(true);
    }
  }
}
