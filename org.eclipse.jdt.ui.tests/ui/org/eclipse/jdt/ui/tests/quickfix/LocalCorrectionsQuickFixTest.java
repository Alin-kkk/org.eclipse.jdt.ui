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

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.corext.template.CodeTemplates;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.correction.CUCorrectionProposal;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionContext;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;

public class LocalCorrectionsQuickFixTest extends QuickFixTest {
	
	private static final Class THIS= LocalCorrectionsQuickFixTest.class;
	
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;


	public LocalCorrectionsQuickFixTest(String name) {
		super(name);
	}


	public static Test suite() {
		if (true) {
			return new TestSuite(THIS);
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new LocalCorrectionsQuickFixTest("testUncaughtExceptionToSurroundingTry"));
			return suite;
		}
	}


	protected void setUp() throws Exception {
		Hashtable options= JavaCore.getDefaultOptions();
		options.put(JavaCore.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(JavaCore.FORMATTER_TAB_SIZE, "4");
		options.put(JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER, JavaCore.ERROR);
		
		JavaCore.setOptions(options);			

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, false);
		
		CodeTemplates.getCodeTemplate(CodeTemplates.CATCHBLOCK).setPattern("");
		CodeTemplates.getCodeTemplate(CodeTemplates.CONSTRUCTORSTUB).setPattern("");
		CodeTemplates.getCodeTemplate(CodeTemplates.METHODSTUB).setPattern("");

		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		assertTrue("rt not found", JavaProjectHelper.addRTJar(fJProject1) != null);

		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, "src");
	}


	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJProject1);
	}

	
	public void testFieldAccessToStatic() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class E {\n");
		buf.append("    public char foo() {\n");
		buf.append("        return (new File(\"x.txt\")).separatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class E {\n");
		buf.append("    public char foo() {\n");
		buf.append("        return File.separatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testInheritedAccessOnStatic() throws Exception {
		IPackageFragment pack0= fSourceFolder.createPackageFragment("pack", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package pack;\n");
		buf.append("public class A {\n");
		buf.append("    public static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack0.createCompilationUnit("A.java", buf.toString(), false, null);		
		
		buf= new StringBuffer();
		buf.append("package pack;\n");
		buf.append("public class B extends A {\n");
		buf.append("}\n");
		pack0.createCompilationUnit("B.java", buf.toString(), false, null);		
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import pack.B;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(B b) {\n");
		buf.append("        b.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 3);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import pack.B;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(B b) {\n");
		buf.append("        B.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import pack.A;\n");		
		buf.append("import pack.B;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(B b) {\n");
		buf.append("        A.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(2);
		String preview3= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package pack;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();		

		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2, preview3 }, new String[] { expected1, expected2, expected3 });
	}	
	
	
	public void testQualifiedAccessToStatic() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Thread t) {\n");
		buf.append("        t.sleep(10);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Thread t) {\n");
		buf.append("        Thread.sleep(10);\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}	
	
	public void testThisAccessToStatic() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() {\n");
		buf.append("    }\n");				
		buf.append("    public void foo() {\n");
		buf.append("        this.goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() {\n");
		buf.append("    }\n");				
		buf.append("    public void foo() {\n");
		buf.append("        E.goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");				
		buf.append("    public void foo() {\n");
		buf.append("        this.goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}
	
	public void testThisAccessToStaticField() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static int fCount;\n");
		buf.append("\n");				
		buf.append("    public void foo() {\n");
		buf.append("        this.fCount= 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static int fCount;\n");
		buf.append("\n");				
		buf.append("    public void foo() {\n");
		buf.append("        E.fCount= 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int fCount;\n");
		buf.append("\n");				
		buf.append("    public void foo() {\n");
		buf.append("        this.fCount= 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
		
	}	
	
	public void testCastMissingInVarDecl() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        Thread th= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        Thread th= (Thread) o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        Object th= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}
	
	public void testCastMissingInVarDecl2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");		
		buf.append("public class Container {\n");
		buf.append("    public List[] getLists() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Container.java", buf.toString(), false, null);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");				
		buf.append("public class E {\n");
		buf.append("    public void foo(Container c) {\n");
		buf.append("         ArrayList[] lists= c.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");				
		buf.append("public class E {\n");
		buf.append("    public void foo(Container c) {\n");
		buf.append("         ArrayList[] lists= (ArrayList[]) c.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");					
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container c) {\n");
		buf.append("         List[] lists= c.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		

	}	
	
	
	public void testCastMissingInFieldDecl() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int time= System.currentTimeMillis();\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int time= (int) System.currentTimeMillis();\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    long time= System.currentTimeMillis();\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}	
	
	public void testCastMissingInAssignment() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");		
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        String str;\n");
		buf.append("        str= iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");		
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        String str;\n");
		buf.append("        str= (String) iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testCastMissingInExpression() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");		
		buf.append("public class E {\n");
		buf.append("    public String[] foo(List list) {\n");
		buf.append("        return list.toArray(new List[list.size()]);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public String[] foo(List list) {\n");
		buf.append("        return (String[]) list.toArray(new List[list.size()]);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public Object[] foo(List list) {\n");
		buf.append("        return list.toArray(new List[list.size()]);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();

		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });
	}
	
	public void testCastOnCastExpression() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(List list) {\n");
		buf.append("        ArrayList a= (Cloneable) list;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(List list) {\n");
		buf.append("        ArrayList a= (ArrayList) list;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(List list) {\n");
		buf.append("        Cloneable a= (Cloneable) list;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();

		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });
	}
	

	public void testUncaughtException() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");			
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");				
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
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");						
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}
	
	public void testUncaughtException2() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");		
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        goo().substring(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");			
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");				
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        goo().substring(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();
		 
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");			
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo().substring(2);\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");						
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}
	
	public void testUncaughtExceptionRemoveMoreSpecific() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.net.SocketException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() throws SocketException {\n");
		buf.append("        this.goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.net.SocketException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        this.goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();
		 
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.net.SocketException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() throws SocketException {\n");
		buf.append("        try {\n");		
		buf.append("            this.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");			
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}
	
	public void testUncaughtExceptionToSurroundingTry() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            E.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 3);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() throws ParseException {\n");
		buf.append("        try {\n");
		buf.append("            E.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();
		 
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            try {\n");
		buf.append("                E.goo();\n");
		buf.append("            } catch (ParseException e1) {\n");
		buf.append("            }\n");		
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(2);
		String preview3= proposal.getCompilationUnitChange().getPreviewContent();
		 
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");		
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            E.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("        }\n");	
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();		
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2, preview3 }, new String[] { expected1, expected2, expected3 });		
	}	
	
	public void testUncaughtExceptionOnSuper1() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.FileInputStream;\n");		
		buf.append("public class E extends FileInputStream {\n");
		buf.append("    public E() {\n");
		buf.append("        super(\"x\");\n");
		buf.append("    }\n");		
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.FileInputStream;\n");		
		buf.append("import java.io.FileNotFoundException;\n");	
		buf.append("public class E extends FileInputStream {\n");
		buf.append("    public E() throws FileNotFoundException {\n");
		buf.append("        super(\"x\");\n");
		buf.append("    }\n");		
		buf.append("}\n");
		
		assertEqualString(preview, buf.toString());
	}
	
	public void testUncaughtExceptionOnSuper2() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A() throws Exception {\n");
		buf.append("    }\n");		
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);		

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public E() {\n");
		buf.append("        super();\n");
		buf.append("    }\n");		
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public E() throws Exception {\n");
		buf.append("        super();\n");
		buf.append("    }\n");		
		buf.append("}\n");
		
		assertEqualString(preview, buf.toString());
	}	
	
	public void testUncaughtExceptionOnThis() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");		
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("        this(null);\n");		
		buf.append("    }\n");
		buf.append("    public E(Object x) throws IOException {\n");
		buf.append("    }\n");				
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");		
		buf.append("public class E {\n");
		buf.append("    public E() throws IOException {\n");
		buf.append("        this(null);\n");		
		buf.append("    }\n");
		buf.append("    public E(Object x) throws IOException {\n");
		buf.append("    }\n");				
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}	
	
	
	boolean BUG_25417= true;
	
	public void testUncaughtExceptionDuplicate() throws Exception {
		if (BUG_25417) {
			return;
		}

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class MyException extends Exception {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("MyException.java", buf.toString(), false, null);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void m1() throws IOException {\n");
		buf.append("        m2();\n");
		buf.append("    }\n");		
		buf.append("    public void m2() throws IOException, ParseException, MyException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 2);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void m1() throws IOException, ParseException, MyException {\n");
		buf.append("        m2();\n");
		buf.append("    }\n");		
		buf.append("    public void m2() throws IOException, ParseException, MyException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();
		 
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void m1() throws IOException {\n");
		buf.append("        try {\n");
		buf.append("            m2();\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("        } catch (MyException e) {\n");		
		buf.append("        }\n");
		buf.append("    }\n");		
		buf.append("    public void m2() throws IOException, ParseException, MyException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}	
	
	public void testMultipleUncaughtExceptions() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");						
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException, ParseException {\n");
		buf.append("    }\n");				
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 2); // 2 uncaught exceptions
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");						
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException, ParseException {\n");
		buf.append("    }\n");				
		buf.append("    public void foo() throws IOException, ParseException {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();
		 
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");		
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException, ParseException {\n");
		buf.append("    }\n");				
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } catch (ParseException e) {\n");		
		buf.append("        }\n");						
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}	
	
	public void testUnneededCatchBlock() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");				
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");			
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");				
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");			
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUnneededCatchBlockSingle() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");			
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");			
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUnneededCatchBlockWithFinally() throws Exception {
	
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");			
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");		
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);
		
	
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");			
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");		
		buf.append("            goo();\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");		
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUnimplementedMethods() throws Exception {
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public interface Inter {\n");
		buf.append("    int getCount(Object[] o) throws IOException;\n");
		buf.append("}\n");
		pack2.createCompilationUnit("Inter.java", buf.toString(), false, null);

		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.Inter;\n");		
		buf.append("public class E implements Inter{\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.Inter;\n");		
		buf.append("public abstract class E implements Inter{\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");		
		buf.append("import test2.Inter;\n");
		buf.append("public class E implements Inter{\n");
		buf.append("\n");
		buf.append("    public int getCount(Object[] o) throws IOException {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
		
	}
	
	public void testUnimplementedMethods2() throws Exception {
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public interface Inter {\n");
		buf.append("    int getCount(Object[] o) throws IOException;\n");
		buf.append("}\n");
		pack2.createCompilationUnit("Inter.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public abstract class InterImpl implements Inter {\n");
		buf.append("    protected abstract int[] getMusic() throws IOException;\n");		
		buf.append("}\n");
		pack2.createCompilationUnit("InterImpl.java", buf.toString(), false, null);		

		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.InterImpl;\n");		
		buf.append("public class E extends InterImpl {\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 2);
		
		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.InterImpl;\n");		
		buf.append("public abstract class E extends InterImpl {\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");		
		buf.append("import test2.InterImpl;\n");
		buf.append("public class E extends InterImpl {\n");
		buf.append("\n");
		buf.append("    protected int[] getMusic() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public int getCount(Object[] o) throws IOException {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });
	}		
	
	public void testUnitializedVariable() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int s;\n");
		buf.append("        try {\n");
		buf.append("            s= 1;\n");
		buf.append("        } catch (Exception e) {\n");
		buf.append("            System.out.println(s);\n");
		buf.append("        }\n");		
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int s = 0;\n");
		buf.append("        try {\n");
		buf.append("            s= 1;\n");
		buf.append("        } catch (Exception e) {\n");
		buf.append("            System.out.println(s);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}

	public void testUndefinedConstructorInDefaultConstructor1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public F(Runnable runnable) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("F.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(Runnable runnable) {\n");
		buf.append("        super(runnable);\n");		
		buf.append("    }\n");		
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUndefinedConstructorInDefaultConstructor2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class F {\n");
		buf.append("    public F(Runnable runnable) throws IOException {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public F(int i, Runnable runnable) {\n");
		buf.append("    }\n");		
		buf.append("}\n");
		pack1.createCompilationUnit("F.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(int i, Runnable runnable) {\n");
		buf.append("        super(i, runnable);\n");		
		buf.append("    }\n");		
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(Runnable runnable) throws IOException {\n");
		buf.append("        super(runnable);\n");				
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });
	}
	
	public void testNotVisibleConstructorInDefaultConstructor() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    private F() {\n");
		buf.append("    }\n");
		buf.append("    public F(Runnable runnable) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("F.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(Runnable runnable) {\n");
		buf.append("        super(runnable);\n");		
		buf.append("    }\n");		
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUnhandledExceptionInDefaultConstructor() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class F {\n");
		buf.append("    public F() throws IOException{\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("F.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E() throws IOException {\n");
		buf.append("        super();\n");
		buf.append("    }\n");		
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUnusedPrivateField() throws Exception {
		Hashtable hashtable= JavaCore.getOptions();
		hashtable.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		JavaCore.setOptions(hashtable);
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int fCount;\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}	

	public void testUnusedPrivateField1() throws Exception {
		Hashtable hashtable= JavaCore.getOptions();
		hashtable.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		JavaCore.setOptions(hashtable);
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int fCount, fColor= fCount;\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int fCount;\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}	

	
	public void testUnusedPrivateMethod() throws Exception {
		Hashtable hashtable= JavaCore.getOptions();
		hashtable.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		JavaCore.setOptions(hashtable);
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int fCount;\n");
		buf.append(" \n");
		buf.append("    private void foo() {\n");
		buf.append("        fCount= 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int fCount;\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUnusedPrivateConstructor() throws Exception {
		Hashtable hashtable= JavaCore.getOptions();
		hashtable.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		JavaCore.setOptions(hashtable);
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append(" \n");
		buf.append("    private E(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testUnusedPrivateType() throws Exception {
		Hashtable hashtable= JavaCore.getOptions();
		hashtable.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		JavaCore.setOptions(hashtable);
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private class F {\n");
		buf.append("    }\n");
		buf.append(" \n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);

		CorrectionContext context= getCorrectionContext(cu, problems[0]);
		assertCorrectContext(context);
		ArrayList proposals= new ArrayList();

		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 1);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}		
	
}
