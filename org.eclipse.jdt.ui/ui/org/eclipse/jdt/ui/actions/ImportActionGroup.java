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

import java.util.Iterator;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.ExportResourcesAction;
import org.eclipse.ui.actions.ImportResourcesAction;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.IContextMenuConstants;

/**
 * Action group to add the Import and Export action to a view part's
 * context menu.
 * 
 * @sine 2.0
 */
public class ImportActionGroup extends ActionGroup {

	private static final String GROUP_IMPORT= "group.import"; //$NON-NLS-1$
	
	private ImportResourcesAction fImportAction;
	private ExportResourcesAction fExportAction;

	/**
	 * Creates a new <code>ImportActionGroup</code>.
	 * 
	 * @param part the view part that owns this action group
	 */
	public ImportActionGroup(IViewPart part) {
		IWorkbench workbench = part.getSite().getWorkbenchWindow().getWorkbench();
		fImportAction= new ImportResourcesAction(workbench);
		fExportAction= new ExportResourcesAction(workbench);			
	}
	
	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	public void fillContextMenu(IMenuManager menu) {
		menu.appendToGroup(IContextMenuConstants.GROUP_REORGANIZE, new Separator(GROUP_IMPORT));
		menu.appendToGroup(GROUP_IMPORT, fImportAction);
		menu.appendToGroup(GROUP_IMPORT, fExportAction);
		super.fillContextMenu(menu);
	}	
}
