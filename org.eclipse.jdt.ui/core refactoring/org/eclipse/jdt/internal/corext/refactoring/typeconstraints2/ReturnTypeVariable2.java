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

package org.eclipse.jdt.internal.corext.refactoring.typeconstraints2;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;

import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;

/**
 * A ReturnTypeVariable is a ConstraintVariable which stands for
 * the return type of a method.
 */

public class ReturnTypeVariable2 extends ConstraintVariable2 implements IDeclaredConstraintVariable {

	private String fMethodBindingKey;
	private ICompilationUnit fCompilationUnit;

	public ReturnTypeVariable2(TType returnType, IMethodBinding methodBinding) {
		super(returnType);
		fMethodBindingKey= methodBinding.getKey();
	}

	public String getMethodBindingKey() {
		return fMethodBindingKey;
	}

	/*
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getMethodBindingKey().hashCode();
	}

	/*
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other.getClass() != ReturnTypeVariable2.class)
			return false;
		
		ReturnTypeVariable2 other2= (ReturnTypeVariable2) other;
		return getMethodBindingKey().equals(other2.getMethodBindingKey());
	}

	public void setCompilationUnit(ICompilationUnit cu) {
		fCompilationUnit= cu;
	}

	public ICompilationUnit getCompilationUnit() {
		return fCompilationUnit;
	}
}
