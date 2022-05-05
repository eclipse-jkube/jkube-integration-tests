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
package org.eclipse.jkube.integrationtests.maven;

import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.eclipse.jkube.integrationtests.Project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public abstract class BaseMavenCase implements Project {

  protected List<String> getProfiles() {
    return new ArrayList<>();
  }

  protected Properties properties(Map<String, String> propertyMap) {
    final Properties ret = new Properties();
    ret.putAll(propertyMap);
    return ret;
  }

  protected Properties properties(String key, String value) {
    return properties(Collections.singletonMap(key, value));
  }

  protected MavenInvocationResult maven(String goal)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, new Properties());
  }

  protected MavenInvocationResult maven(String goal, Properties properties)
    throws IOException, InterruptedException, MavenInvocationException {

    return maven(goal, properties, null);
  }

  protected final MavenInvocationResult maven(
    String goal, Properties properties, MavenUtils.InvocationRequestCustomizer chainedCustomizer)
    throws IOException, InterruptedException, MavenInvocationException {

    try (
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final PrintStream printStream = new PrintStream(baos)
    ) {
      final MavenUtils.InvocationRequestCustomizer recordStdOutCustomizer = invocationRequest ->
        invocationRequest.setOutputHandler(new PrintStreamHandler(printStream, true));
      final InvocationResult mavenResult = MavenUtils.execute(i -> {
        i.setBaseDirectory(new File("../"));
        i.setProjects(Collections.singletonList(getProject()));
        i.setGoals(Collections.singletonList(goal));
        i.setProfiles(getProfiles());
        i.setProperties(properties);
        recordStdOutCustomizer.customize(i);
        Optional.ofNullable(chainedCustomizer).ifPresent(cc -> cc.customize(i));
      });
      printStream.flush();
      baos.flush();
      return new MavenInvocationResult(mavenResult, baos.toString(StandardCharsets.UTF_8));
    }
  }
}
