package org.eclipse.jdt.ui.actions;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.refactoring.structure.ModifyParametersRefactoring;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.refactoring.ModifyParametersWizard;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class ModifyParametersAction extends SelectionDispatchAction {
	
	private ModifyParametersRefactoring fRefactoring;
	private CompilationUnitEditor fEditor;
	
	public ModifyParametersAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	public ModifyParametersAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.getString("RefactoringGroup.modify_Parameters_label"));//$NON-NLS-1$
	}
	
	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		setEnabled(canEnable(selection));
	}

    /*
     * @see SelectionDispatchAction#selectionChanged(ITextSelection)
     */
	protected void selectionChanged(ITextSelection selection) {
	}
	
	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	protected void run(IStructuredSelection selection) {
		startRefactoring();
	}

    /*
     * @see SelectionDispatchAction#run(ITextSelection)
     */
	protected void run(ITextSelection selection) {
		if (! canRun(selection)){
			String unavailable= RefactoringMessages.getString("ModifyParametersAction.unavailable"); //$NON-NLS-1$
			MessageDialog.openInformation(getShell(), RefactoringMessages.getString("OpenRefactoringWizardAction.unavailable"), unavailable); //$NON-NLS-1$
			fRefactoring= null;
			return;
		}
		startRefactoring();	
	}
		
	private boolean canEnable(IStructuredSelection selection){
		if (selection.isEmpty() || selection.size() != 1) 
			return false;
		
		Object first= selection.getFirstElement();
		return (first instanceof IMethod) && shouldAcceptElement((IMethod)first);
	}
		
	private boolean canRun(ITextSelection selection){
		IJavaElement[] elements= resolveElements();
		if (elements.length != 1)
			return false;

		return (elements[0] instanceof IMethod) && shouldAcceptElement((IMethod)elements[0]);
	}

	private boolean shouldAcceptElement(IMethod method) {
		try{
			fRefactoring= new ModifyParametersRefactoring(method);
			return fRefactoring.checkPreactivation().isOK();
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.filterNotPresentException(e))
				JavaPlugin.log(e); //this happen on selection changes in viewers - do not show ui if fails, just log
			return false;
		}	
	}
		
	private IJavaElement[] resolveElements() {
		return SelectionConverter.codeResolveHandled(fEditor, getShell(),  RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"));  //$NON-NLS-1$
	}

	private RefactoringWizard createWizard(){
		String title= RefactoringMessages.getString("RefactoringGroup.modify_method_parameters"); //$NON-NLS-1$
		//FIX ME: wrong
		String helpId= IJavaHelpContextIds.RENAME_PARAMS_ERROR_WIZARD_PAGE;
		return new ModifyParametersWizard(fRefactoring, title, helpId);
	}
	
	private void startRefactoring() {
		Assert.isNotNull(fRefactoring);
		// Work around for http://dev.eclipse.org/bugs/show_bug.cgi?id=19104
		if (!ActionUtil.isProcessable(getShell(), fRefactoring.getMethod()))
			return;
		try{
			Object newElementToProcess= new RefactoringStarter().activate(fRefactoring, createWizard(), RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), true); //$NON-NLS-1$
			if (newElementToProcess == null)
				return;
			IStructuredSelection mockSelection= new StructuredSelection(newElementToProcess);
			selectionChanged(mockSelection);
			if (isEnabled())
				run(mockSelection);
			else
				MessageDialog.openInformation(JavaPlugin.getActiveWorkbenchShell(), ActionMessages.getString("ModifyParameterAction.problem.title"), ActionMessages.getString("ModifyParameterAction.problem.message"));	 //$NON-NLS-1$ //$NON-NLS-2$
		} catch (JavaModelException e){
			ExceptionHandler.handle(e, RefactoringMessages.getString("OpenRefactoringWizardAction.refactoring"), RefactoringMessages.getString("OpenRefactoringWizardAction.exception")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	

}
