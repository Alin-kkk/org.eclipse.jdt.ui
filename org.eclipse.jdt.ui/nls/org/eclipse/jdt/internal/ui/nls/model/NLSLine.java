/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.nls.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.util.Assert;

public class NLSLine {

	private int fLineNumber;
	private List fElements;

	public NLSLine(int lineNumber) {
		fLineNumber= lineNumber;
		Assert.isTrue(fLineNumber >= 0);
		fElements= new ArrayList();
	}
	
	/**
	 * Adds a NLS element to this line.
	 */
	public void add(NLSElement element) {
		Assert.isNotNull(element);
		fElements.add(element);
	}
	
	public NLSElement[] getElements() {
		return (NLSElement[]) fElements.toArray(new NLSElement[fElements.size()]);
	}
	
	public NLSElement get(int index) {
		return (NLSElement)fElements.get(index);
	}
	
	public boolean exists(int index) {
		return index >= 0 && index < fElements.size();
	}
	
	public int size(){
		return fElements.size();
	}
	
	public String toString() {
		StringBuffer result= new StringBuffer();
		result.append("Line: " + fLineNumber + "\n"); //$NON-NLS-2$ //$NON-NLS-1$
		for (Iterator iter= fElements.iterator(); iter.hasNext(); ) {
			result.append("\t"); //$NON-NLS-1$
			result.append(iter.next().toString());
			result.append("\n"); //$NON-NLS-1$
		}
		return result.toString();
	}
}

