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
package org.eclipse.ltk.core.refactoring;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.ltk.internal.core.refactoring.Assert;

/**
 * Operation that, when performed, performs a change to the workbench.
 */
public class PerformChangeOperation implements IWorkspaceRunnable {

	private Change fChange;
	private CreateChangeOperation fCreateChangeOperation;
	private RefactoringStatus fValidationStatus;
	
	private Change fUndoChange;
	private String fUndoName;
	private IUndoManager fUndoManager;
	
	private boolean fChangeExecuted;
	private boolean fChangeExecutionFailed;
	
	/**
	 * Creates a new perform change operation instance for the given change.
	 * 
	 * @param change the change to be applied to the workbench
	 */
	public PerformChangeOperation(Change change) {
		Assert.isNotNull(change);
		fChange= change;
	}

	/**
	 * Creates a new <code>PerformChangeOperation</code> for the given {@link 
	 * CreateChangeOperation}. The create change operation is used to create 
	 * the actual change to execute.
	 * 
	 * @param op the <code>CreateChangeOperation</code> used to create the
	 *  actual change object
	 */
	public PerformChangeOperation(CreateChangeOperation op) {
		Assert.isNotNull(op);
		fCreateChangeOperation= op;
	}
	
	/**
	 * Returns <code>true</code> if the change execution failed.
	 *  
	 * @return <code>true</code> if the change execution failed; 
	 *  <code>false</code> otherwise
	 * 
	 */
	public boolean changeExecutionFailed() {
		return fChangeExecutionFailed;
	}

	/**
	 * Returns <code>true</code> if the change has been executed. Otherwise <code>
	 * false</code> is returned.
	 * 
	 * @return <code>true</code> if the change has been executed, otherwise
	 *  <code>false</code>
	 */
	public boolean changeExecuted() {
		return fChangeExecuted;
	}
	
	/**
	 * Returns the status of the condition checking. Returns <code>null</code> if
	 * no condition checking has been requested.
	 * 
	 * @return the status of the condition checking
	 */
	public RefactoringStatus getConditionCheckingStatus() {
		if (fCreateChangeOperation != null)
			return fCreateChangeOperation.getConditionCheckingStatus();
		return null;
	}
	
	/**
	 * Returns the change used by this operation. This is either the change passed to
	 * the constructor or the one create by the <code>CreateChangeOperation</code>.
	 * Method returns <code>null</code> if the create operation did not create
	 * a corresponding change.
	 * 
	 * @return the change used by this operation or <code>null</code> if no change
	 *  has been created
	 */
	public Change getChange() {
		return fChange;
	}
	
	/**
	 * Returns the undo change of the change performed by this operation. Returns
	 * <code>null</code> if the change hasn't been performed or if the change
	 * doesn't provide a undo.
	 * 
	 * @return the undo change of the performed change or <code>null</code>
	 */
	public Change getUndoChange() {
		return fUndoChange;
	}
	
	/**
	 * Returns the refactoring status returned from the call <code>IChange#isValid()</code>.
	 * Returns <code>null</code> if the change has not been executed.
	 * 
	 * @return the change's validation status
	 */
	public RefactoringStatus getValidationStatus() {
		return fValidationStatus;
	}
	
	/**
	 * Sets the undo manager. If the executed change provides an undo change,
	 * then the undo change is pushed onto this manager.
	 *  
	 * @param manager the undo manager to use or <code>null</code> if no
	 *  undo recording is desired
	 * @param undoName the name used to present the undo change on the undo
	 *  stack. Must be a human-readable string. Must not be <code>null</code>
	 *  if manager is unequal <code>null</code>
	 */
	public void setUndoManager(IUndoManager manager, String undoName) {
		if (manager != null) {
			Assert.isNotNull(undoName);
		}
		fUndoManager= manager;
		fUndoName= undoName;
	}

	/**
	 * {@inheritDoc}
	 */
	public void run(IProgressMonitor pm) throws CoreException {
		try {
			fChangeExecuted= false;
			if (createChange()) {
				pm.beginTask("", 2); //$NON-NLS-1$
				pm.subTask(""); //$NON-NLS-1$
				fCreateChangeOperation.run(new SubProgressMonitor(pm, 1));
				fChange= fCreateChangeOperation.getChange();
				if (fChange != null) {
					executeChange(new SubProgressMonitor(pm, 1));
				} else {
					pm.worked(1);
				}
			} else {
				executeChange(pm);
			}
		} finally {
			pm.done();
		}	
	}
	
	protected void executeChange(IProgressMonitor pm) throws CoreException {
		fChangeExecuted= false;
		if (!fChange.isEnabled())
			return;
		IWorkspaceRunnable runnable= new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				monitor.beginTask("", 10); //$NON-NLS-1$
				Exception exception= null;
				fValidationStatus= fChange.isValid(new SubProgressMonitor(monitor, 1));
				if (fValidationStatus.hasFatalError())
					return;
				try {
					if (fUndoManager != null) {
						ResourcesPlugin.getWorkspace().checkpoint(false);
						fUndoManager.aboutToPerformChange(fChange);
					}
					fChangeExecutionFailed= true;
					fUndoChange= fChange.perform(new SubProgressMonitor(monitor, 9));
					fChangeExecutionFailed= false;
					fChangeExecuted= true;
					try {
						fChange.dispose();
					} finally {
						if (fUndoChange != null && fUndoManager != null)
							fUndoChange.initializeValidationData(new SubProgressMonitor(monitor, 1));
					}
				} catch (CoreException e) {
					exception= e;
					throw e;
				} catch (RuntimeException e) {
					exception= e;
					throw e;
				} finally {
					try {
						if (fUndoManager != null) {
							ResourcesPlugin.getWorkspace().checkpoint(false);
							fUndoManager.changePerformed(fChange, fUndoChange, exception);
							if (fUndoChange != null) {
								fUndoManager.addUndo(fUndoName, fUndoChange);
							} else {
								fUndoManager.flush();
							}
						}
					} catch (RuntimeException e) {
						fUndoManager.flush();
						throw e;
					}
					monitor.done();
				}
			}
		};
		JavaCore.run(runnable, pm);
	}
	
	private boolean createChange() {
		return fCreateChangeOperation != null;
	}
}

