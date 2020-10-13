/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2019 SonarSource SA
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
package org.sonarlint.eclipse.pdt.internal;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import com.openedge.pdt.project.OENature;
import org.sonarlint.eclipse.core.AbstractPlugin;
import org.sonarlint.eclipse.core.SonarLintLogger;

public class SonarPdtPlugin extends AbstractPlugin {

  public static final String PLUGIN_ID = "org.sonarlint.eclipse.pdt"; //$NON-NLS-1$

  private static SonarPdtPlugin plugin;

  public SonarPdtPlugin() {
    plugin = this;
  }

  /**
   * @return the shared instance
   */
  public static SonarPdtPlugin getDefault() {
    return plugin;
  }

}
