/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.refactoring.tests;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.core.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.core.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.core.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.core.refactoring.rename.RenameFieldRefactoring;
import org.eclipse.jdt.refactoring.tests.infra.TestExceptionHandler;
import org.eclipse.jdt.testplugin.TestPluginLauncher;

public class RenameNonPrivateFieldTests extends RefactoringTest{
	
	private static final Class clazz= RenameNonPrivateFieldTests.class;
	private static final String REFACTORING_PATH= "RenameNonPrivateField/";

	public RenameNonPrivateFieldTests(String name) {
		super(name);
	}

	public static void main(String[] args) {
		TestPluginLauncher.run(TestPluginLauncher.getLocationFromProperties(), RenameNonPrivateFieldTests.class, args);
	}
		
	public static Test suite() {
		TestSuite suite= new TestSuite(clazz.getName());
		suite.addTest(noSetupSuite());
		return new MySetup(suite);
	}

	public static Test noSetupSuite() {
		return new TestSuite(clazz);
	}

	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	private void helper1_0(String fieldName, String newFieldName) throws Exception{
		IType classA= getType(createCUfromTestFile(getPackageP(), "A"), "A");
		RenameFieldRefactoring ref= new RenameFieldRefactoring(fgChangeCreator, classA.getField(fieldName));
		ref.setNewName(newFieldName);
		RefactoringStatus result= performRefactoring(ref);
		assertNotNull("precondition was supposed to fail", result);
	}
	
	private void helper1() throws Exception{
		helper1_0("f", "g");
	}
	
	private void helper2(String fieldName, String newFieldName, boolean updateReferences) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		IType classA= getType(cu, "A");
		RenameFieldRefactoring ref= new RenameFieldRefactoring(fgChangeCreator, classA.getField(fieldName));
		ref.setNewName(newFieldName);
		ref.setUpdateReferences(updateReferences);
		RefactoringStatus result= performRefactoring(ref);
		assertEquals("was supposed to pass", null, result);
		assertEquals("invalid renaming", getFileContents(getOutputTestFileName("A")), cu.getSource());
		
		assertTrue("anythingToUndo", Refactoring.getUndoManager().anythingToUndo());
		assertTrue("! anythingToRedo", !Refactoring.getUndoManager().anythingToRedo());
		
		Refactoring.getUndoManager().performUndo(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
		assertEquals("invalid undo", getFileContents(getInputTestFileName("A")), cu.getSource());

		assertTrue("! anythingToUndo", !Refactoring.getUndoManager().anythingToUndo());
		assertTrue("anythingToRedo", Refactoring.getUndoManager().anythingToRedo());
		
		Refactoring.getUndoManager().performRedo(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
		assertEquals("invalid redo", getFileContents(getOutputTestFileName("A")), cu.getSource());
	}
	
	private void helper2(String fieldName, String newFieldName) throws Exception{
		helper2(fieldName, newFieldName, true);
	}
	
	private void helper2() throws Exception{
		helper2(true);
	}
	
	private void helper2(boolean updateReferences) throws Exception{
		helper2("f", "g", updateReferences);
	}

	//--------- tests ----------	
	public void testFail0() throws Exception{
		helper1();
	}
	
	public void testFail1() throws Exception{
		helper1();
	}
	
	public void testFail2() throws Exception{
		helper1();
	}
	
	public void testFail3() throws Exception{
		helper1();
	}
	
	public void testFail4() throws Exception{
		helper1();
	}
	
	public void testFail5() throws Exception{
		helper1();
	}	
	
	public void testFail6() throws Exception{
		helper1();
	}
	
	public void testFail7() throws Exception{
		helper1();
	}
	
	public void testFail8() throws Exception{
		helper1();
	}
	
	public void testFail9() throws Exception{
		helper1();
	}
	
	public void testFail10() throws Exception{
		helper1();
	}
	
	public void testFail11() throws Exception{
		helper1();
	}
	
	public void testFail12() throws Exception{
		helper1();
	}	
	
	public void testFail13() throws Exception{
		helper1();
	}
	
	public void testFail14() throws Exception{
		helper1();
	}
	
	// ------ 
	public void test0() throws Exception{
		helper2();
	}
	
	public void test1() throws Exception{
		helper2();
	}
	
	public void test2() throws Exception{
		helper2();
	}
	
	public void test3() throws Exception{
		helper2();
	}
	
	public void test4() throws Exception{
		helper2();
	}

	public void test5() throws Exception{
		helper2();
	}
	
	public void test6() throws Exception{
		printTestDisabledMessage("1GKB9YH: ITPJCORE:WIN2000 - search for field refs - incorrect results");
		//helper2();
	}

	public void test7() throws Exception{
		helper2();
	}
	
	public void test8() throws Exception{
		printTestDisabledMessage("1GD79XM: ITPJCORE:WINNT - Search - search for field references - not all found");
		//helper2();
	}
	
	public void test9() throws Exception{
		helper2();
	}
	
	public void test10() throws Exception{
		helper2();
	}
	
	public void test11() throws Exception{
		helper2();
	}
	
	public void test12() throws Exception{
		//System.out.println("\nRenameNonPrivateField::" + name() + " disabled (1GIHUQP: ITPJCORE:WINNT - search for static field should be more accurate)");
		helper2();
	}
	
	public void test13() throws Exception{
		//System.out.println("\nRenameNonPrivateField::" + name() + " disabled (1GIHUQP: ITPJCORE:WINNT - search for static field should be more accurate)");
		helper2();
	}
	
	public void test14() throws Exception{
		helper2(false);
	}
	
	public void test15() throws Exception{
		helper2(false);
	}
}