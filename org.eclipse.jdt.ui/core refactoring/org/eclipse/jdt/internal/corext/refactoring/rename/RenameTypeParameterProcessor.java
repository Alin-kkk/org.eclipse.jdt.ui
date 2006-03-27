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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.ChangeDescriptor;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringChangeDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.NodeFinder;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptor;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Rename processor to rename type parameters.
 */
public final class RenameTypeParameterProcessor extends JavaRenameProcessor implements INameUpdating, IReferenceUpdating {

	/**
	 * AST visitor which searches for occurrences of the type parameter.
	 */
	public final class RenameTypeParameterVisitor extends ASTVisitor {

		/** The binding of the type parameter */
		private final IBinding fBinding;

		/** The node of the type parameter name */
		private final SimpleName fName;

		/** The compilation unit rewrite to use */
		private final CompilationUnitRewrite fRewrite;

		/** The status of the visiting process */
		private final RefactoringStatus fStatus;

		/**
		 * Creates a new rename type parameter visitor.
		 * 
		 * @param rewrite
		 *            the compilation unit rewrite to use
		 * @param range
		 *            the source range of the type parameter
		 * @param status
		 *            the status to update
		 */
		public RenameTypeParameterVisitor(final CompilationUnitRewrite rewrite, final ISourceRange range, final RefactoringStatus status) {
			super(true);
			Assert.isNotNull(rewrite);
			Assert.isNotNull(range);
			Assert.isNotNull(status);
			fRewrite= rewrite;
			fName= (SimpleName) NodeFinder.perform(rewrite.getRoot(), range);
			fBinding= fName.resolveBinding();
			fStatus= status;
		}

		/**
		 * Returns the resulting change.
		 * 
		 * @return the resulting change
		 * @throws CoreException
		 *             if the change could not be created
		 */
		public final Change getResult() throws CoreException {
			return fRewrite.createChange();
		}

		public final boolean visit(final AnnotationTypeDeclaration node) {
			final String name= node.getName().getIdentifier();
			if (name.equals(getNewElementName())) {
				fStatus.addError(Messages.format(RefactoringCoreMessages.RenameTypeParameterRefactoring_type_parameter_inner_class_clash, new String[] { name}), JavaStatusContext.create(fTypeParameter.getDeclaringMember().getCompilationUnit(), new SourceRange(node)));
				return false;
			}
			return true;
		}

		public final boolean visit(final EnumDeclaration node) {
			final String name= node.getName().getIdentifier();
			if (name.equals(getNewElementName())) {
				fStatus.addError(Messages.format(RefactoringCoreMessages.RenameTypeParameterRefactoring_type_parameter_inner_class_clash, new String[] { name}), JavaStatusContext.create(fTypeParameter.getDeclaringMember().getCompilationUnit(), new SourceRange(node)));
				return false;
			}
			return true;
		}

		public final boolean visit(final SimpleName node) {
			final ITypeBinding binding= node.resolveTypeBinding();
			if (binding != null && binding.isTypeVariable() && Bindings.equals(binding, fBinding) && node.getIdentifier().equals(fName.getIdentifier())) {
				if (node != fName) {
					if (fUpdateReferences)
						fRewrite.getASTRewrite().set(node, SimpleName.IDENTIFIER_PROPERTY, getNewElementName(), fRewrite.createGroupDescription(RefactoringCoreMessages.RenameTypeParameterRefactoring_update_type_parameter_reference));
				} else
					fRewrite.getASTRewrite().set(node, SimpleName.IDENTIFIER_PROPERTY, getNewElementName(), fRewrite.createGroupDescription(RefactoringCoreMessages.RenameTypeParameterRefactoring_update_type_parameter_declaration));
			}
			return true;
		}

		public final boolean visit(final TypeDeclaration node) {
			final String name= node.getName().getIdentifier();
			if (name.equals(getNewElementName())) {
				fStatus.addError(Messages.format(RefactoringCoreMessages.RenameTypeParameterRefactoring_type_parameter_inner_class_clash, new String[] { name}), JavaStatusContext.create(fTypeParameter.getDeclaringMember().getCompilationUnit(), new SourceRange(node)));
				return false;
			}
			return true;
		}
	}

	private static final String ATTRIBUTE_REFERENCES= "references"; //$NON-NLS-1$
	private static final String ATTRIBUTE_PARAMETER= "parameter"; //$NON-NLS-1$

	private static final String ID_RENAME_TYPE_PARAMETER= "org.eclipse.jdt.ui.rename.type.parameter"; //$NON-NLS-1$

	/** The identifier of this processor */
	public static final String IDENTIFIER= "org.eclipse.jdt.ui.renameTypeParameterProcessor"; //$NON-NLS-1$

	/** The change object */
	private Change fChange= null;

	/** The type parameter to rename */
	private ITypeParameter fTypeParameter;

	/** Should references to the type parameter be updated? */
	private boolean fUpdateReferences= true;

	/**
	 * Creates a new rename type parameter processor.
	 * 
	 * @param parameter
	 *            the type parameter to rename, or <code>null</code> if invoked by scripting
	 */
	public RenameTypeParameterProcessor(final ITypeParameter parameter) {
		fTypeParameter= parameter;
		if (parameter != null)
			setNewElementName(parameter.getElementName());
	}

	public final boolean canEnableUpdateReferences() {
		return true;
	}
	
	protected RenameModifications computeRenameModifications() throws CoreException {
		RenameModifications result= new RenameModifications();
		result.rename(fTypeParameter, new RenameArguments(getNewElementName(), getUpdateReferences()));
		return result;
	}

	protected IFile[] getChangedFiles() throws CoreException {
		return new IFile[] {ResourceUtil.getFile(fTypeParameter.getDeclaringMember().getCompilationUnit())};
	}
	
	protected final RefactoringStatus doCheckFinalConditions(final IProgressMonitor monitor, final CheckConditionsContext context) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(context);
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask("", 5); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.RenameTypeParameterRefactoring_checking);
			status.merge(Checks.checkIfCuBroken(fTypeParameter.getDeclaringMember()));
			monitor.worked(1);
			if (!status.hasFatalError()) {
				status.merge(checkNewElementName(getNewElementName()));
				monitor.worked(1);
				monitor.setTaskName(RefactoringCoreMessages.RenameTypeParameterRefactoring_searching);
				status.merge(createRenameChanges(new SubProgressMonitor(monitor, 2)));
				monitor.setTaskName(RefactoringCoreMessages.RenameTypeParameterRefactoring_checking);
				if (status.hasFatalError())
					return status;
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	public final RefactoringStatus checkInitialConditions(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		if (!fTypeParameter.exists())
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.RenameTypeParameterRefactoring_deleted, fTypeParameter.getDeclaringMember().getCompilationUnit().getElementName()));
		return Checks.checkIfCuBroken(fTypeParameter.getDeclaringMember());
	}

	public final RefactoringStatus checkNewElementName(final String name) throws CoreException {
		Assert.isNotNull(name);
		final RefactoringStatus result= Checks.checkTypeParameterName(name);
		if (Checks.startsWithLowerCase(name))
			result.addWarning(RefactoringCoreMessages.RenameTypeParameterRefactoring_should_start_lowercase);
		if (Checks.isAlreadyNamed(fTypeParameter, name))
			result.addFatalError(RefactoringCoreMessages.RenameTypeParameterRefactoring_another_name);

		final IMember member= fTypeParameter.getDeclaringMember();
		if (member instanceof IType) {
			final IType type= (IType) member;
			if (type.getTypeParameter(name).exists())
				result.addFatalError(RefactoringCoreMessages.RenameTypeParameterRefactoring_class_type_parameter_already_defined);
		} else if (member instanceof IMethod) {
			final IMethod method= (IMethod) member;
			if (method.getTypeParameter(name).exists())
				result.addFatalError(RefactoringCoreMessages.RenameTypeParameterRefactoring_method_type_parameter_already_defined);
		} else {
			JavaPlugin.logErrorMessage("Unexpected sub-type of IMember: " + member.getClass().getName()); //$NON-NLS-1$
			Assert.isTrue(false);
		}
		return result;
	}

	public final Change createChange(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		try {
			Change change= fChange;
			if (change != null) {
				final CompositeChange composite= new CompositeChange("", new Change[] { change}) { //$NON-NLS-1$

					public final ChangeDescriptor getDescriptor() {
						final Map arguments= new HashMap();
						String project= null;
						IJavaProject javaProject= fTypeParameter.getJavaProject();
						if (javaProject != null)
							project= javaProject.getElementName();
						final JavaRefactoringDescriptor descriptor= new JavaRefactoringDescriptor(ID_RENAME_TYPE_PARAMETER, project, Messages.format(RefactoringCoreMessages.RenameTypeParameterProcessor_descriptor_description, new String[] { fTypeParameter.getElementName(), JavaElementLabels.getElementLabel(fTypeParameter.getDeclaringMember(), JavaElementLabels.ALL_FULLY_QUALIFIED), getNewElementName()}), getComment(), arguments, RefactoringDescriptor.NONE);
						arguments.put(JavaRefactoringDescriptor.ATTRIBUTE_INPUT, descriptor.elementToHandle(fTypeParameter.getDeclaringMember()));
						arguments.put(JavaRefactoringDescriptor.ATTRIBUTE_NAME, getNewElementName());
						arguments.put(ATTRIBUTE_PARAMETER, fTypeParameter.getElementName());
						arguments.put(ATTRIBUTE_REFERENCES, Boolean.valueOf(fUpdateReferences).toString());
						return new RefactoringChangeDescriptor(descriptor);
					}
				};
				composite.markAsSynthetic();
				change= composite;
			}
			return change;
		} finally {
			fChange= null;
			monitor.done();
		}
	}

	/**
	 * Creates the necessary changes for the renaming of the type parameter.
	 * 
	 * @param monitor
	 *            the progress monitor to display progress
	 * @return the status of the operation
	 * @throws CoreException
	 *             if the change could not be generated
	 */
	private RefactoringStatus createRenameChanges(final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(monitor);
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask(RefactoringCoreMessages.RenameTypeParameterRefactoring_searching, 2);
			final ICompilationUnit cu= fTypeParameter.getDeclaringMember().getCompilationUnit();
			final CompilationUnit root= RefactoringASTParser.parseWithASTProvider(cu, true, null);
			final CompilationUnitRewrite rewrite= new CompilationUnitRewrite(cu, root);
			final IMember member= fTypeParameter.getDeclaringMember();
			ASTNode declaration= null;
			if (member instanceof IMethod) {
				declaration= ASTNodeSearchUtil.getMethodDeclarationNode((IMethod) member, root);
			} else if (member instanceof IType) {
				declaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode((IType) member, root);
			} else {
				JavaPlugin.logErrorMessage("Unexpected sub-type of IMember: " + member.getClass().getName()); //$NON-NLS-1$
				Assert.isTrue(false);
			}
			monitor.worked(1);
			final RenameTypeParameterVisitor visitor= new RenameTypeParameterVisitor(rewrite, fTypeParameter.getNameRange(), status);
			if (declaration != null)
				declaration.accept(visitor);
			fChange= visitor.getResult();
		} finally {
			monitor.done();
		}
		return status;
	}

	protected final String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fTypeParameter);
	}

	public final String getCurrentElementName() {
		return fTypeParameter.getElementName();
	}

	public final Object[] getElements() {
		return new Object[] { fTypeParameter};
	}

	public final String getIdentifier() {
		return IDENTIFIER;
	}
	
	public final Object getNewElement() throws CoreException {
		final IMember member= fTypeParameter.getDeclaringMember();
		if (member instanceof IType) {
			final IType type= (IType) member;
			return type.getTypeParameter(getNewElementName());
		} else if (member instanceof IMethod) {
			final IMethod method= (IMethod) member;
			return method.getTypeParameter(getNewElementName());
		} else {
			JavaPlugin.logErrorMessage("Unexpected sub-type of IMember: " + member.getClass().getName()); //$NON-NLS-1$
			Assert.isTrue(false);
		}
		return null;
	}

	public final String getProcessorName() {
		return Messages.format(RefactoringCoreMessages.RenameTypeParameterProcessor_name, new String[] { fTypeParameter.getElementName(), getNewElementName()});
	}

	public final boolean getUpdateReferences() {
		return fUpdateReferences;
	}

	public final RefactoringStatus initialize(final RefactoringArguments arguments) {
		if (arguments instanceof JavaRefactoringArguments) {
			final JavaRefactoringArguments extended= (JavaRefactoringArguments) arguments;
			final String parameter= extended.getAttribute(ATTRIBUTE_PARAMETER);
			if (parameter == null || "".equals(parameter)) //$NON-NLS-1$
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_PARAMETER));
			final String handle= extended.getAttribute(JavaRefactoringDescriptor.ATTRIBUTE_INPUT);
			if (handle != null) {
				final IJavaElement element= JavaRefactoringDescriptor.handleToElement(extended.getProject(), handle);
				if (element == null)
					return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_input_not_exists, ID_RENAME_TYPE_PARAMETER));
				else {
					if (element instanceof IMethod)
						fTypeParameter= ((IMethod) element).getTypeParameter(parameter);
					else if (element instanceof IType)
						fTypeParameter= ((IType) element).getTypeParameter(parameter);
					else
						return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { handle, JavaRefactoringDescriptor.ATTRIBUTE_INPUT}));
					if (fTypeParameter == null || !fTypeParameter.exists())
						return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_input_not_exists, ID_RENAME_TYPE_PARAMETER));
				}
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptor.ATTRIBUTE_INPUT));
			final String name= extended.getAttribute(JavaRefactoringDescriptor.ATTRIBUTE_NAME);
			if (name != null && !"".equals(name)) //$NON-NLS-1$
				setNewElementName(name);
			else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptor.ATTRIBUTE_NAME));
			final String references= extended.getAttribute(ATTRIBUTE_REFERENCES);
			if (references != null) {
				fUpdateReferences= Boolean.valueOf(references).booleanValue();
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REFERENCES));
		} else
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments);
		return new RefactoringStatus();
	}

	public final boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameAvailable(fTypeParameter);
	}

	public boolean needsSavedEditors() {
		return false;
	}

	public final void setUpdateReferences(final boolean update) {
		fUpdateReferences= update;
	}
}