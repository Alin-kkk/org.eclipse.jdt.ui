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

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;

/**
 * A ParameterTypeVariable is a ConstraintVariable which stands for
 * the type of a method parameter.
 */
public class ParameterTypeVariable2 extends TypeConstraintVariable2 implements IDeclaredConstraintVariable {

	private final int fParameterIndex;
	private final String fMethodBindingKey;
	private ICompilationUnit fCompilationUnit;
	
	public ParameterTypeVariable2(TType parameterType, int parameterIndex, IMethodBinding methodBinding) {
		super(parameterType);
		Assert.isNotNull(methodBinding);
		Assert.isTrue(0 <= parameterIndex);
		Assert.isTrue(parameterIndex < methodBinding.getParameterTypes().length);
		fParameterIndex= parameterIndex;
		fMethodBindingKey= methodBinding.getKey();
	}
	
	public void setCompilationUnit(ICompilationUnit cu) {
		fCompilationUnit= cu;
	}
	
	public ICompilationUnit getCompilationUnit() {
		return fCompilationUnit;
	}

	public int getParameterIndex() {
		return fParameterIndex;
	}
	
	public String getMethodBindingKey() {
		return fMethodBindingKey;
	}

	/*
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getParameterIndex() ^ getMethodBindingKey().hashCode();
	}

	/*
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other.getClass() != ParameterTypeVariable2.class)
			return false;
		
		ParameterTypeVariable2 other2= (ParameterTypeVariable2) other;
		return getParameterIndex() == other2.getParameterIndex()
				&& getMethodBindingKey().equals(other2.getMethodBindingKey());
	}
	
	public String toString() {
		String toString= (String) getData(TO_STRING);
		return toString == null
			? "[Parameter(" + fParameterIndex + "," + fMethodBindingKey + ")]" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			: toString;
	}

}
