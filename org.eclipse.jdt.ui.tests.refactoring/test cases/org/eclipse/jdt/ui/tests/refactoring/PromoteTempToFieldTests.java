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
package org.eclipse.jdt.ui.tests.refactoring;

import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.Modifier;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.code.PromoteTempToFieldRefactoring;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.ui.tests.refactoring.infra.SourceCompareUtil;
import org.eclipse.jdt.ui.tests.refactoring.infra.TextRangeUtil;

public class PromoteTempToFieldTests extends RefactoringTest{
	
	private static final Class clazz= PromoteTempToFieldTests.class;
	private static final String REFACTORING_PATH= "PromoteTempToField/";
    private Object fCompactPref;
	
	public PromoteTempToFieldTests(String name){
		super(name);
	}
	
	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}
	
	public static Test suite() {
		return new MySetup(new TestSuite(clazz));
	}
	
	protected void setUp() throws Exception {
		super.setUp();
		Hashtable options= JavaCore.getOptions();
		fCompactPref= options.get(JavaCore.FORMATTER_COMPACT_ASSIGNMENT);
		options.put(JavaCore.FORMATTER_COMPACT_ASSIGNMENT, JavaCore.COMPACT);
		JavaCore.setOptions(options);
	}
	
	protected void tearDown() throws Exception {
		super.tearDown();
		Hashtable options= JavaCore.getOptions();
		options.put(JavaCore.FORMATTER_COMPACT_ASSIGNMENT, fCompactPref);
		JavaCore.setOptions(options);	
	}
	
	private String getSimpleTestFileName(boolean canRename, boolean input){
		String fileName = "A_" + getName();
		if (canRename)
			fileName += input ? "_in": "_out";
		return fileName + ".java"; 
	}

	private String getSimpleEnablementTestFileName(){
		return "A_" + getName() + ".java"; 
	}
	
	private String getTestFileName(boolean canRename, boolean input){
		String fileName= TEST_PATH_PREFIX + getRefactoringPath();
		fileName += (canRename ? "canPromote/": "cannotPromote/");
		return fileName + getSimpleTestFileName(canRename, input);
	}
	
	private String getEnablementTestFileName(){
		String fileName= TEST_PATH_PREFIX + getRefactoringPath();
		fileName += "testEnablement/";
		return fileName + getSimpleEnablementTestFileName();
	}
	
	
	//------------
	protected final ICompilationUnit createCUfromTestFile(IPackageFragment pack, boolean canPromote, boolean input) throws Exception {
		return createCU(pack, getSimpleTestFileName(canPromote, input), getFileContents(getTestFileName(canPromote, input)));
	}

	protected final ICompilationUnit createCUfromEnablementTestFile(IPackageFragment pack) throws Exception {
		return createCU(pack, getSimpleEnablementTestFileName(), getFileContents(getEnablementTestFileName()));
	}
	
	private void passHelper(int startLine, int startColumn, int endLine, int endColumn, 
						  String newName,
						  boolean declareStatic,
						  boolean declareFinal,
						  int initializeIn,
						  int accessModifier) throws Exception{
						  	
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), true, true);						  	
		ISourceRange selection= TextRangeUtil.getSelection(cu, startLine, startColumn, endLine, endColumn);
        PromoteTempToFieldRefactoring ref= new PromoteTempToFieldRefactoring(cu, selection.getOffset(), selection.getLength(), JavaPreferencesSettings.getCodeGenerationSettings());

		RefactoringStatus activationResult= ref.checkActivation(new NullProgressMonitor());	
		assertTrue("activation was supposed to be successful", activationResult.isOK());

        ref.setFieldName(newName);
        ref.setDeclareFinal(declareFinal);
        ref.setDeclareStatic(declareStatic);
        ref.setInitializeIn(initializeIn);
        ref.setVisibility(accessModifier);
		
		RefactoringStatus checkInputResult= ref.checkInput(new NullProgressMonitor());
		assertTrue("precondition was supposed to pass", checkInputResult.isOK());

		performChange(ref.createChange(new NullProgressMonitor()));
        		
		IPackageFragment pack= (IPackageFragment)cu.getParent();
		String newCuName= getSimpleTestFileName(true, true);
		ICompilationUnit newcu= pack.getCompilationUnit(newCuName);
		assertTrue(newCuName + " does not exist", newcu.exists());
		assertEquals("incorrect changes", getFileContents(getTestFileName(true, false)), newcu.getSource());
		SourceCompareUtil.compare(newcu.getSource(), getFileContents(getTestFileName(true, false)));
	}

	private void failHelper(int startLine, int startColumn, int endLine, int endColumn, 
						  String newName,
						  boolean declareStatic,
						  boolean declareFinal,
						  int initializeIn,
						  int accessModifier,
						  int expectedSeverity) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), false, true);						  	
		ISourceRange selection= TextRangeUtil.getSelection(cu, startLine, startColumn, endLine, endColumn);
        PromoteTempToFieldRefactoring ref= new PromoteTempToFieldRefactoring(cu, selection.getOffset(), selection.getLength(), JavaPreferencesSettings.getCodeGenerationSettings());
		
		RefactoringStatus result= ref.checkActivation(new NullProgressMonitor());	
        ref.setFieldName(newName);
        ref.setDeclareFinal(declareFinal);
        ref.setDeclareStatic(declareStatic);
        ref.setInitializeIn(initializeIn);
        ref.setVisibility(accessModifier);
		result.merge(ref.checkInput(new NullProgressMonitor()));
		if (result.isOK())
			result= null;
		assertNotNull("precondition was supposed to fail",result);

		assertEquals("incorrect severity:", expectedSeverity, result.getSeverity());
	}

	private void enablementHelper(int startLine, int startColumn, int endLine, int endColumn,
						  String newName,
						  boolean declareStatic,
						  boolean declareFinal,
						  int initializeIn,
						  int accessModifier,
						  boolean expectedCanEnableSettingFinal,
						  boolean expectedCanEnableSettingStatic,
						  boolean expectedCanEnableInitInField,
						  boolean expectedCanEnableInitInMethod,
  						  boolean expectedCanEnableInitInConstructors) throws Exception{
		ICompilationUnit cu= createCUfromEnablementTestFile(getPackageP());
		ISourceRange selection= TextRangeUtil.getSelection(cu, startLine, startColumn, endLine, endColumn);
        PromoteTempToFieldRefactoring ref= new PromoteTempToFieldRefactoring(cu, selection.getOffset(), selection.getLength(), JavaPreferencesSettings.getCodeGenerationSettings());
		RefactoringStatus result= ref.checkActivation(new NullProgressMonitor());
		ref.setFieldName(newName);
        ref.setDeclareFinal(declareFinal);
        ref.setDeclareStatic(declareStatic);
        ref.setInitializeIn(initializeIn);
        ref.setVisibility(accessModifier);
		
		assertEquals("activation checking was supposed to pass", RefactoringStatus.OK, result.getSeverity());
		
		assertEquals("incorrect in-constructor enablement", expectedCanEnableInitInConstructors, 	ref.canEnableSettingDeclareInConstructors());
		assertEquals("incorrect in-field enablement", 		expectedCanEnableInitInField, 			ref.canEnableSettingDeclareInFieldDeclaration());
		assertEquals("incorrect in-method enablement", 		expectedCanEnableInitInMethod, 			ref.canEnableSettingDeclareInMethod());
		assertEquals("incorrect static enablement", 		expectedCanEnableSettingStatic, 		ref.canEnableSettingStatic());
		assertEquals("incorrect final enablement", 			expectedCanEnableSettingFinal, 			ref.canEnableSettingFinal());
	}
	private void enablementHelper1(int startLine, int startColumn, int endLine, int endColumn,
						  boolean expectedCanEnableSettingFinal,
						  boolean expectedCanEnableSettingStatic,
						  boolean expectedCanEnableInitInField,
						  boolean expectedCanEnableInitInMethod,
  						  boolean expectedCanEnableInitInConstructors) throws Exception{
  	   enablementHelper(startLine, startColumn, endLine, endColumn, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD, Modifier.PRIVATE, 
  	   				expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}	

	///---------------------- tests -------------------------//
	
	public void testEnablement0() throws Exception{
        boolean expectedCanEnableInitInConstructors	= true;
        boolean expectedCanEnableInitInMethod			= true;
        boolean expectedCanEnableInitInField			= true;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= true;
        
        String newName= "i";
		boolean declareStatic = false;
	  	boolean declareFinal= false;
	  	int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_FIELD;
	  	int accessModifier= Modifier.PRIVATE;
        
		enablementHelper(5, 13, 5, 14, newName, declareStatic, declareFinal, initializeIn, accessModifier,
					expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}

	public void testEnablement1() throws Exception{
        boolean expectedCanEnableInitInConstructors	= false;
        boolean expectedCanEnableInitInMethod			= false;
        boolean expectedCanEnableInitInField			= false;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= false;
		enablementHelper1(5, 13, 5, 14, expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}
	
	public void testEnablement2() throws Exception{
        boolean expectedCanEnableInitInConstructors	= false;
        boolean expectedCanEnableInitInMethod			= false;
        boolean expectedCanEnableInitInField			= false;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= false;
		enablementHelper1(5, 13, 5, 14, expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}
	
	public void testEnablement3() throws Exception{
        boolean expectedCanEnableInitInConstructors	= true;
        boolean expectedCanEnableInitInMethod			= true;
        boolean expectedCanEnableInitInField			= true;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= false;
		enablementHelper1(5, 13, 5, 14, expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}
	
	public void testEnablement4() throws Exception{
        boolean expectedCanEnableInitInConstructors	= false;
        boolean expectedCanEnableInitInMethod			= true;
        boolean expectedCanEnableInitInField			= true;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= true;

        String newName= "i";
		boolean declareStatic = false;
	  	boolean declareFinal= false;
	  	int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_FIELD;
	  	int accessModifier= Modifier.PRIVATE;

		enablementHelper(5, 13, 5, 14, newName, declareStatic, declareFinal, initializeIn, accessModifier,
					expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}
	
	public void testEnablement5() throws Exception{
        boolean expectedCanEnableInitInConstructors	= false;
        boolean expectedCanEnableInitInMethod			= true;
        boolean expectedCanEnableInitInField			= true;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= true;
        
        String newName= "i";
		boolean declareStatic = false;
	  	boolean declareFinal= false;
	  	int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_FIELD;
	  	int accessModifier= Modifier.PRIVATE;
        
		enablementHelper(7, 21, 7, 22, newName, declareStatic, declareFinal, initializeIn, accessModifier,
					expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}
	
	public void testEnablement6() throws Exception{
        boolean expectedCanEnableInitInConstructors	= false;
        boolean expectedCanEnableInitInMethod			= false;
        boolean expectedCanEnableInitInField			= false;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= false;
		enablementHelper1(7, 21, 7, 22, expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}
	
	public void testEnablement7() throws Exception{
        boolean expectedCanEnableInitInConstructors	= false;
        boolean expectedCanEnableInitInMethod			= true;
        boolean expectedCanEnableInitInField			= true;
        boolean expectedCanEnableSettingStatic			= false;
        boolean expectedCanEnableSettingFinal			= false;
		enablementHelper1(5, 13, 5, 14, expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}

	public void testEnablement8() throws Exception{
        boolean expectedCanEnableInitInConstructors	= true;
        boolean expectedCanEnableInitInMethod			= true;
        boolean expectedCanEnableInitInField			= true;
        boolean expectedCanEnableSettingStatic			= false;
        boolean expectedCanEnableSettingFinal			= true;
        
        String newName= "i";
		boolean declareStatic = false;
	  	boolean declareFinal= false;
	  	int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR;
	  	int accessModifier= Modifier.PRIVATE;
        
		enablementHelper(4, 13, 4, 14, newName, declareStatic, declareFinal, initializeIn, accessModifier,
					expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}

	public void testEnablement9() throws Exception{
        boolean expectedCanEnableInitInConstructors	= false;
        boolean expectedCanEnableInitInMethod			= true;
        boolean expectedCanEnableInitInField			= true;
        boolean expectedCanEnableSettingStatic			= true;
        boolean expectedCanEnableSettingFinal			= false;
        
        String newName= "i";
		boolean declareStatic = true;
	  	boolean declareFinal= false;
	  	int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
	  	int accessModifier= Modifier.PRIVATE;
        
		enablementHelper(4, 13, 4, 14, newName, declareStatic, declareFinal, initializeIn, accessModifier,
					expectedCanEnableSettingFinal, expectedCanEnableSettingStatic, expectedCanEnableInitInField, expectedCanEnableInitInMethod, expectedCanEnableInitInConstructors);
	}
	
	///---- test failing preconditions --------------
	
	public void testFail0() throws Exception{
		failHelper(3, 16, 3, 17, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD, Modifier.PRIVATE, RefactoringStatus.FATAL);
	}

	public void testFail1() throws Exception{
		failHelper(5, 28, 5, 29, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD, Modifier.PRIVATE, RefactoringStatus.FATAL);
	}

	public void testFail2() throws Exception{
		failHelper(5, 15, 5, 16, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD, Modifier.PRIVATE, RefactoringStatus.FATAL);
	}

	public void testFail3() throws Exception{
		failHelper(5, 16, 5, 17, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR, Modifier.PRIVATE, RefactoringStatus.FATAL);
	}

	public void testFail4() throws Exception{
		failHelper(7, 13, 7, 14, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR, Modifier.PRIVATE, RefactoringStatus.FATAL);
	}

	public void testFail5() throws Exception{
		failHelper(6, 13, 6, 14, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD, Modifier.PRIVATE, RefactoringStatus.FATAL);
	}

	public void testFail6() throws Exception{
		failHelper(4, 18, 4, 19, "i", false, false, PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR, Modifier.PRIVATE, RefactoringStatus.FATAL);
	}
	
	///----------- tests of transformation ------------

	public void test0() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(5, 13, 5, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}

	public void test1() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_FIELD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(5, 13, 5, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}

	public void test2() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(7, 13, 7, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test3() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(9, 13, 9, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test4() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(5, 13, 5, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test5() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(6, 21, 6, 22, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test6() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_FIELD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(6, 21, 6, 22, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test7() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_FIELD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(4, 13, 4, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test8() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(4, 13, 4, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test9() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(5, 13, 5, 14, "field", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test10() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR;
        boolean declareFinal= true;
        boolean declareStatic= false;
		passHelper(7, 13, 7, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test11() throws Exception{
        int accessModifier= Modifier.PUBLIC;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(7, 13, 7, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
	
	public void test12() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= true;
		passHelper(5, 13, 5, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}

	public void test13() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= true;
		passHelper(5, 13, 5, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}

	public void test14() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= true;
		passHelper(5, 19, 5, 20, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}

	public void test15() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= true;
		passHelper(5, 19, 5, 20, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}

	public void test16() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_CONSTRUCTOR;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(10, 13, 10, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}

	public void test17() throws Exception{
        int accessModifier= Modifier.PRIVATE;
        int initializeIn= PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD;
        boolean declareFinal= false;
        boolean declareStatic= false;
		passHelper(4, 13, 4, 14, "i", declareStatic, declareFinal, initializeIn, accessModifier);
	}
}
