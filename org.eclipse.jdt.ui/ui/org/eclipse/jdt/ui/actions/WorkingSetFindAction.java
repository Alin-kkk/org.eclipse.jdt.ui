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

import org.eclipse.jface.util.Assert;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IJavaElement;


import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * Wraps a <code>JavaElementSearchActions</code> to find its results
 * in the specified working set.
 * The action is applicable for selections and Search view entries
 * representing a Java element.
 * 
 * <p>
 * Note: This class is for internal use only. Clients should not use this class.
 * </p>
 * 
 * @since 2.0
 */
public class WorkingSetFindAction extends FindAction {

	private FindAction fAction;

	/**
	 * Creates a new <code>WorkingSetAction</code>.
	 * <p>
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * </p>
	 * @param site the site providing context information for this action
	 */
	public WorkingSetFindAction(IWorkbenchSite site, FindAction action, String workingSetName) {
		super(site, workingSetName, null);
		Assert.isNotNull(action);
		fAction= action;
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.WORKING_SET_FIND_ACTION);
	}

	/**
	 * Creates a new <code>WorkingSetAction</code>.
	 * <p>
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * </p>
	 */
	public WorkingSetFindAction(JavaEditor editor, FindAction action, String workingSetName) {
		super(editor, workingSetName, null);
		Assert.isNotNull(action);
		fAction= action;
		setToolTipText(action.getToolTipText());
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.WORKING_SET_FIND_ACTION);
	}

	void run(IJavaElement element) {
		fAction.run(element);
	}

	boolean canOperateOn(IJavaElement element) {
		return fAction.canOperateOn(element);
	}

	int getLimitTo() {
		return -1;
	}

	String getOperationUnavailableMessage() {
		return fAction.getOperationUnavailableMessage();
	}

}
