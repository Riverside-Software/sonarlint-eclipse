/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2021 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.binding;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.ide.IDE.SharedImages;
import org.sonarlint.eclipse.core.internal.resources.ProjectsProviderUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;

public class ProjectSelectionDialog {

  public static Optional<ISonarLintProject> pickProject(String filePath, String projectKey, String connectionId) {
    List<ISonarLintProject> projects = ProjectsProviderUtils.allProjects()
      .stream()
      .sorted(Comparator.comparing(ISonarLintProject::getName))
      .collect(Collectors.toList());
    ElementListSelectionDialog dialog = new ElementListSelectionDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), new SonarLintProjectLabelProvider());
    dialog.setElements(projects.toArray());
    dialog.setMessage(
      "Select the project containing the file " + filePath + ".\nThis Eclipse project will be bound to the project '" + projectKey + "' using connection '" + connectionId + "'");
    dialog.setTitle("SonarLint - Project binding");
    dialog.setHelpAvailable(false);
    if (dialog.open() == Window.OK) {
      return Optional.of((ISonarLintProject) dialog.getResult()[0]);
    }
    return Optional.empty();
  }

  private static final class SonarLintProjectLabelProvider extends LabelProvider {
    @Override
    public String getText(Object element) {
      ISonarLintProject current = (ISonarLintProject) element;
      return current.getName();
    }

    @Override
    public Image getImage(Object element) {
      return PlatformUI.getWorkbench().getSharedImages().getImage(SharedImages.IMG_OBJ_PROJECT);
    }
  }

  private ProjectSelectionDialog() {
    // utility class
  }

}
