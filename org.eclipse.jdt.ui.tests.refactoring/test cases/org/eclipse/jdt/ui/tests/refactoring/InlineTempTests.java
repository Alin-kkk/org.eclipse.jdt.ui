package org.eclipse.jdt.ui.tests.refactoring;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.corext.*;
import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring;

import org.eclipse.jdt.ui.tests.refactoring.infra.TextRangeUtil;

public class InlineTempTests extends RefactoringTest {

	private static final Class clazz= InlineTempTests.class;
	private static final String REFACTORING_PATH= "InlineTemp/";
	
	public InlineTempTests(String name) {
		super(name);
	}
	
	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}
	
	public static Test suite() {
		return new MySetup(new TestSuite(clazz));
	}
	
	private String getSimpleTestFileName(boolean canInline, boolean input){
		String fileName = "A_" + getName();
		if (canInline)
			fileName += input ? "_in": "_out";
		return fileName + ".java"; 
	}
	
	private String getTestFileName(boolean canInline, boolean input){
		String fileName= TEST_PATH_PREFIX + getRefactoringPath();
		fileName += (canInline ? "canInline/": "cannotInline/");
		return fileName + getSimpleTestFileName(canInline, input);
	}
	
	private String getFailingTestFileName(){
		return getTestFileName(false, false);
	}
	private String getPassingTestFileName(boolean input){
		return getTestFileName(true, input);
	}

	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, boolean canInline, boolean input) throws Exception {
		return createCU(pack, getSimpleTestFileName(canInline, input), getFileContents(getTestFileName(canInline, input)));
	}
	
	private ISourceRange getSelection(ICompilationUnit cu) throws Exception{
		String source= cu.getSource();
		int offset= source.indexOf(ExtractMethodTests.SQUARE_BRACKET_OPEN);
		int end= source.indexOf(ExtractMethodTests.SQUARE_BRACKET_CLOSE);
		return new SourceRange(offset, end - offset);
	}
	
	private void helper1(ICompilationUnit cu, ISourceRange selection) throws Exception{
		InlineTempRefactoring ref= new InlineTempRefactoring(cu, selection.getOffset(), selection.getLength());
		
		RefactoringStatus result= performRefactoring(ref);
		assertEquals("precondition was supposed to pass", null, result);
		
		IPackageFragment pack= (IPackageFragment)cu.getParent();
		String newCuName= getSimpleTestFileName(true, true);
		ICompilationUnit newcu= pack.getCompilationUnit(newCuName);
		assertTrue(newCuName + " does not exist", newcu.exists());
		assertEquals("incorrect inlining", getFileContents(getTestFileName(true, false)), newcu.getSource());
	}
	
	private void helper1(int startLine, int startColumn, int endLine, int endColumn) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), true, true);
		ISourceRange selection= TextRangeUtil.getSelection(cu, startLine, startColumn, endLine, endColumn);
		helper1(cu, selection);
	}
	
	private void helper1() throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), true, true);
		helper1(cu, getSelection(cu));
	}	
	
	private void helper2() throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), false, true);
		ISourceRange selection= getSelection(cu);
		InlineTempRefactoring ref= new InlineTempRefactoring(cu, selection.getOffset(), selection.getLength());
		
		RefactoringStatus result= performRefactoring(ref);
		assertNotNull("precondition was supposed to fail", result);
	}
	
	//--- tests 
	
	public void test0() throws Exception{
		helper1();
	}

	public void test1() throws Exception{
		helper1();
	}

	public void test2() throws Exception{
		helper1();
	}
	
	public void test3() throws Exception{
		helper1();
	}

	public void test4() throws Exception{
		helper1();
	}

	public void test5() throws Exception{
		helper1();
	}

	public void test6() throws Exception{
		//printTestDisabledMessage("bug#6429 declaration source start incorrect on local variable");
		helper1(9, 13, 9, 14);
	}

	public void test7() throws Exception{
		helper1();
	}
	
	public void test8() throws Exception{
		//printTestDisabledMessage("bug#6429 declaration source start incorrect on local variable");
		helper1(5, 13, 5, 14);
	}
	
	public void test9() throws Exception{
		helper1();
	}
	
	public void test10() throws Exception{
//		printTestDisabledMessage("regression test for bug#9001");
		helper1(4, 21, 4, 25);
	}
	
	public void test11() throws Exception{
		helper1(5, 21, 5, 25);
	}	

	public void test12() throws Exception{
		helper1(5, 15, 5, 19);
	}	

	public void test13() throws Exception{
		helper1(5, 17, 5, 18);
	}	
	
	public void test14() throws Exception{
		printTestDisabledMessage("regression for bug 11664");		
//		helper1(4, 13, 4, 14);
	}	
	
	public void test15() throws Exception{
		printTestDisabledMessage("regression for bug 11664");		
//		helper1(4, 19, 4, 20);
	}	
	
	public void test16() throws Exception{
//		printTestDisabledMessage("regression test for 10751");		
		helper1(5, 17, 5, 24);
	}	
	
	public void test17() throws Exception{
//		printTestDisabledMessage("regression test for 12200");		
		helper1(8, 18, 8, 21);
	}	

	public void test18() throws Exception{
//		printTestDisabledMessage("regression test for 12200");		
		helper1(6, 18, 6, 21);
	}	

	public void test19() throws Exception{
//		printTestDisabledMessage("regression test for 12212");		
		helper1(6, 19, 6, 19);
	}	

	public void test20() throws Exception{
		printTestDisabledMessage("regression test for 16054");		
//		helper1(4, 17, 4, 18);
	}	

	//------
	
	public void testFail0() throws Exception{
		printTestDisabledMessage("compile errors are ok now");
//		helper2();
	}

	public void testFail1() throws Exception{
		helper2();
	}

	public void testFail2() throws Exception{
		helper2();
	}

	public void testFail3() throws Exception{
		helper2();
	}

	public void testFail4() throws Exception{
		helper2();
	}

	public void testFail5() throws Exception{
		helper2();
	}

	public void testFail6() throws Exception{
		helper2();
	}

	public void testFail7() throws Exception{
		helper2();
	}

	public void testFail8() throws Exception{
		helper2();
	}
	
}

