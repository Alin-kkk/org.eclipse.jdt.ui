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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceManipulation;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.ITypeNameRequestor;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.tests.refactoring.infra.RefactoringTestPlugin;
import org.eclipse.jdt.ui.tests.refactoring.infra.TestExceptionHandler;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

public abstract class RefactoringTest extends TestCase {

	private IPackageFragmentRoot fRoot;
	private IPackageFragment fPackageP;
	private IJavaProject fJavaProject;
	
	public boolean fIsVerbose= false;

	public static final String TEST_PATH_PREFIX= "";

	protected static final String TEST_INPUT_INFIX= "/in/";
	protected static final String TEST_OUTPUT_INFIX= "/out/";
	protected static final String CONTAINER= "src";
	
	public RefactoringTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		fJavaProject= MySetup.getProject();
		fRoot= MySetup.getDefaultSourceFolder();
		fPackageP= MySetup.getPackageP();
		
		if (fIsVerbose){
			System.out.println("\n---------------------------------------------");
			System.out.println("\nTest:" + getClass() + "." + getName());
		}	
		Refactoring.getUndoManager().flush();
	}

	protected void performDummySearch() throws Exception {
		performDummySearch(fPackageP);
	}	

	protected void tearDown() throws Exception {
		performDummySearch();
		
		if (fPackageP.exists()){	
			IJavaElement[] kids= fPackageP.getChildren();
			for (int i= 0; i < kids.length; i++){
				if (kids[i] instanceof ISourceManipulation){
					try{
						if (kids[i].exists() && ! kids[i].isReadOnly())
							((ISourceManipulation)kids[i]).delete(true, null);
					}	catch (JavaModelException e){
						//try to delete'em all
					}
				}	
			}
		}	
		
		if (fRoot.exists()){
			IJavaElement[] packages= fRoot.getChildren();
			for (int i= 0; i < packages.length; i++){
				try{
					IPackageFragment pack= (IPackageFragment)packages[i];
					if (! pack.equals(fPackageP) && pack.exists() && ! pack.isReadOnly())
						pack.delete(true, null);
				}	catch (JavaModelException e){
					//try to delete'em all
				}	
			}
		}
	}

	protected IPackageFragmentRoot getRoot() {
		return fRoot;
	}

	protected IPackageFragment getPackageP() {
		return fPackageP;
	}

	protected final RefactoringStatus performRefactoring(IRefactoring ref) throws JavaModelException {
		RefactoringStatus status= ref.checkPreconditions(new NullProgressMonitor());
		if (!status.isOK())
			return status;

		IChange change= ref.createChange(new NullProgressMonitor());
		performChange(change);
		
		// XXX: this should be done by someone else
		Refactoring.getUndoManager().addUndo(ref.getName(), change.getUndoChange());

		return null;
	}
	
	protected final RefactoringStatus performRefactoringWithStatus(IRefactoring ref) throws JavaModelException {
		RefactoringStatus status= ref.checkPreconditions(new NullProgressMonitor());
		if (status.hasFatalError())
			return status;

		IChange change= ref.createChange(new NullProgressMonitor());
		performChange(change);
		
		// XXX: this should be done by someone else
		Refactoring.getUndoManager().addUndo(ref.getName(), change.getUndoChange());

		return status;
	}
	
	protected void performChange(IChange change) throws JavaModelException{
		change.aboutToPerform(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
		try {
			change.perform(new ChangeContext(new TestExceptionHandler()), new NullProgressMonitor());
		} finally {
			change.performed();
		}
	}

	/****************  helpers  ******************/
	/**** mostly not general, just shortcuts *****/

	protected IType getType(ICompilationUnit cu, String name) throws JavaModelException {
		IType[] types= cu.getAllTypes();
		for (int i= 0; i < types.length; i++)
			if (JavaModelUtil.getTypeQualifiedName(types[i]).equals(name) ||
			    types[i].getElementName().equals(name))
				return types[i];
		return null;
	}
	
	/**
	 * subclasses override to inform about the location of their test cases
	 */
	protected String getRefactoringPath() {
		return "";
	}

	/**
	 *  example "RenameType/"
	 */
	protected String getTestPath() {
		return TEST_PATH_PREFIX + getRefactoringPath();
	}

	/**
	 * @param cuName
	 * @param infix
	 * example "RenameTest/test0 + infix + cuName.java"
	 */
	protected String createTestFileName(String cuName, String infix) {
		return getTestPath() + getName() + infix + cuName + ".java";
	}
	
	protected String getInputTestFileName(String cuName) {
		return createTestFileName(cuName, TEST_INPUT_INFIX);
	}
	
	/**
	 * @param subDirName example "p/" or "org/eclipse/jdt/"
	 */
	protected String getInputTestFileName(String cuName, String subDirName) {
		return createTestFileName(cuName, TEST_INPUT_INFIX + subDirName);
	}

	protected String getOutputTestFileName(String cuName) {
		return createTestFileName(cuName, TEST_OUTPUT_INFIX);
	}
	
	/**
	 * @param subDirName example "p/" or "org/eclipse/jdt/"
	 */
	protected String getOutputTestFileName(String cuName, String subDirName) {
		return createTestFileName(cuName, TEST_OUTPUT_INFIX + subDirName);
	}
	
	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, String cuName) throws Exception {
		return createCUfromTestFile(pack, cuName, true);
	}
	
	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, String cuName, String subDirName) throws Exception {
		return createCUfromTestFile(pack, cuName, subDirName, true);
	}
	
	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, String cuName, boolean input) throws Exception {
		String contents= input 
					? getFileContents(getInputTestFileName(cuName))
					: getFileContents(getOutputTestFileName(cuName));
		return createCU(pack, cuName + ".java", contents);
	}
	
	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, String cuName, String subDirName, boolean input) throws Exception {
		String contents= input 
			? getFileContents(getInputTestFileName(cuName, subDirName))
			: getFileContents(getOutputTestFileName(cuName, subDirName));
		
		return createCU(pack, cuName + ".java", contents);
	}
	
	protected void printTestDisabledMessage(String explanation){
		System.out.println("\n" +getClass().getName() + "::"+ getName() + " disabled (" + explanation + ")");
	}
	
	//-----------------------
	public static InputStream getStream(String content){
		return new StringBufferInputStream(content);
	}
	
	public static IPackageFragmentRoot getSourceFolder(IJavaProject javaProject, String name) throws JavaModelException{
		IPackageFragmentRoot[] roots= javaProject.getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			if (! roots[i].isArchive() && roots[i].getElementName().equals(name))
				return roots[i];
		}
		return null;
	}
	
	public static String getFileContents(String fileName) throws IOException {
		return getContents(getFileInputStream(fileName));
	}

	public static String getContents(IFile file) throws IOException, CoreException {
		return getContents(file.getContents());
	}
	
	public static ICompilationUnit createCU(IPackageFragment pack, String name, String contents) throws Exception {
		if (pack.getCompilationUnit(name).exists())
			return pack.getCompilationUnit(name);
		ICompilationUnit cu= pack.createCompilationUnit(name, contents, true, null);
		cu.save(null, true);
		return cu;
	}

	public static String getContents(InputStream in) throws IOException {
		BufferedReader br= new BufferedReader(new InputStreamReader(in));
		
		StringBuffer sb= new StringBuffer(300);
		try {
			int read= 0;
			while ((read= br.read()) != -1)
				sb.append((char) read);
		} finally {
			br.close();
		}
		return sb.toString();
	}

	public static InputStream getFileInputStream(String fileName) throws IOException {
		return RefactoringTestPlugin.getDefault().getTestResourceStream(fileName);
	}

	public static String removeExtension(String fileName) {
		return fileName.substring(0, fileName.lastIndexOf('.'));
	}
	
	public static void performDummySearch(IJavaElement element) throws Exception{
		new SearchEngine().searchAllTypeNames(
			ResourcesPlugin.getWorkspace(),
			null,
			null,
			IJavaSearchConstants.EXACT_MATCH,
			IJavaSearchConstants.CASE_SENSITIVE,
			IJavaSearchConstants.CLASS,
			SearchEngine.createJavaSearchScope(new IJavaElement[]{element}),
			new Requestor(),
			IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
			null);
	}
	
	public static IMember[] merge(IMember[] a1, IMember[] a2, IMember[] a3){
		return JavaElementUtil.merge(JavaElementUtil.merge(a1, a2), a3);
	}

	public static IMember[] merge(IMember[] a1, IMember[] a2){
		return JavaElementUtil.merge(a1, a2);
	}
		
	public static IField[] getFields(IType type, String[] names) throws JavaModelException{
		if (names == null )
			return new IField[0];
		Set fields= new HashSet();
		for (int i = 0; i < names.length; i++) {
			IField field= type.getField(names[i]);
			Assert.isTrue(field.exists(), "field " + field.getElementName() + " does not exist");
			fields.add(field);
		}
		return (IField[]) fields.toArray(new IField[fields.size()]);	
	}

	public static IType[] getMemberTypes(IType type, String[] names) throws JavaModelException{
		if (names == null )
			return new IType[0];
		Set memberTypes= new HashSet();
		for (int i = 0; i < names.length; i++) {
			IType memberType= type.getType(names[i]);
			Assert.isTrue(memberType.exists(), "member type " + memberType.getElementName() + " does not exist");
			memberTypes.add(memberType);
		}
		return (IType[]) memberTypes.toArray(new IType[memberTypes.size()]);	
	}
	
	public static IMethod[] getMethods(IType type, String[] names, String[][] signatures) throws JavaModelException{
		if (names == null || signatures == null)
			return new IMethod[0];
		List methods= new ArrayList(names.length);
		for (int i = 0; i < names.length; i++) {
			IMethod method= type.getMethod(names[i], signatures[i]);
			Assert.isTrue(method.exists(), "method " + method.getElementName() + " does not exist");
			if (!methods.contains(method))
				methods.add(method);
		}
		return (IMethod[]) methods.toArray(new IMethod[methods.size()]);	
	}

	public static IType[] findTypes(IType[] types, String[] namesOfTypesToPullUp) {
		List found= new ArrayList(types.length);
		for (int i= 0; i < types.length; i++) {
			IType type= types[i];
			for (int j= 0; j < namesOfTypesToPullUp.length; j++) {
				String name= namesOfTypesToPullUp[j];
				if (type.getElementName().equals(name))
					found.add(type);					
			}
		}
		return (IType[]) found.toArray(new IType[found.size()]);
	}
	
	public static IField[] findFields(IField[] fields, String[] namesOfFieldsToPullUp) {
		List found= new ArrayList(fields.length);
		for (int i= 0; i < fields.length; i++) {
			IField field= fields[i];
			for (int j= 0; j < namesOfFieldsToPullUp.length; j++) {
				String name= namesOfFieldsToPullUp[j];
				if (field.getElementName().equals(name))
					found.add(field);					
			}
		}
		return (IField[]) found.toArray(new IField[found.size()]);
	}

	public static IMethod[] findMethods(IMethod[] selectedMethods, String[] namesOfMethods, String[][] signaturesOfMethods){
		List found= new ArrayList(selectedMethods.length);
		for (int i= 0; i < selectedMethods.length; i++) {
			IMethod method= selectedMethods[i];
			String[] paramTypes= method.getParameterTypes();
			for (int j= 0; j < namesOfMethods.length; j++) {
				String methodName= namesOfMethods[j];
				if (! methodName.equals(method.getElementName()))
					continue;
				String[] methodSig= signaturesOfMethods[j];
				if (! areSameSignatures(paramTypes, methodSig))
					continue;
				found.add(method);	
			}
		}
		return (IMethod[]) found.toArray(new IMethod[found.size()]);
	}
	
	private static boolean areSameSignatures(String[] s1, String[] s2){
		if (s1.length != s2.length)
			return false;
		for (int i= 0; i < s1.length; i++) {
			if (! s1[i].equals(s2[i]))
				return false;
		}
		return true;
	}
	
	private static class Requestor implements ITypeNameRequestor{
		
		public void acceptClass(char[] packageName, char[] simpleTypeName, char[][] enclosingTypeNames, String path) {
		}

		public void acceptInterface(char[] packageName, char[] simpleTypeName, char[][] enclosingTypeNames, String path) {
		}
	}
}