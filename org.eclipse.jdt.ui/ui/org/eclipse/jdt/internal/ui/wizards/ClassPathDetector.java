/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.core.util.ISourceAttribute;

import org.eclipse.jdt.ui.PreferenceConstants;

/**
  */
public class ClassPathDetector implements IResourceProxyVisitor {
		
	private HashMap fSourceFolders;
	private List fClassFiles;
	private HashSet fJARFiles;
		
	private IProject fProject;
		
	private IPath fResultOutputFolder;
	private IClasspathEntry[] fResultClasspath;
		
	public ClassPathDetector(IProject project) throws CoreException {
		fSourceFolders= new HashMap();
		fJARFiles= new HashSet(10);
		fClassFiles= new ArrayList(100);
		fProject= project;
			
		project.accept(this, IResource.NONE);
			
		fResultClasspath= null;
		fResultOutputFolder= null;
			
		detectClasspath();
	}
	
	
	private boolean isNested(IPath path, Iterator iter) {
		while (iter.hasNext()) {
			IPath other= (IPath) iter.next();
			if (other.isPrefixOf(path)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Method detectClasspath.
	 */
	private void detectClasspath() {
		ArrayList cpEntries= new ArrayList();
		
		detectSourceFolders(cpEntries);
		IPath outputLocation= detectOutputFolder(cpEntries);
		
		detectLibraries(cpEntries, outputLocation);
			
		if (cpEntries.isEmpty() && fClassFiles.isEmpty()) {
			return;
		}
		IClasspathEntry[] jreEntries= PreferenceConstants.getDefaultJRELibrary();
		for (int i= 0; i < jreEntries.length; i++) {
			cpEntries.add(jreEntries[i]);
		}
		
		IClasspathEntry[] entries= (IClasspathEntry[]) cpEntries.toArray(new IClasspathEntry[cpEntries.size()]);
		if (!JavaConventions.validateClasspath(JavaCore.create(fProject), entries, outputLocation).isOK()) {
			return;
		}
			
		fResultClasspath= entries;
		fResultOutputFolder= outputLocation;
	}
	
	private IPath findInSourceFolders(IPath path) {
		Iterator iter= fSourceFolders.keySet().iterator();
		while (iter.hasNext()) {
			Object key= iter.next();
			List cus= (List) fSourceFolders.get(key);
			if (cus.contains(path)) {
				return (IPath) key;
			}
		}
		return null;
	}
	
	private IPath detectOutputFolder(List entries) {
		HashSet classFolders= new HashSet();
		
		for (Iterator iter= fClassFiles.iterator(); iter.hasNext();) {
			IFile file= (IFile) iter.next();
			IPath location= file.getLocation();
			if (location == null) {
				continue;
			}

			IClassFileReader reader= ToolFactory.createDefaultClassFileReader(location.toOSString(), IClassFileReader.CLASSFILE_ATTRIBUTES);
			if (reader == null) {
				continue; // problematic class file
			}
			char[] className= reader.getClassName();
			ISourceAttribute sourceAttribute= reader.getSourceFileAttribute();
			if (className != null && sourceAttribute != null && sourceAttribute.getSourceFileName() != null) {
				IPath packPath= file.getParent().getFullPath();
				int idx= CharOperation.lastIndexOf('/', className) + 1;
				IPath relPath= new Path(new String(className, 0, idx));
				IPath cuPath= relPath.append(new String(sourceAttribute.getSourceFileName()));
				
				IPath resPath= null;
				if (idx == 0) {
					resPath= packPath;
				} else {
					IPath folderPath= getFolderPath(packPath, relPath);
					if (folderPath != null) {
						resPath= folderPath;
					}
				}
				if (resPath != null) {
					IPath path= findInSourceFolders(cuPath);
					if (path != null) {
						return resPath;
					} else {
						classFolders.add(resPath);	
					}
				}
			}			
		}		
		IPath projPath= fProject.getFullPath();
		if (fSourceFolders.size() == 1 && classFolders.isEmpty() && fSourceFolders.get(projPath) != null) {
			return projPath;
		} else {
			IPath path= projPath.append(PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME));
			while (classFolders.contains(path)) {
				path= new Path(path.toString() + '1');
			}
			return path;
		} 			
	}


	private void detectLibraries(ArrayList cpEntries, IPath outputLocation) {
		Set sourceFolderSet= fSourceFolders.keySet();
		for (Iterator iter= fJARFiles.iterator(); iter.hasNext();) {
			IPath path= (IPath) iter.next();
			if (isNested(path, sourceFolderSet.iterator())) {
				continue;
			}
			if (outputLocation != null && outputLocation.isPrefixOf(path)) {
				continue;
			}
			IClasspathEntry entry= JavaCore.newLibraryEntry(path, null, null);
			cpEntries.add(entry);	
		}
	}


	private void detectSourceFolders(ArrayList resEntries) {
		Set sourceFolderSet= fSourceFolders.keySet();
		for (Iterator iter= sourceFolderSet.iterator(); iter.hasNext();) {
			IPath path= (IPath) iter.next();
			ArrayList excluded= new ArrayList();
			for (Iterator inner= sourceFolderSet.iterator(); inner.hasNext();) {
				IPath other= (IPath) inner.next();
				if (!path.equals(other) && path.isPrefixOf(other)) {
					IPath pathToExclude= other.removeFirstSegments(path.segmentCount()).addTrailingSeparator();
					excluded.add(pathToExclude);
				}
			}
			IPath[] excludedPaths= (IPath[]) excluded.toArray(new IPath[excluded.size()]);
			IClasspathEntry entry= JavaCore.newSourceEntry(path, excludedPaths);
			resEntries.add(entry);
		}
	}

	private void visitCompilationUnit(IFile file) throws JavaModelException {
		ICompilationUnit cu= JavaCore.createCompilationUnitFrom(file);
		if (cu != null) {
			ICompilationUnit workingCopy= null;
			try {
				workingCopy= (ICompilationUnit) cu.getWorkingCopy();
				synchronized(workingCopy) {
					workingCopy.reconcile();
				}
				IPath packPath= file.getParent().getFullPath();
				IPackageDeclaration[] decls= workingCopy.getPackageDeclarations();
				String cuName= file.getName();
				if (decls.length == 0) {
					addToMap(fSourceFolders, packPath, new Path(cuName));
				} else {
					IPath relpath= new Path(decls[0].getElementName().replace('.', '/'));
					IPath folderPath= getFolderPath(packPath, relpath);
					if (folderPath != null) {
						addToMap(fSourceFolders, folderPath, relpath.append(cuName));
					}
				}						
			} finally {
				if (workingCopy != null) {
					workingCopy.destroy();
				}
			}
		}
	}
	
	private void addToMap(HashMap map, IPath folderPath, IPath relPath) {
		List list= (List) map.get(folderPath);
		if (list == null) {
			list= new ArrayList(50);
			map.put(folderPath, list);
		}		
		list.add(relPath);
	}

	private IPath getFolderPath(IPath packPath, IPath relpath) {
		int remainingSegments= packPath.segmentCount() - relpath.segmentCount();
		if (remainingSegments >= 0) {
			IPath common= packPath.removeFirstSegments(remainingSegments);
			if (common.equals(relpath)) {
				return packPath.uptoSegment(remainingSegments);
			}
		}
		return null;
	}

	private boolean hasExtension(String name, String ext) {
		return name.endsWith(ext) && (ext.length() != name.length()); 
	}
	
	private boolean isValidCUName(String name) {
		return !JavaConventions.validateCompilationUnitName(name).matches(IStatus.ERROR);
	}	

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IResourceProxyVisitor#visit(org.eclipse.core.resources.IResourceProxy)
	 */
	public boolean visit(IResourceProxy proxy) throws CoreException {
		if (proxy.getType() == IResource.FILE) {
			String name= proxy.getName();
			if (hasExtension(name, ".java") && isValidCUName(name)) { //$NON-NLS-1$
				visitCompilationUnit((IFile) proxy.requestResource());
			} else if (hasExtension(name, ".class")) { //$NON-NLS-1$
				fClassFiles.add((IFile) proxy.requestResource());
			} else if (hasExtension(name, ".jar")) { //$NON-NLS-1$
				fJARFiles.add(proxy.requestFullPath());
			}
			return false;
		}
		return true;
	}
		
	public IPath getOutputLocation() {
		return fResultOutputFolder;
	}
		
	public IClasspathEntry[] getClasspath() {
		return fResultClasspath;
	}



}
	

