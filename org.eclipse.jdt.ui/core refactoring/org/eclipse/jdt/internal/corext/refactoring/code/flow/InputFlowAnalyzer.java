/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.code.flow;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;

public class InputFlowAnalyzer extends FlowAnalyzer {
	
	private static class LoopReentranceVisitor extends FlowAnalyzer {
		private Selection fSelection;
		private ASTNode fLoopNode;
		public LoopReentranceVisitor(FlowContext context, Selection selection, ASTNode loopNode) {
			super(context);
			fSelection= selection;
			fLoopNode= loopNode;
		}
		protected boolean traverseNode(ASTNode node) {
			return true; // end <= fSelection.end || fSelection.enclosedBy(start, end);	
		}
		protected boolean createReturnFlowInfo(ReturnStatement node) {
			// Make sure that the whole return statement is selected or located before the selection.
			return node.getStartPosition() + node.getLength() <= fSelection.getExclusiveEnd();
		}	
		protected ASTNode getLoopNode() {
			return fLoopNode;
		}
		public void process(ASTNode node) {
			fFlowContext.setLoopReentranceMode(true);	
			node.accept(this);
			fFlowContext.setLoopReentranceMode(false);
		}
		public void endVisit(DoStatement node) {
			if (skipNode(node))
				return;
			DoWhileFlowInfo info= createDoWhile();
			setFlowInfo(node, info);
			info.mergeAction(getFlowInfo(node.getBody()), fFlowContext);
			// No need to merge the condition. It was already considered by the InputFlowAnalyzer.
			info.removeLabel(null);	
		}
		public void endVisit(ForStatement node) {
			if (skipNode(node))
				return;
			FlowInfo initInfo= createSequential(node.initializers());
			FlowInfo conditionInfo= getFlowInfo(node.getExpression());
			FlowInfo incrementInfo= createSequential(node.updaters());
			FlowInfo actionInfo= getFlowInfo(node.getBody());
			ForFlowInfo forInfo= createFor();
			setFlowInfo(node, forInfo);
			// the for statement is the outermost loop. In this case we only have
			// to consider the increment, condition and action.
			if (node == fLoopNode) {
				forInfo.mergeIncrement(incrementInfo, fFlowContext);
				forInfo.mergeCondition(conditionInfo, fFlowContext);
				forInfo.mergeAction(actionInfo, fFlowContext);
				forInfo.removeLabel(null);
			} else {
				// we have to merge two different cases. One if we reenter the for statement
				// immediatelly (that means we have to consider increments, condition and action
				// ) and the other case if we reenter the for in the next loop of
				// the outer loop. Then we have to consider initializations, condtion and action.
				// For a conditional flow info that means:
				// (initializations | increments) & condition & action.
				GenericConditionalFlowInfo initIncr= new GenericConditionalFlowInfo();
				initIncr.merge(initInfo, fFlowContext);
				initIncr.merge(incrementInfo, fFlowContext);
				forInfo.mergeAccessModeSequential(initIncr, fFlowContext);
				forInfo.mergeCondition(conditionInfo, fFlowContext);
				forInfo.mergeAction(actionInfo, fFlowContext);
			}
			forInfo.removeLabel(null);
		}
	}
	
	private Selection fSelection;
	private LoopReentranceVisitor fLoopReentranceVisitor;

	public InputFlowAnalyzer(FlowContext context, Selection selection) {
		super(context);
		fSelection= selection;
		Assert.isNotNull(fSelection);
	}

	public FlowInfo perform(MethodDeclaration method) {
		method.accept(this);
		return getFlowInfo(method);
	}
	
	protected boolean traverseNode(ASTNode node) {
		return node.getStartPosition() + node.getLength() > fSelection.getInclusiveEnd();
	}
	
	protected boolean createReturnFlowInfo(ReturnStatement node) {
		// Make sure that the whole return statement is located after the selection. There can be cases like
		// return i + [x + 10] * 10; In this case we must not create a return info node.		
		return node.getStartPosition() >= fSelection.getInclusiveEnd();
	}
	
	public boolean visit(DoStatement node) {
		createLoopReentranceVisitor(node);
		return super.visit(node);			
	}
	
	public boolean visit(ForStatement node) {
		createLoopReentranceVisitor(node);
		return super.visit(node);			
	}
	
	public boolean visit(WhileStatement node) {
		createLoopReentranceVisitor(node);
		return super.visit(node);			
	}
	
	private void createLoopReentranceVisitor(ASTNode node) {
		if (fLoopReentranceVisitor == null)
			fLoopReentranceVisitor= new LoopReentranceVisitor(fFlowContext, fSelection, node);
	}
	
	public void endVisit(ConditionalExpression node) {
		if (skipNode(node))
			return;
		Expression thenPart= node.getThenExpression();
		Expression elsePart= node.getElseExpression();
		if ((thenPart != null && fSelection.coveredBy(thenPart)) ||
				(elsePart != null && fSelection.coveredBy(elsePart))) {
			GenericSequentialFlowInfo info= createSequential();
			setFlowInfo(node, info);
			endVisitConditional(info, node.getExpression(), new ASTNode[] {thenPart, elsePart});
		} else {
			super.endVisit(node);
		}
	}
	
	public void endVisit(DoStatement node) {
		super.endVisit(node);
		handleLoopReentrance(node);
	}

	public void endVisit(IfStatement node) {
		if (skipNode(node))
			return;
		Statement thenPart= node.getThenStatement();
		Statement elsePart= node.getElseStatement();
		if ((thenPart != null && fSelection.coveredBy(thenPart)) || 
				(elsePart != null && fSelection.coveredBy(elsePart))) {
			GenericSequentialFlowInfo info= createSequential();
			setFlowInfo(node, info);
			endVisitConditional(info, node.getExpression(), new ASTNode[] {thenPart, elsePart});
		} else {
			super.endVisit(node);
		}
	}
	
	public void endVisit(ForStatement node) {
		super.endVisit(node);
		handleLoopReentrance(node);
	}
	
	public void endVisit(SwitchStatement node) {
		if (skipNode(node))
			return;
		SwitchData data= createSwitchData(node);
		TextRange[] ranges= data.getRanges();
		for (int i= 0; i < ranges.length; i++) {
			TextRange range= ranges[i];
			if (fSelection.coveredBy(range)) {
				GenericSequentialFlowInfo info= createSequential();
				setFlowInfo(node, info);
				info.merge(getFlowInfo(node.getExpression()), fFlowContext);
				info.merge(data.getInfo(i), fFlowContext);
				info.removeLabel(null);
				return;
			}
		}
		super.endVisit(node, data);
	}
	
	public void endVisit(WhileStatement node) {
		super.endVisit(node);
		handleLoopReentrance(node);
	}
	
	private void endVisitConditional(GenericSequentialFlowInfo info, ASTNode condition, ASTNode[] branches) {
		info.merge(getFlowInfo(condition), fFlowContext);
		for (int i= 0; i < branches.length; i++) {
			ASTNode branch= branches[i];
			if (branch != null && fSelection.coveredBy(branch)) {
				info.merge(getFlowInfo(branch), fFlowContext);
				break;
			}
		}
	}
	
	private void handleLoopReentrance(ASTNode node) {
		if (!fSelection.enclosedBy(node) || fLoopReentranceVisitor.getLoopNode() != node)
			return;
		
		fLoopReentranceVisitor.process(node);
		GenericSequentialFlowInfo info= createSequential();
		info.merge(getFlowInfo(node), fFlowContext);
		info.merge(fLoopReentranceVisitor.getFlowInfo(node), fFlowContext);
		setFlowInfo(node, info);
	}
}