package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.ResourceBundle;

import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.TextEditorAction;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;


public class GotoErrorAction extends TextEditorAction {
	
	
	private boolean fForward;
	
	
	public GotoErrorAction(String prefix, boolean forward) {
		super(JavaEditorMessages.getResourceBundle(), prefix, null);
		fForward= forward;
	}
	
	/**
	 * @see Action#run()
	 */
	public void run() {
		CompilationUnitEditor e= (CompilationUnitEditor) getTextEditor();
		e.gotoError(fForward);
	}
	
	/**
	 * @see TextEditorAction#setEditor(ITextEditor)
	 */
	public void setEditor(ITextEditor editor) {
		if (editor instanceof CompilationUnitEditor) 
			super.setEditor(editor);
		update();
	}
	
	/**
	 * @see TextEditorAction#update()
	 */
	public void update() {
		ITextEditor editor= getTextEditor();
		if (editor instanceof JavaEditor && SelectionConverter.getInputAsCompilationUnit((JavaEditor)editor) != null)
			setEnabled(true);
		else
			setEnabled(false);
	}
}