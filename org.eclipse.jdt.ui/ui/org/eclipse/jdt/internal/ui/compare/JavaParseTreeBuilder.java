/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.compare;

import java.util.Stack;

import org.eclipse.jface.text.IDocument;

import org.eclipse.jdt.internal.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ISourceElementRequestor;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;


class JavaParseTreeBuilder implements ISourceElementRequestor, ICompilationUnit {
	
	private static final boolean SHOW_COMPILATIONUNIT= true;

	private char[] fBuffer;
	private JavaNode fImportContainer;
	private Stack fStack= new Stack();
	
	
	JavaParseTreeBuilder() {
	}
	
	void init(JavaNode root, char[] buffer) {
		fImportContainer= null;
		fStack.clear();
		fStack.push(root);
		fBuffer= buffer;
	}
		
	//---- ICompilationUnit
	/**
	 * @see ICompilationUnit#getContents
	 */
	public char[] getContents() {
		return fBuffer;
	}
	
	/**
	 * @see ICompilationUnit#getFileName
	 */
	public char[] getFileName() {
		return new char[0];
	}
	
	/**
	 * @see ICompilationUnit#getMainTypeName
	 */
	public char[] getMainTypeName() {
		return new char[0];
	}
	
	/* (non Java doc)
	 * @see IcompilationUnit#getPackageName
	 */
	public char[][]getPackageName() {
		return null;
	}
	
	//---- ISourceElementRequestor
	
	public void enterCompilationUnit() {
		if (SHOW_COMPILATIONUNIT)
			push(JavaNode.CU, null, 0);
	}
	
	public void exitCompilationUnit(int declarationEnd) {
		if (SHOW_COMPILATIONUNIT)
			pop(declarationEnd);
	}
	
	public void acceptPackage(int declarationStart, int declarationEnd, char[] p3) {
		push(JavaNode.PACKAGE, null, declarationStart);
		pop(declarationEnd);
	}
	
	public void acceptImport(int declarationStart, int declarationEnd, char[] name, boolean onDemand) {
		int length= declarationEnd-declarationStart+1;
		if (fImportContainer == null)
			fImportContainer= new JavaNode(getCurrentContainer(), JavaNode.IMPORT_CONTAINER, null, getDocument(), declarationStart, length);
		String nm= new String(name);
		if (onDemand)
			nm+= ".*"; //$NON-NLS-1$
		new JavaNode(fImportContainer, JavaNode.IMPORT, nm, getDocument(), declarationStart, length);
		fImportContainer.setLength(declarationEnd-fImportContainer.getRange().getOffset()+1);
		fImportContainer.setAppendPosition(declarationEnd+2);		// FIXME
	}
	
	public void enterClass(int declarationStart, int p2, char[] name, int p4, int p5, char[] p6, char[][] p7) {
		push(JavaNode.CLASS, new String(name), declarationStart);
	}
	
	public void exitClass(int declarationEnd) {
		pop(declarationEnd);
	}

	public void enterInterface(int declarationStart, int p2, char[] name, int p4, int p5, char[][] p6) {
		push(JavaNode.INTERFACE, new String(name), declarationStart);
	}
	
	public void exitInterface(int declarationEnd) {
		pop(declarationEnd);
	}
	
	public void enterInitializer(int modifiers, int declarationSourceStart) {
		push(JavaNode.INIT, getCurrentContainer().getInitializerCount(), declarationSourceStart);
	}
	
	public void exitInitializer(int declarationSourceEnd) {
		pop(declarationSourceEnd);
	}
	
	public void enterConstructor(int declarationStart, int p2, char[] name, int p4, int p5, char[][] parameterTypes, char[][] p7, char[][] p8) {
		push(JavaNode.CONSTRUCTOR, getSignature(name, parameterTypes), declarationStart);
	}
	
	public void exitConstructor(int declarationEnd) {
		pop(declarationEnd);
	}
	
	public void enterMethod(int declarationStart, int p2, char[] p3, char[] name, int p5, int p6, char[][] parameterTypes, char[][] p8, char[][] p9){
		push(JavaNode.METHOD, getSignature(name, parameterTypes), declarationStart);
	}
	
	public void exitMethod(int declarationEnd) {
		pop(declarationEnd);
	}
	
	public void enterField(int declarationStart, int p2, char[] p3, char[] name, int p5, int p6) {
		push(JavaNode.FIELD, new String(name), declarationStart);
	}
	
	public void exitField(int declarationEnd) {
		pop(declarationEnd);
	}

	//-----

	public void acceptConstructorReference(char[] p1, int p2, int p3){
	}
	
	public void acceptFieldReference(char[] p1, int p2){
	}
	
	public void acceptLineSeparatorPositions(int[] p1){
	}
	
	public void acceptMethodReference(char[] p1, int p2, int p3){
	}
	
	public void acceptProblem(IProblem p1){
	}
	
	public void acceptTypeReference(char[][] p1, int p2, int p3){
	}
	
	public void acceptTypeReference(char[] p1, int p2){
	}
	
	public void acceptUnknownReference(char[][] p1, int p2, int p3){
	}
	
	public void acceptUnknownReference(char[] p1, int p2){
	}	
	
	//----
	
	private IDocument getDocument() {
		JavaNode top= (JavaNode) fStack.peek();
		return top.getDocument();
	}
	
	private JavaNode getCurrentContainer() {
		return (JavaNode) fStack.peek();
	}
	
	/**
	 * Adds a new JavaNode with the given type and name to the current container.
	 */
	private void push(int type, String name, int declarationStart) {
						
		while (declarationStart > 0) {
			char c= fBuffer[declarationStart-1];
			if (c != ' ' && c != '\t')
				break;
			declarationStart--;
		}
					
		fStack.push(new JavaNode(getCurrentContainer(), type, name, getDocument(), declarationStart, 0));
	}
	
	private void pop(int declarationEnd) {
		
		JavaNode current= getCurrentContainer();
		if (current.getTypeCode() == JavaNode.CU)
			current.setAppendPosition(declarationEnd+1);
		else
			current.setAppendPosition(declarationEnd);
			
		current.setLength(declarationEnd - current.getRange().getOffset() + 1);

		fStack.pop();
	}
	
	public String getSignature(char[] name, char[][] parameterTypes) {
		StringBuffer buffer= new StringBuffer();
		buffer.append(name);
		buffer.append('(');
		if (parameterTypes != null) {
			for (int p= 0; p < parameterTypes.length; p++) {
				buffer.append(parameterTypes[p]);
				if (p < parameterTypes.length-1)
					buffer.append(", "); //$NON-NLS-1$
			}
		}
		buffer.append(')');
		return buffer.toString();
	}
}

