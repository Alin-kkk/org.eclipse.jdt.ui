/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.tests.refactoring;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class InlineMethodTests extends AbstractSelectionTestCase {

	private static InlineMethodTestSetup fgTestSetup;
	
	public InlineMethodTests(String name) {
		super(name);
	}

	public static Test suite() {
		fgTestSetup= new InlineMethodTestSetup(new TestSuite(InlineMethodTests.class));
		return fgTestSetup;
	}
	
	protected String getResourceLocation() {
		return "InlineMethodWorkspace/TestCases/";
	}
	
	protected String adaptName(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1) + ".java";
	}
	
	protected void performTest(IPackageFragment packageFragment, String id, int mode, String outputFolder) throws Exception {
		ICompilationUnit unit= createCU(packageFragment, id);
		String source= unit.getSource();
		int[] selection= getSelection(source);
		InlineMethodRefactoring refactoring= InlineMethodRefactoring.create(
			unit, selection[0], selection[1],
			JavaPreferencesSettings.getCodeGenerationSettings());
		refactoring.setSaveChanges(true);
		String out= null;
		switch (mode) {
			case COMPARE_WITH_OUTPUT:
				out= getProofedContent(outputFolder, id);
				break;		
		}
		performTest(unit, refactoring, mode, out);
	}

	/************************ Invalid Tests ********************************/
		
	protected void performInvalidTest() throws Exception {
		performTest(fgTestSetup.getInvalidPackage(), getName(), INVALID_SELECTION, null);
	}
	
	public void testRecursion() throws Exception {
		performInvalidTest();
	}
	
	public void testFieldInitializer() throws Exception {
		performInvalidTest();
	}
	
	public void testLocalInitializer() throws Exception {
		performInvalidTest();
	}
	
	public void testInterruptedStatement() throws Exception {
		performInvalidTest();
	}
	
	public void testMultiLocal() throws Exception {
		performInvalidTest();
	}
	
	public void testComplexBody() throws Exception {
		performInvalidTest();
	}
	
	public void testCompileError1() throws Exception {
		performInvalidTest();
	}
	
	public void testCompileError2() throws Exception {
		performInvalidTest();
	}
	
	public void testCompileError3() throws Exception {
		performInvalidTest();
	}
	
	/************************ Simple Tests ********************************/
		
	private void performSimpleTest() throws Exception {
		performTest(fgTestSetup.getSimplePackage(), getName(), COMPARE_WITH_OUTPUT, "simple_out");
	}
	
	public void testBasic1() throws Exception {
		performSimpleTest();
	}	

	public void testBasic2() throws Exception {
		performSimpleTest();
	}	
	
	public void testEmptyBody() throws Exception {
		performSimpleTest();
	}	

	public void testPrimitiveArray() throws Exception {
		performSimpleTest();
	}	

	public void testTypeArray() throws Exception {
		performSimpleTest();
	}	

	public void testInitializer() throws Exception {
		performSimpleTest();
	}	

	public void testSuper() throws Exception {
		performSimpleTest();
	}	

	/************************ Argument Tests ********************************/
		
	private void performArgumentTest() throws Exception {
		performTest(fgTestSetup.getArgumentPackage(), getName(), COMPARE_WITH_OUTPUT, "argument_out");
	}
	
	public void testLocalReferenceUnused() throws Exception {
		performArgumentTest();
	}	
	
	public void testLocalReferenceRead() throws Exception {
		performArgumentTest();
	}	
	
	public void testLocalReferenceWrite() throws Exception {
		performArgumentTest();
	}	
	
	public void testLocalReferenceLoop() throws Exception {
		performArgumentTest();
	}	
	
	public void testLiteralReferenceRead() throws Exception {
		performArgumentTest();
	}	
	
	public void testLiteralReferenceWrite() throws Exception {
		performArgumentTest();
	}	
	
	public void testParameterNameUsed1() throws Exception {
		performArgumentTest();
	}	
	
	public void testParameterNameUsed2() throws Exception {
		performArgumentTest();
	}	
	
	public void testParameterNameUsed3() throws Exception {
		performArgumentTest();
	}	
	
	public void testParameterNameUsed4() throws Exception {
		performArgumentTest();
	}	
	
	public void testParameterNameUnused1() throws Exception {
		performArgumentTest();
	}	
	
	public void testParameterNameUnused2() throws Exception {
		performArgumentTest();
	}	
	
	public void testParameterNameUnused3() throws Exception {
		performArgumentTest();
	}
	
	/************************ Name Conflict Tests ********************************/
		
	private void performNameConflictTest() throws Exception {
		performTest(fgTestSetup.getNameConflictPackage(), getName(), COMPARE_WITH_OUTPUT, "nameconflict_out");
	}
	
	public void testSameLocal() throws Exception {
		performNameConflictTest();
	}
	
	public void testSameType() throws Exception {
		performNameConflictTest();
	}
	
	public void testSameTypeAfter() throws Exception {
		performNameConflictTest();
	}
	
	public void testSameTypeInSibling() throws Exception {
		performNameConflictTest();
	}
	
	public void testLocalInType() throws Exception {
		performNameConflictTest();
	}
	
	public void testFieldInType() throws Exception {
		performNameConflictTest();
	}

	public void testSwitchStatement() throws Exception {
		performNameConflictTest();
	}

	public void testBlocks() throws Exception {
		performNameConflictTest();
	}

	/************************ Call Tests ********************************/
		
	private void performCallTest() throws Exception {
		performTest(fgTestSetup.getCallPackage(), getName(), COMPARE_WITH_OUTPUT, "call_out");
	}
	
	public void testExpressionStatement() throws Exception {
		performCallTest();
	}

	public void testExpressionStatementWithReturn() throws Exception {
		performCallTest();
	}
	
	public void testStatementWithFunction1() throws Exception {
		performCallTest();
	}
	
	public void testStatementWithFunction2() throws Exception {
		performCallTest();
	}
	
	public void testParenthesis() throws Exception {
		performCallTest();
	}
	
	/************************ Expression Tests ********************************/
		
	private void performExpressionTest() throws Exception {
		performTest(fgTestSetup.getExpressionPackage(), getName(), COMPARE_WITH_OUTPUT, "expression_out");
	}
	
	public void testSimpleExpression() throws Exception {
		performExpressionTest();
	}
	
	public void testSimpleExpressionWithStatements() throws Exception {
		performExpressionTest();
	}
	
	public void testSimpleBody() throws Exception {
		performExpressionTest();
	}
	
	public void testAssignment() throws Exception {
		performExpressionTest();
	}
	
	public void testReturnStatement() throws Exception {
		performExpressionTest();
	}
	
	/************************ Control Statements Tests ********************************/
		
	private void performControlStatementTest() throws Exception {
		performTest(fgTestSetup.getControlStatementPackage(), getName(), COMPARE_WITH_OUTPUT, "controlStatement_out");
	}
	
	public void testForEmpty() throws Exception {
		performControlStatementTest();
	}
	
	public void testForOne() throws Exception {
		performControlStatementTest();
	}
	
	public void testForTwo() throws Exception {
		performControlStatementTest();
	}
	
	public void testIfThenTwo() throws Exception {
		performControlStatementTest();
	}
	
	public void testIfElseTwo() throws Exception {
		performControlStatementTest();
	}
	
	public void testForAssignmentOne() throws Exception {
		performControlStatementTest();
	}
	
	public void testForAssignmentTwo() throws Exception {
		performControlStatementTest();
	}

	/************************ Receiver Tests ********************************/
		
	private void performReceiverTest() throws Exception {
		performTest(fgTestSetup.getReceiverPackage(), getName(), COMPARE_WITH_OUTPUT, "receiver_out");
	}
	
	public void testNoImplicitReceiver() throws Exception {
		performReceiverTest();
	}

	public void testNameThisReceiver() throws Exception {
		performReceiverTest();
	}

	public void testNameImplicitReceiver() throws Exception {
		performReceiverTest();
	}

	public void testExpressionZeroImplicitReceiver() throws Exception {
		performReceiverTest();
	}

	public void testExpressionOneImplicitReceiver() throws Exception {
		performReceiverTest();
	}

	public void testExpressionTwoImplicitReceiver() throws Exception {
		performReceiverTest();
	}

	public void testStaticReceiver() throws Exception {
		performReceiverTest();
	}

	public void testReceiverWithStatic() throws Exception {
		performReceiverTest();
	}
	
	public void testThisExpression() throws Exception {
		performReceiverTest();
	}
	
	/************************ Import Tests ********************************/
		
	private void performImportTest() throws Exception {
		performTest(fgTestSetup.getImportPackage(), getName(), COMPARE_WITH_OUTPUT, "import_out");
	}
		
	public void testUseArray() throws Exception {
		performImportTest();
	}	
		
	public void testUseInArgument() throws Exception {
		performImportTest();
	}	
		
	public void testUseInClassLiteral() throws Exception {
		performImportTest();
	}	
		
	public void testUseInDecl() throws Exception {
		performImportTest();
	}	
		
	public void testUseInDeclClash() throws Exception {
		performImportTest();
	}	
		
	public void testUseInLocalClass() throws Exception {
		performImportTest();
	}	
}
