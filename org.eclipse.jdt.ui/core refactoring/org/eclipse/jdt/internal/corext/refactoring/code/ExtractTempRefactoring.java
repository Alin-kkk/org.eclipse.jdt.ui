/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.filebuffers.ITextFileBuffer;

import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.formatter.CodeFormatter;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.Corext;
import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.dom.fragments.ASTFragmentFactory;
import org.eclipse.jdt.internal.corext.dom.fragments.IASTFragment;
import org.eclipse.jdt.internal.corext.dom.fragments.IExpressionFragment;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaRefactorings;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusCodes;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.rename.RefactoringAnalyzeUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringFileBuffers;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;

/**
 * Extract Local Variable (from selected expression inside method or initializer).
 */
public class ExtractTempRefactoring extends Refactoring {

	private static final class ForStatementChecker extends ASTVisitor {

		private final Collection fForInitializerVariables;

		private boolean fReferringToForVariable= false;

		public ForStatementChecker(Collection forInitializerVariables) {
			Assert.isNotNull(forInitializerVariables);
			fForInitializerVariables= forInitializerVariables;
		}

		public boolean isReferringToForVariable() {
			return fReferringToForVariable;
		}

		public boolean visit(SimpleName node) {
			IBinding binding= node.resolveBinding();
			if (binding != null && fForInitializerVariables.contains(binding)) {
				fReferringToForVariable= true;
			}
			return false;
		}
	}

	private static final String[] KNOWN_METHOD_NAME_PREFIXES= { "get", "is"}; //$NON-NLS-2$ //$NON-NLS-1$

	private static boolean allArraysEqual(Object[][] arrays, int position) {
		Object element= arrays[0][position];
		for (int i= 0; i < arrays.length; i++) {
			Object[] array= arrays[i];
			if (!element.equals(array[position]))
				return false;
		}
		return true;
	}

	private static boolean canReplace(IASTFragment fragment) {
		ASTNode node= fragment.getAssociatedNode();
		ASTNode parent= node.getParent();
		if (parent instanceof VariableDeclarationFragment) {
			VariableDeclarationFragment vdf= (VariableDeclarationFragment) parent;
			if (node.equals(vdf.getName()))
				return false;
		}
		if (isMethodParameter(node))
			return false;
		if (isThrowableInCatchBlock(node))
			return false;
		if (parent instanceof ExpressionStatement)
			return false;
		if (isLeftValue(node))
			return false;
		if (isReferringToLocalVariableFromFor((Expression) node))
			return false;
		if (isUsedInForInitializerOrUpdater((Expression) node))
			return false;
		return true;
	}

	public static ExtractTempRefactoring create(ICompilationUnit cu, int selectionStart, int selectionLength) {
		return new ExtractTempRefactoring(cu, selectionStart, selectionLength);
	}

	private static Object[] getArrayPrefix(Object[] array, int prefixLength) {
		Assert.isTrue(prefixLength <= array.length);
		Assert.isTrue(prefixLength >= 0);
		Object[] prefix= new Object[prefixLength];
		for (int i= 0; i < prefix.length; i++) {
			prefix[i]= array[i];
		}
		return prefix;
	}

	// return List<IVariableBinding>
	private static List getForInitializedVariables(VariableDeclarationExpression variableDeclarations) {
		List forInitializerVariables= new ArrayList(1);
		for (Iterator iter= variableDeclarations.fragments().iterator(); iter.hasNext();) {
			VariableDeclarationFragment fragment= (VariableDeclarationFragment) iter.next();
			IVariableBinding binding= fragment.resolveBinding();
			if (binding != null)
				forInitializerVariables.add(binding);
		}
		return forInitializerVariables;
	}

	private static Object[] getLongestArrayPrefix(Object[][] arrays) {
		int length= -1;
		if (arrays.length == 0)
			return new Object[0];
		int minArrayLength= arrays[0].length;
		for (int i= 1; i < arrays.length; i++)
			minArrayLength= Math.min(minArrayLength, arrays[i].length);

		for (int i= 0; i < minArrayLength; i++) {
			if (!allArraysEqual(arrays, i))
				break;
			length++;
		}
		if (length == -1)
			return new Object[0];
		return getArrayPrefix(arrays[0], length + 1);
	}

	private static ASTNode[] getParents(ASTNode node) {
		ASTNode current= node;
		List parents= new ArrayList();
		do {
			parents.add(current.getParent());
			current= current.getParent();
		} while (current.getParent() != null);
		Collections.reverse(parents);
		return (ASTNode[]) parents.toArray(new ASTNode[parents.size()]);
	}

	private static String getQualifiedName(ITypeBinding typeBinding) {
		if (typeBinding.isAnonymous())
			return getQualifiedName(typeBinding.getSuperclass());
		if (!typeBinding.isArray())
			return typeBinding.getQualifiedName();
		else
			return typeBinding.getElementType().getQualifiedName();
	}

	public static boolean isAvailable(ASTNode[] selectedNodes, ASTNode coveringNode) {
		return Checks.isExtractableExpression(selectedNodes, coveringNode) || (selectedNodes.length == 1 && selectedNodes[0] instanceof ExpressionStatement);
	}

	private static boolean isLeftValue(ASTNode node) {
		ASTNode parent= node.getParent();
		if (parent instanceof Assignment) {
			Assignment assignment= (Assignment) parent;
			if (assignment.getLeftHandSide() == node)
				return true;
		}
		if (parent instanceof PostfixExpression)
			return true;
		if (parent instanceof PrefixExpression) {
			PrefixExpression.Operator op= ((PrefixExpression) parent).getOperator();
			if (op.equals(PrefixExpression.Operator.DECREMENT))
				return true;
			if (op.equals(PrefixExpression.Operator.INCREMENT))
				return true;
			return false;
		}
		return false;
	}

	private static boolean isMethodParameter(ASTNode node) {
		return (node instanceof SimpleName) && (node.getParent() instanceof SingleVariableDeclaration) && (node.getParent().getParent() instanceof MethodDeclaration);
	}

	private static boolean isReferringToLocalVariableFromFor(Expression expression) {
		ASTNode current= expression;
		ASTNode parent= current.getParent();
		while (parent != null && !(parent instanceof BodyDeclaration)) {
			if (parent instanceof ForStatement) {
				ForStatement forStmt= (ForStatement) parent;
				if (forStmt.initializers().contains(current) || forStmt.updaters().contains(current) || forStmt.getExpression() == current) {
					List initializers= forStmt.initializers();
					if (initializers.size() == 1 && initializers.get(0) instanceof VariableDeclarationExpression) {
						List forInitializerVariables= getForInitializedVariables((VariableDeclarationExpression) initializers.get(0));
						ForStatementChecker checker= new ForStatementChecker(forInitializerVariables);
						expression.accept(checker);
						if (checker.isReferringToForVariable())
							return true;
					}
				}
			}
			current= parent;
			parent= current.getParent();
		}
		return false;
	}

	private static boolean isThrowableInCatchBlock(ASTNode node) {
		return (node instanceof SimpleName) && (node.getParent() instanceof SingleVariableDeclaration) && (node.getParent().getParent() instanceof CatchClause);
	}

	private static boolean isUsedInForInitializerOrUpdater(Expression expression) {
		ASTNode parent= expression.getParent();
		if (parent instanceof ForStatement) {
			ForStatement forStmt= (ForStatement) parent;
			return forStmt.initializers().contains(expression) || forStmt.updaters().contains(expression);
		}
		return false;
	}

	// recursive
	private static String removeTrailingSemicolons(String s) {
		String arg= s.trim();
		if (!arg.endsWith(";")) //$NON-NLS-1$
			return arg;
		return removeTrailingSemicolons(arg.substring(0, arg.length() - 1));
	}

	private static IASTFragment[] retainOnlyReplacableMatches(IASTFragment[] allMatches) {
		List result= new ArrayList(allMatches.length);
		for (int i= 0; i < allMatches.length; i++) {
			if (canReplace(allMatches[i]))
				result.add(allMatches[i]);
		}
		return (IASTFragment[]) result.toArray(new IASTFragment[result.size()]);
	}

	private CompilationUnit fCompilationUnitNode;

	private final ICompilationUnit fCu;

	private boolean fDeclareFinal;

	private String[] fExcludedVariableNames;

	private boolean fReplaceAllOccurrences;

	// caches:
	private IExpressionFragment fSelectedExpression;

	private final int fSelectionLength;

	private final int fSelectionStart;

	private String fTempName;

	private ExtractTempRefactoring(ICompilationUnit cu, int selectionStart, int selectionLength) {
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);
		Assert.isTrue(cu.exists());
		fSelectionStart= selectionStart;
		fSelectionLength= selectionLength;
		fCu= cu;

		fReplaceAllOccurrences= true; // default
		fDeclareFinal= false; // default
		fTempName= ""; //$NON-NLS-1$
	}

	private void addImportIfNeeded(TextChange change, ITextFileBuffer buffer) throws CoreException {
		TextEdit importEdit= createImportEditIfNeeded(buffer);
		if (importEdit != null)
			TextChangeCompatibility.addTextEdit(change, RefactoringCoreMessages.getString("ExtractTempRefactoring.update_imports"), importEdit); //$NON-NLS-1$
	}

	private void addReplaceExpressionWithTemp(TextChange change) throws JavaModelException {
		TextEdit[] edits= createReplaceExpressionWithTempEdits();
		for (int i= 0; i < edits.length; i++) {
			TextChangeCompatibility.addTextEdit(change, RefactoringCoreMessages.getString("ExtractTempRefactoring.replace"), edits[i]); //$NON-NLS-1$
		}
	}

	private RefactoringStatus checkExpression() throws JavaModelException {
		Expression selectedExpression= getSelectedExpression().getAssociatedExpression();

		if (selectedExpression instanceof NullLiteral) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.null_literals")); //$NON-NLS-1$
		} else if (selectedExpression instanceof ArrayInitializer) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.array_initializer")); //$NON-NLS-1$
		} else if (selectedExpression instanceof Assignment) {
			if (selectedExpression.getParent() instanceof Expression)
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.assignment")); //$NON-NLS-1$
			else
				return null;

		} else if (selectedExpression instanceof ConditionalExpression) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.single_conditional_expression")); //$NON-NLS-1$
		} else if (selectedExpression instanceof SimpleName) {
			if ((((SimpleName) selectedExpression)).isDeclaration())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.names_in_declarations")); //$NON-NLS-1$
			if (selectedExpression.getParent() instanceof QualifiedName && selectedExpression.getLocationInParent() == QualifiedName.NAME_PROPERTY || selectedExpression.getParent() instanceof FieldAccess && selectedExpression.getLocationInParent() == FieldAccess.NAME_PROPERTY)
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.select_expression"));//$NON-NLS-1$;
		}

		return null;
	}

	// !! Same as in ExtractConstantRefactoring
	private RefactoringStatus checkExpressionFragmentIsRValue() throws JavaModelException {
		switch (Checks.checkExpressionIsRValue(getSelectedExpression().getAssociatedExpression())) {
			case Checks.NOT_RVALUE_MISC:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("ExtractTempRefactoring.select_expression"), null, Corext.getPluginId(), RefactoringStatusCodes.EXPRESSION_NOT_RVALUE, null); //$NON-NLS-1$
			case Checks.NOT_RVALUE_VOID:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("ExtractTempRefactoring.no_void"), null, Corext.getPluginId(), RefactoringStatusCodes.EXPRESSION_NOT_RVALUE_VOID, null); //$NON-NLS-1$
			case Checks.IS_RVALUE:
				return new RefactoringStatus();
			default:
				Assert.isTrue(false);
				return null;
		}
	}

	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		ITextFileBuffer buffer= null;
		final ICompilationUnit original= WorkingCopyUtil.getOriginal(fCu);
		try {
			buffer= RefactoringFileBuffers.acquire(original);
			pm.beginTask(RefactoringCoreMessages.getString("ExtractTempRefactoring.checking_preconditions"), 1); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();

			result.merge(checkMatchingFragments());
			if (Arrays.asList(getExcludedVariableNames()).contains(fTempName))
				result.addWarning(RefactoringCoreMessages.getFormattedString("ExtractTempRefactoring.another_variable", fTempName)); //$NON-NLS-1$

			TextChange change= new DocumentChange(RefactoringCoreMessages.getString("RenameTempRefactoring.rename"), new Document(fCu.getSource())); //$NON-NLS-1$
			try {
				TextChangeCompatibility.addTextEdit(change, "", getAllEdits(buffer)); //$NON-NLS-1$
			} catch (CoreException exception) {
				JavaPlugin.log(exception);
			} catch (BadLocationException exception) {
				JavaPlugin.log(exception);
			}
			String newCuSource= change.getPreviewContent(new NullProgressMonitor());
			ASTParser p= ASTParser.newParser(AST.JLS3);
			p.setSource(newCuSource.toCharArray());
			p.setUnitName(fCu.getElementName());
			p.setProject(fCu.getJavaProject());
			p.setCompilerOptions(RefactoringASTParser.getCompilerOptions(fCu));
			CompilationUnit newCUNode= (CompilationUnit) p.createAST(null);
			IProblem[] newProblems= RefactoringAnalyzeUtil.getIntroducedCompileProblems(newCUNode, fCompilationUnitNode);
			for (int i= 0; i < newProblems.length; i++) {
				IProblem problem= newProblems[i];
				if (problem.isError())
					result.addEntry(JavaRefactorings.createStatusEntry(problem, newCuSource));
			}
			return result;
		} finally {
			RefactoringFileBuffers.release(original);
		}
	}

	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 6); //$NON-NLS-1$

			RefactoringStatus result= Checks.validateModifiesFiles(ResourceUtil.getFiles(new ICompilationUnit[] { fCu}), getValidationContext());
			if (result.hasFatalError())
				return result;

			if (!fCu.isStructureKnown())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.syntax_error")); //$NON-NLS-1$

			fCompilationUnitNode= new RefactoringASTParser(AST.JLS3).parse(fCu, true, new SubProgressMonitor(pm, 3));

			result.merge(checkSelection(new SubProgressMonitor(pm, 3)));
			if ((!result.hasFatalError()) && isLiteralNodeSelected())
				fReplaceAllOccurrences= false;
			return result;
		} finally {
			pm.done();
		}
	}

	private RefactoringStatus checkMatchingFragments() throws JavaModelException {
		RefactoringStatus result= new RefactoringStatus();
		IASTFragment[] matchingFragments= getMatchingFragments();
		for (int i= 0; i < matchingFragments.length; i++) {
			ASTNode node= matchingFragments[i].getAssociatedNode();
			if (isLeftValue(node) && !isReferringToLocalVariableFromFor((Expression) node)) {
				String msg= RefactoringCoreMessages.getString("ExtractTempRefactoring.assigned_to"); //$NON-NLS-1$
				result.addWarning(msg, JavaStatusContext.create(fCu, node));
			}
		}
		return result;
	}

	private RefactoringStatus checkSelection(IProgressMonitor pm) throws JavaModelException {
		try {
			pm.beginTask("", 8); //$NON-NLS-1$

			IExpressionFragment selectedExpression= getSelectedExpression();

			if (selectedExpression == null) {
				String message= RefactoringCoreMessages.getString("ExtractTempRefactoring.select_expression");//$NON-NLS-1$
				return CodeRefactoringUtil.checkMethodSyntaxErrors(fSelectionStart, fSelectionLength, fCompilationUnitNode, message);
			}
			pm.worked(1);

			if (isUsedInExplicitConstructorCall())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.explicit_constructor")); //$NON-NLS-1$
			pm.worked(1);

			if (getEnclosingBodyNode() == null)
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.expr_in_method_or_initializer")); //$NON-NLS-1$
			pm.worked(1);

			ASTNode associatedNode= selectedExpression.getAssociatedNode();
			if (associatedNode instanceof Name && associatedNode.getParent() instanceof ClassInstanceCreation && associatedNode.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY)
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.name_in_new")); //$NON-NLS-1$
			pm.worked(1);

			RefactoringStatus result= new RefactoringStatus();
			result.merge(checkExpression());
			if (result.hasFatalError())
				return result;
			pm.worked(1);

			result.merge(checkExpressionFragmentIsRValue());
			if (result.hasFatalError())
				return result;
			pm.worked(1);

			if (isUsedInForInitializerOrUpdater(getSelectedExpression().getAssociatedExpression()))
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.for_initializer_updater")); //$NON-NLS-1$
			pm.worked(1);

			if (isReferringToLocalVariableFromFor(getSelectedExpression().getAssociatedExpression()))
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.refers_to_for_variable")); //$NON-NLS-1$
			pm.worked(1);

			return result;
		} finally {
			pm.done();
		}
	}

	public RefactoringStatus checkTempName(String newName) {
		RefactoringStatus status= Checks.checkTempName(newName);
		if (Arrays.asList(getExcludedVariableNames()).contains(newName))
			status.addWarning(RefactoringCoreMessages.getFormattedString("ExtractTempRefactoring.another_variable", newName)); //$NON-NLS-1$
		return status;
	}

	private TextEdit createAndInsertTempDeclaration(String delimiter) throws CoreException {
		ASTNode insertBefore= getNodeToInsertTempDeclarationBefore();
		int insertOffset= insertBefore.getStartPosition();
		String text= createTempDeclarationSource(getInitializerSource(), true, delimiter) + CodeFormatterUtil.createIndentString(CodeRefactoringUtil.getIndentationLevel(insertBefore, fCu), fCu.getJavaProject());
		return new InsertEdit(insertOffset, text);
	}

	public Change createChange(IProgressMonitor pm) throws CoreException {
		ITextFileBuffer buffer= null;
		final ICompilationUnit original= WorkingCopyUtil.getOriginal(fCu);
		try {
			buffer= RefactoringFileBuffers.acquire(original);
			pm.beginTask(RefactoringCoreMessages.getString("ExtractTempRefactoring.preview"), 3); //$NON-NLS-1$
			final TextChange result= new CompilationUnitChange(RefactoringCoreMessages.getString("ExtractTempRefactoring.extract_temp"), fCu); //$NON-NLS-1$
			try {
				TextChangeCompatibility.addTextEdit(result, RefactoringCoreMessages.getString("ExtractTempRefactoring.declare_local_variable"), createTempDeclarationEdit(buffer.getDocument().getLineDelimiter(buffer.getDocument().getLineOfOffset(fSelectionStart)))); //$NON-NLS-1$
			} catch (CoreException exception) {
				JavaPlugin.log(exception);
			} catch (BadLocationException exception) {
				JavaPlugin.log(exception);
			}
			pm.worked(1);
			addImportIfNeeded(result, buffer);
			pm.worked(1);
			addReplaceExpressionWithTemp(result);
			pm.worked(1);

			return result;
		} finally {
			RefactoringFileBuffers.release(original);
			pm.done();
		}
	}

	private TextEdit createImportEditIfNeeded(ITextFileBuffer buffer) throws CoreException {
		final Expression expression= getSelectedExpression().getAssociatedExpression();
		if (expression instanceof ClassInstanceCreation) {
			final ClassInstanceCreation creation= (ClassInstanceCreation) expression;
			final Type type= creation.getType();
			if (type.isQualifiedType())
				return null;
			if (type instanceof SimpleType && ((SimpleType) type).getName().isQualifiedName())
				return null;
			if (type instanceof ParameterizedType) {
				final Type raw= ((ParameterizedType) type).getType();
				if (raw instanceof SimpleType && ((SimpleType) raw).getName().isQualifiedName())
					return null;
			}
		}
		final ITypeBinding type= expression.resolveTypeBinding();
		if (type.isPrimitive())
			return null;
		if (type.isArray() && type.getElementType().isPrimitive())
			return null;
		final IPackageBinding pack= type.getPackage();
		final IPackageDeclaration[] declarations= fCu.getPackageDeclarations();
		if (pack != null && declarations.length > 0 && pack.getName().equals(declarations[0].getElementName()))
			return null;
		final ImportRewrite rewrite= new ImportRewrite(fCu);
		rewrite.addImport(type);
		if (rewrite.isEmpty())
			return null;
		else
			return rewrite.createEdit(buffer.getDocument());
	}

	private TextEdit[] createReplaceExpressionWithTempEdits() throws JavaModelException {
		IASTFragment[] fragmentsToReplace= retainOnlyReplacableMatches(getMatchingFragments());
		TextEdit[] result= new TextEdit[fragmentsToReplace.length];
		for (int i= 0; i < fragmentsToReplace.length; i++) {
			IASTFragment fragment= fragmentsToReplace[i];
			int offset= fragment.getStartPosition();
			int length= fragment.getLength();
			result[i]= new ReplaceEdit(offset, length, fTempName);
		}
		return result;
	}

	private TextEdit createTempDeclarationEdit(String delimiter) throws CoreException {
		if (shouldReplaceSelectedExpressionWithTempDeclaration())
			return replaceSelectedExpressionWithTempDeclaration(delimiter);
		else
			return createAndInsertTempDeclaration(delimiter);
	}

	// without the trailing indent
	private String createTempDeclarationSource(String initializerSource, boolean addTrailingLineDelimiter, String delimiter) throws CoreException {
		String modifier= fDeclareFinal ? "final " : ""; //$NON-NLS-1$ //$NON-NLS-2$
		String dummyInitializer= "0"; //$NON-NLS-1$
		String semicolon= ";"; //$NON-NLS-1$
		String dummyDeclaration= modifier + getTempTypeName() + " " + fTempName + " = " + dummyInitializer + semicolon; //$NON-NLS-1$ //$NON-NLS-2$
		int[] position= { dummyDeclaration.length() - dummyInitializer.length() - semicolon.length()};
		String formattedDeclaration= CodeFormatterUtil.format(CodeFormatter.K_STATEMENTS, dummyDeclaration, 0, position, delimiter, fCu.getJavaProject());
		StringBuffer formattedDummyDeclaration= new StringBuffer(formattedDeclaration);
		String tail= addTrailingLineDelimiter ? delimiter : ""; //$NON-NLS-1$
		return formattedDummyDeclaration.replace(position[0], position[0] + dummyInitializer.length(), initializerSource).append(tail).toString();
	}

	public boolean declareFinal() {
		return fDeclareFinal;
	}

	private ASTNode[] findDeepestCommonSuperNodePathForReplacedNodes() throws JavaModelException {
		ASTNode[] matchNodes= getMatchNodes();

		ASTNode[][] matchingNodesParents= new ASTNode[matchNodes.length][];
		for (int i= 0; i < matchNodes.length; i++) {
			matchingNodesParents[i]= getParents(matchNodes[i]);
		}
		List l= Arrays.asList(getLongestArrayPrefix(matchingNodesParents));
		return (ASTNode[]) l.toArray(new ASTNode[l.size()]);
	}

	private TextEdit[] getAllEdits(ITextFileBuffer buffer) throws CoreException, BadLocationException {
		Collection edits= new ArrayList(3);
		edits.add(createTempDeclarationEdit(buffer.getDocument().getLineDelimiter(buffer.getDocument().getLineOfOffset(fSelectionStart))));
		TextEdit importEdit= createImportEditIfNeeded(buffer);
		if (importEdit != null)
			edits.add(importEdit);
		TextEdit[] replaceEdits= createReplaceExpressionWithTempEdits();
		for (int i= 0; i < replaceEdits.length; i++) {
			edits.add(replaceEdits[i]);
		}
		return (TextEdit[]) edits.toArray(new TextEdit[edits.size()]);
	}

	private Block getEnclosingBodyNode() throws JavaModelException {
		ASTNode node= getSelectedExpression().getAssociatedNode();
		do {
			switch (node.getNodeType()) {
				case ASTNode.METHOD_DECLARATION:
					return ((MethodDeclaration) node).getBody();
				case ASTNode.INITIALIZER:
					return ((Initializer) node).getBody();
			}
			node= node.getParent();
		} while (node != null);
		return null;
	}

	private String[] getExcludedVariableNames() {
		if (fExcludedVariableNames == null) {
			try {
				IBinding[] bindings= new ScopeAnalyzer(fCompilationUnitNode).getDeclarationsInScope(getSelectedExpression().getStartPosition(), ScopeAnalyzer.VARIABLES);
				fExcludedVariableNames= new String[bindings.length];
				for (int i= 0; i < bindings.length; i++) {
					fExcludedVariableNames[i]= bindings[i].getName();
				}
			} catch (JavaModelException e) {
				fExcludedVariableNames= new String[0];
			}
		}
		return fExcludedVariableNames;
	}

	private IExpressionFragment getFirstReplacedExpression() throws JavaModelException {
		if (!fReplaceAllOccurrences)
			return getSelectedExpression();
		IASTFragment[] nodesToReplace= retainOnlyReplacableMatches(getMatchingFragments());
		if (nodesToReplace.length == 0)
			return getSelectedExpression();
		Comparator comparator= new Comparator() {

			public int compare(Object o1, Object o2) {
				return ((IASTFragment) o1).getStartPosition() - ((IASTFragment) o2).getStartPosition();
			}
		};
		Arrays.sort(nodesToReplace, comparator);
		return (IExpressionFragment) nodesToReplace[0];
	}

	private String getInitializerSource() throws JavaModelException {
		IExpressionFragment fragment= getSelectedExpression();
		return removeTrailingSemicolons(fCu.getBuffer().getText(fragment.getStartPosition(), fragment.getLength()));
	}

	private Statement getInnermostStatementInBlock(ASTNode node) {
		Block block= (Block) ASTNodes.getParent(node, Block.class);
		if (block == null)
			return null;
		for (Iterator iter= block.statements().iterator(); iter.hasNext();) {
			Statement statement= (Statement) iter.next();
			if (ASTNodes.isParent(node, statement))
				return statement;
		}
		return null;
	}

	private IASTFragment[] getMatchingFragments() throws JavaModelException {
		if (fReplaceAllOccurrences) {
			IASTFragment[] allMatches= ASTFragmentFactory.createFragmentForFullSubtree(getEnclosingBodyNode()).getSubFragmentsMatching(getSelectedExpression());
			return allMatches;
		} else
			return new IASTFragment[] { getSelectedExpression()};
	}

	private ASTNode[] getMatchNodes() throws JavaModelException {
		IASTFragment[] matches= retainOnlyReplacableMatches(getMatchingFragments());
		ASTNode[] result= new ASTNode[matches.length];
		for (int i= 0; i < matches.length; i++)
			result[i]= matches[i].getAssociatedNode();
		return result;
	}

	public String getName() {
		return RefactoringCoreMessages.getString("ExtractTempRefactoring.name"); //$NON-NLS-1$
	}

	private ASTNode getNodeToInsertTempDeclarationBefore() throws JavaModelException {
		if ((!fReplaceAllOccurrences) || (retainOnlyReplacableMatches(getMatchingFragments()).length <= 1))
			return getInnermostStatementInBlock(getSelectedExpression().getAssociatedNode());

		ASTNode[] firstReplaceNodeParents= getParents(getFirstReplacedExpression().getAssociatedNode());
		ASTNode[] commonPath= findDeepestCommonSuperNodePathForReplacedNodes();
		Assert.isTrue(commonPath.length <= firstReplaceNodeParents.length);

		ASTNode deepestCommonParent= firstReplaceNodeParents[commonPath.length - 1];
		if (deepestCommonParent instanceof TryStatement || deepestCommonParent instanceof IfStatement) {
			if (deepestCommonParent.getParent() instanceof Block)
				return deepestCommonParent;
			if (commonPath.length < firstReplaceNodeParents.length)
				return getInnermostStatementInBlock(firstReplaceNodeParents[commonPath.length]);
		}

		if (deepestCommonParent instanceof Block)
			return firstReplaceNodeParents[commonPath.length];

		return getInnermostStatementInBlock(getFirstReplacedExpression().getAssociatedNode());
	}

	private IExpressionFragment getSelectedExpression() throws JavaModelException {
		if (fSelectedExpression != null)
			return fSelectedExpression;
		IASTFragment selectedFragment= ASTFragmentFactory.createFragmentForSourceRange(new SourceRange(fSelectionStart, fSelectionLength), fCompilationUnitNode, fCu);

		if (selectedFragment instanceof IExpressionFragment && !Checks.isInsideJavadoc(selectedFragment.getAssociatedNode())) {
			fSelectedExpression= (IExpressionFragment) selectedFragment;
		} else if (selectedFragment != null && selectedFragment.getAssociatedNode() instanceof ExpressionStatement) {
			ExpressionStatement exprStatement= (ExpressionStatement) selectedFragment.getAssociatedNode();
			Expression expression= exprStatement.getExpression();
			fSelectedExpression= (IExpressionFragment) ASTFragmentFactory.createFragmentForFullSubtree(expression);
		}

		return fSelectedExpression;
	}

	public String getTempSignaturePreview() throws JavaModelException {
		return getTempTypeName() + " " + fTempName; //$NON-NLS-1$
	}

	private String getTempTypeName() throws JavaModelException {
		final Expression expression= getSelectedExpression().getAssociatedExpression();
		if (expression instanceof ClassInstanceCreation) {
			final ClassInstanceCreation creation= (ClassInstanceCreation) expression;
			if (creation.getExpression() instanceof ClassInstanceCreation)
				return getClassInstanceName((ClassInstanceCreation) creation.getExpression()) + getClassInstanceName(creation);
			return getClassInstanceName(creation);
		} else if (expression instanceof CastExpression) {
			final CastExpression cast= (CastExpression) expression;
			return ASTNodes.asString(cast.getType());
		}
		ITypeBinding binding= expression.resolveTypeBinding();
		final StringBuffer buffer= new StringBuffer();
		buffer.insert(0, binding.getName());
		while (binding.getDeclaringClass() != null) {
			buffer.insert(0, binding.getDeclaringClass().getName() + "."); //$NON-NLS-1$
			binding= binding.getDeclaringClass();
		}
		return buffer.toString();
	}

	private String getClassInstanceName(final ClassInstanceCreation creation) {
		return ASTNodes.asString(creation.getType());
	}

	public String guessTempName() {
		String[] proposals= guessTempNames();
		if (proposals.length == 0)
			return fTempName;
		else
			return proposals[0];
	}

	/**
	 * @return proposed variable names (may be empty, but not null). The first proposal should be used as "best guess" (if it exists).
	 */
	public String[] guessTempNames() {
		LinkedHashSet proposals= new LinkedHashSet(); // retain ordering, but prevent duplicates
		try {
			String[] excludedVariableNames= getExcludedVariableNames();
			ASTNode associatedNode= getSelectedExpression().getAssociatedNode();
			if (associatedNode instanceof MethodInvocation) {
				proposals.addAll(guessTempNamesFromMethodInvocation((MethodInvocation) associatedNode, excludedVariableNames));
			} else if (associatedNode instanceof CastExpression) {
				Expression expression= ((CastExpression) associatedNode).getExpression();
				if (expression instanceof MethodInvocation) {
					proposals.addAll(guessTempNamesFromMethodInvocation((MethodInvocation) expression, excludedVariableNames));
				}
			}
			if (associatedNode instanceof Expression) {
				proposals.addAll(guessTempNamesFromExpression((Expression) associatedNode, excludedVariableNames));
			}
		} catch (JavaModelException e) {
			// too bad ... no proposals this time
		}
		return (String[]) proposals.toArray(new String[proposals.size()]);
	}

	private List guessTempNamesFromExpression(Expression selectedExpression, String[] excluded) {
		ITypeBinding expressionBinding= selectedExpression.resolveTypeBinding();
		String typeName= getQualifiedName(expressionBinding);
		if (typeName.length() == 0)
			typeName= expressionBinding.getName();
		if (typeName.length() == 0)
			return Collections.EMPTY_LIST;
		String[] proposals= StubUtility.getLocalNameSuggestions(fCu.getJavaProject(), typeName, expressionBinding.getDimensions(), excluded);
		return Arrays.asList(proposals);
	}

	private List guessTempNamesFromMethodInvocation(MethodInvocation selectedMethodInvocation, String[] excludedVariableNames) {
		String methodName= selectedMethodInvocation.getName().getIdentifier();
		for (int i= 0; i < KNOWN_METHOD_NAME_PREFIXES.length; i++) {
			String prefix= KNOWN_METHOD_NAME_PREFIXES[i];
			if (!methodName.startsWith(prefix))
				continue; // not this prefix
			if (methodName.length() == prefix.length())
				return Collections.EMPTY_LIST;
			char firstAfterPrefix= methodName.charAt(prefix.length());
			if (!Character.isUpperCase(firstAfterPrefix))
				continue;
			String proposal= Character.toLowerCase(firstAfterPrefix) + methodName.substring(prefix.length() + 1);
			methodName= proposal;
			break;
		}
		String[] proposals= StubUtility.getLocalNameSuggestions(fCu.getJavaProject(), methodName, 0, excludedVariableNames);
		return Arrays.asList(proposals);
	}

	private boolean isLiteralNodeSelected() throws JavaModelException {
		IExpressionFragment fragment= getSelectedExpression();
		if (fragment == null)
			return false;
		Expression expression= fragment.getAssociatedExpression();
		if (expression == null)
			return false;
		switch (expression.getNodeType()) {
			case ASTNode.BOOLEAN_LITERAL:
			case ASTNode.CHARACTER_LITERAL:
			case ASTNode.NULL_LITERAL:
			case ASTNode.NUMBER_LITERAL:
				return true;

			default:
				return false;
		}
	}

	private boolean isUsedInExplicitConstructorCall() throws JavaModelException {
		Expression selectedExpression= getSelectedExpression().getAssociatedExpression();
		if (ASTNodes.getParent(selectedExpression, ConstructorInvocation.class) != null)
			return true;
		if (ASTNodes.getParent(selectedExpression, SuperConstructorInvocation.class) != null)
			return true;
		return false;
	}

	public boolean replaceAllOccurrences() {
		return fReplaceAllOccurrences;
	}

	private TextEdit replaceSelectedExpressionWithTempDeclaration(String delimiter) throws CoreException {
		String nodeSource= fCu.getBuffer().getText(getSelectedExpression().getStartPosition(), getSelectedExpression().getLength());
		String text= createTempDeclarationSource(nodeSource, false, delimiter);
		ASTNode parent= getSelectedExpression().getAssociatedNode().getParent();
		Assert.isTrue(parent instanceof ExpressionStatement);

		return new ReplaceEdit(parent.getStartPosition(), parent.getLength(), text);
	}

	public void setDeclareFinal(boolean declareFinal) {
		fDeclareFinal= declareFinal;
	}

	public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
		fReplaceAllOccurrences= replaceAllOccurrences;
	}

	public void setTempName(String newName) {
		fTempName= newName;
	}

	private boolean shouldReplaceSelectedExpressionWithTempDeclaration() throws JavaModelException {
		IExpressionFragment selectedFragment= getSelectedExpression();
		return selectedFragment.getAssociatedNode().getParent() instanceof ExpressionStatement && selectedFragment.matches(ASTFragmentFactory.createFragmentForFullSubtree(selectedFragment.getAssociatedNode()));
	}
}