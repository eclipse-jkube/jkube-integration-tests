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

import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface BaseExtension {

  default ExtensionContext.Namespace getNamespace(ExtensionContext context) {
    return ExtensionContext.Namespace.create(context.getRequiredTestClass());
  }

  default ExtensionContext.Store getStore(ExtensionContext context) {
    return context.getRoot().getStore(getNamespace(context));
  }

  default Field[] extractFields(ExtensionContext context, Class<?> clazz, Predicate<Field>... predicates) {
    final var fields = new ArrayList<>();
    var testClass = context.getTestClass().orElse(Object.class);
    do {
      fields.addAll(extractFields(testClass, clazz, predicates));
      testClass = testClass.getSuperclass();
    } while (testClass != Object.class);
    return fields.toArray(new Field[0]);
  }

  private static List<Field> extractFields(Class<?> classWhereFieldIs, Class<?> fieldType, Predicate<Field>... predicates) {
    if (classWhereFieldIs != null && classWhereFieldIs != Object.class) {
      var fieldStream = Arrays.stream(classWhereFieldIs.getDeclaredFields())
        .filter(f -> fieldType.isAssignableFrom(f.getType()));
      for (Predicate<Field> p : predicates) {
        fieldStream = fieldStream.filter(p);
      }
      return fieldStream.collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  default void setFieldValue(Field field, Object entity, Object value) throws IllegalAccessException {
    final boolean isAccessible = field.isAccessible();
    field.setAccessible(true);
    field.set(entity, value);
    field.setAccessible(isAccessible);
  }
}
