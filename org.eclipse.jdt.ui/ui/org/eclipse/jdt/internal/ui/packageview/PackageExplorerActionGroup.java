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
package org.eclipse.jdt.internal.ui.packageview;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.MoveResourceAction;
import org.eclipse.ui.actions.NewWizardMenu;
import org.eclipse.ui.actions.OpenInNewWindowAction;
import org.eclipse.ui.actions.RenameResourceAction;
import org.eclipse.ui.views.framelist.BackAction;
import org.eclipse.ui.views.framelist.ForwardAction;
import org.eclipse.ui.views.framelist.FrameList;
import org.eclipse.ui.views.framelist.GoIntoAction;
import org.eclipse.ui.views.framelist.UpAction;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.ui.actions.BuildActionGroup;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.CustomFiltersActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.ImportActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.JdtActionConstants;
import org.eclipse.jdt.ui.actions.MemberFilterActionGroup;
import org.eclipse.jdt.ui.actions.NavigateActionGroup;
import org.eclipse.jdt.ui.actions.OpenAction;
import org.eclipse.jdt.ui.actions.ProjectActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;
import org.eclipse.jdt.ui.actions.RefreshAction;
import org.eclipse.jdt.ui.actions.ShowActionGroup;

import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;

import org.eclipse.jdt.internal.ui.preferences.AppearancePreferencePage;
import org.eclipse.jdt.internal.ui.preferences.JavaBasePreferencePage;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetFilterActionGroup;

class PackageExplorerActionGroup extends CompositeActionGroup implements ISelectionChangedListener {

	private PackageExplorerPart fPart;

	private GoIntoAction fZoomInAction;
 	private BackAction fBackAction;
	private ForwardAction fForwardAction;
	private UpAction fUpAction;
	private GotoTypeAction fGotoTypeAction;
	private GotoPackageAction fGotoPackageAction;
	
	private RenameResourceAction fRenameResourceAction;
	private MoveResourceAction fMoveResourceAction;
	
	private NavigateActionGroup fNavigateActionGroup;
	private BuildActionGroup fBuildActionGroup;
	private CCPActionGroup fCCPActionGroup;
	private WorkingSetFilterActionGroup fWorkingSetFilterActionGroup;
	
	private MemberFilterActionGroup fMemberFilterActionGroup;
	private CustomFiltersActionGroup fCustomFiltersActionGroup;	

 	
	public PackageExplorerActionGroup(PackageExplorerPart part) {
		super();
		fPart= part;
		IWorkbenchPartSite site = fPart.getSite();
		Shell shell= site.getShell();
		ISelectionProvider provider= site.getSelectionProvider();
		IStructuredSelection selection= (IStructuredSelection) provider.getSelection();
		setGroups(new ActionGroup[] {
			fNavigateActionGroup= new NavigateActionGroup(fPart), 
			new ShowActionGroup(fPart), 
			fCCPActionGroup= new CCPActionGroup(fPart),
			new RefactorActionGroup(fPart),
			new ImportActionGroup(fPart),
			new GenerateActionGroup(fPart), 
			fBuildActionGroup= new BuildActionGroup(fPart),
			new JavaSearchActionGroup(fPart),
			new ProjectActionGroup(fPart), 
			fWorkingSetFilterActionGroup= new WorkingSetFilterActionGroup(part.getViewer(), JavaUI.ID_PACKAGES, shell, createTitleUpdater())});
		
		PackagesFrameSource frameSource= new PackagesFrameSource(fPart);
		FrameList frameList= new FrameList(frameSource);
		frameSource.connectTo(frameList);
			
		fZoomInAction= new GoIntoAction(frameList);
		fBackAction= new BackAction(frameList);
		fForwardAction= new ForwardAction(frameList);
		fUpAction= new UpAction(frameList);
		
		fRenameResourceAction= new RenameResourceAction(shell);		
		fMoveResourceAction= new MoveResourceAction(shell);
		
		fGotoTypeAction= new GotoTypeAction(fPart);
		fGotoPackageAction= new GotoPackageAction(fPart);
		
		fMemberFilterActionGroup= new MemberFilterActionGroup(fPart.getViewer(), "PackageView");  //$NON-NLS-1$
		fCustomFiltersActionGroup= new CustomFiltersActionGroup(fPart, fPart.getViewer());
		
		provider.addSelectionChangedListener(this);
		update(selection);
	}

	public void dispose() {
		ISelectionProvider provider= fPart.getSite().getSelectionProvider();
		provider.removeSelectionChangedListener(this);
		super.dispose();
	}
	
	//---- Selection changed listener ---------------------------------------------------------
	
	public void selectionChanged(SelectionChangedEvent event) {
		fRenameResourceAction.selectionChanged(event);
		fMoveResourceAction.selectionChanged(event);
		IStructuredSelection selection= (IStructuredSelection)event.getSelection();
		update(selection);
	}

	private void update(IStructuredSelection selection) {
		int size= selection.size();
		Object element= selection.getFirstElement();
		IActionBars actionBars= fPart.getViewSite().getActionBars();
		if (size == 1 && element instanceof IResource) {
			actionBars.setGlobalActionHandler(IWorkbenchActionConstants.RENAME, fRenameResourceAction);
			actionBars.setGlobalActionHandler(IWorkbenchActionConstants.MOVE, fMoveResourceAction);
		} else {
			actionBars.setGlobalActionHandler(IWorkbenchActionConstants.RENAME, null);
			actionBars.setGlobalActionHandler(IWorkbenchActionConstants.MOVE, null);
		}
		actionBars.updateActionBars();
	}

	//---- Persistent state -----------------------------------------------------------------------

	/* package */ void restoreState(IMemento memento) {
		fMemberFilterActionGroup.restoreState(memento);
		fWorkingSetFilterActionGroup.restoreState(memento);
		fCustomFiltersActionGroup.restoreState(memento);
		fPart.getViewer().getControl().setRedraw(false);
		fPart.getViewer().refresh();
		fPart.getViewer().getControl().setRedraw(true);
	}
	
	/* package */ void saveState(IMemento memento) {
		fMemberFilterActionGroup.saveState(memento);
		fWorkingSetFilterActionGroup.saveState(memento);
		fCustomFiltersActionGroup.saveState(memento);
	}

	//---- Action Bars ----------------------------------------------------------------------------

	public void fillActionBars(IActionBars actionBars) {
		super.fillActionBars(actionBars);
		setGlobalActionHandlers(actionBars);
		fillToolBar(actionBars.getToolBarManager());
		fillViewMenu(actionBars.getMenuManager());
		fCustomFiltersActionGroup.fillActionBars(actionBars);
	}

	private void setGlobalActionHandlers(IActionBars actionBars) {
		// Navigate Go Into and Go To actions.
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.GO_INTO, fZoomInAction);
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.BACK, fBackAction);
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.FORWARD, fForwardAction);
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.UP, fUpAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.GOTO_TYPE, fGotoTypeAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.GOTO_PACKAGE, fGotoPackageAction);
	}

	/* package */ void fillToolBar(IToolBarManager toolBar) {
		toolBar.removeAll();
		
		toolBar.add(fBackAction);
		toolBar.add(fForwardAction);
		toolBar.add(fUpAction);
		
		if (AppearancePreferencePage.showCompilationUnitChildren()) {
			toolBar.add(new Separator());
			fMemberFilterActionGroup.contributeToToolBar(toolBar);
		}
	}
	
	/* package */ void fillViewMenu(IMenuManager menu) {
	
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS+"-end"));//$NON-NLS-1$		
	}

	/* package */ void handleSelectionChanged(SelectionChangedEvent event) {
		fZoomInAction.update();
	}

	//---- Context menu -------------------------------------------------------------------------

	public void fillContextMenu(IMenuManager menu) {		
		IStructuredSelection selection= (IStructuredSelection)getContext().getSelection();
		int size= selection.size();
		Object element= selection.getFirstElement();
		IJavaElement jElement= element instanceof IJavaElement ? (IJavaElement)element : null;
		
		if (size == 0 || (size == 1 && (isNewTarget(jElement) || element instanceof IContainer))) {
			IMenuManager newMenu= new MenuManager(PackagesMessages.getString("PackageExplorer.new")); //$NON-NLS-1$
			menu.appendToGroup(IContextMenuConstants.GROUP_NEW, newMenu);
			new NewWizardMenu(newMenu, fPart.getSite().getWorkbenchWindow(), false);
		}
				
		addGotoMenu(menu, element, size);
		
		addOpenNewWindowAction(menu, element);
		
		super.fillContextMenu(menu);
	}
	
	 private void addGotoMenu(IMenuManager menu, Object element, int size) {
		
		if (size == 1 && fPart.getViewer().isExpandable(element) && (isGoIntoTarget(element) || element instanceof IContainer))
			menu.appendToGroup(IContextMenuConstants.GROUP_GOTO, fZoomInAction);
	}
	
	private boolean isNewTarget(IJavaElement element) {
		if (element == null)
			return false;
		int type= element.getElementType();
		return type == IJavaElement.JAVA_PROJECT ||
			type == IJavaElement.PACKAGE_FRAGMENT_ROOT || 
			type == IJavaElement.PACKAGE_FRAGMENT ||
			type == IJavaElement.COMPILATION_UNIT ||
			type == IJavaElement.TYPE;
	}
	
	private boolean isGoIntoTarget(Object element) {
		if (element == null)
			return false;
		if (element instanceof IJavaElement) {
			int type= ((IJavaElement)element).getElementType();
			return type == IJavaElement.JAVA_PROJECT || 
				type == IJavaElement.PACKAGE_FRAGMENT_ROOT || 
				type == IJavaElement.PACKAGE_FRAGMENT;
		}
		return false;
	}

	private void addOpenNewWindowAction(IMenuManager menu, Object element) {
		if (element instanceof IJavaElement) {
			try {
				element= ((IJavaElement)element).getCorrespondingResource();
			} catch(JavaModelException e) {
			}
		}
		if (!(element instanceof IContainer))
			return;
		menu.appendToGroup(
			IContextMenuConstants.GROUP_OPEN, 
			new OpenInNewWindowAction(fPart.getSite().getWorkbenchWindow(), (IContainer)element));
	}

	//---- Key board and mouse handling ------------------------------------------------------------

	/* package*/ void handleDoubleClick(DoubleClickEvent event) {
		TreeViewer viewer= fPart.getViewer();
		Object element= ((IStructuredSelection)event.getSelection()).getFirstElement();
		if (viewer.isExpandable(element)) {
			if (JavaBasePreferencePage.doubleClickGoesInto()) {
				// don't zoom into compilation units and class files
				if (element instanceof IOpenable && 
					!(element instanceof ICompilationUnit) && 
					!(element instanceof IClassFile)) {
					fZoomInAction.run();
				}
			} else {
				if (element instanceof ICompilationUnit && OpenStrategy.getOpenMethod() == OpenStrategy.DOUBLE_CLICK)
					return;
				viewer.setExpandedState(element, !viewer.getExpandedState(element));
			}
		}
	}
	
	/* package */ void handleOpen(OpenEvent event) {
		IAction openAction= fNavigateActionGroup.getOpenAction();
		if (openAction != null && openAction.isEnabled()) {
			openAction.run();
			return;
		}
	}
	
	/* package */ void handleKeyEvent(KeyEvent event) {
		if (event.stateMask != 0) 
			return;		
		
		if (event.keyCode == SWT.F5) {
			IAction refreshAction= fBuildActionGroup.getRefreshAction();
			if (refreshAction != null && refreshAction.isEnabled())
				refreshAction.run();
		} else if (event.character == SWT.DEL) {
			IAction delete= fCCPActionGroup.getDeleteAction();
			if (delete != null && delete.isEnabled())
				delete.run();
		}
	}
	
	private IPropertyChangeListener createTitleUpdater() {
		return new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				String property= event.getProperty();
				if (IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE.equals(property)) {
					IWorkingSet workingSet= (IWorkingSet)event.getNewValue();
					String workingSetName= null;
					if (workingSet != null)
						workingSetName= workingSet.getName();
					fPart.setWorkingSetName(workingSetName);
					fPart.updateTitle();
				}
			}
		};
	}
}

