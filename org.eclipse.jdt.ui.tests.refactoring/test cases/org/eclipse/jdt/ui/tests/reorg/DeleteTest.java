/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.reorg;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgQueries;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaDeleteProcessor;
import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.JavaTestPlugin;

import org.eclipse.jdt.ui.tests.refactoring.RefactoringTestSetup;
import org.eclipse.jdt.ui.tests.refactoring.ParticipantTesting;
import org.eclipse.jdt.ui.tests.refactoring.RefactoringTest;
import org.eclipse.jdt.ui.tests.refactoring.TestModelProvider;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.DeleteRefactoring;


public class DeleteTest extends RefactoringTest{

	private static final Class clazz= DeleteTest.class;
	private static final String REFACTORING_PATH= "Delete/";

	public DeleteTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new RefactoringTestSetup(new TestSuite(clazz));
	}

	public static Test setUpTest(Test someTest) {
		return new RefactoringTestSetup(someTest);
	}

	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	private void verifyDisabled(Object[] elements) throws CoreException {
		JavaDeleteProcessor processor= new JavaDeleteProcessor(elements);
		DeleteRefactoring ref= new DeleteRefactoring(processor);
		assertTrue("delete should be disabled", !ref.isApplicable());
	}

	private void verifyEnabled(Object[] elements) throws CoreException {
		JavaDeleteProcessor processor= new JavaDeleteProcessor(elements);
		DeleteRefactoring ref= new DeleteRefactoring(processor);
		assertTrue("delete should be enabled", ref.isApplicable());
	}

	private IPackageFragmentRoot getArchiveRoot() throws JavaModelException, Exception {
		IPackageFragmentRoot[] roots= RefactoringTestSetup.getProject().getPackageFragmentRoots();
		IPackageFragmentRoot archive= null;
		for (int i= 0; i < roots.length; i++) {
			IPackageFragmentRoot root= roots[i];
			if (root.isArchive() && root.isExternal())
				archive= root;
		}
		return archive;
	}

	private ICompilationUnit fCuA;
	private static final String CU_NAME= "A";

	private void loadFileSetup() throws Exception{
		fCuA= createCUfromTestFile(getPackageP(), CU_NAME);
		assertTrue("A.java does not exist", fCuA.exists());
	}
	
	private void checkDelete(IJavaElement[] elems, boolean deleteCu) throws JavaModelException, Exception {
		ICompilationUnit newCuA= null;
		IResourceChangeListener listener= new IResourceChangeListener() {
			public void resourceChanged(IResourceChangeEvent event) {
				TestModelProvider.assertTrue(event.getDelta());
			}
		};
		try {
			ResourcesPlugin.getWorkspace().addResourceChangeListener(listener);
			DeleteRefactoring refactoring= createRefactoring(elems);
			assertNotNull(refactoring);
			RefactoringStatus status= performRefactoring(refactoring, false);
			assertEquals("precondition was supposed to pass", null, status);

			newCuA= getPackageP().getCompilationUnit(CU_NAME + ".java");
			assertTrue("A.java does not exist", newCuA.exists() == !deleteCu);
			if (! deleteCu)
				assertEqualLines("incorrect content of A.java", getFileContents(getOutputTestFileName(CU_NAME)), newCuA.getSource());
		} finally {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);
			performDummySearch();
			if (newCuA != null && newCuA.exists())
				newCuA.delete(true, null);	
			if (fCuA != null && fCuA.exists()){
				fCuA.delete(true, null);		
				fCuA= null;
			}
		}	
	}
	
	private DeleteRefactoring createRefactoring(Object[] elements) throws CoreException {
		JavaDeleteProcessor processor= new JavaDeleteProcessor(elements);
		DeleteRefactoring result= new DeleteRefactoring(processor);
		processor.setQueries(createReorgQueries());
		return result;		
	}
	
	// package helpers
	
	private void assertPackagesAreDeleted(IPackageFragment[] frags) {
		for (int i= 0; i < frags.length; i++) { 
			assertFalse(frags[i].exists());
			assertFalse(frags[i].getResource().exists());
		}
	}

	private IPackageFragment[] createPackagePath(int no) throws JavaModelException {
		IPackageFragment[] frags= new IPackageFragment[no];
		for (int i=0; i<no; i++) {
			frags[i]= getRoot().createPackageFragment(getPackageName(i), true, new NullProgressMonitor());
		}
		return frags;
	}

	private String getPackageName(int i) {
		StringBuffer buf= new StringBuffer();
		for (int j= 0; j <= i; j++) {
			if (j>0)
				buf.append(".");
			buf.append("a");
			buf.append(j);
		}
		return buf.toString();
	}

	private void executeDeletePackage(Object[] markedForDelete, IPackageFragment[] packsToBeDeleted, Object[] othersToBeDeleted) throws CoreException, Exception {
		executeDeletePackage(markedForDelete, packsToBeDeleted, othersToBeDeleted, false);
	}
	
	/**
	 * Execute a package delete. 
	 * @param markedForDelete The elements selected for deletion ("in the UI")
	 * @param packsToBeDeleted First half of elements which must be deleted after the refactoring
	 * @param othersToBeDeleted Second half (halfs will be merged).
	 * @param deleteSubs true if subpackages should be deleted as well.
	 * @throws CoreException 
	 * @throws Exception 
	 */
	private void executeDeletePackage(Object[] markedForDelete, IPackageFragment[] packsToBeDeleted, Object[] othersToBeDeleted, boolean deleteSubs) throws CoreException, Exception {
		
		List allList= new ArrayList();
		allList.addAll(Arrays.asList(packsToBeDeleted));
		allList.addAll(Arrays.asList(othersToBeDeleted));
		
		Object[] all= allList.toArray();

		ParticipantTesting.reset();
		String[] deleteHandles= ParticipantTesting.createHandles(all);
		
		verifyEnabled(markedForDelete);
		performDummySearch();
		DeleteRefactoring ref= createRefactoring(markedForDelete);
		((JavaDeleteProcessor)ref.getProcessor()).setDeleteSubPackages(deleteSubs);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		
		// assure participants got notified of everything.
		ParticipantTesting.testDelete(deleteHandles);
	}

	//---- tests
	
	private IReorgQueries createReorgQueries() {
		return new MockReorgQueries();
	}

	public void testDisabled_emptySelection() throws Exception{
		verifyDisabled(new Object[] {});
	}

	public void testDisabled_projectAndNonProject() throws Exception{
		IJavaElement[] javaElements= {RefactoringTestSetup.getProject(), getPackageP()};
		verifyDisabled(javaElements);
	}

	public void testDisabled_nonExistingResource() throws Exception{
		IFolder folder= (IFolder)getPackageP().getResource();
		IFile file= folder.getFile("a.txt");
		
		IResource[] resources= {file};
		verifyDisabled(resources);			
	}
	
	public void testDisabled_nonExistingJavaElement() throws Exception{
		IJavaElement notExistingCu= getPackageP().getCompilationUnit("V.java");
		
		IJavaElement[] javaElements= {notExistingCu};
		verifyDisabled(javaElements);			
	}

	public void testDisabled_nullResource() throws Exception{
		Object[] elements= {RefactoringTestSetup.getProject(), null};
		verifyDisabled(elements);
	}
	
	public void testDisabled_nullJavaElement() throws Exception{
		Object[] elements= {getPackageP(), null};
		verifyDisabled(elements);
	}

	public void testDisabled_archiveElement() throws Exception{		
		IPackageFragmentRoot archive= getArchiveRoot();
		assertNotNull(archive);
		
		Object[] elements= archive.getChildren();
		verifyDisabled(elements);
	}

	public void testDisabled_externalArchive() throws Exception{
		IPackageFragmentRoot archive= getArchiveRoot();
		assertNotNull(archive);
		
		Object[] elements= {archive};
		verifyDisabled(elements);
	}

	public void testDisabled_archiveFromAnotherProject() throws Exception{
		//TODO implement me
	}

	public void testDisabled_binaryMember() throws Exception{
		//TODO implement me
	}

	public void testDisabled_javaProject() throws Exception{
		Object[] elements= {RefactoringTestSetup.getProject()};
		verifyDisabled(elements);
	}

	public void testEnabled_defaultPackage() throws Exception{
		IPackageFragment defaultPackage= getRoot().getPackageFragment("");
		ICompilationUnit cu= defaultPackage.createCompilationUnit("A.java", "", false, new NullProgressMonitor());
		
		try{
			Object[] elements= {defaultPackage};
			verifyEnabled(elements);		
		} finally{
			performDummySearch();
			cu.delete(true, new NullProgressMonitor());
		}
	}
	
	public void testDisabled_simpleProject() throws Exception{
		Object[] elements= {RefactoringTestSetup.getProject().getProject()};
		verifyDisabled(elements);
	}

	public void testEnabled_cu() throws Exception{
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "", false, new NullProgressMonitor());
		
		try{		
			Object[] elements= {cu};
			verifyEnabled(elements);
		} finally{
			performDummySearch();
			cu.delete(true, new NullProgressMonitor());
		}
	}	

	public void testEnabled_sourceReferences1() throws Exception{
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "", false, new NullProgressMonitor());
		try{
			IJavaElement importD= cu.createImport("java.lang.*", null, new NullProgressMonitor());
			IJavaElement packageD= cu.createPackageDeclaration("p", new NullProgressMonitor());
			IJavaElement type= cu.createType("class A{}", null, false, new NullProgressMonitor());
			
			Object[] elements= {packageD, importD, type};
			verifyEnabled(elements);			
		} finally{
			performDummySearch();
			cu.delete(true, new NullProgressMonitor());
		}
	}
	
	public void testEnabled_sourceReferences2() throws Exception{
		ICompilationUnit cu= getPackageP().createCompilationUnit("A.java", "", false, new NullProgressMonitor());
		try{
			IType type= cu.createType("class A{}", null, false, new NullProgressMonitor());
			IJavaElement field= type.createField("int i;", null, false, new NullProgressMonitor());
			IJavaElement method= type.createMethod("void f(){}", null, false, new NullProgressMonitor());
			IJavaElement initializer= type.createInitializer("{ int k= 0;}", null, new NullProgressMonitor());
			IJavaElement innerType= type.createType("class Inner{}", null, false,  new NullProgressMonitor());
			
			Object[] elements= {field, method, initializer, innerType};
			verifyEnabled(elements);			
		} finally{
			performDummySearch();
			cu.delete(true, new NullProgressMonitor());
		}
	}	

	
	public void testEnabled_file() throws Exception{
		IFolder folder= (IFolder)getPackageP().getResource();
		IFile file= folder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		try{
			Object[] elements= {file};
			verifyEnabled(elements);			
		} finally{
			performDummySearch();
			file.delete(true, false, null);
		}
	}	

	public void testEnabled_folder() throws Exception{
		IFolder folder= (IFolder)getPackageP().getResource();
		
		Object[] elements= {folder};
		verifyEnabled(elements);			
	}	

	public void testEnabled_readOnlyCu() throws Exception{
		//TODO implement me
	}	

	public void testEnabled_readOnlyFile() throws Exception{
		//TODO implement me
	}	
	
	public void testEnabled_package() throws Exception{
		Object[] elements= {getPackageP()};
		verifyEnabled(elements);
	}

	public void testEnabled_sourceFolder() throws Exception{
		Object[] elements= {getRoot()};
		verifyEnabled(elements);
	}	

	public void testEnabled_linkedFile() throws Exception{
		//TODO implement me
	}	
	
	public void testEnabled_linkedFolder() throws Exception{
		//TODO implement me
	}	

	public void testEnabled_linkedPackage() throws Exception{
		//TODO implement me
	}	

	public void testEnabled_linkedSourceFolder() throws Exception{
		//TODO implement me
	}

	public void testDeleteWithinCu0() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getField("i");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu1() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(fCuA, elem0, fCuA.getResource());

		checkDelete(elems, true);
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteWithinCu2() throws Exception{
		loadFileSetup();
		ParticipantTesting.reset();
		IJavaElement elem0= fCuA.getType("A").getField("i");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu3() throws Exception{
		loadFileSetup();
		ParticipantTesting.reset();
		IJavaElement elem0= fCuA.getType("A").getField("i");
		IJavaElement elem1= fCuA.getType("A").getField("j");
		IJavaElement[] elems= new IJavaElement[]{elem0, elem1};
		String[] handles= ParticipantTesting.createHandles(elem0, elem1);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu4() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getField("i");
		IJavaElement elem1= fCuA.getType("A").getField("k");
		IJavaElement[] elems= new IJavaElement[]{elem0, elem1};
		String[] handles= ParticipantTesting.createHandles(elem0, elem1);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu5() throws Exception{
		loadFileSetup();
		ParticipantTesting.reset();
		IJavaElement elem0= fCuA.getType("A").getField("j");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);		

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu6() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getField("j");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);
		
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu7() throws Exception{
		//exposes bug#9381 IPackageDeclaration is not ISourceManipulation
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getPackageDeclaration("p");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteWithinCu8() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getMethod("m", new String[0]);
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu9() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getInitializer(1);
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu10() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getInitializer(1);
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu11() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getImport("java.util.List");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu12() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getType("B");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteWithinCu13() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getType("B");
		IJavaElement elem1= fCuA.getType("A");
		IJavaElement[] elems= new IJavaElement[]{elem0, elem1};
		String[] handles= ParticipantTesting.createHandles(fCuA, fCuA.getTypes()[0], fCuA.getResource());
		
		checkDelete(elems, true);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu14() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getType("B");
		IJavaElement elem1= fCuA.getType("A");
		IJavaElement elem2= fCuA.getPackageDeclaration("p");
		IJavaElement[] elems= new IJavaElement[]{elem0, elem1, elem2};
		String[] handles= ParticipantTesting.createHandles(fCuA, fCuA.getTypes()[0], fCuA.getResource());
		
		checkDelete(elems, true);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu15() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getField("field");
		IJavaElement getter= fCuA.getType("A").getMethod("getField", new String[] {});
		IJavaElement setter= fCuA.getType("A").getMethod("setField", new String[] {"I"});
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0, getter, setter);
		
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu16() throws Exception{
		if (true) {
			printTestDisabledMessage("testDeleteWithinCu16 disabled for bug#55221");		
			return;
		}
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("Test");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu17() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getMethod("f", new String[0]);
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu18() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getMethod("fs", new String[0]);
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);
		
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteWithinCu19() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getImportContainer();
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);

		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu20() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getField("fEmpty");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);
		
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteWithinCu21() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("A").getField("var11");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);
		
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteWithinCu22() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IJavaElement elem0= fCuA.getType("B");
		IJavaElement[] elems= new IJavaElement[]{elem0};
		String[] handles= ParticipantTesting.createHandles(elem0);
		
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteWithinCu23() throws Exception{
		ParticipantTesting.reset();
		loadFileSetup();
		IType typeA= fCuA.getType("A");
		IJavaElement[] elems= new IJavaElement[]{
				typeA.getField("nestingDepth"), typeA.getField("openOnRun"),
				typeA.getMethod("getNestingDepth", new String[0]), typeA.getMethod("getOpenOnRun", new String[0])
		};
		String[] handles= ParticipantTesting.createHandles(elems);
		
		checkDelete(elems, false);
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteFile() throws Exception{
		ParticipantTesting.reset();
		IFolder folder= (IFolder)getPackageP().getResource();
		IFile file= folder.getFile("a.txt");
		file.create(getStream("123"), true, null);
		assertTrue("file does not exist", file.exists());
		Object[] elem= {file};
		verifyEnabled(elem);			
		performDummySearch();			
		
		String[] handles= ParticipantTesting.createHandles(file);
		
		DeleteRefactoring ref= createRefactoring(elem);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		assertTrue("file not deleted", ! file.exists());
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteFolder() throws Exception{
		ParticipantTesting.reset();
		IFolder folder= (IFolder)getPackageP().getResource();
		IFolder subFolder= folder.getFolder("subFolder");
		subFolder.create(true, true, null);

		assertTrue("folder does not exist", subFolder.exists());
		Object[] elements= {subFolder};
		verifyEnabled(elements);			
		performDummySearch();			

		String[] handles= ParticipantTesting.createHandles(subFolder);
		DeleteRefactoring ref= createRefactoring(elements);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		assertTrue("folder not deleted", ! subFolder.exists());
		ParticipantTesting.testDelete(handles);
	}

	public void testDeleteNestedFolders() throws Exception{
		ParticipantTesting.reset();
		IFolder folder= (IFolder)getPackageP().getResource();
		IFolder subFolder= folder.getFolder("subFolder");
		subFolder.create(true, true, null);
		IFolder subsubFolder= subFolder.getFolder("subSubFolder");
		subsubFolder.create(true, true, null);

		assertTrue("folder does not exist", subFolder.exists());
		assertTrue("folder does not exist", subsubFolder.exists());
		Object[] elements= {subFolder, subsubFolder};
		verifyEnabled(elements);			
		performDummySearch();			

		String[] handles= ParticipantTesting.createHandles(subFolder);
		DeleteRefactoring ref= createRefactoring(elements);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		assertTrue("folder not deleted", ! subFolder.exists());
		assertTrue("folder not deleted", ! subsubFolder.exists());
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteCu() throws Exception{
		ParticipantTesting.reset();
		ICompilationUnit newCU= getPackageP().createCompilationUnit("X.java", "package p; class X{}", true, new NullProgressMonitor());
		assertTrue("cu not created", newCU.exists());

		Object[] elements= {newCU};
		String[] handles= ParticipantTesting.createHandles(newCU, newCU.getTypes()[0], newCU.getResource());
		
		verifyEnabled(elements);			
		performDummySearch();			
		
		DeleteRefactoring ref= createRefactoring(elements);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		assertTrue("cu not deleted", ! newCU.exists());
		ParticipantTesting.testDelete(handles);		
	}
	
	public void testDeleteSourceFolder() throws Exception{
		ParticipantTesting.reset();
		IPackageFragmentRoot fredRoot= JavaProjectHelper.addSourceContainer(RefactoringTestSetup.getProject(), "fred");
		assertTrue("not created", fredRoot.exists());

		Object[] elements= {fredRoot};
		verifyEnabled(elements);			
		performDummySearch();			
		String[] handles= ParticipantTesting.createHandles(fredRoot, fredRoot.getResource());
		DeleteRefactoring ref= createRefactoring(elements);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		assertTrue("not deleted", ! fredRoot.exists());
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteInternalJAR() throws Exception{
		ParticipantTesting.reset();
		File lib= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.MYLIB);
		assertTrue("lib does not exist",  lib != null && lib.exists());
		IPackageFragmentRoot internalJAR= JavaProjectHelper.addLibraryWithImport(RefactoringTestSetup.getProject(), Path.fromOSString(lib.getPath()), null, null);

		Object[] elements= {internalJAR};
		verifyEnabled(elements);			
		performDummySearch();
		String[] handles= ParticipantTesting.createHandles(internalJAR);

		DeleteRefactoring ref= createRefactoring(elements);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		assertTrue("not deleted", ! internalJAR.exists());		
		ParticipantTesting.testDelete(handles);
	}
	
	public void testDeleteClassFile() throws Exception{
		//TODO implement me - how do i get a handle to a class file?
	}
	
	public void testDeletePackage() throws Exception{
		// newPackage    <- delete
		// newPackage.A
		// newPackage.file
		// all three items must be deleted.
		// Notification of package delete and folder delete
		ParticipantTesting.reset();
		IPackageFragment newPackage= getRoot().createPackageFragment("newPackage", true, new NullProgressMonitor());
		assertTrue("package not created", newPackage.exists());
		newPackage.createCompilationUnit("A.java", "public class A {}", false, null);
		IFile file= ((IContainer)newPackage.getResource()).getFile(new Path("Z.txt"));
		file.create(getStream("123"), true, null);
		
		Object[] elements= {newPackage};
		verifyEnabled(elements);			
		performDummySearch();			
		String[] deleteHandles= ParticipantTesting.createHandles(newPackage, newPackage.getResource());
		
		DeleteRefactoring ref= createRefactoring(elements);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		assertTrue("package not deleted", ! newPackage.exists());
		
		ParticipantTesting.testDelete(deleteHandles);
	}

	public void testDeletePackage2() throws Exception{
		// p1   <- delete
		// p1.A
		// p1.file
		// p1.p2
		// this tests cleaning of packages (p2 is not deleted)
		ParticipantTesting.reset();
		IPackageFragment newPackage= getRoot().createPackageFragment("p1", true, new NullProgressMonitor());
		getRoot().createPackageFragment("p1.p2", true, new NullProgressMonitor());
		assertTrue("package not created", newPackage.exists());
		ICompilationUnit cu= newPackage.createCompilationUnit("A.java", "public class A {}", false, null);
		IFile file= ((IContainer)newPackage.getResource()).getFile(new Path("Z.txt"));
		file.create(getStream("123"), true, null);
		
		Object[] elements= {newPackage};
		verifyEnabled(elements);			
		performDummySearch();			
		String[] deleteHandles= ParticipantTesting.createHandles(newPackage, cu.getResource(), file);
		
		DeleteRefactoring ref= createRefactoring(elements);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);
		//Package is not delete since it had sub packages
		assertTrue("package deleted", newPackage.exists());
		
		ParticipantTesting.testDelete(deleteHandles);
	}
	
	public void testDeletePackage3() throws Exception {
		// a0.a1.a2.a3 <- delete
		// a0.a1.a2.a3.A
		// all packages must be removed; folder a0 must be removed.
		IPackageFragment[] frags= createPackagePath(4);
		ICompilationUnit a= frags[3].createCompilationUnit("A.java", "public class A {}", false, null);
		executeDeletePackage(new Object[] { frags[3] }, frags, new Object[] {  frags[0].getResource() } );
		assertPackagesAreDeleted(frags);
		assertFalse(a.exists());
	}
	
	public void testDeletePackage4() throws Exception {
		// a0.a1.a2.a3 <- delete
		// a0.a1.a2.a3.A <- delete
		// all packages must be removed; folder a0 must be removed.
		IPackageFragment[] frags= createPackagePath(4);
		ICompilationUnit a= frags[3].createCompilationUnit("A.java", "public class A {}", false, null);
		executeDeletePackage(new Object[] { frags[3], a }, frags, new Object[] {  frags[0].getResource() } );
		assertPackagesAreDeleted(frags);
		assertFalse(a.exists());
	}
	
	public void testDeletePackage5() throws Exception {
		// a0.a1.a2.A <- not deleted
		// a0.a1.a2.a3.a4.a5 <- delete
		// only a3, a4, a5 are to be deleted; folder a3 must be removed.
		IPackageFragment[] frags= createPackagePath(6);
		ICompilationUnit a= frags[2].createCompilationUnit("A.java", "public class A {}", false, null);
		executeDeletePackage(new Object[] { frags[5] }, new IPackageFragment[] { frags[5], frags[4], frags[3] }, new Object[] { frags[3].getResource() });
		assertPackagesAreDeleted(new IPackageFragment[] { frags[5], frags[4], frags[3] });
		assertTrue(a.exists());
	}
	
	public void testDeletePackage6() throws Exception {
		// a0.a1.a2.anotherPackage
		// a0.a1.a2.a3.a4.a5 <- delete
		// only a3, a4, a5 are to be deleted; folder a3 must be removed
		IPackageFragment[] frags= createPackagePath(6);
		IPackageFragment another= getRoot().createPackageFragment("a0.a1.a2.anotherPackage", true, null);
		executeDeletePackage(new Object[] { frags[5] }, new IPackageFragment[] { frags[5], frags[4], frags[3] }, new Object[] { frags[3].getResource() });
		assertPackagesAreDeleted(new IPackageFragment[] { frags[5], frags[4], frags[3] });
		assertTrue(another.exists());
	}
	
	public void testDeletePackage7() throws Exception {
		// a0.a1.a2.A <- delete
		// a0.a1.a2.a3.a4.a5 <- delete
		// all packages must be deleted; folder a0 must be removed
		IPackageFragment[] frags= createPackagePath(6);
		ICompilationUnit a= frags[2].createCompilationUnit("A.java", "public class A {}", false, null);
		executeDeletePackage(new Object[] { frags[5], a }, frags, new Object[] { frags[0].getResource() });
		assertPackagesAreDeleted(frags);
		assertFalse(a.exists());
	}
	
	public void testDeletePackage8() throws Exception {
		// a0.a1.a2.A <- delete
		// a0.a1.a2.a3.Z <- don't delete
		// a0.a1.a2.a3.a4.a5 <- delete
		// only someFile, a4, and a5 are to be deleted; notification about a4, a5, A
		IPackageFragment[] frags= createPackagePath(6);
		ICompilationUnit a= frags[2].createCompilationUnit("A.java", "public class A {}", false, null);
		IFile file= ((IContainer)frags[3].getResource()).getFile(new Path("Z.txt"));
		file.create(getStream("123"), true, null);
		executeDeletePackage(new Object[] { frags[5], a }, new IPackageFragment[] { frags[5], frags[4] }, new Object[] { frags[4].getResource(), a.getResource(), a, a.getType("A") });
		assertPackagesAreDeleted(new IPackageFragment[] { frags[5], frags[4] });
		assertFalse(a.exists());
		assertTrue(file.exists());
	}
	
	public void testDeletePackage9() throws Exception {
		// a0.a1.a2.A <- delete
		// a0.a1.a2.a3.Z <- delete
		// a0.a1.a2.a3.a4.a5 <- delete
		// all packages must be removed; folder a0 must be removed
		IPackageFragment[] frags= createPackagePath(6);
		ICompilationUnit a= frags[2].createCompilationUnit("A.java", "public class A {}", false, null);
		IFile file= ((IContainer)frags[3].getResource()).getFile(new Path("Z.txt"));
		file.create(getStream("123"), true, null);
		executeDeletePackage(new Object[] { frags[5], a, file }, frags, new Object[] {  frags[0].getResource() });
		assertPackagesAreDeleted(frags);
		assertFalse(a.exists());
		assertFalse(file.exists());
	}
	
	public void testDeletePackage10() throws Exception {
		// a0.a1.a2 <- delete
		// a0.a1.a2.A <- delete
		// a0.a1.a2.a3 <- do not delete
		// only A must be removed
		// This tests "cleaning" of packages -> folder a0.a1.a2 does NOT get removed.
		IPackageFragment[] frags= createPackagePath(4);
		ICompilationUnit a= frags[2].createCompilationUnit("A.java", "public class A {}", false, null);
		
		ParticipantTesting.reset();
		final Object[] markedForDelete= new Object[] { frags[2], a.getResource() };
		String[] deleteHandles= ParticipantTesting.createHandles(markedForDelete);
		
		verifyEnabled(markedForDelete);
		performDummySearch();
		DeleteRefactoring ref= createRefactoring(markedForDelete);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);

		// test handles (!! only the package, not the resource)
		ParticipantTesting.testDelete(deleteHandles);
		// Package is not deleted since it had sub packages
		assertTrue(frags[2].exists());
		assertTrue(frags[2].getResource().exists());
		assertFalse(a.exists());
	}
	
	public void testDeletePackage11() throws Exception {
		// Test deletion of default package of a project which is its own source folder
		// (default) <- delete
		// (default) x.txt <- don't delete
		// expected: x.txt must not be deleted

		IJavaProject newJavaProject= JavaProjectHelper.createJavaProject("TestProject"+System.currentTimeMillis(), "bin");
		JavaProjectHelper.addRTJar(newJavaProject);
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(newJavaProject, null);
		
		IPackageFragment defaultP= root.getPackageFragment("");
		IFile file= ((IContainer)defaultP.getResource()).getFile(new Path("Z.txt"));
		file.create(getStream("123"), true, null);
		
		ICompilationUnit a= defaultP.createCompilationUnit("A.java", "public class A {}", false, null);
		
		ParticipantTesting.reset();
		final Object[] markedForDelete= new Object[] { defaultP };
		String[] deleteHandles= ParticipantTesting.createHandles(new Object[] { defaultP, a.getResource() });
		
		verifyEnabled(markedForDelete);
		performDummySearch();
		DeleteRefactoring ref= createRefactoring(markedForDelete);
		RefactoringStatus status= performRefactoring(ref, false);
		assertEquals("expected to pass", null, status);

		ParticipantTesting.testDelete(deleteHandles);
		assertTrue(defaultP.exists());
		assertTrue(defaultP.getResource().exists());
		assertTrue(file.exists());
		assertFalse(a.exists());
		assertFalse(a.getResource().exists());
	}
	
	public void testDeletePackage12() throws Exception {
		// a0		<- delete
		// a0.a1	<- delete
		// a0.a1.a2
		// a0 and a1 are to be cleaned, do not report any folder deletions
		IPackageFragment[] frags= createPackagePath(3);
		executeDeletePackage(new Object[] { frags[0], frags[1] }, new IPackageFragment[] { frags[0], frags[1] }, new Object[] { });
		assertTrue(frags[0].exists());
		assertTrue(frags[1].exists());
		assertTrue(frags[2].exists());
	}
	
	public void testDeletePackageSub1() throws Exception {
		// a0.a1.a2 <-delete with subs
		// a0.a1.a2.a3
		// a0.a1.a2.a3.file
		// a0.a1.a2.a3.A
		// a0.a1.a2.a3.a4
		// expected: everything deleted
		IPackageFragment[] frags= createPackagePath(5);
		ICompilationUnit a= frags[3].createCompilationUnit("A.java", "public class A {}", false, null);
		IFile file= ((IContainer)frags[3].getResource()).getFile(new Path("Z.txt"));
		file.create(getStream("123"), true, null);
		executeDeletePackage(new Object[] { frags[2] }, frags, new Object[] {  frags[0].getResource() }, true);
		assertPackagesAreDeleted(frags);
		assertFalse(a.exists());
		assertFalse(file.exists());
	}
	
	public void testDeletePackageSub2() throws Exception {
		// (default)	<- delete
		// a0
		// a0.a1
		// expected: everything deleted; notification about deletion of: 
		// PackageFragments: a0, a0.a1, <default>
		// Folders: a0 (NOT the folder of the default package)
		IPackageFragment[] frags= createPackagePath(2);
		IPackageFragment p= getRoot().getPackageFragment("p");
		if (p.exists()) p.delete(true, null);
		final IPackageFragment defaultPackage= getRoot().getPackageFragment("");
		executeDeletePackage(new Object[] { defaultPackage }, frags, new Object[] { defaultPackage, frags[0].getResource() } , true);
		assertPackagesAreDeleted(frags);
	}
	
	public void testDeletePackageSub3() throws Exception {
		// (default)	<- delete
		// (default).A
		// a0
		// a0.a1
		// expected: everything deleted; notification about deletion of: 
		// PackageFragments: a0, a0.a1, <default>
		// Folders: a0 (NOT the folder of the default package)
		// Files: A.java (NOT other files in root, like .classpath).
		IPackageFragment[] frags= createPackagePath(2);
		final IPackageFragment defaultPackage= getRoot().getPackageFragment("");
		IPackageFragment p= getRoot().getPackageFragment("p");
		if (p.exists()) p.delete(true, null);
		ICompilationUnit a= defaultPackage.createCompilationUnit("A.java", "public class A {}", false, null);
		executeDeletePackage(new Object[] { defaultPackage }, frags, new Object[] { defaultPackage, a.getResource(), frags[0].getResource() } , true);
		assertPackagesAreDeleted(frags);
	}

}
