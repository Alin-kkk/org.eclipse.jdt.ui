package org.eclipse.jdt.ui.tests.nls;


import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;


public class NLSScannerTester extends TestCase {


	public NLSScannerTester(String name) {
		super(name);
	}
	
	public static void main (String[] args) {
		junit.textui.TestRunner.run (suite());
	}
	
	public static Test suite() {
		return new TestSuite(NLSScannerTester.class);
	}
	
	private void printDisabledMessage(String msg){
		System.out.println("\nTest " + getName() + " disabled (" + msg + ")");
	}
	
	public void test0() throws Exception{
		String text= "fred";
		List l= NLSScanner.scan(text);
		assertEquals("empty", true, l.isEmpty());
	}
	
	public void test1() throws Exception{
//		String text= "fred\"x\"";
//		List l= NLSScanner.scan(text);
//		assertEquals("non empty", false, l.isEmpty());
//		assertEquals("1 line", 1, l.size());
		printDisabledMessage("Scanner does not handle strings in the first line");
	}
	
	public void test1a() throws Exception{
		String text= "fred\n\"x\"";
		List l= NLSScanner.scan(text);
		assertEquals("non empty", false, l.isEmpty());
		assertEquals("1 line", 1, l.size());
	}
	
	public void test2() throws Exception{
//		String text= "fred\"x\" \"xx\"";
//		List l= NLSScanner.scan(text);
//		assertEquals("non empty", false, l.isEmpty());
//		assertEquals("2 line", 2, l.size());
		printDisabledMessage("Scanner does not handle strings in the first line");
	}
	
	public void test2a() throws Exception{
		String text= "fred\n\"x\" \"xx\"";
		List l= NLSScanner.scan(text);
		assertEquals("non empty", false, l.isEmpty());
		assertEquals("1 lines", 1, l.size());
	}
	
	public void test3() throws Exception{
//		String text= "fred\"x\"\n \"xx\"";
//		List l= NLSScanner.scan(text);
//		assertEquals("non empty", false, l.isEmpty());
//		assertEquals("2 lines", 2, l.size());
		printDisabledMessage("Scanner does not handle strings in the first line");
	}


	public void test4() throws Exception{
		String text= "fred\n \"xx\"";
		List l= NLSScanner.scan(text);
		assertEquals("non empty", false, l.isEmpty());
		assertEquals("1 line", 1, l.size());
	}
	
	public void test5() throws Exception{
		String text= "\n \"xx\"";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("1 string", 1, line.size());
	}	
	
	public void test6() throws Exception{
		String text= "\n \"xx\" \"dff\"";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("2 string", 2, line.size());
	}	
	
	public void test7() throws Exception{
		String text= "\n \"xx\" \n\"dff\"";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("1 string A", 1, line.size());
		
		line= ((NLSLine)l.get(1));
		assertEquals("1 string B", 1, line.size());
	}	


	public void test8() throws Exception{
		String text= "\n \"xx\" \n\"dff\" \"ccc\"";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("1 string A", 1, line.size());
		
		line= ((NLSLine)l.get(1));
		assertEquals("2 strings B", 2, line.size());
	}
	
	public void test9() throws Exception{
		String text= "fred\n \"xx\"" + NLSElement.createTagText(1) + "\n";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		NLSElement el= line.get(0);
		assertEquals("has tag", true, el.hasTag());
	}


	public void test10() throws Exception{
		String text= "fred\n \"xx\"\n";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		NLSElement el= line.get(0);
		assertEquals("has tag", false, el.hasTag());
	}
	
	public void test11() throws Exception{
		String text= 
				"\n\"x\" \"y\""
				+ NLSElement.createTagText(2) 
				+ NLSElement.createTagText(1) 
				+ "\n";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("2 strings", 2, line.size());
		
		NLSElement el= line.get(0);
		assertEquals("0 has tag", true, el.hasTag());
		
		el= line.get(1);
		assertEquals("1 has tag", true, el.hasTag());
	}
	
	public void test12() throws Exception{
		String text= 
				"\n\"x\" \"y\""
				+ NLSElement.createTagText(1) 
				+ NLSElement.createTagText(2) 
				+ "\n";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("2 strings", 2, line.size());
		
		NLSElement el= line.get(0);
		assertEquals("0 has tag", true, el.hasTag());
		
		el= line.get(1);
		assertEquals("1 has tag", true, el.hasTag());
	}
	
	public void test13() throws Exception{
		String text= 
				"\n\"x\" \"y\""
				+ NLSElement.createTagText(1) 
				+ "\n";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("2 strings", 2, line.size());
		
		NLSElement el= line.get(0);
		assertEquals("0 has tag", true, el.hasTag());
		
		el= line.get(1);
		assertEquals("1 has no tag", false, el.hasTag());
	}
	
	public void test14() throws Exception{
		String text= 
				"\n\"x\" \"y\""
				+ NLSElement.createTagText(2) 
				+ "\n";
		List l= NLSScanner.scan(text);
		NLSLine line= ((NLSLine)l.get(0));
		assertEquals("2 strings", 2, line.size());
		
		NLSElement el= line.get(0);
		assertEquals("0 has no tag", false, el.hasTag());
		
		el= line.get(1);
		assertEquals("1 has tag", true, el.hasTag());
	}
				
}


