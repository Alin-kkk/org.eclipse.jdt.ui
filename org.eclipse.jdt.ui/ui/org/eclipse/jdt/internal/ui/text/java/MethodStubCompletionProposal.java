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
package org.eclipse.jdt.internal.ui.text.java;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.corext.codemanipulation.ImportsStructure;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility.GenStubSettings;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class MethodStubCompletionProposal extends JavaTypeCompletionProposal {
	
	private String fTypeName;
	private String fMethodName;
	private String[] fParamTypes;
	private IJavaProject fJavaProject;

	public MethodStubCompletionProposal(IJavaProject jproject, ICompilationUnit cu, String declaringTypeName, String methodName, String[] paramTypes, int start, int length, String displayName, String completionProposal) {
		super(completionProposal, cu, start, length, null, displayName, 0);
		Assert.isNotNull(jproject);
		Assert.isNotNull(methodName);
		Assert.isNotNull(declaringTypeName);
		Assert.isNotNull(paramTypes);

		fTypeName= declaringTypeName;
		fParamTypes= paramTypes;
		fMethodName= methodName;

		fJavaProject= jproject;
	}

	/* (non-Javadoc)
	 * @see JavaTypeCompletionProposal#updateReplacementString(IDocument, char, int, ImportsStructure)
	 */
	protected boolean updateReplacementString(IDocument document, char trigger, int offset, ImportsStructure impStructure) throws CoreException, BadLocationException {
		IType declaringType= fJavaProject.findType(fTypeName);
		if (declaringType != null) {
			IMethod method= JavaModelUtil.findMethod(fMethodName, fParamTypes, false, declaringType);
			if (method != null) {
				GenStubSettings settings= new GenStubSettings(JavaPreferencesSettings.getCodeGenerationSettings());
				IType definingType= null;
				if (impStructure != null) {
					IJavaElement currElem= impStructure.getCompilationUnit().getElementAt(offset);
					if (currElem != null) {
						definingType= (IType) currElem.getAncestor(IJavaElement.TYPE);
					}
				}

				settings.noBody= (definingType != null) && definingType.isInterface();
				settings.callSuper= declaringType.isClass() && !Flags.isAbstract(method.getFlags()) && !Flags.isStatic(method.getFlags());
				settings.methodOverwrites= !Flags.isStatic(method.getFlags());
				
				String definingTypeName= (definingType != null) ? definingType.getElementName() : ""; //$NON-NLS-1$
				
				String stub= StubUtility.genStub(fCompilationUnit, definingTypeName, method, declaringType, settings, impStructure);


				// use the code formatter
				String lineDelim= StubUtility.getLineDelimiterFor(document);
				IRegion region= document.getLineInformationOfOffset(getReplacementOffset());
				int lineStart= region.getOffset();
				int indent= Strings.computeIndent(document.get(lineStart, getReplacementOffset() - lineStart), settings.tabWidth);

				String replacement= CodeFormatterUtil.format(CodeFormatterUtil.K_CLASS_BODY_DECLARATIONS, stub, indent, null, lineDelim);
				
				if (replacement.endsWith(lineDelim)) {
					replacement= replacement.substring(0, replacement.length() - lineDelim.length());
				}
				
				setReplacementString(Strings.trimLeadingTabsAndSpaces(replacement));
				return true;
			}
		}

		return false;
	}

}

