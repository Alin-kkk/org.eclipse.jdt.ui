/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Base class for content providers for type hierarchy viewers.
 * Implementors must override 'getTypesInHierarchy'.
 * Java delta processing is also performed by the content provider
 */
public abstract class TypeHierarchyContentProvider implements ITreeContentProvider {
	protected static final Object[] NO_ELEMENTS= new Object[0];
	
	protected TypeHierarchyLifeCycle fTypeHierarchy;
	protected IMember[] fMemberFilter;
	protected boolean fShowAllTypes;
	
	protected TreeViewer fViewer;
	
	public TypeHierarchyContentProvider(TypeHierarchyLifeCycle lifecycle) {
		fTypeHierarchy= lifecycle;
		fMemberFilter= null;
		fShowAllTypes= false;
	}
	
	/**
	 * Sets members to filter the hierarchy for. Set to <code>null</code> to disable member filtering.
	 * When member filtering is enabled, the hierarchy contains only types that contain
	 * an implementation of one of the filter members and the members themself.
	 * The hierarchy can be empty as well.
	 */
	public void setMemberFilter(IMember[] memberFilter) {
		fMemberFilter= memberFilter;
	}
	
	/**
	 * The members to filter or <code>null</code> if member filtering is disabled.
	 */
	public IMember[] getMemberFilter() {
		return fMemberFilter;
	}
	
	/**
	 * In member filtering mode, show all types even if they do not contain one
	 * of the filtered members
	 */
	public void showAllTypes(boolean show) {
		fShowAllTypes= show;
	}
	
	
	protected final ITypeHierarchy getHierarchy() {
		return fTypeHierarchy.getHierarchy();
	}
	
	/*
	 * Called for the root element
	 * @see IStructuredContentProvider#getElements	 
	 */
	public Object[] getElements(Object parent) {
		ITypeHierarchy hierarchy= getHierarchy();
		if (hierarchy != null) {
			IType input= hierarchy.getType();
			if (input != null) {
				return new IType[] { input };
			}
			// opened on a region: dont show
		}
		return NO_ELEMENTS; 
	}
	
	/**
	 * Hook to overwrite. Filter will be applied on the returned types
	 */	
	protected abstract IType[] getTypesInHierarchy(IType type);
	
	/*
	 * Called for the tree children.
	 * @see ITreeContentProvider#getChildren
	 */	
	public Object[] getChildren(Object element) {
		if (element instanceof IType) {
			IType type= (IType)element;
			IType[] childrenTypes= getTypesInHierarchy(type);
				
			if (fMemberFilter != null) {
				List children= new ArrayList();
				addFilteredMembers(type, children);
				addFilteredTypes(childrenTypes, children);
				return children.toArray();
			} else {
				return childrenTypes;
			}			
		}
		return NO_ELEMENTS;
	}
	
	/*
	 * @see ITreeContentProvider#hasChildren
	 */
	public boolean hasChildren(Object element) {
		if (element instanceof IType) {
			IType type= (IType)element;
			IType[] childrenTypes= getTypesInHierarchy(type);
				
			if (fMemberFilter != null) {
				List children= new ArrayList();
				addFilteredMembers(type, children);
				if (!children.isEmpty()) {
					return true;
				}
				addFilteredTypes(childrenTypes, children);
				return !children.isEmpty();
			} else {
				return childrenTypes.length > 0;
			}				
		}
		return false;
	}	
	
	private void addFilteredMembers(IType parent, List children) {
		try {
			IMethod[] methods= parent.getMethods();
			for (int i= 0; i < fMemberFilter.length; i++) {
				IMember member= fMemberFilter[i];
				if (parent.equals(member.getDeclaringType())) {
					if (!children.contains(member)) {
						children.add(member);
					}
				} else if (member instanceof IMethod) {
					IMethod curr= (IMethod)member;
					IMethod meth= JavaModelUtil.findMethod(curr.getElementName(), curr.getParameterTypes(), curr.isConstructor(), methods);
					if (meth != null && !children.contains(meth)) {
						children.add(meth);
					}
				}
			}		
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
	}
		
	private void addFilteredTypes(IType[] types, List children) {
		try {
			for (int i= 0; i < types.length; i++) {
				if (hasFilteredChildren(types[i])) {
					children.add(types[i]);
				}
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
	}
	
	private boolean hasFilteredChildren(IType type) throws JavaModelException {
		if (fShowAllTypes) {
			return true;
		}
		
		IMethod[] methods= type.getMethods();
		for (int i= 0; i < fMemberFilter.length; i++) {
			IMember member= fMemberFilter[i];
			if (type.equals(member.getDeclaringType())) {
				return true;
			} else if (member instanceof IMethod) {
				IMethod curr= (IMethod)member;
				IMethod meth= JavaModelUtil.findMethod(curr.getElementName(), curr.getParameterTypes(), curr.isConstructor(), methods);
				if (meth != null) {
					return true;
				}
			}
		}
		IType[] childrenTypes= getTypesInHierarchy(type);
		for (int i= 0; i < childrenTypes.length; i++) {
			if (hasFilteredChildren(childrenTypes[i])) {
				return true;
			}
		}
		return false;
	}
	
	/*
	 * @see IContentProvider#inputChanged
	 */
	public void inputChanged(Viewer part, Object oldInput, Object newInput) {
		Assert.isTrue(part instanceof TreeViewer);
		fViewer= (TreeViewer)part;
	}
	
	/*
	 * @see IContentProvider#dispose
	 */	
	public void dispose() {
	}
	
	/*
	 * @see ITreeContentProvider#getParent
	 */
	public Object getParent(Object element) {
		return null;
	}
}