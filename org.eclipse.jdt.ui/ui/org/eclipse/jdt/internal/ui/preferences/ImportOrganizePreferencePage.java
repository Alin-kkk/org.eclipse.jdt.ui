/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.preferences;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.LabelProvider;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.JavaConventions;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;

/*
 * The page for setting the organize import settings
 */
public class ImportOrganizePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	// Preference store keys
	private static final String PREF_IMPORTORDER= JavaUI.ID_PLUGIN + ".importorder"; //$NON-NLS-1$
	private static final String PREF_ONDEMANDTHRESHOLD= JavaUI.ID_PLUGIN + ".ondemandthreshold"; //$NON-NLS-1$
	private static final String PREF_IGNORELOWERCASE= JavaUI.ID_PLUGIN + ".ignorelowercasenames"; //$NON-NLS-1$
	private static final String PREF_LASTLOADPATH= JavaUI.ID_PLUGIN + ".importorder.loadpath"; //$NON-NLS-1$
	private static final String PREF_LASTSAVEPATH= JavaUI.ID_PLUGIN + ".importorder.savepath"; //$NON-NLS-1$

	public static String[] getImportOrderPreference() {
		IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
		String str= prefs.getString(PREF_IMPORTORDER);
		if (str != null) {
			return unpackOrderList(str);
		}
		return new String[0];
	}
	
	private static String[] unpackOrderList(String str) {
		StringTokenizer tok= new StringTokenizer(str, ";"); //$NON-NLS-1$
		int nTokens= tok.countTokens();
		String[] res= new String[nTokens];
		for (int i= 0; i < nTokens; i++) {
			res[i]= tok.nextToken();
		}
		return res;
	}
	
	private static String packOrderList(List orderList) {
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < orderList.size(); i++) {
			buf.append((String) orderList.get(i));
			buf.append(';');
		}
		return buf.toString();
	}	
	
	
	public static int getImportNumberThreshold() {
		IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
		int threshold= prefs.getInt(PREF_ONDEMANDTHRESHOLD);
		if (threshold < 0) {
			threshold= Integer.MAX_VALUE;
		}		
		return threshold;
	}
	
	public static boolean doIgnoreLowerCaseNames() {
		IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
		return prefs.getBoolean(PREF_IGNORELOWERCASE);
	}
	
	
	/**
	 * Initializes the default values of this page in the preference bundle.
	 * Will be called on startup of the JavaPlugin
	 */
	public static void initDefaults(IPreferenceStore prefs) {
		prefs.setDefault(PREF_IMPORTORDER, "java;javax;com"); //$NON-NLS-1$
		prefs.setDefault(PREF_ONDEMANDTHRESHOLD, 99);
	}
	
	private static class ImportOrganizeLabelProvider extends LabelProvider {
		
		private static final Image PCK_ICON= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_PACKAGE);

		public Image getImage(Object element) {
			return PCK_ICON;
		}
	}
	
	private class ImportOrganizeAdapter implements IListAdapter, IDialogFieldListener {

        public void customButtonPressed(DialogField field, int index) {
        	doButtonPressed(index);
        }
        public void selectionChanged(DialogField field) {
        	doSelectionChanged();
        }
        public void dialogFieldChanged(DialogField field) {
        	if (field == fThresholdField) {
        		doThresholdChanged();
        	}
        }
	}

	private ListDialogField fOrderListField;
	private StringDialogField fThresholdField;
	private SelectionButtonDialogField fIgnoreLowerCaseTypesField;
	
	public ImportOrganizePreferencePage() {
		super();
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		setDescription(JavaUIMessages.getString("ImportOrganizePreferencePage.description")); //$NON-NLS-1$
	
		String[] buttonLabels= new String[] { 
			/* 0 */  JavaUIMessages.getString("ImportOrganizePreferencePage.order.add.button"), //$NON-NLS-1$
			/* 1 */  JavaUIMessages.getString("ImportOrganizePreferencePage.order.edit.button"), //$NON-NLS-1$
			/* 2 */  null,
			/* 3 */  JavaUIMessages.getString("ImportOrganizePreferencePage.order.up.button"), //$NON-NLS-1$
			/* 4 */  JavaUIMessages.getString("ImportOrganizePreferencePage.order.down.button"), //$NON-NLS-1$
			/* 5 */  null,
			/* 6 */  JavaUIMessages.getString("ImportOrganizePreferencePage.order.remove.button"), //$NON-NLS-1$
			/* 7 */  null,
			/* 8 */  JavaUIMessages.getString("ImportOrganizePreferencePage.order.load.button"), //$NON-NLS-1$					
			/* 9 */  JavaUIMessages.getString("ImportOrganizePreferencePage.order.save.button") //$NON-NLS-1$			
		};
				
		ImportOrganizeAdapter adapter= new ImportOrganizeAdapter();
		
		fOrderListField= new ListDialogField(adapter, buttonLabels, new ImportOrganizeLabelProvider());
		fOrderListField.setDialogFieldListener(adapter);
		fOrderListField.setLabelText(JavaUIMessages.getString("ImportOrganizePreferencePage.order.label")); //$NON-NLS-1$
		fOrderListField.setUpButtonIndex(3);
		fOrderListField.setDownButtonIndex(4);
		fOrderListField.setRemoveButtonIndex(6);
		
		fOrderListField.enableButton(1, false);
		
		fThresholdField= new StringDialogField();
		fThresholdField.setDialogFieldListener(adapter);
		fThresholdField.setLabelText(JavaUIMessages.getString("ImportOrganizePreferencePage.threshold.label")); //$NON-NLS-1$
	
		fIgnoreLowerCaseTypesField= new SelectionButtonDialogField(SWT.CHECK);
		fIgnoreLowerCaseTypesField.setLabelText(JavaUIMessages.getString("ImportOrganizePreferencePage.ignoreLowerCase.label"));
	}
	
	/**
	 * @see PreferencePage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		// added for 1GEUGE6: ITPJUI:WIN2000 - Help is the same on all preference pages
		super.createControl(parent);
		WorkbenchHelp.setHelp(getControl(), IJavaHelpContextIds.ORGANIZE_IMPORTS_PREFERENCE_PAGE);
	}
	
	protected Control createContents(Composite parent) {
		initialize(getImportOrderPreference(), getImportNumberThreshold(), doIgnoreLowerCaseNames());
	
		Composite composite= new Composite(parent, SWT.NONE);
		
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		
		composite.setLayout(layout);
		
		fOrderListField.doFillIntoGrid(composite, 3);
		LayoutUtil.setHorizontalSpan(fOrderListField.getLabelControl(null), 2);
		LayoutUtil.setHorizontalGrabbing(fOrderListField.getListControl(null));
		
		fThresholdField.doFillIntoGrid(composite, 2);
		((GridData) fThresholdField.getTextControl(null).getLayoutData()).grabExcessHorizontalSpace= false;
		
		fIgnoreLowerCaseTypesField.doFillIntoGrid(composite, 2);
		
		return composite;
	}
	
	private void initialize(String[] importOrder, int threshold, boolean ignoreLowerCase) {
		fOrderListField.removeAllElements();
		for (int i= 0; i < importOrder.length; i++) {
			fOrderListField.addElement(importOrder[i]);
		}
		fThresholdField.setText(String.valueOf(threshold));
		fIgnoreLowerCaseTypesField.setSelection(ignoreLowerCase);
	}
	
	private void doThresholdChanged() {
		StatusInfo status= new StatusInfo();
		String thresholdString= fThresholdField.getText();
		try {
			int threshold= Integer.parseInt(thresholdString);
			if (threshold < 0) {
				status.setError(JavaUIMessages.getString("ImportOrganizePreferencePage.error.invalidthreshold")); //$NON-NLS-1$
			}
		} catch (NumberFormatException e) {
			status.setError(JavaUIMessages.getString("ImportOrganizePreferencePage.error.invalidthreshold")); //$NON-NLS-1$
		}
		updateStatus(status);
	}
	
	private void doButtonPressed(int index) {
		if (index == 0) { // add new
			List existing= fOrderListField.getElements();
			ImportOrganizeInputDialog dialog= new ImportOrganizeInputDialog(getShell(), existing);
			if (dialog.open() == dialog.OK) {
				fOrderListField.addElement(dialog.getResult());
			}
		} else if (index == 1) { // edit
			List selected= fOrderListField.getSelectedElements();
			if (selected.isEmpty()) {
				return;
			}
			String editedEntry= (String) selected.get(0);
			
			List existing= fOrderListField.getElements();
			existing.remove(editedEntry);
			
			ImportOrganizeInputDialog dialog= new ImportOrganizeInputDialog(getShell(), existing);
			dialog.setInitialString(editedEntry);
			if (dialog.open() == dialog.OK) {
				fOrderListField.replaceElement(editedEntry, dialog.getResult());
			}
		} else if (index == 8) { // load
			List order= loadImportOrder();
			if (order != null) {
				fOrderListField.setElements(order);
			}
		} else if (index == 9) { // save
			saveImportOrder(fOrderListField.getElements());
		}		
	}
	
	private void doSelectionChanged() {
		// update enable state of the edit button
		List selected= fOrderListField.getSelectedElements();
		fOrderListField.enableButton(1, selected.size() == 1);
	}
	
	/** 
	 * The import order file is a property file with keys
	 * "0", "1" ... last entry.
	 * values must be valid package names
	 */
	private List loadFromProperties(Properties properties) {
		ArrayList res= new ArrayList();
		int nEntries= properties.size();
		for (int i= 0 ; i < nEntries; i++) {
			String curr= properties.getProperty(String.valueOf(i));
			if (curr != null) {
				if (JavaConventions.validatePackageName(curr).isOK()) {
					res.add(curr);
				} else {
					return null;
				}
			} else {
				return res;
			}
		}
		return res;
	}
	
	private List loadImportOrder() {
		FileDialog dialog= new FileDialog(getShell(), SWT.OPEN);
		dialog.setText(JavaUIMessages.getString("ImportOrganizePreferencePage.loadDialog.title")); //$NON-NLS-1$)
		dialog.setFilterExtensions(new String[] {"*.importorder", "*.*"}); //$NON-NLS-1$
		String lastPath= getPreferenceStore().getString(PREF_LASTLOADPATH);
		if (lastPath != null) {
			dialog.setFilterPath(lastPath);
		}
		String fileName= dialog.open();
		if (fileName != null) {
			getPreferenceStore().putValue(PREF_LASTLOADPATH, dialog.getFilterPath());
					
			Properties properties= new Properties();
			FileInputStream fis= null;
			try {
				fis= new FileInputStream(fileName);
				properties.load(fis);
				List res= loadFromProperties(properties);
				if (res != null) {
					return res;
				}
			} catch (IOException e) {
				JavaPlugin.log(e);
			} finally {
				if (fis != null) {
					try { fis.close(); } catch (IOException e) {}
				}
			}
			String title= JavaUIMessages.getString("ImportOrganizePreferencePage.loadDialog.error.title");
			String message= JavaUIMessages.getString("ImportOrganizePreferencePage.loadDialog.error.message");
			MessageDialog.openError(getShell(), title, message);
		}
		return null;
	}
	
	private void saveImportOrder(List elements) {
		FileDialog dialog= new FileDialog(getShell(), SWT.SAVE);
		dialog.setText(JavaUIMessages.getString("ImportOrganizePreferencePage.saveDialog.title")); //$NON-NLS-1$)
		dialog.setFilterExtensions(new String[] {"*.importorder", "*.*"}); //$NON-NLS-1$
		dialog.setFileName("example.importorder");
		String lastPath= getPreferenceStore().getString(PREF_LASTSAVEPATH);
		if (lastPath != null) {
			dialog.setFilterPath(lastPath);
		}
		String fileName= dialog.open();
		if (fileName != null) {
			getPreferenceStore().putValue(PREF_LASTSAVEPATH, dialog.getFilterPath());
			
			Properties properties= new Properties();
			for (int i= 0; i < elements.size(); i++) {
				properties.setProperty(String.valueOf(i), (String) elements.get(i));
			}
			FileOutputStream fos= null;
			try {
				fos= new FileOutputStream(fileName);
				properties.store(fos, "Organize Import Order");
			} catch (IOException e) {
				JavaPlugin.log(e);
				String title= JavaUIMessages.getString("ImportOrganizePreferencePage.saveDialog.error.title");
				String message= JavaUIMessages.getString("ImportOrganizePreferencePage.saveDialog.error.message");
				MessageDialog.openError(getShell(), title, message);				
			} finally {
				if (fos != null) {
					try { fos.close(); } catch (IOException e) {}
				}
			}
		}
	}

	public void init(IWorkbench workbench) {
	}
	
	private void updateStatus(IStatus status) {
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}	
	
    /**
     * @see PreferencePage#performDefaults()
     */
    protected void performDefaults() {
		String[] order;
		IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
		String str= prefs.getDefaultString(PREF_IMPORTORDER);
		if (str != null) {
			order= unpackOrderList(str);
		} else {
			order= new String[0];
		}
		int threshold= prefs.getDefaultInt(PREF_ONDEMANDTHRESHOLD);
		if (threshold < 0) {
			threshold= Integer.MAX_VALUE;
		}
		boolean ignoreLowerCase= prefs.getDefaultBoolean(PREF_IGNORELOWERCASE);
		initialize(order, threshold, ignoreLowerCase);		
    }

    /**
     * @see IPreferencePage#performOk()
     */
    public boolean performOk() {
  		IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
  		prefs.setValue(PREF_IMPORTORDER, packOrderList(fOrderListField.getElements()));
  		prefs.setValue(PREF_ONDEMANDTHRESHOLD, fThresholdField.getText());
  		prefs.setValue(PREF_IGNORELOWERCASE, fIgnoreLowerCaseTypesField.isSelected());
        return true;
    }

}


