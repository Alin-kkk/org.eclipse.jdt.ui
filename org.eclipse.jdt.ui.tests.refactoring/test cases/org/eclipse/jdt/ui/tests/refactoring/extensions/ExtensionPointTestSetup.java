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
package org.eclipse.jdt.ui.tests.refactoring.extensions;

import junit.extensions.TestSetup;
import junit.framework.Test;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

public class ExtensionPointTestSetup extends TestSetup {
	
	private IJavaProject fJavaProject;
	private IPackageFragmentRoot fRoot;
	private static final String CONTAINER= "src";
	private IPackageFragment fTestPackage;

	public ExtensionPointTestSetup(Test test) {
		super(test);
	}	
	
	public IPackageFragmentRoot getRoot() {
		return fRoot;
	}
		
	protected void setUp() throws Exception {
		super.setUp();
		
		fJavaProject= JavaProjectHelper.createJavaProject("TestProject", "bin");
		JavaProjectHelper.addRTJar(fJavaProject);
		fRoot= JavaProjectHelper.addSourceContainer(fJavaProject, CONTAINER);
		
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description= workspace.getDescription();
		description.setAutoBuilding(false);
		workspace.setDescription(description);
		
		fTestPackage= getRoot().createPackageFragment("test", true, null);
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		JavaProjectHelper.delete(fJavaProject);
	}
}
