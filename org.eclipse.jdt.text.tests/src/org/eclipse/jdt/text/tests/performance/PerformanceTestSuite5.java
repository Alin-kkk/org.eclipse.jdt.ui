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
package org.eclipse.jdt.text.tests.performance;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * @since 3.1
 */
public class PerformanceTestSuite5 extends TestSuite {

	public static Test suite() {
		// exclude PerformanceTestSetup because this suite measures startup performance
		return new CloseWorkbenchDecorator(new PerformanceTestSuite5());
	}
	
	public PerformanceTestSuite5() {
		addTest(OpenTextEditorStartupTest.suiteForMeasurement());
	}
}
