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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.core.runtime.Status;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IPositionUpdater;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.IDocumentExtension.IReplace;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * A <code> LinkedEnvironment</code> umbrellas several <code>PositionGroup</code>s.
 * Responsible for updating the siblings of a linked position when a change
 * occurs.
 * 
 * @since 3.0
 */
public class LinkedEnvironment {

	/**
	 * Checks whether there is alreay a linked environment installed on <code>document</code>.
	 * 
	 * @param document the <code>IDocument</code> of interest
	 * @return <code>true</code> if there is an existing environment, <code>false</code>
	 *         otherwise
	 */
	public static boolean hasEnvironment(IDocument document) {
		// if there is a manager, there also is an enviroment
		return LinkedManager.hasManager(document);
	}

	/**
	 * Checks whether there is alreay a linked environment installed on any of
	 * the <code>documents</code>.
	 * 
	 * @param documents the <code>IDocument</code> s of interest
	 * @return <code>true</code> if there is an existing environment, <code>false</code>
	 *         otherwise
	 */
	public static boolean hasEnvironment(IDocument[] documents) {
		// if there is a manager, there also is an enviroment
		return LinkedManager.hasManager(documents);
	}
	
	/**
	 * Cancels any linked environment on the specified document. If there is no 
	 * environment, nothing happens.
	 * 
	 * @param document the document whose <code>LinkedEnvironment</code> should 
	 * 		  be cancelled
	 */
	public static void closeEnvironment(IDocument document) {
		LinkedManager.cancelManager(document);
	}

	/**
	 * @param document
	 * @param documentOffset
	 * @return
	 */
	public static LinkedEnvironment getEnvironment(IDocument document, int documentOffset) {
		LinkedManager mgr= LinkedManager.getLinkedManager(new IDocument[] {document}, false);
		if (mgr != null)
			return mgr.getTopEnvironment();
		else
			return null;
	}

	/**
	 * Encapsulates the edition triggered by a change to a linked position. Can
	 * be applied to a document as a whole.
	 */
	private class Replace implements IReplace {

		/** The edition to apply on a document. */
		private TextEdit fEdit;

		/**
		 * Creates a new instance.
		 * 
		 * @param edit the edition to apply to a document.
		 */
		public Replace(TextEdit edit) {
			fEdit= edit;
		}

		/*
		 * @see org.eclipse.jface.text.IDocumentExtension.IReplace#perform(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.IDocumentListener)
		 */
		public void perform(IDocument document, IDocumentListener owner) {
			document.removeDocumentListener(owner);
			fIsChanging= true;
			try {
				fEdit.apply(document, TextEdit.UPDATE_REGIONS | TextEdit.CREATE_UNDO);
			} catch (MalformedTreeException e) {
				// log & ignore (can happen on concurrent document modifications
				JavaPlugin.log(new Status(Status.WARNING, JavaPlugin.getPluginId(), Status.OK, "error when applying changes", e)); //$NON-NLS-1$
			} catch (BadLocationException e) {
				// log & ignore (can happen on concurrent document modifications
				JavaPlugin.log(new Status(Status.WARNING, JavaPlugin.getPluginId(), Status.OK, "error when applying changes", e)); //$NON-NLS-1$
			} finally {
				document.addDocumentListener(owner);
				fIsChanging= false;
			}
		}

	}

	/**
	 * The document listener triggering the linked updating of positions
	 * managed by this environment.
	 */
	private class DocumentListener implements IDocumentListener {

		/*
		 * @see org.eclipse.jface.text.IDocumentListener#documentAboutToBeChanged(org.eclipse.jface.text.DocumentEvent)
		 */
		public void documentAboutToBeChanged(DocumentEvent event) {
			// don't react on changes executed by the parent environment
			if (fParentEnvironment != null && fParentEnvironment.isChanging())
				return;

			for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
				LinkedPositionGroup group= (LinkedPositionGroup) it.next();
				if (group.isLegalEvent(event))
					// take the first hit - exlusion is guaranteed by enforcing
					// disjointness when adding positions
					return;
			}
			
			// the event describes a change that lies outside of any managed
			// position -> exit mode.
			// TODO we might not always want to exit, e.g. we want to stay
			// linked if code completion has inserted import statements
			LinkedEnvironment.this.exit(ILinkedListener.EXTERNAL_MODIFICATION);

		}

		/*
		 * @see org.eclipse.jface.text.IDocumentListener#documentChanged(org.eclipse.jface.text.DocumentEvent)
		 */
		public void documentChanged(DocumentEvent event) {
			// don't react on changes executed by the parent environment
			if (fParentEnvironment != null && fParentEnvironment.isChanging())
				return;

			for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
				LinkedPositionGroup group= (LinkedPositionGroup) it.next();

				Map result= group.handleEvent(event);
				if (result == null)
					continue;

				// edit all documents
				for (Iterator it2= result.keySet().iterator(); it2.hasNext(); ) {
					IDocument doc= (IDocument) it2.next();
					TextEdit edit= (TextEdit) result.get(doc);
					Replace replace= new Replace(edit);
				
					// apply the edition, either as post notification replace
					// on the calling document or directly on any other
					// document
					if (doc == event.getDocument()) {
						if (doc instanceof IDocumentExtension) {
							((IDocumentExtension) doc).registerPostNotificationReplace(this, replace);
						} else {
							JavaPlugin.log(new Status(Status.WARNING, JavaPlugin.getPluginId(), Status.OK, "not executing changes on document that is not a IDocumentExtension", null)); //$NON-NLS-1$
						}
					} else {
						replace.perform(doc, this);
					}
				}
				
				// take the first hit - exlusion is guaranteed by enforcing
				// disjointness when adding positions
				return;
			}
		}

	}

	/** The set of linked position groups. */
	private final List fGroups= new ArrayList();
	/** The set of documents spanned by this group. */
	private final Set fDocuments= new HashSet();
	/** The position updater for linked positions. */
	private final IPositionUpdater fUpdater= new InclusivePositionUpdater(getCategory());
	/** The document listener on the documents affected by this environment. */
	private final IDocumentListener fDocumentListener= new DocumentListener();
	/** The parent environment for a hierachical set up, or <code>null</code>. */
	private LinkedEnvironment fParentEnvironment;
	/**
	 * The position in <code>fParentEnvironment</code> that includes all
	 * positions in this object, or <code>null</code> if there is no parent
	 * environment.
	 */
	private LinkedPosition fParentPosition= null;
	/**
	 * An environment is sealed once it has children - no more positions can be
	 * added.
	 */
	private boolean fIsSealed= false;
	/** <code>true</code> when this environment is changing documents. */
	private boolean fIsChanging= false;
	/** The linked listeners. */
	private final List fListeners= new ArrayList();
	/** Flag telling whether we have exited: */
	private boolean fIsActive= true;
	/**
	 * The sequence of document positions as we are going to iterate through
	 * them.
	 */
	private List fPositionSequence= new ArrayList();

	/**
	 * Whether we are in the process of editing documents (set by <code>Replace</code>,
	 * read by <code>DocumentListener</code>.
	 * 
	 * @return <code>true</code> if we are in the process of editing a
	 *         document, <code>false</code> otherwise
	 */
	private boolean isChanging() {
		return fIsChanging || fParentEnvironment != null && fParentEnvironment.isChanging();
	}

	/**
	 * Throws a <code>BadLocationException</code> if <code>group</code>
	 * conflicts with this environment's groups.
	 * 
	 * @param group the group being checked
	 * @throws BadLocationException if <code>group</code> conflicts with this
	 *         environment's groups
	 */
	private void enforceDisjoint(LinkedPositionGroup group) throws BadLocationException {
		for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
			LinkedPositionGroup g= (LinkedPositionGroup) it.next();
			g.enforceDisjoint(group);
		}
	}

	/**
	 * Causes this environment to exit. Called either if a document change
	 * outside this enviroment is detected, or by the UI.
	 * 
	 * <p>This method part of the private protocol between <code>LinkedUIControl</code> and <code>LinkedEnvironment</code>.</p>
	 * 
	 * @param flags the exit flags.
	 */
	void exit(int flags) {
		if (!fIsActive)
			return;
		fIsActive= false;

		for (Iterator it= fDocuments.iterator(); it.hasNext(); ) {
			IDocument doc= (IDocument) it.next();
			try {
				doc.removePositionCategory(getCategory());
			} catch (BadPositionCategoryException e) {
				// won't happen
				Assert.isTrue(false);
			}
			doc.removePositionUpdater(fUpdater);
			doc.removeDocumentListener(fDocumentListener);
		}

		fDocuments.clear();
		fGroups.clear();

		for (Iterator it= fListeners.iterator(); it.hasNext(); ) {
			ILinkedListener listener= (ILinkedListener) it.next();
			listener.left(this, flags);
		}

		fListeners.clear();

		if (fParentEnvironment != null)
			fParentEnvironment.resume(flags);
	}

	/**
	 * Puts <code>document</code> into the set of managed documents. This
	 * involves registering the document listener and adding our position
	 * category.
	 * 
	 * @param document the new document
	 */
	private void manageDocument(IDocument document) {
		if (!fDocuments.contains(document)) {
			fDocuments.add(document);
			document.addPositionCategory(getCategory());
			document.addPositionUpdater(fUpdater);
			document.addDocumentListener(fDocumentListener);
		}

	}

	/**
	 * Returns the position category used by this environment.
	 * 
	 * @return the position category used by this environment
	 */
	private String getCategory() {
		return toString();
	}

	/**
	 * Adds a position group to this <code>LinkedEnvironment</code>. This
	 * method may not be called if the environment is already sealed, i.e. a
	 * nested environment has been added to it. It is also not wise to add
	 * groups once a UI has been established on top of this environment.
	 * 
	 * <p>
	 * If the positions in <code>group</code> conflict with any other groups
	 * in this environment, a <code>BadLocationException</code> is thrown.
	 * Also, if this environment is nested in another one, all positions in all
	 * groups of the child environment have to lie in a single position in the
	 * parent environment, otherwise a <code>BadLocationException</code> is
	 * thrown.
	 * </p>
	 * 
	 * <p>
	 * If <code>group</code> already exists, nothing happens.
	 * </p>
	 * 
	 * @param group the group to be added to this environment
	 * @throws BadLocationException if the group conflicts with the other
	 *         groups in this environment or violates the nesting requirements.
	 * @throws IllegalStateException if the method is called when the
	 *         environment is already sealed
	 */
	public void addGroup(LinkedPositionGroup group) throws BadLocationException {
		if (group == null)
			throw new IllegalArgumentException("group may not be null"); //$NON-NLS-1$
		if (fIsSealed)
			throw new IllegalStateException("environment is already installed"); //$NON-NLS-1$
		if (fGroups.contains(group))
			// nothing happens
			return;

		enforceDisjoint(group);
		group.seal();
		fGroups.add(group);
	}
	
	
	/**
	 * Installs this environment, which includes registering as document listener
	 * on all involved documents and storing global information about this environment. If
	 * an exception is thrown, the installation failed and the environment is unusable.
	 * 
	 * @throws BadLocationException if some of the positions of this environment were not valid positions on their respective documents
	 */
	public void forceInstall() throws BadLocationException {
		if (!install(true))
			Assert.isTrue(false);
	}
	
	/**
	 * Installs this environment, which includes registering as document listener
	 * on all involved documents and storing global information about this environment. The return
	 * value states whether installation was successful; if not, the environment is not installed
	 * and will not work.
	 * 
	 * @return <code>true</code> if installation was successful, <code>false</code> otherwise
	 * @throws BadLocationException if some of the positions of this environment were not valid positions on their respective documents
	 */
	public boolean tryInstall() throws BadLocationException {
		return install(false);
	}
	
	/**
	 * Installs this environment, which includes registering as document listener
	 * on all involved documents and storing global information about this environment. The return
	 * value states whether installation was successful; if not, the environment is not installed
	 * and will not work. The return value can only then become <code>false</code> if <code>force</code>
	 * was set to <code>false</code> as well.
	 * 
	 * @param force if <code>true</code>, any other environment that cannot coexist
	 * with this one is canceled; if <code>false</code>, install will fail when conflicts
	 * occur and return false
	 * @return <code>true</code> if installation was successful, <code>false</code> otherwise
	 * @throws BadLocationException if some of the positions of this environment were not valid positions on their respective documents
	 */
	private boolean install(boolean force) throws BadLocationException {
		if (fIsSealed)
			throw new IllegalStateException("environment is already installed"); //$NON-NLS-1$
		enforceNotEmpty();
		
		IDocument[] documents= getDocuments();
		LinkedManager manager= LinkedManager.getLinkedManager(documents, force);
		// if we force creation, we require a valid manager
		Assert.isTrue(!(force && manager == null));
		if (manager == null)
			return false;
		
		if (!manager.nestEnvironment(this, force))
			if (force)
				Assert.isTrue(false);
			else
				return false;
		
		// we set up successfully. After this point, exit has to be called to 
		// remove registered listeners...
		fIsSealed= true;
		if (fParentEnvironment != null)
			fParentEnvironment.suspend();
		
		// register positions
		try {
			for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
	            LinkedPositionGroup group= (LinkedPositionGroup) it.next();
	            group.register(this);
	        }
			return true;
		} catch (BadLocationException e){
			// if we fail to add, make sure to release all listeners again 
			exit(ILinkedListener.NONE);
			throw e;
		}
	}

	/**
	 * Asserts that there is at least one linked position in this linked
	 * environment, throws an IllegalStateException otherwise.
	 */
	private void enforceNotEmpty() {
        boolean hasPosition= false;
		for (Iterator it= fGroups.iterator(); it.hasNext(); )
			if (!((LinkedPositionGroup) it.next()).isEmtpy()) {
				hasPosition= true;
				break;
			}
		if (!hasPosition)
			throw new IllegalStateException("must specify at least one linked position"); //$NON-NLS-1$

    }

    /**
	 * Collects all the documents that contained positions are set upon.
     * @return the set of documents affected by this environment
     */
    private IDocument[] getDocuments() {
    	Set docs= new HashSet();
        for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
            LinkedPositionGroup group= (LinkedPositionGroup) it.next();
            docs.addAll(Arrays.asList(group.getDocuments()));
        }
        return (IDocument[]) docs.toArray(new IDocument[docs.size()]);
    }

    /**
     * Returns whether the receiver can be nested into the given <code>parent</code>
     * environment. If yes, the parent environment and its position that the receiver
     * fits in are remembered.
     * 
     * @param parent the parent environment candidate
     * @return <code>true</code> if the receiver can be nested into <code>parent</code>, <code>false</code> otherwise
     */
    boolean canNestInto(LinkedEnvironment parent) {
    	for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
			LinkedPositionGroup group= (LinkedPositionGroup) it.next();
			if (!enforceNestability(group, parent)) {
				fParentPosition= null;
				return false;
			}
		}
    	
    	Assert.isNotNull(fParentPosition);
    	fParentEnvironment= parent;
    	return true;
    }

    /**
	 * Called by nested environments when a group is added to them. All
	 * positions in all groups of a nested environment have to fit inside a
	 * single position in the parent environment.
	 * 
	 * @param group the group of the nested environment to be adopted.
	 * @param environment the environment to check against
	 */
	private boolean enforceNestability(LinkedPositionGroup group, LinkedEnvironment environment) {
		Assert.isNotNull(environment);
		Assert.isNotNull(group);
		
		try {
			for (Iterator it= environment.fGroups.iterator(); it.hasNext(); ) {
				LinkedPositionGroup pg= (LinkedPositionGroup) it.next();
				LinkedPosition pos;
				pos= pg.adopt(group);
				if (pos != null && fParentPosition != null && fParentPosition != pos)
					return false; // group does not fit into one parent position, which is illegal
				else if (fParentPosition == null && pos != null)
					fParentPosition= pos;
			}
		} catch (BadLocationException e) {
			return false;
		}

		// group must fit into exactly one of the parent's positions
		return fParentPosition != null;
	}

	/**
	 * Returns whether this environment is nested.
	 * 
	 * <p>This method part of the private protocol between <code>LinkedUIControl</code> and <code>LinkedEnvironment</code>.</p>
	 * 
	 * @return <code>true</code> if this environment is nested, <code>false</code>
	 *         otherwise
	 */
	boolean isNested() {
		return fParentEnvironment != null;
	}

	/**
	 * Returns the positions in this environment that have a tab stop, in the
	 * order they were added.
	 * 
	 * <p>This method part of the private protocol between <code>LinkedUIControl</code> and <code>LinkedEnvironment</code>.</p>
	 * 
	 * @return the positions in this environment that have a tab stop, in the
	 *         order they were added
	 */
	List getTabStopSequence() {
		return fPositionSequence;
	}

	/**
	 * Adds <code>listener</code> to the set of listeners that are informed
	 * upon state changes.
	 * 
	 * @param listener the new listener
	 */
	public void addLinkedListener(ILinkedListener listener) {
		Assert.isNotNull(listener);
		if (!fListeners.contains(listener))
			fListeners.add(listener);
	}

	/**
	 * Removes <code>listener</code> from the set of listeners that are
	 * informed upon state changes.
	 * 
	 * @param listener the new listener
	 */
	public void removeLinkedListener(ILinkedListener listener) {
		fListeners.remove(listener);
	}

	/**
	 * Finds the position in this environment that is closest after <code>toFind</code>.
	 * <code>toFind</code> needs not be a position in this environment and
	 * serves merely as an offset.
	 * 
	 * <p>This method part of the private protocol between <code>LinkedUIControl</code> and <code>LinkedEnvironment</code>.</p>
	 * 
	 * @param toFind the position to search from
	 * @return the closest position in the same document as <code>toFind</code>
	 *         after the offset of <code>toFind</code>, or <code>null</code>
	 */
	LinkedPosition findPosition(LinkedPosition toFind) {
		LinkedPosition position= null;
		for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
			LinkedPositionGroup group= (LinkedPositionGroup) it.next();
			position= group.getPosition(toFind);
			if (position != null)
				break;
		}
		return position;
	}

	/**
	 * Registers a <code>LinkedPosition</code> with this environment. Called
	 * by <code>PositionGroup</code>.
	 * 
	 * @param position the position to register
	 * @throws BadLocationException if the position cannot be added to its
	 *         document
	 */
	void register(LinkedPosition position) throws BadLocationException {
		Assert.isNotNull(position);

		IDocument document= position.getDocument();
		manageDocument(document);
		try {
			document.addPosition(getCategory(), position);
		} catch (BadPositionCategoryException e) {
			// won't happen as the category has been added by manageDocument()
			Assert.isTrue(false);
		}
		int seqNr= position.getSequenceNumber();
		if (seqNr != LinkedPositionGroup.NO_STOP) {
			fPositionSequence.add(position);
		}
	}
	
	/**
	 * Suspends this environment.
	 */
	private void suspend() {
		List l= new ArrayList(fListeners);
		for (Iterator it= l.iterator(); it.hasNext(); ) {
			ILinkedListener listener= (ILinkedListener) it.next();
			listener.suspend(this);
		}
	}

	/**
	 * Resumes this environment. <code>flags</code> can be <code>NONE</code>
	 * or <code>SELECT</code>.
	 * 
	 * @param flags <code>NONE</code> or <code>SELECT</code>
	 */
	private void resume(int flags) {
		List l= new ArrayList(fListeners);
		for (Iterator it= l.iterator(); it.hasNext(); ) {
			ILinkedListener listener= (ILinkedListener) it.next();
			listener.resume(this, flags);
		}
	}

	/**
	 * Returns whether an offset is contained by any position in this
	 * environment.
	 * 
	 * @param offset the offset to check
	 * @return <code>true</code> if <code>offset</code> is included by any
	 *         position (see {@link LinkedPosition#includes(int)}) in this
	 *         environment, <code>false</code> otherwise
	 */
	public boolean anyPositionContains(int offset) {
		for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
			LinkedPositionGroup group= (LinkedPositionGroup) it.next();
			if (group.contains(offset))
				// take the first hit - exlusion is guaranteed by enforcing
				// disjointness when adding positions
				return true;
		}
		return false;
	}
	
	/**
	 * @param position
	 * @return
	 */
	LinkedPositionGroup getGroupForPosition(Position position) {
		for (Iterator it= fGroups.iterator(); it.hasNext(); ) {
			LinkedPositionGroup group= (LinkedPositionGroup) it.next();
			if (group.contains(position))
				return group;
		}
		return null;
	}

	/**
	 * @param documentOffset
	 */
	public ICompletionProposal[] getCompletions(IDocument document, int documentOffset) {
		LinkedPosition toFind= new LinkedPosition(document, documentOffset, 0, LinkedPositionGroup.NO_STOP);
		LinkedPositionGroup group= getGroupForPosition(toFind);
		if (group != null) {
			LinkedPosition position= group.getPosition(toFind);
			if (position instanceof ProposalPosition)
				return ((ProposalPosition) position).getChoices();
		}
		
		return null;
	}
}
