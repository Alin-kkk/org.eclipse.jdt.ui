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


import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.model.IWorkbenchAdapter;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

/**
 * An imlementation of the IWorkbenchAdapter for IJavaElements.
 */
public class JavaWorkbenchAdapter implements IWorkbenchAdapter {
	
	protected static final Object[] NO_CHILDREN= new Object[0];
	
	private JavaElementImageProvider fImageProvider;
	private JavaElementLabelProvider fLabelProvider;

	public JavaWorkbenchAdapter() {
		fImageProvider= new JavaElementImageProvider();
		fLabelProvider= new JavaElementLabelProvider();
	}

	public Object[] getChildren(Object element) {
		if (element instanceof IParent) {
			try {
				return ((IParent)element).getChildren();
			} catch(JavaModelException e) {
				JavaPlugin.log(e); 
			}
		}
		return NO_CHILDREN;
	}

	public ImageDescriptor getImageDescriptor(Object element) {
		return fImageProvider.getJavaImageDescriptor(
			(IJavaElement)element, 
			JavaElementImageProvider.OVERLAY_ICONS | JavaElementImageProvider.SMALL_ICONS);
	}

	public String getLabel(Object element) {
		return JavaElementLabels.getTextLabel(element, JavaElementLabels.M_PARAMETER_TYPES);
	}

	public Object getParent(Object element) {
		if (element instanceof IJavaElement)
			return ((IJavaElement)element).getParent();
		return null;
	}
}
