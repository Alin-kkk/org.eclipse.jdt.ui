/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.browsing;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.internal.ui.viewsupport.BaseJavaElementContentProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTreeViewer;

public class ProjectsView extends JavaBrowsingPart {

	/**
	 * Creates the the viewer of this part.
	 * 
	 * @param parent	the parent for the viewer
	 */
	protected StructuredViewer createViewer(Composite parent) {
		return new ProblemTreeViewer(parent, SWT.MULTI);
	}
	
	/**
	 * Creates the the content provider of this part.
	 */
	protected BaseJavaElementContentProvider createContentProvider() {
		return new ProjectAndSourceFolderContentProvider(this);
	}

	/**
	 * Returns the context ID for the Help system.
	 * 
	 * @return	the string used as ID for the Help context
	 */
	protected String getHelpContextId() {
		return IJavaHelpContextIds.PROJECTS_VIEW;
	}

	/**
	 * Adds additional listeners to this view.
	 */
	protected void hookViewerListeners() {
	}

	protected void setInitialInput() {
		IJavaElement root= JavaCore.create(JavaPlugin.getDefault().getWorkspace().getRoot());
		getViewer().setInput(root);
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * input for this part.
	 * 
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid input
	 */
	protected boolean isValidInput(Object element) {
		return element instanceof IJavaModel;
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * element for this part.
	 * 
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid element
	 */
	protected boolean isValidElement(Object element) {
		return element instanceof IJavaProject || element instanceof IPackageFragmentRoot;
	}

	/**
	 * Finds the element which has to be selected in this part.
	 * 
	 * @param je	the Java element which has the focus
	 */
	protected IJavaElement findElementToSelect(IJavaElement je) {
		if (je == null)
			return null;
			
		switch (je.getElementType()) {
			case IJavaElement.JAVA_MODEL :
				return null;
			case IJavaElement.JAVA_PROJECT:
				return je;
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				if (je.getElementName().equals(IPackageFragmentRoot.DEFAULT_PACKAGEROOT_PATH))
					return je.getParent();
				else
					return je;
			default :
				return findElementToSelect(je.getParent());
		}
	}
	
	/*
	 * @see JavaBrowsingPart#setInput(Object)
	 */
	protected void setInput(Object input) {
		// Don't allow to clear input for this view
		if (input != null)
			super.setInput(input);
	}

}
