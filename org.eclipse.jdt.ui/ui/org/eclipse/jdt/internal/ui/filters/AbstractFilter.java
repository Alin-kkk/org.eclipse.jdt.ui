/*
 * Copyright (c) 2000, 2002 IBM Corp. and others..
 * All rights reserved. � This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
�*
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package org.eclipse.jdt.internal.ui.filters;


import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.jface.viewers.ViewerFilter;


/**
 * Base class for JDT filters.
 */
public abstract class AbstractFilter extends ViewerFilter {

	public boolean isFilterProperty(Object element, String property) {
		return IBasicPropertyConstants.P_TEXT.equals(property) || IBasicPropertyConstants.P_IMAGE.equals(property);
	}
}