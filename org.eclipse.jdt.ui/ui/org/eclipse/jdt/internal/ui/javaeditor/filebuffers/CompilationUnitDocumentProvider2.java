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

package org.eclipse.jdt.internal.ui.javaeditor.filebuffers;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultLineTracker;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ILineTracker;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModelEvent;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelListener;
import org.eclipse.jface.text.source.IAnnotationModelListenerExtension;

import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel;
import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.eclipse.ui.texteditor.ResourceMarkerAnnotationModel;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitAnnotationModelEvent;
import org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider;
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation;
import org.eclipse.jdt.internal.ui.javaeditor.ISavePolicy;
import org.eclipse.jdt.internal.ui.javaeditor.JavaMarkerAnnotation;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;
import org.eclipse.jdt.internal.ui.text.java.IProblemRequestorExtension;


public class CompilationUnitDocumentProvider2 extends TextFileDocumentProvider implements ICompilationUnitDocumentProvider {
		
		/**
		 * Bundle of all required informations to allow working copy management. 
		 */
		static protected class CompilationUnitInfo extends FileInfo {
			public ICompilationUnit fCopy;
		}
		
		/**
		 * Annotation representating an <code>IProblem</code>.
		 */
		static protected class ProblemAnnotation extends Annotation implements IJavaAnnotation {

			private static final String TASK_ANNOTATION_TYPE= "org.eclipse.ui.workbench.texteditor.task"; //$NON-NLS-1$
			private static final String ERROR_ANNOTATION_TYPE= "org.eclipse.ui.workbench.texteditor.error"; //$NON-NLS-1$
			private static final String WARNING_ANNOTATION_TYPE= "org.eclipse.ui.workbench.texteditor.warning"; //$NON-NLS-1$
			private static final String INFO_ANNOTATION_TYPE= "org.eclipse.ui.workbench.texteditor.info"; //$NON-NLS-1$

			private static Image fgQuickFixImage;
			private static Image fgQuickFixErrorImage;
			private static boolean fgQuickFixImagesInitialized= false;
			
			private ICompilationUnit fCompilationUnit;
			private List fOverlaids;
			private IProblem fProblem;
			private Image fImage;
			private boolean fQuickFixImagesInitialized= false;
			private String fType;
			
			
			public ProblemAnnotation(IProblem problem, ICompilationUnit cu) {
				
				fProblem= problem;
				fCompilationUnit= cu;
				setLayer(MarkerAnnotation.PROBLEM_LAYER + 1);
				
				if (IProblem.Task == fProblem.getID())
					fType= TASK_ANNOTATION_TYPE;
				else if (fProblem.isWarning())
					fType= WARNING_ANNOTATION_TYPE;
				else if (fProblem.isError())
					fType= ERROR_ANNOTATION_TYPE;
				else
					fType= INFO_ANNOTATION_TYPE;
			}
			
			private void initializeImages() {
				// http://bugs.eclipse.org/bugs/show_bug.cgi?id=18936
				if (!fQuickFixImagesInitialized) {
					if (isProblem() && indicateQuixFixableProblems() && JavaCorrectionProcessor.hasCorrections(this)) { // no light bulb for tasks
						if (!fgQuickFixImagesInitialized) {
							fgQuickFixImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_PROBLEM);
							fgQuickFixErrorImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_ERROR);
							fgQuickFixImagesInitialized= true;
						}
						if (fType == ERROR_ANNOTATION_TYPE)
							fImage= fgQuickFixErrorImage;
						else
							fImage= fgQuickFixImage;
					}
					fQuickFixImagesInitialized= true;
				}
			}

			private boolean indicateQuixFixableProblems() {
				return PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_CORRECTION_INDICATION);
			}
						
			/*
			 * @see Annotation#paint
			 */
			public void paint(GC gc, Canvas canvas, Rectangle r) {
				initializeImages();
				if (fImage != null)
					drawImage(fImage, gc, canvas, r, SWT.CENTER, SWT.TOP);
			}
			
			/*
			 * @see IJavaAnnotation#getImage(Display)
			 */
			public Image getImage(Display display) {
				initializeImages();
				return fImage;
			}
			
			/*
			 * @see IJavaAnnotation#getMessage()
			 */
			public String getMessage() {
				return fProblem.getMessage();
			}

			/*
			 * @see IJavaAnnotation#isTemporary()
			 */
			public boolean isTemporary() {
				return true;
			}
			
			/*
			 * @see IJavaAnnotation#getArguments()
			 */
			public String[] getArguments() {
				return isProblem() ? fProblem.getArguments() : null;
			}

			/*
			 * @see IJavaAnnotation#getId()
			 */
			public int getId() {
				return fProblem.getID();
			}

			/*
			 * @see IJavaAnnotation#isProblem()
			 */
			public boolean isProblem() {
				return  fType == WARNING_ANNOTATION_TYPE || fType == ERROR_ANNOTATION_TYPE;
			}
			
			/*
			 * @see IJavaAnnotation#isRelevant()
			 */
			public boolean isRelevant() {
				return true;
			}
			
			/*
			 * @see IJavaAnnotation#hasOverlay()
			 */
			public boolean hasOverlay() {
				return false;
			}
			
			/*
			 * @see IJavaAnnotation#addOverlaid(IJavaAnnotation)
			 */
			public void addOverlaid(IJavaAnnotation annotation) {
				if (fOverlaids == null)
					fOverlaids= new ArrayList(1);
				fOverlaids.add(annotation);
			}

			/*
			 * @see IJavaAnnotation#removeOverlaid(IJavaAnnotation)
			 */
			public void removeOverlaid(IJavaAnnotation annotation) {
				if (fOverlaids != null) {
					fOverlaids.remove(annotation);
					if (fOverlaids.size() == 0)
						fOverlaids= null;
				}
			}
			
			/*
			 * @see IJavaAnnotation#getOverlaidIterator()
			 */
			public Iterator getOverlaidIterator() {
				if (fOverlaids != null)
					return fOverlaids.iterator();
				return null;
			}
			
			public String getAnnotationType() {
				return fType;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation#getCompilationUnit()
			 */
			public ICompilationUnit getCompilationUnit() {
				return fCompilationUnit;
			}
		}
		
		/**
		 * Internal structure for mapping positions to some value. 
		 * The reason for this specific structure is that positions can
		 * change over time. Thus a lookup is based on value and not
		 * on hash value.
		 */
		protected static class ReverseMap {
			
			static class Entry {
				Position fPosition;
				Object fValue;
			}
			
			private List fList= new ArrayList(2);
			private int fAnchor= 0;
			
			public ReverseMap() {
			}
			
			public Object get(Position position) {
				
				Entry entry;
				
				// behind anchor
				int length= fList.size();
				for (int i= fAnchor; i < length; i++) {
					entry= (Entry) fList.get(i);
					if (entry.fPosition.equals(position)) {
						fAnchor= i;
						return entry.fValue;
					}
				}
				
				// before anchor
				for (int i= 0; i < fAnchor; i++) {
					entry= (Entry) fList.get(i);
					if (entry.fPosition.equals(position)) {
						fAnchor= i;
						return entry.fValue;
					}
				}
				
				return null;
			}
			
			private int getIndex(Position position) {
				Entry entry;
				int length= fList.size();
				for (int i= 0; i < length; i++) {
					entry= (Entry) fList.get(i);
					if (entry.fPosition.equals(position))
						return i;
				}
				return -1;
			}
			
			public void put(Position position,  Object value) {
				int index= getIndex(position);
				if (index == -1) {
					Entry entry= new Entry();
					entry.fPosition= position;
					entry.fValue= value;
					fList.add(entry);
				} else {
					Entry entry= (Entry) fList.get(index);
					entry.fValue= value;
				}
			}
			
			public void remove(Position position) {
				int index= getIndex(position);
				if (index > -1)
					fList.remove(index);
			}
			
			public void clear() {
				fList.clear();
			}
		}
		
		/**
		 * Annotation model dealing with java marker annotations and temporary problems.
		 * Also acts as problem requestor for its compilation unit. Initialiy inactive. Must explicitly be
		 * activated.
		 */
		protected static class CompilationUnitAnnotationModel extends ResourceMarkerAnnotationModel implements IProblemRequestor, IProblemRequestorExtension {
			
			private ICompilationUnit fCompilationUnit;
			private List fCollectedProblems;
			private List fGeneratedAnnotations;
			private IProgressMonitor fProgressMonitor;
			private boolean fIsActive= false;
			
			private ReverseMap fReverseMap= new ReverseMap();
			private List fPreviouslyOverlaid= null; 
			private List fCurrentlyOverlaid= new ArrayList();
			private CompilationUnitAnnotationModelEvent fCurrentEvent;

			public CompilationUnitAnnotationModel(IResource resource) {
				super(resource);
				fCurrentEvent= new CompilationUnitAnnotationModelEvent(this, getResource());
			}
			
			public void setCompilationUnit(ICompilationUnit unit)  {
				fCompilationUnit= unit;
			}
			
			protected MarkerAnnotation createMarkerAnnotation(IMarker marker) {
				return new JavaMarkerAnnotation(marker);
			}
			
			protected Position createPositionFromProblem(IProblem problem) {
				int start= problem.getSourceStart();
				if (start < 0)
					return null;
					
				int length= problem.getSourceEnd() - problem.getSourceStart() + 1;
				if (length < 0)
					return null;
					
				return new Position(start, length);
			}

			protected void update(IMarkerDelta[] markerDeltas) {
	
				super.update(markerDeltas);

				if (markerDeltas != null && markerDeltas.length > 0) {
					try {
						if (fCompilationUnit != null)
							fCompilationUnit.reconcile(true, null);
					} catch (JavaModelException ex) {
						if (!ex.isDoesNotExist())
							handleCoreException(ex, ex.getMessage());
					}
				}
			}
			
			/*
			 * @see IProblemRequestor#beginReporting()
			 */
			public void beginReporting() {
				if (fCompilationUnit != null && fCompilationUnit.getJavaProject().isOnClasspath(fCompilationUnit))
					fCollectedProblems= new ArrayList();
				else
					fCollectedProblems= null;
			}
			
			/*
			 * @see IProblemRequestor#acceptProblem(IProblem)
			 */
			public void acceptProblem(IProblem problem) {
				if (isActive())
					fCollectedProblems.add(problem);
			}

			/*
			 * @see IProblemRequestor#endReporting()
			 */
			public void endReporting() {
				if (!isActive())
					return;
					
				if (fProgressMonitor != null && fProgressMonitor.isCanceled())
					return;
					
				
				boolean isCanceled= false;
				boolean temporaryProblemsChanged= false;
				
				synchronized (fAnnotations) {
					
					fPreviouslyOverlaid= fCurrentlyOverlaid;
					fCurrentlyOverlaid= new ArrayList();

					if (fGeneratedAnnotations.size() > 0) {
						temporaryProblemsChanged= true;	
						removeAnnotations(fGeneratedAnnotations, false, true);
						fGeneratedAnnotations.clear();
					}
					
					if (fCollectedProblems != null && fCollectedProblems.size() > 0) {
												
						Iterator e= fCollectedProblems.iterator();
						while (e.hasNext()) {
							
							IProblem problem= (IProblem) e.next();
							
							if (fProgressMonitor != null && fProgressMonitor.isCanceled()) {
								isCanceled= true;
								break;
							}
								
							Position position= createPositionFromProblem(problem);
							if (position != null) {
								
								try {
									ProblemAnnotation annotation= new ProblemAnnotation(problem, fCompilationUnit);
									addAnnotation(annotation, position, false);
									overlayMarkers(position, annotation);								
									fGeneratedAnnotations.add(annotation);
								
									temporaryProblemsChanged= true;
								} catch (BadLocationException x) {
									// ignore invalid position
								}
							}
						}
						
						fCollectedProblems.clear();
					}
					
					removeMarkerOverlays(isCanceled);
					fPreviouslyOverlaid.clear();
					fPreviouslyOverlaid= null;
				}
					
				if (temporaryProblemsChanged)
					fireModelChanged();
			}
			
			private void removeMarkerOverlays(boolean isCanceled) {
				if (isCanceled) {
					fCurrentlyOverlaid.addAll(fPreviouslyOverlaid);
				} else if (fPreviouslyOverlaid != null) {
					Iterator e= fPreviouslyOverlaid.iterator();
					while (e.hasNext()) {
						JavaMarkerAnnotation annotation= (JavaMarkerAnnotation) e.next();
						annotation.setOverlay(null);
					}
				}			
			}
			
			/**
			 * Overlays value with problem annotation.
			 * @param problemAnnotation
			 */
			private void setOverlay(Object value, ProblemAnnotation problemAnnotation) {
				if (value instanceof  JavaMarkerAnnotation) {
					JavaMarkerAnnotation annotation= (JavaMarkerAnnotation) value;
					if (annotation.isProblem()) {
						annotation.setOverlay(problemAnnotation);
						fPreviouslyOverlaid.remove(annotation);
						fCurrentlyOverlaid.add(annotation);
					}
				}
			}
			
			private void  overlayMarkers(Position position, ProblemAnnotation problemAnnotation) {
				Object value= getAnnotations(position);
				if (value instanceof List) {
					List list= (List) value;
					for (Iterator e = list.iterator(); e.hasNext();)
						setOverlay(e.next(), problemAnnotation);
				} else {
					setOverlay(value, problemAnnotation);
				}
			}
			
			/**
			 * Tells this annotation model to collect temporary problems from now on.
			 */
			private void startCollectingProblems() {
				fCollectedProblems= new ArrayList();
				fGeneratedAnnotations= new ArrayList();  
			}
			
			/**
			 * Tells this annotation model to no longer collect temporary problems.
			 */
			private void stopCollectingProblems() {
				if (fGeneratedAnnotations != null) {
					removeAnnotations(fGeneratedAnnotations, true, true);
					fGeneratedAnnotations.clear();
				}
				fCollectedProblems= null;
				fGeneratedAnnotations= null;
			}
			
			/*
			 * @see AnnotationModel#fireModelChanged()
			 */
			protected void fireModelChanged() {
				fireModelChanged(fCurrentEvent);
				fCurrentEvent= new CompilationUnitAnnotationModelEvent(this, getResource());
			}
			
			/*
			 * @see IProblemRequestor#isActive()
			 */
			public boolean isActive() {
				return fIsActive && (fCollectedProblems != null);
			}
			
			/*
			 * @see IProblemRequestorExtension#setProgressMonitor(IProgressMonitor)
			 */
			public void setProgressMonitor(IProgressMonitor monitor) {
				fProgressMonitor= monitor;
			}
			
			/*
			 * @see IProblemRequestorExtension#setIsActive(boolean)
			 */
			public void setIsActive(boolean isActive) {
				if (fIsActive != isActive) {
					fIsActive= isActive;
					if (fIsActive)
						startCollectingProblems();
					else
						stopCollectingProblems();
				}
			}
			
			private Object getAnnotations(Position position) {
				return fReverseMap.get(position);
			}
						
			/*
			 * @see AnnotationModel#addAnnotation(Annotation, Position, boolean)
			 */
			protected void addAnnotation(Annotation annotation, Position position, boolean fireModelChanged) throws BadLocationException {
				super.addAnnotation(annotation, position, fireModelChanged);

				fCurrentEvent.annotationAdded(annotation);
				
				Object cached= fReverseMap.get(position);
				if (cached == null)
					fReverseMap.put(position, annotation);
				else if (cached instanceof List) {
					List list= (List) cached;
					list.add(annotation);
				} else if (cached instanceof Annotation) {
					List list= new ArrayList(2);
					list.add(cached);
					list.add(annotation);
					fReverseMap.put(position, list);
				}
			}
			
			/*
			 * @see AnnotationModel#removeAllAnnotations(boolean)
			 */
			protected void removeAllAnnotations(boolean fireModelChanged) {
				for (Iterator iter= getAnnotationIterator(); iter.hasNext();) {
					fCurrentEvent.annotationRemoved((Annotation) iter.next());
				}
				super.removeAllAnnotations(fireModelChanged);
				fReverseMap.clear();
			}
			
			/*
			 * @see AnnotationModel#removeAnnotation(Annotation, boolean)
			 */
			protected void removeAnnotation(Annotation annotation, boolean fireModelChanged) {
				fCurrentEvent.annotationRemoved(annotation);
				
				Position position= getPosition(annotation);
				Object cached= fReverseMap.get(position);
				if (cached instanceof List) {
					List list= (List) cached;
					list.remove(annotation);
					if (list.size() == 1) {
						fReverseMap.put(position, list.get(0));
						list.clear();
					}
				} else if (cached instanceof Annotation) {
					fReverseMap.remove(position);
				}
				super.removeAnnotation(annotation, fireModelChanged);
			}
		}
		
		
		protected static class GlobalAnnotationModelListener implements IAnnotationModelListener, IAnnotationModelListenerExtension {
			
			private ListenerList fListenerList;
			
			public GlobalAnnotationModelListener() {
				fListenerList= new ListenerList();
			}
			
			/**
			 * @see IAnnotationModelListener#modelChanged(IAnnotationModel)
			 */
			public void modelChanged(IAnnotationModel model) {
				Object[] listeners= fListenerList.getListeners();
				for (int i= 0; i < listeners.length; i++) {
					((IAnnotationModelListener) listeners[i]).modelChanged(model);
				}
			}

			/**
			 * @see IAnnotationModelListenerExtension#modelChanged(AnnotationModelEvent)
			 */
			public void modelChanged(AnnotationModelEvent event) {
				Object[] listeners= fListenerList.getListeners();
				for (int i= 0; i < listeners.length; i++) {
					Object curr= listeners[i];
					if (curr instanceof IAnnotationModelListenerExtension) {
						((IAnnotationModelListenerExtension) curr).modelChanged(event);
					}
				}
			}
			
			public void addListener(IAnnotationModelListener listener) {
				fListenerList.add(listener);
			}
			
			public void removeListener(IAnnotationModelListener listener) {
				fListenerList.remove(listener);
			}			
		}		
		
	/** Preference key for temporary problems */
	private final static String HANDLE_TEMPORARY_PROBLEMS= PreferenceConstants.EDITOR_EVALUTE_TEMPORARY_PROBLEMS;
	
	
	/** Indicates whether the save has been initialized by this provider */
	private boolean fIsAboutToSave= false;
	/** The save policy used by this provider */
	private ISavePolicy fSavePolicy;
	/** Internal property changed listener */
	private IPropertyChangeListener fPropertyListener;
	/** Annotation model listener added to all created CU annotation models */
	private GlobalAnnotationModelListener fGlobalAnnotationModelListener;	
	
	/**
	 * Constructor
	 */
	public CompilationUnitDocumentProvider2() {
		fPropertyListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (HANDLE_TEMPORARY_PROBLEMS.equals(event.getProperty()))
					enableHandlingTemporaryProblems();
			}
		};
		
		fGlobalAnnotationModelListener= new GlobalAnnotationModelListener();
		
		JavaPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(fPropertyListener);
	}
	
	/**
	 * Creates a compilation unit from the given file.
	 * 
	 * @param file the file from which to create the compilation unit
	 */
	protected ICompilationUnit createCompilationUnit(IFile file) {
		Object element= JavaCore.create(file);
		if (element instanceof ICompilationUnit)
			return (ICompilationUnit) element;
		return null;
	}
	
	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createEmptyFileInfo()
	 */
	protected FileInfo createEmptyFileInfo() {
		return new CompilationUnitInfo();
	}
	
	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createAnnotationModel(org.eclipse.core.resources.IFile)
	 */
	protected IAnnotationModel createAnnotationModel(IFile file) {
		return new CompilationUnitAnnotationModel(file);
	}
	
	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createFileInfo(java.lang.Object)
	 */
	protected FileInfo createFileInfo(Object element) throws CoreException {
		if (!(element instanceof IFileEditorInput))
			return null;
			
		IFileEditorInput input= (IFileEditorInput) element;
		ICompilationUnit original= createCompilationUnit(input.getFile());
		if (original == null)
			return null;
		
		FileInfo info= super.createFileInfo(element);
		if (!(info instanceof CompilationUnitInfo))
			return null;
			
		CompilationUnitInfo cuInfo= (CompilationUnitInfo) info;
			
		IProblemRequestor requestor= cuInfo.fModel instanceof IProblemRequestor ? (IProblemRequestor) cuInfo.fModel : null;

		if (JavaPlugin.USE_WORKING_COPY_OWNERS)  {
			original.becomeWorkingCopy(requestor, getProgressMonitor());
			cuInfo.fCopy= original;
		} else  {
			cuInfo.fCopy= (ICompilationUnit) original.getSharedWorkingCopy(getProgressMonitor(), JavaPlugin.getDefault().getBufferFactory(), requestor);
		}
		
		if (cuInfo.fModel instanceof CompilationUnitAnnotationModel)   {
			CompilationUnitAnnotationModel model= (CompilationUnitAnnotationModel) cuInfo.fModel;
			model.setCompilationUnit(cuInfo.fCopy);
		}
		cuInfo.fModel.addAnnotationModelListener(fGlobalAnnotationModelListener);
						
		if (requestor instanceof IProblemRequestorExtension) {
			IProblemRequestorExtension extension= (IProblemRequestorExtension) requestor;
			extension.setIsActive(isHandlingTemporaryProblems());
		}
		
		return cuInfo;
	}
	
	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#disposeFileInfo(java.lang.Object, org.eclipse.ui.editors.text.TextFileDocumentProvider.FileInfo)
	 */
	protected void disposeFileInfo(Object element, FileInfo info) {
		if (info instanceof CompilationUnitInfo) {
			CompilationUnitInfo cuInfo= (CompilationUnitInfo) info;
			
			if (JavaPlugin.USE_WORKING_COPY_OWNERS)  {
				
				try  {
					cuInfo.fCopy.discardWorkingCopy();
				} catch (JavaModelException x)  {
					handleCoreException(x, x.getMessage());
				}
			
			} else  {
				cuInfo.fCopy.destroy();
			}
			
			cuInfo.fModel.removeAnnotationModelListener(fGlobalAnnotationModelListener);
		}
		super.disposeFileInfo(element, info);
	}
	
	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#saveDocument(org.eclipse.core.runtime.IProgressMonitor, java.lang.Object, org.eclipse.jface.text.IDocument, boolean)
	 */
	public void saveDocument(IProgressMonitor monitor, Object element, IDocument ignore, boolean overwrite) throws CoreException {
		
		FileInfo fileInfo= getFileInfo(element);		
		if (fileInfo instanceof CompilationUnitInfo) {
			CompilationUnitInfo info= (CompilationUnitInfo) fileInfo;
			
			// update structure, assumes lock on info.fCopy
			info.fCopy.reconcile();
			
			ICompilationUnit original= (ICompilationUnit) info.fCopy.getOriginalElement();
			IResource resource= original.getResource();
			
			if (resource == null) {
				// underlying resource has been deleted, just recreate file, ignore the rest
				super.saveDocument(monitor, element, ignore, overwrite);
				return;
			}
				
			if (fSavePolicy != null)
				fSavePolicy.preSave(info.fCopy);
			
			try {
				
				fIsAboutToSave= true;
				
				// commit working copy
				if (JavaPlugin.USE_WORKING_COPY_OWNERS)
					info.fCopy.commitWorkingCopy(overwrite, monitor);
				else
					info.fCopy.commit(overwrite, monitor);
					
			} catch (CoreException x) {
				// inform about the failure
				fireElementStateChangeFailed(element);
				throw x;
			} catch (RuntimeException x) {
				// inform about the failure
				fireElementStateChangeFailed(element);
				throw x;
			} finally {
				fIsAboutToSave= false;
			}
			
			// If here, the dirty state of the editor will change to "not dirty".
			// Thus, the state changing flag will be reset.
			
			AbstractMarkerAnnotationModel model= (AbstractMarkerAnnotationModel) info.fModel;
			IDocument document= getDocument(element);
			model.updateMarkers(document);
				
			if (fSavePolicy != null) {
				ICompilationUnit unit= fSavePolicy.postSave(original);
				if (unit != null) {
					IResource r= unit.getResource();
					IMarker[] markers= r.findMarkers(IMarker.MARKER, true, IResource.DEPTH_ZERO);
					if (markers != null && markers.length > 0) {
						for (int i= 0; i < markers.length; i++)
							model.updateMarker(markers[i], document, null);
					}
				}
			}
				
			
		} else {
			super.saveDocument(monitor, element, ignore, overwrite);
		}		
	}

	/**
	 * Returns the preference whether handling temporary problems is enabled.
	 */
	protected boolean isHandlingTemporaryProblems() {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		return store.getBoolean(HANDLE_TEMPORARY_PROBLEMS);
	} 
	
	/**
	 * Switches the state of problem acceptance according to the value in the preference store.
	 */
	protected void enableHandlingTemporaryProblems() {
		boolean enable= isHandlingTemporaryProblems();
		for (Iterator iter= getFileInfosIterator(); iter.hasNext();) {
			FileInfo info= (FileInfo) iter.next();
			if (info.fModel instanceof IProblemRequestorExtension) {
				IProblemRequestorExtension  extension= (IProblemRequestorExtension) info.fModel;
				extension.setIsActive(enable);
			}
		}
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#setSavePolicy(org.eclipse.jdt.internal.ui.javaeditor.ISavePolicy)
	 */
	public void setSavePolicy(ISavePolicy savePolicy) {
		fSavePolicy= savePolicy;
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#addGlobalAnnotationModelListener(org.eclipse.jface.text.source.IAnnotationModelListener)
	 */
	public void addGlobalAnnotationModelListener(IAnnotationModelListener listener) {
		fGlobalAnnotationModelListener.addListener(listener);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#removeGlobalAnnotationModelListener(org.eclipse.jface.text.source.IAnnotationModelListener)
	 */
	public void removeGlobalAnnotationModelListener(IAnnotationModelListener listener) {
		fGlobalAnnotationModelListener.removeListener(listener);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#getWorkingCopy(java.lang.Object)
	 */
	public ICompilationUnit getWorkingCopy(Object element) {
		FileInfo fileInfo= getFileInfo(element);		
		if (fileInfo instanceof CompilationUnitInfo) {
			CompilationUnitInfo info= (CompilationUnitInfo) fileInfo;
			return info.fCopy;
		}
		return null;
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#shutdown()
	 */
	public void shutdown() {
		JavaPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(fPropertyListener);
		Iterator e= getConnectedElementsIterator();
		while (e.hasNext())
			disconnect(e.next());
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#saveDocumentContent(org.eclipse.core.runtime.IProgressMonitor, java.lang.Object, org.eclipse.jface.text.IDocument, boolean)
	 */
	public void saveDocumentContent(IProgressMonitor monitor, Object element, IDocument document, boolean overwrite) throws CoreException {
		if (!fIsAboutToSave)
			return;
		super.saveDocument(monitor, element, document, overwrite);
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#createLineTracker(java.lang.Object)
	 */
	public ILineTracker createLineTracker(Object element) {
		return new DefaultLineTracker();
	}
}
