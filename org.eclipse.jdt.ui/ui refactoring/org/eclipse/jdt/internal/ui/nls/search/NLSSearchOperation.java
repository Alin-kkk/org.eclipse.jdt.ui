/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.nls.search;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.actions.WorkspaceModifyOperation;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.ui.nls.NLSImages;

public class NLSSearchOperation extends WorkspaceModifyOperation {
	
	private IWorkspace fWorkspace;
	private IJavaElement fElementPattern;
	private int fLimitTo;
	private String fStringPattern;
	private int fSearchFor;
	private IJavaSearchScope fScope;
	private String fScopeDescription;
	private NLSSearchResultCollector fCollector;
	
	protected NLSSearchOperation(
				IWorkspace workspace,
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				NLSSearchResultCollector collector) {
		fWorkspace= workspace;
		fLimitTo= limitTo;
		fScope= scope;
		fScopeDescription= scopeDescription;
		fCollector= collector;
		fCollector.setOperation(this);
	}
	
	public NLSSearchOperation(
				IWorkspace workspace,
				IJavaElement pattern,
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				NLSSearchResultCollector collector) {
		this(workspace, limitTo, scope, scopeDescription, collector);
		fElementPattern= pattern;
	}
	
	public NLSSearchOperation(
				IWorkspace workspace,
				String pattern,
				int searchFor, 
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				NLSSearchResultCollector collector) {
		this(workspace, limitTo, scope, scopeDescription, collector);
		fStringPattern= pattern;
		fSearchFor= searchFor;
	}

	protected void execute(IProgressMonitor monitor) throws CoreException {
		fCollector.setProgressMonitor(monitor);
		SearchEngine engine= new SearchEngine();
		if (fElementPattern != null)
			engine.search(fWorkspace, fElementPattern, fLimitTo, fScope, fCollector);
		else
			engine.search(fWorkspace, fStringPattern, fSearchFor, fLimitTo, fScope, fCollector);
	}

	String getSingularLabel() {
		String[] args= new String[] {getDescriptionPattern(), fScopeDescription}; //$NON-NLS-1$		
		return NLSSearchMessages.getFormattedString("SearchOperation.singularLabelPostfix", args); //$NON-NLS-1$
	}

	String getPluralLabelPattern() {
		String[] args= new String[] {getDescriptionPattern(), "{0}", fScopeDescription}; //$NON-NLS-1$		
		return NLSSearchMessages.getFormattedString("SearchOperation.pluralLabelPatternPostfix", args); //$NON-NLS-1$
	}
	
	private String getDescriptionPattern() {
		if (fElementPattern != null) {
			if (fLimitTo == IJavaSearchConstants.REFERENCES
			&& fElementPattern.getElementType() == IJavaElement.METHOD)
				return PrettySignature.getUnqualifiedMethodSignature((IMethod)fElementPattern);
			else
				return fElementPattern.getElementName();
		}
		else
			return fStringPattern;
	}
	
	ImageDescriptor getImageDescriptor() {
		return NLSImages.DESC_OBJS_SEARCH_REF;
	}
}