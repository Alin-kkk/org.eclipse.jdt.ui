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


import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;


/** 
 * filters out all empty package fragments.
 */
public class EmptyPackageFilter extends ViewerFilter {

	/**
	 * Returns the result of this filter, when applied to the
	 * given inputs.
	 *
	 * @param inputs the set of elements to 
	 * @return Returns true if element should be included in filtered set
	 */
	public boolean select(Viewer viewer, Object parent, Object element) {
		if (element instanceof IPackageFragment) {
			IPackageFragment pkg= (IPackageFragment)element;
			try {
				return pkg.hasChildren();
			} catch (JavaModelException e) {
				return false;
			}
		}
		return true;
	}


}