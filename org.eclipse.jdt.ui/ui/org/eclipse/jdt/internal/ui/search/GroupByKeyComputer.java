/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.search.ui.IGroupByKeyComputer;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class GroupByKeyComputer implements IGroupByKeyComputer {

	IJavaElement fLastJavaElement= null;;
	String fLastHandle= null;;

	public Object computeGroupByKey(IMarker marker) {
		if (marker == null)
			return null;
		
		IJavaElement jElement= getJavaElement(marker);
		if (jElement != null && jElement.exists()) {
			// no help from JavaModel to rename yet
			// return getJavaElement(marker);
			return fLastHandle;
		}
		return null;
	}

	private String getJavaElementHandleId(IMarker marker) {
		try {
			return (String)marker.getAttribute(IJavaSearchUIConstants.ATT_JE_HANDLE_ID);
		} catch (CoreException ex) {
			ExceptionHandler.handle(ex, SearchMessages.getString("Search.Error.markerAttributeAccess.title"), SearchMessages.getString("Search.Error.markerAttributeAccess.message")); //$NON-NLS-2$ //$NON-NLS-1$
			return null;
		}
	}
	
	private IJavaElement getJavaElement(IMarker marker) {
		String handle= getJavaElementHandleId(marker);
		if (handle == null) {
			fLastHandle= null;
			fLastJavaElement= null;
			return null;
		}
		
		if (!handle.equals(fLastHandle)) {
			fLastJavaElement= SearchUtil.getJavaElement(marker);
			fLastHandle= fLastJavaElement.getHandleIdentifier();
		}
		return fLastJavaElement;
	}
}