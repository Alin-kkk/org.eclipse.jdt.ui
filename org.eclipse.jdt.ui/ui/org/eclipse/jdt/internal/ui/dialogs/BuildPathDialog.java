/*******************************************************************************
 * Copyright (c) 2003 International Business Machines Corp. and others. All
 * rights reserved. This program and the accompanying materials are made
 * available under the terms of the Common Public License v1.0 which accompanies
 * this distribution, and is available at http://www.eclipse.org/legal/cpl-v10.
 * html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.dialogs;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Assert;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;

public class BuildPathDialog extends StatusDialog {

	private IJavaProject fProject;
	private BuildPathsBlock fBlock;

	public BuildPathDialog(Shell parent, IJavaProject project) {
		super(parent);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		Assert.isNotNull(project);
		fProject= project;
	}

	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(JavaUIMessages.getFormattedString("BuildPathDialog.title", fProject.getElementName())); //$NON-NLS-1$
	}

	protected Control createDialogArea(Composite parent) {
		IStatusChangeListener listener = new IStatusChangeListener() {
			public void statusChanged(IStatus status) {
				updateStatus(status);
			}
		};
		Composite result= (Composite)super.createDialogArea(parent);
		fBlock= new BuildPathsBlock(listener, 0);
		fBlock.init(fProject, null, null);
		fBlock.createControl(result).setLayoutData(new GridData(GridData.FILL_BOTH));
		return result;
	}

	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			configureBuildPath();
		}
		super.buttonPressed(buttonId);
	}

	private void configureBuildPath() {
		Shell shell= getShell();
		IRunnableWithProgress runnable= new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor)	throws InvocationTargetException, InterruptedException {
				try {
					fBlock.configureJavaProject(monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} 
			}
		};
		IRunnableWithProgress op= new WorkspaceModifyDelegatingOperation(runnable);
		try {
			new ProgressMonitorDialog(shell).run(false, true, op);
		} catch (InvocationTargetException e) {
			String title= PreferencesMessages.getString("BuildPathsPropertyPage.error.title"); //$NON-NLS-1$
			String message= PreferencesMessages.getString("BuildPathsPropertyPage.error.message"); //$NON-NLS-1$
			ExceptionHandler.handle(e, shell, title, message);
		} catch (InterruptedException e) {
			// cancelled
		}
	}
}
