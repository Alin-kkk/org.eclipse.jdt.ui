/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.refactoring;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameCompilationUnitRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameFieldRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameJavaProjectRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenamePackageRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameSourceFolderRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameTypeRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IRenameRefactoring;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringExecutionHelper;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringPreferences;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringSupport;
import org.eclipse.jdt.internal.ui.reorg.IRefactoringRenameSupport;

/**
 * Central access point to execute rename refactorings.
 * 
 * @since 2.1
 */
public class RenameSupport {

	private IRefactoringRenameSupport fSupport;
	private IJavaElement fElement;
	private RefactoringStatus fPreCheckStatus;
	
	/**
	 * Executes some light weight precondition checking. If the returned status
	 * is an error then the refactoring can't be executed at all. However,
	 * returning an OK status doesn't guarantee that the refactoring can be
	 * executed. It may still fail while performing the exhaustive precondition
	 * checking done inside the methods <code>openDialog</code> or
	 * <code>perform</code>.
	 * 
	 * The method is mainly used to determine enable/disablement of actions.
	 * 
	 * @return the result of the light weight precondition checking.
	 * 
	 * @throws if an unexpected exception occurs while performing the checking.
	 * 
	 * @see #openDialog(Shell)
	 * @see #perform(Shell, IRunnableContext)
	 */
	public IStatus preCheck() throws CoreException {
		ensureChecked();
		if (fPreCheckStatus.hasFatalError())
			return fPreCheckStatus.getFirstEntry(RefactoringStatus.FATAL).asStatus();
		else
			return new Status(IStatus.OK, JavaPlugin.getPluginId(), 0, "", null);
	}

	/**
	 * Opens the refactoring dialog for this rename support. 
	 * 
	 * @param parent a shell used as a parent for the refactoring dialog.
	 * @throws CoreException if an unexpected exception occurs while opening the
	 * dialog.
	 */
	public void openDialog(Shell parent) throws CoreException {
		ensureChecked();
		if (fPreCheckStatus.hasFatalError()) {
			showInformation(parent, fPreCheckStatus);
			return; 
		}
		fSupport.rename(parent, fElement);
	}
	
	/**
	 * Executes the rename refactoring without showing a dialog to gather
	 * additional user input (e.g. the new name of the <tt>IJavaElement</tt>,
	 * ...). Only an error dialog is shown (if necessary) to present the result
	 * of the refactoring's full precondition checking.
	 * 
	 * @param parent a shell used as a parent for the error dialog.
	 * @param context a <tt>IRunnableContext</tt> to execute the operation.
	 * 
	 * @throws InterruptedException if the operation has been canceled by the
	 * user.
	 * @throws InvocationTargetException if an error occured while executing the
	 * operation.
	 * 
	 * @see #openDialog(Shell)
	 * @see IRunnableContext#run(boolean, boolean, org.eclipse.jface.operation.IRunnableWithProgress)
	 */
	public void perform(Shell parent, IRunnableContext context) throws InterruptedException, InvocationTargetException {
		try {
			ensureChecked();
			if (fPreCheckStatus.hasFatalError()) {
				showInformation(parent, fPreCheckStatus);
				return; 
			}
		} catch (CoreException e){
			throw new InvocationTargetException(e);
		}
		RefactoringExecutionHelper helper= new RefactoringExecutionHelper(fSupport.getRefactoring(),
			RefactoringPreferences.getStopSeverity(), parent, context);
		helper.perform();
	}
	
	/** Flag indication that no additional update is to be performed. */
	public static final int NONE= 0;
	
	/** Flag indicating that references are to be updated as well. */
	public static final int UPDATE_REFERENCES= 1 << 0;
	
	/** Flag indicating that Java doc comments are to be updated as well. */
	public static final int UPDATE_JAVADOC_COMMENTS= 1 << 1;
	
	/** Flag indicating that regular comments are to be updated as well. */
	public static final int UPDATE_REGULAR_COMMENTS= 1 << 2;
	
	/** Flag indicating that string literals are to be updated as well. */
	public static final int UPDATE_STRING_LITERALS= 1 << 3;

	/** Flag indicating that the getter method is to be updated as well. */
	public static final int UPDATE_GETTER_METHOD= 1 << 4;

	/** Flag indicating that the setter method is to be updated as well. */
	public static final int UPDATE_SETTER_METHOD= 1 << 5;

	private RenameSupport(IRefactoringRenameSupport support, IJavaElement element) {
		fSupport= support;
		fElement= element;
	}

	/**
	 * Creates a new rename support for the given <tt>IJavaProject</tt>.
	 * 
	 * @param project the <tt>IJavaProject</tt> to be renamed.
	 * @param newName the project's new name. <code>null</code> is a valid
	 * value indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code> or <code>NONE</code>.
	 * @return the <tt>RenameSupport</tt>.
	 * @throws CoreException if an unexpected error occured while creating
	 * the <tt>RenameSupport</tt>.
	 */
	public static RenameSupport create(IJavaProject project, String newName, int flags) throws CoreException {
		RefactoringSupport.JavaProject support= new RefactoringSupport.JavaProject(project);
		RenameJavaProjectRefactoring refactoring= support.getSpecificRefactoring();
		setNewName(refactoring, newName);
		refactoring.setUpdateReferences(updateReferences(flags));
		return new RenameSupport(support, project);
	}
	
	/**
	 * Creates a new rename support for the given <tt>IPackageFragmentRoot</tt>.
	 * 
	 * @param root the <tt>IPackageFragmentRoot</tt> to be renamed.
	 * @param newName the package fragment roor's new name. <code>null</code> is
	 * a valid value indicating that no new name is provided.
	 * @return the <tt>RenameSupport</tt>.
	 * @throws CoreException if an unexpected error occured while creating
	 * the <tt>RenameSupport</tt>.
	 */
	public static RenameSupport create(IPackageFragmentRoot root, String newName) throws CoreException {
		RefactoringSupport.SourceFolder support= new RefactoringSupport.SourceFolder(root);
		RenameSourceFolderRefactoring refactoring= support.getSpecificRefactoring();
		setNewName(refactoring, newName);
		return new RenameSupport(support, root);
	}
	
	/**
	 * Creates a new rename support for the given <tt>IPackageFragment</tt>.
	 * 
	 * @param fragment the <tt>IPackageFragment</tt> to be renamed.
	 * @param newName the package fragement's new name. <code>null</code> is a
	 * valid value indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, <code>UPDATE_JAVADOC_COMMENTS</code>,
	 * <code>UPDATE_REGULAR_COMMENTS</code> and
	 * <code>UPDATE_STRING_LITERALS</code>, or their bitwise OR, or
	 * <code>NONE</code>.
	 * @return the <tt>RenameSupport</tt>.
	 * @throws CoreException if an unexpected error occured while creating
	 * the <tt>RenameSupport</tt>.
	 */
	public static RenameSupport create(IPackageFragment fragment, String newName, int flags) throws CoreException {
		RefactoringSupport.PackageFragment support= new RefactoringSupport.PackageFragment(fragment);
		RenamePackageRefactoring refactoring= support.getSpecificRefactoring();
		setNewName(refactoring, newName);
		refactoring.setUpdateReferences(updateReferences(flags));
		refactoring.setUpdateJavaDoc(updateJavadocComments(flags));
		refactoring.setUpdateComments(updateRegularComments(flags));
		refactoring.setUpdateStrings(updateStringLiterals(flags));
		return new RenameSupport(support, fragment);
	}
	
	/**
	 * Creates a new rename support for the given <tt>ICompilationUnit</tt>.
	 * 
	 * @param unit the <tt>ICompilationUnit</tt> to be renamed.
	 * @param newName the compilation unit's new name. <code>null</code> is a
	 * valid value indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, <code>UPDATE_JAVADOC_COMMENTS</code>,
	 * <code>UPDATE_REGULAR_COMMENTS</code> and
	 * <code>UPDATE_STRING_LITERALS</code>, or their bitwise OR, or
	 * <code>NONE</code>.
	 * @return the <tt>RenameSupport</tt>.
	 * @throws CoreException if an unexpected error occured while creating
	 * the <tt>RenameSupport</tt>.
	 */
	public static RenameSupport create(ICompilationUnit unit, String newName, int flags) throws CoreException {
		RefactoringSupport.CompilationUnit support= new RefactoringSupport.CompilationUnit(unit);
		RenameCompilationUnitRefactoring refactoring= support.getSpecificRefactoring();
		setNewName(refactoring, newName);
		refactoring.setUpdateReferences(updateReferences(flags));
		refactoring.setUpdateJavaDoc(updateJavadocComments(flags));
		refactoring.setUpdateComments(updateRegularComments(flags));
		refactoring.setUpdateStrings(updateStringLiterals(flags));
		return new RenameSupport(support, unit);
	}
	
	/**
	 * Creates a new rename support for the given <tt>IType</tt>.
	 * 
	 * @param type the <tt>IType</tt> to be renamed.
	 * @param newName the type's new name. <code>null</code> is a valid value
	 * indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, <code>UPDATE_JAVADOC_COMMENTS</code>,
	 * <code>UPDATE_REGULAR_COMMENTS</code> and
	 * <code>UPDATE_STRING_LITERALS</code>, or their bitwise OR, or
	 * <code>NONE</code>.
	 * @return the <tt>RenameSupport</tt>.
	 * @throws CoreException if an unexpected error occured while creating
	 * the <tt>RenameSupport</tt>.
	 */
	public static RenameSupport create(IType type, String newName, int flags) throws CoreException {
		RefactoringSupport.Type support= new RefactoringSupport.Type(type);
		RenameTypeRefactoring refactoring= support.getSpecificRefactoring();
		setNewName(refactoring, newName);
		refactoring.setUpdateReferences(updateReferences(flags));
		refactoring.setUpdateJavaDoc(updateJavadocComments(flags));
		refactoring.setUpdateComments(updateRegularComments(flags));
		refactoring.setUpdateStrings(updateStringLiterals(flags));
		return new RenameSupport(support, type);
	}
	
	/**
	 * Creates a new rename support for the given <tt>IMethod</tt>.
	 * 
	 * @param method the <tt>IMethod</tt> to be renamed.
	 * @param newName the method's new name. <code>null</code> is a valid value
	 * indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code> or <code>NONE</code>.
	 * @return the <tt>RenameSupport</tt>.
	 * @throws CoreException if an unexpected error occured while creating
	 * the <tt>RenameSupport</tt>.
	 */
	public static RenameSupport create(IMethod method, String newName, int flags) throws CoreException {
		RefactoringSupport.Method support= new RefactoringSupport.Method(method);
		RenameMethodRefactoring refactoring= support.getSpecificRefactoring();
		setNewName(refactoring, newName);
		refactoring.setUpdateReferences(updateReferences(flags));
		return new RenameSupport(support, method);
	}
	
	/**
	 * Creates a new rename support for the given <tt>IField</tt>.
	 * 
	 * @param method the <tt>IField</tt> to be renamed.
	 * @param newName the field's new name. <code>null</code> is a valid value
	 * indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, <code>UPDATE_JAVADOC_COMMENTS</code>,
	 * <code>UPDATE_REGULAR_COMMENTS</code>,
	 * <code>UPDATE_STRING_LITERALS</code>, </code>UPDATE_GETTER_METHOD</code>
	 * and </code>UPDATE_SETTER_METHOD</code>, or their bitwise OR, or
	 * <code>NONE</code>.
	 * @return the <tt>RenameSupport</tt>.
	 * @throws CoreException if an unexpected error occured while creating
	 * the <tt>RenameSupport</tt>.
	 */
	public static RenameSupport create(IField field, String newName, int flags) throws CoreException {
		RefactoringSupport.Field support= new RefactoringSupport.Field(field);
		RenameFieldRefactoring refactoring= support.getSpecificRefactoring();
		setNewName(refactoring, newName);
		refactoring.setUpdateReferences(updateReferences(flags));
		refactoring.setUpdateJavaDoc(updateJavadocComments(flags));
		refactoring.setUpdateComments(updateRegularComments(flags));
		refactoring.setUpdateStrings(updateStringLiterals(flags));
		refactoring.setRenameGetter(updateGetterMethod(flags));
		refactoring.setRenameSetter(updateSetterMethod(flags));
		return new RenameSupport(support, field);
	}
	
	private static void setNewName(IRenameRefactoring refactoring, String newName) {
		if (newName != null)
			refactoring.setNewName(newName);
	}
	
	private static boolean updateReferences(int flags) {
		return (flags & UPDATE_REFERENCES) != 0;
	}
	
	private static boolean updateJavadocComments(int flags) {
		return (flags & UPDATE_JAVADOC_COMMENTS) != 0;
	}
	
	private static boolean updateRegularComments(int flags) {
		return (flags & UPDATE_REGULAR_COMMENTS) != 0;
	}
	
	private static boolean updateStringLiterals(int flags) {
		return (flags & UPDATE_STRING_LITERALS) != 0;
	}
	
	private static boolean updateGetterMethod(int flags) {
		return (flags & UPDATE_GETTER_METHOD) != 0;
	}
	
	private static boolean updateSetterMethod(int flags) {
		return (flags & UPDATE_SETTER_METHOD) != 0;
	}
	
	private void ensureChecked() throws CoreException {
		if (fPreCheckStatus == null)
			fPreCheckStatus= fSupport.lightCheck();
	}
	
	private void showInformation(Shell parent, RefactoringStatus status) {
		String message= status.getFirstMessage(RefactoringStatus.FATAL);
		MessageDialog.openInformation(parent, fSupport.getRefactoring().getName(), message);
	}
}
