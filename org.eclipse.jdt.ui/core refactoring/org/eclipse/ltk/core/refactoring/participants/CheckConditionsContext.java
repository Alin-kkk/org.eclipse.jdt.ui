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
package org.eclipse.ltk.core.refactoring.participants;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.ltk.core.refactoring.IRefactoringCoreStatusCodes;
import org.eclipse.ltk.internal.core.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.internal.core.refactoring.RefactoringCorePlugin;

/**
 * A context that is shared between the refactoring processor and
 * all its associated participants during condition checking. 
 * <p>
 * The context manages a set of {@link IConditionChecker} objects
 * to collect condition checks that should be perform across all
 * participants and the processor. For example validating if a
 * file can be changed (see {@link org.eclipse.core.resources.IWorkspace#validateEdit(org.eclipse.core.resources.IFile[], java.lang.Object)}
 * should only be called once for all files modified by the processor
 * and all participants. 
 * </p>
 * 
 * @since 3.0
 */
public class CheckConditionsContext {
	
	private Map fCheckers= new HashMap();
	
	/**
	 * Returns the condition checker of the given type.
	 * 
	 * @param clazz the type of the condition checker
	 * 
	 * @return the condition checker or <code>null</code> if
	 *  no checker is registered for the given type
	 */
	public IConditionChecker getChecker(Class clazz) {
		return (IConditionChecker)fCheckers.get(clazz);
	}
	
	/**
	 * Adds the given condition checker. An assertion will be
	 * thrown if a checker of the same type already exists in
	 * this context.
	 * 
	 * @param checker the checker to add
	 * @throws CoreException if a checker of the same type already
	 *  exists.
	 */
	public void add(IConditionChecker checker) throws CoreException {
		Object old= fCheckers.put(checker.getClass(), checker);
		if (old != null) {
			fCheckers.put(checker.getClass(), old);
			throw new CoreException(new Status(IStatus.ERROR, RefactoringCorePlugin.getPluginId(),
				IRefactoringCoreStatusCodes.CHECKER_ALREADY_EXISTS_IN_CONTEXT, 
				RefactoringCoreMessages.getFormattedString("CheckConditionContext.error.checker_exists", checker.getClass().toString()), //$NON-NLS-1$
				null));  
		}
	}
}
