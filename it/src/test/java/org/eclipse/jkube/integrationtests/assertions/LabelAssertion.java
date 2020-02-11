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

import org.hamcrest.Matcher;

import java.util.Map;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;

public class LabelAssertion {

  public static void assertGlobalLabels(Supplier<Map<String, String>> labelSupplier) {
    assertLabels(labelSupplier, allOf(
      hasEntry("provider", "jkube"),
      hasEntry("group", "org.eclipse.jkube.integration-tests")
    ));
  }

  public static void assertLabels(
    Supplier<Map<String, String>> labelSupplier, Matcher<Map<? extends String, ? extends String>> matcher) {

    assertThat(labelSupplier.get(), matcher);
  }
}
