/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICodeFormatter;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportEdit;
import org.eclipse.jdt.internal.corext.codemanipulation.MemberEdit;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.CompilationUnitBuffer;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.WorkingCopyUtil;
import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBufferEditor;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRegion;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Strings;

/**
 * Extracts a method in a compilation unit based on a text selection range.
 * <p>
 * <bf>NOTE:<bf> This class/interface is part of an interim API that is still under development 
 * and expected to change significantly before reaching stability. It is being made available at 
 * this early stage to solicit feedback from pioneering adopters on the understanding that any 
 * code that uses this API will almost certainly be broken (repeatedly) as the API evolves.</p>
 */
public class ExtractMethodRefactoring extends Refactoring {

	private ICompilationUnit fCUnit;
	private ImportEdit fImportEdit;
	private int fSelectionStart;
	private int fSelectionLength;
	private int fSelectionEnd;
	private boolean fCallOnDeclarationLine= true;
	
	private AST fAST;
	private ExtractMethodAnalyzer fAnalyzer;
	private String fVisibility;
	private String fMethodName;
	private boolean fThrowRuntimeExceptions;
	private int fMethodFlags= Modifier.PROTECTED;

	private static final String EMPTY= ""; //$NON-NLS-1$
	private static final String BLANK= " "; //$NON-NLS-1$
	private static final String RETURN= "return"; //$NON-NLS-1$
	private static final String RETURN_BLANK= "return "; //$NON-NLS-1$
	private static final String SEMICOLON= ";"; //$NON-NLS-1$
	private static final String COMMA_BLANK= ", "; //$NON-NLS-1$
	private static final String STATIC= "static"; //$NON-NLS-1$
	
	/**
	 * Creates a new extract method refactoring.
	 *
	 * @param cu the compilation unit which is going to be modified.
	 * @param accessor a callback object to access the source this refactoring is working on.
	 */
	public ExtractMethodRefactoring(ICompilationUnit cu, int selectionStart, int selectionLength, CodeGenerationSettings settings) {
		Assert.isNotNull(cu);
		Assert.isNotNull(settings);
		fCUnit= cu;
		fImportEdit= new ImportEdit(cu, settings);
		fMethodName= "extracted"; //$NON-NLS-1$
		fSelectionStart= selectionStart;
		fSelectionLength= selectionLength;
		fSelectionEnd= fSelectionStart + fSelectionLength - 1;
	}
	
	/* (non-Javadoc)
	 * Method declared in IRefactoring
	 */
	 public String getName() {
	 	return RefactoringCoreMessages.getFormattedString("ExtractMethodRefactoring.name", new String[]{fMethodName, fCUnit.getElementName()}); //$NON-NLS-1$
	 }

	/**
	 * Checks if the refactoring can be activated. Activation typically means, if a
	 * corresponding menu entry can be added to the UI.
	 *
	 * @param pm a progress monitor to report progress during activation checking.
	 * @return the refactoring status describing the result of the activation check.	 
	 */
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException {
		try {
			pm.beginTask(RefactoringCoreMessages.getString("ExtractMethodRefactoring.checking_selection"), 1); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();
			
			if (fSelectionStart < 0 || fSelectionLength == 0)
				return mergeTextSelectionStatus(result);
			
			result.merge(Checks.validateModifiesFiles(ResourceUtil.getFiles(new ICompilationUnit[]{fCUnit})));
			if (result.hasFatalError())
				return result;
			
			CompilationUnit root= AST.parseCompilationUnit(fCUnit, true);
			fAST= root.getAST();
			root.accept(createVisitor());
			
			result.merge(fAnalyzer.checkActivation());
			if (result.hasFatalError())
				return result;
			if (fVisibility == null) {
				int modifiers= fAnalyzer.getEnclosingMethod().getModifiers();
				String visibility= ""; //$NON-NLS-1$
				if (Modifier.isPublic(modifiers))
					visibility= "public"; //$NON-NLS-1$
				else if (Modifier.isProtected(modifiers))
					visibility= "protected"; //$NON-NLS-1$
				else if (Modifier.isPrivate(modifiers))
					visibility= "private"; //$NON-NLS-1$
				setVisibility(visibility);
				
			}
			return result;
		} catch (CoreException e){	
			throw new JavaModelException(e);
		} finally {
			pm.worked(1);
			pm.done();
		}
	}
	
	private ASTVisitor createVisitor() throws JavaModelException {
		fAnalyzer= new ExtractMethodAnalyzer(fCUnit, Selection.createFromStartLength(fSelectionStart, fSelectionLength));
		return fAnalyzer;
	}
	
	/**
	 * Sets the method name to be used for the extracted method.
	 *
	 * @param name the new method name.
	 */	
	public void setMethodName(String name) {
		fMethodName= name;
	}
	
	/**
	 * Returns the method name to be used for the extracted method.
	 * @return the method name to be used for the extracted method.
	 */
	public String getMethodName() {
		return fMethodName;
	} 
	
	/**
	 * Sets the visibility of the new method.
	 * 
	 * @param visibility the visibility of the new method. Valid values are
	 *  "public", "protected", "", and "private"
	 */
	public void setVisibility(String visibility) {
		fVisibility= visibility;
	}
	
	/**
	 * Returns the visibility of the new method.
	 * 
	 * @return the visibility of the new method
	 */
	public String getVisibility() {
		return fVisibility;
	}
	
	/**
	 * Sets whether the new method signature throws runtime exceptions.
	 * 
	 * @param throwsRuntimeExceptions flag indicating if the new method
	 * 	throws runtime exceptions
	 */
	public void setThrowRuntimeExceptions(boolean throwRuntimeExceptions) {
		fThrowRuntimeExceptions= throwRuntimeExceptions;
	}
	
	/**
	 * Checks if the refactoring can work on the values provided by the refactoring
	 * client. The client defined value for the extract method refactoring is the 
	 * new method name.
	 */
	public RefactoringStatus checkMethodName() {
		return Checks.checkMethodName(fMethodName);
	}
	
	
	/* (non-Javadoc)
	 * Method declared in Refactoring
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		RefactoringStatus result= null;
		MethodDeclaration node= fAnalyzer.getEnclosingMethod();
		if (node != null) {
			pm.beginTask(RefactoringCoreMessages.getString("ExtractMethodRefactoring.checking_new_name"), 2); //$NON-NLS-1$
			pm.subTask(EMPTY);
		
			result= Checks.checkMethodName(fMethodName);
			pm.worked(1);
			
			fAnalyzer.checkInput(result, fMethodName, fCUnit.getJavaProject());
			pm.worked(1);
		
			pm.done();
		} else {
			result= new RefactoringStatus();
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * Method declared in IRefactoring
	 */
	public IChange createChange(IProgressMonitor pm) throws JavaModelException {
		if (fMethodName == null)
			return null;
		
		fAnalyzer.aboutToCreateChange();
		MethodDeclaration method= fAnalyzer.getEnclosingMethod();
		String sourceMethodName= method.getName().getIdentifier();
		
		CompilationUnitChange result= null;
		try {
			result= new CompilationUnitChange(
				RefactoringCoreMessages.getFormattedString("ExtractMethodRefactoring.change_name", new String[]{fMethodName, sourceMethodName}),  //$NON-NLS-1$
				fCUnit);
		
			ITypeBinding[] exceptions= fAnalyzer.getExceptions(fThrowRuntimeExceptions, fAST);
			for (int i= 0; i < exceptions.length; i++) {
				ITypeBinding exception= exceptions[i];
				fImportEdit.addImport(Bindings.getFullyQualifiedImportName(exception));
			}
			
			if (fAnalyzer.generateImport()) {
				fImportEdit.addImport(ASTNodes.asString(fAnalyzer.getReturnType()));
			}
		
			if (!fImportEdit.isEmpty())
				result.addTextEdit(RefactoringCoreMessages.getString("ExtractMethodRefactoring.organize_imports"), fImportEdit); //$NON-NLS-1$
			
			TextBuffer buffer= null;
			try {
				// This is cheap since the compilation unit is already open in a editor.
				buffer= TextBuffer.create((IFile)WorkingCopyUtil.getOriginal(fCUnit).getCorrespondingResource());
				String delimiter= buffer.getLineDelimiter(buffer.getLineOfOffset(method.getStartPosition()));
				// Inserting the new method
				result.addTextEdit(RefactoringCoreMessages.getFormattedString("ExtractMethodRefactoring.add_method", fMethodName), //$NON-NLS-1$
					createNewMethodEdit(buffer, delimiter));
			
				// Replacing the old statements with the new method call.
				result.addTextEdit(RefactoringCoreMessages.getFormattedString("ExtractMethodRefactoring.substitute_with_call", fMethodName), //$NON-NLS-1$
					SimpleTextEdit.createReplace(fSelectionStart, fSelectionLength, createCall(buffer, delimiter)));
			} finally {
				TextBuffer.release(buffer);
			}
			
		} catch (CoreException e) {
			throw new JavaModelException(e);
		}
		return result;
	}
	
	/**
	 * Returns the signature of the new method.
	 * 
	 * @return the signature of the extracted method
	 */
	public String getSignature() {
		return getSignature(fMethodName);
	}
	
	/**
	 * Returns the signature of the new method.
	 * 
	 * @param methodName the method name used for the new method
	 * @return the signature of the extracted method
	 */
	public String getSignature(String methodName) {
		StringBuffer buffer= new StringBuffer(fVisibility);		
		if (fVisibility.length() > 0)
			buffer.append(BLANK);
			
		if (Modifier.isStatic(fAnalyzer.getEnclosingMethod().getModifiers())) {
			buffer.append(STATIC);
			buffer.append(BLANK);
		}
		
		Type returnType= fAnalyzer.getReturnType();
		if (returnType != null) {
			if (fAnalyzer.generateImport()) {
				buffer.append(ASTNodes.getTypeName(returnType));
			} else {
				buffer.append(ASTNodes.asString(returnType));
			}
			buffer.append(BLANK);
		}

		buffer.append(methodName);

		appendArguments(buffer);
		appendThrownExceptions(buffer);		
		return buffer.toString();
	}
	
	//---- Helper methods ------------------------------------------------------------------------
	
	private RefactoringStatus mergeTextSelectionStatus(RefactoringStatus status) {
		status.addFatalError(RefactoringCoreMessages.getString("ExtractMethodRefactoring.no_set_of_statements")); //$NON-NLS-1$
		return status;	
	}
	
	private TextEdit createNewMethodEdit(TextBuffer buffer, String delimiter) {
		MethodDeclaration method= fAnalyzer.getEnclosingMethod();
		final int methodStart= method.getStartPosition();
		final int spacing= CodeFormatterUtil.probeMethodSpacing(buffer, method);
		StringBuffer code= new StringBuffer();
		// +1 (e.g <=) for an extra newline since we insert the new code at
		// the end of a method declaration (e.g. right after the closing }
		for (int i= 0; i <= spacing; i++)
			code.append(delimiter);
		String[] lines= Strings.convertIntoLines(CodeFormatterUtil.createMethodDeclaration(getSignature(), createMethodBody(buffer), delimiter));
		String indent= CodeFormatterUtil.createIndentString(buffer.getLineContentOfOffset(methodStart));
		for (int i= 0, lastLine= lines.length - 1; i < lines.length; i++) {
			code.append(indent);
			code.append(lines[i]);
			if (i != lastLine)
				code.append(delimiter);
		} 
		TextEdit result= SimpleTextEdit.createInsert(methodStart + method.getLength(), code.toString());
		return result;
	}
	
	private String[] createMethodBody(TextBuffer buffer) {
		String[] lines= buffer.convertIntoLines(fSelectionStart, fSelectionLength, false);
		
		List result= new ArrayList(lines.length);
		
		String standardIndent= CodeFormatterUtil.createIndentString(buffer.getLineContentOfOffset(fAnalyzer.getSelectedNodeRange().getOffset()));
		
		// Locals that are not passed as an arguments since the extracted method only
		// writes to them
		IVariableBinding[] methodLocals= fAnalyzer.getMethodLocals();
		for (int i= 0; i < methodLocals.length; i++) {
			if (methodLocals[i] != null)
				result.add(standardIndent + getLocalDeclaration(methodLocals[i]));
		}

		String prefix= ""; //$NON-NLS-1$
		int offset= buffer.getLineInformationOfOffset(fSelectionStart).getOffset();
		if (offset < fSelectionStart) {
			String tmp= buffer.getContent(offset, fSelectionStart - offset);
			if (CodeFormatterUtil.containsOnlyWhiteSpaces(tmp)) {
				prefix= tmp;
			} else {
				prefix= CodeFormatterUtil.createIndentString(CodeFormatterUtil.getIndent(tmp));
			}
		}
		
		// We extract an expression
		boolean extractsExpression= fAnalyzer.isExpressionSelected();
		if (extractsExpression) {
			ITypeBinding binding= fAnalyzer.getExpressionBinding();
			if (binding != null && (!binding.isPrimitive() || !"void".equals(binding.getName()))) //$NON-NLS-1$
				prefix= prefix + RETURN_BLANK;
		}
		
		// Reformat and add to buffer
		int lastLine= lines.length - 1;
		for (int i= 0; i < lines.length; i++) {
			String line= lines[i];
			if (i == lastLine && sourceNeedsSemicolon())
				line= line + SEMICOLON;
			if (i == 0) {
				result.add(prefix + line);
			} else {
				result.add(line);	
			}
		}
		IVariableBinding returnValue= fAnalyzer.getReturnValue();
		if (returnValue != null) {
			result.add(standardIndent + RETURN_BLANK + returnValue.getName() + SEMICOLON);
		}
		return (String[]) result.toArray(new String[result.size()]);
	}
	
	private String createCall(TextBuffer buffer, String delimiter) {
		int firstLineIndent= CodeFormatterUtil.getIndent(buffer.getLineContentOfOffset(fSelectionStart));
		StringBuffer code= new StringBuffer();
		
		IVariableBinding[] locals= fAnalyzer.getCallerLocals();
		for (int i= 0; i < locals.length; i++) {
			appendLocalDeclaration(code, locals[i]);
			code.append(SEMICOLON);
			code.append(delimiter);
		}
				
		int returnKind= fAnalyzer.getReturnKind();
		switch (returnKind) {
			case ExtractMethodAnalyzer.ACCESS_TO_LOCAL:
				IVariableBinding binding= fAnalyzer.getReturnLocal();
				if (binding != null) {
					appendLocalDeclaration(code, binding);
				} else {
					binding= fAnalyzer.getReturnValue();
					code.append(binding.getName());
				}
				code.append(" = ");
				break;
			case ExtractMethodAnalyzer.RETURN_STATEMENT_VALUE:
				// We return a value. So the code must look like "return extracted();"
				code.append(RETURN_BLANK);
				break;
		}
		
		IVariableBinding[] arguments= fAnalyzer.getArguments();
		code.append(fMethodName);
		code.append("("); //$NON-NLS-1$
		for (int i= 0; i < arguments.length; i++) {
			if (arguments[i] == null)
				continue;
			if (i > 0)
				code.append(COMMA_BLANK);
			code.append(arguments[i].getName());
		}		
		code.append(")"); //$NON-NLS-1$
						
		if (callNeedsSemicolon())
			code.append(SEMICOLON);
			
		// We have a void return statement. The code looks like
		// extracted();
		// return;	
		if (returnKind == ExtractMethodAnalyzer.RETURN_STATEMENT_VOID) {
			code.append(delimiter);
			code.append(RETURN);
			code.append(SEMICOLON);
		}
		
		ICodeFormatter formatter= ToolFactory.createCodeFormatter();
		String result= formatter.format(code.toString(), firstLineIndent, null, delimiter);
		
		// we have to do this after formatting
		int pos= fSelectionStart + fSelectionLength;
		TextRegion region= buffer.getLineInformationOfOffset(pos);
		if (region.getOffset() == pos)
			result= result + delimiter;
		
		region= buffer.getLineInformationOfOffset(fSelectionStart);
		String selectedLine= buffer.getContent(region.getOffset(), fSelectionStart - region.getOffset());
		int indent= CodeFormatterUtil.getIndent(selectedLine);
		return CodeFormatterUtil.removeIndent(result,  indent, CodeFormatterUtil.getTabWidth());
	}


	private boolean callNeedsSemicolon() {
		ASTNode node= fAnalyzer.getLastSelectedNode();
		return node instanceof Statement;
	}
	
	private boolean sourceNeedsSemicolon() {
		ASTNode node= fAnalyzer.getLastSelectedNode();
		return node instanceof Expression;
	}	
	
	private void appendArguments(StringBuffer buffer) {
		buffer.append('('); //$NON-NLS-1$
		IVariableBinding[] arguments= fAnalyzer.getArguments();
		for (int i= 0; i < arguments.length; i++) {
			IVariableBinding argument= arguments[i];
			if (argument == null)
				continue;
				
			if (i > 0)
				buffer.append(COMMA_BLANK);
			appendLocalDeclaration(buffer, argument);
		}
		buffer.append(')'); //$NON-NLS-1$
	}
	
	private void appendThrownExceptions(StringBuffer buffer) {
		ITypeBinding[] exceptions= fAnalyzer.getExceptions(fThrowRuntimeExceptions, fAST);
		if (exceptions.length == 0)
			return;
			
		buffer.append(" throws "); //$NON-NLS-1$
		for (int i= 0; i < exceptions.length; i++) {
			ITypeBinding exception= exceptions[i];
			if (i > 0)
				buffer.append(", "); //$NON-NLS-1$
			buffer.append(exception.getName());
		}
	}
	
	private void appendLocalDeclaration(StringBuffer buffer, IVariableBinding local) {
		ASTNode root= fAnalyzer.getEnclosingMethod();
		VariableDeclaration declaration= ASTNodes.findVariableDeclaration(local, root);
		String modifiers= ASTNodes.modifierString(ASTNodes.getModifiers(declaration));
		if (modifiers.length() > 0) {
			buffer.append(modifiers);
			buffer.append(BLANK);
		}
		buffer.append(ASTNodes.asString(ASTNodes.getType(declaration)));
		buffer.append(BLANK);
		buffer.append(local.getName());
	}
	
	private void appendLocalDeclaration(StringBuffer buffer, String indent, IVariableBinding local, String delimiter) {
		buffer.append(indent);
		appendLocalDeclaration(buffer, local);
		buffer.append(SEMICOLON);
		buffer.append(delimiter);
	}
	
	private String getLocalDeclaration(IVariableBinding local) {
		StringBuffer buffer= new StringBuffer();
		appendLocalDeclaration(buffer, local);
		buffer.append(SEMICOLON);
		return buffer.toString();
	}
}