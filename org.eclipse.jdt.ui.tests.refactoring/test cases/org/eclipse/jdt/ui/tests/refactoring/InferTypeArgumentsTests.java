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

package org.eclipse.jdt.ui.tests.refactoring;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.internal.corext.refactoring.generics.InferTypeArgumentsRefactoring;

public class InferTypeArgumentsTests extends RefactoringTest {

	private static final boolean BUG_83500= true;
	private static final boolean BUG_84422= true;
	
	private static final Class clazz= InferTypeArgumentsTests.class;
	private static final String REFACTORING_PATH= "InferTypeArguments/";
	
	public static Test suite() {
		return new Java15Setup(new TestSuite(clazz));
	}
	
	public static Test setUpTest(Test someTest) {
		return new Java15Setup(someTest);
	}
	
	public InferTypeArgumentsTests(String name) {
		super(name);
	}
	
	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}
	
	private void performCu(int expectedInitialStatus, int expectedFinalStatus) throws Exception {
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		IJavaElement[] elements= { cu };
		InferTypeArgumentsRefactoring refactoring= InferTypeArgumentsRefactoring.create(elements);
		
		NullProgressMonitor pm= new NullProgressMonitor();
		RefactoringStatus initialStatus= refactoring.checkInitialConditions(pm);
		assertEquals("wrong initial condition status: " + initialStatus, expectedInitialStatus, initialStatus.getSeverity());
		if (! initialStatus.isOK())
			return;
		
		// set client options here (from instance variables)

		PerformRefactoringOperation op= new PerformRefactoringOperation(
				refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
		JavaCore.run(op, new NullProgressMonitor());
		RefactoringStatus finalStatus= op.getConditionStatus();
		assertEquals("wrong final condition status: " + finalStatus, expectedFinalStatus, finalStatus.getSeverity());
		if (finalStatus.getSeverity() == RefactoringStatus.FATAL)
			return;
		
		assertTrue("Validation check failed", !op.getValidationStatus().hasFatalError());
		assertNotNull("No Undo", op.getUndoChange());
			
		String expected= getFileContents(getOutputTestFileName("A"));
		String actual= cu.getSource();
		assertEqualLines(expected, actual);
	}
	
	private void performCuOK() throws Exception {
		performCu(RefactoringStatus.OK, RefactoringStatus.OK);
	}
	
// -------------------------------------------------------------------------------
	
	public void testCuTwoVectorElements() throws Exception {
		performCuOK();
	}
	
	public void testCuHalfPair() throws Exception {
		performCuOK();
	}
	
	public void testCuMethodAndTypeGeneric01() throws Exception {
		performCuOK();
	}
	
	public void testCuMethodAndTypeGeneric02() throws Exception {
		performCuOK();
	}
	
	public void testPairDance() throws Exception {
		createCUfromTestFile(getPackageP(), "Pair");
		createCUfromTestFile(getPackageP(), "InvertedPair");
		performCuOK();
		// deleted in tearDown
	}
	
	public void testCuAddString() throws Exception {
		performCuOK();
	}
	
	public void testCuAddString2() throws Exception {
		performCuOK();
	}
	
	public void testCuIntermediateLocal() throws Exception {
		performCuOK();
	}
	
	public void testCuSuperAndSub() throws Exception {
		if (BUG_83500) {
			printTestDisabledMessage("bug 83500");
			return;
		}
		performCuOK();
	}
	
	public void testCuCommonSuper() throws Exception {
		performCuOK();
	}

	public void testCuAddGetString() throws Exception {
		performCuOK();
	}
	
	public void testCuAddIntegerGetNumber() throws Exception {
		performCuOK();
	}
	
	public void testCuAddGetIterator() throws Exception {
		performCuOK();
	}
	
	public void testCuContains() throws Exception {
		performCuOK();
	}
	
	public void testCuMethodParam() throws Exception {
		performCuOK();
	}
	
	public void testCuMethodReturns() throws Exception {
		performCuOK();
	}
	
	public void testCuCollectionsMin() throws Exception {
		printTestDisabledMessage("inference of method type arguments does not work yet");
//TODO		performCuOK();
	}
	
	public void testCuAddStringInteger() throws Exception {
		printTestDisabledMessage("currently, we don't follow flow through variables of type Object");
//TODO		performCuOK();
	}
	
	public void testCuAddStringIntegerA() throws Exception {
		performCuOK();
	}
		
	public void testCuInferFromCast() throws Exception {
		printTestDisabledMessage("not implemented yet");
//		performCuOK();
	}
	
	public void testCuRippleMethods() throws Exception {
		performCuOK();
	}
	
	public void testCuRippleMethods2() throws Exception {
		performCuOK();
	}
	
	public void testCuCannotStringDouble() throws Exception {
		printTestDisabledMessage("not implemented yet");
//		performCuOK();
	}
	
	public void testCuRippleMethods3() throws Exception {
		printTestDisabledMessage("not implemented yet");
//		performCuOK();
	}
	
	public void testCuArrays01() throws Exception {
		if (BUG_84422) {
			printTestDisabledMessage("BUG_84422");
			return;
		}
		performCuOK();
	}
	
	public void testCuArrays02() throws Exception {
		if (BUG_84422) {
			printTestDisabledMessage("BUG_84422");
			return;
		}
		performCuOK();
	}
	
	public void testCuArrays03() throws Exception {
		if (BUG_84422) {
			printTestDisabledMessage("BUG_84422");
			return;
		}
		performCuOK();
	}
	
	public void testCuArrays04() throws Exception {
		if (BUG_84422) {
			printTestDisabledMessage("BUG_84422");
			return;
		}
		performCuOK();
	}
	
	public void testCuArrays05() throws Exception {
		if (BUG_84422) {
			printTestDisabledMessage("BUG_84422");
			return;
		}
		performCuOK();
	}
	
	public void testCuArrays06() throws Exception {
		if (BUG_84422) {
			printTestDisabledMessage("BUG_84422");
			return;
		}
		performCuOK();
	}
}
