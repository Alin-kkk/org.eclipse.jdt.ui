/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/*
 * The page to configure the task tags
 */
public class TodoTaskPreferencePage extends PropertyAndPreferencePage {

	public static final String PREF_ID= "org.eclipse.jdt.ui.preferences.TodoTaskPreferencePage"; //$NON-NLS-1$
	public static final String PROP_ID= "org.eclipse.jdt.ui.propertyPages.TodoTaskPreferencePage"; //$NON-NLS-1$
	
	private TodoTaskConfigurationBlock fConfigurationBlock;

	public TodoTaskPreferencePage() {
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		setDescription(PreferencesMessages.getString("TodoTaskPreferencePage.description")); //$NON-NLS-1$
		
		// only used when page is shown programatically
		setTitle(PreferencesMessages.getString("TodoTaskPreferencePage.title")); //$NON-NLS-1$
	}
		
	
	/*
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		fConfigurationBlock= new TodoTaskConfigurationBlock(getNewStatusChangedListener(), getProject());
		
		super.createControl(parent);
		
		if (isProjectPreferencePage()) {
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.TODOTASK_PROPERTY_PAGE);
		} else {
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.TODOTASK_PREFERENCE_PAGE);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#createPreferenceContent(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createPreferenceContent(Composite composite) {
		return fConfigurationBlock.createContents(composite);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#hasProjectSpecificOptions()
	 */
	protected boolean hasProjectSpecificOptions() {
		return fConfigurationBlock.hasProjectSpecificOptions();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#openWorkspacePreferences()
	 */
	protected void openWorkspacePreferences() {
		TodoTaskPreferencePage page= new TodoTaskPreferencePage();
		PreferencePageSupport.showPreferencePage(getShell(), PREF_ID, page);
	}
		
	/*
	 * @see org.eclipse.jface.preference.IPreferencePage#performDefaults()
	 */
	protected void performDefaults() {
		super.performDefaults();
		if (fConfigurationBlock != null) {
			fConfigurationBlock.performDefaults();
		}
	}

	/*
	 * @see org.eclipse.jface.preference.IPreferencePage#performOk()
	 */
	public boolean performOk() {
		boolean enabled= !isProjectPreferencePage() || useProjectSettings();
		if (fConfigurationBlock != null && !fConfigurationBlock.performOk(enabled)) {
			return false;
		}	
		return super.performOk();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.DialogPage#dispose()
	 */
	public void dispose() {
		if (fConfigurationBlock != null) {
			fConfigurationBlock.dispose();
		}
		super.dispose();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#setElement(org.eclipse.core.runtime.IAdaptable)
	 */
	public void setElement(IAdaptable element) {
		super.setElement(element);
		setDescription(null); // no description for property page
	}


}
