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
package org.eclipse.jdt.internal.ui.text.correction;

import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

public class MissingReturnTypeCorrectionProposal extends ASTRewriteCorrectionProposal {

	private MethodDeclaration fMethodDecl;
	private ReturnStatement fExistingReturn;

	public MissingReturnTypeCorrectionProposal(ICompilationUnit cu, MethodDeclaration decl, ReturnStatement existingReturn, int relevance) {
		super("", cu, null, relevance, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE)); //$NON-NLS-1$
		fMethodDecl= decl;
		fExistingReturn= existingReturn;
	}

	public String getDisplayString() {
		if (fExistingReturn != null) {
			return CorrectionMessages.getString("MissingReturnTypeCorrectionProposal.changereturnstatement.description"); //$NON-NLS-1$
		} else {
			return CorrectionMessages.getString("MissingReturnTypeCorrectionProposal.addreturnstatement.description"); //$NON-NLS-1$
		}
	}

	/*(non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ASTRewriteCorrectionProposal#getRewrite()
	 */
	protected ASTRewrite getRewrite() throws CoreException {
		AST ast= fMethodDecl.getAST();
		if (fExistingReturn != null) {
			ASTRewrite rewrite= new ASTRewrite(fExistingReturn.getParent());
			
			Expression expression= getReturnExpresion(ast, fExistingReturn.getStartPosition());
			if (expression != null) {
				fExistingReturn.setExpression(expression);
				rewrite.markAsInserted(expression);
			}
			return rewrite;
		} else {
			ASTRewrite rewrite= new ASTRewrite(fMethodDecl);
			
			Block block= fMethodDecl.getBody();
				
			List statements= block.statements();
			int offset;
			if (statements.isEmpty()) {
				offset= block.getStartPosition() + 1;
			} else {
				ASTNode lastStatement= (ASTNode) statements.get(statements.size() - 1);
				offset= lastStatement.getStartPosition() + lastStatement.getLength();
			}
			ReturnStatement returnStatement= ast.newReturnStatement();
			returnStatement.setExpression(getReturnExpresion(ast, offset));
			statements.add(returnStatement);
			rewrite.markAsInserted(returnStatement);
			return rewrite;
		}
	}
	
	private Expression getReturnExpresion(AST ast, int returnOffset) {
		CompilationUnit root= (CompilationUnit) fMethodDecl.getRoot();
		
		IMethodBinding methodBinding= fMethodDecl.resolveBinding();
		if (methodBinding != null && methodBinding.getReturnType() != null) {
			ITypeBinding returnBinding= methodBinding.getReturnType();
			
			ScopeAnalyzer analyzer= new ScopeAnalyzer();
			IBinding[] bindings= analyzer.getDeclarationsInScope(root, returnOffset, ScopeAnalyzer.VARIABLES);
			for (int i= 0; i < bindings.length; i++) {
				IVariableBinding curr= (IVariableBinding) bindings[i];
				ITypeBinding type= curr.getType();
				if (type != null && ASTResolving.canAssign(type, returnBinding) && testModifier(curr)) {
					return ast.newSimpleName(curr.getName());
				}
			}
		}
		return ASTNodeFactory.newDefaultExpression(ast, fMethodDecl.getReturnType(), fMethodDecl.getExtraDimensions());
	}

	private boolean testModifier(IVariableBinding curr) {
		int modifiers= curr.getModifiers();
		int staticFinal= Modifier.STATIC | Modifier.FINAL;
		if ((modifiers & staticFinal) == staticFinal) {
			return false;
		}
		if (Modifier.isStatic(modifiers) && !Modifier.isStatic(fMethodDecl.getModifiers())) {
			return false;
		}
		return true;
	}
	
}
