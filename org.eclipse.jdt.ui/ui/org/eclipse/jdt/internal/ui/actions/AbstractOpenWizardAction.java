/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.actions;

import java.util.Iterator;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.IWorkbenchWizard;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;

public abstract class AbstractOpenWizardAction extends Action implements IWorkbenchWindowActionDelegate {

	private Class[] fActivatedOnTypes;
	private boolean fAcceptEmptySelection;
	private boolean fNoChecking;
	
	/**
	 * Creates a AbstractOpenWizardAction.
	 * @param label The label of the action
	 * @param acceptEmptySelection Specifies if the action allows an empty selection
	 */
	public AbstractOpenWizardAction(String label, boolean acceptEmptySelection) {
		this(label, null, acceptEmptySelection);
	}

	/**
	 * Creates a AbstractOpenWizardAction.
	 * @param label The label of the action
	 * @param activatedOnTypes The action is only enabled when all objects in the selection
	 *                         are of the given types. <code>null</code> will allow all types.
	 * @param acceptEmptySelection Specifies if the action allows an empty selection
	 */	
	public AbstractOpenWizardAction(String label, Class[] activatedOnTypes, boolean acceptEmptySelection) {
		super(label);
		fActivatedOnTypes= activatedOnTypes;
		fAcceptEmptySelection= acceptEmptySelection;
	}

	/**
	 * Creates a AbstractOpenWizardAction with no restrictions on types, and does allow
	 * an empty selection.
	 */
	protected AbstractOpenWizardAction() {
		fActivatedOnTypes= null;
		fAcceptEmptySelection= true;
		fNoChecking= true;
	}
	
	protected IWorkbench getWorkbench() {
		return JavaPlugin.getDefault().getWorkbench();
	}
	
	private boolean isOfAcceptedType(Object obj) {
		if (fActivatedOnTypes != null) {
			for (int i= 0; i < fActivatedOnTypes.length; i++) {
				if (fActivatedOnTypes[i].isInstance(obj)) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
	
	
	private boolean isEnabled(IStructuredSelection selection) {
		Iterator iter= selection.iterator();
		while (iter.hasNext()) {
			Object obj= iter.next();
			if (!isOfAcceptedType(obj) || !shouldAcceptElement(obj)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Can be overridden to add more checks.
	 * obj is guaranteed to be instance of one of the accepted types
	 */
	protected boolean shouldAcceptElement(Object obj) {
		return true;
	}		
		
	/**
	 * Creates the specific wizard.
	 * (to be implemented by a subclass)
	 */
	abstract protected Wizard createWizard();


	protected IStructuredSelection getCurrentSelection() {
		IWorkbenchWindow window= JavaPlugin.getActiveWorkbenchWindow();
		if (window != null) {
			ISelection selection= window.getSelectionService().getSelection();
			if (selection instanceof IStructuredSelection) {
				return (IStructuredSelection) selection;
			}
			
		}
		return null;
	}

	/**
	 * The user has invoked this action.
	 */
	public void run() {
		if (!fNoChecking && !canActionBeAdded()) {
			return;
		}
		
		Wizard wizard= createWizard();
		if (wizard instanceof IWorkbenchWizard) {
			((IWorkbenchWizard)wizard).init(getWorkbench(), getCurrentSelection());
		}
		WizardDialog dialog= new WizardDialog(JavaPlugin.getActiveWorkbenchShell(), wizard);
		dialog.create();
		dialog.getShell().setText(JavaUIMessages.getString("AbstractOpenWizardAction.title")); //$NON-NLS-1$
		dialog.open();
	}
	
	/**
	 * Tests if the action can be run on the current selection.
	 */
	public boolean canActionBeAdded() {
		IStructuredSelection selection= getCurrentSelection();
		if (selection == null || selection.isEmpty()) {
			return fAcceptEmptySelection;
		}
		return isEnabled(selection);
	}

	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		run();
	}

	/**
	 * @see IWorkbenchWindowActionDelegate#dispose()
	 */
	public void dispose() {
	}

	/**
	 * @see IWorkbenchWindowActionDelegate#init(IWorkbenchWindow)
	 */
	public void init(IWorkbenchWindow window) {
	}

	/**
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		// selection taken from selectionprovider
	}

}
