/*
 * (c) Copyright 2002 IBM Corporation.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.dom;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;

/**
 * Maps a selection to a set of AST nodes.
 */
public class SelectionAnalyzer extends GenericVisitor {
	
	private Selection fSelection;
	private boolean fTraverseSelectedNode;
	private ASTNode fLastCoveringNode;
	
	// Selected nodes
	private List fSelectedNodes;
	
	public SelectionAnalyzer(Selection selection, boolean traverseSelectedNode) {
		Assert.isNotNull(selection);
		fSelection= selection;
		fTraverseSelectedNode= traverseSelectedNode;
	}
	
	public boolean hasSelectedNodes() {
		return fSelectedNodes != null && !fSelectedNodes.isEmpty();
	}
	
	public ASTNode[] getSelectedNodes() {
		if (fSelectedNodes == null || fSelectedNodes.isEmpty())
			return new ASTNode[0];
		return (ASTNode[]) fSelectedNodes.toArray(new ASTNode[fSelectedNodes.size()]);
	}
	
	public ASTNode getFirstSelectedNode() {
		if (fSelectedNodes == null || fSelectedNodes.isEmpty())
			return null;
		return (ASTNode)fSelectedNodes.get(0);
	}
	
	public ASTNode getLastSelectedNode() {
		if (fSelectedNodes == null || fSelectedNodes.isEmpty())
			return null;
		return (ASTNode)fSelectedNodes.get(fSelectedNodes.size() - 1);
	}
	
	public boolean isExpressionSelected() {
		if (!hasSelectedNodes())
			return false;
		return fSelectedNodes.get(0) instanceof Expression;
	}
	
	public TextRange getSelectedNodeRange() {
		if (fSelectedNodes == null || fSelectedNodes.isEmpty())
			return null;
		ASTNode firstNode= (ASTNode)fSelectedNodes.get(0);
		ASTNode lastNode= (ASTNode)fSelectedNodes.get(fSelectedNodes.size() - 1);
		return TextRange.createFromStartAndExclusiveEnd(firstNode.getStartPosition(), lastNode.getStartPosition() + lastNode.getLength());
	}
	
	public ASTNode getLastCoveringNode() {
		return fLastCoveringNode;
	}
	
	protected Selection getSelection() {
		return fSelection;
	}
	
	//--- node management ---------------------------------------------------------
	
	protected boolean visitNode(ASTNode node) {
		// The selection lies behind the node.
		if (fSelection.liesOutside(node)) {
			return false;
		} else if (fSelection.covers(node)) {
			if (isFirstNode()) {
				handleFirstSelectedNode(node);
			} else {
				handleNextSelectedNode(node);
			}
			return fTraverseSelectedNode;
		} else if (fSelection.coveredBy(node)) {
			fLastCoveringNode= node;
			return true;
		} else if (fSelection.endsIn(node)) {
			handleSelectionEndsIn(node);
			return false;
		}
		// There is a possibility that the user has selected trailing semicolons that don't belong
		// to the statement. So dive into it to check if sub nodes are fully covered.
		return true;
	}
	
	protected void reset() {
		fSelectedNodes= null;
	}
	
	protected void handleFirstSelectedNode(ASTNode node) {
		fSelectedNodes= new ArrayList(5);
		fSelectedNodes.add(node);
	}
	
	protected void handleNextSelectedNode(ASTNode node) {
		if (getFirstSelectedNode().getParent() == node.getParent()) {
			fSelectedNodes.add(node);
		}
	}

	protected void handleSelectionEndsIn(ASTNode node) {
	}
	
	protected List internalGetSelectedNodes() {
		return fSelectedNodes;
	}
	
	private boolean isFirstNode() {
		return fSelectedNodes == null;
	}	
}