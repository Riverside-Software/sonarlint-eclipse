/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2020 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.flowlocations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.event.AnalysisEvent;
import org.sonarlint.eclipse.core.internal.event.AnalysisListener;
import org.sonarlint.eclipse.core.internal.markers.MarkerFlow;
import org.sonarlint.eclipse.core.internal.markers.MarkerFlowLocation;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils;
import org.sonarlint.eclipse.ui.internal.util.SelectionUtils;
import org.sonarlint.eclipse.ui.internal.views.issues.OnTheFlyIssuesView;
import org.sonarlint.eclipse.ui.internal.views.issues.SonarLintReportView;
import org.sonarlint.eclipse.ui.internal.views.locations.IssueLocationsView;

public class SonarLintFlowLocationsService implements ISelectionListener, AnalysisListener {

  private final CopyOnWriteArrayList<SonarLintMarkerSelectionListener> markerSelectionListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<SonarLintFlowSelectionListener> flowSelectionListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<SonarLintFlowLocationSelectionListener> flowLocationSelectionListeners = new CopyOnWriteArrayList<>();
  private Optional<IMarker> lastSelectedMarker = Optional.empty();
  private Optional<MarkerFlow> lastSelectedFlow = Optional.empty();
  private Optional<MarkerFlowLocation> lastSelectedFlowLocation = Optional.empty();
  private boolean showAnnotationsInEditor = true;

  private static final Set<String> sonarlintMarkerViewsIds = new HashSet<>(Arrays.asList(SonarLintReportView.ID, OnTheFlyIssuesView.ID));

  @Override
  public void selectionChanged(IWorkbenchPart part, ISelection selection) {
    if (part instanceof IViewPart && sonarlintMarkerViewsIds.contains(((IViewPart) part).getViewSite().getId())) {
      IMarker selectedMarker = SelectionUtils.findSelectedSonarLintMarker(selection);
      markerSelected(selectedMarker, false);
    }
  }

  @Override
  public void usedAnalysis(AnalysisEvent event) {
    Display.getDefault().asyncExec(() -> {
      if (lastSelectedMarker.isPresent()) {
        IMarker lastMarker = lastSelectedMarker.get();
        if (!lastMarker.exists()) {
          // Marker has been deleted during the last analysis
          markerSelected(null, false);
        } else {
          List<MarkerFlow> newIssueFlows = MarkerUtils.getIssueFlows(lastSelectedMarker.get());
          // Try to reselect the same flow number than before
          Integer pastFlowNum = lastSelectedFlow.map(MarkerFlow::getNumber).orElse(null);
          if (pastFlowNum != null && newIssueFlows.size() >= pastFlowNum) {
            lastSelectedFlow = Optional.of(newIssueFlows.get(pastFlowNum - 1));
          }
          // Try to select the same flow location
          Integer pastFlowLocationNum = lastSelectedFlowLocation.map(MarkerFlowLocation::getNumber).orElse(null);
          if (pastFlowLocationNum != null && lastSelectedFlow.isPresent() && lastSelectedFlow.get().getLocations().size() >= pastFlowLocationNum) {
            lastSelectedFlowLocation = Optional.of(lastSelectedFlow.get().getLocations().get(pastFlowLocationNum - 1));
          }

          // Force the redraw of everything related to flow, since locations in the marker have been updated
          notifyAllOfMarkerChange();
        }
      }
    });
  }

  public void addFlowSelectionListener(SonarLintFlowSelectionListener l) {
    flowSelectionListeners.addIfAbsent(l);
  }

  public void removeFlowSelectionListener(SonarLintFlowSelectionListener l) {
    flowSelectionListeners.remove(l);
  }

  public void addFlowLocationSelectionListener(SonarLintFlowLocationSelectionListener l) {
    flowLocationSelectionListeners.addIfAbsent(l);
  }

  public void removeFlowLocationSelectionListener(SonarLintFlowLocationSelectionListener l) {
    flowLocationSelectionListeners.remove(l);
  }

  public void addMarkerSelectionListener(SonarLintMarkerSelectionListener l) {
    markerSelectionListeners.addIfAbsent(l);
  }

  public void removeMarkerSelectionListener(SonarLintMarkerSelectionListener l) {
    markerSelectionListeners.remove(l);
  }

  public Optional<IMarker> getLastSelectedMarker() {
    return lastSelectedMarker;
  }

  public Optional<MarkerFlow> getLastSelectedFlow() {
    return lastSelectedFlow;
  }

  public Optional<MarkerFlowLocation> getLastSelectedFlowLocation() {
    return lastSelectedFlowLocation;
  }

  public void flowSelected(@Nullable MarkerFlow selectedFlow) {
    if (!Objects.equals(lastSelectedFlow.orElse(null), selectedFlow) || lastSelectedFlowLocation.isPresent()) {
      lastSelectedFlow = Optional.ofNullable(selectedFlow);
      lastSelectedFlowLocation = Optional.empty();
      flowSelectionListeners.forEach(l -> l.flowSelected(lastSelectedFlow));
    }
  }

  public void flowLocationSelected(@Nullable MarkerFlowLocation selectedFlowLocation) {
    if (!Objects.equals(lastSelectedFlowLocation.orElse(null), selectedFlowLocation)) {
      lastSelectedFlow = Optional.ofNullable((selectedFlowLocation != null ? selectedFlowLocation.getParent() : null));
      lastSelectedFlowLocation = Optional.ofNullable(selectedFlowLocation);
      flowLocationSelectionListeners.forEach(l -> l.flowLocationSelected(lastSelectedFlowLocation));
    }
  }

  public void markerSelected(@Nullable IMarker selectedMarker, boolean forceShowAnnotationsInEditor) {
    if (forceShowAnnotationsInEditor) {
      this.showAnnotationsInEditor = true;
    }
    if (selectedMarker != null && !MarkerUtils.getIssueFlows(selectedMarker).isEmpty()) {
      openIssueLocationsView(forceShowAnnotationsInEditor);
    }
    if (!Objects.equals(lastSelectedMarker.orElse(null), selectedMarker)) {
      lastSelectedMarker = Optional.ofNullable(selectedMarker);
      lastSelectedFlow = Optional.empty();
      lastSelectedFlowLocation = Optional.empty();
      if (selectedMarker != null) {
        List<MarkerFlow> issueFlow = MarkerUtils.getIssueFlows(selectedMarker);
        if (!MarkerUtils.isSecondaryLocations(issueFlow) && !issueFlow.isEmpty()) {
          // Select the first flow
          lastSelectedFlow = Optional.of(issueFlow.get(0));
        }
      }
      notifyAllOfMarkerChange();
    }
  }

  private void notifyAllOfMarkerChange() {
    markerSelectionListeners.forEach(l -> l.markerSelected(lastSelectedMarker));
  }

  private static void openIssueLocationsView(boolean forceShowAnnotationsInEditor) {
    try {
      IssueLocationsView view = (IssueLocationsView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(IssueLocationsView.ID);
      if (view == null) {
        view = (IssueLocationsView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(IssueLocationsView.ID);
      }
      if (forceShowAnnotationsInEditor) {
        view.setShowAnnotations(true);
      }
    } catch (PartInitException e) {
      SonarLintLogger.get().error("Unable to open Issue Locations View", e);
    }
  }

  public boolean isShowAnnotationsInEditor() {
    return showAnnotationsInEditor;
  }

  public void setShowAnnotationsInEditor(boolean enabled) {
    if (showAnnotationsInEditor != enabled) {
      this.showAnnotationsInEditor = enabled;
      notifyAllOfMarkerChange();
    }
  }
}