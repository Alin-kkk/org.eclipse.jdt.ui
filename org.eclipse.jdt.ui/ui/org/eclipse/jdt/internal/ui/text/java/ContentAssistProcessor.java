/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.bindings.TriggerSequence;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistantExtension2;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;

import org.eclipse.jdt.internal.corext.Assert;

import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;

import org.eclipse.jdt.internal.ui.JavaUIMessages;

/**
 * A content assist processor that aggregates the proposals of the
 * {@link org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer}s contributed via the
 * <code>org.eclipse.jdt.ui.javaCompletionProposalComputer</code> extension point.
 * <p>
 * Subclasses may extend:
 * <ul>
 * <li><code>createContext</code> to provide the context object passed to the computers</li>
 * <li><code>createProgressMonitor</code> to change the way progress is reported</li>
 * <li><code>filterAndSort</code> to add sorting and filtering</li>
 * <li><code>getContextInformationValidator</code> to add context validation (needed if any
 * contexts are provided)</li>
 * <li><code>getErrorMessage</code> to change error reporting</li>
 * </ul>
 * </p>
 * 
 * @since 3.2
 */
public class ContentAssistProcessor implements IContentAssistProcessor {
	private static final boolean DEBUG= "true".equalsIgnoreCase(Platform.getDebugOption("org.eclipse.jdt.ui/debug/ResultCollector"));  //$NON-NLS-1$//$NON-NLS-2$

	private static final Comparator ORDER_COMPARATOR= new Comparator() {

		public int compare(Object o1, Object o2) {
			CompletionProposalCategory d1= (CompletionProposalCategory) o1;
			CompletionProposalCategory d2= (CompletionProposalCategory) o2;
			
			return d1.getSortOrder() - d2.getSortOrder();
		}
		
	};
	
	private final List fCategories;
	private final String fPartition;
	private final ContentAssistant fAssistant;
	
	private char[] fCompletionAutoActivationCharacters;
	
	/* cycling stuff */
	private int fRepetition= -1;
	private List/*<List<CompletionProposalCategory>>*/ fCategoryIteration= null;
	private String fIterationGesture= null;
	private int fNumberOfComputedResults= 0;
	private String fErrorMessage;
	
	public ContentAssistProcessor(ContentAssistant assistant, String partition) {
		Assert.isNotNull(partition);
		Assert.isNotNull(assistant);
		fPartition= partition;
		fCategories= CompletionProposalComputerRegistry.getDefault().getProposalCategories();
		fAssistant= assistant;
		fAssistant.addCompletionListener(new ICompletionListener() {
			
			/*
			 * @see org.eclipse.jface.text.contentassist.ICompletionListener#assistSessionStarted(org.eclipse.jface.text.contentassist.ContentAssistEvent)
			 */
			public void assistSessionStarted(ContentAssistEvent event) {
				if (event.processor != ContentAssistProcessor.this)
					return;
				
				fCategoryIteration= getCategoryIteration();
				fRepetition= 0;
				fIterationGesture= getIterationGesture();
				if (event.assistant instanceof IContentAssistantExtension2) {
					IContentAssistantExtension2 extension= (IContentAssistantExtension2) event.assistant;

					if (fCategoryIteration.size() == 1) {
						extension.setRepeatedInvocationMode(false);
						extension.setShowEmptyList(false);
					} else {
						extension.setRepeatedInvocationMode(true);
						extension.setStatusLineVisible(true);
						extension.setStatusMessage(createIterationMessage());
						extension.setShowEmptyList(true);
					}
				
				}
			}
			
			/*
			 * @see org.eclipse.jface.text.contentassist.ICompletionListener#assistSessionEnded(org.eclipse.jface.text.contentassist.ContentAssistEvent)
			 */
			public void assistSessionEnded(ContentAssistEvent event) {
				if (event.processor != ContentAssistProcessor.this)
					return;
				
				fCategoryIteration= null;
				fRepetition= -1;
				fIterationGesture= null;
				if (event.assistant instanceof IContentAssistantExtension2) {
					IContentAssistantExtension2 extension= (IContentAssistantExtension2) event.assistant;
					extension.setShowEmptyList(false);
					extension.setRepeatedInvocationMode(false);
					extension.setStatusLineVisible(false);
				}
			}

			/*
			 * @see org.eclipse.jface.text.contentassist.ICompletionListener#selectionChanged(org.eclipse.jface.text.contentassist.ICompletionProposal, boolean)
			 */
			public void selectionChanged(ICompletionProposal proposal, boolean smartToggle) {}
			
		});
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeCompletionProposals(org.eclipse.jface.text.ITextViewer, int)
	 */
	public final ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		long start= DEBUG ? System.currentTimeMillis() : 0;
		
		clearState();
		
		IProgressMonitor monitor= createProgressMonitor();
		monitor.beginTask(JavaTextMessages.ContentAssistProcessor_computing_proposals, fCategories.size() + 1);

		ContentAssistInvocationContext context= createContext(viewer, offset);
		long setup= DEBUG ? System.currentTimeMillis() : 0;
		
		monitor.subTask(JavaTextMessages.ContentAssistProcessor_collecting_proposals);
		List proposals= collectProposals(viewer, offset, monitor, context);
		long collect= DEBUG ? System.currentTimeMillis() : 0;

		monitor.subTask(JavaTextMessages.ContentAssistProcessor_sorting_proposals);
		List filtered= filterAndSortProposals(proposals, monitor, context);
		fNumberOfComputedResults= filtered.size();
		long filter= DEBUG ? System.currentTimeMillis() : 0;
		
		ICompletionProposal[] result= (ICompletionProposal[]) filtered.toArray(new ICompletionProposal[filtered.size()]);
		monitor.done();
		
		if (DEBUG) {
			System.err.println("Code Assist Stats (" + result.length + " proposals)"); //$NON-NLS-1$ //$NON-NLS-2$
			System.err.println("Code Assist (setup):\t" + (setup - start) ); //$NON-NLS-1$
			System.err.println("Code Assist (collect):\t" + (collect - setup) ); //$NON-NLS-1$
			System.err.println("Code Assist (sort):\t" + (filter - collect) ); //$NON-NLS-1$
		}
		
		return result;
	}

	private void clearState() {
		fErrorMessage=null;
		fNumberOfComputedResults= 0;
	}

	private List collectProposals(ITextViewer viewer, int offset, IProgressMonitor monitor, ContentAssistInvocationContext context) {
		List proposals= new ArrayList();
		List providers= getCategories();
		for (Iterator it= providers.iterator(); it.hasNext();) {
			CompletionProposalCategory cat= (CompletionProposalCategory) it.next();
			List computed= cat.computeCompletionProposals(context, fPartition, new SubProgressMonitor(monitor, 1));
			proposals.addAll(computed);
			if (fErrorMessage == null)
				fErrorMessage= cat.getErrorMessage();
		}
		
		return proposals;
	}

	/**
	 * Filters and sorts the proposals. The passed list may be modified
	 * and returned, or a new list may be created and returned.
	 * 
	 * @param proposals the list of collected proposals (element type:
	 *        {@link ICompletionProposal})
	 * @param monitor a progress monitor
	 * @param context TODO
	 * @return the list of filtered and sorted proposals, ready for
	 *         display (element type: {@link ICompletionProposal})
	 */
	protected List filterAndSortProposals(List proposals, IProgressMonitor monitor, ContentAssistInvocationContext context) {
		return proposals;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeContextInformation(org.eclipse.jface.text.ITextViewer, int)
	 */
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		clearState();

		IProgressMonitor monitor= createProgressMonitor();
		monitor.beginTask(JavaTextMessages.ContentAssistProcessor_computing_contexts, fCategories.size() + 1);
		
		monitor.subTask(JavaTextMessages.ContentAssistProcessor_collecting_contexts);
		List proposals= collectContextInformation(viewer, offset, monitor);

		monitor.subTask(JavaTextMessages.ContentAssistProcessor_sorting_contexts);
		List filtered= filterAndSortContextInformation(proposals, monitor);
		fNumberOfComputedResults= filtered.size();
		
		IContextInformation[] result= (IContextInformation[]) filtered.toArray(new IContextInformation[filtered.size()]);
		monitor.done();
		return result;
	}

	private List collectContextInformation(ITextViewer viewer, int offset, IProgressMonitor monitor) {
		List proposals= new ArrayList();
		ContentAssistInvocationContext context= createContext(viewer, offset);
		
		List providers= getCategories();
		for (Iterator it= providers.iterator(); it.hasNext();) {
			CompletionProposalCategory cat= (CompletionProposalCategory) it.next();
			List computed= cat.computeContextInformation(context, fPartition, new SubProgressMonitor(monitor, 1));
			proposals.addAll(computed);
			if (fErrorMessage == null)
				fErrorMessage= cat.getErrorMessage();
		}
		
		return proposals;
	}

	/**
	 * Filters and sorts the context information objects. The passed
	 * list may be modified and returned, or a new list may be created
	 * and returned.
	 * 
	 * @param contexts the list of collected proposals (element type:
	 *        {@link IContextInformation})
	 * @param monitor a progress monitor
	 * @return the list of filtered and sorted proposals, ready for
	 *         display (element type: {@link IContextInformation})
	 */
	protected List filterAndSortContextInformation(List contexts, IProgressMonitor monitor) {
		return contexts;
	}

	/**
	 * Sets this processor's set of characters triggering the activation of the
	 * completion proposal computation.
	 *
	 * @param activationSet the activation set
	 */
	public final void setCompletionProposalAutoActivationCharacters(char[] activationSet) {
		fCompletionAutoActivationCharacters= activationSet;
	}


	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	public final char[] getCompletionProposalAutoActivationCharacters() {
		return fCompletionAutoActivationCharacters;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationAutoActivationCharacters()
	 */
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		if (fNumberOfComputedResults > 0)
			return null;
		if (fErrorMessage != null)
			return fErrorMessage;
		return JavaUIMessages.JavaEditor_codeassist_noCompletions;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationValidator()
	 */
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	/**
	 * Creates a progress monitor.
	 * <p>
	 * The default implementation creates a
	 * <code>NullProgressMonitor</code>.
	 * </p>
	 * 
	 * @return a progress monitor
	 */
	protected IProgressMonitor createProgressMonitor() {
		return new NullProgressMonitor();
	}

	/**
	 * Creates the context that is passed to the completion proposal
	 * computers.
	 * 
	 * @param viewer the viewer that content assist is invoked on
	 * @param offset the content assist offset
	 * @return the context to be passed to the computers
	 */
	protected ContentAssistInvocationContext createContext(ITextViewer viewer, int offset) {
		return new ContentAssistInvocationContext(viewer, offset);
	}

	private List getCategories() {
		if (fCategoryIteration == null)
			return fCategories;
		
		int iteration= fRepetition % fCategoryIteration.size();
		fAssistant.setStatusMessage(createIterationMessage());
		fAssistant.setEmptyMessage(createEmptyMessage());
		fRepetition++;
		
//		fAssistant.setShowMessage(fRepetition % 2 != 0);
//		
		return (List) fCategoryIteration.get(iteration);
	}

	private List getCategoryIteration() {
		List sequence= new ArrayList();
		sequence.add(getDefaultCategories());
		for (Iterator it= getSeparateCategories().iterator(); it.hasNext();) {
			CompletionProposalCategory cat= (CompletionProposalCategory) it.next();
			sequence.add(Collections.singletonList(cat));
		}
		return sequence;
	}

	private List getDefaultCategories() {
		// default mix - enable all included computers
		List included= new ArrayList();
		for (Iterator it= fCategories.iterator(); it.hasNext();) {
			CompletionProposalCategory category= (CompletionProposalCategory) it.next();
			if (category.isIncluded() && category.hasComputers(fPartition))
				included.add(category);
		}
		return included;
	}

	private List getSeparateCategories() {
		ArrayList sorted= new ArrayList();
		for (Iterator it= fCategories.iterator(); it.hasNext();) {
			CompletionProposalCategory category= (CompletionProposalCategory) it.next();
			if (category.isSeparateCommand() && category.hasComputers(fPartition))
				sorted.add(category);
		}
		Collections.sort(sorted, ORDER_COMPARATOR);
		return sorted;
	}
	
	private String createEmptyMessage() {
		final MessageFormat format= new MessageFormat(JavaTextMessages.ContentAssistProcessor_empty_message);
		Object[] args= {getCategoryLabel(fRepetition)};
		String message= format.format(args);
		return message;
	}
	
	private String createIterationMessage() {
		final MessageFormat format= new MessageFormat(JavaTextMessages.ContentAssistProcessor_toggle_affordance_update_message);
		String current= getCategoryLabel(fRepetition);
		String next= getCategoryLabel(fRepetition + 1);
		Object[] args= { current, fIterationGesture, next };
		String message= format.format(args);
		return message;
	}
	
	private String getCategoryLabel(int repetition) {
		int iteration= repetition % fCategoryIteration.size();
		if (iteration == 0)
			return JavaTextMessages.ContentAssistProcessor_defaultProposalCategory;
		return toString((CompletionProposalCategory) ((List) fCategoryIteration.get(iteration)).get(0));
	}
	
	private String toString(CompletionProposalCategory category) {
		return category.getName().replaceAll("&", ""); //$NON-NLS-1$ //$NON-NLS-2$;
	}

	private String getIterationGesture() {
		final IBindingService bindingSvc= (IBindingService) PlatformUI.getWorkbench().getAdapter(IBindingService.class);
		TriggerSequence[] triggers= bindingSvc.getActiveBindingsFor(getContentAssistCommand());
		return triggers.length > 0 ? 
				  MessageFormat.format(JavaTextMessages.ContentAssistProcessor_toggle_affordance_press_gesture, new Object[] { triggers[0].format() })
				: JavaTextMessages.ContentAssistProcessor_toggle_affordance_click_gesture;
	}

	private ParameterizedCommand getContentAssistCommand() {
		final ICommandService commandSvc= (ICommandService) PlatformUI.getWorkbench().getAdapter(ICommandService.class);
		final Command command= commandSvc.getCommand(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
		ParameterizedCommand pCmd= new ParameterizedCommand(command, null);
		return pCmd;
	}
	
}
