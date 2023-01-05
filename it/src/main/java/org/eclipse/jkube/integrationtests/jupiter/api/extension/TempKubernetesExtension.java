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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @deprecated to be replaced with KubernetesClient KubernetesNamespacedTestExtension
 */
@Deprecated
public class TempKubernetesExtension implements BaseExtension, BeforeAllCallback, AfterAllCallback {

  @Override
  public ExtensionContext.Namespace getNamespace() {
    return ExtensionContext.Namespace.create(TempKubernetesExtension.class);
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    final var client = new KubernetesClientBuilder().build();
    getStore(context).put(KubernetesClient.class, client);
    for (Field field : extractFields(context, KubernetesClient.class, f -> Modifier.isStatic(f.getModifiers()))) {
      setFieldValue(field, null, client);
    }

  }
  @Override
  public void afterAll(ExtensionContext context) {
    getStore(context).get(KubernetesClient.class, KubernetesClient.class).close();
  }
}
