/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.core.resources.IProject;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.ui.forms.widgets.ExpandableComposite;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;

/**
  */
public class ProblemSeveritiesConfigurationBlock extends OptionsConfigurationBlock {

	private static final String SETTINGS_SECTION_NAME= "ProblemSeveritiesConfigurationBlock"; //$NON-NLS-1$
	
	// Preference store keys, see JavaCore.getOptions
	private static final Key PREF_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD);
	private static final Key PREF_PB_METHOD_WITH_CONSTRUCTOR_NAME= getJDTCoreKey(JavaCore.COMPILER_PB_METHOD_WITH_CONSTRUCTOR_NAME);
	private static final Key PREF_PB_DEPRECATION= getJDTCoreKey(JavaCore.COMPILER_PB_DEPRECATION);
	private static final Key PREF_PB_DEPRECATION_IN_DEPRECATED_CODE=getJDTCoreKey(JavaCore.COMPILER_PB_DEPRECATION_IN_DEPRECATED_CODE);
	private static final Key PREF_PB_DEPRECATION_WHEN_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_DEPRECATION_WHEN_OVERRIDING_DEPRECATED_METHOD);
	
	private static final Key PREF_PB_HIDDEN_CATCH_BLOCK= getJDTCoreKey(JavaCore.COMPILER_PB_HIDDEN_CATCH_BLOCK);
	private static final Key PREF_PB_UNUSED_LOCAL= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_LOCAL);
	private static final Key PREF_PB_UNUSED_PARAMETER= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PARAMETER);
	private static final Key PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PARAMETER_WHEN_OVERRIDING_CONCRETE);
	private static final Key PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PARAMETER_WHEN_IMPLEMENTING_ABSTRACT);
	private static final Key PREF_PB_SYNTHETIC_ACCESS_EMULATION= getJDTCoreKey(JavaCore.COMPILER_PB_SYNTHETIC_ACCESS_EMULATION);
	private static final Key PREF_PB_NON_EXTERNALIZED_STRINGS= getJDTCoreKey(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL);
	private static final Key PREF_PB_UNUSED_IMPORT= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_IMPORT);
	private static final Key PREF_PB_UNUSED_PRIVATE= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER);
	private static final Key PREF_PB_STATIC_ACCESS_RECEIVER= getJDTCoreKey(JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER);
	private static final Key PREF_PB_NO_EFFECT_ASSIGNMENT= getJDTCoreKey(JavaCore.COMPILER_PB_NO_EFFECT_ASSIGNMENT);
	private static final Key PREF_PB_CHAR_ARRAY_IN_CONCAT= getJDTCoreKey(JavaCore.COMPILER_PB_CHAR_ARRAY_IN_STRING_CONCATENATION);
	private static final Key PREF_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT= getJDTCoreKey(JavaCore.COMPILER_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT);
	private static final Key PREF_PB_LOCAL_VARIABLE_HIDING= getJDTCoreKey(JavaCore.COMPILER_PB_LOCAL_VARIABLE_HIDING);
	private static final Key PREF_PB_FIELD_HIDING= getJDTCoreKey(JavaCore.COMPILER_PB_FIELD_HIDING);
	private static final Key PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD= getJDTCoreKey(JavaCore.COMPILER_PB_SPECIAL_PARAMETER_HIDING_FIELD);
	private static final Key PREF_PB_INDIRECT_STATIC_ACCESS= getJDTCoreKey(JavaCore.COMPILER_PB_INDIRECT_STATIC_ACCESS);
	private static final Key PREF_PB_EMPTY_STATEMENT= getJDTCoreKey(JavaCore.COMPILER_PB_EMPTY_STATEMENT);
	private static final Key PREF_PB_UNNECESSARY_ELSE= getJDTCoreKey(JavaCore.COMPILER_PB_UNNECESSARY_ELSE);
	private static final Key PREF_PB_UNNECESSARY_TYPE_CHECK= getJDTCoreKey(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK);
	private static final Key PREF_PB_INCOMPATIBLE_INTERFACE_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_INCOMPATIBLE_NON_INHERITED_INTERFACE_METHOD);
	private static final Key PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION);
	private static final Key PREF_PB_MISSING_SERIAL_VERSION= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_SERIAL_VERSION);
	private static final Key PREF_PB_UNDOCUMENTED_EMPTY_BLOCK= getJDTCoreKey(JavaCore.COMPILER_PB_UNDOCUMENTED_EMPTY_BLOCK);
	private static final Key PREF_PB_FINALLY_BLOCK_NOT_COMPLETING= getJDTCoreKey(JavaCore.COMPILER_PB_FINALLY_BLOCK_NOT_COMPLETING);
	private static final Key PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING);
	private static final Key PREF_PB_UNQUALIFIED_FIELD_ACCESS= getJDTCoreKey(JavaCore.COMPILER_PB_UNQUALIFIED_FIELD_ACCESS);
	
	private static final Key PREF_15_PB_UNCHECKED_TYPE_OPERATION= getJDTCoreKey(JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION);
	private static final Key PREF_15_PB_FINAL_PARAM_BOUND= getJDTCoreKey(JavaCore.COMPILER_PB_FINAL_PARAMETER_BOUND);
	private static final Key PREF_15_PB_VARARGS_ARGUMENT_NEED_CAST= getJDTCoreKey(JavaCore.COMPILER_PB_VARARGS_ARGUMENT_NEED_CAST);
	private static final Key PREF_15_PB_AUTOBOXING_PROBLEM= getJDTCoreKey("org.eclipse.jdt.core.compiler.problem.autoboxing"); //$NON-NLS-1$

	
	// values
	private static final String ERROR= JavaCore.ERROR;
	private static final String WARNING= JavaCore.WARNING;
	private static final String IGNORE= JavaCore.IGNORE;

	private static final String ENABLED= JavaCore.ENABLED;
	private static final String DISABLED= JavaCore.DISABLED;
	

	private PixelConverter fPixelConverter;
	
	public ProblemSeveritiesConfigurationBlock(IStatusChangeListener context, IProject project) {
		super(context, project, getKeys());
		
		// compatibilty code for the merge of the two option PB_SIGNAL_PARAMETER: 
		if (ENABLED.equals(getValue(PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT))) {
			setValue(PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING, ENABLED);
		}
	}
	
	private static Key[] getKeys() {
		return new Key[] {
				PREF_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD,
				PREF_PB_METHOD_WITH_CONSTRUCTOR_NAME, PREF_PB_DEPRECATION, PREF_PB_HIDDEN_CATCH_BLOCK, PREF_PB_UNUSED_LOCAL,
				PREF_PB_UNUSED_PARAMETER, PREF_PB_SYNTHETIC_ACCESS_EMULATION, PREF_PB_NON_EXTERNALIZED_STRINGS,
				PREF_PB_UNUSED_IMPORT, 
				PREF_PB_STATIC_ACCESS_RECEIVER, PREF_PB_DEPRECATION_IN_DEPRECATED_CODE, 
				PREF_PB_NO_EFFECT_ASSIGNMENT, PREF_PB_INCOMPATIBLE_INTERFACE_METHOD,
				PREF_PB_UNUSED_PRIVATE, PREF_PB_CHAR_ARRAY_IN_CONCAT, PREF_PB_UNNECESSARY_ELSE,
				PREF_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT, PREF_PB_LOCAL_VARIABLE_HIDING, PREF_PB_FIELD_HIDING,
				PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD, PREF_PB_INDIRECT_STATIC_ACCESS,
				PREF_PB_EMPTY_STATEMENT, PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING, PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT,
				PREF_PB_UNNECESSARY_TYPE_CHECK, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION, PREF_PB_UNQUALIFIED_FIELD_ACCESS,
				PREF_PB_UNDOCUMENTED_EMPTY_BLOCK, PREF_PB_FINALLY_BLOCK_NOT_COMPLETING, PREF_PB_DEPRECATION_WHEN_OVERRIDING,
				PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING, PREF_PB_MISSING_SERIAL_VERSION, 				
				PREF_15_PB_UNCHECKED_TYPE_OPERATION, PREF_15_PB_FINAL_PARAM_BOUND, PREF_15_PB_VARARGS_ARGUMENT_NEED_CAST,
				PREF_15_PB_AUTOBOXING_PROBLEM
			};
	}
	
	/*
	 * @see org.eclipse.jface.preference.PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		fPixelConverter= new PixelConverter(parent);
		setShell(parent.getShell());
				
		Composite commonComposite= createStyleTabContent(parent);
		
		validateSettings(null, null, null);
	
		return commonComposite;
	}
	
	private Composite createStyleTabContent(Composite folder) {
		String[] errorWarningIgnore= new String[] { ERROR, WARNING, IGNORE };
		
		String[] errorWarningIgnoreLabels= new String[] {
			PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.error"),  //$NON-NLS-1$
			PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.warning"), //$NON-NLS-1$
			PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.ignore") //$NON-NLS-1$
		};
		
		String[] enabledDisabled= new String[] { ENABLED, DISABLED };
		
		int nColumns= 3;
		
		final ScrolledPageContent sc1 = new ScrolledPageContent(folder);
		
		Composite composite= sc1.getBody();
		GridLayout layout= new GridLayout(nColumns, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);
		
		Label description= new Label(composite, SWT.LEFT | SWT.WRAP);
		description.setText(PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.common.description")); //$NON-NLS-1$
		description.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, false, nColumns - 1, 1));
				
		int indentStep=  fPixelConverter.convertWidthInCharsToPixels(1);
		
		int defaultIndent= indentStep * 0;
		int extraIndent= indentStep * 2;
		String label;
		ExpandableComposite excomposite;
		Composite inner;
		
		// --- style
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.section.code_style"); //$NON-NLS-1$
		excomposite= createStyleSection(composite, label, nColumns);
		
		inner= new Composite(excomposite, SWT.NONE);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_static_access_receiver.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_STATIC_ACCESS_RECEIVER, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_indirect_access_to_static.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_INDIRECT_STATIC_ACCESS, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);	

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unqualified_field_access.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNQUALIFIED_FIELD_ACCESS, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_undocumented_empty_block.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNDOCUMENTED_EMPTY_BLOCK, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_synth_access_emul.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_SYNTHETIC_ACCESS_EMULATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_method_naming.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_METHOD_WITH_CONSTRUCTOR_NAME, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);			

		// --- potential_programming_problems
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.section.potential_programming_problems"); //$NON-NLS-1$
		excomposite= createStyleSection(composite, label, nColumns);
		
		inner= new Composite(excomposite, SWT.NONE);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_missing_serial_version.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_MISSING_SERIAL_VERSION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
				
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_no_effect_assignment.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_NO_EFFECT_ASSIGNMENT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_accidential_assignement.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_finally_block_not_completing.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_FINALLY_BLOCK_NOT_COMPLETING, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_empty_statement.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_EMPTY_STATEMENT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_char_array_in_concat.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_CHAR_ARRAY_IN_CONCAT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_hidden_catchblock.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_HIDDEN_CATCH_BLOCK, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		// --- name_shadowing
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.section.name_shadowing"); //$NON-NLS-1$
		excomposite= createStyleSection(composite, label, nColumns);
		
		inner= new Composite(excomposite, SWT.NONE);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_field_hiding.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_FIELD_HIDING, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_local_variable_hiding.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_LOCAL_VARIABLE_HIDING, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_special_param_hiding.label"); //$NON-NLS-1$
		addCheckBox(inner, label, PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD, enabledDisabled, extraIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_overriding_pkg_dflt.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);			
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_incompatible_interface_method.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_INCOMPATIBLE_INTERFACE_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		
		// --- deprecations
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.section.deprecations"); //$NON-NLS-1$
		excomposite= createStyleSection(composite, label, nColumns);
		
		inner= new Composite(excomposite, SWT.NONE);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_deprecation.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_DEPRECATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_deprecation_in_deprecation.label"); //$NON-NLS-1$
		addCheckBox(inner, label, PREF_PB_DEPRECATION_IN_DEPRECATED_CODE, enabledDisabled, extraIndent);		
	
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_deprecation_when_overriding.label"); //$NON-NLS-1$
		addCheckBox(inner, label, PREF_PB_DEPRECATION_WHEN_OVERRIDING, enabledDisabled, extraIndent);

		// --- nls
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.section.nls"); //$NON-NLS-1$
		excomposite= createStyleSection(composite, label, nColumns);
		
		inner= new Composite(excomposite, SWT.NONE);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_non_externalized_strings.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_NON_EXTERNALIZED_STRINGS, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		// --- unnecessary_code
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.section.unnecessary_code"); //$NON-NLS-1$
		excomposite= createStyleSection(composite, label, nColumns);
	
		inner= new Composite(excomposite, SWT.NONE);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unused_local.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNUSED_LOCAL, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unused_parameter.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNUSED_PARAMETER, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_signal_param_in_overriding.label"); //$NON-NLS-1$
		addCheckBox(inner, label, PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING, enabledDisabled, extraIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unused_imports.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNUSED_IMPORT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unused_private.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNUSED_PRIVATE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unnecessary_else.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNNECESSARY_ELSE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unnecessary_type_check.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNNECESSARY_TYPE_CHECK, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unused_throwing_exception.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unused_throwing_exception_when_overriding.label"); //$NON-NLS-1$
		addCheckBox(inner, label, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING, enabledDisabled, extraIndent);

		// --- 1.5
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.section.jdk50"); //$NON-NLS-1$
		excomposite= createStyleSection(composite, label, nColumns);

		
		inner= new Composite(excomposite, SWT.NONE);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_unsafe_type_op.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_15_PB_UNCHECKED_TYPE_OPERATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_final_param_bound.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_15_PB_FINAL_PARAM_BOUND, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_inexact_vararg.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_15_PB_VARARGS_ARGUMENT_NEED_CAST, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);

		label= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.pb_autoboxing_problem.label"); //$NON-NLS-1$
		addComboBox(inner, label, PREF_15_PB_AUTOBOXING_PROBLEM, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent);
		
		IDialogSettings section= JavaPlugin.getDefault().getDialogSettings().getSection(SETTINGS_SECTION_NAME);
		restoreSectionExpansionStates(section);
		
		return sc1;
	}
	
	/* (non-javadoc)
	 * Update fields and validate.
	 * @param changedKey Key that changed, or null, if all changed.
	 */	
	protected void validateSettings(Key changedKey, String oldValue, String newValue) {
		if (changedKey != null) {
			if (PREF_PB_UNUSED_PARAMETER.equals(changedKey) ||
					PREF_PB_DEPRECATION.equals(changedKey) ||
					PREF_PB_LOCAL_VARIABLE_HIDING.equals(changedKey) ||
					PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION.equals(changedKey)) {				
				updateEnableStates();
			} else if (PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING.equals(changedKey)) {
				// merging the two options
				setValue(PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT, newValue);
			} else {
				return;
			}
		} else {
			updateEnableStates();
		}		
		fContext.statusChanged(new StatusInfo());
	}
	
	private void updateEnableStates() {
		boolean enableUnusedParams= !checkValue(PREF_PB_UNUSED_PARAMETER, IGNORE);
		getCheckBox(PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING).setEnabled(enableUnusedParams);
		
		boolean enableDeprecation= !checkValue(PREF_PB_DEPRECATION, IGNORE);
		getCheckBox(PREF_PB_DEPRECATION_IN_DEPRECATED_CODE).setEnabled(enableDeprecation);
		getCheckBox(PREF_PB_DEPRECATION_WHEN_OVERRIDING).setEnabled(enableDeprecation);
		
		boolean enableThrownExceptions= !checkValue(PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION, IGNORE);
		getCheckBox(PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING).setEnabled(enableThrownExceptions);

		boolean enableHiding= !checkValue(PREF_PB_LOCAL_VARIABLE_HIDING, IGNORE);
		getCheckBox(PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD).setEnabled(enableHiding);
		
	}

	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		String title= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.needsbuild.title"); //$NON-NLS-1$
		String message;
		if (workspaceSettings) {
			message= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.needsfullbuild.message"); //$NON-NLS-1$
		} else {
			message= PreferencesMessages.getString("ProblemSeveritiesConfigurationBlock.needsprojectbuild.message"); //$NON-NLS-1$
		}
		return new String[] { title, message };
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#dispose()
	 */
	public void dispose() {
		IDialogSettings section= JavaPlugin.getDefault().getDialogSettings().addNewSection(SETTINGS_SECTION_NAME);
		storeSectionExpansionStates(section);
		super.dispose();
	}
	
}
