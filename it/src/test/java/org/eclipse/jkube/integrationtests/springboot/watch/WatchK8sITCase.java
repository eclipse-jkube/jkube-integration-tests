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
package org.eclipse.jkube.integrationtests.springboot.watch;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;

import static org.eclipse.jkube.integrationtests.Tags.KUBERNETES;

@Tag(KUBERNETES)
@Isolated
@Disabled("Test won't pass on CI (Minikube)")
// o.s.b.d.r.c.ClassPathChangeUploader      : A failure occurred when uploading to http://localhost:51337/.~~spring-boot!~/restart. Upload will be retried in 2 seconds
public class WatchK8sITCase extends Watch {
  @Override
  String getPrefix() {
    return "k8s";
  }
}
