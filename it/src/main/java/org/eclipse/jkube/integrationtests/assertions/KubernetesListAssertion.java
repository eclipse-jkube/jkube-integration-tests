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
package org.eclipse.jkube.integrationtests.assertions;

import java.io.File;
import java.nio.file.Path;

import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

public class KubernetesListAssertion {

  public static void assertListResource(Path path) {
    assertListResource(path.toAbsolutePath().toFile());
  }

  public static void assertListResource(File file) {
    assertThat(file, yaml(allOf(
      not(anEmptyMap()),
      hasEntry("kind", "List"),
      hasEntry("apiVersion", "v1")
    )));
  }
}
