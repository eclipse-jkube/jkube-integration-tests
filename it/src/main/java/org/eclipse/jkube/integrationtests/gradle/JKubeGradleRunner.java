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
package org.eclipse.jkube.integrationtests.gradle;

import org.gradle.testkit.runner.GradleRunner;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JKubeGradleRunner {

  private final GradleRunner gradleRunner;

  private final String module;

  private final Path projectPath;

  public JKubeGradleRunner(GradleRunner gradleRunner, String module, Path projectPath) {
    this.gradleRunner = gradleRunner;
    this.module = module;
    this.projectPath = projectPath;
  }

  public GradleRunner tasks(String... tasks) {
    return gradleRunner.withArguments(Stream.of(tasks)
      .map(s -> s.startsWith("-") ? s : ":" + module + ":" +s).collect(Collectors.toList()));
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
