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

package org.eclipse.jdt.internal.ui.text.correction;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerHelpRegistry;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.MarkerAnnotation;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;

import org.eclipse.jdt.internal.corext.refactoring.NullChange;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation;
import org.eclipse.jdt.internal.ui.javaeditor.JavaAnnotationIterator;


public class JavaCorrectionProcessor implements IContentAssistProcessor {

	private static final String QUICKFIX_PROCESSOR_CONTRIBUTION_ID= "quickFixProcessor"; //$NON-NLS-1$
	private static final String QUICKASSIST_PROCESSOR_CONTRIBUTION_ID= "quickAssistProcessor"; //$NON-NLS-1$


	private static class CorrectionsComparator implements Comparator {
		
		private static Collator fgCollator= Collator.getInstance();
		
		public int compare(Object o1, Object o2) {
			if ((o1 instanceof IJavaCompletionProposal) && (o2 instanceof IJavaCompletionProposal)) {
				IJavaCompletionProposal e1= (IJavaCompletionProposal) o1;
				IJavaCompletionProposal e2= (IJavaCompletionProposal) o2;				
				int del= e2.getRelevance() - e1.getRelevance();
				if (del != 0) {
					return del;
				}
				return fgCollator.compare(e1.getDisplayString(), e2.getDisplayString());

			}				
			return fgCollator.compare(((ICompletionProposal) o1).getDisplayString(), ((ICompletionProposal) o2).getDisplayString());
		}
	}
		
	private static ContributedProcessorDescriptor[] fContributedAssistProcessors= null;
	private static ContributedProcessorDescriptor[] fContributedCorrectionProcessors= null;
	private static String fErrorMessage;
	
	private static ContributedProcessorDescriptor[] getProcessorDescriptors(String contributionId) {
		IConfigurationElement[] elements= Platform.getPluginRegistry().getConfigurationElementsFor(JavaUI.ID_PLUGIN, contributionId);
		ArrayList res= new ArrayList(elements.length);
		
		for (int i= 0; i < elements.length; i++) {
			ContributedProcessorDescriptor desc= new ContributedProcessorDescriptor(elements[i]);
			IStatus status= desc.checkSyntax();
			if (status.isOK()) {
				res.add(desc);
			} else {
				JavaPlugin.log(status);
			}
		}
		return (ContributedProcessorDescriptor[]) res.toArray(new ContributedProcessorDescriptor[res.size()]);		
	}
	
	private static ContributedProcessorDescriptor[] getCorrectionProcessors() {
		if (fContributedCorrectionProcessors == null) {
			fContributedCorrectionProcessors= getProcessorDescriptors(QUICKFIX_PROCESSOR_CONTRIBUTION_ID);
		}
		return fContributedCorrectionProcessors;
	}
	
	private static ContributedProcessorDescriptor[] getAssistProcessors() {
		if (fContributedAssistProcessors == null) {
			fContributedAssistProcessors= getProcessorDescriptors(QUICKASSIST_PROCESSOR_CONTRIBUTION_ID);
		}
		return fContributedAssistProcessors;
	}
		
	public static boolean hasCorrections(ICompilationUnit cu, int problemId) {
		ContributedProcessorDescriptor[] processors= getCorrectionProcessors();
		for (int i= 0; i < processors.length; i++) {
			try {
				ICorrectionProcessor processor= (ICorrectionProcessor) processors[i].getProcessor(cu);
				if (processor != null && processor.hasCorrections(cu, problemId)) {
					return true;
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
		return false;
	}

	public static boolean hasCorrections(IJavaAnnotation annotation) {
		int problemId= annotation.getId();
		if (problemId == -1) {
			if (annotation instanceof MarkerAnnotation) {
				return hasCorrections(((MarkerAnnotation) annotation).getMarker());
			}
		} else {
			ICompilationUnit cu= annotation.getCompilationUnit();
			if (cu != null) {
				return hasCorrections(cu, problemId);
			}
		}
		return false;
	}
	
	private static boolean hasCorrections(IMarker marker) {
		if (marker == null || !marker.exists())
			return false;
			
		IMarkerHelpRegistry registry= PlatformUI.getWorkbench().getMarkerHelpRegistry();
		return registry != null && registry.hasResolutions(marker);
	}
	
	public static boolean hasAssists(IAssistContext context) {
		ContributedProcessorDescriptor[] processors= getAssistProcessors();
		for (int i= 0; i < processors.length; i++) {
			try {
				IAssistProcessor processor= (IAssistProcessor) processors[i].getProcessor(context.getCompilationUnit());
				if (processor != null && processor.hasAssists(context)) {
					return true;
				}				
			} catch (Exception e) {
				// ignore
			}
		}
		return false;
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
		ICompilationUnit cu= JavaUI.getWorkingCopyManager().getWorkingCopy(fEditor.getEditorInput());
		IAnnotationModel model= JavaUI.getDocumentProvider().getAnnotationModel(fEditor.getEditorInput());

		int length= viewer != null ? viewer.getSelectedRange().y : 0;
		AssistContext context= new AssistContext(cu, documentOffset, length);

		fErrorMessage= null;
		ArrayList proposals= new ArrayList();
		if (model != null) {
			processProblemAnnotations(context, model, proposals);
		}
		if (proposals.isEmpty()) {
			proposals.add(new ChangeCorrectionProposal(CorrectionMessages.getString("NoCorrectionProposal.description"), new NullChange(), 0, null));  //$NON-NLS-1$
		}
		
		ICompletionProposal[] res= (ICompletionProposal[]) proposals.toArray(new ICompletionProposal[proposals.size()]);
		Arrays.sort(res, new CorrectionsComparator());
		return res;
	}
	
	private boolean isAtPosition(int offset, Position pos) {
		return (pos != null) && (offset >= pos.getOffset() && offset <= (pos.getOffset() +  pos.getLength()));
	}
	

	private void processProblemAnnotations(IAssistContext context, IAnnotationModel model, ArrayList proposals) {
		int offset= context.getSelectionOffset();
		
		ArrayList problems= new ArrayList();
		Iterator iter= new JavaAnnotationIterator(model, true);
		while (iter.hasNext()) {
			IJavaAnnotation annot= (IJavaAnnotation) iter.next();
			Position pos= model.getPosition((Annotation) annot);
			if (isAtPosition(offset, pos)) {
				int problemId= annot.getId();
				if (problemId != -1) {
					problems.add(new ProblemLocation(pos.getOffset(), pos.getLength(), annot));
				} else {
					if (annot instanceof MarkerAnnotation) {
						IMarker marker= ((MarkerAnnotation) annot).getMarker();
						IMarkerResolution[] res= PlatformUI.getWorkbench().getMarkerHelpRegistry().getResolutions(marker);
						if (res.length > 0) {
							for (int i= 0; i < res.length; i++) {
								proposals.add(new MarkerResolutionProposal(res[i], marker));
							}
						}
					}
				}
			}
		}	
		IProblemLocation[] problemLocations= (IProblemLocation[]) problems.toArray(new IProblemLocation[problems.size()]);
		collectCorrections(context, problemLocations, proposals);
		collectAssists(context, problemLocations, proposals);
	}

	public static void collectCorrections(IAssistContext context, IProblemLocation[] locations, ArrayList proposals) {
		ContributedProcessorDescriptor[] processors= getCorrectionProcessors();
		for (int i= 0; i < processors.length; i++) {
			try {
				ICorrectionProcessor curr= (ICorrectionProcessor) processors[i].getProcessor(context.getCompilationUnit());
				if (curr != null) {
					IJavaCompletionProposal[] res= curr.getCorrections(context, locations);
					if (res != null) {
						for (int k= 0; k < res.length; k++) {
							proposals.add(res[k]);
						}
					}
				}
			} catch (Exception e) {
				fErrorMessage= CorrectionMessages.getString("JavaCorrectionProcessor.error.quickfix.message"); //$NON-NLS-1$
				JavaPlugin.log(e);
			}
		}
	}
	
	public static void collectAssists(IAssistContext context, IProblemLocation[] locations, ArrayList proposals) {
		ContributedProcessorDescriptor[] processors= getAssistProcessors();
		for (int i= 0; i < processors.length; i++) {
			try {
				IAssistProcessor curr= (IAssistProcessor) processors[i].getProcessor(context.getCompilationUnit());
				if (curr != null) {
					IJavaCompletionProposal[] res= curr.getAssists(context, locations);
					if (res != null) {
						for (int k= 0; k < res.length; k++) {
							proposals.add(res[k]);
						}
					}				
				}				
			} catch (Exception e) {
				fErrorMessage= CorrectionMessages.getString("JavaCorrectionProcessor.error.quickassist.message"); //$NON-NLS-1$
				JavaPlugin.log(e);
			}
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
		return fErrorMessage;
	}
}
