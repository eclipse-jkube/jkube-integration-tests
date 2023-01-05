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
package org.eclipse.jkube.integrationtests.jupiter.api;

import org.eclipse.jkube.integrationtests.jupiter.api.extension.RequireK8sVersionAtLeastCondition;
import org.eclipse.jkube.integrationtests.jupiter.api.extension.TempKubernetesExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TempKubernetesExtension.class)
@ExtendWith(RequireK8sVersionAtLeastCondition.class)
public @interface RequireK8sVersionAtLeast {
  String majorVersion();
  String minorVersion();
}
