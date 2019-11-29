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
package org.eclipse.jkube.integrationtests.docker;

import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.eclipse.jkube.integrationtests.cli.CliUtils.CliResult;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Could be done using Docker Client included in docker-maven-plugin (or any other).
 * Current approach (use of CLI) is preferred as it's completely independent from FMP.
 */
public class DockerUtils {

  private DockerUtils() {
  }

  public static List<DockerImage> dockerImages() throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand(
        "docker -l error images --format=\"{{.Repository}}\\t{{.Tag}}\\t{{.ID}}\\t{{.CreatedSince}}\"");
    if (result.getExitCode() != 0) {
      throw new IOException(String.format("Docker: %s", result.getOutput()));
    }
    return Stream.of(result.getOutput().replace("\r", "").split("\n"))
        .map(cliImageLine -> cliImageLine.split("\t"))
        .map(parsedImageLine ->
            new DockerImage(parsedImageLine[0], parsedImageLine[1], parsedImageLine[2],
                parsedImageLine[3]))
        .collect(Collectors.toList());
  }

  public static final class DockerImage {

    private final String repository;
    private final String tag;
    private final String id;
    private final String createdSince;

    private DockerImage(String repository, String tag, String id, String createdSince) {
      this.repository = repository;
      this.tag = tag;
      this.id = id;
      this.createdSince = createdSince;
    }

    public String getRepository() {
      return repository;
    }

    public String getTag() {
      return tag;
    }

    public String getId() {
      return id;
    }

    public String getCreatedSince() {
      return createdSince;
    }
  }
}
