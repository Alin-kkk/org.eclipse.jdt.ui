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
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.jdt.internal.ui.JavaPluginImages;

import org.eclipse.jdt.internal.corext.refactoring.structure.PushDownRefactoring;

public class PushDownWizard extends RefactoringWizard {

	public PushDownWizard(PushDownRefactoring ref, String pageTitle, String errorPageContextHelpId) {
		super(ref, pageTitle, errorPageContextHelpId);
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_REFACTOR_PULL_UP);//XXX incorrect icon
	}

	/* non java-doc
	 * @see RefactoringWizard#addUserInputPages
	 */ 
	protected void addUserInputPages(){
		addPage(new PushDownInputPage());
	}
}
