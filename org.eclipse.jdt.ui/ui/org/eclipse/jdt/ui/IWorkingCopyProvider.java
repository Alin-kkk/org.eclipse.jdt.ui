/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui;

/**
 * Interface used for Java element content providers to indicate that
 * the content provider can return working copy elements for members
 * below compilation units (e.g. types, fields, methods, etc.).
 * 
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 *
 * @see org.eclipse.jdt.ui.StandardJavaElementContentProvider
 * @see org.eclipse.jdt.core.IWorkingCopy
 * 
 * @since 2.0 
 */
public interface IWorkingCopyProvider {
	
	/**
	 * Returns <code>true</code> if the content provider returns working 
	 * copy elements; otherwise <code>false</code> is returned.
	 * 
	 * @return whether working copy elements are provided.
	 */
	public boolean providesWorkingCopies();
}
