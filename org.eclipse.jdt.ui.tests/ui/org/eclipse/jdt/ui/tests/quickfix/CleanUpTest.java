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
package org.eclipse.jdt.ui.tests.quickfix;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Preferences;

import org.eclipse.core.resources.IncrementalProjectBuilder;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.corext.fix.IFix;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.CodeStyleCleanUp;
import org.eclipse.jdt.internal.ui.fix.ControlStatementsCleanUp;
import org.eclipse.jdt.internal.ui.fix.ICleanUp;
import org.eclipse.jdt.internal.ui.fix.Java50CleanUp;
import org.eclipse.jdt.internal.ui.fix.PotentialProgrammingProblemsCleanUp;
import org.eclipse.jdt.internal.ui.fix.StringCleanUp;
import org.eclipse.jdt.internal.ui.fix.UnusedCodeCleanUp;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.jdt.ui.tests.core.ProjectTestSetup;

public class CleanUpTest extends QuickFixTest {
	
	private static final String FIELD_COMMENT= "/* Test */";
	
	private static final Class THIS= CleanUpTest.class;
	
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	public CleanUpTest(String name) {
		super(name);
	}
	
	public static Test allTests() {
		return new ProjectTestSetup(new TestSuite(THIS));
	}
	
	public static Test suite() {
		if (true)
			return allTests();
		
		return setUpTest(new CleanUpTest("testRemoveBlock05"));
	}

	public static Test setUpTest(Test test) {
		return new ProjectTestSetup(test);
	}
	
	protected void setUp() throws Exception {
		Hashtable options= TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		
		
		JavaCore.setOptions(options);			

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, false);
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, false);
		
		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODSTUB_ID, "//TODO\n${body_statement}", null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.FIELDCOMMENT_ID, FIELD_COMMENT, null);
		
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
	
	protected static void assertNumberOfChanges(CompilationUnitChange[] changes, int expectedChanges) {
		if (changes.length != expectedChanges) {
			StringBuffer buf= new StringBuffer();
			buf.append("Wrong number of changes, is: ").append(changes.length). append(", expected: ").append(expectedChanges).append('\n');
			for (int i= 0; i < changes.length; i++) {
				CompilationUnitChange curr= changes[i];
				buf.append(" - ").append(curr.getName()).append(" in ").append(curr.getCompilationUnit().getElementName()).append('\n');
			}
			assertTrue(buf.toString(), false);
		}
	}
	
	public static void assertNumberOfProblems(CompilationUnit unit, int expected) {
		IProblem[] problems= unit.getProblems();
		if (problems.length != expected) {
			StringBuffer buf= new StringBuffer();
			buf.append("Wrong number of problems, is: ").append(problems.length). append(", expected: ").append(expected).append('\n');
			for (int i= 0; i < problems.length; i++) {
				buf.append(" - ").append(problems[i].getMessage()).append('\n');
			}
			assertTrue(buf.toString(), false);
		}
	}
	
	private CompilationUnit[] compile(ICompilationUnit[] units) {
		CompilationUnit[] result= new CompilationUnit[units.length];
		for (int i= 0; i < units.length; i++) {
			result[i]= compile(units[i]);
		}
		return result;
	}
	
	private CompilationUnit compile(ICompilationUnit compilationUnit) {
		CompilationUnit result= JavaPlugin.getDefault().getASTProvider().getAST(compilationUnit, ASTProvider.WAIT_YES, null);
		if (result == null) {
			// see bug 63554
			result= ASTResolving.createQuickFixAST(compilationUnit, null);
		}
		return result;
	}
	
	private void setOptions(ICleanUp fix) {
		setOptions(new ICleanUp[] {fix});
	}
	
	private void setOptions(ICleanUp[] fixes) {
		Hashtable options= JavaCore.getOptions();
		for (Iterator iter= options.keySet().iterator(); iter.hasNext();) {
			String key= (String)iter.next();
			String value= (String)options.get(key);
			if ("error".equals(value) || "warning".equals(value)) {  
				options.put(key, "ignore"); 
			}
		}
		
		for (int i= 0; i < fixes.length; i++) {
			ICleanUp fix= fixes[i];
			Map fixOptions= fix.getRequiredOptions();
			if (fixOptions != null) {
				options.putAll(fixOptions);
			}
		}

		JavaCore.setOptions(options);
	}
	
	private void assertRefactoringResultAsExpected(CleanUpRefactoring refactoring, String[] expected) throws CoreException {
		refactoring.checkAllConditions(new NullProgressMonitor());
		CompositeChange change= (CompositeChange)refactoring.createChange(null);
		Change[] children= change.getChildren();
		String[] previews= new String[children.length]; 
		for (int i= 0; i < children.length; i++) {
			previews[i]= ((TextEditBasedChange)children[i]).getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, expected);
	}
	
	private void assertRefactoringHasNoChange(CleanUpRefactoring refactoring) throws CoreException {
		refactoring.checkAllConditions(new NullProgressMonitor());
		CompositeChange change= (CompositeChange)refactoring.createChange(null);
		Change[] children= change.getChildren();
		StringBuffer buf= new StringBuffer();
		buf.append("Refactoring should generate no changes but does change:\n");
		for (int i= 0; i < children.length; i++) {
			buf.append(((TextChange)children[i]).getPreviewContent(null));
		}
		
		assertTrue(buf.toString(), change.getChildren().length == 0);
	}
	
	public void testAddNLSTag01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s= \"\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public String s1 = \"\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s2 = \"\";\n");
		buf.append("        String s3 = s2 + \"\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static final String s= \"\";\n");
		buf.append("    public static String bar(String s1, String s2) {\n");
		buf.append("        bar(\"\", \"\");\n");
		buf.append("        return \"\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new StringCleanUp(StringCleanUp.ADD_MISSING_NLS_TAG);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2, cu3});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 3);
		assertNumberOfProblems(units[2], 4);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s= \"\"; //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public String s1 = \"\"; //$NON-NLS-1$\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s2 = \"\"; //$NON-NLS-1$\n");
		buf.append("        String s3 = s2 + \"\"; //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static final String s= \"\"; //$NON-NLS-1$\n");
		buf.append("    public static String bar(String s1, String s2) {\n");
		buf.append("        bar(\"\", \"\"); //$NON-NLS-1$ //$NON-NLS-2$\n");
		buf.append("        return \"\"; //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();
		
		int numberOfFixes= 3;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2, expected3});	
	}
	
	public void testRemoveNLSTag01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s= null; //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public String s1 = null; //$NON-NLS-1$\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s2 = null; //$NON-NLS-1$\n");
		buf.append("        String s3 = s2 + s2; //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static final String s= null; //$NON-NLS-1$\n");
		buf.append("    public static String bar(String s1, String s2) {\n");
		buf.append("        bar(s2, s1); //$NON-NLS-1$ //$NON-NLS-2$\n");
		buf.append("        return s1; //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);

		ICleanUp cleanUp= new StringCleanUp(StringCleanUp.REMOVE_UNNECESSARY_NLS_TAG);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2, cu3});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 3);
		assertNumberOfProblems(units[2], 4);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s= null; \n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public String s1 = null; \n");
		buf.append("    public void foo() {\n");
		buf.append("        String s2 = null; \n");
		buf.append("        String s3 = s2 + s2; \n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static final String s= null; \n");
		buf.append("    public static String bar(String s1, String s2) {\n");
		buf.append("        bar(s2, s1); \n");
		buf.append("        return s1; \n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();

		int numberOfFixes= 3;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2, expected3});	
	}

	public void testUnusedCode01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E2 {\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import test1.E2;\n");
		buf.append("import java.io.StringReader;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_IMPORTS);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2, cu3});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		assertNumberOfProblems(units[2], 3);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("}\n");
		String expected3= buf.toString();

		int numberOfFixes= 3;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2, expected3});	
	}
	
	public void testUnusedCode02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private void foo() {}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private void foo() {}\n");
		buf.append("    private void bar() {}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    private class E3Inner {\n");
		buf.append("        private void foo() {}\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        Runnable r= new Runnable() {\n");
		buf.append("            public void run() {}\n");
		buf.append("            private void foo() {};\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_METHODS);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2, cu3});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		assertNumberOfProblems(units[2], 3);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    private class E3Inner {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        Runnable r= new Runnable() {\n");
		buf.append("            public void run() {};\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();

		int numberOfFixes= 3;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2, expected3});	
	}
	
	public void testUnusedCode03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private E1(int i) {}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public E2() {}\n");
		buf.append("    private E2(int i) {}\n");
		buf.append("    private E2(String s) {}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    public class E3Inner {\n");
		buf.append("        private E3Inner(int i) {}\n");
		buf.append("    }\n");
		buf.append("    private void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_CONSTRUCTORS);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2, cu3});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		assertNumberOfProblems(units[2], 2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public E2() {}\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    public class E3Inner {\n");
		buf.append("    }\n");
		buf.append("    private void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();

		int numberOfFixes= 3;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2, expected3});	
	}
	
	public void testUnusedCode04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int i;\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private int i= 10;\n");
		buf.append("    private int j= 10;\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    private int i;\n");
		buf.append("    private int j;\n");
		buf.append("    private void foo() {\n");
		buf.append("        i= 10;\n");
		buf.append("        i= 20;\n");
		buf.append("        i= j;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_FIELDS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    private int j;\n");
		buf.append("    private void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2, expected3});
	}
	
	public void testUnusedCode05() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private class E1Inner{}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private class E2Inner1 {}\n");
		buf.append("    private class E2Inner2 {}\n");
		buf.append("    public class E2Inner3 {}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    public class E3Inner {\n");
		buf.append("        private class E3InnerInner {}\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_TYPES);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2, cu3});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		assertNumberOfProblems(units[2], 1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public class E2Inner3 {}\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    public class E3Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();

		int numberOfFixes= 3;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2, expected3});	
	}
	
	public void testUnusedCode06() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        int j= 10;\n");
		buf.append("    }\n");
		buf.append("    private void bar() {\n");
		buf.append("        int i= 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    public class E3Inner {\n");
		buf.append("        public void foo() {\n");
		buf.append("            int i= 10;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        int j= i;\n");
		buf.append("        j= 10;\n");
		buf.append("        j= 20;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_LOCAL_VARIABLES);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("    private void bar() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    public class E3Inner {\n");
		buf.append("        public void foo() {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2, expected3});
	}
	
	public void testUnusedCode07() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= bar();\n");
		buf.append("        int j= 1;\n");
		buf.append("    }\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_LOCAL_VARIABLES);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= bar();\n");
		buf.append("    }\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testUnusedCode08() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int i= bar();\n");
		buf.append("    private int j= 1;\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_FIELDS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int i= bar();\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testUnusedCode09() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 1;\n");
		buf.append("        i= bar();\n");
		buf.append("        int j= 1;\n");
		buf.append("        j= 1;\n");
		buf.append("    }\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_LOCAL_VARIABLES);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 1;\n");
		buf.append("        i= bar();\n");
		buf.append("    }\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testUnusedCode10() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int i= 1;\n");
		buf.append("    private int j= 1;\n");
		buf.append("    public void foo() {\n");
		buf.append("        i= bar();\n");
		buf.append("        j= 1;\n");
		buf.append("    }\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_FIELDS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int i= 1;\n");
		buf.append("    public void foo() {\n");
		buf.append("        i= bar();\n");
		buf.append("    }\n");
		buf.append("    public int bar() {return 1;}\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testUnusedCode11() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1  {\n");
		buf.append("    private void foo(String s) {\n");
		buf.append("        String s1= (String)s;\n");
		buf.append("        Object o= (Object)new Object();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    String s1;\n");
		buf.append("    String s2= (String)s1;\n");
		buf.append("    public void foo(Integer i) {\n");
		buf.append("        Number n= (Number)i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_CAST);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1  {\n");
		buf.append("    private void foo(String s) {\n");
		buf.append("        String s1= s;\n");
		buf.append("        Object o= new Object();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    String s1;\n");
		buf.append("    String s2= s1;\n");
		buf.append("    public void foo(Integer i) {\n");
		buf.append("        Number n= i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2});
	}
	
	public void testUnusedCodeBug123766() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1  {\n");
		buf.append("    private int i,j;\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s1,s2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_FIELDS | UnusedCodeCleanUp.REMOVE_UNUSED_LOCAL_VARIABLES);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1  {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava5001() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private int field= 1;\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private int field1= 1;\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private int field2= 2;\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new Java50CleanUp(Java50CleanUp.ADD_DEPRECATED_ANNOTATION);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private int field= 1;\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private int field1= 1;\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private int field2= 2;\n");
		buf.append("}\n");
		String expected2= buf.toString();

		int numberOfFixes= 2;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2});	
	}
	
	public void testJava5002() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private int f() {return 1;}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private int f1() {return 1;}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private int f2() {return 2;}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new Java50CleanUp(Java50CleanUp.ADD_DEPRECATED_ANNOTATION);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private int f() {return 1;}\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private int f1() {return 1;}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private int f2() {return 2;}\n");
		buf.append("}\n");
		String expected2= buf.toString();

		int numberOfFixes= 2;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2});	
	}
	
	public void testJava5003() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @deprecated\n");
		buf.append(" */\n");
		buf.append("public class E1 {\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private class E2Sub1 {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    private class E2Sub2 {}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new Java50CleanUp(Java50CleanUp.ADD_DEPRECATED_ANNOTATION);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @deprecated\n");
		buf.append(" */\n");
		buf.append("@Deprecated\n");
		buf.append("public class E1 {\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private class E2Sub1 {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    private class E2Sub2 {}\n");
		buf.append("}\n");
		String expected2= buf.toString();

		int numberOfFixes= 2;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2});
	}
	
	public void testJava5004() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    private void foo3() {}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    protected void foo3() {}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    public void foo3() {}\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new Java50CleanUp(Java50CleanUp.ADD_OVERRIDE_ANNOATION);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    @Override\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    @Override\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    protected void foo3() {}\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    @Override\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    @Override\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    @Override\n");
		buf.append("    public void foo3() {}\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2});
	}
	
	public void testJava5005() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    private void foo3() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    public int i;\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    protected void foo3() {}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    public void foo3() {}\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new Java50CleanUp(Java50CleanUp.ADD_OVERRIDE_ANNOATION | Java50CleanUp.ADD_DEPRECATED_ANNOTATION);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    private void foo3() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    public int i;\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    @Override\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    @Override\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    protected void foo3() {}\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    @Override\n");
		buf.append("    public void foo1() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    @Override\n");
		buf.append("    protected void foo2() {}\n");
		buf.append("    /**\n");
		buf.append("     * @deprecated\n");
		buf.append("     */\n");
		buf.append("    @Deprecated\n");
		buf.append("    @Override\n");
		buf.append("    public void foo3() {}\n");
		buf.append("}\n");
		String expected3= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2, expected3});
	}
	
	public void testCodeStyle01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    String s = \"\"; //$NON-NLS-1$\n");
		buf.append("    String t = \"\";  //$NON-NLS-1$\n");
		buf.append("    \n");
		buf.append("    public void foo() {\n");
		buf.append("        s = \"\"; //$NON-NLS-1$\n");
		buf.append("        s = s + s;\n");
		buf.append("        s = t + s;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    int i = 10;\n");
		buf.append("    \n");
		buf.append("    public class E2Inner {\n");
		buf.append("        public void bar() {\n");
		buf.append("            int j = i;\n");
		buf.append("            String k = s + t;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    public void fooBar() {\n");
		buf.append("        String k = s;\n");
		buf.append("        int j = i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    String s = \"\"; //$NON-NLS-1$\n");
		buf.append("    String t = \"\";  //$NON-NLS-1$\n");
		buf.append("    \n");
		buf.append("    public void foo() {\n");
		buf.append("        this.s = \"\"; //$NON-NLS-1$\n");
		buf.append("        this.s = this.s + this.s;\n");
		buf.append("        this.s = this.t + this.s;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1{\n");
		buf.append("    int i = 10;\n");
		buf.append("    \n");
		buf.append("    public class E2Inner {\n");
		buf.append("        public void bar() {\n");
		buf.append("            int j = E2.this.i;\n");
		buf.append("            String k = E2.this.s + E2.this.t;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    public void fooBar() {\n");
		buf.append("        String k = this.s;\n");
		buf.append("        int j = this.i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2});
	}
	
	public void testCodeStyle02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static int i= 0;\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private E1 e1;\n");
		buf.append("    \n");
		buf.append("    public void foo() {\n");
		buf.append("        e1= new E1();\n");
		buf.append("        int j= e1.i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private E1 e1;\n");
		buf.append("    \n");
		buf.append("    public void foo() {\n");
		buf.append("        this.e1= new E1();\n");
		buf.append("        int j= E1.i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static int f;\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= this.f;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public static String s = \"\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(this.s);\n");
		buf.append("        E1 e1= new E1();\n");
		buf.append("        int i= e1.f;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static int g;\n");
		buf.append("    {\n");
		buf.append("        this.g= (new E1()).f;\n");
		buf.append("    }\n");
		buf.append("    public static int f= E1.f;\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1, cu2, cu3});
		assertNumberOfProblems(units[0], 1);
		assertNumberOfProblems(units[1], 2);
		assertNumberOfProblems(units[2], 2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static int f;\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= E1.f;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public static String s = \"\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E2.s);\n");
		buf.append("        E1 e1= new E1();\n");
		buf.append("        int i= E1.f;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static int g;\n");
		buf.append("    {\n");
		buf.append("        E3.g= E1.f;\n");
		buf.append("    }\n");
		buf.append("    public static int f= E1.f;\n");
		buf.append("}\n");
		String expected3= buf.toString();

		int numberOfFixes= 3;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1, expected2, expected3});
	}
	
	public void testCodeStyle04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static int f() {return 1;}\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= this.f();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public static String s() {return \"\";}\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(this.s());\n");
		buf.append("        E1 e1= new E1();\n");
		buf.append("        int i= e1.f();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static int g;\n");
		buf.append("    {\n");
		buf.append("        this.g= (new E1()).f();\n");
		buf.append("    }\n");
		buf.append("    public static int f= E1.f();\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static int f() {return 1;}\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= E1.f();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public static String s() {return \"\";}\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E2.s());\n");
		buf.append("        E1 e1= new E1();\n");
		buf.append("        int i= E1.f();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public static int g;\n");
		buf.append("    {\n");
		buf.append("        E3.g= E1.f();\n");
		buf.append("    }\n");
		buf.append("    public static int f= E1.f();\n");
		buf.append("}\n");
		String expected3= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2, expected3});

	}
	
	public void testCodeStyle05() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public String s= \"\";\n");
		buf.append("    public E2 e2;\n");
		buf.append("    public static int i= 10;\n");
		buf.append("    public void foo() {}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    public int i = 10;\n");
		buf.append("    public E1 e1;\n");
		buf.append("    public void fooBar() {}\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 {\n");
		buf.append("    private E1 e1;    \n");
		buf.append("    public void foo() {\n");
		buf.append("        e1= new E1();\n");
		buf.append("        int j= e1.i;\n");
		buf.append("        String s= e1.s;\n");
		buf.append("        e1.foo();\n");
		buf.append("        e1.e2.fooBar();\n");
		buf.append("        int k= e1.e2.e2.e2.i;\n");
		buf.append("        int h= e1.e2.e2.e1.e2.e1.i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 {\n");
		buf.append("    private E1 e1;    \n");
		buf.append("    public void foo() {\n");
		buf.append("        this.e1= new E1();\n");
		buf.append("        int j= E1.i;\n");
		buf.append("        String s= this.e1.s;\n");
		buf.append("        this.e1.foo();\n");
		buf.append("        this.e1.e2.fooBar();\n");
		buf.append("        int k= this.e1.e2.e2.e2.i;\n");
		buf.append("        int h= E1.i;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		String[] expected= new String[] {expected1};
		
		assertRefactoringResultAsExpected(refactoring, expected);
	}
	
	public void testCodeStyle06() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public String s= \"\";\n");
		buf.append("    public E1 create() {\n");
		buf.append("        return new E1();\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        create().s= \"\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testCodeStyle07() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static int i = 10;\n");
		buf.append("    private static int j = i + 10 * i;\n");
		buf.append("    public void foo() {\n");
		buf.append("        String s= i + \"\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testCodeStyle08() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public final static int i = 1;\n");
		buf.append("    public final int j = 2;\n");
		buf.append("    private final int k = 3;\n");
		buf.append("    public void foo() {\n");
		buf.append("        switch (3) {\n");
		buf.append("        case i: break;\n");
		buf.append("        case j: break;\n");
		buf.append("        case k: break;\n");
		buf.append("        default: break;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testCodeStyle09() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public abstract class E1Inner1 {\n");
		buf.append("        protected int n;\n");
		buf.append("        public abstract void foo();\n");
		buf.append("    }\n");
		buf.append("    public abstract class E1Inner2 {\n");
		buf.append("        public abstract void run();\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        E1Inner1 inner= new E1Inner1() {\n");
		buf.append("            public void foo() {\n");
		buf.append("                E1Inner2 inner2= new E1Inner2() {\n");
		buf.append("                    public void run() {\n");
		buf.append("                        System.out.println(n);\n");
		buf.append("                    }\n");
		buf.append("                };\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testCodeStyle10() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private static final int N;\n");
		buf.append("    static {N= 10;}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC | CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testCodeStyle11() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {    \n");
		buf.append("    public static int E1N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1N);\n");
		buf.append("        E1N = 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    public static int E2N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1N);\n");
		buf.append("        E1N = 10;\n");
		buf.append("        System.out.println(E2N);\n");
		buf.append("        E2N = 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    private static int E3N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1N);\n");
		buf.append("        E1N = 10;\n");
		buf.append("        System.out.println(E2N);\n");
		buf.append("        E2N = 10;\n");
		buf.append("        System.out.println(E3N);\n");
		buf.append("        E3N = 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC | CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {    \n");
		buf.append("    public static int E1N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        E1.E1N = 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    public static int E2N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        E1.E1N = 10;\n");
		buf.append("        System.out.println(E2.E2N);\n");
		buf.append("        E2.E2N = 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    private static int E3N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        E1.E1N = 10;\n");
		buf.append("        System.out.println(E2.E2N);\n");
		buf.append("        E2.E2N = 10;\n");
		buf.append("        System.out.println(E3.E3N);\n");
		buf.append("        E3.E3N = 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected3= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2, expected3});

	}
	
	public void testCodeStyle12() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public final static int N1 = 10;\n");
		buf.append("    public static int N2 = N1;\n");
		buf.append("    {\n");
		buf.append("        System.out.println(N1);\n");
		buf.append("        N2 = 10;\n");
		buf.append("        System.out.println(N2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC | CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public final static int N1 = 10;\n");
		buf.append("    public static int N2 = E1.N1;\n");
		buf.append("    {\n");
		buf.append("        System.out.println(E1.N1);\n");
		buf.append("        E1.N2 = 10;\n");
		buf.append("        System.out.println(E1.N2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle13() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private static class E1Inner {\n");
		buf.append("        private static class E1InnerInner {\n");
		buf.append("            public static int N = 10;\n");
		buf.append("            static {\n");
		buf.append("                System.out.println(N);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC | CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private static class E1Inner {\n");
		buf.append("        private static class E1InnerInner {\n");
		buf.append("            public static int N = 10;\n");
		buf.append("            static {\n");
		buf.append("                System.out.println(E1InnerInner.N);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle14() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    static class E1Inner {\n");
		buf.append("        public static class E1InnerInner {\n");
		buf.append("            public static int N = 10;\n");
		buf.append("            public void foo() {\n");
		buf.append("                System.out.println(N);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC | CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    static class E1Inner {\n");
		buf.append("        public static class E1InnerInner {\n");
		buf.append("            public static int N = 10;\n");
		buf.append("            public void foo() {\n");
		buf.append("                System.out.println(E1InnerInner.N);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle15() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    static class E1Inner {\n");
		buf.append("        public static class E1InnerInner {\n");
		buf.append("            public static int N = 10;\n");
		buf.append("            public void foo() {\n");
		buf.append("                System.out.println((new E1InnerInner()).N);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC | CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    static class E1Inner {\n");
		buf.append("        public static class E1InnerInner {\n");
		buf.append("            public static int N = 10;\n");
		buf.append("            public void foo() {\n");
		buf.append("                System.out.println(E1InnerInner.N);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle16() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static int E1N;\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    public static int E2N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        System.out.println(E2.E1N);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        System.out.println(E2.E1N);\n");
		buf.append("        System.out.println(E3.E1N);\n");
		buf.append("        System.out.println(E2.E2N);\n");
		buf.append("        System.out.println(E3.E2N);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		refactoring.addCompilationUnit(cu3);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC | 
		                                       CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS | CodeStyleCleanUp.CHANGE_INDIRECT_STATIC_ACCESS_TO_DIRECT);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    public static int E2N = 10;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("import test1.E1;\n");
		buf.append("import test1.E2;\n");
		buf.append("public class E3 extends E2 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        System.out.println(E1.E1N);\n");
		buf.append("        System.out.println(E2.E2N);\n");
		buf.append("        System.out.println(E2.E2N);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2});

	}
	
	public void testCodeStyle17() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b= true;\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (b)\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        if (b) {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        } else\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        if (b)\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        else\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        while (b)\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        do\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        while (b);\n");
		buf.append("        for(;;)\n");
		buf.append("            System.out.println(10);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b= true;\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (b) {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        }\n");
		buf.append("        if (b) {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        }\n");
		buf.append("        if (b) {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        }\n");
		buf.append("        while (b) {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        }\n");
		buf.append("        do {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        } while (b);\n");
		buf.append("        for(;;) {\n");
		buf.append("            System.out.println(10);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle18() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (b)\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        else if (q)\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        else\n");
		buf.append("            if (b && q)\n");
		buf.append("                System.out.println(1);\n");
		buf.append("            else\n");
		buf.append("                System.out.println(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (b) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        } else if (q) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        } else\n");
		buf.append("            if (b && q) {\n");
		buf.append("                System.out.println(1);\n");
		buf.append("            } else {\n");
		buf.append("                System.out.println(2);\n");
		buf.append("            }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle19() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (;b;)\n");
		buf.append("            for (;q;)\n");
		buf.append("                if (b)\n");
		buf.append("                    System.out.println(1);\n");
		buf.append("                else if (q)\n");
		buf.append("                    System.out.println(2);\n");
		buf.append("                else\n");
		buf.append("                    System.out.println(3);\n");
		buf.append("        for (;b;)\n");
		buf.append("            for (;q;) {\n");
		buf.append("                \n");
		buf.append("            }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (;b;) {\n");
		buf.append("            for (;q;) {\n");
		buf.append("                if (b) {\n");
		buf.append("                    System.out.println(1);\n");
		buf.append("                } else if (q) {\n");
		buf.append("                    System.out.println(2);\n");
		buf.append("                } else {\n");
		buf.append("                    System.out.println(3);\n");
		buf.append("                }\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        for (;b;) {\n");
		buf.append("            for (;q;) {\n");
		buf.append("                \n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle20() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (b)\n");
		buf.append("            while (q)\n");
		buf.append("                if (b)\n");
		buf.append("                    System.out.println(1);\n");
		buf.append("                else if (q)\n");
		buf.append("                    System.out.println(2);\n");
		buf.append("                else\n");
		buf.append("                    System.out.println(3);\n");
		buf.append("        while (b)\n");
		buf.append("            while (q) {\n");
		buf.append("                \n");
		buf.append("            }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (b) {\n");
		buf.append("            while (q) {\n");
		buf.append("                if (b) {\n");
		buf.append("                    System.out.println(1);\n");
		buf.append("                } else if (q) {\n");
		buf.append("                    System.out.println(2);\n");
		buf.append("                } else {\n");
		buf.append("                    System.out.println(3);\n");
		buf.append("                }\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        while (b) {\n");
		buf.append("            while (q) {\n");
		buf.append("                \n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle21() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        do\n");
		buf.append("            do\n");
		buf.append("                if (b)\n");
		buf.append("                    System.out.println(1);\n");
		buf.append("                else if (q)\n");
		buf.append("                    System.out.println(2);\n");
		buf.append("                else\n");
		buf.append("                    System.out.println(3);\n");
		buf.append("            while (q);\n");
		buf.append("        while (b);\n");
		buf.append("        do\n");
		buf.append("            do {\n");
		buf.append("                \n");
		buf.append("            } while (q);\n");
		buf.append("        while (b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public boolean b, q;\n");
		buf.append("    public void foo() {\n");
		buf.append("        do {\n");
		buf.append("            do {\n");
		buf.append("                if (b) {\n");
		buf.append("                    System.out.println(1);\n");
		buf.append("                } else if (q) {\n");
		buf.append("                    System.out.println(2);\n");
		buf.append("                } else {\n");
		buf.append("                    System.out.println(3);\n");
		buf.append("                }\n");
		buf.append("            } while (q);\n");
		buf.append("        } while (b);\n");
		buf.append("        do {\n");
		buf.append("            do {\n");
		buf.append("                \n");
		buf.append("            } while (q);\n");
		buf.append("        } while (b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle22() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.I1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        I1 i1= new I1() {\n");
		buf.append("            private static final int N= 10;\n");
		buf.append("            public void foo() {\n");
		buf.append("                System.out.println(N);\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public interface I1 {}\n");
		pack2.createCompilationUnit("I1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testCodeStyle23() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int fNb= 0;\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true)\n");
		buf.append("            fNb++;\n");
		buf.append("        String s; //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp codeStyle= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS);
		refactoring.addCleanUp(codeStyle);
		ControlStatementsCleanUp statmentsCleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		refactoring.addCleanUp(statmentsCleanUp);
		ICleanUp stringFix= new StringCleanUp(StringCleanUp.REMOVE_UNNECESSARY_NLS_TAG);
		refactoring.addCleanUp(stringFix);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int fNb= 0;\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true) {\n");
		buf.append("            this.fNb++;\n");
		buf.append("        }\n");
		buf.append("        String s; \n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle24() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true)\n");
		buf.append("            System.out.println(\"\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp codeStyle= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS);
		refactoring.addCleanUp(codeStyle);
		ControlStatementsCleanUp statmentsCleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		refactoring.addCleanUp(statmentsCleanUp);
		ICleanUp stringFix= new StringCleanUp(StringCleanUp.REMOVE_UNNECESSARY_NLS_TAG | StringCleanUp.ADD_MISSING_NLS_TAG);
		refactoring.addCleanUp(stringFix);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true) {\n");
		buf.append("            System.out.println(\"\"); //$NON-NLS-1$\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyle25() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(I1Impl.N);\n");
		buf.append("        I1 i1= new I1();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.I1;\n");
		buf.append("public class I1Impl implements I1 {}\n");
		pack1.createCompilationUnit("I1Impl.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class I1 {}\n");
		pack1.createCompilationUnit("I1.java", buf.toString(), false, null);
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public interface I1 {\n");
		buf.append("    public static int N= 10;\n");
		buf.append("}\n");
		pack2.createCompilationUnit("I1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.CHANGE_INDIRECT_STATIC_ACCESS_TO_DIRECT);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(test2.I1.N);\n");
		buf.append("        I1 i1= new I1();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyleBug118204() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    static String s;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(s);\n");
		buf.append("    }\n");
		buf.append("    E1(){}\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    static String s;\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.s);\n");
		buf.append("    }\n");
		buf.append("    E1(){}\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}

	public void testCodeStyleBug114544() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(new E1().i);\n");
		buf.append("    }\n");
		buf.append("    public static int i= 10;\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		
		setOptions(cleanUp);
		
		CompilationUnit[] units= compile(new ICompilationUnit[] {cu1});
		assertNumberOfProblems(units[0], 1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(E1.i);\n");
		buf.append("    }\n");
		buf.append("    public static int i= 10;\n");
		buf.append("}\n");
		String expected1= buf.toString();

		int numberOfFixes= 1;
		int offset= 0;
		String[] previews= new String[numberOfFixes];
		for (int i= offset; i < numberOfFixes; i++) {
			IFix fix= cleanUp.createFix(units[i]);
			assertNotNull("There are problems but no fix", fix);
			TextChange change= fix.createChange();
			assertNotNull("Null change for an existing fix", change);
			previews[i - offset]= change.getPreviewContent(null);
		}

		assertEqualStringsIgnoreOrder(previews, new String[] { expected1});
	}
	
	public void testCodeStyleBug119170_01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static void foo() {}\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private static class E1 {}\n");
		buf.append("    public void bar() {\n");
		buf.append("        test1.E1 e1= new test1.E1();\n");
		buf.append("        e1.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu2);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private static class E1 {}\n");
		buf.append("    public void bar() {\n");
		buf.append("        test1.E1 e1= new test1.E1();\n");
		buf.append("        test1.E1.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyleBug119170_02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public static void foo() {}\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private static String E1= \"\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        test1.E1 e1= new test1.E1();\n");
		buf.append("        e1.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu2);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    private static String E1= \"\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        test1.E1 e1= new test1.E1();\n");
		buf.append("        test1.E1.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCodeStyleBug123468() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    protected int field;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    private int field;\n");
		buf.append("    public void foo() {\n");
		buf.append("        super.field= 10;\n");
		buf.append("        field= 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu2);
		
		ICleanUp cleanUp= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 extends E1 {\n");
		buf.append("    private int field;\n");
		buf.append("    public void foo() {\n");
		buf.append("        super.field= 10;\n");
		buf.append("        this.field= 10;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<E1> list= new ArrayList<E1>();\n");
		buf.append("        for (Iterator<E1> iter = list.iterator(); iter.hasNext();) {\n");
		buf.append("            E1 e = iter.next();\n");
		buf.append("            System.out.println(e);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<E1> list= new ArrayList<E1>();\n");
		buf.append("        for (E1 e : list) {\n");
		buf.append("            System.out.println(e);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<E1> list1= new ArrayList<E1>();\n");
		buf.append("        List<E1> list2= new ArrayList<E1>();\n");
		buf.append("        for (Iterator<E1> iter = list1.iterator(); iter.hasNext();) {\n");
		buf.append("            E1 e1 = iter.next();\n");
		buf.append("            for (Iterator iterator = list2.iterator(); iterator.hasNext();) {\n");
		buf.append("                E1 e2 = (E1) iterator.next();\n");
		buf.append("                System.out.println(e2);\n");
		buf.append("            }\n");
		buf.append("            System.out.println(e1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<E1> list1= new ArrayList<E1>();\n");
		buf.append("        List<E1> list2= new ArrayList<E1>();\n");
		buf.append("        for (E1 e1 : list1) {\n");
		buf.append("            for (Object element0 : list2) {\n");
		buf.append("                E1 e2 = (E1) element0;\n");
		buf.append("                System.out.println(e2);\n");
		buf.append("            }\n");
		buf.append("            System.out.println(e1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array={1,2,3,4};\n");
		buf.append("        for (int i=0;i<array.length;i++) {\n");
		buf.append("            String[] strs={\"1\",\"2\"};\n");
		buf.append("            for (int j = 0; j < strs.length; j++) {\n");
		buf.append("                System.out.println(array[i]+strs[j]);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array={1,2,3,4};\n");
		buf.append("        for (int element : array) {\n");
		buf.append("            String[] strs={\"1\",\"2\"};\n");
		buf.append("            for (String element0 : strs) {\n");
		buf.append("                System.out.println(element+element0);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array= new int[10];\n");
		buf.append("        for (int i = 0; i < array.length; i++) {\n");
		buf.append("            for (int j = 0; j < array.length; j++) {\n");
		buf.append("                for (int k = 0; k < array.length; k++) {\n");
		buf.append("                }\n");
		buf.append("                for (int k = 0; k < array.length; k++) {\n");
		buf.append("                }\n");
		buf.append("            }\n");
		buf.append("            for (int j = 0; j < array.length; j++) {\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array= new int[10];\n");
		buf.append("        for (int element : array) {\n");
		buf.append("            for (int element0 : array) {\n");
		buf.append("                for (int element1 : array) {\n");
		buf.append("                }\n");
		buf.append("                for (int element1 : array) {\n");
		buf.append("                }\n");
		buf.append("            }\n");
		buf.append("            for (int element0 : array) {\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop05() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array= null;\n");
		buf.append("        for (int i = 0; --i < array.length;) {}\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testJava50ForLoop06() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int a= 0;\n");
		buf.append("        for (a=0;a>0;a++) {}\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testJava50ForLoop07() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int a= 0;\n");
		buf.append("        for (a=0;;a++) {}\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testJava50ForLoop08() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array= null;\n");
		buf.append("        int a= 0;\n");
		buf.append("        for (;a<array.length;a++) {}\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testJava50ForLoop09() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array= null;\n");
		buf.append("        for (int i = 0; i < array.length; i++) {\n");
		buf.append("            final int element= array[i];\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array= null;\n");
		buf.append("        for (final int element : array) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop10() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] array= null;\n");
		buf.append("        int i;\n");
		buf.append("        for (i = 0; i < array.length; i++) {}\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testJava50ForLoop11() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private class E1Sub {\n");
		buf.append("        public int[] array;    \n");
		buf.append("    }\n");
		buf.append("    private E1Sub e1sub;\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i = this.e1sub.array.length;i < 1;i++) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private class E1Sub {\n");
		buf.append("        public int[] array;    \n");
		buf.append("    }\n");
		buf.append("    private E1Sub e1sub;\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int element : this.e1sub.array) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop12() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public int[] array;    \n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i = this.array.length;i < 1;i++) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public int[] array;    \n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int element : this.array) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testJava50ForLoop13() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public int[] array1, array2;\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i = array1.length - array2.length; i < 1; i++) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		assertRefactoringHasNoChange(refactoring);
	}
	
	public void testJava50ForLoop14() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.E3;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        E2 e2= new E2();\n");
		buf.append("        e2.foo();\n");
		buf.append("        E3 e3= new E3();\n");
		buf.append("        for (int i = e3.array.length; i < 1; i++) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E2 {\n");
		buf.append("    public void foo() {};\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E2.java", buf.toString(), false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E2 {}\n");
		pack2.createCompilationUnit("E2.java", buf.toString(), false, null);

		buf= new StringBuffer();
		buf.append("package test2;\n");
		buf.append("public class E3 {\n");
		buf.append("    public E2[] array;\n");
		buf.append("}\n");
		pack2.createCompilationUnit("E3.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import test2.E3;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        E2 e2= new E2();\n");
		buf.append("        e2.foo();\n");
		buf.append("        E3 e3= new E3();\n");
		buf.append("        for (test2.E2 element : e3.array) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}

	
	public void testCombination01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E1 {\n");
		buf.append("    private int i= 10;\n");
		buf.append("    private int j= 20;\n");
		buf.append("    \n");
		buf.append("    public void foo() {\n");
		buf.append("        i= j;\n");
		buf.append("        i= 20;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS);
		ICleanUp cleanUp2= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_FIELDS);
		refactoring.addCleanUp(cleanUp1);
		refactoring.addCleanUp(cleanUp2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E1 {\n");
		buf.append("    private int j= 20;\n");
		buf.append("    \n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCombination02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true)\n");
		buf.append("            System.out.println(\"\");\n");
		buf.append("        if (true)\n");
		buf.append("            System.out.println(\"\");\n");
		buf.append("        if (true)\n");
		buf.append("            System.out.println(\"\");\n");
		buf.append("        System.out.println(\"\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		ICleanUp cleanUp2= new StringCleanUp(StringCleanUp.ADD_MISSING_NLS_TAG);
		refactoring.addCleanUp(cleanUp1);
		refactoring.addCleanUp(cleanUp2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true) {\n");
		buf.append("            System.out.println(\"\"); //$NON-NLS-1$\n");
		buf.append("        }\n");
		buf.append("        if (true) {\n");
		buf.append("            System.out.println(\"\"); //$NON-NLS-1$\n");
		buf.append("        }\n");
		buf.append("        if (true) {\n");
		buf.append("            System.out.println(\"\"); //$NON-NLS-1$\n");
		buf.append("        }\n");
		buf.append("        System.out.println(\"\"); //$NON-NLS-1$\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCombination03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;  \n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E1  {\n");
		buf.append("    private List<String> fList;\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (Iterator<String> iter = fList.iterator(); iter.hasNext();) {\n");
		buf.append("            String element = (String) iter.next();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new CodeStyleCleanUp(CodeStyleCleanUp.QUALIFY_FIELD_ACCESS);
		refactoring.addCleanUp(cleanUp1);
		ICleanUp cleanUp2= new ControlStatementsCleanUp(ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp2);
		
		buf= new StringBuffer();
		buf.append("package test1;  \n");
		buf.append("import java.util.List;\n");
		buf.append("public class E1  {\n");
		buf.append("    private List<String> fList;\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (String string : this.fList) {\n");
		buf.append("            String element = (String) string;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCombinationBug120585() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    private int i= 0;\n");
		buf.append("    void method() {\n");
		buf.append("        int[] array= null;\n");
		buf.append("        for (int i= 0; i < array.length; i++)\n");
		buf.append("            System.out.println(array[i]);\n");
		buf.append("        i= 12;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS | ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		ICleanUp cleanUp2= new UnusedCodeCleanUp(UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_FIELDS);
		refactoring.addCleanUp(cleanUp1);
		refactoring.addCleanUp(cleanUp2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    void method() {\n");
		buf.append("        int[] array= null;\n");
		buf.append("        for (int element : array) {\n");
		buf.append("            System.out.println(element);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testCombinationBug125455() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1  {\n");
		buf.append("    private void bar(boolean wait) {\n");
		buf.append("        if (!wait) \n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("    private void foo(String s) {\n");
		buf.append("        String s1= \"\";\n");
		buf.append("        if (s.equals(\"\"))\n");
		buf.append("            System.out.println();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS);
		ICleanUp cleanUp2= new StringCleanUp(StringCleanUp.ADD_MISSING_NLS_TAG);
		refactoring.addCleanUp(cleanUp1);
		refactoring.addCleanUp(cleanUp2);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1  {\n");
		buf.append("    private void bar(boolean wait) {\n");
		buf.append("        if (!wait) {\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    private void foo(String s) {\n");
		buf.append("        String s1= \"\"; //$NON-NLS-1$\n");
		buf.append("        if (s.equals(\"\")) { //$NON-NLS-1$\n");
		buf.append("            System.out.println();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	
	public void testSerialVersion01() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new PotentialProgrammingProblemsCleanUp(PotentialProgrammingProblemsCleanUp.ADD_CALCULATED_SERIAL_VERSION_ID);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("\n");
		buf.append("    " + FIELD_COMMENT + "\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testSerialVersion02() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("    public class B1 implements Serializable {\n");
		buf.append("    }\n");
		buf.append("    public class B2 extends B1 {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new PotentialProgrammingProblemsCleanUp(PotentialProgrammingProblemsCleanUp.ADD_CALCULATED_SERIAL_VERSION_ID);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("    " + FIELD_COMMENT + "\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("    public class B1 implements Serializable {\n");
		buf.append("\n");
		buf.append("        " + FIELD_COMMENT + "\n");
		buf.append("        private static final long serialVersionUID = 1L;\n");
		buf.append("    }\n");
		buf.append("    public class B2 extends B1 {\n");
		buf.append("\n");
		buf.append("        " + FIELD_COMMENT + "\n");
		buf.append("        private static final long serialVersionUID = 1L;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testSerialVersion03() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Externalizable;\n");
		buf.append("public class E2 implements Externalizable {\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		refactoring.addCompilationUnit(cu2);
		
		ICleanUp cleanUp1= new PotentialProgrammingProblemsCleanUp(PotentialProgrammingProblemsCleanUp.ADD_CALCULATED_SERIAL_VERSION_ID);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("\n");
		buf.append("    " + FIELD_COMMENT + "\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("}\n");
		String expected2= buf.toString();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Externalizable;\n");
		buf.append("public class E2 implements Externalizable {\n");
		buf.append("\n");
		buf.append("    " + FIELD_COMMENT + "\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1, expected2});
	}
	
	public void testSerialVersion04() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Serializable s= new Serializable() {\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new PotentialProgrammingProblemsCleanUp(PotentialProgrammingProblemsCleanUp.ADD_CALCULATED_SERIAL_VERSION_ID);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("    " + FIELD_COMMENT + "\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        Serializable s= new Serializable() {\n");
		buf.append("\n");
		buf.append("            " + FIELD_COMMENT + "\n");
		buf.append("            private static final long serialVersionUID = 1L;\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testSerialVersion05() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("\n");
		buf.append("    private Serializable s= new Serializable() {\n");
		buf.append("        \n");
		buf.append("    };\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new PotentialProgrammingProblemsCleanUp(PotentialProgrammingProblemsCleanUp.ADD_CALCULATED_SERIAL_VERSION_ID);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class E1 implements Serializable {\n");
		buf.append("\n");
		buf.append("    " + FIELD_COMMENT + "\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("    private Serializable s= new Serializable() {\n");
		buf.append("\n");
		buf.append("        " + FIELD_COMMENT + "\n");
		buf.append("        private static final long serialVersionUID = 1L;\n");
		buf.append("        \n");
		buf.append("    };\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testRemoveBlock01() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void if_() {\n");
		buf.append("        if (true) {\n");
		buf.append("            ;\n");
		buf.append("        } else if (false) {\n");
		buf.append("            ;\n");
		buf.append("        } else {\n");
		buf.append("            ;\n");
		buf.append("        }\n");
		buf.append("        \n");
		buf.append("        if (true) {\n");
		buf.append("            ;;\n");
		buf.append("        } else if (false) {\n");
		buf.append("            ;;\n");
		buf.append("        } else {\n");
		buf.append("            ;;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.REMOVE_UNNECESSARY_BLOCKS | ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void if_() {\n");
		buf.append("        if (true)\n");
		buf.append("            ;\n");
		buf.append("        else if (false)\n");
		buf.append("            ;\n");
		buf.append("        else\n");
		buf.append("            ;\n");
		buf.append("        \n");
		buf.append("        if (true) {\n");
		buf.append("            ;;\n");
		buf.append("        } else if (false) {\n");
		buf.append("            ;;\n");
		buf.append("        } else {\n");
		buf.append("            ;;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testRemoveBlock02() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (;;) {\n");
		buf.append("            ; \n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        for (;;) {\n");
		buf.append("            ;; \n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.REMOVE_UNNECESSARY_BLOCKS | ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (;;);\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        for (;;) {\n");
		buf.append("            ;; \n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testRemoveBlock03() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (true) {\n");
		buf.append("            ;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        while (true) {\n");
		buf.append("            ;;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.REMOVE_UNNECESSARY_BLOCKS | ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (true);\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        while (true) {\n");
		buf.append("            ;;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testRemoveBlock04() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        do {\n");
		buf.append("            ;\n");
		buf.append("        } while (true);\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        do {\n");
		buf.append("            ;;\n");
		buf.append("        } while (true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.REMOVE_UNNECESSARY_BLOCKS | ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        do; while (true);\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        do {\n");
		buf.append("            ;;\n");
		buf.append("        } while (true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	public void testRemoveBlock05() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] is= null;\n");
		buf.append("        for (int i= 0;i < is.length;i++) {\n");
		buf.append("            ;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(cu1);
		
		ICleanUp cleanUp1= new ControlStatementsCleanUp(ControlStatementsCleanUp.REMOVE_UNNECESSARY_BLOCKS | ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP);
		refactoring.addCleanUp(cleanUp1);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int[] is= null;\n");
		buf.append("        for (int element : is);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertRefactoringResultAsExpected(refactoring, new String[] {expected1});
	}
	
	
}
