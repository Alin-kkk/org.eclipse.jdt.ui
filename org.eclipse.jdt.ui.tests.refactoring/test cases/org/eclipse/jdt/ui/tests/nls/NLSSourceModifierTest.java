/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.nls;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jface.text.Document;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.ui.tests.core.ProjectTestSetup;

import org.eclipse.jdt.internal.corext.refactoring.nls.NLSHint;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSourceModifier;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSubstitution;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ASTCreator;

import org.eclipse.ltk.core.refactoring.TextChange;

public class NLSSourceModifierTest extends TestCase {
    
    private IJavaProject javaProject;
    
    private IPackageFragmentRoot fSourceFolder;
    
    public static Test allTests() {
		return new ProjectTestSetup(new TestSuite(NLSSourceModifierTest.class));
	}
	
	public static Test suite() {
		return allTests();		
	}

    protected void setUp() throws Exception {
        javaProject = ProjectTestSetup.getProject();
        fSourceFolder = JavaProjectHelper.addSourceContainer(javaProject, "src");                
    }

    protected void tearDown() throws Exception {        
        JavaProjectHelper.clear(javaProject, ProjectTestSetup.getDefaultClasspath());        
    }
       
    public NLSSourceModifierTest(String name) {
        super(name); 
    }
    
    public void testFromSkippedToTranslated() throws Exception {
        
        String klazz =
            "public class Test {\n" +
            "	private String str=\"whatever\";\n" +
            "}\n"; 
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        NLSSubstitution.setPrefix("key.");
        nlsSubstitutions[0].setState(NLSSubstitution.EXTERNALIZED);
        nlsSubstitutions[0].generateKey(nlsSubstitutions);
        
        String defaultSubst= NLSRefactoring.getDefaultSubstitutionPattern();
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor");
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=Accessor.getString(\"key.0\"); //$NON-NLS-1$\n" +
            	"}\n", 
            	doc.get());
    }
    
    public void testFromSkippedToNotTranslated() throws Exception {
        
        String klazz =
            "public class Test {\n" +
            "	private String str=\"whatever\";\n" +
            "}\n"; 
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        NLSSubstitution.setPrefix("key.");
        nlsSubstitutions[0].setState(NLSSubstitution.IGNORED);
        
        String defaultSubst= NLSRefactoring.getDefaultSubstitutionPattern();
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor");
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=\"whatever\"; //$NON-NLS-1$\n" +
                "}\n",  
            	doc.get());
    }
    
    /*
     * TODO: the key should be 0
     */
    public void testFromNotTranslatedToTranslated() throws Exception {
        
        String klazz =
            "public class Test {\n" +
            "	private String str=\"whatever\"; //$NON-NLS-1$\n" +
            "}\n"; 
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        NLSSubstitution.setPrefix("key.");
        nlsSubstitutions[0].setState(NLSSubstitution.EXTERNALIZED);
        nlsSubstitutions[0].generateKey(nlsSubstitutions);
        
        String defaultSubst= NLSRefactoring.getDefaultSubstitutionPattern();
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor");
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=Accessor.getString(\"key.0\"); //$NON-NLS-1$\n" +
            	"}\n", 
            	doc.get());
    }
    
    public void testFromNotTranslatedToSkipped() throws Exception {
        
        String klazz =
            "public class Test {\n" +
            "	private String str=\"whatever\"; //$NON-NLS-1$\n" +
            "}\n"; 
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        NLSSubstitution.setPrefix("key.");
        nlsSubstitutions[0].setState(NLSSubstitution.INTERNALIZED);
        
        String defaultSubst= NLSRefactoring.getDefaultSubstitutionPattern();
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor");
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=\"whatever\"; \n" +
                "}\n",  
            	doc.get());
    }
    
	private NLSSubstitution[] getSubstitutions(ICompilationUnit cu, CompilationUnit astRoot) {
		NLSHint hint= new NLSHint(cu, astRoot);
		return hint.getSubstitutions();
	}

	public void testFromTranslatedToNotTranslated() throws Exception {
        
        String klazz =
            "package test;\n" +
            "public class Test {\n" +
            "	private String str=Accessor.getString(\"key.0\"); //$NON-NLS-1$\n" +
            "}\n"; 
        
        String accessorKlazz =
            "package test;\n" +
    		"public class Accessor {\n" +
    		"	private static final String BUNDLE_NAME = \"test.test\";//$NON-NLS-1$\n" +
    		"	public static String getString(String s) {\n" +
    		"		return \"\";\n" +
    		"	}\n" +
    		"}\n";
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Accessor.java", accessorKlazz, false, null);
        cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        NLSSubstitution.setPrefix("key.");
        nlsSubstitutions[0].setValue("whatever");
        nlsSubstitutions[0].setState(NLSSubstitution.IGNORED);
        
        String defaultSubst= NLSRefactoring.getDefaultSubstitutionPattern();
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor");
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "package test;\n" +
                "public class Test {\n" +
                "	private String str=\"whatever\"; //$NON-NLS-1$\n" +
                "}\n",  
            	doc.get());
    }
    
    public void testFromTranslatedToSkipped() throws Exception {
        
        String klazz =
            "package test;\n" +
            "public class Test {\n" +
            "	private String str=Accessor.getString(\"key.0\"); //$NON-NLS-1$\n" +
            "}\n"; 
        
        String accessorKlazz =
            "package test;\n" +
    		"public class Accessor {\n" +
    		"	private static final String BUNDLE_NAME = \"test.test\";//$NON-NLS-1$\n" +
    		"	public static String getString(String s) {\n" +
    		"		return \"\";\n" +
    		"	}\n" +
    		"}\n";
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Accessor.java", accessorKlazz, false, null);
        cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        NLSSubstitution.setPrefix("key.");
        nlsSubstitutions[0].setValue("whatever");
        nlsSubstitutions[0].setState(NLSSubstitution.INTERNALIZED);
        
        String defaultSubst= NLSRefactoring.getDefaultSubstitutionPattern();
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor");
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "package test;\n" +
                "public class Test {\n" +
                "	private String str=\"whatever\"; \n" +
                "}\n",  
            	doc.get());
    }
    
    public void testReplacementOfKey() throws Exception {        
        String klazz =
            "package test;\n" +
            "public class Test {\n" +
            "	private String str=Accessor.getString(\"key.0\"); //$NON-NLS-1$\n" +
            "}\n"; 
        
        String accessorKlazz =
            "package test;\n" +
    		"public class Accessor {\n" +
    		"	private static final String BUNDLE_NAME = \"test.test\";//$NON-NLS-1$\n" +
    		"	public static String getString(String s) {\n" +
    		"		return \"\";\n" +
    		"	}\n" +
    		"}\n";
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Accessor.java", accessorKlazz, false, null);
        cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        nlsSubstitutions[0].setKey("nls.0");        
        
        String defaultSubst= NLSRefactoring.getDefaultSubstitutionPattern();
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor");
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "package test;\n" +
                "public class Test {\n" +
                "	private String str=Accessor.getString(\"nls.0\"); //$NON-NLS-1$\n" +
                "}\n",  
            	doc.get());
    }

	private CompilationUnit createAST(ICompilationUnit cu) {
		return ASTCreator.createAST(cu, null);
	}
}
