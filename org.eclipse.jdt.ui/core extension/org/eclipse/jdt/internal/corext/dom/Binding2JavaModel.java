/*
 * (c) Copyright IBM Corp. 2000, 2001, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.dom;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

import org.eclipse.jdt.core.Signature;

/**
 * A helper class to convert compiler bindings into corresponding 
 * Java elements.
 */
public class Binding2JavaModel {

	/**
	 * Converts the given <code>ReferenceBinding</code> into a <code>IType</code>
	 * using the classpath defined by the given Java project. Returns <code>null</code>
	 * if the conversion isn't possible.
	 */
	public static IType find(ITypeBinding type, IJavaProject scope) throws JavaModelException {
		if (type.isPrimitive())
			return null;
		String[] typeElements= Bindings.getNameComponents(type);
		IJavaElement element= scope.findElement(getPathToCompilationUnit(type.getPackage(), typeElements[0]));
		IType candidate= null;
		if (element instanceof ICompilationUnit) {
			candidate= ((ICompilationUnit)element).getType(typeElements[0]);
		} else if (element instanceof IClassFile) {
			candidate= ((IClassFile)element).getType();
		}
		
		if (candidate == null || typeElements.length == 1)
			return candidate;
			
		return findTypeInType(typeElements, candidate);
	}

	/**
	 * Finds the given <code>MethodBinding</code> in the given <code>IType</code>. Returns
	 * <code>null</code> if the type doesn't contains a corresponding method.
	 */
	public static IMethod find(IMethodBinding method, IType type) throws JavaModelException {
		IMethod[] candidates= type.getMethods();
		for (int i= 0; i < candidates.length; i++) {
			IMethod candidate= candidates[i];
			if (candidate.getElementName().equals(method.getName()) && sameParameters(method, candidate)) {
				return candidate;
			}
		}
		return null;
	}
	
	//---- Helper methods to convert a type --------------------------------------------
	
	private static IPath getPathToCompilationUnit(IPackageBinding packageBinding, String topLevelTypeName) {
		IPath result= new Path("");
		String[] packageNames= packageBinding.getNameComponents();
		for (int i= 0; i < packageNames.length; i++) {
			result= result.append(packageNames[i]);
		}
		return result.append(topLevelTypeName + ".java");
	}
	
	private static IType findTypeInType(String[] typeElements, IType jmType) {
		IType result= jmType;
		for (int i= 1; i < typeElements.length; i++) {
			result= result.getType(typeElements[i]);
			if (!result.exists())
				return null;
		}
		return result == jmType ? null : result;
	}
	
	private static String getQualifiedName(char[][] compoundName, int start, int length, char separator) {
		StringBuffer buffer= new StringBuffer();
		int lastSlash= length - 1;
		int end= Math.min(compoundName.length, start + length);
		for (int i= start; i < end; i++) {
			buffer.append(compoundName[i]);
			if (i < lastSlash)
				buffer.append(separator);
		}
		return buffer.toString();
	}
	
	//---- Helper methods to convert a method ---------------------------------------------
	
	private static boolean sameParameters(IMethodBinding method, IMethod candidate) throws JavaModelException {
		ITypeBinding[] methodParamters= method.getParameterTypes();
		String[] candidateParameters= candidate.getParameterTypes();
		if (methodParamters.length != candidateParameters.length)
			return false;
		IType scope= candidate.getDeclaringType();
		for (int i= 0; i < methodParamters.length; i++) {
			ITypeBinding methodParameter= methodParamters[i];
			String candidateParameter= candidateParameters[i];
			if (!sameParameter(methodParameter, candidateParameter, scope))
				return false;
		}
		return true;
	}
	
	private static boolean sameParameter(ITypeBinding type, String candidate, IType scope) throws JavaModelException {
		if (isBaseType(candidate)) {
			return type.getName().equals(Signature.toString(candidate));
		} else {
			if (isResolvedType(candidate)) {
				return Signature.toString(candidate).equals(Bindings.asPackageQualifiedName(type));
			} else {
				String[][] qualifiedCandidates= scope.resolveType(Signature.toString(candidate));
				if (qualifiedCandidates == null)
					return false;
				for (int i= 0; i < qualifiedCandidates.length; i++) {
					String[] qualifiedCandidate= qualifiedCandidates[i];
					if (	qualifiedCandidate[0].equals(type.getPackage().getName()) &&
							qualifiedCandidate[1].equals(Bindings.asQualifiedName(type)))
						return true;
				}
			}
		}
		return false;
	}
	
	private static boolean isBaseType(String s) {
		int arrayCount= Signature.getArrayCount(s);
		char c= s.charAt(arrayCount);
		return c != Signature.C_RESOLVED && c != Signature.C_UNRESOLVED;
	}
	
	private static boolean isResolvedType(String s) {
		int arrayCount= Signature.getArrayCount(s);
		return s.charAt(arrayCount) == Signature.C_RESOLVED;
	}	
}

