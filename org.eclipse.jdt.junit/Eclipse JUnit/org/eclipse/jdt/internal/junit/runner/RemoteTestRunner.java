/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.junit.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;

import java.util.List;
import junit.extensions.TestDecorator;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestFailure;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.framework.TestSuite;

/**
 * A TestRunner that reports results via a socket connection.
 * See MessageIds for more information about the protocl.
 */
public class RemoteTestRunner implements TestListener {
	/**
	 * Holder for information for a rerun request
	 */
	private static class RerunRequest {
		String fClassName;
		String fTestName;
		
		public RerunRequest(String className, String testName) {
			fClassName= className;
			fTestName= testName;
		}
	}
	
	private static final String SUITE_METHODNAME= "suite";	
	/**
	 * The name of the test classes to be executed
	 */
	private String[] fTestClassNames;
	/**
	 * The current test result
	 */
	private TestResult fTestResult;

	/**
	 * The client socket.
	 */
	private Socket fClientSocket;
	/**
	 * Print writer for sending messages
	 */
	private PrintWriter fWriter;
	/**
	 * Reader for incoming messages
	 */
	private BufferedReader fReader;
	/**
	 * Host to connect to, default is the localhost
	 */
	private String fHost= "127.0.0.1";
	/**
	 * Port to connect to.
	 */
	private int fPort= -1;
	/**
	 * Is the debug mode enabled?
	 */
	private boolean fDebugMode= false;	
	/**
	 * Keep the test run server alive after a test run has finished.
	 * This allows to rerun tests.
	 */
	private boolean fKeepAlive= false;
	/**
	 * Has the server been stopped
	 */
	private boolean fStopped= false;
	/**
	 * Queue of rerun requests.
	 */
	private List fRerunRequests= new ArrayList(10);
	/**
	 * Reader thread that processes messages from the client.
	 */
	private class ReaderThread extends Thread {

		public void run(){
			try { 
				String message= null; 
				while (true) { 
					if ((message= fReader.readLine()) != null) {
						
						if (message.startsWith(MessageIds.TEST_STOP)){
							fStopped= true;
							RemoteTestRunner.this.stop();
							synchronized(RemoteTestRunner.this) {
								RemoteTestRunner.this.notifyAll();
							}
							break;
						}
						
						else if (message.startsWith(MessageIds.TEST_RERUN)) {
							String arg= message.substring(MessageIds.MSG_HEADER_LENGTH);
							int c= arg.indexOf(" ");
							synchronized(RemoteTestRunner.this) {
								fRerunRequests.add(new RerunRequest(arg.substring(0, c), arg.substring(c+1)));
								RemoteTestRunner.this.notifyAll();
							}
						}
					}
				} 
			} catch (Exception e) {
				RemoteTestRunner.this.stop();
			}
		}
	}	
	
	/** 
	 * The main entry point.
	 * Parameters<pre>
	 * -classnames: the name of the test suite class
     * -host: the host to connect to - default local host
     * -port: the port to connect to, mandatory argument
     * -keepalive: keep the process alive after a test run
     * </pre>
     */
	public static void main(String[] args) {
		// hack to pass the AllTests of JUnit
		// force static initialization of BaseTestRunner 
		// by creating a junit.textui.TestRunner and free
		// it immediately.
		junit.runner.BaseTestRunner runner= new junit.textui.TestRunner();
		runner= null;
		
		RemoteTestRunner testRunServer= new RemoteTestRunner();
		testRunServer.init(args);
		testRunServer.run();
	}
	
	/**
	 * Parse command line arguments. Hook for subclasses to process
	 * additional arguments.
	 */
	protected void init(String[] args) {
		defaultInit(args);		
	}	
	
	/**
	 * The class loader to be used for loading tests.
	 * Subclasses may override to use another class loader.
	 */
	protected ClassLoader getClassLoader() {
		return getClass().getClassLoader();
	}
	
	/**
	 * Process the default arguments.
	 */
	protected final void defaultInit(String[] args) {
		for(int i= 0; i < args.length; i++) {
			if(args[i].toLowerCase().equals("-classnames") || args[i].toLowerCase().equals("-classname")){
				ArrayList list= new ArrayList();
				for (int j= i+1; j < args.length; j++) {
					if (args[j].startsWith("-"))
						break;
					list.add(args[j]);
				}
				fTestClassNames= (String[]) list.toArray(new String[list.size()]);
			}		
			else if(args[i].toLowerCase().equals("-port")) {
				fPort= Integer.parseInt(args[i+1]);
			}
			else if(args[i].toLowerCase().equals("-host")) {
				fHost= args[i+1];
			}
			else if(args[i].toLowerCase().equals("-keepalive")) {
				fKeepAlive= true;
			}
			else if(args[i].toLowerCase().equals("-debugging") || args[i].toLowerCase().equals("-debug")){
				fDebugMode= true;
			}
		}
		if(fTestClassNames == null || fTestClassNames.length == 0)
			throw new IllegalArgumentException("Error: parameter '-classNames' or '-className' not specified");

		if (fPort == -1)
			throw new IllegalArgumentException("Error: parameter '-port' not specified");
		if (fDebugMode)
			System.out.println("keepalive "+fKeepAlive);
	}
	
	/**
	 * Connects to the remote ports and runs the tests.
	 */
	protected void run() {
		if (!connect()) {
			return;
		} 
			
		fTestResult= new TestResult();
		fTestResult.addListener(this);
		runTests(fTestClassNames);
		fTestResult.removeListener(this);
		
		if (fTestResult != null) {
			fTestResult.stop();
			fTestResult= null;
		}
		if (fKeepAlive)
			waitForReruns();
			
		shutDown();
	}

	/**
	 * Waits for rerun requests until an explicit stop request
	 */
	private synchronized void waitForReruns() {
		while (!fStopped) {
			try {
				wait();
				if (!fStopped && fRerunRequests.size() > 0) {
					RerunRequest r= (RerunRequest)fRerunRequests.remove(0);
					rerunTest(r.fClassName, r.fTestName);
				}
			} catch (InterruptedException e) {
			}
		}
	}
	
	/**
	 * Returns the Test corresponding to the given suite. 
	 */
	private Test getTest(String suiteClassName) {
		Class testClass= null;
		try {
			testClass= loadSuiteClass(suiteClassName);
		} catch (ClassNotFoundException e) {
			String clazz= e.getMessage();
			if (clazz == null) 
				clazz= suiteClassName;
			runFailed("Class not found \""+clazz+"\"");
			return null;
		} catch(Exception e) {
			runFailed("Error: "+e.toString());
			return null;
		}
		Method suiteMethod= null;
		try {
			suiteMethod= testClass.getMethod(SUITE_METHODNAME, new Class[0]);
	 	} catch(Exception e) {
	 		// try to extract a test suite automatically
			return new TestSuite(testClass);
		}
		Test test= null;
		try {
			test= (Test)suiteMethod.invoke(null, new Class[0]); // static method
		} 
		catch (InvocationTargetException e) {
			runFailed("Failed to invoke suite():" + e.getTargetException().toString());
			return null;
		}
		catch (IllegalAccessException e) {
			runFailed("Failed to invoke suite():" + e.toString());
			return null;
		}
		return test;
	}

	protected void runFailed(String message) {
		System.err.println(message);
	}
	
	/**
	 * Loads the test suite class.
	 */
	private Class loadSuiteClass(String className) throws ClassNotFoundException {
		if (className == null) 
			return null;
		return getClassLoader().loadClass(className);
	}
			
	/**
	 * Runs a set of tests.
	 */
	private void runTests(String[] testClassNames) {
		// instantiate all tests
		Test[] suites= new Test[testClassNames.length];
		for (int i= 0; i < suites.length; i++) {
			suites[i]= getTest(testClassNames[i]);
		}
		
		// count all testMethods and inform ITestRunListeners		
		int count= countTests(suites);
		notifyTestRunStarted(count);
		
		long startTime= System.currentTimeMillis();
		if (fDebugMode)
			System.out.println("start send tree");
		sendTree(suites[0]);
		if (fDebugMode)
			System.out.println("done send tree"+(System.currentTimeMillis()-startTime));

		long testStartTime= System.currentTimeMillis();
		for (int i= 0; i < suites.length; i++) {
			suites[i].run(fTestResult);
		}
		// inform ITestRunListeners of test end
		if (fTestResult == null || fTestResult.shouldStop())
			notifyTestRunStopped(System.currentTimeMillis() - testStartTime);
		else
			notifyTestRunEnded(System.currentTimeMillis() - testStartTime);
	}
	
	private int countTests(Test[] tests) {
		int count= 0;
		for (int i= 0; i < tests.length; i++) {
			count= count + tests[i].countTestCases();
		}
		return count;
	}
	
	/**
	 * Reruns a test as defined by the fully qualified class name and
	 * the name of the test.
	 */
	public void rerunTest(String className, String testName) {
		Test reloadedTest= null;
		try {
			Class reloadedTestClass= getClassLoader().loadClass(className);
			Class[] classArgs= { String.class };
			Constructor constructor= reloadedTestClass.getConstructor(classArgs);
			Object[] args= new Object[]{testName};
			reloadedTest=(Test)constructor.newInstance(args);
		} catch(Exception e) {
			System.err.println("Could not load " + reloadedTest);
			return;
		}
		TestResult result= new TestResult();
		reloadedTest.run(result);
		notifyTestReran(result, className, testName);
	}


	/*
	 * @see TestListener#addError(Test, Throwable)
	 */
	public final void addError(Test test, Throwable throwable) {
		notifyTestFailed(MessageIds.TEST_ERROR, test.toString(), getTrace(throwable));
	}

	/*
	 * @see TestListener#addFailure(Test, AssertionFailedError)
	 */
	public final void addFailure(Test test, AssertionFailedError assertionFailedError) {
		notifyTestFailed(MessageIds.TEST_FAILED, test.toString(), getTrace(assertionFailedError));
	}

	/*
	 * @see TestListener#endTest(Test)
	 */
	public void endTest(Test test) {
		notifyTestEnded(test.toString());
	}

	/*
	 * @see TestListener#startTest(Test)
	 */
	public void startTest(Test test) {
		notifyTestStarted(test.toString());
	}
	
	private void sendTree(Test test){
		if(test instanceof TestDecorator){
			TestDecorator decorator= (TestDecorator) test;
			sendTree(decorator.getTest());		
		}
		else if(test instanceof TestSuite){
			TestSuite suite= (TestSuite) test;
			notifyTestTreeEntry(suite.toString().trim() + ',' + true + ',' + suite.testCount());
			for(int i=0; i < suite.testCount(); i++){	
				sendTree(suite.testAt(i));		
			}				
		}
		else {
			notifyTestTreeEntry(test.toString().trim() + ',' + false + ',' +  test.countTestCases());
		}
	}
	
	/**
	 * Returns the stack trace for the given throwable.
	 */
	private String getTrace(Throwable t) { 
		StringWriter stringWriter= new StringWriter();
		PrintWriter writer= new PrintWriter(stringWriter);
		t.printStackTrace(writer);
		StringBuffer buffer= stringWriter.getBuffer();
		return buffer.toString();
	}	

	/**
	 * Stop the current test run.
	 */
	protected void stop() {
		if (fTestResult != null) {
			fTestResult.stop();
		}
	}
	
	/**
	 * Connect to the remote test listener.
	 */
	private boolean connect() {
		if (fDebugMode)
			System.out.println("RemoteTestRunner: trying to connect" + fHost + ":" + fPort);
		Exception exception= null;
		for (int i= 1; i < 5; i++) {
			try{
				fClientSocket= new Socket(fHost, fPort);
				fWriter= new PrintWriter(fClientSocket.getOutputStream(), false/*true*/);
				fReader= new BufferedReader(new InputStreamReader(fClientSocket.getInputStream()));
				new ReaderThread().start();
				return true;
			} catch(IOException e){
				exception= e;
			}
			try {
				Thread.currentThread().sleep(200);
			} catch(InterruptedException e) {
			}
		}
		System.err.println("Could not connect to: " + fHost + ":" + fPort);			
		exception.printStackTrace();
		return false;
	}

	/**
	 * Shutsdown the connection to the remote test listener.
	 */
	private void shutDown() {
		if (fWriter != null) {
			fWriter.close();
			fWriter= null;
		}
		
		try {
			if (fReader != null) {
				fReader.close();
				fReader= null;
			}
		} catch(IOException e) {
			if (fDebugMode)
				e.printStackTrace();
		}
		
		try {
			if (fClientSocket != null) {
				fClientSocket.close();
				fClientSocket= null;
			}
		} catch(IOException e) {
			if (fDebugMode)	
				e.printStackTrace();
		}
	}


	private void sendMessage(String msg) {
		if(fWriter == null) 
			return;
		fWriter.println(msg);
	}


	private void notifyTestRunStarted(int testCount) {
		sendMessage(MessageIds.TEST_RUN_START + testCount);
	}


	private void notifyTestRunEnded(long elapsedTime) {
		sendMessage(MessageIds.TEST_RUN_END + elapsedTime);
		fWriter.flush();
		//shutDown();
	}


	private void notifyTestRunStopped(long elapsedTime) {
		sendMessage(MessageIds.TEST_STOPPED + elapsedTime );
		fWriter.flush();
		//shutDown();
	}

	private void notifyTestStarted(String testName) {
		sendMessage(MessageIds.TEST_START + testName);
		fWriter.flush();
	}

	private void notifyTestEnded(String testName) {
		sendMessage(MessageIds.TEST_END + testName);
	}

	private void notifyTestFailed(String status, String testName, String trace) {
		sendMessage(status + testName);
		sendMessage(MessageIds.TRACE_START);
		sendMessage(trace);
		sendMessage(MessageIds.TRACE_END);
		fWriter.flush();
	}

	private void notifyTestTreeEntry(String treeEntry) {
		sendMessage(MessageIds.TEST_TREE + treeEntry);
	}
	
	private void notifyTestReran(TestResult result, String testClass, String testName) {
		TestFailure failure= null;
		if (result.errorCount() > 0) {
			failure= (TestFailure)result.errors().nextElement();
		}
		if (result.failureCount() > 0) {
			failure= (TestFailure)result.failures().nextElement();
		}
		if (failure != null) {
			Throwable t= failure.thrownException();
			String trace= getTrace(t);
			sendMessage(MessageIds.RTRACE_START);
			sendMessage(trace);
			sendMessage(MessageIds.RTRACE_END);
			fWriter.flush();
		}
		String status= "OK";
		if (result.errorCount() > 0)
			status= "ERROR";
		else if (result.failureCount() > 0)
			status= "FAILURE";
		if (fPort != -1) {
			sendMessage(MessageIds.TEST_RERAN + testClass+" "+testName+" "+status);
			fWriter.flush();
		}
	}
}	
