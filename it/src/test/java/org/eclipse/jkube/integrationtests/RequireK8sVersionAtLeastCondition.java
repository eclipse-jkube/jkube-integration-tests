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
package org.eclipse.jkube.integrationtests;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.VersionInfo;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

public class RequireK8sVersionAtLeastCondition implements ExecutionCondition {
  public static final String NON_NUMERIC_CHARACTERS = "[^\\d.]";
  public static final String EMPTY = "";

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
    Optional<RequireK8sVersionAtLeast> requireK8sOptional = findAnnotation(extensionContext.getElement(), RequireK8sVersionAtLeast.class);
    if (requireK8sOptional.isPresent()) {
      final RequireK8sVersionAtLeast requireK8s = requireK8sOptional.get();
      final String majorVersion = requireK8s.majorVersion();
      final String minorVersion = requireK8s.minorVersion();

      if (kubernetesVersionAtLeast(majorVersion, minorVersion)) {
        return ConditionEvaluationResult.enabled(String.format("Kubernetes version is at least %s.%s", majorVersion, minorVersion));
      } else {
        return ConditionEvaluationResult.disabled(String.format("Kubernetes version is below %s.%s", majorVersion, minorVersion));
      }
    }
    return ConditionEvaluationResult.enabled("No assumptions, moving on...");
  }

  public static boolean kubernetesVersionAtLeast(String majorVersion, String minorVersion) {
    try (KubernetesClient client = new KubernetesClientBuilder().build()) {
      VersionInfo versionInfo = client.getVersion();
      String clusterMajorVersion = versionInfo.getMajor().replaceAll(NON_NUMERIC_CHARACTERS, EMPTY);
      String clusterMinorVersion = versionInfo.getMinor().replaceAll(NON_NUMERIC_CHARACTERS, EMPTY);

      if (Integer.parseInt(majorVersion) < Integer.parseInt(clusterMajorVersion)) {
        return true;
      }

      return Integer.parseInt(clusterMajorVersion) >= Integer.parseInt(majorVersion) &&
        Integer.parseInt(clusterMinorVersion) >= Integer.parseInt(minorVersion);
    }
  }
}
