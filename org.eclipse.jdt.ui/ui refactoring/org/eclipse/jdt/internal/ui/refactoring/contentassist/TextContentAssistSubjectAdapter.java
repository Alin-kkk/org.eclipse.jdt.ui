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

package org.eclipse.jdt.internal.ui.refactoring.contentassist;

import java.util.HashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import org.eclipse.jdt.internal.corext.Assert;

/**
 * Adapts a <code>Text</code> to an <code>IContentAssistSubject</code>.
 * 
 * @see org.eclipse.swt.widgets.Text
 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject
 * @since 3.0
 */
public class TextContentAssistSubjectAdapter extends ControlContentAssistSubjectAdapter {

	private class InternalDocument extends Document {
		
		private ModifyListener fModifyListener;
		
		private InternalDocument() {
			super(fText.getText());
			fModifyListener= new ModifyListener() {
				/*
				 * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
				 */
				public void modifyText(ModifyEvent e) {
					set(fText.getText());
				}
			};
			fText.addModifyListener(fModifyListener);
		}
		
		/*
		 * @see org.eclipse.jface.text.AbstractDocument#replace(int, int, java.lang.String)
		 */
		public void replace(int pos, int length, String text) throws BadLocationException {
			super.replace(pos, length, text);
			fText.removeModifyListener(fModifyListener);
			fText.setText(get());
			fText.addModifyListener(fModifyListener);
		}
	}
	
	/**
	 * Creates a content assist subject adapter for the given text.
	 * 
	 * @param text the text to adapt
	 */
	public TextContentAssistSubjectAdapter(Text text) {
		super();
		Assert.isNotNull(text);
		fText= text;
	}

	/**
	 * The text.
	 */
	private Text fText;
	private HashMap fModifyListeners;
	
	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#getControl()
	 */
	public Control getControl() {
		return fText;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#getLineHeight()
	 */
	public int getLineHeight() {
		return fText.getLineHeight();
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#getCaretOffset()
	 */
	public int getCaretOffset() {
		return fText.getCaretPosition();
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#getLocationAtOffset(int)
	 */
	public Point getLocationAtOffset(int offset) {
//		return fText.getCaretLocation();

		//XXX: workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=50256
		String comboString= fText.getText();
		GC gc = new GC(fText);
		gc.setFont(fText.getFont());
		Point extent= gc.textExtent(comboString.substring(0, Math.min(offset, comboString.length())));
		int spaceWidth= gc.textExtent(" ").x; //$NON-NLS-1$
		gc.dispose();
		/*
		 * FIXME: the two space widths below is a workaround for bug 44072
		 */
		int x= 2 * spaceWidth + fText.getClientArea().x + fText.getBorderWidth() + extent.x;
		return new Point(x, 0);
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#getSelectionRange()
	 */
	public Point getWidgetSelectionRange() {
		return new Point(fText.getSelection().x, Math.abs(fText.getSelection().y - fText.getSelection().x));
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#getSelectedRange()
	 */
	public Point getSelectedRange() {
		return new Point(fText.getSelection().x, Math.abs(fText.getSelection().y - fText.getSelection().x));
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#getDocument()
	 */
	public IDocument getDocument() {
		IDocument document= (IDocument)fText.getData("document"); //$NON-NLS-1$
		if (document == null) {
			document= new InternalDocument() ;
			fText.setData("document", document); //$NON-NLS-1$
		}
		return document;
	}
	
	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#setSelectedRange(int, int)
	 */
	public void setSelectedRange(int i, int j) {
		fText.setSelection(new Point(i, i+j));
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistSubject#revealRange(int, int)
	 */
	public void revealRange(int i, int j) {
		// XXX: this should be improved
		fText.setSelection(new Point(i, i+j));
	}

	public boolean addSelectionListener(final SelectionListener selectionListener) {
		fText.addSelectionListener(selectionListener);
		Listener listener= new Listener() {
			/*
			 * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
			 */
			public void handleEvent(Event e) {
				selectionListener.widgetSelected(new SelectionEvent(e));
	
			}
		};
		fText.addListener(SWT.Modify, listener); 
		fModifyListeners.put(selectionListener, listener);
		return true; //TODO: why return true?
	}

	public void removeSelectionListener(SelectionListener selectionListener) {
		fText.removeSelectionListener(selectionListener);
		Object listener= fModifyListeners.get(selectionListener);
		if (listener instanceof Listener)
			fText.removeListener(SWT.Modify, (Listener)listener);
	}
}
