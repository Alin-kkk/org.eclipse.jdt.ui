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
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceManipulation;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.NullChange;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DeleteFileChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DeleteFolderChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DeleteFromClasspathChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DeletePackageFragmentRootChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DeleteSourceManipulationChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.textmanipulation.MultiTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;


class DeleteChangeCreator{
	private DeleteChangeCreator(){}
	
	static IChange createDeleteChange(TextChangeManager manager, IResource[] resources, IJavaElement[] javaElements) throws CoreException{
		CompositeChange composite= new CompositeChange(){
			public boolean isUndoable() {
				return false;
			}	
		};
		for (int i= 0; i < javaElements.length; i++) {
			IJavaElement element= javaElements[i];
			if (! ReorgUtils.isInsideCompilationUnit(element))
				composite.add(createDeleteChange(element));
		}

		for (int i= 0; i < resources.length; i++) {
			composite.add(createDeleteChange(resources[i]));
		}
		
		Map grouped= groupByCompilationUnit(getElementsSmallerThanCu(javaElements));
		if (grouped.size() != 0 ){
			Assert.isNotNull(manager);
			for (Iterator iter= grouped.keySet().iterator(); iter.hasNext();) {
				ICompilationUnit cu= (ICompilationUnit) iter.next();
				composite.add(createDeleteChange(cu, (List)grouped.get(cu), manager));
			}
		}

		return composite;
	}
	
	private static IChange createDeleteChange(IResource resource) {
		Assert.isTrue(! (resource instanceof IWorkspaceRoot));//cannot be done
		Assert.isTrue(! (resource instanceof IProject)); //project deletion is handled by the workbench
		if (resource instanceof IFile)
			return new DeleteFileChange((IFile)resource);
		if (resource instanceof IFolder)
			return new DeleteFolderChange((IFolder)resource);
		Assert.isTrue(false);//there're no more kinds
		return null;
	}

	/*
	 * List<IJavaElement> javaElements 
	 */
	private static IChange createDeleteChange(ICompilationUnit cu, List javaElements, TextChangeManager manager) throws CoreException {
		CompilationUnit cuNode= AST.parseCompilationUnit(cu, false);
		ASTRewrite rewrite= new ASTRewrite(cuNode);
		IJavaElement[] elements= (IJavaElement[]) javaElements.toArray(new IJavaElement[javaElements.size()]);
		ASTNodeDeleteUtil.markAsDeleted(elements, cuNode, rewrite);
		return addTextEditFromRewrite(manager, cu, rewrite);
	}

	private static TextChange addTextEditFromRewrite(TextChangeManager manager, ICompilationUnit cu, ASTRewrite rewrite) throws CoreException {
		TextBuffer textBuffer= TextBuffer.create(cu.getBuffer().getContents());
		TextEdit resultingEdits= new MultiTextEdit();
		rewrite.rewriteNode(textBuffer, resultingEdits);

		TextChange textChange= manager.get(cu);
		if (textChange instanceof TextFileChange){
			TextFileChange tfc= (TextFileChange)textChange;
			tfc.setSave(! cu.isWorkingCopy());
		}
		String message= RefactoringCoreMessages.getString("DeleteChangeCreator.1"); //$NON-NLS-1$
		textChange.addTextEdit(message, resultingEdits);
		rewrite.removeModifications();
		return textChange;
	}

	//List<IJavaElement>
	private static List getElementsSmallerThanCu(IJavaElement[] javaElements){
		List result= new ArrayList();
		for (int i= 0; i < javaElements.length; i++) {
			IJavaElement element= javaElements[i];
			if (ReorgUtils.isInsideCompilationUnit(element))
				result.add(element);
		}
		return result;
	}

	/* List<IJavaElement> javaElements
	 * return ICompilationUnit -> List<IJavaElement>
	 */
	private static Map groupByCompilationUnit(List javaElements){
		Map result= new HashMap();
		for (Iterator iter= javaElements.iterator(); iter.hasNext();) {
			IJavaElement element= (IJavaElement) iter.next();
			ICompilationUnit cu= ReorgUtils.getCompilationUnit(element);
			if (cu != null){
				if (! result.containsKey(cu))
					result.put(cu, new ArrayList(1));
				((List)result.get(cu)).add(element);
			}
		}
		return result;
	}

	private static IChange createDeleteChange(IJavaElement javaElement) throws JavaModelException {
		Assert.isTrue(! ReorgUtils.isInsideCompilationUnit(javaElement));
		
		switch(javaElement.getElementType()){
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				return createPackageFragmentRootDeleteChange((IPackageFragmentRoot)javaElement);

			case IJavaElement.PACKAGE_FRAGMENT:
				return createSourceManipulationDeleteChange((IPackageFragment)javaElement);

			case IJavaElement.COMPILATION_UNIT:
				return createSourceManipulationDeleteChange((ICompilationUnit)javaElement);

			case IJavaElement.CLASS_FILE:
				//if this assert fails, it means that a precondition is missing
				Assert.isTrue(((IClassFile)javaElement).getResource() instanceof IFile);
				return createDeleteChange(((IClassFile)javaElement).getResource());

			case IJavaElement.JAVA_MODEL: //cannot be done
				Assert.isTrue(false);
				return null;

			case IJavaElement.JAVA_PROJECT: //handled differently
				Assert.isTrue(false);
				return null;

			case IJavaElement.TYPE:
			case IJavaElement.FIELD:
			case IJavaElement.METHOD:
			case IJavaElement.INITIALIZER:
			case IJavaElement.PACKAGE_DECLARATION:
			case IJavaElement.IMPORT_CONTAINER:
			case IJavaElement.IMPORT_DECLARATION:
				Assert.isTrue(false);//not done here
			default:
				Assert.isTrue(false);//there's no more kinds
				return new NullChange();
		}
	}

	private static IChange createSourceManipulationDeleteChange(ISourceManipulation element) {
		//XXX workaround for bug 31384, in case of linked ISourceManipulation delete the resource
		if (element instanceof ICompilationUnit || element instanceof IPackageFragment){
			IResource resource;
			if (element instanceof ICompilationUnit)
				resource= ReorgUtils.getResource((ICompilationUnit)element);
			else 
				resource= ((IPackageFragment)element).getResource();
			if (resource != null && resource.isLinked())
				return createDeleteChange(resource);
		}
		return new DeleteSourceManipulationChange(element);
	}
	
	private static IChange createPackageFragmentRootDeleteChange(IPackageFragmentRoot root) throws JavaModelException {
		IResource resource= root.getResource();
		if (resource != null && resource.isLinked()){
			//XXX using this code is a workaround for jcore bug 31998
			//jcore cannot handle linked stuff
			//normally, we should always create DeletePackageFragmentRootChange
			CompositeChange composite= new CompositeChange(RefactoringCoreMessages.getString("DeleteRefactoring.delete_package_fragment_root"), 2); //$NON-NLS-1$
	
			composite.add(new DeleteFromClasspathChange(root));
			Assert.isTrue(! Checks.isClasspathDelete(root));//checked in preconditions
			composite.add(createDeleteChange(resource));
	
			return composite;
		} else {
			Assert.isTrue(! root.isExternal());
			return new DeletePackageFragmentRootChange(root, null);//TODO remove the query argument 
		}
	}
}