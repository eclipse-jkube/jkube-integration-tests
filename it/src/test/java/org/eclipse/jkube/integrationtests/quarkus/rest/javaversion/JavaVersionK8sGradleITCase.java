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
package org.eclipse.jkube.integrationtests.quarkus.rest.javaversion;

import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.javaversion.JavaVersionGradle;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;

@Application("quarkus-rest")
class JavaVersionK8sGradleITCase extends JavaVersionGradle {

  @Gradle(project = "quarkus-rest")
  private JKubeGradleRunner gradle;

  @Override
  protected JKubeGradleRunner getGradle() {
    return gradle;
  }
}
