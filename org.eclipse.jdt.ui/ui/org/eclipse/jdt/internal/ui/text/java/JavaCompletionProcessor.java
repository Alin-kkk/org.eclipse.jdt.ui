package org.eclipse.jdt.internal.ui.text.java;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationExtension;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import org.eclipse.ui.IEditorPart;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.corext.template.ContextType;
import org.eclipse.jdt.internal.corext.template.ContextTypeRegistry;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.JavaCodeReader;
import org.eclipse.jdt.internal.ui.text.template.TemplateEngine;


/**
 * Java completion processor.
 */
public class JavaCompletionProcessor implements IContentAssistProcessor {
		
	private static class ContextInformationWrapper implements IContextInformation, IContextInformationExtension {
		
		private final IContextInformation fContextInformation;
		private int fPosition;
		
		public ContextInformationWrapper(IContextInformation contextInformation) {
			fContextInformation= contextInformation;
		}
		
		/*
		 * @see IContextInformation#getContextDisplayString()
		 */
		public String getContextDisplayString() {
			return fContextInformation.getContextDisplayString();
		}

			/*
		 * @see IContextInformation#getImage()
		 */
		public Image getImage() {
			return fContextInformation.getImage();
		}

		/*
		 * @see IContextInformation#getInformationDisplayString()
		 */
		public String getInformationDisplayString() {
			return fContextInformation.getInformationDisplayString();
		}

		/*
		 * @see IContextInformationExtension#getContextInformationPosition()
		 */
		public int getContextInformationPosition() {
			return fPosition;
		}
		
		public void setContextInformationPosition(int position) {
			fPosition= position;	
		}
	};
	
	
	private final static String VISIBILITY= JavaCore.CODEASSIST_VISIBILITY_CHECK;
	private final static String ENABLED= "enabled"; //$NON-NLS-1$
	private final static String DISABLED= "disabled"; //$NON-NLS-1$
	
	
	
	private IEditorPart fEditor;
	private ResultCollector fCollector;
	private IWorkingCopyManager fManager;
	private IContextInformationValidator fValidator;
	
	private char[] fProposalAutoActivationSet;
	private JavaCompletionProposalComparator fComparator;
	private boolean fAllowAddImports;
	
	private TemplateEngine fTemplateEngine;
	private ExperimentalResultCollector fExperimentalCollector;
	
	private int fNumberOfComputedResults= 0;
	
	
	public JavaCompletionProcessor(IEditorPart editor) {
		fEditor= editor;
		fCollector= new ResultCollector();
		fManager= JavaPlugin.getDefault().getWorkingCopyManager();
		ContextType contextType= ContextTypeRegistry.getInstance().getContextType("java"); //$NON-NLS-1$
		if (contextType != null)
			fTemplateEngine= new TemplateEngine(contextType);
		fExperimentalCollector= new ExperimentalResultCollector();
		fAllowAddImports= true;
		
		fComparator= new JavaCompletionProposalComparator();
	}
	
	/**
	 * Sets this processor's set of characters triggering the activation of the
	 * completion proposal computation.
	 * 
	 * @param activationSet the activation set
	 */
	public void setCompletionProposalAutoActivationCharacters(char[] activationSet) {
		fProposalAutoActivationSet= activationSet;
	}
	
	/**
	 * Tells this processor to restrict its proposal to those element
	 * visible in the actual invocation context.
	 * 
	 * @param restrict <code>true</code> if proposals should be restricted
	 */
	public void restrictProposalsToVisibility(boolean restrict) {
		Hashtable options= JavaCore.getOptions();
		Object value= options.get(VISIBILITY);
		if (value instanceof String) {
			String newValue= restrict ? ENABLED : DISABLED;
			if ( !newValue.equals((String) value)) {
				options.put(VISIBILITY, newValue);
				JavaCore.setOptions(options);
			}
		}
	}
	
	/**
	 * Tells this processor to order the proposals alphabetically.
	 * 
	 * @param order <code>true</code> if proposals should be ordered.
	 */
	public void orderProposalsAlphabetically(boolean order) {
		fComparator.setOrderAlphabetically(order);
	}
	
	/**
	 * Tells this processor to restrict is proposals to those
	 * starting with matching cases.
	 * 
	 * @param restrict <code>true</code> if proposals should be restricted
	 */
	public void restrictProposalsToMatchingCases(boolean restrict) {
		// not yet supported
	}
	
	/**
	 * Tells this processor to add import statement for proposals that have
	 * a fully qualified type name
	 * 
	 * @param restrict <code>true</code> if import can be added
	 */
	public void allowAddingImports(boolean allowAddingImports) {
		fAllowAddImports= allowAddingImports;
	}	
		
	/**
	 * @see IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		if (fNumberOfComputedResults == 0)
			return "No completions available.";
		return fCollector.getErrorMessage();
	}

	/**
	 * @see IContentAssistProcessor#getContextInformationValidator()
	 */
	public IContextInformationValidator getContextInformationValidator() {
		if (fValidator == null)
			fValidator= new JavaParameterListValidator();
		return fValidator;
	}

	/**
	 * @see IContentAssistProcessor#getContextInformationAutoActivationCharacters()
	 */
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	/**
	 * @see IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	public char[] getCompletionProposalAutoActivationCharacters() {
		return fProposalAutoActivationSet;
	}
	
	private boolean looksLikeMethod(JavaCodeReader reader) throws IOException {
		int curr= reader.read();
		while (curr != JavaCodeReader.EOF && Character.isWhitespace((char) curr))
			curr= reader.read();
			
		if (curr == JavaCodeReader.EOF)
			return false;

		return Character.isJavaIdentifierPart((char) curr) || Character.isJavaIdentifierStart((char) curr);
	}
	
	private int guessContextInformationPosition(ITextViewer viewer, int offset) {
		int contextPosition= offset;
			
		IDocument document= viewer.getDocument();
		
		try {

			JavaCodeReader reader= new JavaCodeReader();
			reader.configureBackwardReader(document, offset, true, true);
	
			int nestingLevel= 0;

			int curr= reader.read();		
			while (curr != JavaCodeReader.EOF) {

				if (')' == (char) curr)
					++ nestingLevel;

				else if ('(' == (char) curr) {
					-- nestingLevel;
				
					if (nestingLevel < 0) {
						int start= reader.getOffset();
						if (looksLikeMethod(reader))
							return start + 1;
					}	
				}

				curr= reader.read();					
			}
		} catch (IOException e) {
		}
		
		return contextPosition;
	}		
	
	private List addContextInformations(ITextViewer viewer, int offset) {
		ICompletionProposal[] proposals= computeCompletionProposals(viewer, offset);

		List result= new ArrayList();
		for (int i= 0; i < proposals.length; i++) {
			IContextInformation contextInformation= proposals[i].getContextInformation();
			if (contextInformation != null) {
				ContextInformationWrapper wrapper= new ContextInformationWrapper(contextInformation);
				wrapper.setContextInformationPosition(offset);
				result.add(wrapper);				
			}
		}
		return result;
	}
	
	/**
	 * @see IContentAssistProcessor#computeContextInformation(ITextViewer, int)
	 */
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		int contextInformationPosition= guessContextInformationPosition(viewer, offset);
		List result= addContextInformations(viewer, contextInformationPosition);
		return (IContextInformation[]) result.toArray(new IContextInformation[result.size()]);
	}
	
	/**
	 * Order the given proposals.
	 */
	private ICompletionProposal[] order(ICompletionProposal[] proposals) {
		Arrays.sort(proposals, fComparator);
		return proposals;	
	}
	
	/**
	 * @see IContentAssistProcessor#computeCompletionProposals(ITextViewer, int)
	 */
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		
		ICompilationUnit unit= fManager.getWorkingCopy(fEditor.getEditorInput());
		IJavaCompletionProposal[] results;

		if (JavaPlugin.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.CODEASSIST_FILL_ARGUMENT_NAMES)) {
				
			try {
				if (unit != null) {
	
					fExperimentalCollector.reset(offset, unit.getJavaProject(), fAllowAddImports ? unit : null);
					fExperimentalCollector.setViewer(viewer);
					
					Point selection= viewer.getSelectedRange();
					if (selection.y > 0)
						fExperimentalCollector.setReplacementLength(selection.y);
					
					unit.codeComplete(offset, fExperimentalCollector);
				}
			} catch (JavaModelException x) {
				Shell shell= viewer.getTextWidget().getShell();
				ErrorDialog.openError(shell, JavaTextMessages.getString("CompletionProcessor.error.accessing.title"), JavaTextMessages.getString("CompletionProcessor.error.accessing.message"), x.getStatus()); //$NON-NLS-2$ //$NON-NLS-1$
			}				

			results= fExperimentalCollector.getResults();

		} else {

			try {
				if (unit != null) {
	
					fCollector.reset(offset, unit.getJavaProject(), fAllowAddImports ? unit : null);
					Point selection= viewer.getSelectedRange();
					if (selection.y > 0)
						fCollector.setReplacementLength(selection.y);
					
					unit.codeComplete(offset, fCollector);
				}
			} catch (JavaModelException x) {
				Shell shell= viewer.getTextWidget().getShell();
				ErrorDialog.openError(shell, JavaTextMessages.getString("CompletionProcessor.error.accessing.title"), JavaTextMessages.getString("CompletionProcessor.error.accessing.message"), x.getStatus()); //$NON-NLS-2$ //$NON-NLS-1$
			}
			
			results= fCollector.getResults();
		}

		if (fTemplateEngine != null) {
			try {
				fTemplateEngine.reset();
				fTemplateEngine.complete(viewer, offset, unit);
			} catch (JavaModelException x) {
				Shell shell= viewer.getTextWidget().getShell();
				ErrorDialog.openError(shell, JavaTextMessages.getString("CompletionProcessor.error.accessing.title"), JavaTextMessages.getString("CompletionProcessor.error.accessing.message"), x.getStatus()); //$NON-NLS-2$ //$NON-NLS-1$
			}				
			
			IJavaCompletionProposal[] templateResults= fTemplateEngine.getResults();
	
			// concatenate arrays
			IJavaCompletionProposal[] total= new IJavaCompletionProposal[results.length + templateResults.length];
			System.arraycopy(templateResults, 0, total, 0, templateResults.length);
			System.arraycopy(results, 0, total, templateResults.length, results.length);
			results= total;
		}
		
		fNumberOfComputedResults= (results == null ? 0 : results.length);
		
		/*
		 * Order here and not in result collector to make sure that the order
		 * applies to all proposals and not just those of the compilation unit. 
		 */
		return order(results);
	}
}