/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.workingsets;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.jdt.internal.ui.search.WorkingSetComparator;
/**
 * Working set filter actions (set / clear)
 * 
 * @since 2.0
 * 
 */
public class WorkingSetFilterActionGroup extends ActionGroup {

	private static final String TAG_WORKING_SET_NAME= "workingSetName"; //$NON-NLS-1$
	private static final String SEPARATOR_ID= "workingSetGroupSeparator"; //$NON-NLS-1$

	private StructuredViewer fViewer;
	private WorkingSetFilter fWorkingSetFilter;
	
	private IWorkingSet fWorkingSet= null;
	
	private ClearWorkingSetAction fClearWorkingSetAction;
	private SelectWorkingSetAction fSelectWorkingSetAction;
	private EditWorkingSetAction fEditWorkingSetAction;
	
	private IPropertyChangeListener fPropertyChangeListener;
	private IPropertyChangeListener fTitleUpdater;
	
	private int fLRUMenuCount;
	private IMenuManager fMenuManager;
	private IMenuListener fMenuListener;

	public WorkingSetFilterActionGroup(StructuredViewer viewer, String viewId, Shell shell, IPropertyChangeListener titleUpdater) {
		Assert.isNotNull(viewer);
		Assert.isNotNull(viewId);
		Assert.isNotNull(shell);

		fViewer= viewer;
		fTitleUpdater= titleUpdater;
		fClearWorkingSetAction= new ClearWorkingSetAction(this);
		fSelectWorkingSetAction= new SelectWorkingSetAction(this, shell);
		fEditWorkingSetAction= new EditWorkingSetAction(this, shell);
		fPropertyChangeListener= addWorkingSetChangeSupport();
	}

	/**
	 * Returns the working set which is used by the filter.
	 * 
	 * @return the working set
	 */
	public IWorkingSet getWorkingSet() {
		return fWorkingSet;
	}
		
	/**
	 * Sets this filter's working set.
	 * 
	 * @param workingSet the working set
	 * @param refreshViewer Indiactes if the viewer should be refreshed.
	 */
	public void setWorkingSet(IWorkingSet workingSet, boolean refreshViewer){
		// Update action
		fClearWorkingSetAction.setEnabled(workingSet != null);
		fEditWorkingSetAction.setEnabled(workingSet != null);

		fWorkingSet= workingSet;

		// Update viewer
		if (fWorkingSetFilter != null) {
			fWorkingSetFilter.setWorkingSet(workingSet);	
			if (refreshViewer) {
				fViewer.getControl().setRedraw(false);
				fViewer.refresh();
				fViewer.getControl().setRedraw(true);
			}
			if (fTitleUpdater != null)
				fTitleUpdater.propertyChange(new PropertyChangeEvent(this, IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE, null, workingSet));
		}
	}
	
	/**
	 * Saves the state of the filter actions in a memento.
	 */
	public void saveState(IMemento memento) {
		String workingSetName= ""; //$NON-NLS-1$
		if (fWorkingSet != null)
			workingSetName= fWorkingSet.getName();
		memento.putString(TAG_WORKING_SET_NAME, workingSetName);
	}

	/**
	 * Restores the state of the filter actions from a memento.
	 * <p>
	 * Note: This method does not refresh the viewer.
	 * </p>
	 * @param memento
	 */	
	public void restoreState(IMemento memento) {
		String workingSetName= memento.getString(TAG_WORKING_SET_NAME);
		IWorkingSet ws= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSet(workingSetName);
		setWorkingSet(ws, false);
	}
	

	/* (non-Javadoc)
	 * @see ActionGroup#fillActionBars(IActionBars)
	 */
	public void fillActionBars(IActionBars actionBars) {
		contributeToToolBar(actionBars.getToolBarManager());
		contributeToMenu(actionBars.getMenuManager());
	};
	
	/**
	 * Adds the filter actions to the tool bar
	 */
	public void contributeToToolBar(IToolBarManager tbm) {
		// do nothing
	}

	/**
	 * Adds the filter actions to the menu
	 */
	public void contributeToMenu(IMenuManager mm) {
		mm.add(fSelectWorkingSetAction);
		mm.add(fClearWorkingSetAction);
		mm.add(fEditWorkingSetAction);
		mm.add(new Separator());
		mm.add(new Separator(SEPARATOR_ID));
		addLRUWorkingSetActions(mm);
		
		fMenuManager= mm;
		fMenuListener= new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				removePreviousLRUWorkingSetActions(manager);
				addLRUWorkingSetActions(manager);
			}
		};
		fMenuManager.addMenuListener(fMenuListener);
	}
	
	private void removePreviousLRUWorkingSetActions(IMenuManager mm) {
		for (int i= 1; i <= fLRUMenuCount; i++)
			mm.remove(WorkingSetMenuContributionItem.getId(i));
	}

	private void addLRUWorkingSetActions(IMenuManager mm) {
		IWorkingSet[] workingSets= PlatformUI.getWorkbench().getWorkingSetManager().getRecentWorkingSets();
		List sortedWorkingSets= Arrays.asList(workingSets);
		Collections.sort(sortedWorkingSets, new WorkingSetComparator());
		
		Iterator iter= sortedWorkingSets.iterator();
		int i= 0;
		while (iter.hasNext()) {
			IWorkingSet workingSet= (IWorkingSet)iter.next();
			if (workingSet != null) {
				IContributionItem item= new WorkingSetMenuContributionItem(++i, this, workingSet);
				mm.insertBefore(SEPARATOR_ID, item);
			}
		}
		fLRUMenuCount= i;
	}
	
	/* (non-Javadoc)
	 * @see ActionGroup#dispose()
	 */
	public void dispose() {
		if (fMenuManager != null)
			fMenuManager.removeMenuListener(fMenuListener);
		super.dispose();
	}
	
	private IPropertyChangeListener addWorkingSetChangeSupport() {
		final IPropertyChangeListener propertyChangeListener= createWorkingSetChangeListener();

		fWorkingSetFilter= new WorkingSetFilter();
		fViewer.addFilter(fWorkingSetFilter);

		// Register listener on working set manager
		PlatformUI.getWorkbench().getWorkingSetManager().addPropertyChangeListener(propertyChangeListener);
		
		// Register dispose listener which removes the listeners
		fViewer.getControl().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				PlatformUI.getWorkbench().getWorkingSetManager().removePropertyChangeListener(propertyChangeListener);
			}
		});
		
		return propertyChangeListener;		
	}

	private IPropertyChangeListener createWorkingSetChangeListener() {
		return new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				String property= event.getProperty();
				if (IWorkbenchPage.CHANGE_WORKING_SET_REPLACE.equals(property)) {
					IWorkingSet newWorkingSet= (IWorkingSet) event.getNewValue();

					fWorkingSetFilter.setWorkingSet(newWorkingSet);	

					fViewer.getControl().setRedraw(false);
					fViewer.refresh();
					if (fTitleUpdater != null)
						fTitleUpdater.propertyChange(new PropertyChangeEvent(this, IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE, null, newWorkingSet));
					fViewer.getControl().setRedraw(true);
				} else if (IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE.equals(property)) {
					if (fTitleUpdater != null)
						fTitleUpdater.propertyChange(event);
				} else if (IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE.equals(property)) {
					fViewer.getControl().setRedraw(false);
					fViewer.refresh();
					fViewer.getControl().setRedraw(true);
				}
			}
		};
	}
}