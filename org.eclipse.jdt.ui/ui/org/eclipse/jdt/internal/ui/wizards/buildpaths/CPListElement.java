/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.net.URL;
import java.util.ArrayList;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;

public class CPListElement {
	
	public static final String SOURCEATTACHMENT= "sourcepath";
	public static final String SOURCEATTACHMENTROOT= "rootpath";
	public static final String JAVADOC= "javadoc";
	public static final String OUTPUT= "output";
	public static final String EXCLUSION= "exclusion";
	
	private IJavaProject fProject;
	
	private int fEntryKind;
	private IPath fPath;
	private IResource fResource;
	private boolean fIsExported;
	private boolean fIsMissing;
	
	private CPListElement fParentContainer;
		
	private IClasspathEntry fCachedEntry;
	private ArrayList fChildren;
	
	public CPListElement(IJavaProject project, int entryKind, IPath path, IResource res) {
		fProject= project;

		fEntryKind= entryKind;
		fPath= path;
		fChildren= new ArrayList();
		fResource= res;
		fIsExported= false;
		
		fIsMissing= false;
		fCachedEntry= null;
		fParentContainer= null;
		
		switch (entryKind) {
			case IClasspathEntry.CPE_SOURCE:
				createAttributeElement(OUTPUT, null);
				createAttributeElement(EXCLUSION, new Path[0]);
				break;
			case IClasspathEntry.CPE_LIBRARY:
			case IClasspathEntry.CPE_VARIABLE:
				createAttributeElement(SOURCEATTACHMENT, null);
				createAttributeElement(JAVADOC, null);
				break;
			case IClasspathEntry.CPE_PROJECT:
				break;
			case IClasspathEntry.CPE_CONTAINER:
				try {
					IClasspathContainer container= JavaCore.getClasspathContainer(fPath, fProject);
					IClasspathEntry[] entries= container.getClasspathEntries();
					for (int i= 0; i < entries.length; i++) {
						CPListElement curr= createFromExisting(entries[i], fProject);
						curr.setParentContainer(this);
						fChildren.add(curr);
					}
				} catch (JavaModelException e) {
				}			
				break;
			default:
		}
	}
	
	public IClasspathEntry getClasspathEntry() {
		if (fCachedEntry == null) {
			fCachedEntry= newClasspathEntry();
		}
		return fCachedEntry;
	}
	

	private IClasspathEntry newClasspathEntry() {
		switch (fEntryKind) {
			case IClasspathEntry.CPE_SOURCE:
				IPath outputLocation= (IPath) getAttribute(OUTPUT);
				IPath[] exclusionPattern= (IPath[]) getAttribute(EXCLUSION);
				return JavaCore.newSourceEntry(fPath, exclusionPattern, outputLocation);
			case IClasspathEntry.CPE_LIBRARY:
				IPath attach= (IPath) getAttribute(SOURCEATTACHMENT);
				return JavaCore.newLibraryEntry(fPath, attach, null, isExported());
			case IClasspathEntry.CPE_PROJECT:
				return JavaCore.newProjectEntry(fPath, isExported());
			case IClasspathEntry.CPE_CONTAINER:
				return JavaCore.newContainerEntry(fPath, isExported());
			case IClasspathEntry.CPE_VARIABLE:
				IPath varAttach= (IPath) getAttribute(SOURCEATTACHMENT);
				return JavaCore.newVariableEntry(fPath, varAttach, null, isExported());
			default:
				return null;
		}
	}
	
	/**
	 * Gets the classpath entry path.
	 * @see IClasspathEntry#getPath()
	 */
	public IPath getPath() {
		return fPath;
	}

	/**
	 * Gets the classpath entry kind.
	 * @see IClasspathEntry#getEntryKind()
	 */	
	public int getEntryKind() {
		return fEntryKind;
	}

	/**
	 * Entries without resource are either non existing or a variable entry
	 * External jars do not have a resource
	 */
	public IResource getResource() {
		return fResource;
	}
	
	public CPListElementAttribute setAttribute(String key, Object value) {
		CPListElementAttribute attribute= findAttributeElement(key);
		if (attribute == null) {
			return null;
		}
		attribute.setValue(value);
		attributeChanged(key);
		return attribute;
	}
	
	private CPListElementAttribute findAttributeElement(String key) {
		for (int i= 0; i < fChildren.size(); i++) {
			Object curr= fChildren.get(i);
			if (curr instanceof CPListElementAttribute) {
				CPListElementAttribute elem= (CPListElementAttribute) curr;
				if (key.equals(elem.getKey())) {
					return elem;
				}
			}
		}		
		return null;		
	}
	
	public Object getAttribute(String key) {
		CPListElementAttribute attrib= findAttributeElement(key);
		if (attrib != null) {
			return attrib.getValue();
		}
		return null;
	}
	
	private void createAttributeElement(String key, Object value) {
		fChildren.add(new CPListElementAttribute(this, key, value));
	}	
	
	
	public Object[] getChildren(boolean hideOutputFolder) {
		if (hideOutputFolder && fEntryKind == IClasspathEntry.CPE_SOURCE) {
			return new Object[] { findAttributeElement(EXCLUSION) };
		}
		return fChildren.toArray();
	}
	
	private void setParentContainer(CPListElement element) {
		fParentContainer= element;
	}
	
	public CPListElement getParentContainer() {
		return fParentContainer;
	}	
	
	private void attributeChanged(String key) {
		fCachedEntry= null;
	}
	
	
	/*
	 * @see Object#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (other != null && other.getClass() == getClass()) {
			CPListElement elem= (CPListElement)other;
			return elem.fEntryKind == fEntryKind && elem.fPath.equals(fPath);
		}
		return false;
	}
	
	/*
	 * @see Object#hashCode()
	 */
	public int hashCode() {
		return fPath.hashCode() + fEntryKind;
	}

	/**
	 * Returns if a entry is missing.
	 * @return Returns a boolean
	 */
	public boolean isMissing() {
		return fIsMissing;
	}

	/**
	 * Sets the 'missing' state of the entry.
	 */
	public void setIsMissing(boolean isMissing) {
		fIsMissing= isMissing;
	}

	/**
	 * Returns if a entry is exported (only applies to libraries)
	 * @return Returns a boolean
	 */
	public boolean isExported() {
		return fIsExported;
	}

	/**
	 * Sets the export state of the entry.
	 */
	public void setExported(boolean isExported) {
		if (isExported != fIsExported) {
			fIsExported = isExported;
			
			attributeChanged(null);
		}
	}

	/**
	 * Gets the project.
	 * @return Returns a IJavaProject
	 */
	public IJavaProject getJavaProject() {
		return fProject;
	}
	
	public static CPListElement createFromExisting(IClasspathEntry curr, IJavaProject project) {
		IPath path= curr.getPath();
		IWorkspaceRoot root= project.getProject().getWorkspace().getRoot();

		// get the resource
		IResource res= null;
		boolean isMissing= false;
		URL javaDocLocation= null;

		switch (curr.getEntryKind()) {
			case IClasspathEntry.CPE_CONTAINER:
				res= null;
				try {
					isMissing= (JavaCore.getClasspathContainer(path, project) == null);
				} catch (JavaModelException e) {
					isMissing= true;
				}
				break;
			case IClasspathEntry.CPE_VARIABLE:
				IPath resolvedPath= JavaCore.getResolvedVariablePath(path);
				res= null;
				isMissing=  root.findMember(resolvedPath) == null && !resolvedPath.toFile().isFile();
				javaDocLocation= JavaUI.getLibraryJavadocLocation(resolvedPath);
				break;
			case IClasspathEntry.CPE_LIBRARY:
				res= root.findMember(path);
				if (res == null) {
					if (!ArchiveFileFilter.isArchivePath(path)) {
						if (root.getWorkspace().validatePath(path.toString(), IResource.FOLDER).isOK()) {
							res= root.getFolder(path);
						}
					}
					isMissing= !path.toFile().isFile(); // look for external JARs
				}
				javaDocLocation= JavaUI.getLibraryJavadocLocation(path);
				break;
			case IClasspathEntry.CPE_SOURCE:
				res= root.findMember(path);
				if (res == null) {
					if (root.getWorkspace().validatePath(path.toString(), IResource.FOLDER).isOK()) {
						res= root.getFolder(path);
					}
					isMissing= true;
				}
				break;
			case IClasspathEntry.CPE_PROJECT:
				res= root.findMember(path);
				isMissing= (res == null);
				break;
		}
		CPListElement elem= new CPListElement(project, curr.getEntryKind(), path, res);
		elem.setExported(curr.isExported());
		elem.setAttribute(SOURCEATTACHMENT, curr.getSourceAttachmentPath());
		elem.setAttribute(JAVADOC, javaDocLocation);
		elem.setAttribute(OUTPUT, curr.getOutputLocation());
		elem.setAttribute(EXCLUSION, curr.getExclusionPatterns()); 

		if (project.exists()) {
			elem.setIsMissing(isMissing);
		}
		return elem;
	}	

}