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
import java.util.Arrays;
import java.util.function.Predicate;

public abstract class BaseExtension {

  abstract ExtensionContext.Namespace getNamespace();

  ExtensionContext.Store getStore(ExtensionContext context) {
    return context.getRoot().getStore(getNamespace());
  }

  static Field[] extractFields(ExtensionContext context, Class<?> clazz, Predicate<Field>... predicates) {
    final var testClass = context.getTestClass().orElse(null);
    if (testClass != null) {
      var fieldStream = Arrays.stream(testClass.getDeclaredFields())
        .filter(f -> clazz.isAssignableFrom(f.getType()));
      for (Predicate<Field> p : predicates) {
        fieldStream = fieldStream.filter(p);
      }
      return fieldStream.toArray(Field[]::new);
    }
    return new Field[0];
  }

  static void setFieldValue(Field field, Object entity, Object value) throws IllegalAccessException {
    final boolean isAccessible = field.isAccessible();
    field.setAccessible(true);
    field.set(entity, value);
    field.setAccessible(isAccessible);
  }
}
