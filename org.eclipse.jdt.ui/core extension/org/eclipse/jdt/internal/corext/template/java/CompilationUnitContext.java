/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.template.java;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.IDocument;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.template.ContextType;
import org.eclipse.jdt.internal.corext.template.DocumentTemplateContext;

/**
 * A compilation unit context.
 */
public abstract class CompilationUnitContext extends DocumentTemplateContext {

	/** The compilation unit, may be <code>null</code>. */
	private final ICompilationUnit fCompilationUnit;

	/**
	 * Creates a compilation unit context.
	 * 
	 * @param type   the context type.
	 * @param document the document.
	 * @param completionPosition the completion position within the document.
	 * @param compilationUnit the compilation unit (may be <code>null</code>).
	 */
	protected CompilationUnitContext(ContextType type, IDocument document, int completionPosition,
		ICompilationUnit compilationUnit)
	{
		super(type, document, completionPosition);
		fCompilationUnit= compilationUnit;
	}
	
	/**
	 * Returns the compilation unit if one is associated with this context, <code>null</code> otherwise.
	 */
	public final ICompilationUnit getCompilationUnit() {
		return fCompilationUnit;
	}

	/**
	 * Returns the enclosing element of a particular element type, <code>null</code>
	 * if no enclosing element of that type exists.
	 */
	public IJavaElement findEnclosingElement(int elementType) {
		if (fCompilationUnit == null)
			return null;

		try {
			IJavaElement element= fCompilationUnit.getElementAt(getStart());
			while (element != null && element.getElementType() != elementType)
				element= element.getParent();
			
			return element;

		} catch (JavaModelException e) {
			return null;
		}	
	}

}
