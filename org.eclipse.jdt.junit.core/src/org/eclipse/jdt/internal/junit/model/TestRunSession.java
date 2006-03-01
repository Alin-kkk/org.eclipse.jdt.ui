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

package org.eclipse.jdt.internal.junit.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ListenerList;

import org.eclipse.jface.util.Assert;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

import org.eclipse.jdt.junit.ITestRunListener;

import org.eclipse.jdt.internal.junit.Messages;
import org.eclipse.jdt.internal.junit.launcher.JUnitBaseLaunchConfiguration;
import org.eclipse.jdt.internal.junit.model.TestElement.Status;
import org.eclipse.jdt.internal.junit.ui.JUnitMessages;
import org.eclipse.jdt.internal.junit.ui.JUnitPlugin;

/**
 * 
 */
public class TestRunSession {

	private final IType fLaunchedType;
	private final ILaunch fLaunch;

	private final RemoteTestRunnerClient fTestRunnerClient;

	private final ListenerList/*<ITestSessionListener>*/ fSessionListeners;
	
	/**
	 * The model root.
	 */
	private final TestRoot fTestRoot;
	
	/**
	 * Map from testId to testElement.
	 */
	private final HashMap/*<String, TestElement>*/ fIdToTest;
	
	/**
	 * The TestSuites for which additional children are expected. 
	 */
	private List/*<IncompleteTestSuite>*/ fIncompleteTestSuites;
	
 	/**
 	 * Number of tests started during this test run.
 	 */
	volatile int fStartedCount;
	/**
	 * Number of errors during this test run.
	 */
	volatile int fErrorCount;
	/**
	 * Number of failures during this test run.
	 */
	volatile int fFailureCount;
	/**
	 * Total number of tests to run.
	 */
	volatile int fTotalCount;
	/**
	 * Start time in millis.
	 */
	volatile long fStartTime;
	volatile boolean fIsRunning;
	
	volatile boolean fIsStopped;
	

	public TestRunSession(IType launchedType, int port, ILaunch launch) {
		Assert.isNotNull(launchedType);
		Assert.isNotNull(launch);
		
		fLaunchedType= launchedType;
		fLaunch= launch;
		
		fTestRoot= new TestRoot();
		fIdToTest= new HashMap();
		
		fTestRunnerClient= new RemoteTestRunnerClient();
		fTestRunnerClient.startListening(new ITestRunListener[] { new TestSessionNotifier() }, port);

		fSessionListeners= new ListenerList();
	}
	
	public TestRoot getTestRoot() {
		return fTestRoot;
	}

	public IType getLaunchedType() {
		return fLaunchedType;
	}
	
	public ILaunch getLaunch() {
		return fLaunch;
	}
	
	public String getTestRunName() {
		return fLaunch.getLaunchConfiguration().getName();
	}
	
	public int getErrorCount() {
		return fErrorCount;
	}

	public int getFailureCount() {
		return fFailureCount;
	}

	public int getStartedCount() {
		return fStartedCount;
	}

	public int getTotalCount() {
		return fTotalCount;
	}

	public long getStartTime() {
		return fStartTime;
	}
	
	/**
	 * @return <code>true</code> iff the session has been stopped or terminated
	 */
	public boolean isStopped() {
		return fIsStopped;
	}

	public void addTestSessionListener(ITestSessionListener listener) {
		fSessionListeners.add(listener);
	}
	
	public void removeTestSessionListener(ITestSessionListener listener) {
		fSessionListeners.remove(listener);
	}
	
	public void stopTestRun() {
		fIsStopped= true;
		fTestRunnerClient.stopTest();
	}

	/**
	 * @return <code>true</code> iff the runtime VM of this test session is still alive 
	 */
	public boolean isKeptAlive() {
		return fTestRunnerClient.isRunning() && ILaunchManager.DEBUG_MODE.equals(getLaunch().getLaunchMode());
	}

	/**
	 * @return <code>true</code> iff this session has been started, but not ended nor stopped nor terminated
	 */
	public boolean isRunning() {
		return fIsRunning;
	}
	
	/**
	 * @param testId 
	 * @param className 
	 * @param testName 
	 * @param launchMode 
	 * @return <code>false</code> iff the rerun could not be started
	 * @throws CoreException 
	 */
	public boolean rerunTest(String testId, String className, String testName, String launchMode) throws CoreException {
		if (isKeptAlive()) {
			Status status= ((TestCaseElement) getTestElement(testId)).getStatus();
			if (status == Status.ERROR) {
				fErrorCount--;
			} else if (status == Status.FAILURE) {
				fFailureCount--;
			}
			fTestRunnerClient.rerunTest(testId, className, testName);
			return true;
			
		} else if (getLaunch() != null) {
			// run the selected test using the previous launch configuration
			ILaunchConfiguration launchConfiguration= getLaunch().getLaunchConfiguration();
			if (launchConfiguration != null) {

				String name= className;
				if (testName != null) 
					name+= "."+testName; //$NON-NLS-1$
				String configName= Messages.format(JUnitMessages.TestRunnerViewPart_configName, name); 
				ILaunchConfigurationWorkingCopy tmp= launchConfiguration.copy(configName); 
				// fix for bug: 64838  junit view run single test does not use correct class [JUnit] 
				tmp.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, className);
				// reset the container
				tmp.setAttribute(JUnitBaseLaunchConfiguration.LAUNCH_CONTAINER_ATTR, ""); //$NON-NLS-1$
				if (testName != null) {
					tmp.setAttribute(JUnitBaseLaunchConfiguration.TESTNAME_ATTR, testName);
					//	String args= "-rerun "+testId;
					//	tmp.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, args);
				}
				tmp.launch(launchMode, null);	
				return true;
			}
		}
		
		return false;
	}
	
	public TestElement getTestElement(String id) {
		return (TestElement) fIdToTest.get(id);
	}

	private TestElement addTreeEntry(String treeEntry) {
		// format: testId","testName","isSuite","testcount
		int index0= treeEntry.indexOf(',');
		String id= treeEntry.substring(0, index0);
		
		StringBuffer testNameBuffer= new StringBuffer(100);
		int index1= scanTestName(treeEntry, index0 + 1, testNameBuffer);
		String testName= testNameBuffer.toString().trim();
		
		int index2= treeEntry.indexOf(',', index1 + 1);
		boolean isSuite= treeEntry.substring(index1 + 1, index2).equals("true"); //$NON-NLS-1$
		
		int testCount= Integer.parseInt(treeEntry.substring(index2 + 1));
		
		if (fIncompleteTestSuites.isEmpty()) {
			return createTestElement(fTestRoot, id, testName, isSuite, testCount);
		} else {
			int suiteIndex= fIncompleteTestSuites.size() - 1;
			IncompleteTestSuite openSuite= (IncompleteTestSuite) fIncompleteTestSuites.get(suiteIndex);
			openSuite.fOutstandingChildren--;
			if (openSuite.fOutstandingChildren <= 0)
				fIncompleteTestSuites.remove(suiteIndex);
			return createTestElement(openSuite.fTestSuiteElement, id, testName, isSuite, testCount);
		}
	}

	private TestElement createTestElement(TestSuiteElement parent, String id, String testName, boolean isSuite, int testCount) {
		TestElement testElement;
		if (isSuite) {
			TestSuiteElement testSuiteElement= new TestSuiteElement(parent, id, testName, testCount);
			testElement= testSuiteElement;
			if (testCount > 0)
				fIncompleteTestSuites.add(new IncompleteTestSuite(testSuiteElement, testCount));
		} else {
			testElement= new TestCaseElement(parent, id, testName);
		}
		fIdToTest.put(id, testElement);
		return testElement;
	}
	
	/**
	 * Append the test name from <code>s</code> to <code>testName</code>.
	 *  
	 * @param s the string to scan
	 * @param start the offset of the first character in <code>s</code> 
	 * @param testName the result
	 * 
	 * @return the index of the next ','
	 */
	private int scanTestName(String s, int start, StringBuffer testName) {
		boolean inQuote= false;
		int i= start;
		for (; i < s.length(); i++) {
			char c= s.charAt(i);
			if (c == '\\' && !inQuote) {
				inQuote= true;
				continue;
			} else if (inQuote) {
				inQuote= false;
				testName.append(c);
			} else if (c == ',')
				break;
			else
				testName.append(c);
		}
		return i;
	}

	/**
	 * An {@link ITestRunListener2} that listens to events from the
	 * {@link RemoteTestRunnerClient} and translates them into high-level model
	 * events (broadcasted to {@link ITestSessionListener}s).
	 */
	private class TestSessionNotifier implements ITestRunListener, ITestRunListener2 {
		
		public void testRunStarted(int testCount) {
			fIncompleteTestSuites= new ArrayList();
			
			fStartedCount= 0;
			fFailureCount= 0;
			fErrorCount= 0;
			fTotalCount= testCount;
			
			fStartTime= System.currentTimeMillis();
			fIsRunning= true;
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).sessionStarted();
			}
		}
	
		public void testRunEnded(long elapsedTime) {
			fIsRunning= false;
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).sessionEnded(elapsedTime);
			}
		}
	
		public void testRunStopped(long elapsedTime) {
			fIsRunning= false;
			fIsStopped= true;
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).sessionStopped(elapsedTime);
			}
		}
	
		public void testRunTerminated() {
			fIsRunning= false;
			fIsStopped= true;
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).sessionTerminated();
			}
		}
	
		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.junit.model.ITestRunListener2#testTreeEntry(java.lang.String)
		 */
		public void testTreeEntry(String description) {
			TestElement testElement= addTreeEntry(description);
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).testAdded(testElement);
			}
		}
	
		public void testStarted(String testId, String testName) {
			TestElement testElement= getTestElement(testId);
			if (! (testElement instanceof TestCaseElement)) {
				logUnexpectedTest(testId, testElement);
				return;
			}
			TestCaseElement testCaseElement= (TestCaseElement) testElement;
			setStatus(testCaseElement, Status.RUNNING);
			
			fStartedCount++;
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).testStarted(testCaseElement);
			}
		}
	
		public void testEnded(String testId, String testName) {
			TestElement testElement= getTestElement(testId);
			if (! (testElement instanceof TestCaseElement)) {
				logUnexpectedTest(testId, testElement);
				return;
			}
			TestCaseElement testCaseElement= (TestCaseElement) testElement;

			if (testCaseElement.getStatus() == Status.RUNNING)
				setStatus(testCaseElement, Status.OK);
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).testEnded(testCaseElement);
			}
		}
		
		
		public void testFailed(int status, String testId, String testName, String trace) {
			testFailed(status, testId, testName, trace, null, null);
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.junit.model.ITestRunListener2#testFailed(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
		 */
		public void testFailed(int statusCode, String testId, String testName, String trace, String expected, String actual) {
			TestElement testElement= getTestElement(testId);
			if (! (testElement instanceof TestCaseElement)) {
				logUnexpectedTest(testId, testElement);
				return;
			}
			TestCaseElement test= (TestCaseElement) testElement;

			Status status= Status.convert(statusCode);
			setStatus(test, status, trace, nullifyEmpty(expected), nullifyEmpty(actual));
			
			if (statusCode == ITestRunListener.STATUS_ERROR) {
				fErrorCount++;
			} else {
				fFailureCount++;
			}
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				((ITestSessionListener) listeners[i]).testFailed(test, status, trace, expected, actual);
			}
		}

		private String nullifyEmpty(String string) {
			return string.length() == 0 ? null : string;
		}
	
		public void testReran(String testId, String testClass, String testName, int status, String trace) {
			testReran(testId, testClass, testName, status, trace, "", ""); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.junit.model.ITestRunListener2#testReran(java.lang.String, java.lang.String, java.lang.String, int, java.lang.String, java.lang.String, java.lang.String)
		 */
		public void testReran(String testId, String className, String testName, int statusCode, String trace, String expectedResult, String actualResult) {
			TestElement testElement= getTestElement(testId);
			if (! (testElement instanceof TestCaseElement)) {
				logUnexpectedTest(testId, testElement); //TODO: rerun suites?
				return;
			}
			TestCaseElement testCaseElement= (TestCaseElement) testElement;
			
			Status status= Status.convert(statusCode);
			if (status == Status.ERROR)
				fErrorCount++;
			else if (status == Status.FAILURE)
				fFailureCount++;
			testCaseElement.setStatus(status, trace, nullifyEmpty(expectedResult), nullifyEmpty(actualResult));
			
			Object[] listeners= fSessionListeners.getListeners();
			for (int i= 0; i < listeners.length; ++i) {
				//TODO: post old & new status?
				((ITestSessionListener) listeners[i]).testReran(testCaseElement, status, trace, expectedResult, actualResult);
			}
		}
	
		private void setStatus(TestCaseElement testCaseElement, Status status) {
			testCaseElement.setStatus(status);
		}
		
		private void setStatus(TestCaseElement testCaseElement, Status status, String trace, String expected, String actual) {
			testCaseElement.setStatus(status, trace, expected, actual);
		}
		
		private void logUnexpectedTest(String testId, TestElement testElement) {
			JUnitPlugin.log(new Exception("Unexpected TestElement type for testId '" + testId + "': " + testElement)); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static class IncompleteTestSuite {
		public TestSuiteElement fTestSuiteElement;
		public int fOutstandingChildren;
		
		public IncompleteTestSuite(TestSuiteElement testSuiteElement, int outstandingChildren) {
			fTestSuiteElement= testSuiteElement;
			fOutstandingChildren= outstandingChildren;
		}
	}

	public TestCaseElement[] getAllFailedTestCaseElements() {
		ArrayList failures= new ArrayList();
		addFailures(failures, getTestRoot());
		return (TestCaseElement[]) failures.toArray(new TestCaseElement[failures.size()]);
	}

	private void addFailures(ArrayList failures, TestElement testElement) {
		if (testElement instanceof TestCaseElement) {
			TestCaseElement testCaseElement= (TestCaseElement) testElement;
			if (testCaseElement.getStatus().isFailure()) {
				failures.add(testCaseElement);
			}
		} else {
			TestSuiteElement testSuiteElement= (TestSuiteElement) testElement;
			TestElement[] children= testSuiteElement.getChildren();
			for (int i= 0; i < children.length; i++) {
				addFailures(failures, children[i]);
			}
		}
	}
}
