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
package org.eclipse.jdt.internal.junit.ui;

import org.eclipse.jface.action.Action;

class ShowPreviousFailureAction extends Action {
	
	private TestRunnerViewPart fPart;

	public ShowPreviousFailureAction(TestRunnerViewPart part) {
		super(JUnitMessages.getString("ShowPreviousFailureAction.label"));  //$NON-NLS-1$
		setDisabledImageDescriptor(JUnitPlugin.getImageDescriptor("dlcl16/select_prev.gif")); //$NON-NLS-1$
		setHoverImageDescriptor(JUnitPlugin.getImageDescriptor("clcl16/select_prev.gif")); //$NON-NLS-1$
		setImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/select_prev.gif")); //$NON-NLS-1$
		setToolTipText(JUnitMessages.getString("ShowPreviousFailureAction.tooltip"));  //$NON-NLS-1$
		fPart= part;
	}
	
	public void run() {
		fPart.selectPreviousFailure();
	}
}
