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
package org.sonarlint.eclipse.core.internal.engine.connected;

import org.sonarlint.eclipse.core.internal.preferences.SonarLintProjectConfiguration.EclipseProjectBinding;

public class ResolvedBinding {
  private final EclipseProjectBinding projectBinding;
  private final ConnectionFacade connectionFacade;

  public ResolvedBinding(EclipseProjectBinding projectBinding, ConnectionFacade connectionFacade) {
    this.projectBinding = projectBinding;
    this.connectionFacade = connectionFacade;
  }

  public EclipseProjectBinding getProjectBinding() {
    return projectBinding;
  }

  public ConnectionFacade getConnectionFacade() {
    return connectionFacade;
  }

}
