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

package org.eclipse.jdt.internal.ui.javaeditor;


import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ILineTracker;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.IWidgetTokenKeeper;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.source.IOverviewRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.editors.text.IStorageDocumentProvider;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ContentAssistAction;
import org.eclipse.ui.texteditor.ExtendedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.TextOperationAction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.AddBlockCommentAction;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.actions.IndentAction;
import org.eclipse.jdt.internal.ui.actions.RemoveBlockCommentAction;
import org.eclipse.jdt.internal.ui.compare.LocalHistoryActionGroup;
import org.eclipse.jdt.internal.ui.text.ContentAssistPreference;
import org.eclipse.jdt.internal.ui.text.IJavaPartitions;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionAssistant;
import org.eclipse.jdt.internal.ui.text.java.IReconcilingParticipant;
import org.eclipse.jdt.internal.ui.text.java.SmartSemicolonAutoEditStrategy;
import org.eclipse.jdt.internal.ui.text.link.LinkedPositionManager;
import org.eclipse.jdt.internal.ui.text.link.LinkedPositionUI;
import org.eclipse.jdt.internal.ui.text.link.LinkedPositionUI.ExitFlags;



/**
 * Java specific text editor.
 */
public class CompilationUnitEditor extends JavaEditor implements IReconcilingParticipant {

	/** 
	 * Text operation code for requesting correction assist to show correction
	 * proposals for the current position. 
	 */
	public static final int CORRECTIONASSIST_PROPOSALS= 50;

	
	interface ITextConverter {
		void customizeDocumentCommand(IDocument document, DocumentCommand command);
	}
	
	class AdaptedSourceViewer extends JavaSourceViewer  {
				
		private List fTextConverters;
		private boolean fIgnoreTextConverters= false;
		private JavaCorrectionAssistant fCorrectionAssistant;
		
		public AdaptedSourceViewer(Composite parent, IVerticalRuler verticalRuler, IOverviewRuler overviewRuler, boolean showAnnotationsOverview, int styles) {
			super(parent, verticalRuler, overviewRuler, showAnnotationsOverview, styles);
		}
				
		public IContentAssistant getContentAssistant() {
			return fContentAssistant;
		}
		
		/*
		 * @see ITextOperationTarget#doOperation(int)
		 */
		public void doOperation(int operation) {
		
			if (getTextWidget() == null)
				return;
			
			switch (operation) {
				case CONTENTASSIST_PROPOSALS:
					String msg= fContentAssistant.showPossibleCompletions();
					setStatusLineErrorMessage(msg);
					return;
				case CORRECTIONASSIST_PROPOSALS:
					msg= fCorrectionAssistant.showPossibleCompletions();
					setStatusLineErrorMessage(msg);
					return;
				case UNDO:
					fIgnoreTextConverters= true;
					break;
				case REDO:
					fIgnoreTextConverters= true;
					break;
			}
			
			super.doOperation(operation);
		}
		
		/*
		 * @see ITextOperationTarget#canDoOperation(int)
		 */
		public boolean canDoOperation(int operation) {
			if (operation == CORRECTIONASSIST_PROPOSALS)
				return isEditable();
			return super.canDoOperation(operation);
		}
		
		/*
		 * @see TextViewer#handleDispose()
		 */
		protected void handleDispose() {
			if (fCorrectionAssistant != null) {
				fCorrectionAssistant.uninstall();
				fCorrectionAssistant= null;
			}
			super.handleDispose();
		}
		
		public void insertTextConverter(ITextConverter textConverter, int index) {
			throw new UnsupportedOperationException();
		}
		
		public void addTextConverter(ITextConverter textConverter) {
			if (fTextConverters == null) {
				fTextConverters= new ArrayList(1);
				fTextConverters.add(textConverter);
			} else if (!fTextConverters.contains(textConverter))
				fTextConverters.add(textConverter);
		}
		
		public void removeTextConverter(ITextConverter textConverter) {
			if (fTextConverters != null) {
				fTextConverters.remove(textConverter);
				if (fTextConverters.size() == 0)
					fTextConverters= null;
			}
		}
		
		/*
		 * @see TextViewer#customizeDocumentCommand(DocumentCommand)
		 */
		protected void customizeDocumentCommand(DocumentCommand command) {
			super.customizeDocumentCommand(command);
			if (!fIgnoreTextConverters && fTextConverters != null) {
				for (Iterator e = fTextConverters.iterator(); e.hasNext();)
					((ITextConverter) e.next()).customizeDocumentCommand(getDocument(), command);
			}
			fIgnoreTextConverters= false;
		}
				
		// http://dev.eclipse.org/bugs/show_bug.cgi?id=19270
		public void updateIndentationPrefixes() {
			SourceViewerConfiguration configuration= getSourceViewerConfiguration();
			String[] types= configuration.getConfiguredContentTypes(this);
			for (int i= 0; i < types.length; i++) {
				String[] prefixes= configuration.getIndentPrefixes(this, types[i]);
				if (prefixes != null && prefixes.length > 0)
					setIndentPrefixes(prefixes, types[i]);
			}
		}
		
		/*
		 * @see IWidgetTokenOwner#requestWidgetToken(IWidgetTokenKeeper)
		 */
		public boolean requestWidgetToken(IWidgetTokenKeeper requester) {
			if (WorkbenchHelp.isContextHelpDisplayed())
				return false;
			return super.requestWidgetToken(requester);
		}
		
		/*
		 * @see IWidgetTokenOwnerExtension#requestWidgetToken(IWidgetTokenKeeper, int)
		 * @since 3.0
		 */
		public boolean requestWidgetToken(IWidgetTokenKeeper requester, int priority) {
			if (WorkbenchHelp.isContextHelpDisplayed())
				return false;
			return super.requestWidgetToken(requester, priority);
		}

		/*
		 * @see org.eclipse.jface.text.source.ISourceViewer#configure(org.eclipse.jface.text.source.SourceViewerConfiguration)
		 */
		public void configure(SourceViewerConfiguration configuration) {
			super.configure(configuration);
			fCorrectionAssistant= new JavaCorrectionAssistant(CompilationUnitEditor.this);
			fCorrectionAssistant.install(this);
			IAutoEditStrategy smartSemi= new SmartSemicolonAutoEditStrategy(IJavaPartitions.JAVA_PARTITIONING);
			prependAutoEditStrategy(smartSemi, IDocument.DEFAULT_CONTENT_TYPE);
		}
	}
	
	static class TabConverter implements ITextConverter {
		
		private int fTabRatio;
		private ILineTracker fLineTracker;
		
		public TabConverter() {
		} 
		
		public void setNumberOfSpacesPerTab(int ratio) {
			fTabRatio= ratio;
		}
		
		public void setLineTracker(ILineTracker lineTracker) {
			fLineTracker= lineTracker;
		}
		
		private int insertTabString(StringBuffer buffer, int offsetInLine) {
			
			if (fTabRatio == 0)
				return 0;
				
			int remainder= offsetInLine % fTabRatio;
			remainder= fTabRatio - remainder;
			for (int i= 0; i < remainder; i++)
				buffer.append(' ');
			return remainder;
		}
		
		public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
			String text= command.text;
			if (text == null)
				return;
				
			int index= text.indexOf('\t');
			if (index > -1) {
				
				StringBuffer buffer= new StringBuffer();
				
				fLineTracker.set(command.text);
				int lines= fLineTracker.getNumberOfLines();
				
				try {
						
						for (int i= 0; i < lines; i++) {
							
							int offset= fLineTracker.getLineOffset(i);
							int endOffset= offset + fLineTracker.getLineLength(i);
							String line= text.substring(offset, endOffset);
							
							int position= 0;
							if (i == 0) {
								IRegion firstLine= document.getLineInformationOfOffset(command.offset);
								position= command.offset - firstLine.getOffset();	
							}
							
							int length= line.length();
							for (int j= 0; j < length; j++) {
								char c= line.charAt(j);
								if (c == '\t') {
									position += insertTabString(buffer, position);
								} else {
									buffer.append(c);
									++ position;
								}
							}
							
						}
						
						command.text= buffer.toString();
						
				} catch (BadLocationException x) {
				}
			}
		}
	}
	
	private class ExitPolicy implements LinkedPositionUI.ExitPolicy {
		
		final char fExitCharacter;
		final char fEscapeCharacter;
		final Stack fStack;
		final int fSize;
		
		public ExitPolicy(char exitCharacter, char escapeCharacter, Stack stack) {
			fExitCharacter= exitCharacter;
			fEscapeCharacter= escapeCharacter;
			fStack= stack;
			fSize= fStack.size();
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.text.link.LinkedPositionUI.ExitPolicy#doExit(org.eclipse.jdt.internal.ui.text.link.LinkedPositionManager, org.eclipse.swt.events.VerifyEvent, int, int)
		 */
		public ExitFlags doExit(LinkedPositionManager manager, VerifyEvent event, int offset, int length) {
			
			if (event.character == fExitCharacter) {
				
				if (fSize == fStack.size() && !isMasked(offset)) {
					if (manager.anyPositionIncludes(offset, length))
						return new ExitFlags(LinkedPositionUI.COMMIT| LinkedPositionUI.UPDATE_CARET, false);
					else
						return new ExitFlags(LinkedPositionUI.COMMIT, true);
				}
			}
			
			Position p= manager.getFirstPosition();
			int pEndOffset= p.offset + p.length;
			
			// if the bracket position gets deleted or replaced, take the linked UI down.
			// 1: the event has to happen so that it occurs on or before the position end
			if (pEndOffset >= offset) {
				int endOffset= offset + length;
				// 2: either it is a replace event (selection length > 0, selection extends over closing peer, character != 0)
				// or it is a delete event right at the end of the position, with no selection
				if (pEndOffset < endOffset && event.character != 0 || length == 0 && pEndOffset == endOffset && event.keyCode == 127)
					return new ExitFlags(LinkedPositionUI.COMMIT, true);
			}
			
			switch (event.character) {	
			case '\b': {
				if (p.offset == offset && p.length == length)
					return new ExitFlags(0, false);
				else
					return null;
			}
			case '\n':
			case '\r':
				return new ExitFlags(LinkedPositionUI.COMMIT, true);
			
			case ';':
				if (getInsertMode() == SMART_INSERT)
					return new ExitFlags(LinkedPositionUI.COMMIT, true);
				// else fall through
				
			default:
				return null;
			}						
		}

		private boolean isMasked(int offset) {
			IDocument document= getSourceViewer().getDocument();
			try {
				return fEscapeCharacter == document.getChar(offset - 1);
			} catch (BadLocationException e) {
			}
			return false;
		}
	}
	
	private static class BracketLevel {
		int fOffset;
		int fLength;
		LinkedPositionManager fManager;
		LinkedPositionUI fEditor;
	}
	
	private class BracketInserter implements VerifyKeyListener, LinkedPositionUI.ExitListener {
		
		private boolean fCloseBrackets= true;
		private boolean fCloseStrings= true;
		private Stack fBracketLevelStack= new Stack();

		public void setCloseBracketsEnabled(boolean enabled) {
			fCloseBrackets= enabled;
		}

		public void setCloseStringsEnabled(boolean enabled) {
			fCloseStrings= enabled;
		}

		private boolean hasIdentifierToTheRight(IDocument document, int offset) {
			try {
				int end= offset;
				IRegion endLine= document.getLineInformationOfOffset(end);
				int maxEnd= endLine.getOffset() + endLine.getLength();
				while (end != maxEnd && Character.isWhitespace(document.getChar(end)))
					++end;

				return end != maxEnd && Character.isJavaIdentifierPart(document.getChar(end));

			} catch (BadLocationException e) {
				// be conservative
				return true;
			}
		}

		private boolean hasIdentifierToTheLeft(IDocument document, int offset) {
			try {
				int start= offset;
				IRegion startLine= document.getLineInformationOfOffset(start);
				int minStart= startLine.getOffset();
				while (start != minStart && Character.isWhitespace(document.getChar(start - 1)))
					--start;
				
				return start != minStart && Character.isJavaIdentifierPart(document.getChar(start - 1));

			} catch (BadLocationException e) {
				return true;
			}			
		}

		private boolean hasCharacterToTheRight(IDocument document, int offset, char character) {
			try {
				int end= offset;
				IRegion endLine= document.getLineInformationOfOffset(end);
				int maxEnd= endLine.getOffset() + endLine.getLength();
				while (end != maxEnd && Character.isWhitespace(document.getChar(end)))
					++end;
				
				return end != maxEnd && document.getChar(end) == character;


			} catch (BadLocationException e) {
				// be conservative
				return true;
			}			
		}
		
		/*
		 * @see org.eclipse.swt.custom.VerifyKeyListener#verifyKey(org.eclipse.swt.events.VerifyEvent)
		 */
		public void verifyKey(VerifyEvent event) {			

			if (!event.doit || getInsertMode() != SMART_INSERT)
				return;
				
			final ISourceViewer sourceViewer= getSourceViewer();
			IDocument document= sourceViewer.getDocument();

			final Point selection= sourceViewer.getSelectedRange();
			final int offset= selection.x;
			final int length= selection.y;

			switch (event.character) {
			case '(':
				if (hasCharacterToTheRight(document, offset + length, '('))
					return;

				// fall through

			case '[':
					if (!fCloseBrackets)
						return;
					if (hasIdentifierToTheRight(document, offset + length))
						return;
			
				// fall through
			
			case '\'':
				if (event.character == '\'') {
					if (!fCloseStrings)
						return;
					if (hasIdentifierToTheLeft(document, offset) || hasIdentifierToTheRight(document, offset + length))
						return;
				}
				
				// fall through

			case '"':
				if (event.character == '"') {
					if (!fCloseStrings)
						return;
					if (hasIdentifierToTheLeft(document, offset) || hasIdentifierToTheRight(document, offset + length))
						return;
				}
				
				try {		
					ITypedRegion partition= TextUtilities.getPartition(document, IJavaPartitions.JAVA_PARTITIONING, offset);
					if (! IDocument.DEFAULT_CONTENT_TYPE.equals(partition.getType()) && partition.getOffset() != offset)
						return;
						
					if (!validateEditorInputState())
						return;

					final char character= event.character;
					final char closingCharacter= getPeerCharacter(character);
					final StringBuffer buffer= new StringBuffer();
					buffer.append(character);
					buffer.append(closingCharacter);

					document.replace(offset, length, buffer.toString());
					
					
					BracketLevel level= new BracketLevel();
					fBracketLevelStack.push(level);
					
					level.fManager= new LinkedPositionManager(document, fBracketLevelStack.size() > 1);
					level.fManager.addPosition(offset + 1, 0);

					level.fOffset= offset;
					level.fLength= 2;
			
					level.fEditor= new LinkedPositionUI(sourceViewer, level.fManager);
					level.fEditor.setCancelListener(this);
					level.fEditor.setExitPolicy(new ExitPolicy(closingCharacter, getEscapeCharacter(closingCharacter), fBracketLevelStack));
					level.fEditor.setFinalCaretOffset(offset + 2);
					level.fEditor.enter();
					
					
					IRegion newSelection= level.fEditor.getSelectedRegion();
					sourceViewer.setSelectedRange(newSelection.getOffset(), newSelection.getLength());
	
					event.doit= false;

				} catch (BadLocationException e) {
				}
				break;	
			}
		}
		
		/*
		 * @see org.eclipse.jdt.internal.ui.text.link.LinkedPositionUI.ExitListener#exit(boolean)
		 */
		public void exit(boolean accept) {
			
			BracketLevel level= (BracketLevel) fBracketLevelStack.pop();

			if (accept)
				return;

			// remove brackets
			try {
								
				final ISourceViewer sourceViewer= getSourceViewer();
				IDocument document= sourceViewer.getDocument();	
				document.replace(level.fOffset, level.fLength, null);
				
			} catch (BadLocationException e) {
			}
		}
	}
	
	/** Preference key for code formatter tab size */
	private final static String CODE_FORMATTER_TAB_SIZE= JavaCore.FORMATTER_TAB_SIZE;
	/** Preference key for inserting spaces rather than tabs */
	private final static String SPACES_FOR_TABS= PreferenceConstants.EDITOR_SPACES_FOR_TABS;
	/** Preference key for automatically closing strings */
	private final static String CLOSE_STRINGS= PreferenceConstants.EDITOR_CLOSE_STRINGS;
	/** Preference key for automatically closing brackets and parenthesis */
	private final static String CLOSE_BRACKETS= PreferenceConstants.EDITOR_CLOSE_BRACKETS;
	
	
	/** The editor's save policy */
	protected ISavePolicy fSavePolicy;
	/** Listener to annotation model changes that updates the error tick in the tab image */
	private JavaEditorErrorTickUpdater fJavaEditorErrorTickUpdater;
	/** The editor's tab converter */
	private TabConverter fTabConverter;
	/** The remembered java element */
	private IJavaElement fRememberedElement;
	/** The remembered selection */
	private ITextSelection fRememberedSelection;
	/** The remembered java element offset */
	private int fRememberedElementOffset;
	/** The bracket inserter. */
	private BracketInserter fBracketInserter= new BracketInserter();

	/** The standard action groups added to the menu */
	private GenerateActionGroup fGenerateActionGroup;
	private CompositeActionGroup fContextMenuGroup;
	
	/**
	 * Creates a new compilation unit editor.
	 */
	public CompilationUnitEditor() {
		super();
		setDocumentProvider(JavaPlugin.getDefault().getCompilationUnitDocumentProvider());
		setEditorContextMenuId("#CompilationUnitEditorContext"); //$NON-NLS-1$
		setRulerContextMenuId("#CompilationUnitRulerContext"); //$NON-NLS-1$
		setOutlinerContextMenuId("#CompilationUnitOutlinerContext"); //$NON-NLS-1$
		// don't set help contextId, we install our own help context
		fSavePolicy= null;
			
		fJavaEditorErrorTickUpdater= new JavaEditorErrorTickUpdater(this);		
	}
	
	/*
	 * @see AbstractTextEditor#createActions()
	 */
	protected void createActions() {
		
		super.createActions();

		Action action= new TextOperationAction(JavaEditorMessages.getResourceBundle(), "CorrectionAssistProposal.", this, CORRECTIONASSIST_PROPOSALS); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.CORRECTION_ASSIST_PROPOSALS);		
		setAction("CorrectionAssistProposal", action); //$NON-NLS-1$
		markAsStateDependentAction("CorrectionAssistProposal", true); //$NON-NLS-1$
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.QUICK_FIX_ACTION);

		action= new ContentAssistAction(JavaEditorMessages.getResourceBundle(), "ContentAssistProposal.", this); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);		
		setAction("ContentAssistProposal", action); //$NON-NLS-1$
		markAsStateDependentAction("ContentAssistProposal", true); //$NON-NLS-1$
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.CONTENT_ASSIST_ACTION);

		action= new TextOperationAction(JavaEditorMessages.getResourceBundle(), "ContentAssistContextInformation.", this, ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION);	//$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.CONTENT_ASSIST_CONTEXT_INFORMATION);		
		setAction("ContentAssistContextInformation", action); //$NON-NLS-1$
		markAsStateDependentAction("ContentAssistContextInformation", true); //$NON-NLS-1$
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.PARAMETER_HINTS_ACTION);

		action= new TextOperationAction(JavaEditorMessages.getResourceBundle(), "Comment.", this, ITextOperationTarget.PREFIX); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.COMMENT);		
		setAction("Comment", action); //$NON-NLS-1$
		markAsStateDependentAction("Comment", true); //$NON-NLS-1$
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.COMMENT_ACTION);

		action= new TextOperationAction(JavaEditorMessages.getResourceBundle(), "Uncomment.", this, ITextOperationTarget.STRIP_PREFIX); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.UNCOMMENT);		
		setAction("Uncomment", action); //$NON-NLS-1$
		markAsStateDependentAction("Uncomment", true); //$NON-NLS-1$
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.UNCOMMENT_ACTION);

		action= new TextOperationAction(JavaEditorMessages.getResourceBundle(), "Format.", this, ISourceViewer.FORMAT); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.FORMAT);		
		setAction("Format", action); //$NON-NLS-1$
		markAsStateDependentAction("Format", true); //$NON-NLS-1$
		markAsSelectionDependentAction("Format", true); //$NON-NLS-1$		
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.FORMAT_ACTION);
		
		action= new AddBlockCommentAction(JavaEditorMessages.getResourceBundle(), "AddBlockComment.", this);  //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.ADD_BLOCK_COMMENT);		
		setAction("AddBlockComment", action); //$NON-NLS-1$
		markAsStateDependentAction("AddBlockComment", true); //$NON-NLS-1$
		markAsSelectionDependentAction("AddBlockComment", true); //$NON-NLS-1$		
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.ADD_BLOCK_COMMENT_ACTION);

		action= new RemoveBlockCommentAction(JavaEditorMessages.getResourceBundle(), "RemoveBlockComment.", this);  //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.REMOVE_BLOCK_COMMENT);		
		setAction("RemoveBlockComment", action); //$NON-NLS-1$
		markAsStateDependentAction("RemoveBlockComment", true); //$NON-NLS-1$
		markAsSelectionDependentAction("RemoveBlockComment", true); //$NON-NLS-1$		
		WorkbenchHelp.setHelp(action, IJavaHelpContextIds.REMOVE_BLOCK_COMMENT_ACTION);
		
		action= new IndentAction(JavaEditorMessages.getResourceBundle(), "Editor.Indent.", this); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.INDENT);		
		setAction("Indent", action); //$NON-NLS-1$
		markAsStateDependentAction("Indent", true); //$NON-NLS-1$
		markAsSelectionDependentAction("Indent", true); //$NON-NLS-1$
		
		if (getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SMART_TAB)) {
			removeActionActivationCode(ITextEditorActionConstants.SHIFT_RIGHT);
			setActionActivationCode("Indent", '\t', -1, SWT.NONE); //$NON-NLS-1$
		}
		
		fGenerateActionGroup= new GenerateActionGroup(this, ITextEditorActionConstants.GROUP_EDIT);
		ActionGroup rg= new RefactorActionGroup(this, ITextEditorActionConstants.GROUP_EDIT);
		
		fActionGroups.addGroup(rg);
		fActionGroups.addGroup(fGenerateActionGroup);
		
		// We have to keep the context menu group separate to have better control over positioning
		fContextMenuGroup= new CompositeActionGroup(new ActionGroup[] {
			fGenerateActionGroup, 
			rg, 
			new LocalHistoryActionGroup(this, ITextEditorActionConstants.GROUP_EDIT)});
	}

	/*
	 * @see JavaEditor#getElementAt(int)
	 */
	protected IJavaElement getElementAt(int offset) {
		return getElementAt(offset, true);
	}
	
	/**
	 * Returns the most narrow element including the given offset.  If <code>reconcile</code>
	 * is <code>true</code> the editor's input element is reconciled in advance. If it is 
	 * <code>false</code> this method only returns a result if the editor's input element
	 * does not need to be reconciled.
	 * 
	 * @param offset the offset included by the retrieved element
	 * @param reconcile <code>true</code> if working copy should be reconciled
	 */
	protected IJavaElement getElementAt(int offset, boolean reconcile) {
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		ICompilationUnit unit= manager.getWorkingCopy(getEditorInput());
		
		if (unit != null) {
			try {
				if (reconcile) {
					synchronized (unit) {
						unit.reconcile();
					}
					return unit.getElementAt(offset);
				} else if (unit.isConsistent())
					return unit.getElementAt(offset);
					
			} catch (JavaModelException x) {
				if (!x.isDoesNotExist())
				JavaPlugin.log(x.getStatus());
				// nothing found, be tolerant and go on
			}
		}
		
		return null;
	}
	
	/*
	 * @see JavaEditor#getCorrespondingElement(IJavaElement)
	 */
	protected IJavaElement getCorrespondingElement(IJavaElement element) {
		try {
			return EditorUtility.getWorkingCopy(element, true);
		} catch (JavaModelException x) {
			JavaPlugin.log(x.getStatus());
			// nothing found, be tolerant and go on
		}
		return null;
	}
	
	/*
	 * @see AbstractTextEditor#editorContextMenuAboutToShow(IMenuManager)
	 */
	public void editorContextMenuAboutToShow(IMenuManager menu) {
		super.editorContextMenuAboutToShow(menu);		
				
		ActionContext context= new ActionContext(getSelectionProvider().getSelection());
		fContextMenuGroup.setContext(context);
		fContextMenuGroup.fillContextMenu(menu);
		fContextMenuGroup.setContext(null);
	}
	
	/*
	 * @see JavaEditor#setOutlinePageInput(JavaOutlinePage, IEditorInput)
	 */
	protected void setOutlinePageInput(JavaOutlinePage page, IEditorInput input) {
		if (page != null) {
			IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
			page.setInput(manager.getWorkingCopy(input));
		}
	}
	
	/*
	 * @see AbstractTextEditor#performSaveOperation(WorkspaceModifyOperation, IProgressMonitor)
	 */
	protected void performSaveOperation(WorkspaceModifyOperation operation, IProgressMonitor progressMonitor) {
		IDocumentProvider p= getDocumentProvider();
		if (p instanceof ICompilationUnitDocumentProvider) {
			ICompilationUnitDocumentProvider cp= (ICompilationUnitDocumentProvider) p;
			cp.setSavePolicy(fSavePolicy);
		}
		
		try {
			super.performSaveOperation(operation, progressMonitor);
		} finally {
			if (p instanceof ICompilationUnitDocumentProvider) {
				ICompilationUnitDocumentProvider cp= (ICompilationUnitDocumentProvider) p;
				cp.setSavePolicy(null);
			}
		}
	}
	
	/*
	 * @see AbstractTextEditor#doSaveAs
	 */
	public void doSaveAs() {
		if (askIfNonWorkbenchEncodingIsOk()) {
			super.doSaveAs();
		}
	}

	/*
	 * @see AbstractTextEditor#doSave(IProgressMonitor)
	 */
	public void doSave(IProgressMonitor progressMonitor) {
		
		IDocumentProvider p= getDocumentProvider();
		if (p == null) {
			// editor has been closed
			return;
		}
			
		if (!askIfNonWorkbenchEncodingIsOk()) {
			progressMonitor.setCanceled(true);
			return;
		}
		
		if (p.isDeleted(getEditorInput())) {
			
			if (isSaveAsAllowed()) {
				
				/*
				 * 1GEUSSR: ITPUI:ALL - User should never loose changes made in the editors.
				 * Changed Behavior to make sure that if called inside a regular save (because
				 * of deletion of input element) there is a way to report back to the caller.
				 */
				 performSaveAs(progressMonitor);
			
			} else {
				
				/* 
				 * 1GF5YOX: ITPJUI:ALL - Save of delete file claims it's still there
				 * Missing resources.
				 */
				Shell shell= getSite().getShell();
				MessageDialog.openError(shell, JavaEditorMessages.getString("CompilationUnitEditor.error.saving.title1"), JavaEditorMessages.getString("CompilationUnitEditor.error.saving.message1")); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
		} else {	
			
			setStatusLineErrorMessage(null);
			
			IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
			ICompilationUnit unit= manager.getWorkingCopy(getEditorInput());
			
			if (unit != null) {
				synchronized (unit) { 
					performSaveOperation(createSaveOperation(false), progressMonitor); 
				}
			} else 
				performSaveOperation(createSaveOperation(false), progressMonitor);
		}
	}
	
	/**
	 * Asks the user if it is ok to store in non-workbench encoding.
	 * @return <true> if the user wants to continue
	 */
	private boolean askIfNonWorkbenchEncodingIsOk() {
		IDocumentProvider provider= getDocumentProvider();
		if (provider instanceof IStorageDocumentProvider) {
			IEditorInput input= getEditorInput();
			IStorageDocumentProvider storageProvider= (IStorageDocumentProvider)provider;
			String encoding= storageProvider.getEncoding(input);
			String defaultEncoding= storageProvider.getDefaultEncoding();
			if (encoding != null && !encoding.equals(defaultEncoding)) {
				Shell shell= getSite().getShell();
				String title= JavaEditorMessages.getString("CompilationUnitEditor.warning.save.nonWorkbenchEncoding.title"); //$NON-NLS-1$
				String msg;
				if (input != null)
					msg= MessageFormat.format(JavaEditorMessages.getString("CompilationUnitEditor.warning.save.nonWorkbenchEncoding.message1"), new String[] {input.getName(), encoding});//$NON-NLS-1$
				else
					msg= MessageFormat.format(JavaEditorMessages.getString("CompilationUnitEditor.warning.save.nonWorkbenchEncoding.message2"), new String[] {encoding});//$NON-NLS-1$
				return MessageDialog.openQuestion(shell, title, msg);
			}
		}
		return true;
	}
	
	public boolean isSaveAsAllowed() {
		return true;
	}
	
	/**
	 * The compilation unit editor implementation of this  <code>AbstractTextEditor</code>
	 * method asks the user for the workspace path of a file resource and saves the document
	 * there. See http://dev.eclipse.org/bugs/show_bug.cgi?id=6295
	 */
	protected void performSaveAs(IProgressMonitor progressMonitor) {
		
		Shell shell= getSite().getShell();
		IEditorInput input = getEditorInput();
		
		SaveAsDialog dialog= new SaveAsDialog(shell);
		
		IFile original= (input instanceof IFileEditorInput) ? ((IFileEditorInput) input).getFile() : null;
		if (original != null)
			dialog.setOriginalFile(original);
			
		dialog.create();
		
			
		IDocumentProvider provider= getDocumentProvider();
		if (provider == null) {
			// editor has been programmatically closed while the dialog was open
			return;
		}
		
		if (provider.isDeleted(input) && original != null) {
			String message= JavaEditorMessages.getFormattedString("CompilationUnitEditor.warning.save.delete", new Object[] { original.getName() }); //$NON-NLS-1$
			dialog.setErrorMessage(null);
			dialog.setMessage(message, IMessageProvider.WARNING);
		}
			
		if (dialog.open() == Window.CANCEL) {
			if (progressMonitor != null)
				progressMonitor.setCanceled(true);
			return;
		}
			
		IPath filePath= dialog.getResult();
		if (filePath == null) {
			if (progressMonitor != null)
				progressMonitor.setCanceled(true);
			return;
		}
			
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IFile file= workspace.getRoot().getFile(filePath);
		final IEditorInput newInput= new FileEditorInput(file);
		
		WorkspaceModifyOperation op= new WorkspaceModifyOperation() {
			public void execute(final IProgressMonitor monitor) throws CoreException {
				getDocumentProvider().saveDocument(monitor, newInput, getDocumentProvider().getDocument(getEditorInput()), true);
			}
		};
		
		boolean success= false;
		try {
			
			provider.aboutToChange(newInput);
			new ProgressMonitorDialog(shell).run(false, true, op);
			success= true;
			
		} catch (InterruptedException x) {
		} catch (InvocationTargetException x) {
			
			Throwable t= x.getTargetException();
			if (t instanceof CoreException) {
				CoreException cx= (CoreException) t;
				ErrorDialog.openError(shell, JavaEditorMessages.getString("CompilationUnitEditor.error.saving.title2"), JavaEditorMessages.getString("CompilationUnitEditor.error.saving.message2"), cx.getStatus()); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				MessageDialog.openError(shell, JavaEditorMessages.getString("CompilationUnitEditor.error.saving.title3"), JavaEditorMessages.getString("CompilationUnitEditor.error.saving.message3") + t.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
						
		} finally {
			provider.changed(newInput);
			if (success)
				setInput(newInput);
		}
		
		if (progressMonitor != null)
			progressMonitor.setCanceled(!success);
	}
	
	/*
	 * @see AbstractTextEditor#doSetInput(IEditorInput)
	 */
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		configureTabConverter();
	}

	private void configureTabConverter() {
		if (fTabConverter != null) {
			IDocumentProvider provider= getDocumentProvider();
			if (provider instanceof ICompilationUnitDocumentProvider) {
				ICompilationUnitDocumentProvider cup= (ICompilationUnitDocumentProvider) provider;
				fTabConverter.setLineTracker(cup.createLineTracker(getEditorInput()));
			}
		}
	}
	
	private int getTabSize() {
		Preferences preferences= JavaCore.getPlugin().getPluginPreferences();
		return preferences.getInt(CODE_FORMATTER_TAB_SIZE);	
	}
	
	private void startTabConversion() {
		if (fTabConverter == null) {
			fTabConverter= new TabConverter();
			configureTabConverter();
			fTabConverter.setNumberOfSpacesPerTab(getTabSize());
			AdaptedSourceViewer asv= (AdaptedSourceViewer) getSourceViewer();
			asv.addTextConverter(fTabConverter);
			// http://dev.eclipse.org/bugs/show_bug.cgi?id=19270
			asv.updateIndentationPrefixes();
		}
	}
	
	private void stopTabConversion() {
		if (fTabConverter != null) {
			AdaptedSourceViewer asv= (AdaptedSourceViewer) getSourceViewer();
			asv.removeTextConverter(fTabConverter);
			// http://dev.eclipse.org/bugs/show_bug.cgi?id=19270
			asv.updateIndentationPrefixes();
			fTabConverter= null;
		}
	}
	
	private boolean isTabConversionEnabled() {
		IPreferenceStore store= getPreferenceStore();
		return store.getBoolean(SPACES_FOR_TABS);
	}
	
	public void dispose() {

		ISourceViewer sourceViewer= getSourceViewer();
		if (sourceViewer instanceof ITextViewerExtension)
			((ITextViewerExtension) sourceViewer).removeVerifyKeyListener(fBracketInserter);

		if (fJavaEditorErrorTickUpdater != null) {
			fJavaEditorErrorTickUpdater.dispose();
			fJavaEditorErrorTickUpdater= null;
		}
		
		if (fActionGroups != null) {
			fActionGroups.dispose();
			fActionGroups= null;
		}
			
		super.dispose();
	}
	
	/*
	 * @see AbstractTextEditor#createPartControl(Composite)
	 */
	public void createPartControl(Composite parent) {
		
		super.createPartControl(parent);
					
		if (isTabConversionEnabled())
			startTabConversion();			
			
		IPreferenceStore preferenceStore= getPreferenceStore();
		boolean closeBrackets= preferenceStore.getBoolean(CLOSE_BRACKETS);
		boolean closeStrings= preferenceStore.getBoolean(CLOSE_STRINGS);
		
		fBracketInserter.setCloseBracketsEnabled(closeBrackets);
		fBracketInserter.setCloseStringsEnabled(closeStrings);
		
		ISourceViewer sourceViewer= getSourceViewer();
		if (sourceViewer instanceof ITextViewerExtension)
			((ITextViewerExtension) sourceViewer).prependVerifyKeyListener(fBracketInserter);
		
	}
	
	private static char getEscapeCharacter(char character) {
		switch (character) {
			case '"':
			case '\'':
				return '\\';
			default:
				return 0;
		}
	}
	
	private static char getPeerCharacter(char character) {
		switch (character) {
			case '(':
				return ')';
				
			case ')':
				return '(';
				
			case '[':
				return ']';

			case ']':
				return '[';
				
			case '"':
				return character;
				
			case '\'':
				return character;
			
			default:
				throw new IllegalArgumentException();
		}					
	}
	
	/*
	 * @see AbstractTextEditor#handlePreferenceStoreChanged(PropertyChangeEvent)
	 */
	protected void handlePreferenceStoreChanged(PropertyChangeEvent event) {
		
		try {
			
			AdaptedSourceViewer asv= (AdaptedSourceViewer) getSourceViewer();
			if (asv != null) {
					
				String p= event.getProperty();		
				
				if (CLOSE_BRACKETS.equals(p)) {
					fBracketInserter.setCloseBracketsEnabled(getPreferenceStore().getBoolean(p));
					return;	
				}

				if (CLOSE_STRINGS.equals(p)) {
					fBracketInserter.setCloseStringsEnabled(getPreferenceStore().getBoolean(p));
					return;
				}
								
				if (SPACES_FOR_TABS.equals(p)) {
					if (isTabConversionEnabled())
						startTabConversion();
					else
						stopTabConversion();
					return;
				}
				
				if (PreferenceConstants.EDITOR_SMART_TAB.equals(p)) {
					if (getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SMART_TAB)) {
						removeActionActivationCode(ITextEditorActionConstants.SHIFT_RIGHT);
						setActionActivationCode("Indent", '\t', -1, SWT.NONE); //$NON-NLS-1$
					} else {
						removeActionActivationCode("Indent"); //$NON-NLS-1$
						setActionActivationCode(ITextEditorActionConstants.SHIFT_RIGHT, '\t', -1, SWT.NONE);
					}
				}

				IContentAssistant c= asv.getContentAssistant();
				if (c instanceof ContentAssistant)
					ContentAssistPreference.changeConfiguration((ContentAssistant) c, getPreferenceStore(), event);
			}
				
		} finally {
			super.handlePreferenceStoreChanged(event);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor#handlePreferencePropertyChanged(org.eclipse.core.runtime.Preferences.PropertyChangeEvent)
	 */
	protected void handlePreferencePropertyChanged(org.eclipse.core.runtime.Preferences.PropertyChangeEvent event) {
		AdaptedSourceViewer asv= (AdaptedSourceViewer) getSourceViewer();
		if (asv != null) {
			String p= event.getProperty();					
			if (CODE_FORMATTER_TAB_SIZE.equals(p)) {
				asv.updateIndentationPrefixes();
				if (fTabConverter != null)
					fTabConverter.setNumberOfSpacesPerTab(getTabSize());
			}
		}
		super.handlePreferencePropertyChanged(event);
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor#createJavaSourceViewer(org.eclipse.swt.widgets.Composite, org.eclipse.jface.text.source.IVerticalRuler, org.eclipse.jface.text.source.IOverviewRuler, boolean, int)
	 */
	protected ISourceViewer createJavaSourceViewer(Composite parent, IVerticalRuler verticalRuler, IOverviewRuler overviewRuler, boolean isOverviewRulerVisible, int styles) {
		return new AdaptedSourceViewer(parent, verticalRuler, overviewRuler, isOverviewRulerVisible, styles);
	}
	
	/*
	 * @see IReconcilingParticipant#reconciled()
	 */
	public void reconciled() {
		if (PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SYNC_OUTLINE_ON_CURSOR_MOVE)) {
			Shell shell= getSite().getShell();
			if (shell != null && !shell.isDisposed()) {
				shell.getDisplay().asyncExec(new Runnable() {
					public void run() {
						synchronizeOutlinePageSelection();
					}
				});
			}
		}
	}
		
	protected void updateStateDependentActions() {
		super.updateStateDependentActions();
		fGenerateActionGroup.editorStateChanged();
	}
	
	/**
	 * Returns the updated java element for the old java element.
	 */
	private IJavaElement findElement(IJavaElement element) {
		
		if (element == null)
			return null;
		
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		ICompilationUnit unit= manager.getWorkingCopy(getEditorInput());
		
		if (unit != null) {
			try {
				
				synchronized (unit) {
					unit.reconcile();
				}
				IJavaElement[] findings= unit.findElements(element);
				if (findings != null && findings.length > 0)
					return findings[0];
			
			} catch (JavaModelException x) {
				JavaPlugin.log(x.getStatus());
				// nothing found, be tolerant and go on
			}
		}
		
		return null;
	}
	
	/**
	 * Returns the offset of the given Java element.
	 */
	private int getOffset(IJavaElement element) {
		if (element instanceof ISourceReference) {
			ISourceReference sr= (ISourceReference) element;
			try {
				ISourceRange srcRange= sr.getSourceRange();
				if (srcRange != null)
					return srcRange.getOffset();
			} catch (JavaModelException e) {
			}
		}
		return -1;	
	}
	
	/*
	 * @see AbstractTextEditor#rememberSelection()
	 */
	protected void rememberSelection() {
		ISelectionProvider sp= getSelectionProvider();
		fRememberedSelection= (sp == null ? null : (ITextSelection) sp.getSelection());
		if (fRememberedSelection != null) {
			fRememberedElement= getElementAt(fRememberedSelection.getOffset(), true);
			fRememberedElementOffset= getOffset(fRememberedElement); 
		}
	}
	
	/*
	 * @see AbstractTextEditor#restoreSelection()
	 */
	protected void restoreSelection() {
		
		try {
			
			if (getSourceViewer() == null || fRememberedSelection == null)
				return;
				
			IJavaElement newElement= findElement(fRememberedElement);
			int newOffset= getOffset(newElement);
			int delta= (newOffset > -1 && fRememberedElementOffset > -1) ? newOffset - fRememberedElementOffset : 0;
			if (isValidSelection(delta + fRememberedSelection.getOffset(), fRememberedSelection.getLength()))
				selectAndReveal(delta + fRememberedSelection.getOffset(), fRememberedSelection.getLength());			
			
		} finally {
			fRememberedSelection= null;
			fRememberedElement= null;
			fRememberedElementOffset= -1;
		}
	}
	
	private boolean isValidSelection(int offset, int length) {
		IDocumentProvider provider= getDocumentProvider();
		if (provider != null) {
			IDocument document= provider.getDocument(getEditorInput());
			if (document != null) {
				int end= offset + length;
				int documentLength= document.getLength();
				return 0 <= offset  && offset <= documentLength && 0 <= end && end <= documentLength;
			}
		}
		return false;
	}
	
	/*
	 * @see AbstractTextEditor#canHandleMove(IEditorInput, IEditorInput)
	 */
	protected boolean canHandleMove(IEditorInput originalElement, IEditorInput movedElement) {
		
		String oldExtension= ""; //$NON-NLS-1$
		if (originalElement instanceof IFileEditorInput) {
			IFile file= ((IFileEditorInput) originalElement).getFile();
			if (file != null) {
				String ext= file.getFileExtension();
				if (ext != null)
					oldExtension= ext;
			}
		}
		
		String newExtension= ""; //$NON-NLS-1$
		if (movedElement instanceof IFileEditorInput) {
			IFile file= ((IFileEditorInput) movedElement).getFile();
			if (file != null)
				newExtension= file.getFileExtension();
		}
		
		return oldExtension.equals(newExtension);
	}

	/*
	 * @see org.eclipse.ui.texteditor.ExtendedTextEditor#isPrefQuickDiffAlwaysOn()
	 */
	protected boolean isPrefQuickDiffAlwaysOn() {
		// reestablishes the behaviour from ExtendedTextEditor which was hacked by JavaEditor
		// to disable the change bar for the class file (attached source) java editor.
		IPreferenceStore store= getPreferenceStore();
		return store.getBoolean(ExtendedTextEditorPreferenceConstants.QUICK_DIFF_ALWAYS_ON);
	}

}
