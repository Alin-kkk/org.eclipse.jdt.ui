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


import java.util.Iterator;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.debug.core.model.IBreakpoint;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.Assert;

import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.eclipse.ui.texteditor.MarkerUtilities;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;

import org.eclipse.search.ui.SearchUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.core.Util;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;



public class JavaMarkerAnnotation extends MarkerAnnotation implements IJavaAnnotation {
	
	private static final int NO_IMAGE= 0;
	private static final int ORIGINAL_MARKER_IMAGE= 1;
	private static final int QUICKFIX_IMAGE= 2;
	private static final int QUICKFIX_ERROR_IMAGE= 3;
	private static final int OVERLAY_IMAGE= 4;
	private static final int GRAY_IMAGE= 5;
	private static final int BREAKPOINT_IMAGE= 6;

	private static Image fgQuickFixImage;
	private static Image fgQuickFixErrorImage;
	private static ImageRegistry fgGrayMarkersImageRegistry;
	
	private IDebugModelPresentation fPresentation;
	private IJavaAnnotation fOverlay;
	private boolean fNotRelevant= false;
	private AnnotationType fType;
	private int fImageType;
	private boolean fQuickFixIconEnabled;
	
	
	public JavaMarkerAnnotation(IMarker marker) {
		super(marker);
	}
	
	/*
	 * @see MarkerAnnotation#getUnknownImageName(IMarker)
	 */
	protected String getUnknownImageName(IMarker marker) {
		return JavaPluginImages.IMG_OBJS_GHOST;
	}
	
	/**
	 * Initializes the annotation's icon representation and its drawing layer
	 * based upon the properties of the underlying marker.
	 */
	protected void initialize() {
		fQuickFixIconEnabled= PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_CORRECTION_INDICATION);
		fImageType= NO_IMAGE;
		IMarker marker= getMarker();
		
		if (MarkerUtilities.isMarkerType(marker, IBreakpoint.BREAKPOINT_MARKER)) {
			
			if (fPresentation == null) 
				fPresentation= DebugUITools.newDebugModelPresentation();

			setImage(null); // see bug 32469
			setLayer(4);
			fImageType= BREAKPOINT_IMAGE;					
			
			fType= AnnotationType.UNKNOWN;
			
		} else {
			
			fType= AnnotationType.UNKNOWN;
			if (marker.exists()) {
				try {
					
					if (marker.isSubtypeOf(IMarker.PROBLEM)) {
						int severity= marker.getAttribute(IMarker.SEVERITY, -1);
						switch (severity) {
							case IMarker.SEVERITY_ERROR:
								fType= AnnotationType.ERROR;
								break;
							case IMarker.SEVERITY_WARNING:
								fType= AnnotationType.WARNING;
								break;
						}
					} else if (marker.isSubtypeOf(IMarker.TASK))
						fType= AnnotationType.TASK;
					else if (marker.isSubtypeOf(SearchUI.SEARCH_MARKER)) 
						fType= AnnotationType.SEARCH;
					else if (marker.isSubtypeOf(IMarker.BOOKMARK))
						fType= AnnotationType.BOOKMARK;
						
				} catch(CoreException e) {
					JavaPlugin.log(e);
				}
			}
			super.initialize();
		}
	}
	
	private boolean mustShowQuickFixIcon() {
		return fQuickFixIconEnabled && JavaCorrectionProcessor.hasCorrections(this);
	}
	
	private Image getQuickFixImage() {
		if (fgQuickFixImage == null)
			fgQuickFixImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_PROBLEM);
		return fgQuickFixImage;
	}

	private Image getQuickFixErrorImage() {
		if (fgQuickFixErrorImage == null)
			fgQuickFixErrorImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_ERROR);
		return fgQuickFixErrorImage;
	}

	/*
	 * @see IJavaAnnotation#getMessage()
	 */
	public String getMessage() {
		IMarker marker= getMarker();
		if (marker == null || !marker.exists())
			return ""; //$NON-NLS-1$
		else
			return marker.getAttribute(IMarker.MESSAGE, ""); //$NON-NLS-1$
	}

	/*
	 * @see IJavaAnnotation#isTemporary()
	 */
	public boolean isTemporary() {
		return false;
	}
	
	/*
	 * @see IJavaAnnotation#getArguments()
	 */
	public String[] getArguments() {
		IMarker marker= getMarker();
		if (marker != null && marker.exists() && isProblem())
			return Util.getProblemArgumentsFromMarker(marker.getAttribute(IJavaModelMarker.ARGUMENTS, "")); //$NON-NLS-1$
		return null;
	}

	/*
	 * @see IJavaAnnotation#getId()
	 */
	public int getId() {
		IMarker marker= getMarker();
		if (marker == null  || !marker.exists())
			return -1;
		
		if (isProblem())
			return marker.getAttribute(IJavaModelMarker.ID, -1);
			
		if (fType == AnnotationType.TASK) {
			try {
				if (marker.isSubtypeOf(IJavaModelMarker.TASK_MARKER)) {
					return IProblem.Task;
				}
			} catch (CoreException e) {
				JavaPlugin.log(e); // should no happen, we test for marker.exists
			}
		}
		return -1;
	}
	
	/*
	 * @see IJavaAnnotation#isProblem()
	 */
	public boolean isProblem() {
		return fType == AnnotationType.WARNING || fType == AnnotationType.ERROR;
	}
	
	/*
	 * @see IJavaAnnotation#isRelevant()
	 */
	public boolean isRelevant() {
		return !fNotRelevant;
	}

	/**
	 * Overlays this annotation with the given javaAnnotation.
	 * 
	 * @param javaAnnotation annotation that is overlaid by this annotation
	 */
	public void setOverlay(IJavaAnnotation javaAnnotation) {
		if (fOverlay != null)
			fOverlay.removeOverlaid(this);
			
		fOverlay= javaAnnotation;
		fNotRelevant= (fNotRelevant || fOverlay != null);
		
		if (javaAnnotation != null)
			javaAnnotation.addOverlaid(this);
	}
	
	/*
	 * @see IJavaAnnotation#hasOverlay()
	 */
	public boolean hasOverlay() {
		return fOverlay != null;
	}
	
	/*
	 * @see MarkerAnnotation#getImage(Display)
	 */
	public Image getImage(Display display) {
		if (fImageType == BREAKPOINT_IMAGE) {
			Image result= super.getImage(display);
			if (result == null) {
				IMarker marker= getMarker();
				if (marker != null && marker.exists()) {
					result= fPresentation.getImage(getMarker());
					setImage(result);
				}
			}					
			return result;
		}

		int newImageType= NO_IMAGE;

		if (hasOverlay())
			newImageType= OVERLAY_IMAGE;
		else if (isRelevant()) {
			if (isProblem() && mustShowQuickFixIcon()) { // no light bulb for tasks
				if (fType == AnnotationType.ERROR)
					newImageType= QUICKFIX_ERROR_IMAGE;
				else
					newImageType= QUICKFIX_IMAGE; 
			} else
				newImageType= ORIGINAL_MARKER_IMAGE; 
		} else
			newImageType= GRAY_IMAGE;

		if (fImageType == newImageType && newImageType != OVERLAY_IMAGE)
			// Nothing changed - simply return the current image
			return super.getImage(display);

		Image newImage= null;
		switch (newImageType) {
			case ORIGINAL_MARKER_IMAGE:
				newImage= null;
				break;
			case OVERLAY_IMAGE:
				newImage= fOverlay.getImage(display);
				break;
			case QUICKFIX_IMAGE:
				newImage= getQuickFixImage();
				break;
			case QUICKFIX_ERROR_IMAGE:
				newImage= getQuickFixErrorImage();
				break;
			case GRAY_IMAGE:
				if (fImageType != ORIGINAL_MARKER_IMAGE)
					setImage(null);
				Image originalImage= super.getImage(display);
				if (originalImage != null) {
					ImageRegistry imageRegistry= getGrayMarkerImageRegistry(display);
					if (imageRegistry != null) {
						String key= Integer.toString(originalImage.hashCode());
						Image grayImage= imageRegistry.get(key);
						if (grayImage == null) {
							grayImage= new Image(display, originalImage, SWT.IMAGE_GRAY);
							imageRegistry.put(key, grayImage);
						}
						newImage= grayImage;
					}
				}
				break;
			default:
				Assert.isLegal(false);
		}

		fImageType= newImageType;
		setImage(newImage);
		return super.getImage(display);
	}
	
	private ImageRegistry getGrayMarkerImageRegistry(Display display) {
		if (fgGrayMarkersImageRegistry == null)
			fgGrayMarkersImageRegistry= new ImageRegistry(display);
		return fgGrayMarkersImageRegistry;
	}
	
	/*
	 * @see IJavaAnnotation#addOverlaid(IJavaAnnotation)
	 */
	public void addOverlaid(IJavaAnnotation annotation) {
		// not supported
	}

	/*
	 * @see IJavaAnnotation#removeOverlaid(IJavaAnnotation)
	 */
	public void removeOverlaid(IJavaAnnotation annotation) {
		// not supported
	}
	
	/*
	 * @see IJavaAnnotation#getOverlaidIterator()
	 */
	public Iterator getOverlaidIterator() {
		// not supported
		return null;
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation#getAnnotationType()
	 */
	public AnnotationType getAnnotationType() {
		return fType;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation#getCompilationUnit()
	 */
	public ICompilationUnit getCompilationUnit() {
		IJavaElement element= JavaCore.create(getMarker().getResource());
		if (element instanceof ICompilationUnit) {
			ICompilationUnit cu= (ICompilationUnit)element;
			ICompilationUnit workingCopy= EditorUtility.getWorkingCopy(cu);
			if (workingCopy != null) {
				return workingCopy;
			}
			return cu;
		}
		return null;
	}
}
