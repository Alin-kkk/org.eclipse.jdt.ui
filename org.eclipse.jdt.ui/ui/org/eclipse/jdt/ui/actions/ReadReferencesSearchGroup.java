/*
 * Copyright (c) 2000, 2002 IBM Corp. and others..
 * All rights reserved. � This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
�*
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
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
 * Action group that adds the search for read references actions to a
 * context menu and the global menu bar.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class ReadReferencesSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.getString("group.readReferences"); //$NON-NLS-1$

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	
	private String fGroupId;

	private FindReadReferencesAction fFindReadReferencesAction;
	private FindReadReferencesInHierarchyAction fFindReadReferencesInHierarchyAction;
	private FindReadReferencesInWorkingSetAction fFindReadReferencesInWorkingSetAction;
	
	/**
	 * Creates a new <code>ReadReferencesSearchGroup</code>.
	 * 
	 * @param part the view part that owns this action group
	 */
	public ReadReferencesSearchGroup(IWorkbenchSite site) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindReadReferencesAction= new FindReadReferencesAction(site);
		fFindReadReferencesInHierarchyAction= new FindReadReferencesInHierarchyAction(site);
		fFindReadReferencesInWorkingSetAction= new FindReadReferencesInWorkingSetAction(site);

		// register the actions as selection listeners
		ISelectionProvider provider= fSite.getSelectionProvider();
		ISelection selection= provider.getSelection();
		registerAction(fFindReadReferencesAction, provider, selection);
		registerAction(fFindReadReferencesInHierarchyAction, provider, selection);
		registerAction(fFindReadReferencesInWorkingSetAction, provider, selection);
	}

	/**
	 * Creates a new <code>ReadReferencesSearchGroup</code>.
	 * <p>
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * </p>
	 */
	public ReadReferencesSearchGroup(JavaEditor editor) {
		fEditor= editor;
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindReadReferencesAction= new FindReadReferencesAction(fEditor);
		fFindReadReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKSPACE);
		fEditor.setAction("SearchReadAccessInWorkspace", fFindReadReferencesAction); //$NON-NLS-1$

		fFindReadReferencesInHierarchyAction= new FindReadReferencesInHierarchyAction(fEditor);
		fFindReadReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_HIERARCHY);
		fEditor.setAction("SearchReadAccessInHierarchy", fFindReadReferencesInHierarchyAction); //$NON-NLS-1$

		fFindReadReferencesInWorkingSetAction= new FindReadReferencesInWorkingSetAction(fEditor);
		fFindReadReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKING_SET);
		fEditor.setAction("SearchReadAccessInWorkingSet", fFindReadReferencesInWorkingSetAction); //$NON-NLS-1$
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection) {
		action.update(selection);
		provider.addSelectionChangedListener(action);
	};

	private FindAction[] getActions(ISelection sel) {
		ArrayList actions= new ArrayList(SearchUtil.LRU_WORKINGSET_LIST_SIZE + 2);		
		actions.add(fFindReadReferencesAction);
		actions.add(fFindReadReferencesInHierarchyAction);
		actions.add(fFindReadReferencesInWorkingSetAction);
			
		Iterator iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			IWorkingSet[] workingSets= (IWorkingSet[])iter.next();
			FindAction action;
			if (fEditor != null)
				action= new WorkingSetFindAction(fEditor, new FindReadReferencesInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
			else
				action= new WorkingSetFindAction(fSite, new FindReadReferencesInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
			action.update(sel);
			actions.add(action);
		}
		return (FindAction[])actions.toArray(new FindAction[actions.size()]);
	}

	/* 
	 * Method declared on ActionGroup.
	 */
	public void fillContextMenu(IMenuManager manager) {
		MenuManager javaSearchMM= new MenuManager(MENU_TEXT, IContextMenuConstants.GROUP_SEARCH);
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
	 * Method declared on ActionGroup.
	 */
	public void fillActionBars(IActionBars actionBar) {
		super.fillActionBars(actionBar);
		actionBar.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_WORKSPACE, fFindReadReferencesAction);
		actionBar.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_HIERARCHY, fFindReadReferencesInHierarchyAction);
		actionBar.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_WORKING_SET, fFindReadReferencesInWorkingSetAction);
	}

	/* 
	 * Method declared on ActionGroup.
	 */
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		if (provider != null) {
			disposeAction(fFindReadReferencesAction, provider);
			disposeAction(fFindReadReferencesInHierarchyAction, provider);
			disposeAction(fFindReadReferencesInWorkingSetAction, provider);
		}
		super.dispose();
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}