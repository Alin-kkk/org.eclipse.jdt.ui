/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.jarpackager;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.util.Assert;

import org.eclipse.jdt.ui.jarpackager.IJarDescriptionReader;
import org.eclipse.jdt.ui.jarpackager.IJarExportRunnable;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaStatusConstants;

import org.eclipse.jdt.internal.ui.dialogs.ProblemDialog;

import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class CreateJarActionDelegate extends JarPackageActionDelegate {

	/*
	 * @see IActionDelegate
	 */
	public void run(IAction action) {
		IFile[] descriptions= getDescriptionFiles(getSelection());
		MultiStatus mergedStatus;
		int length= descriptions.length;
		if (length < 1)
			return;

		// Create read multi status		
		String message;
		if (length > 1)
			message= JarPackagerMessages.getString("JarFileExportOperation.creationOfSomeJARsFailed"); //$NON-NLS-1$
		else
			message= JarPackagerMessages.getString("JarFileExportOperation.jarCreationFailed"); //$NON-NLS-1$
		MultiStatus readStatus= new MultiStatus(JavaPlugin.getPluginId(), 0, message, null); //$NON-NLS-1$
		JarPackageData[] jarPackages= readJarPackages(descriptions, readStatus);
		if (jarPackages.length > 0) {
			IStatus status= export(jarPackages);
			if (status == null)
				return; // cancelled
			if (readStatus.getSeverity() == IStatus.ERROR)
				message= readStatus.getMessage();
			else
				message= status.getMessage();
			// Create new status because we want another message - no API to set message
			mergedStatus= new MultiStatus(JavaPlugin.getPluginId(), status.getCode(), readStatus.getChildren(), message, null);
			mergedStatus.merge(status);
		} else
			mergedStatus= readStatus;
		
		if (!mergedStatus.isOK())
			ProblemDialog.open(getShell(), JarPackagerMessages.getString("CreateJarActionDelegate.jarExportProblems"), null, mergedStatus); //$NON-NLS-1$
	}

	private JarPackageData[] readJarPackages(IFile[] descriptions, MultiStatus readStatus) {
		List jarPackagesList= new ArrayList(descriptions.length);
		for (int i= 0; i < descriptions.length; i++) {
			JarPackageData jarPackage= readJarPackage(descriptions[i], readStatus);
			if (jarPackage != null)
				jarPackagesList.add(jarPackage);
		}
		return (JarPackageData[])jarPackagesList.toArray(new JarPackageData[jarPackagesList.size()]);
	}

	private IStatus export(JarPackageData[] jarPackages) {
		Shell shell= getShell();
		IJarExportRunnable op= jarPackages[0].createJarExportRunnable(jarPackages, shell);
		try {
			ProgressMonitorDialog dialog= new ProgressMonitorDialog(shell);
			dialog.run(true, true, op);
		} catch (InvocationTargetException ex) {
			if (ex.getTargetException() != null) {
				ExceptionHandler.handle(ex, shell, JarPackagerMessages.getString("CreateJarActionDelegate.jarExportError.title"), JarPackagerMessages.getString("CreateJarActionDelegate.jarExportError.message")); //$NON-NLS-2$ //$NON-NLS-1$
				return null;
			}
		} catch (InterruptedException e) {
			// do nothing on cancel
			return null;
		}
		return op.getStatus();
	}
	
	/**
	 * Reads the JAR package spec from file.
	 */
	protected JarPackageData readJarPackage(IFile description, MultiStatus readStatus) {
		Assert.isLegal(description.isAccessible());
		Assert.isNotNull(description.getFileExtension());
		Assert.isLegal(description.getFileExtension().equals(JarPackagerUtil.DESCRIPTION_EXTENSION));
		JarPackageData jarPackage= new JarPackageData();
		IJarDescriptionReader reader= null;
		try {
			reader= jarPackage.createJarDescriptionReader(description.getContents());
			// Do not save - only generate JAR
			reader.read(jarPackage);
			jarPackage.setSaveManifest(false);
			jarPackage.setSaveDescription(false);
		} catch (CoreException ex) {
				String message= JarPackagerMessages.getFormattedString("JarFileExportOperation.errorReadingFile", description.getFullPath(), ex.getStatus().getException().getLocalizedMessage()); //$NON-NLS-1$
				addError(readStatus, jarPackage, message, ex.getStatus().getException()); //$NON-NLS-1$
				return null;
		} finally {
			if ((jarPackage == null || jarPackage.logWarnings()) && reader != null)
				// AddWarnings
				readStatus.addAll(reader.getStatus());
			try {
				if (reader != null)
					reader.close();
			}
			catch (CoreException ex) {
				addError(readStatus, jarPackage,JarPackagerMessages.getFormattedString("JarFileExportOperation.errorClosingJarPackageDescriptionReader", description.getFullPath()), ex); //$NON-NLS-1$
			}
		}
		return jarPackage;
	}

	private void addError(MultiStatus problems, JarPackageData jarPackage, String message, Throwable error) {
		if (jarPackage == null || jarPackage.logErrors())
			problems.add(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), JavaStatusConstants.INTERNAL_ERROR, message, error));
	}
}