/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.dom;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.Assert;

public class JavaElementMapper extends GenericVisitor {

	private IMember fElement;
	private int fStart;
	private int fLength;
	private int fEnd;
	private ASTNode fResult;
	
	private JavaElementMapper(IMember element) throws JavaModelException {
		Assert.isNotNull(element);
		fElement= element;
		ISourceRange sourceRange= fElement.getNameRange();
		fStart= sourceRange.getOffset();
		fLength= sourceRange.getLength();
		fEnd= fStart + fLength;
	}

	public static ASTNode perform(IMember member, Class type) throws JavaModelException {
		JavaElementMapper mapper= new JavaElementMapper(member);
		ICompilationUnit unit= member.getCompilationUnit();
		CompilationUnit node= AST.parseCompilationUnit(unit, true);
		node.accept(mapper);
		ASTNode result= mapper.fResult;
		while(result != null && !type.isInstance(result)) {
			result= result.getParent();
		}
		return result;
	}	
	
	protected boolean visitNode(ASTNode node) {
		if (fResult != null) {
			return false;
		} 
		int nodeStart= node.getStartPosition();
		int nodeLength= node.getLength();
		int nodeEnd= nodeStart + nodeLength;
		if (nodeStart == fStart && nodeLength == fLength) {
			fResult= node;
			return false;
		} else if ( nodeStart <= fStart && fEnd <= nodeEnd) {
			return true;
		}
		return false;
	}	
}

