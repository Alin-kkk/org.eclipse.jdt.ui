/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp. and others.
All rights reserved. � This program and the accompanying materials
are made available under the terms of the Common Public License v0.5
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v05.html
�
Contributors:
	Daniel Megert - Initial implementation
**********************************************************************/

package org.eclipse.jdt.internal.ui.workingsets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.wizard.WizardPage;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IWorkingSetPage;

import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaElementContentProvider;
import org.eclipse.jdt.ui.JavaElementSorter;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

import org.eclipse.jdt.internal.ui.packageview.EmptyInnerPackageFilter;
import org.eclipse.jdt.internal.ui.preferences.WorkInProgressPreferencePage;
import org.eclipse.jdt.internal.ui.util.JavaUIHelp;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.StandardJavaUILabelProvider;

/**
 * The Java working set page allows the user to create
 * and edit a Java working set.
 * <p>
 * Working set elements are presented as a Java element tree.
 * </p>
 * 
 * @since 2.0
 */
public class JavaWorkingSetPage extends WizardPage implements IWorkingSetPage {

	final private static String PAGE_TITLE= WorkingSetMessages.getString("JavaWorkingSetPage.title"); //$NON-NLS-1$
	final private static String PAGE_ID= "javaWorkingSetPage"; //$NON-NLS-1$
	
	private Text fWorkingSetName;
	private CheckboxTreeViewer fTree;
	private ITreeContentProvider fTreeContentProvider;
	
	private boolean fFirstCheck;
	private IWorkingSet fWorkingSet;

	/**
	 * Default constructor.
	 */
	public JavaWorkingSetPage() {
		super(PAGE_ID, PAGE_TITLE, JavaPluginImages.DESC_WIZBAN_JAVA_WORKINGSET);
	}

	/**
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		// XXX: workaround for: working set wizards override page title (bug 14492)
		setTitle(PAGE_TITLE);
		
		Composite composite= new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		setControl(composite);

		Label label= new Label(composite, SWT.WRAP);
		label.setText(WorkingSetMessages.getString("JavaWorkingSetPage.workingSet.name")); //$NON-NLS-1$
		GridData gd= new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_CENTER);
		label.setLayoutData(gd);

		fWorkingSetName= new Text(composite, SWT.SINGLE | SWT.BORDER);
		fWorkingSetName.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		fWorkingSetName.addModifyListener(
			new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					validateInput();
				}
			}
		);
		fWorkingSetName.setFocus();
		
		label= new Label(composite, SWT.WRAP);
		label.setText(WorkingSetMessages.getString("JavaWorkingSetPage.workingSet.content")); //$NON-NLS-1$
		gd= new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_CENTER);
		label.setLayoutData(gd);

		fTree= new CheckboxTreeViewer(composite, SWT.H_SCROLL | SWT.V_SCROLL);
		gd= new GridData(GridData.FILL_BOTH | GridData.GRAB_VERTICAL);
		fTree.getControl().setLayoutData(gd);
		
		fTreeContentProvider= new JavaWorkingSetPageContentProvider();
		fTree.setContentProvider(fTreeContentProvider);
		
		StandardJavaUILabelProvider fJavaElementLabelProvider= 
			new StandardJavaUILabelProvider(
				StandardJavaUILabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED,
				StandardJavaUILabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS,
				StandardJavaUILabelProvider.getAdornmentProviders(true, null)
			);
		
		fTree.setLabelProvider(new DecoratingLabelProvider(
			fJavaElementLabelProvider, PlatformUI.getWorkbench().getDecoratorManager())
		);
		fTree.setSorter(new JavaElementSorter());
		fTree.addFilter(new EmptyInnerPackageFilter());
		fTree.setUseHashlookup(true);
		
		fTree.setInput(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()));

		fTree.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				handleCheckStateChange(event);
			}
		});

		fTree.addTreeListener(new ITreeViewerListener() {
			public void treeCollapsed(TreeExpansionEvent event) {
			}
			public void treeExpanded(TreeExpansionEvent event) {
				final Object element= event.getElement();
				if (fTree.getGrayed(element) == false)
					BusyIndicator.showWhile(getShell().getDisplay(), new Runnable() {
					public void run() {
						setSubtreeChecked(element, fTree.getChecked(element), false);
					}
				});
			}
		});

		// Set help for the page 
		JavaUIHelp.setHelp(fTree, IJavaHelpContextIds.JAVA_WORKING_SET_PAGE);
	}

	/*
	 * Implements method from IWorkingSetPage
	 */
	public IWorkingSet getSelection() {
		return fWorkingSet;
	}

	/*
	 * Implements method from IWorkingSetPage
	 */
	public void setSelection(IWorkingSet workingSet) {
		Assert.isNotNull(workingSet, "Working set must not be null"); //$NON-NLS-1$
		fWorkingSet= workingSet;
		if (getShell() != null && fWorkingSetName != null) {
			fFirstCheck= true;
			fWorkingSetName.setText(fWorkingSet.getName());
			initializeCheckedState();
			disableClosedProjects();
		}
	}

	/*
	 * Implements method from IWorkingSetPage
	 */
	public void finish() {
		String workingSetName= fWorkingSetName.getText();
		ArrayList elements= new ArrayList(10);
		findCheckedElements(elements, fTree.getInput());
		if (fWorkingSet == null) {
			IWorkingSetManager workingSetManager= PlatformUI.getWorkbench().getWorkingSetManager();
			fWorkingSet= workingSetManager.createWorkingSet(workingSetName, (IAdaptable[])elements.toArray(new IAdaptable[elements.size()]));
		} else {
			// Add inaccessible resources
			IAdaptable[] oldItems= fWorkingSet.getElements();

			for (int i= 0; i < oldItems.length; i++) {
				IResource oldResource= null;
				if (oldItems[i] instanceof IResource) {
					oldResource= (IResource)oldItems[i];
				} else {
					oldResource= (IResource)oldItems[i].getAdapter(IResource.class);
				}
				if (oldResource != null && oldResource.isAccessible() == false) {
					elements.add(oldResource);
				}
			}
			fWorkingSet.setName(workingSetName);
			fWorkingSet.setElements((IAdaptable[]) elements.toArray(new IAdaptable[elements.size()]));
		}
	}

	private void validateInput() {
		String errorMessage= null; //$NON-NLS-1$
		String newText= fWorkingSetName.getText();

		if (fFirstCheck) {
			fFirstCheck= false;
			return;
		}
		if (newText.equals("")) //$NON-NLS-1$
			errorMessage= WorkingSetMessages.getString("JavaWorkingSetPage.warning.nameMustNotBeEmpty"); //$NON-NLS-1$

		if (errorMessage == null && (fWorkingSet == null || newText.equals(fWorkingSet.getName()) == false)) {
			IWorkingSet[] workingSets= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets();
			for (int i= 0; i < workingSets.length; i++) {
				if (newText.equals(workingSets[i].getName())) {
					errorMessage= WorkingSetMessages.getString("JavaWorkingSetPage.warning.workingSetExists"); //$NON-NLS-1$
				}
			}
		}
		if (errorMessage == null && fTree.getCheckedElements().length == 0)
			errorMessage= WorkingSetMessages.getString("JavaWorkingSetPage.warning.resourceMustBeChecked"); //$NON-NLS-1$

		setErrorMessage(errorMessage);
		setPageComplete(errorMessage == null);
	}
	
	private void disableClosedProjects() {
		IProject[] projects= ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i= 0; i < projects.length; i++) {
			if (!projects[i].isOpen())
				fTree.setGrayed(projects[i], true);
		}
	}

	private void findCheckedElements(List checkedResources, Object parent) {
		Object[] children= fTreeContentProvider.getChildren(parent);
		for (int i= 0; i < children.length; i++) {
			if (fTree.getGrayed(children[i]))
				findCheckedElements(checkedResources, children[i]);
			else if (fTree.getChecked(children[i]))
				checkedResources.add(children[i]);
		}
	}

	void handleCheckStateChange(final CheckStateChangedEvent event) {
		BusyIndicator.showWhile(getShell().getDisplay(), new Runnable() {
			public void run() {
				IAdaptable element= (IAdaptable)event.getElement();
				IResource resource= (IResource)element.getAdapter(IResource.class);
				if (resource != null && !resource.isAccessible()) {
					MessageDialog.openInformation(getShell(), WorkingSetMessages.getString("JavaWorkingSetPage.projectClosedDialog.title"), WorkingSetMessages.getString("JavaWorkingSetPage.projectClosedDialog.message")); //$NON-NLS-2$ //$NON-NLS-1$
					fTree.setChecked(element, false);
					fTree.setGrayed(element, true);
					return;
				}
				boolean state= event.getChecked();		
				fTree.setGrayed(element, false);
				if (isExpandable(element))
					setSubtreeChecked(element, state, true);
					
				updateParentState(element, state);
				validateInput();
			}
		});
	}

	private void setSubtreeChecked(Object parent, boolean state, boolean checkExpandedState) {
		if (!(parent instanceof IAdaptable))
			return;
		IContainer container= (IContainer)((IAdaptable)parent).getAdapter(IContainer.class);
		if ((!fTree.getExpandedState(parent) && checkExpandedState) || (container != null && !container.isAccessible()))
			return;
		
		Object[] children= fTreeContentProvider.getChildren(parent);
		for (int i= children.length - 1; i >= 0; i--) {
			Object element= children[i];
			if (state) {
				fTree.setChecked(element, true);
				fTree.setGrayed(element, false);
			}
			else
				fTree.setGrayChecked(element, false);
			if (isExpandable(element))
				setSubtreeChecked(element, state, true);
		}
	}

	private void updateParentState(Object child, boolean baseChildState) {
		if (child == null)
			return;
		if (child instanceof IAdaptable) {
			IResource resource= (IResource)((IAdaptable)child).getAdapter(IResource.class);
			if (resource != null && !resource.isAccessible())
				return;
		}
		Object parent= fTreeContentProvider.getParent(child);
		if (parent == null)
			return;

		boolean allSameState= true;
		Object[] children= null;
		children= fTreeContentProvider.getChildren(parent);

		for (int i= children.length -1; i >= 0; i--) {
			if (fTree.getChecked(children[i]) != baseChildState || fTree.getGrayed(children[i])) {
				allSameState= false;
				break;
			}
		}
	
		fTree.setGrayed(parent, !allSameState);
		fTree.setChecked(parent, !allSameState || baseChildState);
		
		updateParentState(parent, baseChildState);
	}

	private void initializeCheckedState() {
		if (fWorkingSet == null)
			return;

		BusyIndicator.showWhile(getShell().getDisplay(), new Runnable() {
			public void run() {
				Object[] elements= fWorkingSet.getElements();
				fTree.setCheckedElements(elements);
				for (int i= 0; i < elements.length; i++) {
					Object element= elements[i];
					if (isExpandable(element))
						setSubtreeChecked(element, true, true);
					updateParentState(element, true);
				}
			}
		});
	}
	
	private boolean isExpandable(Object element) {
		return (
			element instanceof IJavaProject
			||
			element instanceof IPackageFragmentRoot
			||
			element instanceof IPackageFragment
			||
			element instanceof IJavaModel
			||
			element instanceof IContainer);
	}
}
