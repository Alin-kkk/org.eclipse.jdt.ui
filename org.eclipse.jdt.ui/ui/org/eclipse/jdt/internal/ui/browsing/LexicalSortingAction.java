/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.browsing;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.ui.JavaElementSorter;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/*
 * XXX: This class should become part of the MemberFilterActionGroup
 *      which should be renamed to MemberActionsGroup
 */
class LexicalSortingAction extends Action {
	private JavaElementSorter fSorter= new JavaElementSorter();
	private StructuredViewer fViewer;
	private String fPreferenceKey;

	LexicalSortingAction(StructuredViewer viewer, String id) {
		super();
		fViewer= viewer;
		fPreferenceKey= "LexicalSortingAction." + id + ".isChecked"; //$NON-NLS-1$ //$NON-NLS-2$
		setText(JavaBrowsingMessages.getString("LexicalSortingAction.label")); //$NON-NLS-1$
		JavaPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif"); //$NON-NLS-1$
		setToolTipText(JavaBrowsingMessages.getString("LexicalSortingAction.tooltip")); //$NON-NLS-1$
		setDescription(JavaBrowsingMessages.getString("LexicalSortingAction.description")); //$NON-NLS-1$
		boolean checked= JavaPlugin.getDefault().getPreferenceStore().getBoolean(fPreferenceKey); //$NON-NLS-1$
		valueChanged(checked, false);
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.LEXICAL_SORTING_BROWSING_ACTION);
	}

	public void run() {
		valueChanged(isChecked(), true);
	}

	private void valueChanged(boolean on, boolean store) {
		setChecked(on);
		if (on)
			fViewer.setSorter(fSorter);
		else
			fViewer.setSorter(null);
		if (store)
			JavaPlugin.getDefault().getPreferenceStore().setValue(fPreferenceKey, on);
	}
};
