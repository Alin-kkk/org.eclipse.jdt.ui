/*
 * (c) Copyright 2001 MyCorporation.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.base;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.corext.refactoring.SourceRange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry.Context;
import org.eclipse.jdt.internal.corext.util.Binding2JavaModel;

/**
 * A java element context can be used to annotate a </code>RefactoringStatusEntry<code> with
 * detailed information about an error detected in an <code>IJavaElement</code>
 */
public abstract class JavaSourceContext extends Context {

	private static class MemberSourceContext extends JavaSourceContext {
		private IMember fMember;
		private MemberSourceContext(IMember member) {
			fMember= member;
		}
		public boolean isBinary() {
			return fMember.isBinary();
		}
		public ICompilationUnit getCompilationUnit() {
			return fMember.getCompilationUnit();
		}
		public IClassFile getClassFile() {
			return fMember.getClassFile();
		}
		public ISourceRange getSourceRange() {
			try {
				return fMember.getSourceRange();
			} catch (JavaModelException e) {
				return new SourceRange(0,0);
			}
		}
	}
	
	private static class ImportDeclarationSourceContext extends JavaSourceContext {
		private IImportDeclaration fImportDeclartion;
		private ImportDeclarationSourceContext(IImportDeclaration declaration) {
			fImportDeclartion= declaration;
		}
		public boolean isBinary() {
			return false;
		}
		public ICompilationUnit getCompilationUnit() {
			return (ICompilationUnit)fImportDeclartion.getParent().getParent();
		}
		public IClassFile getClassFile() {
			return null;
		}
		public ISourceRange getSourceRange() {
			try {
				return fImportDeclartion.getSourceRange();
			} catch (JavaModelException e) {
				return new SourceRange(0,0);
			}
		}
	}
	
	private static class CompilationUnitSourceContext extends JavaSourceContext {
		private ICompilationUnit fCUnit;
		private ISourceRange fSourceRange;
		private CompilationUnitSourceContext(ICompilationUnit cunit, ISourceRange range) {
			fCUnit= cunit;
			fSourceRange= range;
			if (fSourceRange == null)
				fSourceRange= new SourceRange(0,0);
		}
		public boolean isBinary() {
			return false;
		}
		public ICompilationUnit getCompilationUnit() {
			return fCUnit;
		}
		public IClassFile getClassFile() {
			return null;
		}
		public ISourceRange getSourceRange() {
			return fSourceRange;
		}
	}

	/**
	 * Creates an status entry context for the given member
	 * 
	 * @param member the java member for which the context is supposed to be created
	 * @return the status entry context or <code>Context.NULL_CONTEXT</code> if the
	 * 	context cannot be created
	 */
	public static Context create(IMember member) {
		if (member == null || !member.exists())
			return NULL_CONTEXT;
		return new MemberSourceContext(member);
	}
	
	/**
	 * Creates an status entry context for the given import declaration
	 * 
	 * @param declaration the import declaration for which the context is supposed to be created
	 * @return the status entry context or <code>Context.NULL_CONTEXT</code> if the
	 * 	context cannot be created
	 */
	public static Context create(IImportDeclaration declaration) {
		if (declaration == null || !declaration.exists())
			return NULL_CONTEXT;
		return new ImportDeclarationSourceContext(declaration);
	}
	
	/**
	 * Creates an status entry context for the given compilation unit
	 * 
	 * @param cunit the compilation unit for which the context is supposed to be created
	 * @param range the source range that has caused the error
	 * @return the status entry context or <code>Context.NULL_CONTEXT</code> if the
	 * 	context cannot be created
	 */
	public static Context create(ICompilationUnit cunit, ISourceRange range) {
		if (cunit == null)
			return NULL_CONTEXT;
		return new CompilationUnitSourceContext(cunit, range);
	}

	/**
	 * Creates an status entry context for the given method binding
	 * 
	 * @param method the method binding for which the context is supposed to be created
	 * @param scope the Java project that is used to convert the method binding into a
	 * 	<code>IMethod</code>
	 * @return the status entry context or <code>Context.NULL_CONTEXT</code> if the
	 * 	context cannot be created
	 */
	public static Context create(MethodBinding method, IJavaProject scope) {
		ReferenceBinding declaringClass= method.declaringClass;
		IMethod mr= null;
		try {
			IType resource= Binding2JavaModel.find(declaringClass, scope);
			if (resource != null)
				mr= Binding2JavaModel.find(method, resource);
		} catch (JavaModelException e) {
		}
		return new MemberSourceContext(mr);
	}

	/**
	 * Returns whether this context is for a class file.
	 *
	 * @return <code>true</code> if from a class file, and <code>false</code> if
	 *   from a compilation unit
	 */
	public abstract boolean isBinary();
	
	/**
	 * Returns the compilation unit this context is working on. Returns <code>null</code>
	 * if the context is a binary context.
	 * 
	 * @return the compilation unit
	 */
	public abstract ICompilationUnit getCompilationUnit();
	
	/**
	 * Returns the class file this context is working on. Returns <code>null</code>
	 * if the context is not a binary context.
	 * 
	 * @return the class file
	 */
	public abstract IClassFile getClassFile();
	
	/**
	 * Returns the source range associated with this element.
	 *
	 * @return the source range
	 */
	public abstract ISourceRange getSourceRange();
}

