/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.typeconstraints;

import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.Bindings;

public class DeclaringTypeVariable extends ConstraintVariable{
	
	private final IBinding fBinding;
	
	protected DeclaringTypeVariable(ITypeBinding memberTypeBinding) {
		super(memberTypeBinding.getDeclaringClass());
		fBinding= memberTypeBinding;
	}

	protected DeclaringTypeVariable(IVariableBinding fieldBinding) {
		super(fieldBinding.getDeclaringClass());
		Assert.isTrue(fieldBinding.isField());
		fBinding= fieldBinding;
	}

	protected DeclaringTypeVariable(IMethodBinding methodBinding) {
		super(methodBinding.getDeclaringClass());
		fBinding= methodBinding;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "Decl(" + Bindings.asString(fBinding) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (! super.equals(obj))
			return false;
		if (! (obj instanceof DeclaringTypeVariable))
			return false;
		DeclaringTypeVariable other= (DeclaringTypeVariable)obj;
		return Bindings.equals(fBinding, other.fBinding);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return super.hashCode() ^ fBinding.hashCode();
	}
}
