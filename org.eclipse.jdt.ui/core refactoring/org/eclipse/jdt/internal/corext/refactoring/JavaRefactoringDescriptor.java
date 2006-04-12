/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

/**
 * Descriptor object of a java refactoring.
 * 
 * @since 3.2
 */
public final class JavaRefactoringDescriptor extends RefactoringDescriptor {

	/**
	 * Predefined argument called <code>element&lt;Number&gt;</code>.
	 * <p>
	 * This argument should be used to describe the elements being refactored.
	 * The value of this argument does not necessarily have to uniquely identify
	 * the elements. However, it must be possible to uniquely identify the
	 * elements using the value of this argument in conjunction with the values
	 * of the other user-defined attributes.
	 * </p>
	 * <p>
	 * The element arguments are simply distinguished by appending a number to
	 * the argument name, e.g. element1. The indices of this argument are non
	 * zero-based.
	 * </p>
	 */
	public static final String ATTRIBUTE_ELEMENT= "element"; //$NON-NLS-1$

	/**
	 * Predefined argument called <code>input</code>.
	 * <p>
	 * This argument should be used to describe the element being refactored.
	 * The value of this argument does not necessarily have to uniquely identify
	 * the input element. However, it must be possible to uniquely identify the
	 * input element using the value of this argument in conjunction with the
	 * values of the other user-defined attributes.
	 * </p>
	 */
	public static final String ATTRIBUTE_INPUT= "input"; //$NON-NLS-1$

	/**
	 * Predefined argument called <code>name</code>.
	 * <p>
	 * This argument should be used to name the element being refactored. The
	 * value of this argument may be shown in the user interface.
	 * </p>
	 */
	public static final String ATTRIBUTE_NAME= "name"; //$NON-NLS-1$

	/**
	 * Predefined argument called <code>selection</code>.
	 * <p>
	 * This argument should be used to describe user input selections within a
	 * text file. The value of this argument has the format "offset length".
	 * </p>
	 */
	public static final String ATTRIBUTE_SELECTION= "selection"; //$NON-NLS-1$

	/** The version attribute */
	private static final String ATTRIBUTE_VERSION= "version"; //$NON-NLS-1$

	/**
	 * Constant describing the deprecation resolving flag.
	 * <p>
	 * Clients should set this flag to indicate that the refactoring can used to
	 * resolve deprecation problems of members. Refactorings which can run on
	 * binary targets, but require a source attachment to work correctly, should
	 * set the <code>JAR_SOURCE_ATTACHMENT</code> flag as well.
	 * </p>
	 */
	public static final int DEPRECATION_RESOLVING= 1 << 17;

	/**
	 * Constant describing the jar importable flag.
	 * <p>
	 * Clients should set this flag to indicate that the refactoring can be
	 * imported from a JAR file. If this flag is set,
	 * <code>JAR_REFACTORABLE</code> should be set as well.
	 * </p>
	 */
	public static final int JAR_IMPORTABLE= 1 << 16;

	/**
	 * Constant describing the jar refactorable flag.
	 * <p>
	 * Clients should set this flag to indicate that the refactoring can be
	 * performed on a JAR file. Refactorings which can run on binary targets,
	 * but require a source attachment to work correctly, should set the
	 * <code>JAR_SOURCE_ATTACHMENT</code> flag as well.
	 * </p>
	 */
	public static final int JAR_REFACTORABLE= 1 << 19;

	/**
	 * Constant describing the jar source attachment flag.
	 * <p>
	 * Clients should set this flag to indicate that the refactoring can be
	 * performed on a JAR file if and only if it contains a source attachment.
	 * </p>
	 */
	public static final int JAR_SOURCE_ATTACHMENT= 1 << 18;

	/** The version value 1.0 */
	private static final String VALUE_VERSION_1_0= "1.0"; //$NON-NLS-1$

	/**
	 * Converts the specified element to an input handle.
	 * 
	 * @param project
	 *            the project, or <code>null</code> for the workspace
	 * @param element
	 *            the element
	 * @return a corresponding input handle
	 */
	public static String elementToHandle(final String project, final IJavaElement element) {
		final String handle= element.getHandleIdentifier();
		if (project != null) {
			final String id= element.getJavaProject().getHandleIdentifier();
			return handle.substring(id.length());
		}
		return handle;
	}

	/**
	 * Converts an input handle back to the corresponding java element.
	 * 
	 * @param project
	 *            the project, or <code>null</code> for the workspace
	 * @param handle
	 *            the input handle
	 * @return the corresponding java element, or <code>null</code> if no such
	 *         element exists
	 */
	public static IJavaElement handleToElement(final String project, final String handle) {
		return handleToElement(project, handle, true);
	}

	/**
	 * Converts an input handle back to the corresponding java element.
	 * 
	 * @param project
	 *            the project, or <code>null</code> for the workspace
	 * @param handle
	 *            the input handle
	 * @param check
	 *            <code>true</code> to check for existence of the element,
	 *            <code>false</code> otherwise
	 * @return the corresponding java element, or <code>null</code> if no such
	 *         element exists
	 */
	public static IJavaElement handleToElement(final String project, final String handle, final boolean check) {
		IJavaElement element= JavaCore.create(handle);
		if (element == null && project != null) {
			final IJavaProject javaProject= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProject(project);
			final String identifier= javaProject.getHandleIdentifier();
			element= JavaCore.create(identifier + handle);
		}
		if (element != null && (!check || element.exists()))
			return element;
		return null;
	}

	/**
	 * Converts an input handle with the given prefix back to the corresponding
	 * resource.
	 * 
	 * @param project
	 *            the project, or <code>null</code> for the workspace
	 * @param handle
	 *            the input handle
	 * 
	 * @return the corresponding resource, or <code>null</code> if no such
	 *         resource exists
	 */
	public static IResource handleToResource(final String project, final String handle) {
		final IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		if ("".equals(handle)) //$NON-NLS-1$
			return null;
		final IPath path= Path.fromPortableString(handle);
		if (path == null)
			return null;
		if (project != null && !"".equals(project)) //$NON-NLS-1$
			return root.getProject(project).findMember(path);
		return root.findMember(path);
	}

	/**
	 * Converts the specified resource to an input handle.
	 * 
	 * @param project
	 *            the project, or <code>null</code> for the workspace
	 * @param resource
	 *            the resource
	 * 
	 * @return the input handle
	 */
	public static String resourceToHandle(final String project, final IResource resource) {
		if (project != null && !"".equals(project)) //$NON-NLS-1$
			return resource.getProjectRelativePath().toPortableString();
		return resource.getFullPath().toPortableString();
	}

	/** The map of arguments (element type: &lt;String, String&gt;) */
	private final Map fArguments;

	/** The refactoring contribution, or <code>null</code> */
	private JavaRefactoringContribution fContribution;

	/**
	 * Creates a new java refactoring descriptor.
	 * 
	 * @param contribution
	 *            the refactoring contribution, or <code>null</code>
	 * @param id
	 *            the unique id of the refactoring
	 * @param project
	 *            the project name, or <code>null</code>
	 * @param description
	 *            the description
	 * @param comment
	 *            the comment, or <code>null</code>
	 * @param arguments
	 *            the argument map
	 * @param flags
	 *            the flags
	 */
	public JavaRefactoringDescriptor(final JavaRefactoringContribution contribution, final String id, final String project, final String description, final String comment, final Map arguments, final int flags) {
		super(id, project, description, comment, flags);
		Assert.isNotNull(arguments);
		fContribution= contribution;
		fArguments= arguments;
	}

	/**
	 * Creates a new java refactoring descriptor.
	 * 
	 * @param id
	 *            the unique id of the refactoring
	 * @param project
	 *            the project name, or <code>null</code>
	 * @param description
	 *            the description
	 * @param comment
	 *            the comment, or <code>null</code>
	 * @param arguments
	 *            the argument map
	 * @param flags
	 *            the flags
	 */
	public JavaRefactoringDescriptor(final String id, final String project, final String description, final String comment, final Map arguments, final int flags) {
		this(null, id, project, description, comment, arguments, flags);
	}

	/**
	 * Creates refactoring arguments for this refactoring descriptor.
	 * 
	 * @return the refactoring arguments
	 */
	public RefactoringArguments createArguments() {
		final JavaRefactoringArguments arguments= new JavaRefactoringArguments(getProject());
		for (final Iterator iterator= fArguments.entrySet().iterator(); iterator.hasNext();) {
			final Map.Entry entry= (Entry) iterator.next();
			final String name= (String) entry.getKey();
			final String value= (String) entry.getValue();
			if (name != null && !"".equals(name) && value != null) //$NON-NLS-1$
				arguments.setAttribute(name, value);
		}
		return arguments;
	}

	/**
	 * {@inheritDoc}
	 */
	public Refactoring createRefactoring(final RefactoringStatus status) throws CoreException {
		Refactoring refactoring= null;
		if (fContribution != null)
			refactoring= fContribution.createRefactoring(this);
		else {
			final RefactoringContribution contribution= RefactoringCore.getRefactoringContribution(getID());
			if (contribution instanceof JavaRefactoringContribution) {
				fContribution= (JavaRefactoringContribution) contribution;
				refactoring= fContribution.createRefactoring(this);
			}
		}
		if (refactoring != null) {
			if (refactoring instanceof IInitializableRefactoringComponent)
				status.merge(((IInitializableRefactoringComponent) refactoring).initialize(createArguments()));
			else
				status.merge(RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.JavaRefactoringDescriptor_initialization_error, getID())));
		}
		return refactoring;
	}

	/**
	 * Converts the specified element to an input handle.
	 * 
	 * @param element
	 *            the element
	 * @return a corresponding input handle
	 */
	public String elementToHandle(final IJavaElement element) {
		Assert.isNotNull(element);
		return elementToHandle(getProject(), element);
	}

	/**
	 * Returns the argument map
	 * 
	 * @return the argument map.
	 */
	public Map getArguments() {
		final Map map= new HashMap(fArguments);
		map.put(ATTRIBUTE_VERSION, VALUE_VERSION_1_0);
		return map;
	}

	/**
	 * Returns the refactoring contribution.
	 * 
	 * @return the refactoring contribution, or <code>null</code>
	 */
	public JavaRefactoringContribution getContribution() {
		return fContribution;
	}
}