/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

package org.eclipse.jdt.internal.ui.refactoring.sef;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.util.JdtFlags;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.UserInputWizardPage;
import org.eclipse.jdt.internal.ui.util.RowLayouter;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;

public class SelfEncapsulateFieldInputPage extends UserInputWizardPage {

	private SelfEncapsulateFieldRefactoring fRefactoring;

	public SelfEncapsulateFieldInputPage() {
		super(RefactoringMessages.getString("SelfEncapsulateFieldInputPage.inputPage"), true); //$NON-NLS-1$
		setDescription(RefactoringMessages.getString("SelfEncapsulateFieldInputPage.description")); //$NON-NLS-1$
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_REFACTOR_CU);
	}
	
	public void createControl(Composite parent) {
		fRefactoring= (SelfEncapsulateFieldRefactoring)getRefactoring();		
		Composite result= new Composite(parent, SWT.NONE);
		setControl(result);
		initializeDialogUnits(result);
		
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.verticalSpacing= 8;
		result.setLayout(layout);
		RowLayouter layouter= new RowLayouter(layout.numColumns);
		GridData gd= new GridData(GridData.FILL_HORIZONTAL);
		gd.widthHint= convertWidthInCharsToPixels(25);
		layouter.setDefaultGridData(gd, 1);
		
		Label label= new Label(result, SWT.LEFT);
		label.setText(RefactoringMessages.getString("SelfEncapsulateFieldInputPage.getter_name")); //$NON-NLS-1$
		Text text= new Text(result, SWT.BORDER);
		text.setText(fRefactoring.getGetterName());
		layouter.perform(label, text, 1);
		text.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				fRefactoring.setGetterName(((Text)e.widget).getText());
				processValidation();
			}
		});
		text.setFocus();
		
		if (needsModifiers()) {
			label= new Label(result, SWT.LEFT);
			label.setText(RefactoringMessages.getString("SelfEncapsulateFieldInputPage.setter_name")); //$NON-NLS-1$
			text= new Text(result, SWT.BORDER);
			text.setText(fRefactoring.getSetterName());
			layouter.perform(label, text, 1);
			text.addModifyListener(new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					fRefactoring.setSetterName(((Text)e.widget).getText());
					processValidation();
				}
			});
		}			
		
		// createSeparator(result, layouter);
		
		label= new Label(result, SWT.LEFT);
		label.setText(RefactoringMessages.getString("SelfEncapsulateFieldInputPage.insert_after")); //$NON-NLS-1$
		final Combo combo= new Combo(result, SWT.READ_ONLY);
		fillWithPossibleInsertPositions(combo, fRefactoring.getField());
		combo.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				fRefactoring.setInsertionIndex(combo.getSelectionIndex() - 1);
			}
		});
		layouter.perform(label, combo, 1);
		
		createAccessModifier(result, layouter);
		
		createFieldAccessBlock(result);
			
		processValidation();
		
		WorkbenchHelp.setHelp(getControl(), IJavaHelpContextIds.SEF_WIZARD_PAGE);		
	}

	private void createAccessModifier(Composite result, RowLayouter layouter) {
		int visibility= fRefactoring.getVisibility();
		if (Flags.isPublic(visibility))
			return;
		GridLayout layout;
		Label label;
		label= new Label(result, SWT.NONE);
		label.setText(RefactoringMessages.getString("SelfEncapsulateFieldInputPage.access_Modifiers")); //$NON-NLS-1$
		
		Composite group= new Composite(result, SWT.NONE);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		layout= new GridLayout();
		layout.numColumns= 4; layout.marginWidth= 0;
		group.setLayout(layout);
		
		Object[] info= createData(visibility);
		String[] labels= (String[])info[0];
		Integer[] data= (Integer[])info[1];
		for (int i= 0; i < labels.length; i++) {
			Button radio= new Button(group, SWT.RADIO);
			radio.setText(labels[i]);
			radio.setData(data[i]);
			int iData= ((Integer)data[i]).intValue();
			if (iData == visibility)
				radio.setSelection(true);
			radio.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent event) {
					fRefactoring.setVisibility(((Integer)event.widget.getData()).intValue());
				}
			});
		}
		layouter.perform(label, group, 1);	
	}
	
	public void createFieldAccessBlock(Composite result) {
		int indent= convertWidthInCharsToPixels(2);
		Composite block= new Composite(result, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginWidth= 0; layout.marginHeight= 0;
		block.setLayout(layout);
		Label label= new Label(block, SWT.LEFT);
		label.setText(RefactoringMessages.getString("SelfEncapsulateField.field_access")); //$NON-NLS-1$
		Button radio= new Button(block, SWT.RADIO);
		radio.setText(RefactoringMessages.getString("SelfEncapsulateField.use_setter_getter")); //$NON-NLS-1$
		radio.setSelection(true);
		radio.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fRefactoring.setEncapsulateDeclaringClass(true);
			}
		});
		GridData data= new GridData();
		data.horizontalIndent= indent;
		radio.setLayoutData(data);
		
		radio= new Button(block, SWT.RADIO);
		radio.setText(RefactoringMessages.getString("SelfEncapsulateField.keep_references")); //$NON-NLS-1$
		radio.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fRefactoring.setEncapsulateDeclaringClass(false);
			}
		});
		data= new GridData();
		data.horizontalIndent= indent;
		radio.setLayoutData(data);
	}

	private Object[] createData(int visibility) {
		String pub= RefactoringMessages.getString("SelfEncapsulateFieldInputPage.public"); //$NON-NLS-1$
		String pro= RefactoringMessages.getString("SelfEncapsulateFieldInputPage.protected"); //$NON-NLS-1$
		String def= RefactoringMessages.getString("SelfEncapsulateFieldInputPage.default"); //$NON-NLS-1$
		String priv= RefactoringMessages.getString("SelfEncapsulateFieldInputPage.private"); //$NON-NLS-1$
		
		String[] labels= null;
		Integer[] data= null;
		if (Flags.isPrivate(visibility)) {
			labels= new String[] { pub, pro, def, priv };
			data= new Integer[] {new Integer(Flags.AccPublic), new Integer(Flags.AccProtected), new Integer(0), new Integer(Flags.AccPrivate) };
		} else if (Flags.isProtected(visibility)) {
			labels= new String[] { pub, pro };
			data= new Integer[] {new Integer(Flags.AccPublic), new Integer(Flags.AccProtected)};
		} else {
			labels= new String[] { pub, def };
			data= new Integer[] {new Integer(Flags.AccPublic), new Integer(0)};
		}
		return new Object[] {labels, data};
	}
	
	private void createSeparator(Composite result, RowLayouter layouter) {
		Label label;
		label= new Label(result, SWT.SEPARATOR | SWT.HORIZONTAL);
		label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		layouter.perform(label);
	}
	
	private void fillWithPossibleInsertPositions(Combo combo, IField field) {
		int select= 0;
		combo.add(RefactoringMessages.getString("SelfEncapsulateFieldInputPage.first_method")); //$NON-NLS-1$
		try {
			IMethod[] methods= field.getDeclaringType().getMethods();
			for (int i= 0; i < methods.length; i++) {
				combo.add(JavaElementLabels.getElementLabel(methods[i], JavaElementLabels.M_PARAMETER_TYPES));
			}
			if (methods.length > 0)
				select= methods.length;
		} catch (JavaModelException e) {
			// Fall through
		}
		combo.select(select);
		fRefactoring.setInsertionIndex(select - 1);
	}
	
	private void processValidation() {
		RefactoringStatus status= fRefactoring.checkMethodNames();
		String message= null;
		boolean valid= true;
		if (status.hasFatalError()) {
			message= status.getFirstMessage(RefactoringStatus.FATAL);
			valid= false;
		}
		setErrorMessage(message);
		setPageComplete(valid);
	}
	
	private boolean needsModifiers() {
		try {
			return !JdtFlags.isFinal(fRefactoring.getField());
		} catch(JavaModelException e) {
			return true;
		}
	}	
}