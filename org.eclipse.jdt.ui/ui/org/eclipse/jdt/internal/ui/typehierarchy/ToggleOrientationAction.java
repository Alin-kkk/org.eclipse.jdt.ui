/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.typehierarchy;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.util.Assert;

import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * Toggles the orientationof the layout of the type hierarchy
 */
public class ToggleOrientationAction extends Action {

	private TypeHierarchyViewPart fView;	
	private int fOrientation;
	
	public ToggleOrientationAction(TypeHierarchyViewPart v, int orientation) {
		super();
		if (orientation == TypeHierarchyViewPart.VIEW_ORIENTATION_HORIZONTAL) {
			setText(TypeHierarchyMessages.getString("ToggleOrientationAction.horizontal.label")); //$NON-NLS-1$
			setDescription(TypeHierarchyMessages.getString("ToggleOrientationAction.horizontal.description")); //$NON-NLS-1$
			setToolTipText(TypeHierarchyMessages.getString("ToggleOrientationAction.horizontal.tooltip")); //$NON-NLS-1$
			JavaPluginImages.setLocalImageDescriptors(this, "th_horizontal.gif"); //$NON-NLS-1$
		} else if (orientation == TypeHierarchyViewPart.VIEW_ORIENTATION_VERTICAL) {
			setText(TypeHierarchyMessages.getString("ToggleOrientationAction.vertical.label")); //$NON-NLS-1$
			setDescription(TypeHierarchyMessages.getString("ToggleOrientationAction.vertical.description")); //$NON-NLS-1$
			setToolTipText(TypeHierarchyMessages.getString("ToggleOrientationAction.vertical.tooltip")); //$NON-NLS-1$	
			JavaPluginImages.setLocalImageDescriptors(this, "th_vertical.gif"); //$NON-NLS-1$
		} else if (orientation == TypeHierarchyViewPart.VIEW_ORIENTATION_SINGLE) {
			setText(TypeHierarchyMessages.getString("ToggleOrientationAction.single.label")); //$NON-NLS-1$
			setDescription(TypeHierarchyMessages.getString("ToggleOrientationAction.single.description")); //$NON-NLS-1$
			setToolTipText(TypeHierarchyMessages.getString("ToggleOrientationAction.single.tooltip")); //$NON-NLS-1$	
			JavaPluginImages.setLocalImageDescriptors(this, "th_single.gif"); //$NON-NLS-1$
		} else {
			Assert.isTrue(false);
		}
		fView= v;
		fOrientation= orientation;
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.TOGGLE_ORIENTATION_ACTION);
	}
	
	public int getOrientation() {
		return fOrientation;
	}	
	
	/*
	 * @see Action#actionPerformed
	 */		
	public void run() {
		fView.setOrientation(fOrientation); // will toggle the checked state
	}
	
}