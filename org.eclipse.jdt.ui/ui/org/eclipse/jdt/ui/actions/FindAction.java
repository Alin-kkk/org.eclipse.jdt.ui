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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import org.eclipse.search.ui.ISearchResultViewEntry;
import org.eclipse.search.ui.SearchUI;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.search.JavaSearchOperation;
import org.eclipse.jdt.internal.ui.search.JavaSearchResultCollector;
import org.eclipse.jdt.internal.ui.search.LRUWorkingSetsList;
import org.eclipse.jdt.internal.ui.search.SearchMessages;
import org.eclipse.jdt.internal.ui.search.SearchUtil;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * Abstract class for Java search actions.
 * <p>
 * Note: This class is for internal use only. Clients should not use this class.
 * </p>
 */
public abstract class FindAction extends SelectionDispatchAction {

	// A dummy which can't be selected in the UI
	private static final IJavaElement RETURN_WITHOUT_BEEP= JavaCore.create(JavaPlugin.getDefault().getWorkspace().getRoot());
		
	private Class[] fValidTypes;
	private JavaEditor fEditor;	


	FindAction(IWorkbenchSite site, String label, Class[] validTypes) {
		super(site);
		setText(label);
		fValidTypes= validTypes;
	}

	FindAction(JavaEditor editor, String label, Class[] validTypes) {
		this (editor.getEditorSite(), label, validTypes);
		fEditor= editor;
	}

	private boolean canOperateOn(IStructuredSelection sel) {
		return sel != null && !sel.isEmpty() && canOperateOn(getJavaElement(sel, true));
	}
		
	boolean canOperateOn(IJavaElement element) {
		if (fValidTypes == null || fValidTypes.length == 0)
			return false;
		
		if (element != null && element.exists()) {
			for (int i= 0; i < fValidTypes.length; i++) {
				if (fValidTypes[i].isInstance(element)) {
					if (element.getElementType() == IJavaElement.PACKAGE_FRAGMENT)
						return hasChildren((IPackageFragment)element);
					else
						return true;
				}
			}
		}
		return false;
	}
	
	private boolean hasChildren(IPackageFragment packageFragment) {
		try {
			return packageFragment.hasChildren();
		} catch (JavaModelException ex) {
			return false;
		}
	}

	private IJavaElement getJavaElement(IJavaElement o, boolean silent) {
		if (o == null)
			return null;
		switch (o.getElementType()) {
			case IJavaElement.COMPILATION_UNIT:
				return findType((ICompilationUnit)o, silent);
			case IJavaElement.CLASS_FILE:
				return findType((IClassFile)o);			
		}
		return o;
	}

	private IJavaElement getJavaElement(IMarker marker, boolean silent) {
		return getJavaElement(SearchUtil.getJavaElement(marker), silent);
	}


	private IJavaElement getJavaElement(Object o, boolean silent) {
		if (o instanceof IJavaElement)
			return getJavaElement((IJavaElement)o, silent);
		else if (o instanceof IMarker)
			return getJavaElement((IMarker)o, silent);
		else if (o instanceof ISelection)
			return getJavaElement((IStructuredSelection)o, silent);
		else if (o instanceof ISearchResultViewEntry)
			return getJavaElement((ISearchResultViewEntry)o, silent);
		return null;
	}

	IJavaElement getJavaElement(IStructuredSelection selection, boolean silent) {
		if (selection.size() == 1)
			// Selection only enabled if one element selected.
			return getJavaElement(selection.getFirstElement(), silent);
		return null;
	}

	private IJavaElement getJavaElement(ISearchResultViewEntry entry, boolean silent) {
		if (entry != null)
			return getJavaElement(entry.getSelectedMarker(), silent);
		return null;
	}

	private void showOperationUnavailableDialog() {
		MessageDialog.openInformation(getShell(), SearchMessages.getString("JavaElementAction.operationUnavailable.title"), getOperationUnavailableMessage()); //$NON-NLS-1$
	}	

	String getOperationUnavailableMessage() {
		return SearchMessages.getString("JavaElementAction.operationUnavailable.generic"); //$NON-NLS-1$
	}

	private IJavaElement findType(ICompilationUnit cu, boolean silent) {
		IType[] types= null;
		try {					
			types= cu.getAllTypes();
		} catch (JavaModelException ex) {
			// silent mode
			ExceptionHandler.log(ex, SearchMessages.getString("JavaElementAction.error.open.message")); //$NON-NLS-1$
			if (silent)
				return RETURN_WITHOUT_BEEP;
			else
				return null;
		}
		if (types.length == 1 || (silent && types.length > 0))
			return types[0];
		if (silent)
			return RETURN_WITHOUT_BEEP;
		if (types.length == 0)
			return null;
		String title= SearchMessages.getString("JavaElementAction.typeSelectionDialog.title"); //$NON-NLS-1$
		String message = SearchMessages.getString("JavaElementAction.typeSelectionDialog.message"); //$NON-NLS-1$
		int flags= (JavaElementLabelProvider.SHOW_DEFAULT);						

		ElementListSelectionDialog dialog= new ElementListSelectionDialog(getShell(), new JavaElementLabelProvider(flags));
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.setElements(types);
		
		if (dialog.open() == dialog.OK)
			return (IType)dialog.getFirstResult();
		else
			return RETURN_WITHOUT_BEEP;
	}

	private IType findType(IClassFile cf) {
		IType mainType;
		try {					
			mainType= cf.getType();
		} catch (JavaModelException ex) {
			ExceptionHandler.log(ex, SearchMessages.getString("JavaElementAction.error.open.message")); //$NON-NLS-1$
			return null;
		}
		return mainType;
	}
	
	/* 
	 * Method declared on SelectionChangedAction.
	 */
	protected void run(IStructuredSelection selection) {
		IJavaElement element= getJavaElement(selection, false);
		if (element == null) {
			showOperationUnavailableDialog();
			return;
		} 
		else if (element == RETURN_WITHOUT_BEEP)
			return;
		
		run(element);
	}

	/* 
	 * Method declared on SelectionChangedAction.
	 */
	protected void run(ITextSelection selection) {
		try {
			String title= SearchMessages.getString("SearchElementSelectionDialog.title"); //$NON-NLS-1$
			String message= SearchMessages.getString("SearchElementSelectionDialog.message"); //$NON-NLS-1$
			
			IJavaElement[] elements= SelectionConverter.codeResolve(fEditor);
			if (elements.length > 0 && canOperateOn(elements[0])) {
					IJavaElement element= elements[0];
					if (elements.length > 1)
						element= SelectionConverter.codeResolve(fEditor, getShell(), title, message);
					run(element);
			}
			else
				showOperationUnavailableDialog();
		} catch (JavaModelException ex) {
			JavaPlugin.log(ex);
			String title= SearchMessages.getString("Search.Error.search.title"); //$NON-NLS-1$
			String message= SearchMessages.getString("Search.Error.codeResolve"); //$NON-NLS-1$
			ErrorDialog.openError(getShell(), title, message, ex.getStatus());
		}
	}

	/* 
	 * Method declared on SelectionChangedAction.
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		setEnabled(canOperateOn(selection));
	}

	/* 
	 * Method declared on SelectionChangedAction.
	 */
	protected void selectionChanged(ITextSelection selection) {
		setEnabled(fEditor != null);
	}

	void run(IJavaElement element) {
		SearchUI.activateSearchResultView();
		Shell shell= JavaPlugin.getActiveWorkbenchShell();
		JavaSearchOperation op= null;
		try {
			op= makeOperation(element);
			if (op == null)
				return;
		} catch (JavaModelException ex) {
			ExceptionHandler.handle(ex, shell, SearchMessages.getString("Search.Error.search.title"), SearchMessages.getString("Search.Error.search.message")); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		IWorkspaceDescription workspaceDesc= JavaPlugin.getWorkspace().getDescription();
		boolean isAutoBuilding= workspaceDesc.isAutoBuilding();
		if (isAutoBuilding) {
			// disable auto-build during search operation
			workspaceDesc.setAutoBuilding(false);
			try {
				JavaPlugin.getWorkspace().setDescription(workspaceDesc);
			}
			catch (CoreException ex) {
			ExceptionHandler.handle(ex, shell, SearchMessages.getString("Search.Error.setDescription.title"), SearchMessages.getString("Search.Error.setDescription.message")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		try {
			new ProgressMonitorDialog(shell).run(true, true, op);
		} catch (InvocationTargetException ex) {
			ExceptionHandler.handle(ex, shell, SearchMessages.getString("Search.Error.search.title"), SearchMessages.getString("Search.Error.search.message")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch(InterruptedException e) {
		} finally {
			if (isAutoBuilding) {
				// enable auto-building again
				workspaceDesc= JavaPlugin.getWorkspace().getDescription();
				workspaceDesc.setAutoBuilding(true);
				try {
					JavaPlugin.getWorkspace().setDescription(workspaceDesc);
				}
				catch (CoreException ex) {
					ExceptionHandler.handle(ex, shell, SearchMessages.getString("Search.Error.setDescription.title"), SearchMessages.getString("Search.Error.setDescription.message")); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}				
		}
	}

	JavaSearchOperation makeOperation(IJavaElement element) throws JavaModelException {
		IType type= getType(element);
		return new JavaSearchOperation(JavaPlugin.getWorkspace(), element, getLimitTo(), getScope(type), getScopeDescription(type), getCollector());
	};

	abstract int getLimitTo();

	IJavaSearchScope getScope(IType element) throws JavaModelException {
		return SearchEngine.createWorkspaceScope();
	}

	JavaSearchResultCollector getCollector() {
		return new JavaSearchResultCollector();
	}
	
	String getScopeDescription(IType type) {
		return SearchMessages.getString("WorkspaceScope"); //$NON-NLS-1$
	}

	IType getType(IJavaElement element) {
		IType type= null;
		if (element.getElementType() == IJavaElement.TYPE)
			type= (IType)element;
		else if (element instanceof IMember)
			type= ((IMember)element).getDeclaringType();
		if (type != null) {
			ICompilationUnit cu= type.getCompilationUnit();
			if (cu == null)
				return type;
				
			IType wcType= (IType)getWorkingCopy(type);
			if (wcType != null)
				return wcType;
			else
				return type;
		}
		return null;
	}
	
	/**
	 * Tries to find the given element in a working copy.
	 */
	private IJavaElement getWorkingCopy(IJavaElement input) {
		try {
			if (input instanceof ICompilationUnit)
				return EditorUtility.getWorkingCopy((ICompilationUnit)input);
			else
				return EditorUtility.getWorkingCopy(input, true);
		} catch (JavaModelException ex) {
		}
		return null;
	}
}
