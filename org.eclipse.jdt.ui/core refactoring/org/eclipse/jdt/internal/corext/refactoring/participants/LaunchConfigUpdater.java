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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.base.Change;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeAbortException;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;


public class LaunchConfigUpdater extends RenameParticipant {

	private IType fType;
	
	private static class LaunchConfigChange extends Change {

		private IType fType;
		private ILaunchConfiguration fConfig;
		private String fNewName;
		private IChange fUndo;

		public LaunchConfigChange(IType type, ILaunchConfiguration config, String newName) {
			fType= type;
			fConfig= config;
			fNewName= newName;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.corext.refactoring.base.IChange#perform(org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext, org.eclipse.core.runtime.IProgressMonitor)
		 */
		public void perform(ChangeContext context, IProgressMonitor pm) throws JavaModelException, ChangeAbortException {
			pm.beginTask("", 1); //$NON-NLS-1$
			try {
				String current= fConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String)null);
				int index= current.lastIndexOf('.');
				String newTypeName;
				if (index == -1) {
					newTypeName= fNewName;
				} else {
					newTypeName= current.substring(0, index + 1) + fNewName;
				}
				ILaunchConfigurationWorkingCopy copy= fConfig.getWorkingCopy();
				copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, newTypeName);
				copy.doSave();
				fUndo= new LaunchConfigChange(fType, fConfig, 
					(index == -1) ? current : current.substring(index + 1));
			} catch (CoreException e) {
				throw new JavaModelException(e);
			}
			pm.worked(1);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.corext.refactoring.base.IChange#getUndoChange()
		 */
		public IChange getUndoChange() {
			return fUndo;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.corext.refactoring.base.IChange#getName()
		 */
		public String getName() {
			return "Launch configuration update"; //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.corext.refactoring.base.IChange#getModifiedLanguageElement()
		 */
		public Object getModifiedLanguageElement() {
			return fConfig;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#initialize(org.eclipse.jdt.internal.corext.refactoring.participants.RenameRefactoring, java.lang.Object)
	 */
	public void initialize(RenameRefactoring refactoring, Object element) {
		super.initialize(refactoring);
		fType= (IType)element;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#getElement()
	 */
	public Object getElement() {
		return fType;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#canParticipate()
	 */
	public boolean isAvailable() {
		try {
			return JavaModelUtil.hasMainMethod(fType);
		} catch (JavaModelException e) {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#checkActivation()
	 */
	public RefactoringStatus checkActivation() throws CoreException {
		return new RefactoringStatus();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#checkInput(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws CoreException {
		return new RefactoringStatus();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.IRenameParticipant#createChange(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IChange createChange(IProgressMonitor pm) throws CoreException {
		IChange result= null;
		ILaunchManager manager= DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type= manager.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
		ILaunchConfiguration configs[]= manager.getLaunchConfigurations(type);
		String typeName= fType.getFullyQualifiedName();
		for (int i= 0; i < configs.length; i++) {
			String mainType= configs[i].getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String)null);
			if (typeName.equals(mainType)) {
				if (result == null)
					result= new LaunchConfigChange(fType, configs[i], getRefactoring().getNewName());
			}
		}
		return result;
	}
}
