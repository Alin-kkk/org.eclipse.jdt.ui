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
package org.eclipse.jdt.ui.tests.reorg;

import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgQueries;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jdt.ui.tests.refactoring.RefactoringTestSetup;
import org.eclipse.jdt.ui.tests.refactoring.ParticipantTesting;
import org.eclipse.jdt.ui.tests.refactoring.RefactoringTest;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.MoveArguments;
import org.eclipse.ltk.core.refactoring.participants.MoveRefactoring;


public class MoveTest extends RefactoringTest {

	public MoveTest(String name) {
		super(name);
	}

	private static final Class clazz= MoveTest.class;
	private static final String REFACTORING_PATH= "Move/";

	public static Test suite() {
		return new RefactoringTestSetup(new TestSuite(clazz));
	}

	public static Test setUpTest(Test someTest) {
		return new RefactoringTestSetup(someTest);
	}

	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	private IReorgQueries createReorgQueries(){
		return new MockReorgQueries();
	}
	
	private RefactoringStatus performRefactoring(JavaMoveProcessor processor, boolean providesUndo) throws Exception {
		return performRefactoring(new MoveRefactoring(processor), providesUndo);
	}

	private void verifyDisabled(IResource[] resources, IJavaElement[] javaElements) throws JavaModelException {
		CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();
		assertTrue("move should be disabled", ! JavaMoveProcessor.isAvailable(resources, javaElements, settings));
		JavaMoveProcessor processor= JavaMoveProcessor.create(resources, javaElements, settings);
		assertTrue(processor == null);
	}
	
	private JavaMoveProcessor verifyEnabled(IResource[] resources, IJavaElement[] javaElements, IReorgQueries reorgQueries) throws JavaModelException {
		CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();
		assertTrue("move should be enabled", JavaMoveProcessor.isAvailable(resources, javaElements, settings));
		JavaMoveProcessor processor= JavaMoveProcessor.create(resources, javaElements, settings);
		if (reorgQueries != null)
			processor.setReorgQueries(reorgQueries);
		assertNotNull(processor);
		return processor;
	}
	
	private void verifyValidDestination(JavaMoveProcessor ref, Object destination) throws Exception {
		RefactoringStatus status= null;
		if (destination instanceof IResource)
			status= ref.setDestination((IResource)destination);
		else if (destination instanceof IJavaElement)
			status= ref.setDestination((IJavaElement)destination);
		else assertTrue(false);
		
		assertEquals("destination was expected to be valid: " + status.getMessageMatchingSeverity(status.getSeverity()), RefactoringStatus.OK, status.getSeverity());
	}

	private void verifyInvalidDestination(JavaMoveProcessor ref, Object destination) throws Exception {
		RefactoringStatus status= null;
		if (destination instanceof IResource)
			status= ref.setDestination((IResource)destination);
		else if (destination instanceof IJavaElement)
			status= ref.setDestination((IJavaElement)destination);
		else assertTrue(false);
		
		assertEquals("destination was expected to be not valid",  RefactoringStatus.FATAL, status.getSeverity());
	}

	private void safeDelete(ICompilationUnit cu) throws JavaModelException {
		if (cu != null && cu.exists())
			cu.delete(true, new NullProgressMonitor());
	}
	private void safeDelete(IPackageFragment pack) throws JavaModelException {
		if (pack != null && pack.exists())
			pack.delete(true, new NullProgressMonitor());
	}
	private void safeDelete(IResource res) throws CoreException {
		if (res != null && res.exists())
			res.delete(true, null);
	}
	
	
	public void testDisabled_empty() throws Exception {
		IJavaElement[] javaElements= {};
		IResource[] resources= {};
		verifyDisabled(resources, javaElements);
	}
	
	public void testDisabled_null_element() throws Exception {
		IJavaElement[] javaElements= {null};
		IResource[] resources= {};
		verifyDisabled(resources, javaElements);
	}

	public void testDisabled_null_resource() throws Exception {
		IJavaElement[] javaElements= {};
		IResource[] resources= {null};
		verifyDisabled(resources, javaElements);
	}

	public void testDisabled_javaProject() throws Exception {
		IJavaElement[] javaElements= {RefactoringTestSetup.getProject()};
		IResource[] resources= {};
		verifyDisabled(resources, javaElements);
	}

	public void testDisabled_defaultPackage() throws Exception {
		IPackageFragment defaultPackage= getRoot().getPackageFragment("");
		assertTrue(defaultPackage.exists());
		IJavaElement[] javaElements= {defaultPackage};
		IResource[] resources= {};
		verifyDisabled(resources, javaElements);
	}

	public void testDisabled_project() throws Exception {
		IJavaElement[] javaElements= {};
		IResource[] resources= {RefactoringTestSetup.getProject().getProject()};
		verifyDisabled(resources, javaElements);
	}

	public void testDisabled_notExistingElement() throws Exception {
		ICompilationUnit notExistingCu= getPackageP().getCompilationUnit("NotMe.java");
		assertTrue(! notExistingCu.exists());
		IJavaElement[] javaElements= {notExistingCu};
		IResource[] resources= {};
		verifyDisabled(resources, javaElements);		
	}

	public void testDisabled_notExistingResource() throws Exception {
		IFolder folder= (IFolder)getPackageP().getResource();
		IFile notExistingFile= folder.getFile("a.txt");
		
		IJavaElement[] javaElements= {};
		IResource[] resources= {notExistingFile};
		verifyDisabled(resources, javaElements);
	}
	
	public void testDisabled_noCommonParent0() throws Exception {
		IJavaElement[] javaElements= {getPackageP(), getRoot()};
		IResource[] resources= {};
		verifyDisabled(resources, javaElements);		
	}
	
	public void testDisabled_noCommonParent1() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}}", false, new NullProgressMonitor());
		try {
			IType classA= cu.getType("A");
			IMethod	methodFoo= classA.getMethod("foo", new String[0]);
			IJavaElement[] javaElements= { classA, methodFoo };
			IResource[] resources= {};
			verifyDisabled(resources, javaElements);
		} finally {
			performDummySearch();
			safeDelete(cu);
		}		
	}

//	public void testDisabled_noCommonParent2() throws Exception {
//		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}}", false, new NullProgressMonitor());
//		try {
//			IType classA= cu.getType("A");
//			IJavaElement[] javaElements= { classA, cu};
//			IResource[] resources= {};
//			verifyDisabled(resources, javaElements);
//		} finally {
//			performDummySearch();
//			cu.delete(true, new NullProgressMonitor());
//		}		
//	}

	public void testDisabled_noCommonParent3() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}}", false, new NullProgressMonitor());
		try {
			IJavaElement[] javaElements= {cu, getPackageP()};
			IResource[] resources= {};
			verifyDisabled(resources, javaElements);
		} finally {
			performDummySearch();
			safeDelete(cu);
		}		
	}

	public void testDisabled_noCommonParent5() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}}", false, new NullProgressMonitor());
		try {
			IJavaElement[] javaElements= {cu, getRoot()};
			IResource[] resources= {};
			verifyDisabled(resources, javaElements);
		} finally {
			performDummySearch();
			safeDelete(cu);
		}		
	}

	public void testDisabled_noCommonParent6() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}}", false, new NullProgressMonitor());
		try {
			IJavaElement[] javaElements= {cu, getRoot()};
			IResource[] resources= {};
			verifyDisabled(resources, javaElements);
		} finally {
			performDummySearch();
			safeDelete(cu);
		}		
	}

	public void testDisabled_noCommonParent7() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{class Inner{}}", false, new NullProgressMonitor());
		try {
			IType classA= cu.getType("A");
			IType classInner= classA.getType("Inner");
			IJavaElement[] javaElements= { classA, classInner};
			IResource[] resources= {};
			verifyDisabled(resources, javaElements);
		} finally {
			performDummySearch();
			safeDelete(cu);
		}
	}
	
	public void testDisabled_noCommonParent8() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try {
			IType classA= cu.getType("A");
			IMethod	methodFoo= classA.getMethod("foo", new String[0]);
			IJavaElement[] javaElements= { methodFoo, classA};
			IResource[] resources= {};
			verifyDisabled(resources, javaElements);
		} finally {
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_no_fileToItself() throws Exception {
		IFolder superFolder= (IFolder)getPackageP().getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);

		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= file;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			file.delete(true, false, null);
		}
	}
	
	public void testDestination_no_fileToSiblingFile() throws Exception {
		IFolder superFolder= (IFolder)getPackageP().getResource();
		IFile file1= superFolder.getFile("a.txt");
		file1.create(getStream("123"), true, null);
		IFile file2= superFolder.getFile("b.txt");
		file2.create(getStream("123"), true, null);

		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file1};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= file2;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			file1.delete(true, false, null);
			file2.delete(true, false, null);
		}
	}

	public void testDestination_no_folderToItsef() throws Exception {
		IFolder superFolder= (IFolder)getPackageP().getResource();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= folder;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			folder.delete(true, false, null);
		}
	}

	public void testDestination_no_cuToItsef() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= {cu};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= cu;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_no_cuToSiblingCu() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		ICompilationUnit cu1= getPackageP().createCompilationUnit("B.java", "package p;class A{}", false, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= {cu};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= cu1;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
			safeDelete(cu1);
		}
	}

	public void testDestination_no_cuToSiblingFile() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());

		IFolder superFolder= (IFolder)getPackageP().getResource();
		IFile file1= superFolder.getFile("a.txt");
		file1.create(getStream("123"), true, null);

		try{
			IJavaElement[] javaElements= {cu};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= file1;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
			safeDelete(file1);
		}
	}
	
	public void testDestination_no_packageToItsef() throws Exception {
		IJavaElement[] javaElements= {getPackageP()};
		IResource[] resources= {};
		JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

		Object destination= getPackageP();
		verifyInvalidDestination(ref, destination);
	}
	
	public void testDestination_no_sourceFolderToItsef() throws Exception {
		IJavaElement[] javaElements= {getRoot()};
		IResource[] resources= {};
		JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

		Object destination= getRoot();
		verifyInvalidDestination(ref, destination);
	}
	
	public void testDestination_no_methodToItsef() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= method;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_no_fileToParentFolder() throws Exception {
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);
		IFile file= folder.getFile("a.txt");
		file.create(getStream("123"), true, null);

		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= folder;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			folder.delete(true, false, null);
			file.delete(true, false, null);
		}
	}

	public void testDestination_no_fileToParentPackage() throws Exception {
		IFolder superFolder= (IFolder)getPackageP().getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);

		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= getPackageP();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			file.delete(true, false, null);
		}
	}

	public void testDestination_no_fileToParentSourceFolder() throws Exception {
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);

		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= getRoot();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			file.delete(true, false, null);
		}
	}

	public void testDestination_no_folderToParentFolder() throws Exception {
		IProject superFolder= RefactoringTestSetup.getProject().getProject();

		IFolder parentFolder= superFolder.getFolder("folder");
		parentFolder.create(true, true, null);
		IFolder folder= parentFolder.getFolder("subfolder");
		folder.create(true, true, null);

		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {parentFolder};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= parentFolder;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			parentFolder.delete(true, false, null);
		}
	}

	public void testDestination_no_cuToParentPackage() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= {cu};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= cu.getParent();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
		}
	}
	
	public void testDestination_no_packageToParentSourceFolder() throws Exception {
		IJavaElement[] javaElements= {getPackageP()};
		IResource[] resources= {};
		JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

		Object destination= getRoot();
		verifyInvalidDestination(ref, destination);
	}
	
	public void testDestination_no_sourceFolderToParentProject() throws Exception {
		IJavaElement[] javaElements= {getRoot()};
		IResource[] resources= {};
		JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

		Object destination= getRoot().getParent();
		verifyInvalidDestination(ref, destination);
	}
	
	public void testDestination_no_methodToParentType() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= cu.getType("A");
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_yes_cuToMethod() throws Exception {
		IPackageFragment pack1= getRoot().createPackageFragment("q", true, new NullProgressMonitor());
		ICompilationUnit cu= pack1.createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		ICompilationUnit cu1= getPackageP().createCompilationUnit("B.java", "package p;class B{}", false, new NullProgressMonitor());
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {cu1};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= method;
			verifyValidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(pack1);
			safeDelete(cu1);
		}
	}
	
	public void testDestination_no_packageToCu() throws Exception {
		IPackageFragment pack1= getRoot().createPackageFragment("q", true, new NullProgressMonitor());
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= {pack1};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= cu;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(pack1);
		}
	}

	public void testDestination_no_packageToFile() throws Exception {
		IPackageFragment pack1= getRoot().createPackageFragment("q", true, new NullProgressMonitor());
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);

		try{
			IJavaElement[] javaElements= {pack1};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= file;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(pack1);
			file.delete(true, false, null);	
		}
	}

	public void testDestination_no_packageToFolder() throws Exception {
		IPackageFragment pack1= getRoot().createPackageFragment("q", true, new NullProgressMonitor());
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		try{
			IJavaElement[] javaElements= {pack1};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= folder;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(pack1);
			folder.delete(true, false, null);	
		}
	}

	public void testDestination_no_packageToSimpleProject() throws Exception {
		IProject simpleProject= ResourcesPlugin.getWorkspace().getRoot().getProject("mySImpleProject");
		simpleProject.create(null);
		simpleProject.open(null);
		
		IPackageFragment pack1= getRoot().createPackageFragment("q", true, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= {pack1};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= simpleProject;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(pack1);
			simpleProject.delete(true, true, null);	
		}
	}

//	public void testDestination_no_packageToJavaProjectWithNoSourceFolders() throws Exception {
//		IJavaProject otherProject= JavaProjectHelper.createJavaProject("otherProject", null);
//		JavaProjectHelper.addSourceContainer(otherProject, null);
//		IPackageFragment pack1= getRoot().createPackageFragment("q", true, new NullProgressMonitor());
//		try{
//			IJavaElement[] javaElements= {pack1};
//			IResource[] resources= {};
//			JavaMoveProcessor2 ref= verifyEnabled(resources, javaElements, createReorgQueries());
//
//			Object destination= otherProject;
//			verifyInvalidDestination(ref, destination);
//		} finally{
//			performDummySearch();
//			pack1.delete(true, new NullProgressMonitor());
//			JavaProjectHelper.delete(otherProject);
//		}
//	}
	
	public void testDestination_no_packageToSiblingPackage() throws Exception {
		IPackageFragment pack1= getRoot().createPackageFragment("q", true, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= {getPackageP()};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= pack1;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(pack1);
		}
	}
	
	public void testDestination_no_sourceFolderToCu() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		IPackageFragmentRoot sourceFolder= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "src2");
		try{
			IJavaElement[] javaElements= {sourceFolder};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= cu;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
			sourceFolder.delete(0, 0, new NullProgressMonitor());
		}
	}

	public void testDestination_no_sourceFolderToPackage() throws Exception {
		IPackageFragmentRoot sourceFolder= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "src2");
		try{
			IJavaElement[] javaElements= {sourceFolder};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= getPackageP();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			sourceFolder.delete(0, 0, new NullProgressMonitor());
		}
	}

	public void testDestination_no_sourceFolderToFile() throws Exception {
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		
		IPackageFragmentRoot sourceFolder= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "src2");
		try{
			IJavaElement[] javaElements= {sourceFolder};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= file;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			sourceFolder.delete(0, 0, new NullProgressMonitor());
			file.delete(true, false, null);	
		}
	}

	public void testDestination_no_sourceFolderToFolder() throws Exception {
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);
		
		IPackageFragmentRoot sourceFolder= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "src2");
		try{
			IJavaElement[] javaElements= {sourceFolder};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= folder;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			sourceFolder.delete(0, 0, new NullProgressMonitor());
			folder.delete(true, false, null);	
		}
	}

	public void testDestination_no_sourceFolderToSourceFolder() throws Exception {
		IPackageFragmentRoot sourceFolder= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "src2");
		try{
			IJavaElement[] javaElements= {sourceFolder};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= getRoot();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			sourceFolder.delete(0, 0, new NullProgressMonitor());
		}
	}

	public void testDestination_no_sourceFolderToSimpleProject() throws Exception {
		IPackageFragmentRoot sourceFolder= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "src2");
		IProject simpleProject= ResourcesPlugin.getWorkspace().getRoot().getProject("mySImpleProject");
		simpleProject.create(null);
		simpleProject.open(null);

		try{
			IJavaElement[] javaElements= {sourceFolder};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= simpleProject;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			sourceFolder.delete(0, 0, new NullProgressMonitor());
			simpleProject.delete(true, true, null);
		}
	}

	public void testDestination_no_sourceFolderToJavaProjecteWithNoSourceFolder() throws Exception {
		IJavaProject otherProject= JavaProjectHelper.createJavaProject("otherProject", null);
		JavaProjectHelper.addSourceContainer(otherProject, null);
		IPackageFragmentRoot sourceFolder= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "src2");
		
		try{
			IJavaElement[] javaElements= {sourceFolder};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= otherProject;
			verifyInvalidDestination(ref, destination);
		}finally{
			performDummySearch();
			sourceFolder.delete(0, 0, new NullProgressMonitor());
			JavaProjectHelper.delete(otherProject);
		} 
	}
	
	public void testDestination_no_methodToCu() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		ICompilationUnit cu1= getPackageP().createCompilationUnit("B.java", "package p;class B{}", false, new NullProgressMonitor());
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= cu1;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
			safeDelete(cu1);
		}
	}

	public void testDestination_no_methodToFile() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);

		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= file;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
			file.delete(true, false, null);
		}
	}

	public void testDestination_no_methodToFolder() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= folder;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
			folder.delete(true, false, null);
		}
	}
	
	public void testDestination_no_methodToPackage() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= getPackageP();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_no_methodToSourceFolder() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= getRoot();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_no_methodToJavaProject() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= RefactoringTestSetup.getProject();
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_no_methodToSimpleProject() throws Exception {
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		IProject simpleProject= ResourcesPlugin.getWorkspace().getRoot().getProject("mySImpleProject");
		simpleProject.create(null);
		simpleProject.open(null);
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {method};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
	
			Object destination= simpleProject;
			verifyInvalidDestination(ref, destination);
		} finally{
			performDummySearch();
			safeDelete(cu);
			simpleProject.delete(true, true, null);
		}
	}
	
	public void testDestination_no_cuToItself() throws Exception{
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= { cu1};
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
			Object destination= cu1;
			verifyInvalidDestination(ref, destination);			
		}finally{
			performDummySearch();
			safeDelete(cu1);
		}
	}

	public void testDestination_yes_cuToOtherPackage() throws Exception{
		IPackageFragment otherPackage= getRoot().createPackageFragment("otherPackage", true, new NullProgressMonitor());
		String oldSource= "package p;class A{void foo(){}class Inner{}}";
		String newSource= "package otherPackage;class A{void foo(){}class Inner{}}";
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", oldSource, false, new NullProgressMonitor());
		try{
			ParticipantTesting.reset();
			IJavaElement[] javaElements= { cu1};
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cu1, cu1.getTypes()[0], cu1.getResource()});
			JavaMoveProcessor processor= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= otherPackage;
			verifyValidDestination(processor, destination);
			
			assertTrue("source file does not exist before moving", cu1.exists());
			RefactoringStatus status= performRefactoring(processor, false);
			assertEquals(null, status);
			assertTrue("source file exists after moving", ! cu1.exists());
			ICompilationUnit newCu= otherPackage.getCompilationUnit(cu1.getElementName());
			assertTrue("new file does not exist after moving", newCu.exists());
			assertEqualLines("source differs", newSource, newCu.getSource());
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(otherPackage, processor.getUpdateReferences()),
					new MoveArguments(otherPackage, processor.getUpdateReferences()),
					new MoveArguments(otherPackage.getResource(), processor.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(otherPackage);
		}
	}

	public void testDestination_yes_cuToOtherPackageWithMultiRoot() throws Exception {
		ParticipantTesting.reset();
		//regression test for https://bugs.eclipse.org/bugs/show_bug.cgi?id=47788
		IPackageFragment otherPackage= getRoot().createPackageFragment("otherPackage", true, new NullProgressMonitor());
		String oldA= "package p;public class A{}";
		String newA= "package otherPackage;public class A{}";
		ICompilationUnit cuA= getPackageP().createCompilationUnit("A.java", oldA, false, new NullProgressMonitor());
		
		IPackageFragmentRoot testSrc= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "testSrc");
		IPackageFragment testP= testSrc.createPackageFragment("p", true, new NullProgressMonitor());
		String oldRef= "package p;\npublic class Ref { A t = new A(); }";
		String newRef= "package p;\n\nimport otherPackage.A;\n\npublic class Ref { A t = new A(); }";
		ICompilationUnit cuRef= testP.createCompilationUnit("Ref.java", oldRef, false, new NullProgressMonitor());
		try{
			IJavaElement[] javaElements= {cuA};
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cuA, cuA.getTypes()[0], cuA.getResource()});
			JavaMoveProcessor processor= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= otherPackage;
			verifyValidDestination(processor, destination);
			
			assertTrue("source file does not exist before moving", cuA.exists());
			RefactoringStatus status= performRefactoring(processor, false);
			assertEquals(null, status);
			assertTrue("source file exists after moving", ! cuA.exists());
			ICompilationUnit newCu= otherPackage.getCompilationUnit(cuA.getElementName());
			assertTrue("new file does not exist after moving", newCu.exists());
			assertEqualLines("source differs", newA, newCu.getSource());
			assertEqualLines("Ref differs", newRef, cuRef.getSource());
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(otherPackage, processor.getUpdateReferences()),
					new MoveArguments(otherPackage, processor.getUpdateReferences()),
					new MoveArguments(otherPackage.getResource(), processor.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(otherPackage);
			JavaProjectHelper.removeSourceContainer(RefactoringTestSetup.getProject(), testSrc.getElementName());
		}
	}
	
	public void testDestination_yes_cuToRoot() throws Exception{
		ParticipantTesting.reset();
		String newSource= "class A{void foo(){}class Inner{}}";
		String oldSource= "package p;class A{void foo(){}class Inner{}}";
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", oldSource, false, new NullProgressMonitor());
		ICompilationUnit newCu= null;
		try{
			IJavaElement[] javaElements= { cu1};
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cu1, cu1.getTypes()[0], cu1.getResource()});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= getRoot();
			verifyValidDestination(ref, destination);			
			
			assertTrue("source file does not exist before moving", cu1.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("source file exists after moving", ! cu1.exists());
			newCu= getRoot().getPackageFragment("").getCompilationUnit(cu1.getElementName());
			assertTrue("new file does not exist after moving", newCu.exists());
			assertEqualLines("source differs", newSource, newCu.getSource());

			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(getRoot().getPackageFragment(""), ref.getUpdateReferences()),
					new MoveArguments(getRoot().getPackageFragment(""), ref.getUpdateReferences()),
					new MoveArguments(getRoot().getResource(), ref.getUpdateReferences())});
			
		}finally{
			performDummySearch();
			safeDelete(newCu);
		}
	}

	public void testDestination_yes_cuFromRoot() throws Exception{
		ParticipantTesting.reset();
		
		//import statement with type from default package - only <= java 1.3
		IJavaProject javaProject= getRoot().getJavaProject();
		Map originalOptions= javaProject.getOptions(false);
		Map newOptions= javaProject.getOptions(false);
		newOptions.put(JavaCore.COMPILER_COMPLIANCE, "1.3");
		newOptions.put(JavaCore.COMPILER_SOURCE, "1.3");
		javaProject.setOptions(newOptions);
		
		String oldD= "import org.test.Reference;public class Default {Reference ref;}";
		String oldRef= "package org.test;import Default;public class Reference{Default d;}";
		String newD= "package org;\nimport org.test.Reference;public class Default {Reference ref;}";
		String newRef= "package org.test;import org.Default;\npublic class Reference{Default d;}";
		ICompilationUnit cuD= getRoot().getPackageFragment("").createCompilationUnit("Default.java", oldD, false, new NullProgressMonitor());
		IPackageFragment orgTest= getRoot().createPackageFragment("org.test", false, new NullProgressMonitor());
		ICompilationUnit cuRef= orgTest.createCompilationUnit("Reference.java", oldRef, false, new NullProgressMonitor());
		IPackageFragment org= getRoot().getPackageFragment("org");
		ICompilationUnit newCuD= org.getCompilationUnit(cuD.getElementName());
		try{
			IJavaElement[] javaElements= { cuD };
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cuD, cuD.getTypes()[0], cuD.getResource()});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			verifyValidDestination(ref, org);			
			
			assertTrue("source file Default.java does not exist before moving", cuD.exists());
			assertTrue("source file Reference.java does not exist before moving", cuRef.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("source file Default.java exists after moving", ! cuD.exists());
			assertTrue("new file Default.java does not exist after moving", newCuD.exists());
			assertTrue("source file Reference.java does not exist after moving", cuRef.exists());
			assertEqualLines("Default.java differs", newD, newCuD.getSource());
			assertEqualLines("Reference.java differs", newRef, cuRef.getSource());

			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(org, ref.getUpdateReferences()),
					new MoveArguments(org, ref.getUpdateReferences()),
					new MoveArguments(org.getResource(), ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(newCuD);
			safeDelete(orgTest);
			safeDelete(org);
			if (newOptions != null)
				javaProject.setOptions(originalOptions);
		}
	}

	public void testDestination_no_cuFromRoot() throws Exception{
		//import statement with type from default package - only <= java 1.3
		IJavaProject javaProject= getRoot().getJavaProject();
		Map originalOptions= javaProject.getOptions(false);
		Map newOptions= javaProject.getOptions(false);
		newOptions.put(JavaCore.COMPILER_COMPLIANCE, "1.4"); //will cause error (potential match)
		newOptions.put(JavaCore.COMPILER_SOURCE, "1.4"); //will cause error (potential match)
		javaProject.setOptions(newOptions);
		
		String oldD= "import org.test.Reference;public class Default {Reference ref;}";
		String oldRef= "package org.test;import Default;public class Reference{Default d;}";
		String newD= "package org;\nimport org.test.Reference;public class Default {Reference ref;}";
		String newRef= "package org.test;import org.Default;\npublic class Reference{Default d;}";
		ICompilationUnit cuD= getRoot().getPackageFragment("").createCompilationUnit("Default.java", oldD, false, new NullProgressMonitor());
		IPackageFragment orgTest= getRoot().createPackageFragment("org.test", false, new NullProgressMonitor());
		ICompilationUnit cuRef= orgTest.createCompilationUnit("Reference.java", oldRef, false, new NullProgressMonitor());
		IPackageFragment org= getRoot().getPackageFragment("org");
		ICompilationUnit newCuD= org.getCompilationUnit(cuD.getElementName());
		try{
			IJavaElement[] javaElements= { cuD };
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			verifyValidDestination(ref, org);			
			
			assertTrue("source file Default.java does not exist before moving", cuD.exists());
			assertTrue("source file Reference.java does not exist before moving", cuRef.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(RefactoringStatus.ERROR, status.getSeverity());
			assertTrue("source file Default.java exists after moving", ! cuD.exists());
			assertTrue("new file Default.java does not exist after moving", newCuD.exists());
			assertTrue("source file Reference.java does not exist after moving", cuRef.exists());
			assertEqualLines("Default.java differs", newD, newCuD.getSource());
			assertEqualLines("Reference.java differs", newRef, cuRef.getSource());

		}finally{
			performDummySearch();
			safeDelete(newCuD);
			safeDelete(orgTest);
			safeDelete(org);
			if (newOptions != null)
				javaProject.setOptions(originalOptions);
		}
	}

	public void testDestination_yes_cuToProject() throws Exception{
		ParticipantTesting.reset();
		String oldSource= "package p;class A{void foo(){}class Inner{}}";
		String newSource= oldSource;
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", oldSource, false, new NullProgressMonitor());
		IFile newFile= null;
		try{
			IJavaElement[] javaElements= { cu1};
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cu1, cu1.getTypes()[0], cu1.getResource()});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			IJavaProject project= RefactoringTestSetup.getProject();
			Object destination= project;
			verifyValidDestination(ref, destination);			

			assertTrue("source file does not exist before moving", cu1.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("source file exists after moving", ! cu1.exists());
			newFile= project.getProject().getFile(cu1.getElementName());
			assertEqualLines("source differs", newSource, getContents(newFile));
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(project.getProject(), ref.getUpdateReferences()),
					new MoveArguments(project.getProject(), ref.getUpdateReferences()),
					new MoveArguments(project.getResource(), ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(newFile);
		}
	}

	public void testDestination_yes_cuToSimpleProject() throws Exception{
		ParticipantTesting.reset();
		String oldSource= "package p;class A{void foo(){}class Inner{}}";
		String newSource= oldSource;
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", oldSource, false, new NullProgressMonitor());
		IProject simpleProject= ResourcesPlugin.getWorkspace().getRoot().getProject("mySImpleProject");
		simpleProject.create(null);
		simpleProject.open(null);
		IFile newFile= null;
		try{
			IJavaElement[] javaElements= { cu1};
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cu1, cu1.getTypes()[0], cu1.getResource()});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= simpleProject;
			verifyValidDestination(ref, destination);

			assertTrue("source file does not exist before moving", cu1.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("source file exists after moving", ! cu1.exists());
			newFile= simpleProject.getFile(cu1.getElementName());
			assertEqualLines("source differs", newSource, getContents(newFile));
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(simpleProject, ref.getUpdateReferences()),
					new MoveArguments(simpleProject, ref.getUpdateReferences()),
					new MoveArguments(simpleProject, ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(simpleProject);
		}
	}

	public void testDestination_yes_cuToFileInDifferentPackage() throws Exception{
		ParticipantTesting.reset();
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		IPackageFragment otherPackage= getRoot().createPackageFragment("other", true, new NullProgressMonitor());
		IFolder superFolder= (IFolder) otherPackage.getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		
		ICompilationUnit newCu= null;
		try{
			IJavaElement[] javaElements= { cu1};
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cu1, cu1.getTypes()[0], cu1.getResource()});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= file;
			verifyValidDestination(ref, destination);
			
			assertTrue("source file does not exist before", cu1.exists());
			
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			assertTrue("source file not moved", ! cu1.exists());
			
			newCu= otherPackage.getCompilationUnit(cu1.getElementName());
			assertTrue("new file does not exist after", newCu.exists());

			String expectedSource= "package other;class A{void foo(){}class Inner{}}";
			assertEqualLines("source compare failed", expectedSource, newCu.getSource());
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(otherPackage, ref.getUpdateReferences()),
					new MoveArguments(otherPackage, ref.getUpdateReferences()),
					new MoveArguments(otherPackage.getResource(), ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(otherPackage);
			safeDelete(newCu);
			safeDelete(file);
		}
	}

	public void testDestination_yes_cuToFolder() throws Exception{
		ParticipantTesting.reset();
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);
		
		IFile newFile= null;
		try{
			IJavaElement[] javaElements= { cu1};
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {cu1, cu1.getTypes()[0], cu1.getResource()});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= folder;
			verifyValidDestination(ref, destination);			

			assertTrue("source file does not exist before", cu1.exists());
			String expectedSource= cu1.getSource();
			
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			assertTrue("source file not moved", ! cu1.exists());
			
			newFile= folder.getFile(cu1.getElementName());
			assertTrue("new file does not exist after", newFile.exists());

			assertEqualLines("source compare failed", expectedSource, getContents(newFile));
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(destination, ref.getUpdateReferences()),
					new MoveArguments(destination, ref.getUpdateReferences()),
					new MoveArguments(folder, ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(newFile);
			safeDelete(folder);
		}
	}

	public void testDestination_yes_fileToSiblingFolder() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		IFile newFile= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			String[] handles= ParticipantTesting.createHandles(new Object[] {file});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= folder;
			verifyValidDestination(ref, destination);			

			assertTrue("source file does not exist before", file.exists());
			
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			assertTrue("source file not moved", ! file.exists());
			
			newFile= folder.getFile(file.getName());
			assertTrue("new file does not exist after", newFile.exists());
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(folder, ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(newFile);
			safeDelete(folder);
		}
	}		

	public void testDestination_yes_fileToCu() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		
		ICompilationUnit cu1= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());			
		IFile newFile= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			String[] handles= ParticipantTesting.createHandles(new Object[] {file});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= cu1;
			verifyValidDestination(ref, destination);
			
			assertTrue("source file does not exist before", file.exists());
			
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			assertTrue("source file not moved", ! file.exists());
			
			newFile= ((IFolder)cu1.getParent().getResource()).getFile(file.getName());
			assertTrue("new file does not exist after", newFile.exists());
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(getPackageP().getResource(), ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(newFile);
			safeDelete(cu1);
		}
	}		
	
	public void testDestination_yes_fileToPackage() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		
		IFile newFile= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			String[] handles= ParticipantTesting.createHandles(new Object[] {file});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= getPackageP();
			verifyValidDestination(ref, destination);			

			assertTrue("source file does not exist before", file.exists());
			
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			assertTrue("source file not moved", ! file.exists());
			
			newFile= ((IFolder)getPackageP().getResource()).getFile(file.getName());
			assertTrue("new file does not exist after", newFile.exists());
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(getPackageP().getResource(), ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(newFile);
		}
	}

	public void testDestination_yes_fileToMethod() throws Exception {
		ParticipantTesting.reset();
		IFolder superFolder= (IFolder)getRoot().getResource();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		IFile newFile= null;
		try{
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			String[] handles= ParticipantTesting.createHandles(new Object[] {file});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= method;
			verifyValidDestination(ref, destination);

			assertTrue("source file does not exist before", file.exists());
			
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			assertTrue("source file not moved", ! file.exists());
			
			newFile= ((IFolder)getPackageP().getResource()).getFile(file.getName());
			assertTrue("new file does not exist after", newFile.exists());
			
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(getPackageP().getResource(), ref.getUpdateReferences())});
		} finally{
			performDummySearch();
			safeDelete(file);
			safeDelete(cu);
		}
	}

	public void testDestination_yes_fileToRoot() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);

		IFile newFile= null;	
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			String[] handles= ParticipantTesting.createHandles(new Object[] {file});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= getRoot();
			verifyValidDestination(ref, destination);			

			assertTrue("source file does not exist before", file.exists());
			
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			assertTrue("source file not moved", ! file.exists());
			
			newFile= ((IFolder)getRoot().getResource()).getFile(file.getName());
			assertTrue("new file does not exist after", newFile.exists());
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(getRoot().getResource(), ref.getUpdateReferences())});
		}finally{
			performDummySearch();
			safeDelete(newFile);
		}
	}		

	public void testDestination_no_fileToParentProject() throws Exception{
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFile file= superFolder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {file};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= RefactoringTestSetup.getProject();
			verifyInvalidDestination(ref, destination);			
		}finally{
			performDummySearch();
			safeDelete(file);
		}
	}		

	public void testDestination_yes_folderToSiblingFolder() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		IFolder otherFolder= superFolder.getFolder("otherfolder");
		otherFolder.create(true, true, null);
		
		IFolder newFolder= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			String[] handles= ParticipantTesting.createHandles(new Object[] {folder});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= otherFolder;
			verifyValidDestination(ref, destination);
			
			assertTrue("folder does not exist before", folder.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("folder not moved", ! folder.exists());
			newFolder= otherFolder.getFolder(folder.getName());
			assertTrue("new folder does not exist after", newFolder.exists());		
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(destination, ref.getUpdateReferences())});
		} finally{
			performDummySearch();
			safeDelete(newFolder);			
			safeDelete(otherFolder);			
		}
	}

	public void testDestination_no_folderToParentProject() throws Exception{
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= RefactoringTestSetup.getProject();
			verifyInvalidDestination(ref, destination);						
		} finally{
			performDummySearch();
			safeDelete(folder);			
		}
	}

	public void testDestination_yes_folderToSiblingRoot() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);
		
		IPackageFragment newPackage= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			String[] handles= ParticipantTesting.createHandles(new Object[] {folder});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= getRoot();
			verifyValidDestination(ref, destination);						
			
			assertTrue("folder does not exist before", folder.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("folder not moved", ! folder.exists());
			newPackage= getRoot().getPackageFragment(folder.getName());
			assertTrue("new folder does not exist after", newPackage.exists());		
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(getRoot().getResource(), ref.getUpdateReferences())});
		} finally{
			performDummySearch();
			safeDelete(newPackage);
		}
	}

	public void testDestination_yes_folderToPackage() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		IPackageFragment newPackage= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			String[] handles= ParticipantTesting.createHandles(new Object[] {folder});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= getPackageP();
			verifyValidDestination(ref, destination);						

			assertTrue("folder does not exist before", folder.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("folder not moved", ! folder.exists());
			newPackage= getRoot().getPackageFragment(getPackageP().getElementName() + "." + folder.getName());
			assertTrue("new package does not exist after", newPackage.exists());		
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(getPackageP().getResource(), ref.getUpdateReferences())});
		} finally{
			performDummySearch();
			safeDelete(newPackage);
		}
	}
	
	public void testDestination_yes_folderToFileInAnotherFolder() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		IFolder otherFolder= superFolder.getFolder("otherfolder");
		otherFolder.create(true, true, null);
		IFile fileInAnotherFolder= otherFolder.getFile("f.tex");
		fileInAnotherFolder.create(getStream("123"), true, null);

		IFolder newFolder= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			String[] handles= ParticipantTesting.createHandles(new Object[] {folder});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= fileInAnotherFolder;
			verifyValidDestination(ref, destination);						
			
			assertTrue("folder does not exist before", folder.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("folder not moved", ! folder.exists());
			newFolder= otherFolder.getFolder(folder.getName());
			assertTrue("new folder does not exist after", newFolder.exists());
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(otherFolder, ref.getUpdateReferences())});
		} finally{
			performDummySearch();
//			folder.delete(true, new NullProgressMonitor());	
			safeDelete(otherFolder);
		}
	}

	public void testDestination_yes_folderToCu() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "package p;class A{void foo(){}class Inner{}}", false, new NullProgressMonitor());
		
		IPackageFragment newPackage= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			String[] handles= ParticipantTesting.createHandles(new Object[] {folder});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= cu;
			verifyValidDestination(ref, destination);						

			assertTrue("folder does not exist before", folder.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("folder not moved", ! folder.exists());
			newPackage= getRoot().getPackageFragment(getPackageP().getElementName() + "." + folder.getName());
			assertTrue("new package does not exist after", newPackage.exists());		
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(cu.getParent().getResource(), ref.getUpdateReferences())});
		} finally{
			performDummySearch();
			safeDelete(newPackage);
			safeDelete(cu);
		}
	}

	public void testDestination_yes_folderToSimpleProject() throws Exception{
		ParticipantTesting.reset();
		IProject superFolder= RefactoringTestSetup.getProject().getProject();
		IFolder folder= superFolder.getFolder("folder");
		folder.create(true, true, null);

		IProject simpleProject= ResourcesPlugin.getWorkspace().getRoot().getProject("mySImpleProject");
		simpleProject.create(null);
		simpleProject.open(null);
		
		IFolder newFolder= null;
		try{
			IJavaElement[] javaElements= {};
			IResource[] resources= {folder};
			String[] handles= ParticipantTesting.createHandles(new Object[] {folder});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= simpleProject;
			verifyValidDestination(ref, destination);
			
			assertTrue("folder does not exist before", folder.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("folder not moved", ! folder.exists());
			newFolder= simpleProject.getFolder(folder.getName());
			assertTrue("new folder does not exist after", newFolder.exists());		
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(simpleProject, ref.getUpdateReferences())});
		} finally{
			performDummySearch();
			safeDelete(folder);
			simpleProject.delete(true, true, new NullProgressMonitor());
		}
	}

	public void testDestination_yes_sourceFolderToOtherProject() throws Exception{
		ParticipantTesting.reset();
		IJavaProject otherJavaProject= JavaProjectHelper.createJavaProject("other", "bin");
		
		IPackageFragmentRoot oldRoot= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "newSrc");
		IPackageFragmentRoot newRoot= null;
		try {
			IJavaElement[] javaElements= { oldRoot };
			IResource[] resources= {};
			String[] handles= ParticipantTesting.createHandles(new Object[] {oldRoot, oldRoot.getResource()});
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());

			Object destination= otherJavaProject;
			verifyValidDestination(ref, destination);

			assertTrue("folder does not exist before", oldRoot.exists());
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			assertTrue("folder not moved", ! oldRoot.exists());
			newRoot= getSourceFolder(otherJavaProject, oldRoot.getElementName());
			assertTrue("new folder does not exist after", newRoot.exists());		
			ParticipantTesting.testMove(
				handles,
				new MoveArguments[] {
					new MoveArguments(otherJavaProject, ref.getUpdateReferences()),
					new MoveArguments(otherJavaProject.getResource(), ref.getUpdateReferences())});
		} finally {
			performDummySearch();
			JavaProjectHelper.delete(otherJavaProject);
		}
	}

	public void testDestination_no_methodToItself() throws Exception{
		ICompilationUnit cu= null;
		try {
			cu= createCUfromTestFile(getPackageP(), "A");
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= { method };
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
			Object destination= method;
			verifyInvalidDestination(ref, destination);
		} finally {
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_yes_methodToOtherType() throws Exception{
		ParticipantTesting.reset();
		ICompilationUnit cu= null;
		try {
			cu= createCUfromTestFile(getPackageP(), "A");
			IMethod method= cu.getType("A").getMethod("foo", new String[0]);
			IJavaElement[] javaElements= { method };
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
			IType otherType= cu.getType("B");
			Object destination= otherType;
			verifyValidDestination(ref, destination);
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);

			String expected= getFileContents(getOutputTestFileName(removeExtension(cu.getElementName())));
			assertEqualLines("source differs", expected, cu.getSource());
			ParticipantTesting.testMove(new String[] {},new MoveArguments[] {} );
		} finally {
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_yes_fieldToOtherType() throws Exception{
		ParticipantTesting.reset();
		ICompilationUnit cu= null;
		try {
			cu= createCUfromTestFile(getPackageP(), "A");
			IField field= cu.getType("A").getField("f");
			IJavaElement[] javaElements= { field };
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
			IType otherType= cu.getType("B");
			Object destination= otherType;
			verifyValidDestination(ref, destination);
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			String expected= getFileContents(getOutputTestFileName(removeExtension(cu.getElementName())));
			assertEqualLines("source differs", expected, cu.getSource());
			ParticipantTesting.testMove(new String[] {},new MoveArguments[] {} );
		} finally {
			performDummySearch();
			safeDelete(cu);
		}
	}

	public void testDestination_yes_initializerToOtherType() throws Exception{
		ParticipantTesting.reset();
		ICompilationUnit cu= null;
		try {
			cu= createCUfromTestFile(getPackageP(), "A");
			IInitializer initializer= cu.getType("A").getInitializer(1);
			IJavaElement[] javaElements= { initializer };
			IResource[] resources= {};
			JavaMoveProcessor ref= verifyEnabled(resources, javaElements, createReorgQueries());
			IType otherType= cu.getType("B");
			Object destination= otherType;
			verifyValidDestination(ref, destination);
			RefactoringStatus status= performRefactoring(ref, false);
			assertEquals(null, status);
			
			String expected= getFileContents(getOutputTestFileName(removeExtension(cu.getElementName())));
			assertEqualLines("source differs", expected, cu.getSource());
			ParticipantTesting.testMove(new String[] {},new MoveArguments[] {} );
		} finally {
			performDummySearch();
			safeDelete(cu);
		}
	}
}
