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

package org.eclipse.jdt.internal.ui.text.correction;

import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;

public class ReplaceCorrectionProposal extends CUCorrectionProposal {
	
	private String fReplacementString;
	private int fOffset;
	private int fLength;
	
	public ReplaceCorrectionProposal(String label, ICompilationUnit cu, int offset, int length, String replacementString, int relevance) {
		super(label, cu, relevance);
		fReplacementString= replacementString;
		fOffset= offset;
		fLength= length;
	}	

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.CUCorrectionProposal#createCompilationUnitChange(String, ICompilationUnit, TextEdit)
	 */
	protected CompilationUnitChange createCompilationUnitChange(String name, ICompilationUnit cu, TextEdit root) throws CoreException {
		CompilationUnitChange change= super.createCompilationUnitChange(name, cu, root);
		TextEdit edit= new ReplaceEdit(fOffset, fLength, fReplacementString);
		root.add(edit);
		return change;
	}
	
}
