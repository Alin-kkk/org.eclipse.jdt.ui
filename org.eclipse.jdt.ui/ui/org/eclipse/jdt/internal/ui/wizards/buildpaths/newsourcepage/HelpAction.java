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

package org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage;

import org.eclipse.jface.action.Action;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

/**
 * Action to get help.
 */
public class HelpAction extends Action {
    
    public HelpAction() {
        super();
        setImageDescriptor(JavaPluginImages.DESC_OBJS_HELP);
        setText(NewWizardMessages.getString("NewSourceContainerWorkbookPage.ToolBar.Help.label")); //$NON-NLS-1$
        setToolTipText(NewWizardMessages.getString("NewSourceContainerWorkbookPage.ToolBar.Help.tooltip")); //$NON-NLS-1$;
    }
    
    public void run() {
        PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(NewWizardMessages.getString("NewSourceContainerWorkbookPage.ToolBar.Help.link")); //$NON-NLS-1$
    }
}
