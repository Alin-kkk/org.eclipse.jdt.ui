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
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.eclipse.jdt.ui.CodeGeneration;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class UnimplementedMethodsCompletionProposal extends ASTRewriteCorrectionProposal {

	private ASTNode fTypeNode;

	public UnimplementedMethodsCompletionProposal(ICompilationUnit cu, ASTNode typeNode, int relevance) {
		super(null, cu, null, relevance, null);
		setDisplayName(CorrectionMessages.getString("UnimplementedMethodsCompletionProposal.description"));//$NON-NLS-1$
		setImage(JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
		
		fTypeNode= typeNode;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ASTRewriteCorrectionProposal#getRewrite()
	 */
	protected ASTRewrite getRewrite() throws CoreException {
		ITypeBinding binding;
		List bodyDecls;
		if (fTypeNode instanceof AnonymousClassDeclaration) {
			AnonymousClassDeclaration decl= (AnonymousClassDeclaration) fTypeNode;
			binding= decl.resolveBinding();
			bodyDecls= decl.bodyDeclarations();
		} else {
			TypeDeclaration decl= (TypeDeclaration) fTypeNode;
			binding= decl.resolveBinding();
			bodyDecls= decl.bodyDeclarations();
		}
		IMethodBinding[] methods= evalUnimplementedMethods(binding);
		
		ASTRewrite rewrite= new ASTRewrite(fTypeNode);
		AST ast= fTypeNode.getAST();
		CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();
		if (!settings.createComments || binding.isAnonymous()) {
			settings= null;
		}
		
		for (int i= 0; i < methods.length; i++) {
			MethodDeclaration newMethodDecl= createNewMethodDeclaration(ast, methods[i], rewrite, binding.getName(), settings);
			rewrite.markAsInserted(newMethodDecl);
			bodyDecls.add(newMethodDecl);
		}
		return rewrite;
	}
	
	private MethodDeclaration createNewMethodDeclaration(AST ast, IMethodBinding binding, ASTRewrite rewrite, String typeName, CodeGenerationSettings commentSettings) throws CoreException {
		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.setModifiers(binding.getModifiers() & ~Modifier.ABSTRACT);
		decl.setName(ast.newSimpleName(binding.getName()));
		decl.setConstructor(false);
		
		String returnTypeName= addImport(binding.getReturnType());
		decl.setReturnType(ASTNodeFactory.newType(ast, returnTypeName));
		
		List parameters= decl.parameters();
		ITypeBinding[] params= binding.getParameterTypes();
		String[] paramNames= getArgumentNames(binding);
		for (int i= 0; i < params.length; i++) {
			String paramTypeName= addImport(params[i]);
			SingleVariableDeclaration var= ast.newSingleVariableDeclaration();
			var.setType(ASTNodeFactory.newType(ast, paramTypeName));
			var.setName(ast.newSimpleName(paramNames[i]));
			parameters.add(var);
		}		
		
		List thrownExceptions= decl.thrownExceptions();
		ITypeBinding[] excTypes= binding.getExceptionTypes();
		for (int i= 0; i < excTypes.length; i++) {
			String excTypeName= addImport(excTypes[i]);
			thrownExceptions.add(ASTNodeFactory.newName(ast, excTypeName));
		}
		
		Block body= ast.newBlock();
		decl.setBody(body);
	
		String bodyStatement= ""; //$NON-NLS-1$
		Expression expression= ASTNodeFactory.newDefaultExpression(ast, decl.getReturnType(), decl.getExtraDimensions());
		if (expression != null) {
			ReturnStatement returnStatement= ast.newReturnStatement();
			returnStatement.setExpression(expression);
			bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, String.valueOf('\n'));
		}

		String placeHolder= CodeGeneration.getMethodBodyContent(getCompilationUnit(), typeName, binding.getName(), false, bodyStatement, String.valueOf('\n')); 	
		if (placeHolder != null) {
			ASTNode todoNode= rewrite.createPlaceholder(placeHolder, ASTRewrite.STATEMENT);
			body.statements().add(todoNode);
		}
		
		if (commentSettings != null) {
			String string= CodeGeneration.getMethodComment(getCompilationUnit(), typeName, decl, binding, String.valueOf('\n'));
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createPlaceholder(string, ASTRewrite.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		return decl;
	}
	
	private String[] getArgumentNames(IMethodBinding binding) {
		int nParams= binding.getParameterTypes().length;
		if (nParams > 0) {
			try {
				IMethod method= Bindings.findMethod(binding, getCompilationUnit().getJavaProject());
				if (method != null) {
					return method.getParameterNames();
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}			
		String[] names= new String[nParams];
		for (int i= 0; i < names.length; i++) {
			names[i]= "arg" + i; //$NON-NLS-1$
		}
		return names;
	}
		
	private void findUnimplementedInterfaceMethods(ITypeBinding typeBinding, HashSet visited, ArrayList allMethods, ArrayList toImplement) {
		if (visited.add(typeBinding)) {
			IMethodBinding[] typeMethods= typeBinding.getDeclaredMethods();
			for (int i= 0; i < typeMethods.length; i++) {
				IMethodBinding curr= typeMethods[i];
				IMethodBinding impl= findMethod(curr, allMethods);
				if (impl == null || ((curr.getExceptionTypes().length < impl.getExceptionTypes().length) && !Modifier.isFinal(impl.getModifiers()))) {
					if (impl != null) {
						allMethods.remove(impl);
					}
					// implement an interface method when it does not exist in the hierarchy
					// or when it throws less exceptions that the implemented
					toImplement.add(curr);
					allMethods.add(curr);
				}
			}
			ITypeBinding[] superInterfaces= typeBinding.getInterfaces();
			for (int i= 0; i < superInterfaces.length; i++) {
				findUnimplementedInterfaceMethods(superInterfaces[i], visited, allMethods, toImplement);
			}
		}
	}

	private IMethodBinding[] evalUnimplementedMethods(ITypeBinding typeBinding) {
		ArrayList allMethods= new ArrayList();
		ArrayList toImplement= new ArrayList();
		
		IMethodBinding[] typeMethods= typeBinding.getDeclaredMethods();
		for (int i= 0; i < typeMethods.length; i++) {
			IMethodBinding curr= typeMethods[i];
			int modifiers= curr.getModifiers();
			if (!curr.isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)) {
				allMethods.add(curr);
			}
		}

		ITypeBinding superClass= typeBinding.getSuperclass();
		while (superClass != null) {
			typeMethods= superClass.getDeclaredMethods();
			for (int i= 0; i < typeMethods.length; i++) {
				IMethodBinding curr= typeMethods[i];
				int modifiers= curr.getModifiers();
				if (!curr.isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)) {
					if (findMethod(curr, allMethods) == null) {
						allMethods.add(curr);
					}
				}
			}
			superClass= superClass.getSuperclass();
		}	

		for (int i= 0; i < allMethods.size(); i++) {
			IMethodBinding curr= (IMethodBinding) allMethods.get(i);
			int modifiers= curr.getModifiers();
			if ((Modifier.isAbstract(modifiers) || curr.getDeclaringClass().isInterface()) && (typeBinding != curr.getDeclaringClass())) {
				// implement all abstract methods
				toImplement.add(curr);
			}
		}

		HashSet visited= new HashSet();
		ITypeBinding curr= typeBinding;
		while (curr != null) {
			ITypeBinding[] superInterfaces= curr.getInterfaces();
			for (int i= 0; i < superInterfaces.length; i++) {
				findUnimplementedInterfaceMethods(superInterfaces[i], visited, allMethods, toImplement);
			}
			curr= curr.getSuperclass();
		}
		
		return (IMethodBinding[]) toImplement.toArray(new IMethodBinding[toImplement.size()]);
	}
		
	private IMethodBinding findMethod(IMethodBinding method, ArrayList allMethods) {
		for (int i= 0; i < allMethods.size(); i++) {
			IMethodBinding curr= (IMethodBinding) allMethods.get(i);
			if (Bindings.isEqualMethod(method, curr.getName(), curr.getParameterTypes())) {
				return curr;
			}
		}
		return null;
	}
	

}
