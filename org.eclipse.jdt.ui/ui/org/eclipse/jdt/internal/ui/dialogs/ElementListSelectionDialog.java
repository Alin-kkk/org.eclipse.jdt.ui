/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.dialogs;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import java.util.Arrays;
import org.eclipse.jface.viewers.ILabelProvider;

/**
 * A class to select elements out of a list of elements.
 */
public class ElementListSelectionDialog extends AbstractElementListSelectionDialog {
	
	private Object[] fElements;
	
	/**
	 * Creates a list selection dialog.
	 * @param renderer          the label renderer.
	 * @param ignoreCase        specifies whether sorting, folding and filtering are case sensitive.
	 * @param multipleSelection specifies whether multiple selection is allowed.
	 */	
	public ElementListSelectionDialog(Shell parent,	ILabelProvider renderer) {
		super(parent, renderer);
	}

	/**
	 * Sets the elements of the list.
	 * @param elements the elements of the list.
	 */
	public void setElements(Object[] elements) {
		fElements= elements;
	}

	/*
	 * @see SelectionStatusDialog#computeResult()
	 */
	protected void computeResult() {
		setResult(Arrays.asList(getSelectedElements()));
	}
	
	/*
	 * @see Dialog#createDialogArea(Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		Composite contents= (Composite) super.createDialogArea(parent);
		
		createMessageArea(contents);
		createFilterText(contents);
		createFilteredList(contents);

		initFilteredList();
		initFilterText();
		setListElements(fElements);
						
		return contents;
	}
}