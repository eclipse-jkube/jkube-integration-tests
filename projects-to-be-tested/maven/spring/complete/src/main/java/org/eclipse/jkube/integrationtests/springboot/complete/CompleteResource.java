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
package org.eclipse.jkube.integrationtests.springboot.complete;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/")
public class CompleteResource {

  @GetMapping(path = "jkube/hello")
  public String jkubeHello() {
    return "hello";
  }

  @GetMapping(path = "jkube/readme")
  public String jkubeReadme() throws IOException {
    // Retrieved from a git mounted volume defined in a k8s/deployment fragment
    return Files.readString(Path.of("/","app", "jkube", "README.md"));
  }

  @GetMapping(path = "jkube/static-file")
  public String jkubeStaticFile() throws IOException {
    return Files.readString(Path.of("/","deployments", "static", "static-file.txt"));
  }
}
