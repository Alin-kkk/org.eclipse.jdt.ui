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
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.core.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.core.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.core.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.core.refactoring.rename.RenameMethodRefactoring;
import org.eclipse.jdt.refactoring.tests.infra.TestExceptionHandler;
import org.eclipse.jdt.testplugin.TestPluginLauncher;

public class RenameVirtualMethodInClassTests extends RefactoringTest {
	
	private static final Class clazz= RenameVirtualMethodInClassTests.class;
	private static final String REFACTORING_PATH= "RenameVirtualMethodInClass/";
		
	public RenameVirtualMethodInClassTests(String name) {
		super(name);
	}
	
	public static void main(String[] args) {
		TestPluginLauncher.run(TestPluginLauncher.getLocationFromProperties(), clazz, args);
	}
	
	public static Test suite() {
		TestSuite suite= new TestSuite(clazz.getName());
		suite.addTest(noSetupSuite());
		return new MySetup(suite);
	}
	
	public static Test noSetupSuite() {
		return new TestSuite(clazz);
	}
	
	protected String getRefactoringPath(){
		return REFACTORING_PATH;
	}
	
	private void helper1_0(String methodName, String newMethodName, String[] signatures) throws Exception{
		IType classA= getType(createCUfromTestFile(getPackageP(), "A"), "A");
		RenameMethodRefactoring ref= RenameMethodRefactoring.createInstance(fgChangeCreator, classA.getMethod(methodName, signatures));
		ref.setNewName(newMethodName);
		RefactoringStatus result= performRefactoring(ref);
		assertNotNull("precondition was supposed to fail", result);
	}
	
	private void helper1() throws Exception{
		helper1_0("m", "k", new String[0]);
	}
	
	private void helper2_0(String methodName, String newMethodName, String[] signatures, boolean shouldPass, boolean updateReferences) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		IType classA= getType(cu, "A");
		RenameMethodRefactoring ref=  RenameMethodRefactoring.createInstance(fgChangeCreator, classA.getMethod(methodName, signatures));
		ref.setUpdateReferences(updateReferences);
		ref.setNewName(newMethodName);
		
		assertEquals("was supposed to pass", null, performRefactoring(ref));
		if (!shouldPass){
			assertTrue("incorrect renaming because of java model", ! getFileContents(getOutputTestFileName("A")).equals(cu.getSource()));
			return;
		}
		assertEquals("incorrect renaming", getFileContents(getOutputTestFileName("A")), cu.getSource());
		
		assertTrue("anythingToUndo", Refactoring.getUndoManager().anythingToUndo());
		assertTrue("! anythingToRedo", !Refactoring.getUndoManager().anythingToRedo());
		//assertEquals("1 to undo", 1, Refactoring.getUndoManager().getRefactoringLog().size());
		
		Refactoring.getUndoManager().performUndo(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
		assertEquals("invalid undo", getFileContents(getInputTestFileName("A")), cu.getSource());

		assertTrue("! anythingToUndo", !Refactoring.getUndoManager().anythingToUndo());
		assertTrue("anythingToRedo", Refactoring.getUndoManager().anythingToRedo());
		//assertEquals("1 to redo", 1, Refactoring.getUndoManager().getRedoStack().size());
		
		Refactoring.getUndoManager().performRedo(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
		assertEquals("invalid redo", getFileContents(getOutputTestFileName("A")), cu.getSource());
	}
	
	private void helper2_0(String methodName, String newMethodName, String[] signatures, boolean shouldPass) throws Exception{
		helper2_0(methodName, newMethodName, signatures, shouldPass, true);
	}
	
	private void helper2_0(String methodName, String newMethodName, String[] signatures) throws Exception{
		helper2_0(methodName, newMethodName, signatures, true);
	}
	
	private void helper2(boolean updateReferences) throws Exception{
		helper2_0("m", "k", new String[0], true, updateReferences);
	}

	private void helper2() throws Exception{
		helper2(true);
	}
	
	private void helper2_fail() throws Exception{
		helper2_0("m", "k", new String[0], false);
	}
	
/******************************************************************/	
	public void testFail0() throws Exception{
		helper1();
	}
	
	public void testFail1() throws Exception{
		helper1_0("toString", "fred", new String[0]);
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
		helper1_0("m", "k", new String[]{Signature.SIG_INT});
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
		helper1_0("m", "k", new String[]{Signature.SIG_INT});
	}
	
	public void testFail15() throws Exception{
		helper1();
	}
	
	public void testFail17() throws Exception{
		helper1();
	}
	
	public void testFail18() throws Exception{
		helper1();
	}
	
	public void testFail19() throws Exception{
		helper1();
	}
	
	public void testFail20() throws Exception{
		helper1();
	}
	
	public void testFail21() throws Exception{
		helper1();
	}
	
	public void testFail22() throws Exception{
		helper1();
	}
	
	public void testFail23() throws Exception{
		helper1();
	}
	
	public void testFail24() throws Exception{
		helper1();
	}
	
	public void testFail25() throws Exception{
		helper1();
	}
	
	public void testFail26() throws Exception{
		helper1();
	}
	
	public void testFail27() throws Exception{
		helper1();
	}
	
	public void testFail28() throws Exception{
		helper1();
	}
	
	public void testFail29() throws Exception{
		helper1();
	}
		
	public void testFail30() throws Exception{
		helper1();
	}
	
	public void testFail31() throws Exception{
		helper1_0("m", "k", new String[]{"QString;"});
	}
	
	public void testFail32() throws Exception{
		helper1_0("m", "k", new String[]{"QObject;"});
	}
	
	public void testFail33() throws Exception{
		helper1_0("toString", "k", new String[0]);
	}
	
	public void testFail34() throws Exception{
		helper1_0("m", "k", new String[]{"QString;"});
	}
	
	public void testFail35() throws Exception{
		helper1();
	}
	
	public void testFail36() throws Exception{
		helper1();
	}
	
	public void testFail37() throws Exception{
		helper1();
	}
	
	public void testFail38() throws Exception{
		printTestDisabledMessage("needs fixing");
		//helper1();
	}
	
	public void test1() throws Exception{
		helper2();
	}	

	public void test10() throws Exception{
		helper2();
	}	
	
	public void test11() throws Exception{
		helper2();
	}	

	public void test12() throws Exception{
		helper2();
	}	

	public void test13() throws Exception{
		helper2();
	}	
	
	public void test14() throws Exception{
		helper2();
	}
	
	public void test15() throws Exception{
		helper2_0("m", "k", new String[]{Signature.SIG_INT});
	}		
	
	public void test16() throws Exception{
		helper2_0("m", "fred", new String[]{Signature.SIG_INT});
	}		
	
	public void test17() throws Exception{
		printTestDisabledMessage("");
		//helper2_0("m", "kk", new String[]{Signature.SIG_INT});
	}		
	
	public void test18() throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		ICompilationUnit cuC= createCUfromTestFile(getPackageP(), "C");
		
		IType classB= getType(cu, "B");
		RenameMethodRefactoring ref= RenameMethodRefactoring.createInstance(fgChangeCreator, classB.getMethod("m", new String[]{"I"}));
		ref.setNewName("kk");
		assertEquals("was supposed to pass", null, performRefactoring(ref));
		assertEquals("invalid renaming A", getFileContents(getOutputTestFileName("A")), cu.getSource());
		assertEquals("invalid renaming C", getFileContents(getOutputTestFileName("C")), cuC.getSource());
		
	}
	
	public void test19() throws Exception{
		helper2_0("m", "fred", new String[0]);
	}		
	
	public void test2() throws Exception{
		helper2_0("m", "fred", new String[0]);
	}		
	
	public void test20() throws Exception{
		helper2_0("m", "fred", new String[]{Signature.SIG_INT});
	}		
	
	public void test21() throws Exception{
		helper2_0("m", "fred", new String[]{Signature.SIG_INT});
	}
	
	public void test22() throws Exception{
		helper2();
	}		
	
	//anonymous inner class
	public void test23() throws Exception{
		helper2_fail();
	}		
	
	public void test24() throws Exception{
		helper2_0("m", "k", new String[]{"QString;"});
	}		
	
	public void test25() throws Exception{
		printTestDisabledMessage("waiting for 1GIIBC3: ITPJCORE:WINNT - search for method references - missing matches");
		//helper2();
	}		
	
	public void test26() throws Exception{
		helper2();
	}		
	
	public void test27() throws Exception{
		helper2();
	}		
	
	public void test28() throws Exception{
		helper2();
	}		
	
	public void test29() throws Exception{
		helper2();
	}		
	
	public void test30() throws Exception{
		helper2();
	}
	
	public void test31() throws Exception{
		helper2();
	}
	
	public void test32() throws Exception{
		helper2(false);
	}
	
	public void test33() throws Exception{
		helper2();
	}
	
	//anonymous inner class
	public void testAnon0() throws Exception{
		helper2_fail();
	}		
	
}