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
package org.eclipse.jdt.internal.ui.javadocexport;

import java.util.ArrayList;

import org.eclipse.core.resources.IWorkspaceRoot;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class JavadocProjectContentProvider implements ITreeContentProvider {

	/*
	 * @see ITreeContentProvider#getChildren(Object)
	 */
	public Object[] getChildren(Object parentElement) {
		try {
			if (parentElement instanceof IJavaProject) {
				IJavaProject project= (IJavaProject) parentElement;
				return getPackageFragmentRoots(project);
			} else if (parentElement instanceof IPackageFragmentRoot) {
				return getPackageFragments((IPackageFragmentRoot) parentElement);
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
		return new Object[0];
	}
	/*
	 * @see IStructuredContentProvider#getElements(Object)
	 */
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof IWorkspaceRoot) {
			ArrayList list= new ArrayList();
			try {
				IJavaProject[] jprojects= JavaCore.create((IWorkspaceRoot) inputElement).getJavaProjects();
				for (int i= 0; i < jprojects.length; i++) {
					IJavaProject javaProject= jprojects[i];
					IPackageFragment[] els= javaProject.getPackageFragments();
					for (int j= 0; j < els.length; j++) {
						IPackageFragment iPackageFragment= (IPackageFragment) els[j];
						if (iPackageFragment.getCompilationUnits().length > 0) {
							list.add(javaProject);
							break;
						}
					}
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
			return list.toArray();
		}
		return new Object[0];
	}

	/*
	 * @see ITreeContentProvider#getParent(Object)
	 */
	public Object getParent(Object element) {
		
		IJavaElement parent= ((IJavaElement)element).getParent();
		if (parent instanceof IPackageFragmentRoot) {
			IPackageFragmentRoot root= (IPackageFragmentRoot) parent;
			if (root.getPath().equals(root.getJavaProject().getProject().getFullPath())) {
				return root.getJavaProject();
			}
		}
		return parent;
	}

	/*
	 * @see ITreeContentProvider#hasChildren(Object)
	 */
	public boolean hasChildren(Object element) {
		try {
			if (element instanceof IPackageFragment)
				return (((IPackageFragment) element).getChildren().length > 0);
			else
				return (getChildren(element).length > 0);
		} catch (JavaModelException e) {
		}
		return false;
	}

	/*
	 * @see IContentProvider#dispose()
	 */
	public void dispose() {
	}

	/*
	 * @see IContentProvider#inputChanged(Viewer, Object, Object)
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}

	private Object[] getPackageFragmentRoots(IJavaProject project) throws JavaModelException {
		ArrayList result= new ArrayList();

		IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			IPackageFragmentRoot root= roots[i];
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				if (root.getPath().equals(root.getJavaProject().getProject().getFullPath())) {
					return getPackageFragments(root);
				}
				result.add(root);
			}
		}
		return result.toArray();
	}

	private Object[] getPackageFragments(IPackageFragmentRoot root) throws JavaModelException {
		ArrayList packageFragments= new ArrayList();

		IJavaElement[] children= root.getChildren();
		for (int i= 0; i < children.length; i++) {
			if (((IPackageFragment) children[i]).containsJavaResources())
				packageFragments.add(children[i]);
		}
		return packageFragments.toArray();
	}
}
