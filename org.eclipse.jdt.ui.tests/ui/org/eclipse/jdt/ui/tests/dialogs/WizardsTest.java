/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.ui.tests.dialogs;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.internal.WorkbenchPlugin;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestPluginLauncher;
import org.eclipse.jdt.testplugin.util.DialogCheck;

import org.eclipse.jdt.internal.ui.jarpackager.JarPackageWizard;
import org.eclipse.jdt.internal.ui.wizards.NewClassCreationWizard;
import org.eclipse.jdt.internal.ui.wizards.NewInterfaceCreationWizard;
import org.eclipse.jdt.internal.ui.wizards.NewPackageCreationWizard;
import org.eclipse.jdt.internal.ui.wizards.NewProjectCreationWizard;
import org.eclipse.jdt.internal.ui.wizards.NewSnippetFileCreationWizard;
import org.eclipse.jdt.internal.ui.wizards.NewSourceFolderCreationWizard;

public class WizardsTest extends TestCase {
	private static final int SIZING_WIZARD_WIDTH    = 470;
	private static final int SIZING_WIZARD_HEIGHT   = 550;
	private static final int SIZING_WIZARD_WIDTH_2  = 500;
	private static final int SIZING_WIZARD_HEIGHT_2 = 500;
	
	private static final String PROJECT_NAME = "DummyProject";

	public static void main(String[] args) {
		TestPluginLauncher.run(TestPluginLauncher.getLocationFromProperties(), WizardsTest.class, args);
	}
	
	public static Test suite() {
		TestSuite suite= new TestSuite(WizardsTest.class.getName());
		suite.addTest(new WizardsTest("testClassWizard"));
		suite.addTest(new WizardsTest("testInterfaceWizard"));
		suite.addTest(new WizardsTest("testJarPackageWizard"));
		suite.addTest(new WizardsTest("testNewProjectWizard"));
		suite.addTest(new WizardsTest("testPackageWizard"));
		suite.addTest(new WizardsTest("testScrapbookWizard"));
		suite.addTest(new WizardsTest("testSourceFolderWizard"));				
		return suite;
	}		
	
	public WizardsTest(String name) {
		super(name);
	}
	private Shell getShell() {
		return DialogCheck.getShell();
	}
	private IWorkbench getWorkbench() {
		return WorkbenchPlugin.getDefault().getWorkbench();
	}
	
	public void testNewProjectWizard() throws Exception {	
		NewProjectCreationWizard wizard = new NewProjectCreationWizard();
		wizard.init(getWorkbench(),  null);
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		DialogCheck.assertDialog(dialog, this);
	}
	
	public void testSourceFolderWizard() throws Exception {
		IJavaProject jproject= JavaProjectHelper.createJavaProject(PROJECT_NAME, "bin");
		JavaProjectHelper.addSourceContainer(jproject, "src1");
		JavaProjectHelper.addRTJar(jproject);
	
		NewSourceFolderCreationWizard wizard = new NewSourceFolderCreationWizard();
		wizard.init(getWorkbench(), new StructuredSelection(jproject));
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		DialogCheck.assertDialog(dialog, this);
		
		JavaProjectHelper.delete(jproject);
	}
	
	public void testPackageWizard() throws Exception {
		IJavaProject jproject= JavaProjectHelper.createJavaProject(PROJECT_NAME, "bin");
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(jproject, "src1");
		JavaProjectHelper.addRTJar(jproject);
	
		NewPackageCreationWizard wizard = new NewPackageCreationWizard();
		wizard.init(getWorkbench(), new StructuredSelection(root));
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		DialogCheck.assertDialog(dialog, this);
		
		JavaProjectHelper.delete(jproject);
	}
	
	public void testClassWizard() throws Exception {
		IJavaProject jproject= JavaProjectHelper.createJavaProject(PROJECT_NAME, "bin");
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(jproject, "src1");
		IPackageFragment pack= root.createPackageFragment("org.eclipse.jdt.internal.ui.hello", true, null);
		JavaProjectHelper.addRTJar(jproject);
	
		NewClassCreationWizard wizard = new NewClassCreationWizard();
		wizard.init(getWorkbench(), new StructuredSelection(pack));
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		DialogCheck.assertDialog(dialog, this);
		
		JavaProjectHelper.delete(jproject);
	}
	
	public void testInterfaceWizard() throws Exception {
		IJavaProject jproject= JavaProjectHelper.createJavaProject(PROJECT_NAME, "bin");
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(jproject, "src1");
		IPackageFragment pack= root.createPackageFragment("org.eclipse.jdt.internal.ui.hello", true, null);
		JavaProjectHelper.addRTJar(jproject);
	
		NewInterfaceCreationWizard wizard = new NewInterfaceCreationWizard();
		wizard.init(getWorkbench(), new StructuredSelection(pack));
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		DialogCheck.assertDialog(dialog, this);
		
		JavaProjectHelper.delete(jproject);
	}
	
	public void testScrapbookWizard() throws Exception {
		IJavaProject jproject= JavaProjectHelper.createJavaProject(PROJECT_NAME, "bin");
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(jproject, "src1");
		JavaProjectHelper.addRTJar(jproject);
	
		NewSnippetFileCreationWizard wizard = new NewSnippetFileCreationWizard();
		wizard.init(getWorkbench(), new StructuredSelection(root));
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		DialogCheck.assertDialog(dialog, this);
		
		JavaProjectHelper.delete(jproject);
	}
	
	public void testJarPackageWizard() throws Exception {
		IJavaProject jproject= JavaProjectHelper.createJavaProject(PROJECT_NAME, "bin");
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(jproject, "src1");
		JavaProjectHelper.addRTJar(jproject);
		IPackageFragment pack= root.createPackageFragment("org.eclipse.jdt.internal.ui.hello", true, null);
		ICompilationUnit cu= pack.getCompilationUnit("HelloWorld.java");
		IType type= cu.createType("public class HelloWorld {\npublic static void main(String[] args) {}\n}\n", null, true, null);	
	
		JarPackageWizard wizard = new JarPackageWizard();
		wizard.init(getWorkbench(), new StructuredSelection(root));
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		DialogCheck.assertDialog(dialog, this);
		
		JavaProjectHelper.delete(jproject);
	}				
	
}

