/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ltk.core.refactoring;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ltk.core.refactoring.history.IRefactoringHistoryService;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;

import org.eclipse.ltk.internal.core.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.internal.core.refactoring.history.RefactoringHistoryService;

/**
 * Operation that, when run, executes a series of refactoring sequentially.
 * Refactorings are executed using {@link PerformRefactoringOperation}.
 * <p>
 * The operation should be executed via the run method offered by
 * <code>IWorkspace</code> to achieve proper delta batching.
 * </p>
 * <p>
 * Note: this class is not intended to be instantiated or extended outside of
 * the refactoring framework.
 * </p>
 * <p>
 * Note: This API is considered experimental and may change in the near future.
 * </p>
 * 
 * @see org.eclipse.core.resources.IWorkspace
 * @see PerformRefactoringOperation
 * @see RefactoringHistory
 * @see RefactoringHistoryService
 * 
 * @since 3.2
 */
public class PerformRefactoringHistoryOperation implements IWorkspaceRunnable {

	/** The status of the execution */
	private RefactoringStatus fExecutionStatus= new RefactoringStatus();

	/** The refactoring history */
	private final RefactoringHistory fRefactoringHistory;

	/**
	 * Creates a new perform refactoring history operation.
	 * 
	 * @param history
	 *            the refactoring history
	 */
	public PerformRefactoringHistoryOperation(final RefactoringHistory history) {
		Assert.isNotNull(history);
		fRefactoringHistory= history;
	}

	/**
	 * Hook method which is called when the specified refactoring is going to be
	 * executed.
	 * 
	 * @param refactoring
	 *            the refactoring about to be executed
	 * @param descriptor
	 *            the refactoring descriptor
	 * @param monitor
	 *            the progress monitor to use
	 * @return a status describing the outcome of the initialization
	 */
	protected RefactoringStatus aboutToPerformRefactoring(final Refactoring refactoring, final RefactoringDescriptor descriptor, final IProgressMonitor monitor) {
		Assert.isNotNull(refactoring);
		Assert.isNotNull(descriptor);
		return descriptor.initialize(refactoring);
	}

	/**
	 * Returns the execution status. Guaranteed not to be <code>null</code>.
	 * 
	 * @return the status of the session
	 */
	public final RefactoringStatus getExecutionStatus() {
		return fExecutionStatus;
	}

	/**
	 * Hook method which is called when the specified refactoring has been
	 * performed.
	 * 
	 * @param refactoring
	 *            the refactoring which has been performed
	 * @param monitor
	 *            the progress monitor to use
	 */
	protected void refactoringPerformed(final Refactoring refactoring, final IProgressMonitor monitor) {
		// Do nothing
	}

	/**
	 * {@inheritDoc}
	 */
	public void run(final IProgressMonitor monitor) throws CoreException {
		fExecutionStatus= new RefactoringStatus();
		final RefactoringDescriptorProxy[] proxies= fRefactoringHistory.getDescriptors();
		monitor.beginTask(RefactoringCoreMessages.PerformRefactoringHistoryOperation_perform_refactorings, 160 * proxies.length);
		final IRefactoringHistoryService service= RefactoringHistoryService.getInstance();
		try {
			service.connect();
			for (int index= 0; index < proxies.length && !fExecutionStatus.hasFatalError(); index++) {
				final RefactoringDescriptor descriptor= proxies[index].requestDescriptor(new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				if (descriptor != null) {
					final Refactoring refactoring= descriptor.createRefactoring();
					if (refactoring != null) {
						final PerformRefactoringOperation operation= new PerformRefactoringOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
						try {
							final RefactoringStatus status= aboutToPerformRefactoring(refactoring, descriptor, new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
							if (!status.hasFatalError())
								ResourcesPlugin.getWorkspace().run(operation, new SubProgressMonitor(monitor, 90, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
							else
								fExecutionStatus.merge(status);
						} finally {
							refactoringPerformed(refactoring, new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
						}
						fExecutionStatus.merge(operation.getConditionStatus());
						if (!fExecutionStatus.hasFatalError())
							fExecutionStatus.merge(operation.getValidationStatus());
					}
				}
			}
		} finally {
			service.disconnect();
			monitor.done();
		}
	}
}
