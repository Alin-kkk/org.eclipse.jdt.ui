/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.refactoring;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Assert;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.refactoring.Refactoring;
import org.eclipse.jdt.core.refactoring.RefactoringStatus;

/**
 * Operation that, when run, check proceconditions of an <code>Refactoring</code> passed 
 * on creation.
 */
public class CheckConditionsOperation implements IRunnableWithProgress {
	private Refactoring fRefactoring;
	private int fStyle;
	private RefactoringStatus fStatus;
	
	public final static int NONE=		   0;
	public final static int ACTIVATION=    1 << 1;
	public final static int INPUT=	   1 << 2;
	public final static int PRECONDITIONS= ACTIVATION | INPUT;
	final static int LAST=                 1 << 3;
	
	/**
	 * Creates a new <code>CheckConditionsOperation</code>.
	 * 
	 * @param refactoring the refactoring. Parameter must not be <code>null</code>.
	 * @param style style to define which conditions to check.
	 */
	public CheckConditionsOperation(Refactoring refactoring, int style) {
		Assert.isNotNull(refactoring);
		fRefactoring= refactoring;
		fStyle= style;
		Assert.isTrue(checkStyle(fStyle));
	}

	/*
	 * (Non-Javadoc)
	 * Method defined int IRunnableWithProgress
	 */
	public void run(IProgressMonitor pm) throws InvocationTargetException {
		try {
			fStatus= null;
			if ((fStyle & PRECONDITIONS) == PRECONDITIONS)
				fStatus= fRefactoring.checkPreconditions(pm);
			else if ((fStyle & ACTIVATION) == ACTIVATION)
				fStatus= fRefactoring.checkActivation(pm);
			else if ((fStyle & INPUT) == INPUT)
				fStatus= fRefactoring.checkInput(pm);
		} catch (JavaModelException e) {
			throw new InvocationTargetException(e);
		}
	}

	/**
	 * Returns the outcome of the operation or <code>null</code> if an exception 
	 * has occured when performing the operation.
	 * 
	 * @return the <code>RefactoringStatus</code> returned from 
	 *  <code>IRefactoring.checkPreconditions</code>.
	 * @see IRefactoring#checkPreconditions
	 */
	public RefactoringStatus getStatus() {
		return fStatus;
	}
	
	private boolean checkStyle(int style) {
		return style < LAST;
	}

}