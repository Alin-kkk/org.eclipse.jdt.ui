/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.AbstractJavaElementRenameChange;
import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.changes.*;
import org.eclipse.jdt.internal.corext.refactoring.*;


public class RenamePackageChange extends AbstractJavaElementRenameChange {

	public RenamePackageChange(IPackageFragment pack, String newName) throws JavaModelException{
		this(pack.getCorrespondingResource().getFullPath(), pack.getElementName(), newName);
		Assert.isTrue(!pack.isReadOnly(), RefactoringCoreMessages.getString("RenamePackageChange.assert.read_only")); //$NON-NLS-1$
	}
	
	private RenamePackageChange(IPath resourcePath, String oldName, String newName){
		super(resourcePath, oldName, newName);
	}
		
	protected IPath createPath(String packageName){
		return new Path(packageName.replace('.', IPath.SEPARATOR));
	}
	
	private IPath createNewPath(){
		IPackageFragment oldPackage= (IPackageFragment)getModifiedLanguageElement();
		IPath oldPackageName= createPath(oldPackage.getElementName());
		IPath newPackageName= createPath(getNewName());
		return getResourcePath().removeLastSegments(oldPackageName.segmentCount()).append(newPackageName);
	}
	
	public String getName() {
		return RefactoringCoreMessages.getFormattedString("RenamePackageChange.name", new String[]{getOldName(), getNewName()}); //$NON-NLS-1$
	}

	/* non java-doc
	 * @see AbstractRenameChange#createUndoChange()
	 */
	protected IChange createUndoChange() {
		return new RenamePackageChange(createNewPath(), getNewName(), getOldName());
	}
	
	protected void doRename(IProgressMonitor pm) throws JavaModelException {
		((IPackageFragment)getModifiedLanguageElement()).rename(getNewName(), false, pm);
	}

	public RefactoringStatus aboutToPerform(ChangeContext context, IProgressMonitor pm) {
		// PR: 1GEWDUH: ITPJCORE:WINNT - Refactoring - Unable to undo refactor change
		RefactoringStatus result= super.aboutToPerform(context, pm);
		IJavaElement element= (IJavaElement)getModifiedLanguageElement();
		if (element != null && element.exists() && context.getUnsavedFiles().length > 0 && element instanceof IPackageFragment) {
			IPackageFragment pack= (IPackageFragment)element;
			try {
				ICompilationUnit[] units= pack.getCompilationUnits();
				if (units == null || units.length == 0)
					return result;
					
				pm.beginTask("", units.length); //$NON-NLS-1$
				for (int i= 0; i < units.length; i++) {
					pm.subTask(RefactoringCoreMessages.getFormattedString("RenamePackageChange.checking_change", element.getElementName())); //$NON-NLS-1$
					checkIfResourceIsUnsaved(units[i], result, context);
					pm.worked(1);
				}
				pm.done();
			} catch (JavaModelException e) {
				handleJavaModelException(e, result);
			}
		}
		return result;
	}
}