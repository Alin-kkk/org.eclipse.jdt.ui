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
package org.eclipse.jdt.internal.ui.text.link;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;

/**
 * A <code>Position</code> on a document that knows which document it is
 * registered with and a sequence number for tab stops.
 * 
 * @since 3.0
 */
class LinkedPosition extends Position {

	/** The document this position belongs to. */
	private IDocument fDocument;
	private int fSequenceNumber;
	
	/**
	 * Creates a new instance.
	 * 
	 * @param document the document
	 * @param offset the offset of the position
	 * @param length the length of the position
	 * @param sequence the iteration sequence rank
	 */
	public LinkedPosition(IDocument document, int offset, int length, int sequence) {
		super(offset, length);
		Assert.isNotNull(document);
		fDocument= document;
		fSequenceNumber= sequence;
	}

	/**
	 * @return Returns the document.
	 */
	public IDocument getDocument() {
		return fDocument;
	}
	
	/*
	 * @see org.eclipse.jface.text.Position#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (other instanceof LinkedPosition) {
			LinkedPosition p= (LinkedPosition) other;
			return p.offset == offset && p.length == length && p.fDocument == fDocument;
		}
		return false;
	}
	
	/**
	 * Returns whether this position overlaps with <code>position</code>.
	 * 
	 * @param position the position to check.
	 * @return <code>true</code> if this position overlaps with <code>position</code>, <code>false</code> otherwise
	 */
	public boolean overlapsWith(LinkedPosition position) {
		Assert.isNotNull(position);
		return position.getDocument() == fDocument && overlapsWith(position.getOffset(), position.getLength());
	}

	/**
	 * Returns whether this position includes <code>event</code>.
	 * 
	 * @param event the event to check.
	 * @return <code>true</code> if this position includes <code>event</code>, <code>false</code> otherwise
	 */
	public boolean includes(DocumentEvent event) {
		return includes(event.getDocument(), event.getOffset(), event.getLength());
	}

	/**
	 * Returns whether this position includes <code>position</code>.
	 * 
	 * @param position the position to check.
	 * @return <code>true</code> if this position includes <code>position</code>, <code>false</code> otherwise
	 */
	public boolean includes(LinkedPosition position) {
		return includes(position.getDocument(), position.getOffset(), position.getLength());
	}
	
	/**
	 * Overrides {@link Position#includes(int)} so every offset is considered included that lies in
	 * between the first and last offset of this position, and offsets that are right at the end
	 * of the position.
	 * 
	 * @param pOffset the offset to check
	 * @return <code>true</code> if <code>pOffset</code> is in [offset, offset + length]
	 */
	public boolean includes(int pOffset) {
		return this.offset <= pOffset && pOffset <= this.offset + this.length;
	}
	
	protected boolean includes(IDocument doc, int off, int len) {
		return doc == fDocument && off >= offset && len + off <= offset + length;
		
	}

	/**
	 * Returns the content of this position on the referenced document.
	 * 
	 * @return the content of the document at this position
	 * @throws BadLocationException if the position is not valid
	 */
	public String getContent() throws BadLocationException {
		return fDocument.get(offset, length);
	}

	/**
	 * Returns the sequence number of this position.
	 * 
	 * @return the sequence number of this position
	 */
	public int getSequenceNumber() {
		return fSequenceNumber;
	}

	/**
	 * Sets the sequence number of this position.
	 * 
	 * @param sequence the new sequence number
	 */
	public void setSequenceNumber(int sequence) {
		fSequenceNumber= sequence;
	}
	
	/*
	 * @see org.eclipse.jface.text.Position#hashCode()
	 */
	public int hashCode() {
		return fDocument.hashCode() | super.hashCode() | fSequenceNumber;
	}

}
