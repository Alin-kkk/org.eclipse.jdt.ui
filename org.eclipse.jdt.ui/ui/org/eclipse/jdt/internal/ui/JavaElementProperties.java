package org.eclipse.jdt.internal.ui;/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 1999, 2000
 */


import org.eclipse.jface.viewers.IBasicPropertyConstants;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.PropertyDescriptor;

import org.eclipse.jdt.core.IJavaElement;

public class JavaElementProperties implements IPropertySource {
	
	private IJavaElement fSource;
	
	// Property Descriptors
	static private IPropertyDescriptor[] fgPropertyDescriptors= new IPropertyDescriptor[1];
	{
		PropertyDescriptor descriptor;

		// resource name
		descriptor= new PropertyDescriptor(IBasicPropertyConstants.P_TEXT, "Name");
		descriptor.setAlwaysIncompatible(true);
		fgPropertyDescriptors[0]= descriptor;
	}
	
	public JavaElementProperties(IJavaElement source) {
		fSource= source;
	}
	
	public IPropertyDescriptor[] getPropertyDescriptors() {
		return fgPropertyDescriptors;
	}
	
	public Object getPropertyValue(Object name) {
		if (name.equals(IBasicPropertyConstants.P_TEXT)) {
			return fSource.getElementName();
		}
		return null;
	}
	
	public void setPropertyValue(Object name, Object value) {
	}
	
	public Object getEditableValue() {
		return null;
	}
	
	public boolean isPropertySet(Object property) {
		return false;
	}
	
	public void resetPropertyValue(Object property) {
	}
}
