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
package org.eclipse.jkube.integrationtests.springboot.crd;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.maven.BaseMavenCase;
import org.eclipse.jkube.integrationtests.springboot.crd.v1beta1.Framework;

import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

abstract class CustomResourceApp extends BaseMavenCase implements JKubeCase {

  private static final String PROJECT_CUSTOMRESOURCE = "projects-to-be-tested/maven/spring-boot/crd";
  private static final String FRAMEWORKS_CRD = "frameworks.jkube.eclipse.org";

  @Override
  public String getProject() {
    return PROJECT_CUSTOMRESOURCE;
  }

  @Override
  public String getApplication() {
    return "spring-boot-crd";
  }

  final Pod assertThatShouldApplyResources() throws Exception {
    final Pod pod = awaitPod(this)
      .logContains("Started CustomResourceApplication in", 40)
      .getKubernetesResource();
    String namespace = pod.getMetadata().getNamespace();
    assertFrameworkCustomResourceDefinitionApplied(this);
    assertFrameworkCustomResourcesApplied(this, namespace);
    awaitService(this, namespace)
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, true)
      .assertNodePortResponse("http",
        containsString("[\"quarkus\",\"spring-boot\",\"vertx\"]"));
    return pod;
  }

  protected void assertCustomResourceDefinitionDeleted(JKubeCase jKubeCase) {
    assertThat(jKubeCase.getKubernetesClient().apiextensions().v1().customResourceDefinitions().withName(FRAMEWORKS_CRD).get(), nullValue());
  }

  protected void assertFrameworkCustomResourcesApplied(JKubeCase jKubeCase, String namespace) {
    MixedOperation<Framework, KubernetesResourceList<Framework>, Resource<Framework>> frameworkClient =
      jKubeCase.getKubernetesClient().resources(Framework.class);
    KubernetesResourceList<Framework> frameworks = frameworkClient.inNamespace(namespace).list();
    assertThat(frameworks, notNullValue());
    assertThat(frameworks.getItems(), hasSize(3));
  }

  protected void assertFrameworkCustomResourceDefinitionApplied(JKubeCase jKubeCase) {
    CustomResourceDefinition crd = jKubeCase.getKubernetesClient().apiextensions().v1().customResourceDefinitions().withName(FRAMEWORKS_CRD).get();
    assertThat(crd, notNullValue());
  }

}
