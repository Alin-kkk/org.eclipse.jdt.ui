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
package org.eclipse.jsp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.text.edits.SimpleTextEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.core.indexsearch.ISearchResultCollector;

import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.participants.IRefactoringProcessor;
import org.eclipse.jdt.internal.corext.refactoring.participants.RenameParticipant;


public class RenameTypeParticipant extends RenameParticipant {

	private IType fType;

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRefactoringParticipant#initialize(org.eclipse.jdt.internal.corext.refactoring.participants.IRefactoringProcessor, java.lang.Object)
	 */
	public void initialize(IRefactoringProcessor processor, Object element) throws CoreException {
		super.initialize(processor);
		fType= (IType)element;
	}

	public boolean operatesOn(Object element) {
		return fType.equals(element);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#isAvailable()
	 */
	public boolean isAvailable() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#checkActivation()
	 */
	public RefactoringStatus checkActivation() throws CoreException {
		return new RefactoringStatus();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#checkInput(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws CoreException {
		return new RefactoringStatus();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#createChange(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IChange createChange(IProgressMonitor pm) throws CoreException {
		final Map changes= new HashMap();
		final String newName= computeNewName();
		ISearchResultCollector collector= new ISearchResultCollector() {
			public void accept(IResource resource, int start, int length) throws CoreException {
				TextFileChange change= (TextFileChange)changes.get(resource);
				if (change == null) {
					change= new TextFileChange(resource.getName(), (IFile)resource);
					changes.put(resource, change);
				}
				change.addTextEdit("Update type reference", SimpleTextEdit.createReplace(start, length, newName)); //$NON-NLS-1$
			}
		};
		JspUIPlugin.getDefault().search(new JspTypeQuery(fType), collector, pm);
		
		if (changes.size() == 0)
			return null;
		CompositeChange result= new CompositeChange("JSP updates"); //$NON-NLS-1$
		for (Iterator iter= changes.values().iterator(); iter.hasNext();) {
			result.add((IChange)iter.next());
		}
		return result;
	}
	
	private String computeNewName() {
		String currentName= fType.getFullyQualifiedName();
		int pos= currentName.lastIndexOf('.');
		if (pos == -1)
			return getNewName();
		return currentName.substring(0, pos + 1) + getNewName();
	}

}
