/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * Convenience class for error exceptions thrown inside JavaUI plugin.
 */
public class JavaUIStatus extends Status {

	private JavaUIStatus(int severity, int code, String message, Throwable throwable) {
		super(severity, JavaPlugin.getPluginId(), code, message, throwable);
	}

	public static IStatus createError(int code, String message, Throwable throwable) {
		return new JavaUIStatus(IStatus.ERROR, code, message, throwable);
	}
	
	public static IStatus createWarning(int code, String message, Throwable throwable) {
		return new JavaUIStatus(IStatus.WARNING, code, message, throwable);
	}

	public static IStatus createInfo(int code, String message, Throwable throwable) {
		return new JavaUIStatus(IStatus.INFO, code, message, throwable);
	}
}

