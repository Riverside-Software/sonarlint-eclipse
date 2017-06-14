/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2017 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.server.wizard;

import javax.annotation.Nullable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.jobs.ServerUpdateJob;
import org.sonarlint.eclipse.core.internal.server.IServer;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;
import org.sonarlint.eclipse.ui.internal.server.actions.JobUtils;
import org.sonarlint.eclipse.ui.internal.server.wizard.ServerConnectionModel.AuthMethod;
import org.sonarlint.eclipse.ui.internal.server.wizard.ServerConnectionModel.ConnectionType;

public class ServerConnectionWizard extends Wizard implements INewWizard {

  private final ServerConnectionModel model;
  private final ConnectionTypeWizardPage connectionTypeWizardPage;
  private final UrlWizardPage urlPage;
  private final AuthMethodWizardPage authMethodPage;
  private final UsernamePasswordWizardPage credentialsPage;
  private final TokenWizardPage tokenPage;
  private final OrganizationWizardPage orgPage;
  private final ServerIdWizardPage serverIdPage;
  private final EndWizardPage endPage;
  private final IServer editedServer;

  private ServerConnectionWizard(String title, ServerConnectionModel model, IServer editedServer) {
    super();
    this.model = model;
    this.editedServer = editedServer;
    setNeedsProgressMonitor(true);
    setWindowTitle(title);
    setHelpAvailable(false);
    connectionTypeWizardPage = new ConnectionTypeWizardPage(model);
    urlPage = new UrlWizardPage(model);
    authMethodPage = new AuthMethodWizardPage(model);
    credentialsPage = new UsernamePasswordWizardPage(model);
    tokenPage = new TokenWizardPage(model);
    orgPage = new OrganizationWizardPage(model);
    serverIdPage = new ServerIdWizardPage(model);
    endPage = new EndWizardPage(model);
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    // Nothing to do
  }

  public ServerConnectionWizard() {
    this("Connect to a SonarQube Server", new ServerConnectionModel(), null);
  }

  public ServerConnectionWizard(String serverId) {
    this();
    model.setServerId(serverId);
  }

  public ServerConnectionWizard(IServer sonarServer) {
    this("Edit connection to a SonarQube Server", new ServerConnectionModel(sonarServer), sonarServer);
  }

  @Override
  public IWizardPage getStartingPage() {
    if (!model.isEdit()) {
      return connectionTypeWizardPage;
    }
    return firstPageAfterConnectionType();
  }

  @Override
  public void addPages() {
    if (!model.isEdit()) {
      addPage(connectionTypeWizardPage);
      addPage(serverIdPage);
    }
    addPage(urlPage);
    addPage(authMethodPage);
    addPage(credentialsPage);
    addPage(tokenPage);
    addPage(orgPage);
    addPage(endPage);
  }

  @Override
  public IWizardPage getNextPage(IWizardPage page) {
    if (page == connectionTypeWizardPage) {
      return firstPageAfterConnectionType();
    }
    if (page == urlPage) {
      return authMethodPage;
    }
    if (page == authMethodPage) {
      return model.getAuthMethod() == AuthMethod.PASSWORD ? credentialsPage : tokenPage;
    }
    if (page == credentialsPage || page == tokenPage) {
      return orgPage;
    }
    if (page == orgPage) {
      return model.isEdit() ? endPage : serverIdPage;
    }
    if (page == serverIdPage) {
      return endPage;
    }
    return null;
  }

  @Override
  public IWizardPage getPreviousPage(IWizardPage page) {
    // This method is only used for the first page of a wizard,
    // because every following page remember the previous one on its own
    return null;
  }

  private IWizardPage firstPageAfterConnectionType() {
    // Skip URL and auth method page if SonarCloud
    return model.getConnectionType() == ConnectionType.SONARCLOUD ? tokenPage : urlPage;
  }

  @Override
  public boolean canFinish() {
    IWizardPage currentPage = getContainer().getCurrentPage();
    return currentPage == endPage && super.canFinish();
  }

  @Override
  public boolean performFinish() {
    if (model.isEdit() && !testConnection(model.getOrganization())) {
      return false;
    }
    IServer server;
    if (model.isEdit()) {
      editedServer.updateConfig(model.getServerUrl(), model.getOrganization(), model.getUsername(), model.getPassword());
      server = editedServer;
    } else {
      server = SonarLintCorePlugin.getServersManager().create(model.getServerId(), model.getServerUrl(), model.getOrganization(), model.getUsername(), model.getPassword());
      SonarLintCorePlugin.getServersManager().addServer(server, model.getUsername(), model.getPassword());
    }

    Job j = new ServerUpdateJob(server);
    j.addJobChangeListener(new JobChangeAdapter() {
      @Override
      public void done(IJobChangeEvent event) {
        if (event.getResult().isOK()) {
          JobUtils.scheduleAnalysisOfOpenFilesInBoundProjects(server, TriggerType.BINDING_CHANGE);
        }
      }
    });
    j.schedule();
    return true;
  }

  public boolean beforeNextPressed() {
    IWizardPage currentPage = getContainer().getCurrentPage();
    if (currentPage == credentialsPage || currentPage == tokenPage) {
      if (!testConnection(null)) {
        return false;
      }
    }
    if (currentPage == orgPage && !testConnection(model.getOrganization())) {
      return false;
    }
    return true;
  }

  private boolean testConnection(@Nullable String organization) {
    IWizardPage currentPage = getContainer().getCurrentPage();
    IStatus status;
    try {
      ServerConnectionTestJob testJob = new ServerConnectionTestJob(model.getServerUrl(), organization, model.getUsername(), model.getPassword());
      getContainer().run(true, true, testJob);
      status = testJob.getStatus();
    } catch (OperationCanceledException e1) {
      return false;
    } catch (Exception e1) {
      status = new Status(IStatus.ERROR, SonarLintUiPlugin.PLUGIN_ID, Messages.ServerLocationWizardPage_msg_error + " " +
        e1.getMessage(), e1);
    }

    String message = status.getMessage();
    if (status.getSeverity() != IStatus.OK) {
      ((WizardPage) currentPage).setMessage(message, IMessageProvider.ERROR);
      return false;
    }

    return true;
  }

}