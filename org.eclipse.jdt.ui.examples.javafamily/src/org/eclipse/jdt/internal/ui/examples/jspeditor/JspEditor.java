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

package org.eclipse.jdt.internal.ui.examples.jspeditor;

import org.eclipse.jface.text.source.DefaultAnnotationAccess;
import org.eclipse.jface.text.source.IAnnotationAccess;

import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.ui.editors.text.TextEditor;


/**
 * A simple JSP Editor.
 * 
 * @since 3.0
 */
public class JspEditor extends TextEditor {

	/**
	 * Creates a new JSP editor.
	 */
	public JspEditor() {
		super();
		setSourceViewerConfiguration(new JspSourceViewerConfiguration(this));
		setDocumentProvider(new FileDocumentProvider());
	}

	/*
	 * @see TextEditor#createAnnotationAccess()
	 */
	protected IAnnotationAccess createAnnotationAccess() {
		return new DefaultAnnotationAccess();
	}
}
