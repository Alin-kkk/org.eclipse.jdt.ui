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
package org.eclipse.jdt.internal.corext.textmanipulation;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @deprecated
 */
public class TextManipulationMessages {

	private static final String BUNDLE_NAME= "org.eclipse.jdt.internal.corext.textmanipulation.Messages"; //$NON-NLS-1$

	private static final ResourceBundle RESOURCE_BUNDLE= ResourceBundle.getBundle(BUNDLE_NAME);

	private TextManipulationMessages() {
	}

	public static String getString(String key) {
		try {
			return RESOURCE_BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}
	
	public static String getFormattedString(String key, Object arg) {
		return getFormattedString(key, new Object[] { arg });
	}
	
	public static String getFormattedString(String key, Object[] args) {
		return MessageFormat.format(getString(key), args);	
	}		
}
