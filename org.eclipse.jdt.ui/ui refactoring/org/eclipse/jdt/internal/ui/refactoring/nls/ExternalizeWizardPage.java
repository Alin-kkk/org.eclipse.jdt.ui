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
package org.eclipse.jdt.internal.ui.refactoring.nls;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.UserInputWizardPage;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSubstitution;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRegion;

class ExternalizeWizardPage extends UserInputWizardPage {

	private static final String[] PROPERTIES;
	private static final String[] fgTitles;
	private static final int TASK_PROP= 0; 
	private static final int KEY_PROP= 1; 
	private static final int VAL_PROP= 2; 
	private static final int SIZE= 3; //column counter
	private static final int ROW_COUNT= 5;
	public static final String DEFAULT_KEY_PREFIX= ""; //$NON-NLS-1$
	
	public static final String PAGE_NAME= "NLSWizardPage1"; //$NON-NLS-1$
	static {
		PROPERTIES= new String[SIZE];
		PROPERTIES[TASK_PROP]= "task"; //$NON-NLS-1$
		PROPERTIES[KEY_PROP]= "key"; //$NON-NLS-1$
		PROPERTIES[VAL_PROP]= "value"; //$NON-NLS-1$
		
		fgTitles= new String[SIZE];
		fgTitles[TASK_PROP]= ""; //$NON-NLS-1$
		fgTitles[KEY_PROP]= NLSUIMessages.getString("ExternalizeWizard.key");  //$NON-NLS-1$
		fgTitles[VAL_PROP]= NLSUIMessages.getString("ExternalizeWizard.value");  //$NON-NLS-1$
	}
	
	private class CellModifier implements ICellModifier {
		
		/**
		 * @see ICellModifier#canModify(Object, String)
		 */
		public boolean canModify(Object element, String property) {
			if (property == null)
				return false;
			if (PROPERTIES[VAL_PROP].equals(property))
				return false;
			if (! (element instanceof NLSSubstitution))	
				return false;
				
			if (PROPERTIES[TASK_PROP].equals(property))
				return true;						
				
			return (((NLSSubstitution)element).task == NLSSubstitution.TRANSLATE); 	
		}
		
		/**
		 * @see ICellModifier#getValue(Object, String)
		 */
		public Object getValue(Object element, String property) {
			if (element instanceof NLSSubstitution) {
				NLSSubstitution s= (NLSSubstitution) element;
				if (PROPERTIES[KEY_PROP].equals(property))
					return s.key;
				if (PROPERTIES[VAL_PROP].equals(property))
					return s.value.toString();
				if (PROPERTIES[TASK_PROP].equals(property)){
					return new Integer(s.task);
				}	
			}
			return null;
		}
		
		/**
		 * @see ICellModifier#modify(Object, String, Object)
		 */
		public void modify(Object element, String property, Object value) {
			if (element instanceof TableItem) {
				Object data= ((TableItem) element).getData();
				if (data instanceof NLSSubstitution) {
					NLSSubstitution s= (NLSSubstitution) data;
					if (PROPERTIES[KEY_PROP].equals(property)) {
						s.key= (String) value;
						fViewer.update(s, new String[] { property });
					}
					if (PROPERTIES[TASK_PROP].equals(property)) {
						s.task= ((Integer)value).intValue();
						updateLabels();
						fViewer.update(s, new String[] { property });
					}
				}
			}
		}
	};
	
	
	private class NlsSubstitutionLabelProvider extends LabelProvider implements ITableLabelProvider {
		
		public String getColumnText(Object element, int columnIndex) {
			if (element instanceof NLSSubstitution) {
				NLSSubstitution s= (NLSSubstitution) element;
				if (columnIndex == KEY_PROP){
					if (s.task == NLSSubstitution.TRANSLATE)
						return s.key;
					else
						return "";//$NON-NLS-1$
				}	
				if (columnIndex == VAL_PROP)
					return s.value.getValue();
			}
			return ""; //$NON-NLS-1$
		}
		
		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex != TASK_PROP)
				return null;
			if (element instanceof NLSSubstitution)
				return getNLSImage((NLSSubstitution) element);
			return null;	
		}
	};
	
	private static Image getNLSImage(NLSSubstitution sub){
		return getNLSImage(sub.task);
	}
	
	private static Image getNLSImage(int task){
		switch (task){
			case NLSSubstitution.TRANSLATE:
				return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_TRANSLATE);
			case NLSSubstitution.NEVER_TRANSLATE:	
				return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_NEVER_TRANSLATE);
			case NLSSubstitution.SKIP:
				return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_SKIP);	
			default:
				Assert.isTrue(false);	
				return null;
		}
	}
	
	private Text fPrefixField;
	private Table fTable;
	private TableViewer fViewer;
	private SourceViewer fSourceViewer;
	private Label fTranslateLabel, fNoTranslateLabel, fSkipLabel;
	private CLabel fPreviewLabel;
	private Button fEditButton;
	
	public ExternalizeWizardPage() {
		super(PAGE_NAME, false); //$NON-NLS-1$
	}
	
	private ICompilationUnit getCu(){
		return ((NLSRefactoring)getRefactoring()).getCu();
	}
	
	/*
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		
		Composite supercomposite= new Composite(parent, SWT.NONE);
		supercomposite.setLayout(new GridLayout());
		
		createKeyPrefixField(supercomposite);

		SashForm composite= new SashForm(supercomposite, SWT.VERTICAL);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
				
		createTableViewer(composite);
		createSourceViewer(composite);
				
		composite.setWeights(new int[]{55, 45});
	 
		createLabels(supercomposite);
		
		// promote control
		setControl(supercomposite);
		Dialog.applyDialogFont(supercomposite);
		WorkbenchHelp.setHelp(supercomposite, IJavaHelpContextIds.EXTERNALIZE_WIZARD_KEYVALUE_PAGE);
	}
	
	private void createTableViewer(Composite composite){
		createTableComposite(composite);
		
		/*
		 * Feature of CellEditors - double click is ignored.
		 * The workaround is to register my own listener and force the desired 
		 * behavior.
		 */
		fViewer= new TableViewer(fTable){
				protected void hookControl(Control control) {
					super.hookControl(control);
					((Table) control).addMouseListener(new MouseAdapter() {
						public void mouseDoubleClick(MouseEvent e) {
							if (getTable().getSelection().length == 0)
								return;
							TableItem item= getTable().getSelection()[0];
							if (item.getBounds(TASK_PROP).contains(e.x, e.y)){
								List widgetSel= getSelectionFromWidget();
								if (widgetSel == null || widgetSel.size() != 1)
									return;
								NLSSubstitution s= (NLSSubstitution)widgetSel.get(0);
								Integer value= (Integer)getCellModifier().getValue(s, PROPERTIES[TASK_PROP]);
								int newValue= MultiStateCellEditor.getNextValue(NLSSubstitution.STATE_COUNT, value.intValue());
								getCellModifier().modify(item, PROPERTIES[TASK_PROP], new Integer(newValue));
							}	
						}
					});
				}
		};
		fViewer.setUseHashlookup(true);
		
		final CellEditor editors[]= new CellEditor[SIZE];
		editors[TASK_PROP]= new MultiStateCellEditor(fTable, NLSSubstitution.STATE_COUNT, NLSSubstitution.DEFAULT);
		editors[VAL_PROP]= new TextCellEditor(fTable);
		
		class AutoApplyTextCellEditor extends TextCellEditor {
			public AutoApplyTextCellEditor(Composite parent) {
				super(parent);
			}
			public void fireApplyEditorValue() {
				super.fireApplyEditorValue();
			}
		};
		editors[KEY_PROP]= new AutoApplyTextCellEditor(fTable);
		editors[KEY_PROP].getControl().addFocusListener(new FocusAdapter() {
			public void focusLost(FocusEvent e) {
				((AutoApplyTextCellEditor)editors[KEY_PROP]).fireApplyEditorValue();
			}
		});

		fViewer.setCellEditors(editors);
		
		fViewer.setColumnProperties(PROPERTIES);
		fViewer.setCellModifier(new CellModifier());

		fViewer.setContentProvider(new NLSSubstitutionContentProvider());
		fViewer.setLabelProvider(new NlsSubstitutionLabelProvider());
		
		fViewer.setInput(getCu());
		
		fViewer.addDoubleClickListener(new IDoubleClickListener(){
			public void doubleClick(DoubleClickEvent event) {
				Set selected= getSelectedTableEntries();
				if (selected.size() != 1)
					return;
				NLSSubstitution nls= (NLSSubstitution)selected.iterator().next();
				if (nls.task == NLSSubstitution.TRANSLATE)
					openEditButton(event.getSelection());
			}
		});
		fViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				ExternalizeWizardPage.this.selectionChanged(event);
			}
		});		
		fViewer.getControl().addFocusListener(new FocusAdapter(){
			public void focusLost(FocusEvent e) {
				fPreviewLabel.setText(""); //$NON-NLS-1$
			}
		});
	}
	
	private void createSourceViewer(Composite parent){
		Composite c= new Composite(parent, SWT.NONE);
		c.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout gl= new GridLayout();
		gl.marginHeight= 0;
		gl.marginWidth= 0;
        c.setLayout(gl);
		
		Label l= new Label(c, SWT.NONE);
		l.setText(NLSUIMessages.getString("wizardPage.context")); //$NON-NLS-1$
		l.setLayoutData(new GridData());
		
		// source viewer
		JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();		
		int styles= SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION;
		fSourceViewer= new SourceViewer(c, null, styles);
		fSourceViewer.configure(new JavaSourceViewerConfiguration(tools, null));
		fSourceViewer.getControl().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));

		try {
			
			String contents= getCu().getBuffer().getContents();
			IDocument document= new Document(contents);
			
			IDocumentPartitioner partitioner= tools.createDocumentPartitioner();
			partitioner.connect(document);
			document.setDocumentPartitioner(partitioner);
			
			fSourceViewer.setDocument(document);
			fSourceViewer.setEditable(false);
			
			GridData gd= new GridData(GridData.FILL_BOTH);
			gd.heightHint= convertHeightInCharsToPixels(10);
			fSourceViewer.getControl().setLayoutData(gd);
			
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, NLSUIMessages.getString("wizardPage.title"), NLSUIMessages.getString("wizardPage.exception")); //$NON-NLS-2$ //$NON-NLS-1$
		}
	}
	
	private void createKeyPrefixField(Composite parent){
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		GridLayout gl= new GridLayout();
		gl.numColumns= 2;
		gl.marginWidth= 0;
		composite.setLayout(gl);
		
		Label l= new Label(composite, SWT.NONE);
		l.setText(NLSUIMessages.getString("wizardPage.common_prefix")); //$NON-NLS-1$
		l.setLayoutData(new GridData());
		
		fPrefixField= new Text(composite, SWT.SINGLE | SWT.BORDER);
		fPrefixField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		fPrefixField.setText(guessPrefix());
		fPrefixField.selectAll();
	}

	private String guessPrefix() {
		String cuName= getCu().getElementName();
		if (cuName.endsWith(".java")) //$NON-NLS-1$
			return cuName.substring(0, cuName.length() - ".java".length()) + "."; //$NON-NLS-2$ //$NON-NLS-1$
		return DEFAULT_KEY_PREFIX;	
	}
	
	private void createLabels(Composite parent){
		Composite labelSuperComposite= new Composite(parent, SWT.NONE);
		GridLayout gl= new GridLayout();
		gl.marginHeight= 0;
		gl.marginWidth= 0;
		labelSuperComposite.setLayout(gl);
		labelSuperComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		fPreviewLabel= new CLabel(labelSuperComposite, SWT.NONE);
		fPreviewLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL));
		
		createNumberLabels(labelSuperComposite);
		
		updateLabels();
	}

	private void createNumberLabels(Composite labelSuperComposite) {
		Composite labelComposite= new Composite(labelSuperComposite, SWT.NONE);
		GridLayout gd= new GridLayout();
		gd.numColumns= 6;
		gd.marginWidth= 0;
		labelComposite.setLayout(gd);
		labelComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label l1= new Label(labelComposite, SWT.NONE);
		l1.setImage(getNLSImage(NLSSubstitution.TRANSLATE));
		l1.setLayoutData(new GridData());
		fTranslateLabel= new Label(labelComposite, SWT.NONE);
		GridData gdata= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
		gdata.widthHint= 60;
		fTranslateLabel.setLayoutData(gdata);
		
		Label l2= new Label(labelComposite, SWT.NONE);
		l2.setImage(getNLSImage(NLSSubstitution.NEVER_TRANSLATE));
		l2.setLayoutData(new GridData());
		fNoTranslateLabel= new Label(labelComposite, SWT.NONE);
		gdata= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
		gdata.widthHint= 60;
		fNoTranslateLabel.setLayoutData(gdata);
		
		Label l3= new Label(labelComposite, SWT.NONE);
		l3.setImage(getNLSImage(NLSSubstitution.SKIP));
		l3.setLayoutData(new GridData());
		fSkipLabel= new Label(labelComposite, SWT.NONE);
		gdata= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
		gdata.widthHint= 60;
		fSkipLabel.setLayoutData(gdata);
	}
	
	private void createTableComposite(Composite parent) {
		Composite comp= new Composite(parent, SWT.NONE);
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout gl= new GridLayout();
		gl.marginWidth= 0;
		gl.marginHeight= 0;
		comp.setLayout(gl);
		
		Label l= new Label(comp, SWT.NONE);
		l.setText(NLSUIMessages.getString("wizardPage.strings_to_externalize")); //$NON-NLS-1$
		l.setLayoutData(new GridData());
		
		createTable(comp);
	}
	
	private void createTable(Composite parent){
		Composite c= new Composite(parent, SWT.NONE);
		GridLayout gl= new GridLayout();
		gl.numColumns= 2;
		gl.marginWidth= 0;
		gl.marginHeight= 0;
		c.setLayout(gl);
		c.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		fTable= new Table(c, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION | SWT.HIDE_SELECTION | SWT.BORDER);
		GridData tableGD= new GridData(GridData.FILL_BOTH);
		tableGD.heightHint= SWTUtil.getTableHeightHint(fTable, ROW_COUNT);	
		tableGD.widthHint= 40;
		fTable.setLayoutData(tableGD);
		
		fTable.setLinesVisible(true);
		
		TableLayout layout= new TableLayout();
		fTable.setLayout(layout);
		fTable.setHeaderVisible(true);
		
		ColumnLayoutData[] columnLayoutData= new ColumnLayoutData[SIZE];
		columnLayoutData[TASK_PROP]= new ColumnPixelData(20, false);
		columnLayoutData[KEY_PROP]= new ColumnWeightData(40, true);
		columnLayoutData[VAL_PROP]= new ColumnWeightData(40, true);
		
		for (int i= 0; i < fgTitles.length; i++) {
			TableColumn tc= new TableColumn(fTable, SWT.NONE, i);
			tc.setText(fgTitles[i]);
			layout.addColumnData(columnLayoutData[i]);
			tc.setResizable(columnLayoutData[i].resizable);
		}
		
		createButtonComposite(c);	
	}
	
	private void createButtonComposite(Composite parent){
		Composite buttonComp= new Composite(parent, SWT.NONE);
		GridLayout gl= new GridLayout();
		gl.marginHeight= 0;
		gl.marginWidth= 0;
		buttonComp.setLayout(gl);
		buttonComp.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		
		Button translateSelected= new Button(buttonComp, SWT.PUSH);
		translateSelected.setText(NLSUIMessages.getString("wizardPage.Translate_Selected")); //$NON-NLS-1$
		translateSelected.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		SWTUtil.setButtonDimensionHint(translateSelected);
		translateSelected.addSelectionListener(new SelectionAdapter(){
			public void widgetSelected(SelectionEvent e) {
				setSelectedTasks(NLSSubstitution.TRANSLATE);
			}
		});
		
		Button noTranslateSelected= new Button(buttonComp, SWT.PUSH);
		noTranslateSelected.setText(NLSUIMessages.getString("wizardPage.Never_Translate_Selected")); //$NON-NLS-1$
		noTranslateSelected.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		SWTUtil.setButtonDimensionHint(noTranslateSelected);
		noTranslateSelected.addSelectionListener(new SelectionAdapter(){
			public void widgetSelected(SelectionEvent e) {
				setSelectedTasks(NLSSubstitution.NEVER_TRANSLATE);
			}
		});
		
		Button skipSelected= new Button(buttonComp, SWT.PUSH);
		skipSelected.setText(NLSUIMessages.getString("wizardPage.Skip_Selected")); //$NON-NLS-1$
		skipSelected.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		SWTUtil.setButtonDimensionHint(skipSelected);
		skipSelected.addSelectionListener(new SelectionAdapter(){
			public void widgetSelected(SelectionEvent e) {
				setSelectedTasks(NLSSubstitution.SKIP);
			}
		});
		
		fEditButton= new Button(buttonComp, SWT.PUSH);
        fEditButton.setText(RefactoringMessages.getString("ExternalizeWizardPage.Edit_Key")); //$NON-NLS-1$
        fEditButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        SWTUtil.setButtonDimensionHint(fEditButton);
        fEditButton.setEnabled(false);
        fEditButton.addSelectionListener(new SelectionAdapter(){
        	public void widgetSelected(SelectionEvent e) {
        		openEditButton(fViewer.getSelection());
        	}
        });
	}
	
	
	private void openEditButton(ISelection selection){
		try{
			Set selected= getSelectedTableEntries();
			Assert.isTrue(selected.size() == 1);
			NLSSubstitution nls= (NLSSubstitution)selected.iterator().next();
			InputDialog dialog= new InputDialog(getShell(), RefactoringMessages.getString("ExternalizeWizardPage.Externalize_Strings"), RefactoringMessages.getString("ExternalizeWizardPage.Enter_New_Key"), nls.key, createKeyValidator());  //$NON-NLS-1$ //$NON-NLS-2$
			if (dialog.open() == InputDialog.CANCEL)
				return;
			nls.key= dialog.getValue();
			fViewer.update(nls, new String[] { PROPERTIES[KEY_PROP] });
		} finally{
			fViewer.refresh();
			fViewer.getControl().setFocus();
			fViewer.setSelection(selection);
		}
	}
	
	private IInputValidator createKeyValidator(){
		return new IInputValidator(){
			public String isValid(String newText) {
				RefactoringStatus result= NLSRefactoring.checkKey(newText);
				if (result == null)
					return null;				
				RefactoringStatusEntry firstFatal= result.getFirstEntry(RefactoringStatus.FATAL);
				if (firstFatal == null)
					return null;				
				return firstFatal.getMessage();
			}
		};
	}
	
	private Set getSelectedTableEntries() {
		ISelection sel= fViewer.getSelection();
		if (sel instanceof IStructuredSelection)
			return new HashSet(((IStructuredSelection)sel).toList());
		else		
			return new HashSet(0);
	}
		
	private void setSelectedTasks(int task){
		Assert.isTrue(task == NLSSubstitution.TRANSLATE 
				   || task == NLSSubstitution.NEVER_TRANSLATE
				   || task == NLSSubstitution.SKIP);
		Set selected= getSelectedTableEntries();		
		String[] props= new String[] { PROPERTIES[TASK_PROP] };
		for (Iterator iter= selected.iterator(); iter.hasNext();) {
			((NLSSubstitution) iter.next()).task= task;
		}
		fViewer.update(selected.toArray(), props);
		updateLabels();
		fViewer.getControl().setFocus();
		if (selected.size() == 1)
			fEditButton.setEnabled(task == NLSSubstitution.TRANSLATE);
	}	
	
	private void updateLabels(){
		NLSSubstitution[] elems= getNlsSubstutions(); //calculate only once
		fTranslateLabel.setText(NLSUIMessages.getString("wizardPage.translate") + NLSSubstitution.countItems(elems, NLSSubstitution.TRANSLATE)); //$NON-NLS-1$
		fNoTranslateLabel.setText(NLSUIMessages.getString("wizardPage.never_translate") + NLSSubstitution.countItems(elems, NLSSubstitution.NEVER_TRANSLATE)); //$NON-NLS-1$
		fSkipLabel.setText(NLSUIMessages.getString("wizardPage.skip") + NLSSubstitution.countItems(elems, NLSSubstitution.SKIP)); //$NON-NLS-1$
	}
			
	private void selectionChanged(SelectionChangedEvent event) {
		ISelection s= event.getSelection();
		if (! (s instanceof IStructuredSelection)) 
			return;
		IStructuredSelection ss= (IStructuredSelection) s;
		updateEditButtonState(ss);
		if (ss.size() == 0){
			fPreviewLabel.setText("");//$NON-NLS-1$
			return;
		}
		NLSSubstitution first= (NLSSubstitution) ss.getFirstElement();
		TextRegion region= first.value.getPosition();
		fSourceViewer.setSelectedRange(region.getOffset(), region.getLength());
		fSourceViewer.revealRange(region.getOffset(), region.getLength());
		if (ss.size() == 1){
			String message= NLSUIMessages.getFormattedString("ExternalizeWizardPage.preview", fPrefixField.getText() + first.key);//$NON-NLS-1$
			fPreviewLabel.setText(message);
		} else{
			String message=  NLSUIMessages.getFormattedString("ExternalizeWizardPage.selected", String.valueOf(ss.size())); //$NON-NLS-1$
			fPreviewLabel.setText(message);
		}	
	}
	
	private void updateEditButtonState(IStructuredSelection selection){
		if (selection.size() != 1){
			fEditButton.setEnabled(false);
			return;
		}	
		NLSSubstitution first= (NLSSubstitution) selection.getFirstElement();	
		fEditButton.setEnabled(first.task == NLSSubstitution.TRANSLATE);
	}
	
	private NLSSubstitutionContentProvider getContentProvider(){
		return (NLSSubstitutionContentProvider)fViewer.getContentProvider();
	}
	
	private void initializeRefactoring(){
		NLSRefactoring ref= (NLSRefactoring) getRefactoring();
		ref.setNlsSubstitutions(addKeyPrefix(getNlsSubstutions(), fPrefixField.getText()));
		ref.setLines(getContentProvider().getLines());
	}
		
	public boolean performFinish(){
		initializeRefactoring();
		
		//when finish is pressed on the first page - we want the settings from the
		//second page to be set to the refactoring object
		((ExternalizeWizardPage2)getWizard().getPage(ExternalizeWizardPage2.PAGE_NAME)).updateRefactoring();
		return super.performFinish();
	}
	
	public IWizardPage getNextPage() {
		initializeRefactoring();
		return super.getNextPage();
	}
	
	public void dispose(){
		//widgets will be disposed. only need to null'em
		fNoTranslateLabel= null;
		fPrefixField= null;
		fSkipLabel= null;
		fSourceViewer= null;
		fTable= null;
		fTranslateLabel= null;
		fViewer= null;
		fEditButton= null;
		super.dispose();
	}
	
	//creates a copy
	private static NLSSubstitution[] addKeyPrefix(NLSSubstitution[] subs, String prefix){
		NLSSubstitution[] result= new NLSSubstitution[subs.length];
		for (int i= 0; i < result.length; i++){
			result[i]= new NLSSubstitution(subs[i]);
			result[i].key = prefix + subs[i].key;
		}	
		return result;	
	}
	
	private NLSSubstitution[] getNlsSubstutions(){
		Object[] input= getContentProvider().getElements(getCu());
		if (input == null)
			return new NLSSubstitution[0];
		NLSSubstitution[] result= new NLSSubstitution[input.length];
		for (int i= 0; i< input.length; i++){
			result[i]= (NLSSubstitution)input[i];
		}
		return result;
	}
}
