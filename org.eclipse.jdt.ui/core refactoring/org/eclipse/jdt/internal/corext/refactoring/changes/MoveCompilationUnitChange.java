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
package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.NullChange;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;

public class MoveCompilationUnitChange extends CompilationUnitReorgChange {

	public MoveCompilationUnitChange(ICompilationUnit cu, IPackageFragment newPackage){
		super(cu, newPackage);
	}
	
	private MoveCompilationUnitChange(IPackageFragment oldPackage, String cuName, IPackageFragment newPackage){
		super(oldPackage.getHandleIdentifier(), newPackage.getHandleIdentifier(), oldPackage.getCompilationUnit(cuName).getHandleIdentifier());
	}
	
	/* non java-doc
	 * @see IChange#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.getFormattedString("MoveCompilationUnitChange.name", //$NON-NLS-1$
		new String[]{getCu().getElementName(), getPackageName(getDestinationPackage())}); 
	}
	
	/* non java-doc
	 * @see IChange#getUndoChange()
	 */
	public IChange getUndoChange() {
		if (!isActive())
			return new NullChange();
		else	
			return new MoveCompilationUnitChange(getDestinationPackage(), getCu().getElementName(), getOldPackage());
	}

	/* non java-doc
	 * @see CompilationUnitReorgChange#doPeform(IProgressMonitor)
	 */
	void doPeform(IProgressMonitor pm) throws JavaModelException{
		String name;
		String newName= getNewName();
		if (newName == null)
			name= getCu().getElementName();
		else
			name= newName;	

		if (getDestinationPackage().getCompilationUnit(name).exists())
			makeNotUndoable();
		
		getCu().move(getDestinationPackage(), null, newName, true, pm);
	}
}
