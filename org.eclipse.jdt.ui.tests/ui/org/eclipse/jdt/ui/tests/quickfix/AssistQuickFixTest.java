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
package org.eclipse.jdt.ui.tests.quickfix;

import java.util.ArrayList;
import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.core.runtime.Preferences;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.tests.core.ProjectTestSetup;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.correction.AssignToVariableAssistProposal;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.internal.ui.text.correction.CUCorrectionProposal;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;
import org.eclipse.jdt.internal.ui.text.correction.LinkedNamesAssistProposal;

public class AssistQuickFixTest extends QuickFixTest {
	
	private static final Class THIS= AssistQuickFixTest.class;
	
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;


	public AssistQuickFixTest(String name) {
		super(name);
	}
	
	public static Test allTests() {
		return new ProjectTestSetup(new TestSuite(THIS));
	}
	
	public static Test suite() {
		if (true) {
			return allTests();
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new AssistQuickFixTest("testUnwrapMethodInvocation"));
			return new ProjectTestSetup(suite);
		}
	}


	protected void setUp() throws Exception {
		Hashtable options= JavaCore.getDefaultOptions();
		options.put(JavaCore.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(JavaCore.FORMATTER_TAB_SIZE, "4");
		
		JavaCore.setOptions(options);			

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, false);
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, false);
		
		Preferences corePrefs= JavaCore.getPlugin().getPluginPreferences();
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_SUFFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES, "");		
		
		fJProject1= ProjectTestSetup.getProject();

		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, "src");
	}


	protected void tearDown() throws Exception {
		JavaProjectHelper.clear(fJProject1, ProjectTestSetup.getDefaultClasspath());
	}
	
	public void testAssignToLocal() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        getClass();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("getClass()");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 3);
		assertCorrectLabels(proposals);
		
		boolean doField= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			Object curr= proposals.get(i);
			if (!(curr instanceof AssignToVariableAssistProposal)) {
				continue;
			}
			AssignToVariableAssistProposal proposal= (AssignToVariableAssistProposal) curr;
			if (proposal.getVariableKind() == AssignToVariableAssistProposal.FIELD) {
				assertTrue("same proposal kind", doField);
				doField= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("    private Class class1;\n");
				buf.append("\n");
				buf.append("    public void foo() {\n");
				buf.append("        class1 = getClass();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
				
			} else if (proposal.getVariableKind() == AssignToVariableAssistProposal.LOCAL) {
				assertTrue("same proposal kind", doLocal);
				doLocal= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("    public void foo() {\n");
				buf.append("        Class class1 = getClass();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			}
		}
	}
	
	public void testAssignToLocal2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");		
		buf.append("public class E {\n");
		buf.append("    public Vector goo() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        goo().iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("goo().iterator()");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 3);
		assertCorrectLabels(proposals);

		boolean doField= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			Object curr= proposals.get(i);
			if (!(curr instanceof AssignToVariableAssistProposal)) {
				continue;
			}			
			AssignToVariableAssistProposal proposal= (AssignToVariableAssistProposal) curr;
			if (proposal.getVariableKind() == AssignToVariableAssistProposal.FIELD) {
				assertTrue("same proposal kind", doField);
				doField= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.util.Iterator;\n");
				buf.append("import java.util.Vector;\n");				
				buf.append("public class E {\n");
				buf.append("    private Iterator iterator;\n");
				buf.append("    public Vector goo() {\n");
				buf.append("        return null;\n");
				buf.append("    }\n");		
				buf.append("    public void foo() {\n");
				buf.append("        iterator = goo().iterator();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
				
			} else if (proposal.getVariableKind() == AssignToVariableAssistProposal.LOCAL) {
				assertTrue("same proposal kind", doLocal);
				doLocal= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.util.Iterator;\n");
				buf.append("import java.util.Vector;\n");				
				buf.append("public class E {\n");
				buf.append("    public Vector goo() {\n");
				buf.append("        return null;\n");
				buf.append("    }\n");		
				buf.append("    public void foo() {\n");
				buf.append("        Iterator iterator = goo().iterator();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());

			}
		}
	}
	
	public void testAssignToLocal3() throws Exception {
		// test prefixes and this qualification
		
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, true);
		Preferences corePrefs= JavaCore.getPlugin().getPluginPreferences();
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "f");
		corePrefs.setValue(JavaCore.CODEASSIST_LOCAL_PREFIXES, "_");
			
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    private int fCount;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.getSecurityManager();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("System");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 3);
		assertCorrectLabels(proposals);

		boolean doField= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			Object curr= proposals.get(i);
			if (!(curr instanceof AssignToVariableAssistProposal)) {
				continue;
			}			
			AssignToVariableAssistProposal proposal= (AssignToVariableAssistProposal) curr;
			if (proposal.getVariableKind() == AssignToVariableAssistProposal.FIELD) {
				assertTrue("same proposal kind", doField);
				doField= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("\n");
				buf.append("    private int fCount;\n");
				buf.append("    private SecurityManager fManager;\n");
				buf.append("\n");				
				buf.append("    public void foo() {\n");
				buf.append("        this.fManager = System.getSecurityManager();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
				
			} else if (proposal.getVariableKind() == AssignToVariableAssistProposal.LOCAL) {
				assertTrue("same proposal kind", doLocal);
				doLocal= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("\n");
				buf.append("    private int fCount;\n");
				buf.append("\n");
				buf.append("    public void foo() {\n");
				buf.append("        SecurityManager _manager = System.getSecurityManager();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());

			}
		}
	}
	
	public void testAssignToLocal4() throws Exception {
		// test name conflict
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    private int f;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        Math.min(1.0f, 2.0f);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("Math");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 3);
		assertCorrectLabels(proposals);

		boolean doField= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			Object curr= proposals.get(i);
			if (!(curr instanceof AssignToVariableAssistProposal)) {
				continue;
			}			
			AssignToVariableAssistProposal proposal= (AssignToVariableAssistProposal) curr;
			if (proposal.getVariableKind() == AssignToVariableAssistProposal.FIELD) {
				assertTrue("same proposal kind", doField);
				doField= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("\n");
				buf.append("    private int f;\n");
				buf.append("    private float g;\n");
				buf.append("\n");				
				buf.append("    public void foo() {\n");
				buf.append("        g = Math.min(1.0f, 2.0f);\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
				
			} else if (proposal.getVariableKind() == AssignToVariableAssistProposal.LOCAL) {
				assertTrue("same proposal kind", doLocal);
				doLocal= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("\n");
				buf.append("    private int f;\n");
				buf.append("\n");
				buf.append("    public void foo() {\n");
				buf.append("        float g = Math.min(1.0f, 2.0f);\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());

			}
		}
	}

	public void testAssignToLocal5() throws Exception {
		// test prefixes and this qualification on static method
		
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, true);
		Preferences corePrefs= JavaCore.getPlugin().getPluginPreferences();
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "f");
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, "fg");
		corePrefs.setValue(JavaCore.CODEASSIST_LOCAL_PREFIXES, "_");
			
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    private int fCount;\n");
		buf.append("\n");
		buf.append("    public static void foo() {\n");
		buf.append("        System.getSecurityManager();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("System");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 3);
		assertCorrectLabels(proposals);

		boolean doField= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			Object curr= proposals.get(i);
			if (!(curr instanceof AssignToVariableAssistProposal)) {
				continue;
			}			
			AssignToVariableAssistProposal proposal= (AssignToVariableAssistProposal) curr;
			if (proposal.getVariableKind() == AssignToVariableAssistProposal.FIELD) {
				assertTrue("same proposal kind", doField);
				doField= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("\n");
				buf.append("    private int fCount;\n");
				buf.append("    private static SecurityManager fgManager;\n");
				buf.append("\n");				
				buf.append("    public static void foo() {\n");
				buf.append("        E.fgManager = System.getSecurityManager();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
				
			} else if (proposal.getVariableKind() == AssignToVariableAssistProposal.LOCAL) {
				assertTrue("same proposal kind", doLocal);
				doLocal= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("\n");
				buf.append("    private int fCount;\n");
				buf.append("\n");
				buf.append("    public static void foo() {\n");
				buf.append("        SecurityManager _manager = System.getSecurityManager();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());

			}
		}
	}


	
	public void testAssignParamToField() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public  E(int count) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("count");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		int index= (proposals.get(0) instanceof LinkedNamesAssistProposal) ? 1 : 0;
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(index);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("\n");
		buf.append("    public  E(int count) {\n");
		buf.append("        this.count = count;\n");		
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testAssignParamToField2() throws Exception {
		Preferences corePrefs= JavaCore.getPlugin().getPluginPreferences();
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "f");

		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public  E(int count, Vector vec[]) {\n");
		buf.append("        super();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("vec");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		int index= (proposals.get(0) instanceof LinkedNamesAssistProposal) ? 1 : 0;
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(index);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Vector[] fVec;\n");
		buf.append("\n");
		buf.append("    public  E(int count, Vector vec[]) {\n");
		buf.append("        super();\n");
		buf.append("        fVec = vec;\n");		
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}

	public void testAssignParamToField3() throws Exception {
		Preferences corePrefs= JavaCore.getPlugin().getPluginPreferences();
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, "fg");

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, true);
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private int fgVec;\n");
		buf.append("\n");
		buf.append("    public static void foo(int count, Vector vec[]) {\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("vec[]");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		int index= (proposals.get(0) instanceof LinkedNamesAssistProposal) ? 1 : 0;
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(index);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private int fgVec;\n");
		buf.append("    private static Vector[] fgVec2;\n");
		buf.append("\n");
		buf.append("    public static void foo(int count, Vector vec[]) {\n");
		buf.append("        E.fgVec2 = vec;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testAssignParamToField4() throws Exception {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, true);
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private long count;\n");
		buf.append("\n");
		buf.append("    public void foo(int count) {\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
				
		int offset= buf.toString().indexOf("int count");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private long count;\n");
		buf.append("    private int count2;\n");
		buf.append("\n");
		buf.append("    public void foo(int count) {\n");
		buf.append("        this.count2 = count;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}	
	
	
	
	public void testAssignToLocal2CursorAtEnd() throws Exception {
//		Preferences corePrefs= JavaCore.getPlugin().getPluginPreferences();
//		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "");
//		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_SUFFIXES, "_m");
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");		
		buf.append("public class E {\n");
		buf.append("    public Vector goo() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        goo().toArray();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "goo().toArray();";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		boolean doField= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			AssignToVariableAssistProposal proposal= (AssignToVariableAssistProposal) proposals.get(i);
			if (proposal.getVariableKind() == AssignToVariableAssistProposal.FIELD) {
				assertTrue("same proposal kind", doField);
				doField= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.util.Vector;\n");				
				buf.append("public class E {\n");
				buf.append("    private Object[] objects;\n");
				buf.append("    public Vector goo() {\n");
				buf.append("        return null;\n");
				buf.append("    }\n");		
				buf.append("    public void foo() {\n");
				buf.append("        objects = goo().toArray();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
				
			} else if (proposal.getVariableKind() == AssignToVariableAssistProposal.LOCAL) {
				assertTrue("same proposal kind", doLocal);
				doLocal= false;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
		
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.util.Vector;\n");				
				buf.append("public class E {\n");
				buf.append("    public Vector goo() {\n");
				buf.append("        return null;\n");
				buf.append("    }\n");		
				buf.append("    public void foo() {\n");
				buf.append("        Object[] objects = goo().toArray();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
				
			}
		}
	}
	
	public void testReplaceCatchClauseWithThrowsWithFinally() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");	
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "(IOException e)";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testReplaceSingleCatchClauseWithThrows() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");	
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "(IOException e)";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");	
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
				
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });	

	}
	
	public void testUnwrapForLoop() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i= 0; i < 3; i++) {\n");		
		buf.append("            goo();\n");
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "for";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testUnwrapDoStatement() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        do {\n");		
		buf.append("            goo();\n");
		buf.append("            goo();\n");
		buf.append("            goo();\n");
		buf.append("        } while (true);\n");	
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "do";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("        goo();\n");
		buf.append("        goo();\n");		
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}	
	
	public void testUnwrapWhileLoop2Statements() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (true) {\n");		
		buf.append("            goo();\n");
		buf.append("            System.out.println();\n");		
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "while";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("        System.out.println();\n");		
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testUnwrapIfStatement() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (1+ 3 == 6) {\n");		
		buf.append("            StringBuffer buf= new StringBuffer();\n");
		buf.append("            buf.append(1);\n");
		buf.append("            buf.append(2);\n");
		buf.append("            buf.append(3);\n");
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "if";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        StringBuffer buf= new StringBuffer();\n");
		buf.append("        buf.append(1);\n");
		buf.append("        buf.append(2);\n");
		buf.append("        buf.append(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (1+ 3 == 6) {\n");		
		buf.append("            StringBuffer buf= new StringBuffer();\n");
		buf.append("            buf.append(1);\n");
		buf.append("            buf.append(2);\n");
		buf.append("            buf.append(3);\n");
		buf.append("        } else {\n");
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
				
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });	
	}
	
	public void testUnwrapTryStatement() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            StringBuffer buf= new StringBuffer();\n");
		buf.append("            buf.append(1);\n");
		buf.append("            buf.append(2);\n");
		buf.append("            buf.append(3);\n");
		buf.append("        } finally {\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "try";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        StringBuffer buf= new StringBuffer();\n");
		buf.append("        buf.append(1);\n");
		buf.append("        buf.append(2);\n");
		buf.append("        buf.append(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testUnwrapAnonymous() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Runnable run= new Runnable() {\n");
		buf.append("            public void run() { \n");
		buf.append("                throw new NullPointerException();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "};";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        throw new NullPointerException();\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testUnwrapBlock() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        {\n");
		buf.append("            { \n");
		buf.append("                throw new NullPointerException();\n");
		buf.append("            }//comment\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "}//comment";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        {\n");
		buf.append("            throw new NullPointerException();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testUnwrapParanthesis() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return (9+ 8);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "(9+ 8)";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 9+ 8;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testUnwrapMethodInvocation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return Math.abs(9+ 8);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "Math.abs(9+ 8)";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str) + str.length(), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 9+ 8;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testSplitDeclaration1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i = 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "9";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        i = 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testSplitDeclaration2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i = 0; i < 9; i++) {\n");
		buf.append("       }\n");		
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "0";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        for (i = 0; i < 9; i++) {\n");
		buf.append("       }\n");		
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}
	
	public void testSplitDeclaration3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        final int i[] = null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "i[]";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		int index= (proposals.get(0) instanceof LinkedNamesAssistProposal) ? 1 : 0;
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(index);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        final int i[];\n");
		buf.append("        i = null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}	
	
	public void testJoinDeclaration1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int var[];\n");
		buf.append("        var = null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "var";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		int index= (proposals.get(0) instanceof LinkedNamesAssistProposal) ? 1 : 0;
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(index);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int var[] = null;\n");
		buf.append("    }\n");
		buf.append("}\n");		


		assertEqualString(preview, buf.toString());	
	}
	
	public void testJoinDeclaration2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int var;\n");
		buf.append("        for (var = 0; var < 9; var++) {\n");
		buf.append("       }\n");		
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		String str= "var";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectAssists(context, null, proposals);
		
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		int index= (proposals.get(0) instanceof LinkedNamesAssistProposal) ? 1 : 0;
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(index);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int var = 0; var < 9; var++) {\n");
		buf.append("       }\n");		
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());	
	}	
	
	
	
}
