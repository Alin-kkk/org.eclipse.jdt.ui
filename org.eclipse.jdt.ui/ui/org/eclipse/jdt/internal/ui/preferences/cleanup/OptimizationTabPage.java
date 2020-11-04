/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.BooleanLiteralCleanUp;
import org.eclipse.jdt.internal.ui.fix.BreakLoopCleanUp;
import org.eclipse.jdt.internal.ui.fix.LazyLogicalCleanUp;
import org.eclipse.jdt.internal.ui.fix.NoStringCreationCleanUp;
import org.eclipse.jdt.internal.ui.fix.PatternCleanUp;
import org.eclipse.jdt.internal.ui.fix.PrimitiveSerializationCleanUp;
import org.eclipse.jdt.internal.ui.fix.StringBuilderCleanUp;

public final class OptimizationTabPage extends AbstractCleanUpTabPage {
	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.optimization"; //$NON-NLS-1$

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new BreakLoopCleanUp(values),
				new StringBuilderCleanUp(values),
				new LazyLogicalCleanUp(values),
				new PrimitiveSerializationCleanUp(values),
				new PatternCleanUp(values),
				new NoStringCreationCleanUp(values),
				new BooleanLiteralCleanUp(values)
		};
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group optimizationGroup= createGroup(numColumns, composite, CleanUpMessages.OptimizationTabPage_GroupName_Optimization);

		final CheckboxPreference breakLoopPref= createCheckboxPref(optimizationGroup, numColumns, CleanUpMessages.OptimizationTabPage_CheckboxName_BreakLoop, CleanUpConstants.BREAK_LOOP, CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(breakLoopPref);

		final CheckboxPreference stringBuilderPref= createCheckboxPref(optimizationGroup, numColumns, CleanUpMessages.OptimizationTabPage_CheckboxName_StringBuilder, CleanUpConstants.STRINGBUILDER, CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(stringBuilderPref);

		final CheckboxPreference useLazyLogicalPref= createCheckboxPref(optimizationGroup, numColumns, CleanUpMessages.OptimizationTabPage_CheckboxName_UseLazyLogicalOperator,
				CleanUpConstants.USE_LAZY_LOGICAL_OPERATOR, CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(useLazyLogicalPref);

		final CheckboxPreference primitiveSerializationPref= createCheckboxPref(optimizationGroup, numColumns, CleanUpMessages.OptimizationTabPage_CheckboxName_PrimitiveSerialization, CleanUpConstants.PRIMITIVE_SERIALIZATION, CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(primitiveSerializationPref);

		final CheckboxPreference precompileRegExPref= createCheckboxPref(optimizationGroup, numColumns, CleanUpMessages.OptimizationTabPage_CheckboxName_PrecompileRegEx, CleanUpConstants.PRECOMPILE_REGEX,
				CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(precompileRegExPref);

		final CheckboxPreference noStringCreation= createCheckboxPref(optimizationGroup, numColumns, CleanUpMessages.OptimizationTabPage_CheckboxName_NoStringCreation, CleanUpConstants.NO_STRING_CREATION, CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(noStringCreation);

		final CheckboxPreference booleanLiteral= createCheckboxPref(optimizationGroup, numColumns, CleanUpMessages.OptimizationTabPage_CheckboxName_BooleanLiteral, CleanUpConstants.PREFER_BOOLEAN_LITERAL, CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(booleanLiteral);
	}
}
