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
package org.eclipse.jdt.ui.actions;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard;
import org.eclipse.jdt.internal.ui.refactoring.UseSupertypeWizard;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.structure.UseSupertypeWherePossibleRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Tries to use a super type of a class where possible.
 *  
 * @since 2.1
 */
public class UseSupertypeAction extends SelectionDispatchAction{
	private CompilationUnitEditor fEditor;
	
	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public UseSupertypeAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/**
	 * Creates a new <code>UseSupertypeAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public UseSupertypeAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.getString("UseSupertypeAction.use_Supertype")); //$NON-NLS-1$
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.USE_SUPERTYPE_ACTION);
	}
	
	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(canEnable(selection));
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.filterNotPresentException(e))
				JavaPlugin.log(e);
			setEnabled(false);//no ui - happens on selection changes
		}
	}

    /*
     * @see SelectionDispatchAction#selectionChanged(ITextSelection)
     */
	public void selectionChanged(ITextSelection selection) {
		//do nothing, this happens too often
	}
	
	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	public void run(IStructuredSelection selection) {
		try {
			if (canEnable(selection))
				startRefactoring(getSingleSelectedType(selection));
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), RefactoringMessages.getString("OpenRefactoringWizardAction.exception")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

    /*
     * @see SelectionDispatchAction#run(ITextSelection)
     */
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isProcessable(getShell(), fEditor))
				return;
			IType type= getSingleSelectedType();
			if (canRunOn(type)){
				startRefactoring(type);
			} else {
				String unavailable= RefactoringMessages.getString("UseSupertypeAction.to_activate"); //$NON-NLS-1$
				MessageDialog.openInformation(getShell(), RefactoringMessages.getString("OpenRefactoringWizardAction.unavailable"), unavailable); //$NON-NLS-1$
			}
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), RefactoringMessages.getString("OpenRefactoringWizardAction.exception")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
		
	private static boolean canEnable(IStructuredSelection selection) throws JavaModelException{
		return canRunOn(getSingleSelectedType(selection));
	}
	
	private static IType getSingleSelectedType(IStructuredSelection selection) throws JavaModelException{
		if (selection.isEmpty() || selection.size() != 1) 
			return null;
			
		Object first= selection.getFirstElement();
		if (first instanceof IType)
			return (IType)first;
		if (first instanceof ICompilationUnit)	
			return JavaElementUtil.getMainType((ICompilationUnit)first);
		return null;
	}
		
	private IType getSingleSelectedType(){
		IJavaElement[] elements= resolveElements();
		if (elements.length != 1)
			return null;
		if (elements[0] instanceof IType)
			return (IType)elements[0];
		return null;
	}
	
	private static boolean canRunOn(IType type) throws JavaModelException{
		return UseSupertypeWherePossibleRefactoring.isAvailable(type);
	}
	
	private IJavaElement[] resolveElements() {
		return SelectionConverter.codeResolveHandled(fEditor, getShell(),  RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"));  //$NON-NLS-1$
	}

	private static RefactoringWizard createWizard(UseSupertypeWherePossibleRefactoring refactoring){
		return new UseSupertypeWizard(refactoring);
	}
	
	private void startRefactoring(IType type) throws JavaModelException {
		UseSupertypeWherePossibleRefactoring refactoring= UseSupertypeWherePossibleRefactoring.create(type, JavaPreferencesSettings.getCodeGenerationSettings());
		Assert.isNotNull(refactoring);
		// Work around for http://dev.eclipse.org/bugs/show_bug.cgi?id=19104
		if (!ActionUtil.isProcessable(getShell(), refactoring.getInputType()))
			return;
		Object newElementToProcess= new RefactoringStarter().activate(refactoring, createWizard(refactoring), getShell(), RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), true); //$NON-NLS-1$
		if (newElementToProcess == null)
			return;
		IStructuredSelection mockSelection= new StructuredSelection(newElementToProcess);
		selectionChanged(mockSelection);
		if (isEnabled())
			run(mockSelection);
		else
			MessageDialog.openInformation(JavaPlugin.getActiveWorkbenchShell(), RefactoringMessages.getString("UseSupertypeAction.Refactoring"), RefactoringMessages.getString("UseSupertypeAction.not_possible")); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
