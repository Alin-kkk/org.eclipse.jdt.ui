/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.text.tests;

import java.io.Reader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IBufferChangedListener;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.javadoc.JavaDocCommentReader;

import org.eclipse.jdt.internal.ui.text.HTMLPrinter;
import org.eclipse.jdt.internal.ui.text.javadoc.JavaDoc2HTMLTextReader;

public class JavaDoc2HTMLTextReaderTester extends TestCase {

	private boolean isVerbose= false;
	
	public JavaDoc2HTMLTextReaderTester(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(JavaDoc2HTMLTextReaderTester.class);
	}

	private String getTransformedJavaDoc(String string) {
		Reader reader= new JavaDocCommentReader(new MockBuffer(string), 0, string.length());
		return HTMLPrinter.read(new JavaDoc2HTMLTextReader(reader));
	}
	
	private void verify(String string, String expected){
		String result = getTransformedJavaDoc(string);
		if (isVerbose)
			System.out.println("result:" + result); //$NON-NLS-1$
		assertEquals(expected, result);
	}

	public void test0(){
		String string= "/**@deprecated*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@deprecated</dt><dd></dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test1(){
		String string= "/**@author Foo Bar*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar</dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test2(){
		//test for bug 14658
		String string= "/**@author Foo Bar<a href=\"mailto:foobar@eclipse.org\">foobar@eclipse.org</a>*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar<a href=\"mailto:foobar@eclipse.org\">foobar@eclipse.org</a></dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}
	
	public void test3(){
		//test for bug 14658
		String string= "/**@author Foo Bar<a href=\"mailto:foobar@eclipse.org\">foobar@eclipse.org</a>\n *@deprecated*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar<a href=\"mailto:foobar@eclipse.org\">foobar@eclipse.org</a></dd><dt>@deprecated</dt><dd></dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test4(){
		String string= "/**@author Foo Bar\n * @deprecated*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar</dd><dt>@deprecated</dt><dd></dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}
	
	public void test5(){
		String string= "/**@author Foo Bar\n * @author Baz Fred*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar</dd><dt>@author</dt><dd>Baz Fred</dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test6(){
		String string= "/**@author Foo Bar\n * @since 2.0*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar</dd><dt>@since</dt><dd>2.0</dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	
	public void test7(){
		if (true){
			System.out.println(getClass().getName()+"::" + getName() +" disabled(corner case - @see tag inside <a> tag)"); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		String string= "/**@author Foo Bar<a href=\"mailto:foobar@see.org\">foobar@see.org</a>*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar<a href=\"mailto:foobar@see.org\">foobar@see.org</a></dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test8(){
		if (true){
			System.out.println(getClass().getName()+"::" + getName() +" disabled(corner case - @see tag inside <a> tag)"); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		String string= "/**@author Foo Bar<a href=\"mailto:foobar@see.org\">foobar@eclipse.org</a>*/"; //$NON-NLS-1$
		String expected= "<dl><dt>@author</dt><dd>Foo Bar<a href=\"mailto:foobar@see.org\">foobar@eclipse.org</a></dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test9(){
		String string= "/**@throws NullPointerException*/"; //$NON-NLS-1$
		String expected= "<dl><dt>Throws:</dt><dd>NullPointerException</dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test10(){
		//test for bug 8131
		String string= "/**@exception NullPointerException*/"; //$NON-NLS-1$
		String expected= "<dl><dt>Throws:</dt><dd>NullPointerException</dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test11(){
		//test for bug 8132
		String string= "/**@exception NullPointerException \n * @throws java.lang.Exception*/"; //$NON-NLS-1$
		String expected= "<dl><dt>Throws:</dt><dd>NullPointerException</dd><dd>java.lang.Exception</dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}

	public void test12(){
		String string= "/** \n *@param i fred or <code>null</code> \n*/"; //$NON-NLS-1$
		String expected= "<dl><dt>Parameters:</dt><dd><b>i</b> fred or <code>null</code></dd></dl>"; //$NON-NLS-1$
		verify(string, expected);
	}
	
	public void test13_withText(){
		String string= "/**\n * This is a {@linkplain Foo#bar(String, int) test link}. End.*/"; //$NON-NLS-1$
		String expected= " This is a test link. End."; //$NON-NLS-1$
		verify(string, expected);
	}
	
	public void test13_withoutText(){
		String string= "/**\n * This is a {@linkplain Foo#bar(String, int)}. End.*/"; //$NON-NLS-1$
		String expected= " This is a Foo.bar(String, int). End."; //$NON-NLS-1$
		verify(string, expected);
	}
	
	public void test14_withText(){
		String string= "/**\n * This is a {@link Foo#bar(String, int) test link}. End.*/"; //$NON-NLS-1$
		String expected= " This is a test link. End."; //$NON-NLS-1$
		verify(string, expected);
	}
	
	
	public void test14_withoutText(){
		String string= "/**\n * This is a {@link Foo#bar(String, int)}. End.*/"; //$NON-NLS-1$
		String expected= " This is a Foo.bar(String, int). End."; //$NON-NLS-1$
		verify(string, expected);
	}
	
	public void test15(){
		String string= "/**\n * This is a <a href=\"{@docRoot}/test.html\">test link</a>. End.*/"; //$NON-NLS-1$
		String expected= " This is a <a href=\"/test.html\">test link</a>. End."; //$NON-NLS-1$
		verify(string, expected);
	}
}

class MockBuffer implements IBuffer{
	
	private StringBuffer fStringBuffer;
	MockBuffer(String string){
		fStringBuffer= new StringBuffer(string);
	}
	
	public void addBufferChangedListener(IBufferChangedListener listener) {
	}


	public void append(char[] text) {
		fStringBuffer.append(text);
	}


	public void append(String text) {
		fStringBuffer.append(text);
	}


	public void close() {
	}


	public char getChar(int position) {
		return fStringBuffer.charAt(position);
	}


	public char[] getCharacters() {
		return fStringBuffer.toString().toCharArray();
	}


	public String getContents() {
		return fStringBuffer.toString();
	}


	public int getLength() {
		return fStringBuffer.length();
	}


	public IOpenable getOwner() {
		return null;
	}


	public String getText(int offset, int length) {
		return fStringBuffer.toString().substring(offset, offset + length);
	}


	public IResource getUnderlyingResource() {
		return null;
	}


	public boolean hasUnsavedChanges() {
		return false;
	}


	public boolean isClosed() {
		return false;
	}


	public boolean isReadOnly() {
		return false;
	}


	public void removeBufferChangedListener(IBufferChangedListener listener) {
	}


	public void replace(int position, int length, char[] text) {
	}


	public void replace(int position, int length, String text) {
	}


	public void save(IProgressMonitor progress, boolean force)
		throws JavaModelException {
	}


	public void setContents(char[] contents) {
	}


	public void setContents(String contents) {
		fStringBuffer= new StringBuffer(contents);
	}
}

