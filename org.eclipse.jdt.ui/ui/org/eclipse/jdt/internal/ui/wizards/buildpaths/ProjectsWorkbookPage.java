/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.swt.MGridLayout;


public class ProjectsWorkbookPage extends BuildPathBasePage {
			
	private ListDialogField fClassPathList;
	private IJavaProject fCurrJProject;
	
	private CheckedListDialogField fProjectsList;
	
	public ProjectsWorkbookPage(ListDialogField classPathList) {
		fClassPathList= classPathList;
				
		ProjectsListListener listener= new ProjectsListListener();
		
		String[] buttonLabels= new String[] {
			/* 0 */ NewWizardMessages.getString("ProjectsWorkbookPage.projects.checkall.button"), //$NON-NLS-1$
			/* 1 */ NewWizardMessages.getString("ProjectsWorkbookPage.projects.uncheckall.button") //$NON-NLS-1$
		};
		
		fProjectsList= new CheckedListDialogField(null, buttonLabels, new CPListLabelProvider());
		fProjectsList.setDialogFieldListener(listener);
		fProjectsList.setLabelText(NewWizardMessages.getString("ProjectsWorkbookPage.projects.label")); //$NON-NLS-1$
		fProjectsList.setCheckAllButtonIndex(0);
		fProjectsList.setUncheckAllButtonIndex(1);
	}
	
	public void init(IJavaProject jproject) {
		updateProjectsList(jproject);
	}
		
	private void updateProjectsList(IJavaProject currJProject) {
		try {
			IJavaModel jmodel= currJProject.getJavaModel();
			IJavaProject[] jprojects= jmodel.getJavaProjects();
			
			List projects= new ArrayList(jprojects.length);
			
			// a vector remembering all projects that dont have to be added anymore
			List existingProjects= new ArrayList(jprojects.length);
			existingProjects.add(currJProject.getProject());
			
			final List checkedProjects= new ArrayList(jprojects.length);
			// add the projects-cpentries that are already on the class path
			List cpelements= fClassPathList.getElements();
			for (int i= cpelements.size() - 1 ; i >= 0; i--) {
				CPListElement cpelem= (CPListElement)cpelements.get(i);
				if (cpelem.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
					existingProjects.add(cpelem.getResource());
					projects.add(cpelem);
					checkedProjects.add(cpelem);
				}
			}
			
			for (int i= 0; i < jprojects.length; i++) {
				IProject proj= jprojects[i].getProject();
				if (!existingProjects.contains(proj)) {
					projects.add(new CPListElement(IClasspathEntry.CPE_PROJECT, proj.getFullPath(), proj));
				}
			}	
						
			fProjectsList.setElements(projects);
			fProjectsList.setCheckedElements(checkedProjects);
				
		} catch (JavaModelException e) {
			// no solution exists or other problems: create an empty list
			fProjectsList.setElements(new ArrayList(5));
		}
		fCurrJProject= currJProject;
	}		
		
	// -------- UI creation ---------
		
	public Control getControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
			
		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fProjectsList }, true);
			
		MGridLayout layout= (MGridLayout)composite.getLayout();
		layout.marginWidth= 5;
		layout.marginHeight= 5;
		
		fProjectsList.setButtonsMinWidth(110);
		fProjectsList.getTableViewer().setSorter(new CPListElementSorter());	
				
		return composite;
	}
	
	private class ProjectsListListener implements IDialogFieldListener {
			
		// ---------- IDialogFieldListener --------
	
		public void dialogFieldChanged(DialogField field) {
			if (fCurrJProject != null) {
				// already initialized
				updateClasspathList();
			}
		}
	}
	
	private void updateClasspathList() {
		List projelements= fProjectsList.getCheckedElements();
		
		boolean remove= false;
		List cpelements= fClassPathList.getElements();
		// backwards, as entries will be deleted
		for (int i= cpelements.size() -1; i >= 0 ; i--) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			if (cpe.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
				if (!projelements.remove(cpe)) {
					cpelements.remove(i);
					remove= true;
				}	
			}
		}
		for (int i= 0; i < projelements.size(); i++) {
			cpelements.add(projelements.get(i));
		}
		if (remove || (projelements.size() > 0)) {
			fClassPathList.setElements(cpelements);
		}
	}
	
	/**
	 * @see BuildPathBasePage#getSelection
	 */
	public List getSelection() {
		return fProjectsList.getSelectedElements();
	}

	/**
	 * @see BuildPathBasePage#setSelection
	 */	
	public void setSelection(List selElements) {
		filterSelection(selElements, IClasspathEntry.CPE_PROJECT);
		fProjectsList.selectElements(new StructuredSelection(selElements));
	}	


}