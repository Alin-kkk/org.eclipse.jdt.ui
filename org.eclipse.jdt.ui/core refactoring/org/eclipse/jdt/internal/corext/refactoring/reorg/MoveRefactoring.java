/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.ICompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.changes.AddToClasspathChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.MoveCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.MovePackageChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.MoveResourceChange;
import org.eclipse.jdt.internal.corext.refactoring.text.ITextBufferChangeCreator;

public class MoveRefactoring extends ReorgRefactoring {
	
	private boolean fUpdateReferences;
	private CodeGenerationSettings fSettings;
	
	public MoveRefactoring(List elements, CodeGenerationSettings settings){
		super(elements);
		Assert.isNotNull(settings);
		fSettings= settings;
	}
	
	/* non java-doc
	 * @see IRefactoring#getName()
	 */
	public String getName() {
		return "Move elements";
	}
	
	/* non java-doc
	 * @see Refactoring#checkInput(IProgressMonitor)
	 */
	public final RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		pm.beginTask("", 1);
		try{
			RefactoringStatus result= new RefactoringStatus();
			result.merge(checkReadOnlyStatus());
			return result;
		} finally{
			pm.done();
		}	
	}
	
	private RefactoringStatus checkReadOnlyStatus() throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		for (Iterator iter= getElements().iterator(); iter.hasNext(); ){
			Object each= iter.next();
			if (Checks.isReadOnly(each))
				result.addError("Selected element " + ReorgUtils.getName(each) + "(or one or its sub-elements) is marked as read-only.");
		}
		return result;
	}
	
	/* non java-doc
	 * @see ReorgRefactoring#isValidDestinationForCusAndFiles(Object)
	 */
	boolean isValidDestinationForCusAndFiles(Object dest) throws JavaModelException{
		Object destination= getDestinationForCusAndFiles(dest);
		if (destination instanceof IPackageFragment)
			return canCopyCusAndFiles(dest);	
		
		return canCopyResources(dest);	
	}

	//overridden
	boolean canCopySourceFolders(Object dest) throws JavaModelException{
		IJavaProject javaProject= JavaCore.create(getDestinationForSourceFolders(dest));
		return super.canCopySourceFolders(dest) && !destinationIsParent(getElements(), javaProject);
	}
	
	//overridden
	boolean canCopyPackages(Object dest) throws JavaModelException{
		return super.canCopyPackages(dest) && !destinationIsParent(getElements(), getDestinationForPackages(dest));
	}
	
	//overridden
	boolean canCopyResources(Object dest) throws JavaModelException{
		return super.canCopyResources(dest) && ! destinationIsParentForResources(getDestinationForResources(dest));
	}
	
	public boolean canUpdateReferences() throws JavaModelException{
		if (getDestination() == null)
			return false;
		
		if (hasPackages())
			return false;

		if (hasSourceFolders())
			return false;			
			
		if (! hasCus())	
			return false;	
		
		Object dest= getDestinationForCusAndFiles(getDestination());
		if (!(dest instanceof IPackageFragment))
			return false;
			
		if (((IPackageFragment)dest).isDefaultPackage())
			return false;
		
		if (isAnyCUFromDefaultPackage())
			return false;
			
		if (!allExist())
			return false;	
		
		if (isAnyUnsaved())
			return false;
		
		if (anyHasSyntaxErrors())
			return false;	
			
		return true;	
	}
	
	private boolean allExist(){
		for (Iterator iter= getElements().iterator(); iter.hasNext(); ){
			Object each= iter.next();
			if (!(each instanceof IJavaElement))
				continue;
			if (! ((IJavaElement)each).exists())
				return false;
		}
		return true;
	}
	
	private boolean isAnyCUFromDefaultPackage(){
		for (Iterator iter= getElements().iterator(); iter.hasNext(); ){
			Object each= iter.next();
			if (! (each instanceof ICompilationUnit))
				continue;
			if (isDefaultPackage(((ICompilationUnit)each).getParent()))	
				return true;
		}
		return false;
	}
	
	private static boolean isDefaultPackage(IJavaElement element){		
			if (! (element instanceof IPackageFragment))
				return false;
			return (((IPackageFragment)element).isDefaultPackage());
	}
	
	private boolean anyHasSyntaxErrors() throws JavaModelException{
		for (Iterator iter= getElements().iterator(); iter.hasNext(); ){
			Object each= iter.next();
			if (! (each instanceof ICompilationUnit))
				continue;
			
			if (hasSyntaxErrors((ICompilationUnit)JavaCore.create(getResource((ICompilationUnit)each))))
					return true;
		}
		return false;
	}
	
	private static boolean hasSyntaxErrors(ICompilationUnit cu) throws JavaModelException{
		Assert.isTrue(!cu.isWorkingCopy());
		return ! cu.isStructureKnown();
	}
	
	private boolean isAnyUnsaved(){
		List elements= getElements();
		IFile[] unsaved= getUnsavedFiles();
		for (int i= 0; i < unsaved.length; i++){
			if (elements.contains(unsaved[i]))
				return true;
			if (elements.contains(JavaCore.create(unsaved[i])))	
				return true;
		}
		return false;
	}
	
	public void setUpdateReferences(boolean update){
		fUpdateReferences= update;
	}
	
	//---- changes 

	/* non java-doc
	 * @see IRefactoring#creteChange(IProgressMonitor)
	 */	
	public IChange createChange(IProgressMonitor pm) throws JavaModelException {
		if (! fUpdateReferences)
			return super.createChange(pm);
		
		pm.beginTask("", 2);
		try{
			CompositeChange composite= new CompositeChange("reorganize elements", 2){
				public boolean isUndoable(){ //XX this can be undoable in some cases. should enable it at some point.
					return false; 
				}
			};
			Object dest= getDestinationForCusAndFiles(getDestination());
			if (dest instanceof IPackageFragment){			
				MoveCuUpdateCreator creator= new MoveCuUpdateCreator(collectCus(), (IPackageFragment)dest, fSettings);
				addAllChildren(composite, creator.createUpdateChange(new SubProgressMonitor(pm, 1)));
			}
			IChange fileMove= super.createChange(new SubProgressMonitor(pm, 1));
			if (fileMove instanceof ICompositeChange){
				addAllChildren(composite, (ICompositeChange)fileMove);		
			} else{
				composite.add(fileMove);
			}	
			return composite;
		} finally{
			pm.done();
		}
	}
	
	private static void addAllChildren(CompositeChange collector, ICompositeChange composite){
		collector.addAll(composite.getChildren());
	}
		
	private ICompilationUnit[] collectCus(){
		List cus= new ArrayList();
		for (Iterator iter= getElements().iterator(); iter.hasNext(); ){
			Object each= iter.next();
			if (each instanceof ICompilationUnit)
				cus.add(each);
		}
		return (ICompilationUnit[])cus.toArray(new ICompilationUnit[cus.size()]);
	}
	
	/*
	 * @see ReorgRefactoring#createChange(ICompilationUnit)
	 */	
	IChange createChange(ICompilationUnit cu) throws JavaModelException{
		Object dest= getDestinationForCusAndFiles(getDestination());
		if (dest instanceof IPackageFragment)
			return new MoveCompilationUnitChange(cu, (IPackageFragment)dest);
		Assert.isTrue(dest instanceof IContainer);//this should be checked before - in preconditions
		return new MoveResourceChange(getResource(cu), (IContainer)dest);
	}

	/*
	 * @see ReorgRefactoring#createChange(IPackageFragmentRoot)
	 */
	IChange createChange(IPackageFragmentRoot root) throws JavaModelException{
		IResource res= root.getUnderlyingResource();
		IProject project= getDestinationForSourceFolders(getDestination());
		IJavaProject javaProject= JavaCore.create(project);
		CompositeChange result= new CompositeChange("move source folder", 2);
		result.add(new MoveResourceChange(res, project));
		if (javaProject != null)
			result.add(new AddToClasspathChange(javaProject, root.getElementName()));
		return result;
	}

	/*
	 * @see ReorgRefactoring#createChange(IPackageFragment)
	 */		
	IChange createChange(IPackageFragment pack) throws JavaModelException{
		return new MovePackageChange(pack, getDestinationForPackages(getDestination()));
	}
	
	/*
	 * @see ReorgRefactoring#createChange(IResource)
	 */	
	IChange createChange(IResource res) throws JavaModelException{
		return new MoveResourceChange(res, getDestinationForResources(getDestination()));
	}
}

