/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.jarpackager;

import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.wizard.WizardDialog;

import org.xml.sax.SAXException;

import org.eclipse.jdt.ui.jarpackager.IJarDescriptionReader;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.dialogs.ProblemDialog;

import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * This action delegate opens the JAR Package Wizard and initializes
 * it with the selected JAR package description.
 */
public class OpenJarPackageWizardActionDelegate extends JarPackageActionDelegate {

	private IJarDescriptionReader fReader;

	/*
	 * @see IActionDelegate
	 */
	public void run(IAction action) {
		Shell parent= getShell();
		JarPackageData jarPackage= null;
		String errorDetail= null;
		try {
			jarPackage= readJarPackage(getDescriptionFile(getSelection()));			
		} catch (IOException ex) {
			errorDetail= ex.getLocalizedMessage();
			MessageDialog.openError(parent, JarPackagerMessages.getString("OpenJarPackageWizardDelegate.error.openJarPackager.title"), JarPackagerMessages.getString("OpenJarPackageWizardDelegate.error.openJarPackager.message") + errorDetail); //$NON-NLS-2$ //$NON-NLS-1$
			return;
		} catch (CoreException ex) {
			errorDetail= ex.getLocalizedMessage();
			ExceptionHandler.handle(ex, parent, JarPackagerMessages.getString("OpenJarPackageWizardDelegate.error.openJarPackager.title"), JarPackagerMessages.getString("OpenJarPackageWizardDelegate.error.openJarPackager.message") + errorDetail); //$NON-NLS-2$ //$NON-NLS-1$
			return;
		} catch (SAXException ex) {
			errorDetail= JarPackagerMessages.getString("OpenJarPackageWizardDelegate.badXmlFormat") + ex.getLocalizedMessage(); //$NON-NLS-1$
			MessageDialog.openError(parent, JarPackagerMessages.getString("OpenJarPackageWizardDelegate.error.openJarPackager.title"), JarPackagerMessages.getString("OpenJarPackageWizardDelegate.error.openJarPackager.message") + errorDetail); //$NON-NLS-2$ //$NON-NLS-1$
			return;
		}

		if (fReader != null && !fReader.getStatus().isOK())
			ProblemDialog.open(parent, JarPackagerMessages.getString("OpenJarPackageWizardDelegate.jarDescriptionReaderWarnings.title"), null, fReader.getStatus()); //$NON-NLS-1$
		JarPackageWizard wizard= new JarPackageWizard();
		wizard.init(getWorkbench(), jarPackage);
		WizardDialog dialog= new WizardDialog(parent, wizard);
		dialog.create();
		dialog.open();
	}
	
	/**
	 * Reads the JAR package spec from file.
	 */
	private JarPackageData readJarPackage(IFile description) throws CoreException, IOException, SAXException {
		Assert.isLegal(description.isAccessible());
		Assert.isNotNull(description.getFileExtension());
		Assert.isLegal(description.getFileExtension().equals(JarPackagerUtil.DESCRIPTION_EXTENSION));
		JarPackageData jarPackage= new JarPackageData();
		try {
			fReader= jarPackage.createJarDescriptionReader(description.getContents());
			fReader.read(jarPackage);
		} finally {
			if (fReader != null)
				fReader.close();
		}
		return jarPackage;
	}
}