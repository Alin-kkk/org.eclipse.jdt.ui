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
package org.eclipse.jdt.ui.examples;

import java.util.HashMap;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.texteditor.MarkerUtilities;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;

import org.eclipse.jdt.testplugin.JavaTestPlugin;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;

public class AddTestMarkersAction extends Action implements IActionDelegate {

	private ICompilationUnit fCompilationUnit;

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	public void run(IAction action) {
		try {
			EditorUtility.openInEditor(fCompilationUnit);
			
			ICompilationUnit cu= JavaModelUtil.toWorkingCopy(fCompilationUnit);
			IScanner scanner= ToolFactory.createScanner(true, false, false, true);
			scanner.setSource(cu.getSource().toCharArray());
			
			int count= 0;
			int tok;
			do {
				tok= scanner.getNextToken();
				if (isComment(tok)) {
					int start= scanner.getCurrentTokenStartPosition();
					int end= scanner.getCurrentTokenEndPosition() + 1;
					int line= scanner.getLineNumber(start);
					createMarker(cu, line, start, end - start);
					count++;
				}
			} while (tok != ITerminalSymbols.TokenNameEOF);
			
			MessageDialog.openInformation(null, "Test Markers", count + " markers added");
		} catch (Exception e) {
			JavaTestPlugin.log(e);
		}
		
	}

	public static boolean isComment(int token) {
		return token == ITerminalSymbols.TokenNameCOMMENT_BLOCK || token == ITerminalSymbols.TokenNameCOMMENT_JAVADOC 
			|| token == ITerminalSymbols.TokenNameCOMMENT_LINE;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction, org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		fCompilationUnit= null;
		if (selection instanceof IStructuredSelection) {
			Object object= ((IStructuredSelection) selection).getFirstElement();
			if (object instanceof ICompilationUnit) {
				fCompilationUnit= (ICompilationUnit) object;
				
			}
		}
	}
	
	
	private void createMarker(ICompilationUnit cu, int line, int offset, int len) throws CoreException {
		ICompilationUnit original= JavaModelUtil.toOriginal(cu);

		HashMap map= new HashMap();
		map.put(IMarker.LOCATION, cu.getElementName());
		map.put(IMarker.MESSAGE, "Test marker");
		map.put(IMarker.SEVERITY, new Integer(IMarker.SEVERITY_ERROR));
		map.put(IMarker.LINE_NUMBER, new Integer(line));
		map.put(IMarker.CHAR_START, new Integer(offset));
		map.put(IMarker.CHAR_END, new Integer(offset + len));
	
		MarkerUtilities.createMarker(original.getResource(), map, "org.eclipse.jdt.ui.tests.testmarker");
	}	

}
