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
package org.eclipse.jdt.internal.corext.refactoring.reorg2;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;


public class ASTNodeDeleteUtil {
	private ASTNodeDeleteUtil(){}
	
	public static void markAsDeleted(IJavaElement[] javaElements, CompilationUnit cuNode, ASTRewrite rewrite) throws JavaModelException{
		for (int i= 0; i<javaElements.length;i++) {
			markAsDeleted(javaElements[i], cuNode, rewrite);
		}
		propagateFieldDeclarationNodeDeletions(rewrite);			
	}
	
	private static void markAsDeleted(IJavaElement element, CompilationUnit cuNode, ASTRewrite rewrite) throws JavaModelException {
		ASTNode[] declarationNodes= getDeclarationNode(element, cuNode);
		for (int i= 0; i < declarationNodes.length; i++) {
			ASTNode node= declarationNodes[i];
			if (node != null)
				rewrite.markAsRemoved(node);
		}
	}

	private static ASTNode[] getDeclarationNode(IJavaElement element, CompilationUnit cuNode) throws JavaModelException {
		switch(element.getElementType()){
			case IJavaElement.FIELD:
				return new ASTNode[]{ASTNodeSearchUtil.getFieldDeclarationFragmentNode((IField)element, cuNode)};
			case IJavaElement.IMPORT_CONTAINER:
				return ASTNodeSearchUtil.getImportNodes((IImportContainer)element, cuNode);
			case IJavaElement.IMPORT_DECLARATION:
				return new ASTNode[]{ASTNodeSearchUtil.getImportDeclarationNode((IImportDeclaration)element, cuNode)};
			case IJavaElement.INITIALIZER:
				return new ASTNode[]{ASTNodeSearchUtil.getInitializerNode((IInitializer)element, cuNode)};
			case IJavaElement.METHOD:
				return new ASTNode[]{ASTNodeSearchUtil.getMethodDeclarationNode((IMethod)element, cuNode)};
			case IJavaElement.PACKAGE_DECLARATION:
				return new ASTNode[]{ASTNodeSearchUtil.getPackageDeclarationNode((IPackageDeclaration)element, cuNode)};
			case IJavaElement.TYPE:
				return new ASTNode[]{ASTNodeSearchUtil.getTypeDeclarationNode((IType)element, cuNode)};
			default:
				Assert.isTrue(false);
				return null;
		}
	}

	//TODO use this method in pull up and push down
	private static void propagateFieldDeclarationNodeDeletions(ASTRewrite rewrite) {
		Set removedNodes= getRemovedNodes(rewrite);
		for (Iterator iter= removedNodes.iterator(); iter.hasNext();) {
			ASTNode node= (ASTNode) iter.next();
			if (node instanceof VariableDeclarationFragment){
				if (node.getParent() instanceof FieldDeclaration){
					FieldDeclaration fd= (FieldDeclaration)node.getParent();
					if (! rewrite.isRemoved(fd) && removedNodes.containsAll(fd.fragments()))
						rewrite.markAsRemoved(fd);
				}
			}
		}
	}

	/*
	 * return Set<ASTNode>
	 */
	private static Set getRemovedNodes(final ASTRewrite rewrite) {
		final Set result= new HashSet();
		rewrite.getRootNode().accept(new GenericVisitor(){
			protected boolean visitNode(ASTNode node) {
				if (rewrite.isRemoved(node))
					result.add(node);
				return true;
			}
		});
		return result;
	}
}
