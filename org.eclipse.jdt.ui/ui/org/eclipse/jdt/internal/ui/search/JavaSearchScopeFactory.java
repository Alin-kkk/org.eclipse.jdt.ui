/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.search;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IWorkingSetSelectionDialog;

import org.eclipse.search.ui.ISearchResultViewEntry;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class JavaSearchScopeFactory {

	private static JavaSearchScopeFactory fgInstance;
	private static IJavaSearchScope EMPTY_SCOPE= SearchEngine.createJavaSearchScope(new IJavaElement[] {});
	
	private JavaSearchScopeFactory() {
	}

	public static JavaSearchScopeFactory getInstance() {
		if (fgInstance == null)
			fgInstance= new JavaSearchScopeFactory();
		return fgInstance;
	}

	public IWorkingSet[] queryWorkingSets() throws JavaModelException {
		IWorkingSetSelectionDialog dialog= PlatformUI.getWorkbench().getWorkingSetManager().createWorkingSetSelectionDialog(JavaPlugin.getActiveWorkbenchShell());
		if (dialog.open() == Window.OK) {
			IWorkingSet[] workingSets= dialog.getSelection();
			if (workingSets.length > 0)
				return workingSets;
		}
		return null;
	}

	public IJavaSearchScope createJavaSearchScope(IWorkingSet[] workingSets) {
		if (workingSets == null || workingSets.length < 1)
			return EMPTY_SCOPE;

		Set javaElements= new HashSet(workingSets.length * 10);
		for (int i= 0; i < workingSets.length; i++)
			addJavaElements(javaElements, workingSets[i]);
		return createJavaSearchScope(javaElements);
	}
	
	public IJavaSearchScope createJavaSearchScope(IWorkingSet workingSet) {
		Set javaElements= new HashSet(10);
		addJavaElements(javaElements, workingSet);
		return createJavaSearchScope(javaElements);
	}

	public IJavaSearchScope createJavaSearchScope(IResource[] resources) {
		if (resources == null)
			return EMPTY_SCOPE;
		Set javaElements= new HashSet(resources.length);
		addJavaElements(javaElements, resources);
		return createJavaSearchScope(javaElements);
	}
	
	public IJavaSearchScope createJavaSearchScope(ISelection selection) {
		if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
			Iterator iter= ((IStructuredSelection)selection).iterator();
			Set javaElements= new HashSet(((IStructuredSelection)selection).size());
			while (iter.hasNext()) {
				Object selectedElement= iter.next();

				// Unpack search result view entry
				if (selectedElement instanceof ISearchResultViewEntry)
					selectedElement= ((ISearchResultViewEntry)selectedElement).getGroupByKey();
				
				if (selectedElement instanceof IJavaElement)
					addJavaElements(javaElements, (IJavaElement)selectedElement);
				else if (selectedElement instanceof IResource)
					addJavaElements(javaElements, (IResource)selectedElement);
				else if (selectedElement instanceof IAdaptable) {
					IResource resource= (IResource)((IAdaptable)selectedElement).getAdapter(IResource.class);
					if (resource != null)
						addJavaElements(javaElements, resource);
				}
			}
			return createJavaSearchScope(javaElements);
		}
		return EMPTY_SCOPE;
	}

	private IJavaSearchScope createJavaSearchScope(Set javaElements) {
		return SearchEngine.createJavaSearchScope((IJavaElement[])javaElements.toArray(new IJavaElement[javaElements.size()]));
	}

	private void addJavaElements(Set javaElements, IResource[] resources) {
		for (int i= 0; i < resources.length; i++)
			addJavaElements(javaElements, resources[i]);
	}

	private void addJavaElements(Set javaElements, IAdaptable resource) {
		IJavaElement javaElement= (IJavaElement)resource.getAdapter(IJavaElement.class);
		if (javaElement == null)
			// not a Java resource
			return;
		
		if (javaElement.getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
			// add other possible package fragments
			try {
				addJavaElements(javaElements, ((IFolder)resource).members());
			} catch (CoreException ex) {
				// don't add elements
			}
		}
			
		addJavaElements(javaElements, javaElement);
	}

	private void addJavaElements(Set javaElements, IJavaElement javaElement) {
		switch (javaElement.getElementType()) {
			case IJavaElement.JAVA_PROJECT:
				addJavaElements(javaElements, (IJavaProject)javaElement);
				break;
			default:
				javaElements.add(javaElement);
		}
		
	}

	private void addJavaElements(Set javaElements, IJavaProject javaProject) {
		IPackageFragmentRoot[] roots;
		try {
			roots= javaProject.getPackageFragmentRoots();
		} catch (JavaModelException ex) {
			return;
		}

		for (int i= 0; i < roots.length; i++)
			if (!roots[i].isExternal())
				javaElements.add(roots[i]);
	}

	private void addJavaElements(Set javaElements, IWorkingSet workingSet) {
		if (workingSet == null)
			return;
		
		IAdaptable[] elements= workingSet.getElements();
		for (int i= 0; i < elements.length; i++) {
			if (elements[i] instanceof IJavaElement)
				addJavaElements(javaElements, (IJavaElement)elements[i]);
			else
				addJavaElements(javaElements, elements[i]);
		}
	}
}
