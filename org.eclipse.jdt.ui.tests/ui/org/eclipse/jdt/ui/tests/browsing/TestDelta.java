/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 *
 * Contributors:
 *     IBM Corporation - initial implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.tests.browsing;

import org.eclipse.core.resources.IResourceDelta;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;

/**
 * A faux  delta for testing
 */
public class TestDelta implements IJavaElementDelta {

	private int fKind;
	private IJavaElement fElement;

	private IJavaElementDelta[] fAffectedChildren;

	public TestDelta(int kind, IJavaElement element) {
		fKind= kind;
		fElement= element;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getAddedChildren()
	 */
	public IJavaElementDelta[] getAddedChildren() {
		return null;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getAffectedChildren()
	 */
	public IJavaElementDelta[] getAffectedChildren() {
		if (fAffectedChildren == null)
			return new IJavaElementDelta[0];
		else
			return fAffectedChildren;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getChangedChildren()
	 */
	public IJavaElementDelta[] getChangedChildren() {
		return null;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getElement()
	 */
	public IJavaElement getElement() {
		return fElement;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getFlags()
	 */
	public int getFlags() {
		return 0;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getKind()
	 */
	public int getKind() {
		return fKind;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getMovedFromElement()
	 */
	public IJavaElement getMovedFromElement() {
		return null;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getMovedToElement()
	 */
	public IJavaElement getMovedToElement() {
		return null;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getRemovedChildren()
	 */
	public IJavaElementDelta[] getRemovedChildren() {
		return null;
	}
	/*
	 * @see org.eclipse.jdt.core.IJavaElementDelta#getResourceDeltas()
	 */
	public IResourceDelta[] getResourceDeltas() {
		return null;
	}

	/**
	 * Set the affected children of this Delta.
	 * @param children
	 */
	public void setAffectedChildren(IJavaElementDelta[] children) {
		fAffectedChildren= children;
	}

	/**
	 * Create the parent deltas for an IPackageFragment.
	 * 
	 * @param frag
	 * @param delta
	 * @return TestDelta
	 */
	public static TestDelta createParentDeltas(IPackageFragment frag, TestDelta delta) {
		IJavaElement root= frag.getParent();
		TestDelta rootDelta= new TestDelta(IJavaElementDelta.CHANGED, root);

		IJavaProject proj= root.getJavaProject();
		TestDelta projectDelta= new TestDelta(IJavaElementDelta.CHANGED, proj);

		IJavaModel model= proj.getJavaModel();
		TestDelta modelDelta= new TestDelta(IJavaElementDelta.CHANGED, model);

		//set affected children
		modelDelta.setAffectedChildren(new IJavaElementDelta[] { projectDelta });
		projectDelta.setAffectedChildren(new IJavaElementDelta[] { rootDelta });
		rootDelta.setAffectedChildren(new IJavaElementDelta[] { delta });
		return modelDelta;
	}

	/**
	 * Create a delta for a list of ICompilationUnits, all the parent deltas
	 * from the JavaModel down are created as well.
	 * 
	 * @param cu
	 * @param parent
	 * @param action
	 * @return IJavaElementDelta
	 */
	public static IJavaElementDelta createCUDelta(ICompilationUnit[] cu, IPackageFragment parent, int action) {
		TestDelta fragmentDelta= new TestDelta(IJavaElementDelta.CHANGED, parent);

		TestDelta[] deltas= new TestDelta[cu.length];
		for (int i= 0; i < cu.length; i++) {
			deltas[i]= new TestDelta(action, cu[i]);
		}

		fragmentDelta.setAffectedChildren(deltas);
		return createParentDeltas(parent, fragmentDelta);
	}

	/**
	 * Creates a delta for a PackageFragment from the JavaModel down.
	 * 
	 * @param frag
	 * @param action
	 * @return IJavaElementDelta
	 */
	public static IJavaElementDelta createDelta(IPackageFragment frag, int action) {
		TestDelta delta= new TestDelta(action, frag);
		return createParentDeltas(frag, delta);
	}

}
