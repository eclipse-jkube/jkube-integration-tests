/*
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
package org.eclipse.jkube.integrationtests.quarkus.na7ive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;

@Singleton
public class NativeService {

  private static final Logger log = LoggerFactory.getLogger(NativeService.class);

  private static final String NATIVE_RESOURCE= "/native.json";

  private String nativeResource;

  @PostConstruct
  protected final void initialize() {
    final ObjectMapper objectMapper = new ObjectMapper();
    try (final InputStream nativeStream = NativeService.class.getResourceAsStream(NATIVE_RESOURCE)) {
      nativeResource = objectMapper.readValue(nativeStream, String.class);
    } catch (IOException ex) {
      log.error("Error loading native.json", ex);
    }
  }

  String getNativeResource() {
    return nativeResource;
  }
}
