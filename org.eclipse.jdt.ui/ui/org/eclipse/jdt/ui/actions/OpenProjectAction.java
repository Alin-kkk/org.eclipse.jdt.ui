/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Assert;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

/**
 * Action to open a closed project. Action presents a dialog from which the
 * user can select the projects to be opened.
 * 
 * @since 2.0
 */
public class OpenProjectAction extends Action implements IResourceChangeListener {
	
	private IWorkbenchSite fSite;

	/**
	 * Creates a new <code>OpenProjectAction</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public OpenProjectAction(IWorkbenchSite site) {
		Assert.isNotNull(site);
		fSite= site;
		setEnabled(hasCloseProjects());
	}
	
	/*
	 * @see IAction#run()
	 */
	public void run() {
		ElementListSelectionDialog dialog= new ElementListSelectionDialog(fSite.getShell(), new JavaElementLabelProvider());
		dialog.setTitle(ActionMessages.getString("OpenProjectAction.dialog.title")); //$NON-NLS-1$
		dialog.setMessage(ActionMessages.getString("OpenProjectAction.dialog.message")); //$NON-NLS-1$
		dialog.setElements(getClosedProjects());
		dialog.setMultipleSelection(true);
		int result= dialog.open();
		if (result != Dialog.OK)
			return;
		final Object[] projects= dialog.getResult();
		IRunnableWithProgress runnable= new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				monitor.beginTask("", projects.length); //$NON-NLS-1$
				MultiStatus errorStatus= null;
				for (int i = 0; i < projects.length; i++) {
					IProject project= (IProject)projects[i];
					try {
						project.open(new SubProgressMonitor(monitor, 1));
					} catch (CoreException e) {
						if (errorStatus == null)
							errorStatus = new MultiStatus(JavaPlugin.getPluginId(), IStatus.ERROR, ActionMessages.getString("OpenProjectAction.error.message"), e); //$NON-NLS-1$
						errorStatus.merge(e.getStatus());
					}
				}
				monitor.done();
				if (errorStatus != null)
					throw new InvocationTargetException(new CoreException(errorStatus));
			}
		};
		ProgressMonitorDialog pd= new ProgressMonitorDialog(fSite.getShell());
		try {
			pd.run(true, true, runnable);	
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, fSite.getShell(), 
				ActionMessages.getString("OpenProjectAction.dialog.title"), //$NON-NLS-1$
				ActionMessages.getString("OpenProjectAction.error.message")); //$NON-NLS-1$
		} catch (InterruptedException e) {
		}
	}
	
	/*
	 * @see IResourceChangeListener#resourceChanged(IResourceChangeEvent)
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta != null) {
			IResourceDelta[] projDeltas = delta.getAffectedChildren(IResourceDelta.CHANGED);
			for (int i = 0; i < projDeltas.length; ++i) {
				IResourceDelta projDelta = projDeltas[i];
				if ((projDelta.getFlags() & IResourceDelta.OPEN) != 0) {
					setEnabled(hasCloseProjects());
					return;
				}
			}
		}
	}
		
	private Object[] getClosedProjects() {
		IProject[] projects= ResourcesPlugin.getWorkspace().getRoot().getProjects();
		List result= new ArrayList(5);
		for (int i = 0; i < projects.length; i++) {
			IProject project= projects[i];
			if (!project.isOpen())
				result.add(project);
		}
		return result.toArray();
	}
	
	private boolean hasCloseProjects() {
		IProject[] projects= ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			if (!projects[i].isOpen())
				return true;
		}
		return false;
	}
}
