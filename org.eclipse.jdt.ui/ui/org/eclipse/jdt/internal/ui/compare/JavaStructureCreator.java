/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.compare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.SourceElementParser;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.InvalidInputException;
import org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.eclipse.jdt.internal.compiler.parser.TerminalSymbols;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.compare.IEditableContent;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.*;
import org.eclipse.compare.internal.DocumentManager;


public class JavaStructureCreator implements IStructureCreator {
	
	/**
	 * Used to bail out from ProblemFactory.
	 */
	private static class ParseError extends Error {
	}
	
	/**
	 * This problem factory aborts parsing on first error.
	 */
	static class ProblemFactory implements IProblemFactory {
		
		public IProblem createProblem(char[] originatingFileName, int problemId, String[] arguments, int severity, int startPosition, int endPosition, int lineNumber) {
			throw new ParseError();
		}
		
		public Locale getLocale() {
			return Locale.getDefault();
		}
		
		public String getLocalizedMessage(int problemId, String[] problemArguments) {
			return "" + problemId; //$NON-NLS-1$
		}
	}

	/**
	 * RewriteInfos are used temporarily when rewriting the diff tree
	 * in order to combine similar diff nodes ("smart folding").
	 */
	static class RewriteInfo {
		
		boolean fIsOut= false;
		
		JavaNode fAncestor= null;
		JavaNode fLeft= null;
		JavaNode fRight= null;
		
		ArrayList fChildren= new ArrayList();
		
		void add(IDiffElement diff) {
			fChildren.add(diff);
		}
		
		void setDiff(ICompareInput diff) {
			if (fIsOut)
				return;
			
			fIsOut= true;
			
			JavaNode a= (JavaNode) diff.getAncestor();
			JavaNode y= (JavaNode) diff.getLeft();
			JavaNode m= (JavaNode) diff.getRight();
			
			if (a != null) {
				if (fAncestor != null)
					return;
				fAncestor= a;
			}
			if (y != null) {
				if (fLeft != null)
					return;
				fLeft= y;
			}
			if (m != null) {
				if (fRight != null)
					return;
				fRight= m;
			}
			
			fIsOut= false;
		}
				
		/**
		 * Returns true if some nodes could be successfully combined into one.
		 */
		boolean matches() {
			return !fIsOut && fAncestor != null && fLeft != null && fRight != null;
		}
	}		
	
	public JavaStructureCreator() {
	}
	
	/**
	 * Returns the name that appears in the enclosing pane title bar.
	 */
	public String getName() {
		return CompareMessages.getString("JavaStructureViewer.title"); //$NON-NLS-1$
	}
	
	/**
	 * Returns a tree of JavaNodes for the given input
	 * which must implement the IStreamContentAccessor interface.
	 * In case of error null is returned.
	 */
	public IStructureComparator getStructure(final Object input) {
		String contents= null;
		char[] buffer= null;
		IDocument doc= DocumentManager.get(input);
		if (doc == null) {
			if (input instanceof IStreamContentAccessor) {
				IStreamContentAccessor sca= (IStreamContentAccessor) input;			
				try {
					contents= JavaCompareUtilities.readString(sca.getContents());
				} catch (CoreException ex) {
					JavaPlugin.log(ex);
					return null;
				}			
			}
			
			if (contents != null) {
				int n= contents.length();
				buffer= new char[n];
				contents.getChars(0, n, buffer, 0);
				
				doc= new Document(contents);
				DocumentManager.put(input, doc);
				IDocumentPartitioner dp= JavaCompareUtilities.createJavaPartitioner();
				if (dp != null) {
					doc.setDocumentPartitioner(dp);
					dp.connect(doc);
				}
				
			}
		}
		
		if (doc != null) {
			boolean isEditable= false;
			if (input instanceof IEditableContent)
				isEditable= ((IEditableContent) input).isEditable();
			
			// we hook into the root node to intercept all node changes
			JavaNode root= new JavaNode(doc, isEditable) {
				void nodeChanged(JavaNode node) {
					save(this, input);
				}
			};
			
			if (buffer == null) {
				contents= doc.get();
				int n= contents.length();
				buffer= new char[n];
				contents.getChars(0, n, buffer, 0);
			}
			JavaParseTreeBuilder builder= new JavaParseTreeBuilder(root, buffer);
			SourceElementParser parser= new SourceElementParser(builder,
						new ProblemFactory(), new CompilerOptions(JavaCore.getOptions()));
			try {
				parser.parseCompilationUnit(builder, false);
			} catch (ParseError ex) {
				// parse error: bail out
				return null;
			}
			return root;
		}
		return null;
	}
		
	/**
	 * Returns true because this IStructureCreator knows how to save.
	 */
	public boolean canSave() {
		return true;
	}
	
	public void save(IStructureComparator node, Object input) {
		if (node instanceof JavaNode && input instanceof IEditableContent) {
			IDocument document= ((JavaNode)node).getDocument();
			IEditableContent bca= (IEditableContent) input;
			bca.setContent(document.get().getBytes());
		}
	}
	
	/**
	 * Returns the contents of the given node as a string.
	 * This string is used to test the content of a Java element
	 * for equality. Is is never shown in the UI, so any string representing
	 * the content will do.
	 * @param node must implement the IStreamContentAccessor interface
	 * @param ignoreWhiteSpace if true all Java white space (incl. comments) is removed from the contents.
	 */
	public String getContents(Object node, boolean ignoreWhiteSpace) {
		
		if (! (node instanceof IStreamContentAccessor))
			return null;
			
		IStreamContentAccessor sca= (IStreamContentAccessor) node;
		String content= null;
		try {
			content= JavaCompareUtilities.readString(sca.getContents());
		} catch (CoreException ex) {
			JavaPlugin.log(ex);
			return null;
		}
				
		if (ignoreWhiteSpace) { 	// we return everything but Java whitespace
			
			// replace comments and whitespace by a single blank
			StringBuffer buf= new StringBuffer();
			char[] b= content.toCharArray();
			
			// to avoid the trouble when dealing with Unicode
			// we use the Java scanner to extract non-whitespace and non-comment tokens
			Scanner scanner= new Scanner(true, true);	// however we request Whitespace and Comments
			scanner.setSourceBuffer(b);
			try {
				int token;
				while ((token= scanner.getNextToken()) != TerminalSymbols.TokenNameEOF) {
					switch (token) {
					case Scanner.TokenNameWHITESPACE:
					case Scanner.TokenNameCOMMENT_BLOCK:
					case Scanner.TokenNameCOMMENT_JAVADOC:
					case Scanner.TokenNameCOMMENT_LINE:
						int l= buf.length();
						if (l > 0 && buf.charAt(l-1) != ' ')
							buf.append(' ');
						break;
					default:
						buf.append(b, scanner.startPosition, scanner.currentPosition - scanner.startPosition);
						buf.append(' ');
						break;
					}
				}
				content= buf.toString();	// success!
			} catch (InvalidInputException ex) {
			}
		}
		return content;
	}
	
	/**
	 * Returns true since this IStructureCreator can rewrite the diff tree
	 * in order to fold certain combinations of additons and deletions.
	 */
	public boolean canRewriteTree() {
		return true;
	}
	
	/**
	 * Tries to detect certain combinations of additons and deletions
	 * as renames or signature changes and foldes them into a single node.
	 */
	public void rewriteTree(Differencer differencer, IDiffContainer root) {
		
		HashMap map= new HashMap(10);
				
		Object[] children= root.getChildren();
		for (int i= 0; i < children.length; i++) {
			DiffNode diff= (DiffNode) children[i];
			JavaNode jn= (JavaNode) diff.getId();
			
			if (jn == null)
				continue;
			int type= jn.getTypeCode();
			
			// we can only combine methods or constructors
			if (type == JavaNode.METHOD || type == JavaNode.CONSTRUCTOR) {
				
				// find or create a RewriteInfo for all methods with the same name
				String name= jn.extractMethodName();
				RewriteInfo nameInfo= (RewriteInfo) map.get(name);
				if (nameInfo == null) {
					nameInfo= new RewriteInfo();
					map.put(name, nameInfo);
				}
				nameInfo.add(diff);
				
				// find or create a RewriteInfo for all methods with the same
				// (non-empty) argument list
				String argList= jn.extractArgumentList();
				RewriteInfo argInfo= null;
				if (argList != null && !argList.equals("()")) { //$NON-NLS-1$
					argInfo= (RewriteInfo) map.get(argList);
					if (argInfo == null) {
						argInfo= new RewriteInfo();
						map.put(argList, argInfo);
					}
					argInfo.add(diff);
				}
				
				switch (diff.getKind() & Differencer.CHANGE_TYPE_MASK) {
				case Differencer.ADDITION:
				case Differencer.DELETION:
					// we only consider addition and deletions
					// since a rename or arg list change looks
					// like a pair of addition and deletions
					if (type != JavaNode.CONSTRUCTOR)
						nameInfo.setDiff((ICompareInput)diff);
					
					if (argInfo != null)
						argInfo.setDiff((ICompareInput)diff);
					break;
				default:
					break;
				}
			}
			
			// recurse
			if (diff instanceof IDiffContainer)
				rewriteTree(differencer, (IDiffContainer)diff);
		}
		
		// now we have to rebuild the diff tree according to the combined
		// changes
		Iterator it= map.keySet().iterator();
		while (it.hasNext()) {
			String name= (String) it.next();
			RewriteInfo i= (RewriteInfo) map.get(name);
			if (i.matches()) { // we found a RewriteInfo that could be succesfully combined
				
				// we have to find the differences of the newly combined node
				// (because in the first pass we only got a deletion and an addition)
				DiffNode d= (DiffNode) differencer.findDifferences(true, null, root, i.fAncestor, i.fLeft, i.fRight);
				if (d != null) {// there better should be a difference
					d.setDontExpand(true);
					Iterator it2= i.fChildren.iterator();
					while (it2.hasNext()) {
						IDiffElement rd= (IDiffElement) it2.next();
						root.removeToRoot(rd);
						d.add(rd);
					}
				}
			}
		}
	}
	
	/**
	 * If selector is an IJavaElement this method tries to return an
	 * IStructureComparator object for it.
	 * In case of error or if the given selector cannot be found
	 * null is returned.
	 * @param selector the IJavaElement to extract
	 * @param input must implement the IStreamContentAccessor interface.
	 */
	public IStructureComparator locate(Object selector, Object input) {
		
		if (!(selector instanceof IJavaElement))
			return null;

		// try to build the JavaNode tree from input
		IStructureComparator structure= getStructure(input);
		if (structure == null)	// we couldn't parse the structure 
			return null;		// so we can't find anything
			
		// build a path
		String[] path= createPath((IJavaElement) selector);
			
		// find the path in the JavaNode tree
		return find(structure, path, 0);
	}
	
	private static String[] createPath(IJavaElement je) {
			
		// build a path starting at the given Java element and walk
		// up the parent chain until we reach a IWorkingCopy or ICompilationUnit
		List args= new ArrayList();
		while (je != null) {
			// each path component has a name that uses the same
			// conventions as a JavaNode name
			String name= JavaCompareUtilities.getJavaElementID(je);
			if (name == null)
				return null;
			args.add(name);
			if (je instanceof IWorkingCopy || je instanceof ICompilationUnit)
				break;
			je= je.getParent();
		}
		
		// revert the path
		int n= args.size();
		String[] path= new String[n];
		for (int i= 0; i < n; i++)
			path[i]= (String) args.get(n-1-i);
			
		return path;
	}
	
	/**
	 * Recursivly extracts the given path from the tree.
	 */
	private static IStructureComparator find(IStructureComparator tree, String[] path, int index) {
		if (tree != null) {
			Object[] children= tree.getChildren();
			if (children != null) {
				for (int i= 0; i < children.length; i++) {
					IStructureComparator child= (IStructureComparator) children[i];
					if (child instanceof ITypedElement && child instanceof DocumentRangeNode) {
						String n1= null;
						if (child instanceof DocumentRangeNode)
							n1= ((DocumentRangeNode)child).getId();
						if (n1 == null)
							n1= ((ITypedElement)child).getName();
						String n2= path[index];
						if (n1.equals(n2)) {
							if (index == path.length-1)
								return child;
							IStructureComparator result= find(child, path, index+1);
							if (result != null)
								return result;
						}	
					}
				}
			}
		}
		return null;
	}

	/**
	 * Returns true if the given IJavaElement maps to a JavaNode.
	 * The JavaHistoryAction uses this function to determine whether
	 * a selected Java element can be replaced by some piece of
	 * code from the local history.
	 */
	static boolean hasEdition(IJavaElement je) {

		if (je instanceof IMember && ((IMember)je).isBinary())
			return false;
			
		switch (je.getElementType()) {
		case JavaElement.COMPILATION_UNIT:
		case JavaElement.TYPE:
		case JavaElement.FIELD:
		case JavaElement.METHOD:
		case JavaElement.INITIALIZER:
		case JavaElement.PACKAGE_DECLARATION:
		case JavaElement.IMPORT_CONTAINER:
		case JavaElement.IMPORT_DECLARATION:
			return true;
		}
		return false;
	}
}
