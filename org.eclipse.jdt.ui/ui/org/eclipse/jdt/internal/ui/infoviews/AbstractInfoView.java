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
package org.eclipse.jdt.internal.ui.infoviews;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.search.SearchUtil;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;

/**
 * Abstract class for views which show information for a given element.
 * 
 * @since 3.0
 */
abstract class AbstractInfoView extends ViewPart implements ISelectionListener, IMenuListener {


	/** JavaElementLabels flags used for the title */
	private static final int TITLE_LABEL_FLAGS= JavaElementLabels.DEFAULT_QUALIFIED;
	/** JavaElementLabels flags used for the tool tip text */
	private static final int TOOLTIP_LABEL_FLAGS= JavaElementLabels.DEFAULT_QUALIFIED | JavaElementLabels.ROOT_POST_QUALIFIED | JavaElementLabels.APPEND_ROOT_PATH |
			JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_PARAMETER_NAMES | JavaElementLabels.M_APP_RETURNTYPE | JavaElementLabels.M_EXCEPTIONS | 
			JavaElementLabels.F_APP_TYPE_SIGNATURE;


	/*
	 * @see IPartListener2
	 */
	private IPartListener2 fPartListener= new IPartListener2() {
		public void partVisible(IWorkbenchPartReference ref) {
			if (ref.getId().equals(getSite().getId())) {
				IWorkbenchPart activePart= ref.getPage().getActivePart();
				if (activePart != null)
					selectionChanged(activePart, ref.getPage().getSelection());
				startListeningForSelectionChanges();
			}
		}
		public void partHidden(IWorkbenchPartReference ref) {
			if (ref.getId().equals(getSite().getId()))
				stopListeningForSelectionChanges();
		}
		public void partInputChanged(IWorkbenchPartReference ref) {
			if (!ref.getId().equals(getSite().getId()))
				setInputFrom(ref.getPart(false));
		}
		public void partActivated(IWorkbenchPartReference ref) {
		}
		public void partBroughtToTop(IWorkbenchPartReference ref) {
		}
		public void partClosed(IWorkbenchPartReference ref) {
		}
		public void partDeactivated(IWorkbenchPartReference ref) {
		}
		public void partOpened(IWorkbenchPartReference ref) {
		}
	};


	/** The current input. */
	protected IJavaElement fCurrentInput;
	/** The copy to clipboard action. */
	private SelectionDispatchAction fCopyToClipboardAction;
	/** The goto input action. */
	private GotoInputAction fGotoInputAction;


	/**
	 * Set the input of this view.
	 *  
	 * @param input the input object
	 * @return	<code>true</code> if the input was set successfully 
	 */
	abstract protected boolean setInput(Object input);

	/**
	 * Create the part control.
	 * 
 	 * @param parent the parent control
	 * @see IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	abstract protected void internalCreatePartControl(Composite parent);

	/**
	 * Set the view's foreground color.
	 * 
	 * @param color the SWT color
	 */
	abstract protected void setForeground(Color color);

	/**
	 * Set the view's background color.
	 * 
	 * @param color the SWT color
	 */
	abstract protected void setBackground(Color color);

	/**
	 * Returns the view's primary control.
	 * 
	 * @return the primary control
	 */
	abstract Control getControl();

	/*
	 * @see IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	public final void createPartControl(Composite parent) {
		internalCreatePartControl(parent);
		setInfoColor();
		getSite().getWorkbenchWindow().getPartService().addPartListener(fPartListener);
		createContextMenu();
		createActions();
		fillActionBars(getViewSite().getActionBars());
	}

	/**
	 * Creates the actions and action groups for this view.
	 */
	protected void createActions() {
		fGotoInputAction= new GotoInputAction(this);
		fGotoInputAction.setEnabled(false);
		fCopyToClipboardAction= new CopyToClipboardAction(getViewSite());
		getSelectionProvider().addSelectionChangedListener(fCopyToClipboardAction);
	}

	/**
	 * Creates the context menu for this view.
	 */
	protected void createContextMenu() {
		MenuManager menuManager= new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(this);
		Menu contextMenu= menuManager.createContextMenu(getControl());
		getControl().setMenu(contextMenu);
		getSite().registerContextMenu(menuManager, getSelectionProvider());
	}

	/*
	 * @see IMenuListener#menuAboutToShow(org.eclipse.jface.action.IMenuManager)
	 */	
	public void menuAboutToShow(IMenuManager menu) {
		menu.add(new Separator(IContextMenuConstants.GROUP_OPEN));		
		menu.add(new Separator(ITextEditorActionConstants.GROUP_EDIT));
		menu.add(new Separator(IContextMenuConstants.GROUP_ADDITIONS));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_EDIT, fCopyToClipboardAction);
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, fGotoInputAction);
	}

	/**
	 * Returns the input of this view.
	 *  
	 * @return input the input object or <code>null</code> if not input is set 
	 */
	protected IJavaElement getInput() {
		return fCurrentInput;
	}

	// Helper method
	ISelectionProvider getSelectionProvider() {
		return getViewSite().getSelectionProvider();
	}

	/**
	 * Fills the actions bars.
	 * <p>
	 * Subclasses may extend.
	 */	
	protected void fillActionBars(IActionBars actionBars) {
		IToolBarManager toolBar= actionBars.getToolBarManager();
		fillToolBar(toolBar);
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.COPY, fCopyToClipboardAction);
	}

	/**
	 * Fills the tool bar.
	 * <p> 
	 * Default is to do nothing.</p>
	 */	
	protected void fillToolBar(IToolBarManager tbm) {
		tbm.add(fGotoInputAction);
	}	

	/**
	 * Sets the foreground and background color to the corresponding SWT info color.
	 */
	private void setInfoColor() {
		if (getSite().getShell().isDisposed())
			return;
		
		Display display= getSite().getShell().getDisplay();
		if (display == null || display.isDisposed())
			return;

		setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
		setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
	}
	
	/**
	 * Start to listen for selection changes.
	 */
	protected void startListeningForSelectionChanges() {
		getSite().getWorkbenchWindow().getSelectionService().addPostSelectionListener(this);
	}

	/**
	 * Stop to listen for selection changes.
	 */
	protected void stopListeningForSelectionChanges() {
		getSite().getWorkbenchWindow().getSelectionService().removePostSelectionListener(this);
	}

	/*
	 * @see ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (part.equals(this))
			return;

		setInputFrom(part);
	}

	/**
	 * Tells whether the new input should be ignored
	 * if the current input is the same.
	 * 
	 * @return <code>true</code> if the new input should be ignored
	 */
	protected boolean isIgnoringEqualInput() {
		return true;
	}

	/**
	 * Finds and returns the Java element selected in the given part.
	 * 
	 * @param part the workbench part for which to find the selected Java element
	 * @return the selected Java element
	 */
	protected IJavaElement findSelectedJavaElement(IWorkbenchPart part) {
		Object element;
		try {
			IStructuredSelection sel= SelectionConverter.getStructuredSelection(part);
			element= SelectionUtil.getSingleElement(sel);
		} catch (JavaModelException e) {
			return null;
		}
			
		return findJavaElement(element);
	}

	/**
	 * Tries to get a Java element out of the given element.
	 * 
	 * @param element an object
	 * @return the Java element represented by the given element or <code>null</code>
	 */
	protected IJavaElement findJavaElement(Object element) {

		if (SearchUtil.isISearchResultViewEntry(element)) {
			IJavaElement je= SearchUtil.getJavaElement(element);
			if (je != null)
				return je;
			element= SearchUtil.getResource(element);
		}
	
		IJavaElement je= null;
		if (element instanceof IAdaptable)
			je= (IJavaElement)((IAdaptable)element).getAdapter(IJavaElement.class);
			
		if (je != null && je.getElementType() == IJavaElement.COMPILATION_UNIT)
			je= WorkingCopyUtil.getWorkingCopyIfExists((ICompilationUnit)je);
			
		return je;
	}

	/**
	 * Finds and returns the type for the given CU.
	 * 
	 * @param cu	the compilation unit
	 * @return	the type with same name as the given CU or the first type in the CU 
	 */
	protected IType getTypeForCU(ICompilationUnit cu) {
		
		if (cu == null || !cu.exists())
			return null;
		
		cu= WorkingCopyUtil.getWorkingCopyIfExists(cu);
		
		// Use primary type if possible
		IType primaryType= cu.findPrimaryType();
		if (primaryType != null)
			return primaryType;

		// Use first top-level type
		try {
			IType[] types= cu.getTypes();
			if (types.length > 0)
				return types[0];
			else
				return null;
		} catch (JavaModelException ex) {
			return null;
		}
	}	

	/**
	 * Sets this view's input based on the selection in the given part.
	 * 
	 * @param part the part from which to get the selected Java element
	 */
	private void setInputFrom(IWorkbenchPart part) {
		IJavaElement je= findSelectedJavaElement(part);

		if (!isIgnoringEqualInput() && fCurrentInput != null && fCurrentInput.equals(je))
			return;
		
		if (!setInput(je))
			return;

		fCurrentInput= je;
		fGotoInputAction.setEnabled(true);
		
		String title= InfoViewMessages.getFormattedString("AbstractInfoView.compoundTitle", getSite().getRegisteredName(), JavaElementLabels.getElementLabel(je, TITLE_LABEL_FLAGS)); //$NON-NLS-1$
		setTitle(title);
		setTitleToolTip(JavaElementLabels.getElementLabel(je, TOOLTIP_LABEL_FLAGS));  //$NON-NLS-1$//$NON-NLS-2$
	}

	/*
	 * @see IWorkbenchPart#dispose()
	 */
	final public void dispose() {
		getSite().getWorkbenchWindow().getPartService().removePartListener(fPartListener);
		getSelectionProvider().removeSelectionChangedListener(fCopyToClipboardAction);
		internalDispose();
	}

	/*
	 * @see IWorkbenchPart#dispose()
	 */
	protected void internalDispose() {
	}
}
