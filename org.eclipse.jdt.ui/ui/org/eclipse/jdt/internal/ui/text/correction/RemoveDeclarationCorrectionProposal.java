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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ui.ISharedImages;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.LinkedNodeFinder;
import org.eclipse.jdt.internal.ui.JavaPlugin;

public class RemoveDeclarationCorrectionProposal extends ASTRewriteCorrectionProposal {

	private static class SideEffectFinder extends ASTVisitor {
		
		private ArrayList fSideEffectNodes;
		
		public SideEffectFinder(ArrayList res) {
			fSideEffectNodes= res;
		}
			
		public boolean visit(Assignment node) {
			fSideEffectNodes.add(node);
			return false;
		}

		public boolean visit(MethodInvocation node) {
			fSideEffectNodes.add(node);
			return false;
		}		

		public boolean visit(ClassInstanceCreation node) {
			fSideEffectNodes.add(node);
			return false;
		}

		public boolean visit(SuperMethodInvocation node) {
			fSideEffectNodes.add(node);
			return false;
		}		
	}


	private CompilationUnit fRoot;
	private IBinding fBinding;

	public RemoveDeclarationCorrectionProposal(ICompilationUnit cu, CompilationUnit root, IBinding binding, int relevance) {
		super("", cu, null, relevance, JavaPlugin.getDefault().getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_DELETE)); //$NON-NLS-1$
		fBinding= binding;
		fRoot= root;
	}

	public String getDisplayString() {
		String name= fBinding.getName();
		switch (fBinding.getKind()) {
			case IBinding.TYPE:
				return CorrectionMessages.getFormattedString("RemoveDeclarationCorrectionProposal.removeunusedtype.description", name); //$NON-NLS-1$
			case IBinding.METHOD:
				if (((IMethodBinding) fBinding).isConstructor()) {
					return CorrectionMessages.getFormattedString("RemoveDeclarationCorrectionProposal.removeunusedconstructor.description", name); //$NON-NLS-1$
				} else {
					return CorrectionMessages.getFormattedString("RemoveDeclarationCorrectionProposal.removeunusedmethod.description", name); //$NON-NLS-1$
				}
			case IBinding.VARIABLE:
				if (((IVariableBinding) fBinding).isField()) {
					return CorrectionMessages.getFormattedString("RemoveDeclarationCorrectionProposal.removeunusedfield.description", name); //$NON-NLS-1$
				} else {
					return CorrectionMessages.getFormattedString("RemoveDeclarationCorrectionProposal.removeunusedvar.description", name); //$NON-NLS-1$
				}
			default:
				return super.getDisplayString();		
		}
	}

	/*(non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ASTRewriteCorrectionProposal#getRewrite()
	 */
	protected ASTRewrite getRewrite() throws CoreException {
		ASTNode declaration= fRoot.findDeclaringNode(fBinding);
		ASTRewrite rewrite;
		if (fBinding.getKind() != IBinding.VARIABLE) {
			rewrite= new ASTRewrite(declaration.getParent());
			rewrite.markAsRemoved(declaration);
		} else {
			// variable
			rewrite= new ASTRewrite(fRoot); 
			SimpleName[] references= LinkedNodeFinder.perform(fRoot, fBinding);
			for (int i= 0; i < references.length; i++) {
				removeVariableReferences(rewrite, references[i]);
			}
		}
		return rewrite;
	}
	
	/**
	 * Remove the field or variable declaration including the initializer.
	 * 
	 */
	
	private void removeVariableReferences(ASTRewrite rewrite, SimpleName reference) {
		int nameParentType= reference.getParent().getNodeType();
		if (nameParentType == ASTNode.ASSIGNMENT) {
			Assignment assignment= (Assignment) reference.getParent();
			Expression rightHand= assignment.getRightHandSide();
			
			ASTNode parent= assignment.getParent();
			if (parent.getNodeType() == ASTNode.EXPRESSION_STATEMENT && rightHand.getNodeType() != ASTNode.ASSIGNMENT) {
				removeVariableWithInitializer(rewrite, rightHand, parent);
			}	else {
				rewrite.markAsReplaced(assignment, rewrite.createCopy(rightHand));
			}
		} else if (nameParentType == ASTNode.SINGLE_VARIABLE_DECLARATION) {
			rewrite.markAsRemoved(reference.getParent());
		} else if (nameParentType == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
			VariableDeclarationFragment frag= (VariableDeclarationFragment) reference.getParent();
			ASTNode varDecl= frag.getParent();
			List fragments;
			if (varDecl instanceof VariableDeclarationExpression) {
				fragments= ((VariableDeclarationExpression) varDecl).fragments();
			} else if (varDecl instanceof FieldDeclaration) {
				fragments= ((FieldDeclaration) varDecl).fragments();
			} else {	
				fragments= ((VariableDeclarationStatement) varDecl).fragments();
			}
			if (fragments.size() == 1) {
				rewrite.markAsRemoved(varDecl);
			} else {
				rewrite.markAsRemoved(frag); // don't try to preserve
			}
		}
	}
	
	private void removeVariableWithInitializer(ASTRewrite rewrite, ASTNode initializerNode, ASTNode statementNode) {
		ArrayList sideEffectNodes= new ArrayList();
		initializerNode.accept(new SideEffectFinder(sideEffectNodes));
		int nSideEffects= sideEffectNodes.size();
		if (nSideEffects == 0) {
			rewrite.markAsRemoved(statementNode); 
		} else {
			// do nothing yet
		}
	}		
	
}
