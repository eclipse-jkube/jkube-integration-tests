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
package org.eclipse.jkube.integrationtests.assertions;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.openshift.api.model.Route;
import okhttp3.Response;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.hamcrest.Matcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertLabels;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class ServiceAssertion extends KubernetesClientAssertion<Service> {

  private static final String OC_ROUTE_ANNOTATION_TIMEOUT = "haproxy.router.openshift.io/timeout";
  private ServiceAssertion(JKubeCase jKubeCase, Service service) {
    super(jKubeCase, service);
  }

  public static Function<JKubeCase, ServiceAssertion> assertService(Service service) {
    return jKubeCase -> new ServiceAssertion(jKubeCase, service);
  }

  public static void assertServiceExists(JKubeCase jKubeCase, Matcher<Boolean> existsMatcher) {
    final boolean servicesExists = jKubeCase.getKubernetesClient().services().list().getItems().stream()
      .anyMatch(s -> s.getMetadata().getName().startsWith(jKubeCase.getApplication()));
    assertThat(servicesExists, existsMatcher);
  }

  public static ServiceAssertion awaitService(JKubeCase jKubeCase, String namespace) throws InterruptedException {
    final Service service = jKubeCase.getKubernetesClient().services()
      .inNamespace(namespace)
      .withName(jKubeCase.getApplication())
      .waitUntilCondition(Objects::nonNull, DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS);
    assertThat(service, notNullValue());
    assertLabels(jKubeCase).assertStandardLabels(service.getMetadata()::getLabels);
    assertLabels(jKubeCase).assertStandardLabels(service.getSpec()::getSelector);
    return assertService(service).apply(jKubeCase);
  }

  public ServiceAssertion assertExposed() {
    assertThat(getKubernetesResource().getMetadata().getLabels(), hasEntry("expose", "true"));
    return this;
  }

  public ServiceAssertion assertIsClusterIp() {
    assertThat(getKubernetesResource().getSpec().getType(), equalTo("ClusterIP"));
    return this;
  }

  public ServiceAssertion assertIsNodePort() {
    assertThat(getKubernetesResource().getSpec().getType(), equalTo("NodePort"));
    return this;
  }

  public ServiceAssertion assertPorts(Matcher<? super List<ServicePort>> portMatcher) {
    assertThat(getKubernetesResource().getSpec().getPorts(), portMatcher);
    return this;
  }

  public ServiceAssertion assertPort(String name, Integer port, boolean isNodePort) {
    assertPorts(hasItem(allOf(
      hasProperty("name", equalTo(name)),
      hasProperty("port", equalTo(port)),
      hasProperty("nodePort", isNodePort ? notNullValue() : nullValue())
    )));
    return this;
  }

  public ServiceAssertion assertNodePortResponse(String name, Matcher<? super String> responseBodyMatcher, String... path)
    throws Exception {

    final ServicePort port = getKubernetesResource().getSpec().getPorts().stream()
      .filter(sp -> sp.getName().equals(name))
      .filter(sp -> sp.getNodePort() != null)
      .findAny().orElse(null);
    assertThat(port, notNullValue());
    final String clusterHost = getClusterHost();
    final String host;
    if (isOpenShiftClient() && isNotLocal(clusterHost)) {
      host = openShiftRouteHost();
    } else {
      host = String.format("%s:%s", clusterHost, port.getNodePort());
    }
    final Response response = getWithRetry(String.format("http://%s/%s", host, String.join("/", path)));
    assertThat(response.body(), notNullValue());
    assertThat(response.body().string(), responseBodyMatcher);
    return this;
  }

  private String openShiftRouteHost() throws Exception {
    openShiftRouteIncreaseTimeout();
    final Route route = getOpenShiftClient().routes()
      .inNamespace(getKubernetesResource().getMetadata().getNamespace())
      .withName(getKubernetesResource().getMetadata().getName())
      .waitUntilCondition(r -> Optional.ofNullable(r)
          .filter(fr -> fr.getMetadata().getAnnotations().containsKey(OC_ROUTE_ANNOTATION_TIMEOUT))
          .isPresent(),
        DEFAULT_AWAIT_TIME_SECONDS, TimeUnit.SECONDS);
    assertThat(route, notNullValue());
    return route.getSpec().getHost();
  }

  private void openShiftRouteIncreaseTimeout() {
    getOpenShiftClient().routes()
      .inNamespace(getKubernetesResource().getMetadata().getNamespace())
      .withName(getKubernetesResource().getMetadata().getName())
      .edit().editMetadata()
      .addToAnnotations(OC_ROUTE_ANNOTATION_TIMEOUT, "30s")
      .endMetadata()
      .done();
  }

  private static boolean isNotLocal(String host) throws UnknownHostException {
    return !InetAddress.getByName(host).isLoopbackAddress();
  }
}

