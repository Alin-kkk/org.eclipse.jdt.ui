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
 * Action group that adds the search for write references actions to a
 * context menu and the global menu bar.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class WriteReferencesSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.getString("group.writeReferences"); //$NON-NLS-1$

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;
		
	private String fGroupId;

	private FindWriteReferencesAction fFindWriteReferencesAction;
	private FindWriteReferencesInHierarchyAction fFindWriteReferencesInHierarchyAction;
	private FindWriteReferencesInWorkingSetAction fFindWriteReferencesInWorkingSetAction;
	
	/**
	 * Creates a new <code>WriteReferencesSearchGroup</code>. The action 
	 * requires that the selection provided by the site's selection provider is of 
	 * type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param part the view part that owns this action group
	 */
	public WriteReferencesSearchGroup(IWorkbenchSite site) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindWriteReferencesAction= new FindWriteReferencesAction(site);
		fFindWriteReferencesInHierarchyAction= new FindWriteReferencesInHierarchyAction(site);
		fFindWriteReferencesInWorkingSetAction= new FindWriteReferencesInWorkingSetAction(site);

		// register the actions as selection listeners
		ISelectionProvider provider= fSite.getSelectionProvider();
		ISelection selection= provider.getSelection();
		registerAction(fFindWriteReferencesAction, provider, selection);
		registerAction(fFindWriteReferencesInHierarchyAction, provider, selection);
		registerAction(fFindWriteReferencesInWorkingSetAction, provider, selection);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public WriteReferencesSearchGroup(JavaEditor editor) {
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindWriteReferencesAction= new FindWriteReferencesAction(fEditor);
		fFindWriteReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKSPACE);
		fEditor.setAction("SearchWriteAccessInWorkspace", fFindWriteReferencesAction); //$NON-NLS-1$

		fFindWriteReferencesInHierarchyAction= new FindWriteReferencesInHierarchyAction(fEditor);
		fFindWriteReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_HIERARCHY);
		fEditor.setAction("SearchWriteAccessInHierarchy", fFindWriteReferencesInHierarchyAction); //$NON-NLS-1$

		fFindWriteReferencesInWorkingSetAction= new FindWriteReferencesInWorkingSetAction(fEditor);
		fFindWriteReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKING_SET);
		fEditor.setAction("SearchWriteAccessInWorkingSet", fFindWriteReferencesInWorkingSetAction); //$NON-NLS-1$
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection) {
		action.update(selection);
		provider.addSelectionChangedListener(action);
	};

	private FindAction[] getActions(ISelection sel) {
		ArrayList actions= new ArrayList(SearchUtil.LRU_WORKINGSET_LIST_SIZE + 2);		
		actions.add(fFindWriteReferencesAction);
		actions.add(fFindWriteReferencesInHierarchyAction);
		actions.add(fFindWriteReferencesInWorkingSetAction);
			
		Iterator iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			IWorkingSet[] workingSets= (IWorkingSet[])iter.next();
			FindAction action;
			if (fEditor != null)
				action= new WorkingSetFindAction(fEditor, new FindWriteReferencesInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
			else
				action= new WorkingSetFindAction(fSite, new FindWriteReferencesInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
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
	public void fillActionBars(IActionBars actionBars) {
		Assert.isNotNull(actionBars);
		super.fillActionBars(actionBars);
		fActionBars= actionBars;
		updateGlobalActionHandlers();
	}

	/* 
	 * Method declared on ActionGroup.
	 */
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		if (provider != null) {
			disposeAction(fFindWriteReferencesAction, provider);
			disposeAction(fFindWriteReferencesInHierarchyAction, provider);
			disposeAction(fFindWriteReferencesInWorkingSetAction, provider);
		}
		fFindWriteReferencesAction= null;
		fFindWriteReferencesInHierarchyAction= null;
		fFindWriteReferencesInWorkingSetAction= null;
		updateGlobalActionHandlers();
		super.dispose();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_WORKSPACE, fFindWriteReferencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_HIERARCHY, fFindWriteReferencesInHierarchyAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_WORKING_SET, fFindWriteReferencesInWorkingSetAction);
		}
	}
	
	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}