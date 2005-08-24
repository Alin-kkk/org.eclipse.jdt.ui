package org.eclipse.jdt.junit.tests;

import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Table;

import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchesListener2;

import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationManager;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchShortcutExtension;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.junit.ui.FailureTableDisplay;
import org.eclipse.jdt.internal.junit.ui.FailureTrace;
import org.eclipse.jdt.internal.junit.ui.TestRunnerViewPart;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

public class WrappingSystemTest extends TestCase implements ILaunchesListener2 {
	private boolean fLaunchHasTerminated = false;

	private IJavaProject fProject;

	public void launchesAdded(ILaunch[] launches) {
		// nothing
	}

	public void launchesChanged(ILaunch[] launches) {
		// nothing
	}

	public void launchesRemoved(ILaunch[] launches) {
		// nothing
	}

	public void launchesTerminated(ILaunch[] launches) {
		fLaunchHasTerminated = true;
	}

	public void test00characterizeSecondLine() throws Exception {
		runTests("\\n", 1000, 2);
		String text = getText(1);
		assertTrue(text, text.startsWith("Numbers"));
	}

	public void test01shouldWrapSecondLine() throws Exception {
		runTests("\\n", 1000, 2);
		String text = getText(1);
		assertTrue(text, text.length() < 300);
	}

	public void test02characterizeImages() throws Exception {
		runTests("\\n", 0, 3);
		assertEquals(getFailureDisplay().getExceptionIcon(), getImage(0));
		assertEquals(null, getImage(1));
		assertEquals(getFailureDisplay().getStackIcon(), getImage(2));
	}

	private FailureTableDisplay getFailureDisplay() throws PartInitException {
		return getFailureTrace().getFailureTableDisplay();
	}

	public void test03shouldWrapFirstLine() throws Exception {
		runTests("", 1000, 1);
		String text = getText(0);
		assertTrue(text, text.length() < 300);
	}

	private String getText(int i) throws PartInitException {
		return getTable().getItem(i).getText();
	}

	private FailureTrace getFailureTrace() throws PartInitException {
		TestRunnerViewPart viewPart = (TestRunnerViewPart) PlatformUI
				.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				.showView(TestRunnerViewPart.NAME);
		return viewPart.getFailureTrace();
	}

	private Image getImage(int i) throws PartInitException {
		return getTable().getItem(i).getImage();
	}

	private Table getTable() throws PartInitException {
		return getFailureDisplay().getTable();
	}

	protected synchronized boolean hasNotTerminated() {
		return !fLaunchHasTerminated;
	}

	public void runTests(String prefixForErrorMessage,
			int howManyNumbersInErrorString, int numExpectedTableItems)
			throws CoreException, JavaModelException, PartInitException {
		launchTests(prefixForErrorMessage, howManyNumbersInErrorString);
		waitForTableToFill(numExpectedTableItems, 30000);
	}

	protected void launchTests(String prefixForErrorMessage,
			int howManyNumbersInErrorString) throws CoreException,
			JavaModelException {
		// have to set up an 1.3 project to avoid requiring a 5.0 VM
		JavaProjectHelper.addRTJar13(fProject); 
		JavaProjectHelper.addVariableEntry(fProject, new Path(
				"JUNIT_HOME/junit.jar"), null, null);

		IPackageFragmentRoot root = JavaProjectHelper.addSourceContainer(
				fProject, "src");
		IPackageFragment pack = root.createPackageFragment("pack", true, null);

		ICompilationUnit cu1 = pack.getCompilationUnit("LongTraceLines.java");

		String initialString = prefixForErrorMessage + "Numbers:";

		String initializeString = "String errorString = \"" + initialString
				+ "\";";

		String contents = "public class LongTraceLines extends TestCase {\n"
							+ "	public void testLongTraceLine() throws Exception {\n"
							+ ("		" + initializeString + "\n")
							+ ("		for (int i = 0; i < "
								+ howManyNumbersInErrorString + "; i++) {\n")
							+ "			errorString += \" \" + i;\n"
							+ "		}\n"
							+ "		throw new RuntimeException(errorString);\n"
							+ "	}\n"
							+ "}";

		IType type = cu1.createType(contents, null, true, null);
		cu1.createImport("junit.framework.TestCase", null, Flags.AccDefault,
				null);
		cu1.createImport("java.util.Arrays", null, Flags.AccDefault, null);

		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		lm.addLaunchListener(this);

		LaunchConfigurationManager manager = DebugUIPlugin.getDefault()
				.getLaunchConfigurationManager();
		List launchShortcuts = manager.getLaunchShortcuts();
		LaunchShortcutExtension ext = null;
		for (Iterator iter = launchShortcuts.iterator(); iter.hasNext();) {
			ext = (LaunchShortcutExtension) iter.next();
			if (ext.getLabel().equals("JUnit Test"))
				break;
		}
		ext.launch(new StructuredSelection(type), ILaunchManager.RUN_MODE);
	}

	protected void waitForTableToFill(int numExpectedTableLines,
			int millisecondTimeout) throws PartInitException {
		long startTime = System.currentTimeMillis();
		while (stillWaiting(numExpectedTableLines)) {
			if (System.currentTimeMillis() - startTime > millisecondTimeout)
				fail("Timeout waiting for " + numExpectedTableLines 
						+ " lines in table. Present: " + getNumTableItems() + " items.\n"
						+ "The 2nd vm has " + (hasNotTerminated() ? "not " : "") + "terminated.");
			dispatchEvents();
		}
	}

	protected void dispatchEvents() {
		PlatformUI.getWorkbench().getDisplay().readAndDispatch();
	}

	protected boolean stillWaiting(int numExpectedTableLines)
			throws PartInitException {
		return hasNotTerminated() || getNumTableItems() < numExpectedTableLines;
	}

	protected int getNumTableItems() throws PartInitException {
		return getTable().getItemCount();
	}

	protected void setUp() throws Exception {
		super.setUp();
		fProject = JavaProjectHelper.createJavaProject("a", "bin");
	}

	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fProject);
	}
}
