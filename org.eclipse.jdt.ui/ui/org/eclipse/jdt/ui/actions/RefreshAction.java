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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Action for refreshing the workspace from the local file system for
 * the selected resources and all of their descendents. This action
 * also considers external Jars managed by the Java Model.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 */
public class RefreshAction extends SelectionDispatchAction {

	/**
	 * Creates a new <code>RefreshAction</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public RefreshAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.getString("RefreshAction.label")); //$NON-NLS-1$
		setToolTipText(ActionMessages.getString("RefreshAction.toolTip")); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * Method declared in SelectionDispatchAction
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		setEnabled(checkEnabled(selection));
	}

	private boolean checkEnabled(IStructuredSelection selection) {
		if (selection.isEmpty())
			return true;
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			Object element= (Object) iter.next();
			if (element instanceof IAdaptable) {
				IResource resource= (IResource)((IAdaptable)element).getAdapter(IResource.class);
				if (resource == null)
					return false;
			} else {
				return false;
			}
		}
		return true;
	}

	/* (non-Javadoc)
	 * Method declared in SelectionDispatchAction
	 */
	protected void run(IStructuredSelection selection) {
		final IResource[] resources= getResources(selection);
		IWorkspaceRunnable operation= new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				monitor.beginTask(ActionMessages.getString("RefreshAction.progressMessage"), resources.length * 2); //$NON-NLS-1$
				monitor.subTask(""); //$NON-NLS-1$
				List javaElements= new ArrayList(5);
				for (int i= 0; i < resources.length; i++) {
					IResource resource= resources[i];
					resource.refreshLocal(IResource.DEPTH_INFINITE, new SubProgressMonitor(monitor, 1));
					if (resource.getType() == IResource.PROJECT) {
						checkLocationDeleted(((IProject)resource));
					}
					IJavaElement jElement= JavaCore.create(resource);
					if (jElement != null)
						javaElements.add(jElement);
				}
				IJavaModel model= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
				model.refreshExternalArchives(
					(IJavaElement[]) javaElements.toArray(new IJavaElement[javaElements.size()]),
					new SubProgressMonitor(monitor, resources.length));
			}
		};
		
		try {
			new ProgressMonitorDialog(getShell()).run(true, true, new WorkbenchRunnableAdapter(operation));
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), 
				ActionMessages.getString("RefreshAction.error.title"),  //$NON-NLS-1$
				ActionMessages.getString("RefreshAction.error.message")); //$NON-NLS-1$
		} catch (InterruptedException e) {
			// canceled
		}
	}
	
	private IResource[] getResources(IStructuredSelection selection) {
		if (selection.isEmpty()) {
			return new IResource[] {ResourcesPlugin.getWorkspace().getRoot()};
		}
		
		List result= new ArrayList(selection.size());
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			Object element= (Object) iter.next();
			if (element instanceof IAdaptable) {
				IResource resource= (IResource)((IAdaptable)element).getAdapter(IResource.class);
				if (resource != null)
					result.add(resource);
			}			
		}
		
		for (Iterator iter= result.iterator(); iter.hasNext();) {
			IResource resource= (IResource) iter.next();
			if (isDescendent(result, resource))
				iter.remove();			
		}
		
		return (IResource[]) result.toArray(new IResource[result.size()]);
	}
	
	private boolean isDescendent(List candidates, IResource element) {
		IResource parent= element.getParent();
		while (parent != null) {
			if (candidates.contains(parent))
				return true;
			parent= parent.getParent();
		}
		return false;
	}
	
	private void checkLocationDeleted(IProject project) throws CoreException {
		if (!project.exists())
			return;
		File location = project.getLocation().toFile();
		if (!location.exists()) {
			final String message = ActionMessages.getFormattedString(
				"RefreshAction.locationDeleted.message", //$NON-NLS-1$
				new Object[] {project.getName(), location.getAbsolutePath()});
			final boolean[] result= new boolean[1];
			// Must prompt user in UI thread (we're in the operation thread here).
			getShell().getDisplay().syncExec(new Runnable() {
				public void run() {
					result[0]= MessageDialog.openQuestion(getShell(), 
						ActionMessages.getString("RefreshAction.locationDeleted.title"), //$NON-NLS-1$
						message);
				}
			});
			if (result[0]) { 
				project.delete(true, true, null);
			}
		}
	}	
}

