package org.eclipse.jdt.internal.ui.text;

/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 2000
 */

import org.eclipse.jface.text.rules.IWhitespaceDetector;

/**
 * A java aware white space detector.
 */
public class JavaWhitespaceDetector implements IWhitespaceDetector {

	/**
	 * @see IWhitespaceDetector#isWhitespace
	 */
	public boolean isWhitespace(char c) {
		return Character.isWhitespace(c);
	}
}
