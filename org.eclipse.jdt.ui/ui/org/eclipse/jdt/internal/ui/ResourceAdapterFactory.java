package org.eclipse.jdt.internal.ui;/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 1999, 2000
 */


import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdapterFactory;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

public class ResourceAdapterFactory implements IAdapterFactory {

	private static Class[] PROPERTIES= new Class[] {
		IJavaElement.class
	};
		
	public Class[] getAdapterList() {
		return PROPERTIES;
	}
	
	public Object getAdapter(Object element, Class key) {
		if (IJavaElement.class.equals(key)) {
			return JavaCore.create((IResource)element);
		}
		return null;
	}	
}