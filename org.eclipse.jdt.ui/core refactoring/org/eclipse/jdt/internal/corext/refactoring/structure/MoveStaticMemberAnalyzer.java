/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;


/* package */ class MoveStaticMemberAnalyzer extends ASTVisitor {

	protected RefactoringStatus fStatus;
	
	protected MoveStaticMembersRefactoring.ASTData fAst;
	protected IBinding[] fMembers;
	
	protected boolean fNeedsImport;
	protected Set fProcessed;
	
	protected static final String REFERENCE_UPDATE= RefactoringCoreMessages.getString("MoveMembersRefactoring.referenceUpdate"); //$NON-NLS-1$
	
	public MoveStaticMemberAnalyzer(MoveStaticMembersRefactoring.ASTData ast, IBinding[] members) {
		fStatus= new RefactoringStatus();
		fAst= ast;
		fMembers= members;
		fProcessed= new HashSet();
	}
	
	public RefactoringStatus getStatus() {
		return fStatus;
	}
	
	protected boolean isProcessed(ASTNode node) {
		return fProcessed.contains(node);
	}
	
	protected void rewrite(SimpleName node, ITypeBinding type) {
		AST ast= node.getAST();
		QualifiedName name= ast.newQualifiedName(
			ast.newSimpleName(type.getName()),
			(SimpleName)fAst.rewriter.createCopy(node));
		fAst.rewriter.markAsReplaced(node, name, fAst.createGroupDescription(REFERENCE_UPDATE));
		fProcessed.add(node);
		fNeedsImport= true;
	}
	
	protected void rewrite(QualifiedName node, ITypeBinding type) {
		rewriteName(node.getQualifier(), type);
		fProcessed.add(node.getName());
	}
	
	protected void rewrite(FieldAccess node, ITypeBinding type) {
		Expression exp= node.getExpression();
		if (exp == null) {
			exp= node.getAST().newSimpleName(type.getName());
			fAst.rewriter.markAsInserted(exp, fAst.createGroupDescription(REFERENCE_UPDATE));
			node.setExpression(exp);
			fNeedsImport= true;
		} else if (exp instanceof Name) {
			rewriteName((Name)exp, type);
		} else {
			rewriteExpression(node, exp, type);
		}
		fProcessed.add(node.getName());
	}
	
	protected void rewrite(MethodInvocation node, ITypeBinding type) {
		Expression exp= node.getExpression();
		if (exp == null) {
			exp= node.getAST().newSimpleName(type.getName());
			fAst.rewriter.markAsInserted(exp, fAst.createGroupDescription(REFERENCE_UPDATE));
			node.setExpression(exp);
			fNeedsImport= true;
		} else if (exp instanceof Name) {
			rewriteName((Name)exp, type);
		} else {
			rewriteExpression(node, exp, type);
		}
		fProcessed.add(node.getName());
	}
	
	private void rewriteName(Name name, ITypeBinding type) {
		AST creator= name.getAST();
		boolean fullyQualified= false;
		if (name instanceof QualifiedName) {
			SimpleName left= ASTNodes.getLeftMostSimpleName((QualifiedName)name);
			if (left.resolveBinding() instanceof IPackageBinding)
				fullyQualified= true;
		}
		if (fullyQualified) {
			fAst.rewriter.markAsReplaced(
				name, 
				ASTNodeFactory.newName(creator, type.getQualifiedName()),
				fAst.createGroupDescription(REFERENCE_UPDATE));
		} else {
			fAst.rewriter.markAsReplaced(
				name, 
				creator.newSimpleName(type.getName()),
				fAst.createGroupDescription(REFERENCE_UPDATE));
			fNeedsImport= true;
		}
	}
			
	private void rewriteExpression(ASTNode node, Expression exp, ITypeBinding type) {
		SimpleName replace= node.getAST().newSimpleName(type.getName());
		fAst.rewriter.markAsReplaced(exp, replace, fAst.createGroupDescription(REFERENCE_UPDATE));
		fNeedsImport= true;
		nonStaticAccess(node);
	}
	
	protected void nonStaticAccess(ASTNode node) {
		fStatus.addWarning("Replacing non-static access to static member with static access", 
			JavaStatusContext.create(fAst.unit, node));
	}
	
	protected boolean isStaticAccess(Expression exp, ITypeBinding type) {
		if (!(exp instanceof Name))
			return false;
		return Bindings.equals(type, ((Name)exp).resolveBinding());
	} 
	
	protected boolean isMovedMember(IBinding binding) {
		if (binding == null)
			return false;
		for (int i= 0; i < fMembers.length; i++) {
			if (Bindings.equals(fMembers[i], binding))
				return true;
		}
		return false;
	}
}
