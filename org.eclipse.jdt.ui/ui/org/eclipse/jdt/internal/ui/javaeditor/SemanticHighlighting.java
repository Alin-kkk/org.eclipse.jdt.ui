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

package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.swt.graphics.RGB;

/**
 * Semantic highlighting
 */
public abstract class SemanticHighlighting {

	/**
	 * @return the preference key, will be augmented by a prefix and a suffix for each preference
	 */
	public abstract String getPreferenceKey();

	/**
	 * @return the default text color
	 */
	public abstract RGB getDefaultTextColor();

	/**
	 * @return <code>true</code> if the text attribute bold is set by default
	 */
	public abstract boolean isBoldByDefault();
	
	/**
	 * @return <code>true</code> if the text attribute italic is set by default
	 */
	public abstract boolean isItalicByDefault();
	
	/**
	 * @return <code>true</code> if the text attribute italic is enabled by default
	 */
	public abstract boolean isEnabledByDefault();
	
	/**
	 * @return the display name
	 */
	public abstract String getDisplayName();
	
	/**
	 * Returns <code>true</code> iff the semantic highlighting consumes the semantic token.
	 * <p>
	 * NOTE: Implementors are not allowed to keep a reference on the token or on any object
	 * retrieved from the token.
	 * </p>
	 * 
	 * @param token the semantic token
	 * @return <code>true</code> iff the semantic highlighting consumes the semantic token
	 */
	public abstract boolean consumes(SemanticToken token);
}
