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
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.search.SearchMessages;
import org.eclipse.jdt.internal.ui.search.SearchUtil;

/**
 * Action group that adds the search for references actions to a
 * context menu and the global menu bar.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class ReferencesSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.getString("group.references"); //$NON-NLS-1$

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;
	
	private String fGroupId;
	
	private FindReferencesAction fFindReferencesAction;
	private FindReferencesInHierarchyAction fFindReferencesInHierarchyAction;
	private FindReferencesInWorkingSetAction fFindReferencesInWorkingSetAction;

	/**
	 * Creates a new <code>ReferencesSearchGroup</code>. The group requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param part the view part that owns this action group
	 */
	public ReferencesSearchGroup(IWorkbenchSite site) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindReferencesAction= new FindReferencesAction(site);
		fFindReferencesInHierarchyAction= new FindReferencesInHierarchyAction(site);
		fFindReferencesInWorkingSetAction= new FindReferencesInWorkingSetAction(site);

		// register the actions as selection listeners
		ISelectionProvider provider= fSite.getSelectionProvider();
		ISelection selection= provider.getSelection();
		registerAction(fFindReferencesAction, provider, selection);
		registerAction(fFindReferencesInHierarchyAction, provider, selection);
		registerAction(fFindReferencesInWorkingSetAction, provider, selection);
	}


	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public ReferencesSearchGroup(JavaEditor editor) {
		Assert.isNotNull(editor);
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindReferencesAction= new FindReferencesAction(editor);
		fFindReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKSPACE);
		fEditor.setAction("SearchReferencesInWorkspace", fFindReferencesAction); //$NON-NLS-1$

		fFindReferencesInHierarchyAction= new FindReferencesInHierarchyAction(fEditor);
		fFindReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_HIERARCHY);
		fEditor.setAction("SearchReferencesInHierarchy", fFindReferencesInHierarchyAction); //$NON-NLS-1$
		
		fFindReferencesInWorkingSetAction= new FindReferencesInWorkingSetAction(fEditor);
		fFindReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKING_SET);
		fEditor.setAction("SearchReferencesInWorkingSet", fFindReferencesInWorkingSetAction); //$NON-NLS-1$
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection) {
		action.update(selection);
		provider.addSelectionChangedListener(action);
	};

	/**
	 * Note: this method is for internal use only. Clients should not call this method.
	 */	
	protected FindAction[] getActions(ISelection sel) {
		ArrayList actions= new ArrayList(SearchUtil.LRU_WORKINGSET_LIST_SIZE + 3);
		actions.add(fFindReferencesAction);
		actions.add(fFindReferencesInHierarchyAction);
		actions.add(fFindReferencesInWorkingSetAction);

		Iterator iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			IWorkingSet[] workingSets= (IWorkingSet[])iter.next();
			FindAction action;
			if (fEditor != null)
				action= new WorkingSetFindAction(fEditor, new FindReferencesInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
			else
				action= new WorkingSetFindAction(fSite, new FindReferencesInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
			action.update(sel);
			actions.add(action);
		}
		return (FindAction[])actions.toArray(new FindAction[actions.size()]);
	}

	/**
	 * Note: this method is for internal use only. Clients should not call this method.
	 */
	protected String getName() {
		return MENU_TEXT;
	}
	
	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	public void fillActionBars(IActionBars actionBars) {
		Assert.isNotNull(actionBars);
		super.fillActionBars(actionBars);
		fActionBars= actionBars;
		updateGlobalActionHandlers();
	}

	public void fillContextMenu(IMenuManager manager) {
		MenuManager javaSearchMM= new MenuManager(getName(), IContextMenuConstants.GROUP_SEARCH);
		ISelection sel= getContext().getSelection();
		FindAction[] actions= getActions(sel);
		for (int i= 0; i < actions.length; i++) {
			FindAction action= actions[i];
			if (action.isEnabled())
				javaSearchMM.add(action);
		}
		
		if (!javaSearchMM.isEmpty())
			manager.appendToGroup(fGroupId, javaSearchMM);
	}
	
	/* 
	 * Overrides method declared in ActionGroup
	 */
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		if (provider != null) {
			disposeAction(fFindReferencesAction, provider);
			disposeAction(fFindReferencesInHierarchyAction, provider);
			disposeAction(fFindReferencesInWorkingSetAction, provider);
		}
		fFindReferencesAction= null;
		fFindReferencesInHierarchyAction= null;
		fFindReferencesInWorkingSetAction= null;
		updateGlobalActionHandlers();
		super.dispose();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_REFERENCES_IN_WORKSPACE, fFindReferencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_REFERENCES_IN_HIERARCHY, fFindReferencesInHierarchyAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_REFERENCES_IN_WORKING_SET, fFindReferencesInWorkingSetAction);
		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}
