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

import java.util.Comparator;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.analysis.IAnalysisConfigurator;
import org.sonarlint.eclipse.core.analysis.IFileLanguageProvider;
import org.sonarlint.eclipse.core.analysis.IPreAnalysisContext;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import com.openedge.core.runtime.IDatabaseSchemaReference;
import com.openedge.core.runtime.IDatabaseAlias;
import com.openedge.core.runtime.IDatabaseField;
import com.openedge.core.runtime.IDatabaseIndex;
import com.openedge.core.runtime.IDatabaseIndexField;
import com.openedge.core.runtime.IDatabaseTable;
import com.openedge.pdt.project.OENature;
import com.openedge.pdt.project.OEProject;
import com.openedge.pdt.project.OEProjectPlugin;
import com.openedge.pdt.project.PropathEntry;
import com.openedge.pdt.project.PropathConstants;
import com.openedge.pdt.project.connection.DatabaseConnectionManager;
import java.util.stream.Collectors;

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
      return underlyingProject != null && (underlyingProject.hasNature(OENature.PROGRESS_NATURE_ID));
    } catch (CoreException t) {
      return false;
    }
  }

  @Override
  public void configure(IPreAnalysisContext context, IProgressMonitor monitor) {
    IProject underlyingProject = context.getProject().getResource() instanceof IProject ? (IProject) context.getProject().getResource() : null;
    if (underlyingProject == null)
      return;
    OEProject oeProject = OEProject.getOEProject(underlyingProject);
    if (oeProject == null)
      return;

    SonarLintLogger.get().debug("useXrefXML: " + oeProject.getConfiguration().useXrefXML());
    IPath xrefPath = oeProject.getConfiguration().getXREFXMLPath();
    if (xrefPath != null) {
      SonarLintLogger.get().debug("XREFXMLPath: " + xrefPath.toOSString());
    }

    String src = "";
    String rCodePath = "";
    for (PropathEntry entry : oeProject.getPropathHandler().getPropathEntriesByKind(PropathConstants.SOURCE_DIRECTORY)) {
      // Get real path of source entry
      IPath[] srcEntry = oeProject.getPropathHandler().translateEntriesToPaths(new PropathEntry[] { entry });
      if ((srcEntry != null) && (srcEntry.length > 0))
        src = src + (src.length() == 0 ? "" : ",") + srcEntry[0].toOSString();

      // And see if build directory set for this entry
      String buildEntry = oeProject.getOEProjectPropathHandler().getRealPath(entry.getBuild().getPath());
      if ((buildEntry != null) && !"".equals(buildEntry))
        rCodePath = rCodePath + (rCodePath.length() == 0 ? "" : ",") + buildEntry;
    }
    if (oeProject.getConfiguration().getRCodePath() != null) {
      rCodePath = rCodePath + (rCodePath.length() == 0 ? "" : ",") + oeProject.getConfiguration().getRCodePath().toOSString();
    }

    String propath = "";
    for (IPath pp : oeProject.getPropathHandler().translateEntriesToPaths(oeProject.getPropathHandler().getPropathEntries())) {
      propath = propath + (propath.length() == 0 ? "" : ",") + pp.toOSString();
    }

    SonarLintLogger.get().debug("sonar.sources set to '" + src + "'");
    SonarLintLogger.get().debug("sonar.oe.propath set to '" + propath + "'");

    context.setAnalysisProperty("sonar.sources", src);
    context.setAnalysisProperty("sonar.oe.propath", propath);
    if ((rCodePath != null) && !"".equals(rCodePath))
      context.setAnalysisProperty("sonar.oe.binaries", rCodePath);
    if (oeProject.getConfiguration().useXrefXML() && (xrefPath != null))
      context.setAnalysisProperty("sonar.oe.lint.xref", xrefPath.toOSString());

    String slintDB = "";
    String aliases = "";
    SonarLintLogger.get().debug("Set of DB configured for project: '" + oeProject.getAVMProperty(OEProject.ID_DATABASES) + "'");
    SonarLintLogger.get().debug("Set of GUID for project: '" + oeProject.getAVMProperty(OEProject.GUID_DATABASES) + "'");
    File workDir = underlyingProject.getLocation().toFile();
    DatabaseConnectionManager mgr = OEProjectPlugin.getDefault().getDatabaseConnectionManager();
    for (IDatabaseSchemaReference ref : mgr.getSchemasForProject(oeProject)) {
      SonarLintLogger.get().debug("Schema reference: '" + ref.getDatabaseGUID() + "'");
      File f = generateSchemaFile(underlyingProject, ref, workDir);
      slintDB = slintDB + (slintDB.length() > 0 ? "," : "") + f;
      if ((ref.getAlias() != null) && !ref.getAlias().isEmpty()) {
        aliases = aliases + (aliases.length() > 0 ? ";" : "") + ref.getDatabaseName();
        for (IDatabaseAlias alias : ref.getAlias()) {
          aliases = aliases + "," + alias.getAlias();
        }
      }
    }
    if (slintDB.length() > 0)
      context.setAnalysisProperty("sonar.oe.lint.databases", slintDB);
    if (aliases.length() > 0)
      context.setAnalysisProperty("sonar.oe.aliases", aliases);
    SonarLintLogger.get().debug("DB schema task completed");
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

  private File generateSchemaFile(IProject prj, IDatabaseSchemaReference ref, File workDir) {
    File serFile = new File(workDir, ".sonarlint/" + ref.getDatabaseName() + ".schema");
    Object lastTS = null;
    try {
      lastTS = prj.getSessionProperty(new QualifiedName(null, ref.getDatabaseGUID()));
    } catch (CoreException uncaught) {
      SonarLintLogger.get().error("Couldn't retrieve DB timestamp session property");
    }
    if (ref.getDatabaseTimeStamp().equals(lastTS) && serFile.exists()) {
      SonarLintLogger.get().debug("DB schema file already present for: " + ref.getDatabaseName());
      return serFile;
    }

    SonarLintLogger.get().debug("Generating schema file for: " + ref.getDatabaseName());
    serFile.getParentFile().mkdirs();
    try (OutputStream out = new FileOutputStream(serFile); OutputStreamWriter osw = new OutputStreamWriter(out, Charset.forName("utf-8")); BufferedWriter writer = new BufferedWriter(osw)) {
      writer.write("## " + ref.getDatabaseTimeStamp());
      writer.newLine();
      // Sort by table name name
      for (IDatabaseTable tbl : ref.getSchemaRoot().getTables().stream().sorted((o1, o2) -> o1.getTable().compareTo(o2.getTable())).collect(Collectors.toList())) {
        writer.write("T" + tbl.getTable());
        writer.newLine();
        // Sort by field order
        for (IDatabaseField fld : tbl.getFields().stream().sorted(Comparator.comparingInt(IDatabaseField::getOrder)).collect(Collectors.toList())) {
          writer.write("F" + fld.getField() + ":" + fld.getDataType().getDataTypeName() + ":" + fld.getExtent());
          writer.newLine();
        }
        // Exclude indexes without any field (i.e. default table index)
        for (IDatabaseIndex idx : tbl.getIndexes().stream().filter(o -> ((o.getIndexFieldObjects().length > 0) && !"".equals(o.getIndexFieldObjects()[0].getFieldName()))).collect(Collectors.toList())) {
          writer.write("I" + idx.getIndex() + ":" + (idx.isPrimary() ? "P" : "") + (idx.isUnique() ? "U" : ""));
          for (IDatabaseIndexField fld : idx.getIndexFieldObjects()) {
            writer.write(":" + (fld.isAscedning() ? 'A' : 'D') + fld.getFieldName());
          }
          writer.newLine();
        }
      }
    } catch (IOException caught) {
      SonarLintLogger.get().error("Unable to serialize database schema: " + ref.getDatabaseName());
    }
    try {
      prj.setSessionProperty(new QualifiedName(null, ref.getDatabaseGUID()), ref.getDatabaseTimeStamp());
    } catch (CoreException uncaught) {
      SonarLintLogger.get().error("Couldn't set DB timestamp session property");
    }

    return serFile;
  }
}
