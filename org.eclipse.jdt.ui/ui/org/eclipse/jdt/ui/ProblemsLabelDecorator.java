/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.ui;

import java.util.Iterator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;

import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.MarkerAnnotation;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.IProblemChangedListener;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.ImageImageDescriptor;

/**
 * LabelDecorator that decorates an element's image with error and warning overlays that represent the severity
 * of markers attached to the element's underlying resource.
 * To see a problem decoration for a marker, the marker needs to be a subtype of IMarker.PROBLEM.
 * Updating of images on marker change is currently only performed on elements in Java projects.
 */
public class ProblemsLabelDecorator implements ILabelDecorator {
	
	/**
	 * LabelProviderChangedEvent sent out by the ProblemsLabelDecorator.
	 */
	public static class ProblemsLabelChangedEvent extends LabelProviderChangedEvent {

		private boolean fMarkerChange;

		public ProblemsLabelChangedEvent(IBaseLabelProvider source, IResource[] changedResource, boolean isMarkerChange) {
			super(source, changedResource);
			fMarkerChange= isMarkerChange;
		}
		
		/**
		 * Returns if this event origins from marker changes. If set to <code>false</code>, an annotation model change
		 * was the origin. Viewers not displaying working copies can ignore these events.
		 */
		public boolean isMarkerChange() {
			return fMarkerChange;
		}

	}

	private static final int ERRORTICK_WARNING= JavaElementImageDescriptor.WARNING;
	private static final int ERRORTICK_ERROR= JavaElementImageDescriptor.ERROR;	

	private ImageDescriptorRegistry fRegistry;
	private boolean fRegistryNeedsDispose= false;
	private IProblemChangedListener fProblemChangedListener;
	
	private ListenerList fListeners;

	/**
	 * Constructor for ProblemsLabelDecorator.
	 */
	public ProblemsLabelDecorator() {
		this(new ImageDescriptorRegistry());
		fRegistryNeedsDispose= true;
	}
	
	/**
	 * Internal constructor. Creates decorator with a shared image registry.
	 * @param registry The registry to use or <code>null</code> to use the Java plugin's
	 * image registry.
	 */	
	public ProblemsLabelDecorator(ImageDescriptorRegistry registry) {
		if (registry == null) {
			registry= JavaPlugin.getImageDescriptorRegistry();
		}
		fRegistry= registry;
		fProblemChangedListener= null;
	}

	/* (non-Javadoc)
	 * @see ILabelDecorator#decorateText(String, Object)
	 */
	public String decorateText(String text, Object element) {
		return text;
	}	

	/* (non-Javadoc)
	 * @see ILabelDecorator#decorateImage(Image, Object)
	 */
	public Image decorateImage(Image image, Object obj) {
		int adornmentFlags= computeAdornmentFlags(obj);
		if (adornmentFlags != 0) {
			ImageDescriptor baseImage= new ImageImageDescriptor(image);
			Rectangle bounds= image.getBounds();
			return fRegistry.get(new JavaElementImageDescriptor(baseImage, adornmentFlags, new Point(bounds.width, bounds.height)));
		}
		return image;
	}

	private int computeAdornmentFlags(Object obj) {
		try {
			if (obj instanceof IJavaElement) {
				IJavaElement element= (IJavaElement) obj;
				int type= element.getElementType();
				switch (type) {
					case IJavaElement.JAVA_PROJECT:
					case IJavaElement.PACKAGE_FRAGMENT_ROOT:
						return getErrorTicksFromMarkers(element.getResource(), IResource.DEPTH_INFINITE, null);
					case IJavaElement.PACKAGE_FRAGMENT:
					case IJavaElement.CLASS_FILE:
						return getErrorTicksFromMarkers(element.getResource(), IResource.DEPTH_ONE, null);
					case IJavaElement.COMPILATION_UNIT:
					case IJavaElement.PACKAGE_DECLARATION:
					case IJavaElement.IMPORT_DECLARATION:
					case IJavaElement.IMPORT_CONTAINER:
					case IJavaElement.TYPE:
					case IJavaElement.INITIALIZER:
					case IJavaElement.METHOD:
					case IJavaElement.FIELD:
						ICompilationUnit cu= (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						if (cu != null) {
							ISourceReference ref= (type == IJavaElement.COMPILATION_UNIT) ? null : (ISourceReference) element;
							// The assumption is that only source elements in compilation unit can have markers
							if (cu.isWorkingCopy()) {
								// working copy: look at annotation model
								return getErrorTicksFromWorkingCopy((ICompilationUnit) cu.getOriginalElement(), ref);
							}
							return getErrorTicksFromMarkers(cu.getResource(), IResource.DEPTH_ONE, ref);
						}
						break;
					default:
				}
			} else if (obj instanceof IResource) {
				return getErrorTicksFromMarkers((IResource) obj, IResource.DEPTH_INFINITE, null);
			}
		} catch (CoreException e) {
			JavaPlugin.logIgnoringNotPresentException(e);
		}
		return 0;
	}

	private int getErrorTicksFromMarkers(IResource res, int depth, ISourceReference sourceElement) throws CoreException {
		if (res == null || !res.isAccessible()) {
			return 0;
		}
		int info= 0;
		
		IMarker[] markers= res.findMarkers(IMarker.PROBLEM, true, depth);
		if (markers != null) {
			for (int i= 0; i < markers.length && (info != ERRORTICK_ERROR); i++) {
				IMarker curr= markers[i];
				if (sourceElement == null || isMarkerInRange(curr, sourceElement)) {
					int priority= curr.getAttribute(IMarker.SEVERITY, -1);
					if (priority == IMarker.SEVERITY_WARNING) {
						info= ERRORTICK_WARNING;
					} else if (priority == IMarker.SEVERITY_ERROR) {
						info= ERRORTICK_ERROR;
					}
				}
			}			
		}
		return info;
	}

	private boolean isMarkerInRange(IMarker marker, ISourceReference sourceElement) throws CoreException {
		if (marker.isSubtypeOf(IMarker.TEXT)) {
			ISourceRange range= sourceElement.getSourceRange();
			int pos= marker.getAttribute(IMarker.CHAR_START, -1);
			int offset= range.getOffset();
			return (offset <= pos && offset + range.getLength() > pos);
		}
		return false;
	}
	
	
	private int getErrorTicksFromWorkingCopy(ICompilationUnit original, ISourceReference sourceElement) throws CoreException {
		int info= 0;
		if (!original.exists()) {
			return 0;
		}
		
		FileEditorInput editorInput= new FileEditorInput((IFile) original.getCorrespondingResource());
		IAnnotationModel model= JavaPlugin.getDefault().getCompilationUnitDocumentProvider().getAnnotationModel(editorInput);
		if (model != null) {
			Iterator iter= model.getAnnotationIterator();
			while ((info != ERRORTICK_ERROR) && iter.hasNext()) {
				Annotation curr= (Annotation) iter.next();
				IMarker marker= isAnnotationInRange(model, curr, sourceElement);
				if (marker != null) {
					int priority= marker.getAttribute(IMarker.SEVERITY, -1);
					if (priority == IMarker.SEVERITY_WARNING) {
						info= ERRORTICK_WARNING;
					} else if (priority == IMarker.SEVERITY_ERROR) {
						info= ERRORTICK_ERROR;
					}
				}
			}
		}
		return info;
	}
			
	private IMarker isAnnotationInRange(IAnnotationModel model, Annotation annot, ISourceReference sourceElement) throws CoreException {
		if (annot instanceof MarkerAnnotation) {
			IMarker marker= ((MarkerAnnotation) annot).getMarker();
			if (marker.exists() && marker.isSubtypeOf(IMarker.PROBLEM)) {
				ISourceRange range= sourceElement.getSourceRange();
				Position pos= model.getPosition(annot);
				if (pos.overlapsWith(range.getOffset(), range.getLength())) {
					return marker;
				}
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see IBaseLabelProvider#dispose()
	 */
	public void dispose() {
		if (fProblemChangedListener != null) {
			JavaPlugin.getDefault().getProblemMarkerManager().removeListener(fProblemChangedListener);
			fProblemChangedListener= null;
		}
		if (fRegistryNeedsDispose) {
			fRegistry.dispose();
		}
	}

	/* (non-Javadoc)
	 * @see IBaseLabelProvider#isLabelProperty(Object, String)
	 */
	public boolean isLabelProperty(Object element, String property) {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see IBaseLabelProvider#addListener(ILabelProviderListener)
	 */
	public void addListener(ILabelProviderListener listener) {
		if (fListeners == null) {
			fListeners= new ListenerList();
		}
		fListeners.add(listener);
		if (fProblemChangedListener == null) {
			fProblemChangedListener= new IProblemChangedListener() {
				public void problemsChanged(IResource[] changedResources, boolean isMarkerChange) {
					fireProblemsChanged(changedResources, isMarkerChange);
				}
			};
			JavaPlugin.getDefault().getProblemMarkerManager().addListener(fProblemChangedListener);
		}
	}	

	/* (non-Javadoc)
	 * @see IBaseLabelProvider#removeListener(ILabelProviderListener)
	 */
	public void removeListener(ILabelProviderListener listener) {
		if (fListeners != null) {
			fListeners.remove(listener);
			if (fListeners.isEmpty() && fProblemChangedListener != null) {
				JavaPlugin.getDefault().getProblemMarkerManager().removeListener(fProblemChangedListener);
				fProblemChangedListener= null;
			}
		}
	}
	
	private void fireProblemsChanged(IResource[] changedResources, boolean isMarkerChange) {
		if (fListeners != null && !fListeners.isEmpty()) {
			LabelProviderChangedEvent event= new ProblemsLabelChangedEvent(this, changedResources, isMarkerChange);
			Object[] listeners= fListeners.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				((ILabelProviderListener) listeners[i]).labelProviderChanged(event);
			}
		}
	}	

}
