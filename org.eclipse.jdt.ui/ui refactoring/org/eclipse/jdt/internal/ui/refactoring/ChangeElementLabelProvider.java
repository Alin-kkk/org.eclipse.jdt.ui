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
package org.eclipse.jdt.internal.ui.refactoring;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IFile;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.ICompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextFileChange;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

class ChangeElementLabelProvider extends LabelProvider {

	private int fJavaElementFlags;
	private JavaElementLabelProvider fJavaElementLabelProvider;
	private Map fDescriptorImageMap= new HashMap();
	private boolean fShowQualification= true;

	public ChangeElementLabelProvider(int javaElementFlags) {
		fJavaElementFlags= javaElementFlags;
		fJavaElementLabelProvider= new JavaElementLabelProvider(javaElementFlags);
	}
		
	public void setShowQualification(boolean showQualification) {
		fShowQualification= showQualification;
		LabelProviderChangedEvent event= new LabelProviderChangedEvent(this, null);
		fireLabelProviderChanged(event);
	}
	
	public Image getImage(Object object) {
		if (object instanceof DefaultChangeElement) {
			Object element= ((DefaultChangeElement)object).getChange();
			return doGetImage(element);
		} else if (object instanceof TextEditChangeElement) {
			Object element= ((TextEditChangeElement)object).getTextEditChange();
			return doGetImage(element);
		} else if (object instanceof PseudoJavaChangeElement) {
			PseudoJavaChangeElement element= (PseudoJavaChangeElement)object;
			return fJavaElementLabelProvider.getImage(element.getJavaElement());
		}
		return super.getImage(object);
	}
	
	public String getText(Object object) {
		if (object instanceof DefaultChangeElement) {
			IChange change= ((DefaultChangeElement)object).getChange();
			if (!fShowQualification)
				return change.getName();
			
			if (change instanceof TextFileChange) {
				IFile file= ((TextFileChange)change).getFile();
				return RefactoringMessages.getFormattedString(
					"PreviewWizardPage.changeElementLabelProvider.textFormat",  //$NON-NLS-1$
					new String[] {file.getName(), getPath(file)});
			} else {
				return change.getName();
			}
		} else if (object instanceof TextEditChangeElement) {
			TextEditChangeElement element= (TextEditChangeElement)object;
			String result= element.getTextEditChange().getName();
			if ((fJavaElementFlags & JavaElementLabelProvider.SHOW_POST_QUALIFIED) != 0) {
				ChangeElement parent= getParent(element);
				if (parent != null) 
					result= RefactoringMessages.getFormattedString(
						"PreviewWizardPage.changeElementLabelProvider.textFormatEdit",  //$NON-NLS-1$
						new String[] {getText(parent), result});
			}
			return result;
		} else if (object instanceof PseudoJavaChangeElement) {
			PseudoJavaChangeElement element= (PseudoJavaChangeElement)object;
			return fJavaElementLabelProvider.getText(element.getJavaElement());
		}
		return super.getText(object);
	}
	
	public void dispose() {
		for (Iterator iter= fDescriptorImageMap.values().iterator(); iter.hasNext(); ) {
			Image image= (Image)iter.next();
			image.dispose();
		}
		super.dispose();
	}
	
	private Image doGetImage(Object element) {
		ImageDescriptor descriptor= null;
		if (descriptor == null) {
			if (element instanceof TextEditChangeElement) {
				descriptor= JavaPluginImages.DESC_OBJS_TEXT_EDIT;
			} else if (element instanceof ICompositeChange) {
				descriptor= JavaPluginImages.DESC_OBJS_COMPOSITE_CHANGE;	
			} else if (element instanceof CompilationUnitChange) {
				descriptor= JavaPluginImages.DESC_OBJS_CU_CHANGE;
			} else if (element instanceof TextFileChange) {
				descriptor= JavaPluginImages.DESC_OBJS_FILE_CHANGE;
			} else {
				descriptor= JavaPluginImages.DESC_OBJS_DEFAULT_CHANGE;
			}
		}
		Image image= (Image)fDescriptorImageMap.get(descriptor);
		if (image == null) {
			image= descriptor.createImage();
			fDescriptorImageMap.put(descriptor, image);
		}
		return image;
	}
	
	private String getPath(IFile file) {
		StringBuffer result= new StringBuffer(file.getProject().getName());
		String projectRelativePath= file.getParent().getProjectRelativePath().toString();
		if (projectRelativePath.length() > 0) {
			result.append('/');
			result.append(projectRelativePath);
		}
		return result.toString();
	}
	
	private ChangeElement getParent(TextEditChangeElement element) {
		ChangeElement parent= element.getParent();
		while (parent != null && !(parent instanceof PseudoJavaChangeElement) && !(parent instanceof DefaultChangeElement)) {
			parent= parent.getParent();
		}
		return parent;
	}
}

