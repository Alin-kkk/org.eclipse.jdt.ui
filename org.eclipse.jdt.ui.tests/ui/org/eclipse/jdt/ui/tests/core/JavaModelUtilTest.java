/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.ui.tests.core;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipFile;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.JavaTestPlugin;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

public class JavaModelUtilTest extends TestCase {
	
	private static final Class THIS= JavaModelUtilTest.class;
	
	private IJavaProject fJProject1;
	private IJavaProject fJProject2;

	private static final IPath LIB= new Path("testresources/mylib.jar");

	public JavaModelUtilTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(THIS);
	}


	protected void setUp() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		fJProject2= JavaProjectHelper.createJavaProject("TestProject2", "bin");

		IPackageFragmentRoot jdk= JavaProjectHelper.addVariableRTJar(fJProject1, "JRE_LIB_TEST", null, null);
		assertTrue("jdk not found", jdk != null);

		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC);
		assertTrue("junit src not found", junitSrcArchive != null && junitSrcArchive.exists());
		ZipFile zipfile= new ZipFile(junitSrcArchive);
		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src", zipfile);

		File mylibJar= JavaTestPlugin.getDefault().getFileInPlugin(LIB);
		assertTrue("lib not found", junitSrcArchive != null && junitSrcArchive.exists());
		
		JavaProjectHelper.addLibraryWithImport(fJProject1, new Path(mylibJar.getPath()), null, null);

		JavaProjectHelper.addVariableEntry(fJProject2, new Path("JRE_LIB_TEST"), null, null);

		IPackageFragmentRoot root1= JavaProjectHelper.addSourceContainer(fJProject2, "src");
		IPackageFragment pack1= root1.createPackageFragment("pack1", true, null);
		
		ICompilationUnit cu1= pack1.getCompilationUnit("ReqProjType.java");
		cu1.createType("public class ReqProjType { static class Inner { static class InnerInner {} }\n}\n", null, true, null);

		JavaProjectHelper.addRequiredProject(fJProject1, fJProject2);
	}


	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJProject1);
		JavaProjectHelper.delete(fJProject2);
	}


	private void assertElementName(String name, IJavaElement elem, int type) {
		assertNotNull(name, elem);
		assertEquals(name + "-name", name, elem.getElementName());
		assertTrue(name + "-type", type == elem.getElementType());
	}

	public void testFindType() throws Exception {
		IType type= JavaModelUtil.findType(fJProject1, "junit.extensions.ExceptionTestCase");
		assertElementName("ExceptionTestCase", type, IJavaElement.TYPE);

		type= JavaModelUtil.findType(fJProject1, "junit.samples.money.IMoney");
		assertElementName("IMoney", type, IJavaElement.TYPE);	

		type= JavaModelUtil.findType(fJProject1, "junit.tests.TestCaseTest.TornDown");
		assertElementName("TornDown", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "mylib.Foo");
		assertElementName("Foo", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "mylib.Foo.FooInner");
		assertElementName("FooInner", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "mylib.Foo.FooInner.FooInnerInner");
		assertElementName("FooInnerInner", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "pack1.ReqProjType");
		assertElementName("ReqProjType", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "pack1.ReqProjType.Inner");
		assertElementName("Inner", type, IJavaElement.TYPE);	

		type= JavaModelUtil.findType(fJProject1, "pack1.ReqProjType.Inner.InnerInner");
		assertElementName("InnerInner", type, IJavaElement.TYPE);	
	}
	
	
	
	public void testFindType2() throws Exception {
		IType type= JavaModelUtil.findType(fJProject1, "junit.extensions", "ExceptionTestCase");
		assertElementName("ExceptionTestCase", type, IJavaElement.TYPE);

		type= JavaModelUtil.findType(fJProject1, "junit.samples.money" , "IMoney");
		assertElementName("IMoney", type, IJavaElement.TYPE);	

		type= JavaModelUtil.findType(fJProject1, "junit.tests", "TestCaseTest.TornDown");
		assertElementName("TornDown", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "mylib" , "Foo");
		assertElementName("Foo", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "mylib", "Foo.FooInner");
		assertElementName("FooInner", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "mylib", "Foo.FooInner.FooInnerInner");
		assertElementName("FooInnerInner", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "pack1", "ReqProjType");
		assertElementName("ReqProjType", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findType(fJProject1, "pack1", "ReqProjType.Inner");
		assertElementName("Inner", type, IJavaElement.TYPE);	

		type= JavaModelUtil.findType(fJProject1, "pack1", "ReqProjType.Inner.InnerInner");
		assertElementName("InnerInner", type, IJavaElement.TYPE);				
	}
	
	public void testFindTypeContainer() throws Exception {
		IJavaElement elem= JavaModelUtil.findTypeContainer(fJProject1, "junit.extensions");
		assertElementName("junit.extensions", elem, IJavaElement.PACKAGE_FRAGMENT);

		elem= JavaModelUtil.findTypeContainer(fJProject1, "junit.tests.TestCaseTest");
		assertElementName("TestCaseTest", elem, IJavaElement.TYPE);
		
		elem= JavaModelUtil.findTypeContainer(fJProject1, "mylib" );
		assertElementName("mylib", elem, IJavaElement.PACKAGE_FRAGMENT);
		
		elem= JavaModelUtil.findTypeContainer(fJProject1, "mylib.Foo");
		assertElementName("Foo", elem, IJavaElement.TYPE);
		
		elem= JavaModelUtil.findTypeContainer(fJProject1, "mylib.Foo.FooInner");
		assertElementName("FooInner", elem, IJavaElement.TYPE);
		
		elem= JavaModelUtil.findTypeContainer(fJProject1, "pack1");
		assertElementName("pack1", elem, IJavaElement.PACKAGE_FRAGMENT);
		
		elem= JavaModelUtil.findTypeContainer(fJProject1, "pack1.ReqProjType");
		assertElementName("ReqProjType", elem, IJavaElement.TYPE);	

		elem= JavaModelUtil.findTypeContainer(fJProject1, "pack1.ReqProjType.Inner");
		assertElementName("Inner", elem, IJavaElement.TYPE);				
	}
	
	public void testFindTypeInCompilationUnit() throws Exception {
		ICompilationUnit cu= (ICompilationUnit) fJProject1.findElement(new Path("junit/tests/TestCaseTest.java"));
		assertElementName("TestCaseTest.java", cu, IJavaElement.COMPILATION_UNIT);
		
		IType type= JavaModelUtil.findTypeInCompilationUnit(cu, "TestCaseTest");
		assertElementName("TestCaseTest", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findTypeInCompilationUnit(cu, "TestCaseTest.TornDown");
		assertElementName("TornDown", type, IJavaElement.TYPE);
		
		cu= (ICompilationUnit) fJProject1.findElement(new Path("pack1/ReqProjType.java"));
		assertElementName("ReqProjType.java", cu, IJavaElement.COMPILATION_UNIT);
		
		type= JavaModelUtil.findTypeInCompilationUnit(cu, "ReqProjType");
		assertElementName("ReqProjType", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findTypeInCompilationUnit(cu, "ReqProjType.Inner");
		assertElementName("Inner", type, IJavaElement.TYPE);
		
		type= JavaModelUtil.findTypeInCompilationUnit(cu, "ReqProjType.Inner.InnerInner");
		assertElementName("InnerInner", type, IJavaElement.TYPE);		
	}
	
	public void testFindMemberInCompilationUnit() throws Exception {
		ICompilationUnit cu= (ICompilationUnit) fJProject1.findElement(new Path("junit/tests/TestCaseTest.java"));
		assertElementName("TestCaseTest.java", cu, IJavaElement.COMPILATION_UNIT);
		ArrayList children= new ArrayList();
		
		IType type= JavaModelUtil.findTypeInCompilationUnit(cu, "TestCaseTest");
		assertElementName("TestCaseTest", type, IJavaElement.TYPE);
		
		children.addAll(Arrays.asList(type.getChildren()));
		
		type= JavaModelUtil.findTypeInCompilationUnit(cu, "TestCaseTest.TornDown");
		assertElementName("TornDown", type, IJavaElement.TYPE);
		
		children.addAll(Arrays.asList(type.getChildren()));
		
		assertTrue("a", children.size() == 19);

		for (int i= 0; i < children.size(); i++) {
			Object curr= children.get(i);
			assertTrue("b", curr instanceof IMember);
			IMember member= JavaModelUtil.findMemberInCompilationUnit(cu, (IMember) curr);
			assertEquals("b-" + i, curr, member);
		}
	}
	
	private void assertClasspathEntry(String name, IJavaElement elem, IPath path, int type) throws Exception {
		IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(elem);
		assertNotNull(name + "-noroot", root);
		IClasspathEntry entry= JavaModelUtil.getRawClasspathEntry(root);
		assertNotNull(name + "-nocp", entry);
		assertEquals(name + "-wrongpath", entry.getPath(), path);
		assertTrue(name + "-wrongtype", type == entry.getEntryKind());
	}
	
	public void testGetRawClasspathEntry() throws Exception {
		IType type= JavaModelUtil.findType(fJProject1, "junit.extensions.ExceptionTestCase");
		assertElementName("ExceptionTestCase", type, IJavaElement.TYPE);
		IPath path= fJProject1.getProject().getFullPath().append("src");
		assertClasspathEntry("ExceptionTestCase", type, path, IClasspathEntry.CPE_SOURCE);
		
		type= JavaModelUtil.findType(fJProject1, "mylib.Foo");
		assertElementName("Foo", type, IJavaElement.TYPE);
		path= fJProject1.getProject().getFullPath().append(LIB.lastSegment());
		assertClasspathEntry("Foo", type, path, IClasspathEntry.CPE_LIBRARY);
		
		type= JavaModelUtil.findType(fJProject1, "java.lang.Object");
		assertElementName("Object", type, IJavaElement.TYPE);
		path= new Path("JRE_LIB_TEST");
		assertClasspathEntry("Object", type, path, IClasspathEntry.CPE_VARIABLE);
	
		type= JavaModelUtil.findType(fJProject1, "pack1.ReqProjType");
		assertElementName("ReqProjType", type, IJavaElement.TYPE);
		path= fJProject2.getProject().getFullPath().append("src");
		assertClasspathEntry("ReqProjType", type, path, IClasspathEntry.CPE_SOURCE);		
	}
	
	public void testIsOnBuildPath() throws Exception {
		IType type= JavaModelUtil.findType(fJProject1, "junit.extensions.ExceptionTestCase");
		assertElementName("ExceptionTestCase", type, IJavaElement.TYPE);
		assertTrue("ExceptionTestCase-bp1", JavaModelUtil.isOnBuildPath(fJProject1, type));
		assertTrue("ExceptionTestCase-bp2", !JavaModelUtil.isOnBuildPath(fJProject2, type));
		
		type= JavaModelUtil.findType(fJProject1, "java.lang.Object");
		assertElementName("Object", type, IJavaElement.TYPE);		
		assertTrue("Object-bp1", JavaModelUtil.isOnBuildPath(fJProject1, type));
		// relies on shared objects for library entries
		assertTrue("Object-bp2", JavaModelUtil.isOnBuildPath(fJProject2, type));			
		
		type= JavaModelUtil.findType(fJProject1, "pack1.ReqProjType");
		assertElementName("ReqProjType", type, IJavaElement.TYPE);
		assertTrue("ReqProjType-bp1", JavaModelUtil.isOnBuildPath(fJProject1, type));
		// relies on shared objects for project entries
		assertTrue("ReqProjType-bp2", JavaModelUtil.isOnBuildPath(fJProject2, type));		
	}
	
	private void assertFindMethod(String methName, String[] paramTypeNames, boolean isConstructor, IType type) throws Exception {
		String[] sig= new String[paramTypeNames.length];
		for (int i= 0; i < paramTypeNames.length; i++) {
			// create as unresolved
			String name= Signature.getSimpleName(paramTypeNames[i]);
			sig[i]= Signature.createTypeSignature(name, false);
			assertNotNull(methName + "-ts1" + i, sig[i]);
		}
		IMethod meth= JavaModelUtil.findMethod(methName, sig, isConstructor, type);
		assertElementName(methName, meth, IJavaElement.METHOD);
		assertTrue("methName-nparam1", meth.getParameterTypes().length == paramTypeNames.length);
		
		for (int i= 0; i < paramTypeNames.length; i++) {
			// create as resolved
			sig[i]= Signature.createTypeSignature(paramTypeNames[i], true);
			assertNotNull(methName + "-ts2" + i, sig[i]);
		}		
		meth= JavaModelUtil.findMethod(methName, sig, isConstructor, type);
		assertElementName(methName, meth, IJavaElement.METHOD);
		assertTrue("methName-nparam2", meth.getParameterTypes().length == paramTypeNames.length);
	}
	
	public void testFindMethod() throws Exception {
		IType type= JavaModelUtil.findType(fJProject1, "junit.framework.Assert");
		assertElementName("Assert", type, IJavaElement.TYPE);
		
		assertFindMethod("assertNotNull", new String[] { "java.lang.Object" }, false, type);
		assertFindMethod("assertNotNull", new String[] { "java.lang.String", "java.lang.Object" }, false, type);
		assertFindMethod("assertEquals", new String[] { "java.lang.String", "double", "double", "double" }, false, type);
		assertFindMethod("assertEquals", new String[] { "java.lang.String", "long", "long" }, false, type);
		assertFindMethod("Assert", new String[0], true, type);

		type= JavaModelUtil.findType(fJProject1, "junit.samples.money.MoneyTest");
		assertElementName("MoneyTest", type, IJavaElement.TYPE);

		assertFindMethod("main", new String[] { "java.lang.String[]" }, false, type);
		assertFindMethod("setUp", new String[0] , false, type);
		assertFindMethod("MoneyTest", new String[] { "java.lang.String" } , true, type);
	}

	private void assertFindMethodInHierarchy(String methName, String[] paramTypeNames, boolean isConstructor, IType type, String declaringTypeName) throws Exception {
		ITypeHierarchy hierarchy= type.newSupertypeHierarchy(null);
		
		String[] sig= new String[paramTypeNames.length];
		for (int i= 0; i < paramTypeNames.length; i++) {
			// create as unresolved
			String name= Signature.getSimpleName(paramTypeNames[i]);
			sig[i]= Signature.createTypeSignature(name, false);
			assertNotNull(methName + "-ts1" + i, sig[i]);
		}
		IMethod meth= JavaModelUtil.findMethodDeclarationInHierarchy(hierarchy, type, methName, sig, isConstructor);
		assertElementName(methName, meth, IJavaElement.METHOD);
		assertTrue("methName-nparam1", meth.getParameterTypes().length == paramTypeNames.length);
		assertEquals("methName-decltype", declaringTypeName, JavaModelUtil.getFullyQualifiedName(meth.getDeclaringType()));
		
		for (int i= 0; i < paramTypeNames.length; i++) {
			// create as resolved
			sig[i]= Signature.createTypeSignature(paramTypeNames[i], true);
			assertNotNull(methName + "-ts2" + i, sig[i]);
		}		
		meth= JavaModelUtil.findMethodDeclarationInHierarchy(hierarchy, type, methName, sig, isConstructor);
		assertElementName(methName, meth, IJavaElement.METHOD);
		assertTrue("methName-nparam2", meth.getParameterTypes().length == paramTypeNames.length);
		assertEquals("methName-decltype", declaringTypeName, JavaModelUtil.getFullyQualifiedName(meth.getDeclaringType()));
	}

	public void testFindMethodInHierarchy() throws Exception {
		IType type= JavaModelUtil.findType(fJProject1, "junit.extensions.TestSetup");
		assertElementName("TestSetup", type, IJavaElement.TYPE);		
		
		assertFindMethodInHierarchy("run", new String[] { "junit.framework.TestResult" }, false, type, "junit.framework.Test");
		assertFindMethodInHierarchy("toString", new String[] {} , false, type, "java.lang.Object");
	}
	
	public void testHasMainMethod() throws Exception {
		IType type= JavaModelUtil.findType(fJProject1, "junit.samples.money.MoneyTest");
		assertElementName("MoneyTest", type, IJavaElement.TYPE);
		
		assertTrue("MoneyTest-nomain", JavaModelUtil.hasMainMethod(type));
		
		type= JavaModelUtil.findType(fJProject1, "junit.framework.TestResult");
		assertElementName("TestResult", type, IJavaElement.TYPE);
		
		assertTrue("TestResult-hasmain", !JavaModelUtil.hasMainMethod(type));
		
		type= JavaModelUtil.findType(fJProject1, "junit.samples.VectorTest");
		assertElementName("VectorTest", type, IJavaElement.TYPE);
		
		assertTrue("VectorTest-nomain", JavaModelUtil.hasMainMethod(type));
	}
	
}
