/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.codemanipulation;

import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBufferEditor;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRegion;

/**
 * Deletes the text range specified by an AST nodes from a source file.
 */
public final class DeleteNodeEdit extends SimpleTextEdit {

	private boolean fDeleteLine;
	private int fDelimiterToken;

	public DeleteNodeEdit(ASTNode node, boolean deleteLine) {
		this(node.getStartPosition(), node.getLength(), deleteLine, ASTNodes.getDelimiterToken(node));
	}
	
	public DeleteNodeEdit(int start, int length, boolean deleteLine, int delimiterToken) {
		super(start, length, ""); //$NON-NLS-1$
		fDeleteLine= deleteLine;
		fDelimiterToken= delimiterToken;
	}

	private DeleteNodeEdit(TextRange range, boolean deleteLine) {
		super(range,""); //$NON-NLS-1$
		fDeleteLine= deleteLine;
	}
	
	/* non Java-doc
	 * @see TextEdit#connect(TextBufferEditor)
	 */
	public void connect(TextBufferEditor editor) throws CoreException {
		TextBuffer buffer= editor.getTextBuffer();
		TextRange range= getTextRange();
		TextRegion region;
		int startOffset= buffer.getLineInformationOfOffset(range.getOffset()).getOffset();
		region= buffer.getLineInformationOfOffset(range.getInclusiveEnd());
		int endOffset= region.getOffset() + region.getLength() - 1;		// inclusive.
		IScanner scanner= ToolFactory.createScanner(true, false, false, false);
		scanner.setSource(buffer.getContent(startOffset, endOffset - startOffset + 1).toCharArray());
		try {
			int start= fDeleteLine ? getStart(buffer, scanner, startOffset) : range.getOffset();
			int end= fDeleteLine ? getLineEnd(buffer, scanner, startOffset) : getEndIncludingDelimiter(buffer, scanner, startOffset);
			setTextRange(new TextRange(start, end - start));
		} catch (InvalidInputException e) {
			// Only delete node range
		}
	}

	/* non Java-doc
	 * @see TextEdit#copy()
	 */
	public TextEdit copy() throws CoreException {
		return new DeleteNodeEdit(getTextRange().copy(), this.fDeleteLine);
	}
	
	private int getStart(TextBuffer buffer, IScanner scanner, int startOffset) throws InvalidInputException {
		int relativeNodeStart= getTextRange().getOffset() - startOffset;
		scanner.resetTo(startOffset, relativeNodeStart - 1);
		int token;
		loop: while((token= scanner.getNextToken()) != ITerminalSymbols.TokenNameEOF) {
			switch (token) {
				case ITerminalSymbols.TokenNameCOMMENT_LINE:
				case ITerminalSymbols.TokenNameCOMMENT_BLOCK:
				case ITerminalSymbols.TokenNameCOMMENT_JAVADOC:
					continue loop;
				default:
					return getTextRange().getOffset();
			}
		}
		return startOffset;
	}
	
	private int getLineEnd(TextBuffer buffer, IScanner scanner, int startOffset) throws InvalidInputException {
		int result= getTextRange().getExclusiveEnd();
		final int sourceLength= scanner.getSource().length;
		scanner.resetTo(result - startOffset, sourceLength - 1);
		int token;
		while((token= scanner.getNextToken()) != ITerminalSymbols.TokenNameEOF) {
			if (token == ITerminalSymbols.TokenNameCOMMENT_LINE || token == ITerminalSymbols.TokenNameCOMMENT_BLOCK 
					|| token == ITerminalSymbols.TokenNameCOMMENT_JAVADOC || token == fDelimiterToken) {
				continue;
			} 
			return result;
		}
		// we can remove the entire line
		return startOffset + sourceLength+ buffer.getLineDelimiter(buffer.getLineOfOffset(result - 1)).length();
	}
	
	private int getEndIncludingDelimiter(TextBuffer buffer, IScanner scanner, int startOffset) throws InvalidInputException {
		int result= getTextRange().getExclusiveEnd();
		if (fDelimiterToken == -1)
			return result;
		scanner.resetTo(result - startOffset, scanner.getSource().length);
		int token;
		boolean delimiterFound= false;
		loop: while((token= scanner.getNextToken()) != ITerminalSymbols.TokenNameEOF) {
			if (token == ITerminalSymbols.TokenNameCOMMENT_LINE || token == ITerminalSymbols.TokenNameCOMMENT_BLOCK 
					|| token == ITerminalSymbols.TokenNameCOMMENT_JAVADOC) {
				if (delimiterFound) {
					return startOffset + scanner.getCurrentTokenStartPosition();
				}
				continue loop;
			} else if (token == fDelimiterToken) {
				delimiterFound= true;
				result= startOffset + scanner.getCurrentTokenEndPosition();
			} else {
				if (delimiterFound)
					result= startOffset + scanner.getCurrentTokenStartPosition();
				return result;
			}
		}
		return result;
	}	
}
