/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sebastian Davids: sdavids@gmx.de bug 37333 Failure Trace cannot 
 * 			navigate to non-public class in CU throwing Exception

 *******************************************************************************/
package org.eclipse.jdt.internal.junit.ui;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchResultCollector;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ISearchPattern;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Open a test in the Java editor and reveal a given line
 */
public class OpenEditorAtLineAction extends OpenEditorAction {

	//fix for bug 37333
	private class NonPublicClassInCUCollector implements IJavaSearchResultCollector {
		private IJavaElement fFound;

		public void accept(IResource resource, int start, int end, IJavaElement enclosingElement, int accuracy)
			throws JavaModelException {

			if ((enclosingElement instanceof IType) && (resource.getName().equals(fCUName)))
				fFound= enclosingElement;
		}

		public IProgressMonitor getProgressMonitor() {
			return new NullProgressMonitor();
		}

		public void aboutToStart() {}
		public void done() {}
	}
		
	private int fLineNumber;
	private String fCUName;
	
	/**
	 * Constructor for OpenEditorAtLineAction.
	 */
	public OpenEditorAtLineAction(TestRunnerViewPart testRunner, String cuName, String className, int line) {
		super(testRunner, className);
		WorkbenchHelp.setHelp(this, IJUnitHelpContextIds.OPENEDITORATLINE_ACTION);
		fLineNumber= line;
		fCUName= cuName;
	}
		
	protected void reveal(ITextEditor textEditor) {
		if (fLineNumber >= 0) {
			try {
				IDocument document= textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
				textEditor.selectAndReveal(document.getLineOffset(fLineNumber-1), document.getLineLength(fLineNumber-1));
			} catch (BadLocationException x) {
				// marker refers to invalid text position -> do nothing
			}
		}
	}
	
	protected IJavaElement findElement(IJavaProject project, String className) throws JavaModelException {
		IJavaElement element= project.findType(className);
		
		//fix for bug 37333
		if (element == null) {
			ISearchPattern pattern=	SearchEngine.createSearchPattern(className, IJavaSearchConstants.TYPE, IJavaSearchConstants.DECLARATIONS, true);
			IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaElement[] { project }, false);
			NonPublicClassInCUCollector collector= new NonPublicClassInCUCollector();

			SearchEngine searchEngine= new SearchEngine();
			searchEngine.search(JavaPlugin.getWorkspace(), pattern, scope, collector);
			
			element= collector.fFound;
		}
		
		return element;
	}

	public boolean isEnabled() {
		try {
			return getLaunchedProject().findType(getClassName()) != null;
		} catch (JavaModelException e) {
		}
		return false;
	}
}
