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
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import java.util.List;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.MoveProjectAction;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

import org.eclipse.jdt.internal.corext.refactoring.reorg.MoveRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;


public class ReorgMoveAction extends SelectionDispatchAction {

	public ReorgMoveAction(IWorkbenchSite site) {
		super(site);
		setText("&Move...");
		setDescription("Move the selected elements");

		update(getSelection());
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.MOVE_ACTION);
	}

	public void selectionChanged(IStructuredSelection selection) {
		if (canDelegateToWorkbenchAction(selection)) {
			setEnabled(createWorkbenchAction(selection).isEnabled());
			return;
		}
		try {
			List elements= selection.toList();
			IResource[] resources= ReorgUtils.getResources(elements);
			IJavaElement[] javaElements= ReorgUtils.getJavaElements(elements);
			if (elements.size() != resources.length + javaElements.length)
				setEnabled(false);
			else
				setEnabled(canEnable(resources, javaElements));
		} catch (JavaModelException e) {
			//no ui here - this happens on selection changes
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.filterNotPresentException(e))
				JavaPlugin.log(e);
			setEnabled(false);
		}
	}
	
	private boolean canEnable(IResource[] resources, IJavaElement[] javaElements) throws JavaModelException {
		return MoveRefactoring.isAvailable(resources, javaElements, JavaPreferencesSettings.getCodeGenerationSettings());
	}

	private MoveProjectAction createWorkbenchAction(IStructuredSelection selection) {
		MoveProjectAction action= new MoveProjectAction(JavaPlugin.getActiveWorkbenchShell());
		action.selectionChanged(selection);
		return action;
	}
	
	private boolean canDelegateToWorkbenchAction(IStructuredSelection selection) {
		return ReorgUtils.containsOnlyProjects(selection.toList());
	}

	public void run(IStructuredSelection selection) {
		if (canDelegateToWorkbenchAction(selection)){
			createWorkbenchAction(selection).run();
			return;
		}
		try {
			List elements= selection.toList();
			IResource[] resources= ReorgUtils.getResources(elements);
			IJavaElement[] javaElements= ReorgUtils.getJavaElements(elements);
			if (canEnable(resources, javaElements)) 
				startRefactoring(resources, javaElements);
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), RefactoringMessages.getString("OpenRefactoringWizardAction.exception")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void startRefactoring(IResource[] resources, IJavaElement[] javaElements) throws JavaModelException{
		MoveRefactoring refactoring= createRefactoring(resources, javaElements);
		RefactoringWizard wizard= createWizard(refactoring);
		/*
		 * We want to get the shell from the refactoring dialog but it's not known at this point, 
		 * so we pass the wizard and then, once the dialog is open, we will have access to its shell.
		 */
		refactoring.setReorgQueries(new ReorgQueries(wizard));
		new RefactoringStarter().activate(refactoring, wizard, getShell(), RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), true); //$NON-NLS-1$
	}

	private RefactoringWizard createWizard(MoveRefactoring refactoring) {
		return new ReorgMoveWizard(refactoring);
	}

	private MoveRefactoring createRefactoring(IResource[] resources, IJavaElement[] javaElements) throws JavaModelException {
		return MoveRefactoring.create(resources, javaElements, JavaPreferencesSettings.getCodeGenerationSettings());
	}

}
