/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.OpenTypeHierarchyUtil;

/**
 * This action opens a type hierarchy on the selected type.
 * <p>
 * The action is applicable to selections containing elements of type
 * <code>IType</code>.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class OpenTypeHierarchyAction extends SelectionDispatchAction {
	
	private JavaEditor fEditor;
	
	/**
	 * Creates a new <code>OpenTypeHierarchyAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public OpenTypeHierarchyAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.getString("OpenTypeHierarchyAction.label")); //$NON-NLS-1$
		setToolTipText(ActionMessages.getString("OpenTypeHierarchyAction.tooltip")); //$NON-NLS-1$
		setDescription(ActionMessages.getString("OpenTypeHierarchyAction.description")); //$NON-NLS-1$
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.OPEN_TYPE_HIERARCHY_ACTION);
	}
	
	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 */
	public OpenTypeHierarchyAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void selectionChanged(ITextSelection selection) {
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		setEnabled(canEnabled(selection));
	}
	
	private boolean canEnabled(IStructuredSelection selection) {
		if (selection.size() != 1)
			return false;
		Object input= selection.getFirstElement();
		if (!(input instanceof IJavaElement))
			return false;
		switch (((IJavaElement)input).getElementType()) {
			case IJavaElement.INITIALIZER:
			case IJavaElement.METHOD:
			case IJavaElement.FIELD:
			case IJavaElement.TYPE:
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			case IJavaElement.JAVA_PROJECT:
			case IJavaElement.PACKAGE_FRAGMENT:
			case IJavaElement.PACKAGE_DECLARATION:
			case IJavaElement.IMPORT_DECLARATION:	
			case IJavaElement.CLASS_FILE:
			case IJavaElement.COMPILATION_UNIT:
				return true;
			default:
				return false;
		}
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void run(ITextSelection selection) {
		IJavaElement[] elements= SelectionConverter.codeResolveOrInputHandled(fEditor, getShell(), getDialogTitle());
		if (elements == null)
			return;
		List candidates= new ArrayList(elements.length);
		for (int i= 0; i < elements.length; i++) {
			IJavaElement[] resolvedElements= OpenTypeHierarchyUtil.getCandidates(elements[i]);
			if (resolvedElements != null)	
				candidates.addAll(Arrays.asList(resolvedElements));
		}
		run((IJavaElement[])candidates.toArray(new IJavaElement[candidates.size()]));
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void run(IStructuredSelection selection) {
		if (selection.size() != 1)
			return;
		List result= new ArrayList(1);
		IStatus status= compileCandidates(result, selection.getFirstElement());
		if (status.isOK()) {
			run((IJavaElement[]) result.toArray(new IJavaElement[result.size()]));
		} else {
			ErrorDialog.openError(getShell(), getDialogTitle(), ActionMessages.getString("OpenTypeHierarchyAction.messages.title"), status); //$NON-NLS-1$
		}
	}
	
	private void run(IJavaElement[] elements) {
		if (elements.length == 0) {
			getShell().getDisplay().beep();
			return;
		}
		OpenTypeHierarchyUtil.open(elements, getSite().getWorkbenchWindow());
	}
	
	private static String getDialogTitle() {
		return ActionMessages.getString("OpenTypeHierarchyAction.dialog.title"); //$NON-NLS-1$
	}
	
	private static IStatus compileCandidates(List result, Object input) {
		if (!(input instanceof IJavaElement))
			return createStatus(ActionMessages.getString("OpenTypeHierarchyAction.messages.no_java_element")); //$NON-NLS-1$
			
		IStatus ok= new Status(IStatus.OK, JavaPlugin.getPluginId(), 0, "", null); //$NON-NLS-1$
		IJavaElement elem= (IJavaElement)input;
		try {
			switch (elem.getElementType()) {
				case IJavaElement.INITIALIZER:
				case IJavaElement.METHOD:
				case IJavaElement.FIELD:
				case IJavaElement.TYPE:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				case IJavaElement.JAVA_PROJECT:
					result.add(elem);
					return ok;
				case IJavaElement.PACKAGE_FRAGMENT:
					if (((IPackageFragment)elem).containsJavaResources()) {
						result.add(elem);
						return ok;
					}
					return createStatus(ActionMessages.getString("OpenTypeHierarchyAction.messages.no_java_resources")); //$NON-NLS-1$
				case IJavaElement.PACKAGE_DECLARATION:
					result.add(elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT));
					return ok;
				case IJavaElement.IMPORT_DECLARATION:	
					IImportDeclaration decl= (IImportDeclaration) elem;
					if (decl.isOnDemand()) {
						elem= JavaModelUtil.findTypeContainer(elem.getJavaProject(), Signature.getQualifier(elem.getElementName()));
					} else {
						elem= elem.getJavaProject().findType(elem.getElementName());
					}
					if (elem != null) {
						result.add(elem);
						return ok;
					}
					elem= elem.getAncestor(IJavaElement.COMPILATION_UNIT);				
				case IJavaElement.CLASS_FILE:
					result.add(((IClassFile)input).getType());
					return ok;				
				case IJavaElement.COMPILATION_UNIT:
					ICompilationUnit cu= (ICompilationUnit)elem;
					IType[] types= cu.getTypes();
					if (types.length > 0) {
						result.addAll(Arrays.asList(types));
						return ok;
					}
					return createStatus(ActionMessages.getString("OpenTypeHierarchyAction.messages.no_types")); //$NON-NLS-1$
			}
		} catch (JavaModelException e) {
			return e.getStatus();
		}
		return createStatus(ActionMessages.getString("OpenTypeHierarchyAction.messages.no_valid_java_element")); //$NON-NLS-1$
	}
	
	private static IStatus createStatus(String message) {
		return new Status(IStatus.INFO, JavaPlugin.getPluginId(), IJavaStatusConstants.INTERNAL_ERROR, message, null);
	}			
}