/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.text.java;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;

import org.eclipse.jdt.internal.corext.template.TemplateMessages;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.link.LinkedPositionManager;
import org.eclipse.jdt.internal.ui.text.link.LinkedPositionUI;

/**
 * An experimental proposal.
 */
public class ExperimentalProposal extends JavaCompletionProposal {

	private int[] fPositionOffsets;
	private int[] fPositionLengths;
	private ITextViewer fViewer;

	private IRegion fSelectedRegion; // initialized by apply()
		
	/**
	 * Creates a template proposal with a template and its context.
	 * @param template  the template
	 * @param context   the context in which the template was requested.
	 * @param image     the icon of the proposal.
	 */		
	public ExperimentalProposal(String replacementString, int replacementOffset, int replacementLength, Image image,
	    String displayString, int[] positionOffsets, int[] positionLengths, ITextViewer viewer)
	{
		super(replacementString, replacementOffset, replacementLength, image, displayString);		

		fPositionOffsets= positionOffsets;
		fPositionLengths= positionLengths;
		fViewer= viewer;
	}

	/*
	 * @see ICompletionProposalExtension#apply(IDocument, char)
	 */
	public void apply(IDocument document, char trigger, int offset) {
		super.apply(document, trigger, offset);

		int replacementOffset= getReplacementOffset();
		int replacementLength= getReplacementLength();
		String replacementString= getReplacementString();
		
		try {
			LinkedPositionManager manager= new LinkedPositionManager(document);
			for (int i= 0; i != fPositionOffsets.length; i++)
				manager.addPosition(replacementOffset + fPositionOffsets[i], fPositionLengths[i]);
			
			LinkedPositionUI editor= new LinkedPositionUI(fViewer, manager);
			editor.setFinalCaretOffset(replacementOffset + replacementString.length());
			editor.enter();

			fSelectedRegion= editor.getSelectedRegion();

		} catch (BadLocationException e) {
			JavaPlugin.log(e);	
			openErrorDialog(e);
		}		
	}
	
	/**
	 * @see ICompletionProposal#getSelection(IDocument)
	 */
	public Point getSelection(IDocument document) {
		if (fSelectedRegion == null)
			return new Point(getReplacementOffset(), 0);

		return new Point(fSelectedRegion.getOffset(), fSelectedRegion.getLength());
	}

	private void openErrorDialog(BadLocationException e) {
		Shell shell= fViewer.getTextWidget().getShell();
		MessageDialog.openError(shell, TemplateMessages.getString("TemplateEvaluator.error.title"), e.getMessage()); //$NON-NLS-1$
	}	

}