/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.search;

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

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPluginImages;

public class JavaSearchOperation extends WorkspaceModifyOperation {
	
	private IWorkspace fWorkspace;
	private IJavaElement fElementPattern;
	private int fLimitTo;
	private String fStringPattern;
	private boolean fIsCaseSensitive;
	private int fSearchFor;
	private IJavaSearchScope fScope;
	private String fScopeDescription;
	private JavaSearchResultCollector fCollector;
	
	protected JavaSearchOperation(
				IWorkspace workspace,
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				JavaSearchResultCollector collector) {
		fWorkspace= workspace;
		fLimitTo= limitTo;
		fScope= scope;
		fScopeDescription= scopeDescription;
		fCollector= collector;
		fCollector.setOperation(this);
	}
	
	public JavaSearchOperation(
				IWorkspace workspace,
				IJavaElement pattern,
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				JavaSearchResultCollector collector) {
		this(workspace, limitTo, scope, scopeDescription, collector);
		fElementPattern= pattern;
	}
	
	public JavaSearchOperation(
				IWorkspace workspace,
				String pattern,
				boolean caseSensitive,
				int searchFor, 
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				JavaSearchResultCollector collector) {
		this(workspace, limitTo, scope, scopeDescription, collector);
		fStringPattern= pattern;
		fIsCaseSensitive= caseSensitive;
		fSearchFor= searchFor;
	}
	
	protected void execute(IProgressMonitor monitor) throws CoreException {
		fCollector.setProgressMonitor(monitor);
		
		// Also search working copies
		SearchEngine engine= new SearchEngine(JavaUI.getSharedWorkingCopiesOnClasspath());
		
		if (fElementPattern != null)
			engine.search(fWorkspace, fElementPattern, fLimitTo, fScope, fCollector);
		else
			engine.search(fWorkspace, SearchEngine.createSearchPattern(fStringPattern, fSearchFor, fLimitTo, fIsCaseSensitive), fScope, fCollector);
	}

	String getSingularLabel() {
		String desc= null;
		if (fElementPattern != null) {
			if (fLimitTo == IJavaSearchConstants.REFERENCES
			&& fElementPattern.getElementType() == IJavaElement.METHOD)
				desc= PrettySignature.getUnqualifiedMethodSignature((IMethod)fElementPattern);
			else
				desc= fElementPattern.getElementName();
			if ("".equals(desc) && fElementPattern.getElementType() == IJavaElement.PACKAGE_FRAGMENT) //$NON-NLS-1$
				desc= SearchMessages.getString("JavaSearchOperation.default_package"); //$NON-NLS-1$
		}
		else
			desc= fStringPattern;

		String[] args= new String[] {desc, fScopeDescription}; //$NON-NLS-1$
		switch (fLimitTo) {
			case IJavaSearchConstants.IMPLEMENTORS:
				return SearchMessages.getFormattedString("JavaSearchOperation.singularImplementorsPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.DECLARATIONS:
				return SearchMessages.getFormattedString("JavaSearchOperation.singularDeclarationsPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.REFERENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.singularReferencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.ALL_OCCURRENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.singularOccurrencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.READ_ACCESSES:
				return SearchMessages.getFormattedString("JavaSearchOperation.singularReadReferencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.WRITE_ACCESSES:
				return SearchMessages.getFormattedString("JavaSearchOperation.singularWriteReferencesPostfix", args); //$NON-NLS-1$
			default:
				return SearchMessages.getFormattedString("JavaSearchOperation.singularOccurrencesPostfix", args); //$NON-NLS-1$;
		}
	}

	String getPluralLabelPattern() {
		String desc= null;
		if (fElementPattern != null) {
			if (fLimitTo == IJavaSearchConstants.REFERENCES
			&& fElementPattern.getElementType() == IJavaElement.METHOD)
				desc= PrettySignature.getUnqualifiedMethodSignature((IMethod)fElementPattern);
			else
				desc= fElementPattern.getElementName();
			if ("".equals(desc) && fElementPattern.getElementType() == IJavaElement.PACKAGE_FRAGMENT) //$NON-NLS-1$
				desc= SearchMessages.getString("JavaSearchOperation.default_package"); //$NON-NLS-1$
		}
		else
			desc= fStringPattern;

		String[] args= new String[] {desc, "{0}", fScopeDescription}; //$NON-NLS-1$
		switch (fLimitTo) {
			case IJavaSearchConstants.IMPLEMENTORS:
				return SearchMessages.getFormattedString("JavaSearchOperation.pluralImplementorsPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.DECLARATIONS:
				return SearchMessages.getFormattedString("JavaSearchOperation.pluralDeclarationsPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.REFERENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.pluralReferencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.ALL_OCCURRENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.pluralOccurrencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.READ_ACCESSES:
				return SearchMessages.getFormattedString("JavaSearchOperation.pluralReadReferencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.WRITE_ACCESSES:
				return SearchMessages.getFormattedString("JavaSearchOperation.pluralWriteReferencesPostfix", args); //$NON-NLS-1$
			default:
				return SearchMessages.getFormattedString("JavaSearchOperation.pluralOccurrencesPostfix", args); //$NON-NLS-1$;
		}
	}
	
	ImageDescriptor getImageDescriptor() {
		if (fLimitTo == IJavaSearchConstants.IMPLEMENTORS || fLimitTo == IJavaSearchConstants.DECLARATIONS)
			return JavaPluginImages.DESC_OBJS_SEARCH_DECL;
		else
			return JavaPluginImages.DESC_OBJS_SEARCH_REF;
	}
}