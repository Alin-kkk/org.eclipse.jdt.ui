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

package org.eclipse.jdt.text.tests.comments;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * 
 * @since 3.0
 */
public class CommentsTestSuite {

	public static Test suite() {
		TestSuite suite = new TestSuite("Test Suite org.eclipse.jdt.text.tests.comments"); //$NON-NLS-1$
		//$JUnit-BEGIN$
		suite.addTest(MultiLineTestCase.suite());
		suite.addTest(SingleLineTestCase.suite());
		suite.addTest(JavaDocTestCase.suite());
		//$JUnit-END$
		return suite;
	}
}
