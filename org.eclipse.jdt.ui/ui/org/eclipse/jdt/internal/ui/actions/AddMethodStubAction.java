/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.corext.codemanipulation.AddMethodStubOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.IRequestQuery;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;


/**
 * Creates method stubs in a type.
 * The type has to be set before usage (init)
 * Always forces the type to open in an editor. The result is unsaved,
 * so the user can decide if the changes are acceptable.
 */
public class AddMethodStubAction extends Action {

	private ISelection fSelection;
	private IType fParentType;

	public AddMethodStubAction() {
		super(JavaUIMessages.getString("AddMethodStubAction.label")); //$NON-NLS-1$
		setDescription(JavaUIMessages.getString("AddMethodStubAction.description")); //$NON-NLS-1$
		setToolTipText(JavaUIMessages.getString("AddMethodStubAction.tooltip")); //$NON-NLS-1$
		
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.ADD_METHODSTUB_ACTION);		
	}

	
	public boolean init(IType parentType, ISelection selection) {
		if (canActionBeAdded(parentType, selection)) {
			fParentType= parentType;
			fSelection= selection;
			if (parentType != null) {
				try {
					if (parentType.isInterface()) {
						setText(JavaUIMessages.getFormattedString("AddMethodStubAction.detailed.implement", parentType.getElementName())); //$NON-NLS-1$
					} else {
						setText(JavaUIMessages.getFormattedString("AddMethodStubAction.detailed.override", parentType.getElementName())); //$NON-NLS-1$
					}
				} catch (JavaModelException e) {
					JavaPlugin.log(e);
				}
			} else {
				setText(JavaUIMessages.getString("AddMethodStubAction.label")); //$NON-NLS-1$
			}
			return true;
		}
		fParentType= null;
		fSelection= null;
		return false;
	}	

	public void run() {
		if (!canActionBeAdded(fParentType, fSelection)) {
			return;
		}
		
		Shell shell= JavaPlugin.getActiveWorkbenchShell();
		try {		
			// open an editor and work on a working copy
			IEditorPart editor= EditorUtility.openInEditor(fParentType);
			IType usedType= (IType)EditorUtility.getWorkingCopy(fParentType);
			if (usedType == null) {
				MessageDialog.openError(shell, JavaUIMessages.getString("AddMethodStubAction.error.title"), JavaUIMessages.getString("AddMethodStubAction.error.type_removed_in_editor")); //$NON-NLS-2$ //$NON-NLS-1$
				return;
			}
			
			CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings();

			List list= ((IStructuredSelection)fSelection).toList();	
			IMethod[] methods= (IMethod[]) list.toArray(new IMethod[list.size()]); 
			AddMethodStubOperation op= new AddMethodStubOperation(usedType, methods, settings, createOverrideQuery(), createReplaceQuery(), false);
		
			ProgressMonitorDialog dialog= new ProgressMonitorDialog(shell);
			dialog.run(false, true, new WorkbenchRunnableAdapter(op));
			IMethod[] res= op.getCreatedMethods();
			if (res != null && res.length > 0 && editor != null) {
				EditorUtility.revealInEditor(editor, res[0]);
			}
		} catch (InvocationTargetException e) {
			MessageDialog.openError(shell, JavaUIMessages.getString("AddMethodStubAction.error.title"), e.getTargetException().getMessage()); //$NON-NLS-1$
			JavaPlugin.log(e.getTargetException());
		} catch (CoreException e) {
			ErrorDialog.openError(shell, JavaUIMessages.getString("AddMethodStubAction.error.title"), null, e.getStatus()); //$NON-NLS-1$
			JavaPlugin.log(e.getStatus());
		} catch (InterruptedException e) {
			// Do nothing. Operation has been canceled by user.
		}
	}
	
	private IRequestQuery createOverrideQuery() {
		return new IRequestQuery() {
			public int doQuery(IMember method) {
				String methodName= JavaElementLabels.getElementLabel(method, JavaElementLabels.M_PARAMETER_TYPES);
				String declTypeName= JavaElementLabels.getElementLabel(method.getDeclaringType(), 0);
				String formattedMessage;
				try {
					if (Flags.isFinal(method.getFlags())) {
						formattedMessage= JavaUIMessages.getFormattedString("AddMethodStubAction.OverridesFinalDialog.message", new String[] { methodName, declTypeName }); //$NON-NLS-1$
					} else {
						formattedMessage= JavaUIMessages.getFormattedString("AddMethodStubAction.OverridesPrivateDialog.message", new String[] { methodName, declTypeName }); //$NON-NLS-1$
					}
				} catch (JavaModelException e) {
					JavaPlugin.log(e.getStatus());
					return IRequestQuery.CANCEL;
				}
				return showQueryDialog(formattedMessage);	
			}
		};
	}
	
	private IRequestQuery createReplaceQuery() {
		return new IRequestQuery() {
			public int doQuery(IMember method) {
				String methodName= JavaElementLabels.getElementLabel(method, JavaElementLabels.M_PARAMETER_TYPES);
				String formattedMessage= JavaUIMessages.getFormattedString("AddMethodStubAction.ReplaceExistingDialog.message", methodName); //$NON-NLS-1$
				return showQueryDialog(formattedMessage);	
			}
		};
	}
	
	
	private int showQueryDialog(final String message) {
		int[] returnCodes= {IRequestQuery.YES, IRequestQuery.NO, IRequestQuery.YES_ALL, IRequestQuery.CANCEL};
		final Shell shell= JavaPlugin.getActiveWorkbenchShell();
		if (shell == null) {
			JavaPlugin.logErrorMessage("AddMethodStubAction.showQueryDialog: No active shell found");
			return IRequestQuery.CANCEL;
		}
		final int[] result= { MessageDialog.CANCEL };
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				String title= JavaUIMessages.getString("AddMethodStubAction.QueryDialog.title"); //$NON-NLS-1$
				String[] options= {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.YES_TO_ALL_LABEL, IDialogConstants.CANCEL_LABEL};
				MessageDialog dialog= new MessageDialog(shell, title, null, message, MessageDialog.QUESTION, options, 0);
				result[0]= dialog.open();				
			}
		});
		int returnVal= result[0];
		return returnVal < 0 ? IRequestQuery.CANCEL : returnCodes[returnVal];
	}	
	
	/**
	  * Tests if the action can run with given arguments
	 */
	public static boolean canActionBeAdded(IType parentType, ISelection selection) {
		if (parentType == null || parentType.getCompilationUnit() == null ||
				!(selection instanceof IStructuredSelection) || selection.isEmpty()) {
			return false;
		}
		Object[] elems= ((IStructuredSelection)selection).toArray();
		int nSelected= elems.length;
		if (nSelected > 0) {
			for (int i= 0; i < nSelected; i++) {
				Object elem= elems[i];
				if (!(elem instanceof IMethod)) {
					return false;
				}
				IMethod meth= (IMethod)elem;
				if (meth.getDeclaringType().equals(parentType)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	

}