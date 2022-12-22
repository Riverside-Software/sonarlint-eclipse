/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2022 SonarSource SA
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.openedge.core.runtime.IAVMClient;
import com.openedge.core.runtime.IDatabaseAlias;
import com.openedge.core.runtime.IDatabaseField;
import com.openedge.core.runtime.IDatabaseIndex;
import com.openedge.core.runtime.IDatabaseIndexField;
import com.openedge.core.runtime.IDatabaseSchemaReference;
import com.openedge.core.runtime.IDatabaseTable;
import com.openedge.core.runtime.ProgressCommand;
import com.openedge.pdt.project.OENature;
import com.openedge.pdt.project.OEProject;
import com.openedge.pdt.project.OEProjectPlugin;
import com.openedge.pdt.project.PropathConstants;
import com.openedge.pdt.project.PropathEntry;
import com.openedge.pdt.project.connection.DatabaseConnectionManager;
import com.openedge.pdt.project.connection.DatabaseConnectionProfile;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.analysis.IAnalysisConfigurator;
import org.sonarlint.eclipse.core.analysis.IFileLanguageProvider;
import org.sonarlint.eclipse.core.analysis.IPreAnalysisContext;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarsource.sonarlint.core.client.api.common.Language;

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
  public Set<Language> whitelistedLanguages() {
    return EnumSet.of(Language.OPENEDGE, Language.OPENEDGE_DB);
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

    // Make sure .sonarlint is available in project directory
    File sonarLintDir = new File(underlyingProject.getLocation().toFile(), ".sonarlint");
    sonarLintDir.mkdirs();

    boolean hasDB = false;
    try {
      hasDB = oeProject.hasDatabases();
    } catch (ConcurrentModificationException caught) {
      SonarLintLogger.get().info("Trapped synchronization problem from DB list, please retry later");
    }

    if (hasDB) {
      File slintReady = new File(sonarLintDir, "dblist.txt");
      SonarLintLogger.get().debug("Project has DB connections, looking for file " + slintReady.getAbsolutePath());

      boolean sendJob = false;
      if (slintReady.canRead()) {
        SonarLintLogger.get().debug("Reading dblist.txt");
        try (FileReader reader = new FileReader(slintReady); BufferedReader reader2 = new BufferedReader(reader)) {
          String tmp = reader2.readLine();
          long timeStamp = 0L;
          if (tmp != null)
            timeStamp = Long.parseLong(tmp);
          tmp = reader2.readLine();
          if (tmp != null)
            slintDB = tmp;
          tmp = reader2.readLine();
          if (tmp != null)
            aliases = tmp;

          if (timeStamp < System.currentTimeMillis() - 86400000L) {
            SonarLintLogger.get().debug("Schema older than 24 hours, regenerating...");
            sendJob = true;
          }

          boolean allFilesFound = true;
          for (String str : slintDB.split(",")) {
            File f = new File(str);
            allFilesFound &= f.canRead();
          }
          if (!allFilesFound) {
            SonarLintLogger.get().debug("At least one schema file couldn't be found, regenerating...");
            sendJob = true;
          }

        } catch (IOException | NumberFormatException caught) {
          SonarLintLogger.get().debug("Problem reading dblist.txt, regenerating...");
          sendJob = true;
        }
      } else {
        SonarLintLogger.get().debug("File not found, generating schema...");
        sendJob = true;
      }

      if (sendJob) {
        Job schemaJob = createJob(underlyingProject, oeProject);
        try {
          if (underlyingProject.getSessionProperty(new QualifiedName("org.sonarlint.eclipse.pdt", "slintdb")) == null) {
            SonarLintLogger.get().debug("Scheduling schema job...");
            underlyingProject.setSessionProperty(new QualifiedName("org.sonarlint.eclipse.pdt", "slintdb"), "1");
            schemaJob.schedule();
          }
        } catch (CoreException uncaught) {
          // Nothing
        }
      }
    } else {
      SonarLintLogger.get().debug("No DB connection defined...");
    }

    if (slintDB.length() > 0)
      context.setAnalysisProperty("sonar.oe.lint.databases", slintDB);
    if (aliases.length() > 0)
      context.setAnalysisProperty("sonar.oe.aliases", aliases);
  }

  private Job createJob(IProject project, OEProject oeProject) {
    return new Job("Generate SonarLint DB Structure for " + oeProject.getName()) {
      protected final IStatus run(IProgressMonitor monitor) {
        IAVMClient avm = oeProject.getRuntime();
        if ((avm != null) && avm.isAvailable()) {
          ProgressCommand progressCommand = new ProgressCommand("slintschema", new File(project.getLocation().toFile(), ".sonarlint").getAbsolutePath() , "eu.rssw.sonarlint.Schema");
          avm.runProgressCommand(progressCommand);
          try {
            progressCommand.waitforResult();
          }
          catch (InterruptedException interruptedException) {
            monitor.done();
            return Status.CANCEL_STATUS;
          }
        }
        monitor.done();

        try {
          project.setSessionProperty(new QualifiedName("org.sonarlint.eclipse.pdt", "slintdb"), null);
        } catch (CoreException uncaught) {
          // Nothing
        }

        return Status.OK_STATUS;
      }
    };
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
