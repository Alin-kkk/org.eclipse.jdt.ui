package org.eclipse.jdt.internal.corext.refactoring.code;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.dom.Selection;

class NameCollector extends GenericVisitor {
	private List names= new ArrayList();
	private Selection fSelection;
	public NameCollector(ASTNode node) {
		fSelection= Selection.createFromStartLength(node.getStartPosition(), node.getLength());
	}
	protected boolean visitNode(ASTNode node) {
		if (node.getStartPosition() > fSelection.getInclusiveEnd())
			return true;
		if (fSelection.coveredBy(node))
			return true;
		return false;
	}
	public boolean visit(SimpleName node) {
		names.add(node.getIdentifier());
		return super.visit(node);
	}
	public boolean visit(VariableDeclarationStatement node) {
		return true;
	}
	public boolean visit(VariableDeclarationFragment node) {
		boolean result= super.visit(node);
		if (!result)
			names.add(node.getName().getIdentifier());
		return result;
	}
	public boolean visit(SingleVariableDeclaration node) {
		boolean result= super.visit(node);
		if (!result)
			names.add(node.getName().getIdentifier());
		return result;
	}
	public boolean visit(TypeDeclarationStatement node) {
		names.add(node.getTypeDeclaration().getName().getIdentifier());
		return false;
	}

    List getNames() {
        return names;
    }
}