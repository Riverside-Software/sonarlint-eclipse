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
package org.sonarlint.eclipse.ui.internal.binding.wizard.connection;

import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.runtime.IStatus;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;

public class MandatoryAndUniqueServerIdValidator implements IValidator {

  private final boolean edit;

  public MandatoryAndUniqueServerIdValidator(boolean edit) {
    this.edit = edit;
  }

  @Override
  public IStatus validate(Object value) {
    var errorMsg = SonarLintCorePlugin.getConnectionManager().validate((String) value, edit);
    if (errorMsg != null) {
      return ValidationStatus.error(errorMsg);
    }
    return ValidationStatus.ok();
  }
}
