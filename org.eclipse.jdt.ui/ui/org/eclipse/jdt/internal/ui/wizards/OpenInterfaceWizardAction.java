/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards;

import org.eclipse.jface.wizard.Wizard;

import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;

public class OpenInterfaceWizardAction extends AbstractOpenWizardAction {

	public OpenInterfaceWizardAction() {
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.OPEN_INTERFACE_WIZARD_ACTION);
	}
	
	public OpenInterfaceWizardAction(String label, Class[] acceptedTypes) {
		super(label, acceptedTypes, false);
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.OPEN_INTERFACE_WIZARD_ACTION);
	}
	
	protected Wizard createWizard() { 
		return new NewInterfaceCreationWizard(); 
	}
	
	protected boolean shouldAcceptElement(Object obj) { 
		return isOnBuildPath(obj) && !isInArchive(obj);
	}
}