/*******************************************************************************
 * Copyright (c) 2003 International Business Machines Corp. and others.
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
import java.util.Iterator;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.corext.codemanipulation.SortMembersOperation;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.javaeditor.AnnotationType;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Sorts the members of a compilation unit with the sort order as specified in
 * the Sort Order preference page.
 * <p>
 * The action will open the parent compilation unit in a Java editor. The result
 * is unsaved, so the user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing a single
 * <code>ICompilationUnit</code> or top level <code>IType</code> in a
 * compilation unit.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.1
 */
public class SortMembersAction extends SelectionDispatchAction {

	private CompilationUnitEditor fEditor;

	/**
	 * Creates a new <code>SortMembersAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public SortMembersAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.getString("SortMembersAction.label")); //$NON-NLS-1$
		setDescription(ActionMessages.getString("SortMembersAction.description")); //$NON-NLS-1$
		setToolTipText(ActionMessages.getString("SortMembersAction.tooltip")); //$NON-NLS-1$		
		
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.SORT_MEMBERS_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public SortMembersAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(checkEnabledEditor());
	}
	
	private boolean checkEnabledEditor() {
		return fEditor != null && SelectionConverter.canOperateOn(fEditor);
	}	
	
	//---- Structured Viewer -----------------------------------------------------------
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		boolean enabled= false;
		try {
			enabled= getSelectedCompilationUnit(selection) != null;
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
			ICompilationUnit cu= getSelectedCompilationUnit(selection);
			if (cu == null || !ElementValidator.check(cu, getShell(), getDialogTitle(), false)) {
				return;
			}
			
			// open an editor and work on a working copy
			IEditorPart editor= EditorUtility.openInEditor(cu);
			if (editor != null) {
				run(shell, JavaModelUtil.toWorkingCopy(cu), editor);
			}
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
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	protected void run(ITextSelection selection) {
		Shell shell= getShell();
		IJavaElement input= SelectionConverter.getInput(fEditor);
		if (input instanceof ICompilationUnit && ElementValidator.check(input, getShell(), getDialogTitle(), true))
			run(shell, (ICompilationUnit) input, fEditor);
		else
			MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.getString("SortMembersAction.not_applicable")); //$NON-NLS-1$
	}

	//---- Helpers -------------------------------------------------------------------
	
	private boolean containsRelevantMarkers(IEditorPart editor) {
		IAnnotationModel model= JavaUI.getDocumentProvider().getAnnotationModel(editor.getEditorInput());
		Iterator iterator= model.getAnnotationIterator();
		while (iterator.hasNext()) {
			Object element= iterator.next();
			if (element instanceof IJavaAnnotation) {
				IJavaAnnotation annot= (IJavaAnnotation) element;
				if (annot.isRelevant() && !annot.isTemporary()) {
					AnnotationType type= annot.getAnnotationType();
					return (type == AnnotationType.BOOKMARK || type == AnnotationType.TASK || type == AnnotationType.UNKNOWN);
				}
			}
		}		
		return false;
	}
	
	private void run(Shell shell, ICompilationUnit cu, IEditorPart editor) {
		if (!ActionUtil.isProcessable(getShell(), cu)) {
			return;
		}		
		
		if (containsRelevantMarkers(editor)) {
			if (!MessageDialog.openConfirm(getShell(), getDialogTitle(), ActionMessages.getString("SortMembersAction.containsmarkers"))) { //$NON-NLS-1$
				return;
			}
		}

		SortMembersOperation op= new SortMembersOperation(cu, null);
		try {
			BusyIndicatorRunnableContext context= new BusyIndicatorRunnableContext();
			context.run(false, true, new WorkbenchRunnableAdapter(op));
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, shell, getDialogTitle(), null); 
		} catch (InterruptedException e) {
			// Do nothing. Operation has been canceled by user.
		}
	}
		
	private ICompilationUnit getSelectedCompilationUnit(IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			Object element= selection.getFirstElement();
			if (element instanceof ICompilationUnit) {
				return (ICompilationUnit) element;
			} else if (element instanceof IType) {
				IType type= (IType) element;
				if (type.getParent() instanceof ICompilationUnit) { // only top level types
					return type.getCompilationUnit();
				}
			}
		}
		return null;
	}
	
	private String getDialogTitle() {
		return ActionMessages.getString("SortMembersAction.error.title"); //$NON-NLS-1$
	}	
}