/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.fix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;

public class LambdaExpressionsFix extends CompilationUnitRewriteOperationsFix {

	private static final class FunctionalAnonymousClassesFinder extends ASTVisitor {

		private final ArrayList<ClassInstanceCreation> fNodes= new ArrayList<ClassInstanceCreation>();
		
		public static ArrayList<ClassInstanceCreation> perform(ASTNode node) {
			FunctionalAnonymousClassesFinder finder= new FunctionalAnonymousClassesFinder();
			node.accept(finder);
			return finder.fNodes;
		}
		
		@Override
		public boolean visit(ClassInstanceCreation node) {
			if (isFunctionalAnonymous(node)) {
				fNodes.add(node);
			}
			return true;
		}
	}
	
	private static final class LambdaExpressionsFinder extends ASTVisitor {
	
		private final HashMap<LambdaExpression, IMethodBinding> fNodes= new HashMap<LambdaExpression, IMethodBinding>();
	
		public static HashMap<LambdaExpression, IMethodBinding> perform(ASTNode node) {
			LambdaExpressionsFinder finder= new LambdaExpressionsFinder();
			node.accept(finder);
			return finder.fNodes;
		}
		
		@Override
		public boolean visit(LambdaExpression node) {
			IMethodBinding abstractMethod= getSingleAbstractMethod(node);
			if (abstractMethod != null) {
				fNodes.put(node, abstractMethod);
			}
			return true;
		}
	}

	private static class AbortSearchException extends RuntimeException {
		private static final long serialVersionUID= 1L;
	}

	private static final class SuperThisReferenceFinder extends HierarchicalASTVisitor {
		
		private ITypeBinding fFunctionalInterface;
		private MethodDeclaration fMethodDeclaration;
		
		static boolean hasReference(MethodDeclaration node) {
			try {
				SuperThisReferenceFinder finder= new SuperThisReferenceFinder();
				ClassInstanceCreation cic= (ClassInstanceCreation) node.getParent().getParent();
				finder.fFunctionalInterface= cic.getType().resolveBinding();
				finder.fMethodDeclaration= node;
				node.accept(finder);
			} catch (AbortSearchException e) {
				return true;
			}
			return false;
		}
		
		@Override
		public boolean visit(AnonymousClassDeclaration node) {
			return false;
		}
		
		@Override
		public boolean visit(BodyDeclaration node) {
			return false;
		}
		
		@Override
		public boolean visit(MethodDeclaration node) {
			return node == fMethodDeclaration;
		}
		
		@Override
		public boolean visit(ThisExpression node) {
			if (node.getQualifier() == null)
				throw new AbortSearchException();
			return true; // references to outer scope are harmless
		}
		
		@Override
		public boolean visit(SuperMethodInvocation node) {
			if (node.getQualifier() == null) {
				throw new AbortSearchException();
			} else {
				IBinding qualifierType= node.getQualifier().resolveBinding();
				if (qualifierType instanceof ITypeBinding && ((ITypeBinding) qualifierType).isInterface()) {
					throw new AbortSearchException(); // JLS8: new overloaded meaning of 'interface'.super.'method'(..)
				}
			}
			return true; // references to outer scopes are harmless
		}
		
		@Override
		public boolean visit(SuperFieldAccess node) {
			throw new AbortSearchException();
		}
		
		@Override
		public boolean visit(MethodInvocation node) {
			IMethodBinding binding= node.resolveMethodBinding();
			if (binding != null && !JdtFlags.isStatic(binding) && node.getExpression() == null
					&& Bindings.isSuperType(binding.getDeclaringClass(), fFunctionalInterface, false))
				throw new AbortSearchException();
			return true;
		}
	}
	
	private static class CreateLambdaOperation extends CompilationUnitRewriteOperation {

		private final List<ClassInstanceCreation> fExpressions;

		public CreateLambdaOperation(List<ClassInstanceCreation> expressions) {
			fExpressions= expressions;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModel model) throws CoreException {

			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			ImportRemover importRemover= cuRewrite.getImportRemover();
			AST ast= rewrite.getAST();

			for (Iterator<ClassInstanceCreation> iterator= fExpressions.iterator(); iterator.hasNext();) {
				ClassInstanceCreation classInstanceCreation= iterator.next();
				TextEditGroup group= createTextEditGroup(FixMessages.LambdaExpressionsFix_convert_to_lambda_expression, cuRewrite);

				AnonymousClassDeclaration anonymTypeDecl= classInstanceCreation.getAnonymousClassDeclaration();
				List<BodyDeclaration> bodyDeclarations= anonymTypeDecl.bodyDeclarations();

				Object object= bodyDeclarations.get(0);
				if (!(object instanceof MethodDeclaration))
					continue;
				MethodDeclaration methodDeclaration= (MethodDeclaration) object;
				List<SingleVariableDeclaration> methodParameters= methodDeclaration.parameters();

				// use short form with inferred parameter types and without parentheses if possible
				LambdaExpression lambdaExpression= ast.newLambdaExpression();
				List<VariableDeclaration> lambdaParameters= lambdaExpression.parameters();
				lambdaExpression.setParentheses(methodParameters.size() != 1);
				for (SingleVariableDeclaration methodParameter : methodParameters) {
					if (!methodParameter.modifiers().isEmpty()) {
						lambdaParameters.add((SingleVariableDeclaration) rewrite.createCopyTarget(methodParameter));
					} else {
						VariableDeclarationFragment lambdaParameter= ast.newVariableDeclarationFragment();
						lambdaParameter.setName((SimpleName) rewrite.createCopyTarget(methodParameter.getName()));
						lambdaParameters.add(lambdaParameter);
					}
				}
				
				Block body= methodDeclaration.getBody();
				List<Statement> statements= body.statements();
				ASTNode lambdaBody;
				if (statements.size() == 1) {
					// use short form with just an expression body if possible
					Statement statement= statements.get(0);
					if (statement instanceof ExpressionStatement) {
						lambdaBody= ((ExpressionStatement) statement).getExpression();
					} else if (statement instanceof ReturnStatement) {
						lambdaBody= ((ReturnStatement) statement).getExpression();
					} else {
						lambdaBody= body;
					}
				} else {
					lambdaBody= body;
				}
				//TODO: Bug 421479: [1.8][clean up][quick assist] convert anonymous to lambda must consider lost scope of interface
//				lambdaBody.accept(new InterfaceAccessQualifier(rewrite, classInstanceCreation.getType().resolveBinding())); //TODO: maybe need a separate ASTRewrite and string placeholder
				
				lambdaExpression.setBody(rewrite.createCopyTarget(lambdaBody));
				Expression replacement= lambdaExpression;
				ITypeBinding cicTypeBinding= classInstanceCreation.getType().resolveBinding();
				ITypeBinding targetTypeBinding= ASTNodes.getTargetType(classInstanceCreation);
				if (cicTypeBinding != null && (ASTNodes.isTargetAmbiguous(classInstanceCreation) || !cicTypeBinding.isEqualTo(targetTypeBinding))) {
					CastExpression cast= ast.newCastExpression();
					cast.setExpression(lambdaExpression);
					ImportRewrite importRewrite= cuRewrite.getImportRewrite();
					ImportRewriteContext importRewriteContext= new ContextSensitiveImportRewriteContext(classInstanceCreation, importRewrite);
					Type castType= importRewrite.addImport(cicTypeBinding, ast, importRewriteContext);
					importRemover.registerAddedImports(castType);
					cast.setType(castType);
					replacement= cast;
				}
				rewrite.replace(classInstanceCreation, replacement, group);

				importRemover.registerRemovedNode(classInstanceCreation);
				importRemover.registerRetainedNode(methodDeclaration);
			}
		}
	}

	private static class CreateAnonymousClassCreationOperation extends CompilationUnitRewriteOperation {

		private final Map<LambdaExpression, IMethodBinding> fExpressions;

		public CreateAnonymousClassCreationOperation(Map<LambdaExpression, IMethodBinding> changedNodes) {
			fExpressions= changedNodes;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModel model) throws CoreException {

			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= rewrite.getAST();

			for (LambdaExpression lambdaExpression : fExpressions.keySet()) {
				TextEditGroup group= createTextEditGroup(FixMessages.LambdaExpressionsFix_convert_to_anonymous_class_creation, cuRewrite);

				ITypeBinding lambdaTypeBinding= lambdaExpression.resolveTypeBinding();
				IMethodBinding methodBinding= fExpressions.get(lambdaExpression);
				List<VariableDeclaration> parameters= lambdaExpression.parameters();
				String[] parameterNames= new String[parameters.size()];
				for (int i= 0; i < parameterNames.length; i++) {
					parameterNames[i]= parameters.get(i).getName().getIdentifier();
				}

				final CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(cuRewrite.getCu().getJavaProject());
				ImportRewrite importRewrite= cuRewrite.getImportRewrite();
				ImportRewriteContext importContext= new ContextSensitiveImportRewriteContext(lambdaExpression, importRewrite);
				
				MethodDeclaration methodDeclaration= StubUtility2.createImplementationStub(cuRewrite.getCu(), rewrite, importRewrite, importContext,
						methodBinding, parameterNames, lambdaTypeBinding.getName(), settings, false);

				Block block;
				ASTNode lambdaBody= lambdaExpression.getBody();
				if (lambdaBody instanceof Block) {
					block= (Block) rewrite.createCopyTarget(lambdaBody);
				} else {
					block= ast.newBlock();
					List<Statement> statements= block.statements();
					ITypeBinding returnType= methodBinding.getReturnType();
					Expression copyTarget= (Expression) rewrite.createCopyTarget(lambdaBody);
					if (Bindings.isVoidType(returnType)) {
						ExpressionStatement newExpressionStatement= ast.newExpressionStatement(copyTarget);
						statements.add(newExpressionStatement);
					} else {
						ReturnStatement returnStatement= ast.newReturnStatement();
						returnStatement.setExpression(copyTarget);
						statements.add(returnStatement);
					}
				}
				methodDeclaration.setBody(block);

				AnonymousClassDeclaration anonymousClassDeclaration= ast.newAnonymousClassDeclaration();
				List<BodyDeclaration> bodyDeclarations= anonymousClassDeclaration.bodyDeclarations();
				bodyDeclarations.add(methodDeclaration);

				Type creationType= ASTNodeFactory.newCreationType(ast, lambdaTypeBinding, importRewrite, importContext);
				
				ClassInstanceCreation classInstanceCreation= ast.newClassInstanceCreation();
				classInstanceCreation.setType(creationType);
				classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);

				rewrite.replace(lambdaExpression, classInstanceCreation, group);
			}
		}
	}

	public static LambdaExpressionsFix createConvertToLambdaFix(ClassInstanceCreation cic) {
		CompilationUnit root= (CompilationUnit) cic.getRoot();
		if (!JavaModelUtil.is18OrHigher(root.getJavaElement().getJavaProject()))
			return null;

		if (!LambdaExpressionsFix.isFunctionalAnonymous(cic))
			return null;

		CreateLambdaOperation op= new CreateLambdaOperation(Collections.singletonList(cic));
		return new LambdaExpressionsFix(FixMessages.LambdaExpressionsFix_convert_to_lambda_expression, root, new CompilationUnitRewriteOperation[] { op });
	}

	public static IProposableFix createConvertToAnonymousClassCreationsFix(LambdaExpression lambda) {
		// offer the quick assist at pre 1.8 levels as well to get rid of the compilation error (TODO: offer this as a quick fix in that case)

		IMethodBinding abstractMethod= getSingleAbstractMethod(lambda);
		if (abstractMethod == null)
			return null;

		CreateAnonymousClassCreationOperation op= new CreateAnonymousClassCreationOperation(Collections.singletonMap(lambda, abstractMethod));
		CompilationUnit root= (CompilationUnit) lambda.getRoot();
		return new LambdaExpressionsFix(FixMessages.LambdaExpressionsFix_convert_to_anonymous_class_creation, root, new CompilationUnitRewriteOperation[] { op });
	}

	private static IMethodBinding getSingleAbstractMethod(LambdaExpression lambda) {
		ITypeBinding lambdaTypeBinding= lambda.resolveTypeBinding();
		if (lambdaTypeBinding == null)
			return null;

		ITypeBinding objectType= lambda.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
		ArrayList<IMethodBinding> objectPublicMethods= new ArrayList<IMethodBinding>();
		for (IMethodBinding objMethod : objectType.getDeclaredMethods()) {
			if (Modifier.isPublic(objMethod.getModifiers()) && !objMethod.isConstructor()) {
				objectPublicMethods.add(objMethod);
			}
		}
		ArrayList<IMethodBinding> abstractMethods= new ArrayList<IMethodBinding>();
		StubUtility2.findUnimplementedInterfaceMethods(lambdaTypeBinding, new HashSet<ITypeBinding>(), objectPublicMethods, lambdaTypeBinding.getPackage(), abstractMethods);

		if (abstractMethods.size() != 1)
			return null;
		return abstractMethods.get(0);
	}

	public static ICleanUpFix createCleanUp(CompilationUnit compilationUnit, boolean useLambda, boolean useAnonymous) {
		if (!JavaModelUtil.is18OrHigher(compilationUnit.getJavaElement().getJavaProject()))
			return null;

		if (useLambda) {
			ArrayList<ClassInstanceCreation> convertibleNodes= FunctionalAnonymousClassesFinder.perform(compilationUnit);
			if (convertibleNodes.isEmpty())
				return null;

			CompilationUnitRewriteOperation op= new CreateLambdaOperation(convertibleNodes);
			return new LambdaExpressionsFix(FixMessages.LambdaExpressionsFix_convert_to_lambda_expression, compilationUnit, new CompilationUnitRewriteOperation[] { op });
			
		} else if (useAnonymous) {
			HashMap<LambdaExpression, IMethodBinding> convertibleNodes= LambdaExpressionsFinder.perform(compilationUnit);
			if (convertibleNodes.isEmpty())
				return null;
			
			CompilationUnitRewriteOperation op= new CreateAnonymousClassCreationOperation(convertibleNodes);
			return new LambdaExpressionsFix(FixMessages.LambdaExpressionsFix_convert_to_anonymous_class_creation, compilationUnit, new CompilationUnitRewriteOperation[] { op });
			
		}
		return null;
	}

	protected LambdaExpressionsFix(String name, CompilationUnit compilationUnit, CompilationUnitRewriteOperation[] fixRewriteOperations) {
		super(name, compilationUnit, fixRewriteOperations);
	}

	static boolean isFunctionalAnonymous(ClassInstanceCreation node) {
		ITypeBinding typeBinding= node.resolveTypeBinding();
		if (typeBinding == null)
			return false;
		ITypeBinding[] interfaces= typeBinding.getInterfaces();
		if (interfaces.length != 1)
			return false;
		if (interfaces[0].getFunctionalInterfaceMethod() == null)
			return false;
	
		AnonymousClassDeclaration anonymTypeDecl= node.getAnonymousClassDeclaration();
		if (anonymTypeDecl == null || anonymTypeDecl.resolveBinding() == null)
			return false;
		
		List<BodyDeclaration> bodyDeclarations= anonymTypeDecl.bodyDeclarations();
		// cannot convert if there are fields or additional methods
		if (bodyDeclarations.size() != 1)
			return false;
		BodyDeclaration bodyDeclaration= bodyDeclarations.get(0);
		if (!(bodyDeclaration instanceof MethodDeclaration))
			return false;

		MethodDeclaration methodDecl= (MethodDeclaration) bodyDeclaration;
		IMethodBinding methodBinding= methodDecl.resolveBinding();

		if (methodBinding == null)
			return false;
		// generic lambda expressions are not allowed
		if (methodBinding.isGenericMethod())
			return false;

		// lambda cannot refer to 'this'/'super' literals
		if (SuperThisReferenceFinder.hasReference(methodDecl))
			return false;
		
		if (!isInTargetTypeContext(node))
			return false;
		
		return true;
	}

	private static boolean isInTargetTypeContext(ClassInstanceCreation node) {
		//TODO: probably incomplete, should reuse https://bugs.eclipse.org/bugs/show_bug.cgi?id=408966#c6
		StructuralPropertyDescriptor locationInParent= node.getLocationInParent();
		
		if (locationInParent == ReturnStatement.EXPRESSION_PROPERTY) {
			MethodDeclaration methodDeclaration= ASTResolving.findParentMethodDeclaration(node);
			if (methodDeclaration == null)
				return false;
			IMethodBinding methodBinding= methodDeclaration.resolveBinding();
			if (methodBinding == null)
				return false;
			//TODO: could also cast to the CIC type instead of aborting...
			return methodBinding.getReturnType().getFunctionalInterfaceMethod() != null;
		}
		
		//TODO: should also check whether variable is of a functional type
		return locationInParent == SingleVariableDeclaration.INITIALIZER_PROPERTY
				|| locationInParent == VariableDeclarationFragment.INITIALIZER_PROPERTY
				|| locationInParent == Assignment.RIGHT_HAND_SIDE_PROPERTY
				|| locationInParent == ArrayInitializer.EXPRESSIONS_PROPERTY
				
				|| locationInParent == MethodInvocation.ARGUMENTS_PROPERTY
				|| locationInParent == SuperMethodInvocation.ARGUMENTS_PROPERTY
				|| locationInParent == ConstructorInvocation.ARGUMENTS_PROPERTY
				|| locationInParent == SuperConstructorInvocation.ARGUMENTS_PROPERTY
				|| locationInParent == ClassInstanceCreation.ARGUMENTS_PROPERTY
				|| locationInParent == EnumConstantDeclaration.ARGUMENTS_PROPERTY
				
				|| locationInParent == LambdaExpression.BODY_PROPERTY
				|| locationInParent == ConditionalExpression.THEN_EXPRESSION_PROPERTY
				|| locationInParent == ConditionalExpression.ELSE_EXPRESSION_PROPERTY
				|| locationInParent == CastExpression.EXPRESSION_PROPERTY;
	}
}
