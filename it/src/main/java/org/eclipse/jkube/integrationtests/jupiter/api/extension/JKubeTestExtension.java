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

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.eclipse.jkube.integrationtests.DefaultJKubeCase;
import org.eclipse.jkube.integrationtests.JKubeCase;
import org.eclipse.jkube.integrationtests.jupiter.api.GradleTest;
import org.eclipse.jkube.integrationtests.jupiter.api.MavenTest;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class JKubeTestExtension extends BaseExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {
  @Override
  ExtensionContext.Namespace getNamespace() {
    return ExtensionContext.Namespace.create(JKubeTestExtension.class);
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    final var gradle = context.getRequiredTestClass().getAnnotation(GradleTest.class);
    final var maven = context.getRequiredTestClass().getAnnotation(MavenTest.class);
    final String application;
    if (gradle != null) {
      application = String.join("-", gradle.project());
    } else {
      application = maven.value();
    }
    final var jKubeCase = new DefaultJKubeCase(application, new KubernetesClientBuilder().build());
    getStore(context).put("jKubeCase", jKubeCase);
    for (Field field : extractFields(context, JKubeCase.class, f -> Modifier.isStatic(f.getModifiers()))) {
      setFieldValue(field, null, jKubeCase);
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    for (Field field : extractFields(context, JKubeCase.class, f -> !Modifier.isStatic(f.getModifiers()))) {
      setFieldValue(field, context.getRequiredTestInstance(), getStore(context).get("jKubeCase"));
    }

  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    getStore(context).get("jKubeCase", JKubeCase.class).getKubernetesClient().close();
  }
}
