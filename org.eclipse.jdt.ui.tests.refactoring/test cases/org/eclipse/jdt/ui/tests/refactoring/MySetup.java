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
package org.eclipse.jdt.ui.tests.refactoring;

import junit.extensions.TestSetup;
import junit.framework.Test;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

public class MySetup extends TestSetup {
	
	public MySetup(Test test) {
		super(test);
	}
	public static final String CONTAINER= "src";
	private static IPackageFragmentRoot fgRoot;
	private static IPackageFragment fgPackageP;
	private static IJavaProject fgJavaTestProject;
	
	public static IPackageFragmentRoot getDefaultSourceFolder() throws Exception {
		if (fgRoot != null) 
			return fgRoot;
		throw new Exception("MySetup not initialized");
	}
	
	public static IJavaProject getProject()throws Exception {
		if (fgJavaTestProject != null)
			return fgJavaTestProject;
		throw new Exception("MySetup not initialized");
	}
	
	public static IPackageFragment getPackageP()throws Exception {
		if (fgPackageP != null) 
			return fgPackageP;
		throw new Exception("MySetup not initialized");
	}
	
	protected void setUp() throws Exception {
		super.setUp();
		fgJavaTestProject= JavaProjectHelper.createJavaProject("TestProject", "bin");
		JavaProjectHelper.addRTJar(fgJavaTestProject);
		fgRoot= JavaProjectHelper.addSourceContainer(fgJavaTestProject, CONTAINER);
		fgPackageP= fgRoot.createPackageFragment("p", true, null);
	}
	
	protected void tearDown() throws Exception {
		if (fgPackageP.exists())
			fgPackageP.delete(true, null);
		JavaProjectHelper.removeSourceContainer(fgJavaTestProject, CONTAINER);
		JavaProjectHelper.delete(fgJavaTestProject);
		super.tearDown();
	}
	
}

