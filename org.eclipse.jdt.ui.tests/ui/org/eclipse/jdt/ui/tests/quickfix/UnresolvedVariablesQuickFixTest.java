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
import org.eclipse.jdt.internal.ui.text.correction.NewCUCompletionUsingWizardProposal;
import org.eclipse.jdt.internal.ui.text.correction.NewVariableCompletionProposal;

public class UnresolvedVariablesQuickFixTest extends QuickFixTest {
	
	private static final Class THIS= UnresolvedVariablesQuickFixTest.class;
	
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	public UnresolvedVariablesQuickFixTest(String name) {
		super(name);
	}


	public static Test suite() {
		if (true) {
			return new TestSuite(THIS);
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new UnresolvedVariablesQuickFixTest("testVarInAnonymous"));
			return suite;
		}
	}


	protected void setUp() throws Exception {
		Hashtable options= JavaCore.getDefaultOptions();
		options.put(JavaCore.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(JavaCore.FORMATTER_TAB_SIZE, "4");
		options.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.IGNORE);		
		JavaCore.setOptions(options);			

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, false);
		
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		assertTrue("rt not found", JavaProjectHelper.addRTJar(fJProject1) != null);
		
		CodeTemplates.getCodeTemplate(CodeTemplates.NEWTYPE).setPattern("");
		CodeTemplates.getCodeTemplate(CodeTemplates.TYPECOMMENT).setPattern("");
		
		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, "src");
	}


	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJProject1);
	}

	
	public void testVarInAssignment() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        iter= vec.iterator();\n");
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
		
		boolean doField= true, doParam= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			NewVariableCompletionProposal proposal= (NewVariableCompletionProposal) proposals.get(i);
			String preview= proposal.getCompilationUnitChange().getPreviewContent();

			if (proposal.getVariableKind() == NewVariableCompletionProposal.FIELD) {
				assertTrue("2 field proposals", doField);
				doField= false;
				
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.util.Iterator;\n");
				buf.append("import java.util.Vector;\n");
				buf.append("public class E {\n");
				buf.append("    private Iterator iter;\n");
				buf.append("\n");
				buf.append("    void foo(Vector vec) {\n");
				buf.append("        iter= vec.iterator();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			} else if (proposal.getVariableKind() == NewVariableCompletionProposal.LOCAL) {
				assertTrue("2 local proposals", doLocal);
				doLocal= false;
				
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.util.Iterator;\n");
				buf.append("import java.util.Vector;\n");
				buf.append("public class E {\n");
				buf.append("    void foo(Vector vec) {\n");
				buf.append("        Iterator iter = vec.iterator();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			} else if (proposal.getVariableKind() == NewVariableCompletionProposal.PARAM) {
				assertTrue("2 param proposals", doParam);
				doParam= false;
				
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.util.Iterator;\n");
				buf.append("import java.util.Vector;\n");
				buf.append("public class E {\n");
				buf.append("    void foo(Vector vec, Iterator iter) {\n");
				buf.append("        iter= vec.iterator();\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			} else {
				assertTrue("unknown type", false);
			}
		}
	}
	
	public void testVarInForInitializer() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (i= 0;;) {\n");
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
		
		boolean doField= true, doParam= true, doLocal= true;
		for (int i= 0; i < proposals.size(); i++) {
			NewVariableCompletionProposal proposal= (NewVariableCompletionProposal) proposals.get(i);
			String preview= proposal.getCompilationUnitChange().getPreviewContent();

			if (proposal.getVariableKind() == NewVariableCompletionProposal.FIELD) {
				assertTrue("2 field proposals", doField);
				doField= false;
				
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("    private int i;\n");
				buf.append("\n");
				buf.append("    void foo() {\n");
				buf.append("        for (i= 0;;) {\n");
				buf.append("        }\n");		
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			} else if (proposal.getVariableKind() == NewVariableCompletionProposal.LOCAL) {
				assertTrue("2 local proposals", doLocal);
				doLocal= false;
				
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("    void foo() {\n");
				buf.append("        for (int i = 0;;) {\n");
				buf.append("        }\n");		
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			} else if (proposal.getVariableKind() == NewVariableCompletionProposal.PARAM) {
				assertTrue("2 param proposals", doParam);
				doParam= false;
				
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("    void foo(int i) {\n");
				buf.append("        for (i= 0;;) {\n");
				buf.append("        }\n");		
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			} else {
				assertTrue("unknown type", false);
			}
		}
	}	
	
	
	public void testVarInInitializer() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int i= k;\n");
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
		
		NewVariableCompletionProposal proposal= (NewVariableCompletionProposal) proposals.get(0);
		String preview= proposal.getCompilationUnitChange().getPreviewContent();

		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int k;\n");
		buf.append("\n");
		buf.append("    private int i= k;\n");
		buf.append("}\n");
		assertEqualString(preview, buf.toString());
	}
	
	public void testVarInOtherType() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var2= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("F.java", buf.toString(), false, null);		
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int var1;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu1, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu1, problems[0]);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);

		boolean doNew= true, doChange= true;
		for (int i= 0; i < proposals.size(); i++) {
			Object curr= proposals.get(i);
			if (curr instanceof NewVariableCompletionProposal) {
				assertTrue("2 new proposals", doNew);
				doNew= false;
				NewVariableCompletionProposal proposal= (NewVariableCompletionProposal) curr;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
	
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class E {\n");
				buf.append("    public int var2;\n");
				buf.append("\n");				
				buf.append("    private int var1;\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			} else if (curr instanceof CUCorrectionProposal) {
				assertTrue("2 replace proposals", doChange);
				doChange= false;
				CUCorrectionProposal proposal= (CUCorrectionProposal) curr;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
	
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("public class F {\n");
				buf.append("    void foo(E e) {\n");
				buf.append("         e.var1= 2;\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			}
		}
	}
	
	public void testVarInSuperFieldAccess() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         super.var2= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("F.java", buf.toString(), false, null);		
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int var1;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu1, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu1, problems[0]);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();
	
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         super.var1= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();
	
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int var2;\n");
		buf.append("\n");
		buf.append("    protected int var1;\n");
		buf.append("}\n");
		String expected2= buf.toString();

		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}
	
	public void testVarInSuper() throws Exception {
		StringBuffer buf= new StringBuffer();

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test3.E;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         this.color= baz();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("F.java", buf.toString(), false, null);		

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		pack2.createCompilationUnit("E.java", buf.toString(), false, null);
		
		IPackageFragment pack3= fSourceFolder.createPackageFragment("test3", false, null);
		buf= new StringBuffer();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    protected Object olor;\n");
		buf.append("    public test2.E baz() {\n");
		buf.append("        return null;\n");		
		buf.append("    }\n");				
		buf.append("}\n");
		pack3.createCompilationUnit("E.java", buf.toString(), false, null);		
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu1, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu1, problems[0]);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();
	
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test3.E;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         this.olor= baz();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();
	
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test3.E;\n");
		buf.append("public class F extends E {\n");
		buf.append("    private test2.E color;\n");
		buf.append("\n");
		buf.append("    void foo() {\n");
		buf.append("         this.color= baz();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();

		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2 }, new String[] { expected1, expected2 });		
	}	
	
	
	public void testVarInAnonymous() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
				
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu1, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu1, problems[0]);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 5);
		assertCorrectLabels(proposals);
		
		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            private int fCount;\n");
		buf.append("\n");
		buf.append("            public void run() {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int fCount;\n");
		buf.append("\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(2);
		String preview3= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                int fCount = 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(3);
		String preview4= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run(int fCount) {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected4= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(4);
		String preview5= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                fcount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected5= buf.toString();			
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2, preview3, preview4, preview5 }, new String[] { expected1, expected2, expected3, expected4, expected5 });		
	}
	
	public void testLongVarRef() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public int mash;\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var.hash= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("F.java", buf.toString(), false, null);		
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public F var;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot= AST.parseCompilationUnit(cu1, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu1, problems[0]);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 2);
		assertCorrectLabels(proposals);

		CUCorrectionProposal proposal= (CUCorrectionProposal) proposals.get(0);
		String preview1= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    private int hash;\n");
		buf.append("    public int mash;\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var.hash= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		proposal= (CUCorrectionProposal) proposals.get(1);
		String preview2= proposal.getCompilationUnitChange().getPreviewContent();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public int mash;\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var.mash= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();			
		
		assertEqualStringsIgnoreOrder(new String[] { preview1, preview2}, new String[] { expected1, expected2});		
	}	

	public void testVarAndTypeRef() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class F {\n");
		buf.append("    void foo() {\n");
		buf.append("        char ch= Fixe.pathSeparatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("F.java", buf.toString(), false, null);		

		CompilationUnit astRoot= AST.parseCompilationUnit(cu1, true);
		IProblem[] problems= astRoot.getProblems();
		assertNumberOf("problems", problems.length, 1);
		
		CorrectionContext context= getCorrectionContext(cu1, problems[0]);
		ArrayList proposals= new ArrayList();
		
		JavaCorrectionProcessor.collectCorrections(context,  proposals);
		assertNumberOf("proposals", proposals.size(), 6);
		assertCorrectLabels(proposals);

		boolean doField= true, doParam= true, doLocal= true, doInterface= true, doClass= true, doChange= true;
		for (int i= 0; i < proposals.size(); i++) {
			Object curr= proposals.get(i);
			if (curr instanceof NewVariableCompletionProposal) {
				NewVariableCompletionProposal proposal= (NewVariableCompletionProposal) proposals.get(i);
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
	
				if (proposal.getVariableKind() == NewVariableCompletionProposal.FIELD) {
					assertTrue("2 field proposals", doField);
					doField= false;
					
					buf= new StringBuffer();
					buf.append("package test1;\n");
					buf.append("import java.io.File;\n");
					buf.append("public class F {\n");
					buf.append("    private Object Fixe;\n");
					buf.append("\n");
					buf.append("    void foo() {\n");
					buf.append("        char ch= Fixe.pathSeparatorChar;\n");
					buf.append("    }\n");
					buf.append("}\n");
					assertEqualString(preview, buf.toString());
				} else if (proposal.getVariableKind() == NewVariableCompletionProposal.LOCAL) {
					assertTrue("2 local proposals", doLocal);
					doLocal= false;
					
					buf= new StringBuffer();
					buf.append("package test1;\n");
					buf.append("import java.io.File;\n");
					buf.append("public class F {\n");
					buf.append("    void foo() {\n");
					buf.append("        Object Fixe = null;\n");					
					buf.append("        char ch= Fixe.pathSeparatorChar;\n");
					buf.append("    }\n");
					buf.append("}\n");
					assertEqualString(preview, buf.toString());
				} else if (proposal.getVariableKind() == NewVariableCompletionProposal.PARAM) {
					assertTrue("2 param proposals", doParam);
					doParam= false;
					
					buf= new StringBuffer();
					buf.append("package test1;\n");
					buf.append("import java.io.File;\n");
					buf.append("public class F {\n");
					buf.append("    void foo(Object Fixe) {\n");
					buf.append("        char ch= Fixe.pathSeparatorChar;\n");
					buf.append("    }\n");
					buf.append("}\n");
					assertEqualString(preview, buf.toString());
				} else {
					assertTrue("unknown type", false);
				}
			} else if (curr instanceof NewCUCompletionUsingWizardProposal) {
				NewCUCompletionUsingWizardProposal proposal= (NewCUCompletionUsingWizardProposal) curr;
				proposal.setShowDialog(false);
				proposal.apply(null);
				
				ICompilationUnit newCU= pack1.getCompilationUnit("Fixe.java");
				assertTrue("Nothing created", newCU.exists());

				if (proposal.isClass()) {
					assertTrue("2 class proposals", doClass);
					doClass= false;

					buf= new StringBuffer();
					buf.append("package test1;\n");
					buf.append("\n");
					buf.append("public class Fixe {\n");
					buf.append("\n");
					buf.append("}\n");
					assertEqualStringIgnoreDelim(newCU.getSource(), buf.toString());
					JavaProjectHelper.performDummySearch();
					newCU.delete(true, null);
				} else {
					assertTrue("2 interface proposals", doInterface);
					doInterface= false;					
					
					buf= new StringBuffer();
					buf.append("package test1;\n");
					buf.append("\n");
					buf.append("public interface Fixe {\n");
					buf.append("\n");
					buf.append("}\n");
					assertEqualStringIgnoreDelim(newCU.getSource(), buf.toString());
					JavaProjectHelper.performDummySearch();
					newCU.delete(true, null);
				}
			} else {
				assertTrue("2 replace proposals", doChange);
				doChange= false;
				CUCorrectionProposal proposal= (CUCorrectionProposal) curr;
				String preview= proposal.getCompilationUnitChange().getPreviewContent();
	
				buf= new StringBuffer();
				buf.append("package test1;\n");
				buf.append("import java.io.File;\n");
				buf.append("public class F {\n");
				buf.append("    void foo() {\n");
				buf.append("        char ch= File.pathSeparatorChar;\n");
				buf.append("    }\n");
				buf.append("}\n");
				assertEqualString(preview, buf.toString());
			}
		}		
	
	}


}
