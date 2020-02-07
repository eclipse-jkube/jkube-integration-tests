package org.eclipse.jkube.integrationtests.assertions;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.Request;
import okhttp3.Response;
import org.hamcrest.Matcher;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class ServiceAssertion extends KubernetesClientAssertion<Service> {

  private ServiceAssertion(KubernetesClient kubernetesClient, Service service) {
    super(kubernetesClient, service);
  }

  public static ServiceAssertion assertService(KubernetesClient kubernetesClient, Service service) {
    return new ServiceAssertion(kubernetesClient, service);
  }

  public static Service awaitService(KubernetesClient kubernetesClient, String namespace, String name)
    throws InterruptedException {

    final Service service = kubernetesClient.services()
      .inNamespace(namespace)
      .withName(name)
      .waitUntilCondition(Objects::nonNull, 10L, TimeUnit.SECONDS);
    assertThat(service, notNullValue());
    return service;
  }

  public void assertPort(String name, Integer port, boolean isNodePort) {
    assertThat(kubernetesResource.getSpec().getPorts(), hasItem(allOf(
      hasProperty("name", equalTo(name)),
      hasProperty("port", equalTo(port)),
      hasProperty("nodePort", isNodePort ? notNullValue() : nullValue())
    )));
  }

  public void assertNodePortResponse(String name, Matcher<? super String> responseBodyMatcher) throws Exception {
    final ServicePort port = kubernetesResource.getSpec().getPorts().stream()
      .filter(sp -> sp.getName().equals(name))
      .filter(sp -> sp.getNodePort() != null)
      .findAny().orElse(null);
    assertThat(port, notNullValue());
    final Response response = httpClient().newCall(new Request.Builder()
      .get()
      .url(String.format("http://%s:%s/", getClusterHost(), port.getNodePort())).build())
      .execute();
    assertThat(response.body(), notNullValue());
    assertThat(response.body().string(), responseBodyMatcher);
  }
}
