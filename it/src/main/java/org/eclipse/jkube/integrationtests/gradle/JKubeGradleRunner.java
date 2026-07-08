/*
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
package org.eclipse.jkube.integrationtests.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.eclipse.jkube.integrationtests.AsyncUtil.executorService;
import static org.eclipse.jkube.integrationtests.JKubeCase.JKUBE_VERSION_SYSTEM_PROPERTY;

public class JKubeGradleRunner {

  private static final Logger log = LoggerFactory.getLogger(JKubeGradleRunner.class);
  private static final String JKUBE_VERSION_GRADLE_PROPERTY = JKUBE_VERSION_SYSTEM_PROPERTY;
  private final GradleRunner gradleRunner;

  private final String module;

  private final Path projectPath;

  public JKubeGradleRunner(GradleRunner gradleRunner, String module, Path projectPath) {
    this.gradleRunner = gradleRunner;
    this.module = module;
    this.projectPath = projectPath;
  }

  public GradleRunner tasks(String... tasks) {
    return tasks(true, true, tasks);
  }

  public GradleRunner tasks(boolean offline, boolean forModule, String... tasks) {
    final ArrayList<String> arguments = new ArrayList<>();
    if (offline) {
      arguments.add("--offline");
    }
    Stream.of(tasks)
      .map(s -> forModule && !s.startsWith("-") ? ":" + module + ":" + s : s)
      .forEach(arguments::add);
    final var jKubeVersion = System.getProperty(JKUBE_VERSION_SYSTEM_PROPERTY);
    if (jKubeVersion != null) {
      arguments.add(0, "-P" + JKUBE_VERSION_GRADLE_PROPERTY + "=" + jKubeVersion);
    }
    log.info("Running 'gradle {}'", String.join(" ", arguments));
    return gradleRunner.withArguments(arguments);
  }

  public CompletableFuture<BuildResult> tasksAsync(OutputStream out, String... tasks) {
    return tasksAsync(out, true, true, tasks);
  }

  public CompletableFuture<BuildResult> tasksAsync(OutputStream out, boolean offline, boolean forModule, String... tasks) {
    final GradleRunner configuredRunner = tasks(offline, forModule, tasks);
    if (out != null) {
      configuredRunner.forwardStdOutput(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      configuredRunner.forwardStdError(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }
    final CompletableFuture<BuildResult> future = new CompletableFuture<>();
    final var asyncRun = CompletableFuture.runAsync(() -> {
      try {
        future.complete(configuredRunner.build());
      } catch (Exception e) {
        future.completeExceptionally(e);
      }
    }, executorService());
    future.whenCompleteAsync((result, throwable) -> {
      if (!asyncRun.isDone()) {
        asyncRun.cancel(true);
      }
    });
    return future;
  }

  public Path getProjectPath() {
    return projectPath;
  }

  public Path getModulePath() {
    var modulePath = projectPath;
    for (String segment : module.split(":")) {
      modulePath = modulePath.resolve(segment);
    }
    return modulePath;
  }
}
