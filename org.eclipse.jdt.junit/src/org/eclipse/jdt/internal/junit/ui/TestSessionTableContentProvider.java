/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.junit.ui;

import java.util.ArrayList;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.internal.junit.model.TestCaseElement;
import org.eclipse.jdt.internal.junit.model.TestElement;
import org.eclipse.jdt.internal.junit.model.TestRoot;
import org.eclipse.jdt.internal.junit.model.TestSuiteElement;


public class TestSessionTableContentProvider implements IStructuredContentProvider {

	private final Object[] NO_CHILDREN= new Object[0];
	
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}

	public Object[] getElements(Object inputElement) {
		ArrayList all= new ArrayList();
		addAll(all, (TestRoot) inputElement);
		return all.toArray();
	}

	private void addAll(ArrayList all, TestSuiteElement suite) {
		TestElement[] children= suite.getChildren();
		for (int i= 0; i < children.length; i++) {
			TestElement element= children[i];
			if (element instanceof TestSuiteElement) {
				addAll(all, (TestSuiteElement) element);
			} else if (element instanceof TestCaseElement) {
				all.add(element);
			}
		}
	}

	public void dispose() {
	}
}
