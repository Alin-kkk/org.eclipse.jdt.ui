package org.eclipse.jdt.internal.ui.typehierarchy;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

public class HistoryListAction extends Action {
	
	private class HistoryListDialog extends StatusDialog {
		
		private ListDialogField fHistoryList;
		private IStatus fHistoryStatus;
		private IJavaElement fResult;
		
		private HistoryListDialog(Shell shell, IJavaElement[] elements) {
			super(shell);
			setTitle(TypeHierarchyMessages.getString("HistoryListDialog.title"));
			
			String[] buttonLabels= new String[] { 
				/* 0 */ TypeHierarchyMessages.getString("HistoryListDialog.remove.button") //$NON-NLS-1$
			};
					
			IListAdapter adapter= new IListAdapter() {
				public void customButtonPressed(DialogField field, int index) {
				}
				public void selectionChanged(DialogField field) {
					doSelectionChanged();
				}
			};
		
			JavaElementLabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_QUALIFIED | JavaElementLabelProvider.SHOW_ROOT);
			
			fHistoryList= new ListDialogField(adapter, buttonLabels, labelProvider);
			fHistoryList.setLabelText(TypeHierarchyMessages.getString("HistoryListDialog.label")); //$NON-NLS-1$
			fHistoryList.setRemoveButtonIndex(0);
			fHistoryList.setElements(Arrays.asList(elements));
			
			ISelection sel;
			if (elements.length > 0) {
				sel= new StructuredSelection(elements[0]);
			} else {
				sel= new StructuredSelection();
			}
			
			fHistoryList.selectElements(sel);
		}
		
		/*
		 * @see Dialog#createDialogArea(Composite)
		 */
		protected Control createDialogArea(Composite parent) {
			initializeDialogUnits(parent);
			
			Composite composite= (Composite) super.createDialogArea(parent);
			
			Composite inner= new Composite(composite, SWT.NONE);
			inner.setLayoutData(new GridData(GridData.FILL));

			LayoutUtil.doDefaultLayout(inner, new DialogField[] { fHistoryList }, true, 0, 0);
			LayoutUtil.setHeigthHint(fHistoryList.getListControl(null), convertHeightInCharsToPixels(12));
			LayoutUtil.setHorizontalGrabbing(fHistoryList.getListControl(null));

				
			fHistoryList.getTableViewer().addDoubleClickListener(new IDoubleClickListener() {
				public void doubleClick(DoubleClickEvent event) {
					if (fHistoryStatus.isOK()) {
						okPressed();
					}
				}
			});
			return composite;
		}
		
		private void doSelectionChanged() {
			StatusInfo status= new StatusInfo();
			List selected= fHistoryList.getSelectedElements();
			if (selected.size() != 1) {
				status.setError("");
				fResult= null;
			} else {
				fResult= (IJavaElement) selected.get(0);
			}
			fHistoryStatus= status;
			updateStatus(status);	
		}
				
		public IJavaElement getResult() {
			return fResult;
		}
		
		public IJavaElement[] getRemaining() {
			List elems= fHistoryList.getElements();
			return (IJavaElement[]) elems.toArray(new IJavaElement[elems.size()]);
		}	
		
	}
	
	private TypeHierarchyViewPart fView;
	
	public HistoryListAction(TypeHierarchyViewPart view) {
		fView= view;
		setText(TypeHierarchyMessages.getString("HistoryListAction.label"));
		JavaPluginImages.setLocalImageDescriptors(this, "history_list.gif"); //$NON-NLS-1$
	}
	
		
	/*
	 * @see IAction#run()
	 */
	public void run() {
		IJavaElement[] historyEntries= fView.getHistoryEntries();
		HistoryListDialog dialog= new HistoryListDialog(JavaPlugin.getActiveWorkbenchShell(), historyEntries);
		if (dialog.open() == dialog.OK) {
			fView.setHistoryEntries(dialog.getRemaining());
			fView.setInputElement(dialog.getResult());
		}
	}

}

