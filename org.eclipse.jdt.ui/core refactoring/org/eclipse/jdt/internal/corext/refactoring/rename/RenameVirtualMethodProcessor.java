/*******************************************************************************
 * Copyright (c) 2003 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.Context;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.util.JdtFlags;


public class RenameVirtualMethodProcessor extends RenameMethodProcessor {
	
	private IMethod fOriginalMethod;
	private RefactoringStatus fActivationStatus;
	
	public IMethod getOriginalMethod() {
		return fOriginalMethod;
	}

	public void initialize(Object method) {
		super.initialize(method);
		fOriginalMethod= getMethod();
	}

	public boolean isAvailable() throws CoreException {
		return super.isAvailable() && MethodChecks.isVirtual(getMethod());
	}
	
	//------------ preconditions -------------
	
	public RefactoringStatus checkActivation() throws CoreException {
		if (fActivationStatus != null)
			return fActivationStatus;
			
		fActivationStatus= new RefactoringStatus();
		fActivationStatus.merge(super.checkActivation());
		if (fActivationStatus.hasFatalError())
			return fActivationStatus;			
	
		IMethod method= getMethod();
		
		// super check activation might change the method to be
		// changed.
		fOriginalMethod= method;
		
		if (! method.getDeclaringType().isInterface()) {
			IMethod inInterface= MethodChecks.isDeclaredInInterface(method, new NullProgressMonitor());
			if (inInterface != null && !inInterface.equals(method)) {
				super.initialize(inInterface);
				return fActivationStatus;	
			}
		}
		
		IMethod overrides= MethodChecks.overridesAnotherMethod(method, new NullProgressMonitor());
		if (overrides != null && !overrides.equals(method)) {
			super.initialize(overrides);
			return fActivationStatus;
		}
		return fActivationStatus;
	}

	public RefactoringStatus checkInput(IProgressMonitor pm) throws CoreException {
		try{
			pm.beginTask("", 12); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();

			result.merge(super.checkInput(new SubProgressMonitor(pm, 1)));
			if (result.hasFatalError())
				return result;

			if (getMethod().getDeclaringType().isInterface()) {
				if (isSpecialCase())
					result.addError(RefactoringCoreMessages.getString("RenameMethodInInterfaceRefactoring.special_case")); //$NON-NLS-1$
				pm.worked(1);
				IMethod relatedMethod= relatedTypeDeclaresMethodName(new SubProgressMonitor(pm, 4), getMethod(), getNewElementName());
				if (relatedMethod != null){
					Context context= JavaStatusContext.create(relatedMethod);
					result.addError(RefactoringCoreMessages.getString("RenameMethodInInterfaceRefactoring.already_defined"), context); //$NON-NLS-1$
				}	
			} else {
				if (hierarchyDeclaresSimilarNativeMethod(new SubProgressMonitor(pm, 2))) {
					result.addError(RefactoringCoreMessages.getFormattedString(
						"RenameVirtualMethodRefactoring.requieres_renaming_native",  //$NON-NLS-1$
						new String[]{getMethod().getElementName(), "UnsatisfiedLinkError"})); //$NON-NLS-1$
				}
	
				IMethod hierarchyMethod= hierarchyDeclaresMethodName(new SubProgressMonitor(pm, 2), getMethod(), getNewElementName());
				if (hierarchyMethod != null) {
					Context context= JavaStatusContext.create(hierarchyMethod);
					result.addError(RefactoringCoreMessages.getFormattedString(
						"RenameVirtualMethodRefactoring.hierarchy_declares1", //$NON-NLS-1$
						getNewElementName()), context); 
				}	
			}

			return result;
		} finally{
			pm.done();
		}
	}
	
	//---- Interface checks -------------------------------------
	
	private IMethod relatedTypeDeclaresMethodName(IProgressMonitor pm, IMethod method, String newName) throws CoreException {
		try{
			pm.beginTask("", 2); //$NON-NLS-1$
			Set types= getRelatedTypes(new SubProgressMonitor(pm, 1));
			for (Iterator iter= types.iterator(); iter.hasNext(); ) {
				IMethod m= Checks.findMethod(method, (IType)iter.next());
				IMethod hierarchyMethod= hierarchyDeclaresMethodName(new SubProgressMonitor(pm, 1), m, newName);
				if (hierarchyMethod != null)
					return hierarchyMethod;
			}
			return null;
		} finally {
			pm.done();
		}	
	}

	private boolean isSpecialCase() throws CoreException {
		String[] noParams= new String[0];
		String[] specialNames= new String[]{"toString", "toString", "toString", "toString", "equals", //$NON-NLS-5$ //$NON-NLS-4$ //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
											"equals", "getClass", "getClass", "hashCode", "notify", //$NON-NLS-5$ //$NON-NLS-4$ //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
											"notifyAll", "wait", "wait", "wait"}; //$NON-NLS-4$ //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
		String[][] specialParamTypes= new String[][]{noParams, noParams, noParams, noParams,
													 {"QObject;"}, {"Qjava.lang.Object;"}, noParams, noParams, //$NON-NLS-2$ //$NON-NLS-1$
													 noParams, noParams, noParams, {Signature.SIG_LONG, Signature.SIG_INT},
													 {Signature.SIG_LONG}, noParams};
		String[] specialReturnTypes= new String[]{"QString;", "QString;", "Qjava.lang.String;", "Qjava.lang.String;", //$NON-NLS-4$ //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
												   Signature.SIG_BOOLEAN, Signature.SIG_BOOLEAN, "QClass;", "Qjava.lang.Class;", //$NON-NLS-2$ //$NON-NLS-1$
												   Signature.SIG_INT, Signature.SIG_VOID, Signature.SIG_VOID, Signature.SIG_VOID,
												   Signature.SIG_VOID, Signature.SIG_VOID};
		Assert.isTrue((specialNames.length == specialParamTypes.length) && (specialParamTypes.length == specialReturnTypes.length));
		for (int i= 0; i < specialNames.length; i++){
			if (specialNames[i].equals(getNewElementName()) 
				&& Checks.compareParamTypes(getMethod().getParameterTypes(), specialParamTypes[i]) 
				&& !specialReturnTypes[i].equals(getMethod().getReturnType())){
					return true;
			}
		}
		return false;		
	}
	
	private Set getRelatedTypes(IProgressMonitor pm) throws CoreException {
		Set methods= getMethodsToRename(getMethod(), pm, null);
		Set result= new HashSet(methods.size());
		for (Iterator iter= methods.iterator(); iter.hasNext(); ){
			result.add(((IMethod)iter.next()).getDeclaringType());
		}
		return result;
	}
	
	//---- Class checks -------------------------------------
	
	private boolean hierarchyDeclaresSimilarNativeMethod(IProgressMonitor pm) throws CoreException {
		IType[] classes= getMethod().getDeclaringType().newTypeHierarchy(pm).getAllSubtypes(getMethod().getDeclaringType());
		return classesDeclareOverridingNativeMethod(classes);
	}
		
	private boolean classesDeclareOverridingNativeMethod(IType[] classes) throws CoreException {
		for (int i= 0; i < classes.length; i++){
			IMethod[] methods= classes[i].getMethods();
			for (int j= 0; j < methods.length; j++){
				if ((!methods[j].equals(getMethod()))
					&& (JdtFlags.isNative(methods[j]))
					&& (null != Checks.findMethod(getMethod(), new IMethod[]{methods[j]})))
						return true;
			}
		}
		return false;
	}
}
