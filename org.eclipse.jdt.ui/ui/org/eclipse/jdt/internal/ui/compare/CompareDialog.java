/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.compare;

import java.io.*;
import java.util.ResourceBundle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.layout.*;

import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.compare.*;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.ICompareInput;


class CompareDialog extends ResizableDialog {
	
	class ViewerSwitchingPane extends CompareViewerSwitchingPane {
		
		ViewerSwitchingPane(Composite parent, int style) {
			super(parent, style, false);
		}
	
		protected Viewer getViewer(Viewer oldViewer, Object input) {
			if (input instanceof ICompareInput)
				return CompareUI.findContentViewer(oldViewer, (ICompareInput)input, this, fCompareConfiguration);
			return null;
		}
				
		public void setImage(Image image) {
			// don't show icon
		}
	}
	
	private CompareViewerSwitchingPane fContentPane;
	private CompareConfiguration fCompareConfiguration;
	private ICompareInput fInput;
	
	
	CompareDialog(Shell parent, ResourceBundle bundle) {
		super(parent, bundle);		
		
		fCompareConfiguration= new CompareConfiguration();
		fCompareConfiguration.setLeftEditable(false);
		fCompareConfiguration.setRightEditable(false);
	}
	
	void compare(ICompareInput input) {
		
		fInput= input;
		
		fCompareConfiguration.setLeftLabel(fInput.getLeft().getName());
		fCompareConfiguration.setLeftImage(fInput.getLeft().getImage());
		
		fCompareConfiguration.setRightLabel(fInput.getRight().getName());
		fCompareConfiguration.setRightImage(fInput.getRight().getImage());
		
		if (fContentPane != null)
			fContentPane.setInput(fInput);
			
		open();
	}
	
	 /* (non Javadoc)
 	 * Creates SWT control tree.
 	 */
	protected synchronized Control createDialogArea(Composite parent) {
		
		getShell().setText(JavaCompareUtilities.getString(fBundle, "title")); //$NON-NLS-1$
		
		fContentPane= new ViewerSwitchingPane(parent, SWT.BORDER | SWT.FLAT);
		fContentPane.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL
					| GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
		
		if (fInput != null)
			fContentPane.setInput(fInput);
			
		return fContentPane;
	}
	
	/* (non-Javadoc)
	 * Method declared on Dialog.
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		String buttonLabel= JavaCompareUtilities.getString(fBundle, "buttonLabel", IDialogConstants.OK_LABEL); //$NON-NLS-1$
		createButton(parent, IDialogConstants.CANCEL_ID, buttonLabel, false);
	}

}
