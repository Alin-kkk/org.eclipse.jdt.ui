package org.eclipse.jdt.ui.nls.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.eclipse.jdt.internal.ui.nls.model.NLSElement;

public class NLSElementTester extends TestCase{
	
	public NLSElementTester(String name) {
		super(name);
	}
	
	private NLSElement fEl;
	private int fOff, fLen;
	private String fVal;
	
	
	public static void main (String[] args) {
		junit.textui.TestRunner.run (suite());
	}
	
	public static Test suite() {
		return new TestSuite(NLSElementTester.class);
	}
	
	protected void setUp(){
		fOff= 3;
		fLen= 5;
		fVal= "test";
		fEl= new NLSElement(fVal, fOff, fLen);
	}
	
	protected void tearDown(){
	}
	
	public void test0(){
		assertEquals("Position offset", fOff, fEl.getPosition().getOffset());
	}
	
	public void test1(){	
		assertEquals("Position length", fLen, fEl.getPosition().getLength());
	}
	
	public void test2(){		
		assertEquals("value", fVal, fEl.getValue());
	}
	
	public void test3(){	
		assertEquals("tagposition", null, fEl.getTagPosition());
	}
	
	public void test3a(){	
		fEl.setTagPosition(1, 2);
		assertEquals("tagposition.length", 2, fEl.getTagPosition().getLength());
		assertEquals("tagposition.offset", 1, fEl.getTagPosition().getOffset());
	}

	public void test4(){	
		assertEquals("hastag", false, fEl.hasTag());
	}
	
	public void test4a(){	
		fEl.setTagPosition(1, 2);
		assertEquals("hastag", true, fEl.hasTag());
	}
		
}

