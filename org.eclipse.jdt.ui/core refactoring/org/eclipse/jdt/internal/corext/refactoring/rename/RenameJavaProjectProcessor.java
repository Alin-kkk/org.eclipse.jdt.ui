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
package org.eclipse.jdt.internal.corext.refactoring.rename;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameJavaProjectChange;
import org.eclipse.jdt.internal.corext.refactoring.participants.IResourceModifications;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.participants.RenameProcessor;
import org.eclipse.jdt.internal.corext.refactoring.participants.ResourceModifications;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;


public class RenameJavaProjectProcessor extends RenameProcessor implements IReferenceUpdating {

	private IJavaProject fProject;
	private boolean fUpdateReferences;
	
	//---- IRefactoringProcessor ---------------------------------------------------

	public void initialize(Object element) {
		if (!(element instanceof IJavaProject))
			return;
		fProject= (IJavaProject)element;
		setNewElementName(fProject.getElementName());
		fUpdateReferences= true;
	}
	
	public boolean isAvailable() throws CoreException {
		if (fProject == null)
			return false;
		if (! Checks.isAvailable(fProject))	
			return false;
		if (! fProject.isConsistent())
			return false;	
		return true;
	}
	
	public String getProcessorName() {
		return RefactoringCoreMessages.getFormattedString(
			"RenameJavaProjectRefactoring.rename", //$NON-NLS-1$
			new String[]{getCurrentElementName(), getNewElementName()});
	}
	
	public IProject[] getScope() throws CoreException {
		return JavaProcessors.computeScope(fProject);
	}
	
	public Object getElement() {
		return fProject;
	}

	public IResourceModifications getResourceModifications() throws CoreException {
		ResourceModifications result= new ResourceModifications();
		result.setRename(fProject.getProject(), getNewElementName());
		return result;		
	}
		 
	public Object getNewElement() throws CoreException {
		IPath newPath= fProject.getPath().removeLastSegments(1).append(getNewElementName());
		return JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().findMember(newPath));
	}
	
	//---- IReferenceUpdating --------------------------------------
		
	public boolean canEnableUpdateReferences() {
		return true;
	}

	public void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}

	public boolean getUpdateReferences() {
		return fUpdateReferences;
	}
		
	//---- IRenameProcessor ----------------------------------------------
	
	public String getCurrentElementName() {
		return fProject.getElementName();
	}
	
	public RefactoringStatus checkActivation() throws CoreException {
		return new RefactoringStatus();
	}
	
	public RefactoringStatus checkNewElementName(String newName) throws CoreException {
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= RefactoringStatus.create(ResourcesPlugin.getWorkspace().validateName(newName, IResource.PROJECT));
		if (result.hasFatalError())
			return result;
		
		if (projectNameAlreadyExists(newName))
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("RenameJavaProjectRefactoring.already_exists")); //$NON-NLS-1$
		
		return new RefactoringStatus();
	}
	
	public RefactoringStatus checkInput(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 1); //$NON-NLS-1$
		try{
			if (isReadOnly()){
				String message= RefactoringCoreMessages.getFormattedString("RenameJavaProjectRefactoring.read_only", //$NON-NLS-1$
									fProject.getElementName());
				return RefactoringStatus.createErrorStatus(message);
			}	
			return new RefactoringStatus();
		} finally{
			pm.done();
		}	
	}
	
	private boolean isReadOnly() throws CoreException {
		return fProject.getResource().isReadOnly();
	}
	
	private boolean projectNameAlreadyExists(String newName){
		return fProject.getJavaModel().getJavaProject(newName).exists();
	}

	//--- changes 
	
	public IChange createChange(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 1); //$NON-NLS-1$
		try{
			return new RenameJavaProjectChange(fProject, fNewElementName, fUpdateReferences);
		} finally{
			pm.done();
		}	
	}
}