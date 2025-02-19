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
package org.sonarlint.eclipse.pdt.internal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import eu.rssw.pct.FileEntry;
import eu.rssw.pct.PLReader;
import eu.rssw.pct.PLReader.InvalidLibraryException;
import eu.rssw.pct.RCodeInfo;
import eu.rssw.pct.RCodeInfo.InvalidRCodeException;

import com.openedge.core.metadata.ICodeModel;
import com.openedge.core.metadata.ITypeInfo;
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
import org.sonarlint.eclipse.core.analysis.SonarLintLanguage;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;

public class OEProjectConfiguratorExtension implements IAnalysisConfigurator, IFileLanguageProvider {

  private static final String OPENEDGE_LANGUAGE_KEY = "oe";

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
  public Set<SonarLintLanguage> enableLanguages() {
    return EnumSet.of(SonarLintLanguage.OPENEDGE, SonarLintLanguage.OPENEDGE_DB);
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

    // List of full path to all .PL files
    List<String> plList = new ArrayList<>();
    for (IPath pp : oeProject.getPropathHandler().translateEntriesToPaths(oeProject.getPropathHandler().getPropathEntries())) {
      if (pp.toOSString().endsWith(".pl")) {
        SonarLintLogger.get().debug("Add PL to cache list: " + pp.toOSString());
        plList.add(pp.toOSString());
      }
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

    File slintPL = generatePLCache(plList, sonarLintDir);
    context.setAnalysisProperty("sonar.oe.lint.pl.cache", slintPL.getAbsolutePath());

    Path slintRC = Path.of(sonarLintDir.toPath().toString(), "rcc.txt");
    SonarLintLogger.get().debug("Generating rcode cache to  " + slintRC.toString());
    if (!oeProject.getCodeModel().isModelBuilding()) {
      String cache = getRCodeCache(oeProject.getCodeModel());
      try (OutputStream out = Files.newOutputStream(slintRC)) {
        out.write(cache.getBytes());
        context.setAnalysisProperty("sonar.oe.lint.rcode.cache", slintRC.toAbsolutePath().toString());
        SonarLintLogger.get().debug("Success !");
      } catch (IOException caught) {
        SonarLintLogger.get().error("Error ! " + caught.getMessage());
      }
    } else {
      SonarLintLogger.get().debug("PDSOE currently building code model, rcode information not refreshed");
      if (Files.isReadable(slintRC))
        context.setAnalysisProperty("sonar.oe.lint.rcode.cache", slintRC.toAbsolutePath().toString());
    }

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
  public SonarLintLanguage language(ISonarLintFile file) {
    IFile iFile = file.getResource() instanceof IFile ? (IFile) file.getResource() : null;
    SonarLintLogger.get().debug("Language on " + iFile);
    String ext = iFile == null ? null : iFile.getFileExtension();
    if (ext == null) {
      return null;
    } else if ("p".equalsIgnoreCase(ext) || "w".equalsIgnoreCase(ext) || "i".equalsIgnoreCase(ext) || "cls".equalsIgnoreCase(ext)) {
      return SonarLintLanguage.OPENEDGE;
    } else if ("df".equalsIgnoreCase(ext)) {
      return SonarLintLanguage.OPENEDGE_DB;
    }

    return null;
  }

  private File generatePLCache(List<String> plList, File sonarLintDir) {
    File slintPL = new File(sonarLintDir, "pl.txt");
    final long timeStamp = slintPL.lastModified(); // 0 if does not exist
    boolean overwrite = plList.stream().anyMatch(it -> new File(it).lastModified() > timeStamp);
    if (overwrite) {
      try (OutputStream out = new FileOutputStream(slintPL);
           OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
           BufferedWriter writer = new BufferedWriter(osw)) {
        for (String entry : plList) {
          try {
            PLReader plr = new PLReader(Paths.get(entry));
            for (FileEntry entry2 : plr.getFileList()) {
              if (entry2.getFileName().endsWith(".r")) {
                try {
                  RCodeInfo rci = new RCodeInfo(plr.getInputStream(entry2));
                  if (rci.isClass()) {
                    writer.write(rci.getTypeInfo().getTypeName());
                    writer.write(':');
                    writer.write(entry);
                    writer.write('#');
                    writer.write(entry2.getFileName());
                    writer.newLine();
                  }
                } catch (InvalidRCodeException | IOException caught) {
                  // Silently discards file
                }
              }
            }
          } catch (InvalidLibraryException caught) {
            SonarLintLogger.get().error("Invalid library: " + caught.getMessage());
          }
        }
      } catch (IOException caught) {
        SonarLintLogger.get().error("Unable to serialize PL cache: " + caught.getMessage());
      }
    } else {
      SonarLintLogger.get().debug("No PL cache overwrite");
    }

    return slintPL;
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
    try (OutputStream out = new FileOutputStream(serFile); OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8); BufferedWriter writer = new BufferedWriter(osw)) {
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

  public String getRCodeCache(ICodeModel codeModel) {
    long strtTime = System.nanoTime();
    StringBuilder rcodeCache = new StringBuilder();
    for (ITypeInfo info : codeModel.getAllTypes()) {
      if ((info.getRfilePath() != null) && !info.getRfilePath().trim().isEmpty() && !info.isInsidePL()) {
        rcodeCache.append(info.getFullName()).append(':').append(info.getRfilePath()).append(System.lineSeparator());
      }
    }
    long elapsedTime = System.nanoTime() - strtTime;
    SonarLintLogger.get().info("RCode Cache Generation: " + (elapsedTime / 1_000_000L) + " ms");

    return rcodeCache.toString();
  }

}
