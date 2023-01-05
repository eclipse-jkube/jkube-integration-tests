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
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @deprecated to be replaced with KubernetesClient KubernetesNamespacedTestExtension
 */
@Deprecated
public class TempKubernetesExtension implements HasKubernetesClient, BeforeAllCallback, AfterAllCallback {

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    for (Field field : extractFields(context, KubernetesClient.class, f -> Modifier.isStatic(f.getModifiers()))) {
      setFieldValue(field, null, getClient(context));
    }

  }
  @Override
  public void afterAll(ExtensionContext context) {
    // Note that the ThreadPoolExecutor in OkHttp's RealConnectionPool is shared amongst all the OkHttp client
    // instances. This means that closing one OkHttp client instance effectively closes all the others.
    // In order to be able to use this safely, we should transition to one of the other HttpClient implementations
    getClient(context).close();
  }
}
