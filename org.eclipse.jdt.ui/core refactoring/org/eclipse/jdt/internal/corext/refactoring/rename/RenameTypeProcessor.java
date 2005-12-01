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
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.text.edits.ReplaceEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.IDerivedElementRefactoringProcessor;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.GenericRefactoringArguments;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.ltk.core.refactoring.participants.ValidateEditChecker;
import org.eclipse.osgi.util.NLS;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeReferenceMatch;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameResourceChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.participants.ResourceModifications;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IDerivedElementUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IQualifiedNameUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.Changes;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.QualifiedNameFinder;
import org.eclipse.jdt.internal.corext.refactoring.util.QualifiedNameSearchResult;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class RenameTypeProcessor extends JavaRenameProcessor implements ITextUpdating, IReferenceUpdating, IQualifiedNameUpdating, IDerivedElementUpdating, IDerivedElementRefactoringProcessor {

	private static final String ID_RENAME_TYPE= "org.eclipse.jdt.ui.rename.type"; //$NON-NLS-1$
	private static final String ATTRIBUTE_QUALIFIED= "qualified"; //$NON-NLS-1$
	private static final String ATTRIBUTE_REFERENCES= "references"; //$NON-NLS-1$
	private static final String ATTRIBUTE_TEXTUAL_MATCHES= "textual"; //$NON-NLS-1$
	private static final String ATTRIBUTE_PATTERNS= "patterns"; //$NON-NLS-1$
	private static final String ATTRIBUTE_DERIVED= "derived"; //$NON-NLS-1$
	private static final String ATTRIBUTE_DERIVED_MATCHING_STRATEGY= "matchstrategy"; //$NON-NLS-1$
	
    public static final GroupCategorySet CATEGORY_TYPE_RENAME= new GroupCategorySet(new GroupCategory("org.eclipse.jdt.internal.corext.refactoring.rename.renameType.type", RefactoringCoreMessages.RenameTypeProcessor_changeCategory_type, RefactoringCoreMessages.RenameTypeProcessor_changeCategory_type_description)); //$NON-NLS-1$
    public static final GroupCategorySet CATEGORY_METHOD_RENAME= new GroupCategorySet(new GroupCategory("org.eclipse.jdt.internal.corext.refactoring.rename.renameType.method", RefactoringCoreMessages.RenameTypeProcessor_changeCategory_method, RefactoringCoreMessages.RenameTypeProcessor_changeCategory_method_description)); //$NON-NLS-1$
    public static final GroupCategorySet CATEGORY_FIELD_RENAME= new GroupCategorySet(new GroupCategory("org.eclipse.jdt.internal.corext.refactoring.rename.renameType.field", RefactoringCoreMessages.RenameTypeProcessor_changeCategory_fields, RefactoringCoreMessages.RenameTypeProcessor_changeCategory_fields_description)); //$NON-NLS-1$ 
    public static final GroupCategorySet CATEGORY_LOCAL_RENAME= new GroupCategorySet(new GroupCategory("org.eclipse.jdt.internal.corext.refactoring.rename.renameType.local", RefactoringCoreMessages.RenameTypeProcessor_changeCategory_local_variables, RefactoringCoreMessages.RenameTypeProcessor_changeCategory_local_variables_description)); //$NON-NLS-1$			
    
	private IType fType;
	private SearchResultGroup[] fReferences;
	private TextChangeManager fChangeManager;
	private QualifiedNameSearchResult fQualifiedNameSearchResult;
	
	private boolean fUpdateReferences;
	
	private boolean fUpdateTextualMatches;

	private boolean fUpdateQualifiedNames;
	private String fFilePatterns;

	public static final String IDENTIFIER= "org.eclipse.jdt.ui.renameTypeProcessor"; //$NON-NLS-1$
	
	// --- Derived

	private boolean fUpdateDerivedElements;
	private Map/* <IJavaElement, String> */fFinalDerivedElementToName= null;
	private int fRenamingStrategy;

	// Preloaded information for the UI.
	private LinkedHashMap/* <IJavaElement, String> */fPreloadedElementToName= null;
	private Map/* <IJavaElement, Boolean> */fPreloadedElementToSelection= null;
	private LinkedHashMap/* <IJavaElement, String> */fPreloadedElementToNameDefault= null;

	// Cache information to decide whether to
	// re-update references and preload info
	private String fCachedNewName= null;
	private boolean fCachedRenameDerivedElements= false;
	private int fCachedRenamingStrategy= -1;
	private RefactoringStatus fCachedRefactoringStatus= null;

	private class NoOverrideProgressMonitor extends SubProgressMonitor {

		public NoOverrideProgressMonitor(IProgressMonitor monitor, int ticks) {
			super(monitor, ticks, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL);
		}

		public void setTaskName(String name) {
			// do nothing
		}
	}

	public RenameTypeProcessor(IType type) {
		fType= type;
		if (type != null)
			setNewElementName(type.getElementName());
		fUpdateReferences= true; //default is yes
		fUpdateTextualMatches= false;
		fUpdateDerivedElements= false; // default is no
		fRenamingStrategy= RenamingNameSuggestor.STRATEGY_EXACT;
	}
	
	public IType getType() {
		return fType;
	}

	//---- IRefactoringProcessor ---------------------------------------------------

	public String getIdentifier() {
		return IDENTIFIER;
	}
	
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameAvailable(fType);
	}
	 
	public String getProcessorName() {
		return Messages.format(
			RefactoringCoreMessages.RenameTypeRefactoring_name,  
			new String[]{JavaModelUtil.getFullyQualifiedName(fType), getNewElementName()});
	}
	
	protected String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fType);
	}

	public Object[] getElements() {
		return new Object[] {fType};
	}
	
	protected void loadDerivedParticipants(RefactoringStatus status, List result, String[] natures, SharableParticipants shared) throws CoreException {
		String newCUName= getNewCompilationUnit().getElementName();
		RenameArguments arguments= new RenameArguments(newCUName, getUpdateReferences(), getUpdateDerivedElements());
		loadDerivedParticipants(status, result, 
			computeDerivedElements(), arguments, 
			computeResourceModifications(), natures, shared);
	}
	
	private Object[] computeDerivedElements() {
		if (! isPrimaryType())
			return new Object[0];
		return new Object[] { fType.getCompilationUnit() };
	}

	private ResourceModifications computeResourceModifications() {
		if (! isPrimaryType())
			return null;
		ICompilationUnit cu= fType.getCompilationUnit();
		IResource resource= cu.getResource();
		if (resource == null)
			return null;
		ResourceModifications result= new ResourceModifications();
		String renamedCUName= JavaModelUtil.getRenamedCUName(cu, getNewElementName());
		result.setRename(resource, new RenameArguments(renamedCUName, getUpdateReferences()));
		return result;		
	}
		
	/*
	 * Note: this is a handle-only method!
	 */
	private boolean isPrimaryType() {
		String cuName= fType.getCompilationUnit().getElementName();
		String typeName= fType.getElementName();
		return Checks.isTopLevel(fType) && JavaCore.removeJavaLikeExtension(cuName).equals(typeName);
	}
	
	//---- IRenameProcessor ----------------------------------------------
	
	public String getCurrentElementName(){
		return fType.getElementName();
	}
	
	public String getCurrentElementQualifier(){
		return JavaModelUtil.getTypeContainerName(fType);
	}
	
	public RefactoringStatus checkNewElementName(String newName){
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= Checks.checkTypeName(newName);
		if (Checks.isAlreadyNamed(fType, newName))
			result.addFatalError(RefactoringCoreMessages.RenameTypeRefactoring_choose_another_name);	 
		return result;
	}
	
	public Object getNewElement() {
		if (Checks.isTopLevel(fType)) {
			return getNewCompilationUnit().getType(getNewElementName());
		} else {
			return fType.getDeclaringType().getType(getNewElementName());
		}
	}

	private ICompilationUnit getNewCompilationUnit() {
		ICompilationUnit cu= fType.getCompilationUnit();
		if (isPrimaryType()) {
			IPackageFragment parent= fType.getPackageFragment();
			String renamedCUName= JavaModelUtil.getRenamedCUName(cu, getNewElementName());
			return parent.getCompilationUnit(renamedCUName);
		} else {
			return cu;
		}
	}

	//---- ITextUpdating -------------------------------------------------

	public boolean canEnableTextUpdating() {
		return true;
	}
	
	public boolean getUpdateTextualMatches() {
		return fUpdateTextualMatches;
	}
	public void setUpdateTextualMatches(boolean update) {
		fUpdateTextualMatches= update;
	}

	//---- IReferenceUpdating --------------------------------------
		
	public void setUpdateReferences(boolean update){
		fUpdateReferences= update;
	}
	
	public boolean canEnableUpdateReferences(){
		return true;
	}
	
	public boolean getUpdateReferences(){
		return fUpdateReferences;
	}

	//---- IQualifiedNameUpdating ----------------------------------

	public boolean canEnableQualifiedNameUpdating() {
		return !fType.getPackageFragment().isDefaultPackage() && !(fType.getParent() instanceof IType);
	}
	
	public boolean getUpdateQualifiedNames() {
		return fUpdateQualifiedNames;
	}
	
	public void setUpdateQualifiedNames(boolean update) {
		fUpdateQualifiedNames= update;
	}
	
	public String getFilePatterns() {
		return fFilePatterns;
	}
	
	public void setFilePatterns(String patterns) {
		Assert.isNotNull(patterns);
		fFilePatterns= patterns;
	}
	
	//------------- Conditions -----------------
	
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		IType orig= (IType)WorkingCopyUtil.getOriginal(fType);
		if (orig == null || ! orig.exists()){
			String message= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_does_not_exist, 
						new String[]{JavaModelUtil.getFullyQualifiedName(fType), fType.getCompilationUnit().getElementName()});
			return RefactoringStatus.createFatalErrorStatus(message);
		}	
		fType= orig;
		
		return Checks.checkIfCuBroken(fType);
	}

	/* non java-doc
	 * @see Refactoring#checkInput
	 */		
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		Assert.isNotNull(fType, "type"); //$NON-NLS-1$
		Assert.isNotNull(getNewElementName(), "newName"); //$NON-NLS-1$
		RefactoringStatus result= new RefactoringStatus();
		
		int referenceSearchTicks= fUpdateReferences || fUpdateDerivedElements ? 15 : 0;
		int affectedCusTicks= fUpdateReferences || fUpdateDerivedElements ? 10 : 1;
		int derivedTicks= fUpdateDerivedElements ? 85 : 0;
		int createChangeTicks = 5;
		int qualifiedNamesTicks= fUpdateQualifiedNames ? 50 : 0;
		
		try{
			pm.beginTask("", 12 + referenceSearchTicks + affectedCusTicks + derivedTicks + createChangeTicks + qualifiedNamesTicks); //$NON-NLS-1$
			pm.setTaskName(RefactoringCoreMessages.RenameTypeRefactoring_checking);

			fChangeManager= new TextChangeManager(true);
			
			result.merge(checkNewElementName(getNewElementName()));
			if (result.hasFatalError())
				return result;
			result.merge(Checks.checkIfCuBroken(fType));
			if (result.hasFatalError())
				return result;
			pm.worked(1);
		
			result.merge(checkTypesInCompilationUnit());
			pm.worked(1);
		
			result.merge(checkForMethodsWithConstructorNames());
			pm.worked(1);
		
			result.merge(checkImportedTypes());	
			pm.worked(1);
		
			if (Checks.isTopLevel(fType) && (JdtFlags.isPublic(fType)))
				result.merge(Checks.checkCompilationUnitNewName(fType.getCompilationUnit(), getNewElementName()));
			pm.worked(1);	
			
			if (isPrimaryType())
				result.merge(checkNewPathValidity());
			pm.worked(1);	
			
			result.merge(checkEnclosingTypes());
			pm.worked(1);	
			
			result.merge(checkEnclosedTypes());
			pm.worked(1);	
			
			result.merge(checkTypesInPackage());
			pm.worked(1);	
			
			result.merge(checkTypesImportedInCu());
			pm.worked(1);	
		
			result.merge(Checks.checkForMainAndNativeMethods(fType));
			pm.worked(1);	
		
			// before doing any expensive analysis
			if (result.hasFatalError())
				return result;
							
			result.merge(analyseEnclosedTypes());
			pm.worked(1);
			// before doing _the really_ expensive analysis
			if (result.hasFatalError())
				return result;
			
			// Load references, including derived elements
			if (fUpdateReferences || fUpdateDerivedElements) {
				pm.setTaskName(RefactoringCoreMessages.RenameTypeRefactoring_searching);
				result.merge(initializeReferences(new SubProgressMonitor(pm, referenceSearchTicks)));
			} else {
				fReferences= new SearchResultGroup[0];
			}
	
			pm.setTaskName(RefactoringCoreMessages.RenameTypeRefactoring_checking); 
			if (pm.isCanceled())
				throw new OperationCanceledException();
			
			if (fUpdateReferences || fUpdateDerivedElements) {
				result.merge(analyzeAffectedCompilationUnits(new SubProgressMonitor(pm, affectedCusTicks)));
			} else {
				Checks.checkCompileErrorsInAffectedFile(result, fType.getResource());
				pm.worked(affectedCusTicks);
			}
			
			if (result.hasFatalError())
				return result;
			
			if (fUpdateDerivedElements) {
				result.merge(initializeDerivedRenameProcessors(new SubProgressMonitor(pm, derivedTicks), context));
				if (result.hasFatalError())
					return result;
			}

			createChanges(new SubProgressMonitor(pm, createChangeTicks));
	
			if (fUpdateQualifiedNames)			
				computeQualifiedNameMatches(new SubProgressMonitor(pm, qualifiedNamesTicks));
	
			ValidateEditChecker checker= (ValidateEditChecker)context.getChecker(ValidateEditChecker.class);
			checker.addFiles(getAllFilesToModify());
			return result;
		} finally {
			pm.done();
		}	
	}
	
	/**
	 * Initializes the references to the type and the derived elements. This
	 * method creates both the fReferences and the fPreloadedDerivedElements
	 * fields.
	 * 
	 * May be called from the UI.
	 * 
	 * @throws JavaModelException some fundamental error with the underlying model
	 * @throws OperationCanceledException if user canceled the task
	 * 
	 */
	public RefactoringStatus initializeReferences(IProgressMonitor monitor) throws JavaModelException, OperationCanceledException {

		Assert.isNotNull(fType);
		Assert.isNotNull(getNewElementName());

		// Do not search again if the preconditions have not changed.
		// Search depends on the type, the new name, the derived elements, and
		// the strategy

		if (fReferences != null && (getNewElementName().equals(fCachedNewName)) && (fCachedRenameDerivedElements == getUpdateDerivedElements()) && (fCachedRenamingStrategy == fRenamingStrategy))
			return fCachedRefactoringStatus;

		fCachedNewName= getNewElementName();
		fCachedRenameDerivedElements= fUpdateDerivedElements;
		fCachedRenamingStrategy= fRenamingStrategy;
		fCachedRefactoringStatus= new RefactoringStatus();

		
		try {
			fReferences= RefactoringSearchEngine.search(SearchPattern.createPattern(fType, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE), RefactoringScopeFactory
					.create(fType), monitor, fCachedRefactoringStatus);
			fReferences= Checks.excludeCompilationUnits(fReferences, fCachedRefactoringStatus);

			fPreloadedElementToName= new LinkedHashMap();
			fPreloadedElementToSelection= new HashMap();

			final String unQualifiedTypeName= fType.getElementName();

			monitor.beginTask("", fReferences.length); //$NON-NLS-1$

			if (getUpdateDerivedElements()) {

				RenamingNameSuggestor sugg= new RenamingNameSuggestor(fRenamingStrategy);

				for (int i= 0; i < fReferences.length; i++) {
					final ICompilationUnit cu= fReferences[i].getCompilationUnit();
					if (cu == null)
						continue;

					final SearchMatch[] results= fReferences[i].getSearchResults();

					for (int j= 0; j < results.length; j++) {

						if (! (results[j] instanceof TypeReferenceMatch))
							continue;

						final TypeReferenceMatch match= (TypeReferenceMatch) results[j];
						final List matches= new ArrayList();

						if (match.getLocalElement() != null)
							matches.add(match.getLocalElement());
						else
							matches.add(match.getElement());

						final IJavaElement[] others= match.getOtherElements();
						if (others != null)
							matches.addAll(Arrays.asList(others));

						for (Iterator iter= matches.iterator(); iter.hasNext();) {
							final IJavaElement element= (IJavaElement) iter.next();

							if (! (element instanceof IMethod) && ! (element instanceof IField) && ! (element instanceof ILocalVariable))
								continue;
							
							if (!isInDeclaredType(match.getOffset(), element))
								continue;

							if (element instanceof IField) {
								final IField currentField= (IField) element;
								final String newFieldName= sugg.suggestNewFieldName(currentField.getJavaProject(), currentField.getElementName(), Flags.isStatic(currentField.getFlags()),
										unQualifiedTypeName, getNewElementName());

								if (newFieldName != null)
									fPreloadedElementToName.put(currentField, newFieldName);
							}

							if (element instanceof IMethod) {
								final IMethod currentMethod= (IMethod) element;
								addMethodRename(unQualifiedTypeName, sugg, currentMethod);
							}

							if (element instanceof ILocalVariable) {
								final ILocalVariable currentLocal= (ILocalVariable) element;
								final boolean isParameter;
								
								if (isParameter(currentLocal)) {
									addMethodRename(unQualifiedTypeName, sugg, (IMethod) currentLocal.getParent());
									isParameter= true;
								} else
									isParameter= false;

								final String newLocalName= sugg
										.suggestNewLocalName(currentLocal.getJavaProject(), currentLocal.getElementName(), isParameter, unQualifiedTypeName, getNewElementName());

								if (newLocalName != null)
									fPreloadedElementToName.put(currentLocal, newLocalName);
							}
						}
					}
					if (monitor.isCanceled())
						throw new OperationCanceledException();
				}
			}

			for (Iterator iter= fPreloadedElementToName.keySet().iterator(); iter.hasNext();) {
				IJavaElement element= (IJavaElement) iter.next();
				fPreloadedElementToSelection.put(element, Boolean.TRUE);
			}
			fPreloadedElementToNameDefault= (LinkedHashMap) fPreloadedElementToName.clone();

		} catch (OperationCanceledException e) {
			fReferences= null;
			fPreloadedElementToName= null;
			throw new OperationCanceledException();
		}
		return fCachedRefactoringStatus;
	}

	/**
	 * Returns true iff the given local variable is a parameter of its
	 * declaring method.
	 * 
	 * TODO replace this method with new API when available: 
	 * 		https://bugs.eclipse.org/bugs/show_bug.cgi?id=48420
	 */
	private boolean isParameter(ILocalVariable currentLocal) throws JavaModelException {

		final IJavaElement parent= currentLocal.getParent();
		if (parent instanceof IMethod) {
			final String[] params= ((IMethod) parent).getParameterNames();
			for (int i= 0; i < params.length; i++) {
				if (params[i].equals(currentLocal.getElementName()))
					return true;
			}
		}
		return false;
	}

	/**
	 * Returns true iff the given search match offset (must be a match of a type
	 * reference) lies before the element name of its enclosing java element,
	 * false if not. In other words: If this method returns true, the match is
	 * the declared type (or return type) of the enclosing element.
	 * 
	 */
	private boolean isInDeclaredType(int matchOffset, IJavaElement parentElement) throws JavaModelException {
		if (parentElement != null) {
			int enclosingNameOffset= 0;
			if (parentElement instanceof IMethod || parentElement instanceof IField)
				enclosingNameOffset= ((IMember) parentElement).getNameRange().getOffset();
			else if (parentElement instanceof ILocalVariable)
				enclosingNameOffset= ((ILocalVariable) parentElement).getNameRange().getOffset();

			return (matchOffset < enclosingNameOffset);
		}
		return false;
	}
	
	private void addMethodRename(final String unQualifiedTypeName, RenamingNameSuggestor sugg, final IMethod currentMethod) throws JavaModelException {
		if (!currentMethod.isConstructor()) {
			final String newMethodName= sugg.suggestNewMethodName(currentMethod.getElementName(), unQualifiedTypeName, getNewElementName());

			if (newMethodName != null)
				fPreloadedElementToName.put(currentMethod, newMethodName);
		}
	}

	private RefactoringStatus checkNewPathValidity() {
		IContainer c= ResourceUtil.getResource(fType).getParent();
		
		String notRename= RefactoringCoreMessages.RenameTypeRefactoring_will_not_rename; 
		IStatus status= c.getWorkspace().validateName(getNewElementName(), IResource.FILE);
		if (status.getSeverity() == IStatus.ERROR)
			return RefactoringStatus.createWarningStatus(status.getMessage() + ". " + notRename); //$NON-NLS-1$
		
		status= c.getWorkspace().validatePath(createNewPath(getNewElementName()), IResource.FILE);
		if (status.getSeverity() == IStatus.ERROR)
			return RefactoringStatus.createWarningStatus(status.getMessage() + ". " + notRename); //$NON-NLS-1$

		return new RefactoringStatus();
	}
	
	private String createNewPath(String newName) {
		return ResourceUtil.getResource(fType).getFullPath().removeLastSegments(1).append(newName).toString();
	}
	
	private RefactoringStatus checkTypesImportedInCu() throws CoreException {
		IImportDeclaration imp= getImportedType(fType.getCompilationUnit(), getNewElementName());
		
		if (imp == null)
			return null;	
			
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_imported, 
											new Object[]{getNewElementName(), ResourceUtil.getResource(fType).getFullPath()});
		IJavaElement grandParent= imp.getParent().getParent();
		if (grandParent instanceof ICompilationUnit)
			return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(imp));

		return null;	
	}
	
	private RefactoringStatus checkTypesInPackage() throws CoreException {
		IType type= Checks.findTypeInPackage(fType.getPackageFragment(), getNewElementName());
		if (type == null || ! type.exists())
			return null;
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_exists, 
																	new String[]{getNewElementName(), fType.getPackageFragment().getElementName()});
		return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(type));
	}
	
	private RefactoringStatus checkEnclosedTypes() throws CoreException {
		IType enclosedType= findEnclosedType(fType, getNewElementName());
		if (enclosedType == null)
			return null;
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_encloses,  
																		new String[]{JavaModelUtil.getFullyQualifiedName(fType), getNewElementName()});
		return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(enclosedType));
	}
	
	private RefactoringStatus checkEnclosingTypes() {
		IType enclosingType= findEnclosingType(fType, getNewElementName());
		if (enclosingType == null)
			return null;
			
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_enclosed,
								new String[]{JavaModelUtil.getFullyQualifiedName(fType), getNewElementName()});
		return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(enclosingType));
	}
	
	private static IType findEnclosedType(IType type, String newName) throws CoreException {
		IType[] enclosedTypes= type.getTypes();
		for (int i= 0; i < enclosedTypes.length; i++){
			if (newName.equals(enclosedTypes[i].getElementName()) || findEnclosedType(enclosedTypes[i], newName) != null)
				return enclosedTypes[i];
		}
		return null;
	}
		
	private static IType findEnclosingType(IType type, String newName) {
		IType enclosing= type.getDeclaringType();
		while (enclosing != null){
			if (newName.equals(enclosing.getElementName()))
				return enclosing;
			else 
				enclosing= enclosing.getDeclaringType();	
		}
		return null;
	}
	
	private static IImportDeclaration getImportedType(ICompilationUnit cu, String typeName) throws CoreException {
		IImportDeclaration[] imports= cu.getImports();
		String dotTypeName= "." + typeName; //$NON-NLS-1$
		for (int i= 0; i < imports.length; i++){
			if (imports[i].getElementName().endsWith(dotTypeName))
				return imports[i];
		}
		return null;
	}
	
	private RefactoringStatus checkForMethodsWithConstructorNames()  throws CoreException{
		IMethod[] methods= fType.getMethods();
		for (int i= 0; i < methods.length; i++){
			if (methods[i].isConstructor())
				continue;
			RefactoringStatus check= Checks.checkIfConstructorName(methods[i], methods[i].getElementName(), getNewElementName());	
			if (check != null)
				return check;
		}
		return null;
	}	
	
	private RefactoringStatus checkImportedTypes() throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		IImportDeclaration[] imports= fType.getCompilationUnit().getImports();	
		for (int i= 0; i < imports.length; i++)
			analyzeImportDeclaration(imports[i], result);
		return result;
	}
	
	private RefactoringStatus checkTypesInCompilationUnit() {
		RefactoringStatus result= new RefactoringStatus();
		if (! Checks.isTopLevel(fType)){ //the other case checked in checkTypesInPackage
			IType siblingType= fType.getDeclaringType().getType(getNewElementName());
			if (siblingType.exists()){
				String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_member_type_exists, 
																		new String[]{getNewElementName(), JavaModelUtil.getFullyQualifiedName(fType.getDeclaringType())});
				result.addError(msg, JavaStatusContext.create(siblingType));
			}
		}
		return result;
	}
	
	private RefactoringStatus analyseEnclosedTypes() throws CoreException {
		final ISourceRange typeRange= fType.getSourceRange();
		final RefactoringStatus result= new RefactoringStatus();
		CompilationUnit cuNode= new RefactoringASTParser(AST.JLS3).parse(fType.getCompilationUnit(), false);
		cuNode.accept(new ASTVisitor(){
			
			public boolean visit(TypeDeclaration node){ // enums and annotations can't be local
				if (node.getStartPosition() <= typeRange.getOffset())
					return true;
				if (node.getStartPosition() > typeRange.getOffset() + typeRange.getLength())
					return true;
		
				if (getNewElementName().equals(node.getName().getIdentifier())){
					RefactoringStatusContext	context= JavaStatusContext.create(fType.getCompilationUnit(), node);
					String msg= null;
					if (node.isLocalTypeDeclaration()){
						msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_local_type, 
									new String[]{JavaElementUtil.createSignature(fType), getNewElementName()});
					}	
					else if (node.isMemberTypeDeclaration()){
						msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_member_type, 
								new String[]{JavaElementUtil.createSignature(fType), getNewElementName()});
					}	
					if (msg != null)	
						result.addError(msg, context);
				}
		
				MethodDeclaration[] methods= node.getMethods();
				for (int i= 0; i < methods.length; i++) {
					if (Modifier.isNative(methods[i].getModifiers())){
						RefactoringStatusContext	context= JavaStatusContext.create(fType.getCompilationUnit(), methods[i]);
						String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_enclosed_type_native, node.getName().getIdentifier());
						result.addWarning(msg, context); 
					}	
				}
				return true;
			}
		});
		return result;
	}
	
	private static ICompilationUnit getCompilationUnit(IImportDeclaration imp) {
		return (ICompilationUnit)imp.getParent().getParent();
	}
	
	private void analyzeImportedTypes(IType[] types, RefactoringStatus result, IImportDeclaration imp) throws CoreException {
		for (int i= 0; i < types.length; i++) {
			//could this be a problem (same package imports)?
			if (JdtFlags.isPublic(types[i]) && types[i].getElementName().equals(getNewElementName())){
				String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_name_conflict1, 
																			new Object[]{JavaModelUtil.getFullyQualifiedName(types[i]), getFullPath(getCompilationUnit(imp))});
				result.addError(msg, JavaStatusContext.create(imp));
			}
		}
	}
	
	private static IJavaElement convertFromImportDeclaration(IImportDeclaration declaration) throws CoreException {
			if (declaration.isOnDemand()){ 
				String packageName= declaration.getElementName().substring(0, declaration.getElementName().length() - 2);
				return JavaModelUtil.findTypeContainer(declaration.getJavaProject(), packageName);
			} else 
				return JavaModelUtil.findTypeContainer(declaration.getJavaProject(), declaration.getElementName());
	}

	private void analyzeImportDeclaration(IImportDeclaration imp, RefactoringStatus result) throws CoreException{
		if (!imp.isOnDemand())
			return; //analyzed earlier
		
		IJavaElement imported= convertFromImportDeclaration(imp);
		if (imported == null)
			return;
			
		if (imported instanceof IPackageFragment){
			ICompilationUnit[] cus= ((IPackageFragment)imported).getCompilationUnits();
			for (int i= 0; i < cus.length; i++) {
				analyzeImportedTypes(cus[i].getTypes(), result, imp);
			}	
		} else {
			//cast safe: see JavaModelUtility.convertFromImportDeclaration
			analyzeImportedTypes(((IType)imported).getTypes(), result, imp);
		}
	}
	
	private IFile[] getAllFilesToModify() throws CoreException {
		List result= new ArrayList();
		result.addAll(Arrays.asList(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits())));
		if (fQualifiedNameSearchResult != null)
			result.addAll(Arrays.asList(fQualifiedNameSearchResult.getAllFiles()));
		if (willRenameCU())
			result.add(ResourceUtil.getFile(fType.getCompilationUnit()));
		return (IFile[]) result.toArray(new IFile[result.size()]);
	}
	
	/*
	 * Analyzes all compilation units in which type is referenced
	 */
	private RefactoringStatus analyzeAffectedCompilationUnits(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
			
		result.merge(Checks.checkCompileErrorsInAffectedFiles(fReferences, fType.getResource()));	
		
		pm.beginTask("", fReferences.length); //$NON-NLS-1$
		result.merge(checkConflictingTypes(pm));
		return result;
	}
	
	private RefactoringStatus checkConflictingTypes(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		IJavaSearchScope scope= RefactoringScopeFactory.create(fType);
		SearchPattern pattern= SearchPattern.createPattern(getNewElementName(),
				IJavaSearchConstants.TYPE, IJavaSearchConstants.ALL_OCCURRENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
		ICompilationUnit[] cusWithReferencesToConflictingTypes= RefactoringSearchEngine.findAffectedCompilationUnits(pattern, scope, pm, result);
		if (cusWithReferencesToConflictingTypes.length == 0)
			return result;
		ICompilationUnit[] 	cusWithReferencesToRenamedType= getCus(fReferences);

		ICompilationUnit[] intersection= isIntersectionEmpty(cusWithReferencesToRenamedType, cusWithReferencesToConflictingTypes);
		if (intersection.length == 0)
			return result;
		
		for (int i= 0; i < intersection.length; i++) {
			RefactoringStatusContext context= JavaStatusContext.create(intersection[i]);
			String message= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_another_type, 
				new String[]{getNewElementName(), intersection[i].getElementName()});
			result.addError(message, context);
		}	
		return result;
	}
	
	private static ICompilationUnit[] isIntersectionEmpty(ICompilationUnit[] a1, ICompilationUnit[] a2){
		Set set1= new HashSet(Arrays.asList(a1));
		Set set2= new HashSet(Arrays.asList(a2));
		set1.retainAll(set2);
		return (ICompilationUnit[]) set1.toArray(new ICompilationUnit[set1.size()]);
	}
	
	private static ICompilationUnit[] getCus(SearchResultGroup[] searchResultGroups){
		List cus= new ArrayList(searchResultGroups.length);
		for (int i= 0; i < searchResultGroups.length; i++) {
			ICompilationUnit cu= searchResultGroups[i].getCompilationUnit();
			if (cu != null)
				cus.add(cu);
		}
		return (ICompilationUnit[]) cus.toArray(new ICompilationUnit[cus.size()]);
	}
	
	private static String getFullPath(ICompilationUnit cu) {
		Assert.isTrue(cu.exists());
		return ResourceUtil.getResource(cu).getFullPath().toString();
	}
	
	//------------- Changes ---------------

	public Change createChange(IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask(RefactoringCoreMessages.RenameTypeRefactoring_creating_change, 4);
			final DynamicValidationStateChange result= new DynamicValidationStateChange(RefactoringCoreMessages.Change_javaChanges) {

				public RefactoringDescriptor getRefactoringDescriptor() {
					final Map arguments= new HashMap();
					arguments.put(RefactoringDescriptor.INPUT, fType.getHandleIdentifier());
					arguments.put(RefactoringDescriptor.NAME, getNewElementName());
					if (fFilePatterns != null && !"".equals(fFilePatterns)) //$NON-NLS-1$
						arguments.put(ATTRIBUTE_PATTERNS, fFilePatterns);
					arguments.put(ATTRIBUTE_REFERENCES, Boolean.valueOf(fUpdateReferences).toString());
					arguments.put(ATTRIBUTE_QUALIFIED, Boolean.valueOf(fUpdateQualifiedNames).toString());
					arguments.put(ATTRIBUTE_TEXTUAL_MATCHES, Boolean.valueOf(fUpdateTextualMatches).toString());
					arguments.put(ATTRIBUTE_DERIVED, Boolean.valueOf(fUpdateDerivedElements).toString());
					arguments.put(ATTRIBUTE_DERIVED_MATCHING_STRATEGY, Integer.toString(fRenamingStrategy));
					String project= null;
					IJavaProject javaProject= fType.getJavaProject();
					if (javaProject != null)
						project= javaProject.getElementName();
					int flags= RefactoringDescriptor.STRUCTURAL_CHANGE;
					try {
						if (!Flags.isPrivate(fType.getFlags()))
							flags|= RefactoringDescriptor.CLOSURE_CHANGE;
					} catch (JavaModelException exception) {
						JavaPlugin.log(exception);
					}
					return new RefactoringDescriptor(ID_RENAME_TYPE, project, MessageFormat.format(RefactoringCoreMessages.RenameTypeProcessor_descriptor_description, new String[] { JavaElementLabels.getElementLabel(fType, JavaElementLabels.ALL_FULLY_QUALIFIED), getNewElementName()}), null, arguments, flags);
				}
			};
			result.addAll(fChangeManager.getAllChanges());
			if (willRenameCU()) {
				IResource resource= ResourceUtil.getResource(fType);
				if (resource != null && resource.isLinked()) {
					String ext= resource.getFileExtension();
					String renamedResourceName;
					if (ext == null)
						renamedResourceName= getNewElementName();
					else
						renamedResourceName= getNewElementName() + '.' + ext;
					result.add(new RenameResourceChange(ResourceUtil.getResource(fType), renamedResourceName));
				} else {
					String renamedCUName= JavaModelUtil.getRenamedCUName(fType.getCompilationUnit(), getNewElementName());
					result.add(new RenameCompilationUnitChange(fType.getCompilationUnit(), renamedCUName));
				}
			}
			monitor.worked(1);
			return result;
		} finally {
			fChangeManager= null;
		}
	}
	
	public Change postCreateChange(Change[] participantChanges, IProgressMonitor pm) throws CoreException {
		if (fQualifiedNameSearchResult != null) {
			try {
				return fQualifiedNameSearchResult.getSingleChange(Changes.getModifiedFiles(participantChanges));
			} finally {
				fQualifiedNameSearchResult= null;
			}
		} else {
			return null;
		}
	}
	
	private boolean willRenameCU() throws CoreException{
		String name = JavaCore.removeJavaLikeExtension(fType.getCompilationUnit().getElementName());
		if (! (Checks.isTopLevel(fType) && name.equals(fType.getElementName())))
			return false;
		if (! checkNewPathValidity().isOK())
			return false;
		if (! Checks.checkCompilationUnitNewName(fType.getCompilationUnit(), getNewElementName()).isOK())
			return false;
		return true;	
	}
	
	private void createChanges(IProgressMonitor pm) throws CoreException {
		try{
			pm.beginTask("", 12); //$NON-NLS-1$
			pm.setTaskName(RefactoringCoreMessages.RenameTypeProcessor_creating_changes); 
			
			if (fUpdateReferences)
				addReferenceUpdates(fChangeManager, new SubProgressMonitor(pm, 3));

			// Derived updates have already been added.
	
			pm.worked(1);
			
			IResource resource= ResourceUtil.getResource(fType);
			// if we have a linked resource then we don't use CU renaming 
			// directly. So we have to update the code by ourselves.
			if ((resource != null && resource.isLinked()) || !willRenameCU()) {
				addTypeDeclarationUpdate(fChangeManager);
				pm.worked(1);
				
				addConstructorRenames(fChangeManager);
				pm.worked(1);
			} else {
				pm.worked(2);
			}
			
			if (fUpdateTextualMatches) {
				pm.subTask(RefactoringCoreMessages.RenameTypeRefactoring_searching_text); 
				TextMatchUpdater.perform(new SubProgressMonitor(pm, 1), RefactoringScopeFactory.create(fType), this, fChangeManager, fReferences);
				if (fUpdateDerivedElements)
					addDerivedTextualUpdates(fChangeManager, new SubProgressMonitor(pm, 3));
			}
			
		} finally{
			pm.done();
		}	
	}
	
	private void addTypeDeclarationUpdate(TextChangeManager manager) throws CoreException {
		String name= RefactoringCoreMessages.RenameTypeRefactoring_update; 
		int typeNameLength= fType.getElementName().length();
		ICompilationUnit cu= fType.getCompilationUnit();
		TextChangeCompatibility.addTextEdit(manager.get(cu), name, new ReplaceEdit(fType.getNameRange().getOffset(), typeNameLength, getNewElementName()));
	}
	
	private void addConstructorRenames(TextChangeManager manager) throws CoreException {
		ICompilationUnit cu= fType.getCompilationUnit();
		IMethod[] methods= fType.getMethods();
		int typeNameLength= fType.getElementName().length();
		for (int i= 0; i < methods.length; i++){
			if (methods[i].isConstructor()) {
				/*
				 * constructor declarations cannot be fully qualified so we can use simple replace here
				 *
				 * if (methods[i].getNameRange() == null), then it's a binary file so it's wrong anyway 
				 * (checked as a precondition)
				 */				
				String name= RefactoringCoreMessages.RenameTypeRefactoring_rename_constructor; 
				TextChangeCompatibility.addTextEdit(manager.get(cu), name, new ReplaceEdit(methods[i].getNameRange().getOffset(), typeNameLength, getNewElementName()));
			}
		}
	}
	
	private void addReferenceUpdates(TextChangeManager manager, IProgressMonitor pm) {
		pm.beginTask("", fReferences.length); //$NON-NLS-1$
		for (int i= 0; i < fReferences.length; i++){
			ICompilationUnit cu= fReferences[i].getCompilationUnit();
			if (cu == null)
				continue;
					
			String name= RefactoringCoreMessages.RenameTypeRefactoring_update_reference; 
			SearchMatch[] results= fReferences[i].getSearchResults();

			for (int j= 0; j < results.length; j++){
				SearchMatch match= results[j];
				String oldName= fType.getElementName();
				int offset= match.getOffset() + match.getLength() - oldName.length(); // reference may be qualified
				TextChangeCompatibility.addTextEdit(manager.get(cu), name, new ReplaceEdit(offset, oldName.length(), getNewElementName()), CATEGORY_TYPE_RENAME);
			}
			pm.worked(1);
		}
	}
	
	private void computeQualifiedNameMatches(IProgressMonitor pm) throws CoreException {
		IPackageFragment fragment= fType.getPackageFragment();
		if (fQualifiedNameSearchResult == null)
			fQualifiedNameSearchResult= new QualifiedNameSearchResult();
		QualifiedNameFinder.process(fQualifiedNameSearchResult, fType.getFullyQualifiedName(),  
			fragment.getElementName() + "." + getNewElementName(), //$NON-NLS-1$
			fFilePatterns, fType.getJavaProject().getProject(), pm);
	}

	public RefactoringStatus initialize(RefactoringArguments arguments) {
		if (arguments instanceof GenericRefactoringArguments) {
			final GenericRefactoringArguments generic= (GenericRefactoringArguments) arguments;
			final String handle= generic.getAttribute(RefactoringDescriptor.INPUT);
			if (handle != null) {
				final IJavaElement element= JavaCore.create(handle);
				if (element == null || !element.exists())
					return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_input_not_exists, getIdentifier()));
				else
					fType= (IType) element;
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, RefactoringDescriptor.INPUT));
			final String name= generic.getAttribute(RefactoringDescriptor.NAME);
			if (name != null) {
				if (fType != null) {
					final RefactoringStatus status= checkNewElementName(name);
					if (!status.hasError())
						setNewElementName(name);
					else
						return status;
				}
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, RefactoringDescriptor.NAME));
			final String patterns= generic.getAttribute(ATTRIBUTE_PATTERNS);
			if (patterns != null && !"".equals(patterns)) //$NON-NLS-1$
				fFilePatterns= patterns;
			final String references= generic.getAttribute(ATTRIBUTE_REFERENCES);
			if (references != null) {
				fUpdateReferences= Boolean.valueOf(references).booleanValue();
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REFERENCES));
			final String matches= generic.getAttribute(ATTRIBUTE_TEXTUAL_MATCHES);
			if (matches != null) {
				fUpdateTextualMatches= Boolean.valueOf(matches).booleanValue();
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_TEXTUAL_MATCHES));
			final String qualified= generic.getAttribute(ATTRIBUTE_QUALIFIED);
			if (qualified != null) {
				fUpdateQualifiedNames= Boolean.valueOf(qualified).booleanValue();
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_QUALIFIED));
			final String derived= generic.getAttribute(ATTRIBUTE_DERIVED);
			if (derived != null)
				fUpdateDerivedElements= Boolean.valueOf(derived).booleanValue();
			else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_DERIVED));
			final String derivedMatchingStrategy= generic.getAttribute(ATTRIBUTE_DERIVED_MATCHING_STRATEGY);
			if (derivedMatchingStrategy != null) {
				try {
					fRenamingStrategy= Integer.valueOf(derivedMatchingStrategy).intValue();
				} catch (NumberFormatException e) {
					return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, derivedMatchingStrategy, ATTRIBUTE_QUALIFIED));
				}
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_DERIVED_MATCHING_STRATEGY));
		} else
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments);
		return new RefactoringStatus();
	}
	
	// --------- Derived

	/**
	 * Creates and initializes the derived refactoring processors
	 */
	private RefactoringStatus initializeDerivedRenameProcessors(IProgressMonitor progressMonitor, CheckConditionsContext context) throws CoreException {

		Assert.isNotNull(fPreloadedElementToName);
		Assert.isNotNull(fPreloadedElementToSelection);

		final RefactoringStatus status= new RefactoringStatus();
		final Set handledTopLevelMethods= new HashSet();
		final Set warnings= new HashSet();
		final List processors= new ArrayList();
		fFinalDerivedElementToName= new HashMap();
		
		CompilationUnit currentResolvedCU= null;
		ICompilationUnit currentCU= null;
		
		int current= 0;
		final int max= fPreloadedElementToName.size();

		progressMonitor.beginTask("", max * 3); //$NON-NLS-1$
		progressMonitor.setTaskName(RefactoringCoreMessages.RenameTypeProcessor_checking_derived_refactoring_conditions); 

		for (Iterator iter= fPreloadedElementToName.keySet().iterator(); iter.hasNext();) {

			final IJavaElement element= (IJavaElement) iter.next();
			
			current++;
			progressMonitor.worked(3);

			// not selected? -> skip
			if (! ((Boolean) (fPreloadedElementToSelection.get(element))).booleanValue())
				continue;

			// already registered? (may happen with overridden methods) -> skip
			if (fFinalDerivedElementToName.containsKey(element))
				continue;
			
			// CompilationUnit changed? (note: fPreloadedElementToName is sorted by CompilationUnit)
			ICompilationUnit newCU= (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
			
			if (!newCU.equals(currentCU)) {

				checkCUCompleteConditions(status, currentResolvedCU, currentCU, processors);
				
				if (status.hasFatalError())
					return status;
				
				// reset values
				currentResolvedCU= null;
				currentCU= newCU;
				processors.clear();
			}
			
			final String newName= (String) fPreloadedElementToName.get(element);
			RefactoringProcessor processor= null;
			
			if (element instanceof ILocalVariable) {
				final ILocalVariable currentLocal= (ILocalVariable) element;

				if (currentResolvedCU == null)
					currentResolvedCU= new RefactoringASTParser(AST.JLS3).parse(currentCU, true);
				
				processor= createLocalRenameProcessor(currentLocal, newName, currentResolvedCU);

				// don't check for conflicting rename => is done by #checkCUCompleteConditions().
				
				if (status.hasFatalError())
					return status;
				fFinalDerivedElementToName.put(currentLocal, newName);
			}
			if (element instanceof IField) {
				final IField currentField= (IField) element;
				processor= createFieldRenameProcessor(currentField, newName);

				status.merge(checkForConflictingRename(currentField, newName));
				if (status.hasFatalError())
					return status;
				fFinalDerivedElementToName.put(currentField, newName);
			}
			if (element instanceof IMethod) {
				IMethod currentMethod= (IMethod) element;
				if (MethodChecks.isVirtual(currentMethod)) {
					
					final IType declaringType= currentMethod.getDeclaringType();
					ITypeHierarchy hierarchy= null;
					if (!declaringType.isInterface()) 
						hierarchy= declaringType.newTypeHierarchy(new NullProgressMonitor());
					
					final IMethod topmost= MethodChecks.getTopmostMethod(currentMethod, hierarchy, new NullProgressMonitor());
					if (topmost != null)
						currentMethod= topmost;
					if (handledTopLevelMethods.contains(currentMethod))
						continue;
					handledTopLevelMethods.add(currentMethod);
					final IMethod[] ripples= MethodChecks.getOverriddenMethods(currentMethod, new NullProgressMonitor());

					if (checkForWarnings(warnings, newName, ripples))
						continue;

					status.merge(checkForConflictingRename(ripples, newName));
					if (status.hasFatalError())
						return status;

					processor= createVirtualMethodRenameProcessor(currentMethod, newName, ripples, hierarchy);
					fFinalDerivedElementToName.put(currentMethod, newName);
					for (int i= 0; i < ripples.length; i++) {
						fFinalDerivedElementToName.put(ripples[i], newName);
					}
				} else {
					
					status.merge(checkForConflictingRename(new IMethod[] { currentMethod }, newName));
					if (status.hasFatalError())
						break;
					
					fFinalDerivedElementToName.put(currentMethod, newName);
					
					processor= createNonVirtualMethodRenameProcessor(currentMethod, newName);
				}
			}
			
			progressMonitor.subTask(Messages.format(RefactoringCoreMessages.RenameTypeProcessor_progress_current_total, new Object[] { String.valueOf(current), String.valueOf(max)}));

			status.merge(processor.checkInitialConditions(new NoOverrideProgressMonitor(progressMonitor, 1)));

			if (status.hasFatalError())
				return status;

			status.merge(processor.checkFinalConditions(new NoOverrideProgressMonitor(progressMonitor, 1), context));

			if (status.hasFatalError())
				return status;
			
			processors.add(processor);

			progressMonitor.worked(1);
			
			if (progressMonitor.isCanceled())
				throw new OperationCanceledException();
		}

		// check last CU
		checkCUCompleteConditions(status, currentResolvedCU, currentCU, processors);
		
		status.merge(addWarnings(warnings));

		progressMonitor.done();
		return status;
	}

	private void checkCUCompleteConditions(final RefactoringStatus status, CompilationUnit currentResolvedCU, ICompilationUnit currentCU, List processors) throws CoreException {

		// check local variable conditions
		List locals= getProcessorsOfType(processors, RenameLocalVariableProcessor.class);
		if (!locals.isEmpty()) {
			RenameAnalyzeUtil.LocalAnalyzePackage[] analyzePackages= new RenameAnalyzeUtil.LocalAnalyzePackage[locals.size()];
			TextChangeManager manager= new TextChangeManager();
			int current= 0;
			TextChange textChange= manager.get(currentCU);
			textChange.setKeepPreviewEdits(true);
			for (Iterator iterator= locals.iterator(); iterator.hasNext();) {
				RenameLocalVariableProcessor localProcessor= (RenameLocalVariableProcessor) iterator.next();
				RenameAnalyzeUtil.LocalAnalyzePackage analyzePackage= localProcessor.getLocalAnalyzePackage();
				analyzePackages[current]= analyzePackage;
				for (int i= 0; i < analyzePackage.fOccurenceEdits.length; i++) {
					TextChangeCompatibility.addTextEdit(textChange, "", analyzePackage.fOccurenceEdits[i], GroupCategorySet.NONE); //$NON-NLS-1$
				}
				current++;
			}
			status.merge(RenameAnalyzeUtil.analyzeLocalRenames(analyzePackages, textChange, currentResolvedCU));
		}

		/*
		 * There is room for performance improvement here: One could move
		 * shadowing analyses out of the field and method processors and perform
		 * it here, thus saving on working copy creation. Drawback is increased
		 * heap consumption.
		 */
	}

	private List getProcessorsOfType(List processors, Class type) {
		List tmp= new ArrayList();
		for (Iterator iter= processors.iterator(); iter.hasNext();) {
			RefactoringProcessor element= (RefactoringProcessor) iter.next();
			if (element.getClass().equals(type))
				tmp.add(element);
		}
		return tmp;
	}

	// ------------------ Error checking -------------

	/**
	 * Checks whether one of the given methods, which will all be renamed to
	 * "newName", shares a type with another already registered method which is
	 * renamed to the same new name and shares the same parameters.
	 * 
	 * @see #checkForConflictingRename(IField, String)
	 */
	private RefactoringStatus checkForConflictingRename(IMethod[] methods, String newName) {
		RefactoringStatus status= new RefactoringStatus();
		for (Iterator iter= fFinalDerivedElementToName.keySet().iterator(); iter.hasNext();) {
			IJavaElement element= (IJavaElement) iter.next();
			if (element instanceof IMethod) {
				IMethod alreadyRegisteredMethod= (IMethod) element;
				String alreadyRegisteredMethodName= (String) fFinalDerivedElementToName.get(element);
				for (int i= 0; i < methods.length; i++) {
					IMethod method2= methods[i];
					if ( (alreadyRegisteredMethodName.equals(newName)) && (method2.getDeclaringType().equals(alreadyRegisteredMethod.getDeclaringType()))
							&& (sameParams(alreadyRegisteredMethod, method2))) {
						String message= Messages.format(RefactoringCoreMessages.RenameTypeProcessor_cannot_rename_methods_same_new_name, new String[] { alreadyRegisteredMethod.getElementName(),
								method2.getElementName(), alreadyRegisteredMethod.getDeclaringType().getFullyQualifiedName(), newName });
						status.addFatalError(message);
						return status;
					}
				}
			}
		}
		return status;
	}

	private static boolean sameParams(IMethod method, IMethod method2) {

		if (method.getNumberOfParameters() != method2.getNumberOfParameters())
			return false;

		String[] params= method.getParameterTypes();
		String[] params2= method2.getParameterTypes();

		for (int i= 0; i < params.length; i++) {
			String t1= Signature.getSimpleName(Signature.toString(params[i]));
			String t2= Signature.getSimpleName(Signature.toString(params2[i]));
			if (!t1.equals(t2)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * If suffix matching is enabled, the refactoring may suggest two fields to
	 * have the same name which reside in the same type. Same thing may also
	 * happen if the user makes poor choices for the field names.
	 * 
	 * Consider: FooBarThing fFooBarThing; FooBarThing fBarThing;
	 * 
	 * Rename "FooBarThing" to "DifferentHunk". Suggestion for both fields is
	 * "fDifferentHunk" (and rightly so).
	 */
	private RefactoringStatus checkForConflictingRename(IField currentField, String newName) {
		RefactoringStatus status= new RefactoringStatus();
		for (Iterator iter= fFinalDerivedElementToName.keySet().iterator(); iter.hasNext();) {
			IJavaElement element= (IJavaElement) iter.next();
			if (element instanceof IField) {
				IField alreadyRegisteredField= (IField) element;
				String alreadyRegisteredFieldName= (String) fFinalDerivedElementToName.get(element);
				if (alreadyRegisteredFieldName.equals(newName)) {
					if (alreadyRegisteredField.getDeclaringType().equals(currentField.getDeclaringType())) {
						
						String message= Messages.format(RefactoringCoreMessages.RenameTypeProcessor_cannot_rename_fields_same_new_name, new String[] { alreadyRegisteredField.getElementName(),
								currentField.getElementName(), alreadyRegisteredField.getDeclaringType().getFullyQualifiedName(), newName });
						status.addFatalError(message);
						return status;
					}
				}
			}
		}
		return status;
	}

	private RefactoringStatus addWarnings(final Set warnings) {
		RefactoringStatus status= new RefactoringStatus();

		// Remove deleted ripple methods from user selection and add warnings
		for (Iterator iter= warnings.iterator(); iter.hasNext();) {
			final Warning warning= (Warning) iter.next();
			final IMethod[] elements= warning.getRipple();
			if (warning.isSelectionWarning()) {
				String message= Messages.format(RefactoringCoreMessages.RenameTypeProcessor_deselected_method_is_overridden,
						new String[] { JavaElementLabels.getElementLabel(elements[0], JavaElementLabels.ALL_DEFAULT),
								JavaElementLabels.getElementLabel(elements[0].getDeclaringType(), JavaElementLabels.ALL_DEFAULT) });
				status.addWarning(message);
			}
			if (warning.isNameWarning()) {
				String message= Messages.format(
						RefactoringCoreMessages.RenameTypeProcessor_renamed_method_is_overridden, new String[] {
								JavaElementLabels.getElementLabel(elements[0], JavaElementLabels.ALL_DEFAULT),
								JavaElementLabels.getElementLabel(elements[0].getDeclaringType(), JavaElementLabels.ALL_DEFAULT) });
				status.addWarning(message);
			}
			for (int i= 0; i < elements.length; i++)
				fPreloadedElementToSelection.put(elements[i], Boolean.FALSE);
		}
		return status;
	}

	/*
	 * If one of the methods of this ripple was deselected or renamed by
	 * the user, deselect the whole chain and add warnings.
	 */
	private boolean checkForWarnings(final Set warnings, final String newName, final IMethod[] ripples) {

		boolean addSelectionWarning= false;
		boolean addNameWarning= false;
		for (int i= 0; i < ripples.length; i++) {
			String newNameOfRipple= (String) fPreloadedElementToName.get(ripples[i]);
			Boolean selected= (Boolean) fPreloadedElementToSelection.get(ripples[i]);

			// selected may be null here due to supermethods like
			// setSomeClass(Object class) (subsignature match)
			// Don't add a warning.
			if (selected == null)
				continue;

			if (!selected.booleanValue())
				addSelectionWarning= true;

			if (!newName.equals(newNameOfRipple))
				addNameWarning= true;
		}
		if (addSelectionWarning || addNameWarning)
			warnings.add(new Warning(ripples, addSelectionWarning, addNameWarning));

		return (addSelectionWarning || addNameWarning);
	}

	private class Warning {

		private IMethod[] fRipple;
		private boolean fSelectionWarning;
		private boolean fNameWarning;

		public Warning(IMethod[] ripple, boolean isSelectionWarning, boolean isNameWarning) {
			fRipple= ripple;
			fSelectionWarning= isSelectionWarning;
			fNameWarning= isNameWarning;
		}

		public boolean isNameWarning() {
			return fNameWarning;
		}

		public IMethod[] getRipple() {
			return fRipple;
		}

		public boolean isSelectionWarning() {
			return fSelectionWarning;
		}
	}

	// ----------------- Processor creation --------

	private RenameMethodProcessor createVirtualMethodRenameProcessor(IMethod currentMethod, String newMethodName, IMethod[] ripples, ITypeHierarchy hierarchy) throws JavaModelException {
		RenameMethodProcessor processor= new RenameVirtualMethodProcessor(currentMethod, ripples, fChangeManager, hierarchy, CATEGORY_METHOD_RENAME);
		initMethodProcessor(processor, newMethodName);
		return processor;
	}

	private RenameMethodProcessor createNonVirtualMethodRenameProcessor(IMethod currentMethod, String newMethodName) {
		RenameMethodProcessor processor= new RenameNonVirtualMethodProcessor(currentMethod, fChangeManager, CATEGORY_METHOD_RENAME);
		initMethodProcessor(processor, newMethodName);
		return processor;
	}

	private void initMethodProcessor(RenameMethodProcessor processor, String newMethodName) {
		processor.setNewElementName(newMethodName);
		processor.setUpdateReferences(getUpdateReferences());
	}

	private RenameFieldProcessor createFieldRenameProcessor(final IField field, final String newName) {
		final RenameFieldProcessor processor= new RenameFieldProcessor(field, fChangeManager, CATEGORY_FIELD_RENAME);
		processor.setNewElementName(newName);
		processor.setRenameGetter(false);
		processor.setRenameSetter(false);
		processor.setUpdateReferences(getUpdateReferences());
		processor.setUpdateTextualMatches(false);
		return processor;
	}
	
	private RenameLocalVariableProcessor createLocalRenameProcessor(final ILocalVariable local, final String newName, final CompilationUnit compilationUnit) {
		final RenameLocalVariableProcessor processor= new RenameLocalVariableProcessor(local, fChangeManager, compilationUnit, CATEGORY_LOCAL_RENAME);
		processor.setNewElementName(newName);
		processor.setUpdateReferences(getUpdateReferences());
		return processor;
	}

	// ----------- Edit creation -----------


	/**
	 * Updates textual matches for fields.
	 * 
	 * Strategy for matching text matches: Match and replace all fully qualified
	 * field names, but non-qualified field names ony iff there are no fields
	 * which have the same original, but a different new name. Don't add java
	 * references; duplicate edits may be created but do not matter.
	 * 
	 */
	private void addDerivedTextualUpdates(TextChangeManager manager, IProgressMonitor monitor) throws CoreException {

		final Map simpleNames= new HashMap();
		final List forbiddenSimpleNames= new ArrayList();

		for (Iterator iter= fFinalDerivedElementToName.keySet().iterator(); iter.hasNext();) {
			final IJavaElement element= (IJavaElement) iter.next();
			if (element instanceof IField) {

				if (forbiddenSimpleNames.contains(element.getElementName()))
					continue;

				final String registeredNewName= (String) simpleNames.get(element.getElementName());
				final String newNameToCheck= (String) fFinalDerivedElementToName.get(element);
				if (registeredNewName == null)
					simpleNames.put(element.getElementName(), newNameToCheck);
				else if (!registeredNewName.equals(newNameToCheck))
					forbiddenSimpleNames.add(element.getElementName());
			}
		}

		for (Iterator iter= fFinalDerivedElementToName.keySet().iterator(); iter.hasNext();) {
			final IJavaElement element= (IJavaElement) iter.next();
			if (element instanceof IField) {
				final IField field= (IField) element;
				final String newName= (String) fFinalDerivedElementToName.get(field);
				TextMatchUpdater.perform(monitor, RefactoringScopeFactory.create(field), field.getElementName(), field.getDeclaringType().getFullyQualifiedName(), newName, manager,
						new SearchResultGroup[0], forbiddenSimpleNames.contains(field.getElementName()));
			}
		}
	}

	// ---- IDerivedElementUpdating

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.IDerivedElementUpdating#canEnableDerivedElementUpdating()
	 */
	public boolean canEnableDerivedElementUpdating() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.IDerivedElementUpdating#setUpdateDerivedElements(boolean)
	 */
	public void setUpdateDerivedElements(boolean update) {
		fUpdateDerivedElements= update;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.IDerivedElementUpdating#getUpdateDerivedElements()
	 */
	public boolean getUpdateDerivedElements() {
		return fUpdateDerivedElements;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.IDerivedElementUpdating#getMatchStrategy()
	 */
	public int getMatchStrategy() {
		return fRenamingStrategy;

	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.IDerivedElementUpdating#setMatchStrategy(int)
	 */
	public void setMatchStrategy(int selectedStrategy) {
		fRenamingStrategy= selectedStrategy;
	}

	// ------------- IDerivedElementRefactoringComponent

	/**
	 * Returns the derived elements of the type, i.e. IFields, IMethods,
	 * and ILocalVariables.
	 * 
	 */
	public Object[] getDerivedElements() {
		if (fFinalDerivedElementToName == null)
			return null;
		return fFinalDerivedElementToName.keySet().toArray();
	}

	/**
	 * This method is used to ask the refactoring object for an updated element
	 * handle of java elements and resources in the project. The returned handle
	 * is either an updated handle of the given handle which reflects all
	 * changes to the project - or the original handle if it is not affected by
	 * the changes.
	 * 
	 * Note that local variables <strong>cannot</strong> be resolved using this
	 * method.
	 * 
	 */
	public Object getRefactoredElement(Object element) {

		if (element instanceof IFile) {
			if (Checks.isTopLevel(fType) && element.equals(fType.getResource()))
				return getNewCompilationUnit().getResource();
		} else if (element instanceof ICompilationUnit) {
			if (Checks.isTopLevel(fType) && element.equals(fType.getCompilationUnit()))
				return getNewCompilationUnit();
		} else if (element instanceof IMember) {
			final IType newType= (IType) getNewElement();
			final RefactoringHandleTransplanter transplanter= new RefactoringHandleTransplanter(fType, newType, fFinalDerivedElementToName);
			return transplanter.transplantHandle((IMember) element);
		} 
			
		return element;
	}

	// ------ UI interaction

	/**
	 * Returns the map of derived elements (IJavaElement -> String with new name)
	 * This map is live. Callers may change the new names of the elements; they
	 * may not change the key set.
	 */
	public Map/* <IJavaElement, String> */getDerivedElementsToNewNames() {
		return fPreloadedElementToName;
	}

	/**
	 * Returns the map of derived elements (IJavaElement -> Boolean if selected) This
	 * map is live. Callers may change the selection status of the elements;
	 * they may not change the key set.
	 */
	public Map/* <IJavaElement, Boolean> */getDerivedElementsToSelection() {
		return fPreloadedElementToSelection;
	}

	/**
	 * Resets the element maps back to the original status. This affects the
	 * maps returned in {@link #getDerivedElementsToNewNames() } and
	 * {@link #getDerivedElementsToSelection() }. All new names are reset to
	 * the calculated ones and every element gets selected.
	 * 
	 */
	public void resetSelectedDerivedElements() {
		Assert.isNotNull(fPreloadedElementToName);
		for (Iterator iter= fPreloadedElementToNameDefault.keySet().iterator(); iter.hasNext();) {
			final IJavaElement element= (IJavaElement) iter.next();
			fPreloadedElementToName.put(element, fPreloadedElementToNameDefault.get(element));
			fPreloadedElementToSelection.put(element, Boolean.TRUE);
		}
	}

	/**
	 * Returns true iff the "update derived elements" flag is set AND the
	 * search yielded some elements to be renamed.
	 * 
	 */
	public boolean hasDerivedElementsToRename() {
		if (!fUpdateDerivedElements)
			return false;
		if (fPreloadedElementToName == null)
			return false;
		if (fPreloadedElementToName.size() == 0)
			return false;
		return true;
	}
}
