/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2018 SonarSource SA
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.CheckForNull;

import com.openedge.pdt.project.IOpenEdgeProject;
import com.openedge.pdt.project.OEProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.content.IContentType;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.configurator.ProjectConfigurationRequest;
import org.sonarlint.eclipse.core.configurator.ProjectConfigurator;

public class OEProjectConfigurator extends ProjectConfigurator {

  @Override
  public boolean canConfigure(IProject project) {
    return SonarPdtPlugin.hasOENature(project);
  }

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) {
    SonarLintLogger.get().debug("Entering PDT configure() for " + request.toString());
    OEProject oeProject = OEProject.getOEProject(request.getProject());

    for (Iterator<IFile> it = request.getFilesToAnalyze().iterator(); it.hasNext();) {
      SonarLintLogger.get().debug("** File to analyze " + it.next());
    }
    configureOEProject(oeProject, request.getSonarProjectProperties());
  }

  // Visible for testing
  public void configureOEProject(IOpenEdgeProject javaProject, Map<String, String> sonarProjectProperties) {
    // SonarLintLogger.get().info("Setting properties");
    // sonarProjectProperties.put("sonar.sources", "src");
    // sonarProjectProperties.put("sonar.oe.binaries", "build");
    // sonarProjectProperties.put("sonar.oe.propath", "src,src/procedures,src/classes");
  }

  private static Path getProjectBaseDir(IProject project) {
    IPath projectLocation = project.getLocation();
    // In some infrequent cases the project may be virtual and don't have physical location
    return projectLocation != null ? projectLocation.toFile().toPath() : ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
  }

}
