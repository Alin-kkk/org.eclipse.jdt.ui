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
package org.eclipse.ltk.core.refactoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.internal.core.refactoring.RefactoringCorePlugin;

/**
 * Represents a composite change. Composite changes can be marked
 * as generic. Generic composite changes will not be shown in the
 * user interface. When rendering a change tree the children of
 * a generic composite change will be shown as children of the
 * parent change.
 * 
 * @see Change
 * 
 * @since 3.0
 */
public class CompositeChange extends Change {

	private String fName;
	private List fChanges;
	private boolean fIsGeneric;
	private Change fUndoUntilException;
	
	/**
	 * Creates a new composite change with a generic name. The change
	 * is marked as generic and should therefore not be persented in
	 * the user interface.
	 */
	public CompositeChange() {
		this(RefactoringCoreMessages.getString("CompositeChange.CompositeChange")); //$NON-NLS-1$
		markAsGeneric();
	}

	/**
	 * Creates a new composite change with the given name.
	 * 
	 * @param name the name of the composite change
	 */
	public CompositeChange(String name) {
		this(name, new ArrayList(5));
	}
	
	/**
	 * Creates a new composite change with the given name and array
	 * of children.
	 * 
	 * @param name the change's name
	 * @param children the initiale array of children
	 */
	public CompositeChange(String name, Change[] children) {
		this(name, new ArrayList(children.length));
		addAll(children);
	}
			
	private CompositeChange(String name, List changes) {
		Assert.isNotNull(changes);
		Assert.isNotNull(name);
		fChanges= changes;
		fName= name;
		fIsGeneric= false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getName() {
		return fName;
	}
	
	/**
	 * Returns whether this change is generic or not.
	 * 
	 * @return <code>true</code>if this change is generic; otherwise
	 *  <code>false</code>
	 */
	public boolean isGeneric() {
		return fIsGeneric;
	}
	
	/**
	 * Marks this change as generic.
	 */
	public void markAsGeneric() {
		fIsGeneric= true;
	}
	
	/**
	 * Adds the given change to the list of children.
	 * 
	 * @param change the change to add
	 */
	public void add(Change change) {
		if (change != null) {
			Assert.isTrue(change.getParent() == null);
			fChanges.add(change);
			change.setParent(this);
		}
	}
	
	/**
	 * Adds all changes in the given array to the list of children.
	 * 
	 * @param changes the changes to add
	 */
	public void addAll(Change[] changes) {
		for (int i= 0; i < changes.length; i++) {
			add(changes[i]);
		}
	}
	
	/**
	 * Merges the children of the given composite change into this
	 * change. This means the changes are removed from the given
	 * composite change and added to this change.
	 * 
	 * @param change the change to merge
	 */
	public void merge(CompositeChange change) {
		Change[] others= change.getChildren();
		for (int i= 0; i < others.length; i++) {
			Change other= others[i];
			change.remove(other);
			add(other);
		}
	}
	
	/**
	 * Removes the given change from the list of children.
	 *
	 * @param change the change to remove
	 * 
	 * @return <code>true</code> if the change contained the given
	 *  child; otherwise <code>false</code> is returned
	 */
	public boolean remove(Change change) {
		Assert.isNotNull(change);
		boolean result= fChanges.remove(change);
		if (result) {
			change.setParent(null);
		}
		return result;
		
	}
	
	/**
	 * Returns the children managed by this composite change. 
	 * 
	 * @return the children
	 */
	public Change[] getChildren() {
		if (fChanges == null)
			return null;
		return (Change[])fChanges.toArray(new Change[fChanges.size()]);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * The composite change sends <code>setEnabled</code> to all its children.
	 * </p>
	 */
	public void setEnabled(boolean enabled) {
		for (Iterator iter= fChanges.iterator(); iter.hasNext(); ) {
			((Change)iter.next()).setEnabled(enabled);
		}
	}	
	/**
	 * {@inheritDoc}
	 * <p>
	 * The composite change sends <code>initializeValidationData</code> to all its 
	 * children. If one of the children throws an exception the remaining children
	 * will not receive the <code>initializeValidationData</code> call.
	 * </p>
	 */
	public void initializeValidationData(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", fChanges.size()); //$NON-NLS-1$
		for (Iterator iter= fChanges.iterator(); iter.hasNext();) {
			Change change= (Change)iter.next();
			change.initializeValidationData(new SubProgressMonitor(pm, 1));
			pm.worked(1);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * The composite change sends <code>isValid</code> to all its children
	 * until the first one returns a status with a severity of <code>FATAL
	 * </code>. If one of the children throws an exception the remaining children
	 * will not receive the <code>isValid</code> call.
	 * </p>
	 */
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		pm.beginTask("", fChanges.size()); //$NON-NLS-1$
		for (Iterator iter= fChanges.iterator(); iter.hasNext() && !result.hasFatalError();) {
			Change change= (Change)iter.next();
			if (change.isEnabled())
				result.merge(change.isValid(new SubProgressMonitor(pm, 1)));
			else
				pm.worked(1);
		}
		pm.done();
		return result;
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * The composite change sends <code>perform</code> to all its <em>enabled</code>
	 * children. If one of the children throws an exception the remaining children
	 * will not receive the <code>perform</code> call. In this case the method <code>
	 * getUndoUntilException</code> can be used to get an undo object containing the
	 * undos of all executed children.
	 * </p>
	 */
	public Change perform(IProgressMonitor pm) throws CoreException {
		fUndoUntilException= null;
		List undos= new ArrayList(fChanges.size());
		pm.beginTask("", fChanges.size()); //$NON-NLS-1$
		pm.setTaskName(RefactoringCoreMessages.getString("CompositeChange.performingChangesTask.name")); //$NON-NLS-1$
		Change change= null;
		try {
			for (Iterator iter= fChanges.iterator(); iter.hasNext();) {
				change= (Change)iter.next();
				if (change.isEnabled()) {
					Change undoChange= change.perform(new SubProgressMonitor(pm, 1));
					if (undos != null) {
						if (undoChange == null) {
							undos= null;
						} else {
							undos.add(undoChange);
						}
					}
				}
			}
			if (undos != null) {
				Collections.reverse(undos);
				return createUndoChange((Change[]) undos.toArray(new Change[undos.size()]));
			} else {
				return null;
			}
		} catch (CoreException e) {
			handleUndos(change, undos);
			throw e;
		} catch (RuntimeException e) {
			handleUndos(change, undos);
			throw e;
		}
	}
	
	private void handleUndos(Change failedChange, List undos) {
		if (undos == null) {
			fUndoUntilException= null;
			return;
		}
		if (failedChange instanceof CompositeChange) {
			Change partUndoChange= ((CompositeChange)failedChange).getUndoUntilException();
			if (partUndoChange != null) {
				undos.add(partUndoChange);
			}
		}
		if (undos.size() == 0) {
			fUndoUntilException= new NullChange(getName());
			return;
		}
		Collections.reverse(undos);
		fUndoUntilException= createUndoChange((Change[]) undos.toArray(new Change[undos.size()]));
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * The composite change sends <code>dispose</code> to all its children. It is guaranteed
	 * that all children receive the <code>dispose</code> call.
	 * </p>
	 */
	public void dispose() {
		for (Iterator iter= fChanges.iterator(); iter.hasNext(); ) {
			final Change change= (Change)iter.next();
			Platform.run(new ISafeRunnable() {
				public void run() throws Exception {
					change.dispose();
				}
				public void handleException(Throwable exception) {
					RefactoringCorePlugin.log(exception);
				}
			});
		}
	}
	
	/**
	 * Returns the undo object containing all undo changes of those children
	 * that got successfully executed while performing this change. Returns
	 * <code>null</code> if all changes were executed successfully.
	 * 
	 * @return the undo object containing all undo changes of those children
	 *  that got successfully executed while performming this change
	 */
	public Change getUndoUntilException() {
		return fUndoUntilException;
	}
		
	/**
	 * Hook to create an undo change.
	 * 
	 * @param childUndos the child undo. The undos appear in the
	 *  list in the reverse order of their execution. So the first
	 *  change in the array is the undo change of the last change
	 *  that got executed.
	 * 
	 * @return the undo change
	 * 
	 * @throws CoreException if an undo change can't be created
	 */
	protected Change createUndoChange(Change[] childUndos) {
		return new CompositeChange(getName(), childUndos);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Object getModifiedElement() {
		return null;
	}

	public String toString() {
		StringBuffer buff= new StringBuffer();
		buff.append(getName());
		buff.append("\n"); //$NON-NLS-1$
		for (Iterator iter= fChanges.iterator(); iter.hasNext();) {
			buff.append("<").append(iter.next().toString()).append("/>\n"); //$NON-NLS-2$ //$NON-NLS-1$
		}
		return buff.toString();
	}
	
}
