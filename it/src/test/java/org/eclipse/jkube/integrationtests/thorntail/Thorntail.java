package org.eclipse.jkube.integrationtests.thorntail;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jkube.integrationtests.PodReadyWatcher;
import org.eclipse.jkube.integrationtests.maven.MavenUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertGlobalLabels;
import static org.eclipse.jkube.integrationtests.assertions.LabelAssertion.assertLabels;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.assertPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.assertService;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public class Thorntail {

  static final String PROJECT_THORNTAIL = "projects-to-be-tested/thorntail/microprofile";

  final void assertThatShouldApplyResources(KubernetesClient kc) throws Exception {
    final PodReadyWatcher podWatcher = new PodReadyWatcher();
    kc.pods().withLabel("app", "thorntail-microprofile").watch(podWatcher);
    final Pod pod = podWatcher.await(30L, TimeUnit.SECONDS);
    assertThat(pod, notNullValue());
    assertThat(pod.getMetadata().getName(), startsWith("thorntail-microprofile"));
    assertStandardLabels(pod.getMetadata()::getLabels);
    assertPod(kc, pod).logContains("Deployed \"thorntail-microprofile-0.0.0-SNAPSHOT.war\"", 60);
    final Service service = awaitService(kc, pod.getMetadata().getNamespace(), "thorntail-microprofile");
    assertStandardLabels(service.getMetadata()::getLabels);
    assertThat(service.getMetadata().getLabels(), hasEntry("expose", "true"));
    assertStandardLabels(service.getSpec()::getSelector);
    assertThat(service.getSpec().getPorts(), hasSize(1));
    assertThat(service.getSpec().getType(), equalTo("NodePort"));
    assertService(kc, service).assertPort("http", 8080, true);
    assertService(kc, service).assertNodePortResponse("http", equalTo("JKube from Thorntail rocks!"));
  }

  final void assertThatShouldDeleteAllAppliedResources(KubernetesClient kc) {
    final Optional<Pod> matchingPod = kc.pods().list().getItems().stream()
      .filter(p -> p.getMetadata().getName().startsWith("thorntail-microprofile"))
      .filter(((Predicate<Pod>)(p -> p.getMetadata().getName().endsWith("-build"))).negate())
      .findAny();
    final Function<Pod, Pod> refreshPod = pod ->
      kc.pods().withName(pod.getMetadata().getName()).get();
    matchingPod.map(refreshPod).ifPresent(updatedPod ->
      assertThat(updatedPod.getMetadata().getDeletionTimestamp(), notNullValue()));
    final boolean servicesExist = kc.services().list().getItems().stream()
      .anyMatch(s -> s.getMetadata().getName().startsWith("thorntail-microprofile"));
    assertThat(servicesExist, equalTo(false));
  }

  static InvocationResult maven(String goal)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties());
  }

  static InvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {

    return MavenUtils.execute(i -> {
      i.setBaseDirectory(new File("../"));
      i.setProjects(Collections.singletonList(PROJECT_THORNTAIL));
      i.setGoals(Collections.singletonList(goal));
      i.setProperties(properties);
    });
  }

  static void assertStandardLabels(Supplier<Map<String, String>> labelSupplier) {
    assertGlobalLabels(labelSupplier);
    assertLabels(labelSupplier, hasEntry("app", "thorntail-microprofile"));
  }
}
