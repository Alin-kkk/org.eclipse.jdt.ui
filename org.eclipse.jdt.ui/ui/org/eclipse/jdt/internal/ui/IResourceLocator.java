/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui;

import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.JavaModelException;

/**
 * This interface allows to locate different
 * resources which are related to an object
 */
public interface IResourceLocator {
	/**
	 * Returns the underlying finest granularity resource that contains
	 * the element, or <code>null</code> if the element is not contained
	 * in a resource (for example, a working copy, or an element contained
	 * in an external archive).
	 *
	 * @param	element	the element for which the resource is located
	 * @exception JavaModelException if the element does not exist or if an
	 *		exception occurrs while accessing its underlying resource
	 * @see IJavaElement#getUnderlyingResource
	 */
	IResource getUnderlyingResource(Object element) throws JavaModelException;
	/**
	 * Returns the resource that corresponds directly to the element,
	 * or <code>null</code> if there is no resource that corresponds to
	 * the element.
	 *
	 * <p>For example, the corresponding resource for an <code>ICompilationUnit</code>
	 * is its underlying <code>IFile</code>. The corresponding resource for
	 * an <code>IPackageFragment</code> that is not contained in an archive 
	 * is its underlying <code>IFolder</code>. An <code>IPackageFragment</code>
	 * contained in an archive has no corresponding resource. Similarly, there
	 * are no corresponding resources for <code>IMethods</code>,
	 * <code>IFields</code>, etc.
	 *
	 * @param	element	the element for which the resource is located
	 * @exception JavaModelException if the element does not exist or if an
	 *		exception occurrs while accessing its corresponding resource
	 * @see IJavaElement#getCorrespondingResource
	 */
	IResource getCorrespondingResource(Object element) throws JavaModelException;
	/**
	 * Returns the resource that contains the element. If the element is not
	 * directly contained by a resource then a helper resource or <code>null</code>
	 * is returned. Clients define the helper resource as needed.
	 *
	 * @param	element	the element for which the resource is located
	 * @exception JavaModelException if the element does not exist or if an
	 *		exception occurrs while accessing its containing resource
	 */
	IResource getContainingResource(Object element) throws JavaModelException;
}
