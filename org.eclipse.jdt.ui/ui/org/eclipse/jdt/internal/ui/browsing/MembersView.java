/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.browsing;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IMemento;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.OverrideIndicatorLabelDecorator;

import org.eclipse.jdt.ui.actions.MemberFilterActionGroup;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;

import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTreeViewer;

public class MembersView extends JavaBrowsingPart {
	
	private MemberFilterActionGroup fMemberFilterActionGroup;

	/**
	 * Creates and returns the label provider for this part.
	 * 
	 * @return	the label provider
	 * @see	ILabelProvider
	 */
	protected ILabelProvider createLabelProvider() {
		return new AppearanceAwareLabelProvider(
						AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS,
						AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS,
						AppearanceAwareLabelProvider.getDecorators(true, new OverrideIndicatorLabelDecorator(null))
						);
	}

	/**
	 * Returns the context ID for the Help system
	 * 
	 * @return	the string used as ID for the Help context
	 */
	protected String getHelpContextId() {
		return IJavaHelpContextIds.MEMBERS_VIEW;
	}

	/**
	 * Creates the the viewer of this part.
	 * 
	 * @param parent	the parent for the viewer
	 */
	protected StructuredViewer createViewer(Composite parent) {
		ProblemTreeViewer viewer= new ProblemTreeViewer(parent, SWT.MULTI);
		fMemberFilterActionGroup= new MemberFilterActionGroup(viewer, JavaUI.ID_MEMBERS_VIEW);
		return viewer;
	}

	protected void fillToolBar(IToolBarManager tbm) {
		tbm.add(new LexicalSortingAction(getViewer(), JavaUI.ID_MEMBERS_VIEW));
		fMemberFilterActionGroup.contributeToToolBar(tbm);
	}
	
	/**
	 * Answers if the given <code>element</code> is a valid
	 * input for this part.
	 * 
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid input
	 */
	protected boolean isValidInput(Object element) {
		if (element instanceof IType) {
			IType type= (IType)element;
			return type.isBinary() || type.getDeclaringType() == null;
		}
		return false;
	}
	
	/**
	 * Answers if the given <code>element</code> is a valid
	 * element for this part.
	 * 
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid element
	 */
	protected boolean isValidElement(Object element) {
		if (element instanceof IMember)
			return super.isValidElement(((IMember)element).getDeclaringType());
		else if (element instanceof IImportDeclaration)
			return isValidElement(((IJavaElement)element).getParent());
		else if (element instanceof IImportContainer) {
			Object input= getViewer().getInput();
			if (input instanceof IJavaElement) {
				ICompilationUnit cu= (ICompilationUnit)((IJavaElement)input).getAncestor(IJavaElement.COMPILATION_UNIT);
				if (cu != null) {
					ICompilationUnit importContainerCu= (ICompilationUnit)((IJavaElement)element).getAncestor(IJavaElement.COMPILATION_UNIT);
					return cu.equals(importContainerCu);
				} else {
					IClassFile cf= (IClassFile)((IJavaElement)input).getAncestor(IJavaElement.CLASS_FILE);
					IClassFile importContainerCf= (IClassFile)((IJavaElement)element).getAncestor(IJavaElement.CLASS_FILE);
					return cf != null && cf.equals(importContainerCf);
				}
			}
		}
		return false;
	}

	/**
	 * Finds the element which has to be selected in this part.
	 * 
	 * @param je	the Java element which has the focus
	 */
	protected IJavaElement findElementToSelect(IJavaElement je) {
		if (je == null)
			return null;

		switch (je.getElementType()) {
			case IJavaElement.TYPE:
				if (((IType)je).getDeclaringType() == null)
					return null;
			case IJavaElement.METHOD:
			case IJavaElement.FIELD:
			case IJavaElement.PACKAGE_DECLARATION:
			case IJavaElement.IMPORT_CONTAINER:
			case IJavaElement.IMPORT_DECLARATION:
				je= getSuitableJavaElement(je);
				if (je != null)
					return je;
			default:
				return null; 
		}
	}

	/**
	 * Finds the closest Java element which can be used as input for
	 * this part and has the given Java element as child
	 * 
	 * @param 	je 	the Java element for which to search the closest input
	 * @return	the closest Java element used as input for this part
	 */
	protected IJavaElement findInputForJavaElement(IJavaElement je) {
		if (je == null || !je.exists())
			return null;
			
		switch (je.getElementType()) {
			case IJavaElement.TYPE:
				IType type= ((IType)je).getDeclaringType();
				if (type == null)
					return je;
				else
					return findInputForJavaElement(type);
			case IJavaElement.COMPILATION_UNIT:
				return getTypeForCU((ICompilationUnit)je);
			case IJavaElement.CLASS_FILE:
				try {
					return findInputForJavaElement(((IClassFile)je).getType());
				} catch (JavaModelException ex) {
					return null;
				}
			case IJavaElement.IMPORT_DECLARATION:
				return findInputForJavaElement(je.getParent());
			case IJavaElement.PACKAGE_DECLARATION:
			case IJavaElement.IMPORT_CONTAINER:
				IJavaElement parent= je.getParent();
				if (parent instanceof ICompilationUnit) {
					IType[] types;
					try {
						types= ((ICompilationUnit)parent).getAllTypes();
					} catch (JavaModelException ex) {
						return null;
					}
					if (types.length > 0)
						return types[0];
					else
						return null;
				}
				else if (parent instanceof IClassFile)
					return findInputForJavaElement(parent);
			default:
				if (je instanceof IMember)
					return findInputForJavaElement(((IMember)je).getDeclaringType());
		}
		return null; 	
	}
	
	/*
	 * Implements method from IViewPart.
	 */
	public void saveState(IMemento memento) {
		super.saveState(memento);
		fMemberFilterActionGroup.saveState(memento);
	}	

	protected void restoreState(IMemento memento) {
		super.restoreState(memento);
		fMemberFilterActionGroup.restoreState(memento);
	}	

	protected void hookViewerListeners() {
		super.hookViewerListeners();
		getViewer().addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				TreeViewer viewer= (TreeViewer)getViewer();
				Object element= ((IStructuredSelection)event.getSelection()).getFirstElement();
				if (viewer.isExpandable(element))
					viewer.setExpandedState(element, !viewer.getExpandedState(element));
			}
		});
	}
}
