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
package org.eclipse.jkube.integrationtests.springboot.javaversion;

import org.eclipse.jkube.integrationtests.javaversion.JavaVersionMaven;
import org.junit.jupiter.api.Disabled;

@Disabled("Pending jkube.java.version support — eclipse-jkube/jkube#3931")
class JavaVersionK8sITCase extends JavaVersionMaven {

  @Override
  public String getProject() {
    return "projects-to-be-tested/maven/spring/zero-config";
  }

  @Override
  public String getApplication() {
    return "spring-boot-zero-config";
  }

  @Override
  protected String getLogMarker() {
    return "Started";
  }
}
