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
package org.eclipse.jdt.internal.ui.search;

import java.util.StringTokenizer;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.PatternQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;

import org.eclipse.jdt.internal.ui.JavaPlugin;

abstract class MatchFilter {
	
	private static final String SETTINGS_LAST_USED_FILTERS= "filters_last_used";  //$NON-NLS-1$
	
	public static MatchFilter[] getLastUsedFilters() {
		String string= JavaPlugin.getDefault().getDialogSettings().get(SETTINGS_LAST_USED_FILTERS);
		if (string != null && string.length() > 0) {
			return decodeFiltersString(string);
		}
		return getDefaultFilters();
	}
	
	public static void setLastUsedFilters(MatchFilter[] filters) {
		String encoded= encodeFilters(filters);
		JavaPlugin.getDefault().getDialogSettings().put(SETTINGS_LAST_USED_FILTERS, encoded);
	}
	
	public static MatchFilter[] getDefaultFilters() {
		return new MatchFilter[] { IMPORT_FILTER };
	}
	
	private static String encodeFilters(MatchFilter[] enabledFilters) {
		StringBuffer buf= new StringBuffer();
		buf.append(enabledFilters.length);
		for (int i= 0; i < enabledFilters.length; i++) {
			buf.append(';');
			buf.append(enabledFilters[i].getID());
		}
		return buf.toString();
	}
	
	private static MatchFilter[] decodeFiltersString(String encodedString) {
		StringTokenizer tokenizer= new StringTokenizer(encodedString, String.valueOf(';'));
		int count= Integer.valueOf(tokenizer.nextToken()).intValue();
		MatchFilter[] res= new MatchFilter[count];
		for (int i= 0; i < count; i++) {
			res[i]= findMatchFilter(tokenizer.nextToken());
		}
		return res;
	}
		
	
	public abstract boolean isApplicable(JavaSearchQuery query);
	
	public abstract boolean filters(JavaElementMatch match);

	public abstract String getName();
	public abstract String getActionLabel();
	
	public abstract String getDescription();
	
	public abstract String getID();
	
	private static final MatchFilter IMPORT_FILTER= new ImportFilter(); 
	private static final MatchFilter JAVADOC_FILTER= new JavadocFilter(); 
	private static final MatchFilter READ_FILTER= new ReadFilter(); 
	private static final MatchFilter WRITE_FILTER= new WriteFilter(); 
	private static final MatchFilter INEXACT_FILTER= new InexactMatchFilter(); 
	private static final MatchFilter ERASURE_FILTER= new ErasureMatchFilter(); 
	
	private static final MatchFilter[] ALL_FILTERS= new MatchFilter[] {
			IMPORT_FILTER,
			JAVADOC_FILTER,
			READ_FILTER,
			WRITE_FILTER,
			INEXACT_FILTER,
			ERASURE_FILTER
	};
		
	public static MatchFilter[] allFilters() {
		return ALL_FILTERS;
	}
	
	private static MatchFilter findMatchFilter(String id) {
		for (int i= 0; i < ALL_FILTERS.length; i++) {
			if (ALL_FILTERS[i].getID().equals(id))
				return ALL_FILTERS[i];
		}
		return IMPORT_FILTER; // just return something, should not happen
	}


}

class ImportFilter extends MatchFilter {
	public boolean filters(JavaElementMatch match) {
		return match.getElement() instanceof IImportDeclaration;
	}

	public String getName() {
		return SearchMessages.MatchFilter_ImportFilter_name; 
	}

	public String getActionLabel() {
		return SearchMessages.MatchFilter_ImportFilter_actionLabel; 
	}

	public String getDescription() {
		return SearchMessages.MatchFilter_ImportFilter_description; 
	}
	
	public boolean isApplicable(JavaSearchQuery query) {
		QuerySpecification spec= query.getSpecification();
		if (spec instanceof ElementQuerySpecification) {
			ElementQuerySpecification elementSpec= (ElementQuerySpecification) spec;
			return elementSpec.getElement() instanceof IType;
		} else if (spec instanceof PatternQuerySpecification) {
			PatternQuerySpecification patternSpec= (PatternQuerySpecification) spec;
			return patternSpec.getSearchFor() == IJavaSearchConstants.TYPE;
		}
		return false;
	}

	public String getID() {
		return "filter_imports"; //$NON-NLS-1$
	}
}

abstract class FieldFilter extends MatchFilter {
	public boolean isApplicable(JavaSearchQuery query) {
		QuerySpecification spec= query.getSpecification();
		if (spec instanceof ElementQuerySpecification) {
			ElementQuerySpecification elementSpec= (ElementQuerySpecification) spec;
			return elementSpec.getElement() instanceof IField;
		} else if (spec instanceof PatternQuerySpecification) {
			PatternQuerySpecification patternSpec= (PatternQuerySpecification) spec;
			return patternSpec.getSearchFor() == IJavaSearchConstants.FIELD;
		}
		return false;
	}

}

class WriteFilter extends FieldFilter {
	public boolean filters(JavaElementMatch match) {
		return match.isWriteAccess() && !match.isReadAccess();
	}
	public String getName() {
		return SearchMessages.MatchFilter_WriteFilter_name; 
	}
	public String getActionLabel() {
		return SearchMessages.MatchFilter_WriteFilter_actionLabel; 
	}
	public String getDescription() {
		return SearchMessages.MatchFilter_WriteFilter_description; 
	}
	public String getID() {
		return "filter_writes"; //$NON-NLS-1$
	}
}

class ReadFilter extends FieldFilter {
	public boolean filters(JavaElementMatch match) {
		return match.isReadAccess() && !match.isWriteAccess();
	}
	public String getName() {
		return SearchMessages.MatchFilter_ReadFilter_name; 
	}
	public String getActionLabel() {
		return SearchMessages.MatchFilter_ReadFilter_actionLabel; 
	}
	public String getDescription() {
		return SearchMessages.MatchFilter_ReadFilter_description; 
	}
	public String getID() {
		return "filter_reads"; //$NON-NLS-1$
	}
}

class JavadocFilter extends MatchFilter {
	public boolean filters(JavaElementMatch match) {
		return match.isJavadoc();
	}
	public String getName() {
		return SearchMessages.MatchFilter_JavadocFilter_name; 
	}
	public String getActionLabel() {
		return SearchMessages.MatchFilter_JavadocFilter_actionLabel; 
	}
	public String getDescription() {
		return SearchMessages.MatchFilter_JavadocFilter_description; 
	}
	public boolean isApplicable(JavaSearchQuery query) {
		return true;
	}
	public String getID() {
		return "filter_javadoc"; //$NON-NLS-1$
	}
}

abstract class GenericTypeFilter extends MatchFilter {
	public boolean isApplicable(JavaSearchQuery query) {
		QuerySpecification spec= query.getSpecification();
		if (spec instanceof ElementQuerySpecification) {
			ElementQuerySpecification elementSpec= (ElementQuerySpecification) spec;
			Object element= elementSpec.getElement();
			ITypeParameter[] typeParameters= null;
			try {
				if (element instanceof IType) {
					typeParameters= ((IType)element).getTypeParameters();
				} else if (element instanceof IMethod) {
					typeParameters= ((IMethod)element).getTypeParameters();
				}
			} catch (JavaModelException e) {
				return false;
			}
			return typeParameters != null && typeParameters.length > 0;
		}
		return false;
	}
}

class ErasureMatchFilter extends GenericTypeFilter {
	public boolean filters(JavaElementMatch match) {
		return (match.getMatchRule() & (SearchPattern.R_FULL_MATCH | SearchPattern.R_EQUIVALENT_MATCH)) == 0;
	}
	public String getName() {
		return SearchMessages.MatchFilter_ErasureFilter_name; 
	}
	public String getActionLabel() {
		return SearchMessages.MatchFilter_ErasureFilter_actionLabel; 
	}
	public String getDescription() {
		return SearchMessages.MatchFilter_ErasureFilter_description; 
	}
	public String getID() {
		return "filter_erasure"; //$NON-NLS-1$
	}
}

class InexactMatchFilter extends GenericTypeFilter {
	public boolean filters(JavaElementMatch match) {
		return (match.getMatchRule() & (SearchPattern.R_FULL_MATCH)) == 0;
	}
	public String getName() {
		return SearchMessages.MatchFilter_InexactFilter_name; 
	}
	public String getActionLabel() {
		return SearchMessages.MatchFilter_InexactFilter_actionLabel; 
	}
	public String getDescription() {
		return SearchMessages.MatchFilter_InexactFilter_description; 
	}
	public String getID() {
		return "filter_inexact"; //$NON-NLS-1$
	}
}

