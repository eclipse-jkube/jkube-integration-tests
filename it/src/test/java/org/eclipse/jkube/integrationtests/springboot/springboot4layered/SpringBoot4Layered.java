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
package org.eclipse.jkube.integrationtests.springboot.springboot4layered;

public interface SpringBoot4Layered {

  String PROJECT_SPRING_BOOT_4_LAYERED = "projects-to-be-tested/maven/spring/spring-boot-4-layered";

  static String getApplication() {
    return "spring-boot-4-layered";
  }
}