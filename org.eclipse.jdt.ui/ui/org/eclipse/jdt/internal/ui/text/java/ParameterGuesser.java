/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContextType;

import org.eclipse.jdt.core.CompletionRequestorAdapter;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.template.contentassist.PositionBasedCompletionProposal;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;
import org.eclipse.jdt.internal.ui.util.StringMatcher;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
 
/**
 * This class triggers a code-completion that will track all local ane member variables for later
 * use as a parameter guessing proposal.
 * 
 * @author Andrew McCullough
 */
public class ParameterGuesser {
	
	private static final class PositionBasedJavaContext extends JavaContext {

		private Position fPosition;

		/**
		 * @param type
		 * @param document
		 * @param position
		 * @param compilationUnit
		 */
		public PositionBasedJavaContext(TemplateContextType type, IDocument document, Position position, ICompilationUnit compilationUnit) {
			super(type, document, position.getOffset(), position.getLength(), compilationUnit);
			fPosition= position;
		}
		
		/*
		 * @see org.eclipse.jdt.internal.corext.template.java.JavaContext#getStart()
		 */
		public int getStart() {
			return fPosition.getOffset();
		}
		
		/*
		 * @see org.eclipse.jdt.internal.corext.template.java.JavaContext#getEnd()
		 */
		public int getEnd() {
			return fPosition.getOffset() + fPosition.getLength();
		}
		
	}
	

	private static final class Variable {

		/**
		 * Variable type. Used to choose the best guess based on scope (Local beats instance beats inherited)
		 */
		public static final int LOCAL= 0;	
		public static final int FIELD= 1;
		public static final int METHOD= 1;
		public static final int INHERITED_FIELD= 3;
		public static final int INHERITED_METHOD= 3;

		public final String typePackage;
		public final String typeName;
		public final String name;
		public final int variableType;
		public final int positionScore;
		public boolean alreadyMatched;
		public char[] triggerChars;
		public ImageDescriptor descriptor;
		public boolean isAutoboxingMatch;
		
		/**
		 * Creates a variable.
		 */
		public Variable(String typePackage, String typeName, String name, int variableType, int positionScore, char[] triggers, ImageDescriptor descriptor) {
			this.typePackage= typePackage;
			this.typeName= typeName;
			this.name= name;
			this.variableType= variableType;
			this.positionScore= positionScore;
			triggerChars= triggers;
			this.descriptor= descriptor;
		}

		/*
		 * @see Object#toString()
		 */
		public String toString() {

			StringBuffer buffer= new StringBuffer();

			if (typePackage.length() != 0) {
				buffer.append(typePackage);
				buffer.append('.');
			}

			buffer.append(typeName);
			buffer.append(' ');
			buffer.append(name);
			buffer.append(" ("); //$NON-NLS-1$
			buffer.append(variableType);
			buffer.append(')');

			return buffer.toString();
		}
		
		String getFQN() {
			return ParameterGuesser.getFQN(typePackage, typeName);
		}
	}

	private static final class VariableCollector extends CompletionRequestorAdapter {

		/** The enclosing type name */
		private String fEnclosingTypeName;
		/** The local and member variables */
		private List fVars;

		public List collect(int codeAssistOffset, ICompilationUnit compilationUnit) throws JavaModelException {	
			Assert.isTrue(codeAssistOffset >= 0);
			Assert.isNotNull(compilationUnit);
	
			fVars= new ArrayList();			
					
			String source= compilationUnit.getSource();
			if (source == null)
				return fVars;
					
			fEnclosingTypeName= getEnclosingTypeName(codeAssistOffset, compilationUnit);
	
			// find some whitespace to start our variable-finding code complete from.
			// this allows the VariableTracker to find all available variables (no prefix to match for the code completion)				
			int completionOffset= getCompletionOffset(source, codeAssistOffset);
			
			compilationUnit.codeComplete(completionOffset, this);
			
			// add this, true, false
			int dotPos= fEnclosingTypeName.lastIndexOf('.');
			String thisType;
			String thisPkg;
			if (dotPos != -1) {
				thisType= fEnclosingTypeName.substring(dotPos + 1);
				thisPkg= fEnclosingTypeName.substring(0, dotPos);
			} else {
				thisPkg= new String();
				thisType= fEnclosingTypeName;
			}
			addVariable(Variable.FIELD, thisPkg.toCharArray(), thisType.toCharArray(), "this".toCharArray(), new char[] {'.'}, getFieldDescriptor(Flags.AccPublic | Flags.AccFinal)); //$NON-NLS-1$
			addVariable(Variable.FIELD, new char[0], "boolean".toCharArray(), "true".toCharArray(), new char[0], null);  //$NON-NLS-1$//$NON-NLS-2$
			addVariable(Variable.FIELD, new char[0], "boolean".toCharArray(), "false".toCharArray(), new char[0], null);  //$NON-NLS-1$//$NON-NLS-2$
			
			return fVars;
		}
		
		private static String getEnclosingTypeName(int codeAssistOffset, ICompilationUnit compilationUnit) throws JavaModelException {

			IJavaElement element= compilationUnit.getElementAt(codeAssistOffset);
			if (element == null)
				return null;
				
			element= element.getAncestor(IJavaElement.TYPE);
			if (element == null)
				return null;
				
			return element.getElementName();		
		}

		/**
		 * Determine if the declaring type matches the type of the code completion invocation
		 */
		private final boolean isInherited(String declaringTypeName) {
			return !declaringTypeName.equals(fEnclosingTypeName);
		}

		private void addVariable(int varType, char[] typePackageName, char[] typeName, char[] name, char[] triggers, ImageDescriptor descriptor) {
			fVars.add(new Variable(new String(typePackageName), new String(typeName), new String(name), varType, fVars.size(), triggers, descriptor));
		}

		/*
		 * @see ICompletionRequestor#acceptField(char[], char[], char[], char[], char[], char[], int, int, int, int)
		 */
		public void acceptField(char[] declaringTypePackageName, char[] declaringTypeName, char[] name,
			char[] typePackageName, char[] typeName, char[] completionName, int modifiers, int completionStart,
			int completionEnd, int relevance)
		{
			char[] triggers= new char[0];
			if (!isInherited(new String(declaringTypeName)))
				addVariable(Variable.FIELD, typePackageName, typeName, name, triggers, getFieldDescriptor(modifiers));
			else
				addVariable(Variable.INHERITED_FIELD, typePackageName, typeName, name, triggers, getFieldDescriptor(modifiers));
		}
	
		/*
		 * @see ICompletionRequestor#acceptLocalVariable(char[], char[], char[], int, int, int, int)
		 */
		public void acceptLocalVariable(char[] name, char[] typePackageName, char[] typeName, int modifiers,
			int completionStart, int completionEnd, int relevance)
		{
			char[] triggers= new char[0];
			addVariable(Variable.LOCAL, typePackageName, typeName, name, triggers, decorate(JavaPluginImages.DESC_OBJS_LOCAL_VARIABLE, modifiers));
		}
		
		/*
		 * @see org.eclipse.jdt.core.CompletionRequestorAdapter#acceptMethod(char[], char[], char[], char[][], char[][], char[][], char[], char[], char[], int, int, int, int)
		 */
		public void acceptMethod(char[] declaringTypePackageName, char[] declaringTypeName, char[] selector, char[][] parameterPackageNames, char[][] parameterTypeNames, char[][] parameterNames, char[] returnTypePackageName, char[] returnTypeName, char[] completionName, int modifiers, int completionStart, int completionEnd, int relevance) {
			// TODO: for now: only add zero-arg methods.
			if (parameterNames.length == 0) {
				char[] triggers= new char[0];
				addVariable(isInherited(new String(declaringTypeName)) ? Variable.INHERITED_METHOD : Variable.METHOD, returnTypePackageName, returnTypeName, completionName, triggers, getMemberDescriptor(modifiers));
			}
		}

		protected ImageDescriptor getMemberDescriptor(int modifiers) {
			ImageDescriptor desc= JavaElementImageProvider.getMethodImageDescriptor(false, modifiers);
			return decorate(desc, modifiers);
		}
	
		protected ImageDescriptor getFieldDescriptor(int modifiers) {
			ImageDescriptor desc= JavaElementImageProvider.getFieldImageDescriptor(false, modifiers);
			return decorate(desc, modifiers);
		}
		
		private ImageDescriptor decorate(ImageDescriptor descriptor, int modifiers) {
			int flags= 0;
			
			if (Flags.isDeprecated(modifiers))
				flags |= JavaElementImageDescriptor.WARNING;
		 	
			if (Flags.isStatic(modifiers))
				flags |= JavaElementImageDescriptor.STATIC;
		
			if (Flags.isFinal(modifiers))
				flags |= JavaElementImageDescriptor.FINAL;
			
			if (Flags.isSynchronized(modifiers))
				flags |= JavaElementImageDescriptor.SYNCHRONIZED;
			
			if (Flags.isAbstract(modifiers))
				flags |= JavaElementImageDescriptor.ABSTRACT;
			
			return new JavaElementImageDescriptor(descriptor, flags, JavaElementImageProvider.SMALL_SIZE);

		}
	}
	
	private static Map fgPrimitiveAssignments;
	private static Map fgAutounbox;
	
	static {
		HashMap primitiveAssignments= new HashMap();
		// put (LHS, RHS)
		primitiveAssignments.put("boolean", Collections.singleton("boolean")); //$NON-NLS-1$ //$NON-NLS-2$
		primitiveAssignments.put("byte", Collections.singleton("byte")); //$NON-NLS-1$ //$NON-NLS-2$
		primitiveAssignments.put("short", Collections.unmodifiableSet(new HashSet(Arrays.asList(new String[] {"short", "byte"})))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		primitiveAssignments.put("char", Collections.singleton("char")); //$NON-NLS-1$ //$NON-NLS-2$
		primitiveAssignments.put("int", Collections.unmodifiableSet(new HashSet(Arrays.asList(new String[] {"int", "short", "char", "byte"})))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		primitiveAssignments.put("long", Collections.unmodifiableSet(new HashSet(Arrays.asList(new String[] {"long", "int", "short", "char", "byte"})))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		primitiveAssignments.put("float", Collections.unmodifiableSet(new HashSet(Arrays.asList(new String[] {"float", "long", "int", "short", "char", "byte"})))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
		primitiveAssignments.put("double", Collections.unmodifiableSet(new HashSet(Arrays.asList(new String[] {"double", "float", "long", "int", "short", "char", "byte"})))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
		primitiveAssignments.put("primitive number", Collections.unmodifiableSet(new HashSet(Arrays.asList(new String[] {"double", "float", "long", "int", "short", "byte"})))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
		fgPrimitiveAssignments= Collections.unmodifiableMap(primitiveAssignments);

		HashMap autounbox= new HashMap();
		autounbox.put("java.lang.Boolean", "boolean"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Byte", "byte"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Short", "short"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Character", "char"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Integer", "int"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Long", "long"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Float", "float"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Double", "double"); //$NON-NLS-1$ //$NON-NLS-2$
		autounbox.put("java.lang.Number", "primitive number"); // dummy for reverse assignment //$NON-NLS-1$ //$NON-NLS-2$
		fgAutounbox= Collections.unmodifiableMap(autounbox);

	}
	
	/** The compilation unit we are computing the completion for */
	private final ICompilationUnit fCompilationUnit;
	/** The code assist offset. */
	private final int fCodeAssistOffset;
	/** Local and member variables of the compilation unit */
	private List fVariables;
	private ImageDescriptorRegistry fRegistry= JavaPlugin.getImageDescriptorRegistry();
	private boolean fIsTemplateMatch;
	private boolean fAllowAutoBoxing;

	/**
	 * Creates a parameter guesser for compilation unit and offset.
	 * 
	 * @param codeAssistOffset the offset at which to perform code assist
	 * @param compilationUnit the compilation unit in which code assist is performed
	 */
	public ParameterGuesser(int codeAssistOffset, ICompilationUnit compilationUnit) {
		Assert.isTrue(codeAssistOffset >= 0);
		Assert.isNotNull(compilationUnit);
		
		fCodeAssistOffset= codeAssistOffset;
		fCompilationUnit= compilationUnit;
		
		
		IJavaProject project= fCompilationUnit.getJavaProject();
		String sourceVersion= project == null 
				? JavaCore.getOption(JavaCore.COMPILER_SOURCE)
				: project.getOption(JavaCore.COMPILER_SOURCE, true);
		
		fAllowAutoBoxing= JavaCore.VERSION_1_5.compareTo(sourceVersion) <= 0;
	}
	
	/**
	 * Returns the offset at which code assist is performed.
	 */
	public int getCodeAssistOffset() {
		return fCodeAssistOffset;	
	}
	
	/**
	 * Returns the compilation unit in which code assist is performed.
	 */
	public ICompilationUnit getCompilationUnit() {
		return fCompilationUnit;	
	}
	
	/**
	 * Returns the matches for the type and name argument, ordered by match quality.
	 * 
	 * @param paramPackage - the package of the parameter we are trying to match
	 * @param paramType - the qualified name of the parameter we are trying to match
	 * @param paramName - the name of the parameter (used to find similarly named matches)
	 * @param pos
	 * @param document
	 * @return returns the name of the best match, or <code>null</code> if no match found
	 * @throws JavaModelException
	 */
	public ICompletionProposal[] parameterProposals(String paramPackage, String paramType, String paramName, Position pos, IDocument document) throws JavaModelException {
		
		if (fVariables == null) {
			VariableCollector variableCollector= new VariableCollector();
			fVariables= variableCollector.collect(fCodeAssistOffset, fCompilationUnit);
		}
		
		fIsTemplateMatch= false;
		
		List typeMatches= findFieldsMatchingType(fVariables, paramPackage, paramType);
		orderMatches(typeMatches, paramName);
		if (typeMatches == null)
			return new ICompletionProposal[0];
			
		ICompletionProposal[] ret= new ICompletionProposal[typeMatches.size() + (fIsTemplateMatch ? 1 : 0)];
		int i= 0; int replacementLength= 0;
		for (Iterator it= typeMatches.iterator(); it.hasNext();) {
			Variable v= (Variable)it.next();
			if (i == 0) {
				v.alreadyMatched= true;
				replacementLength= v.name.length();
			}
			
			// bump priority for collection-toArrays if there are no assignable arrays
			if (fIsTemplateMatch && !isArrayType(v.typeName)) {
				ret[i++]= createTemplateProposal(document, pos, paramType);
				fIsTemplateMatch= false;
			}
			
			final char[] triggers= new char[v.triggerChars.length + 1];
			System.arraycopy(v.triggerChars, 0, triggers, 0, v.triggerChars.length);
			String displayString= v.isAutoboxingMatch ? v.name : v.name;
			triggers[triggers.length - 1]= ';';
			ICompletionProposal proposal= new PositionBasedCompletionProposal(v.name, pos, replacementLength, getImage(v.descriptor), displayString, null, null) {
				public char[] getTriggerCharacters() {
					return triggers;
				}
			};
			ret[i++]= proposal;
		}

		if (fIsTemplateMatch) {
			ret[i++]= createTemplateProposal(document, pos, paramType);
		}
		
		return ret;
	}
	
	private TemplateProposal createTemplateProposal(IDocument document, Position position, String templateType) {
		String dimensionLess= templateType.substring(0, templateType.length() - 2);
		TemplateContextType contextType= JavaPlugin.getDefault().getTemplateContextRegistry().getContextType("java"); //$NON-NLS-1$
		
		position.offset= getCompletionOffset(document.get(), fCodeAssistOffset);
		JavaContext context= new PositionBasedJavaContext(contextType, document, position, fCompilationUnit);
		context.getCollections(); // force code completion at the completion offset...
		
		context.setForceEvaluation(true);
		context.setVariable("type", dimensionLess); //$NON-NLS-1$
		
		IRegion region= new Region(fCodeAssistOffset, 0);
		
		Template toArray= JavaPlugin.getDefault().getTemplateStore().findTemplate("toarray"); //$NON-NLS-1$
		TemplateProposal proposal= new TemplateProposal(toArray, context, region, JavaPluginImages.get(JavaPluginImages.IMG_OBJS_TEMPLATE));
		return proposal;
	}

	private static class MatchComparator implements Comparator {

		private String fParamName;

		MatchComparator(String paramName) {
			fParamName= paramName;
		}
		public int compare(Object o1, Object o2) {
			Variable one= (Variable)o1;
			Variable two= (Variable)o2;
			
			return score(two) - score(one);
		}
		
		/**
		 * The four order criteria as described below - put already used into bit 10, all others into
		 * bits 0-9, 11-20, 21-30; 31 is sign - always 0
		 * @param v
		 * @return the score for <code>v</code>
		 */
		private int score(Variable v) {
			int variableScore= 100 - v.variableType; // since these are increasing with distance
			int subStringScore= getLongestCommonSubstring(v.name, fParamName).length();
			// substring scores under 60% are not considered
			// this prevents marginal matches like a - ba and false - isBool that will
			// destroy the sort order
			int shorter= Math.min(v.name.length(), fParamName.length());
			if (subStringScore < 0.6 * shorter)
				subStringScore= 0;
				
			int positionScore= v.positionScore; // since ???
			int matchedScore= v.alreadyMatched ? 0 : 1;
			int autoboxingScore= v.isAutoboxingMatch ? 0 : 1;
			
			int score= autoboxingScore << 30 | variableScore << 21 | subStringScore << 11 | matchedScore << 10 | positionScore;
			return score;	
		}
		
	}

	/**
	 * Determine the best match of all possible type matches.  The input into this method is all 
	 * possible completions that match the type of the argument. The purpose of this method is to
	 * choose among them based on the following simple rules:
	 * 
	 * 	1) Local Variables > Instance/Class Variables > Inherited Instance/Class Variables
	 * 
	 * 	2) A longer case insensitive substring match will prevail
	 * 
	 *  3) Variables that have not been used already during this completion will prevail over 
	 * 		those that have already been used (this avoids the same String/int/char from being passed
	 * 		in for multiple arguments)
	 * 
	 * 	4) A better source position score will prevail (the declaration point of the variable, or
	 * 		"how close to the point of completion?"
	 */
	private static void orderMatches(List typeMatches, String paramName) {
		if (typeMatches != null) Collections.sort(typeMatches, new MatchComparator(paramName));
	}

	/**
	 * Finds a local or member variable that matched the type of the parameter
	 */
	private List findFieldsMatchingType(List variables, String typePackage, String typeName) throws JavaModelException {
		
		if (typeName == null || typeName.length() == 0)
			return null;
		
		// traverse the lists in reverse order, since it is empirically true that the code
		// completion engine returns variables in the order they are found -- and we want to find
		// matches closest to the code completion point.. No idea if this behavior is guaranteed.
		
		List matches= new ArrayList();
		
		boolean isArrayType= isArrayType(typeName);
		
		for (ListIterator iterator= variables.listIterator(variables.size()); iterator.hasPrevious(); ) {
			Variable variable= (Variable) iterator.previous();
			variable.isAutoboxingMatch= false;
			if (isTypeMatch(variable, typePackage, typeName))
				matches.add(variable);
			if (isArrayType && isAssignable(variable, "java.util", "Collection")) { //$NON-NLS-1$//$NON-NLS-2$
				fIsTemplateMatch= true;
				isArrayType= false;
			}
		}

		// add null proposal
		if (!isPrimitive(typeName.toCharArray()))
			matches.add(new Variable(typePackage, typeName, "null", Variable.FIELD, 2, new char[0], null)); //$NON-NLS-1$		
				
		return matches.isEmpty() ? null : matches;
	}

	private boolean isArrayType(String typeName) {
		// check for an exact match (fast)
		return typeName.endsWith("[]"); //$NON-NLS-1$
	}

	/**
	 * Return true if variable is a match for the given type.  This method will search the SuperTypeHierarchy
	 * of the vairable's type and see if the argument's type is included. This method is approximately 
	 * the same as:  if (argumentVar.getClass().isAssignableFrom(field.getClass()))
	 */
	private boolean isTypeMatch(Variable variable, String typePackage, String typeName) throws JavaModelException {
		
		// this should look at fully qualified name, but currently the CompletionEngine is not
		// sending the package names...
		
		// if there is no package specified, do the check on type name only.  This will work for primitives
		// and for local variables that cannot be resolved.
		
		if (typePackage == null || variable.typePackage == null || 
			typePackage.length() == 0 || variable.typePackage.length() == 0) {
			
			if (variable.typeName.equals(typeName))
				return true;
			
			if (isPrimitiveAssignable(typeName, variable.typeName))
				return true;
			
			if (fAllowAutoBoxing && isAutoBoxingAssignable(variable, typePackage, typeName)) {
				variable.isAutoboxingMatch= true;
				return true;
			}
		} 
		
		// if we get to here, we're doing a "fully qualified match" -- meaning including packages, no primitives
		// and no unresolved variables.  
			
		// if there is an exact textual match, there is no need to search type hierarchy.. this is
		// a quick way to pick up an exact match.
		if (variable.typeName.equals(typeName) && variable.typePackage.equals(typePackage))
			return true;
		
		// otherwise, we get a match/no match by searching the type hierarchy	
		return isAssignable(variable, typePackage, typeName);
	}

	private boolean isAutoBoxingAssignable(Variable variable, String typePackage, String typeName) {
		// autounbox variable to match primitive parameter
		if ((typePackage == null || typePackage.length() == 0) && typeName != null) {
			
			String unboxedVariable= getAutoUnboxedType(variable.getFQN());
			return isPrimitiveAssignable(typeName, unboxedVariable);
		}

		// variable is primitive, autobox to match parameter type
		if ((variable.typePackage == null || variable.typePackage.length() == 0) && variable.typeName != null) {
			String unboxedType= getAutoUnboxedType(getFQN(typePackage, typeName));
			return isPrimitiveAssignable(unboxedType, variable.typeName);
		}
		
		return false;
	}
	
	private boolean isPrimitiveAssignable(String lhs, String rhs) {
		Set targets= (Set) fgPrimitiveAssignments.get(lhs);
		return targets != null && targets.contains(rhs);
	}

	private String getAutoUnboxedType(String type) {
		String primitive= (String) fgAutounbox.get(type);
		return primitive; 
	}

	/**
	 * Returns true if variable is assignable to a type, false otherwise.
	 */
	private boolean isAssignable(Variable variable, String typePackage, String typeName) throws JavaModelException {
		IJavaProject project= fCompilationUnit.getJavaProject();
		IType paramType= project.findType(getFQN(typePackage, typeName));
		IType varType= project.findType(variable.getFQN());
		if (varType == null || paramType == null)
			return false;

		ITypeHierarchy hierarchy= SuperTypeHierarchyCache.getTypeHierarchy(varType);
		return hierarchy.contains(paramType);
	}

	/**
	 * Returns the longest common substring of two strings.
	 */
	private static String getLongestCommonSubstring(String first, String second) {
		
		String shorter= (first.length() <= second.length()) ? first : second;
		String longer= shorter == first ? second : first;

		int minLength= shorter.length();

		StringBuffer pattern= new StringBuffer(shorter.length() + 2);	
		String longestCommonSubstring= ""; //$NON-NLS-1$
		
		for (int i= 0; i < minLength; i++) {
			for (int j= i + 1; j <= minLength; j++) {				
				if (j - i < longestCommonSubstring.length())
					continue;
				
				String substring= shorter.substring(i, j);
				pattern.setLength(0);
				pattern.append('*');
				pattern.append(substring);
				pattern.append('*');
				
				StringMatcher matcher= new StringMatcher(pattern.toString(), true, false);
				if (matcher.match(longer))
					longestCommonSubstring= substring;
			}
		}
	
		return longestCommonSubstring;
	}

	private Image getImage(ImageDescriptor descriptor) {
		return (descriptor == null) ? null : fRegistry.get(descriptor);
	}
	
	/**
	 * Returns <code>true</code> if <code>typeName</code> is the name of a primitive type.
	 * 
	 * @param typeName the type to check
	 * @return <code>true</code> if <code>typeName</code> is the name of a primitive type
	 */
	private static boolean isPrimitive(char[] typeName) {
		String s= new String(typeName);
		return "boolean".equals(s) || "byte".equals(s) || "short".equals(s) || "int".equals(s) || "long".equals(s) || "float".equals(s) || "double".equals(s) || "char".equals(s); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
	}

	private static int getCompletionOffset(String source, int start) {
		int index= start;
		char c;
		while (index > 0 && (c= source.charAt(index - 1)) != '{' && c != ';')
			index--;
		return Math.min(index + 1, source.length());
	}
	
	private static String getFQN(String typePackage, String typeName) {
		StringBuffer buffer= new StringBuffer();

		if (typePackage.length() != 0) {
			buffer.append(typePackage);
			buffer.append('.');
		}

		buffer.append(typeName);
		return buffer.toString();
	}
}
