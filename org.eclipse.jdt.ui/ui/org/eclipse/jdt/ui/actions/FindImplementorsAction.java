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
package org.eclipse.jdt.ui.actions;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.search.SearchMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Finds implementors of the selected element in the workspace.
 * The action is applicable for selections representing a Java interface.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class FindImplementorsAction extends FindAction {

	/**
	 * Creates a new <code>FindImplementorsAction</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public FindImplementorsAction(IWorkbenchSite site) {
		super(site, SearchMessages.getString("Search.FindImplementorsAction.label"), new Class[] {IType.class}); //$NON-NLS-1$
		init();
	}

	/**
	 * Creates a new <code>FindImplementorsAction</code>.
	 * <p>
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * </p>
	 */
	public FindImplementorsAction(JavaEditor editor) {
		super(editor, SearchMessages.getString("Search.FindImplementorsAction.label"), new Class[] {IType.class}); //$NON-NLS-1$
		init();
	}

	private void init() {
		setToolTipText(SearchMessages.getString("Search.FindImplementorsAction.tooltip")); //$NON-NLS-1$
		setImageDescriptor(JavaPluginImages.DESC_OBJS_SEARCH_DECL);
	}

	boolean canOperateOn(IJavaElement element) {
		if (!super.canOperateOn(element))
			return false;

		if (element.getElementType() == IJavaElement.TYPE)
			try {
				return ((IType) element).isInterface();
			} catch (JavaModelException ex) {
				ExceptionHandler.log(ex, SearchMessages.getString("Search.Error.javaElementAccess.message")); //$NON-NLS-1$
				return false;
			}
		// should not happen: handled by super.canOperateOn
		return false;
	}

	int getLimitTo() {
		return IJavaSearchConstants.IMPLEMENTORS;
	}

	String getOperationUnavailableMessage() {
		return SearchMessages.getString("JavaElementAction.operationUnavailable.interface"); //$NON-NLS-1$
	}
}

