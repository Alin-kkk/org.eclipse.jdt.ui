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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;

/**
 * A group of positions in multiple documents that are simultaneously modified -
 * if one gets edited, all other positions in a <code>PositionGroup</code>
 * are edited the same way. All linked positions in a group have the same
 * content.
 * <p>
 * Normally, new positions are given a tab stop weight which can be used by
 * clients, e.g. the UI. If no weight is given, a position will not be visited.
 * If no weights are used at all, the first position in a document is taken as
 * the only stop as to comply with the behaviour of the old linked position
 * infrastructure.
 * </p>
 * 
 * @since 3.0
 */
public class LinkedPositionGroup {
	
	/** Sequence constant declaring that a position should not be stopped by. */
	public static final int NO_STOP= -1;

	/* members */

	/** The linked positions of this group. */
	private final List fPositions= new LinkedList();
	/** The environment. */
	private LinkedEnvironment fEnvironment;

	/*
	 * iteration variables, set to communicate state between isLegalEvent and
	 * handleEvent
	 */
	/** The position including the most recent <code>DocumentEvent</code>. */
	private LinkedPosition fLastPosition;
	/** The offset of <code>fLastPosition</code>. */
	private int fLastPositionOffset;
	/**
	 * <code>true</code> if there are custom iteration weights. For backward
	 * compatibility.
	 */
	private boolean fHasCustomIteration= false;
	
	/**
	 * Creates a new position group.
	 */
	public LinkedPositionGroup() {
		fEnvironment= null;
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param offset the offset of the position
	 * @param length the length of the position
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument document, int offset, int length) throws BadLocationException {
		createPosition(document, offset, length, LinkedPositionGroup.NO_STOP);
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param offset the offset of the position
	 * @param length the length of the position
	 * @param sequence the tab stop number of the position
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument document, int offset, int length, int sequence) throws BadLocationException {
		addPosition(new LinkedPosition(document, offset, length, sequence));
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param region a region describing the new position (it is not
	 *        stored, only its values are used)
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument doc, IRegion region) throws BadLocationException {
		createPosition(doc, region, LinkedPositionGroup.NO_STOP);
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param region a region describing the new position (it is not
	 *        stored, only its values are used)
	 * @param sequence the tab stop number of the position
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument doc, IRegion region, int sequence) throws BadLocationException {
		createPosition(doc, region.getOffset(), region.getLength(), sequence);
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param region a region describing the new position (it is not
	 *        stored, only its values are used)
	 * @param proposals the completion proposals to be shown when a position of
	 *        this type comes up
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument doc, IRegion region, ICompletionProposal[] proposals) throws BadLocationException {
		createPosition(doc, region, LinkedPositionGroup.NO_STOP, proposals);
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param offset the offset of the position
	 * @param length the length of the position
	 * @param proposals the completion proposals to be shown when a position of
	 *        this type comes up
	 * @param sequence the tab stop number of the position
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument doc, int offset, int length, int sequence, ICompletionProposal[] proposals) throws BadLocationException {
		addPosition(new ProposalPosition(doc, offset, length, sequence, proposals));
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param offset the offset of the position
	 * @param length the length of the position
	 * @param proposals the completion proposals to be shown when a position of
	 *        this type comes up
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument doc, int offset, int length, ICompletionProposal[] proposals) throws BadLocationException {
		createPosition(doc, offset, length, LinkedPositionGroup.NO_STOP, proposals);
	}

	/**
	 * Adds a position to this group. If the position overlaps with another in
	 * this group, or if the group is already part of an environment, an
	 * exception is thrown.
	 * 
	 * @param document the document of the position
	 * @param region a region describing the new position (it is not
	 *        stored, only its values are used)
	 * @param proposals the completion proposals to be shown when a position of
	 *        this type comes up
	 * @param sequence the tab stop number of the position
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	public void createPosition(IDocument doc, IRegion region, int sequence, ICompletionProposal[] proposals) throws BadLocationException {
		createPosition(doc, region.getOffset(), region.getLength(), sequence, proposals);
	}

	/**
	 * Not implemented yet.
	 */
	public void createPosition(IDocument doc, IRegion region, int sequence, IContentAssistProcessor processor) throws BadLocationException {
		// TODO add overloaded versions with some lazy computation interface
		throw new UnsupportedOperationException();
	}

	/**
	 * Implementation of all the <code>addPosition</code> methods. Enforces
	 * constraints and sets the custom iteration flag. If the position is
	 * already in this group, nothing happens.
	 * 
	 * @param position the position to add
	 * @throws BadLocationException if the position is invalid or conflicts
	 *         with other positions in the group
	 * @throws IllegalStateException if the group has alreay been added to an
	 *         environment
	 */
	private void addPosition(LinkedPosition position) throws BadLocationException {
		Assert.isNotNull(position);
		// don't add positions after it is installed.
		if (fEnvironment != null)
			throw new IllegalStateException("cannot add positions after the group is added to an environment"); //$NON-NLS-1$

		if (!fPositions.contains(position)) {
			enforceDisjoint(position);
			enforceEqualContent(position);
			fPositions.add(position);
			fHasCustomIteration |= position.getSequenceNumber() != LinkedPositionGroup.NO_STOP;
		} else
			return; // nothing happens
	}

	/**
	 * Adds <code>position</code> to the document and ensures management of
	 * its document by the environment.
	 * 
	 * @param position the position to register
	 * @throws BadLocationException if the position overlaps with an other, or
	 *         is illegal on the document
	 */
	private void register(LinkedPosition position) throws BadLocationException {
		Assert.isNotNull(fEnvironment);
		Assert.isNotNull(position);

		fEnvironment.register(position);
	}

	/**
	 * Enforces the invariant that all positions must contain the same string.
	 * 
	 * @param position the position to check
	 * @throws BadLocationException if the equal content check fails
	 */
	private void enforceEqualContent(LinkedPosition position) throws BadLocationException {
		if (fPositions.size() > 0) {
			String groupContent= ((LinkedPosition) fPositions.get(0)).getContent();
			String positionContent= position.getContent();
			if (!groupContent.equals(positionContent))
				throw new BadLocationException();
		}
	}

	/**
	 * Enforces the invariant that all positions must be disjoint.
	 * 
	 * @param position the position to check
	 * @throws BadLocationException if the disjointness check fails
	 */
	private void enforceDisjoint(LinkedPosition position) throws BadLocationException {
		for (Iterator it= fPositions.iterator(); it.hasNext(); ) {
			LinkedPosition p= (LinkedPosition) it.next();
			if (p.overlapsWith(position))
				throw new BadLocationException();
		}
	}

	/**
	 * Enforces the disjointness for another group
	 * 
	 * @param group the group to check
	 * @throws BadLocationException if the disjointness check fails
	 */
	void enforceDisjoint(LinkedPositionGroup group) throws BadLocationException {
		Assert.isNotNull(group);
		for (Iterator it= group.fPositions.iterator(); it.hasNext(); ) {
			LinkedPosition p= (LinkedPosition) it.next();
			enforceDisjoint(p);
		}
	}

	/**
	 * Checks whether <code>event</code> fits in any of the positions of this
	 * group.
	 * 
	 * @param event the document event to check
	 * @return <code>true</code> if <code>event</code> fits in any position
	 */
	boolean isLegalEvent(DocumentEvent event) {
		for (Iterator it= fPositions.iterator(); it.hasNext(); ) {
			LinkedPosition pos= (LinkedPosition) it.next();
			if (pos.includes(event)) {
				fLastPosition= pos;
				fLastPositionOffset= pos.getOffset();
				return true;
			}
		}
		fLastPosition= null;
		fLastPositionOffset= -1;
		return false;
	}

	/**
	 * Creates an edition of a document change that will forward any
	 * modification in one position to all linked siblings. The return value is
	 * a map from <code>IDocument</code> to <code>TextEdit</code>.
	 * 
	 * @param event the document event to check
	 * @return a map of edits, grouped by edited document
	 */
	Map handleEvent(DocumentEvent event) {

		if (fLastPosition != null) {

			Map map= new HashMap();

			int relOffset= event.getOffset() - fLastPositionOffset;
			int length= event.getLength();
			String text= event.getText();

			for (Iterator it2= fPositions.iterator(); it2.hasNext(); ) {
				LinkedPosition p= (LinkedPosition) it2.next();
				if (p == fLastPosition)
					continue; // don't re-update the origin of the change
				
				List edits= (List) map.get(p.getDocument());
				if (edits == null) {
					edits= new ArrayList();
					map.put(p.getDocument(), edits);
				}

				edits.add(new ReplaceEdit(p.getOffset() + relOffset, length, text));
			}

			for (Iterator it2= map.keySet().iterator(); it2.hasNext(); ) {
				IDocument d= (IDocument) it2.next();
				TextEdit edit= new MultiTextEdit(0, d.getLength());
				edit.addChildren((TextEdit[]) ((List) map.get(d)).toArray(new TextEdit[0]));
				map.put(d, edit);
			}

			return map;

		}

		return null;
	}

	/**
	 * Sets the environment of this group. Once an environment has been set, no
	 * more positions can be added and the environment cannot be changed.
	 * 
	 * @param environment the environment
	 * @throws BadLocationException if registering any position with the
	 *         environment fails
	 */
	void setEnvironment(LinkedEnvironment environment) throws BadLocationException {
		Assert.isNotNull(environment);
		Assert.isTrue(fEnvironment == null);
		fEnvironment= environment;

		if (fHasCustomIteration == false && fPositions.size() > 0) {
			((LinkedPosition) fPositions.get(0)).setSequenceNumber(0);
		}

		for (Iterator it= fPositions.iterator(); it.hasNext(); ) {
			LinkedPosition pos= (LinkedPosition) it.next();
			register(pos);
		}
	}

	/**
	 * Returns the position in this group that encompasses all positions in
	 * <code>group</code>.
	 * 
	 * @param group the group to be adopted
	 * @return a position in the receiver that contains all positions in <code>group</code>,
	 *         or <code>null</code> if none can be found
	 * @throws BadLocationException if more than one position are affected by
	 *         <code>group</code>
	 */
	LinkedPosition adopt(LinkedPositionGroup group) throws BadLocationException {
		LinkedPosition found= null;
		for (Iterator it= group.fPositions.iterator(); it.hasNext(); ) {
			LinkedPosition pos= (LinkedPosition) it.next();
			LinkedPosition localFound= null;
			for (Iterator it2= fPositions.iterator(); it2.hasNext(); ) {
				LinkedPosition myPos= (LinkedPosition) it2.next();
				if (myPos.includes(pos)) {
					if (found == null)
						found= myPos;
					else if (found != myPos)
						throw new BadLocationException();
					if (localFound == null)
						localFound= myPos;
				}
			}

			if (localFound != found)
				throw new BadLocationException();
		}
		return found;
	}

	/**
	 * Finds the closest position to <code>toFind</code>.
	 */
	LinkedPosition getPosition(LinkedPosition toFind) {
		for (Iterator it= fPositions.iterator(); it.hasNext(); ) {
			LinkedPosition p= (LinkedPosition) it.next();
			if (p.includes(toFind))
				return p;
		}
		return null;
	}

	/**
	 * Returns <code>true</code> if <code>offset</code> is contained in any
	 * position in this group.
	 * 
	 * @param offset the offset to check
	 * @return <code>true</code> if offset is contained by this group
	 */
	boolean contains(int offset) {
		for (Iterator it= fPositions.iterator(); it.hasNext(); ) {
			LinkedPosition pos= (LinkedPosition) it.next();
			if (pos.includes(offset)) {
				return true;
			}
		}
		return false;
	}
}
