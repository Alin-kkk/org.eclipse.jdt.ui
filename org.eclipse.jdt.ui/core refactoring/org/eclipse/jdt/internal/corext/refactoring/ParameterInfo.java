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
package org.eclipse.jdt.internal.corext.refactoring;

import org.eclipse.jdt.internal.corext.Assert;

public class ParameterInfo {
	
	private static final int INDEX_FOR_ADDED= -1;
	private final String fOldName;
	private final String fOldTypeName;
	private final int fOldIndex;

	private String fNewTypeName;
	private String fDefaultValue;
	private String fNewName;
	private Object fData;
	private boolean fIsDeleted;
	
	public ParameterInfo(String type, String name, int index){
		fOldTypeName= type;
		fNewTypeName= type;
		fOldName= name;
		fNewName= name;
		fOldIndex= index;
		fDefaultValue= ""; //$NON-NLS-1$
		fIsDeleted= false;
	}

	public static ParameterInfo createInfoForAddedParameter(){
		return new ParameterInfo("", "", INDEX_FOR_ADDED); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	public boolean isDeleted(){
		return fIsDeleted;
	}
	
	public void markAsDeleted(){
		Assert.isTrue(! isAdded());//added param infos should be simply removed from the list
		fIsDeleted= true;
	}
	
	public boolean isAdded(){
		return fOldIndex == INDEX_FOR_ADDED;
	}
	
	public String getDefaultValue(){
		return fDefaultValue;
	}
	
	public void setDefaultValue(String value){
		Assert.isNotNull(value);
		fDefaultValue= value;
	}

	public String getOldTypeName() {
		return fOldTypeName;
	}
	
	public String getNewTypeName() {
		return fNewTypeName;
	}
	
	public void setNewTypeName(String type){
		Assert.isNotNull(type);
		fNewTypeName= type;
	}

	public String getOldName() {
		return fOldName;
	}

	public int getOldIndex() {
		return fOldIndex;
	}

	public void setNewName(String newName) {
		Assert.isNotNull(newName);
		fNewName= newName;
	}

	public String getNewName() {
		return fNewName;
	}

	public Object getData() {
		return fData;
	}

	public void setData(Object data) {
		fData= data;
	}
	
	public boolean isRenamed() {
		return !fOldName.equals(fNewName);
	}
	
	public boolean isTypeNameChanged() {
		return !fOldTypeName.equals(fNewTypeName);
	}
	
}
