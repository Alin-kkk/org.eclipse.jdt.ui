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
package org.eclipse.jdt.ui.tests.core;

import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;

public class CodeFormatterUtilTest extends CoreTests {
	
	private static final Class THIS= CodeFormatterUtilTest.class;
	
	private IJavaProject fJProject1;

	public CodeFormatterUtilTest(String name) {
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
			suite.addTest(new CodeFormatterUtilTest("test1"));
			return new ProjectTestSetup(suite);
		}	
	}


	protected void setUp() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		JavaProjectHelper.addRequiredProject(fJProject1, ProjectTestSetup.getProject());

		Hashtable options= JavaCore.getDefaultOptions();
		options.put(JavaCore.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(JavaCore.FORMATTER_TAB_SIZE, "4");
		options.put(JavaCore.FORMATTER_LINE_SPLIT, "999");
		JavaCore.setOptions(options);
	}


	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJProject1);
	}
	
	
	public void testCU() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("    Runnable run= new Runnable() {};\n");
		buf.append("    }\n");
		buf.append("}\n");
		String contents= buf.toString();
		
		AST ast= new AST();
		CompilationUnit unit= ast.newCompilationUnit();
		
		String formatted= CodeFormatterUtil.format(unit, contents, 0, null, "\n", null);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Runnable run = new Runnable() {\n");
		buf.append("        };\n");	
		buf.append("    }\n");
		buf.append("}\n");
		String expected= buf.toString();
		assertEqualString(formatted, expected);
	}
	
	public void testCUWithPos() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("    Runnable run= new Runnable() {};\n");
		buf.append("    }\n");
		buf.append("}\n");
		String contents= buf.toString();
		
		AST ast= new AST();
		CompilationUnit unit= ast.newCompilationUnit();
		
		String word1= "new";
		int start1= contents.indexOf(word1);
		int[] positions= { start1, start1 + word1.length() - 1};
		
		String formatted= CodeFormatterUtil.format(unit, contents, 0, positions, "\n", null);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Runnable run = new Runnable() {\n");
		buf.append("        };\n");	
		buf.append("    }\n");
		buf.append("}\n");
		String expected= buf.toString();
		assertEqualString(formatted, expected);
		
		String curr1= formatted.substring(positions[0], positions[1] + 1);
		assertEqualString(curr1, word1);
	}	
	
	public void testPackage() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("  package   com . test1;");
		String contents= buf.toString();
		
		AST ast= new AST();
		PackageDeclaration decl= ast.newPackageDeclaration();
		
		String formatted= CodeFormatterUtil.format(decl, contents, 0, null, "\n", null);

		buf= new StringBuffer();
		buf.append("package com.test1;");
		String expected= buf.toString();
		assertEqualString(expected, formatted);
	}
	
	public void testPackageWithPos() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("package   com . test1;");
		String contents= buf.toString();
		
		AST ast= new AST();
		PackageDeclaration node= ast.newPackageDeclaration();
		
		String word1= "com";
		int start1= contents.indexOf(word1);
		
		String word2= ";";
		int start2= contents.indexOf(word2);		
		
		int[] positions= { start1, start1 + word1.length() - 1, start2, start2 + word2.length() - 1};
		
		String formatted= CodeFormatterUtil.format(node, contents, 0, positions, "\n", null);

		buf= new StringBuffer();
		buf.append("package com.test1;");
		String expected= buf.toString();
		assertEqualString(formatted, expected);
		
		String curr1= formatted.substring(positions[0], positions[1] + 1);
		assertEqualString(curr1, word1);
		
		String curr2= formatted.substring(positions[2], positions[3] + 1);
		assertEqualString(curr2, word2);
		
	}		
	
	public void testVarDeclStatemenetWithPos() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("x[ ]=\nnew  int[ offset]");
		String contents= buf.toString();
		
		AST ast= new AST();
		VariableDeclarationFragment node= ast.newVariableDeclarationFragment();
		
		String word1= "new";
		int start1= contents.indexOf(word1);
		
		String word2= "offset";
		int start2= contents.indexOf(word2);		
		
		int[] positions= { start1, start1 + word1.length() - 1, start2, start2 + word2.length() - 1};
		
		String formatted= CodeFormatterUtil.format(node, contents, 0, positions, "\n", null);

		buf= new StringBuffer();
		buf.append("x[] = new int[offset]");
		String expected= buf.toString();
		assertEqualString(formatted, expected);
		
		String curr1= formatted.substring(positions[0], positions[1] + 1);
		assertEqualString(curr1, word1);
		
		String curr2= formatted.substring(positions[2], positions[3] + 1);
		assertEqualString(curr2, word2);
		
	}
	
	public void testJavadoc() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("/** bar\n");
		buf.append(" * foo\n");
		buf.append(" */\n");
		String contents= buf.toString();
		
		AST ast= new AST();
		Javadoc node= ast.newJavadoc();
		
		String word1= "bar";
		int start1= contents.indexOf(word1);
		
		String word2= "foo";
		int start2= contents.indexOf(word2);		
		
		int[] positions= { start1, start1 + word1.length() - 1, start2, start2 + word2.length() - 1};
		
		String formatted= CodeFormatterUtil.format(node, contents, 0, positions, "\n", null);

		buf= new StringBuffer();
		buf.append("/** bar\n");
		buf.append(" * foo\n");
		buf.append(" */\n");
		String expected= buf.toString();
		assertEqualString(formatted, expected);
		
		String curr1= formatted.substring(positions[0], positions[1] + 1);
		assertEqualString(curr1, word1);
		
		String curr2= formatted.substring(positions[2], positions[3] + 1);
		assertEqualString(curr2, word2);
		
	}
	
	public void testCatchClause() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("catch\n");
		buf.append("(Exception e) {\n");
		buf.append("}");
		String contents= buf.toString();
		
		AST ast= new AST();
		CatchClause node= ast.newCatchClause();
		
		String word1= "catch";
		int start1= contents.indexOf(word1);
		
		String word2= "Exception";
		int start2= contents.indexOf(word2);		
		
		int[] positions= { start1, start1 + word1.length() - 1, start2, start2 + word2.length() - 1};
		
		String formatted= CodeFormatterUtil.format(node, contents, 0, positions, "\n", null);

		buf= new StringBuffer();
		buf.append("catch (Exception e) {\n");
		buf.append("}");
		String expected= buf.toString();
		assertEqualString(formatted, expected);
		
		String curr1= formatted.substring(positions[0], positions[1] + 1);
		assertEqualString(curr1, word1);
		
		String curr2= formatted.substring(positions[2], positions[3] + 1);
		assertEqualString(curr2, word2);
		
	}
	
	public void testFormatSubstring() throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("    Runnable runnable= new Runnable() {};\n");
		buf.append("    runnable.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String contents= buf.toString();
		
		String formatString= "Runnable runnable= new Runnable() {};";
		int formatStart= contents.indexOf(formatString);
		int formatEnd= formatStart + formatString.length();
		
		
		String word1= "import";
		int start1= contents.indexOf(word1);
		
		String word2= "new";
		int start2= contents.indexOf(word2);
		
		String word3= "toString";
		int start3= contents.indexOf(word3);		
		
		int[] positions= { start1, start1 + word1.length() - 1, start2, start2 + word2.length() - 1, start3, start3 + word3.length() - 1};
		
		String formatted= CodeFormatterUtil.format(CodeFormatterUtil.K_COMPILATION_UNIT, contents, formatStart, formatEnd, 0, positions, "\n", null);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("    Runnable runnable = new Runnable() {\n");
		buf.append("        };\n");		buf.append("    runnable.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected= buf.toString();
		assertEqualString(formatted, expected);
		
		String curr1= formatted.substring(positions[0], positions[1] + 1);
		assertEqualString(curr1, word1);
		
		String curr2= formatted.substring(positions[2], positions[3] + 1);
		assertEqualString(curr2, word2);
		
		String curr3= formatted.substring(positions[4], positions[5] + 1);
		assertEqualString(curr3, word3);
		
	}	
	
}
