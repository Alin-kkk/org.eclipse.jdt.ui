package org.eclipse.jdt.ui.actions;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IEditorActionBarContributor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.part.EditorActionBarContributor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation.IChooseImportQuery;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.TypeInfo;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.MultiElementListSelectionDialog;
import org.eclipse.jdt.internal.ui.dialogs.ProblemDialog;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.preferences.ImportOrganizePreferencePage;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.TypeInfoLabelProvider;

public class OrganizeImportsAction extends SelectionDispatchAction {
	
	private JavaEditor fEditor;

	/* (non-Javadoc)
	 * Class implements IObjectActionDelegate
	 */
	public static class ObjectDelegate implements IObjectActionDelegate {
		private OrganizeImportsAction fAction;
		public void setActivePart(IAction action, IWorkbenchPart targetPart) {
			fAction= new OrganizeImportsAction(targetPart.getSite());
		}
		public void run(IAction action) {
			fAction.run();
		}
		public void selectionChanged(IAction action, ISelection selection) {
			if (fAction == null)
				action.setEnabled(false);
		}
	}

	public OrganizeImportsAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.getString("OrganizeImportsAction.label")); //$NON-NLS-1$
		setToolTipText(ActionMessages.getString("OrganizeImportsAction.tooltip")); //$NON-NLS-1$
		setDescription(ActionMessages.getString("OrganizeImportsAction.description")); //$NON-NLS-1$

		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.ORGANIZE_IMPORTS_ACTION);					
	}
	
	/**
	 * Creates a new <code>OrganizeImportsAction</code>.
	 * <p>
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * </p>
	 */
	public OrganizeImportsAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void selectionChanged(ITextSelection selection) {
		boolean isEnabled= false;
		try {
			if (fEditor != null) {
				IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
				ICompilationUnit cu= manager.getWorkingCopy(fEditor.getEditorInput());
				isEnabled= JavaModelUtil.isEditable(cu);
			}
		} catch (JavaModelException e) {
		}
		setEnabled(isEnabled);
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		ICompilationUnit[] cus= getCompilationUnits(selection);
		boolean isEnabled= cus.length > 0;
		try {
			for (int i= 0; i < cus.length; i++) {
				if (!JavaModelUtil.isEditable(cus[i])) {
					isEnabled= false;
					break;
				}
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
		setEnabled(isEnabled);
	}
	
	private ICompilationUnit[] getCompilationUnits(IStructuredSelection selection) {
		HashSet result= new HashSet();
		Object[] selected= selection.toArray();
		for (int i= 0; i < selected.length; i++) {
			try {
				if (selected[i] instanceof IJavaElement) {
					IJavaElement elem= (IJavaElement) selected[i];
					switch (elem.getElementType()) {
						case IJavaElement.COMPILATION_UNIT:
							result.add(elem);
							break;
						case IJavaElement.IMPORT_CONTAINER:
							result.add(elem.getParent());
							break;
						case IJavaElement.PACKAGE_FRAGMENT:
							IPackageFragment pack= (IPackageFragment) elem;
							result.addAll(Arrays.asList(pack.getCompilationUnits()));
							break;
					}
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
		return (ICompilationUnit[]) result.toArray(new ICompilationUnit[result.size()]);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void run(ITextSelection selection) {
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		ICompilationUnit cu= manager.getWorkingCopy(fEditor.getEditorInput());
		runOnSingle(cu, true);
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void run(IStructuredSelection selection) {
		ICompilationUnit[] cus= getCompilationUnits(selection);
		if (cus.length == 1) {
			runOnSingle(cus[0], true);
		} else {
			runOnMultiple(cus, true);
		}
	}


	private void runOnMultiple(final ICompilationUnit[] cus, final boolean doResolve) {
		try {
			String message= ActionMessages.getString("OrganizeImportsAction.multi.status.description"); //$NON-NLS-1$
			final MultiStatus status= new MultiStatus(JavaUI.ID_PLUGIN, Status.OK, message, null);
			
			ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());
			dialog.run(false, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					doRunOnMultiple(cus, status, doResolve, monitor);
				}
			});
			if (!status.isOK()) {
				String title= ActionMessages.getString("OrganizeImportsAction.multi.status.title"); //$NON-NLS-1$
				ProblemDialog.open(getShell(), title, null, status);
			}
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.getString("OrganizeImportsAction.error.title"), ActionMessages.getString("OrganizeImportsAction.error.message")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (InterruptedException e) {
			// cancelled by user
		}		
		
	}
	
	private void doRunOnMultiple(ICompilationUnit[] cus, MultiStatus status, boolean doResolve, IProgressMonitor monitor) throws InterruptedException {
	
		final class OrganizeImportError extends Error {
		}

		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}	
	
		monitor.beginTask(ActionMessages.getString("OrganizeImportsAction.multi.op.description"), cus.length); //$NON-NLS-1$
		try {
			String[] prefOrder= ImportOrganizePreferencePage.getImportOrderPreference();
			int threshold= ImportOrganizePreferencePage.getImportNumberThreshold();
			boolean ignoreLowerCaseNames= ImportOrganizePreferencePage.doIgnoreLowerCaseNames();
	
			IChooseImportQuery query= new IChooseImportQuery() {
				public TypeInfo[] chooseImports(TypeInfo[][] openChoices, ISourceRange[] ranges) {
					throw new OrganizeImportError();
				}
			};
	
			for (int i= 0; i < cus.length; i++) {
				ICompilationUnit cu= cus[i];
				try {
					if (!cu.isWorkingCopy()) {
						ICompilationUnit workingCopy= EditorUtility.getWorkingCopy(cu);
						if (workingCopy != null) {
							cu= workingCopy;
						}
					}
					OrganizeImportsOperation op= new OrganizeImportsOperation(cu, prefOrder, threshold, ignoreLowerCaseNames, !cu.isWorkingCopy(), doResolve, query);
					op.run(new SubProgressMonitor(monitor, 1));
					ISourceRange errorRange= op.getErrorSourceRange();
					if (errorRange != null) {
						String message= ActionMessages.getFormattedString("OrganizeImportsAction.multi.error.parse", cu.getElementName()); //$NON-NLS-1$
						status.add(new Status(Status.INFO, JavaUI.ID_PLUGIN, Status.ERROR, message, null));
					} 
				} catch (OrganizeImportError e) {
					String message= ActionMessages.getFormattedString("OrganizeImportsAction.multi.error.unresolvable", cu.getElementName()); //$NON-NLS-1$
					status.add(new Status(Status.INFO, JavaUI.ID_PLUGIN, Status.ERROR, message, null));					
				} catch (CoreException e) {
					JavaPlugin.log(e);
					String message= ActionMessages.getFormattedString("OrganizeImportsAction.multi.error.unexpected", e.getMessage()); //$NON-NLS-1$
					status.add(new Status(Status.ERROR, JavaUI.ID_PLUGIN, Status.ERROR, message, null));					
				} catch (OperationCanceledException e) {
					throw new InterruptedException();
				}
			}
		} finally {
			monitor.done();
		}
	}
				

	private void runOnSingle(ICompilationUnit cu, boolean doResolve) {
		try {
			String[] prefOrder= ImportOrganizePreferencePage.getImportOrderPreference();
			int threshold= ImportOrganizePreferencePage.getImportNumberThreshold();
			boolean ignoreLowerCaseNames= ImportOrganizePreferencePage.doIgnoreLowerCaseNames();
			
			if (!cu.isWorkingCopy()) {
				IEditorPart editor= EditorUtility.openInEditor(cu);
				if (editor instanceof JavaEditor) {
					fEditor= (JavaEditor) editor;
				}
				
				ICompilationUnit workingCopy= EditorUtility.getWorkingCopy(cu);
				if (workingCopy != null) {
					cu= workingCopy;
				}
			}			
			
			OrganizeImportsOperation op= new OrganizeImportsOperation(cu, prefOrder, threshold, ignoreLowerCaseNames, !cu.isWorkingCopy(), doResolve, createChooseImportQuery());
		
			BusyIndicatorRunnableContext context= new BusyIndicatorRunnableContext();
			context.run(false, true, new WorkbenchRunnableAdapter(op));
			ISourceRange errorRange= op.getErrorSourceRange();
			if (errorRange != null) {
				MessageDialog.openInformation(getShell(), ActionMessages.getString("OrganizeImportsAction.error.title"), ActionMessages.getString("OrganizeImportsAction.single.error.parse")); //$NON-NLS-1$ //$NON-NLS-2$
				if (fEditor != null) {
					fEditor.selectAndReveal(errorRange.getOffset(), errorRange.getLength());
				}
			} else {
				setStatusBarMessage(getOrganizeInfo(op));
			}
		} catch (CoreException e) {	
			ExceptionHandler.handle(e, getShell(), ActionMessages.getString("OrganizeImportsAction.error.title"), ActionMessages.getString("OrganizeImportsAction.error.message")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.getString("OrganizeImportsAction.error.title"), ActionMessages.getString("OrganizeImportsAction.error.message")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (InterruptedException e) {
		}
	}
	
	private String getOrganizeInfo(OrganizeImportsOperation op) {
		int nImportsAdded= op.getNumberOfImportsAdded();
		if (nImportsAdded >= 0) {
			return ActionMessages.getFormattedString("OrganizeImportsAction.summary_added", String.valueOf(nImportsAdded)); //$NON-NLS-1$
		} else {
			return ActionMessages.getFormattedString("OrganizeImportsAction.summary_removed", String.valueOf(-nImportsAdded)); //$NON-NLS-1$
		}
	}
		
	private IChooseImportQuery createChooseImportQuery() {
		return new IChooseImportQuery() {
			public TypeInfo[] chooseImports(TypeInfo[][] openChoices, ISourceRange[] ranges) {
				return doChooseImports(openChoices, ranges);
			}
		};
	}
	
	private TypeInfo[] doChooseImports(TypeInfo[][] openChoices, final ISourceRange[] ranges) {
		// remember selection
		ISelection sel= fEditor.getSelectionProvider().getSelection();
		TypeInfo[] result= null;;
		ILabelProvider labelProvider= new TypeInfoLabelProvider(TypeInfoLabelProvider.SHOW_FULLYQUALIFIED);
		
		MultiElementListSelectionDialog dialog= new MultiElementListSelectionDialog(getShell(), labelProvider) {
			protected void handleSelectionChanged() {
				super.handleSelectionChanged();
				// show choices in editor
				doListSelectionChanged(getCurrentPage(), ranges);
			}
		};
		dialog.setTitle(ActionMessages.getString("OrganizeImportsAction.selectiondialog.title")); //$NON-NLS-1$
		dialog.setMessage(ActionMessages.getString("OrganizeImportsAction.selectiondialog.message")); //$NON-NLS-1$
		dialog.setElements(openChoices);
		if (dialog.open() == dialog.OK) {
			Object[] res= dialog.getResult();			
			result= new TypeInfo[res.length];
			for (int i= 0; i < res.length; i++) {
				Object[] array= (Object[]) res[i];
				if (array.length > 0)
					result[i]= (TypeInfo) array[0];
			}
		}
		// restore selection
		if (sel instanceof ITextSelection) {
			ITextSelection textSelection= (ITextSelection) sel;
			fEditor.selectAndReveal(textSelection.getOffset(), textSelection.getLength());
		}
		return result;
	}
	
	private void doListSelectionChanged(int page, ISourceRange[] ranges) {
		if (page >= 0 && page < ranges.length) {
			ISourceRange range= ranges[page];
			fEditor.selectAndReveal(range.getOffset(), range.getLength());
		}
	}
	
	private void setStatusBarMessage(String message) {
		IEditorActionBarContributor contributor= fEditor.getEditorSite().getActionBarContributor();
		if (contributor instanceof EditorActionBarContributor) {
			IStatusLineManager manager= ((EditorActionBarContributor) contributor).getActionBars().getStatusLineManager();
			manager.setMessage(message);
		}
	}
	
}
