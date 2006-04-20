/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.keys.IBindingService;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.JdtActionConstants;
import org.eclipse.jdt.ui.actions.SurroundWithTryCatchAction;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;

public class SurroundWithActionGroup extends ActionGroup {
	
	private CompilationUnitEditor fEditor;
	private SurroundWithTryCatchAction fSurroundWithTryCatchAction;
	
	public SurroundWithActionGroup(CompilationUnitEditor editor) {
		fEditor= editor;
		fSurroundWithTryCatchAction= createSurroundWithTryCatchAction(fEditor);
	}
	
	public void fillActionBars(IActionBars actionBar) {
		actionBar.setGlobalActionHandler(JdtActionConstants.SURROUND_WITH_TRY_CATCH, fSurroundWithTryCatchAction);
	}
	
	/**
	 * The Menu to show when right click on the editor
	 * {@inheritDoc}
	 */
	public void fillContextMenu(IMenuManager menu) {
		String menuText= ActionMessages.SurroundWithTemplateMenuAction_SurroundWithTemplateSubMenuName;
				
		String shortcutString= getShortcutString();
		if (shortcutString != null) {
			String[] args= new String[] { menuText, shortcutString};
			menuText= Messages.format(ActionMessages.QuickMenuAction_menuTextWithShortcut, args); 
		}
		
		MenuManager subMenu = new MenuManager(menuText, SurroundWithTemplateMenuAction.SURROUND_WITH_QUICK_MENU_ACTION_ID);	
				
		if (SurroundWithTemplateMenuAction.fillMenu(subMenu, fEditor, fSurroundWithTryCatchAction))
			menu.appendToGroup(GenerateActionGroup.GROUP_CODE, subMenu);
	}
	
	private String getShortcutString() {
		IBindingService bindingService= (IBindingService)PlatformUI.getWorkbench().getAdapter(IBindingService.class);
		if (bindingService == null)
			return null;
		return bindingService.getBestActiveBindingFormattedFor(SurroundWithTemplateMenuAction.SURROUND_WITH_QUICK_MENU_ACTION_ID);
	}
	
	private static SurroundWithTryCatchAction createSurroundWithTryCatchAction(CompilationUnitEditor editor) {
		SurroundWithTryCatchAction result= new SurroundWithTryCatchAction(editor);
		result.setText(ActionMessages.SurroundWithTemplateMenuAction_SurroundWithTryCatchActionName);
		result.setActionDefinitionId(IJavaEditorActionDefinitionIds.SURROUND_WITH_TRY_CATCH);
		editor.setAction("SurroundWithTryCatch", result); //$NON-NLS-1$		
		return result;
	}
}
