/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/*
 * http://dev.eclipse.org/bugs/show_bug.cgi?id=19104
 */
public class ActionUtil {

	public static boolean isProcessable(Shell shell, Object element) {
		if (!(element instanceof IJavaElement))
			return true;
			
		if (checkJavaElement((IJavaElement)element))
			return true;
		MessageDialog.openInformation(shell, 
			ActionMessages.getString("ActionUtil.notOnBuildPath.title"),  //$NON-NLS-1$
			ActionMessages.getString("ActionUtil.notOnBuildPath.message")); //$NON-NLS-1$
		return false;
	}

	private static boolean checkJavaElement(IJavaElement element) {	
        //fix for bug http://dev.eclipse.org/bugs/show_bug.cgi?id=20051
        if (element.getElementType() == IJavaElement.JAVA_PROJECT)
            return true;
		IJavaProject project= element.getJavaProject();
		try {
			if (!project.isOnClasspath(element))
				return false;
			IProject resourceProject= project.getProject();
			if (resourceProject == null)
				return false;
			IProjectNature nature= resourceProject.getNature(JavaCore.NATURE_ID);
			// We have a Java project
			if (nature != null)
				return true;
		} catch (CoreException e) {
		}
		return false;
	}
}

