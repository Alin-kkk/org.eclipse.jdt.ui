/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;

import org.eclipse.ui.texteditor.IMarkerUpdater;
import org.eclipse.ui.texteditor.MarkerUtilities;

import org.eclipse.search.ui.SearchUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class JavaSearchMarkerUpdater implements IMarkerUpdater {
	
	
	public JavaSearchMarkerUpdater() {
		super();
	}
	
	/**
	 * @see IMarkerUpdater#updateMarker(IMarker, IDocument, Position)
	 */
	public boolean updateMarker(IMarker marker, IDocument document, Position position) {
		
		try {
			String id= (String) marker.getAttribute(IJavaSearchUIConstants.ATT_JE_HANDLE_ID);
			if (id != null) {
				IJavaElement j= JavaCore.create(id);
				if (j == null || !j.exists() || !j.isStructureKnown()) {
					IResource resource= marker.getResource();
					if (MarkerUtilities.getCharStart(marker) != -1 && MarkerUtilities.getCharEnd(marker) != -1 && resource instanceof IFile) {
						Object o= JavaCore.create(resource);
						if (o instanceof ICompilationUnit) {
							IJavaElement element= ((ICompilationUnit) o).getElementAt(position.getOffset());
							if (element != null) {
								marker.setAttribute(IJavaSearchUIConstants.ATT_JE_HANDLE_ID, element.getHandleIdentifier());
								return true;
							}
							else
								return false;
						}
					}
				} else {
					return true;
				}
			}
			else
				// no java search marker
				return true;
		} catch (CoreException ex) {
			ExceptionHandler.handle(ex, SearchMessages.getString("Search.Error.markerAttributeAccess.title"), SearchMessages.getString("Search.Error.markerAttributeAccess.message")); //$NON-NLS-2$ //$NON-NLS-1$
		}
		return false;
	}
	
	/**
	 * @see IMarkerUpdater#getAttribute()
	 */
	public String[] getAttribute() {
		return new String[] { IJavaSearchUIConstants.ATT_JE_HANDLE_ID };
	}

	/**
	 * @see IMarkerUpdater#getMarkerType()
	 */
	public String getMarkerType() {
		return SearchUI.SEARCH_MARKER;
	}
}