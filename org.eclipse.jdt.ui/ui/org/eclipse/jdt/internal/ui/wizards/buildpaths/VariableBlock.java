/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;


public class VariableBlock {
	
	private ListDialogField fVariablesList;
	private Control fControl;
	
	private List fSelectedElements;
	private boolean fAskToBuild;
	
	
	/**
	 * Constructor for VariableBlock
	 */
	public VariableBlock(boolean inPreferencePage, String initSelection) {
		
		fSelectedElements= new ArrayList(0);
		fAskToBuild= true;
		
		String[] buttonLabels= new String[] { 
			/* 0 */ NewWizardMessages.getString("VariableBlock.vars.add.button"), //$NON-NLS-1$
			/* 1 */ NewWizardMessages.getString("VariableBlock.vars.edit.button"), //$NON-NLS-1$
			/* 2 */ null,
			/* 3 */ NewWizardMessages.getString("VariableBlock.vars.remove.button") //$NON-NLS-1$
		};
				
		VariablesAdapter adapter= new VariablesAdapter();
		
		CPVariableElementLabelProvider labelProvider= new CPVariableElementLabelProvider(!inPreferencePage);
		
		fVariablesList= new ListDialogField(adapter, buttonLabels, labelProvider);
		fVariablesList.setDialogFieldListener(adapter);
		fVariablesList.setLabelText(NewWizardMessages.getString("VariableBlock.vars.label")); //$NON-NLS-1$
		fVariablesList.setRemoveButtonIndex(3);
		
		fVariablesList.enableButton(1, false);
		
		fVariablesList.setViewerSorter(new ViewerSorter() {
			public int compare(Viewer viewer, Object e1, Object e2) {
				if (e1 instanceof CPVariableElement && e2 instanceof CPVariableElement) {
					return ((CPVariableElement)e1).getName().compareTo(((CPVariableElement)e2).getName());
				}
				return super.compare(viewer, e1, e2);
			}
		});
		
		
		
		CPVariableElement initSelectedElement= null;
		
		String[] reservedName= getReservedVariableNames();
		ArrayList reserved= new ArrayList(reservedName.length);
		addAll(reservedName, reserved);
				
		String[] entries= JavaCore.getClasspathVariableNames();
		ArrayList elements= new ArrayList(entries.length);
		for (int i= 0; i < entries.length; i++) {
			String name= entries[i];
			CPVariableElement elem;
			IPath entryPath= JavaCore.getClasspathVariable(name);
			if (entryPath != null) {
				elem= new CPVariableElement(name, entryPath, reserved.contains(name));
				elements.add(elem);
				if (name.equals(initSelection)) {
					initSelectedElement= elem;
				}
			} else {				
				JavaPlugin.logErrorMessage("VariableBlock: Classpath variable with null value: " + name); //$NON-NLS-1$
			}
		}
		
		fVariablesList.setElements(elements);
		
		if (initSelectedElement != null) {
			ISelection sel= new StructuredSelection(initSelectedElement);
			fVariablesList.selectElements(sel);
		} else {
			fVariablesList.selectFirstElement();
		}
	}
	
	private String[] getReservedVariableNames() {
		return new String[] {
			JavaRuntime.JRELIB_VARIABLE,
			JavaRuntime.JRESRC_VARIABLE,
			JavaRuntime.JRESRCROOT_VARIABLE,
		};
	}
	
	public Control createContents(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fVariablesList }, true, 0, 0);
		LayoutUtil.setHorizontalGrabbing(fVariablesList.getListControl(null));
		
		fControl= composite;
		return composite;
	}
	
	public void addDoubleClickListener(IDoubleClickListener listener) {
		fVariablesList.getTableViewer().addDoubleClickListener(listener);
	}
	
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		fVariablesList.getTableViewer().addSelectionChangedListener(listener);
	}	
		
	
	private Shell getShell() {
		if (fControl != null) {
			return fControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}
	
	private class VariablesAdapter implements IDialogFieldListener, IListAdapter {
		
		// -------- IListAdapter --------
			
		public void customButtonPressed(ListDialogField field, int index) {
			switch (index) {
			case 0: /* add */
				editEntries(null);
				break;
			case 1: /* edit */
				List selected= field.getSelectedElements();			
				editEntries((CPVariableElement)selected.get(0));
				break;
			}
		}
		
		public void selectionChanged(ListDialogField field) {
			doSelectionChanged(field);
		}
		
		public void doubleClicked(ListDialogField field) {
		}			
			
		// ---------- IDialogFieldListener --------
	
		public void dialogFieldChanged(DialogField field) {
		}
	
	}
	
	private boolean containsReserved(List selected) {
		for (int i= selected.size()-1; i >= 0; i--) {
			if (((CPVariableElement)selected.get(i)).isReserved()) {
				return true;
			}
		}
		return false;
	}
	
	private static void addAll(Object[] objs, Collection dest) {
		for (int i= 0; i < objs.length; i++) {
			dest.add(objs[i]);
		}
	}

	private void doSelectionChanged(DialogField field) {
		List selected= fVariablesList.getSelectedElements();
		boolean isSingleSelected= selected.size() == 1;
		boolean containsReserved= containsReserved(selected);
		
		// edit
		fVariablesList.enableButton(1, isSingleSelected && !containsReserved);
		// remove button
		fVariablesList.enableButton(3, !containsReserved);
		
		fSelectedElements= selected;
	}
	
	private void editEntries(CPVariableElement entry) {
		List existingEntries= fVariablesList.getElements();

		VariableCreationDialog dialog= new VariableCreationDialog(getShell(), entry, existingEntries);
		if (dialog.open() != VariableCreationDialog.OK) {
			return;
		}
		CPVariableElement newEntry= dialog.getClasspathElement();
		if (entry == null) {
			fVariablesList.addElement(newEntry);
			entry= newEntry;
		} else {
			entry.setName(newEntry.getName());
			entry.setPath(newEntry.getPath());
			fVariablesList.refresh();
		}
		fVariablesList.selectElements(new StructuredSelection(entry));
	}
	
	public List getSelectedElements() {
		return fSelectedElements;
	}
	
	
	public void performDefaults() {
		fVariablesList.removeAllElements();
		String[] reservedName= getReservedVariableNames();
		for (int i= 0; i < reservedName.length; i++) {
			CPVariableElement elem= new CPVariableElement(reservedName[i], Path.EMPTY, true);
			elem.setReserved(true);
			fVariablesList.addElement(elem);
		}
	}

	public boolean performOk() {
		ArrayList removedVariables= new ArrayList();
		ArrayList changedVariables= new ArrayList();
		removedVariables.addAll(Arrays.asList(JavaCore.getClasspathVariableNames()));

		// remove all unchanged
		List changedElements= fVariablesList.getElements();
		for (int i= changedElements.size()-1; i >= 0; i--) {
			CPVariableElement curr= (CPVariableElement) changedElements.get(i);
			if (curr.isReserved()) {
				changedElements.remove(curr);
			} else {
				IPath path= curr.getPath();
				IPath prevPath= JavaCore.getClasspathVariable(curr.getName());
				if (prevPath != null && prevPath.equals(path)) {
					changedElements.remove(curr);
				} else {
					changedVariables.add(curr.getName());
				}
			}
			removedVariables.remove(curr.getName());
		}
		int steps= changedElements.size() + removedVariables.size();
		if (steps > 0) {
			
			boolean needsBuild= false;
			if (fAskToBuild && doesChangeRequireFullBuild(removedVariables, changedVariables)) {
				String title= NewWizardMessages.getString("VariableBlock.needsbuild.title"); //$NON-NLS-1$
				String message= NewWizardMessages.getString("VariableBlock.needsbuild.message"); //$NON-NLS-1$
				
				MessageDialog buildDialog= new MessageDialog(getShell(), title, null, message, MessageDialog.QUESTION, new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL }, 2);
				int res= buildDialog.open();
				if (res != 0 && res != 1) {
					return false;
				}
				needsBuild= (res == 0);
			}
			
			IRunnableWithProgress runnable= new VariableBlockRunnable(removedVariables, changedElements, needsBuild);
			
			ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());
			try {
				dialog.run(true, true, runnable);
			} catch (InvocationTargetException e) {
				ExceptionHandler.handle(e, getShell(), NewWizardMessages.getString("VariableBlock.operation_errror.title"), NewWizardMessages.getString("VariableBlock.operation_errror.message")); //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			} catch (InterruptedException e) {
				return true;
			}
		}
		return true;
	}
	
	private boolean doesChangeRequireFullBuild(List removed, List changed) {
		try {
			IJavaModel model= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
			IJavaProject[] projects= model.getJavaProjects();
			for (int i= 0; i < projects.length; i++) {
				IClasspathEntry[] entries= projects[i].getRawClasspath();
				for (int k= 0; k < entries.length; k++) {
					IClasspathEntry curr= entries[k];
					if (curr.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
						String var= curr.getPath().segment(0);
						if (removed.contains(var) || changed.contains(var)) {
							return true;
						}
					}
				}
			}
		} catch (JavaModelException e) {
			return true;
		}
		return false;
	}
	
	private class VariableBlockRunnable implements IRunnableWithProgress {
		private List fToRemove;
		private List fToChange;
		private boolean fDoBuild;
		
		public VariableBlockRunnable(List toRemove, List toChange, boolean doBuild) {
			fToRemove= toRemove;
			fToChange= toChange;
			fDoBuild= doBuild;
		}
		
		/*
	 	 * @see IRunnableWithProgress#run(IProgressMonitor)
		 */
		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
			monitor.beginTask(NewWizardMessages.getString("VariableBlock.operation_desc"), fDoBuild ? 2 : 1); //$NON-NLS-1$
			try {
				int nVariables= fToChange.size() + fToRemove.size();
				
				String[] names= new String[nVariables];
				IPath[] paths= new IPath[nVariables];
				int k= 0;
				
				for (int i= 0; i < fToChange.size(); i++) {
					CPVariableElement curr= (CPVariableElement) fToChange.get(i);
					names[k]= curr.getName();
					paths[k]= curr.getPath();
					k++;
				}
				for (int i= 0; i < fToRemove.size(); i++) {
					names[k]= (String) fToRemove.get(i);
					paths[k]= null;
					k++;					
				}
				JavaCore.setClasspathVariables(names, paths, new SubProgressMonitor(monitor, 1));
				
				if (fDoBuild) {
					JavaPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, new SubProgressMonitor(monitor, 1));
				}
				
			} catch (CoreException e) {
				throw new InvocationTargetException(e);
			} finally {
				monitor.done();
			}
		}
	}
	
	/**
	 * If set to true, a dialog will ask the user to build on variable changed
	 * @param askToBuild The askToBuild to set
	 */
	public void setAskToBuild(boolean askToBuild) {
		fAskToBuild= askToBuild;
	}

}
