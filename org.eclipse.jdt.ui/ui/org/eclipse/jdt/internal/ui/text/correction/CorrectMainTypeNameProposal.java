/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.correction;

import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.dom.LinkedNodeFinder;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;

/**
 * Renames the primary type to be compatible with the name of the compilation unit.
 * All constructurs and local references to the type are renamed as well.
  */
public class CorrectMainTypeNameProposal extends ASTRewriteCorrectionProposal {

	private String fOldName;
	private String fNewName;

	/**
	 * Constructor for CorrectTypeNameProposal.
	 */
	public CorrectMainTypeNameProposal(ICompilationUnit cu, String oldTypeName, int relevance) {
		super("", cu, null, relevance, null); //$NON-NLS-1$
		fNewName= Signature.getQualifier(cu.getElementName());

		setDisplayName(Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_renametype_description, fNewName));
		setImage(JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));

		fOldName= oldTypeName;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ASTRewriteCorrectionProposal#getRewrite()
	 */
	protected ASTRewrite getRewrite() throws CoreException {
		char[] content= getCompilationUnit().getBuffer().getCharacters();

		ASTParser astParser= ASTParser.newParser(ASTProvider.AST_LEVEL);
		astParser.setSource(content);
		astParser.setUnitName(fOldName + ".java"); //$NON-NLS-1$
		astParser.setProject(getCompilationUnit().getJavaProject());
		astParser.setResolveBindings(true);
		CompilationUnit astRoot= (CompilationUnit) astParser.createAST(null);

		AST ast= astRoot.getAST();
		ASTRewrite rewrite= ASTRewrite.create(ast);

		AbstractTypeDeclaration decl= findTypeDeclaration(astRoot.types(), fOldName);
		if (decl != null) {
			ASTNode[] sameNodes= LinkedNodeFinder.findByNode(astRoot, decl.getName());
			for (int i= 0; i < sameNodes.length; i++) {
				rewrite.replace(sameNodes[i], ast.newSimpleName(fNewName), null);
			}
		}
		return rewrite;
	}

	private AbstractTypeDeclaration findTypeDeclaration(List types, String name) {
		for (Iterator iter= types.iterator(); iter.hasNext();) {
			AbstractTypeDeclaration decl= (AbstractTypeDeclaration) iter.next();
			if (name.equals(decl.getName().getIdentifier())) {
				return decl;
			}
		}
		return null;
	}

}
