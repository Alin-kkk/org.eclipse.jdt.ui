/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.actions;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.viewers.ISelection;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.dialogs.OpenTypeSelectionDialog;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.OpenTypeHierarchyUtil;

import org.eclipse.jdt.ui.IJavaElementSearchConstants;

public class OpenTypeAction extends Action implements IWorkbenchWindowActionDelegate {
	
	public OpenTypeAction() {
		super();
		setText(JavaUIMessages.getString("OpenTypeAction.label")); //$NON-NLS-1$
		setDescription(JavaUIMessages.getString("OpenTypeAction.description")); //$NON-NLS-1$
		setToolTipText(JavaUIMessages.getString("OpenTypeAction.tooltip")); //$NON-NLS-1$
		setImageDescriptor(JavaPluginImages.DESC_TOOL_OPENTYPE);
	}

	public void run() {
		Shell parent= JavaPlugin.getActiveWorkbenchShell();
		OpenTypeSelectionDialog dialog= new OpenTypeSelectionDialog(parent, new ProgressMonitorDialog(parent), 
			SearchEngine.createWorkspaceScope(), IJavaElementSearchConstants.CONSIDER_TYPES);
		
		dialog.setMatchEmptyString(false);	
		dialog.setTitle(JavaUIMessages.getString("OpenTypeAction.dialogTitle")); //$NON-NLS-1$
		dialog.setMessage(JavaUIMessages.getString("OpenTypeAction.dialogMessage")); //$NON-NLS-1$
		if (dialog.open() == IDialogConstants.CANCEL_ID) {
			return;
		}
		
		Object[] types= dialog.getResult();
		if (types != null && types.length > 0) {
			IType type= (IType)types[0];
			if (dialog.showInTypeHierarchy()) {
				OpenTypeHierarchyUtil.open(new IType[] { type }, JavaPlugin.getActiveWorkbenchWindow());
			} else {
				try {
					IEditorPart part= EditorUtility.openInEditor(type, true);
					EditorUtility.revealInEditor(part, type);
				} catch (CoreException x) {
					String title= JavaUIMessages.getString("OpenTypeAction.errorTitle"); //$NON-NLS-1$
					String message= JavaUIMessages.getString("OpenTypeAction.errorMessage"); //$NON-NLS-1$
					ExceptionHandler.handle(x, title, message);
				}
			}
		}
	}

	//---- IWorkbenchWindowActionDelegate ------------------------------------------------

	public void run(IAction action) {
		run();
	}
	
	public void dispose() {
		// do nothing.
	}
	
	public void init(IWorkbenchWindow window) {
		// do nothing.
	}
	
	public void selectionChanged(IAction action, ISelection selection) {
		// do nothing. Action doesn't depend on selection.
	}
}