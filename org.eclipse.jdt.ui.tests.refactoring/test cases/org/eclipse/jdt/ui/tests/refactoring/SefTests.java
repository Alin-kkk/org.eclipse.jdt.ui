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

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Preferences;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.tests.refactoring.infra.TestExceptionHandler;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldRefactoring;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class SefTests extends AbstractSelectionTestCase {

	private static SefTestSetup fgTestSetup;
	
	public SefTests(String name) {
		super(name);
	}
	
	public static Test suite() {
		fgTestSetup= new SefTestSetup(new TestSuite(SefTests.class));
		return fgTestSetup;
	}

	protected IPackageFragmentRoot getRoot() {
		return fgTestSetup.getRoot();
	}
	
	protected String getResourceLocation() {
		return "SefWorkSpace/SefTests/";
	}
	
	protected String adaptName(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1) + ".java";
	}	
	
	protected void performTest(IPackageFragment packageFragment, String id, String outputFolder, String fieldName) throws Exception {
		IProgressMonitor pm= new NullProgressMonitor();
		ICompilationUnit unit= createCU(packageFragment, id);
		IField field= getField(unit, fieldName);
		assertNotNull(field);
		CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();
		
		Preferences preferences= JavaCore.getPlugin().getPluginPreferences();
		preferences.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "");
		preferences.setValue(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, "");
		preferences.setValue(JavaCore.CODEASSIST_FIELD_SUFFIXES, "");
		preferences.setValue(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES, "");

		SelfEncapsulateFieldRefactoring refactoring= new SelfEncapsulateFieldRefactoring(field, settings);
		RefactoringStatus status= refactoring.checkPreconditions(pm);
		assertTrue(!status.hasFatalError());
		IChange change= refactoring.createChange(pm);
		assertNotNull(change);
		ChangeContext context= new ChangeContext(new TestExceptionHandler());
		change.aboutToPerform(context, new NullProgressMonitor());
		change.perform(context, pm);
		change.performed();
		assertNotNull(change.getUndoChange());
		String source= unit.getSource();
		String out= getProofedContent(outputFolder, id);
		compareSource(source, out);
	}
	
	protected void performInvalidTest(IPackageFragment packageFragment, String id, String fieldName) throws Exception {
		ICompilationUnit unit= createCU(packageFragment, id);
		IField field= getField(unit, fieldName);
		assertNotNull(field);
		CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();

		Preferences preferences= JavaCore.getPlugin().getPluginPreferences();
		preferences.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "");
		preferences.setValue(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, "");
		preferences.setValue(JavaCore.CODEASSIST_FIELD_SUFFIXES, "");
		preferences.setValue(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES, "");


		SelfEncapsulateFieldRefactoring refactoring= new SelfEncapsulateFieldRefactoring(field, settings);
		RefactoringStatus status= refactoring.checkPreconditions(new NullProgressMonitor());
		assertTrue(status.hasError());
	}	
	
	private static IField getField(ICompilationUnit unit, String fieldName) throws Exception {
		IField result= null;
		IType[] types= unit.getAllTypes();
		for (int i= 0; i < types.length; i++) {
			IType type= types[i];
			result= type.getField(fieldName);
			if (result != null && result.exists())
				break;
		}
		return result;
	}

	private IPackageFragment getObjectPackage() throws JavaModelException {
		return fgTestSetup.getObjectPackage();
 	}
	
	private IPackageFragment getBasePackage() throws JavaModelException {
		return fgTestSetup.getBasePackage();
 	}
	
	private IPackageFragment getInvalidPackage() throws JavaModelException {
		return fgTestSetup.getInvalidPackage();
	}
	
	private void objectTest(String fieldName) throws Exception {
		performTest(getObjectPackage(), getName(), "object_out", fieldName);
	}
	
	private void baseTest(String fieldName) throws Exception {
		performTest(getBasePackage(), getName(), "base_out", fieldName);
	}
	
	private void invalidTest(String fieldName) throws Exception {
		performInvalidTest(getInvalidPackage(), getName(), fieldName);
	}
	
	//=====================================================================================
	// Invalid
	//=====================================================================================
	
	public void testPostfixExpression() throws Exception {
		invalidTest("field");
	}
	
	public void testInvalidOverwrite() throws Exception {
		invalidTest("field");
	}
	
	//=====================================================================================
	// Primitiv Data Test
	//=====================================================================================
	
	public void testPrefixInt() throws Exception {
		baseTest("field");
	}
	
	public void testPrefixBoolean() throws Exception {
		baseTest("field");
	}
	
	public void testPostfixInt() throws Exception {
		baseTest("field");
	}
	
	public void testThisExpression() throws Exception {
		baseTest("field");
	}
	
	public void testThisExpressionInner() throws Exception {
		baseTest("field");
	}
	
	//=====================================================================================
	// Basic Object Test
	//=====================================================================================
	
	public void testSimpleRead() throws Exception {
		objectTest("field");
	}
	
	public void testSimpleWrite() throws Exception {
		objectTest("field");
	}
	
	public void testSimpleReadWrite() throws Exception {
		objectTest("field");
	}
	
	public void testNestedRead() throws Exception {
		objectTest("field");
	}
	
	public void testArrayRead() throws Exception {
		objectTest("field");
	}
	
	public void testSetterInAssignment() throws Exception {
		objectTest("field");
	}
	
	public void testSetterInExpression() throws Exception {
		objectTest("field");
	}
	
	public void testSetterInInitialization() throws Exception {
		objectTest("field");
	}
	
	public void testSetterAsReceiver() throws Exception {
		objectTest("field");
	}
	
	public void testCompoundWrite() throws Exception {
		objectTest("field");
	}
	
	public void testCompoundWrite2() throws Exception {
		objectTest("field");
	}
	
	public void testCompoundWrite3() throws Exception {
		objectTest("field");
	}
	
	public void testFinalField() throws Exception {
		objectTest("field");
	}	
}
