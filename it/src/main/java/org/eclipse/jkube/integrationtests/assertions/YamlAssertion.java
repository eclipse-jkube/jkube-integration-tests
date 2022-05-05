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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class YamlAssertion {

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  public static <V> Matcher<File> yaml(Matcher<Map<? extends String, ? extends V>> matcher) {
    return new YamlMapFileMatcher<>(matcher);
  }

  private static class YamlFileMatcher<M> extends TypeSafeMatcher<File> {

    private final Matcher<? super M> matcher;

    private YamlFileMatcher(Matcher<? super M> matcher) {
      super(File.class);
      this.matcher = matcher;
    }

    @Override
    protected boolean matchesSafely(File yamlFile) {
      try {
        return matcher.matches(readFile(yamlFile));
      } catch (IOException ex) {
        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("YAML file containing [").appendDescriptionOf(matcher).appendText("]");
    }

    @Override
    protected void describeMismatchSafely(File yamlFile, Description mismatchDescription) {
      mismatchDescription.appendText("YAML file (").appendValue(yamlFile).appendText(") - ");
      try {
        matcher.describeMismatch(readFile(yamlFile), mismatchDescription);
      } catch (IOException ex) {
        mismatchDescription.appendText("can't be loaded (").appendText(ex.getMessage()).appendText(")");
      }
    }

    private M readFile(File yamlFile) throws IOException {
      return YAML_MAPPER.readValue(yamlFile, new TypeReference<>() { });
    }
  }

  private static class YamlMapFileMatcher<V> extends YamlFileMatcher<Map<String, V>> {

    private YamlMapFileMatcher(Matcher<? super Map<String, V>> matcher) {
      super(matcher);
    }
  }
}
