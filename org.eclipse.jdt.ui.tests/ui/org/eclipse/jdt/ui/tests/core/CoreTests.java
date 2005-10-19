/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.core;

import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.StringAsserts;

import org.eclipse.jdt.ui.tests.core.source.SourceActionTests;

/**
  */
public class CoreTests extends TestCase {

	public static Test suite() {
		
		TestSuite suite= new TestSuite();
		suite.addTest(AddImportTest.allTests());
		suite.addTest(SourceActionTests.suite());
		suite.addTest(ASTNodesInsertTest.allTests());
		suite.addTest(BindingsNameTest.allTests());
		suite.addTest(CallHierarchyTest.allTests());
		suite.addTest(ClassPathDetectorTest.allTests());
		suite.addTest(CodeCompletionTest.allTests());
		suite.addTest(CodeFormatterUtilTest.allTests());
		suite.addTest(HierarchicalASTVisitorTest.allTests());
		suite.addTest(ImportOrganizeTest.allTests());
		suite.addTest(JavaElementLabelsTest.allTests());
		suite.addTest(JavaModelUtilTest.allTests());
		suite.addTest(MethodOverrideTest.allTests());
		suite.addTest(NameProposerTest.allTests());
		suite.addTest(OverrideTest.allTests());
		suite.addTest(PartialASTTest.allTests());
		suite.addTest(ScopeAnalyzerTest.allTests());
		suite.addTest(TemplateStoreTest.allTests());
		suite.addTest(TypeHierarchyTest.allTests());
		suite.addTest(TypeRulesTest.allTests());
		suite.addTest(TypeInfoTest.allTests());	
		suite.addTest(StringsTest.allTests());
		
		return new ProjectTestSetup(suite);
	}

	public CoreTests(String name) {
		super(name);
	}
	
	public static void assertEqualString(String actual, String expected) {	
		StringAsserts.assertEqualString(actual, expected);
	}
	
	public static void assertEqualStringIgnoreDelim(String actual, String expected) throws IOException {
		StringAsserts.assertEqualStringIgnoreDelim(actual, expected);
	}	
	
	public static void assertEqualStringsIgnoreOrder(String[] actuals, String[] expecteds) {
		StringAsserts.assertEqualStringsIgnoreOrder(actuals, expecteds);			
	}
	
	public static void assertNumberOf(String name, int is, int expected) {
		assertTrue("Wrong number of " + name + ", is: " + is + ", expected: " + expected, is == expected);
	}
	
}
