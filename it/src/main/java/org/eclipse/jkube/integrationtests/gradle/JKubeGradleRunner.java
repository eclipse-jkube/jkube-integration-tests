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
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;

import static org.eclipse.jkube.integrationtests.JKubeCase.JKUBE_VERSION_SYSTEM_PROPERTY;

public class JKubeGradleRunner {

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
    final ArrayList<String> arguments = new ArrayList<>();
    arguments.add("--offline");
    Stream.of(tasks)
      .map(s -> s.startsWith("-") ? s : ":" + module + ":" +s)
      .forEach(arguments::add);
    Optional.ofNullable(System.getProperty(JKUBE_VERSION_SYSTEM_PROPERTY)).ifPresent(jkubeVersion ->
      arguments.add(0, "-P" + JKUBE_VERSION_GRADLE_PROPERTY + "=" + jkubeVersion));
    return gradleRunner.withArguments(arguments);
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
