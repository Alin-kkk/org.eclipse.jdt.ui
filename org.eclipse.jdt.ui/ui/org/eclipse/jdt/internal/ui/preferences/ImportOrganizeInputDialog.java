/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.preferences;

import java.util.HashMap;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.util.Assert;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;

/**
 * Dialog to enter a new package entry in the organize import preference page.
 */
public class ImportOrganizeInputDialog extends StatusDialog {
	
	private class ImportOrganizeInputAdapter implements IDialogFieldListener, IStringButtonAdapter {
		/**
		 * @see IDialogFieldListener#dialogFieldChanged(DialogField)
		 */
		public void dialogFieldChanged(DialogField field) {
			doValidation();
		}			
		/**
		 * @see IStringButtonAdapter#changeControlPressed(DialogField)
		 */
		public void changeControlPressed(DialogField field) {
			doButtonPressed();
		}
	}
	
	private StringButtonDialogField fNameDialogField;
	private List fExistingEntries;
		
	public ImportOrganizeInputDialog(Shell parent, List existingEntries) {
		super(parent);
		
		fExistingEntries= existingEntries;
		
		setTitle(JavaUIMessages.getString("ImportOrganizeInputDialog.title"));

		ImportOrganizeInputAdapter adapter= new ImportOrganizeInputAdapter();

		fNameDialogField= new StringButtonDialogField(adapter);
		fNameDialogField.setLabelText(JavaUIMessages.getString("ImportOrganizeInputDialog.message"));
		fNameDialogField.setButtonLabel(JavaUIMessages.getString("ImportOrganizeInputDialog.browse.button"));
		fNameDialogField.setDialogFieldListener(adapter);		
	}
	
	public void setInitialString(String input) {
		Assert.isNotNull(input);
		fNameDialogField.setText(input);
	}
	
	public Object getResult() {
		return fNameDialogField.getText();
	}
	
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite) super.createDialogArea(parent);
		
		Composite inner= new Composite(composite, SWT.NONE);
		LayoutUtil.doDefaultLayout(inner, new DialogField[] { fNameDialogField }, true, 350, 0, 0, 0);
		fNameDialogField.postSetFocusOnDialogField(parent.getDisplay());
		
		return composite;
	}
	
	private void doButtonPressed() {
		HashMap allPackages= new HashMap();
		// no duplicate entries
		try {
			IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
			IJavaProject[] projects= JavaCore.create(root).getJavaProjects();
			for (int i= 0; i < projects.length; i++) {
				IPackageFragment[] packs= projects[i].getPackageFragments();
				for (int k=0; k < packs.length; k++) {
					IPackageFragment curr= packs[k];
					// filter out default package and resource folders
					if (!curr.isDefaultPackage() && (curr.hasChildren() || curr.getNonJavaResources().length == 0)) {
						allPackages.put(curr.getElementName(), curr);
					}
				}
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
		Object initialSelection= allPackages.get(fNameDialogField.getText());
		
			
		ElementListSelectionDialog dialog= new ElementListSelectionDialog(getShell(), new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT));
		dialog.setIgnoreCase(false);
		dialog.setTitle(JavaUIMessages.getString("ImportOrganizeInputDialog.ChoosePackageDialog.title")); //$NON-NLS-1$
		dialog.setMessage(JavaUIMessages.getString("ImportOrganizeInputDialog.ChoosePackageDialog.description")); //$NON-NLS-1$
		dialog.setEmptyListMessage(JavaUIMessages.getString("ImportOrganizeInputDialog.ChoosePackageDialog.empty")); //$NON-NLS-1$
		dialog.setElements(allPackages.values().toArray());
		if (initialSelection != null) {
			dialog.setInitialSelections(new Object[] { initialSelection });
		}
		
		if (dialog.open() == dialog.OK) {
			IPackageFragment res= (IPackageFragment) dialog.getFirstResult();
			fNameDialogField.setText(res.getElementName());
		}
	}
	
	private void doValidation() {
		StatusInfo status= new StatusInfo();
		String newText= fNameDialogField.getText();
		if (newText.length() == 0) {
			status.setError(JavaUIMessages.getString("ImportOrganizeInputDialog.error.enterName"));
		} else {
			IStatus val= JavaConventions.validatePackageName(newText);
			if (val.matches(IStatus.ERROR)) {
				status.setError(JavaUIMessages.getFormattedString("ImportOrganizeInputDialog.error.invalidName", val.getMessage()));
			} else {
				if (fExistingEntries.contains(newText)) {
					status.setError(JavaUIMessages.getString("ImportOrganizeInputDialog.error.entryExists"));
				}
			}
		}
		updateStatus(status);
	}

}