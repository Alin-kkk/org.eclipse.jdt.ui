package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.Change;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeAbortException;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.reorg.INewNameQuery;

abstract class CompilationUnitReorgChange extends Change {

	private String fCuHandle;
	private String fOldPackageHandle;
	private String fNewPackageHandle;

	private INewNameQuery fNewNameQuery;
	
	CompilationUnitReorgChange(ICompilationUnit cu, IPackageFragment dest, INewNameQuery newNameQuery){
		fCuHandle= cu.getHandleIdentifier();
		fNewPackageHandle= dest.getHandleIdentifier();
		fNewNameQuery= newNameQuery;
		fOldPackageHandle= cu.getParent().getHandleIdentifier();
	}
	
	CompilationUnitReorgChange(ICompilationUnit cu, IPackageFragment dest){
		this(cu, dest, null);
	}
	
	CompilationUnitReorgChange(String oldPackageHandle, String newPackageHandle, String cuHandle){
		fOldPackageHandle= oldPackageHandle;
		fNewPackageHandle= newPackageHandle;
		fCuHandle= cuHandle;
	}
	
	/* non java-doc
	 * @see IChange#perform(ChangeContext, IProgressMonitor)
	 */
	public final void perform(ChangeContext context, IProgressMonitor pm) throws JavaModelException, ChangeAbortException {
		pm.beginTask(getName(), 1);
		try{
			if (!isActive())
				return;
			doPeform(new SubProgressMonitor(pm, 1));
		}catch (Exception e) {
			handleException(context, e);
			setActive(false);
		} finally {
			pm.done();
		}
	}
	
	abstract void doPeform(IProgressMonitor pm) throws JavaModelException;
	
	/*
	 * @see IChange#getModifiedLanguageElement()
	 */
	public Object getModifiedLanguageElement() {
		return getCu();
	}
	
	ICompilationUnit getCu(){
		return (ICompilationUnit)JavaCore.create(fCuHandle);
	}
	
	IPackageFragment getOldPackage(){
		return (IPackageFragment)JavaCore.create(fOldPackageHandle);
	}
	
	IPackageFragment getDestinationPackage(){
		return (IPackageFragment)JavaCore.create(fNewPackageHandle);
	}
	
	String getNewName() {
		if (fNewNameQuery == null)
			return null;
		return fNewNameQuery.getNewName();	
	}

	static String getPackageName(IPackageFragment pack){
		if (pack.isDefaultPackage())
			return RefactoringCoreMessages.getString("MoveCompilationUnitChange.default_package"); //$NON-NLS-1$
		else
			return pack.getElementName();	
	}
}

