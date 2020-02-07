package org.eclipse.jkube.integrationtests.assertions;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;

import java.net.MalformedURLException;
import java.net.URL;

public class KubernetesClientAssertion<T extends KubernetesResource> {

  private static OkHttpClient okHttpClient;

  synchronized static OkHttpClient httpClient() {
    if (okHttpClient == null) {
      okHttpClient = new OkHttpClient.Builder().build();
    }
    return okHttpClient;
  }

  final KubernetesClient kubernetesClient;
  final T kubernetesResource;

  KubernetesClientAssertion(KubernetesClient kubernetesClient, T kubernetesResource) {
    this.kubernetesClient = kubernetesClient;
    this.kubernetesResource = kubernetesResource;
  }

  final String getClusterHost() throws MalformedURLException {
    return new URL(kubernetesClient.getConfiguration().getMasterUrl()).getHost();
  }
}
