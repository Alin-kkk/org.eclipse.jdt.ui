/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.correction.proposals;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;

import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;

public class MissingReturnTypeInLambdaCorrectionProposal extends MissingReturnTypeCorrectionProposal {

	private final LambdaExpression lambdaExpression;

	public MissingReturnTypeInLambdaCorrectionProposal(ICompilationUnit cu, LambdaExpression lambda, ReturnStatement existingReturn, int relevance) {
		super(cu, null, existingReturn, relevance);
		lambdaExpression= lambda;
		fExistingReturn= existingReturn;
	}

	@Override
	protected AST getAST() {
		return lambdaExpression.getAST();
	}
	
	@Override
	public ITypeBinding getReturnTypeBinding() {
		IMethodBinding methodBinding= lambdaExpression.resolveMethodBinding();
		if (methodBinding != null && methodBinding.getReturnType() != null) {
			return methodBinding.getReturnType();
		}
		return null;
	}

	
	@Override
	protected CompilationUnit getCU() {
		return (CompilationUnit) lambdaExpression.getRoot();
	}
	
	@Override
	protected Expression createDefaultExpression(AST ast) {
		return ASTNodeFactory.newDefaultExpression(ast, getReturnTypeBinding());
	}
	
	@Override
	protected ASTNode getBody() {
		return lambdaExpression.getBody();
	}
	
	@Override
	protected int getModifiers() {
		return 0;
	}
}
