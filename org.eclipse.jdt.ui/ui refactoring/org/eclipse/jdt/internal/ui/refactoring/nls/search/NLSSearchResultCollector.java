/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.refactoring.nls.search;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.text.Position;

import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import org.eclipse.search.ui.ISearchResultView;
import org.eclipse.search.ui.SearchUI;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchResultCollector;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.search.GroupByKeyComputer;
import org.eclipse.jdt.internal.ui.search.IJavaSearchUIConstants;
import org.eclipse.jdt.internal.ui.util.StringMatcher;

class NLSSearchResultCollector implements IJavaSearchResultCollector {

	private static final String MATCH= NLSSearchMessages.getString("SearchResultCollector.match"); //$NON-NLS-1$
	private static final String MATCHES= NLSSearchMessages.getString("SearchResultCollector.matches"); //$NON-NLS-1$
	private static final String DONE= NLSSearchMessages.getString("SearchResultCollector.done"); //$NON-NLS-1$
	private static final String SEARCHING= NLSSearchMessages.getString("SearchResultCollector.searching"); //$NON-NLS-1$
	private static final StringMatcher fgGetClassNameMatcher= new StringMatcher("*.class.getName()*", false, false);  //$NON-NLS-1$

	private IProgressMonitor fMonitor;
	private ISearchResultView fView;
	private NLSSearchOperation fOperation;
	private IFile fPropertyFile;
	private Properties fProperties;
	private HashSet fUsedPropertyNames;
	private int fMatchCount= 0;
	private Integer[] fMessageFormatArgs= new Integer[1];

	public NLSSearchResultCollector(IFile propertyFile) {
		fPropertyFile= propertyFile;
	}

	/**
	 * @see IJavaSearchResultCollector#aboutToStart().
	 */
	public void aboutToStart() {
		fView= SearchUI.getSearchResultView();
		fMatchCount= 0;
		if (fView != null)
			fView.searchStarted(NLSSearchPage.EXTENSION_POINT_ID, fOperation.getSingularLabel(), fOperation.getPluralLabelPattern(), fOperation.getImageDescriptor(), null, new NLSSearchResultLabelProvider(), new org.eclipse.jdt.internal.ui.search.GotoMarkerAction(), new NLSGroupByKeyComputer(), fOperation);
		loadProperties(fPropertyFile);
		fUsedPropertyNames= new HashSet(fProperties.size());

		if (!getProgressMonitor().isCanceled())
			getProgressMonitor().subTask(SEARCHING);
	}
	
	/**
	 * @see IJavaSearchResultCollector#accept
	 */
	public void accept(IResource resource, int start, int end, IJavaElement enclosingElement, int accuracy) throws CoreException {
		// ignore matches in import declarations
		if (enclosingElement.getElementType() == IJavaElement.IMPORT_DECLARATION)
			return;
			
		if (enclosingElement.getElementType() == IJavaElement.FIELD) {
			IField field= (IField)enclosingElement;
			String source= field.getSource();
			if (source != null && fgGetClassNameMatcher.match(source))
				return;
		}
		/*
		 * Found reference to NLS Wrapper - now check if the key is there
		 */
		Position keyPosition= new Position(start, Math.max(0, end - start));
		String key= findKey(resource, start, end, enclosingElement, keyPosition);
		if (key != null && isKeyDefined(key))
			return;

		start= keyPosition.getOffset();
		end= start + keyPosition.getLength();

		IMarker marker= resource.createMarker(SearchUI.SEARCH_MARKER);
		HashMap attributes= new HashMap(4);
		JavaCore.addJavaElementMarkerAttributes(attributes, enclosingElement);
		attributes.put(IJavaSearchUIConstants.ATT_JE_HANDLE_ID, enclosingElement.getHandleIdentifier());
		attributes.put(IMarker.CHAR_START, new Integer(Math.max(start, 0)));
		attributes.put(IMarker.CHAR_END, new Integer(Math.max(end, 0)));
		if (enclosingElement instanceof IMember && ((IMember) enclosingElement).isBinary())
			attributes.put(IWorkbenchPage.EDITOR_ID_ATTR, JavaUI.ID_CF_EDITOR);
		else
			attributes.put(IWorkbenchPage.EDITOR_ID_ATTR, JavaUI.ID_CU_EDITOR);
		marker.setAttributes(attributes);

		fView.addMatch(enclosingElement.getElementName(), enclosingElement, resource, marker);

		fMatchCount++;
	
		if (!getProgressMonitor().isCanceled())
			getProgressMonitor().subTask(getFormattedMatchesString(fMatchCount));
	}

	/**
	 * @see IJavaSearchResultCollector#done().
	 */
	public void done() {

		markUnusedPropertyNames();

		if (!getProgressMonitor().isCanceled()) {
			String matchesString= getFormattedMatchesString(fMatchCount);
			getProgressMonitor().setTaskName(MessageFormat.format(DONE, new String[]{matchesString}));
		}

		if (fView != null)
			fView.searchFinished();

		// Cut no longer unused references because the collector might be re-used
		fView= null;
		fMonitor= null;
	}

	/**
	 * @see IJavaSearchResultCollector#getProgressMonitor().
	 */
	public IProgressMonitor getProgressMonitor() {
		return fMonitor;
	};

	void setProgressMonitor(IProgressMonitor pm) {
		fMonitor= pm;
	}

	void setOperation(NLSSearchOperation operation) {
		fOperation= operation;
	}

	/**
	 * Checks if the key is defined in the property file
	 */
	protected boolean isKeyDefined(String key) throws CoreException {
		// Parse error - don't check key
		if (key == null)
			return true;

		if (key != null && fProperties.getProperty(key) != null) {
			fUsedPropertyNames.add(key);
			return true;
		}
		return false;
	}
	
	/**
	 * Finds the key defined by the given match. The assumption is that
	 * the key is the first argument and it is a string i.e. quoted ("...").
	 * 
	 * @return a string denoting the key, null if no key can be found
	 */
	protected String findKey(IResource resource, int start, int end, IJavaElement enclosingElement, Position keyPosition) throws CoreException {
		if (enclosingElement instanceof ISourceReference) {
			int offset= ((ISourceReference) enclosingElement).getSourceRange().getOffset();
			int searchStart= end - offset;
			int matchStart= end;
			String source= ((ISourceReference) enclosingElement).getSource();
			if (searchStart >= source.length())
				return null;
			source= source.substring(searchStart);
			if (source.charAt(0) != '.' || Character.isWhitespace(source.charAt(0)))
				return null;
			int i= source.indexOf(')');
			int j= source.indexOf(',');
			int firstBraket= source.indexOf('(');
			int searchEnd= -1;
			if (i == -1)
				searchEnd= j;
			else if (j == -1)
				searchEnd= i;
			else
				searchEnd= Math.min(i, j);
			if (searchEnd == -1 || firstBraket == -1 || searchEnd <= firstBraket)
				return null;
			source= source.substring(firstBraket, searchEnd);
			matchStart += firstBraket;
			int firstQuote= source.indexOf('"');
			matchStart += firstQuote + 1;
			int secondQuote= source.lastIndexOf('"');

			if (secondQuote <= firstQuote)
				return null;

			keyPosition.setOffset(matchStart);
			keyPosition.setLength(Math.max(1, secondQuote - firstQuote - 1));
			return source.substring(firstQuote + 1, secondQuote);
		}
		return null;
	}

	protected void markUnusedPropertyNames() {
		Object key= new Object();
		for (Enumeration enum= fProperties.propertyNames(); enum.hasMoreElements();) {
			String propertyName= (String) enum.nextElement();
			if (!fUsedPropertyNames.contains(propertyName)) {
				IMarker marker= null;
				try {
					marker= fPropertyFile.createMarker(SearchUI.SEARCH_MARKER);
				} catch (CoreException ex) {
					// skip this marker
					// XXX: log error
					continue;
				}
				int start= findPropertyNameStartPosition(propertyName);
				HashMap attributes= new HashMap(4);
				attributes.put(IMarker.MESSAGE, NLSSearchMessages.getFormattedString("NLSSearchResultCollector.unusedKeys", fPropertyFile.getName())); //$NON-NLS-1$
				attributes.put(IMarker.CHAR_START, new Integer(Math.max(start, 0)));
				attributes.put(IMarker.CHAR_END, new Integer(Math.max(start + propertyName.length(), 0)));
				try {
					marker.setAttributes(attributes);
				} catch (CoreException ex) {
					// Skip this marker
					// XXX: log error
					continue;
				}
				fView.addMatch(fPropertyFile.getName(), key, fPropertyFile, marker);
			}
		}
	}
	
	/**
	 * Finds the start position in the property file. We assume that
	 * the key is the first match on a line.
	 * 
	 * @return	the start position of the property name in the file, -1 if not found
	 */
	protected int findPropertyNameStartPosition(String propertyName) {
		LineReader lineReader= null;
		try {
			lineReader= new LineReader(fPropertyFile.getContents());
		} catch (CoreException cex) {
			if (lineReader != null)
				try {
					lineReader.close();
				} catch (IOException ex) {
					// XXX: log error
				}
			return -1;
		}
		int start= 0;
		try {
			StringBuffer buf= new StringBuffer(80);
			int eols= lineReader.readLine(buf);
			int keyLength= propertyName.length();
			while (eols > 0) {
				String line= buf.toString();
				int i= line.indexOf(propertyName);
				int charPos= i + keyLength;
				char terminatorChar= 0;
				boolean hasNoValue= (charPos >= line.length());
				if (i > -1 && !hasNoValue)
					terminatorChar= line.charAt(charPos);
				if (line.trim().startsWith(propertyName) && (hasNoValue || Character.isWhitespace(terminatorChar) || terminatorChar == '=')) {
					start += line.indexOf(propertyName);
					eols= -1;
				} else {
					start += line.length() + eols;
					buf.setLength(0);
					eols= lineReader.readLine(buf);
				}
			}
		} catch (IOException ex) {
			// XXX: log error			
			return -1;
		} finally {
			try {
				lineReader.close();
			} catch (IOException ex) {
				// XXX: log error
			}
		}
		return start;
	}

	protected void loadProperties(IFile propertyFile) {
		Set duplicateKeys= new HashSet();
		fProperties= new Properties(duplicateKeys);
		InputStream stream;
		try {
			stream= new BufferedInputStream(propertyFile.getContents());
		} catch (CoreException ex) {
			fProperties= new Properties();
			return;
		}
		try {
			fProperties.load(stream);
		} catch (IOException ex) {
			fProperties= new Properties();
			return;
		} finally {
			try {
				stream.close();
			} catch (IOException ex) {
			}
			reportDuplicateKeys(duplicateKeys.iterator());
		}
	}

	protected void reportDuplicateKeys(Iterator duplicateKeys) {
		Object key= new Object();		
		while (duplicateKeys.hasNext()) {
			IMarker marker= null;
			try {
				marker= fPropertyFile.createMarker(SearchUI.SEARCH_MARKER);
			} catch (CoreException ex) {
				// skip this marker
				// XXX: log error
				continue;
			}
			String propertyName= (String)duplicateKeys.next();
			int start= findPropertyNameStartPosition(propertyName);
			HashMap attributes= new HashMap(4);
			attributes.put(IMarker.MESSAGE, NLSSearchMessages.getFormattedString("NLSSearchResultCollector.duplicateKeys", fPropertyFile.getName())); //$NON-NLS-1$
			attributes.put(IMarker.CHAR_START, new Integer(Math.max(start, 0)));
			attributes.put(IMarker.CHAR_END, new Integer(Math.max(start + propertyName.length(), 0)));
			attributes.put(IWorkbenchPage.EDITOR_ID_ATTR, JavaUI.ID_CU_EDITOR);
			try {
				marker.setAttributes(attributes);
			} catch (CoreException ex) {
				// Skip this marker
				// XXX: log error
				continue;
			}
			fView.addMatch(fPropertyFile.getName(), key, fPropertyFile, marker);
		}
	}

	private String getFormattedMatchesString(int count) {
		if (fMatchCount == 1)
			return MATCH;
		fMessageFormatArgs[0]= new Integer(count);
		return MessageFormat.format(MATCHES, fMessageFormatArgs);

	}
}