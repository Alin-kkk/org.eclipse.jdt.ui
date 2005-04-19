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

package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IFile;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.part.FileEditorInput;

import org.eclipse.ltk.core.refactoring.CompositeChange;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.changes.AddToClasspathChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.MoveCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.TypeInfo;
import org.eclipse.jdt.internal.corext.util.TypeInfoRequestor;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.actions.OrganizeImportsAction;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.CoreUtility;

public class ReorgCorrectionsSubProcessor {

	public static void getWrongTypeNameProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		String[] args= problem.getProblemArguments();
		if (args.length == 2) {
			ICompilationUnit cu= context.getCompilationUnit();
			boolean isLinked= cu.getResource().isLinked();

			// rename type
			proposals.add(new CorrectMainTypeNameProposal(cu, args[1], 5));

			String newCUName= args[1] + ".java"; //$NON-NLS-1$
			ICompilationUnit newCU= ((IPackageFragment) (cu.getParent())).getCompilationUnit(newCUName);
			if (!newCU.exists() && !isLinked && !JavaConventions.validateCompilationUnitName(newCUName).matches(IStatus.ERROR)) {
				RenameCompilationUnitChange change= new RenameCompilationUnitChange(cu, newCUName);

				// rename cu
				String label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_renamecu_description, newCUName);
				proposals.add(new ChangeCorrectionProposal(label, change, 6, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_RENAME)));
			}
		}
	}

	public static void getWrongPackageDeclNameProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		String[] args= problem.getProblemArguments();
		if (args.length == 1) {
			ICompilationUnit cu= context.getCompilationUnit();
			boolean isLinked= cu.getResource().isLinked();

			// correct pack decl
			int relevance= cu.getPackageDeclarations().length == 0 ? 7 : 5; // bug 38357
			proposals.add(new CorrectPackageDeclarationProposal(cu, problem, relevance));

			// move to pack
			IPackageDeclaration[] packDecls= cu.getPackageDeclarations();
			String newPackName= packDecls.length > 0 ? packDecls[0].getElementName() : ""; //$NON-NLS-1$

			IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(cu);
			IPackageFragment newPack= root.getPackageFragment(newPackName);

			ICompilationUnit newCU= newPack.getCompilationUnit(cu.getElementName());
			if (!newCU.exists() && !isLinked) {
				String label;
				if (newPack.isDefaultPackage()) {
					label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_movecu_default_description, cu.getElementName());
				} else {
					label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_movecu_description, new Object[] { cu.getElementName(), newPack.getElementName() });
				}
				CompositeChange composite= new CompositeChange(label);
				composite.add(new CreatePackageChange(newPack));
				composite.add(new MoveCompilationUnitChange(cu, newPack));

				proposals.add(new ChangeCorrectionProposal(label, composite, 6, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_MOVE)));
			}
		}
	}

	public static void removeImportStatementProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) {
		final ICompilationUnit cu= context.getCompilationUnit();

		ASTNode selectedNode= problem.getCoveringNode(context.getASTRoot());
		if (selectedNode != null) {
			ASTNode node= ASTNodes.getParent(selectedNode, ASTNode.IMPORT_DECLARATION);
			if (node instanceof ImportDeclaration) {
				ASTRewrite rewrite= ASTRewrite.create(node.getAST());

				rewrite.remove(node, null);

				String label= CorrectionMessages.ReorgCorrectionsSubProcessor_unusedimport_description;
				Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_DELETE_IMPORT);

				ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal(label, cu, rewrite, 6, image);
				proposals.add(proposal);
			}
		}

		String name= CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description;
		ChangeCorrectionProposal proposal= new ChangeCorrectionProposal(name, null, 5, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE)) {
			public void apply(IDocument document) {
				IEditorInput input= new FileEditorInput((IFile) cu.getResource());
				IWorkbenchPage p= JavaPlugin.getActivePage();
				if (p == null) {
					return;
				}
				IEditorPart part= p.findEditor(input);
				if (part instanceof JavaEditor) {
					OrganizeImportsAction action= new OrganizeImportsAction((JavaEditor) part);
					action.run(cu);
				}
			}
		};
		proposals.add(proposal);
	}

	public static void importNotFoundProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		ICompilationUnit cu= context.getCompilationUnit();
		IJavaProject project= cu.getJavaProject();

		ASTNode selectedNode= problem.getCoveringNode(context.getASTRoot());
		if (selectedNode != null) {
			ImportDeclaration importDeclaration= (ImportDeclaration) ASTNodes.getParent(selectedNode, ASTNode.IMPORT_DECLARATION);
			if (importDeclaration == null) {
				return;
			}
			String name= ASTNodes.asString(importDeclaration.getName());
			char[] packageName;
			char[] typeName= null;
			if (importDeclaration.isOnDemand()) {
				packageName= name.toCharArray();
			} else {
				packageName= Signature.getQualifier(name).toCharArray();
				typeName= Signature.getSimpleName(name).toCharArray();
			}
			IJavaSearchScope scope= SearchEngine.createWorkspaceScope();
			ArrayList res= new ArrayList();
			TypeNameRequestor requestor= new TypeInfoRequestor(res);
			new SearchEngine().searchAllTypeNames(packageName, typeName,
					SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE, IJavaSearchConstants.TYPE, scope, requestor,
					IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, null);

			if (res.isEmpty()) {
				return;
			}
			HashSet addedClaspaths= new HashSet();
			for (int i= 0; i < res.size(); i++) {
				TypeInfo curr= (TypeInfo) res.get(i);
				IType type= curr.resolveType(scope);
				if (type != null) {
					IPackageFragmentRoot root= (IPackageFragmentRoot) type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
					IClasspathEntry entry= root.getRawClasspathEntry();
					if (entry == null) {
						continue;
					}
					IJavaProject other= root.getJavaProject();
					int entryKind= entry.getEntryKind();
					if ((entry.isExported() || entryKind == IClasspathEntry.CPE_SOURCE) && addedClaspaths.add(other)) {
						String[] args= { other.getElementName(), project.getElementName() };
						String label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_project_description, args);
						IClasspathEntry newEntry= JavaCore.newProjectEntry(other.getPath());
						AddToClasspathChange change= new AddToClasspathChange(project, newEntry);
						if (!change.entryAlreadyExists()) {
							ChangeCorrectionProposal proposal= new ChangeCorrectionProposal(label, change, 8, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
							proposals.add(proposal);
						}
					}
					if ((entryKind == IClasspathEntry.CPE_LIBRARY || entryKind == IClasspathEntry.CPE_VARIABLE || entryKind == IClasspathEntry.CPE_CONTAINER) && addedClaspaths.add(entry)) {
						String label= getAddClasspathLabel(entry, root, project);
						if (label != null) {
							AddToClasspathChange change= new AddToClasspathChange(project, entry);
							if (!change.entryAlreadyExists()) {
								ChangeCorrectionProposal proposal= new ChangeCorrectionProposal(label, change, 7, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
								proposals.add(proposal);
							}
						}
					}
				}
			}
		}
	}

	private static String getAddClasspathLabel(IClasspathEntry entry, IPackageFragmentRoot root, IJavaProject project) {
		switch (entry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
				if (root.isArchive()) {
					String[] args= { JavaElementLabels.getElementLabel(root, 0), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_archive_description, args);
				} else {
					String[] args= { JavaElementLabels.getElementLabel(root, 0), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_classfolder_description, args);
				}
			case IClasspathEntry.CPE_VARIABLE: {
					String[] args= { JavaElementLabels.getElementLabel(root, 0), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_variable_description, args);
				}
			case IClasspathEntry.CPE_CONTAINER:
				try {
					String[] args= { JavaElementLabels.getContainerEntryLabel(entry.getPath(), root.getJavaProject()), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_library_description, args);
				} catch (JavaModelException e) {
					// ignore
				}
				break;
			}
		return null;
	}

	private static class ChangeTo50Compliance extends ChangeCorrectionProposal {

		private final IJavaProject fProject;

		public ChangeTo50Compliance(String name, IJavaProject project, int relevance) {
			super(name, null, relevance, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
			fProject= project;
		}

		public void apply(IDocument document) {
			if (fProject != null) {
				Map map= fProject.getOptions(false);
				JavaModelUtil.set50CompilanceOptions(map);
				fProject.setOptions(map);
				CoreUtility.startBuildInBackground(fProject.getProject());
			} else {
				Hashtable map= JavaCore.getOptions();
				JavaModelUtil.set50CompilanceOptions(map);
				JavaCore.setOptions(map);
				CoreUtility.startBuildInBackground(null);
			}
		}
	}

	public static void getNeed50ComplianceProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) {
		ICompilationUnit cu= context.getCompilationUnit();
		String label1= CorrectionMessages.ReorgCorrectionsSubProcessor_50_project_compliance_description;
		proposals.add(new ChangeTo50Compliance(label1, cu.getJavaProject(), 5));

		if (cu.getJavaProject().getOption(JavaCore.COMPILER_COMPLIANCE, false) == null) {
			String label2= CorrectionMessages.ReorgCorrectionsSubProcessor_50_workspace_compliance_description;
			proposals.add(new ChangeTo50Compliance(label2, null, 6));
		}
	}
}