/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.ui.tests.core;

import java.io.File;
import java.util.zip.ZipFile;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.JavaTestPlugin;
import org.eclipse.jdt.testplugin.TestPluginLauncher;

import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation.IChooseImportQuery;
import org.eclipse.jdt.internal.corext.util.TypeInfo;

public class ImportOrganizeTest extends TestCase {
	
	private static final Class THIS= ImportOrganizeTest.class;
	
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	public ImportOrganizeTest(String name) {
		super(name);
	}

	public static void main(String[] args) {
		TestPluginLauncher.run(TestPluginLauncher.getLocationFromProperties(), THIS, args);
	}


	public static Test suite() {
		return new TestSuite(THIS);
	}


	protected void setUp() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");

		assertTrue("rt not found", JavaProjectHelper.addRTJar(fJProject1) != null);

		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC);
		assertTrue("junit src not found", junitSrcArchive != null && junitSrcArchive.exists());
		ZipFile zipfile= new ZipFile(junitSrcArchive);
		fSourceFolder= JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src", zipfile);
	}


	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJProject1);
	}
	
	private IChooseImportQuery createQuery(final String name, final String[] choices, final int[] nEntries) {
		return new IChooseImportQuery() {
			public TypeInfo[] chooseImports(TypeInfo[][] openChoices, ISourceRange[] ranges) {
				assertTrue(name + "-query-nchoices1", choices.length == openChoices.length);
				assertTrue(name + "-query-nchoices2", nEntries.length == openChoices.length);
				if (nEntries != null) {
					for (int i= 0; i < nEntries.length; i++) {
						assertTrue(name + "-query-cnt" + i, openChoices[i].length == nEntries[i]);
					}
				}
				TypeInfo[] res= new TypeInfo[openChoices.length];
				for (int i= 0; i < openChoices.length; i++) {
					TypeInfo[] selection= openChoices[i];
					assertNotNull(name + "-query-setset" + i, selection);
					assertTrue(name + "-query-setlen" + i, selection.length > 0);
					TypeInfo found= null;
					for (int k= 0; k < selection.length; k++) {
						if (selection[k].getFullyQualifiedName().equals(choices[i])) {
							found= selection[k];
						}
					}
					assertNotNull(name + "-query-notfound" + i, found);
					res[i]= found;
				}
				return res;
			}
		};
	}
	
	private void assertImports(ICompilationUnit cu, String[] imports) throws Exception {
		IImportDeclaration[] desc= cu.getImports();
		assertTrue(cu.getElementName() + "-count", desc.length == imports.length);
		for (int i= 0; i < imports.length; i++) {
			assertEquals(cu.getElementName() + "-cmpentries" + i, desc[i].getElementName(), imports[i]);
		}
	}
	
	public void test1() throws Exception {
		ICompilationUnit cu= (ICompilationUnit) fJProject1.findElement(new Path("junit/runner/BaseTestRunner.java"));
		assertNotNull("BaseTestRunner.java", cu);
		
		IPackageFragmentRoot root= (IPackageFragmentRoot)cu.getParent().getParent();
		IPackageFragment pack= root.createPackageFragment("mytest", true, null);
		
		ICompilationUnit colidingCU= pack.getCompilationUnit("TestListener.java");
		colidingCU.createType("public abstract class TestListener {\n}\n", null, true, null);
		
		
		String[] order= new String[0];
		IChooseImportQuery query= createQuery("BaseTestRunner", new String[] { "junit.framework.TestListener" }, new int[] { 2 });
		
		OrganizeImportsOperation op= new OrganizeImportsOperation(cu, order, 99, false, true, true, query);
		op.run(null);
		
		assertImports(cu, new String[] {
			"java.io.BufferedReader",
			"java.io.File",
			"java.io.FileInputStream",
			"java.io.IOException",
			"java.io.InputStream",
			"java.io.PrintWriter",
			"java.io.StringReader",
			"java.io.StringWriter",
			"java.lang.reflect.InvocationTargetException",
			"java.lang.reflect.Method",
			"java.text.NumberFormat",
			"java.util.Properties",
			"junit.framework.Test",
			"junit.framework.TestListener",
			"junit.framework.TestSuite"
		});
	}
		
	public void test2() throws Exception {
		ICompilationUnit cu= (ICompilationUnit) fJProject1.findElement(new Path("junit/runner/LoadingTestCollector.java"));
		assertNotNull("LoadingTestCollector.java", cu);
		
		String[] order= new String[0];
		IChooseImportQuery query= createQuery("LoadingTestCollector", new String[] { }, new int[] { });
		
		OrganizeImportsOperation op= new OrganizeImportsOperation(cu, order, 99, false, true, true, query);
		op.run(null);
		
		assertImports(cu, new String[] {
			"java.lang.reflect.Constructor",
			"java.lang.reflect.Method",
			"java.lang.reflect.Modifier",
			"junit.framework.Test"	
		});	
	}
	
	
	public void test3() throws Exception {
		ICompilationUnit cu= (ICompilationUnit) fJProject1.findElement(new Path("junit/runner/TestCaseClassLoader.java"));
		assertNotNull("TestCaseClassLoader.java", cu);
		
		String[] order= new String[0];
		IChooseImportQuery query= createQuery("TestCaseClassLoader", new String[] { }, new int[] { });
		
		OrganizeImportsOperation op= new OrganizeImportsOperation(cu, order, 3, false, true, true, query);
		op.run(null);
		
		assertImports(cu, new String[] {
			"java.io.*",
			"java.net.URL",
			"java.util.*",
			"java.util.zip.ZipEntry",
			"java.util.zip.ZipFile",
		});	
	}
		
	public void test4() throws Exception {
		ICompilationUnit cu= (ICompilationUnit) fJProject1.findElement(new Path("junit/textui/TestRunner.java"));
		assertNotNull("TestRunner.java", cu);
		
		String[] order= new String[0];
		IChooseImportQuery query= createQuery("TestRunner", new String[] {}, new int[] {});
		
		OrganizeImportsOperation op= new OrganizeImportsOperation(cu, order, 99, false, true, true, query);
		op.run(null);
		
		assertImports(cu, new String[] {
			"java.io.PrintStream",
			"java.util.Enumeration",
			"junit.framework.AssertionFailedError",
			"junit.framework.Test",
			"junit.framework.TestFailure",
			"junit.framework.TestResult",
			"junit.framework.TestSuite",
			"junit.runner.BaseTestRunner",
			"junit.runner.StandardTestSuiteLoader",
			"junit.runner.TestSuiteLoader",
			"junit.runner.Version"
		});		
	}
	
	public void test5() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test", false, null);
		for (int ch= 'A'; ch < 'M'; ch++) {
			String name= String.valueOf((char) ch);
			ICompilationUnit cu= pack.getCompilationUnit(name + ".java");
			String content= "public class " + name + " {}";
			cu.createType(content, null, false, null);
		}
		for (int ch= 'A'; ch < 'M'; ch++) {
			String name= "I" + String.valueOf((char) ch);
			ICompilationUnit cu= pack.getCompilationUnit(name + ".java");
			String content= "public interface " + name + " {}";
			cu.createType(content, null, false, null);
		}		
		
		StringBuffer buf= new StringBuffer();
		buf.append("public class ImportTest extends A implements IA, IB {\n");
		buf.append("  private B fB;\n");
		buf.append("  private Object fObj= new C();\n");
		buf.append("  public IB foo(IC c, ID d) throws IOException {\n");
		buf.append("   Object local= (D) fObj;\n");
		buf.append("   if (local instanceof E) {};\n");
		buf.append("   return null;\n");
		buf.append("  }\n");
		buf.append("}\n");
		
		pack= fSourceFolder.createPackageFragment("other", false, null);
		ICompilationUnit cu= pack.getCompilationUnit("ImportTest.java");
		cu.createType(buf.toString(), null, false, null);

		String[] order= new String[0];
		IChooseImportQuery query= createQuery("ImportTest", new String[] {}, new int[] {});
	
		OrganizeImportsOperation op= new OrganizeImportsOperation(cu, order, 99, false, true, true, query);
		op.run(null);
		
		assertImports(cu, new String[] {
			"java.io.IOException",
			"test.A",
			"test.B",
			"test.C",
			"test.D",
			"test.E",			
			"test.IA",
			"test.IB",
			"test.IC",
			"test.ID",
		});		
	}
	
	public void test6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("  protected static class C1 {\n");
		buf.append("    public static class C2 {\n");
		buf.append("    }\n");
		buf.append("  }\n");
		buf.append("}\n");	
		ICompilationUnit cu1= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
		
		
		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
				
		buf= new StringBuffer();
		buf.append("package test2;\n");

		buf.append("import test2.A.A1;\n");		
		buf.append("import test2.A.A1.A2;\n");
		buf.append("import test2.A.A1.A2.A3;\n");
		buf.append("import test2.A.B1;\n");
		buf.append("import test2.A.B1.B2;\n");
		buf.append("import test1.C;\n");
		buf.append("import test1.C.C1.C2;\n");
		
		buf.append("public class A {\n");
		buf.append("    public static class A1 {\n");
		buf.append("        public static class A2 {\n");
		buf.append("            public static class A3 {\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		
		buf.append("    public static class B1 {\n");
		buf.append("        public static class B2 {\n");
		buf.append("        }\n");
		
		buf.append("        public static class B3 {\n");
		buf.append("            public static class B4 extends C {\n");
		buf.append("                B4 b4;\n");
		buf.append("                B3 b3;\n");
		buf.append("                B2 b2;\n");
		buf.append("                B1 b1;\n");
		buf.append("                A1 a1;\n");
		buf.append("                A2 a2;\n");
		buf.append("                A3 a3;\n");
		buf.append("                C1 c1;\n");
		buf.append("                C2 c2;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		ICompilationUnit cu2= pack2.createCompilationUnit("A.java", buf.toString(), false, null);


		String[] order= new String[0];
		IChooseImportQuery query= createQuery("A", new String[] {}, new int[] {});
	
		OrganizeImportsOperation op= new OrganizeImportsOperation(cu2, order, 99, false, true, true, query);
		op.run(null);
		
		assertImports(cu2, new String[] {
			"test1.C", 
			"test1.C.C1.C2",
			"test2.A.A1.A2", 
			"test2.A.A1.A2.A3"
		});
	}
	
	
	
}
