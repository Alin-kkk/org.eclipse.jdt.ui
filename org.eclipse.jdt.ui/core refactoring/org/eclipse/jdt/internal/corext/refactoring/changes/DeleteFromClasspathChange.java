/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.NullChange;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.Change;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeAbortException;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;

public class DeleteFromClasspathChange extends Change {

	private final String fProjectHandle;
	private final IPath fPathToDelete;
	
	private IPath fPath;
	private IPath fSourceAttachmentPath;
	private IPath fSourceAttachmentRootPath;
	private int fEntryKind;
	private int fContentKind;
	
	public DeleteFromClasspathChange(IPackageFragmentRoot root) {
		this(root.getPath(), root.getJavaProject());
	}
	
	DeleteFromClasspathChange(IPath pathToDelete, IJavaProject project){
		Assert.isNotNull(pathToDelete);
		fPathToDelete= pathToDelete;
		fProjectHandle= project.getHandleIdentifier();
	}
	
	/* non java-doc
	 * @see IChange#perform(ChangeContext, IProgressMonitor)
	 */
	public void perform(ChangeContext context, IProgressMonitor pm)	throws JavaModelException, ChangeAbortException {
		pm.beginTask(getName(), 1);
		try{
			if (!isActive())
				return;
			IJavaProject project= getJavaProject();
			IClasspathEntry[] cp= project.getRawClasspath();
			IClasspathEntry[] newCp= new IClasspathEntry[cp.length-1];
			int i= 0; 
			int j= 0;
			while (j < newCp.length) {
				IClasspathEntry current= JavaCore.getResolvedClasspathEntry(cp[i]);
				if (current != null && toBeDeleted(current)) {
					i++;
					setDeletedEntryProperties(current);
				} 

				newCp[j]= cp[i];
				i++;
				j++;
			}
			
			IClasspathEntry last= JavaCore.getResolvedClasspathEntry(cp[cp.length - 1]);
			if (last != null && toBeDeleted(last))
				setDeletedEntryProperties(last);
				
			project.setRawClasspath(newCp, pm);
		} finally{
			pm.done();
		}
	}
	
	private boolean toBeDeleted(IClasspathEntry entry){
		if (entry == null) //safety net
			return false; 
		return fPathToDelete.equals(entry.getPath());
	}
	
	private void setDeletedEntryProperties(IClasspathEntry entry){
		fEntryKind= entry.getEntryKind();
		fContentKind= entry.getContentKind();
		fPath= entry.getPath();
		fSourceAttachmentPath= entry.getSourceAttachmentPath();
		fSourceAttachmentRootPath= entry.getSourceAttachmentRootPath();
	}
	
	private IJavaProject getJavaProject(){
		return (IJavaProject)JavaCore.create(fProjectHandle);
	}
	
	public IChange getUndoChange() {
		if (!isActive())
			return new NullChange();

		return new AddToClasspathChange(getJavaProject(), fEntryKind, fContentKind, 
										fPath, fSourceAttachmentPath, fSourceAttachmentRootPath);
	}

	/* non java-doc
	 * @see IChange#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.getString("DeleteFromClassPathChange.remove") + getJavaProject().getElementName(); //$NON-NLS-1$
	}

	/* non java-doc
	 * @see IChange#getModifiedLanguageElement()
	 */
	public Object getModifiedLanguageElement() {
		return getJavaProject();
	}
}
