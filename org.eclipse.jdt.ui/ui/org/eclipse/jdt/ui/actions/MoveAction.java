/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInstanceMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveStaticMembersProcessor;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.MoveInstanceMethodAction;
import org.eclipse.jdt.internal.ui.refactoring.actions.MoveStaticMembersAction;
import org.eclipse.jdt.internal.ui.refactoring.reorg.ReorgMoveAction;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * This action moves Java elements to a new location. The action prompts
 * the user for the new location.
 * <p>
 * The action is applicable to a homogeneous selection containing either
 * projects, package fragment roots, package fragments, compilation units,
 * or static methods.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class MoveAction extends SelectionDispatchAction{
//TODO: remove duplicate availability checks. Look at
//- f...Action.selectionChanged
//- f...Action.isEnabled
//- ...Refactoring.isAvailable
//- try...
//... and remove duplicated code for text/structured selections.
//We have to clean this up, once we have a long term solution to
//bug 35748 (no JavaElements for local types). 
	
	private CompilationUnitEditor fEditor;
	private MoveInstanceMethodAction fMoveInstanceMethodAction;
	private MoveStaticMembersAction fMoveStaticMembersAction;
	private ReorgMoveAction fReorgMoveAction;
	
	/**
	 * Creates a new <code>MoveAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public MoveAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.getString("MoveAction.text")); //$NON-NLS-1$
		fMoveStaticMembersAction= new MoveStaticMembersAction(site);
		fMoveInstanceMethodAction= new MoveInstanceMethodAction(site);
		fReorgMoveAction= new ReorgMoveAction(site);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.MOVE_ACTION);
	}
	
	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 */
	public MoveAction(CompilationUnitEditor editor) {
		super(editor.getEditorSite());
		fEditor= editor;
		setText(RefactoringMessages.getString("MoveAction.text")); //$NON-NLS-1$
		fMoveStaticMembersAction= new MoveStaticMembersAction(editor);
		fMoveInstanceMethodAction= new MoveInstanceMethodAction(editor);
		fReorgMoveAction= new ReorgMoveAction(editor.getEditorSite());
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.MOVE_ACTION);
	}	

	/*
	 * @see ISelectionChangedListener#selectionChanged(SelectionChangedEvent)
	 */
	public void selectionChanged(SelectionChangedEvent event) {
		fMoveStaticMembersAction.selectionChanged(event);
		fMoveInstanceMethodAction.selectionChanged(event);
		fReorgMoveAction.selectionChanged(event);
		setEnabled(computeEnableState());	
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	public void run(IStructuredSelection selection) {
		try {
			if (fMoveInstanceMethodAction.isEnabled() && tryMoveInstanceMethod(selection)) 
				return;
	
			if (fMoveStaticMembersAction.isEnabled() && tryMoveStaticMembers(selection)) 
				return;
	
			if (fReorgMoveAction.isEnabled())
				fReorgMoveAction.run();
		
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), RefactoringMessages.getString("OpenRefactoringWizardAction.exception")); //$NON-NLS-1$ //$NON-NLS-2$
		}

	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.text.ITextSelection)
	 */
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isProcessable(getShell(), fEditor))
				return;
			if (fMoveStaticMembersAction.isEnabled() && tryMoveStaticMembers(selection))
				return;
		
			if (fMoveInstanceMethodAction.isEnabled() && tryMoveInstanceMethod(selection))
				return;
	
			if (tryReorgMove(selection))
				return;
			
			MessageDialog.openInformation(getShell(), RefactoringMessages.getString("MoveAction.Move"), RefactoringMessages.getString("MoveAction.select")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), RefactoringMessages.getString("OpenRefactoringWizardAction.exception")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	private boolean tryMoveStaticMembers(ITextSelection selection) throws JavaModelException{
		IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
		if (element == null || !(element instanceof IMember))
			return false;
		IMember[] array= new IMember[]{(IMember)element};
		if (! MoveStaticMembersProcessor.isAvailable(array))	
			return false;
		IJavaProject project= null;
		if (array.length > 0)
			project= array[0].getJavaProject();
		MoveStaticMembersProcessor refactoring= MoveStaticMembersProcessor.create(array, JavaPreferencesSettings.getCodeGenerationSettings(project));
		if (refactoring == null)
			return false;
		fMoveStaticMembersAction.run(selection);
		return true;			
	}

	private static IMember[] getSelectedMembers(IStructuredSelection selection){
		if (selection.isEmpty())
			return null;
		
		for  (Iterator iter= selection.iterator(); iter.hasNext(); ) {
			if (! (iter.next() instanceof IMember))
				return null;
		}
		return convertToMemberArray(selection.toArray());
	}

	private static IMember[] convertToMemberArray(Object[] obj) {
		if (obj == null)
			return null;
		Set memberSet= new HashSet();
		memberSet.addAll(Arrays.asList(obj));
		return (IMember[]) memberSet.toArray(new IMember[memberSet.size()]);
	}

	private boolean tryMoveStaticMembers(IStructuredSelection selection) throws JavaModelException{
		IMember[] array= getSelectedMembers(selection);
		if (! MoveStaticMembersProcessor.isAvailable(array))	
			return false;
		IJavaProject project= null;
		if (array.length > 0)
			project= array[0].getJavaProject();
		MoveStaticMembersProcessor refactoring= MoveStaticMembersProcessor.create(array, JavaPreferencesSettings.getCodeGenerationSettings(project));
		if (refactoring == null)
			return false;
		fMoveStaticMembersAction.run(selection);
		return true;			
	}
	
	private boolean tryMoveInstanceMethod(ITextSelection selection) throws JavaModelException{
		IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
		if (element == null || !(element instanceof IMethod))
			return false;
		
		IMethod method= (IMethod)element;
		if (! MoveInstanceMethodRefactoring.isAvailable(method))
			return false;	
		MoveInstanceMethodRefactoring refactoring= MoveInstanceMethodRefactoring.create(method, JavaPreferencesSettings.getCodeGenerationSettings(method.getJavaProject()));
		if (refactoring == null)
			return false;
		fMoveInstanceMethodAction.run(selection);	
		return true;
	}

	private boolean tryMoveInstanceMethod(IStructuredSelection selection) throws JavaModelException{
		IMethod method= getSingleSelectedMethod(selection);
		if (method == null)
			return false;	
		if (! MoveInstanceMethodRefactoring.isAvailable(method))
			return false;	
		MoveInstanceMethodRefactoring refactoring= MoveInstanceMethodRefactoring.create(method, JavaPreferencesSettings.getCodeGenerationSettings(method.getJavaProject()));
		if (refactoring == null)
			return false;
		fMoveInstanceMethodAction.run(selection);	
		return true;
	}

	private static IMethod getSingleSelectedMethod(IStructuredSelection selection) {
		if (selection.isEmpty() || selection.size() != 1) 
			return null;
		
		Object first= selection.getFirstElement();
		if (! (first instanceof IMethod))
			return null;
		return (IMethod) first;
	}
	

	private boolean tryReorgMove(ITextSelection selection) throws JavaModelException{
		IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
		if (element == null)
			return false;
		StructuredSelection mockStructuredSelection= new StructuredSelection(element);
		fReorgMoveAction.selectionChanged(mockStructuredSelection);
		if (!fReorgMoveAction.isEnabled())
			return false;
			
		fReorgMoveAction.run(mockStructuredSelection);
		return true;			
	}


	/*
	 * @see SelectionDispatchAction#update(ISelection)
	 */
	public void update(ISelection selection) {
		fMoveStaticMembersAction.update(selection);
		fMoveInstanceMethodAction.update(selection);
		fReorgMoveAction.update(selection);
		setEnabled(computeEnableState());
	}
	
	private boolean computeEnableState(){
		return fMoveStaticMembersAction.isEnabled()
				|| fMoveInstanceMethodAction.isEnabled()
				|| fReorgMoveAction.isEnabled();
	}
}
