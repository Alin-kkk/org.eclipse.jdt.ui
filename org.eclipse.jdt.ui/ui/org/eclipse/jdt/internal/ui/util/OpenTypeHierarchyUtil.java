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
package org.eclipse.jdt.internal.ui.util;import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.preferences.JavaBasePreferencePage;
import org.eclipse.jdt.internal.ui.typehierarchy.TypeHierarchyViewPart;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

public class OpenTypeHierarchyUtil {
	
	private OpenTypeHierarchyUtil() {
	}

	/**
	 * @deprecated Use org.eclipse.jdt.ui.actions.OpenTypeHierarchyAction directly
	 */
	public static boolean canOperateOn(ISelection s) {
		Object element= getElement(s);
			
		return (element != null) 
			? (getCandidates(element) != null) 
			: false;
	}
	
	public static TypeHierarchyViewPart open(IJavaElement element, IWorkbenchWindow window) {
		IJavaElement[] candidates= getCandidates(element);
		if (candidates != null) {
			return open(candidates, window);
		}
		return null;
	}	
	
	public static TypeHierarchyViewPart open(IJavaElement[] candidates, IWorkbenchWindow window) {
		Assert.isTrue(candidates != null && candidates.length != 0);
			
		IJavaElement input= null;
		if (candidates.length > 1) {
			input= selectCandidate(candidates, window.getShell());
		} else {
			input= candidates[0];
		}
		if (input == null)
			return null;
			
		try {
			if (JavaBasePreferencePage.openTypeHierarchyInPerspective()) {
				return openInPerspective(window, input);
			} else {
				return openInViewPart(window, input);
			}
				
		} catch (WorkbenchException e) {
			ExceptionHandler.handle(e, window.getShell(),
				JavaUIMessages.getString("OpenTypeHierarchyUtil.error.open_perspective"), //$NON-NLS-1$
				e.getMessage());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, window.getShell(),
				JavaUIMessages.getString("OpenTypeHierarchyUtil.error.open_editor"), //$NON-NLS-1$
				e.getMessage());
		}
		return null;
	}

	private static TypeHierarchyViewPart openInViewPart(IWorkbenchWindow window, IJavaElement input) {
		IWorkbenchPage page= window.getActivePage();
		try {
			// 1GEUMSG: ITPJUI:WINNT - Class hierarchy not shown when fast view
			if (input instanceof IMember) {
				openEditor(input);
			}
			TypeHierarchyViewPart result= (TypeHierarchyViewPart)page.showView(JavaUI.ID_TYPE_HIERARCHY);
			result.setInputElement(input);
			if (input instanceof IMember) {
				result.selectMember((IMember) input);
			}
			return result;
		} catch (CoreException e) {
			ExceptionHandler.handle(e, window.getShell(), 
				JavaUIMessages.getString("OpenTypeHierarchyUtil.error.open_view"), e.getMessage()); //$NON-NLS-1$
		}
		return null;		
	}
	
	private static TypeHierarchyViewPart openInPerspective(IWorkbenchWindow window, IJavaElement input) throws WorkbenchException, JavaModelException {
		IWorkbench workbench= JavaPlugin.getDefault().getWorkbench();
		// The problem is that the input element can be a working copy. So we first convert it to the original element if
		// it exists.
		if (input instanceof IMember) {
			ICompilationUnit cu= ((IMember)input).getCompilationUnit();
			if (cu != null && cu.isWorkingCopy()) {
				IJavaElement je= cu.getOriginal(input);
				if (je != null)
					input= je;
			}
		}
		IWorkbenchPage page= workbench.showPerspective(JavaUI.ID_HIERARCHYPERSPECTIVE, window, input);
		if (input instanceof IMember) {
			openEditor(input);
		}
		return (TypeHierarchyViewPart)page.showView(JavaUI.ID_TYPE_HIERARCHY);
	}

	private static void openEditor(Object input) throws PartInitException, JavaModelException {
		IEditorPart part= EditorUtility.openInEditor(input, true);
		if (input instanceof IJavaElement)
			EditorUtility.revealInEditor(part, (IJavaElement) input);
	}
	
	private static IWorkbenchPage findPage(IWorkbenchWindow window) {
		IPerspectiveRegistry registry= PlatformUI.getWorkbench().getPerspectiveRegistry();
		IPerspectiveDescriptor pd= registry.findPerspectiveWithId(JavaUI.ID_HIERARCHYPERSPECTIVE);
		IWorkbenchPage pages[]= window.getPages();
		for (int i= 0; i < pages.length; i++) {
			IWorkbenchPage page= pages[i];
			if (page.getPerspective().equals(pd))
				return page;
		}
		return null;
	}
	
	private static IJavaElement selectCandidate(IJavaElement[] candidates, Shell shell) {		
		int flags= (JavaElementLabelProvider.SHOW_DEFAULT);						

		ElementListSelectionDialog dialog= new ElementListSelectionDialog(shell,			
			new JavaElementLabelProvider(flags));
		dialog.setTitle(JavaUIMessages.getString("OpenTypeHierarchyUtil.selectionDialog.title"));  //$NON-NLS-1$
		dialog.setMessage(JavaUIMessages.getString("OpenTypeHierarchyUtil.selectionDialog.message")); //$NON-NLS-1$
		dialog.setElements(candidates);

		if (dialog.open() == dialog.OK) {
			Object[] elements= dialog.getResult();
			if ((elements != null) && (elements.length == 1))
				return (IJavaElement) elements[0];
		}
		return null;
	}
	
	private static Object getElement(ISelection s) {
		if (!(s instanceof IStructuredSelection))
			return null;
		IStructuredSelection selection= (IStructuredSelection)s;
		if (selection.size() != 1)
			return null;
		return selection.getFirstElement();	
	}
	
	/**
	 * Converts the input to a possible input candidates
	 */	
	public static IJavaElement[] getCandidates(Object input) {
		if (!(input instanceof IJavaElement)) {
			return null;
		}
		try {
			IJavaElement elem= (IJavaElement) input;
			switch (elem.getElementType()) {
				case IJavaElement.INITIALIZER:
				case IJavaElement.METHOD:
				case IJavaElement.FIELD:
				case IJavaElement.TYPE:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				case IJavaElement.JAVA_PROJECT:
					return new IJavaElement[] { elem };
				case IJavaElement.PACKAGE_FRAGMENT:
					if (((IPackageFragment)elem).containsJavaResources())
						return new IJavaElement[] {elem};
					break;
				case IJavaElement.PACKAGE_DECLARATION:
					return new IJavaElement[] { elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT) };
				case IJavaElement.IMPORT_DECLARATION:	
					IImportDeclaration decl= (IImportDeclaration) elem;
					if (decl.isOnDemand()) {
						elem= JavaModelUtil.findTypeContainer(elem.getJavaProject(), Signature.getQualifier(elem.getElementName()));
					} else {
						elem= elem.getJavaProject().findType(elem.getElementName());
					}
					if (elem == null)
						return null;
					return new IJavaElement[] {elem};
					
				case IJavaElement.CLASS_FILE:
					return new IJavaElement[] { ((IClassFile)input).getType() };				
				case IJavaElement.COMPILATION_UNIT: {
						ICompilationUnit cu= (ICompilationUnit) elem.getAncestor(IJavaElement.COMPILATION_UNIT);
					if (cu != null) {
						IType[] types= cu.getTypes();
						if (types.length > 0) {
							return types;
						}
					}
					break;
				}					
				default:
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
		return null;	
	}	
}
