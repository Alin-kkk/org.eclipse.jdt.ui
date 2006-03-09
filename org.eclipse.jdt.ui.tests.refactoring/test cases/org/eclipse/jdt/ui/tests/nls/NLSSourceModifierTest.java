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
package org.eclipse.jdt.ui.tests.nls;

import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.Preferences;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jface.text.Document;

import org.eclipse.ltk.core.refactoring.TextChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.refactoring.nls.AccessorClassCreator;
import org.eclipse.jdt.internal.corext.refactoring.nls.AccessorClassModifier;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSHint;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSourceModifier;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSubstitution;
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ASTCreator;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.StringAsserts;
import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.jdt.ui.tests.core.ProjectTestSetup;

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
    	Hashtable options= TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		
		JavaCore.setOptions(options);			

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, false);
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, false);
		
		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODSTUB_ID, "//TODO\n${body_statement}", null);
		
		Preferences corePrefs= JavaCore.getPlugin().getPluginPreferences();
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_SUFFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES, "");		
		
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
        nlsSubstitutions[0].setState(NLSSubstitution.EXTERNALIZED);
        nlsSubstitutions[0].setPrefix("key.");
        nlsSubstitutions[0].generateKey(nlsSubstitutions);
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", false);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=Accessor.getString(\"key.0\"); //$NON-NLS-1$\n" +
            	"}\n", 
            	doc.get());
    }
    
   public void testFromSkippedToTranslatedEclipseNew() throws Exception {
        
        String klazz =
            "public class Test {\n" +
            "	private String str=\"whatever\";\n" +
            "}\n"; 
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        nlsSubstitutions[0].setState(NLSSubstitution.EXTERNALIZED);
        nlsSubstitutions[0].setPrefix("key_");
        nlsSubstitutions[0].generateKey(nlsSubstitutions);
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", true);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=Accessor.key_0;\n" +
            	"}\n", 
            	doc.get());
        
      CreateTextFileChange accessorChange= (CreateTextFileChange)AccessorClassCreator.create(cu, "Accessor", pack.getPath().append("Accessor.java"), pack, pack.getPath().append("test.properties"), true, nlsSubstitutions, null);
      String accessor= accessorChange.getPreview();
      StringBuffer buf= new StringBuffer();
      buf.append("package test;\n");
      buf.append("\n");
      buf.append("import org.eclipse.osgi.util.NLS;\n");
      buf.append("\n");
      buf.append("public class Accessor extends NLS {\n");
      buf.append("    private static final String BUNDLE_NAME = \"test.test\"; //$NON-NLS-1$\n");
      buf.append("    private Accessor() {\n");
      buf.append("    }\n");
      buf.append("    static {\n");
      buf.append("        // initialize resource bundle\n");
      buf.append("        NLS.initializeMessages(BUNDLE_NAME, Accessor.class);\n");
      buf.append("    }\n");
      buf.append("    public static String key_0;\n");
      buf.append("}\n");
      String expected= buf.toString();
      StringAsserts.assertEqualStringIgnoreDelim(accessor, expected);
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
        nlsSubstitutions[0].setPrefix("key.");
        nlsSubstitutions[0].setState(NLSSubstitution.IGNORED);
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", false);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=\"whatever\"; //$NON-NLS-1$\n" +
                "}\n",  
            	doc.get());
    }
    
    public void testFromSkippedToNotTranslatedEclipse() throws Exception {
        
        String klazz =
            "public class Test {\n" +
            "	private String str=\"whatever\";\n" +
            "}\n"; 
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        nlsSubstitutions[0].setPrefix("key_");
        nlsSubstitutions[0].setState(NLSSubstitution.IGNORED);
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", true);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=\"whatever\"; //$NON-NLS-1$\n" +
                "}\n",  
            	doc.get());
        
        CreateTextFileChange accessorChange= (CreateTextFileChange)AccessorClassCreator.create(cu, "Accessor", pack.getPath().append("Accessor.java"), pack, pack.getPath().append("test.properties"), true, nlsSubstitutions, null);
        String accessor= accessorChange.getPreview();
        StringBuffer buf= new StringBuffer();
        buf.append("package test;\n");
        buf.append("\n");
        buf.append("import org.eclipse.osgi.util.NLS;\n");
        buf.append("\n");
        buf.append("public class Accessor extends NLS {\n");
        buf.append("    private static final String BUNDLE_NAME = \"test.test\"; //$NON-NLS-1$\n");
        buf.append("    private Accessor() {\n");
        buf.append("    }\n");
        buf.append("    static {\n");
        buf.append("        // initialize resource bundle\n");
        buf.append("        NLS.initializeMessages(BUNDLE_NAME, Accessor.class);\n");
        buf.append("    }\n");
        buf.append("\n");
        buf.append("}\n");
        String expected= buf.toString();
        StringAsserts.assertEqualStringIgnoreDelim(accessor, expected);
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
        nlsSubstitutions[0].setState(NLSSubstitution.EXTERNALIZED);
        nlsSubstitutions[0].setPrefix("key.");
        nlsSubstitutions[0].generateKey(nlsSubstitutions);
        
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", false);
        
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
        nlsSubstitutions[0].setPrefix("key.");
        nlsSubstitutions[0].setState(NLSSubstitution.INTERNALIZED);
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", false);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=\"whatever\"; \n" +
                "}\n",  
            	doc.get());
    }
    
    public void testFromNotTranslatedToSkippedEclipse() throws Exception {
        
        String klazz =
            "public class Test {\n" +
            "	private String str=\"whatever\"; //$NON-NLS-1$\n" +
            "}\n"; 
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        nlsSubstitutions[0].setPrefix("key_");
        nlsSubstitutions[0].setState(NLSSubstitution.INTERNALIZED);
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", true);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "public class Test {\n" +
                "	private String str=\"whatever\"; \n" +
                "}\n",  
            	doc.get());
        
        CreateTextFileChange accessorChange= (CreateTextFileChange)AccessorClassCreator.create(cu, "Accessor", pack.getPath().append("Accessor.java"), pack, pack.getPath().append("test.properties"), true, nlsSubstitutions, null);
        String accessor= accessorChange.getPreview();
        StringBuffer buf= new StringBuffer();
        buf.append("package test;\n");
        buf.append("\n");
        buf.append("import org.eclipse.osgi.util.NLS;\n");
        buf.append("\n");
        buf.append("public class Accessor extends NLS {\n");
        buf.append("    private static final String BUNDLE_NAME = \"test.test\"; //$NON-NLS-1$\n");
        buf.append("    private Accessor() {\n");
        buf.append("    }\n");
        buf.append("    static {\n");
        buf.append("        // initialize resource bundle\n");
        buf.append("        NLS.initializeMessages(BUNDLE_NAME, Accessor.class);\n");
        buf.append("    }\n");
        buf.append("\n");
        buf.append("}\n");
        String expected= buf.toString();
        StringAsserts.assertEqualStringIgnoreDelim(accessor, expected);
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
        nlsSubstitutions[0].setValue("whatever");
        nlsSubstitutions[0].setPrefix("key.");
        nlsSubstitutions[0].setState(NLSSubstitution.IGNORED);
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", false);
        
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
        nlsSubstitutions[0].setValue("whatever");
        nlsSubstitutions[0].setState(NLSSubstitution.INTERNALIZED);
        nlsSubstitutions[0].setPrefix("key.");
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", false);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "package test;\n" +
                "public class Test {\n" +
                "	private String str=\"whatever\"; \n" +
                "}\n",  
            	doc.get());
    }
    
    public void testFromTranslatedToSkippedEclipse() throws Exception {
        
        String klazz =
            "package test;\n" +
            "public class Test {\n" +
            "	private String str=Accessor.key_0;\n" +
            "}\n"; 
        
        StringBuffer buf= new StringBuffer();
        buf.append("package test;\n");
        buf.append("import org.eclipse.osgi.util.NLS;\n");
        buf.append("public class Accessor extends NLS {\n");
        buf.append("    private static final String BUNDLE_NAME = \"test.test\"; //$NON-NLS-1$\n");
        buf.append("\n");
        buf.append("    private Accessor() {\n");
        buf.append("    }\n");
        buf.append("    static {\n");
        buf.append("        // initialize resource bundle\n");
        buf.append("        NLS.initializeMessages(BUNDLE_NAME, Accessor.class);\n");
        buf.append("    }\n");
        buf.append("    public static String key_0;\n");
        buf.append("}\n");
        String accessorKlazz= buf.toString();
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit accessorCu= pack.createCompilationUnit("Accessor.java", accessorKlazz, false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        IPackageFragment nlspack= fSourceFolder.createPackageFragment("org.eclipse.osgi.util", false, null);
        nlspack.createCompilationUnit("NLS.java", "public class NLS {}", false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        nlsSubstitutions[0].setValue("whatever");
        nlsSubstitutions[0].setState(NLSSubstitution.INTERNALIZED);
        nlsSubstitutions[0].setPrefix("key_");
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", true);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "package test;\n" +
                "public class Test {\n" +
                "	private String str=\"whatever\";\n" +
                "}\n",  
            	doc.get());
        
        TextChange accessorChange= (TextChange)AccessorClassModifier.create(accessorCu, nlsSubstitutions);
        Document accessorDoc= new Document(accessorKlazz);
        accessorChange.getEdit().apply(accessorDoc);
        
        buf= new StringBuffer();
        buf.append("package test;\n");
        buf.append("import org.eclipse.osgi.util.NLS;\n");
        buf.append("public class Accessor extends NLS {\n");
        buf.append("    private static final String BUNDLE_NAME = \"test.test\"; //$NON-NLS-1$\n");
        buf.append("\n");
        buf.append("    private Accessor() {\n");
        buf.append("    }\n");
        buf.append("    static {\n");
        buf.append("        // initialize resource bundle\n");
        buf.append("        NLS.initializeMessages(BUNDLE_NAME, Accessor.class);\n");
        buf.append("    }\n");
        buf.append("}\n");
        String expected= buf.toString();
        
        assertEquals(expected, accessorDoc.get());
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
        nlsSubstitutions[0].setPrefix("key.");
        nlsSubstitutions[0].setKey("nls.0");        
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", false);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "package test;\n" +
                "public class Test {\n" +
                "	private String str=Accessor.getString(\"nls.0\"); //$NON-NLS-1$\n" +
                "}\n",  
            	doc.get());
    }
    
    public void testReplacementOfKeyEclipse() throws Exception {        
        String klazz =
            "package test;\n" +
            "public class Test {\n" +
            "	private String str=Accessor.key_0; \n" +
            "}\n"; 
        
        StringBuffer buf= new StringBuffer();
        buf.append("package test;\n");
        buf.append("import org.eclipse.osgi.util.NLS;\n");
        buf.append("public class Accessor extends NLS {\n");
        buf.append("    private static final String BUNDLE_NAME = \"test.test\"; //$NON-NLS-1$\n");
        buf.append("\n");
        buf.append("    private Accessor() {\n");
        buf.append("    }\n");
        buf.append("    static {\n");
        buf.append("        // initialize resource bundle\n");
        buf.append("        NLS.initializeMessages(BUNDLE_NAME, Accessor.class);\n");
        buf.append("    }\n");
        buf.append("    public static String key_0;\n");
        buf.append("}\n");
        String accessorKlazz= buf.toString();
        
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
        ICompilationUnit accessorCu= pack.createCompilationUnit("Accessor.java", accessorKlazz, false, null);
        ICompilationUnit cu= pack.createCompilationUnit("Test.java", klazz, false, null);
        
        IPackageFragment nlspack= fSourceFolder.createPackageFragment("org.eclipse.osgi.util", false, null);
        nlspack.createCompilationUnit("NLS.java", "public class NLS {}", false, null);
        
        CompilationUnit astRoot= createAST(cu);
        NLSSubstitution[] nlsSubstitutions = getSubstitutions(cu, astRoot);
        nlsSubstitutions[0].setInitialValue("whatever");
        nlsSubstitutions[0].setValue("whatever");
        nlsSubstitutions[0].setPrefix("nls_");
        nlsSubstitutions[0].setKey("nls_0");   
        
        String defaultSubst= NLSRefactoring.DEFAULT_SUBST_PATTERN;
        TextChange change = (TextChange) NLSSourceModifier.create(cu, nlsSubstitutions, defaultSubst, pack, "Accessor", true);
        
        Document doc = new Document(klazz);
        change.getEdit().apply(doc);
        
        assertEquals(
                "package test;\n" +
                "public class Test {\n" +
                "	private String str=Accessor.nls_0; \n" +
                "}\n",  
            	doc.get());
        
        TextChange accessorChange= (TextChange)AccessorClassModifier.create(accessorCu, nlsSubstitutions);
        Document accessorDoc= new Document(accessorKlazz);
        accessorChange.getEdit().apply(accessorDoc);
        
        buf= new StringBuffer();
        buf.append("package test;\n");
        buf.append("import org.eclipse.osgi.util.NLS;\n");
        buf.append("public class Accessor extends NLS {\n");
        buf.append("    private static final String BUNDLE_NAME = \"test.test\"; //$NON-NLS-1$\n");
        buf.append("\n");
        buf.append("    private Accessor() {\n");
        buf.append("    }\n");
        buf.append("    static {\n");
        buf.append("        // initialize resource bundle\n");
        buf.append("        NLS.initializeMessages(BUNDLE_NAME, Accessor.class);\n");
        buf.append("    }\n");
        buf.append("    public static String nls_0;\n");
        buf.append("}\n");
        String expected= buf.toString();
        
        assertEquals(expected, accessorDoc.get());
    }

	private CompilationUnit createAST(ICompilationUnit cu) {
		return ASTCreator.createAST(cu, null);
	}
}
