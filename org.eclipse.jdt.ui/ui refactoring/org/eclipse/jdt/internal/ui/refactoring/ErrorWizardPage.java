/*
 * (c) Copyright IBM Corp. 2000, 2001. 
 * All Rights Reserved.
 */

package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.help.DialogPageContextComputer;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.texteditor.IDocumentProvider;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.internal.corext.refactoring.base.FileContext;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaSourceContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry.Context;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.InternalClassFileEditorInput;
import org.eclipse.jdt.internal.ui.refactoring.SourceContextViewer.SourceContextInput;

import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

/**
 * Presents the list of failed preconditions to the user
 */
public class ErrorWizardPage extends RefactoringWizardPage {
		
	private static class NullContextViewer implements IErrorContextViewer {
		private Label fLabel;
		public NullContextViewer(Composite parent) {
			fLabel= new Label(parent, SWT.CENTER | SWT.FLAT);
			fLabel.setText("No context information available");
		}
		public void setInput(Object input) {
			// do nothing
		}
		public Control getControl() {
			return fLabel;
		}
	}
	
	public static final String PAGE_NAME= "ErrorPage"; //$NON-NLS-1$
	
	private RefactoringStatus fStatus;
	private TableViewer fTableViewer;
	private Label fContextLabel;
	private PageBook fContextViewerContainer;
	private IErrorContextViewer fCurrentContextViewer;
	private SourceContextViewer fSourceViewer;
	private NullContextViewer fNullContextViewer;
	
	private final String fHelpContextID;

	private static final IDocument EMPTY_DOCUMENT= new Document("");
		
	public ErrorWizardPage(String helpContextId){
		super(PAGE_NAME);
		fHelpContextID= helpContextId;
	}
	
	/**
	 * Sets the page's refactoring status to the given value.
	 * @param status the refactoring status.
	 */
	public void setStatus(RefactoringStatus status){
		fStatus= status;
		if (fStatus != null) {
			setPageComplete(isRefactoringPossible());
			int severity= fStatus.getSeverity();
			if (severity >= RefactoringStatus.FATAL) {
				setDescription(RefactoringMessages.getString("ErrorWizardPage.cannot_proceed")); //$NON-NLS-1$
			} else if (severity >= RefactoringStatus.INFO) {
				setDescription(RefactoringMessages.getString("ErrorWizardPage.confirm")); //$NON-NLS-1$
			} else {
				setDescription(""); //$NON-NLS-1$
			}
		} else {
			setPageComplete(true);
			setDescription(""); //$NON-NLS-1$
		}	
	}
	
	/**
	 * Returns a viewer used to show a context for the given status entry. The returned viewer
	 * is kept referenced until the wizard page gets disposed. So it is up to the implementor of this
	 * method to reuse existing viewers over different staus entries. The method may return
	 * <code>nulll</code> indicating that no context is available for the given status entry.
	 * 
	 * @param entry the <code>RefactoringStatusEntry</code> for which the context viewer is requested
	 * @param currentViewer the currently used error context viewer
	 * @param parent the parent to be used if a new viewer must be created
	 * @return the viewer to show a context for the given status entry
	 */
	protected IErrorContextViewer getErrorContextViewer(RefactoringStatusEntry entry, IErrorContextViewer currentViewer, Composite parent) {
		return null;
	}
	
	/**
	 * Returns the <code>SourceContextInput</code> if the context for the given status entry
	 * can be displayed using a </code>SourceContextViewer</code>. The method may return
	 * <code>null</code> indicating that the context for the given status entry is not compatible
	 * with a source viewer.
	 * 
	 * @param entry the <code>RefactoringStatusEntry</code>
	 * @return a input element for a <code>SourceContextViewer</code>
	 */
	protected SourceContextInput getSourceContextInput(RefactoringStatusEntry entry) {
		Context context= entry.getContext();
		if (context == Context.NULL_CONTEXT)
			return null;
		IEditorInput editorInput= null;
		SourceViewerConfiguration configuration= null;
		IDocumentProvider provider= null;
		ISourceRange range= null;
		try {
			if (context instanceof FileContext) {
				FileContext fc= (FileContext)context;
				editorInput= new FileEditorInput(fc.getFile());
				configuration= new SourceViewerConfiguration();
				provider= JavaPlugin.getDefault().getCompilationUnitDocumentProvider();
				range= fc.getSourceRange();
			} else if (context instanceof JavaSourceContext) {
				JavaSourceContext jsc= (JavaSourceContext)context;
				range= jsc.getSourceRange();
				if (jsc.isBinary()) {
					editorInput= new InternalClassFileEditorInput(jsc.getClassFile());
					configuration= new JavaSourceViewerConfiguration(getJavaTextTools(), null);
					provider= JavaPlugin.getDefault().getClassFileDocumentProvider();
				} else {
					ICompilationUnit cunit= jsc.getCompilationUnit();
					if (cunit.isWorkingCopy())
						cunit= (ICompilationUnit)cunit.getOriginalElement();
					editorInput= new FileEditorInput((IFile)cunit.getUnderlyingResource());
					configuration= new JavaSourceViewerConfiguration(getJavaTextTools(), null);
					provider= JavaPlugin.getDefault().getCompilationUnitDocumentProvider();
				}
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
		IDocument document= null;
		if (editorInput != null) {
			try {
				provider.connect(editorInput);
				document=provider.getDocument(editorInput);
			} catch (CoreException e) {
			} finally {
				provider.disconnect(editorInput);
			}
		}
		if (document == null || configuration == null)
			return null;
		return new SourceContextInput(document, configuration, range);
	}
		 
	//---- UI creation ----------------------------------------------------------------------
	
	/* (non-Javadoc)
	 * Method declared in IWizardPage.
	 */
	public void createControl(Composite parent) {
		// Additional composite is needed to limit the size of the table to
		// 60 characters.
		//Composite content= new Composite(parent, SWT.NONE);
		SashForm content= new SashForm(parent, SWT.VERTICAL);
		GridLayout layout= new GridLayout();
		layout.numColumns= 1; layout.marginWidth= 0; layout.marginHeight= 0;
		content.setLayout(layout);
		
		createTableViewer(content);
		fContextViewerContainer= new PageBook(content, SWT.NONE);
		fNullContextViewer= new NullContextViewer(fContextViewerContainer);
		fSourceViewer= new SourceContextViewer(fContextViewerContainer);
		fContextViewerContainer.showPage(fNullContextViewer.getControl());
		fCurrentContextViewer= fNullContextViewer;	
		
		content.setWeights(new int[]{35, 65});
		setControl(content);
		WorkbenchHelp.setHelp(getControl(), new DialogPageContextComputer(this, fHelpContextID));			
	}
	
	private  void createTableViewer(Composite parent) {
		fTableViewer= new TableViewer(new Table(parent, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL));
		fTableViewer.setLabelProvider(new RefactoringStatusEntryLabelProvider());
		fTableViewer.setContentProvider(new RefactoringStatusContentProvider());
		fTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				entrySelected(event.getSelection());
			}
		});	
		Table tableControl= fTableViewer.getTable();
		initializeDialogUnits(tableControl);
		GridData gd= new GridData(GridData.FILL_BOTH);
		gd.widthHint= convertWidthInCharsToPixels(60);
		tableControl.setLayoutData(gd);
		// Add a column so that we can pack it in setVisible.
		TableColumn tc= new TableColumn(tableControl, SWT.NONE);
		tc.setResizable(false);
	}

	//---- Feed status entry into context viewer ---------------------------------------------------------

	private void entrySelected(ISelection s) {
		if (!(s instanceof IStructuredSelection))
			return;
		Object first= ((IStructuredSelection) s).getFirstElement();
		if (! (first instanceof RefactoringStatusEntry))
			return;
			
		showInSourceViewer((RefactoringStatusEntry)first);
	}

	private void showInSourceViewer(RefactoringStatusEntry entry) {
		SourceContextInput input= getSourceContextInput(entry);
		if (input != null) {
			fSourceViewer.setInput(input);
			fCurrentContextViewer= fSourceViewer;
		} else {
			fCurrentContextViewer= getErrorContextViewer(entry, fCurrentContextViewer, fContextViewerContainer);
			if (fCurrentContextViewer == null)
				fCurrentContextViewer= fNullContextViewer;
		}
		fContextViewerContainer.showPage(fCurrentContextViewer.getControl());
	}
	
	//---- Reimplementation of WizardPage methods ------------------------------------------

	/* (non-Javadoc)
	 * Method declared on IDialog.
	 */
	public void setVisible(boolean visible) {
		if (visible && fTableViewer.getInput() != fStatus) {
			fTableViewer.setInput(fStatus);
			fTableViewer.getTable().getColumn(0).pack();
			ISelection selection= fTableViewer.getSelection();
			if (selection.isEmpty()) {
				RefactoringStatusEntry entry= getFirstEntry();
				if (entry != null) {
					fTableViewer.setSelection(new StructuredSelection(entry));
					showInSourceViewer(entry);
					fTableViewer.getControl().setFocus();
				}
			}
		}
		super.setVisible(visible);
	}
	
	/* (non-Javadoc)
	 * Method declared in IWizardPage.
	 */
	public boolean canFlipToNextPage() {
		// We have to call super.getNextPage since computing the next
		// page is expensive. So we avoid it as long as possible.
		return fStatus != null && isRefactoringPossible() &&
			   isPageComplete() && super.getNextPage() != null;
	}
	
	/* (non-Javadoc)
	 * Method declared in IWizardPage.
	 */
	public IWizardPage getNextPage() {
		RefactoringWizard wizard= getRefactoringWizard();
		IChange change= wizard.getChange();
		if (change == null) {
			change= wizard.createChange(CreateChangeOperation.CHECK_NONE, RefactoringStatus.ERROR, false);
			wizard.setChange(change);
		}
		if (change == null)
			return this;
			
		return super.getNextPage();
	}
	
	/* (non-JavaDoc)
	 * Method defined in RefactoringWizardPage
	 */
	protected boolean performFinish() {
		RefactoringWizard wizard= getRefactoringWizard();
		IChange change= wizard.getChange();
		PerformChangeOperation op= null;
		if (change != null) {
			op= new PerformChangeOperation(change);
		} else {
			CreateChangeOperation ccop= new CreateChangeOperation(getRefactoring(), CreateChangeOperation.CHECK_NONE);
			ccop.setCheckPassedSeverity(RefactoringStatus.ERROR);
			
			op= new PerformChangeOperation(ccop);
			op.setCheckPassedSeverity(RefactoringStatus.ERROR);
		}
		return wizard.performFinish(op);
	} 
	
	//---- Helpers ----------------------------------------------------------------------------------------
	
	private RefactoringStatusEntry getFirstEntry(){
		if (fStatus == null || fStatus.getEntries().isEmpty())
			return null;
		return (RefactoringStatusEntry)fStatus.getEntries().get(0);
	}
		
	private boolean isRefactoringPossible() {
		return fStatus.getSeverity() < RefactoringStatus.FATAL;
	}
	
	private static JavaTextTools getJavaTextTools() {
		return JavaPlugin.getDefault().getJavaTextTools();	
	}			
}
