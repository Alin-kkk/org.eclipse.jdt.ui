/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.util;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.HelpEvent;
import org.eclipse.swt.events.HelpListener;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

public class JavaUIHelp {

	public static void setHelp(StructuredViewer viewer, String contextId) {
		JavaUIHelpListener listener= new JavaUIHelpListener(viewer, contextId);
		viewer.getControl().addHelpListener(listener);
	}

	public static void setHelp(JavaEditor editor, StyledText text, String contextId) {
		JavaUIHelpListener listener= new JavaUIHelpListener(editor, contextId);
		text.addHelpListener(listener);
	}

	private static class JavaUIHelpListener implements HelpListener {

		private StructuredViewer fViewer;
		private String fContextId;
		private JavaEditor fEditor;

		public JavaUIHelpListener(StructuredViewer viewer, String contextId) {
			fViewer= viewer;
			fContextId= contextId;
		}

		public JavaUIHelpListener(JavaEditor editor, String contextId) {
			fContextId= contextId;
			fEditor= editor;
		}

		/*
		* @see HelpListener#helpRequested(HelpEvent)
		* 
		*/
		public void helpRequested(HelpEvent e) {
			try {
				Object[] selected= null;
				if (fViewer != null) {
					ISelection selection= fViewer.getSelection();
					if (selection instanceof IStructuredSelection) {
						selected= ((IStructuredSelection) selection).toArray();
					}
				} else if (fEditor != null) {
					IJavaElement input= SelectionConverter.getInput(fEditor);
					if (ActionUtil.checkJavaElement(input)) {
						selected= SelectionConverter.codeResolve(fEditor);					
					}
				}
				JavadocHelpContext.displayHelp(fContextId, selected);
			} catch (CoreException x) {
				JavaPlugin.log(x);
			}
		}
	}

}