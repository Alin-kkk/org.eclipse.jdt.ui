/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public abstract class AbstractExceptionAnalyzer extends ASTVisitor {
	
	private List fCurrentExceptions;	// Elements in this list are of type TypeBinding
	private Stack fTryStack;

	protected AbstractExceptionAnalyzer() {
		fTryStack= new Stack();
		fCurrentExceptions= new ArrayList(1);
		fTryStack.push(fCurrentExceptions);
	}

	public abstract boolean visit(ThrowStatement node);
	
	public abstract boolean visit(MethodInvocation node);
	
	public abstract boolean visit(ClassInstanceCreation node);
	
	public boolean visit(TypeDeclaration node) {
		// Don't dive into a local type.
		if (node.isLocalTypeDeclaration())
			return false;
		return true;
	}
	
	public boolean visit(AnonymousClassDeclaration node) {
		// Don't dive into a local type.
		return false;
	}
	
	public boolean visit(TryStatement node) {
		fCurrentExceptions= new ArrayList(1);
		fTryStack.push(fCurrentExceptions);
		
		// visit try block
		node.getBody().accept(this);
		
		// Remove those exceptions that get catch by following catch blocks
		List catchClauses= node.catchClauses();
		if (!catchClauses.isEmpty())
			handleCatchArguments(catchClauses);
		List current= (List)fTryStack.pop();
		fCurrentExceptions= (List)fTryStack.peek();
		for (Iterator iter= current.iterator(); iter.hasNext();) {
			addException((ITypeBinding)iter.next());
		}
		
		// visit catch and finally
		for (Iterator iter= catchClauses.iterator(); iter.hasNext(); ) {
			((CatchClause)iter.next()).accept(this);
		}
		if (node.getFinally() != null)
			node.getFinally().accept(this);
			
		// return false. We have visited the body by ourselves.	
		return false;
	}
	
	protected void addExceptions(ITypeBinding[] exceptions) {
		if(exceptions == null)
			return;
		for (int i= 0; i < exceptions.length;i++) {
			addException(exceptions[i]);
		}			
	}
	
	protected void addException(ITypeBinding exception) {
		if (!fCurrentExceptions.contains(exception))
			fCurrentExceptions.add(exception);
	}
	
	protected List getCurrentExceptions() {
		return fCurrentExceptions;
	}
	
	private void handleCatchArguments(List catchClauses) {
		for (Iterator iter= catchClauses.iterator(); iter.hasNext(); ) {
			CatchClause clause= (CatchClause)iter.next();
			ITypeBinding catchTypeBinding= clause.getException().getType().resolveBinding();
			if (catchTypeBinding == null)	// No correct type resolve.
				continue;
			for (Iterator exceptions= new ArrayList(fCurrentExceptions).iterator(); exceptions.hasNext(); ) {
				ITypeBinding throwTypeBinding= (ITypeBinding)exceptions.next();
				if (catches(catchTypeBinding, throwTypeBinding))
					fCurrentExceptions.remove(throwTypeBinding);
			}
		}
	}
	
	private boolean catches(ITypeBinding catchTypeBinding, ITypeBinding throwTypeBinding) {
		while(throwTypeBinding != null) {
			if (throwTypeBinding == catchTypeBinding)
				return true;
			throwTypeBinding= throwTypeBinding.getSuperclass();	
		}
		return false;
	}	
}
