/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.IUIConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusDialog;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ComboDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;

public class LibrariesWorkbookPage extends BuildPathBasePage {
	
	private ListDialogField fClassPathList;
	private IJavaProject fCurrJProject;
	
	private ListDialogField fLibrariesList;
	private IWorkspaceRoot fWorkspaceRoot;
	
	private IDialogSettings fDialogSettings;
	
	private Control fSWTControl;
	
	private final int IDX_ADDJAR= 0;
	private final int IDX_ADDEXT= 1;
	private final int IDX_ADDVAR= 2;
	private final int IDX_ADDADV= 3;
	private final int IDX_EDIT= 5;
	private final int IDX_ATTACH= 6;
	
	private final int IDX_REMOVE= 8;
	
		
	public LibrariesWorkbookPage(IWorkspaceRoot root, ListDialogField classPathList) {
		fClassPathList= classPathList;
		fWorkspaceRoot= root;
		fSWTControl= null;
		
		fDialogSettings= JavaPlugin.getDefault().getDialogSettings();
		
		String[] buttonLabels= new String[] { 
			/* IDX_ADDJAR*/ NewWizardMessages.getString("LibrariesWorkbookPage.libraries.addjar.button"),	//$NON-NLS-1$
			/* IDX_ADDEXT */ NewWizardMessages.getString("LibrariesWorkbookPage.libraries.addextjar.button"), //$NON-NLS-1$
			/* IDX_ADDVAR */ NewWizardMessages.getString("LibrariesWorkbookPage.libraries.addvariable.button"), //$NON-NLS-1$
			/* IDX_ADDADV */ NewWizardMessages.getString("LibrariesWorkbookPage.libraries.advanced.button"), //$NON-NLS-1$
			/* */ null,  
			/* IDX_EDIT */ NewWizardMessages.getString("LibrariesWorkbookPage.libraries.edit.button"), //$NON-NLS-1$
			/* IDX_ATTACH */ NewWizardMessages.getString("LibrariesWorkbookPage.libraries.setsource.button"), //$NON-NLS-1$
			/* */ null,  
			/* IDX_REMOVE */ NewWizardMessages.getString("LibrariesWorkbookPage.libraries.remove.button") //$NON-NLS-1$
		};		
				
		LibrariesAdapter adapter= new LibrariesAdapter();
				
		fLibrariesList= new ListDialogField(adapter, buttonLabels, new CPListLabelProvider());
		fLibrariesList.setDialogFieldListener(adapter);
		fLibrariesList.setLabelText(NewWizardMessages.getString("LibrariesWorkbookPage.libraries.label")); //$NON-NLS-1$
		fLibrariesList.setRemoveButtonIndex(IDX_REMOVE); //$NON-NLS-1$
	
		fLibrariesList.enableButton(IDX_EDIT, false);
		fLibrariesList.enableButton(IDX_ATTACH, false);
		
		fLibrariesList.setViewerSorter(new CPListElementSorter());

	}
		
	public void init(IJavaProject jproject) {
		fCurrJProject= jproject;
		updateLibrariesList();
	}
	
	private boolean isLibraryKind(int kind) {
		return kind == IClasspathEntry.CPE_LIBRARY || kind == IClasspathEntry.CPE_VARIABLE || kind == IClasspathEntry.CPE_CONTAINER;
	}
	
	private void updateLibrariesList() {
		List cpelements= fClassPathList.getElements();
		List libelements= new ArrayList(cpelements.size());
		
		int nElements= cpelements.size();
		for (int i= 0; i < nElements; i++) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			if (isLibraryKind(cpe.getEntryKind())) {
				libelements.add(cpe);
			}
		}
		fLibrariesList.setElements(libelements);
	}		
		
	// -------- ui creation
	
	public Control getControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);
		
		Composite composite= new Composite(parent, SWT.NONE);
			
		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fLibrariesList }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(fLibrariesList.getListControl(null));
		
		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		fLibrariesList.setButtonsMinWidth(buttonBarWidth);
		
		fLibrariesList.getTableViewer().setSorter(new CPListElementSorter());
		
		fSWTControl= composite;
				
		return composite;
	}
	
	private Shell getShell() {
		if (fSWTControl != null) {
			return fSWTControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}
	
	
	private class LibrariesAdapter implements IDialogFieldListener, IListAdapter {
		
		// -------- IListAdapter --------
		public void customButtonPressed(DialogField field, int index) {
			libaryPageCustomButtonPressed(field, index);
		}
		
		public void selectionChanged(DialogField field) {
			libaryPageSelectionChanged(field);
		}
			
		// ---------- IDialogFieldListener --------
	
		public void dialogFieldChanged(DialogField field) {
			libaryPageDialogFieldChanged(field);
		}
	}
	
	private void libaryPageCustomButtonPressed(DialogField field, int index) {
		CPListElement[] libentries= null;
		switch (index) {
		case IDX_ADDJAR: /* add jar */
			libentries= openJarFileDialog(null);
			break;
		case IDX_ADDEXT: /* add external jar */
			libentries= openExtJarFileDialog(null);
			break;
		case IDX_ADDVAR: /* add variable */
			libentries= openVariableSelectionDialog(null);
			break;
		case IDX_ADDADV: /* addvanced */
			AdvancedDialog advDialog= new AdvancedDialog(getShell());
			if (advDialog.open() == advDialog.OK) {
				libentries= advDialog.getResult();
			}
			break;
		case IDX_ATTACH: /* set source attachment */
			List selElements= fLibrariesList.getSelectedElements();
			CPListElement selElement= (CPListElement) selElements.get(0);				
			SourceAttachmentDialog dialog= new SourceAttachmentDialog(getShell(), fWorkspaceRoot, selElement.getClasspathEntry());
			if (dialog.open() == dialog.OK) {
				selElement.setSourceAttachment(dialog.getSourceAttachmentPath(), dialog.getSourceAttachmentRootPath());
				fLibrariesList.refresh();
				fClassPathList.refresh();
			}
			break;
		case IDX_EDIT: /* edit */
			editEntry();
			return;
		}
		if (libentries != null) {
			int nElementsChosen= libentries.length;					
			// remove duplicates
			List cplist= fLibrariesList.getElements();
			List elementsToAdd= new ArrayList(nElementsChosen);
			
			for (int i= 0; i < nElementsChosen; i++) {
				CPListElement curr= libentries[i];
				if (!cplist.contains(curr) && !elementsToAdd.contains(curr)) {
					elementsToAdd.add(curr);
					addAttachmentsFromExistingLibs(curr);
				}
			}
			fLibrariesList.addElements(elementsToAdd);
			fLibrariesList.postSetSelection(new StructuredSelection(libentries));
		}
	}

	/**
	 * Method editEntry.
	 */
	private void editEntry() {
		List selElements= fLibrariesList.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		CPListElement elem= (CPListElement) selElements.get(0);
		CPListElement[] res= null;
		
		switch (elem.getEntryKind()) {
		case IClasspathEntry.CPE_CONTAINER:
			String title= NewWizardMessages.getString("LibrariesWorkbookPage.ContainerDialog.edit.title"); //$NON-NLS-1$
			res= openContainerDialog(title, new ClasspathContainerWizard(elem.getClasspathEntry()));
			break;
		case IClasspathEntry.CPE_LIBRARY:
			IResource resource= elem.getResource();
			if (resource == null) {
				res= openExtJarFileDialog(elem);
			} else if (resource.getType() == IResource.FOLDER) {
				if (resource.exists()) {
					res= openClassFolderDialog(elem);
				} else {
					res= openNewClassFolderDialog(elem);
				} 
			} else if (resource.getType() == IResource.FILE) {
				res= openJarFileDialog(elem);			
			}
			break;
		case IClasspathEntry.CPE_VARIABLE:
			res= openVariableSelectionDialog(elem);
			break;
		}
		if (res != null) {
			fLibrariesList.replaceElement(elem, res[0]);
		}		
			
	}


	
	private void libaryPageSelectionChanged(DialogField field) {
		List selElements= fLibrariesList.getSelectedElements();
		fLibrariesList.enableButton(IDX_ATTACH, canDoSourceAttachment(selElements));
		fLibrariesList.enableButton(IDX_EDIT, selElements.size() == 1);
	}
	
	private void libaryPageDialogFieldChanged(DialogField field) {
		if (fCurrJProject != null) {
			// already initialized
			updateClasspathList();
		}
	}	
	
	private boolean canDoSourceAttachment(List selElements) {
		if (selElements != null && selElements.size() == 1) {
			CPListElement elem= (CPListElement) selElements.get(0);
			if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
				return (!(elem.getResource() instanceof IFolder));
			} else if (elem.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
				return true;
			}
		}
		return false;
	}		
	
	private void updateClasspathList() {
		List projelements= fLibrariesList.getElements();
		
		boolean remove= false;
		List cpelements= fClassPathList.getElements();
		// backwards, as entries will be deleted
		for (int i= cpelements.size() - 1; i >= 0; i--) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			int kind= cpe.getEntryKind();
			if (isLibraryKind(kind)) {
				if (!projelements.remove(cpe)) {
					cpelements.remove(i);
					remove= true;
				}	
			}
		}
		for (int i= 0; i < projelements.size(); i++) {
			cpelements.add(projelements.get(i));
		}
		if (remove || (projelements.size() > 0)) {
			fClassPathList.setElements(cpelements);
		}
	}
	
		
	private CPListElement[] openNewClassFolderDialog(CPListElement existing) {
		String title= (existing == null) ? NewWizardMessages.getString("LibrariesWorkbookPage.NewClassFolderDialog.new.title") : NewWizardMessages.getString("LibrariesWorkbookPage.NewClassFolderDialog.edit.title"); //$NON-NLS-1$ //$NON-NLS-2$
		IProject currProject= fCurrJProject.getProject();
		
		NewContainerDialog dialog= new NewContainerDialog(getShell(), title, currProject, getUsedContainers(existing), existing);
		IPath projpath= currProject.getFullPath();
		dialog.setMessage(NewWizardMessages.getFormattedString("LibrariesWorkbookPage.NewClassFolderDialog.description", projpath.toString())); //$NON-NLS-1$
		if (dialog.open() == dialog.OK) {
			IFolder folder= dialog.getFolder();
			return new CPListElement[] { newCPLibraryElement(folder) };
		}
		return null;
	}
			
			
	private CPListElement[] openClassFolderDialog(CPListElement existing) {	
		Class[] acceptedClasses= new Class[] { IFolder.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, existing == null);
			
		acceptedClasses= new Class[] { IProject.class, IFolder.class };

		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, getUsedContainers(existing));	
			
		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		String title= (existing == null) ? NewWizardMessages.getString("LibrariesWorkbookPage.ExistingClassFolderDialog.new.title") : NewWizardMessages.getString("LibrariesWorkbookPage.ExistingClassFolderDialog.edit.title"); //$NON-NLS-1$ //$NON-NLS-2$
		String message= (existing == null) ? NewWizardMessages.getString("LibrariesWorkbookPage.ExistingClassFolderDialog.new.description") : NewWizardMessages.getString("LibrariesWorkbookPage.ExistingClassFolderDialog.edit.description"); //$NON-NLS-1$ //$NON-NLS-2$

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getShell(), lp, cp);
		dialog.setValidator(validator);
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.addFilter(filter);
		dialog.setInput(fWorkspaceRoot);
		if (existing == null) {
			dialog.setInitialSelection(fCurrJProject.getProject());
		} else {
			dialog.setInitialSelection(existing.getResource());
		}
		
		if (dialog.open() == dialog.OK) {
			Object[] elements= dialog.getResult();
			CPListElement[] res= new CPListElement[elements.length];
			for (int i= 0; i < res.length; i++) {
				IResource elem= (IResource) elements[i];
				res[i]= newCPLibraryElement(elem);
			}
			return res;
		}
		return null;		
	}
	
	private CPListElement[] openJarFileDialog(CPListElement existing) {
		Class[] acceptedClasses= new Class[] { IFile.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, existing == null);
		ViewerFilter filter= new ArchiveFileFilter(getUsedJARFiles(existing));
		
		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		String title= (existing == null) ? NewWizardMessages.getString("LibrariesWorkbookPage.JARArchiveDialog.new.title") : NewWizardMessages.getString("LibrariesWorkbookPage.JARArchiveDialog.edit.title"); //$NON-NLS-1$ //$NON-NLS-2$
		String message= (existing == null) ? NewWizardMessages.getString("LibrariesWorkbookPage.JARArchiveDialog.new.description") : NewWizardMessages.getString("LibrariesWorkbookPage.JARArchiveDialog.edit.description"); //$NON-NLS-1$ //$NON-NLS-2$

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getShell(), lp, cp);
		dialog.setValidator(validator);
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.addFilter(filter);
		dialog.setInput(fWorkspaceRoot);
		if (existing == null) {
			dialog.setInitialSelection(fCurrJProject.getProject());		
		} else {
			dialog.setInitialSelection(existing.getResource());
		}

		if (dialog.open() == dialog.OK) {
			Object[] elements= dialog.getResult();
			CPListElement[] res= new CPListElement[elements.length];
			for (int i= 0; i < res.length; i++) {
				IResource elem= (IResource)elements[i];
				res[i]= newCPLibraryElement(elem);
			}
			return res;
		}
		return null;
	}
	
	private IContainer[] getUsedContainers(CPListElement existing) {
		ArrayList res= new ArrayList();
		if (fCurrJProject.exists()) {
			try {
				IPath outputLocation= fCurrJProject.getOutputLocation();
				if (outputLocation != null) {
					IResource resource= fWorkspaceRoot.findMember(outputLocation);
					if (resource instanceof IFolder) { // != Project
						res.add(resource);
					}
				}
			} catch (JavaModelException e) {
				// ignore it here, just log
				JavaPlugin.log(e.getStatus());
			}
		}	
			
		List cplist= fLibrariesList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= (CPListElement)cplist.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY && (elem != existing)) {
				IResource resource= elem.getResource();
				if (resource instanceof IContainer && !resource.equals(existing)) {
					res.add(resource);
				}
			}
		}
		return (IContainer[]) res.toArray(new IContainer[res.size()]);
	}
	
	private IFile[] getUsedJARFiles(CPListElement existing) {
		List res= new ArrayList();
		List cplist= fLibrariesList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= (CPListElement)cplist.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY && (elem != existing)) {
				IResource resource= elem.getResource();
				if (resource instanceof IFile) {
					res.add(resource);
				}
			}
		}
		return (IFile[]) res.toArray(new IFile[res.size()]);
	}	
	
	private CPListElement newCPLibraryElement(IResource res) {
		return new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, res.getFullPath(), res);
	};

	
	private CPListElement[] openExtJarFileDialog(CPListElement existing) {
		String lastUsedPath;
		if (existing != null) {
			lastUsedPath= existing.getPath().removeLastSegments(1).toOSString();
		} else {
			lastUsedPath= fDialogSettings.get(IUIConstants.DIALOGSTORE_LASTEXTJAR);
			if (lastUsedPath == null) {
				lastUsedPath= ""; //$NON-NLS-1$
			}
		}
		String title= (existing == null) ? NewWizardMessages.getString("LibrariesWorkbookPage.ExtJARArchiveDialog.new.title") : NewWizardMessages.getString("LibrariesWorkbookPage.ExtJARArchiveDialog.edit.title"); //$NON-NLS-1$ //$NON-NLS-2$
		
		FileDialog dialog= new FileDialog(getShell(), existing == null ? SWT.MULTI : SWT.SINGLE);
		dialog.setText(title);
		dialog.setFilterExtensions(new String[] {"*.jar;*.zip"}); //$NON-NLS-1$
		dialog.setFilterPath(lastUsedPath);
		if (existing != null) {
			dialog.setFileName(existing.getPath().lastSegment());
		}
		
		String res= dialog.open();
		if (res == null) {
			return null;
		}
		String[] fileNames= dialog.getFileNames();
		int nChosen= fileNames.length;
			
		IPath filterPath= new Path(dialog.getFilterPath());
		CPListElement[] elems= new CPListElement[nChosen];
		for (int i= 0; i < nChosen; i++) {
			IPath path= filterPath.append(fileNames[i]).makeAbsolute();	
			elems[i]= new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, path, null);
		}
		fDialogSettings.put(IUIConstants.DIALOGSTORE_LASTEXTJAR, filterPath.toOSString());
		
		return elems;
	}
	
	private CPListElement[] openVariableSelectionDialog(CPListElement existing) {
		String title= (existing == null) ? NewWizardMessages.getString("LibrariesWorkbookPage.VariableSelectionDialog.new.title") : NewWizardMessages.getString("LibrariesWorkbookPage.VariableSelectionDialog.edit.title"); //$NON-NLS-1$ //$NON-NLS-2$
		
		NewVariableEntryDialog dialog= new NewVariableEntryDialog(getShell(), title, null);
		if (dialog.open() == dialog.OK) {
			List existingElements= fLibrariesList.getElements();
			
			IPath[] paths= dialog.getResult();
			ArrayList result= new ArrayList();
			for (int i = 0; i < paths.length; i++) {
				CPListElement elem= new CPListElement(fCurrJProject, IClasspathEntry.CPE_VARIABLE, paths[i], null);
				IPath resolvedPath= JavaCore.getResolvedVariablePath(paths[i]);
				elem.setIsMissing((resolvedPath == null) || !resolvedPath.toFile().exists());
				if (!existingElements.contains(elem)) {
					result.add(elem);
				}
			}
			return (CPListElement[]) result.toArray(new CPListElement[result.size()]);
		}
		return null;
	}
	
	private CPListElement[] openContainerDialog(String title, ClasspathContainerWizard wizard) {
		WizardDialog dialog= new WizardDialog(getShell(), wizard);
		PixelConverter converter= new PixelConverter(getShell());
		
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(40), converter.convertHeightInCharsToPixels(20));
		dialog.create();
		dialog.getShell().setText(title);
		if (dialog.open() == dialog.OK) {
			IClasspathEntry created= wizard.getNewEntry();
			if (created != null) {			
				CPListElement elem= new CPListElement(fCurrJProject, IClasspathEntry.CPE_CONTAINER, created.getPath(), null);
				if (elem != null) {
					return new CPListElement[] { elem };
				}
			}
		}			
		return null;
	}	
	
	
	private void addAttachmentsFromExistingLibs(CPListElement elem) {
		if (elem.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
			return;
		}
		
		try {
			IJavaModel jmodel= fCurrJProject.getJavaModel();
			IJavaProject[] jprojects= jmodel.getJavaProjects();
			for (int i= 0; i < jprojects.length; i++) {
				IJavaProject curr= jprojects[i];
				if (!curr.equals(fCurrJProject)) {
					IClasspathEntry[] entries= curr.getRawClasspath();
					for (int k= 0; k < entries.length; k++) {
						IClasspathEntry entry= entries[k];
						if (entry.getEntryKind() == elem.getEntryKind()
							&& entry.getPath().equals(elem.getPath())) {
							IPath attachPath= entry.getSourceAttachmentPath();
							if (attachPath != null && !attachPath.isEmpty()) {
								elem.setSourceAttachment(attachPath, entry.getSourceAttachmentRootPath());
								return;
							}
						}
					}
				}
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
	}
	
	private class AdvancedDialog extends Dialog {

		private DialogField fLabelField;
		private SelectionButtonDialogField fCreateFolderField;
		private SelectionButtonDialogField fAddFolderField;
		private SelectionButtonDialogField fAddContainerField;
		private ComboDialogField fCombo;
		private CPListElement[] fResult;
		
		private ClasspathContainerDescriptor[] fDescriptors;

		public AdvancedDialog(Shell parent) {
			super(parent);
			
			fDescriptors= ClasspathContainerDescriptor.getDescriptors();
			
			fLabelField= new DialogField();
			fLabelField.setLabelText(NewWizardMessages.getString("LibrariesWorkbookPage.AdvancedDialog.description")); //$NON-NLS-1$
			
			fCreateFolderField= new SelectionButtonDialogField(SWT.RADIO);
			fCreateFolderField.setLabelText(NewWizardMessages.getString("LibrariesWorkbookPage.AdvancedDialog.createfolder")); //$NON-NLS-1$

			fAddFolderField= new SelectionButtonDialogField(SWT.RADIO);
			fAddFolderField.setLabelText(NewWizardMessages.getString("LibrariesWorkbookPage.AdvancedDialog.addfolder")); //$NON-NLS-1$

			fAddContainerField= new SelectionButtonDialogField(SWT.RADIO);
			fAddContainerField.setLabelText(NewWizardMessages.getString("LibrariesWorkbookPage.AdvancedDialog.addcontainer")); //$NON-NLS-1$
						
			String[] names= new String[fDescriptors.length];
			for (int i = 0; i < names.length; i++) {
				names[i]= fDescriptors[i].getName();
			}
			
			fCombo= new ComboDialogField(SWT.READ_ONLY);
			fCombo.setItems(names);
			fCombo.selectItem(0);
			fAddContainerField.attachDialogField(fCombo);
		}

		/* 
		 * @see Window#create(Shell)
		 */
		protected void configureShell(Shell shell) {
			super.configureShell(shell);
			shell.setText(NewWizardMessages.getString("LibrariesWorkbookPage.AdvancedDialog.title")); //$NON-NLS-1$
		}

		protected Control createDialogArea(Composite parent) {
			initializeDialogUnits(parent);
			
			Composite composite= (Composite) super.createDialogArea(parent);
			Composite inner= new Composite(composite, SWT.NONE);
			GridLayout layout= new GridLayout();
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			inner.setLayout(layout);
			
			fLabelField.doFillIntoGrid(inner, 1);
			fCreateFolderField.doFillIntoGrid(inner, 1);
			fAddFolderField.doFillIntoGrid(inner, 1);
			fAddContainerField.doFillIntoGrid(inner, 1);
			Control control= fCombo.getComboControl(inner);
			GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
			gd.horizontalIndent= convertWidthInCharsToPixels(3);
			control.setLayoutData(gd);
		
			return composite;
		}
		
		
		/* (non-Javadoc)
		 * @see Dialog#okPressed()
		 */
		protected void okPressed() {
			fResult= null;
			if (fCreateFolderField.isSelected()) {
				fResult= openNewClassFolderDialog(null);
			} else if (fAddFolderField.isSelected()) {
				fResult= openClassFolderDialog(null);
			} else if (fAddContainerField.isSelected()) {
				String selected= fCombo.getText();
				for (int i = 0; i < fDescriptors.length; i++) {
					if (fDescriptors[i].getName().equals(selected)) {
						String title= NewWizardMessages.getString("LibrariesWorkbookPage.ContainerDialog.new.title"); //$NON-NLS-1$
						fResult= openContainerDialog(title, new ClasspathContainerWizard(fDescriptors[i]));
						break;
					}
				}
			}
			if (fResult != null) {
				super.okPressed();
			}
			// stay open
		}
		
		public CPListElement[] getResult() {
			return fResult;
		}

	}
	
		
					
	// a dialog to set the source attachment properties
	private static class SourceAttachmentDialog extends StatusDialog implements IStatusChangeListener {
		
		private SourceAttachmentBlock fSourceAttachmentBlock;
				
		public SourceAttachmentDialog(Shell parent, IWorkspaceRoot root, IClasspathEntry entry) {
			super(parent);
			setTitle(NewWizardMessages.getFormattedString("LibrariesWorkbookPage.SourceAttachmentDialog.title", entry.getPath().toString())); //$NON-NLS-1$
			fSourceAttachmentBlock= new SourceAttachmentBlock(root, this, entry);
		}
		
		/*
		 * @see Windows#configureShell
		 */
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			WorkbenchHelp.setHelp(newShell, IJavaHelpContextIds.SOURCE_ATTACHMENT_DIALOG);
		}		
				
		protected Control createDialogArea(Composite parent) {
			Composite composite= (Composite)super.createDialogArea(parent);
						
			Control inner= fSourceAttachmentBlock.createControl(composite);
			inner.setLayoutData(new GridData(GridData.FILL_BOTH));
			return composite;
		}
		
		public void statusChanged(IStatus status) {
			updateStatus(status);
		}
		
		public IPath getSourceAttachmentPath() {
			return fSourceAttachmentBlock.getSourceAttachmentPath();
		}
		
		public IPath getSourceAttachmentRootPath() {
			return fSourceAttachmentBlock.getSourceAttachmentRootPath();
		}
				
	}
	
	/*
	 * @see BuildPathBasePage#getSelection
	 */
	public List getSelection() {
		return fLibrariesList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */	
	public void setSelection(List selElements) {
		for (int i= selElements.size()-1; i >= 0; i--) {
			CPListElement curr= (CPListElement) selElements.get(i);
			if (!isLibraryKind(curr.getEntryKind())) {
				selElements.remove(i);
			}
		}
		fLibrariesList.selectElements(new StructuredSelection(selElements));
	}	


}