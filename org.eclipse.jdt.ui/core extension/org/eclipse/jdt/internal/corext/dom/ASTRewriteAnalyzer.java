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
package org.eclipse.jdt.internal.corext.dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite.CopyPlaceholderData;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite.MovePlaceholderData;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite.StringPlaceholderData;
import org.eclipse.jdt.internal.corext.dom.ASTRewriteFormatter.NodeMarker;
import org.eclipse.jdt.internal.corext.textmanipulation.CopyTargetEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.GroupDescription;
import org.eclipse.jdt.internal.corext.textmanipulation.MoveTargetEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.MultiTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.RangeMarker;
import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Infrastructure to support code modifications. Existing code must stay untouched, new code
 * added with correct formatting, moved code left with the users formattings / comments.
 * Idea:
 * - Get the AST for existing code 
 * - Inserting new nodes or mark existing nodes as replaced, removed, copied or moved.
 * - This visitor analyses the changes or annotations and generates text edits
 * (text manipulation API) that describe the required code changes. See test
 * cases in org.eclipse.jdt.ui.tests / package org.eclipse.jdt.ui.tests.
 * astrewrite for examples
 */
public class ASTRewriteAnalyzer extends ASTVisitor {
	
	private static final boolean DEBUG= true;

	private TextEdit fCurrentEdit;
	private TokenScanner fTokenScanner; // shared scanner
	private HashMap fNodeRanges;
	private HashMap fCopySources;
	private HashMap fMoveSources;
	
	final TextBuffer fTextBuffer;
	private final ASTRewrite fRewrite;
	
	private final ASTRewriteFormatter fFormatter;
	
	/**
	 * Constructor for ASTChangeAnalyzer.
	 */
	public ASTRewriteAnalyzer(TextBuffer textBuffer, TextEdit rootEdit, ASTRewrite rewrite) {
		fTextBuffer= textBuffer;
		fTokenScanner= null;
		fNodeRanges= null;
		fRewrite= rewrite;
		fCurrentEdit= rootEdit;
		fCopySources= new HashMap();
		fMoveSources= new HashMap();
		
		fFormatter= new ASTRewriteFormatter(rewrite);
	}
	
	final ListRewriter getDefaultRewriter() {
		return new ListRewriter();
	}	
	
	final TokenScanner getScanner() {
		if (fTokenScanner == null) {
			fTokenScanner= new TokenScanner(fTextBuffer);
		}
		return fTokenScanner;
	}
	
	final ISourceRange getNodeRange(ASTNode node, int prevNodePos) {
		if (fNodeRanges == null) {
			fNodeRanges= new HashMap();
		} else {
			ISourceRange range= (ISourceRange) fNodeRanges.get(node);
			if (range != null) {
				return range;
			}
		}
		if (prevNodePos == -1) {
			ASTNode parent= node.getParent();
			if (parent == null || isInserted(parent)) {
				prevNodePos= 0;
			} else {
				List siblings= ASTNodes.getChildren(parent);
				ASTNode res= findPreviousExistingNode(siblings, node);
				if (res != null) {
					prevNodePos= res.getStartPosition() + res.getLength();
				} else {
					prevNodePos= parent.getStartPosition();
				}
			}
		}
		ISourceRange range= ASTNodes.getNodeRangeWithComments(node, prevNodePos, -1, getScanner());
		fNodeRanges.put(node, range);
		return range;
	}
	
	final private CopyIndentedSourceEdit[] getCopySources(ASTNode node) {
		int count= fRewrite.getCopyCount(node);
		if (count == 0) {
			return null;
		}
		CopyIndentedSourceEdit[] edits= (CopyIndentedSourceEdit[]) fCopySources.get(node);
		if (edits == null) {
			edits= new CopyIndentedSourceEdit[count];
			ISourceRange range= getNodeRange(node, -1);
			for (int i= 0; i < count; i++) {
				edits[i]= new CopyIndentedSourceEdit(range.getOffset(), range.getLength());
			}
			fCopySources.put(node, edits);
		}
		return edits;
	}
	
	final private MoveIndentedSourceEdit getMoveSource(ASTNode node) {
		if (isMoveSource(node)) {
			MoveIndentedSourceEdit edit= (MoveIndentedSourceEdit) fMoveSources.get(node);
			if (edit == null) {
				ISourceRange range= getNodeRange(node, -1);
				edit= new MoveIndentedSourceEdit(range.getOffset(), range.getLength());
			}
			fMoveSources.put(node, edit);
			return edit;
		}
		return null;
	}
	
	final int getChangeKind(ASTNode node) {
		return fRewrite.getChangeKind(node);
	}
	
	final boolean isChanged(ASTNode node) {
		int kind= getChangeKind(node);
		return kind != ASTRewrite.UNCHANGED;
	}
	
	final boolean isInsertOrRemove(ASTNode node) {
		int kind= getChangeKind(node);
		return kind == ASTRewrite.INSERTED || kind == ASTRewrite.REMOVED;
	}
	
	final boolean isInserted(ASTNode node) {
		return fRewrite.isInserted(node);
	}
	
	final boolean isInsertBoundToPrevious(ASTNode node) {
		return fRewrite.isInsertBoundToPrevious(node);
	}	
	
	final boolean isReplaced(ASTNode node) {
		return fRewrite.isReplaced(node);
	}
	
	final boolean isRemoved(ASTNode node) {
		return fRewrite.isRemoved(node);
	}	
	
	final boolean isModified(ASTNode node) {
		return fRewrite.isModified(node);
	}
	
	final boolean isMoveSource(ASTNode node) {
		return fRewrite.isMoveSource(node);
	}	
		
	final ASTNode getModifiedNode(ASTNode node) {
		return fRewrite.getModifiedNode(node);
	}

	final ASTNode getReplacingNode(ASTNode node) {
		return fRewrite.getReplacingNode(node);
	}
	
	final GroupDescription getDescription(ASTNode node) {
		return fRewrite.getDescription(node);
	}
	
	private final void addEdit(TextEdit edit) {
		if (DEBUG) {
			TextRange editRange= edit.getTextRange();
			if (!(fCurrentEdit instanceof MultiTextEdit)) {
				TextRange parentRange= fCurrentEdit.getTextRange();
				if (parentRange.getOffset() > editRange.getOffset() || editRange.getExclusiveEnd() > parentRange.getExclusiveEnd()) {
					Assert.isTrue(false, "Parent range must surround edit range:" + parentRange.toString() + ", " + editRange.toString()); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			Iterator iter= fCurrentEdit.iterator();
			while (iter.hasNext()) {
				TextEdit curr= (TextEdit) iter.next();
				TextRange range= curr.getTextRange();
				if (range.getOffset() < editRange.getExclusiveEnd() && editRange.getOffset() < range.getExclusiveEnd()) {
					Assert.isTrue(false, "Sibling range must not overlap:" + range.toString() + ", " + editRange.toString()); //$NON-NLS-1$ //$NON-NLS-2$
				}				
			}
		}
		fCurrentEdit.add(edit);
	}
	
	final void doTextInsert(int offset, String insertString, GroupDescription description) {
		if (insertString.length() > 0) {
			TextEdit edit= SimpleTextEdit.createInsert(offset, insertString);
			addEdit(edit);
			if (description != null) {
				addDescription(description, edit);
			}
		}
	}
	
	final void addDescription(GroupDescription groupDesc, TextEdit edit) {
		groupDesc.addTextEdit(edit);
	}

	
	final TextEdit doTextRemove(int offset, int len, GroupDescription description) {
		if (len == 0) {
			return null;
		}
		TextEdit edit= SimpleTextEdit.createDelete(offset, len);
		addEdit(edit);
		if (description != null) {
			addDescription(description, edit);
		}
		return edit;
	}
	
	final void doTextRemoveAndVisit(int offset, int len, ASTNode node) {
		TextEdit edit= doTextRemove(offset, len, getDescription(node));
		if (edit != null) {
			fCurrentEdit= edit;
			doVisit(node);
			fCurrentEdit= edit.getParent();
		}
	}
	
	final int doVisit(ASTNode node) {
		node.accept(this);
		return node.getStartPosition() + node.getLength();
	}	
	
	
	final void doTextReplace(int offset, int len, String insertString, GroupDescription description) {
		if (len > 0 || insertString.length() > 0) {
			TextEdit edit= SimpleTextEdit.createReplace(offset, len, insertString);
			addEdit(edit);
			if (description != null) {
				addDescription(description, edit);
			}
		}
	}
		
	final TextEdit doTextCopy(ASTNode copiedNode, int destOffset, int sourceIndentLevel, String destIndentString, int tabWidth, GroupDescription description) {
		CopyIndentedSourceEdit[] edits= getCopySources(copiedNode);
		if (edits == null) {
			Assert.isTrue(false, "Copy source not annotated" + copiedNode.toString()); //$NON-NLS-1$
		}
		CopyIndentedSourceEdit sourceEdit= null;
		for (int i= 0; i < edits.length; i++) {
			if (!edits[i].isInitialized()) {
				sourceEdit= edits[i];
				break;
			}
		}
		if (sourceEdit == null) {
			Assert.isTrue(false, "No copy source available" + copiedNode.toString()); //$NON-NLS-1$
		}

		sourceEdit.initialize(sourceIndentLevel, destIndentString, tabWidth);
	
		CopyTargetEdit targetEdit= new CopyTargetEdit(destOffset, sourceEdit);
		addEdit(targetEdit);
	
		if (description != null) {
			addDescription(description, sourceEdit);
			addDescription(description, targetEdit);
		}
		return targetEdit;			

	}
	
	final TextEdit doTextMove(ASTNode movedNode, int destOffset, int sourceIndentLevel, String destIndentString, int tabWidth, GroupDescription description) {
		MoveIndentedSourceEdit moveEdit= getMoveSource(movedNode);
		if (moveEdit.isInitialized()) {
			Assert.isTrue(false, "No move source available" + movedNode.toString()); //$NON-NLS-1$
		}		
		moveEdit.initialize(sourceIndentLevel, destIndentString, tabWidth);
	
		MoveTargetEdit targetEdit= new MoveTargetEdit(destOffset, moveEdit);
		addEdit(targetEdit);
	
		if (description != null) {
			addDescription(description, moveEdit);
			addDescription(description, targetEdit);
		}
		return targetEdit;			

	}	
	
			
	private int getPosBeforeSpaces(int pos) {
		while (pos > 0 && Strings.isIndentChar(fTextBuffer.getChar(pos - 1))) {
			pos--;
		}
		return pos;
	}	
	
	private void checkNoModification(ASTNode node) {
		if (isModified(node)) {
			Assert.isTrue(false, "Can not modify " + node.getClass().getName()); //$NON-NLS-1$
		}
	}
	
	private void checkNoChange(ASTNode node) {
		if (isChanged(node)) {
			Assert.isTrue(false, "Can not insert or replace node in " + node.getParent().getClass().getName()); //$NON-NLS-1$
		}
	}
	
	private void checkNoInsertOrRemove(ASTNode node) {
		if (isInserted(node) || isRemoved(node)) {	
			Assert.isTrue(false, "Can not insert or remove node " + node + " in " + node.getParent().getClass().getName()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	private class ListRewriter {
		protected String fContantSeparator;
		protected int fStartPos;
		
		protected List fList;
		
		protected final ASTNode getNode(int index) {
			return (ASTNode) fList.get(index);
		}
		
		protected String getSeparatorString(int nodeIndex) {
			return fContantSeparator;
		}
		
		protected int getInitialIndent() {
			return getIndent(fStartPos);
		}
		
		protected int getNodeIndent(int nodeIndex) {
			ASTNode node= getNode(nodeIndex);
			if (isInserted(node)) {
				for (int i= nodeIndex - 1; i>= 0; i--) {
					ASTNode curr= getNode(i);
					if (!isInserted(curr)) {
						return getIndent(curr.getStartPosition());
					}
				}
				return getInitialIndent();
			}
			return getIndent(node.getStartPosition());
		}
		
		protected int getStartOfNextNode(int nextIndex, int defaultPos) {
			return getNextExistingStartPos(fList, nextIndex, defaultPos);
		}
		
		protected int getEndOfNode(ASTNode node) {
			ISourceRange range= getNodeRange(node, -1);
			return range.getOffset() + range.getLength();
		}		
		
		public int rewriteList(List list, int startPos, String keyword, String separator) {
			fContantSeparator= separator;
			return rewriteList(list, startPos, keyword);
		}
		
		private boolean insertAfterSeparator(ASTNode node) {
			return !isInsertBoundToPrevious(node);
		}
		
		public int rewriteList(List list, int startPos, String keyword) {
			fList= list;
			fStartPos= startPos;
			
			int total= list.size();
			if (total == 0) {
				return fStartPos;
			}
		
			int currPos= -1;
			
			int lastNonInsert= -1;
			int lastNonDelete= -1;
		
			for (int i= 0; i < total; i++) {
				ASTNode elem= getNode(i);
				int currMark= getChangeKind(elem);
				if (currMark != ASTRewrite.INSERTED) {
					lastNonInsert= i;
					if (currPos == -1) {
						currPos= getNodeRange(elem, startPos).getOffset();
					}
				}
				if (currMark != ASTRewrite.REMOVED) {
					lastNonDelete= i;
				}			
			}
		
			if (currPos == -1) { // only inserts
				if (keyword.length() > 0) {  // creating a new list -> insert keyword first (e.g. " throws ")
					GroupDescription description= getDescription(getNode(0)); // first node is insert
					doTextInsert(startPos, keyword, description);
				}
				currPos= startPos;
			}
			if (lastNonDelete == -1) { // all removed, set back to start so the keyword is removed as well
				currPos= startPos;
			}
			
			int prevEnd= currPos;
			
			final int NONE= 0, NEW= 1, EXISTING= 2;
			int separatorState= NEW;

			for (int i= 0; i < total; i++) {
				ASTNode elem= getNode(i);
				int currMark= getChangeKind(elem);
				int nextIndex= i + 1;

				if (currMark == ASTRewrite.INSERTED) {
					GroupDescription description= getDescription(elem);
					
					if (separatorState == NONE) { // element after last existing element (but not first)
						doTextInsert(currPos, getSeparatorString(i - 1), description);
						separatorState= NEW;
					}
					if (separatorState == NEW || insertAfterSeparator(elem)) {
						doTextInsert(currPos, elem, getNodeIndent(i), true, description);
						
						separatorState= NEW;
						if (i != lastNonDelete) {
							if (getChangeKind(getNode(nextIndex)) != ASTRewrite.INSERTED) {
								doTextInsert(currPos, getSeparatorString(i), description);
							} else {
								separatorState= NONE;
							}
						}
					} else { // EXISTING && insert before separator
						doTextInsert(prevEnd, getSeparatorString(i - 1), description);
						doTextInsert(prevEnd, elem, getNodeIndent(i), true, description);
					}
				} else if (currMark == ASTRewrite.REMOVED) {
					int currEnd= getEndOfNode(elem);
					if (i > lastNonDelete && separatorState == EXISTING) {
						// is last, remove previous separator
						doTextRemoveAndVisit(prevEnd, currEnd - prevEnd, elem);
						currPos= currEnd;
						prevEnd= currEnd;
					} else {
						// remove element and next separator
						int end= getStartOfNextNode(nextIndex, currEnd); // start of next
						doTextRemoveAndVisit(currPos, end - currPos, elem);
						currPos= end;
						prevEnd= currEnd;
						separatorState= NEW;
					}
				} else { // replaced or unchanged
					if (currMark == ASTRewrite.REPLACED) {
						int currEnd= getEndOfNode(elem);
						
						GroupDescription description= getDescription(elem);
						ASTNode changed= getReplacingNode(elem);
						doTextRemoveAndVisit(currPos, currEnd - currPos, elem);
						doTextInsert(currPos, changed, getNodeIndent(i), true, description);
						
						prevEnd= currEnd;
					} else { // is unchanged
						doVisit(elem);
					}
					if (i == lastNonInsert) { // last node or next nodes are all inserts
						separatorState= NONE;
						if (currMark == ASTRewrite.UNCHANGED) {
							prevEnd= getEndOfNode(elem);
						}
						currPos= prevEnd;
					} else if (getChangeKind(getNode(nextIndex)) != ASTRewrite.UNCHANGED) {
						// no updates needed while nodes are unchanged
						if (currMark == ASTRewrite.UNCHANGED) {
							prevEnd= getEndOfNode(elem);
						}
						currPos= getStartOfNextNode(nextIndex, prevEnd); // start of next
						separatorState= EXISTING;							
					}
				}

			}
			return currPos;
		}
	}
			
	private int rewriteRequiredNode(ASTNode node) {
		checkNoInsertOrRemove(node);
		if (isReplaced(node)) {
			ISourceRange range= getNodeRange(node, -1);
			int offset= range.getOffset();
			int length= range.getLength();
			doTextRemoveAndVisit(offset, length, node);
			doTextInsert(offset, getReplacingNode(node), getIndent(offset), true, getDescription(node));
			return offset + length;
		} else {
			return doVisit(node);
		}
	}
	
	private int rewriteNode(ASTNode node, int offset, String prefix) {
		int changeKind= getChangeKind(node);
		if (changeKind == ASTRewrite.INSERTED) {
			GroupDescription description= getDescription(node);
			doTextInsert(offset, prefix, description);
			doTextInsert(offset, node, getIndent(offset), true, description);
			return offset;
		} else if (changeKind == ASTRewrite.REMOVED) {
			ISourceRange range= getNodeRange(node, -1);
			int nodeOffset= range.getOffset();
			int nodeLen= range.getLength();		
			// if there is a prefix, remove the prefix as well
			int len= nodeOffset + nodeLen - offset;
			doTextRemoveAndVisit(offset, len, node);
			return nodeOffset + nodeLen;
		} else if (changeKind == ASTRewrite.REPLACED) {
			ISourceRange range= getNodeRange(node, -1);
			int nodeOffset= range.getOffset();
			int nodeLen= range.getLength();				
			doTextRemoveAndVisit(nodeOffset, nodeLen, node);
			doTextInsert(nodeOffset, getReplacingNode(node), getIndent(offset), true, getDescription(node));
			return nodeOffset + nodeLen;
		} else {
			return doVisit(node);
		}
	}		

	private int rewriteOptionalQualifier(ASTNode node, int startPos) {
		int changeKind= getChangeKind(node);
		if (changeKind == ASTRewrite.INSERTED) {
			GroupDescription description= getDescription(node);
			doTextInsert(startPos, node, getIndent(startPos), true, description);
			doTextInsert(startPos, ".", description); //$NON-NLS-1$
			return startPos;
		} else {
			if (changeKind == ASTRewrite.REMOVED) {
				try {
					int dotEnd= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameDOT, node.getStartPosition() + node.getLength());
					doTextRemoveAndVisit(startPos, dotEnd - startPos, node);
					return dotEnd;
				} catch (CoreException e) {
					JavaPlugin.log(e);
				}
				return doVisit(node);
			} else if (changeKind == ASTRewrite.REPLACED) {
				ISourceRange range= getNodeRange(node, startPos);
				doTextRemoveAndVisit(range.getOffset(), range.getLength(), node);
				doTextInsert(range.getOffset(), getReplacingNode(node), getIndent(startPos), true, getDescription(node));
				return range.getOffset() + range.getLength();
			} else {
				return doVisit(node);
			}
		}
	}
	
	private class ParagraphListRewriter extends ListRewriter {
		
		public final int DEFAULT_SPACING= 1;
		
		private int fInitialIndent;
		private int fSeparatorLines;
		
		public ParagraphListRewriter(int initialIndent, int separator) {
			fInitialIndent= initialIndent;
			fSeparatorLines= separator;
		}
		
		protected int getInitialIndent() {
			return fInitialIndent;
		}
						
		protected String getSeparatorString(int nodeIndex) {
			int newLines= fSeparatorLines == -1 ? getNewLines(nodeIndex) : fSeparatorLines;
			
			String lineDelim= fTextBuffer.getLineDelimiter();
			StringBuffer buf= new StringBuffer(lineDelim);
			for (int i= 0; i < newLines; i++) {
				buf.append(lineDelim);
			}
			buf.append(CodeFormatterUtil.createIndentString(getNodeIndent(nodeIndex + 1)));
			return buf.toString();
		}
		
		private int getNewLines(int nodeIndex) {
			ASTNode curr= getNode(nodeIndex);
			ASTNode next= getNode(nodeIndex + 1);
			
			int currKind= curr.getNodeType();
			int nextKind= next.getNodeType();

			ASTNode last= null;
			ASTNode secondLast= null;
			for (int i= 0; i < fList.size(); i++) {
				ASTNode elem= getNode(i);
				if (!isInserted(elem)) {
					if (last != null) {
						if (elem.getNodeType() == nextKind && last.getNodeType() == currKind) {
							return countEmptyLines(last);
						}
						secondLast= last;
					}
					last= elem;
				}
			}
			if (currKind == ASTNode.FIELD_DECLARATION && nextKind == ASTNode.FIELD_DECLARATION ) {
				return 0;
			}
			if (secondLast != null) {
				return countEmptyLines(secondLast);
			}
			return DEFAULT_SPACING;
		}

		private int countEmptyLines(ASTNode last) {
			int lastLine= fTextBuffer.getLineOfOffset(last.getStartPosition() + last.getLength());
			int scanLine= lastLine + 1;
			int numLines= fTextBuffer.getNumberOfLines();
			while(scanLine < numLines && Strings.containsOnlyWhitespaces(fTextBuffer.getLineContent(scanLine))){
				scanLine++;
			}
			return scanLine - lastLine - 1;	
		}
		
	}
	
	private int rewriteParagraphList(List list, int insertPos, int insertIndent, int separator, int lead) {
		boolean hasChanges= false;
		boolean hasExisting= false;
		
		for (int i= 0; i < list.size(); i++) {
			ASTNode elem= (ASTNode) list.get(i);
			int changeKind= getChangeKind(elem);
			hasChanges |= (changeKind != ASTRewrite.UNCHANGED);
			hasExisting |= (changeKind != ASTRewrite.INSERTED);
		}
		if (!hasChanges) {
			return visitList(list, insertPos);
		}
		
		ParagraphListRewriter listRewriter= new ParagraphListRewriter(insertIndent, separator);
		StringBuffer leadString= new StringBuffer();
		if (!hasExisting) {
			for (int i= 0; i < lead; i++) {
				leadString.append(fTextBuffer.getLineDelimiter());
			}
			leadString.append(CodeFormatterUtil.createIndentString(insertIndent));
		}
		return listRewriter.rewriteList(list, insertPos, leadString.toString());
	}
	
	private int rewriteRequiredParagraph(ASTNode elem) {
		checkNoInsertOrRemove(elem);
		if (isReplaced(elem)) {
			ISourceRange range= getNodeRange(elem, -1);
			int offset= range.getOffset();
			int length= range.getLength();

			doTextRemoveAndVisit(offset, length, elem);
			doTextInsert(offset, getReplacingNode(elem), getIndent(offset), true, getDescription(elem));
			return offset + length;			
		} else {
			return doVisit(elem);
		}
	}	

	private void rewriteMethodBody(MethodDeclaration methodDecl, Block body, int startPos) { 
		int changeKind= getChangeKind(body);
		if (changeKind == ASTRewrite.INSERTED) {
			int endPos= methodDecl.getStartPosition() + methodDecl.getLength();
			GroupDescription description= getDescription(body);
			doTextRemove(startPos, endPos - startPos, description);
			doTextInsert(startPos, " ", description); //$NON-NLS-1$
			doTextInsert(startPos, body, getIndent(methodDecl.getStartPosition()), true, description);
		} else if (changeKind == ASTRewrite.REMOVED) {
			int endPos= methodDecl.getStartPosition() + methodDecl.getLength();
			doTextRemoveAndVisit(startPos, endPos - startPos, body);
			doTextInsert(startPos, ";", getDescription(body)); //$NON-NLS-1$
		} else if (changeKind == ASTRewrite.REPLACED) {
			doTextRemoveAndVisit(body.getStartPosition(), body.getLength(), body);
			doTextInsert(body.getStartPosition(), getReplacingNode(body), getIndent(body.getStartPosition()), true,getDescription(body));
		} else {
			doVisit(body);
		}
	}
	
	private void rewriteExtraDimensions(int oldDim, int newDim, int pos, GroupDescription description) {
		if (oldDim < newDim) {
			for (int i= oldDim; i < newDim; i++) {
				doTextInsert(pos, "[]", description); //$NON-NLS-1$
			}
		} else if (newDim < oldDim) {
			try {
				getScanner().setOffset(pos);
				for (int i= newDim; i < oldDim; i++) {
					getScanner().readToToken(ITerminalSymbols.TokenNameRBRACKET);
				}
				doTextRemove(pos, getScanner().getCurrentEndOffset() - pos, description);
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
	}	
	
	final int getNextExistingStartPos(List list, int listStartIndex, int defaultOffset) {
		for (int i= listStartIndex; i < list.size(); i++) {
			ASTNode elem= (ASTNode) list.get(i);
			if (!isInserted(elem)) {
				ISourceRange range= getNodeRange(elem, defaultOffset);
				return range.getOffset();
			}
		}
		return defaultOffset;
	}
	
	final ASTNode findPreviousExistingNode(List list, ASTNode node) {
		ASTNode res= null;
		for (int i= 0; i < list.size(); i++) {
			ASTNode elem= (ASTNode) list.get(i);
			if (elem == node) {
				return res;
			}
			if (!isInserted(elem)) {
				res= elem;
			}
		}
		return null;		
	}
		
	private boolean hasChanges(List list) {
		for (int i= 0; i < list.size(); i++) {
			ASTNode elem= (ASTNode) list.get(i);
			if (isChanged(elem)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isAllRemoved(List list) {
		for (int i= 0; i < list.size(); i++) {
			ASTNode elem= (ASTNode) list.get(i);
			if (!isRemoved(elem)) {
				return false;
			}
		}
		return true;
	}	
	
	private int visitList(List list, int defaultPos) {
		int endPos= defaultPos;
		for (Iterator iter= list.iterator(); iter.hasNext();) {
			ASTNode curr= ((ASTNode) iter.next());
			endPos= doVisit(curr);
		}
		return endPos;
	}
	
	/*
	 * Next token is a left brace. Returns the offset after the brace. For incomplete code, return the start offset.  
	 */
	private int getPosAfterLeftBrace(int pos) {
		try {
			int nextToken= getScanner().readNext(pos, true);
			if (nextToken == ITerminalSymbols.TokenNameLBRACE) {
				return getScanner().getCurrentEndOffset();
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
		return pos;
	}
	
	final int getIndent(int pos) {
		int line= fTextBuffer.getLineOfOffset(pos);
		return fTextBuffer.getLineIndent(line, CodeFormatterUtil.getTabWidth());
	}

	final void doTextInsert(int insertOffset, ASTNode node, int initialIndentLevel, boolean removeLeadingIndent, GroupDescription description) {		
		ArrayList markers= new ArrayList();
		String formatted= fFormatter.getFormattedResult(node, initialIndentLevel, fTextBuffer.getLineDelimiter(), markers);

		int tabWidth= CodeFormatterUtil.getTabWidth();
		int currPos= 0;
		if (removeLeadingIndent) {
			while (currPos < formatted.length() && Strings.isIndentChar(formatted.charAt(currPos))) {
				currPos++;
			}
		}
		for (int i= 0; i < markers.size(); i++) {
			NodeMarker curr= (NodeMarker) markers.get(i);
			
			int offset= curr.offset;
			if (offset != currPos) {
				String insertStr= formatted.substring(currPos, offset);
				doTextInsert(insertOffset, insertStr, description);
			}
			Object data= curr.data;
			if (data instanceof GroupDescription) {
				TextEdit edit= new RangeMarker(insertOffset, 0);
				addDescription((GroupDescription) data, edit);
				addEdit(edit);				
			} else {
				String destIndentString=  Strings.getIndentString(getCurrentLine(formatted, offset), tabWidth);
				if (data instanceof MovePlaceholderData) {
					ASTNode existingNode= ((MovePlaceholderData) data).node;
					int srcIndentLevel= getIndent(existingNode.getStartPosition());
					doTextMove(existingNode, insertOffset, srcIndentLevel, destIndentString, tabWidth, description);
				} else if (data instanceof CopyPlaceholderData) {
					ASTNode existingNode= ((CopyPlaceholderData) data).node;
					int srcIndentLevel= getIndent(existingNode.getStartPosition());
					doTextCopy(existingNode, insertOffset, srcIndentLevel, destIndentString, tabWidth, description);
				} else if (data instanceof StringPlaceholderData) {
					String code= ((StringPlaceholderData) data).code;
					String str= Strings.changeIndent(code, 0, tabWidth, destIndentString, fTextBuffer.getLineDelimiter()); 
					doTextInsert(insertOffset, str, description);
				}
			}
			currPos= offset + curr.length;
		}
		if (currPos < formatted.length()) {
			String insertStr= formatted.substring(currPos);
			doTextInsert(insertOffset, insertStr, description);
		}
	}
	
	private String getCurrentLine(String str, int pos) {
		for (int i= pos - 1; i>= 0; i--) {
			char ch= str.charAt(i);
			if (Strings.isLineDelimiterChar(ch)) {
				return str.substring(i + 1, pos);
			}
		}
		return str.substring(0, pos);
	}	
	

	private void rewriteModifiers(int offset, int oldModifiers, int newModifiers, GroupDescription description) {
		if (oldModifiers == newModifiers) {
			return;
		}
		
		try {
			int tok= getScanner().readNext(offset, true);
			int startPos= getScanner().getCurrentStartOffset();
			int endPos= startPos;
			loop: while (true) {
				boolean keep= true;
				switch (tok) {
					case ITerminalSymbols.TokenNamepublic: keep= Modifier.isPublic(newModifiers); break;
					case ITerminalSymbols.TokenNameprotected: keep= Modifier.isProtected(newModifiers); break;
					case ITerminalSymbols.TokenNameprivate: keep= Modifier.isPrivate(newModifiers); break;
					case ITerminalSymbols.TokenNamestatic: keep= Modifier.isStatic(newModifiers); break;
					case ITerminalSymbols.TokenNamefinal: keep= Modifier.isFinal(newModifiers); break;
					case ITerminalSymbols.TokenNameabstract: keep= Modifier.isAbstract(newModifiers); break;
					case ITerminalSymbols.TokenNamenative: keep= Modifier.isNative(newModifiers); break;
					case ITerminalSymbols.TokenNamevolatile: keep= Modifier.isVolatile(newModifiers); break;
					case ITerminalSymbols.TokenNamestrictfp: keep= Modifier.isStrictfp(newModifiers); break;
					case ITerminalSymbols.TokenNametransient: keep= Modifier.isTransient(newModifiers); break;
					case ITerminalSymbols.TokenNamesynchronized: keep= Modifier.isSynchronized(newModifiers); break;
					default:
						break loop;
				}
				tok= getScanner().readNext(true);
				int currPos= endPos;
				endPos= getScanner().getCurrentStartOffset();
				if (!keep) {
					doTextRemove(currPos, endPos - currPos, description);
				}
			} 
			int addedModifiers= newModifiers & ~oldModifiers;
			if (addedModifiers != 0) {
				if (startPos != endPos) {
					int visibilityModifiers= addedModifiers & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED);
					if (visibilityModifiers != 0) {
						StringBuffer buf= new StringBuffer();
						ASTFlattener.printModifiers(visibilityModifiers, buf);
						doTextInsert(startPos, buf.toString(), description);
						addedModifiers &= ~visibilityModifiers;
					}
				}
				StringBuffer buf= new StringBuffer();
				ASTFlattener.printModifiers(addedModifiers, buf);
				doTextInsert(endPos, buf.toString(), description);
			}
		} catch (CoreException e) {
			// ignore
		}		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#postVisit(ASTNode)
	 */
	public void postVisit(ASTNode node) {
		Object annotation= fRewrite.getTrackedNodeData(node);
		if (annotation != null) {
			fCurrentEdit= fCurrentEdit.getParent();
		}
		int count= fRewrite.getCopyCount(node);
		while (count > 0) {
			fCurrentEdit= fCurrentEdit.getParent();
			count--;
		}
		if (isMoveSource(node)) {
			fCurrentEdit= fCurrentEdit.getParent();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#preVisit(ASTNode)
	 */
	public void preVisit(ASTNode node) {
		// first move, then copies, then range marker
		TextEdit moveEdit= getMoveSource(node);
		if (moveEdit != null) {
			addEdit(moveEdit);
			fCurrentEdit= moveEdit;
		}
		TextEdit[] edits= getCopySources(node);
		if (edits != null) {
			for (int i= 0; i < edits.length; i++) {
				TextEdit edit= edits[i];
				addEdit(edit);
				fCurrentEdit= edit;		
			}
		}
		GroupDescription data= fRewrite.getTrackedNodeData(node);
		if (data != null) {
			ISourceRange range= getNodeRange(node, -1);
			TextEdit edit= new RangeMarker(range.getOffset(), range.getLength());
			addDescription(data, edit);
			addEdit(edit);
			fCurrentEdit= edit;
		}
	}	

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(CompilationUnit)
	 */ 
	public boolean visit(CompilationUnit node) {
		int startPos= 0;
		PackageDeclaration packageDeclaration= node.getPackage();
		if (packageDeclaration != null) {
			startPos= rewriteNode(packageDeclaration, 0, ""); //$NON-NLS-1$
			if (isInserted(packageDeclaration)) {
				doTextInsert(0, fTextBuffer.getLineDelimiter(), getDescription(packageDeclaration));
			}
		}
				
		List imports= node.imports();
		startPos= rewriteParagraphList(imports, startPos, 0, 0, 2);

		List types= node.types();
		rewriteParagraphList(types, startPos, 0, -1, 2);
		return false;
	}


	/**
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(TypeDeclaration)
	 */
	public boolean visit(TypeDeclaration typeDecl) {
		
		// modifiers & class/interface
		boolean invertType= false;
		if (isModified(typeDecl)) {
			TypeDeclaration modifiedNode= (TypeDeclaration) getModifiedNode(typeDecl);
			int modfiedModifiers= modifiedNode.getModifiers();
			
			rewriteModifiers(typeDecl.getStartPosition(), typeDecl.getModifiers(), modfiedModifiers, getDescription(typeDecl));
			if (modifiedNode.isInterface() != typeDecl.isInterface()) { // change from class to interface or reverse
				invertType= true;
				try {
					int typeToken= typeDecl.isInterface() ? ITerminalSymbols.TokenNameinterface : ITerminalSymbols.TokenNameclass;
					getScanner().readToToken(typeToken, typeDecl.getStartPosition());
					
					String str= typeDecl.isInterface() ? "class" : "interface"; //$NON-NLS-1$ //$NON-NLS-2$
					int start= getScanner().getCurrentStartOffset();
					int end= getScanner().getCurrentEndOffset();
					
					doTextReplace(start, end - start, str, getDescription(typeDecl));
				} catch (CoreException e) {
					// ignore
				}
			}
		}
		
		// name
		int pos= rewriteRequiredNode(typeDecl.getName());
		
		// superclass
		Name superClass= typeDecl.getSuperclass();
		if ((!typeDecl.isInterface() || invertType) && superClass != null) {
			int changeKind= getChangeKind(superClass);
			if (changeKind == ASTRewrite.INSERTED) {
				String str= " extends " + ASTNodes.asString(superClass); //$NON-NLS-1$
				doTextInsert(pos, str, getDescription(superClass));
			} else {
				if (changeKind == ASTRewrite.REMOVED) {
					ISourceRange range= getNodeRange(superClass, pos);
					int endPos= range.getOffset() + range.getLength();
					doTextRemoveAndVisit(pos, endPos - pos, superClass);
					pos= endPos;
				} else if (changeKind == ASTRewrite.REPLACED) {
					ISourceRange range= getNodeRange(superClass, pos);
					doTextRemoveAndVisit(range.getOffset(), range.getLength(), superClass);
					doTextInsert(range.getOffset(), getReplacingNode(superClass), 0, false, getDescription(superClass));
					pos= range.getOffset() + range.getLength();
				} else {
					pos= doVisit(superClass);
				}
			}
		}
		// extended interfaces
		List interfaces= typeDecl.superInterfaces();
		
		String keyword= (typeDecl.isInterface() != invertType) ? " extends " : " implements "; //$NON-NLS-1$ //$NON-NLS-2$
		if (invertType && !isAllRemoved(interfaces)) {
			int firstStart= getNextExistingStartPos(interfaces, 0, pos);
			doTextReplace(pos, firstStart - pos, keyword, getDescription(typeDecl));
			keyword= ""; //$NON-NLS-1$
			pos= firstStart;
		}
		
		if (hasChanges(interfaces)) {
			pos= getDefaultRewriter().rewriteList(interfaces, pos, keyword, ", "); //$NON-NLS-1$
		} else {
			pos= visitList(interfaces, pos);
		}
		
		// type members
		List members= typeDecl.bodyDeclarations();

		// startPos : find position after left brace of type, be aware that bracket might be missing
		int startIndent= getIndent(typeDecl.getStartPosition()) + 1;
		int startPos= getPosAfterLeftBrace(pos);
		rewriteParagraphList(members, startPos, startIndent, -1, 2);
		return false;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(MethodDeclaration)
	 */
	public boolean visit(MethodDeclaration methodDecl) {
		boolean willBeConstructor= methodDecl.isConstructor();
		if (isModified(methodDecl)) {
			MethodDeclaration modifiedNode= (MethodDeclaration) getModifiedNode(methodDecl);
			rewriteModifiers(methodDecl.getStartPosition(), methodDecl.getModifiers(), modifiedNode.getModifiers(), getDescription(methodDecl));
			willBeConstructor= modifiedNode.isConstructor();
		}
		
		Type returnType= methodDecl.getReturnType();
		if (!willBeConstructor || isRemoved(returnType)) {
			try {
				int startPos= methodDecl.getStartPosition();
				if (isInsertOrRemove(returnType)) {
					getScanner().setOffset(startPos);
					int token= getScanner().readNext(true);
					while (TokenScanner.isModifier(token)) {
						startPos= getScanner().getCurrentEndOffset();
						token= getScanner().readNext(true);
					}
				}
				if (startPos == methodDecl.getName().getStartPosition()) {
					rewriteNode(returnType, startPos, ""); //$NON-NLS-1$
					if (isInserted(returnType)) {
						doTextInsert(startPos, " ", getDescription(returnType)); //$NON-NLS-1$
					}
				} else {
					rewriteNode(returnType, startPos, " "); //$NON-NLS-1$
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
	
		int pos= rewriteRequiredNode(methodDecl.getName());
		try {
			List parameters= methodDecl.parameters();
			if (hasChanges(parameters)) {
				pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLPAREN, pos);
				pos= getDefaultRewriter().rewriteList(parameters, pos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				pos= visitList(parameters, pos);
			}
			
			if (isModified(methodDecl)) {
				MethodDeclaration modifedNode= (MethodDeclaration) getModifiedNode(methodDecl);
				int oldDim= methodDecl.getExtraDimensions();
				int newDim= modifedNode.getExtraDimensions();
				if (oldDim != newDim) {
					int offset= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameRPAREN, pos);
					rewriteExtraDimensions(oldDim, newDim, offset, getDescription(methodDecl));
				}
			}				
			
			List exceptions= methodDecl.thrownExceptions();
			boolean hasExceptionChanges= hasChanges(exceptions);
			Block body= methodDecl.getBody();
			
			if (hasExceptionChanges || body != null && isInsertOrRemove(body)) {
				pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameRPAREN, pos);
				int dim= methodDecl.getExtraDimensions();
				while (dim > 0) {
					pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameRBRACKET, pos);
					dim--;
				}
			}				
			if (hasExceptionChanges) {
				pos= getDefaultRewriter().rewriteList(exceptions, pos, " throws ", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				pos= visitList(exceptions, pos);
			}
			if (body != null) {
				rewriteMethodBody(methodDecl, body, pos);
			}				
		} catch (CoreException e) {
			// ignore
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(Block)
	 */
	public boolean visit(Block block) {
		List list=  block.statements();
		
		List collapsedChildren= fRewrite.getCollapsedNodes(block);
		int startPos;
		if (collapsedChildren != null) {
			list= collapsedChildren; // not a real block, but a placeholder
			startPos= getNextExistingStartPos(list, 0, block.getStartPosition());
		} else {
			startPos= getPosAfterLeftBrace(block.getStartPosition());
		}
		int startIndent= getIndent(block.getStartPosition()) + 1;
		rewriteParagraphList(list, startPos, startIndent, 0, 1);
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ReturnStatement)
	 */
	public boolean visit(ReturnStatement node) {
		Expression expression= node.getExpression();
		if (expression != null) {
			if (isChanged(expression)) {
				try {
					int offset= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNamereturn, node.getStartPosition());
					rewriteNode(expression, offset, " "); //$NON-NLS-1$
				} catch (CoreException e) {
					JavaPlugin.log(e);
				}
			} else {
				doVisit(expression);
			}
		}
		return false;
	}		
	

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(AnonymousClassDeclaration)
	 */
	public boolean visit(AnonymousClassDeclaration node) {
		List declarations= node.bodyDeclarations();
		int startPos= getPosAfterLeftBrace(node.getStartPosition());
		int startIndent= getIndent(node.getStartPosition()) + 1;
		rewriteParagraphList(declarations, startPos, startIndent, -1, 2);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ArrayAccess)
	 */
	public boolean visit(ArrayAccess node) {
		rewriteRequiredNode(node.getArray());
		rewriteRequiredNode(node.getIndex());
		return false;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ArrayCreation)
	 */
	public boolean visit(ArrayCreation node) {
		ArrayType arrayType= node.getType();
		int nOldBrackets= arrayType.getDimensions(); // number of total brackets
		int nNewBrackets= nOldBrackets;
		
		GroupDescription description= null;
		checkNoInsertOrRemove(arrayType);
		if (isReplaced(arrayType)) { // changed arraytype can have different dimension or type name
			ArrayType replacingType= (ArrayType) getReplacingNode(arrayType);
			description= getDescription(arrayType);
			
			Assert.isTrue(replacingType != null, "Cant remove array type in ArrayCreation"); //$NON-NLS-1$
			Type newType= replacingType.getElementType();
			Type oldType= arrayType.getElementType();
			if (!newType.equals(oldType)) {
				ISourceRange range= getNodeRange(oldType, node.getStartPosition());
				doTextRemove(range.getOffset(), range.getLength(), description);
				doTextInsert(range.getOffset(), newType, 0, false, description);
			}
			nNewBrackets= replacingType.getDimensions();
		}
		doVisit(arrayType);
		List dimExpressions= node.dimensions(); // dimension node with expressions
		try {
			// offset on first opening brace
			int offset= getScanner().getTokenStartOffset(ITerminalSymbols.TokenNameLBRACKET, arrayType.getStartPosition());
			for (int i= 0; i < dimExpressions.size(); i++) {
				ASTNode elem = (ASTNode) dimExpressions.get(i);
				int changeKind= getChangeKind(elem);
				if (changeKind == ASTRewrite.INSERTED) { // insert new dimension
					description= getDescription(elem);
					doTextInsert(offset, "[", description); //$NON-NLS-1$
					doTextInsert(offset, elem, 0, false, description);
					doTextInsert(offset, "]", description); //$NON-NLS-1$
					nNewBrackets--;
				} else {
					int elemEnd= elem.getStartPosition() + elem.getLength();
					int endPos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameRBRACKET, elemEnd);
					if (changeKind == ASTRewrite.REMOVED) {
						// remove includes open and closing brace
						description= getDescription(elem);
						doTextRemoveAndVisit(offset, endPos - offset, elem);
					} else if (changeKind == ASTRewrite.REPLACED) {
						description= getDescription(elem);
						ISourceRange range= getNodeRange(elem, offset);
						doTextRemoveAndVisit(range.getOffset(), range.getLength(), elem);
						doTextInsert(range.getOffset(), getReplacingNode(elem), 0, false, description);
						nNewBrackets--;
					} else {
						doVisit(elem);
						nNewBrackets--;
					}
					offset= endPos;
					nOldBrackets--;
				}
			}
			rewriteExtraDimensions(nOldBrackets, nNewBrackets, offset, description);
		
			ArrayInitializer initializer= node.getInitializer();
			if (initializer != null) {
				int pos= node.getStartPosition() + node.getLength(); // insert pos
				if (isRemoved(initializer)) {
					pos= getPosBeforeSpaces(initializer.getStartPosition()); // remove pos
				}
				rewriteNode(initializer, pos, " "); //$NON-NLS-1$
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}		
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ArrayInitializer)
	 */
	public boolean visit(ArrayInitializer node) {
		List expressions= node.expressions();
		int startPos= getPosAfterLeftBrace(node.getStartPosition());
		if (hasChanges(expressions)) {
			getDefaultRewriter().rewriteList(expressions, startPos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			visitList(expressions, startPos);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ArrayType)
	 */
	public boolean visit(ArrayType node) {
		rewriteRequiredNode(node.getComponentType());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(AssertStatement)
	 */
	public boolean visit(AssertStatement node) {
		int offset= rewriteRequiredNode(node.getExpression());
		if (node.getMessage() != null) { 
			rewriteNode(node.getMessage(), offset, " : "); //$NON-NLS-1$
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(Assignment)
	 */
	public boolean visit(Assignment node) {
		Expression leftHand= node.getLeftHandSide();
		int pos= rewriteRequiredNode(leftHand);
		
		if (isModified(node)) {
			try {
				getScanner().readNext(pos, true);
				Assignment.Operator modifiedOp= ((Assignment) getModifiedNode(node)).getOperator();
				doTextReplace(getScanner().getCurrentStartOffset(), getScanner().getCurrentLength(), modifiedOp.toString(), getDescription(node));
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
			
		Expression rightHand= node.getRightHandSide();
		rewriteRequiredNode(rightHand);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(BooleanLiteral)
	 */
	public boolean visit(BooleanLiteral node) {
		checkNoModification(node); // no modification possible
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(BreakStatement)
	 */
	public boolean visit(BreakStatement node) {
		ASTNode label= node.getLabel();
		if (label != null) {
			if (isChanged(label)) {
				try {
					int offset= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNamebreak, node.getStartPosition());
					rewriteNode(label, offset, " "); // space between break and label //$NON-NLS-1$
				} catch (CoreException e) {
					JavaPlugin.log(e);
				}
			} else {
				doVisit(label);
			}
		}
		return false;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(CastExpression)
	 */
	public boolean visit(CastExpression node) {
		rewriteRequiredNode(node.getType());
		rewriteRequiredNode(node.getExpression());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(CatchClause)
	 */
	public boolean visit(CatchClause node) { // catch (Exception) Block
		rewriteRequiredNode(node.getException());
		rewriteRequiredParagraph(node.getBody());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(CharacterLiteral)
	 */
	public boolean visit(CharacterLiteral node) {
		checkNoModification(node); // no modification possible
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ClassInstanceCreation)
	 */
	public boolean visit(ClassInstanceCreation node) {
		Expression expression= node.getExpression();
		if (expression != null) {
			rewriteOptionalQualifier(expression, node.getStartPosition());
		}
		
		Name name= node.getName();
		int pos= rewriteRequiredNode(name);

		List arguments= node.arguments();
		if (hasChanges(arguments)) {
			try {
				int startpos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLPAREN, pos);
				getDefaultRewriter().rewriteList(arguments, startpos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		} else {
			visitList(arguments, 0);
		}
		
		AnonymousClassDeclaration decl= node.getAnonymousClassDeclaration();
		if (decl != null) {
			pos= node.getStartPosition() + node.getLength(); // for insert
			if (isRemoved(decl)) {
				pos= getPosBeforeSpaces(decl.getStartPosition()); // for remove
			}
			rewriteNode(decl, pos, " "); //$NON-NLS-1$
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ConditionalExpression)
	 */
	public boolean visit(ConditionalExpression node) { // expression ? thenExpression : elseExpression
		rewriteRequiredNode(node.getExpression());
		rewriteRequiredNode(node.getThenExpression());
		rewriteRequiredNode(node.getElseExpression());	
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ConstructorInvocation)
	 */
	public boolean visit(ConstructorInvocation node) {
		List arguments= node.arguments();
		if (hasChanges(arguments)) {
			try {
				int startpos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLPAREN, node.getStartPosition());
				getDefaultRewriter().rewriteList(arguments, startpos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		} else {
			visitList(arguments, 0);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ContinueStatement)
	 */
	public boolean visit(ContinueStatement node) {
		ASTNode label= node.getLabel();
		if (label != null) {
			if (isChanged(label)) {
				try {
					int offset= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNamecontinue, node.getStartPosition());
					rewriteNode(label, offset, " "); // space between continue and label //$NON-NLS-1$
				} catch (CoreException e) {
					JavaPlugin.log(e);
				}
			} else {
				doVisit(label);
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(DoStatement)
	 */
	public boolean visit(DoStatement node) { // do statement while expression
		rewriteRequiredNode(node.getBody());	
		rewriteRequiredNode(node.getExpression());	
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(EmptyStatement)
	 */
	public boolean visit(EmptyStatement node) {
		checkNoModification(node); // no modification possible
		return false;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ExpressionStatement)
	 */
	public boolean visit(ExpressionStatement node) { // expression
		rewriteRequiredNode(node.getExpression());	
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(FieldAccess)
	 */
	public boolean visit(FieldAccess node) { // expression.name
		rewriteRequiredNode(node.getExpression()); // expression
		rewriteRequiredNode(node.getName()); // name
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(FieldDeclaration)
	 */
	public boolean visit(FieldDeclaration node) { //{ Modifier } Type VariableDeclarationFragment { ',' VariableDeclarationFragment } ';'
		if (isModified(node)) {
			FieldDeclaration modifedNode= (FieldDeclaration) getModifiedNode(node);
			rewriteModifiers(node.getStartPosition(), node.getModifiers(), modifedNode.getModifiers(), getDescription(node));
		}
		
		Type type= node.getType();
		rewriteRequiredNode(type);
		
		List fragments= node.fragments();
		if (hasChanges(fragments)) {
			int startPos= getNextExistingStartPos(fragments, 0, type.getStartPosition() + type.getLength());
			getDefaultRewriter().rewriteList(fragments, startPos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			visitList(fragments, 0);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ForStatement)
	 */
	public boolean visit(ForStatement node) {
		try {
			int pos= node.getStartPosition();
			
			List initializers= node.initializers();
			if (hasChanges(initializers)) { // position after opening parent
				int startOffset= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLPAREN, pos);
				pos= getDefaultRewriter().rewriteList(initializers, startOffset, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				pos= visitList(initializers, pos);
			}
			
			// position after first semicolon
			pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameSEMICOLON, pos);
			
			Expression expression= node.getExpression();
			if (expression != null) {
				rewriteNode(expression, pos, ""); //$NON-NLS-1$
			}
			
			List updaters= node.updaters();
			if (hasChanges(updaters)) {
				int startOffset= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameSEMICOLON, pos);
				getDefaultRewriter().rewriteList(updaters, startOffset, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				visitList(updaters, 0);
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
			
		rewriteRequiredNode(node.getBody()); // body
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(IfStatement)
	 */
	public boolean visit(IfStatement node) {
		rewriteRequiredNode(node.getExpression()); // statement
		
		Statement thenStatement= node.getThenStatement();
		rewriteRequiredNode(thenStatement); // then statement

		Statement elseStatement= node.getElseStatement();
		if (elseStatement != null) {
			int startPos= thenStatement.getStartPosition() + thenStatement.getLength();
			rewriteNode(elseStatement, startPos, " else "); //$NON-NLS-1$
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ImportDeclaration)
	 */
	public boolean visit(ImportDeclaration node) {
		Name name= node.getName();
		rewriteRequiredNode(name); // statement
		
		if (isModified(node)) {
			ImportDeclaration modifiedNode= (ImportDeclaration) getModifiedNode(node);
			if (!node.isOnDemand() && modifiedNode.isOnDemand()) {
				doTextInsert(name.getStartPosition() + name.getLength(), ".*", getDescription(node)); //$NON-NLS-1$
			} else if (node.isOnDemand() && !modifiedNode.isOnDemand()) {
				try {
					int startPos= name.getStartPosition() + name.getLength();
					int endPos= getScanner().getTokenStartOffset(ITerminalSymbols.TokenNameSEMICOLON, startPos);
					doTextRemove(startPos, endPos - startPos, getDescription(node));
				} catch (CoreException e) {
					JavaPlugin.log(e);
				}
			}
		}
		return false;
	}
	
	private void replaceOperation(int posBeforeOperation, String newOperation, GroupDescription description) {
		try {
			getScanner().readNext(posBeforeOperation, true);
			doTextReplace(getScanner().getCurrentStartOffset(), getScanner().getCurrentLength(), newOperation, description);
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
	}
	

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(InfixExpression)
	 */
	public boolean visit(InfixExpression node) {
		Expression leftHand= node.getLeftOperand();
		int pos= rewriteRequiredNode(leftHand);
		
		String operation= node.getOperator().toString();
		boolean needsNewOperation= false;
		if (isModified(node)) {
			InfixExpression.Operator modifiedOp= ((InfixExpression) getModifiedNode(node)).getOperator();
			String newOperation= modifiedOp.toString();
			if (!newOperation.equals(operation)) {
				operation= newOperation;
				needsNewOperation= true;
				replaceOperation(pos, operation, getDescription(node));
			}
		}
			
		Expression rightHand= node.getRightOperand();
		pos= rewriteRequiredNode(rightHand);
		
		List extendedOperands= node.extendedOperands();
		if (needsNewOperation || hasChanges(extendedOperands)) {
			String prefixString= ' ' + operation + ' ';
			for (int i= 0; i < extendedOperands.size(); i++) {
				ASTNode elem= (ASTNode) extendedOperands.get(i);
				if (needsNewOperation && !isInsertOrRemove(elem)) {
					replaceOperation(pos, operation, getDescription(node));
				}
				pos= rewriteNode(elem, pos, prefixString);
			}
		} else {
			visitList(extendedOperands, 0);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(Initializer)
	 */
	public boolean visit(Initializer node) {
		if (isModified(node)) {
			Initializer modifedNode= (Initializer) getModifiedNode(node);
			rewriteModifiers(node.getStartPosition(), node.getModifiers(), modifedNode.getModifiers(), getDescription(node));
		}
		rewriteRequiredNode(node.getBody());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(InstanceofExpression)
	 */
	public boolean visit(InstanceofExpression node) {
		rewriteRequiredNode(node.getLeftOperand());
		rewriteRequiredNode(node.getRightOperand());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(Javadoc)
	 */
	public boolean visit(Javadoc node) {
		checkNoChange(node);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(LabeledStatement)
	 */
	public boolean visit(LabeledStatement node) {
		rewriteRequiredNode(node.getLabel());
		rewriteRequiredNode(node.getBody());		
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(MethodInvocation)
	 */
	public boolean visit(MethodInvocation node) {
		Expression optExpression= node.getExpression();
		if (optExpression != null) {
			rewriteOptionalQualifier(optExpression, node.getStartPosition());
		}

		int pos= rewriteRequiredNode(node.getName());
		List arguments= node.arguments();
		
		if (hasChanges(arguments)) { // eval position after opening parent
			try {
				int startOffset= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLPAREN, pos);
				getDefaultRewriter().rewriteList(arguments, startOffset, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		} else {
			visitList(arguments, 0);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(NullLiteral)
	 */
	public boolean visit(NullLiteral node) {
		checkNoModification(node);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(NumberLiteral)
	 */
	public boolean visit(NumberLiteral node) {
		checkNoModification(node);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(PackageDeclaration)
	 */
	public boolean visit(PackageDeclaration node) {
		rewriteRequiredNode(node.getName());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ParenthesizedExpression)
	 */
	public boolean visit(ParenthesizedExpression node) {
		rewriteRequiredNode(node.getExpression());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(PostfixExpression)
	 */
	public boolean visit(PostfixExpression node) {
		int pos= rewriteRequiredNode(node.getOperand());

		if (isModified(node)) {
			PostfixExpression.Operator modifiedOp= ((PostfixExpression) getModifiedNode(node)).getOperator();
			String newOperation= modifiedOp.toString();
			String oldOperation= node.getOperator().toString();
			if (!newOperation.equals(oldOperation)) {
				replaceOperation(pos, newOperation, getDescription(node));
			}
		}
		return false;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(PrefixExpression)
	 */
	public boolean visit(PrefixExpression node) {
		if (isModified(node)) {
			PrefixExpression.Operator modifiedOp= ((PrefixExpression) getModifiedNode(node)).getOperator();
			String newOperation= modifiedOp.toString();
			String oldOperation= node.getOperator().toString();
			if (!newOperation.equals(oldOperation)) {
				replaceOperation(node.getStartPosition(), newOperation, getDescription(node));
			}
		}
		rewriteRequiredNode(node.getOperand());
		return false;	
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(PrimitiveType)
	 */
	public boolean visit(PrimitiveType node) {
		checkNoModification(node);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(QualifiedName)
	 */
	public boolean visit(QualifiedName node) {
		rewriteRequiredNode(node.getQualifier());
		rewriteRequiredNode(node.getName());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SimpleName)
	 */
	public boolean visit(SimpleName node) {
		checkNoModification(node);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SimpleType)
	 */
	public boolean visit(SimpleType node) {
		rewriteRequiredNode(node.getName());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SingleVariableDeclaration)
	 */
	public boolean visit(SingleVariableDeclaration node) {
		if (isModified(node)) {
			SingleVariableDeclaration modifedNode= (SingleVariableDeclaration) getModifiedNode(node);
			rewriteModifiers(node.getStartPosition(), node.getModifiers(), modifedNode.getModifiers(), getDescription(node));
		}
		
		rewriteRequiredNode(node.getType());
		int pos= rewriteRequiredNode(node.getName());
		if (isModified(node)) {
			GroupDescription description= getDescription(node);
			SingleVariableDeclaration modifedNode= (SingleVariableDeclaration) getModifiedNode(node);
			rewriteExtraDimensions(node.getExtraDimensions(), modifedNode.getExtraDimensions(), pos, description);
		}			
		
		Expression initializer= node.getInitializer();
		if (initializer != null) {
			rewriteNode(node, pos, " = "); //$NON-NLS-1$
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(StringLiteral)
	 */
	public boolean visit(StringLiteral node) {
		checkNoModification(node);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SuperConstructorInvocation)
	 */
	public boolean visit(SuperConstructorInvocation node) {
		int pos= node.getStartPosition();
		Expression optExpression= node.getExpression();
		if (optExpression != null) {
			pos= rewriteOptionalQualifier(optExpression, pos);
		}

		List arguments= node.arguments();
		if (hasChanges(arguments)) { // eval position after opening parent
			try {
				pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLPAREN, pos);
				getDefaultRewriter().rewriteList(arguments, pos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		} else {
			visitList(arguments, 0);
		}
		return false;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SuperFieldAccess)
	 */
	public boolean visit(SuperFieldAccess node) {
		int pos= node.getStartPosition();
		Expression optExpression= node.getQualifier();
		if (optExpression != null) {
			rewriteOptionalQualifier(optExpression, pos);
		}
		
		rewriteRequiredNode(node.getName());
		return false;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SuperMethodInvocation)
	 */
	public boolean visit(SuperMethodInvocation node) {
		int pos= node.getStartPosition();
		Expression optExpression= node.getQualifier();
		if (optExpression != null) {
			rewriteOptionalQualifier(optExpression, pos);
		}
		
		pos= rewriteRequiredNode(node.getName());
		List arguments= node.arguments();
		if (hasChanges(arguments)) { // eval position after opening parent
			try {
				pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLPAREN, pos);
				getDefaultRewriter().rewriteList(arguments, pos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		} else {
			visitList(arguments, 0);
		}		
		return false;	
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SwitchCase)
	 */
	public boolean visit(SwitchCase node) {
		// dont allow switching from case to default or back. New statements should be created.
		Expression expression= node.getExpression();
		if (expression != null) {
			rewriteRequiredNode(expression);
		}
		return false;
	}

	private class SwitchListRewriter extends ParagraphListRewriter {

		public SwitchListRewriter(int initialIndent) {
			super(initialIndent, 0);
		}
		
		protected int getNodeIndent(int nodeIndex) {
			int indent= getInitialIndent();
			ASTNode node= getNode(nodeIndex);
			if (node.getNodeType() != ASTNode.SWITCH_CASE) {
				indent++;
			}
			return indent;
		}		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SwitchStatement)
	 */
	public boolean visit(SwitchStatement node) {
		int pos= rewriteRequiredNode(node.getExpression());
		
		List statements= node.statements();
		if (hasChanges(statements)) {
			try {
				pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameLBRACE, pos);
				int insertIndent= getIndent(node.getStartPosition()) + 1;
				
				ParagraphListRewriter listRewriter= new SwitchListRewriter(insertIndent);
				StringBuffer leadString= new StringBuffer();
				leadString.append(fTextBuffer.getLineDelimiter());
				leadString.append(CodeFormatterUtil.createIndentString(insertIndent));
				listRewriter.rewriteList(statements, pos, leadString.toString());				
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		} else {
			visitList(statements, pos);
		}
		return false;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(SynchronizedStatement)
	 */
	public boolean visit(SynchronizedStatement node) {
		rewriteRequiredNode(node.getExpression());
		rewriteRequiredNode(node.getBody());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ThisExpression)
	 */
	public boolean visit(ThisExpression node) {
		Expression optExpression= node.getQualifier();
		if (optExpression != null) {
			rewriteOptionalQualifier(optExpression, node.getStartPosition());
		}
		return false;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ThrowStatement)
	 */
	public boolean visit(ThrowStatement node) {
		rewriteRequiredNode(node.getExpression());		
		return false;	
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(TryStatement)
	 */
	public boolean visit(TryStatement node) {
		int pos= rewriteRequiredNode(node.getBody());
		
		List catchBlocks= node.catchClauses();
		if (hasChanges(catchBlocks)) {
			pos= getDefaultRewriter().rewriteList(catchBlocks, pos, " ", " "); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			pos= visitList(catchBlocks, pos);
		}
		Block finallyBlock= node.getFinally();
		if (finallyBlock != null) {
			rewriteNode(finallyBlock, pos, " finally "); //$NON-NLS-1$
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(TypeDeclarationStatement)
	 */
	public boolean visit(TypeDeclarationStatement node) {
		rewriteRequiredNode(node.getTypeDeclaration());	
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(TypeLiteral)
	 */
	public boolean visit(TypeLiteral node) {
		rewriteRequiredNode(node.getType());
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(VariableDeclarationExpression)
	 */
	public boolean visit(VariableDeclarationExpression node) {
		// same code as FieldDeclaration
		if (isModified(node)) {
			VariableDeclarationExpression modifedNode= (VariableDeclarationExpression) getModifiedNode(node);
			rewriteModifiers(node.getStartPosition(), node.getModifiers(), modifedNode.getModifiers(),getDescription(node));
		}
		
		Type type= node.getType();
		rewriteRequiredNode(type);
		
		List fragments= node.fragments();
		if (hasChanges(fragments)) {
			int startPos= getNextExistingStartPos(fragments, 0, type.getStartPosition() + type.getLength());
			getDefaultRewriter().rewriteList(fragments, startPos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			visitList(fragments, 0);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(VariableDeclarationFragment)
	 */
	public boolean visit(VariableDeclarationFragment node) {
		int pos= rewriteRequiredNode(node.getName());
		
		if (isModified(node)) {
			GroupDescription description= getDescription(node);
			VariableDeclarationFragment modifedNode= (VariableDeclarationFragment) getModifiedNode(node);
			rewriteExtraDimensions(node.getExtraDimensions(), modifedNode.getExtraDimensions(), pos, description);
		}			
		Expression initializer= node.getInitializer();
		if (initializer != null) {
			if (isRemoved(initializer)) {
				int dim= node.getExtraDimensions();
				if (dim > 0) {
					try {
						for (int i= 0; i < dim; i++) {
							pos= getScanner().getTokenEndOffset(ITerminalSymbols.TokenNameRBRACKET, pos);
						}
					} catch (CoreException e) {
						JavaPlugin.log(e);
					}
				}
			} else {
				pos= node.getStartPosition() + node.getLength(); // insert pos
			}
			rewriteNode(initializer, pos, " = "); //$NON-NLS-1$
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(VariableDeclarationStatement)
	 */
	public boolean visit(VariableDeclarationStatement node) {
		// same code as FieldDeclaration		
		if (isModified(node)) {
			VariableDeclarationStatement modifedNode= (VariableDeclarationStatement) getModifiedNode(node);
			rewriteModifiers(node.getStartPosition(), node.getModifiers(), modifedNode.getModifiers(), getDescription(node));
		}
		
		Type type= node.getType();
		rewriteRequiredNode(type);
		
		List fragments= node.fragments();
		if (hasChanges(fragments)) {
			int startPos= getNextExistingStartPos(fragments, 0, type.getStartPosition() + type.getLength());
			getDefaultRewriter().rewriteList(fragments, startPos, "", ", "); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			visitList(fragments, 0);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(WhileStatement)
	 */
	public boolean visit(WhileStatement node) {
		rewriteRequiredNode(node.getExpression());
		rewriteRequiredNode(node.getBody()); // body
		return false;
	}

}
