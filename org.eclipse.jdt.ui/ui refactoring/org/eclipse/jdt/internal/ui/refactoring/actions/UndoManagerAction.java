/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

package org.eclipse.jdt.internal.ui.refactoring.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry;
import org.eclipse.jdt.internal.corext.refactoring.base.UndoManagerAdapter;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.changes.AbortChangeExceptionHandler;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public abstract class UndoManagerAction implements IWorkbenchWindowActionDelegate {

	private static final int MAX_LENGTH= 30;

	private RefactoringStatus fPreflightStatus;
	private IAction fAction;
	private IWorkbenchWindow fWorkbenchWindow;
	private UndoManagerAdapter fUndoManagerListener;

	public UndoManagerAction() {
	}
	
	protected abstract IRunnableWithProgress createOperation(ChangeContext context);
	
	protected abstract UndoManagerAdapter createUndoManagerListener();
	
	protected abstract String getName();
	
	protected IWorkbenchWindow getWorkbenchWindow() {
		return fWorkbenchWindow;
	}
	
	protected IAction getAction() {
		return fAction;
	}
	
	protected boolean isHooked() {
		return fAction != null;
	}
	
	protected void hookListener(IAction action) {
		if (isHooked())
			return;
		fAction= action;
		fUndoManagerListener= createUndoManagerListener();
		Refactoring.getUndoManager().addListener(fUndoManagerListener);
	}
	
	protected String shortenText(String text, int patternLength) {
		int length= text.length();
		final int finalLength = MAX_LENGTH + patternLength;
		if (text.length() <= finalLength)
			return text;
		StringBuffer result= new StringBuffer();
		int mid= finalLength / 2;
		result.append(text.substring(0, mid));
		result.append("..."); //$NON-NLS-1$
		result.append(text.substring(length - mid));
		return result.toString();
	}
			
	/* (non-Javadoc)
	 * Method declared in IActionDelegate
	 */
	public void dispose() {
		if (fUndoManagerListener != null)
			Refactoring.getUndoManager().removeListener(fUndoManagerListener);
		fWorkbenchWindow= null;
		fAction= null;
		fUndoManagerListener= null;
	}
	
	/* (non-Javadoc)
	 * Method declared in IActionDelegate
	 */
	public void init(IWorkbenchWindow window) {
		fWorkbenchWindow= window;
	}
	
	/* (non-Javadoc)
	 * Method declared in IActionDelegate
	 */
	public void run(IAction action) {
		// PR: 1GEWDUH: ITPJCORE:WINNT - Refactoring - Unable to undo refactor change
		Shell parent= JavaPlugin.getActiveWorkbenchShell();
		ChangeContext context= new ChangeContext(new AbortChangeExceptionHandler(), getUnsavedFiles());
		IRunnableWithProgress op= createOperation(context);
		try {
			ProgressMonitorDialog dialog= new ProgressMonitorDialog(parent);
			// Don't execute in separate thread since it updates the UI.
			dialog.run(false, false, op);
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, RefactoringMessages.getString("UndoManagerAction.error"), RefactoringMessages.getString("UndoManagerAction.internal_error")); //$NON-NLS-2$ //$NON-NLS-1$
		} catch (InterruptedException e) {
			// Opertation isn't cancelable.
		} finally {
			context.clearPerformedChanges();
		}
		
		if (fPreflightStatus.hasError()) {
			String name= getName();
			MultiStatus status = createMultiStatus(name);
			String message= RefactoringMessages.getFormattedString("UndoManagerAction.cannot_be_executed", name); //$NON-NLS-1$
			ErrorDialog error= new ErrorDialog(parent, name, message, status, IStatus.ERROR) {
				public void create() {
					super.create();
					buttonPressed(IDialogConstants.DETAILS_ID);
				}
			};
			error.open();
		}
	}
	
	/* package */ void setPreflightStatus(RefactoringStatus status) {
		// PR: 1GEWDUH: ITPJCORE:WINNT - Refactoring - Unable to undo refactor change
		fPreflightStatus= status;
	}
	
	private MultiStatus createMultiStatus(String name) {
		// PR: 1GEWDUH: ITPJCORE:WINNT - Refactoring - Unable to undo refactor change
		MultiStatus status= new MultiStatus(
			JavaPlugin.getPluginId(), 
			IStatus.ERROR,
			RefactoringMessages.getString("UndoManagerAction.unsaved_filed"), //$NON-NLS-1$
			null);
		String id= JavaPlugin.getPluginId();
		for (Iterator iter= fPreflightStatus.getEntries().iterator(); iter.hasNext(); ) {
			RefactoringStatusEntry entry= (RefactoringStatusEntry)iter.next();
			status.merge(new Status(
				IStatus.ERROR,
				id,
				IStatus.ERROR,
				entry.getMessage(),
				null));
		}
		return status;
	}
	
	private IFile[] getUnsavedFiles() {
		// PR: 1GEWDUH: ITPJCORE:WINNT - Refactoring - Unable to undo refactor change
		IEditorPart[] parts= JavaPlugin.getDirtyEditors();
		List result= new ArrayList(parts.length);
		for (int i= 0; i < parts.length; i++) {
			IEditorInput input= parts[i].getEditorInput();
			if (input instanceof IFileEditorInput) {
				result.add(((IFileEditorInput)input).getFile());
			}
		}
		return (IFile[])result.toArray(new IFile[result.size()]);
	}	
}