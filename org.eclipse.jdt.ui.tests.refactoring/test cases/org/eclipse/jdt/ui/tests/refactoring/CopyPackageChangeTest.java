package org.eclipse.jdt.ui.tests.refactoring;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.internal.core.refactoring.changes.CopyPackageChange;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

public class CopyPackageChangeTest extends RefactoringTest {

	private static final String REFACTORING_PATH= "CopyPackageChange/";
	private static final Class clazz= CopyPackageChangeTest.class;
	
	public CopyPackageChangeTest(String name) {
		super(name);
	}
	
	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}
	
	public static Test suite() {
		TestSuite suite= new TestSuite(clazz.getName());
		suite.addTest(noSetupSuite());
		return new MySetup(suite);
	}
	
	public static Test noSetupSuite() {
		return new TestSuite(clazz);
	}

	public void test0() throws Exception{
		ICompilationUnit cu= createCU(getPackageP(), "A.java", getFileContents(getRefactoringPath() + "A.java"));
		
		IPackageFragmentRoot newRoot= JavaProjectHelper.addSourceContainer(MySetup.getProject(), "newName");
		
		String packName= getPackageP().getElementName();
		CopyPackageChange change= new CopyPackageChange(getPackageP(), newRoot);
		performChange(change);
		IPackageFragment copied= newRoot.getPackageFragment(packName);
		assertTrue("copied.exists()", copied.exists());
		assertTrue(copied.getChildren().length == 1);
	}
}

