/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.corext.refactoring.generics;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.CastVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.CollectionElementVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ConstraintVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.DeclaringTypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.EquivalenceRepresentative;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeConstraint2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.InferTypeArgumentsTCModel;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.PlainTypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.SimpleTypeConstraint2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.TypeConstraintVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.TypeSet;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.TypeVariable2;



public class InferTypeArgumentsConstraintsSolver {

	private final static String TYPE_ESTIMATE= "typeEstimate"; //$NON-NLS-1$
	
	private final InferTypeArgumentsTCModel fTypeConstraintFactory;
	
	/**
	 * The work-list used by the type constraint solver to hold the set of
	 * nodes in the constraint graph that remain to be (re-)processed. Entries
	 * are <code>ConstraintVariable2</code>s.
	 */
	private LinkedList/*<ConstraintVariable2>*/ fWorkList;
	
	private HashMap/*<ICompilationUnit, List<ConstraintVariable2>>*/ fDeclarationsToUpdate;
	private HashMap/*<ICompilationUnit, List<CastVariable2>>*/ fCastsToRemove;
	
	public InferTypeArgumentsConstraintsSolver(InferTypeArgumentsTCModel typeConstraintFactory) {
		fTypeConstraintFactory= typeConstraintFactory;
		fWorkList= new LinkedList();
	}
	
	public void solveConstraints() {
		// TODO: solve constraints
		ConstraintVariable2[] allConstraintVariables= fTypeConstraintFactory.getAllConstraintVariables();
		initializeTypeEstimates(allConstraintVariables);
//		EquivalenceRepresentative[] equivalenceRepresentatives= fTypeConstraintFactory.getEquivalenceRepresentatives();
//		initializeTypeEstimates(equivalenceRepresentatives);
		fWorkList.addAll(Arrays.asList(allConstraintVariables));
		runSolver();
		chooseTypes(allConstraintVariables);
		findCastsToRemove(fTypeConstraintFactory.getCastVariables());
		//chooseTypes(equivalenceRepresentatives);
		// TODO: clear caches?
//		getDeclarationsToUpdate();
	}

	private void initializeTypeEstimates(ConstraintVariable2[] allConstraintVariables) {
		for (int i= 0; i < allConstraintVariables.length; i++) {
			ConstraintVariable2 cv= allConstraintVariables[i];
			//TODO: everything is a TypeConstraintVariable2 now
			if (cv instanceof TypeConstraintVariable2) {
				TypeConstraintVariable2 typeConstraintCv= (TypeConstraintVariable2) cv;
				//TODO: not necessary for types that are not used in a TypeConstraint but only as type in CollectionElementVariable
				EquivalenceRepresentative representative= typeConstraintCv.getRepresentative();
				if (representative == null) {
					representative= new EquivalenceRepresentative(typeConstraintCv);
					representative.setTypeEstimate(TypeSet.create(typeConstraintCv.getType()));
					typeConstraintCv.setRepresentative(representative);
				} else {
					TypeSet typeEstimate= representative.getTypeEstimate();
					if (typeEstimate == null) {
						TypeConstraintVariable2[] cvs= representative.getElements();
						typeEstimate= TypeSet.getUniverse();
						for (int j= 0; j < cvs.length; j++)
							typeEstimate= typeEstimate.restrictedTo(TypeSet.create(cvs[j].getType()));
						representative.setTypeEstimate(typeEstimate);
					}
				}
//				setTypeEstimate(cv, TypeSet.create(typeConstraintCv.getType()));
			} else if (cv instanceof CollectionElementVariable2) {
//				setTypeEstimate(cv, TypeSet.getUniverse());
			}
		}
	}

//	private void initializeTypeEstimates(EquivalenceRepresentative[] equivalenceRepresentatives) {
//		for (int i= 0; i < equivalenceRepresentatives.length; i++) {
//			EquivalenceRepresentative representative= equivalenceRepresentatives[i];
//			//TODO: get existing element types iff code was already 1.5
//			if (representative.getTypeEstimate() == null)
//				representative.setTypeEstimate(TypeSet.getUniverse());
//		}
//	}

	private static void setTypeEstimate(ConstraintVariable2 cv, TypeSet typeSet) {
		if (cv instanceof TypeConstraintVariable2) {
			TypeConstraintVariable2 typeCv= (TypeConstraintVariable2) cv;
			EquivalenceRepresentative representative= typeCv.getRepresentative();
			if (representative == null) {
				representative= new EquivalenceRepresentative(typeCv);
				representative.setTypeEstimate(typeSet);
				typeCv.setRepresentative(representative);
			} else {
				representative.setTypeEstimate(typeSet);
			}
		} else {
			throw new IllegalStateException();
		}
	}

	private static TypeSet getTypeEstimate(ConstraintVariable2 cv) {
		return (TypeSet) cv.getData(TYPE_ESTIMATE);
	}
	
	private void runSolver() {
		while (! fWorkList.isEmpty()) {
			// Get a variable whose type estimate has changed
			ConstraintVariable2 cv= (ConstraintVariable2) fWorkList.removeFirst();
			List/*<ITypeConstraint2>*/ usedIn= fTypeConstraintFactory.getUsedIn(cv);
			processConstraints(usedIn, cv);
		}
	}
	
	/**
	 * Given a list of <code>ITypeConstraint2</code>s that all refer to a
	 * given <code>ConstraintVariable2</code> (whose type bound has presumably
	 * just changed), process each <code>ITypeConstraint</code>, propagating
	 * the type bound across the constraint as needed.
	 * 
	 * @param usedIn the <code>List</code> of <code>ITypeConstraint2</code>s
	 * to process
	 * @param changedCv the constraint variable whose type bound has changed
	 */
	private void processConstraints(List/*<ITypeConstraint2>*/ usedIn, ConstraintVariable2 changedCv) {
		int i= 0;
		for (Iterator iter= usedIn.iterator(); iter.hasNext(); i++) {
			ITypeConstraint2 tc= (ITypeConstraint2) iter.next();
			if (tc instanceof SimpleTypeConstraint2) {
				SimpleTypeConstraint2 stc= (SimpleTypeConstraint2) tc;
				maintainSimpleConstraint(changedCv, stc);
				//TODO: prune tcs which cannot cause further changes
				// Maybe these should be pruned after a special first loop over all ConstraintVariables,
				// Since this can only happen once for every CV in the work list.
//				if (isConstantConstraint(stc))
//					fTypeConstraintFactory.removeUsedIn(stc, changedCv);
			} else {
				//TODO
			}
		}
	}
	
	private void maintainSimpleConstraint(ConstraintVariable2 changedCv, SimpleTypeConstraint2 stc) {
		ConstraintVariable2 left= stc.getLeft();
		ConstraintVariable2 right= stc.getRight();
		
		Assert.isTrue(stc.getOperator().isSubtypeOperator()); // left <= right
		
		if (left instanceof TypeConstraintVariable2 && right instanceof TypeConstraintVariable2) {
			EquivalenceRepresentative rightRep= ((TypeConstraintVariable2) right).getRepresentative();
			TypeSet rightEstimate= rightRep.getTypeEstimate();
			EquivalenceRepresentative leftRep= ((TypeConstraintVariable2) left).getRepresentative();
			TypeSet leftEstimate= leftRep.getTypeEstimate();
			TypeSet newRightEstimate= rightEstimate.restrictedTo(leftEstimate);
			if (rightEstimate != newRightEstimate) {
				rightRep.setTypeEstimate(newRightEstimate);
				fWorkList.addAll(Arrays.asList(rightRep.getElements()));
			}
		} else {
			throw new IllegalStateException();
		}
		
	}

	private boolean isConstantConstraint(SimpleTypeConstraint2 stc) {
		return isConstantTypeEntity(stc.getLeft()) || isConstantTypeEntity(stc.getRight());
	}

	private static boolean isConstantTypeEntity(ConstraintVariable2 v) {
		return v instanceof PlainTypeVariable2 || v instanceof DeclaringTypeVariable2 || v instanceof TypeVariable2;
	}

	private void chooseTypes(ConstraintVariable2[] allConstraintVariables) {
		fDeclarationsToUpdate= new HashMap();
		for (int i= 0; i < allConstraintVariables.length; i++) {
			ConstraintVariable2 cv= allConstraintVariables[i];
			if (cv instanceof CollectionElementVariable2) {
				CollectionElementVariable2 elementCv= (CollectionElementVariable2) cv;
				EquivalenceRepresentative representative= elementCv.getRepresentative();
				if (representative == null)
					continue; //TODO: should not happen iff all unused constraint variables got pruned
				//TODO: should calculate only once per EquivalenceRepresentative; can throw away estimate TypeSet afterwards
				TType type= representative.getTypeEstimate().chooseSingleType(); //TODO: is null for Universe TypeSet
				setChosenType(elementCv, type);
				ICompilationUnit cu= elementCv.getCompilationUnit();
				if (cu != null) //TODO: shouldn't be the case
					addToMultiMap(fDeclarationsToUpdate, cu, cv);
			} else {
				TypeSet typeSet= getTypeEstimate(cv);
				if (typeSet != null)
					setChosenType(cv, typeSet.chooseSingleType());
			}
		}
	}

	private void findCastsToRemove(CastVariable2[] castVariables) {
		fCastsToRemove= new HashMap();
		for (int i= 0; i < castVariables.length; i++) {
			CastVariable2 castCv= castVariables[i];
			TypeConstraintVariable2 expressionVariable= castCv.getExpressionVariable();
			TType chosenType= InferTypeArgumentsConstraintsSolver.getChosenType(expressionVariable);
			if (chosenType != null && chosenType.canAssignTo(castCv.getType())) {
				ICompilationUnit cu= castCv.getCompilationUnit();
				addToMultiMap(fCastsToRemove, cu, castCv);
			}
		}
	}

	private void addToMultiMap(HashMap map, ICompilationUnit cu, ConstraintVariable2 cv) {
		ArrayList cvs= (ArrayList) map.get(cu);
		if (cvs != null) {
			cvs.add(cv);
		} else {
			cvs= new ArrayList(1);
			cvs.add(cv);
			map.put(cu, cvs);
		}
	}

	public HashMap/*<ICompilationUnit, List<ConstraintVariable2>>*/ getDeclarationsToUpdate() {
		return fDeclarationsToUpdate;
	}
	
	public HashMap/*<ICompilationUnit, List<CastVariable2>>*/ getCastsToRemove() {
		return fCastsToRemove;
	}
	
	public static TType getChosenType(ConstraintVariable2 cv) {
		if (cv instanceof CollectionElementVariable2) {
			CollectionElementVariable2 collectionElementCv= (CollectionElementVariable2) cv;
			EquivalenceRepresentative representative= collectionElementCv.getRepresentative();
			if (representative == null) { //TODO: should not have to set this here. Clean up when caching chosen type
				// no representative == no restriction
				representative= new EquivalenceRepresentative(collectionElementCv);
				representative.setTypeEstimate(TypeSet.getUniverse());
				collectionElementCv.setRepresentative(representative);
			}
			return representative.getTypeEstimate().chooseSingleType();
		}
		return (TType) cv.getData(TYPE_ESTIMATE);
	}

	private static void setChosenType(ConstraintVariable2 cv, TType type) {
		cv.setData(TYPE_ESTIMATE, type);
	}
}
