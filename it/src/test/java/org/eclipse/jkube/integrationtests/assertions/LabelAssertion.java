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

import org.eclipse.jkube.integrationtests.JKubeCase;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.util.Map;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.startsWith;

public class LabelAssertion {

  private final JKubeCase jKubeCase;

  private LabelAssertion(JKubeCase jKubeCase) {
    this.jKubeCase = jKubeCase;
  }

  public static LabelAssertion assertLabels(JKubeCase jKubeCase) {
    return new LabelAssertion(jKubeCase);
  }

  public LabelAssertion assertStandardLabels(Supplier<Map<String, String>> labelSupplier) {
    assertGlobalLabels(labelSupplier);
    assertLabels(labelSupplier, hasEntry("app", jKubeCase.getApplication()));
    return this;
  }

  public static void assertGlobalLabels(Supplier<Map<String, String>> labelSupplier) {
    assertLabels(labelSupplier, allOf(
      hasEntry("provider", "jkube"),
      hasEntry(Matchers.<String>equalTo("group"), startsWith("org.eclipse.jkube.integration-tests"))
    ));
  }

  public static void assertLabels(
    Supplier<Map<String, String>> labelSupplier, Matcher<Map<? extends String, ? extends String>> matcher) {

    assertThat(labelSupplier.get(), matcher);
  }
}
