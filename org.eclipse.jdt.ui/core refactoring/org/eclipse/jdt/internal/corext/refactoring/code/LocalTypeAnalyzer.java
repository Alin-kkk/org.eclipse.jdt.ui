/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;

public class LocalTypeAnalyzer extends ASTVisitor {

	private Selection fSelection;
	private List fTypeDeclarationsBefore= new ArrayList(2);
	private List fTypeDeclarationsSelected= new ArrayList(2);
	private String fBeforeTypeReferenced;
	private String fSelectedTypeReferenced;

	//---- Analyzing statements ----------------------------------------------------------------
	
	public static RefactoringStatus perform(MethodDeclaration method, Selection selection) {
		LocalTypeAnalyzer analyzer= new LocalTypeAnalyzer(selection);
		method.accept(analyzer);
		RefactoringStatus result= new RefactoringStatus();
		analyzer.check(result);
		return result;
	}
	
	private LocalTypeAnalyzer(Selection selection) {
		fSelection= selection;
	}
	
	public boolean visit(SimpleName node) {
		IBinding binding= node.resolveBinding();
		if (binding instanceof ITypeBinding)
			processLocalTypeBinding((ITypeBinding)binding, fSelection.getVisitSelectionMode(node));
			
		return true;
	}
	
	public boolean visit(TypeDeclaration node) {
		int mode= fSelection.getVisitSelectionMode(node);
		switch (mode) {
			case Selection.BEFORE:
				fTypeDeclarationsBefore.add(node);
				break;
			case Selection.SELECTED:
				fTypeDeclarationsSelected.add(node);
				break;
		}
		return true;
	}
	
	private void processLocalTypeBinding(ITypeBinding binding, int mode) {
		switch (mode) {
			case Selection.SELECTED:
				if (fBeforeTypeReferenced != null)
					break;
				if (checkBinding(fTypeDeclarationsBefore, binding))
					fBeforeTypeReferenced= RefactoringCoreMessages.getString("LocalTypeAnalyzer.local_type_from_outside"); //$NON-NLS-1$
				break;
			case Selection.AFTER:
				if (fSelectedTypeReferenced != null)
					break;
				if (checkBinding(fTypeDeclarationsSelected, binding))
					fSelectedTypeReferenced= RefactoringCoreMessages.getString("LocalTypeAnalyzer.local_type_referenced_outside"); //$NON-NLS-1$
				break;
		}
	}
	
	private boolean checkBinding(List declarations, ITypeBinding binding) {
		for (Iterator iter= declarations.iterator(); iter.hasNext();) {
			TypeDeclaration declaration= (TypeDeclaration)iter.next();
			if (declaration.resolveBinding() == binding) {
				return true;
			}
		}
		return false;
	}
	
	private void check(RefactoringStatus status) {
		if (fBeforeTypeReferenced != null)
			status.addFatalError(fBeforeTypeReferenced);
		if (fSelectedTypeReferenced != null)
			status.addFatalError(fSelectedTypeReferenced);
	}	
}