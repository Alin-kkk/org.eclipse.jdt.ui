package org.eclipse.jdt.ui.tests.actions;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;

import org.eclipse.jdt.ui.tests.refactoring.MySetup;
import org.eclipse.jdt.ui.tests.refactoring.RefactoringTest;
import org.eclipse.jdt.ui.tests.refactoring.infra.TextRangeUtil;

import org.eclipse.jdt.internal.ui.javaeditor.structureselection.GoToNextPreviousMemberAction;

public class GoToNextPreviousMemberActionTests extends RefactoringTest{

	private static final Class clazz= GoToNextPreviousMemberActionTests.class;
	private static final String REFACTORING_PATH= "GoToNextPreviousMemberAction/";
	
	public GoToNextPreviousMemberActionTests(String name){
		super(name);
	}
	
	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}
	
	public static Test suite() {
		return new MySetup(new TestSuite(clazz));
	}
	
	private String getSimpleTestFileName(){
		return "A_" + getName() + ".java";
	}
	
	private String getTestFileName(){
		return TEST_PATH_PREFIX + getRefactoringPath() + getSimpleTestFileName();
	}
	
	//------------
	protected ICompilationUnit createCUfromTestFile() throws Exception {
		return createCU(getPackageP(), getSimpleTestFileName(), getFileContents(getTestFileName()));
	}
	
	private void helper(int startLine, int startColumn, int endLine, int endColumn, 
											int expectedStartLine, int expectedStartColumn, boolean isSelectNext) throws Exception{
		ICompilationUnit cu= createCUfromTestFile();
		ISourceRange selection= TextRangeUtil.getSelection(cu, startLine, startColumn, endLine, endColumn);
		ISourceRange actualNewRange= new GoToNextPreviousMemberAction(isSelectNext).getNewSelectionRange(selection, cu);
		ISourceRange expectedNewRange= TextRangeUtil.getSelection(cu, expectedStartLine, expectedStartColumn, expectedStartLine, expectedStartColumn);
		assertEquals("incorrect selection offset", expectedNewRange.getOffset(), actualNewRange.getOffset());
		assertEquals("incorrect selection length", expectedNewRange.getLength(), actualNewRange.getLength());
	}	

	private void helperNext(int startLine, int startColumn, int expectedStartLine, int expectedStartColumn) throws Exception{
		helper(startLine, startColumn, startLine, startColumn, expectedStartLine, expectedStartColumn, true);										
	}

	private void helperPrevious(int startLine, int startColumn, int expectedStartLine, int expectedStartColumn) throws Exception{
		helper(startLine, startColumn, startLine, startColumn, expectedStartLine, expectedStartColumn, false);			
	}

	//----
	public void testPrevious0() throws Exception{
		helperPrevious(6, 5, 5, 11);
	}

	public void testPrevious1() throws Exception{
		helperPrevious(8, 5, 7, 6);
	}

	public void testPrevious2() throws Exception{
		helperPrevious(3, 1, 3, 1);
	}

	public void testPrevious3() throws Exception{
		helperPrevious(15, 9, 13, 5);
	}

	public void testPrevious4() throws Exception{
		helperPrevious(19, 1, 18, 9);
	}

	public void testPrevious5() throws Exception{
		helperPrevious(31, 10, 27, 10);
	}

	public void testPrevious6() throws Exception{
		helperPrevious(35, 3, 34, 2);
	}

	public void testNext0() throws Exception{
		helperNext(3, 1, 4, 7);
	}

	public void testNext1() throws Exception{
		helperNext(27, 10, 31, 10);
	}

	public void testNext2() throws Exception{
		helperNext(35, 2, 35, 2);
	}
}
