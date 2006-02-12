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
package org.eclipse.jdt.internal.corext.refactoring;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;

/**
 * Interface for refactoring components which can be initialized by refactoring
 * arguments.
 * 
 * @since 3.2
 */
public interface IInitializableRefactoringComponent {

	/**
	 * Initializes the refactoring component with the refactoring arguments.
	 * 
	 * @param arguments
	 *            the refactoring arguments
	 * @return an object describing the status of the initialization. If the
	 *         status has severity <code>FATAL_ERROR</code>, the associated
	 *         refactoring will not be executed.
	 */
	public RefactoringStatus initialize(RefactoringArguments arguments);
}