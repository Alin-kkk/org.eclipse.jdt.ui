/*******************************************************************************
 * Copyright (c) 2003 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.participants;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IProject;

import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;


public interface IRefactoringProcessor {
	
	public void initialize(Object element) throws CoreException;
	
	public boolean isAvailable() throws CoreException;
	
	public String getProcessorName();
	
	public IProject[] getScope() throws CoreException;
	
	public Object getElement();
	
	public Object[] getDerivedElements() throws CoreException;
	
	public IResourceModifications getResourceModifications() throws CoreException;
	
	public RefactoringStatus checkActivation() throws CoreException;
	
	public RefactoringStatus checkInput(IProgressMonitor pm) throws CoreException;
	
	public IChange createChange(IProgressMonitor pm) throws CoreException;
}
