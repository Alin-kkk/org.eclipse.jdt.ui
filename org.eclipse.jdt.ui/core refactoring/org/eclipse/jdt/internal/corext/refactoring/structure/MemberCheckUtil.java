/*
 * (c) Copyright 2001 MyCorporation.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.structure;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaSourceContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry.Context;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

public class MemberCheckUtil {
	
	private MemberCheckUtil(){
	}
	
	public static RefactoringStatus checkMembersInDestinationType(IMember[] members, IType destinationType) throws JavaModelException {	
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < members.length; i++) {
			if (members[i].getElementType() == IJavaElement.METHOD)
				checkMethodInType(destinationType, result, (IMethod)members[i]);
			else 
				checkFieldInType(destinationType, result, (IField)members[i]);
		}
		return result;	
	}

	private static void checkMethodInType(IType destinationType, RefactoringStatus result, IMethod method) throws JavaModelException {
		IMethod[] destinationTypeMethods= destinationType.getMethods();
		IMethod found= findMethod(method, destinationTypeMethods);
		if (found != null){
			Context context= JavaSourceContext.create(destinationType.getCompilationUnit(), found.getSourceRange());
			result.addError("Method '" + method.getElementName() + "' (with the same signature) already exists in superclass '" + destinationType.getFullyQualifiedName() 
										+ "', which will result in compile errors if you proceed.",
										context);
		} else {
			IMethod similar= Checks.findMethod(method, destinationType);
			if (similar != null){
				Context context= JavaSourceContext.create(destinationType.getCompilationUnit(), similar.getSourceRange());
				result.addWarning("Method '" + method.getElementName() + "' (with the same number of parameters) already exists in type '" 
													+ destinationType.getFullyQualifiedName() + "'",
													context);
			}										
		}	
	}
	
	private static void checkFieldInType(IType destinationType, RefactoringStatus result, IField field) throws JavaModelException {
		IField destinationTypeField= destinationType.getField(field.getElementName());	
		if (! destinationTypeField.exists())
			return;
		Context context= JavaSourceContext.create(destinationType.getCompilationUnit(), destinationTypeField.getSourceRange());
		result.addError("Field '" + field.getElementName() + "' already exists in superclass '" + destinationType.getFullyQualifiedName() 
									+ "', which will result in compile errors if you proceed.",
									context);
	}
	
	/**
	 * Finds a method in a list of methods.
	 * @return The found method or <code>null</code>, if nothing found
	 */
	public static IMethod findMethod(IMethod method, IMethod[] allMethods) throws JavaModelException {
		String name= method.getElementName();
		String[] paramTypes= method.getParameterTypes();
		boolean isConstructor= method.isConstructor();

		for (int i= 0; i < allMethods.length; i++) {
			if (JavaModelUtil.isSameMethodSignature(name, paramTypes, isConstructor, allMethods[i]))
				return allMethods[i];
		}
		return null;
	}
}
