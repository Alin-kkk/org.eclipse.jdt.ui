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
package org.eclipse.jdt.internal.corext.refactoring.reorg2;

import org.eclipse.core.resources.IResource;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.dialogs.Dialog;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.refactoring.QualifiedNameComponent;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard;
import org.eclipse.jdt.internal.ui.reorg.ReorgMessages;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;


public class ReorgMoveWizard extends RefactoringWizard{

	//TODO fix help context id
	private static final String HELP_CONTEXT_ID= "";

	public ReorgMoveWizard(MoveRefactoring2 ref) {
		super(ref, "Move", HELP_CONTEXT_ID);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	protected void addUserInputPages() {
		addPage(new MoveInputPage());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard#hasPreviewPage()
	 */
	protected boolean hasPreviewPage() {
		return getMoveRefactoring().canUpdateReferences() || getMoveRefactoring().canEnableQualifiedNameUpdating();
	}

	private MoveRefactoring2 getMoveRefactoring(){
		return (MoveRefactoring2) getRefactoring();
	}

	private static class MoveInputPage extends ReorgUserInputPage{

		private static final String PAGE_NAME= "MoveInputPage"; //$NON-NLS-1$
		private Button fReferenceCheckbox;
		private Button fQualifiedNameCheckbox;
		private QualifiedNameComponent fQualifiedNameComponent;
		
		public MoveInputPage() {
			super(PAGE_NAME);
		}

		private MoveRefactoring2 getMoveRefactoring(){
			return (MoveRefactoring2) getRefactoring();
		}

		protected Object getInitiallySelectedElement() {
			return getMoveRefactoring().getCommonParentForInputElements();
		}
		
		protected RefactoringStatus verifyDestination(Object selected) throws JavaModelException{
			MoveRefactoring2 refactoring= getMoveRefactoring();
			final RefactoringStatus refactoringStatus;
			if (selected instanceof IJavaElement)
				refactoringStatus= refactoring.setDestination((IJavaElement)selected);
			else if (selected instanceof IResource)
				refactoringStatus= refactoring.setDestination((IResource)selected);
			else refactoringStatus= RefactoringStatus.createFatalErrorStatus("The selected element cannot be the destination of this operation.");
			
			updateUIStatus();
			return refactoringStatus;
		}
	
		private void updateUIStatus() {
			getRefactoringWizard().setPreviewReview(false);
			MoveRefactoring2 refactoring= getMoveRefactoring();
			if (fReferenceCheckbox != null){
				fReferenceCheckbox.setEnabled(canUpdateReferences());
				refactoring.setUpdateReferences(fReferenceCheckbox.getEnabled() && fReferenceCheckbox.getSelection());
			}
			if (fQualifiedNameCheckbox != null){
				boolean enabled= refactoring.canEnableQualifiedNameUpdating();
				fQualifiedNameCheckbox.setEnabled(enabled);
				if (enabled) {
					fQualifiedNameComponent.setEnabled(refactoring.getUpdateQualifiedNames());
					if (refactoring.getUpdateQualifiedNames())
						getRefactoringWizard().setPreviewReview(true);
				} else {
					fQualifiedNameComponent.setEnabled(false);
				}
				refactoring.setUpdateQualifiedNames(fQualifiedNameCheckbox.getEnabled() && fQualifiedNameCheckbox.getSelection());
			}
		}

		private void addUpdateReferenceComponent(Composite result) {
			final MoveRefactoring2 refactoring= getMoveRefactoring();
			if (! refactoring.canUpdateReferences())
				return;
			fReferenceCheckbox= new Button(result, SWT.CHECK);
			fReferenceCheckbox.setText(ReorgMessages.getString("JdtMoveAction.update_references")); //$NON-NLS-1$
			fReferenceCheckbox.setSelection(refactoring.getUpdateReferences());
			fReferenceCheckbox.setEnabled(canUpdateReferences());
			
			fReferenceCheckbox.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					refactoring.setUpdateReferences(((Button)e.widget).getSelection());
					updateUIStatus();
				}
			});
		}

		private void addUpdateQualifiedNameComponent(Composite parent, int marginWidth) {
			final MoveRefactoring2 refactoring= getMoveRefactoring();
			if (!refactoring.canEnableQualifiedNameUpdating() || !refactoring.canUpdateQualifiedNames())
				return;
			fQualifiedNameCheckbox= new Button(parent, SWT.CHECK);
			int indent= marginWidth + fQualifiedNameCheckbox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
			fQualifiedNameCheckbox.setText(RefactoringMessages.getString("RenameInputWizardPage.update_qualified_names")); //$NON-NLS-1$
			fQualifiedNameCheckbox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fQualifiedNameCheckbox.setSelection(refactoring.getUpdateQualifiedNames());
		
			fQualifiedNameComponent= new QualifiedNameComponent(parent, SWT.NONE, refactoring, getRefactoringSettings());
			fQualifiedNameComponent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			GridData gd= (GridData)fQualifiedNameComponent.getLayoutData();
			gd.horizontalAlignment= GridData.FILL;
			gd.horizontalIndent= indent;
			fQualifiedNameComponent.setEnabled(false);

			fQualifiedNameCheckbox.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					boolean enabled= ((Button)e.widget).getSelection();
					fQualifiedNameComponent.setEnabled(enabled);
					refactoring.setUpdateQualifiedNames(enabled);
					updateUIStatus();
				}
			});
		}
		
		public void createControl(Composite parent) {
			super.createControl(parent);
			Composite result= (Composite)super.getControl();
			addUpdateReferenceComponent(result);
			addUpdateQualifiedNameComponent(result, ((GridLayout)result.getLayout()).marginWidth);
			setControl(result);
			Dialog.applyDialogFont(result);
		}

		private boolean canUpdateReferences() {
			return getMoveRefactoring().canUpdateReferences();
		}
	}
}
