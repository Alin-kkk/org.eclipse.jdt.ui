package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;

import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.IUpdate;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ITypeNameRequestor;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.IWorkingCopyManager;

import org.eclipse.jdt.internal.corext.codemanipulation.AddImportsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.TypeInfo;
import org.eclipse.jdt.internal.corext.util.TypeInfoRequestor;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.util.TypeInfoLabelProvider;

public class AddImportOnSelectionAction extends Action implements IUpdate {
		
	private ITextEditor fEditor;
	
	public AddImportOnSelectionAction(ITextEditor editor) {	
		super(JavaEditorMessages.getString("AddImportOnSelection.label"));		 //$NON-NLS-1$
		setToolTipText(JavaEditorMessages.getString("AddImportOnSelection.tooltip")); //$NON-NLS-1$
		setDescription(JavaEditorMessages.getString("AddImportOnSelection.description")); //$NON-NLS-1$
		
		fEditor= editor;
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.ADD_IMPORT_ON_SELECTION_ACTION);	
	}
	
	public AddImportOnSelectionAction() {
		this(null);
	}
	
	public void setContentEditor(ITextEditor editor) {
		fEditor= editor;
	}
	
	public void update() {
		boolean isEnabled= false;
		try {
			ISelection selection= fEditor.getSelectionProvider().getSelection();
			isEnabled= (selection instanceof ITextSelection && JavaModelUtil.isEditable(getCompilationUnit()));
		} catch(JavaModelException e) {
		}
		setEnabled(isEnabled);
	}	
			
	private ICompilationUnit getCompilationUnit () {
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();				
		return manager.getWorkingCopy(fEditor.getEditorInput());
	}
	
	/**
	 * @see IAction#actionPerformed
	 */
	public void run() {
		ICompilationUnit cu= getCompilationUnit();
		if (cu != null) {
			ISelection s= fEditor.getSelectionProvider().getSelection();
			IDocument doc= fEditor.getDocumentProvider().getDocument(fEditor.getEditorInput());
			ITextSelection selection= (ITextSelection) s;
			
			if (doc != null) {
				try {
					int nameStart= getNameStart(doc, selection.getOffset());
					int nameEnd= getNameEnd(doc, selection.getOffset() + selection.getLength());
					int len= nameEnd - nameStart;
					
					String name= doc.get(nameStart, len).trim();
					String simpleName= Signature.getSimpleName(name);
					String containerName= Signature.getQualifier(name);
					
					IJavaSearchScope searchScope= SearchEngine.createJavaSearchScope(new IJavaElement[] { cu.getJavaProject() });
					
					TypeInfo[] types= findAllTypes(simpleName, searchScope, null);
					if (types.length== 0) {
						getShell().getDisplay().beep();
						return;
					}
					
					TypeInfo chosen= selectResult(types, containerName, getShell());
					if (chosen == null) {
						return;
					}
					IType type= chosen.resolveType(searchScope);
					if (type == null) {
						JavaPlugin.logErrorMessage("AddImportOnSelectionAction: Failed to resolve TypeRef: " + chosen.toString()); //$NON-NLS-1$
						MessageDialog.openError(getShell(), JavaEditorMessages.getString("AddImportOnSelection.error.title"), JavaEditorMessages.getString("AddImportOnSelection.error.notresolved.message")); //$NON-NLS-1$ //$NON-NLS-2$
						return;
					}
					removeQualification(doc, nameStart, chosen);
					
					CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();
					AddImportsOperation op= new AddImportsOperation(cu, new IJavaElement[] { type }, settings, false);
					ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());
					try {
						dialog.run(false, true, new WorkbenchRunnableAdapter(op));
					} catch (InvocationTargetException e) {
						JavaPlugin.log(e);
						MessageDialog.openError(getShell(), JavaEditorMessages.getString("AddImportOnSelection.error.title"), e.getTargetException().getMessage()); //$NON-NLS-1$
					} catch (InterruptedException e) {
						// Do nothing. Operation has been canceled.
					}
					return;
				} catch (CoreException e) {
					JavaPlugin.log(e);
					ErrorDialog.openError(getShell(), JavaEditorMessages.getString("AddImportOnSelection.error.title"), null, e.getStatus()); //$NON-NLS-1$
				} catch (BadLocationException e) {
					JavaPlugin.log(e);
					MessageDialog.openError(getShell(), JavaEditorMessages.getString("AddImportOnSelection.error.title"), e.getMessage()); //$NON-NLS-1$
				}
			}
		}		
	}
	
	private int getNameStart(IDocument doc, int pos) throws BadLocationException {
		while (pos > 0) {
			char ch= doc.getChar(pos - 1);
			if (!Character.isJavaIdentifierPart(ch) && ch != '.') {
				return pos;
			}
			pos--;
		}
		return pos;
	}
	
	private int getNameEnd(IDocument doc, int pos) throws BadLocationException {
		int len= doc.getLength();
		while (pos < len) {
			char ch= doc.getChar(pos);
			if (!Character.isJavaIdentifierPart(ch)) {
				return pos;
			}
			pos++;
		}
		return pos;
	}		
	
	private void removeQualification(IDocument doc, int nameStart, TypeInfo typeInfo) throws BadLocationException {
		String containerName= typeInfo.getTypeContainerName();
		int containerLen= containerName.length();
		if (containerLen > 0) {
			for (int k= 0; k < containerLen; k++) {
				if (doc.getChar(nameStart + k) != containerName.charAt(k)) {
					return;
				}
			}
			doc.replace(nameStart, containerLen + 1, ""); //$NON-NLS-1$
		}
	}	

	/**
	 * Finds a type by the simple name.
	 */
	private static TypeInfo[] findAllTypes(String simpleTypeName, IJavaSearchScope searchScope, IProgressMonitor monitor) throws CoreException {
		SearchEngine searchEngine= new SearchEngine();
		
		ArrayList typeRefsFound= new ArrayList(10);
		ITypeNameRequestor requestor= new TypeInfoRequestor(typeRefsFound);

		searchEngine.searchAllTypeNames(
			JavaPlugin.getWorkspace(), 
			null, 
			simpleTypeName.toCharArray(), 
			IJavaSearchConstants.EXACT_MATCH, 
			IJavaSearchConstants.CASE_SENSITIVE, 
			IJavaSearchConstants.TYPE, 
			searchScope, 
			requestor, 
			IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, 
			monitor);
			
		return (TypeInfo[]) typeRefsFound.toArray(new TypeInfo[typeRefsFound.size()]);
	}
	
	private Shell getShell() {
		return fEditor.getSite().getShell();
	}
		
						
	private TypeInfo selectResult(TypeInfo[] results, String containerName, Shell shell) {
		int nResults= results.length;
		
		if (nResults == 0) {
			return null;
		} else if (nResults == 1) {
			return results[0];
		}
		
		if (containerName.length() != 0) {
			for (int i= 0; i < nResults; i++) {
				TypeInfo curr= (TypeInfo) results[i];
				if (containerName.equals(curr.getTypeContainerName())) {
					return curr;
				}
			}
		}		
		ElementListSelectionDialog dialog= new ElementListSelectionDialog(getShell(), new TypeInfoLabelProvider(TypeInfoLabelProvider.SHOW_FULLYQUALIFIED));
		dialog.setTitle(JavaEditorMessages.getString("AddImportOnSelection.dialog.title")); //$NON-NLS-1$
		dialog.setMessage(JavaEditorMessages.getString("AddImportOnSelection.dialog.message")); //$NON-NLS-1$
		dialog.setElements(results);
		if (dialog.open() == dialog.OK) {
			return (TypeInfo) dialog.getFirstResult();
		}
		return null;
	}
}