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
package org.eclipse.jdt.internal.corext.template.java;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.template.ContextType;
import org.eclipse.jdt.internal.corext.template.ContextTypeRegistry;
import org.eclipse.jdt.internal.corext.template.TemplateContext;
import org.eclipse.jdt.internal.corext.template.TemplatePosition;
import org.eclipse.jdt.internal.corext.template.TemplateVariable;

/**
  */
public class CodeTemplateContextType extends ContextType {
	
	public static final String CATCHBLOCK_CONTEXTTYPE= "catchblock_context"; //$NON-NLS-1$
	public static final String METHODBODY_CONTEXTTYPE= "methodbody_context"; //$NON-NLS-1$
	public static final String CONSTRUCTORBODY_CONTEXTTYPE= "constructorbody_context"; //$NON-NLS-1$
	public static final String GETTERBODY_CONTEXTTYPE= "getterbody_context"; //$NON-NLS-1$
	public static final String SETTERBODY_CONTEXTTYPE= "setterbody_context"; //$NON-NLS-1$
	public static final String NEWTYPE_CONTEXTTYPE= "newtype_context"; //$NON-NLS-1$
	public static final String TYPECOMMENT_CONTEXTTYPE= "typecomment_context"; //$NON-NLS-1$
	public static final String METHODCOMMENT_CONTEXTTYPE= "methodcomment_context"; //$NON-NLS-1$
	public static final String CONSTRUCTORCOMMENT_CONTEXTTYPE= "constructorcomment_context"; //$NON-NLS-1$
	public static final String OVERRIDECOMMENT_CONTEXTTYPE= "overridecomment_context"; //$NON-NLS-1$

	public static final String EXCEPTION_TYPE= "exception_type"; //$NON-NLS-1$
	public static final String EXCEPTION_VAR= "exception_var"; //$NON-NLS-1$
	public static final String ENCLOSING_METHOD= "enclosing_method"; //$NON-NLS-1$
	public static final String ENCLOSING_TYPE= "enclosing_type"; //$NON-NLS-1$
	public static final String BODY_STATEMENT= "body_statement"; //$NON-NLS-1$
	public static final String FIELD= "field"; //$NON-NLS-1$
	public static final String PARAM= "param"; //$NON-NLS-1$
	public static final String RETURN_TYPE= "return_type"; //$NON-NLS-1$
	public static final String SEE_TAG= "see_to_overridden"; //$NON-NLS-1$
	
	public static final String TAGS= "tags"; //$NON-NLS-1$
	
	public static final String TYPENAME= "type_name"; //$NON-NLS-1$
	public static final String FILENAME= "file_name"; //$NON-NLS-1$
	public static final String PACKAGENAME= "package_name"; //$NON-NLS-1$
	public static final String PROJECTNAME= "project_name"; //$NON-NLS-1$

	public static final String PACKAGE_DECLARATION= "package_declaration"; //$NON-NLS-1$
	public static final String TYPE_DECLARATION= "type_declaration"; //$NON-NLS-1$
	public static final String TYPE_COMMENT= "typecomment"; //$NON-NLS-1$
	
	public static class CodeTemplateVariable extends TemplateVariable {
		public CodeTemplateVariable(String name, String description) {
			super(name, description);
		}
		
		public String evaluate(TemplateContext context) {
			return context.getVariable(getName());
		}
	}
	
	public static class TagsTemplateVariable extends TemplateVariable {
		public TagsTemplateVariable() {
			super(TAGS,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.tags")); //$NON-NLS-1$
		}
		
		public String evaluate(TemplateContext context) {
			return "@"; //$NON-NLS-1$
		}
	}	
		
	protected static class Todo extends TemplateVariable {

		public Todo() {
			super("todo", JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.todo")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		public String evaluate(TemplateContext context) {
			String todoTaskTag= StubUtility.getTodoTaskTag(((CodeTemplateContext) context).getJavaProject());
			if (todoTaskTag == null)
				return "XXX"; //$NON-NLS-1$
	
			return todoTaskTag;
		}
	}
	
	private IScanner fScanner;
	
	
	public CodeTemplateContextType(String contextName) {
		super(contextName);
		
		// global
		addVariable(new GlobalVariables.Dollar());
		addVariable(new GlobalVariables.Date());
		addVariable(new GlobalVariables.Year());
		addVariable(new GlobalVariables.Time());
		addVariable(new GlobalVariables.User());
		addVariable(new Todo());
		
		if (CATCHBLOCK_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(EXCEPTION_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.exceptiontype"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(EXCEPTION_VAR,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.exceptionvar"))); //$NON-NLS-1$
		} else if (METHODBODY_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(ENCLOSING_METHOD,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingmethod"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(BODY_STATEMENT,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.bodystatement"))); //$NON-NLS-1$
		} else if (CONSTRUCTORBODY_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(BODY_STATEMENT,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.bodystatement"))); //$NON-NLS-1$
		} else if (GETTERBODY_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(ENCLOSING_METHOD,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingmethod"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(FIELD, JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.field"))); //$NON-NLS-1$
		} else if (SETTERBODY_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(ENCLOSING_METHOD,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingmethod"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(FIELD, JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.field"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(PARAM, JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.param"))); //$NON-NLS-1$
		} else if (NEWTYPE_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(TYPENAME,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.typename"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(PACKAGE_DECLARATION,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.packdeclaration"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(TYPE_DECLARATION,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.typedeclaration"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(TYPE_COMMENT,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.typecomment"))); //$NON-NLS-1$
			addCompilationUnitVariables();
		} else if (TYPECOMMENT_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(TYPENAME,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.typename"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new TagsTemplateVariable());
			addCompilationUnitVariables();
		} else if (METHODCOMMENT_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(ENCLOSING_METHOD,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingmethod"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(RETURN_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.returntype"))); //$NON-NLS-1$
			addVariable(new TagsTemplateVariable());
			addCompilationUnitVariables();
		} else if (OVERRIDECOMMENT_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(ENCLOSING_METHOD,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingmethod"))); //$NON-NLS-1$
			addVariable(new CodeTemplateVariable(SEE_TAG,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.seetag"))); //$NON-NLS-1$
			addVariable(new TagsTemplateVariable());
			addCompilationUnitVariables();
		} else if (CONSTRUCTORCOMMENT_CONTEXTTYPE.equals(contextName)) {
			addVariable(new CodeTemplateVariable(ENCLOSING_TYPE,  JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.enclosingtype"))); //$NON-NLS-1$
			addVariable(new TagsTemplateVariable());
			addCompilationUnitVariables();
		}
	}
	
	private void addCompilationUnitVariables() {
		addVariable(new CodeTemplateVariable(FILENAME, JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.filename"))); //$NON-NLS-1$
		addVariable(new CodeTemplateVariable(PACKAGENAME, JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.packagename"))); //$NON-NLS-1$
		addVariable(new CodeTemplateVariable(PROJECTNAME, JavaTemplateMessages.getString("CodeTemplateContextType.variable.description.projectname"))); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.template.ContextType#validateVariables(org.eclipse.jdt.internal.corext.template.TemplatePosition[])
	 */
	protected String validateVariables(TemplatePosition[] variables) {
		ArrayList required=  new ArrayList(5);
		String contextName= getName();
		if (NEWTYPE_CONTEXTTYPE.equals(contextName)) {
			required.add(PACKAGE_DECLARATION);
			required.add(TYPE_DECLARATION);
		}
		for (int i= 0; i < variables.length; i++) {
			String var= variables[i].getName();
			if (getVariable(var) == null) {
				return JavaTemplateMessages.getFormattedString("CodeTemplateContextType.validate.unknownvariable", var); //$NON-NLS-1$
			}
			required.remove(var);
		}
		if (!required.isEmpty()) {
			String missing= (String) required.get(0);
			return JavaTemplateMessages.getFormattedString("CodeTemplateContextType.validate.missingvariable", missing); //$NON-NLS-1$
		}
		return super.validateVariables(variables);
	}	
	
	

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.template.ContextType#createContext()
	 */
	public TemplateContext createContext() {
		return null;
	}

	public static void registerContextTypes(ContextTypeRegistry registry) {
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.CATCHBLOCK_CONTEXTTYPE));
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.METHODBODY_CONTEXTTYPE));
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.CONSTRUCTORBODY_CONTEXTTYPE));
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.GETTERBODY_CONTEXTTYPE));
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.SETTERBODY_CONTEXTTYPE));		
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.NEWTYPE_CONTEXTTYPE));
		
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.TYPECOMMENT_CONTEXTTYPE));
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.METHODCOMMENT_CONTEXTTYPE));
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.CONSTRUCTORCOMMENT_CONTEXTTYPE));
		registry.add(new CodeTemplateContextType(CodeTemplateContextType.OVERRIDECOMMENT_CONTEXTTYPE));		
	}
	
	

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.template.ContextType#validate(java.lang.String)
	 */
	public String validate(String pattern) throws CoreException {
		String message= super.validate(pattern);
		if (message != null) {
			return message;
		}
		String contextName= getName();
		if (METHODCOMMENT_CONTEXTTYPE.equals(contextName) || CONSTRUCTORCOMMENT_CONTEXTTYPE.equals(contextName) 
				|| TYPECOMMENT_CONTEXTTYPE.equals(contextName) || OVERRIDECOMMENT_CONTEXTTYPE.equals(contextName)) {
			if (!isValidComment(pattern)) {
				return JavaTemplateMessages.getString("CodeTemplateContextType.validate.invalidcomment"); //$NON-NLS-1$
			}
		}
		return null;
	}
	
	private IScanner getScanner() {
		if (fScanner == null) {
			fScanner= ToolFactory.createScanner(true, false, false, false);
		}
		return fScanner;
	}
	
	
	private boolean isValidComment(String template) {
		IScanner scanner= getScanner();
		scanner.setSource(template.toCharArray());
		try {
			int next= scanner.getNextToken();
			while (next == ITerminalSymbols.TokenNameCOMMENT_LINE || next == ITerminalSymbols.TokenNameCOMMENT_JAVADOC || next == ITerminalSymbols.TokenNameCOMMENT_BLOCK) {
				next= scanner.getNextToken();
			}
			return next == ITerminalSymbols.TokenNameEOF;
		} catch (InvalidInputException e) {
		}
		return false;
	}	

}
