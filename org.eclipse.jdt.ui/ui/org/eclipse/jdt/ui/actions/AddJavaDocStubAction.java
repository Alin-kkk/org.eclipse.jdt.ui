/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
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
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.corext.codemanipulation.AddJavaDocStubOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Creates a Java Doc Stubs for the selected members.
 * <p>
 * Will open the parent compilation unit in a Java editor. The result is 
 * unsaved, so the user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing elements
 * of type <code>IMember</code>.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class AddJavaDocStubAction extends SelectionDispatchAction {

	private CompilationUnitEditor fEditor;

	/**
	 * Creates a new <code>AddJavaDocStubAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public AddJavaDocStubAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.getString("AddJavaDocStubAction.label")); //$NON-NLS-1$
		setDescription(ActionMessages.getString("AddJavaDocStubAction.description")); //$NON-NLS-1$
		setToolTipText(ActionMessages.getString("AddJavaDocStubAction.tooltip")); //$NON-NLS-1$
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.ADD_JAVADOC_STUB_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public AddJavaDocStubAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(checkEnabledEditor());
	}

	//---- Structured Viewer -----------------------------------------------------------
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		IMember[] members= getSelectedMembers(selection);
		setEnabled(members != null && members.length > 0 && JavaModelUtil.isEditable(members[0].getCompilationUnit()));
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */		
	protected void run(IStructuredSelection selection) {
		IMember[] members= getSelectedMembers(selection);
		if (members == null || members.length == 0) {
			return;
		}
		
		try {
			ICompilationUnit cu= members[0].getCompilationUnit();
			// open the editor, forces the creation of a working copy
			IEditorPart editor= EditorUtility.openInEditor(cu);
			
			ICompilationUnit workingCopyCU;
			IMember[] workingCopyMembers;
			if (cu.isWorkingCopy()) {
				workingCopyCU= cu;
				workingCopyMembers= members;
				synchronized (workingCopyCU) {
					workingCopyCU.reconcile();
				}
			
			} else {
				// get the corresponding elements from the working copy
				workingCopyCU= EditorUtility.getWorkingCopy(cu);
				if (workingCopyCU == null) {
					showError(ActionMessages.getString("AddJavaDocStubsAction.error.noWorkingCopy")); //$NON-NLS-1$
					return;
				}
				workingCopyMembers= new IMember[members.length];
				for (int i= 0; i < members.length; i++) {
					IMember member= members[i];
					IMember workingCopyMember= (IMember) JavaModelUtil.findMemberInCompilationUnit(workingCopyCU, member);
					if (workingCopyMember == null) {
						showError(ActionMessages.getFormattedString("AddJavaDocStubsAction.error.memberNotExisting", member.getElementName())); //$NON-NLS-1$
						return;
					}
					workingCopyMembers[i]= workingCopyMember;
				}
			}
			
			run(workingCopyMembers);
			synchronized (workingCopyCU) {
				workingCopyCU.reconcile();
			}					
			EditorUtility.revealInEditor(editor, members[0]);
			
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.getString("AddJavaDocStubsAction.error.actionFailed")); //$NON-NLS-1$
		}
	}
	
	//---- Java Editior --------------------------------------------------------------
	
	/* package */ void editorStateChanged() {
		setEnabled(checkEnabledEditor());
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */		
	protected void selectionChanged(ITextSelection selection) {
	}

	private boolean checkEnabledEditor() {
		return fEditor != null && !fEditor.isEditorInputReadOnly() && SelectionConverter.canOperateOn(fEditor);
	}	
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */		
	protected void run(ITextSelection selection) {
		try {
			IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
			int type= element != null ? element.getElementType() : -1;
			if (type != IJavaElement.METHOD && type != IJavaElement.TYPE) {
		 		element= SelectionConverter.getTypeAtOffset(fEditor);
		 		if (element == null) {
					MessageDialog.openInformation(getShell(), getDialogTitle(), 
						ActionMessages.getString("AddJavaDocStubsAction.not_applicable")); //$NON-NLS-1$
					return;
		 		}
			}
			run(new IMember[] { (IMember)element });
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.getString("AddJavaDocStubsAction.error.actionFailed")); //$NON-NLS-1$
		}
	}

	//---- Helpers -------------------------------------------------------------------
	
	private void run(IMember[] members) {
		try {
			CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();

			AddJavaDocStubOperation op= new AddJavaDocStubOperation(members, settings);
			ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());
			dialog.run(false, true, new WorkbenchRunnableAdapter(op));					
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.getString("AddJavaDocStubsAction.error.actionFailed")); //$NON-NLS-1$
		} catch (InterruptedException e) {
			// operation cancelled
		}
	}
	
	private void showError(String message) {
		MessageDialog.openError(getShell(), getDialogTitle(), message);
	}
	
	private IMember[] getSelectedMembers(IStructuredSelection selection) {
		List elements= selection.toList();
		int nElements= elements.size();
		if (nElements > 0) {
			IMember[] res= new IMember[nElements];
			ICompilationUnit cu= null;
			for (int i= 0; i < nElements; i++) {
				Object curr= elements.get(i);
				if (curr instanceof IMethod || curr instanceof IType) {
					IMember member= (IMember)curr; // limit to methods & types
					
					if (i == 0) {
						cu= member.getCompilationUnit();
						if (cu == null) {
							return null;
						}
					} else if (!cu.equals(member.getCompilationUnit())) {
						return null;
					}						
					res[i]= member;
				} else {
					return null;
				}
			}
			return res;
		}
		return null;
	}
	
	private String getDialogTitle() {
		return ActionMessages.getString("AddJavaDocStubsAction.error.dialogTitle"); //$NON-NLS-1$
	}	
}