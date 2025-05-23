/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2025 SonarSource SA
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.ui.internal.util.wizard;

import org.eclipse.core.databinding.beans.IBeanValueProperty;
import org.eclipse.core.databinding.beans.typed.BeanProperties;

public class BeanPropertiesCompat {
  public static <S, T> IBeanValueProperty<S, T> value(Class<S> beanClass, String propertyName) {
    if (JFaceUtils.IS_TYPED_API_SUPPORTED) {
      return BeanProperties.value(beanClass, propertyName);
    }
    try {
      var beanPropertiesClass = Class.forName("org.eclipse.core.databinding.beans.BeanProperties");
      var valueMethod = beanPropertiesClass.getMethod("value", Class.class, String.class);
      return (IBeanValueProperty<S, T>) valueMethod.invoke(null, beanClass, propertyName);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to call deprecated method", e);
    }
  }

  private BeanPropertiesCompat() {
    // utility class
  }
}
