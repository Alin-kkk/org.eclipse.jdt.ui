/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.TestOptionsSetup;

import org.eclipse.jdt.ui.tests.astrewrite.ASTRewritingTest;
import org.eclipse.jdt.ui.tests.browsing.PackagesViewContentProviderTests;
import org.eclipse.jdt.ui.tests.browsing.PackagesViewDeltaTests;
import org.eclipse.jdt.ui.tests.callhierarchy.CallHierarchyContentProviderTest;
import org.eclipse.jdt.ui.tests.core.CoreTests;
import org.eclipse.jdt.ui.tests.packageview.PackageExplorerTests;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixTest;
import org.eclipse.jdt.ui.tests.search.SearchTest;
import org.eclipse.jdt.ui.tests.wizardapi.NewJavaProjectWizardTest;


/**
 * Test all areas of the UI.
 */
public class AutomatedSuite extends TestSuite {

	/**
	 * Returns the suite.  This is required to
	 * use the JUnit Launcher.
	 * 
	 * @return the test
	 */
	public static Test suite() {
		return new TestOptionsSetup(new AutomatedSuite());
	}

	/**
	 * Construct the test suite.
	 */
	public AutomatedSuite() {
		addTest(CoreTests.suite());
		addTest(ASTRewritingTest.suite());
		addTest(QuickFixTest.suite());
		
		addTest(NewJavaProjectWizardTest.suite());
		
		addTest(PackageExplorerTests.suite());
		
		addTest(PackagesViewContentProviderTests.suite());
		addTest(PackagesViewDeltaTests.suite());
		
		addTest(CallHierarchyContentProviderTest.suite());
		
		addTest(SearchTest.suite()); 
	}
}

