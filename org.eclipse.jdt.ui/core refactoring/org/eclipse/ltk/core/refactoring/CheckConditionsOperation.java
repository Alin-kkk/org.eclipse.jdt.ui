/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
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

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.ltk.internal.core.refactoring.Assert;

/**
 * Operation that, when run, check preconditions of the {@link Refactoring}
 * passed on creation.
 * 
 * @since 3.0
 */
public class CheckConditionsOperation implements IWorkspaceRunnable {
	
	private Refactoring fRefactoring;
	private int fStyle;
	private RefactoringStatus fStatus;
	
	public final static int NONE=			0;
	public final static int ACTIVATION=    	1 << 1;
	public final static int INPUT=	   		1 << 2;
	public final static int PRECONDITIONS= 	ACTIVATION | INPUT;
	private final static int LAST=          1 << 3;
	
	/**
	 * Creates a new <code>CheckConditionsOperation</code>.
	 * 
	 * @param refactoring the refactoring for which the preconditions are to
	 *  be checked.
	 * @param style style to define which conditions to check. Must be one of
	 *  <code>ACTIVATION</code>, <code>INPUT</code> or <code>PRECONDITIONS</code>
	 */
	public CheckConditionsOperation(Refactoring refactoring, int style) {
		Assert.isNotNull(refactoring);
		fRefactoring= refactoring;
		fStyle= style;
		Assert.isTrue(checkStyle(fStyle));
	}

	/**
	 * {@inheritDoc}
	 */
	public void run(IProgressMonitor pm) throws CoreException {
		try {
			fStatus= null;
			if ((fStyle & PRECONDITIONS) == PRECONDITIONS)
				fStatus= fRefactoring.checkPreconditions(pm);
			else if ((fStyle & ACTIVATION) == ACTIVATION)
				fStatus= fRefactoring.checkActivation(pm);
			else if ((fStyle & INPUT) == INPUT)
				fStatus= fRefactoring.checkInput(pm);
		} finally {
			pm.done();
		}
	}

	/**
	 * Returns the outcome of the operation or <code>null</code> if an exception 
	 * has occurred when performing the operation.
	 * 
	 * @return the {@link RefactoringStatus} of the condition checking
	 */
	public RefactoringStatus getStatus() {
		return fStatus;
	}
	
	/**
	 * Returns the operation's refactoring
	 * 
	 * @return the operation's refactoring
	 */
	public Refactoring getRefactoring() {
		return fRefactoring;
	}
	
	/**
	 * Returns the condition checking style.
	 * 
	 * @return the condition checking style
	 */
	public int getStyle() {
		return fStyle;
	}
	
	private boolean checkStyle(int style) {
		return style > NONE && style < LAST;
	}

}
