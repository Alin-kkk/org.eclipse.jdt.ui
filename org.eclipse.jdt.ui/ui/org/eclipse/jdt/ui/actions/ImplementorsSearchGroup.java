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
 * Action group that adds the search for implementors actions to a
 * context menu and the global menu bar.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class ImplementorsSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.getString("group.implementors"); //$NON-NLS-1$

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;
	
	private String fGroupId;

	private FindImplementorsAction fFindImplementorsAction;
	private FindImplementorsInWorkingSetAction fFindImplementorsInWorkingSetAction;

	/**
	 * Creates a new <code>ImplementorsSearchGroup</code>. The group 
	 * requires that the selection provided by the site's selection provider 
	 * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param part the view part that owns this action group
	 */
	public ImplementorsSearchGroup(IWorkbenchSite site) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindImplementorsAction= new FindImplementorsAction(site);
		fFindImplementorsInWorkingSetAction= new FindImplementorsInWorkingSetAction(site);

		// register the actions as selection listeners
		ISelectionProvider provider= fSite.getSelectionProvider();
		ISelection selection= provider.getSelection();
		registerAction(fFindImplementorsAction, provider, selection);
		registerAction(fFindImplementorsInWorkingSetAction, provider, selection);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public ImplementorsSearchGroup(JavaEditor editor) {
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindImplementorsAction= new FindImplementorsAction(fEditor);
		fFindImplementorsAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_WORKSPACE);
		fEditor.setAction("SearchImplementorsInWorkspace", fFindImplementorsAction); //$NON-NLS-1$

		fFindImplementorsInWorkingSetAction= new FindImplementorsInWorkingSetAction(fEditor);
		fFindImplementorsInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_WORKING_SET);
		fEditor.setAction("SearchImplementorsInWorkingSet", fFindImplementorsInWorkingSetAction); //$NON-NLS-1$
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection){
		action.update(selection);
		provider.addSelectionChangedListener(action);
	};

	private FindAction[] getActions(ISelection sel) {
		ArrayList actions= new ArrayList(SearchUtil.LRU_WORKINGSET_LIST_SIZE + 2);		
		actions.add(fFindImplementorsAction);
		actions.add(fFindImplementorsInWorkingSetAction);
			
		Iterator iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			IWorkingSet[] workingSets= (IWorkingSet[])iter.next();
			FindAction action;
			if (fEditor != null)
				action= new WorkingSetFindAction(fEditor, new FindImplementorsInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
			else
				action= new WorkingSetFindAction(fSite, new FindImplementorsInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
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
			disposeAction(fFindImplementorsAction, provider);
			disposeAction(fFindImplementorsInWorkingSetAction, provider);
		}
		super.dispose();
		fFindImplementorsAction= null;
		fFindImplementorsInWorkingSetAction= null;
		updateGlobalActionHandlers();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_IMPLEMENTORS_IN_WORKSPACE, fFindImplementorsAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_IMPLEMENTORS_IN_WORKING_SET, fFindImplementorsInWorkingSetAction);
		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}


