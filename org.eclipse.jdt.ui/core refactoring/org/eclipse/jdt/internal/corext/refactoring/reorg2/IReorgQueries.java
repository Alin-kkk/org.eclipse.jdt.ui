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
package org.eclipse.jdt.internal.corext.refactoring.reorg2;


public interface IReorgQueries {
	public static final int CONFIRM_DELETE_GETTER_SETTER= 						1;
	public static final int CONFIRM_DELETE_EMPTY_CUS= 							2;
	public static final int CONFIRM_DELETE_REFERENCED_ARCHIVES= 				3;
	public static final int CONFIRM_DELETE_FOLDERS_CONTAINING_SOURCE_FOLDERS= 	4;
	public static final int CONFIRM_READ_ONLY_ELEMENTS= 						5;
	public static final int CONFIRM_OVERWRITTING=								6;
	
	/*
	 * The ID is used to uniquely identify a query. It's also useful for testing.
	 */
	IConfirmQuery createYesYesToAllNoNoToAllQuery(String queryTitle, boolean allowCancel, int queryID);

	/*
	 * The ID is used to uniquely identify a query. It's also useful for testing.
	 */
	IConfirmQuery createYesNoQuery(String queryTitle, boolean allowCancel, int queryID);
}