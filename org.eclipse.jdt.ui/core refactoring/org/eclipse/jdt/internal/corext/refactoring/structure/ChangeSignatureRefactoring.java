/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.Context;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaSourceContext;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChange;
import org.eclipse.jdt.internal.corext.refactoring.rename.MethodChecks;
import org.eclipse.jdt.internal.corext.refactoring.rename.RefactoringAnalyzeUtil;
import org.eclipse.jdt.internal.corext.refactoring.rename.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.rename.RippleMethodFinder;
import org.eclipse.jdt.internal.corext.refactoring.rename.TempOccurrenceFinder;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.textmanipulation.MultiTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.util.AllTypesCache;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.TypeInfo;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

public class ChangeSignatureRefactoring extends Refactoring {
	
	private final List fParameterInfos;
	private final CodeGenerationSettings fCodeGenerationSettings;
	private final ImportEditManager fImportEditManager;
	private final ASTNodeMappingManager fAstManager;
	private final ASTRewriteManager fRewriteManager;

	private TextChangeManager fChangeManager;
	private IMethod fMethod;
	private IMethod[] fRippleMethods;
	private ASTNode[] fOccurrenceNodes;
	private Set fDescriptionGroups;
	private String fReturnTypeName;
	private int fVisibility;
	private static final String CONST_CLASS_DECL = "class A{";//$NON-NLS-1$
	private static final String CONST_ASSIGN = " i=";		//$NON-NLS-1$
	private static final String CONST_CLOSE = ";}";			//$NON-NLS-1$
	private static final String DEFAULT_NEW_PARAM_TYPE= "int";//$NON-NLS-1$
	private static final String DEFAULT_NEW_PARAM_VALUE= "0"; //$NON-NLS-1$

	public ChangeSignatureRefactoring(IMethod method, CodeGenerationSettings codeGenerationSettings){
		Assert.isNotNull(method);
		fMethod= method;
		fParameterInfos= createParameterInfoList(method);
		fAstManager= new ASTNodeMappingManager();
		fRewriteManager= new ASTRewriteManager(fAstManager);
		fDescriptionGroups= new HashSet(0);
		fCodeGenerationSettings= codeGenerationSettings;
		fImportEditManager= new ImportEditManager(fCodeGenerationSettings);
		try {
			fReturnTypeName= getInitialReturnTypeName();
			fVisibility= getInitialMethodVisibility();
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			fVisibility= JdtFlags.VISIBILITY_CODE_INVALID;
			fReturnTypeName= ""; //$NON-NLS-1$
		}
	}
	
	private String getInitialReturnTypeName() throws JavaModelException{
		return Signature.toString(Signature.getReturnType(fMethod.getSignature()));
	}
	
	private int getInitialMethodVisibility() throws JavaModelException{
		return JdtFlags.getVisibilityCode(fMethod);
	}
	
	private static List createParameterInfoList(IMethod method) {
		try {
			String[] typeNames= method.getParameterTypes();
			String[] oldNames= method.getParameterNames();
			List result= new ArrayList(typeNames.length);
			for (int i= 0; i < oldNames.length; i++){
				result.add(new ParameterInfo(Signature.toString(typeNames[i]), oldNames[i], i));
			}
			return result;
		} catch(JavaModelException e) {
			JavaPlugin.log(e);
			return new ArrayList(0);
		}		
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.getString("ChangeSignatureRefactoring.modify_Parameters"); //$NON-NLS-1$
	}
	
	public IMethod getMethod() {
		return fMethod;
	}
	
	public void setNewReturnTypeName(String newReturnTypeName){
		Assert.isNotNull(newReturnTypeName);
		fReturnTypeName= newReturnTypeName;
	}
	
	public boolean canChangeReturnType(){
		try {
			return ! fMethod.isConstructor();
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return false;
		}
	}
	
	/*
	 * @see JdtFlags
	 */
	public int getVisibility(){
		return fVisibility;
	}

	/*
	 * @see JdtFlags
	 */	
	public void setVisibility(int visibility){
		Assert.isTrue(	visibility == Modifier.PUBLIC ||
		            	visibility == Modifier.PROTECTED ||
		            	visibility == Modifier.NONE ||
		            	visibility == Modifier.PRIVATE);  
		fVisibility= visibility;            	
	}
	
	/*
	 * @see JdtFlags
	 */	
	public int[] getAvailableVisibilities() throws JavaModelException{
		if (fMethod.getDeclaringType().isInterface())
			return new int[]{Modifier.PUBLIC};
		else 	
			return new int[]{	Modifier.PUBLIC,
								Modifier.PROTECTED,
								Modifier.NONE,
								Modifier.PRIVATE};
	}
	
	/**
	 * 	 * @return List of <code>ParameterInfo</code> objects.	 */
	public List getParameterInfos(){
		return fParameterInfos;
	}
	
	public void setupNewParameterInfo(ParameterInfo parameter) {
		parameter.setDefaultValue(DEFAULT_NEW_PARAM_VALUE);
		parameter.setNewTypeName(DEFAULT_NEW_PARAM_TYPE);
		parameter.setNewName(findUnusedParameterName());
	} 
	
	private String findUnusedParameterName() {
		Set usedNames= getUsedParameterNames();
		int i= 0;
		String prefix= "arg";  //$NON-NLS-1$
		while(true){
			String candidate= prefix + i++;
			if (! usedNames.contains(candidate))
				return candidate;
		}
	}
	
	private Set getUsedParameterNames(){
		Set names= new HashSet(2);
		for (Iterator iter= getNotDeletedInfos().iterator(); iter.hasNext();) {
			names.add(((ParameterInfo) iter.next()).getNewName());
		}
		return names;
	}
	
	public RefactoringStatus checkSignature() throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		checkForDuplicateNames(result);
		if (result.hasFatalError())
			return result;
		checkParameters(result);
		if (result.hasFatalError())
			return result;
		checkReturnType(result);
		return result;
	}
	
    public boolean isSignatureSameAsInitial() throws JavaModelException {
        if (fMethod.getNumberOfParameters() == 0 && fParameterInfos.isEmpty() && isVisibilitySameAsInitial() && isReturnTypeSameAsInitial())
            return true;
        if (   areNamesSameAsInitial()
	        && isOrderSameAsInitial()
    	    && isVisibilitySameAsInitial()
        	&& ! areAnyParametersDeleted()
        	&& isReturnTypeSameAsInitial()
        	&& areParameterTypesSameAsInitial())
        	return true;
       return false; 
    }

	private boolean areParameterTypesSameAsInitial() {
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (! info.isAdded() && ! info.isDeleted() && info.isTypeNameChanged())
				return false;
		}
		return true;
	}
	
	private boolean isReturnTypeSameAsInitial() throws JavaModelException {
		return fReturnTypeName.equals(getInitialReturnTypeName());
	}
	
	private boolean areAnyParametersDeleted() {
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (info.isDeleted())
				return true;
		}
		return false;
	}

	private void checkParameters(RefactoringStatus result) {
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (info.isDeleted())
				continue;
			checkParameterType(result, info);
			if (result.hasFatalError())
				return;
			result.merge(Checks.checkTempName(info.getNewName()));
			if (result.hasFatalError())
				return;
			if (info.isAdded())	
				checkParameterDefaultValue(result, info);
		}
	}
	
	private void checkReturnType(RefactoringStatus result) {
		if (! isValidTypeName(fReturnTypeName, true)){
			String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.invalid_return_type", new String[]{fReturnTypeName}); //$NON-NLS-1$
			result.addFatalError(msg);
		}	
	}

	private void checkParameterDefaultValue(RefactoringStatus result, ParameterInfo info) {
		if (info.getDefaultValue().trim().equals("")){ //$NON-NLS-1$
			String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.default_value", new String[]{info.getNewName()}); //$NON-NLS-1$
			result.addFatalError(msg);
			return;
		}	
		if (! isValidExpression(info.getDefaultValue())){
			String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.invalid_expression", new String[]{info.getDefaultValue()}); //$NON-NLS-1$
			result.addFatalError(msg);
		}	
	}

	private void checkParameterType(RefactoringStatus result, ParameterInfo info) {
		if (! info.isTypeNameChanged())
			return;
		if (info.getNewTypeName().trim().equals("")){ //$NON-NLS-1$
			String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.parameter_type", new String[]{info.getNewName()}); //$NON-NLS-1$
			result.addFatalError(msg);
			return;
		}	
		if (! isValidTypeName(info.getNewTypeName(), false)){
			String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.invalid_type_name", new String[]{info.getNewTypeName()}); //$NON-NLS-1$
			result.addFatalError(msg);
		}	
	}

	private static boolean isValidTypeName(String string, boolean isVoidAllowed){
		if ("".equals(string.trim())) //speed up for a common case //$NON-NLS-1$
			return false;
		if (! string.trim().equals(string))
			return false;
		if (PrimitiveType.toCode(string) == PrimitiveType.VOID)
			return isVoidAllowed;
		if (! Checks.checkTypeName(string).hasFatalError())
			return true;
		if (isPrimitiveTypeName(string))
			return true;
		StringBuffer cuBuff= new StringBuffer();
		cuBuff.append(CONST_CLASS_DECL);
		int offset= cuBuff.length();
		cuBuff.append(string)
			  .append(CONST_ASSIGN)
			  .append("null") //$NON-NLS-1$
			  .append(CONST_CLOSE);
		CompilationUnit cu= AST.parseCompilationUnit(cuBuff.toString().toCharArray());
		Selection selection= Selection.createFromStartLength(offset, string.length());
		SelectionAnalyzer analyzer= new SelectionAnalyzer(selection, false);
		cu.accept(analyzer);
		ASTNode selected= analyzer.getFirstSelectedNode();
		if (!(selected instanceof Type))
			return false;
		Type type= (Type)selected;
		if (isVoidArrayType(type))
			return false;
		return string.equals(cuBuff.substring(type.getStartPosition(), ASTNodes.getExclusiveEnd(type)));
	}
	
	private static boolean isVoidArrayType(Type type){
		if (! type.isArrayType())
			return false;
		
		ArrayType arrayType= (ArrayType)type;
		if (! arrayType.getComponentType().isPrimitiveType())
			return false;
		PrimitiveType primitiveType= (PrimitiveType)arrayType.getComponentType();
		return (primitiveType.getPrimitiveTypeCode() == PrimitiveType.VOID);
	}
	
	private static boolean isValidExpression(String string){
		String trimmed= string.trim();
		if ("".equals(trimmed)) //speed up for a common case //$NON-NLS-1$
			return false;
		StringBuffer cuBuff= new StringBuffer();
		cuBuff.append(CONST_CLASS_DECL)
			  .append("Object") //$NON-NLS-1$
			  .append(CONST_ASSIGN);
		int offset= cuBuff.length();
		cuBuff.append(trimmed)
			  .append(CONST_CLOSE);
		CompilationUnit cu= AST.parseCompilationUnit(cuBuff.toString().toCharArray());
		Selection selection= Selection.createFromStartLength(offset, trimmed.length());
		SelectionAnalyzer analyzer= new SelectionAnalyzer(selection, false);
		cu.accept(analyzer);
		ASTNode selected= analyzer.getFirstSelectedNode();
		return (selected instanceof Expression) && 
				trimmed.equals(cuBuff.substring(selected.getStartPosition(), ASTNodes.getExclusiveEnd(selected)));
	}

	/* non java-doc
	 * @see Refactoring#checkPreconditions(IProgressMonitor)
	 */
	public RefactoringStatus checkPreconditions(IProgressMonitor pm) throws JavaModelException{
		RefactoringStatus result= checkPreactivation();
		if (result.hasFatalError())
			return result;
		result.merge(super.checkPreconditions(pm));
		return result;
	}

	public RefactoringStatus checkPreactivation() throws JavaModelException{
		return Checks.checkAvailability(fMethod);
	}
	
	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkActivation(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask("", 2); //$NON-NLS-1$
			RefactoringStatus result= Checks.checkIfCuBroken(fMethod);
			if (result.hasFatalError())
				return result;
			IMethod orig= (IMethod)WorkingCopyUtil.getOriginal(fMethod);
			if (orig == null || ! orig.exists()){
				String message= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.method_deleted", fMethod.getCompilationUnit().getElementName());//$NON-NLS-1$
				return RefactoringStatus.createFatalErrorStatus(message);
			}
			fMethod= orig;
			
			if (MethodChecks.isVirtual(fMethod)){
				result.merge(MethodChecks.checkIfComesFromInterface(getMethod(), new SubProgressMonitor(pm, 1)));
				if (result.hasFatalError())
					return result;	
				
				result.merge(MethodChecks.checkIfOverridesAnother(getMethod(), new SubProgressMonitor(pm, 1)));
				if (result.hasFatalError())
					return result;			
			} 
			if (fMethod.getDeclaringType().isInterface()){
				result.merge(MethodChecks.checkIfOverridesAnother(getMethod(), new SubProgressMonitor(pm, 1)));
				if (result.hasFatalError())
					return result;
			}
				
			return result;
		} finally{
			pm.done();
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkInput(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask(RefactoringCoreMessages.getString("ChangeSignatureRefactoring.checking_preconditions"), 6); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();
			if (isSignatureSameAsInitial())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ChangeSignatureRefactoring.unchanged")); //$NON-NLS-1$
			result.merge(checkSignature());
			if (result.hasFatalError())
				return result;

			fRippleMethods= RippleMethodFinder.getRelatedMethods(fMethod, new SubProgressMonitor(pm, 1), null);
			fOccurrenceNodes= findOccurrenceNodes(new SubProgressMonitor(pm, 1));
			
			result.merge(checkVisibilityChanges());
					
			if (! isOrderSameAsInitial())	
				result.merge(checkReorderings(new SubProgressMonitor(pm, 1)));	
			else pm.worked(1);
			
			if (result.hasFatalError())
				return result;

			result.merge(collectAndCheckImports(new SubProgressMonitor(pm, 1)));

			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 1));

			result.merge(checkIfDeletedParametersUsed());
			if (mustAnalyzeAst()) 
				result.merge(analyzeAst()); 
			if (result.hasFatalError())
				return result;

			result.merge(validateModifiesFiles());
			return result;
		} catch (CoreException e){	
			throw new JavaModelException(e);
		} finally{
			pm.done();
		}
	}

	private RefactoringStatus collectAndCheckImports(IProgressMonitor pm) throws JavaModelException {
		RefactoringStatus result= new RefactoringStatus();
		List notDeleted= getNotDeletedInfos();
		pm.beginTask("", notDeleted.size());
		for (Iterator iter= notDeleted.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (! info.isTypeNameChanged())
				continue;
			String typeName= getSimpleParameterTypeName(info);
			if (isPrimitiveTypeName(typeName))
				continue;
			if (tryResolvingType(typeName))
				continue;
			result.merge(tryFinidingInTypeCache(typeName, info, new SubProgressMonitor(pm, 1)));
		}
		pm.done();
		return result;
	}

	//returns true iff succeeded
	private boolean tryResolvingType(String typeName) throws JavaModelException{
		String[][] fqns= getMethod().getDeclaringType().resolveType(typeName);
		if (fqns == null || fqns.length != 1)
			return false;
		String fullName= JavaModelUtil.concatenateName(fqns[0][0], fqns[0][1]);
		importToAllCusOfRippleMethods(fullName);
		return true;
	}
	
	private RefactoringStatus tryFinidingInTypeCache(String typeName, ParameterInfo info, IProgressMonitor pm) throws JavaModelException{
		List typeRefsFound= findTypeInfos(typeName, pm);

		if (typeRefsFound.size() == 0){
			String[] keys= {info.getNewTypeName()};
			String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.not_unique", keys); //$NON-NLS-1$
			return RefactoringStatus.createErrorStatus(msg);
		} else if (typeRefsFound.size() == 1){
			TypeInfo typeInfo= (TypeInfo) typeRefsFound.get(0);
			String fullName= typeInfo.getFullyQualifiedName();
			importToAllCusOfRippleMethods(fullName);
			return new RefactoringStatus();
		} else {
			Assert.isTrue(typeRefsFound.size() > 1);
			String[] keys= {info.getNewTypeName(), String.valueOf(typeRefsFound.size())};
			String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.ambiguous", keys); //$NON-NLS-1$
			return RefactoringStatus.createErrorStatus(msg);
		}
	}

	private List findTypeInfos(String typeName, IProgressMonitor pm) throws JavaModelException {
		IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaProject[]{getMethod().getJavaProject()}, true);
		IPackageFragment currPackage= getMethod().getDeclaringType().getPackageFragment();
		TypeInfo[] infos= AllTypesCache.getTypesForName(typeName, scope, pm);
		List typeRefsFound= new ArrayList();
		for (int i= 0; i < infos.length; i++) {
			TypeInfo curr= infos[i];
			IType type= curr.resolveType(scope);
			if (type != null && JavaModelUtil.isVisible(type, currPackage)) {
				typeRefsFound.add(curr);
			}
		}
		return typeRefsFound;
	}
	
	private void importToAllCusOfRippleMethods(String fullName) throws JavaModelException {
		for (int i= 0; i < fRippleMethods.length; i++) {
			ICompilationUnit wc= WorkingCopyUtil.getWorkingCopyIfExists(fRippleMethods[i].getCompilationUnit());
			fImportEditManager.addImportTo(fullName, wc);
		}
	}	
	
	private static boolean isPrimitiveTypeName(String typeName){
		return PrimitiveType.toCode(typeName) != null;
	}
	
	private static String getSimpleParameterTypeName(ParameterInfo info) {
		String typeName= info.getNewTypeName();
		if (typeName.indexOf('[') != -1)
			typeName= typeName.substring(0, typeName.indexOf('['));
		return typeName.trim();
	}
	
	private RefactoringStatus checkIfDeletedParametersUsed() {
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < fOccurrenceNodes.length; i++) {
			ASTNode methodOccurrence= fOccurrenceNodes[i];
			if (isReferenceNode(methodOccurrence))
				continue;
			ICompilationUnit cu= fAstManager.getCompilationUnit(methodOccurrence);
			MethodDeclaration decl= getMethodDeclaration(methodOccurrence);
			if (decl == null) //this can be null because of bug 27236
				continue;
			String typeName= getFullTypeName(decl);
			for (Iterator iter= getDeletedInfos().iterator(); iter.hasNext();) {
				ParameterInfo info= (ParameterInfo) iter.next();
				SingleVariableDeclaration paramDecl= (SingleVariableDeclaration) decl.parameters().get(info.getOldIndex());
				ASTNode[] paramRefs= TempOccurrenceFinder.findTempOccurrenceNodes(paramDecl, true, false);
				if (paramRefs.length > 0){
					Context context= JavaSourceContext.create(cu, paramRefs[0]);
					Object[] keys= new String[]{paramDecl.getName().getIdentifier(),
												decl.getName().getIdentifier(),
												typeName};
					String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.parameter_used", keys); //$NON-NLS-1$
					result.addWarning(msg, context);
				}
			}	
		}
		return result;
	}
	
	private String getFullTypeName(MethodDeclaration decl) {
		TypeDeclaration typeDecl= (TypeDeclaration)ASTNodes.getParent(decl, TypeDeclaration.class);
		AnonymousClassDeclaration anonymous= (AnonymousClassDeclaration)ASTNodes.getParent(decl, AnonymousClassDeclaration.class);
		if (anonymous != null && ASTNodes.isParent(typeDecl, anonymous)){
			ClassInstanceCreation cic= (ClassInstanceCreation)ASTNodes.getParent(decl, ClassInstanceCreation.class);
			return RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.anonymous_subclass", new String[]{ASTNodes.getNameIdentifier(cic.getName())}); //$NON-NLS-1$
		} else 
			return typeDecl.getName().getIdentifier();
	}
	
	private RefactoringStatus checkVisibilityChanges() throws JavaModelException {
		if (isVisibilitySameAsInitial())
			return null;
	    if (fRippleMethods.length == 1)
	    	return null;
	    Assert.isTrue(getInitialMethodVisibility() != Modifier.PRIVATE);
	    if (fVisibility == Modifier.PRIVATE)
	    	return RefactoringStatus.createWarningStatus(RefactoringCoreMessages.getString("ChangeSignatureRefactoring.non-virtual")); //$NON-NLS-1$
		return null;
	}
	
	public String getMethodSignaturePreview() throws JavaModelException{
		StringBuffer buff= new StringBuffer();
		
		buff.append(getPreviewOfVisibityString());
		if (! getMethod().isConstructor())
			buff.append(getReturnTypeString())
				.append(' ');

		buff.append(getMethod().getElementName())
			.append(Signature.C_PARAM_START)
			.append(getMethodParameters())
			.append(Signature.C_PARAM_END);
		return buff.toString();
	}

	private String getPreviewOfVisibityString() {
		String visibilityString= JdtFlags.getVisibilityString(fVisibility);
		if ("".equals(visibilityString)) //$NON-NLS-1$
			return visibilityString;
		return visibilityString + ' ';
	}

	private void checkForDuplicateNames(RefactoringStatus result){
		Set found= new HashSet();
		Set doubled= new HashSet();
		for (Iterator iter = getNotDeletedInfos().iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo)iter.next();
			String newName= info.getNewName();
			if (found.contains(newName) && !doubled.contains(newName)){
				result.addFatalError(RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.duplicate_name", newName));//$NON-NLS-1$	
				doubled.add(newName);
			} else {
				found.add(newName);
			}	
		}
	}
	
	private ICompilationUnit getCu() {
		return WorkingCopyUtil.getWorkingCopyIfExists(fMethod.getCompilationUnit());
	}
	
	private boolean mustAnalyzeAst() throws JavaModelException{
		if (JdtFlags.isAbstract(getMethod()))
			return false;
		else if (JdtFlags.isNative(getMethod()))
			return false;
		else if (getMethod().getDeclaringType().isInterface())
			return false;
		else 
			return true;
	}
	
	private RefactoringStatus analyzeAst() throws JavaModelException{		
		try {
			RefactoringStatus result= new RefactoringStatus();
						
			result.merge(checkCompilation());
			if (result.hasError())
				return result;
			return result;
		} catch(CoreException e) {
			throw new JavaModelException(e);
		}	
	}
	private RefactoringStatus checkCompilation() throws CoreException {
		ICompilationUnit cu= getCu();
		CompilationUnit compliationUnitNode= fAstManager.getAST(cu);
		TextChange change= fChangeManager.get(cu);
		String newCuSource= change.getPreviewContent();
		CompilationUnit newCUNode= AST.parseCompilationUnit(newCuSource.toCharArray(), cu.getElementName(), cu.getJavaProject());
		IProblem[] problems= RefactoringAnalyzeUtil.getIntroducedCompileProblems(newCuSource, newCUNode, compliationUnitNode);
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < problems.length; i++) {
			IProblem problem= problems[i];
			if (shouldReport(problem))
				result.addEntry(RefactoringStatusEntry.create(problem, newCuSource));
		}
		return result;
	}
		
	private boolean shouldReport(IProblem problem) {
		if (! problem.isError())
			return false;
		if (problem.getID() == IProblem.ArgumentTypeNotFound) //reported when trying to import
			return false;
		return true;	
	}

	private static String createGroupDescriptionString(String oldParamName){
		return "rename." + oldParamName; //$NON-NLS-1$
	}
	
	public String getReturnTypeString() {
		return fReturnTypeName;
	}	

	private String getMethodParameters() throws JavaModelException {
		StringBuffer buff= new StringBuffer();
		int i= 0;
		for (Iterator iter= getNotDeletedInfos().iterator(); iter.hasNext(); i++) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (i != 0 )
				buff.append(", ");  //$NON-NLS-1$
			buff.append(createDeclarationString(info));
		}
		return buff.toString();
	}
		
	private List getDeletedInfos(){
		List result= new ArrayList(1);
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (info.isDeleted())
				result.add(info);
		}
		return result;
	}
	
	private List getNotDeletedInfos(){
		List result= new ArrayList(fParameterInfos.size());
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (! info.isDeleted())
				result.add(info);
		}
		return result;
	}

	private boolean areNamesSameAsInitial() {
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (! info.getOldName().equals(info.getNewName()))
				return false;
		}
		return true;
	}

	private boolean isOrderSameAsInitial(){
		int i= 0;
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext(); i++) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (info.getOldIndex() != i)
				return false;
			if (info.isAdded())
				return false;
		}
		return true;
	}

	private RefactoringStatus checkReorderings(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask(RefactoringCoreMessages.getString("ChangeSignatureRefactoring.checking_preconditions"), 2); //$NON-NLS-1$

			RefactoringStatus result= new RefactoringStatus();
			result.merge(checkNativeMethods());
			result.merge(checkParameterNamesInRippleMethods());
			return result;
		} finally{
			pm.done();
		}	
	}
	
	private RefactoringStatus checkParameterNamesInRippleMethods() throws JavaModelException {
		RefactoringStatus result= new RefactoringStatus();
		Set newParameterNames= getNewParameterNamesList();
		for (int i= 0; i < fRippleMethods.length; i++) {
			String[] paramNames= fRippleMethods[i].getParameterNames();
			for (int j= 0; j < paramNames.length; j++) {
				if (newParameterNames.contains(paramNames[j])){
					String[] args= new String[]{JavaElementUtil.createMethodSignature(fRippleMethods[i]), paramNames[j]};
					String msg= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.already_has", args); //$NON-NLS-1$
					Context context= JavaSourceContext.create(fRippleMethods[i].getCompilationUnit(), fRippleMethods[i].getNameRange());
					result.addError(msg, context);
				}	
			}
		}
		return result;
	}
	
	private Set getNewParameterNamesList() {
		Set oldNames= getOriginalParameterNames();
		Set currentNames= getNamesOfNotDeletedParameters();
		currentNames.removeAll(oldNames);
		return currentNames;
	}
	
	private Set getNamesOfNotDeletedParameters() {
		Set result= new HashSet();
		for (Iterator iter= getNotDeletedInfos().iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			result.add(info.getNewName());
		}
		return result;
	}
	
	private Set getOriginalParameterNames() {
		Set result= new HashSet();
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (! info.isAdded())
				result.add(info.getOldName());
		}
		return result;
	}
	
	private RefactoringStatus checkNativeMethods() throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < fRippleMethods.length; i++) {
			if (JdtFlags.isNative(fRippleMethods[i])){
				String message= RefactoringCoreMessages.getFormattedString("ChangeSignatureRefactoring.native", //$NON-NLS-1$
					new String[]{JavaElementUtil.createMethodSignature(fRippleMethods[i]), JavaModelUtil.getFullyQualifiedName(fRippleMethods[i].getDeclaringType())});
				result.addError(message, JavaSourceContext.create(fRippleMethods[i]));			
			}								
		}
		return result;
	}

	private IFile[] getAllFilesToModify() throws CoreException{
		return ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
	}
	
	private RefactoringStatus validateModifiesFiles() throws CoreException{
		return Checks.validateModifiesFiles(getAllFilesToModify());
	}

	//--  changes ----
	public IChange createChange(IProgressMonitor pm) throws JavaModelException {
		pm.beginTask("", 1); //$NON-NLS-1$
		try{
			return new CompositeChange(RefactoringCoreMessages.getString("ChangeSignatureRefactoring.restructure_parameters"), fChangeManager.getAllChanges()); //$NON-NLS-1$
		} finally{
			pm.done();
		}	
	}

	private TextChangeManager createChangeManager(IProgressMonitor pm) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.getString("ChangeSignatureRefactoring.preview"), 2); //$NON-NLS-1$

		if (! areNamesSameAsInitial())
			addRenamings();
		modifyMethodOccurrences(new SubProgressMonitor(pm, 1));
		if (isNoArgConstructor())
			modifyImplicitCallsToNoArgConstructor(new SubProgressMonitor(pm, 1));
		
		TextChangeManager manager= new TextChangeManager();
		fillWithRewriteEdits(manager);
		pm.done();
		return manager;
	}	
	
	private void modifyImplicitCallsToNoArgConstructor(IProgressMonitor pm) throws JavaModelException {
		TypeDeclaration[] subclassNodes= getSubclassNodes(fMethod.getDeclaringType(), pm);
		for (int i= 0; i < subclassNodes.length; i++) {
			modifyImplicitCallsToNoArgConstructor(subclassNodes[i]);
		}
	}
	
	private void modifyImplicitCallsToNoArgConstructor(TypeDeclaration subclass) {
		MethodDeclaration[] constructors= getAllConstructors(subclass);
		if (constructors.length == 0){
			addNewConstructorToSubclass(subclass);
		} else {
			ASTRewrite rewrite= getRewrite(subclass);
			for (int i= 0; i < constructors.length; i++) {
				if (containsImplicitCallToSuperConstructor(constructors[i])){
					SuperConstructorInvocation superCall= addExplicitSuperConstructorCall(constructors[i], rewrite);
					rewrite.markAsInserted(superCall);
				}	
			}
		}
	}
	
	private SuperConstructorInvocation addExplicitSuperConstructorCall(MethodDeclaration constructor, ASTRewrite rewrite) {
		SuperConstructorInvocation superCall= constructor.getAST().newSuperConstructorInvocation();
		addArgumentsToNewCuperConstructorCall(superCall, rewrite);
		constructor.getBody().statements().add(0, superCall);
		return superCall;
	}
	
	private void addArgumentsToNewCuperConstructorCall(SuperConstructorInvocation superCall, ASTRewrite rewrite) {
		int i= 0;
		for (Iterator iter= getNotDeletedInfos().iterator(); iter.hasNext(); i++) {
			ParameterInfo info= (ParameterInfo) iter.next();
			superCall.arguments().add(i, createNewExpression(rewrite, info));
		}
	}
	
	private boolean containsImplicitCallToSuperConstructor(MethodDeclaration constructor) {
		Assert.isTrue(constructor.isConstructor());
		Block body= constructor.getBody();
		if (body == null)
			return false;
		if (body.statements().size() == 0)
			return true;
		if (body.statements().get(0) instanceof ConstructorInvocation)
			return false;
		if (body.statements().get(0) instanceof SuperConstructorInvocation)
			return false;
		return true;
	}
	
	private void addNewConstructorToSubclass(TypeDeclaration subclass) {
		AST ast= subclass.getAST();
		MethodDeclaration newConstructor= ast.newMethodDeclaration();
		newConstructor.setName(ast.newSimpleName(subclass.getName().getIdentifier()));
		newConstructor.setConstructor(true);
		newConstructor.setBody(ast.newBlock());
		newConstructor.setExtraDimensions(0);
		newConstructor.setJavadoc(null);
		newConstructor.setModifiers(getAccessModifier(subclass));
		newConstructor.setReturnType(ast.newPrimitiveType(PrimitiveType.VOID));
		
		addExplicitSuperConstructorCall(newConstructor, getRewrite(subclass));
		subclass.bodyDeclarations().add(0, newConstructor); // ok to add as first ???
		getRewrite(subclass).markAsInserted(newConstructor);
	}
	
	private static int getAccessModifier(TypeDeclaration subclass) {
		int modifiers= subclass.getModifiers();
		if (Modifier.isPublic(modifiers))
			return Modifier.PUBLIC;
		else if (Modifier.isProtected(modifiers))
			return Modifier.PROTECTED;
		else if (Modifier.isPrivate(modifiers))
			return Modifier.PRIVATE;
		else
			return Modifier.NONE;
	}
	
	private MethodDeclaration[] getAllConstructors(TypeDeclaration typeDeclaration) {
		MethodDeclaration[] methods= typeDeclaration.getMethods();
		List result= new ArrayList(1);
		for (int i= 0; i < methods.length; i++) {
			if (methods[i].isConstructor())
				result.add(methods[i]);
		}
		return (MethodDeclaration[]) result.toArray(new MethodDeclaration[result.size()]);
	}
	
	private TypeDeclaration[] getSubclassNodes(IType iType, IProgressMonitor pm) throws JavaModelException {
		IType[] subclasses= getSubclasses(iType, pm);
		List result= new ArrayList(subclasses.length);
		for(int i= 0; i < subclasses.length; i++){
			TypeDeclaration td= ASTNodeSearchUtil.getTypeDeclarationNode(subclasses[i], fAstManager);
			if (td != null)
				result.add(td);
		}
		return (TypeDeclaration[]) result.toArray(new TypeDeclaration[result.size()]);
	}
	
	private IType[] getSubclasses(IType iType, IProgressMonitor pm) throws JavaModelException {
		return iType.newTypeHierarchy(pm).getSubclasses(iType);
	}
	
	private boolean isNoArgConstructor() throws JavaModelException {
		return fMethod.isConstructor() && fMethod.getNumberOfParameters() == 0;
	}

	private void fillWithRewriteEdits(TextChangeManager manager) throws JavaModelException, CoreException {
		CompilationUnit[] cuNodes= fRewriteManager.getAllCompilationUnitNodes();
		for (int i= 0; i < cuNodes.length; i++) {
			CompilationUnit cuNode= cuNodes[i];
			ASTRewrite rewrite= fRewriteManager.getRewrite(cuNode);
			TextBuffer textBuffer= TextBuffer.create(fAstManager.getCompilationUnit(cuNode).getBuffer().getContents());
			TextEdit resultingEdits= new MultiTextEdit();
			rewrite.rewriteNode(textBuffer, resultingEdits, fDescriptionGroups);

			ICompilationUnit cu= fAstManager.getCompilationUnit(cuNode);
			TextChange textChange= manager.get(fAstManager.getCompilationUnit(cuNode));
			if (fImportEditManager.hasImportEditFor(cu))
				resultingEdits.add(fImportEditManager.getImportEdit(cu));
			textChange.addTextEdit(RefactoringCoreMessages.getString("ChangeSignatureRefactoring.modify_parameters"), resultingEdits); //$NON-NLS-1$
			rewrite.removeModifications();

		}
	}

	private void modifyMethodOccurrences(IProgressMonitor pm) throws JavaModelException{
		pm.beginTask("", fOccurrenceNodes.length); //$NON-NLS-1$
		try{
			for (int i= 0; i < fOccurrenceNodes.length; i++) {
				ASTNode methodOccurrence= fOccurrenceNodes[i];
				if (isReferenceNode(methodOccurrence))
					updateReferenceNode(methodOccurrence);
				else	
					updateDeclarationNode(methodOccurrence);
				pm.worked(1);	
			}
		} finally{
			pm.done();
		}
	}
	
	private void updateDeclarationNode(ASTNode methodOccurrence) throws JavaModelException {
		MethodDeclaration methodDeclaration= getMethodDeclaration(methodOccurrence);
		if (methodDeclaration == null) //can be null - see bug 27236
			return;
		if (! methodDeclaration.isConstructor())
			changeReturnType(methodDeclaration);
		changeParameterTypes(methodDeclaration);
				
		if (needsVisibilityUpdate(methodDeclaration))
			changeVisibility(methodDeclaration);
		reshuffleElements(methodOccurrence, methodDeclaration.parameters());
	}
	
	private void changeParameterTypes(MethodDeclaration methodDeclaration) {
		for (Iterator iter= fParameterInfos.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if (! info.isAdded() && ! info.isDeleted() && info.isTypeNameChanged()){
				SingleVariableDeclaration oldParam= (SingleVariableDeclaration)methodDeclaration.parameters().get(info.getOldIndex());
				replaceTypeNode(oldParam.getType(), info.getNewTypeName());
			}
		}
	}

	private void replaceTypeNode(Type typeNode, String newTypeName){
		Type newParamType= (Type)getRewrite(typeNode).createPlaceholder(newTypeName, ASTRewrite.TYPE);
		getRewrite(typeNode).markAsReplaced(typeNode, newParamType);
	}

	private ASTRewrite getRewrite(ASTNode node){
		return fRewriteManager.getRewrite(ASTNodeMappingManager.getCompilationUnitNode(node));
	}

	private void changeReturnType(MethodDeclaration methodDeclaration) {
		replaceTypeNode(methodDeclaration.getReturnType(), fReturnTypeName);
	}

	private void changeVisibility(MethodDeclaration methodDeclaration) throws JavaModelException {
		MethodDeclaration modifierMethodDeclaration= methodDeclaration.getAST().newMethodDeclaration();
		modifierMethodDeclaration.setModifiers(getNewModifiers(methodDeclaration));
		modifierMethodDeclaration.setExtraDimensions(methodDeclaration.getExtraDimensions());
		ASTRewrite rewrite= getRewrite(methodDeclaration);
		rewrite.markAsModified(methodDeclaration, modifierMethodDeclaration);
	}

	private void updateReferenceNode(ASTNode methodOccurrence) {		
		reshuffleElements(methodOccurrence, getArguments(methodOccurrence));
	}
	
	private void reshuffleElements(ASTNode methodOccurrence, List elementList) {
		ASTRewrite rewrite= getRewrite(methodOccurrence);
		AST ast= methodOccurrence.getAST();
		boolean isReference= isReferenceNode(methodOccurrence);
		ASTNode[] nodes= getSubNodesOfMethodOccurrenceNode(methodOccurrence);
		deleteExcesiveElements(rewrite, nodes);
		
		List nonDeletedInfos= getNotDeletedInfos();
		ASTNode[] newPermutation= new ASTNode[nonDeletedInfos.size()];
		for (int i=0; i < newPermutation.length; i++) {
			ParameterInfo info= (ParameterInfo) nonDeletedInfos.get(i);
			
			if (info.isAdded())
				newPermutation[i]= createNewElementForList(rewrite, ast, info, isReference);
			 else if (info.getOldIndex() != i)
			 	if (rewrite.isReplaced(nodes[info.getOldIndex()]))
					newPermutation[i]= rewrite.getReplacingNode(nodes[info.getOldIndex()]);
			 	else
					newPermutation[i]= rewrite.createCopy(nodes[info.getOldIndex()]);
			 else
			 	newPermutation[i]= nodes[i];
		}
		for (int i= 0; i < Math.min(nodes.length, newPermutation.length); i++) {
			if (nodes[i] != newPermutation[i])
				rewrite.markAsReplaced(nodes[i], newPermutation[i]);
		}
		for (int i= nodes.length; i < newPermutation.length; i++) {
			ParameterInfo info= (ParameterInfo) nonDeletedInfos.get(i);
			if (info.isAdded()){
				ASTNode newElement= createNewElementForList(rewrite, ast, info, isReference);
				elementList.add(i, newElement);
				rewrite.markAsInserted(newElement);
			} else {
				elementList.add(i, newPermutation[i]);
				rewrite.markAsInserted(newPermutation[i]);
			}	
		}
	}

	private void deleteExcesiveElements(ASTRewrite rewrite, ASTNode[] nodes) {
		for (int i= getNotDeletedInfos().size(); i < nodes.length; i++) {
			rewrite.markAsRemoved(nodes[i]);
		}
	}

	private ASTNode createNewElementForList(ASTRewrite rewrite, AST ast, ParameterInfo info, boolean isReferenceNode){
		if (isReferenceNode)
			return createNewExpression(rewrite, info);
		else
			return createNewSingleVariableDeclaration(rewrite, ast, info);	
	}
	
	private static SingleVariableDeclaration createNewSingleVariableDeclaration(ASTRewrite rewrite, AST ast, ParameterInfo info) {
		SingleVariableDeclaration newP= ast.newSingleVariableDeclaration();
		newP.setName(ast.newSimpleName(info.getNewName()));
		newP.setType((Type)rewrite.createPlaceholder(info.getNewTypeName(), ASTRewrite.TYPE));
		return newP;
	}
	
	private static Expression createNewExpression(ASTRewrite rewrite, ParameterInfo info) {
		return (Expression)rewrite.createPlaceholder(info.getDefaultValue(), ASTRewrite.EXPRESSION);
	}

	private int getNewModifiers(MethodDeclaration md) {
		return ASTNodes.clearAccessModifiers(md.getModifiers()) | fVisibility;
	}
	
	private IMethod getMethod(MethodDeclaration methodDeclaration) throws JavaModelException{
		return (IMethod)fAstManager.getCompilationUnit(methodDeclaration).getElementAt(methodDeclaration.getName().getStartPosition());
	}
	
	private boolean needsVisibilityUpdate(MethodDeclaration methodDeclaration) throws JavaModelException {
		return needsVisibilityUpdate(getMethod(methodDeclaration));
	}
	
	private boolean needsVisibilityUpdate(IMethod method) throws JavaModelException {
		if (isVisibilitySameAsInitial())
			return false;
		if (isIncreasingVisibility())
			return JdtFlags.isHigherVisibility(fVisibility, JdtFlags.getVisibilityCode(method));
		else
			return JdtFlags.isHigherVisibility(JdtFlags.getVisibilityCode(method), fVisibility);
	}

	private boolean isIncreasingVisibility() throws JavaModelException{
		return JdtFlags.isHigherVisibility(fVisibility, JdtFlags.getVisibilityCode(fMethod));
	}
	
	private boolean isVisibilitySameAsInitial() throws JavaModelException {
		return fVisibility == JdtFlags.getVisibilityCode(fMethod);
	}
	
	private ParameterInfo[] getRenamedParameterNames(){
		List result= new ArrayList();
		for (Iterator iterator = getParameterInfos().iterator(); iterator.hasNext();) {
			ParameterInfo info= (ParameterInfo)iterator.next();
			if (! info.isAdded() && ! info.getOldName().equals(info.getNewName()))
				result.add(info);
		}
		return (ParameterInfo[]) result.toArray(new ParameterInfo[result.size()]);
	}

	private IJavaSearchScope createRefactoringScope()  throws JavaModelException{
		if (fRippleMethods.length == 1)	
			return RefactoringScopeFactory.create(fRippleMethods[0]);
		return SearchEngine.createWorkspaceScope();
	}
	
	private ASTNode[] findOccurrenceNodes(IProgressMonitor pm) throws JavaModelException{
		if (fMethod.isConstructor())
			return ConstructorReferenceFinder.getConstructorOccurrenceNodes(fMethod, fAstManager, pm);
		else	
			return ASTNodeSearchUtil.findOccurrenceNodes(fRippleMethods, fAstManager, pm, createRefactoringScope());
	}
	
	private void addRenamings() throws JavaModelException {
		MethodDeclaration methodDeclaration= ASTNodeSearchUtil.getMethodDeclarationNode(fMethod, fAstManager);
		ParameterInfo[] infos= getRenamedParameterNames();
		ICompilationUnit cu= WorkingCopyUtil.getWorkingCopyIfExists(fMethod.getCompilationUnit());
		if (cu == null)
			return;
		
		for (int i= 0; i < infos.length; i++) {
			ParameterInfo info= infos[i];
			SingleVariableDeclaration param= (SingleVariableDeclaration)methodDeclaration.parameters().get(info.getOldIndex());
			ASTNode[] paramOccurrences= TempOccurrenceFinder.findTempOccurrenceNodes(param, true, true);
			for (int j= 0; j < paramOccurrences.length; j++) {
				ASTNode occurence= paramOccurrences[j];
				if (occurence instanceof SimpleName){
					SimpleName newName= occurence.getAST().newSimpleName(info.getNewName());
					fRewriteManager.getRewrite(cu).markAsReplaced(occurence, newName, createGroupDescriptionString(info.getOldName()));
				}
			}
		}
	}
	
	private ASTNode[] getSubNodesOfMethodOccurrenceNode(ASTNode occurrenceNode) {
		if (isReferenceNode(occurrenceNode))
			return (Expression[])getArguments(occurrenceNode).toArray(new Expression[getArguments(occurrenceNode).size()]);
		else
			return getSubNodesOfMethodDeclarationNode(occurrenceNode);
	}

	private SingleVariableDeclaration[] getSubNodesOfMethodDeclarationNode(ASTNode occurrenceNode) {
		Assert.isTrue(! isReferenceNode(occurrenceNode));
		MethodDeclaration methodDeclaration= getMethodDeclaration(occurrenceNode);
		if (methodDeclaration == null) //can be null - see bug 27236
			return new SingleVariableDeclaration[0];
		return (SingleVariableDeclaration[]) methodDeclaration.parameters().toArray(new SingleVariableDeclaration[methodDeclaration.parameters().size()]);
	}
	
	private static MethodDeclaration getMethodDeclaration(ASTNode node){
		return (MethodDeclaration)ASTNodes.getParent(node, MethodDeclaration.class);
	}

	private static String createDeclarationString(ParameterInfo info) {
		return info.getNewTypeName() + " " + info.getNewName(); //$NON-NLS-1$
	}

	private static List getArguments(ASTNode node) {
		if (node instanceof SimpleName && node.getParent() instanceof MethodInvocation)
			return ((MethodInvocation)node.getParent()).arguments();
			
		if (node instanceof SimpleName && node.getParent() instanceof SuperMethodInvocation)
			return ((SuperMethodInvocation)node.getParent()).arguments();
			
		if (node instanceof SimpleName && node.getParent() instanceof ClassInstanceCreation)
			return ((ClassInstanceCreation)node.getParent()).arguments();
			
		if (node instanceof ExpressionStatement && isReferenceNode(((ExpressionStatement)node).getExpression()))
			return getArguments(((ExpressionStatement)node).getExpression());
			
		if (node instanceof MethodInvocation)	
			return ((MethodInvocation)node).arguments();
			
		if (node instanceof SuperMethodInvocation)	
			return ((SuperMethodInvocation)node).arguments();
			
		if (node instanceof ClassInstanceCreation)	
			return ((ClassInstanceCreation)node).arguments();
			
		if (node instanceof ConstructorInvocation)	
			return ((ConstructorInvocation)node).arguments();
			
		if (node instanceof SuperConstructorInvocation)	
			return ((SuperConstructorInvocation)node).arguments();
			
		return null;	
	}
	
	private static boolean isReferenceNode(ASTNode node){
		if (node instanceof SimpleName && node.getParent() instanceof MethodInvocation)
			return true;
		if (node instanceof SimpleName && node.getParent() instanceof SuperMethodInvocation)
			return true;
		if (node instanceof SimpleName && node.getParent() instanceof ClassInstanceCreation)
			return true;
		if (node instanceof ExpressionStatement && isReferenceNode(((ExpressionStatement)node).getExpression()))
			return true;
		if (node instanceof MethodInvocation)	
			return true;
		if (node instanceof SuperMethodInvocation)	
			return true;
		if (node instanceof ClassInstanceCreation)	
			return true;
		if (node instanceof ConstructorInvocation)	
			return true;
		if (node instanceof SuperConstructorInvocation)	
			return true;
		return false;	
	}
}