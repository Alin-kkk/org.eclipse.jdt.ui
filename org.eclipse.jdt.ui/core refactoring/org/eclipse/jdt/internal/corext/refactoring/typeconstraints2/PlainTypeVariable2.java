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

import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;

import org.eclipse.jdt.internal.corext.Assert;

/**
 * A PlainTypeVariable is a ConstraintVariable which stands for a
 * plain type (without an updatable Source location)
 */

public class PlainTypeVariable2 extends TypeConstraintVariable2 {

	protected PlainTypeVariable2(TType type) {
		super(type);
		Assert.isTrue(! type.isWildcardType());
		Assert.isTrue(! type.isTypeVariable());
	}

	/*
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getType().hashCode();
	}

	/*
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other.getClass() != PlainTypeVariable2.class)
			return false;
		
		return getType() == ((PlainTypeVariable2) other).getType();
	}
	
	public String toString() {
		return getType().getPrettySignature();
	}

}
