/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.preferences;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;
import org.eclipse.ui.dialogs.PropertyPage;
import org.eclipse.ui.help.DialogPageContextComputer;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;

/**
 * Property page for configuring the Java build path
 */
public class BuildPathsPropertyPage extends PropertyPage implements IStatusChangeListener {
		
	private BuildPathsBlock fBuildPathsBlock;
	
	/*
	 * @see PreferencePage#createControl(Composite)
	 */
	protected Control createContents(Composite parent) {
		WorkbenchHelp.setHelp(parent, IJavaHelpContextIds.BUILD_PATH_PROPERTY_PAGE);

		// ensure the page has no special buttons
		noDefaultAndApplyButton();		
		
		IProject project= getProject();
		if (project == null || !isJavaProject(project)) {
			return createWithoutJava(parent);
		} else if (!project.isOpen()) {
			return createForClosedProject(parent);
		} else {
			return createWithJava(parent, project);
		}
	}
	
	/**
	 * Content for valid projects.
	 */
	private Control createWithJava(Composite parent, IProject project) {
		IWorkspaceRoot root= JavaPlugin.getWorkspace().getRoot();
		fBuildPathsBlock= new BuildPathsBlock(root, this, false);
		fBuildPathsBlock.init(JavaCore.create(project), null, null);
		return fBuildPathsBlock.createControl(parent);
	}

	/**
	 * Content for non-Java projects.
	 */	
	private Control createWithoutJava(Composite parent) {
		Label label= new Label(parent, SWT.LEFT);
		label.setText(JavaUIMessages.getString("BuildPathsPropertyPage.no_java_project.message")); //$NON-NLS-1$
		
		fBuildPathsBlock= null;
		setValid(true);
		return label;
	}

	/**
	 * Content for closed projects.
	 */		
	private Control createForClosedProject(Composite parent) {
		Label label= new Label(parent, SWT.LEFT);
		label.setText(JavaUIMessages.getString("BuildPathsPropertyPage.closed_project.message")); //$NON-NLS-1$
		
		fBuildPathsBlock= null;
		setValid(true);
		return label;
	}
	
	private IProject getProject() {
		IAdaptable adaptable= getElement();
		if (adaptable != null) {
			IJavaElement elem= (IJavaElement) adaptable.getAdapter(IJavaElement.class);
			if (elem instanceof IJavaProject) {
				return ((IJavaProject) elem).getProject();
			}
		}
		return null;
	}
	
	private boolean isJavaProject(IProject proj) {
		try {
			return proj.hasNature(JavaCore.NATURE_ID);
		} catch (CoreException e) {
			JavaPlugin.logIgnoringNotPresentException(e);
		}
		return false;
	}	
	
	/*
	 * @see IPreferencePage#performOk
	 */
	public boolean performOk() {
		if (fBuildPathsBlock != null) {
			Shell shell= getControl().getShell();
			IRunnableWithProgress runnable= new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor)	throws InvocationTargetException, InterruptedException {
					try {
						fBuildPathsBlock.configureJavaProject(monitor);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					} 
				}
			};
			IRunnableWithProgress op= new WorkspaceModifyDelegatingOperation(runnable);
			try {
				new ProgressMonitorDialog(shell).run(false, true, op);
			} catch (InvocationTargetException e) {
				String title= JavaUIMessages.getString("BuildPathsPropertyPage.error.title"); //$NON-NLS-1$
				String message= JavaUIMessages.getString("BuildPathsPropertyPage.error.message"); //$NON-NLS-1$
				ExceptionHandler.handle(e, shell, title, message);
				return false;
			} catch (InterruptedException e) {
				// cancelled
				return false;
			}
		}
		return true;
	}
	
	/**
	 * @see IStatusChangeListener#statusChanged
	 */
	public void statusChanged(IStatus status) {
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}

}