/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchResultCollector;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ISearchPattern;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.JavaUI;

/**
 * Convenience wrapper for <code>SearchEngine</code> - performs searching and sorts the results.
 */
public class RefactoringSearchEngine {

	//no instances
	private RefactoringSearchEngine(){
	}
	
	public static ICompilationUnit[] findAffectedCompilationUnits(final IProgressMonitor pm, IJavaSearchScope scope, ISearchPattern pattern) throws JavaModelException {
		final Set matches= new HashSet(5);
		IJavaSearchResultCollector collector = new IJavaSearchResultCollector() {
			private IResource fLastMatch;
			public void aboutToStart() {};
			public void accept(IResource resource, int start, int end, IJavaElement enclosingElement, int accuracy) throws CoreException {
				if (fLastMatch != resource) {
					matches.add(resource);	
					fLastMatch= resource;
				}
			}
			public void done() {};
			public IProgressMonitor getProgressMonitor() {
				return pm;
			};
		};
		new SearchEngine().search(ResourcesPlugin.getWorkspace(), pattern, scope, collector);
		// XXX: This is a layer breaker - should not access jdt.ui
		IWorkingCopy[] workingCopies= JavaUI.getSharedWorkingCopies();
		List result= new ArrayList(matches.size());
		for (Iterator iter= matches.iterator(); iter.hasNext(); ) {
			IResource resource= (IResource)iter.next();
			IJavaElement element= JavaCore.create(resource);
			if (element instanceof ICompilationUnit) {
				ICompilationUnit original= (ICompilationUnit)element;
				IWorkingCopy wcopy= getWorkingCopy(original, workingCopies);
				if (wcopy != null && wcopy instanceof ICompilationUnit)
					result.add(wcopy);
				else
					result.add(original);
			}
		}
		return (ICompilationUnit[])result.toArray(new ICompilationUnit[result.size()]);
	}
	
	private static IWorkingCopy getWorkingCopy(ICompilationUnit unit, IWorkingCopy[] workingCopies) {
		for (int i= 0; i < workingCopies.length; i++) {
			IWorkingCopy wcopy= workingCopies[i];
			if (unit.equals(wcopy.getOriginalElement()))
				return wcopy;
		}
		return null;
	}
		
	/**
	 * Performs searching for a given <code>SearchPattern</code>.
	 * Returns SearchResultGroup[] 
	 * In each of SearchResultGroups all SearchResults are
	 * sorted backwards by <code>SearchResult#getStart()</code> 
	 * @see SearchResult
	 */			
	public static SearchResultGroup[] search(IProgressMonitor pm, IJavaSearchScope scope, ISearchPattern pattern) throws JavaModelException {
		return search(pm, scope, pattern, null);
	}
	
	public static SearchResultGroup[] search(IProgressMonitor pm, IJavaSearchScope scope, ISearchPattern pattern, ICompilationUnit[] workingCopies) throws JavaModelException {
		SearchResultCollector collector= new SearchResultCollector(pm);
		search(pm, scope, pattern, collector, workingCopies);	
		Map grouped= groupByResource(collector.getResults());
		
		SearchResultGroup[] result= new SearchResultGroup[grouped.keySet().size()];
		int i= 0;
		for (Iterator iter= grouped.keySet().iterator(); iter.hasNext();) {
			IResource resource= (IResource)iter.next();
			List searchResults= (List)grouped.get(resource);
			result[i]= new SearchResultGroup(resource, createSearchResultArray(searchResults));
			i++;
		}
		return result;
	}
	
	private static SearchResult[] createSearchResultArray(List searchResults){
		return (SearchResult[])searchResults.toArray(new SearchResult[searchResults.size()]);
	}
	
	private static Map groupByResource(List searchResults){
		Map grouped= new HashMap(); //IResource -> List of SearchResults
		for (Iterator iter= searchResults.iterator(); iter.hasNext();) {
			SearchResult searchResult= (SearchResult) iter.next();
			if (! grouped.containsKey(searchResult.getResource()))
				grouped.put(searchResult.getResource(), new ArrayList(1));
			((List)grouped.get(searchResult.getResource())).add(searchResult);
		}
		return grouped;
	}
	
	private static void search(IProgressMonitor pm, IJavaSearchScope scope, ISearchPattern pattern, IJavaSearchResultCollector collector, ICompilationUnit[] workingCopies) throws JavaModelException {
		if (pattern == null)
			return;
		Assert.isNotNull(scope, "scope"); //$NON-NLS-1$
		createSearchEngine(workingCopies).search(ResourcesPlugin.getWorkspace(), pattern, scope, collector);
	}
	
	private static SearchEngine createSearchEngine(ICompilationUnit[] workingCopies){
		if (workingCopies == null)
			return new SearchEngine();
		else 	
			return  new SearchEngine(workingCopies);
	}
}
