/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp. and others.
All rights reserved. � This program and the accompanying materials
are made available under the terms of the Common Public License v0.5
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v05.html
�
Contributors:
	Daniel Megert - Initial API
**********************************************************************/

package org.eclipse.jdt.ui.jarpackager;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

/**
 * Reads the description file of a JAR package data object
 * into such an object.
 * <p>
 * The format is defined by the client who implements the
 * reader/writer pair.
 * </p>
 * 
 * @see org.eclipse.jdt.ui.jarpackager.JarPackageData
 * @see org.eclipse.jdt.ui.jarpackager.IJarDescriptionWriter
 * @since 2.0
 */
public interface IJarDescriptionReader {

	/**
	 * Reads Jar Package description and fills data into
     * It is the client's responsibility to close the reader
	 * the JAR Package data object.
	 * 
	 * @param jarPackageData	the object into which data is filled
	 * @throws CoreException	if read failed, e.g. I/O error during read operation
	 */
	public void read(JarPackageData jarPackageData) throws CoreException;
	
	/**
     * Closes this reader.
     * It is the client's responsibility to close the reader
     * after the read operation.
     * 
	 * @throws CoreException	if closing fails, e.g. I/O error during close operation
     */
    public void close() throws CoreException;

	/**
	 * Returns the status of this reader.
	 * If there were any errors, the result is a status object containing
	 * individual status objects for each error.
	 * If there were no errors, the result is a status object with error code <code>OK</code>.
	 *
	 * @return the status of this operation
	 */
	public IStatus getStatus();
}
