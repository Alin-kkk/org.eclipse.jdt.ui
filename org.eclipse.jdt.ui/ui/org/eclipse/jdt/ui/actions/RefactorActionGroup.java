/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.Page;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;

import org.eclipse.jdt.ui.IContextMenuConstants;

/**
 * Action group that adds refactor actions (e.g. Rename..., Move..., etc.)
 * to a context menu and the global menu bar.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class RefactorActionGroup extends ActionGroup {
	
	/**
	 * Pop-up menu: id of the refactor sub menu (value <code>org.eclipse.jdt.ui.
	 * refactoring.menu</code>).
	 * 
	 * @since 2.1
	 */
	public static final String MENU_ID= "org.eclipse.jdt.ui.refactoring.menu"; //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the reorg group of the refactor sub menu (value
	 * <code>reorgGroup</code>).
	 * 
	 * @since 2.1
	 */
	public static final String GROUP_REORG= "reorgGroup"; //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the type group of the refactor sub menu (value
	 * <code>typeGroup</code>).
	 * 
	 * @since 2.1
	 */
	public static final String GROUP_TYPE= "typeGroup"; //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the coding group of the refactor sub menu (value
	 * <code>codingGroup</code>).
	 * 
	 * @since 2.1
	 */
	public static final String GROUP_CODING= "codingGroup"; //$NON-NLS-1$

	private IWorkbenchSite fSite;
	private boolean fIsEditorOwner;
	private String fGroupName= IContextMenuConstants.GROUP_REORGANIZE;

 	private SelectionDispatchAction fMoveAction;
	private SelectionDispatchAction fRenameAction;
	private SelectionDispatchAction fModifyParametersAction;
	private SelectionDispatchAction fConvertAnonymousToNestedAction;
	private SelectionDispatchAction fConvertNestedToTopAction;
	
	private SelectionDispatchAction fPullUpAction;
	private SelectionDispatchAction fPushDownAction;
	private SelectionDispatchAction fExtractInterfaceAction;
	private SelectionDispatchAction fUseSupertypeAction;
	
	private SelectionDispatchAction fInlineAction;
	private SelectionDispatchAction fExtractMethodAction;
	private SelectionDispatchAction fExtractTempAction;
	private SelectionDispatchAction fExtractConstantAction;
    private SelectionDispatchAction fConvertLocalToFieldAction;
	private SelectionDispatchAction fSelfEncapsulateField;
	
	/**
	 * Creates a new <code>RefactorActionGroup</code>. The group requires
	 * that the selection provided by the part's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param part the view part that owns this action group
	 */
	public RefactorActionGroup(IViewPart part) {
		this(part.getSite());
	}	
	
	/**
	 * Creates a new <code>RefactorActionGroup</code>. The action requires
	 * that the selection provided by the page's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param page the page that owns this action group
	 */
	public RefactorActionGroup(Page page) {
		this(page.getSite());
	}
	
	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public RefactorActionGroup(CompilationUnitEditor editor, String groupName) {
		fSite= editor.getEditorSite();
		fIsEditorOwner= true;
		fGroupName= groupName;
		ISelectionProvider provider= editor.getSelectionProvider();
		ISelection selection= provider.getSelection();
		
		fRenameAction= new RenameAction(editor);
		fRenameAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.RENAME_ELEMENT);
		fRenameAction.update(selection);
		editor.setAction("RenameElement", fRenameAction); //$NON-NLS-1$
		
		fSelfEncapsulateField= new SelfEncapsulateFieldAction(editor);
		fSelfEncapsulateField.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELF_ENCAPSULATE_FIELD);
		fSelfEncapsulateField.update(selection);
		editor.setAction("SelfEncapsulateField", fSelfEncapsulateField); //$NON-NLS-1$
		
		fModifyParametersAction= new ModifyParametersAction(editor);
		fModifyParametersAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.MODIFY_METHOD_PARAMETERS);
		fModifyParametersAction.update(selection);
		editor.setAction("ModifyParameters", fModifyParametersAction); //$NON-NLS-1$

		fPullUpAction= new PullUpAction(editor);
		fPullUpAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.PULL_UP);
		fPullUpAction.update(selection);
		editor.setAction("PullUp", fPullUpAction); //$NON-NLS-1$

		fPushDownAction= new PushDownAction(editor);
		fPushDownAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.PUSH_DOWN);
		fPushDownAction.update(selection);
		editor.setAction("PushDown", fPushDownAction); //$NON-NLS-1$
		
		fMoveAction= new MoveAction(editor);
		fMoveAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.MOVE_ELEMENT);
		fMoveAction.update(selection);
		editor.setAction("MoveElement", fMoveAction); //$NON-NLS-1$
				
		fExtractTempAction= new ExtractTempAction(editor);
		fExtractTempAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.EXTRACT_LOCAL_VARIABLE);
		initAction(fExtractTempAction, provider, selection);
		editor.setAction("ExtractLocalVariable", fExtractTempAction); //$NON-NLS-1$

		fExtractConstantAction= new ExtractConstantAction(editor);
		fExtractConstantAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.EXTRACT_CONSTANT);
		initAction(fExtractConstantAction, provider, selection);
		editor.setAction("ExtractConstant", fExtractConstantAction); //$NON-NLS-1$

		fInlineAction= new InlineAction(editor);
		fInlineAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.INLINE);
		fInlineAction.update(selection);
		editor.setAction("Inline", fInlineAction); //$NON-NLS-1$
		
		fExtractMethodAction= new ExtractMethodAction(editor);
		fExtractMethodAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.EXTRACT_METHOD);
		initAction(fExtractMethodAction, provider, selection);
		editor.setAction("ExtractMethod", fExtractMethodAction); //$NON-NLS-1$

		fConvertLocalToFieldAction= new ConvertLocalToFieldAction(editor);
		fConvertLocalToFieldAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.PROMOTE_LOCAL_VARIABLE);
		initAction(fConvertLocalToFieldAction, provider, selection);
		editor.setAction("PromoteTemp", fConvertLocalToFieldAction); //$NON-NLS-1$

		fConvertAnonymousToNestedAction= new ConvertAnonymousToNestedAction(editor);
		fConvertAnonymousToNestedAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.CONVERT_ANONYMOUS_TO_NESTED);
		initAction(fConvertAnonymousToNestedAction, provider, selection);
		editor.setAction("ConvertAnonymousToNested", fConvertAnonymousToNestedAction); //$NON-NLS-1$

		fExtractInterfaceAction= new ExtractInterfaceAction(editor);
		fExtractInterfaceAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.EXTRACT_INTERFACE);
		fExtractInterfaceAction.update(selection);
		editor.setAction("ExtractInterface", fExtractInterfaceAction); //$NON-NLS-1$

		fConvertNestedToTopAction= new ConvertNestedToTopAction(editor);
		fConvertNestedToTopAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.MOVE_INNER_TO_TOP);
		fConvertNestedToTopAction.update(selection);
		editor.setAction("MoveInnerToTop", fConvertNestedToTopAction); //$NON-NLS-1$
		
		fUseSupertypeAction= new UseSupertypeAction(editor);
		fUseSupertypeAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.USE_SUPERTYPE);
		fUseSupertypeAction.update(selection);
		editor.setAction("UseSupertype", fUseSupertypeAction); //$NON-NLS-1$
	}

	private RefactorActionGroup(IWorkbenchSite site) {
		fSite= site;
		fIsEditorOwner= false;
		ISelectionProvider provider= fSite.getSelectionProvider();
		ISelection selection= provider.getSelection();
		
		fMoveAction= new MoveAction(site);
		initAction(fMoveAction, provider, selection);
		
		fRenameAction= new RenameAction(site);
		initAction(fRenameAction, provider, selection);
		
		fModifyParametersAction= new ModifyParametersAction(fSite);
		initAction(fModifyParametersAction, provider, selection);
		
		fPullUpAction= new PullUpAction(fSite);
		initAction(fPullUpAction, provider, selection);

		fPushDownAction= new PushDownAction(fSite);
		initAction(fPushDownAction, provider, selection);
		
		fSelfEncapsulateField= new SelfEncapsulateFieldAction(fSite);
		initAction(fSelfEncapsulateField, provider, selection);

		fExtractInterfaceAction= new ExtractInterfaceAction(fSite);
		initAction(fExtractInterfaceAction, provider, selection);

		fConvertNestedToTopAction= new ConvertNestedToTopAction(fSite);
		initAction(fConvertNestedToTopAction, provider, selection);

		fUseSupertypeAction= new UseSupertypeAction(fSite);
		initAction(fUseSupertypeAction, provider, selection);
		
		fInlineAction= new InlineAction(fSite);
		initAction(fInlineAction, provider, selection);
	}

	private static void initAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection){
		action.update(selection);
		provider.addSelectionChangedListener(action);
	};
	
	private boolean isEditorOwner() {
		return fIsEditorOwner;
	}
	
	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	public void fillActionBars(IActionBars actionBars) {
		super.fillActionBars(actionBars);
		actionBars.setGlobalActionHandler(JdtActionConstants.SELF_ENCAPSULATE_FIELD, fSelfEncapsulateField);
		actionBars.setGlobalActionHandler(JdtActionConstants.MOVE, fMoveAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.RENAME, fRenameAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.MODIFY_PARAMETERS, fModifyParametersAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.PULL_UP, fPullUpAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.PUSH_DOWN, fPushDownAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.EXTRACT_TEMP, fExtractTempAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.EXTRACT_CONSTANT, fExtractConstantAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.EXTRACT_METHOD, fExtractMethodAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.INLINE, fInlineAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.EXTRACT_INTERFACE, fExtractInterfaceAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.CONVERT_NESTED_TO_TOP, fConvertNestedToTopAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.USE_SUPERTYPE, fUseSupertypeAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.CONVERT_LOCAL_TO_FIELD, fConvertLocalToFieldAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.CONVERT_ANONYMOUS_TO_NESTED, fConvertAnonymousToNestedAction);
	}
	
	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	public void fillContextMenu(IMenuManager menu) {
		super.fillContextMenu(menu);
		addRefactorSubmenu(menu);
	}
	
	/*
	 * @see ActionGroup#dispose()
	 */
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		disposeAction(fSelfEncapsulateField, provider);
		disposeAction(fMoveAction, provider);
		disposeAction(fRenameAction, provider);
		disposeAction(fModifyParametersAction, provider);
		disposeAction(fPullUpAction, provider);
		disposeAction(fPushDownAction, provider);
		disposeAction(fExtractTempAction, provider);
		disposeAction(fExtractConstantAction, provider);
		disposeAction(fExtractMethodAction, provider);
		disposeAction(fInlineAction, provider);
		disposeAction(fExtractInterfaceAction, provider);
		disposeAction(fConvertNestedToTopAction, provider);
		disposeAction(fUseSupertypeAction, provider);
		disposeAction(fConvertLocalToFieldAction, provider);
		disposeAction(fConvertAnonymousToNestedAction, provider);
		super.dispose();
	}
	
	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
	
	private void addRefactorSubmenu(IMenuManager menu) {
		IMenuManager refactorSubmenu= new MenuManager(ActionMessages.getString("RefactorMenu.label"), MENU_ID);  //$NON-NLS-1$
		int added= 0;
		refactorSubmenu.add(new Separator(GROUP_REORG));
		added+= addAction(refactorSubmenu, fRenameAction);
		added+= addAction(refactorSubmenu, fMoveAction);
		added+= addAction(refactorSubmenu, fModifyParametersAction);
		added+= addAction(refactorSubmenu, fConvertAnonymousToNestedAction);
		added+= addAction(refactorSubmenu, fConvertNestedToTopAction);
		refactorSubmenu.add(new Separator(GROUP_TYPE));
		added+= addAction(refactorSubmenu, fPullUpAction);
		added+= addAction(refactorSubmenu, fPushDownAction);
		added+= addAction(refactorSubmenu, fExtractInterfaceAction);
		added+= addAction(refactorSubmenu, fUseSupertypeAction);
		refactorSubmenu.add(new Separator(GROUP_CODING));
		added+= addAction(refactorSubmenu, fInlineAction);
		added+= addAction(refactorSubmenu, fExtractMethodAction);
		added+= addAction(refactorSubmenu, fExtractTempAction);
		added+= addAction(refactorSubmenu, fExtractConstantAction);
		added+= addAction(refactorSubmenu, fConvertLocalToFieldAction);
		added+= addAction(refactorSubmenu, fSelfEncapsulateField);
		if (added > 0)
			menu.appendToGroup(fGroupName, refactorSubmenu);
	}
	
	private int addAction(IMenuManager menu, IAction action) {
		if (action != null && action.isEnabled()) {
			menu.add(action);
			return 1;
		}
		return 0;
	}
}
