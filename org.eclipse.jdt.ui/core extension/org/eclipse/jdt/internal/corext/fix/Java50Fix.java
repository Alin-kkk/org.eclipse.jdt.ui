/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.fix;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.dom.LinkedNodeFinder;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.generics.InferTypeArgumentsConstraintCreator;
import org.eclipse.jdt.internal.corext.refactoring.generics.InferTypeArgumentsConstraintsSolver;
import org.eclipse.jdt.internal.corext.refactoring.generics.InferTypeArgumentsRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.generics.InferTypeArgumentsTCModel;
import org.eclipse.jdt.internal.corext.refactoring.generics.InferTypeArgumentsUpdate;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation;

/**
 * Fix which introduce new language constructs to pre Java50 code.
 * Requires a compiler level setting of 5.0+
 * Supported:
 * 		Add missing @Override annotation
 * 		Add missing @Deprecated annotation
 * 		Convert for loop to enhanced for loop
 */
public class Java50Fix extends LinkedFix {
	
	private static final String OVERRIDE= "Override"; //$NON-NLS-1$
	private static final String DEPRECATED= "Deprecated"; //$NON-NLS-1$
	private static final String FOR_LOOP_ELEMENT_IDENTIFIER= "element"; //$NON-NLS-1$

	private static class ForLoopConverterGenerator extends GenericVisitor {

		private final List fForConverters;
		private final Hashtable fUsedNames;
		private final CompilationUnit fCompilationUnit;
		
		public ForLoopConverterGenerator(List forConverters, CompilationUnit compilationUnit) {
			fForConverters= forConverters;
			fCompilationUnit= compilationUnit;
			fUsedNames= new Hashtable();
		}
		
		public boolean visit(ForStatement node) {
			List usedVaribles= getUsedVariableNames(node);
			usedVaribles.addAll(fUsedNames.values());
			String[] used= (String[])usedVaribles.toArray(new String[usedVaribles.size()]);

			String identifierName= FOR_LOOP_ELEMENT_IDENTIFIER;
			int count= 0;
			for (int i= 0; i < used.length; i++) {
				if (used[i].equals(identifierName)) {
					identifierName= FOR_LOOP_ELEMENT_IDENTIFIER + count;
					count++;
					i= 0;
				}
			}
			
			ConvertForLoopOperation forConverter= new ConvertForLoopOperation(fCompilationUnit, node, identifierName);
			if (forConverter.satisfiesPreconditions()) {
				fForConverters.add(forConverter);
				fUsedNames.put(node, identifierName);
			} else {
				ConvertIterableLoopOperation iterableConverter= new ConvertIterableLoopOperation(fCompilationUnit, node, identifierName);
				if (iterableConverter.isApplicable()) {
					fForConverters.add(iterableConverter);
					fUsedNames.put(node, identifierName);
				}
			}
			return super.visit(node);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.corext.dom.GenericVisitor#endVisit(org.eclipse.jdt.core.dom.ForStatement)
		 */
		public void endVisit(ForStatement node) {
			fUsedNames.remove(node);
			super.endVisit(node);
		}

		private List getUsedVariableNames(ASTNode node) {
			CompilationUnit root= (CompilationUnit)node.getRoot();
			IBinding[] varsBefore= (new ScopeAnalyzer(root)).getDeclarationsInScope(node.getStartPosition(),
				ScopeAnalyzer.VARIABLES);
			IBinding[] varsAfter= (new ScopeAnalyzer(root)).getDeclarationsAfter(node.getStartPosition()
				+ node.getLength(), ScopeAnalyzer.VARIABLES);

			List names= new ArrayList();
			for (int i= 0; i < varsBefore.length; i++) {
				names.add(varsBefore[i].getName());
			}
			for (int i= 0; i < varsAfter.length; i++) {
				names.add(varsAfter[i].getName());
			}
			return names;
		}
	}
	
	private static class AnnotationRewriteOperation implements IFixRewriteOperation {
		private final BodyDeclaration fBodyDeclaration;
		private final String fAnnotation;

		public AnnotationRewriteOperation(BodyDeclaration bodyDeclaration, String annotation) {
			fBodyDeclaration= bodyDeclaration;
			fAnnotation= annotation;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.corext.fix.AbstractFix.IFixRewriteOperation#rewriteAST(org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite, java.util.List)
		 */
		public void rewriteAST(CompilationUnitRewrite cuRewrite, List textEditGroups) throws CoreException {
			AST ast= cuRewrite.getRoot().getAST();
			ListRewrite listRewrite= cuRewrite.getASTRewrite().getListRewrite(fBodyDeclaration, fBodyDeclaration.getModifiersProperty());
			Annotation newAnnotation= ast.newMarkerAnnotation();
			newAnnotation.setTypeName(ast.newSimpleName(fAnnotation));
			TextEditGroup group= new TextEditGroup(Messages.format(FixMessages.Java50Fix_AddMissingAnnotation_description, new String[] {fAnnotation}));
			textEditGroups.add(group);
			listRewrite.insertFirst(newAnnotation, group);
		}
	}
	
	private static class AddTypeParametersOperation extends AbstractLinkedFixRewriteOperation {
		
		private final SimpleType[] fTypes;

		public AddTypeParametersOperation(SimpleType[] types) {
			fTypes= types;
		}

		/**
		 * {@inheritDoc}
		 */
		public ITrackedNodePosition rewriteAST(CompilationUnitRewrite cuRewrite, List textEditGroups, List positionGroups) throws CoreException {
			InferTypeArgumentsTCModel model= new InferTypeArgumentsTCModel();
			InferTypeArgumentsConstraintCreator creator= new InferTypeArgumentsConstraintCreator(model, true);
			
			CompilationUnit root= cuRewrite.getRoot();
			root.setProperty(RefactoringASTParser.SOURCE_PROPERTY, cuRewrite.getCu());
			root.accept(creator);
			
			InferTypeArgumentsConstraintsSolver solver= new InferTypeArgumentsConstraintsSolver(model);
			InferTypeArgumentsUpdate update= solver.solveConstraints(new NullProgressMonitor());
			solver= null; //free caches
			
			ASTNode[] nodes= InferTypeArgumentsRefactoring.inferArguments(fTypes, update, model, cuRewrite);
			if (nodes.length == 0)
				return null;
			
			ASTRewrite astRewrite= cuRewrite.getASTRewrite();
			for (int i= 0; i < nodes.length; i++) {
				if (nodes[i] instanceof ParameterizedType) {
					ParameterizedType type= (ParameterizedType)nodes[0];
					List args= (List)type.getStructuralProperty(ParameterizedType.TYPE_ARGUMENTS_PROPERTY);
					int j= 0;
					for (Iterator iter= args.iterator(); iter.hasNext();) {
						PositionGroup group= new PositionGroup("G" + i + "_" + j); //$NON-NLS-1$ //$NON-NLS-2$
						Type argType= (Type)iter.next();
						if (positionGroups.isEmpty()) {
							group.addFirstPosition(astRewrite.track(argType));
						} else {
							group.addPosition(astRewrite.track(argType));
						}
						if (argType.isWildcardType()) {
							group.addProposal("?", "?");  //$NON-NLS-1$//$NON-NLS-2$
							group.addProposal("Object", "Object");  //$NON-NLS-1$//$NON-NLS-2$
						}
						positionGroups.add(group);
						j++;
					}
				}
			}
			return astRewrite.track(nodes[0]);
		}
	}
	
	public static Java50Fix createAddOverrideAnnotationFix(CompilationUnit compilationUnit, IProblemLocation problem) {
		if (problem.getProblemId() != IProblem.MissingOverrideAnnotation)
			return null;
		
		return createFix(compilationUnit, problem, OVERRIDE, FixMessages.Java50Fix_AddOverride_description);
	}
	
	public static Java50Fix createAddDeprectatedAnnotation(CompilationUnit compilationUnit, IProblemLocation problem) {
		int id= problem.getProblemId();
		if (id != IProblem.FieldMissingDeprecatedAnnotation && 
			id != IProblem.MethodMissingDeprecatedAnnotation && 
			id != IProblem.TypeMissingDeprecatedAnnotation)
			
			return null;
			
		return createFix(compilationUnit, problem, DEPRECATED, FixMessages.Java50Fix_AddDeprecated_description);
	}
	
	private static Java50Fix createFix(CompilationUnit compilationUnit, IProblemLocation problem, String annotation, String label) {
		ICompilationUnit cu= (ICompilationUnit)compilationUnit.getJavaElement();
		if (!JavaModelUtil.is50OrHigher(cu.getJavaProject()))
			return null;
		
		ASTNode selectedNode= problem.getCoveringNode(compilationUnit);
		if (selectedNode == null)
			return null;
		
		ASTNode declaringNode= getDeclaringNode(selectedNode);
		if (!(declaringNode instanceof BodyDeclaration)) 
			return null;
		
		BodyDeclaration declaration= (BodyDeclaration) declaringNode;
		
		AnnotationRewriteOperation operation= new AnnotationRewriteOperation(declaration, annotation);
		
		return new Java50Fix(label, compilationUnit, new IFixRewriteOperation[] {operation});
	}
	
	public static Java50Fix createRawTypeReferenceFix(CompilationUnit compilationUnit, IProblemLocation problem) {
		List operations= new ArrayList();
		SimpleType node= createRawTypeReferenceOperations(compilationUnit, new IProblemLocation[] {problem}, operations);
		if (operations.size() == 0)
			return null;
		
		return new Java50Fix(Messages.format(FixMessages.Java50Fix_AddTypeParameters_description, node.getName()), compilationUnit, (IFixRewriteOperation[])operations.toArray(new IFixRewriteOperation[operations.size()]));
	}
	
	public static Java50Fix createConvertForLoopToEnhancedFix(CompilationUnit compilationUnit, ForStatement loop) {
		ConvertForLoopOperation loopConverter= new ConvertForLoopOperation(compilationUnit, loop, FOR_LOOP_ELEMENT_IDENTIFIER);
		if (!loopConverter.satisfiesPreconditions())
			return null;
		
		return new Java50Fix(FixMessages.Java50Fix_ConvertToEnhancedForLoop_description, compilationUnit, new ILinkedFixRewriteOperation[] {loopConverter});
	}
	
	public static Java50Fix createConvertIterableLoopToEnhancedFix(CompilationUnit compilationUnit, ForStatement loop) {
		ConvertIterableLoopOperation loopConverter= new ConvertIterableLoopOperation(compilationUnit, loop, FOR_LOOP_ELEMENT_IDENTIFIER);
		if (!loopConverter.isApplicable())
			return null;

		return new Java50Fix(FixMessages.Java50Fix_ConvertToEnhancedForLoop_description, compilationUnit, new ILinkedFixRewriteOperation[] {loopConverter});
	}
	
	public static IFix createCleanUp(CompilationUnit compilationUnit, 
			boolean addOverrideAnnotation, 
			boolean addDeprecatedAnnotation, 
			boolean convertToEnhancedForLoop, 
			boolean rawTypeReference) {
		
		ICompilationUnit cu= (ICompilationUnit)compilationUnit.getJavaElement();
		if (!JavaModelUtil.is50OrHigher(cu.getJavaProject()))
			return null;
		
		if (!addOverrideAnnotation && !addDeprecatedAnnotation && !convertToEnhancedForLoop && !rawTypeReference)
			return null;

		List/*<IFixRewriteOperation>*/ operations= new ArrayList();

		IProblem[] problems= compilationUnit.getProblems();
		IProblemLocation[] locations= new IProblemLocation[problems.length];
		for (int i= 0; i < problems.length; i++) {
			locations[i]= new ProblemLocation(problems[i]);
		}
		
		if (addOverrideAnnotation)
			createAddOverrideAnnotationOperations(compilationUnit, locations, operations);
		
		if (addDeprecatedAnnotation)
			createAddDeprecatedAnnotationOperations(compilationUnit, locations, operations);
		
		if (convertToEnhancedForLoop) {
			ForLoopConverterGenerator forLoopFinder= new ForLoopConverterGenerator(operations, compilationUnit);
			compilationUnit.accept(forLoopFinder);
		}
		
		if (rawTypeReference)
			createRawTypeReferenceOperations(compilationUnit, locations, operations);
		
		if (operations.size() == 0)
			return null;
		
		IFixRewriteOperation[] operationsArray= (IFixRewriteOperation[])operations.toArray(new IFixRewriteOperation[operations.size()]);
		return new Java50Fix("", compilationUnit, operationsArray); //$NON-NLS-1$
	}

	public static IFix createCleanUp(CompilationUnit compilationUnit, IProblemLocation[] problems,
			boolean addOverrideAnnotation, 
			boolean addDeprecatedAnnotation,
			boolean rawTypeReferences) {
		
		ICompilationUnit cu= (ICompilationUnit)compilationUnit.getJavaElement();
		if (!JavaModelUtil.is50OrHigher(cu.getJavaProject()))
			return null;
		
		if (!addOverrideAnnotation && !addDeprecatedAnnotation && !rawTypeReferences)
			return null;

		List/*<IFixRewriteOperation>*/ operations= new ArrayList();
		
		if (addOverrideAnnotation)
			createAddOverrideAnnotationOperations(compilationUnit, problems, operations);
		
		if (addDeprecatedAnnotation)
			createAddDeprecatedAnnotationOperations(compilationUnit, problems, operations);
		
		if (rawTypeReferences)
			createRawTypeReferenceOperations(compilationUnit, problems, operations);
			

		if (operations.size() == 0)
			return null;
		
		IFixRewriteOperation[] operationsArray= (IFixRewriteOperation[])operations.toArray(new IFixRewriteOperation[operations.size()]);
		return new Java50Fix("", compilationUnit, operationsArray); //$NON-NLS-1$
	}
	
	private static void createAddDeprecatedAnnotationOperations(CompilationUnit compilationUnit, IProblemLocation[] locations, List result) {
		for (int i= 0; i < locations.length; i++) {
			int id= locations[i].getProblemId();
			
			if (id == IProblem.FieldMissingDeprecatedAnnotation ||
				id == IProblem.MethodMissingDeprecatedAnnotation ||
				id == IProblem.TypeMissingDeprecatedAnnotation) {
				
				IProblemLocation problem= locations[i];

				ASTNode selectedNode= problem.getCoveringNode(compilationUnit);
				if (selectedNode != null) { 
					
					ASTNode declaringNode= getDeclaringNode(selectedNode);
					if (declaringNode instanceof BodyDeclaration) {
						BodyDeclaration declaration= (BodyDeclaration) declaringNode;
						AnnotationRewriteOperation operation= new AnnotationRewriteOperation(declaration, DEPRECATED);
						result.add(operation);
					}
				}
			}	
		}
	}

	private static void createAddOverrideAnnotationOperations(CompilationUnit compilationUnit, IProblemLocation[] locations, List result) {
		for (int i= 0; i < locations.length; i++) {
			
			if (locations[i].getProblemId() == IProblem.MissingOverrideAnnotation) {

				IProblemLocation problem= locations[i];

				ASTNode selectedNode= problem.getCoveringNode(compilationUnit);
				if (selectedNode != null) { 
					
					ASTNode declaringNode= getDeclaringNode(selectedNode);
					if (declaringNode instanceof BodyDeclaration) {
						BodyDeclaration declaration= (BodyDeclaration) declaringNode;
						AnnotationRewriteOperation operation= new AnnotationRewriteOperation(declaration, OVERRIDE);
						result.add(operation);
					}
				}
			}	
		}
	}
	
	private static SimpleType createRawTypeReferenceOperations(CompilationUnit compilationUnit, IProblemLocation[] locations, List operations) {
		List/*<SimpleType>*/ result= new ArrayList();
		for (int i= 0; i < locations.length; i++) {
			IProblemLocation problem= locations[i];
			ASTNode node= problem.getCoveredNode(compilationUnit);
			if (node instanceof ClassInstanceCreation) {
				ASTNode rawReference= (ASTNode)node.getStructuralProperty(ClassInstanceCreation.TYPE_PROPERTY);
				if (isRawTypeReference(rawReference)) {
					result.add(rawReference);
				}
			} else if (node instanceof SimpleName) {
				ASTNode rawReference= node.getParent();
				if (isRawTypeReference(rawReference)) {
					result.add(rawReference);
				}
			} else if (node instanceof MethodInvocation) {
				MethodInvocation invocation= (MethodInvocation)node;
				
				ASTNode rawReference= getRawReference(invocation, compilationUnit);
				if (rawReference != null) {
					result.add(rawReference);
				}
			}
		}
		
		if (result.size() == 0)
			return null;
		
		SimpleType[] types= (SimpleType[])result.toArray(new SimpleType[result.size()]);
		operations.add(new AddTypeParametersOperation(types));
		return types[0];
	}

	private static ASTNode getRawReference(MethodInvocation invocation, CompilationUnit compilationUnit) {
		Name name1= (Name)invocation.getStructuralProperty(MethodInvocation.NAME_PROPERTY);
		if (name1 instanceof SimpleName) {
			ASTNode rawReference= getRawReference((SimpleName)name1, compilationUnit);
			if (rawReference != null) {
				return rawReference;
			}
		}
		
		Expression expr= (Expression)invocation.getStructuralProperty(MethodInvocation.EXPRESSION_PROPERTY);
		if (expr instanceof SimpleName) {
			ASTNode rawReference= getRawReference((SimpleName)expr, compilationUnit);
			if (rawReference != null) {
				return rawReference;
			}
		} else if (expr instanceof QualifiedName) {
			Name name= (Name)expr;
			while (name instanceof QualifiedName) {
				SimpleName simpleName= (SimpleName)name.getStructuralProperty(QualifiedName.NAME_PROPERTY);
				ASTNode rawReference= getRawReference(simpleName, compilationUnit);
				if (rawReference != null) {
					return rawReference;
				}
				name= (Name)name.getStructuralProperty(QualifiedName.QUALIFIER_PROPERTY);
			}
			if (name instanceof SimpleName) {
				ASTNode rawReference= getRawReference((SimpleName)name, compilationUnit);
				if (rawReference != null) {
					return rawReference;
				}
			}
		} else if (expr instanceof MethodInvocation) {
			ASTNode rawReference= getRawReference((MethodInvocation)expr, compilationUnit);
			if (rawReference != null) {
				return rawReference;
			}
		}
		return null;
	}

	private static ASTNode getRawReference(SimpleName name, CompilationUnit compilationUnit) {
		SimpleName[] names= LinkedNodeFinder.findByNode(compilationUnit, name);
		for (int j= 0; j < names.length; j++) {
			if (names[j].getParent() instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment fragment= (VariableDeclarationFragment)names[j].getParent();
				if (fragment.getParent() instanceof VariableDeclarationStatement) {
					VariableDeclarationStatement statement= (VariableDeclarationStatement)fragment.getParent();
					ASTNode result= (ASTNode)statement.getStructuralProperty(VariableDeclarationStatement.TYPE_PROPERTY);
					if (isRawTypeReference(result))
						return result;
				} else if (fragment.getParent() instanceof FieldDeclaration) {
					FieldDeclaration declaration= (FieldDeclaration)fragment.getParent();
					ASTNode result= (ASTNode)declaration.getStructuralProperty(FieldDeclaration.TYPE_PROPERTY);
					if (isRawTypeReference(result))
						return result;
				}
			} else if (names[j].getParent() instanceof SingleVariableDeclaration) {
				SingleVariableDeclaration declaration= (SingleVariableDeclaration)names[j].getParent();
				ASTNode result= (ASTNode)declaration.getStructuralProperty(SingleVariableDeclaration.TYPE_PROPERTY);
				if (isRawTypeReference(result))
					return result;
			} else if (names[j].getParent() instanceof MethodDeclaration) {
				MethodDeclaration methodDecl= (MethodDeclaration)names[j].getParent();
				ASTNode result= (ASTNode)methodDecl.getStructuralProperty(MethodDeclaration.RETURN_TYPE2_PROPERTY);
				if (isRawTypeReference(result))
					return result;
			}
		}
		return null;
	}

	private static boolean isRawTypeReference(ASTNode node) {
		if (!(node instanceof SimpleType))
			return false;
			
		ITypeBinding binding= ((SimpleType)node).resolveBinding().getTypeDeclaration();
		ITypeBinding[] parameters= binding.getTypeParameters();
		if (parameters.length == 0)
			return false;
		
		return true;
	}

	private static ASTNode getDeclaringNode(ASTNode selectedNode) {
		ASTNode declaringNode= null;		
		if (selectedNode instanceof MethodDeclaration) {
			declaringNode= selectedNode;
		} else if (selectedNode instanceof SimpleName) {
			StructuralPropertyDescriptor locationInParent= selectedNode.getLocationInParent();
			if (locationInParent == MethodDeclaration.NAME_PROPERTY || locationInParent == TypeDeclaration.NAME_PROPERTY) {
				declaringNode= selectedNode.getParent();
			} else if (locationInParent == VariableDeclarationFragment.NAME_PROPERTY) {
				declaringNode= selectedNode.getParent().getParent();
			}
		}
		return declaringNode;
	}
		
	private Java50Fix(String name, CompilationUnit compilationUnit, IFixRewriteOperation[] fixRewrites) {
		super(name, compilationUnit, fixRewrites);
	}
	
}
