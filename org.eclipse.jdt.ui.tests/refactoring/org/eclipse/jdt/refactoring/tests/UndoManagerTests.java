/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.refactoring.tests;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.internal.core.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.core.refactoring.base.Refactoring;
import org.eclipse.jdt.refactoring.tests.infra.TestExceptionHandler;
import org.eclipse.jdt.testplugin.TestPluginLauncher;

public class UndoManagerTests extends RefactoringTest {
	
	private static final Class clazz= UndoManagerTests.class;
	public UndoManagerTests(String name) {
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
	
	private void checkState(boolean undo, boolean redo, int undoCount, int redoCount){
		checkState(0, undo, redo, undoCount, redoCount);
	}
	
	private void checkState(int iterationCount, boolean undo, boolean redo, int undoCount, int redoCount){
		assertTrue(iterationCount + " undo", undo == Refactoring.getUndoManager().anythingToUndo());
		assertTrue(iterationCount + " redo", redo == Refactoring.getUndoManager().anythingToRedo());
		//assertEquals(iterationCount + "undo stack", undoCount, Refactoring.getUndoManager().getRefactoringLog().size());
		//assertEquals(iterationCount + "redo stack", redoCount, Refactoring.getUndoManager().getRedoStack().size());
	}
	
	private void performUndo() throws Exception {
		Refactoring.getUndoManager().performUndo(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
	}
	
	private void performRedo() throws Exception {
		Refactoring.getUndoManager().performRedo(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
	}
	
	public void test0() throws Exception{
		checkState(false, false, 0, 0);
	}
	
	public void test1() throws Exception{	
		performRefactoring(new NullRefactoring());
		checkState(true, false, 1, 0);
	}
	
	public void test2() throws Exception{	
		performRefactoring(new NullRefactoring());
		performUndo();
		checkState(false, true, 0, 1);
	}
	
	public void test3() throws Exception{	
		performRefactoring(new NullRefactoring());
		performUndo();
		performRedo();
		checkState(true, false, 1, 0);
	}
	
	public void test4() throws Exception{	
		performRefactoring(new NullRefactoring());
		performUndo();
		performRedo();
		performUndo();
		checkState(false, true, 0, 1);
	}

	public void test5() throws Exception{	
		performRefactoring(new NullRefactoring());
		performUndo();
		performRedo();
		performRefactoring(new NullRefactoring());
		checkState(true, false, 2, 0);
	}
	
	public void test6() throws Exception{	
		performRefactoring(new NullRefactoring());
		performUndo();
		performRedo();
		performRefactoring(new NullRefactoring());
		performUndo();
		performUndo();
		checkState(false, true, 0, 2);
	}	
	
	public void test7() throws Exception{	
		performRefactoring(new NullRefactoring());
		performUndo();
		performRedo();
		performRefactoring(new NullRefactoring());
		performUndo();
		checkState(true, true, 1, 1);
	}	
	
	public void test8() throws Exception{	
		int limit= 10;
		for (int i= 0; i < limit; i++){
			checkState(i, i != 0, false, i, 0);			
			performRefactoring(new NullRefactoring());
		}
		for (int i= 0; i < limit; i++){
			checkState(i, i != limit, i != 0, limit - i, i);			
			performUndo();
		}
		
		for (int i= 0; i < limit; i++){
			checkState(i, i != 0, i != limit, i, limit - i);			
			performRedo();
		}
	}
	

	public void test9() throws Exception{	
		performRefactoring(new NullRefactoring());
		performUndo();
		performRedo();
		performRefactoring(new NullRefactoring());
		performUndo();
		Refactoring.getUndoManager().flush();
		checkState(false, false, 0, 0);
	}		
}