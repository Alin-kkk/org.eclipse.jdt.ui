/*******************************************************************************
 * Copyright (c) 2003 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.participants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IProject;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;

import org.eclipse.jdt.internal.corext.util.JdtFlags;


/**
 * Utility class to deal with Java element processors.
 */
public class JavaProcessors {

	public static IProject[] computeScope(IJavaElement element) throws CoreException {
		if (element instanceof IMember) {
			IMember member= (IMember)element;
			if (JdtFlags.isPrivate(member) || JdtFlags.isPackageVisible(member)) {
				return new IProject[] { element.getJavaProject().getProject() };
			}
		}
		IJavaProject project= element.getJavaProject();
		Set result= new HashSet(Arrays.asList(Processors.computeScope(project.getProject())));
		return (IProject[])result.toArray(new IProject[result.size()]);
	}	
}
