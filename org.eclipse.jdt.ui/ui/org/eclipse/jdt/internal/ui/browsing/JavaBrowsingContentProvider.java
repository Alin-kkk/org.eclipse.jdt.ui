/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.browsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.preferences.JavaBasePreferencePage;
import org.eclipse.jdt.internal.ui.viewsupport.BaseJavaElementContentProvider;


class JavaBrowsingContentProvider extends BaseJavaElementContentProvider implements IElementChangedListener {
	
	private StructuredViewer fViewer;
	private Object fInput;
	private JavaBrowsingPart fBrowsingPart;
	
	public JavaBrowsingContentProvider(boolean provideMembers, JavaBrowsingPart browsingPart) {
		super(provideMembers, reconcileJavaViews());
		fBrowsingPart= browsingPart;
		fViewer= fBrowsingPart.getViewer();
		JavaCore.addElementChangedListener(this);
	}

	private static boolean reconcileJavaViews() {
		return JavaBasePreferencePage.reconcileJavaViews();
	}

	public Object[] getChildren(Object element) {
		if (!exists(element))
			return NO_CHILDREN;
			
		try {
			if (element instanceof Collection) {
				Collection elements= (Collection)element;
				if (elements.isEmpty())
					return NO_CHILDREN;
				Object[] result= new Object[0];
				Iterator iter= ((Collection)element).iterator();
				while (iter.hasNext()) {
					Object[] children= getChildren(iter.next());
					if (children != NO_CHILDREN)
						result= concatenate(result, children);
				}
				return result;
			}
			if (element instanceof IPackageFragment) 
				return getPackageContents((IPackageFragment)element);
			if (fProvideMembers && element instanceof IType)
				return getChildren((IType)element);
			if (fProvideMembers && element instanceof ISourceReference && element instanceof IParent)
				return removeImportAndPackageDeclarations(super.getChildren(element));
			if (element instanceof IJavaProject) 
				return getPackageFragmentRoots((IJavaProject)element);
			return super.getChildren(element);
		} catch (JavaModelException e) {
			return NO_CHILDREN;
		}		
	}

	private Object[] getPackageContents(IPackageFragment fragment) throws JavaModelException {
		ISourceReference[] sourceRefs;
		if (fragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
			sourceRefs= fragment.getCompilationUnits();
			if (getProvideWorkingCopy()) {
				for (int i= 0; i < sourceRefs.length; i++) {
					IWorkingCopy wc= EditorUtility.getWorkingCopy((ICompilationUnit)sourceRefs[i]);
					if (wc != null)
						sourceRefs[i]= (ICompilationUnit)wc;
				}
			}
		}
		else {
			IClassFile[] classFiles= fragment.getClassFiles();
			List topLevelClassFile= new ArrayList();
			for (int i= 0; i < classFiles.length; i++) {
				IType type= classFiles[i].getType();
				if (type != null && type.getDeclaringType() == null)
					topLevelClassFile.add(classFiles[i]);
			}
			sourceRefs= (ISourceReference[])topLevelClassFile.toArray(new ISourceReference[topLevelClassFile.size()]);
		}

		Object[] result= new Object[0];
		for (int i= 0; i < sourceRefs.length; i++)
			result= concatenate(result, removeImportAndPackageDeclarations(getChildren(sourceRefs[i])));
		return concatenate(result, fragment.getNonJavaResources());
	}

	private Object[] removeImportAndPackageDeclarations(Object[] members) {
		ArrayList tempResult= new ArrayList(members.length);
		for (int i= 0; i < members.length; i++)
			if (!(members[i] instanceof IImportContainer) && !(members[i] instanceof IPackageDeclaration))
				tempResult.add(members[i]);
		return tempResult.toArray();
	}

	private Object[] getChildren(IType type) throws JavaModelException{
		IParent parent;
		if (type.isBinary())
			parent= type.getClassFile();
		else {
			parent= type.getCompilationUnit();
			if (getProvideWorkingCopy()) {
				IWorkingCopy wc= EditorUtility.getWorkingCopy((ICompilationUnit)parent);
				if (wc != null) {
					parent= (IParent)wc;
					IMember wcType= EditorUtility.getWorkingCopy(type);
					if (wcType != null)
						type= (IType)wcType;
				}
			}
		}
		if (type.getDeclaringType() != null)
			return type.getChildren();

		// Add import declarations
		IJavaElement[] members= parent.getChildren();		
		ArrayList tempResult= new ArrayList(members.length);
		for (int i= 0; i < members.length; i++)
			if ((members[i] instanceof IImportContainer))
				tempResult.add(members[i]);
		tempResult.addAll(Arrays.asList(type.getChildren()));
		return tempResult.toArray();
	}

	private Object[] getPackageFragmentRoots(IJavaProject project) throws JavaModelException {
		if (!project.getProject().isOpen())
			return NO_CHILDREN;
			
		IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
		List list= new ArrayList(roots.length);
		// filter out package fragments that correspond to projects and
		// replace them with the package fragments directly
		for (int i= 0; i < roots.length; i++) {
			IPackageFragmentRoot root= (IPackageFragmentRoot)roots[i];
			if (!root.isExternal()) {
				Object[] children= root.getChildren();
				for (int k= 0; k < children.length; k++) 
					list.add(children[k]);
			}
			else if (hasChildren(root)) {
				list.add(root);
			} 
		}
		return concatenate(list.toArray(), project.getNonJavaResources());
	}

	// ---------------- Element change handling

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		super.inputChanged(viewer, oldInput, newInput);

		if (newInput instanceof Collection) {
			// Get a template object from the collection
			Collection col= (Collection)newInput;
			if (!col.isEmpty())
				newInput= col.iterator().next();
			else
				newInput= null;
		}
		fInput= newInput;
	}
	
	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	public void dispose() {
		super.dispose();
		JavaCore.removeElementChangedListener(this);
	}
	
	/* (non-Javadoc)
	 * Method declared on IElementChangedListener.
	 */
	public void elementChanged(final ElementChangedEvent event) {
		try {
			processDelta(event.getDelta());
		} catch(JavaModelException e) {
			JavaPlugin.getDefault().log(e.getStatus()); //$NON-NLS-1$
		}
	}


	/**
	 * Processes a delta recursively. When more than two children are affected the
	 * tree is fully refreshed starting at this node. The delta is processed in the
	 * current thread but the viewer updates are posted to the UI thread.
	 */
	protected void processDelta(IJavaElementDelta delta) throws JavaModelException {
		int kind= delta.getKind();
		int flags= delta.getFlags();
		IJavaElement element= delta.getElement();
		if (!getProvideWorkingCopy() && element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy()) {
			return;
		}

		// handle open and closing of a solution or project
		if (((flags & IJavaElementDelta.F_CLOSED) != 0) || ((flags & IJavaElementDelta.F_OPENED) != 0)) {
			postRefresh(element);
			return;
		}
		if (kind == IJavaElementDelta.REMOVED) {
			Object parent= internalGetParent(element);
			if (fBrowsingPart.isValidElement(element)) {
				if (element instanceof IClassFile) {
					postRemove(((IClassFile)element).getType());
				} else if (element instanceof ICompilationUnit && !((ICompilationUnit)element).isWorkingCopy()) {
					if (!getProvideWorkingCopy())
						postRefresh(null);
				} else if (element instanceof ICompilationUnit && ((ICompilationUnit)element).isWorkingCopy()) {
					if (getProvideWorkingCopy())
						postRefresh(null);
				} else if (parent instanceof ICompilationUnit && getProvideWorkingCopy() && !((ICompilationUnit)parent).isWorkingCopy()) {
					if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy()) {
						// working copy removed from system - refresh
						postRefresh(null);
					}
				} else if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy() && parent != null && parent.equals(fInput))
				// closed editor - removing working copy
					postRefresh(null);
				else
					postRemove(element);
			}
				
			if (fBrowsingPart.isAncestorOf(element, fInput)) {
				if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy()) {
					postAdjustInputAndSetSelection(((IWorkingCopy)element).getOriginal((IJavaElement)fInput));
				} else
					postAdjustInputAndSetSelection(null);
			} 
			
			
			return;
		}
		if (kind == IJavaElementDelta.ADDED && delta.getMovedFromElement() != null && element instanceof ICompilationUnit)
			return;
		
		if (kind == IJavaElementDelta.ADDED) {
			if (fBrowsingPart.isValidElement(element)) {
				Object parent= internalGetParent(element);				
				if (element instanceof IClassFile) {
					postAdd(parent, ((IClassFile)element).getType());
				} else if (element instanceof ICompilationUnit && !((ICompilationUnit)element).isWorkingCopy()) {
					if (!getProvideWorkingCopy())
						postAdd(parent, ((ICompilationUnit)element).getAllTypes());
				} else if (parent instanceof ICompilationUnit && getProvideWorkingCopy() && !((ICompilationUnit)parent).isWorkingCopy()) {
					//	do nothing
				} else if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy()) {
					// new working copy comes to live
					postRefresh(null);
				} else
					postAdd(parent, element);
			} else	if (fInput == null) {
				IJavaElement newInput= fBrowsingPart.findInputForJavaElement(element);
				if (newInput != null)
					postAdjustInputAndSetSelection(element);
			} else if (element instanceof IType && fBrowsingPart.isValidInput(element)) {
				IJavaElement cu1= element.getAncestor(IJavaElement.COMPILATION_UNIT);
				IJavaElement cu2= ((IJavaElement)fInput).getAncestor(IJavaElement.COMPILATION_UNIT);
				if  (cu1 != null && cu2 != null && cu1.equals(cu2))
					postAdjustInputAndSetSelection(element);
			}
			return;
		}

		if (kind == IJavaElementDelta.CHANGED) {
			if (fBrowsingPart.isValidElement(element)) {
				postRefresh(element);
			}
		}

		if (isClassPathChange(delta))
			 // throw the towel and do a full refresh
			postRefresh(null);
		
		IJavaElementDelta[] affectedChildren= delta.getAffectedChildren();
		if (affectedChildren.length > 1) {
			// a package fragment might become non empty refresh from the parent
			if (element instanceof IPackageFragment) {
				IJavaElement parent= (IJavaElement)internalGetParent(element);
				// avoid posting a refresh to an unvisible parent
				if (element.equals(fInput)) {
					postRefresh(element);
				} else {
					postRefresh(parent);
				}
			}
			// more than one child changed, refresh from here downwards
			if (element instanceof IPackageFragmentRoot && fBrowsingPart.isValidElement(element)) {
				postRefresh(skipProjectPackageFragmentRoot((IPackageFragmentRoot)element));
				return;
			}
		}
		for (int i= 0; i < affectedChildren.length; i++) {
			processDelta(affectedChildren[i]);
		}
	}
	
	/**
	 * Updates the package icon
	 */
	 private void updatePackageIcon(final IJavaElement element) {
	 	postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) 
					fViewer.update(element, new String[]{IBasicPropertyConstants.P_IMAGE});
			}
		});
	 }

		
	private void postRefresh(final Object root) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed())
					fViewer.refresh(root);
			}
		});
	}

	private void postAdd(final Object parent, final Object element) {
		postAdd(parent, new Object[] {element});
	}
		
	private void postAdd(final Object parent, final Object[] elements) {
		if (elements.length <= 0)
			return;
		
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					ctrl.setRedraw(false);
					if (fViewer instanceof AbstractTreeViewer) {
						if (fViewer.testFindItem(parent) == null) {
							Object root= ((AbstractTreeViewer)fViewer).getInput();
							if (root != null)
								((AbstractTreeViewer)fViewer).add(root, elements);
						}
						else
							((AbstractTreeViewer)fViewer).add(parent, elements);
					}
					else if (fViewer instanceof ListViewer)
						((ListViewer)fViewer).add(elements);
					else if (fViewer instanceof TableViewer)
						((TableViewer)fViewer).add(elements);
					if (fViewer.testFindItem(elements[0]) != null)
						fBrowsingPart.adjustInputAndSetSelection((IJavaElement)elements[0]);
					ctrl.setRedraw(true);
				}
			}
		});
	}

	private void postRemove(final Object element) {
		postRemove(new Object[] {element});
	}

	private void postRemove(final Object[] elements) {
		if (elements.length <= 0)
			return;
		
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					ctrl.setRedraw(false);
					if (fViewer instanceof AbstractTreeViewer)
						((AbstractTreeViewer)fViewer).remove(elements);
					else if (fViewer instanceof ListViewer)
						((ListViewer)fViewer).remove(elements);
					else if (fViewer instanceof TableViewer)
						((TableViewer)fViewer).remove(elements);
					ctrl.setRedraw(true);
				}
			}
		});
	}

	private void postAdjustInputAndSetSelection(final Object element) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					ctrl.setRedraw(false);
					fBrowsingPart.adjustInputAndSetSelection((IJavaElement)element);
					ctrl.setRedraw(true);
				}
			}
		});
	}

	private void postRunnable(final Runnable r) {
		Control ctrl= fViewer.getControl();
		if (ctrl != null && !ctrl.isDisposed()) {
			fBrowsingPart.setProcessSelectionEvents(false);
			try {
				Display currentDisplay= ctrl.getDisplay().getCurrent();
				if (currentDisplay != null && currentDisplay.equals(ctrl.getDisplay()))
					ctrl.getDisplay().syncExec(r);
				else				
					ctrl.getDisplay().asyncExec(r);
			} finally {
				fBrowsingPart.setProcessSelectionEvents(true);
			}
		}
	}
}
