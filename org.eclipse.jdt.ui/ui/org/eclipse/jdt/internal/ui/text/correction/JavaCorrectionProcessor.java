/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.jdt.internal.ui.text.correction;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.IDocumentProvider;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.ui.IWorkingCopyManager;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.IProblemAnnotation;
import org.eclipse.jdt.internal.ui.javaeditor.ProblemAnnotationIterator;


public class JavaCorrectionProcessor implements IContentAssistProcessor {

	public static boolean hasCorrections(int problemId) {
		switch (problemId) {
			case IProblem.UnterminatedString:
			case IProblem.UnterminatedComment:
			case IProblem.UndefinedMethod:
			case IProblem.ParameterMismatch:
			case IProblem.MethodButWithConstructorName:
			case IProblem.UndefinedField:
			case IProblem.UndefinedName:
			case IProblem.PublicClassMustMatchFileName:
			case IProblem.PackageIsNotExpectedPackage:
			case IProblem.UndefinedType:
			case IProblem.FieldTypeNotFound:
			case IProblem.ArgumentTypeNotFound:
			case IProblem.ReturnTypeNotFound:
			case IProblem.SuperclassNotFound:
			case IProblem.ExceptionTypeNotFound:
			case IProblem.InterfaceNotFound: 
			case IProblem.TypeMismatch:
			case IProblem.UnhandledException:
				return true;
			default:
				return false;
		}
	}
	
	private static class CorrectionsComparator implements Comparator {
		
		private static Collator fgCollator= Collator.getInstance();
		
		public int compare(Object o1, Object o2) {
			ChangeCorrectionProposal e1= (ChangeCorrectionProposal) o1;
			ChangeCorrectionProposal e2= (ChangeCorrectionProposal) o2;
			int del= e2.getRelevance() - e1.getRelevance();
			if (del != 0) {
				return del;
			}
			return fgCollator.compare(e1.getDisplayString(), e2.getDisplayString());
		}
	}


	private IEditorPart fEditor;

	/**
	 * Constructor for JavaCorrectionProcessor.
	 */
	public JavaCorrectionProcessor(IEditorPart editor) {
		fEditor= editor;
	}

	/*
	 * @see IContentAssistProcessor#computeCompletionProposals(ITextViewer, int)
	 */
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int documentOffset) {
		
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		ICompilationUnit cu= manager.getWorkingCopy(fEditor.getEditorInput());
		
		IDocumentProvider provider= JavaPlugin.getDefault().getCompilationUnitDocumentProvider();
		IAnnotationModel model= provider.getAnnotationModel(fEditor.getEditorInput());

		ArrayList proposals= new ArrayList();
		HashSet idsProcessed= new HashSet();
			
		for (Iterator iter= new ProblemAnnotationIterator(model); iter.hasNext();) {
			IProblemAnnotation annot= (IProblemAnnotation) iter.next();
			Position pos= model.getPosition((Annotation) annot);
			if (pos != null) {
				int start= pos.getOffset();
				if (documentOffset >= start && documentOffset <= (start +  pos.getLength())) {
					Integer probId= new Integer(annot.getId());
					if (!idsProcessed.contains(probId)) {
						ProblemPosition pp = new ProblemPosition(pos, annot, cu);
						idsProcessed.add(probId);
						collectCorrections(pp, proposals);
					}
				}
			}
		}
		if (proposals.isEmpty()) {
			proposals.add(new NoCorrectionProposal(null));
		}
		ICompletionProposal[] res= (ICompletionProposal[]) proposals.toArray(new ICompletionProposal[proposals.size()]);
		Arrays.sort(res, new CorrectionsComparator());
		return res;
	}
	
	private void collectCorrections(ProblemPosition problemPos, ArrayList proposals) {
		try {
			int id= problemPos.getId();
			
			
			switch (id) {
				case IProblem.UnterminatedString:
					String quoteLabel= CorrectionMessages.getString("JavaCorrectionProcessor.addquote.description"); //$NON-NLS-1$
					int pos= InsertCorrectionProposal.moveBack(problemPos.getOffset() + problemPos.getLength(), problemPos.getOffset(), "\n\r", problemPos.getCompilationUnit()); //$NON-NLS-1$
					proposals.add(new InsertCorrectionProposal(quoteLabel, problemPos.getCompilationUnit(), pos, "\"", 0)); //$NON-NLS-1$ 
					break;
				case IProblem.UnterminatedComment:
					String commentLabel= CorrectionMessages.getString("JavaCorrectionProcessor.addcomment.description"); //$NON-NLS-1$
					proposals.add(new InsertCorrectionProposal(commentLabel, problemPos.getCompilationUnit(), problemPos.getOffset() + problemPos.getLength(), "*/", 0)); //$NON-NLS-1$
					break;
				case IProblem.UndefinedMethod:
					UnresolvedElementsSubProcessor.getMethodProposals(problemPos, false, proposals);
					break;
				case IProblem.ParameterMismatch:
					UnresolvedElementsSubProcessor.getMethodProposals(problemPos, true, proposals);
					break;
				case IProblem.MethodButWithConstructorName:	
					LocalCorrectionsSubProcessor.addMethodWithConstrNameProposals(problemPos, proposals);
					break;
				case IProblem.UndefinedField:
				case IProblem.UndefinedName:
					UnresolvedElementsSubProcessor.getVariableProposals(problemPos, proposals);
					break;					
				case IProblem.PublicClassMustMatchFileName:
					ReorgCorrectionsSubProcessor.getWrongTypeNameProposals(problemPos, proposals);
					break;
				case IProblem.PackageIsNotExpectedPackage:
					ReorgCorrectionsSubProcessor.getWrongPackageDeclNameProposals(problemPos, proposals);
					break;
				case IProblem.UndefinedType:
				case IProblem.FieldTypeNotFound:
				case IProblem.ArgumentTypeNotFound:
					UnresolvedElementsSubProcessor.getTypeProposals(problemPos, SimilarElementsRequestor.ALL_TYPES, proposals);
					break;
				case IProblem.ReturnTypeNotFound:
					UnresolvedElementsSubProcessor.getTypeProposals(problemPos, SimilarElementsRequestor.ALL_TYPES | SimilarElementsRequestor.VOIDTYPE, proposals);
					break;
				case IProblem.SuperclassNotFound:
				case IProblem.ExceptionTypeNotFound:
					UnresolvedElementsSubProcessor.getTypeProposals(problemPos, SimilarElementsRequestor.CLASSES, proposals);
					break;				
				case IProblem.InterfaceNotFound: 
					UnresolvedElementsSubProcessor.getTypeProposals(problemPos, SimilarElementsRequestor.INTERFACES, proposals);
					break;	
				case IProblem.TypeMismatch:
					LocalCorrectionsSubProcessor.addCastProposals(problemPos, proposals);
					break;
				case IProblem.UnhandledException:
					LocalCorrectionsSubProcessor.addUncaughtExceptionProposals(problemPos, proposals);
					break;
				case IProblem.LocalVariableIsNeverUsed:
					LocalCorrectionsSubProcessor.addUnusedVariableProposals(problemPos, proposals);
					break;
				case IProblem.VoidMethodReturnsValue:
					LocalCorrectionsSubProcessor.addVoidMethodReturnsProposals(problemPos, proposals);
					break;
				case IProblem.MissingReturnType:
					LocalCorrectionsSubProcessor.addMissingReturnTypeProposals(problemPos, proposals);
					break;
				default:
					 proposals.add(new NoCorrectionProposal(problemPos));
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
	}

	/*
	 * @see IContentAssistProcessor#computeContextInformation(ITextViewer, int)
	 */
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int documentOffset) {
		return null;
	}

	/*
	 * @see IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	public char[] getCompletionProposalAutoActivationCharacters() {
		return null;
	}

	/*
	 * @see IContentAssistProcessor#getContextInformationAutoActivationCharacters()
	 */
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	/*
	 * @see IContentAssistProcessor#getContextInformationValidator()
	 */
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	/*
	 * @see IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		return null;
	}
	

	
	

}