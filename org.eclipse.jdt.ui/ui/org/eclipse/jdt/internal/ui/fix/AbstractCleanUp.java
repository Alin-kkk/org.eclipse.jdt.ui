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
package org.eclipse.jdt.internal.ui.fix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.compiler.IProblem;

public abstract class AbstractCleanUp implements ICleanUp {

	protected static IDialogSettings getSection(IDialogSettings settings, String sectionName) {
		IDialogSettings section= settings.getSection(sectionName);
		if (section == null)
			section= settings.addNewSection(sectionName);
		return section;
	}

	private static final String SETTINGS_FLAG_NAME= "flag"; //$NON-NLS-1$
	
	private int fFlags;
	
	protected AbstractCleanUp(IDialogSettings settings, int defaultFlag) {

		if (settings.get(SETTINGS_FLAG_NAME) == null)
			settings.put(SETTINGS_FLAG_NAME, defaultFlag);
		
		fFlags= settings.getInt(SETTINGS_FLAG_NAME);
	}

	protected AbstractCleanUp(int flag) {
		fFlags= flag;
	}

	public void saveSettings(IDialogSettings settings) {
		settings.put(SETTINGS_FLAG_NAME, fFlags);
	}

	/**
	 * {@inheritDoc}
	 */
	public void setFlag(int flag, boolean b) {
		if (!isFlag(flag) && b) {
			fFlags |= flag;
		} else if (isFlag(flag) && !b) {
			fFlags &= ~flag;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isFlag(int flag) {
		return (fFlags & flag) != 0;
	}
	
	protected int getNumberOfProblems(IProblem[] problems, int problemId) {
		int result= 0;
		for (int i=0;i<problems.length;i++) {
			if (problems[i].getID() == problemId)
				result++;
		}
		return result;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void beginCleanUp(IJavaProject project, ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		if (monitor != null)
			monitor.done();
		//Default do nothing
	}

	/**
	 * {@inheritDoc}
	 */
	public void endCleanUp() throws CoreException {
		//Default do nothing
	}
	
}
