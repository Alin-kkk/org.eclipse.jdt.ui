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
package org.eclipse.jdt.internal.ui.refactoring.actions;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.InlineConstantWizard;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineConstantRefactoring;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

/**
 * Inlines a constant.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 */
public class InlineConstantAction extends SelectionDispatchAction {

	private CompilationUnitEditor fEditor;
	
	private static final String DIALOG_TITLE= RefactoringMessages.getString("InlineConstantAction.dialog_title"); //$NON-NLS-1$

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public InlineConstantAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	public InlineConstantAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.getString("InlineConstantAction.inline_Constant")); //$NON-NLS-1$
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.INLINE_ACTION);		
	}

	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(canEnable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);//no ui
		}
	}

    /*
     * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jface.text.ITextSelection)
     */
    protected void selectionChanged(ITextSelection selection) {
       //do nothing
    }

	private static boolean canEnable(IStructuredSelection selection) throws JavaModelException{
		if (selection.isEmpty() || selection.size() != 1) 
			return false;
		
		Object first= selection.getFirstElement();
		return (first instanceof IField) && shouldAcceptElement((IField)first);
	}

	private static boolean shouldAcceptElement(IField field) throws JavaModelException {
		return (! field.isBinary() && JdtFlags.isStatic(field) && JdtFlags.isFinal(field));
	}
	
	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	protected void run(IStructuredSelection selection) {		
		try {
			Assert.isTrue(canEnable(selection));
			
			Object first= selection.getFirstElement();
			Assert.isTrue(first instanceof IField);
			
			IField field= (IField) first;
			run(field.getNameRange().getOffset(), field.getNameRange().getLength(), field.getCompilationUnit());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), DIALOG_TITLE, RefactoringMessages.getString("InlineConstantAction.unexpected_exception"));	 //$NON-NLS-1$
		}
 	}	
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */		
	protected void run(ITextSelection selection) {
		run(selection.getOffset(), selection.getLength(), getCompilationUnitForTextSelection());
	}
	
	private void run(int selectionOffset, int selectionLength, ICompilationUnit cu) {
		Assert.isNotNull(cu);
		Assert.isTrue(selectionOffset >= 0);
		Assert.isTrue(selectionLength >= 0);
		if (!ActionUtil.isProcessable(getShell(), cu))
			return;
		
		InlineConstantRefactoring refactoring= InlineConstantRefactoring.create(
			cu, selectionOffset, selectionLength,
			JavaPreferencesSettings.getCodeGenerationSettings());
		if (refactoring == null) {
			MessageDialog.openInformation(getShell(), DIALOG_TITLE, RefactoringMessages.getString("InlineConstantAction.no_constant_reference_or_declaration")); //$NON-NLS-1$
			return;
		}
		try {
			new RefactoringStarter().activate(refactoring, createWizard(refactoring), getShell(), DIALOG_TITLE, true);
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), DIALOG_TITLE, RefactoringMessages.getString("InlineConstantAction.unexpected_exception"));	 //$NON-NLS-1$
		}		
	}
	
	private ICompilationUnit getCompilationUnitForTextSelection() {
		Assert.isNotNull(fEditor);
		return SelectionConverter.getInputAsCompilationUnit(fEditor);
	}
	
	private static RefactoringWizard createWizard(InlineConstantRefactoring refactoring) {
		return new InlineConstantWizard(refactoring);
	}
}
