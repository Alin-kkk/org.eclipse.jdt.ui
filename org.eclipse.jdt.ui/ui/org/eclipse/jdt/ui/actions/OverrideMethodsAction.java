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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.codemanipulation.AddUnimplementedMethodsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.OverrideMethodDialog;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;


/**
 * Adds unimplemented methods of a type. Action opens a dialog from
 * which the user can chosse the methods to be added.
 * <p>
 * Will open the parent compilation unit in a Java editor. The result is 
 * unsaved, so the user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing elements
 * of type <code>IType</code>.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class OverrideMethodsAction extends SelectionDispatchAction {

	private CompilationUnitEditor fEditor;	

	/**
	 * Creates a new <code>OverrideMethodsAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public OverrideMethodsAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.getString("OverrideMethodsAction.label")); //$NON-NLS-1$
		setDescription(ActionMessages.getString("OverrideMethodsAction.description")); //$NON-NLS-1$
		setToolTipText(ActionMessages.getString("OverrideMethodsAction.tooltip")); //$NON-NLS-1$		
		
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.ADD_UNIMPLEMENTED_METHODS_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public OverrideMethodsAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(checkEnabledEditor());
	}
		
	//---- Structured Viewer -----------------------------------------------------------
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		boolean enabled= false;
		try {
			enabled= getSelectedType(selection) != null;
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.filterNotPresentException(e))
				JavaPlugin.log(e);
		}
		setEnabled(enabled);
	}	
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	protected void run(IStructuredSelection selection) {
		Shell shell= getShell();
		try {
			IType type= getSelectedType(selection);
			if (type == null || !ElementValidator.check(type, getShell(), getDialogTitle(), false) || !ActionUtil.isProcessable(getShell(), type)) {
				return;
			}
			
			// open an editor and work on a working copy
			IEditorPart editor= EditorUtility.openInEditor(type);
			type= (IType)EditorUtility.getWorkingCopy(type);
			
			if (type == null) {
				MessageDialog.openError(shell, getDialogTitle(), ActionMessages.getString("OverrideMethodsAction.error.type_removed_in_editor")); //$NON-NLS-1$
				return;
			}
			
			run(shell, type, editor);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, shell, getDialogTitle(), null); 
		}			
	}

	//---- Java Editior --------------------------------------------------------------
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	protected void selectionChanged(ITextSelection selection) {
	}
	
	private boolean checkEnabledEditor() {
		return fEditor != null && SelectionConverter.canOperateOn(fEditor);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	protected void run(ITextSelection selection) {
		Shell shell= getShell();
		try {
			IType type= SelectionConverter.getTypeAtOffset(fEditor);
			if (type != null) {
				if (!ElementValidator.check(type, shell, getDialogTitle(), false) || !ActionUtil.isProcessable(shell, type)) {
					return;
				}						
				run(shell, type, fEditor);
			} else {
				MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.getString("OverrideMethodsAction.not_applicable")); //$NON-NLS-1$
			}
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), null);
		}
	}

	//---- Helpers -------------------------------------------------------------------
	
	private void run(Shell shell, IType type, IEditorPart editor) throws JavaModelException {
		OverrideMethodDialog dialog= new OverrideMethodDialog(shell, fEditor, type, false);

		IMethod[] selected= null;
		int dialogResult= dialog.open();
		if (dialogResult == Window.OK) {		
			Object[] checkedElements= dialog.getResult();
			if (checkedElements == null)
				return;
			ArrayList result= new ArrayList(checkedElements.length);
			for (int i= 0; i < checkedElements.length; i++) {
				Object curr= checkedElements[i];
				if (curr instanceof IMethod) {
					result.add(curr);
				}
			}
			selected= (IMethod[]) result.toArray(new IMethod[result.size()]);
			
			CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();
			settings.createComments= dialog.getGenerateComment();
	
			IJavaElement elementPosition= dialog.getElementPosition();
			AddUnimplementedMethodsOperation op= new AddUnimplementedMethodsOperation(type, settings, selected, false, elementPosition);
			
			IRewriteTarget target= editor != null ? (IRewriteTarget) editor.getAdapter(IRewriteTarget.class) : null;
			if (target != null) {
				target.beginCompoundChange();		
			}
			try {
				IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
				if (context == null) {
					context= new BusyIndicatorRunnableContext();
				}
				context.run(false, true, new WorkbenchRunnableAdapter(op));
				IMethod[] res= op.getCreatedMethods();
				if (res == null || res.length == 0) {
					MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.getString("OverrideMethodsAction.error.nothing_found")); //$NON-NLS-1$
				} else if (editor != null) {
					EditorUtility.revealInEditor(editor, res[0]);
				}
			} catch (InvocationTargetException e) {
				ExceptionHandler.handle(e, shell, getDialogTitle(), null); 
			} catch (InterruptedException e) {
				// Do nothing. Operation has been canceled by user.
			} finally {
				if (target != null) {
					target.endCompoundChange();		
				}
			}
		}
	}
		
	private IType getSelectedType(IStructuredSelection selection) throws JavaModelException {
		Object[] elements= selection.toArray();
		if (elements.length == 1 && (elements[0] instanceof IType)) {
			IType type= (IType) elements[0];
			if (type.getCompilationUnit() != null && type.isClass()) {
				return type;
			}
		}
		return null;
	}
	
	private String getDialogTitle() {
		return ActionMessages.getString("OverrideMethodsAction.error.title"); //$NON-NLS-1$
	}		

}
