/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ITypeBinding;

import org.eclipse.jdt.internal.corext.Assert;

public final class RawType extends HierarchyType {
	
	private GenericType fTypeDeclaration;
	
	protected RawType(TypeEnvironment environment) {
		super(environment);
	}

	protected void initialize(ITypeBinding binding, IType javaElementType) {
		Assert.isTrue(binding.isRawType());
		super.initialize(binding, javaElementType);
		TypeEnvironment environment= getEnvironment();
		fTypeDeclaration= (GenericType)environment.create(binding.getTypeDeclaration());
	}
	
	public int getElementType() {
		return RAW_TYPE;
	}
	
	public boolean doEquals(TType type) {
		return getJavaElementType().equals(((RawType)type).getJavaElementType());
	}
	
	public int hashCode() {
		return getJavaElementType().hashCode();
	}
	
	public TType getTypeDeclaration() {
		return fTypeDeclaration;
	}
	
	public TType getErasure() {
		return fTypeDeclaration;
	}
	
	/* package */ GenericType getGenericType() {
		return fTypeDeclaration;
	}
	
	protected boolean doCanAssignTo(TType target) {
		int targetType= target.getElementType();
		switch (targetType) {
			case NULL_TYPE: return false;
			case VOID_TYPE: return false;
			case PRIMITIVE_TYPE: return false;
			
			case ARRAY_TYPE: return false;
			
			case STANDARD_TYPE: return canAssignToStandardType((StandardType)target); 
			case GENERIC_TYPE: return false;
			case PARAMETERIZED_TYPE: return isSubType((ParameterizedType)target);
			case RAW_TYPE: return isSubType((HierarchyType)target);
			
			case UNBOUND_WILDCARD_TYPE:
			case SUPER_WILDCARD_TYPE:
			case EXTENDS_WILDCARD_TYPE: 
				return ((WildcardType)target).checkAssignmentBound(this);
			
			case TYPE_VARIABLE: return false;
		}
		return false;
	}

	protected boolean isTypeEquivalentTo(TType other) {
		int otherElementType= other.getElementType();
		if (otherElementType == PARAMETERIZED_TYPE || otherElementType == GENERIC_TYPE)
			return getErasure().isTypeEquivalentTo(other.getErasure());
		return super.isTypeEquivalentTo(other);
	}

	public String getName() {
		return getJavaElementType().getElementName();
	}
	
	protected String getPlainPrettySignature() {
		return getJavaElementType().getFullyQualifiedName('.');
	}
}
