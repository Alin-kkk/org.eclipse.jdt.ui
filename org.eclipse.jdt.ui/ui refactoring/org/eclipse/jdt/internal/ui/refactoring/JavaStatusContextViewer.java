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
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;

import org.eclipse.core.resources.IFile;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.part.FileEditorInput;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.base.Context;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStringStatusContext;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.InternalClassFileEditorInput;

import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;


public class JavaStatusContextViewer extends SourceContextViewer {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.IStatusContextViewer#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		getSourceViewer().configure(new JavaSourceViewerConfiguration(JavaPlugin.getDefault().getJavaTextTools(), null));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.IStatusContextViewer#setInput(java.lang.Object)
	 */
	public void setInput(Context context) throws JavaModelException {
		if (context instanceof JavaStatusContext) {
			JavaStatusContext jsc= (JavaStatusContext)context;
			IDocument document= null;
			if (jsc.isBinary()) {
				IEditorInput editorInput= new InternalClassFileEditorInput(jsc.getClassFile());
				document= getDocument(JavaPlugin.getDefault().getClassFileDocumentProvider(), editorInput);
			} else {
				ICompilationUnit cunit= jsc.getCompilationUnit();
				if (cunit.isWorkingCopy()) {
					document= newJavaDocument(cunit.getSource());
				} else {
					IEditorInput editorInput= new FileEditorInput((IFile)cunit.getResource());
					document= getDocument(JavaPlugin.getDefault().getCompilationUnitDocumentProvider(), editorInput);
				}
			}
			setInput(document, jsc.getSourceRange());
		} else if (context instanceof JavaStringStatusContext) {
			JavaStringStatusContext sc= (JavaStringStatusContext)context;
			setInput(newJavaDocument(sc.getSource()), sc.getSourceRange());
		}
	}
	
	private IDocument newJavaDocument(String source) {
		IDocument result= new Document(source);
		IDocumentPartitioner partitioner= JavaPlugin.getDefault().getJavaTextTools().createDocumentPartitioner();
		result.setDocumentPartitioner(partitioner);
		partitioner.connect(result);
		return result;
	}
}
