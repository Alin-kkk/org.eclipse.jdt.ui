/* * (c) Copyright IBM Corp. 2000, 2001. * All Rights Reserved. */package org.eclipse.jdt.internal.ui.refactoring;import org.eclipse.swt.SWT;import org.eclipse.swt.events.SelectionAdapter;import org.eclipse.swt.events.SelectionEvent;import org.eclipse.swt.layout.GridData;import org.eclipse.swt.layout.GridLayout;import org.eclipse.swt.widgets.Button;import org.eclipse.swt.widgets.Composite;import org.eclipse.swt.widgets.Label;import org.eclipse.swt.widgets.Text;import org.eclipse.ui.help.DialogPageContextComputer;import org.eclipse.ui.help.WorkbenchHelp;import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdatingRefactoring;import org.eclipse.jdt.internal.corext.refactoring.tagging.IRenameRefactoring;import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdatingRefactoring;import org.eclipse.jdt.internal.ui.util.RowLayouter;

public class RenameInputWizardPage extends TextInputWizardPage{
	private String fHelpContextID;		/**
	 * Creates a new text input page.
	 * @param isLastUserPage <code>true</code> if this page is the wizard's last
	 *  user input page. Otherwise <code>false</code>.
	 */
	public RenameInputWizardPage(String contextHelpId, boolean isLastUserPage) {
		super(isLastUserPage);		fHelpContextID= contextHelpId;
	}
	
	/**
	 * Creates a new text input page.
	 * @param isLastUserPage <code>true</code> if this page is the wizard's last
	 *  user input page. Otherwise <code>false</code>.
	 * @param initialSetting the initialSetting.
	 */
	public RenameInputWizardPage(String contextHelpId, boolean isLastUserPage, String initialValue) {
		super(isLastUserPage, initialValue);		fHelpContextID= contextHelpId;
	}
	
	/* non java-doc
	 * @see DialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite result= new Composite(parent, SWT.NONE);
		setControl(result);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;		layout.verticalSpacing= 8;
		result.setLayout(layout);
		RowLayouter layouter= new RowLayouter(2);
		
		Label label= new Label(result, SWT.NONE);
		label.setText(getLabelText());
		
		Text text= createTextInputField(result);
		text.selectAll();
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
				
		layouter.perform(label, text, 1);				addOptionalUpdateReferencesCheckbox(result, layouter);		addOptionalUpdateCommentsAndStringCheckboxes(result, layouter);				WorkbenchHelp.setHelp(getControl(), new DialogPageContextComputer(this, fHelpContextID));	}		private void addOptionalUpdateCommentsAndStringCheckboxes(Composite result, RowLayouter layouter) {		if (!(getRefactoring() instanceof ITextUpdatingRefactoring))			return; 				ITextUpdatingRefactoring refactoring= (ITextUpdatingRefactoring)getRefactoring();		if (!refactoring.canEnableTextUpdating())			return;				addUpdateJavaDocCheckbox(result, layouter, refactoring);		addUpdateCommentsCheckbox(result, layouter, refactoring);		addUpdateStringsCheckbox(result, layouter, refactoring);	}

	private void addOptionalUpdateReferencesCheckbox(Composite result, RowLayouter layouter) {		if (! (getRefactoring() instanceof IReferenceUpdatingRefactoring))			return;		final IReferenceUpdatingRefactoring ref= (IReferenceUpdatingRefactoring)getRefactoring();			if (! ref.canEnableUpdateReferences())				return;
		String title= "Update references to the renamed element";		boolean defaultValue= ref.getUpdateReferences();		final Button checkBox= createCheckbox(result, title, defaultValue, layouter);		ref.setUpdateReferences(checkBox.getSelection());		checkBox.addSelectionListener(new SelectionAdapter(){			public void widgetSelected(SelectionEvent e) {				ref.setUpdateReferences(checkBox.getSelection());			}		});		
	}			private void addUpdateStringsCheckbox(Composite result, RowLayouter layouter, final ITextUpdatingRefactoring refactoring) {		String title= "Update references in string literals";		boolean defaultValue= refactoring.getUpdateStrings();		final Button checkBox= createCheckbox(result, title, defaultValue, layouter);		refactoring.setUpdateStrings(checkBox.getSelection());		checkBox.addSelectionListener(new SelectionAdapter(){			public void widgetSelected(SelectionEvent e) {				refactoring.setUpdateStrings(checkBox.getSelection());			}		});			}	private void  addUpdateCommentsCheckbox(Composite result, RowLayouter layouter, final ITextUpdatingRefactoring refactoring) {		String title= "Update references in regular comments";		boolean defaultValue= refactoring.getUpdateComments();		final Button checkBox= createCheckbox(result, title, defaultValue, layouter);		refactoring.setUpdateComments(checkBox.getSelection());		checkBox.addSelectionListener(new SelectionAdapter(){			public void widgetSelected(SelectionEvent e) {				refactoring.setUpdateComments(checkBox.getSelection());			}		});			}	private void  addUpdateJavaDocCheckbox(Composite result, RowLayouter layouter, final ITextUpdatingRefactoring refactoring) {		String title= "Update references in JavaDoc comments";		boolean defaultValue= refactoring.getUpdateJavaDoc();		final Button checkBox= createCheckbox(result, title, defaultValue, layouter);		refactoring.setUpdateJavaDoc(checkBox.getSelection());		checkBox.addSelectionListener(new SelectionAdapter(){			public void widgetSelected(SelectionEvent e) {				refactoring.setUpdateJavaDoc(checkBox.getSelection());			}		});			}		
	protected String getLabelText(){
		return RefactoringMessages.getString("RenameInputWizardPage.enter_name"); //$NON-NLS-1$
	}
	private IRenameRefactoring getRenameRefactoring(){		return (IRenameRefactoring)getRefactoring();	}		private static Button createCheckbox(Composite parent, String title, boolean value, RowLayouter layouter){		Button checkBox= new Button(parent, SWT.CHECK);		checkBox.setText(title);		checkBox.setSelection(value);		layouter.perform(checkBox);		return checkBox;			}}
