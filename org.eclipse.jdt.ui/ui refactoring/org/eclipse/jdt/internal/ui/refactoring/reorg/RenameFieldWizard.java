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
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.IMethod;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.Dialog;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameFieldProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameRefactoring;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public class RenameFieldWizard extends RenameRefactoringWizard {

	public RenameFieldWizard() {
		super(RefactoringMessages.getString("RenameFieldWizard.defaultPageTitle"),  //$NON-NLS-1$
			RefactoringMessages.getString("RenameFieldWizard.inputPage.description"),   //$NON-NLS-1$
			JavaPluginImages.DESC_WIZBAN_REFACTOR_FIELD,
			IJavaHelpContextIds.RENAME_FIELD_WIZARD_PAGE);
	}

	protected RenameInputWizardPage createInputPage(String message, String initialSetting) {
		return new RenameFieldInputWizardPage(message, IJavaHelpContextIds.RENAME_FIELD_WIZARD_PAGE, initialSetting) {
			protected RefactoringStatus validateTextField(String text) {
				RefactoringStatus result= validateNewName(text);
				updateGetterSetterLabels();
				return result;
			}	
		};
	}

	private static class RenameFieldInputWizardPage extends RenameInputWizardPage {

		private Button fRenameGetter;
		private Button fRenameSetter;
		private String fGetterRenamingErrorMessage;
		private String fSetterRenamingErrorMessage;
	
		public RenameFieldInputWizardPage(String message, String contextHelpId, String initialValue) {
			super(message, contextHelpId, true, initialValue);
		}

		/* non java-doc
		 * @see DialogPage#createControl(org.eclipse.swt.widgets.Composite)
		 */
		public void createControl(Composite parent) {
			super.createControl(parent);
			Composite parentComposite= (Composite)getControl();
				
			Composite composite= new Composite(parentComposite, SWT.NONE);
			composite.setLayout(new GridLayout());
			composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		
			Label separator= new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);
			separator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			getGetterSetterRenamingEnablement();
				
			fRenameGetter= new Button(composite, SWT.CHECK);
			fRenameGetter.setEnabled(fGetterRenamingErrorMessage == null);
			fRenameGetter.setSelection(getRenameFieldProcessor().getRenameGetter());
			fRenameGetter.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fRenameGetter.addSelectionListener(new SelectionAdapter(){
				public void widgetSelected(SelectionEvent e) {
					getRenameFieldProcessor().setRenameGetter(fRenameGetter.getSelection());
				}
			});
		
			fRenameSetter= new Button(composite, SWT.CHECK);
			fRenameSetter.setEnabled(fSetterRenamingErrorMessage == null);
			fRenameSetter.setSelection(getRenameFieldProcessor().getRenameSetter());
			fRenameSetter.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fRenameSetter.addSelectionListener(new SelectionAdapter(){
				public void widgetSelected(SelectionEvent e) {
					getRenameFieldProcessor().setRenameSetter(fRenameSetter.getSelection());
				}
			});
		
			updateGetterSetterLabels();
			Dialog.applyDialogFont(composite);
		}
		private void getGetterSetterRenamingEnablement() {
			BusyIndicator.showWhile(getShell().getDisplay(), new Runnable(){
				public void run() {
					checkGetterRenamingEnablement();
					checkSetterRenamingEnablement();
				}
			});
		}
	
		protected void updateGetterSetterLabels(){
			fRenameGetter.setText(getRenameGetterLabel());
			fRenameSetter.setText(getRenameSetterLabel());
		}
	
		private String getRenameGetterLabel(){
			String defaultLabel= RefactoringMessages.getString("RenameFieldInputWizardPage.rename_getter"); //$NON-NLS-1$
			if (fGetterRenamingErrorMessage != null)
				return constructDisabledGetterRenamingLabel(defaultLabel);
			try {
				IMethod	getter= getRenameFieldProcessor().getGetter();
				if (getter == null || ! getter.exists())
					return defaultLabel;
				String oldGetterName= getter.getElementName();
				String newGetterName= createNewGetterName();
				return RefactoringMessages.getFormattedString("RenameFieldInputWizardPage.rename_getter_to", new String[]{oldGetterName, newGetterName}); //$NON-NLS-1$
			} catch(CoreException e) {
				JavaPlugin.log(e)	;
				return defaultLabel;			
			}
		}
		
		private String getRenameSetterLabel(){
			String defaultLabel= RefactoringMessages.getString("RenameFieldInputWizardPage.rename_setter"); //$NON-NLS-1$
			if (fSetterRenamingErrorMessage != null)
				return constructDisabledSetterRenamingLabel(defaultLabel);
			try {
				IMethod	setter= getRenameFieldProcessor().getSetter();
				if (setter == null || ! setter.exists())
					return defaultLabel;
				String oldSetterName= setter.getElementName();
				String newSetterName= createNewSetterName();
				return RefactoringMessages.getFormattedString("RenameFieldInputWizardPage.rename_setter_to", new String[]{oldSetterName, newSetterName});//$NON-NLS-1$
			} catch(CoreException e) {
				JavaPlugin.log(e);
				return defaultLabel;			
			}
		}
		private String constructDisabledSetterRenamingLabel(String defaultLabel) {
			if (fSetterRenamingErrorMessage.equals("")) //$NON-NLS-1$
				return defaultLabel;
			String[] keys= {defaultLabel, fSetterRenamingErrorMessage};
			return RefactoringMessages.getFormattedString("RenameFieldInputWizardPage.setter_label", keys); //$NON-NLS-1$
		}
	
		private String constructDisabledGetterRenamingLabel(String defaultLabel) {
			if (fGetterRenamingErrorMessage.equals("")) //$NON-NLS-1$
				return defaultLabel;
			String[] keys= {defaultLabel, fGetterRenamingErrorMessage};
			return RefactoringMessages.getFormattedString("RenameFieldInputWizardPage.getter_label", keys);			 //$NON-NLS-1$
		}
	
		private String createNewGetterName() throws CoreException {
			return getRenameFieldProcessor().getNewGetterName();
		}
	
		private String createNewSetterName() throws CoreException {
			return getRenameFieldProcessor().getNewSetterName();
		}
	
		private String checkGetterRenamingEnablement() {
			if (fGetterRenamingErrorMessage != null)
				return  fGetterRenamingErrorMessage;
			try {
				fGetterRenamingErrorMessage= getRenameFieldProcessor().canEnableGetterRenaming();
				return fGetterRenamingErrorMessage;
			} catch (CoreException e) {
				JavaPlugin.log(e);
				return ""; //$NON-NLS-1$
			} 
		}

		private String checkSetterRenamingEnablement() {
			if (fSetterRenamingErrorMessage != null)
				return  fSetterRenamingErrorMessage;
			try {
				fSetterRenamingErrorMessage= getRenameFieldProcessor().canEnableSetterRenaming();
				return fSetterRenamingErrorMessage;
			} catch (CoreException e) {
				JavaPlugin.log(e);
				return ""; //$NON-NLS-1$
			} 
		}
	
		private RenameFieldProcessor getRenameFieldProcessor() {
			return (RenameFieldProcessor)((RenameRefactoring)getRefactoring()).getProcessor();
		}
	}
}
