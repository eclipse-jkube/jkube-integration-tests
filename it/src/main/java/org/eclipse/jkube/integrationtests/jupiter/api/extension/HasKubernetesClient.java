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
package org.eclipse.jkube.integrationtests.jupiter.api.extension;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.extension.ExtensionContext;

public interface HasKubernetesClient extends BaseExtension {

  default KubernetesClient getClient(ExtensionContext context) {
    synchronized (context.getRoot()) {
      var client = getStore(context).get(KubernetesClient.class, KubernetesClient.class);
      if (client == null) {
        client = new KubernetesClientBuilder().build();
        getStore(context).put(KubernetesClient.class, client);
      }
      return client;
    }
  }
}
