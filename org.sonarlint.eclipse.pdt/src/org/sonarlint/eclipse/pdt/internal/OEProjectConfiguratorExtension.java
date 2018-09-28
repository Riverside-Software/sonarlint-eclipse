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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.analysis.IAnalysisConfigurator;
import org.sonarlint.eclipse.core.analysis.IFileLanguageProvider;
import org.sonarlint.eclipse.core.analysis.IPreAnalysisContext;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import com.openedge.pdt.project.OENature;
import com.openedge.pdt.project.OEProject;
import com.openedge.pdt.project.PropathEntry;
import com.openedge.pdt.project.PropathConstants;

public class OEProjectConfiguratorExtension implements IAnalysisConfigurator, IFileLanguageProvider {

  public OEProjectConfiguratorExtension() {
    SonarLintLogger.get().debug("OEProjectConfiguratorExtension");
  }

  private static boolean isPdtPresent() {
    try {
      Class.forName("com.openedge.pdt.project.OEProjectPlugin");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public boolean canConfigure(ISonarLintProject project) {
    SonarLintLogger.get().debug("Entering canConfigure...");
    try {
      IProject underlyingProject = project.getResource() instanceof IProject ? (IProject) project.getResource() : null;
      // SonarLintLogger.get().info("   ... " + (underlyingProject != null && (underlyingProject.hasNature(OENature.PROGRESS_NATURE_ID))));
      return underlyingProject != null && (underlyingProject.hasNature(OENature.PROGRESS_NATURE_ID));
    } catch (CoreException t) {
      return false;
    }
  }

  @Override
  public void configure(IPreAnalysisContext context, IProgressMonitor monitor) {
    SonarLintLogger.get().debug("Configure");
    
    IProject underlyingProject = context.getProject().getResource() instanceof IProject ? (IProject) context.getProject().getResource() : null;
    if (underlyingProject == null)
      return;
    OEProject oeProject = OEProject.getOEProject(underlyingProject);
    if (oeProject == null)
      return;

    IPath rCodePath = oeProject.getConfiguration().getRCodePath();
    if (rCodePath != null) {
      SonarLintLogger.get().debug("RCodePath: " + rCodePath.toOSString());
    }

    SonarLintLogger.get().debug("useXrefXML: " + oeProject.getConfiguration().useXrefXML());
    IPath xrefPath = oeProject.getConfiguration().getXREFXMLPath();
    if (xrefPath != null) {
      SonarLintLogger.get().debug("XREFXMLPath: " + xrefPath.toOSString());
    }

    String src = "";
    for (IPath entry : oeProject.getPropathHandler().translateEntriesToPaths(oeProject.getPropathHandler().getPropathEntriesByKind(PropathConstants.SOURCE_DIRECTORY))) {
      src = src + (src = src.length() == 0 ? "" : ",") + entry.toOSString();
    }

    String propath = "";
    for (IPath pp : oeProject.getPropathHandler().translateEntriesToPaths(oeProject.getPropathHandler().getPropathEntries())) {
      propath = propath + (propath.length() == 0 ? "" : ",") + pp.toOSString();
    }

    SonarLintLogger.get().debug("sonar.sources set to '" + src + "'");
    SonarLintLogger.get().debug("sonar.oe.propath set to '" + propath + "'");

    context.setAnalysisProperty("sonar.sources", src);
    context.setAnalysisProperty("sonar.oe.propath", propath);
    if (rCodePath != null)
      context.setAnalysisProperty("sonar.oe.binaries", rCodePath.toOSString());
    if (xrefPath != null)
      context.setAnalysisProperty("sonar.oe.lint.xref", xrefPath.toOSString());
  }

  @Override
  public String language(ISonarLintFile file) {
    IFile iFile = file.getResource() instanceof IFile ? (IFile) file.getResource() : null;
    SonarLintLogger.get().debug("Language on " + iFile);
    String ext = iFile == null ? null : iFile.getFileExtension();
    if (ext == null) {
      return null;
    } else if ("p".equalsIgnoreCase(ext) || "w".equalsIgnoreCase(ext) || "i".equalsIgnoreCase(ext) || "cls".equalsIgnoreCase(ext)) {
      return "oe";
    } else if ("df".equalsIgnoreCase(ext)) {
      return "oedb";
    }

    return null;
  }

}
