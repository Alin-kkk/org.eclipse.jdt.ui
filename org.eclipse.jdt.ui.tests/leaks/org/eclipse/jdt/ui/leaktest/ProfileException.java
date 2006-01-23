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
package org.eclipse.jdt.ui.leaktest;



public class ProfileException extends Exception {

	private static final long serialVersionUID= -7189229935014298072L;

	public ProfileException(String msg, Throwable e) {
		super(msg, e);
	}

	public ProfileException(Throwable e) {
		super(e);
	}

	public ProfileException(String msg) {
		super(msg);
	}

}
