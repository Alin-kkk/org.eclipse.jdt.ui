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
package org.eclipse.jdt.internal.ui.javaeditor;



import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModelEvent;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.texteditor.MarkerAnnotation;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Event sent out by changes of the compilation unit annotation model.
 */
public class CompilationUnitAnnotationModelEvent  extends AnnotationModelEvent {
	
	private boolean fIncludesProblemMarkerAnnotations;
	private IResource fUnderlyingResource;
	
	/**
	 * Constructor for CompilationUnitAnnotationModelEvent.
	 * @param model
	 * @param underlyingResource The annotation model's underlying resource 
	 */
	public CompilationUnitAnnotationModelEvent(IAnnotationModel model, IResource underlyingResource) {
		super(model);
		fUnderlyingResource= underlyingResource;
		fIncludesProblemMarkerAnnotations= false;
	}
	
	private void testIfProblemMarker(Annotation annotation) {
		if (!fIncludesProblemMarkerAnnotations && annotation instanceof MarkerAnnotation) {
			try {
				IMarker marker= ((MarkerAnnotation) annotation).getMarker();
				if (!marker.exists() || marker.isSubtypeOf(IMarker.PROBLEM)) {
					fIncludesProblemMarkerAnnotations= true;
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}		
	}
	
	/**
	 * Report an added annotation
	 */
	/*package*/ void annotationAdded(Annotation annotation) {
		testIfProblemMarker(annotation);
	}

	/**
	 * Report an remove annotation
	 */
	/*package*/ void annotationRemoved(Annotation annotation) {
		testIfProblemMarker(annotation);
	}
		
	/**
	 * Returns whether the change included problem marker annotations.
	 * 
	 * @return <code>true</code> if the change included marker annotations
	 */
	public boolean includesProblemMarkerAnnotationChanges() {
		return fIncludesProblemMarkerAnnotations;
	}
	
	/**
	 * Returns the annotation model's underlying resource
	 */
	public IResource getUnderlyingResource() {
		return fUnderlyingResource;
	}

}
