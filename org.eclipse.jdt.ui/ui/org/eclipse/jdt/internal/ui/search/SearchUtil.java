/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.search;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.internal.ui.browsing.JavaElementTypeComparator;

import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * This class contains some utility methods for J Search.
 */
public class SearchUtil extends JavaModelUtil {

	// LRU working sets
	public static int LRU_WORKINGSET_LIST_SIZE= 3;
	private static LRUWorkingSetsList fgLRUWorkingSets;

	// Settings store
	private static final String DIALOG_SETTINGS_KEY= "JavaElementSearchActions"; //$NON-NLS-1$
	private static final String STORE_LRU_WORKING_SET_NAMES= "lastUsedWorkingSetNames"; //$NON-NLS-1$
	
	private static final JavaElementTypeComparator fgJavaElementTypeComparator= new JavaElementTypeComparator();
	private static IDialogSettings fgSettingsStore;

	public static IJavaElement getJavaElement(IMarker marker) {
		if (marker == null || !marker.exists())
			return null;
		try {
			String handleId= (String)marker.getAttribute(IJavaSearchUIConstants.ATT_JE_HANDLE_ID);
			IJavaElement je= JavaCore.create(handleId);
			if (je == null)
				return null;

			if (!marker.getAttribute(IJavaSearchUIConstants.ATT_IS_WORKING_COPY, false)) {
				if (je != null && je.exists())
					return je;
			}

			if (isBinary(je) || fgJavaElementTypeComparator.compare(je, IJavaElement.COMPILATION_UNIT) > 0)
				return je;
			
			ICompilationUnit cu= findCompilationUnit(je);
			if (cu == null || !cu.exists()) {
				cu= (ICompilationUnit)JavaCore.create(marker.getResource());
			}

			// Find working copy element
			IWorkingCopy[] workingCopies= JavaUI.getSharedWorkingCopies();
			int i= 0;
			while (i < workingCopies.length) {
				if (workingCopies[i].getOriginalElement().equals(cu)) {
					je= findInWorkingCopy(workingCopies[i], je, true);
					break;
				}
				i++;
			}
			if (je != null && !je.exists()) {
				IJavaElement[] jElements= cu.findElements(je);
				if (jElements == null || jElements.length == 0)
					je= cu.getElementAt(marker.getAttribute(IMarker.CHAR_START, 0));
				else
					je= jElements[0];
			}
			return je;
		} catch (CoreException ex) {
			ExceptionHandler.handle(ex, SearchMessages.getString("Search.Error.createJavaElement.title"), SearchMessages.getString("Search.Error.createJavaElement.message")); //$NON-NLS-2$ //$NON-NLS-1$
			return null;
		}
	}

	private static boolean isBinary(IJavaElement jElement) {
		if (jElement instanceof IMember)
			return ((IMember)jElement).isBinary();

		IPackageFragmentRoot pkgRoot= (IPackageFragmentRoot)jElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (pkgRoot != null && pkgRoot.isArchive())
			return true;

		return false;
	}

	private static IJavaElement fixCUName(IMarker marker, String handle) {
			// FIXME: This is a dirty fix for 1GCE1EI: ITPJUI:WINNT - Can't handle rename of resource
			if (handle != null) {
				String resourceName= ""; //$NON-NLS-1$
				if (marker.getResource() != null)
					resourceName= marker.getResource().getName();
				if (!handleContainsWrongCU(handle, resourceName)) {
				 	handle= computeFixedHandle(handle, resourceName);
					IJavaElement je= JavaCore.create(handle);
				 	if (je != null && je.exists()) {
				 		try {
							marker.setAttribute(IJavaSearchUIConstants.ATT_JE_HANDLE_ID, handle);
				 		} catch (CoreException ex) {
				 			// leave old attribute
				 		} finally {
							return je;
				 		}
				 	}
				}
			}
			return null;
	}
	
	private static boolean handleContainsWrongCU(String handle, String resourceName) {
		int start= handle.indexOf('{');
		int end= handle.indexOf(".java"); //$NON-NLS-1$
		if (start >= end)
			return false;
		String name= handle.substring(start + 1, end + 5);
		return name.equals(resourceName);
	}
	
	private static String computeFixedHandle(String handle, String resourceName) {
		int start= handle.indexOf('{');
		int end= handle.indexOf(".java"); //$NON-NLS-1$
		handle= handle.substring(0, start + 1) + resourceName + handle.substring(end + 5);
		return handle;
	}

	/** 
	 * Returns the working copy of the given java element.
	 * @param javaElement the javaElement for which the working copyshould be found
	 * @param reconcile indicates whether the working copy must be reconcile prior to searching it
	 * @return the working copy of the given element or <code>null</code> if none
	 */	
	private static IJavaElement findInWorkingCopy(IWorkingCopy workingCopy, IJavaElement element, boolean reconcile) throws JavaModelException {
		if (workingCopy != null) {
			if (reconcile) {
				synchronized (workingCopy) {
					workingCopy.reconcile();
					return SearchUtil.findInCompilationUnit((ICompilationUnit)workingCopy, element);
				}
			} else {
					return SearchUtil.findInCompilationUnit((ICompilationUnit)workingCopy, element);
			}
		}
		return null;
	}

	/**
	 * Returns the compilation unit for the given java element.
	 * 
	 * @param	element the java element whose compilation unit is searched for
	 * @return	the compilation unit of the given java element
	 */
	static ICompilationUnit findCompilationUnit(IJavaElement element) {
		if (element == null)
			return null;

		if (element.getElementType() == IJavaElement.COMPILATION_UNIT)
			return (ICompilationUnit)element;
			
		if (element instanceof IMember)
			return ((IMember)element).getCompilationUnit();

		return findCompilationUnit(element.getParent());
	}

	/*
	 * Copied from JavaModelUtil and patched to allow members which do not exist.
	 * The only case where this is a problem is for methods which have same name and
	 * paramters as a constructor. The constructor will win in such a situation.
	 * 
	 * @see JavaModelUtil#findMemberInCompilationUnit(ICompilationUnit, IMember)
	 */		
	public static IMember findInCompilationUnit(ICompilationUnit cu, IMember member) throws JavaModelException {
		if (member.getElementType() == IJavaElement.TYPE) {
			return findTypeInCompilationUnit(cu, getTypeQualifiedName((IType)member));
		} else {
			IType declaringType= findTypeInCompilationUnit(cu, getTypeQualifiedName(member.getDeclaringType()));
			if (declaringType != null) {
				IMember result= null;
				switch (member.getElementType()) {
				case IJavaElement.FIELD:
					result= declaringType.getField(member.getElementName());
					break;
				case IJavaElement.METHOD:
					IMethod meth= (IMethod) member;
					// XXX: Begin patch ---------------------
					boolean isConstructor;
					if (meth.exists())
						isConstructor= meth.isConstructor();
					else
						isConstructor= declaringType.getElementName().equals(meth.getElementName());
					// XXX: End patch -----------------------
					result= findMethod(meth.getElementName(), meth.getParameterTypes(), isConstructor, declaringType);
					break;
				case IJavaElement.INITIALIZER:
					result= declaringType.getInitializer(1);
					break;					
				}
				if (result != null && result.exists()) {
					return result;
				}
			}
		}
		return null;
	}

	/*
	 * XXX: Unchanged copy from JavaModelUtil
	 */
	public static IJavaElement findInCompilationUnit(ICompilationUnit cu, IJavaElement element) throws JavaModelException {
		
		if (element instanceof IMember)
			return findInCompilationUnit(cu, (IMember)element);
		
		int type= element.getElementType();
		switch (type) {
			case IJavaElement.IMPORT_CONTAINER:
				return cu.getImportContainer();
			
			case IJavaElement.PACKAGE_DECLARATION:
				return find(cu.getPackageDeclarations(), element.getElementName());
			
			case IJavaElement.IMPORT_DECLARATION:
				return find(cu.getImports(), element.getElementName());
			
			case IJavaElement.COMPILATION_UNIT:
				return cu;
		}
		
		return null;
	}
	
	/*
	 * XXX: Unchanged copy from JavaModelUtil
	 */
	private static IJavaElement find(IJavaElement[] elements, String name) {
		if (elements == null || name == null)
			return null;
			
		for (int i= 0; i < elements.length; i++) {
			if (name.equals(elements[i].getElementName()))
				return elements[i];
		}
		
		return null;
	}

	public static String toString(IWorkingSet[] workingSets) {
		Arrays.sort(workingSets, new WorkingSetComparator());
		String result= ""; //$NON-NLS-1$
		if (workingSets != null && workingSets.length > 0) {
			boolean firstFound= false;
			for (int i= 0; i < workingSets.length; i++) {
				String workingSetName= workingSets[i].getName();
				if (firstFound)
					result= SearchMessages.getFormattedString("SearchUtil.workingSetConcatenation", new String[] {result, workingSetName}); //$NON-NLS-1$
				else {
					result= workingSetName;
					firstFound= true;
				}
			}
		}
		return result;
	}

	// ---------- LRU working set handling ----------

	/**
	 * Updates the LRU list of working sets.
	 * 
	 * @param workingSets	the workings sets to be added to the LRU list
	 */
	public static void updateLRUWorkingSets(IWorkingSet[] workingSets) {
		if (workingSets == null || workingSets.length < 1)
			return;
		
		getLRUWorkingSets().add(workingSets);
		saveState();
	}

	private static void saveState() {
		IWorkingSet[] workingSets;
		Iterator iter= fgLRUWorkingSets.iterator();
		int i= 0;
		while (iter.hasNext()) {
			workingSets= (IWorkingSet[])iter.next();
			String[] names= new String[workingSets.length];
			for (int j= 0; j < workingSets.length; j++)
				names[j]= workingSets[j].getName();
			fgSettingsStore.put(STORE_LRU_WORKING_SET_NAMES + i, names);
			i++;
		}
	}

	public static LRUWorkingSetsList getLRUWorkingSets() {
		if (fgLRUWorkingSets == null) {
			restoreState();
		}
		return fgLRUWorkingSets;
	}

	static void restoreState() {
		fgLRUWorkingSets= new LRUWorkingSetsList(LRU_WORKINGSET_LIST_SIZE);
		fgSettingsStore= JavaPlugin.getDefault().getDialogSettings().getSection(DIALOG_SETTINGS_KEY);
		if (fgSettingsStore == null)
			fgSettingsStore= JavaPlugin.getDefault().getDialogSettings().addNewSection(DIALOG_SETTINGS_KEY);
		
		boolean foundLRU= false;
		for (int i= LRU_WORKINGSET_LIST_SIZE - 1; i >= 0; i--) {
			String[] lruWorkingSetNames= fgSettingsStore.getArray(STORE_LRU_WORKING_SET_NAMES + i);
			if (lruWorkingSetNames != null) {
				Set workingSets= new HashSet(2);
				for (int j= 0; j < lruWorkingSetNames.length; j++) {
					IWorkingSet workingSet= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSet(lruWorkingSetNames[j]);
					if (workingSet != null) {
						workingSets.add(workingSet);
					}
				}
				foundLRU= true;
				if (!workingSets.isEmpty())
					fgLRUWorkingSets.add((IWorkingSet[])workingSets.toArray(new IWorkingSet[workingSets.size()]));
			}
		}
		if (!foundLRU)
			// try old preference format
			restoreFromOldFormat();
	}

	private static void restoreFromOldFormat() {
		fgLRUWorkingSets= new LRUWorkingSetsList(LRU_WORKINGSET_LIST_SIZE);
		fgSettingsStore= JavaPlugin.getDefault().getDialogSettings().getSection(DIALOG_SETTINGS_KEY);
		if (fgSettingsStore == null)
			fgSettingsStore= JavaPlugin.getDefault().getDialogSettings().addNewSection(DIALOG_SETTINGS_KEY);

		boolean foundLRU= false;
		String[] lruWorkingSetNames= fgSettingsStore.getArray(STORE_LRU_WORKING_SET_NAMES);
		if (lruWorkingSetNames != null) {
			for (int i= lruWorkingSetNames.length - 1; i >= 0; i--) {
				IWorkingSet workingSet= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSet(lruWorkingSetNames[i]);
				if (workingSet != null) {
					foundLRU= true;
					fgLRUWorkingSets.add(new IWorkingSet[]{workingSet});
				}
			}
		}
		if (foundLRU)
			// save in new format
			saveState();
	}
}
