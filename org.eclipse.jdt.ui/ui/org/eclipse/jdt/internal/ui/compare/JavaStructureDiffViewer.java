/*
 * (c) Copyright IBM Corp. 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.compare;

import org.eclipse.swt.widgets.*;

import org.eclipse.jface.action.*;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.compare.*;
import org.eclipse.compare.internal.ChangePropertyAction;
import org.eclipse.compare.structuremergeviewer.StructureDiffViewer;

import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.internal.Utilities;
import org.eclipse.compare.structuremergeviewer.ICompareInput;


class JavaStructureDiffViewer extends StructureDiffViewer {

	private static final String SMART= "SMART"; //$NON-NLS-1$

	private ActionContributionItem fSmartActionItem;
	private JavaStructureCreator fStructureCreator;
	private boolean fThreeWay;

	public JavaStructureDiffViewer(Composite parent, CompareConfiguration configuration) {
		super(parent, configuration);
		fStructureCreator= new JavaStructureCreator();
		setStructureCreator(fStructureCreator);
	}
	
	protected void compareInputChanged(ICompareInput input) {
		
		fThreeWay= input != null ? input.getAncestor() != null
							     : false;
		setSmartButtonVisible(fThreeWay);
		
		super.compareInputChanged(input);
	}
	
	/**
	 * Overriden to create a "smart" button in the viewer's pane control bar.
	 * <p>
	 * Clients can override this method and are free to decide whether they want to call
	 * the inherited method.
	 *
	 * @param toolbarManager the toolbar manager for which to add the buttons
	 */
	protected void createToolItems(ToolBarManager toolBarManager) {
		
		super.createToolItems(toolBarManager);
		
		IAction a= new ChangePropertyAction(getBundle(), getCompareConfiguration(), "action.Smart.", SMART); //$NON-NLS-1$
		fSmartActionItem= new ActionContributionItem(a);
		fSmartActionItem.setVisible(fThreeWay);
		toolBarManager.appendToGroup("modes", fSmartActionItem); //$NON-NLS-1$
	}
	
	protected void postDiffHook(Differencer differencer, IDiffContainer root) {
		if (fStructureCreator.canRewriteTree()) {
			boolean smart= Utilities.getBoolean(getCompareConfiguration(), SMART, false);
			if (smart && root != null)
				fStructureCreator.rewriteTree(differencer, root);
		}
	}
	
	/**
	 * Tracks property changes of the configuration object.
	 * Clients may override to track their own property changes.
	 * In this case they must call the inherited method.
	 */
	protected void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(SMART))
			diff();
		else
			super.propertyChange(event);
	}
	
	private void setSmartButtonVisible(boolean visible) {
		if (fSmartActionItem == null)
			return;
		Control c= getControl();
		if (c == null && c.isDisposed())
			return;
			
		fSmartActionItem.setVisible(visible);
		ToolBarManager tbm= CompareViewerPane.getToolBarManager(c.getParent());
		if (tbm != null) {
			tbm.update(true);
			ToolBar tb= tbm.getControl();
			if (!tb.isDisposed())
				tb.getParent().layout(true);
		}
	}
}
