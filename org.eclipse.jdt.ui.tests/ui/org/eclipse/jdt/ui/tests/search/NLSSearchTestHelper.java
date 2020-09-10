/*******************************************************************************
 * Copyright (c) 2006, 2013 IBM Corporation and others.
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
package org.eclipse.jdt.ui.tests.search;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IFile;

import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.text.Match;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.ui.refactoring.nls.search.CompilationUnitEntry;
import org.eclipse.jdt.internal.ui.refactoring.nls.search.FileEntry;
import org.eclipse.jdt.internal.ui.refactoring.nls.search.NLSSearchQuery;
import org.eclipse.jdt.internal.ui.refactoring.nls.search.NLSSearchResult;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;

public class NLSSearchTestHelper {

	public static void assertNumberOfProblems(ICompilationUnit accessor, IFile propertiesFile, int expected) {
		assertNumberResults(searchProblems(accessor, propertiesFile), expected);
	}

	public static void assertHasUndefinedKey(ICompilationUnit accessor, IFile propertiesFile, String key, IFile file, boolean isAccessor) throws CoreException {
		assertResultHasUndefinedKey(key, file, isAccessor, searchProblems(accessor, propertiesFile));
	}

	public static void assertHasUnusedKey(ICompilationUnit accessor, IFile propertiesFile, String key, IFile file, boolean isAccessor) throws IOException, CoreException {
		assertResultHasUnusedKey(key, file, isAccessor, searchProblems(accessor, propertiesFile));
	}

	public static void assertHasDuplicateKey(ICompilationUnit accessor, IFile propertiesFile, String key, IFile file) throws CoreException, IOException {
		assertResultHasDuplicateKey(key, file, searchProblems(accessor, propertiesFile));
	}

	private static NLSSearchResult searchProblems(ICompilationUnit accessor, IFile propertiesFile) {
		IType type= accessor.getType("Accessor");
		NLSSearchQuery query= new NLSSearchQuery((new IType[] {type}), (new IFile[] {propertiesFile}), SearchEngine.createWorkspaceScope(), ""); //$NON-NLS-1$
		NewSearchUI.runQueryInForeground(new BusyIndicatorRunnableContext(), query);
		NLSSearchResult result= (NLSSearchResult)query.getSearchResult();
		return result;
	}

	private static void assertNumberResults(NLSSearchResult result, int expected) {
		int is= result.getElements().length;
		Assert.assertEquals("Expected number of results is " + expected + " but was " + is, expected, is);
	}

	private static void assertResultHasUndefinedKey(String key, IFile file, boolean isAccessor, NLSSearchResult result) throws CoreException {
		for (Match match : result.getFileMatchAdapter().computeContainedMatches(result, file)) {
			if (match.getElement() instanceof ICompilationUnit) {
				ICompilationUnit unit= (ICompilationUnit)match.getElement();
				String field= unit.getSource().substring(match.getOffset(), match.getOffset() + match.getLength());
				if ((isAccessor && field.contains(key)) || (!isAccessor && field.equals(key)))
					return;
			}
		}

		Assert.fail("No undefined key problem found for " + key + " in " + file.getName());
	}

	private static void assertResultHasUnusedKey(String key, IFile file, boolean isAccessor, NLSSearchResult result) throws IOException, CoreException {
		for (Match match : result.getFileMatchAdapter().computeContainedMatches(result, file)) {
			if (match.getElement() instanceof CompilationUnitEntry) {
				ICompilationUnit unit= ((CompilationUnitEntry)match.getElement()).getCompilationUnit();
				String field= unit.getSource().substring(match.getOffset(), match.getOffset() + match.getLength());
				if ((isAccessor && field.contains(key)) || (!isAccessor && field.equals(key)))
					return;
			} else if (match.getElement() instanceof FileEntry) {
				FileEntry entry= (FileEntry)match.getElement();
				String content= getContent(entry.getPropertiesFile());
				String propkey= content.substring(match.getOffset(), match.getOffset() + match.getLength());
				if ((isAccessor && propkey.contains(key)) || (!isAccessor && propkey.equals(key)))
					return;
			}
		}

		Assert.fail("No unused key problem found for " + key + " in " + file.getName());
	}

	private static String getContent(IFile entry) throws CoreException, IOException {
		StringBuilder buf= new StringBuilder();
		try (InputStream contents= entry.getContents()) {
			char ch= (char)contents.read();
			int avilable= contents.available();
			while (avilable > 0 && ch != -1) {
				buf.append(ch);
				ch= (char)contents.read();
				avilable--;
			}
			return buf.toString();
		}
	}

	private static void assertResultHasDuplicateKey(String key, IFile file, NLSSearchResult result) throws CoreException, IOException {
		for (Match match : result.getFileMatchAdapter().computeContainedMatches(result, file)) {
			if (match.getElement() instanceof FileEntry) {
				FileEntry entry= (FileEntry)match.getElement();
				String content= getContent(entry.getPropertiesFile());
				int firstIndex= content.indexOf(key);
				if (firstIndex != -1 && content.indexOf(key, firstIndex + 1) != -1)
					return;
			}
		}

		Assert.fail("No duplicate key problem found for " + key + " in " + file.getName());
	}
}
