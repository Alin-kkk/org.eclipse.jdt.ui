/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.jdt.internal.ui.dialogs.StatusDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;
import org.eclipse.jdt.internal.ui.wizards.swt.MGridLayout;

public class NewContainerDialog extends StatusDialog {
	
	private StringDialogField fContainerDialogField;
	private StatusInfo fContainerFieldStatus;
	
	private IFolder fFolder;
	private IContainer[] fExistingFolders;
	private IProject fCurrProject;
		
	public NewContainerDialog(Shell parent, String title, IProject project, IContainer[] existingFolders) {
		super(parent);
		setTitle(title);
		
		fContainerFieldStatus= new StatusInfo();
		
		SourceContainerAdapter adapter= new SourceContainerAdapter();
		fContainerDialogField= new StringDialogField();
		fContainerDialogField.setDialogFieldListener(adapter);
		
		fFolder= null;
		fExistingFolders= existingFolders;
		fCurrProject= project;
		
		fContainerDialogField.setText(""); //$NON-NLS-1$
	}
	
	public void setMessage(String message) {
		fContainerDialogField.setLabelText(message);
	}
	
		
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite)super.createDialogArea(parent);
		
		Composite inner= new Composite(composite, SWT.NONE);
		MGridLayout layout= new MGridLayout();
		layout.minimumWidth= convertWidthInCharsToPixels(70);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 1;
		inner.setLayout(layout);
		
		fContainerDialogField.doFillIntoGrid(inner, 2);
				
		fContainerDialogField.postSetFocusOnDialogField(parent.getDisplay());
		return composite;
	}

		
	// -------- SourceContainerAdapter --------

	private class SourceContainerAdapter implements IDialogFieldListener {
		
		// -------- IDialogFieldListener
		
		public void dialogFieldChanged(DialogField field) {
			doStatusLineUpdate();
		}
	}
	
	protected void doStatusLineUpdate() {
		checkIfPathValid();
		updateStatus(fContainerFieldStatus);
	}		
	
	protected void checkIfPathValid() {
		fFolder= null;
		
		String pathStr= fContainerDialogField.getText();
		if (pathStr.length() == 0) {
			fContainerFieldStatus.setError(NewWizardMessages.getString("NewContainerDialog.error.enterpath")); //$NON-NLS-1$
			return;
		}
		IPath path= fCurrProject.getFullPath().append(pathStr);
		IWorkspace workspace= fCurrProject.getWorkspace();
		
		IStatus pathValidation= workspace.validatePath(path.toString(), IResource.FOLDER);
		if (!pathValidation.isOK()) {
			fContainerFieldStatus.setError(NewWizardMessages.getFormattedString("NewContainerDialog.error.invalidpath", pathValidation.getMessage())); //$NON-NLS-1$
			return;
		}
		IFolder folder= fCurrProject.getFolder(pathStr);
		if (isFolderExisting(folder)) {
			fContainerFieldStatus.setError(NewWizardMessages.getString("NewContainerDialog.error.pathexists")); //$NON-NLS-1$
			return;
		}
		fContainerFieldStatus.setOK();
		fFolder= folder;
	}
	
	private boolean isFolderExisting(IFolder folder) {
		for (int i= 0; i < fExistingFolders.length; i++) {
			if (folder.equals(fExistingFolders[i])) {
				return true;
			}
		}
		return false;
	}
		
	
		
	public IFolder getFolder() {
		return fFolder;
	}
		
	
}