/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.preferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;

import org.eclipse.jface.text.Assert;

import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.ExtendedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.MarkerAnnotationPreferences;
import org.eclipse.ui.texteditor.quickdiff.QuickDiff;
import org.eclipse.ui.texteditor.quickdiff.ReferenceProviderDescriptor;

/**
 * Configures quick diff preferences
 * 
 * @since 3.0
 */
class QuickdiffConfigurationBlock {
	
	public interface IMessages {
		String getString(String key);
	}

	private String fPrefix;
	private IMessages fMessages;
	
	private IPreferenceStore fStore;
	
	private Map fCheckBoxes= new HashMap();
	private SelectionListener fCheckBoxListener= new SelectionListener() {
		public void widgetDefaultSelected(SelectionEvent e) {
		}
		public void widgetSelected(SelectionEvent e) {
			Button button= (Button) e.widget;
			fStore.setValue((String) fCheckBoxes.get(button), button.getSelection());
		}
	};
	
	/**
	 * List for the reference provider default. 
	 * @since 3.0
	 */
	private org.eclipse.swt.widgets.List fQuickDiffProviderList;
	/**
	 * The reference provider default's list model.
	 * @since 3.0 
	 */
	private String[][] fQuickDiffProviderListModel;
	/**
	 * Button controlling default setting of the selected reference provider.
	 * @since 3.0
	 */
	private Button fSetDefaultButton;
	/**
	 * The quick diff color model.
	 * @since 3.0 
	 */
	private String[][] fQuickDiffModel;
	/**
	 * The color editors for quick diff.
	 * @since 3.0
	 */
	private ColorEditor[] fQuickDiffColorEditors;
	/**
	 * The checkbox for the quick diff overview ruler property.
	 * @since 3.0
	 */
	private Button fQuickDiffOverviewRulerCheckBox;

	

	public QuickdiffConfigurationBlock(IPreferenceStore store, String prefix, IMessages messages) {
		Assert.isNotNull(store);
		Assert.isNotNull(prefix);
		Assert.isNotNull(messages);
		fPrefix= prefix;
		fMessages= messages;
		fStore= store;
		MarkerAnnotationPreferences markerAnnotationPreferences= new MarkerAnnotationPreferences();
		fQuickDiffModel= createQuickDiffModel(markerAnnotationPreferences);
		fQuickDiffProviderListModel= createQuickDiffReferenceListModel();
	}
	
	private String[][] createQuickDiffModel(MarkerAnnotationPreferences preferences) {
		String[][] items= new String[3][];

		Iterator e= preferences.getAnnotationPreferences().iterator();
		while (e.hasNext()) {
			AnnotationPreference info= (AnnotationPreference) e.next();
			if (info.getAnnotationType().equals("org.eclipse.ui.workbench.texteditor.quickdiffChange")) //$NON-NLS-1$
				items[0]= new String[] { info.getColorPreferenceKey(), info.getOverviewRulerPreferenceKey(), fPrefix + ".quickdiff.changeColor" }; //$NON-NLS-1$
			else if (info.getAnnotationType().equals("org.eclipse.ui.workbench.texteditor.quickdiffAddition")) //$NON-NLS-1$
				items[1]= new String[] { info.getColorPreferenceKey(), info.getOverviewRulerPreferenceKey(), fPrefix + ".quickdiff.additionColor" }; //$NON-NLS-1$
			else if (info.getAnnotationType().equals("org.eclipse.ui.workbench.texteditor.quickdiffDeletion")) //$NON-NLS-1$
				items[2]= new String[] { info.getColorPreferenceKey(), info.getOverviewRulerPreferenceKey(), fPrefix + ".quickdiff.deletionColor" }; //$NON-NLS-1$
		}
		return items;
	}
	
	private String[][] createQuickDiffReferenceListModel() {
		java.util.List descriptors= new QuickDiff().getReferenceProviderDescriptors();
		ArrayList listModelItems= new ArrayList();
		for (Iterator it= descriptors.iterator(); it.hasNext();) {
			ReferenceProviderDescriptor descriptor= (ReferenceProviderDescriptor) it.next();
			String label= descriptor.getLabel();
			int i= label.indexOf('&');
			while (i >= 0) {
				if (i < label.length())
					label= label.substring(0, i) + label.substring(i+1);
				else
					label.substring(0, i);
				i= label.indexOf('&');
			}
			listModelItems.add(new String[] { descriptor.getId(), label });
		}
		String[][] items= new String[listModelItems.size()][];
		listModelItems.toArray(items);
		return items;
	}
	
	private Button addCheckBox(Composite parent, String label, String key, int indentation) {		
		Button checkBox= new Button(parent, SWT.CHECK);
		checkBox.setText(label);
		
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalIndent= indentation;
		gd.horizontalSpan= 2;
		checkBox.setLayoutData(gd);
		checkBox.addSelectionListener(fCheckBoxListener);
		
		fCheckBoxes.put(checkBox, key);
		
		return checkBox;
	}
	
	/**
	 * Creates page for hover preferences.
	 */
	public Control createControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout(); layout.numColumns= 2;
		composite.setLayout(layout);

		String label= fMessages.getString(fPrefix + ".quickdiff.showForNewEditors"); //$NON-NLS-1$
		addCheckBox(composite, label, ExtendedTextEditorPreferenceConstants.QUICK_DIFF_ALWAYS_ON, 0);

		label= fMessages.getString(fPrefix + ".quickdiff.showChangeRuler"); //$NON-NLS-1$
		addCheckBox(composite, label, ExtendedTextEditorPreferenceConstants.QUICK_DIFF_SHOW_CHANGE_RULER, 0);

		label= fMessages.getString(fPrefix + ".quickdiff.characterMode"); //$NON-NLS-1$
		addCheckBox(composite, label, ExtendedTextEditorPreferenceConstants.QUICK_DIFF_CHARACTER_MODE, 0);

		label= fMessages.getString(fPrefix + ".quickdiff.showInOverviewRuler"); //$NON-NLS-1$
		fQuickDiffOverviewRulerCheckBox= new Button(composite, SWT.CHECK);
		fQuickDiffOverviewRulerCheckBox.setText(label);
		
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalIndent= 0;
		gd.horizontalSpan= 2;
		fQuickDiffOverviewRulerCheckBox.setLayoutData(gd);
		fQuickDiffOverviewRulerCheckBox.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				for (int i= 0; i < fQuickDiffModel.length; i++) {
					fStore.setValue(fQuickDiffModel[i][1], fQuickDiffOverviewRulerCheckBox.getSelection());
				}
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		
		// spacer
		Label l= new Label(composite, SWT.LEFT );
		gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 2;
		gd.heightHint= 5;
		l.setLayoutData(gd);

		Group group= new Group(composite, SWT.NONE);
		group.setText(fMessages.getString(fPrefix + ".quickdiff.colorTitle")); //$NON-NLS-1$
		layout= new GridLayout();
		layout.numColumns= 2;
		group.setLayout(layout);
		gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalSpan= 2;
		group.setLayoutData(gd);
		
		fQuickDiffColorEditors= new ColorEditor[3];
		for (int i= 0; i < fQuickDiffModel.length; i++) {
			label= fMessages.getString(fQuickDiffModel[i][2]); //$NON-NLS-1$
			l= new Label(group, SWT.LEFT);
			l.setText(label);
			final ColorEditor editor= new ColorEditor(group);
			fQuickDiffColorEditors[i]= editor;
			Button changeColorButton= editor.getButton();
			gd= new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalAlignment= GridData.BEGINNING;
			changeColorButton.setLayoutData(gd);
			changeColorButton.addSelectionListener(new SelectionListener() {
				public void widgetDefaultSelected(SelectionEvent e) {
					// do nothing
				}
				
				public void widgetSelected(SelectionEvent e) {
					String key= fQuickDiffModel[0][0];
					PreferenceConverter.setValue(fStore, key, editor.getColorValue());
				}
			});
		}

		
		// spacer
		l= new Label(composite, SWT.LEFT );
		gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 2;
		gd.heightHint= 5;
		l.setLayoutData(gd);
		
		l= new Label(composite, SWT.LEFT);
		l.setText(fMessages.getString(fPrefix + ".quickdiff.referenceProviderTitle")); //$NON-NLS-1$
		gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 2;
		l.setLayoutData(gd);

		Composite editorComposite= new Composite(composite, SWT.NONE);
		layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		editorComposite.setLayout(layout);
		gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_VERTICAL);
		gd.horizontalSpan= 2;
		editorComposite.setLayoutData(gd);		

		fQuickDiffProviderList= new List(editorComposite, SWT.SINGLE | SWT.V_SCROLL | SWT.BORDER);
		gd= new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
		gd.heightHint= 60;
		fQuickDiffProviderList.setLayoutData(gd);
						
		Composite stylesComposite= new Composite(editorComposite, SWT.NONE);
		layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 2;
		stylesComposite.setLayout(layout);
		stylesComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		fSetDefaultButton= new Button(stylesComposite, SWT.PUSH);
		fSetDefaultButton.setText(fMessages.getString(fPrefix + ".quickdiff.setDefault")); //$NON-NLS-1$
		gd= new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalAlignment= GridData.BEGINNING;
		gd.horizontalSpan= 2;
		fSetDefaultButton.setLayoutData(gd);
		
		fQuickDiffProviderList.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
			
			public void widgetSelected(SelectionEvent e) {
				handleProviderListSelection();
			}

		});
		
		fSetDefaultButton.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
			
			public void widgetSelected(SelectionEvent e) {
				int i= fQuickDiffProviderList.getSelectionIndex();
				for (int j= 0; j < fQuickDiffProviderListModel.length; j++) {
					if (fStore.getString(ExtendedTextEditorPreferenceConstants.QUICK_DIFF_DEFAULT_PROVIDER).equals(fQuickDiffProviderListModel[j][0])) {
						fQuickDiffProviderList.remove(j);
						fQuickDiffProviderList.add(fQuickDiffProviderListModel[j][1], j);
					}
					if (i == j) {
						fQuickDiffProviderList.remove(j);
						fQuickDiffProviderList.add(fQuickDiffProviderListModel[j][1] + " " + fMessages.getString(fPrefix + ".quickdiff.defaultlabel"), j);  //$NON-NLS-1$//$NON-NLS-2$
					}
				}
				fSetDefaultButton.setEnabled(false);
				fQuickDiffProviderList.setSelection(i);
				fQuickDiffProviderList.redraw();
				
				fStore.setValue(ExtendedTextEditorPreferenceConstants.QUICK_DIFF_DEFAULT_PROVIDER, fQuickDiffProviderListModel[i][0]);
			}
		});
		
		return composite;
	}

	public void initialize() {
		
		for (int i= 0; i < fQuickDiffProviderListModel.length; i++) {
			String label= fQuickDiffProviderListModel[i][1];
			if (fStore.getString(ExtendedTextEditorPreferenceConstants.QUICK_DIFF_DEFAULT_PROVIDER).equals(fQuickDiffProviderListModel[i][0]))
				label += " " + PreferenceMessages.getString(fPrefix + ".quickdiff.defaultlabel"); //$NON-NLS-1$ //$NON-NLS-2$
			fQuickDiffProviderList.add(label);
		}
		fQuickDiffProviderList.getDisplay().asyncExec(new Runnable() {
			public void run() {
				if (fQuickDiffProviderList != null && !fQuickDiffProviderList.isDisposed()) {
					fQuickDiffProviderList.select(0);
					handleProviderListSelection();
				}
			}
		});

	}

	public void initializeFields() {
		Iterator e= fCheckBoxes.keySet().iterator();
		while (e.hasNext()) {
			Button b= (Button) e.next();
			String key= (String) fCheckBoxes.get(b);
			b.setSelection(fStore.getBoolean(key));
		}
        
		updateQuickDiffControls();
	}

	public void performOk() {
	}

	public void performDefaults() {
		initializeFields();
		handleProviderListSelection();
	}

	private void handleProviderListSelection() {
		int i= fQuickDiffProviderList.getSelectionIndex();
		
		boolean b= fStore.getString(ExtendedTextEditorPreferenceConstants.QUICK_DIFF_DEFAULT_PROVIDER).equals(fQuickDiffProviderListModel[i][0]);
		fSetDefaultButton.setEnabled(!b);
	}
	
	private void updateQuickDiffControls() {
		boolean quickdiffOverviewRuler= false;
		for (int i= 0; i < fQuickDiffModel.length; i++) {
			fQuickDiffColorEditors[i].setColorValue(PreferenceConverter.getColor(fStore, fQuickDiffModel[i][0]));
			quickdiffOverviewRuler |= fStore.getBoolean(fQuickDiffModel[i][1]);
		}
		fQuickDiffOverviewRulerCheckBox.setSelection(quickdiffOverviewRuler);
	}
}
