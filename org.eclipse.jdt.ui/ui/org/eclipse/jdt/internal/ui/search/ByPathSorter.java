/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;

import org.eclipse.core.resources.IResource;

import org.eclipse.search.ui.ISearchResultViewEntry;

/**
 * Sorts the search result viewer by the resource name.
 */
public class ByPathSorter extends ViewerSorter {

	/*
	 * Overrides method from ViewerSorter
	 */
	public int compare(Viewer viewer, Object e1, Object e2) {
		String name1= null;
		String name2= null;
		if (e1 instanceof ISearchResultViewEntry)
			name1= ((ISearchResultViewEntry)e1).getResource().getFullPath().toString();
		if (e2 instanceof ISearchResultViewEntry)
			name2= ((ISearchResultViewEntry)e2).getResource().getFullPath().toString();
		if (name1 == null)
			name1= ""; //$NON-NLS-1$
		if (name2 == null)
			name2= ""; //$NON-NLS-1$
		return name1.toLowerCase().compareTo(name2.toLowerCase());
	}

	/*
	 * Overrides method from ViewerSorter
	 */
	public boolean isSorterProperty(Object element, String property) {
		return true;
	}
}
	