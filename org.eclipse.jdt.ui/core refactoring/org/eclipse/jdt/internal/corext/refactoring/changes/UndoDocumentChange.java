/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.UndoEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

import org.eclipse.jdt.internal.corext.refactoring.base.Change;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.ltk.internal.refactoring.core.TextChanges;

/**
 * TODO
 * @since 3.0
 */
public class UndoDocumentChange extends Change {
	
	private String fName;
	private UndoEdit fUndo;
	private IDocument fDocument;
	private int fLength;
	
	public UndoDocumentChange(String name, IDocument document, UndoEdit undo) {
		fName= name;
		fUndo= undo;
		fDocument= document;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getName() {
		return fName;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Object getModifiedElement() {
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void initializeValidationData(IProgressMonitor pm) throws CoreException {
		fLength= fDocument.getLength();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 1); //$NON-NLS-1$
		RefactoringStatus result= TextChanges.isValid(fDocument, fLength);
		pm.worked(1);
		return result;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Change perform(IProgressMonitor pm) throws CoreException {
		try {
			UndoEdit redo= fUndo.apply(fDocument, TextEdit.CREATE_UNDO);
			Change result= new UndoDocumentChange(getName(), fDocument, redo);
			return result;
		} catch (BadLocationException e) {
			throw Changes.asCoreException(e);
		}
	}
}