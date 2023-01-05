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

import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.GradleTest;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GradleExtension implements BaseExtension, BeforeAllCallback, BeforeEachCallback {


  private volatile boolean cleanBuild = false;

  @Override
  public ExtensionContext.Namespace getNamespace() {
    return ExtensionContext.Namespace.create(GradleExtension.class);
  }

  @Override
  public synchronized void beforeAll(ExtensionContext context) throws Exception {
    final var annotation = context.getRequiredTestClass().getAnnotation(GradleTest.class);
    var rootPath = Path.of("").toAbsolutePath();
    while (!rootPath.resolve(".github").toFile().exists()) {
      rootPath = rootPath.getParent();
    }
    var projectPath = rootPath.resolve("projects-to-be-tested").resolve("gradle");
    var gradleRunner = GradleRunner.create()
      .withGradleInstallation(getGradleInstallation())
      .withProjectDir(projectPath.toFile());
    if (annotation.forwardOutput()) {
      gradleRunner.forwardOutput();
    }
    final var jKubeGradleRunner = new JKubeGradleRunner(
      gradleRunner, String.join(":", annotation.project()), projectPath);
    if (!cleanBuild) {
      final List<String> cleanBuildTasks = new ArrayList<>();
      if (annotation.clean()) {
        cleanBuildTasks.add("clean");
        cleanBuildTasks.add("k8sConfigView");
        cleanBuildTasks.add("ocConfigView");
      }
      if (annotation.build()) {
        cleanBuildTasks.add("build");
      }
      jKubeGradleRunner.tasks(false, false, cleanBuildTasks.toArray(new String[0])).build();
    }
    cleanBuild = true;
    gradleRunner.withArguments(Collections.emptyList());
    getStore(context).put("jKubeGradleRunner", jKubeGradleRunner);
    for (Field field : extractFields(context, JKubeGradleRunner.class, f -> Modifier.isStatic(f.getModifiers()))) {
      setFieldValue(field, null, jKubeGradleRunner);
    }
  }


  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    for (Field field : extractFields(context, JKubeGradleRunner.class, f -> !Modifier.isStatic(f.getModifiers()))) {
      setFieldValue(field, context.getRequiredTestInstance(), getStore(context).get("jKubeGradleRunner"));
    }
  }

  private static File getGradleInstallation() throws IOException, InterruptedException {
    final var localGradle = CliUtils.runCommand("readlink -f $(which gradle)").getOutput();
    return new File(localGradle).getAbsoluteFile().getParentFile().getParentFile();
  }
}
