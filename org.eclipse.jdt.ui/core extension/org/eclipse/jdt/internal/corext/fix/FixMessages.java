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
package org.eclipse.jdt.internal.corext.fix;

import org.eclipse.osgi.util.NLS;

public class FixMessages extends NLS {
	private static final String BUNDLE_NAME= "org.eclipse.jdt.internal.corext.fix.FixMessages"; //$NON-NLS-1$

	private FixMessages() {
	}

	public static String CleanUpRefactoring_Refactoring_name;
	public static String CleanUpRefactoring_ProcessingCompilationUnit_message;
	public static String CleanUpRefactoring_Initialize_message;
	
	public static String UnusedCodeFix_RemoveFieldOrLocal_description;
	public static String UnusedCodeFix_RemoveMethod_description;
	public static String UnusedCodeFix_RemoveConstructor_description;
	public static String UnusedCodeFix_RemoveType_description;
	public static String UnusedCodeFix_RemoveImport_description;
	public static String UnusedCodeFix_RemoveCast_description;
	
	public static String Java50Fix_AddMissingAnnotation_description;
	public static String Java50Fix_AddDeprecated_description;
	public static String Java50Fix_AddOverride_description;
	public static String Java50Fix_ConvertToEnhancedForLoop_description;
	public static String Java50Fix_ParametrizeTypeReference_description;
	public static String Java50Fix_AddTypeParameters_description;
	public static String Java50Fix_SerialVersionNotInitialized_exception_description;
	public static String Java50Fix_SerialVersionNotFound_exception_description;
	public static String Java50Fix_SerialVersion_default_description;
	public static String Java50Fix_SerialVersion_hash_description;
	public static String Java50Fix_InitializeSerialVersionId_subtask_description;
	public static String Java50Fix_SerialVersion_CalculateHierarchy_description;
	
	public static String StringFix_AddRemoveNonNls_description;
	public static String StringFix_AddNonNls_description;
	public static String StringFix_RemoveNonNls_description;
	
	public static String CodeStyleFix_ChangeAccessToStatic_description;
	public static String CodeStyleFix_AddThisQualifier_description;
	public static String CodeStyleFix_QualifyWithThis_description;
	public static String CodeStyleFix_ChangeAccessToStaticUsingInstanceType_description;
	public static String CodeStyleFix_ChangeStaticAccess_description;
	public static String CodeStyleFix_ChangeIfToBlock_desription;
	public static String CodeStyleFix_ChangeElseToBlock_description;
	public static String CodeStyleFix_ChangeControlToBlock_description;
	public static String CodeStyleFix_removeThis_groupDescription;
	
	public static String SerialVersion_group_description;
	
	public static String ControlStatementsFix_removeIfBlock_proposalDescription;
	public static String ControlStatementsFix_removeElseBlock_proposalDescription;
	public static String ControlStatementsFix_removeIfElseBlock_proposalDescription;
	public static String ControlStatementsFix_removeBrackets_proposalDescription;

	public static String ExpressionsFix_addParanoiacParenthesis_description;
	public static String ExpressionsFix_removeUnnecessaryParenthesis_description;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, FixMessages.class);
	}

}
