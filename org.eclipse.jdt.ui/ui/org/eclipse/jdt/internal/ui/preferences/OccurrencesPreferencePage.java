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

package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.jdt.internal.ui.JavaPlugin;



/**
 * Annotations preference page.
 * <p>
 * Note: Must be public since it is referenced from plugin.xml
 * </p>
 * 
 * @since 3.0
 */
public class OccurrencesPreferencePage extends AbstractConfigurationBlockPreferencePage {

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigureationBlockPreferencePage#getHelpId()
	 */
	protected String getHelpId() {
		return null; // FIXME: Needs help context ID
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigurationBlockPreferencePage#setDescription()
	 */
	protected void setDescription() {
		// This page has no description
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigurationBlockPreferencePage#setPreferenceStore()
	 */
	protected void setPreferenceStore() {
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigureationBlockPreferencePage#createConfigurationBlock(org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore)
	 */
	protected IPreferenceConfigurationBlock createConfigurationBlock(OverlayPreferenceStore overlayPreferenceStore) {
		return new MarkOccurrencesConfigurationBlock(overlayPreferenceStore);
	}
}
