package org.eclipse.jdt.ui.tests.core;

import java.io.File;
import java.util.zip.ZipFile;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.JavaTestPlugin;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.wizards.ClassPathDetector;

/**
  */
public class ClassPathDetectorTest extends TestCase {

	private static final Class THIS= ClassPathDetectorTest.class;
	
	private IJavaProject fJProject1;

	private boolean fEnableAutoBuildAfterTesting;

	public ClassPathDetectorTest(String name) {
		super(name);
	}

	public static Test suite() {
		if (true) {
			return new TestSuite(THIS);
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new ClassPathDetectorTest("testClassFolder"));
			return suite;
		}
	}


	protected void setUp() throws Exception {

		IWorkspace workspace= JavaTestPlugin.getWorkspace();
		assertNotNull(workspace);

		// disable auto-build
		IWorkspaceDescription workspaceDesc= workspace.getDescription();
		if (workspaceDesc.isAutoBuilding()) {
			fEnableAutoBuildAfterTesting= true;
			JavaProjectHelper.setAutoBuilding(false);
		}
		
		IPreferenceStore store= PreferenceConstants.getPreferenceStore();
		store.setValue(PreferenceConstants.SRCBIN_BINNAME, "bin");

		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
	}

	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJProject1);

		if (fEnableAutoBuildAfterTesting)
			JavaProjectHelper.setAutoBuilding(true);		
	}
	
	private boolean hasSamePaths(IPath[] a, IPath[] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int i= 0; i < a.length; i++) {
			if (!a[i].equals(b[i])) {
				return false;
			}
		}
		return true;
	}
	

	private IClasspathEntry findEntry(IClasspathEntry entry, IClasspathEntry[] entries) {
		for (int i= 0; i < entries.length; i++) {
			IClasspathEntry curr= entries[i];
			if (curr.getPath().equals(entry.getPath()) && curr.getEntryKind() == entry.getEntryKind()) {
				if (curr.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					if (hasSamePaths(curr.getExclusionPatterns(), entry.getExclusionPatterns())) {
						return curr;
					}
				} else {
					return curr;
				}
			}
		}
		return null;
	}



	private void assertSameClasspath(IClasspathEntry[] projectEntries, IClasspathEntry[] entries) throws Exception {
		assertEquals("Number of classpath entries", projectEntries.length, entries.length);
		
		for (int i= 0; i < projectEntries.length; i++) {
			IClasspathEntry curr= projectEntries[i];
			assertTrue("entry not found: " + curr.getPath(), findEntry(curr, entries) != null);
		}
	}
	
	private void clearClasspath() throws Exception {
		
		// see 29306 
		IClasspathEntry other= JavaCore.newSourceEntry(fJProject1.getPath());
		fJProject1.setRawClasspath(new IClasspathEntry[] { other }, fJProject1.getPath().append("bin"), null);
		
		//fJProject1.setRawClasspath(new IClasspathEntry[0], projectOutput, null);		
	}
	
	
	
	public void testSourceAndLibrary() throws Exception {
		// source folder & internal JAR
		
		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC);
		assertTrue("junit src not found", junitSrcArchive != null && junitSrcArchive.exists());
		ZipFile zipfile= new ZipFile(junitSrcArchive);
		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src", zipfile);		
		
		File mylibJar= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.MYLIB);
		assertTrue("lib not found", junitSrcArchive != null && junitSrcArchive.exists());

		JavaProjectHelper.addLibraryWithImport(fJProject1, new Path(mylibJar.getPath()), null, null);
		
		IClasspathEntry[] jreEntries= PreferenceConstants.getDefaultJRELibrary();
		for (int i= 0; i < jreEntries.length; i++) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntries[i]);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		
		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		IPath projectOutput= fJProject1.getPath().append("bin");
		
		clearClasspath();
		
		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject());
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull("No classpath detected", entries);
		assertNotNull("No outputLocation detected", outputLocation);
		
		assertSameClasspath(projectEntries, entries);

		assertTrue("Output folder", outputLocation.equals(projectOutput));
	}

	public void testTwoSourceFolders() throws Exception {
		// 2 source folders
		
		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC);
		assertTrue("junit src not found", junitSrcArchive != null && junitSrcArchive.exists());
		ZipFile zipfile= new ZipFile(junitSrcArchive);
		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src1", zipfile);
		
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJProject1, "src2");
		IPackageFragment pack1= root.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        getClass();\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		IClasspathEntry[] jreEntries= PreferenceConstants.getDefaultJRELibrary();
		for (int i= 0; i < jreEntries.length; i++) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntries[i]);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		
		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		
		IPath projectOutput= fJProject1.getPath().append("bin");
		clearClasspath();
		
		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject());
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull("No classpath detected", entries);
		assertNotNull("No outputLocation detected", outputLocation);
		
		assertSameClasspath(projectEntries, entries);
		
		assertTrue("Output folder", outputLocation.equals(projectOutput));
	}
	
	public void testNestedSources() throws Exception {
		// 2 nested source folders
		
		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC);
		assertTrue("junit src not found", junitSrcArchive != null && junitSrcArchive.exists());
		ZipFile zipfile= new ZipFile(junitSrcArchive);
		IPath[] exclusionFilter= new IPath[] { new Path("src2/") };
		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src1", zipfile, exclusionFilter);
		
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJProject1, "src1/src2");
		IPackageFragment pack1= root.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        getClass();\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		IClasspathEntry[] jreEntries= PreferenceConstants.getDefaultJRELibrary();
		for (int i= 0; i < jreEntries.length; i++) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntries[i]);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		
		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		
		IPath projectOutput= fJProject1.getPath().append("bin");
		clearClasspath();

		
		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject());
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull("No classpath detected", entries);
		assertNotNull("No outputLocation detected", outputLocation);
		
		assertSameClasspath(projectEntries, entries);
		
		assertTrue("Output folder", outputLocation.equals(projectOutput));
	}
	
	public void testClassFolder() throws Exception {
		// 2 nested source folders
		
/**		File lib= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.MYLIB);
		assertTrue("lib not found", lib != null && lib.exists());
		ZipFile zipfile= new ZipFile(lib);
		
		JavaProjectHelper.addClassFolderWithImport(fJProject1, "cf", null, null, zipfile);
		
		IClasspathEntry[] jreEntries= PreferenceConstants.getDefaultJRELibrary();
		for (int i= 0; i < jreEntries.length; i++) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntries[i]);
		}
		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		
		IPath projectOutput= fJProject1.getPath().append("bin");
		clearClasspath();
		
		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject());
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull("No classpath detected", entries);
		assertNotNull("No outputLocation detected", outputLocation);
		
		assertSameClasspath(projectEntries, entries);
		
		assertTrue("Output folder", outputLocation.equals(projectOutput));
		*/
	}	
	

}
