/**********************************************************************Copyright (c) 2000, 2002 IBM Corp. and others.All rights reserved. � This program and the accompanying materialsare made available under the terms of the Common Public License v0.5which accompanies this distribution, and is available athttp://www.eclipse.org/legal/cpl-v05.html�Contributors:	Daniel Megert - Initial API**********************************************************************/package org.eclipse.jdt.ui.jarpackager;import org.eclipse.core.runtime.IStatus;import org.eclipse.jface.operation.IRunnableWithProgress;/**
 * A runnable which executes a JAR export operation within the workspace. * * Clients may implement this interface. *  * @see org.eclipse.jdt.ui.jarpackager.JarPackageData#createJarExportRunnable(Shell) * @see org.eclipse.core.resources.IWorkspaceRunnable * @since 2.0
 */
public interface IJarExportRunnable extends IRunnableWithProgress {

	/**
	 * Returns the current status of this operation.
	 * The result is a status object which may contain individual
	 * nested status objects.	 * <p>	 * Clients may call this method during the operation and add	 * additional status information.	 * </p>
	 *
	 * @return the status of this operation
	 */
	public IStatus getStatus();}
