/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeHierarchyChangedListener;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Manages a type hierarchy, to keep it refreshed, and to allow it to be shared.
 */
public class TypeHierarchyLifeCycle implements ITypeHierarchyChangedListener, IElementChangedListener {
	
	private boolean fHierarchyRefreshNeeded;
	private ITypeHierarchy fHierarchy;
	private IJavaElement fInputElement;
	private boolean fIsSuperTypesOnly;
	
	private List fChangeListeners;
	
	public TypeHierarchyLifeCycle() {
		this(false);
	}	
	
	public TypeHierarchyLifeCycle(boolean isSuperTypesOnly) {
		fHierarchy= null;
		fInputElement= null;
		fIsSuperTypesOnly= isSuperTypesOnly;
		fChangeListeners= new ArrayList(2);
	}
	
	public ITypeHierarchy getHierarchy() {
		return fHierarchy;
	}
	
	public IJavaElement getInputElement() {
		return fInputElement;
	}
	
	
	public void freeHierarchy() {
		if (fHierarchy != null) {
			fHierarchy.removeTypeHierarchyChangedListener(this);
			JavaCore.removeElementChangedListener(this);
			fHierarchy= null;
			fInputElement= null;
		}
	}
	
	public void removeChangedListener(ITypeHierarchyLifeCycleListener listener) {
		fChangeListeners.remove(listener);
	}
	
	public void addChangedListener(ITypeHierarchyLifeCycleListener listener) {
		if (!fChangeListeners.contains(listener)) {
			fChangeListeners.add(listener);
		}
	}
	
	private void fireChange(IType[] changedTypes) {
		for (int i= fChangeListeners.size()-1; i>=0; i--) {
			ITypeHierarchyLifeCycleListener curr= (ITypeHierarchyLifeCycleListener) fChangeListeners.get(i);
			curr.typeHierarchyChanged(this, changedTypes);
		}
	}
		
	public void ensureRefreshedTypeHierarchy(final IJavaElement element) throws JavaModelException {
		if (element == null || !element.exists()) {
			freeHierarchy();
			return;
		}
		boolean hierachyCreationNeeded= (fHierarchy == null || !element.equals(fInputElement));
		
		if (hierachyCreationNeeded || fHierarchyRefreshNeeded) {
			IRunnableWithProgress op= new IRunnableWithProgress() {
				public void run(IProgressMonitor pm) throws InvocationTargetException {
					try {
						doHierarchyRefresh(element, pm);
					} catch (JavaModelException e) {
						throw new InvocationTargetException(e);
					}
				}
			};
			
			try {
				new BusyIndicatorRunnableContext().run(false, false, op);
			} catch (InvocationTargetException e) {
				Throwable th= e.getTargetException();
				if (th instanceof JavaModelException) {
					throw (JavaModelException)th;
				} else {
					throw new JavaModelException(th, IStatus.ERROR);
				}
			} catch (InterruptedException e) {
				// Not cancelable.
			}
			
			fHierarchyRefreshNeeded= false;
		}
	}
	
	private void doHierarchyRefresh(IJavaElement element, IProgressMonitor pm) throws JavaModelException {
		boolean hierachyCreationNeeded= (fHierarchy == null || !element.equals(fInputElement));
		// to ensore the order of the two listeners always remove / add listeners on operations
		// on type hierarchies
		if (fHierarchy != null) {
			fHierarchy.removeTypeHierarchyChangedListener(this);
			JavaCore.removeElementChangedListener(this);
		}
		if (hierachyCreationNeeded) {
			fInputElement= element;
			if (element.getElementType() == IJavaElement.TYPE) {
				IType type= (IType) element;
				if (fIsSuperTypesOnly) {
					fHierarchy= type.newSupertypeHierarchy(pm);
				} else {
					fHierarchy= type.newTypeHierarchy(pm);
				}
			} else {
				IRegion region= JavaCore.newRegion();
				if (element.getElementType() == IJavaElement.JAVA_PROJECT) {
					// for projects only add the contained source folders
					IPackageFragmentRoot[] roots= ((IJavaProject) element).getPackageFragmentRoots();
					for (int i= 0; i < roots.length; i++) {
						if (!roots[i].isExternal()) {
							region.add(roots[i]);
						}
					}
				} else {
					region.add(element);
				}
				IJavaProject jproject= element.getJavaProject();
				fHierarchy= jproject.newTypeHierarchy(region, pm);				
			}
		} else {
			fHierarchy.refresh(pm);
		}
		fHierarchy.addTypeHierarchyChangedListener(this);
		JavaCore.addElementChangedListener(this);
	}		
	
	/*
	 * @see ITypeHierarchyChangedListener#typeHierarchyChanged
	 */
	public void typeHierarchyChanged(ITypeHierarchy typeHierarchy) {
	 	fHierarchyRefreshNeeded= true;
	}		

	/*
	 * @see IElementChangedListener#elementChanged(ElementChangedEvent)
	 */
	public void elementChanged(ElementChangedEvent event) {
		if (fChangeListeners.isEmpty()) {
			return;
		}
		
		IJavaElement elem= event.getDelta().getElement();
		if (elem instanceof IWorkingCopy && ((IWorkingCopy)elem).isWorkingCopy()) {
			return;
		}
		if (fHierarchyRefreshNeeded) {
			fireChange(null);
		} else {
			ArrayList changedTypes= new ArrayList();
			processDelta(event.getDelta(), changedTypes);
			fireChange((IType[]) changedTypes.toArray(new IType[changedTypes.size()]));
		}
	}
	
	/*
	 * Assume that the hierarchy is intact (no refresh needed)
	 */					
	private void processDelta(IJavaElementDelta delta, ArrayList changedTypes) {
		IJavaElement element= delta.getElement();
		switch (element.getElementType()) {
			case IJavaElement.TYPE:
				processTypeDelta((IType) element, changedTypes);
				processChildrenDelta(delta, changedTypes); // (inner types)
				break;
			case IJavaElement.JAVA_MODEL:
			case IJavaElement.JAVA_PROJECT:
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			case IJavaElement.PACKAGE_FRAGMENT:
				processChildrenDelta(delta, changedTypes);
				break;
			case IJavaElement.COMPILATION_UNIT:
				if (delta.getKind() == IJavaElementDelta.CHANGED && isPossibleStructuralChange(delta.getFlags())) {
					try {
						IType[] types= ((ICompilationUnit) element).getAllTypes();
						for (int i= 0; i < types.length; i++) {
							processTypeDelta(types[i], changedTypes);
						}
					} catch (JavaModelException e) {
						JavaPlugin.log(e);
					}
				} else {
					processChildrenDelta(delta, changedTypes);
				}
				break;
			case IJavaElement.CLASS_FILE:	
				if (delta.getKind() == IJavaElementDelta.CHANGED) {
					try {
						IType type= ((IClassFile) element).getType();
						processTypeDelta(type, changedTypes);
					} catch (JavaModelException e) {
						JavaPlugin.log(e);
					}
				} else {
					processChildrenDelta(delta, changedTypes);
				}
				break;				
		}
	}
	
	private boolean isPossibleStructuralChange(int flags) {
		return (flags & (IJavaElementDelta.F_CONTENT | IJavaElementDelta.F_FINE_GRAINED)) == IJavaElementDelta.F_CONTENT;
	}
	
	private void processTypeDelta(IType type, ArrayList changedTypes) {
		if (getHierarchy().contains(type)) {
			changedTypes.add(type);
		}
	}
	
	private void processChildrenDelta(IJavaElementDelta delta, ArrayList changedTypes) {
		IJavaElementDelta[] children= delta.getAffectedChildren();
		for (int i= 0; i < children.length; i++) {
			processDelta(children[i], changedTypes); // recursive
		}
	}
	
}