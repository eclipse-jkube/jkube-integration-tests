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
