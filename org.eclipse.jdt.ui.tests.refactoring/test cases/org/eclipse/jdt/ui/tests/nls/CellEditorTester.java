package org.eclipse.jdt.ui.tests.nls;


import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.eclipse.jdt.internal.ui.refactoring.nls.MultiStateCellEditor;


public class CellEditorTester extends TestCase {

	/**
	 * Constructor for CellEditorTester
	 */
	public CellEditorTester(String name) {
		super(name);
	}
	
	public static void main (String[] args) {
		junit.textui.TestRunner.run (suite());
	}
	
	public static Test suite() {
		return new TestSuite(CellEditorTester.class);
	}
	
	public void test0(){
		MultiStateCellEditor ce= new MultiStateCellEditor(null, 3, 0);
		assertTrue(ce.getValue().equals(new Integer(0)));	
	}
	
	public void test1(){
		MultiStateCellEditor ce= new MultiStateCellEditor(null, 3, 0);
		ce.activate();
		assertTrue(ce.getValue().equals(new Integer(1)));	
	}
	public void test2(){
		MultiStateCellEditor ce= new MultiStateCellEditor(null, 3, 0);
		ce.activate();
		ce.activate();
		assertTrue(ce.getValue().equals(new Integer(2)));	
	}
	
	public void test3(){
		MultiStateCellEditor ce= new MultiStateCellEditor(null, 3, 0);
		ce.activate();
		ce.activate();
		ce.activate();
		assertTrue(ce.getValue().equals(new Integer(0)));	
	}
	
	public void test4(){
		MultiStateCellEditor ce= new MultiStateCellEditor(null, 3, 0);
		ce.setValue(new Integer(1));
		assertTrue(ce.getValue().equals(new Integer(1)));	
	}
	
	public void test5(){
		MultiStateCellEditor ce= new MultiStateCellEditor(null, 3, 0);
		ce.setValue(new Integer(2));
		ce.activate();
		assertTrue(ce.getValue().equals(new Integer(0)));	
	}	
}


