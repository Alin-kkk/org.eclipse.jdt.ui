/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.nls;

import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

/**
 * good citizen problems - wizard is only valid after constructor (when the pages toggle
 * some values and force an validate the validate can't get a wizard)
 */
public class ExternalizeWizard extends RefactoringWizard {

	public ExternalizeWizard(NLSRefactoring refactoring) {
		super(refactoring,CHECK_INITIAL_CONDITIONS_ON_OPEN | WIZARD_BASED_USER_INTERFACE);
		setDefaultPageTitle(NLSUIMessages.getFormattedString("ExternalizeWizard.page.title", refactoring.getCu().getElementName())); //$NON-NLS-1$
		setWindowTitle(NLSUIMessages.getString("ExternalizeWizard.name"));//$NON-NLS-1$
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_EXTERNALIZE_STRINGS);
	}

	/**
	 * @see RefactoringWizard#addUserInputPages()
	 */
	protected void addUserInputPages() {

		NLSRefactoring nlsRefac= (NLSRefactoring) getRefactoring();
		ExternalizeWizardPage page= new ExternalizeWizardPage(nlsRefac);
		page.setMessage(NLSUIMessages.getString("ExternalizeWizard.select")); //$NON-NLS-1$
		addPage(page);

		/*ExternalizeWizardPage2 page2= new ExternalizeWizardPage2(nlsRefac);
		 page2.setMessage(NLSUIMessages.getString("wizard.select_values")); //$NON-NLS-1$
		 addPage(page2);*/
	}

	public boolean canFinish() {
		IWizardPage page= getContainer().getCurrentPage();
		return super.canFinish() && !(page instanceof ExternalizeWizardPage);
	}
}
