/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.actions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.internal.corext.refactoring.rename.RenameResourceProcessor;

import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.UserInterfaceStarter;
import org.eclipse.jdt.internal.ui.refactoring.reorg.RenameUserInterfaceManager;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

public class RenameResourceAction extends SelectionDispatchAction {

	public RenameResourceAction(IWorkbenchSite site) {
		super(site);
	}
	
	public void selectionChanged(IStructuredSelection selection) {
		IResource element= getResource(selection);
		if (element == null) {
			setEnabled(false);
		} else {
			RenameResourceProcessor processor= new RenameResourceProcessor(element);
			try {
				setEnabled(processor.isApplicable());
			} catch (CoreException e) {
				setEnabled(false);
			}
		}
	}

	public void run(IStructuredSelection selection) {
		IResource resource = getResource(selection);
		// Work around for http://dev.eclipse.org/bugs/show_bug.cgi?id=19104		
		if (!ActionUtil.isProcessable(getShell(), resource))
			return;
		RenameResourceProcessor processor= new RenameResourceProcessor(resource);
		try {
			if(!processor.isApplicable())
				return;
			RenameRefactoring refactoring= new RenameRefactoring(processor);
			UserInterfaceStarter starter= RenameUserInterfaceManager.getDefault().getStarter(refactoring);
			starter.activate(refactoring, getShell(), true);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, RefactoringMessages.getString("RenameJavaElementAction.name"), RefactoringMessages.getString("RenameJavaElementAction.exception"));  //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	private static IResource getResource(IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;
		Object first= selection.getFirstElement();
		if (! (first instanceof IResource))
			return null;
		return (IResource)first;
	}
}
