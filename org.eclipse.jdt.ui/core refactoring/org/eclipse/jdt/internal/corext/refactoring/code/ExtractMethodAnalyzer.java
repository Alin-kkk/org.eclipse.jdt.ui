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
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Message;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.LocalVariableIndex;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.code.flow.FlowContext;
import org.eclipse.jdt.internal.corext.refactoring.code.flow.FlowInfo;
import org.eclipse.jdt.internal.corext.refactoring.code.flow.InOutFlowAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.code.flow.InputFlowAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.util.CodeAnalyzer;

/* package */ class ExtractMethodAnalyzer extends CodeAnalyzer {

	public static final int ERROR=					-2;
	public static final int UNDEFINED=				-1;
	public static final int NO=						0;
	public static final int EXPRESSION=				1;
	public static final int ACCESS_TO_LOCAL=		2;
	public static final int RETURN_STATEMENT_VOID=	3;
	public static final int RETURN_STATEMENT_VALUE=	4;
	public static final int MULTIPLE=				5;

	private MethodDeclaration fEnclosingMethod;
	private IMethodBinding fEnclosingMethodBinding;
	private int fMaxVariableId;

	private int fReturnKind;
	private Type fReturnType;
	
	private FlowInfo fInputFlowInfo;
	private FlowContext fInputFlowContext;
	
	private IVariableBinding[] fArguments;
	private IVariableBinding[] fMethodLocals;
	
	private IVariableBinding fReturnValue;
	private IVariableBinding[] fCallerLocals;
	private IVariableBinding fReturnLocal;
	
	private ITypeBinding[] fAllExceptions;
	private ITypeBinding fExpressionBinding;

	private boolean fForceStatic;
	private boolean fIsLastStatementSelected;
	
	public ExtractMethodAnalyzer(ICompilationUnit unit, Selection selection) throws JavaModelException {
		super(unit, selection, false);
	}
	
	public MethodDeclaration getEnclosingMethod() {
		return fEnclosingMethod;
	}
	
	public int getReturnKind() {
		return fReturnKind;
	}
	
	public boolean extractsExpression() {
		return fReturnKind == EXPRESSION;
	}
	
	public Type getReturnType() {
		return fReturnType;
	}

	public boolean generateImport() {
		switch (fReturnKind) {
			case EXPRESSION:
				return true;
			default:
				return false;
		}
	}
	
	public IVariableBinding[] getArguments() {
		return fArguments;
	}
	
	public IVariableBinding[] getMethodLocals() {
		return fMethodLocals;
	}
	
	public IVariableBinding getReturnValue() {
		return fReturnValue;
	}
	
	public IVariableBinding[] getCallerLocals() {
		return fCallerLocals;
	}
	
	public IVariableBinding getReturnLocal() {
		return fReturnLocal;
	}
	
	public ITypeBinding getExpressionBinding() {
		return fExpressionBinding;
	}
	
	public boolean getForceStatic() {
		return fForceStatic;
	}
	
	//---- Activation checking ---------------------------------------------------------------------------
	
	public RefactoringStatus checkActivation() {
		RefactoringStatus result= getStatus();
		checkExpression(result);
		if (result.hasFatalError())
			return result;
			
		fReturnKind= UNDEFINED;
		fMaxVariableId= LocalVariableIndex.perform(fEnclosingMethod);
		if (analyzeSelection(result).hasFatalError())
			return result;

		int returns= fReturnKind == NO ? 0 : 1;
		if (fReturnValue != null) {
			fReturnKind= ACCESS_TO_LOCAL;
			returns++;
		}
		if (isExpressionSelected()) {
			fReturnKind= EXPRESSION;
			returns++;
		}
			
		if (returns > 1) {
			result.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.ambiguous_return_value"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
			fReturnKind= MULTIPLE;
			return result;
		}
		initReturnType();
		return result;
	}
	
	private void checkExpression(RefactoringStatus status) {
		ASTNode[] nodes= getSelectedNodes();
		if (nodes != null && nodes.length == 1) {
			ASTNode node= nodes[0];
			if (node instanceof NullLiteral) {
				status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_null"), JavaStatusContext.create(fCUnit, node)); //$NON-NLS-1$
			} else if (node instanceof Type) {
				status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_type_reference"), JavaStatusContext.create(fCUnit, node)); //$NON-NLS-1$
			}
		}
	}
	
	private void initReturnType() {
		AST ast= fEnclosingMethod.getAST();
		fReturnType= null;
		switch (fReturnKind) {
			case ACCESS_TO_LOCAL:
				VariableDeclaration declaration= ASTNodes.findVariableDeclaration(fReturnValue, fEnclosingMethod);
				fReturnType= ASTNodes.getType(declaration);
				break;
			case EXPRESSION:
				Expression expression= (Expression)getFirstSelectedNode();
				if (expression.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
					fExpressionBinding= ASTNodes.getTypeBinding(((ClassInstanceCreation)expression).getName());
				} else {
					fExpressionBinding= expression.resolveTypeBinding();
				}
				if (fExpressionBinding != null) {
					fReturnType= ASTNodeFactory.newType(ast, fExpressionBinding, true);
					break;
				}
				fReturnType= ast.newPrimitiveType(PrimitiveType.VOID);
				getStatus().addError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_determine_return_type"), JavaStatusContext.create(fCUnit, expression)); //$NON-NLS-1$
				break;	
			case RETURN_STATEMENT_VALUE:
				fReturnType= fEnclosingMethod.getReturnType();
				break;
			default:
				fReturnType= ast.newPrimitiveType(PrimitiveType.VOID);
		}
		if (fReturnType == null)
			fReturnType= ast.newPrimitiveType(PrimitiveType.VOID);
	}
	
	//---- Input checking -----------------------------------------------------------------------------------
		
	public void checkInput(RefactoringStatus status, String methodName, IJavaProject scope, AST ast) {
		ITypeBinding[] arguments= getArgumentTypes();
		ITypeBinding type= fEnclosingMethodBinding != null ? fEnclosingMethodBinding.getDeclaringClass() : ast.resolveWellKnownType("void"); //$NON-NLS-1$
		status.merge(Checks.checkMethodInType(type, methodName, arguments, scope));
		status.merge(Checks.checkMethodInHierarchy(type.getSuperclass(), methodName, null, arguments, scope));
	}
	
	private ITypeBinding[] getArgumentTypes() {
		ITypeBinding[] result= new ITypeBinding[fArguments.length];
		for (int i= 0; i < fArguments.length; i++) {
			result[i]= fArguments[i].getType();
		}
		return result;
	}
	
	private RefactoringStatus analyzeSelection(RefactoringStatus status) {
		fInputFlowContext= new FlowContext(0, fMaxVariableId + 1);
		fInputFlowContext.setConsiderAccessMode(true);
		fInputFlowContext.setComputeMode(FlowContext.ARGUMENTS);
		
		InOutFlowAnalyzer flowAnalyzer= new InOutFlowAnalyzer(fInputFlowContext);
		fInputFlowInfo= flowAnalyzer.perform(getSelectedNodes());
		
		if (fInputFlowInfo.branches()) {
			status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.branch_mismatch"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
			fReturnKind= ERROR;
			return status;
		}
		if (fInputFlowInfo.isValueReturn()) {
			fReturnKind= RETURN_STATEMENT_VALUE;
		} else  if (fInputFlowInfo.isVoidReturn() || (fInputFlowInfo.isPartialReturn() && isVoidMethod() && isLastStatementSelected())) {
			fReturnKind= RETURN_STATEMENT_VOID;
		} else if (fInputFlowInfo.isNoReturn() || fInputFlowInfo.isThrow() || fInputFlowInfo.isUndefined()) {
			fReturnKind= NO;
		}
		
		if (fReturnKind == UNDEFINED) {
			status.addFatalError(RefactoringCoreMessages.getString("FlowAnalyzer.execution_flow"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
			fReturnKind= ERROR;
			return status;
		}
		computeInput();
		computeExceptions();
		computeOutput(status);
		if (!status.hasFatalError())
			adjustArgumentsAndMethodLocals();
		compressArrays();
		return status;
	}
	
	private boolean isVoidMethod() {
		ITypeBinding binding= fEnclosingMethodBinding.getReturnType();
		if (fEnclosingMethod.getAST().resolveWellKnownType("void").equals(binding)) //$NON-NLS-1$
			return true;
		return false;
	}
	
	public boolean isLastStatementSelected() {
		return fIsLastStatementSelected;
	}

	private void computeLastStatementSelected() {
		ASTNode[] nodes= getSelectedNodes();
		if (nodes.length == 0) {
			fIsLastStatementSelected= false;
		} else {
			List statements= fEnclosingMethod.getBody().statements();
			fIsLastStatementSelected= nodes[nodes.length - 1] == statements.get(statements.size() - 1);
		}
	}

	private void computeInput() {
		int argumentMode= FlowInfo.READ | FlowInfo.READ_POTENTIAL | FlowInfo.WRITE_POTENTIAL | FlowInfo.UNKNOWN;
		fArguments= fInputFlowInfo.get(fInputFlowContext, argumentMode);
		removeSelectedDeclarations(fArguments);
		fMethodLocals= fInputFlowInfo.get(fInputFlowContext, FlowInfo.WRITE | FlowInfo.WRITE_POTENTIAL);
		removeSelectedDeclarations(fMethodLocals);
	}
	
	private void removeSelectedDeclarations(IVariableBinding[] bindings) {
		Selection selection= getSelection();
		for (int i= 0; i < bindings.length; i++) {
			ASTNode decl= ((CompilationUnit)fEnclosingMethod.getRoot()).findDeclaringNode(bindings[i]);
			if (selection.covers(decl))
				bindings[i]= null;
		}
	}
	
	private void computeOutput(RefactoringStatus status) {
		// First find all writes inside the selection.
		FlowContext flowContext= new FlowContext(0, fMaxVariableId + 1);
		flowContext.setConsiderAccessMode(true);
		flowContext.setComputeMode(FlowContext.RETURN_VALUES);
		FlowInfo returnInfo= new InOutFlowAnalyzer(flowContext).perform(getSelectedNodes());
		IVariableBinding[] returnValues= returnInfo.get(flowContext, FlowInfo.WRITE | FlowInfo.WRITE_POTENTIAL | FlowInfo.UNKNOWN);
		
		int counter= 0;
		flowContext.setComputeMode(FlowContext.ARGUMENTS);
		FlowInfo argInfo= new InputFlowAnalyzer(flowContext, getSelection()).perform(fEnclosingMethod);
		IVariableBinding[] reads= argInfo.get(flowContext, FlowInfo.READ | FlowInfo.READ_POTENTIAL | FlowInfo.UNKNOWN);
		outer: for (int i= 0; i < returnValues.length && counter <= 1; i++) {
			IVariableBinding binding= returnValues[i];
			for (int x= 0; x < reads.length; x++) {
				if (reads[x] == binding) {
					counter++;
					fReturnValue= binding;
					continue outer;
				}
			}
		}
		switch (counter) {
			case 0:
				fReturnValue= null;
				break;
			case 1:
				break;
			default:
				fReturnValue= null;
				status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.assignments_to_local"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
				return;
		}
		List callerLocals= new ArrayList(5);
		IVariableBinding[] writes= argInfo.get(flowContext, FlowInfo.WRITE);
		for (int i= 0; i < writes.length; i++) {
			IVariableBinding write= writes[i];
			if (getSelection().covers(ASTNodes.findDeclaration(write, fEnclosingMethod)))
				callerLocals.add(write);
		}
		fCallerLocals= (IVariableBinding[])callerLocals.toArray(new IVariableBinding[callerLocals.size()]);
		if (fReturnValue != null && getSelection().covers(ASTNodes.findDeclaration(fReturnValue, fEnclosingMethod)))
			fReturnLocal= fReturnValue;
	}
	
	private void adjustArgumentsAndMethodLocals() {
		for (int i= 0; i < fArguments.length; i++) {
			IVariableBinding argument= fArguments[i];
			if (fInputFlowInfo.hasAccessMode(fInputFlowContext, argument, FlowInfo.WRITE_POTENTIAL)) {
				if (argument != fReturnValue)
					fArguments[i]= null;
				// We didn't remove the argument. So we have to remove the local declaration
				if (fArguments[i] != null) {
					for (int l= 0; l < fMethodLocals.length; l++) {
						if (fMethodLocals[l] == argument)
							fMethodLocals[l]= null;						
					}
				}
			}
		}
	}
	
	private void compressArrays() {
		fArguments= compressArray(fArguments);
		fCallerLocals= compressArray(fCallerLocals);
		fMethodLocals= compressArray(fMethodLocals);
	}
	
	private IVariableBinding[] compressArray(IVariableBinding[] array) {
		if (array == null)
			return null;
		int size= 0;
		for (int i= 0; i < array.length; i++) {
			if (array[i] != null)
				size++;	
		}
		if (size == array.length)
			return array;
		IVariableBinding[] result= new IVariableBinding[size];
		for (int i= 0, r= 0; i < array.length; i++) {
			if (array[i] != null)
				result[r++]= array[i];		
		}
		return result;
	}
	
	//---- Change creation ----------------------------------------------------------------------------------
	
	public void aboutToCreateChange() {
	}

	//---- Exceptions -----------------------------------------------------------------------------------------
	
	public ITypeBinding[] getExceptions(boolean includeRuntimeExceptions, AST ast) {
		if (includeRuntimeExceptions)
			return fAllExceptions;
		List result= new ArrayList(fAllExceptions.length);
		for (int i= 0; i < fAllExceptions.length; i++) {
			ITypeBinding exception= fAllExceptions[i];
			if (!includeRuntimeExceptions && Bindings.isRuntimeException(exception))
				continue;
			result.add(exception);
		}
		return (ITypeBinding[]) result.toArray(new ITypeBinding[result.size()]);
	}
	
	private void computeExceptions() {
		fAllExceptions= ExceptionAnalyzer.perform(fEnclosingMethod, getSelectedNodes());
	}
	
	//---- Special visitor methods ---------------------------------------------------------------------------

	protected void handleNextSelectedNode(ASTNode node) {
		super.handleNextSelectedNode(node);
		checkParent(node);
	}
	
	protected void handleSelectionEndsIn(ASTNode node) {
		invalidSelection(RefactoringCoreMessages.getString("StatementAnalyzer.doesNotCover"), JavaStatusContext.create(fCUnit, node)); //$NON-NLS-1$
		super.handleSelectionEndsIn(node);
	}
		
	private void checkParent(ASTNode node) {
		ASTNode firstParent= getFirstSelectedNode().getParent();
		do {
			node= node.getParent();
			if (node == firstParent)
				return;
		} while (node != null);
		invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.parent_mismatch")); //$NON-NLS-1$
	}
	
	public void endVisit(CompilationUnit node) {
		RefactoringStatus status= getStatus();
		superCall: {
			if (status.hasFatalError())
				break superCall;
			if (!hasSelectedNodes()) {
				ASTNode coveringNode= getLastCoveringNode();
				if (coveringNode instanceof Block && coveringNode.getParent() instanceof MethodDeclaration) {
					MethodDeclaration methodDecl= (MethodDeclaration)coveringNode.getParent();
					Message[] messages= ASTNodes.getMessages(methodDecl, ASTNodes.NODE_ONLY);
					if (messages.length > 0) {
						status.addFatalError(RefactoringCoreMessages.getFormattedString(
							"ExtractMethodAnalyzer.compile_errors", //$NON-NLS-1$
							methodDecl.getName().getIdentifier()), JavaStatusContext.create(fCUnit, methodDecl));
						break superCall;
					}
				}
				status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.only_method_body")); //$NON-NLS-1$
				break superCall;
			}
			fEnclosingMethod= (MethodDeclaration)ASTNodes.getParent(getFirstSelectedNode(), MethodDeclaration.class);
			if (fEnclosingMethod == null) {
				status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.only_method_body")); //$NON-NLS-1$
				break superCall;
			} else {
				fEnclosingMethodBinding= fEnclosingMethod.resolveBinding();
			}
			if (!isSingleExpressionOrStatementSet()) {
				status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.single_expression_or_set")); //$NON-NLS-1$
				break superCall;
			}
			if (isExpressionSelected()) {
				ASTNode expression= getFirstSelectedNode();
				if (expression instanceof Name) {
					Name name= (Name)expression;
					if (name.resolveBinding() instanceof ITypeBinding) {
						status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_type_reference")); //$NON-NLS-1$
						break superCall;
					}
					if (name.isSimpleName() && ((SimpleName)name).isDeclaration()) {
						status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_name_in_declaration")); //$NON-NLS-1$
						break superCall;
					}
				}
				fForceStatic= 
					ASTNodes.getParent(expression, ASTNode.SUPER_CONSTRUCTOR_INVOCATION) != null ||
					ASTNodes.getParent(expression, ASTNode.CONSTRUCTOR_INVOCATION) != null;
			}
			status.merge(LocalTypeAnalyzer.perform(fEnclosingMethod, getSelection()));
			computeLastStatementSelected();
		}
		super.endVisit(node);
	}
	
	public boolean visit(AnonymousClassDeclaration node) {
		boolean result= super.visit(node);
		if (isFirstSelectedNode(node)) {
			invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_anonymous_type"), JavaStatusContext.create(fCUnit, node)); //$NON-NLS-1$
			return false;
		}
		return result;
	}
	
	public boolean visit(Assignment node) {
		boolean result= super.visit(node);
		if (getSelection().getVisitSelectionMode(node.getLeftHandSide()) == Selection.SELECTED) {
			invalidSelection(
				RefactoringCoreMessages.getString("ExtractMethodAnalyzer.leftHandSideOfAssignment"),  //$NON-NLS-1$
				JavaStatusContext.create(fCUnit, node));
			return false;
		}
		return result;
	}

	public boolean visit(DoStatement node) {
		boolean result= super.visit(node);
		
		int actionStart= getBuffer().indexAfter(ITerminalSymbols.TokenNamedo, node.getStartPosition());
		if (getSelection().getOffset() == actionStart) {
			invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.after_do_keyword"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
			return false;
		}
		
		return result;
	}

	public boolean visit(MethodDeclaration node) {
		Block body= node.getBody();
		if (body == null || !getSelection().enclosedBy(body))
			return false;
		return super.visit(node);
	}
	
	public boolean visit(ConstructorInvocation node) {
		return visitConstructorInvocation(node, super.visit(node));
	}
	
	public boolean visit(SuperConstructorInvocation node) {
		return visitConstructorInvocation(node, super.visit(node));
	}
	
	private boolean visitConstructorInvocation(ASTNode node, boolean superResult) {
		if (getSelection().getVisitSelectionMode(node) == Selection.SELECTED) {
			invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.super_or_this"), JavaStatusContext.create(fCUnit, node)); //$NON-NLS-1$
			return false;
		}
		return superResult;
	}
	
	public boolean visit(VariableDeclarationFragment node) {
		boolean result= super.visit(node);
		if (isFirstSelectedNode(node)) {
			invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_variable_declaration_fragment"), JavaStatusContext.create(fCUnit, node)); //$NON-NLS-1$
			return false;
		}
		return result;
	}
	
	public void endVisit(ForStatement node) {
		if (getSelection().getEndVisitSelectionMode(node) == Selection.AFTER) {
			if (node.initializers().contains(getFirstSelectedNode())) {
				invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_for_initializer"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
			} else if (node.updaters().contains(getLastSelectedNode())) {
				invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_for_updater"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
			}
		}
		super.endVisit(node);
	}		
	
	public void endVisit(VariableDeclarationExpression node) {
		checkTypeInDeclaration(node.getType());
		super.endVisit(node);		
	}
			
	public void endVisit(VariableDeclarationStatement node) {
		checkTypeInDeclaration(node.getType());
		super.endVisit(node);		
	}
			
	private boolean isFirstSelectedNode(ASTNode node) {
		return getSelection().getVisitSelectionMode(node) == Selection.SELECTED && getFirstSelectedNode() == node;
	}
	
	private void checkTypeInDeclaration(Type node) {
		if (getSelection().getEndVisitSelectionMode(node) == Selection.SELECTED && getFirstSelectedNode() == node) {
			invalidSelection(RefactoringCoreMessages.getString("ExtractMethodAnalyzer.cannot_extract_variable_declaration"), JavaStatusContext.create(fCUnit, getSelection())); //$NON-NLS-1$
		}
	}
	
	private boolean isSingleExpressionOrStatementSet() {
		ASTNode first= getFirstSelectedNode();
		if (first == null)
			return true;
		if (first instanceof Expression && getSelectedNodes().length != 1)
			return false;
		return true;
	}
}

