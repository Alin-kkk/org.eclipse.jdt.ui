/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;

/**
 * UI to set the source attachment archive and root.
 * Same implementation for both setting attachments for libraries from
 * variable entries and for normal (internal or external) jar.
 */
public class SourceAttachmentBlock {
	
	private static class UpdatedClasspathContainer implements IClasspathContainer {

		private IClasspathEntry[] fNewEntries;
		private IClasspathContainer fOriginal;

		public UpdatedClasspathContainer(IClasspathContainer original, IClasspathEntry[] newEntries) {
			fNewEntries= newEntries;
			fOriginal= original;
		}

		public IClasspathEntry[] getClasspathEntries() {
			return fNewEntries;
		}

		public String getDescription() {
			return fOriginal.getDescription();
		}

		public int getKind() {
			return fOriginal.getKind();
		}

		public IPath getPath() {
			return fOriginal.getPath();
		}
	}
	
	private IStatusChangeListener fContext;
	
	private StringButtonDialogField fFileNameField;
	private SelectionButtonDialogField fInternalButtonField;
	
	private IStatus fNameStatus;
	
	/**
	 * The file to which the archive path points to.
	 * Only set when the file exists.
	 */
	private File fResolvedFile; 

	/**
	 * The path to which the archive variable points.
	 * Null if invalid path or not resolvable. Must not exist.
	 */	
	private IPath fFileVariablePath;
		
	private IWorkspaceRoot fWorkspaceRoot;
	
	private Control fSWTWidget;
	private CLabel fFullPathResolvedLabel;
	
	private IJavaProject fProject;
	private IClasspathEntry fEntry;
	private IPath fContainerPath;

	/**
	 * @deprecated 
	 */
	public SourceAttachmentBlock(IWorkspaceRoot root, IStatusChangeListener context, IClasspathEntry oldEntry) {
		this(context, oldEntry, null, null);
	}	
	
	/**
	 * @param context listeners for status updates
	 * @param entryToEdit The entry to edit
	 * @param containerPath Path of the container that contains the given entry or
	 * <code>null</code> if the entry does not belong to a container.
	 * @param project Project to which the entry belongs. Can be
	 * <code>null</code> if <code>getRunnable</code> is not run and the entry
	 * does not belong to a container.
	 *
	 */
	public SourceAttachmentBlock(IStatusChangeListener context, IClasspathEntry entry, IPath containerPath, IJavaProject project) {
		Assert.isNotNull(entry);
		
		fContext= context;
		fEntry= entry;
		fContainerPath= containerPath;
		fProject= project;
		
		int kind= entry.getEntryKind();
		Assert.isTrue(kind == IClasspathEntry.CPE_LIBRARY || kind == IClasspathEntry.CPE_VARIABLE);
				
		fWorkspaceRoot= ResourcesPlugin.getWorkspace().getRoot();
		
		fNameStatus= new StatusInfo();
		
		SourceAttachmentAdapter adapter= new SourceAttachmentAdapter();
		
		// create the dialog fields (no widgets yet)
		if (isVariableEntry()) {
			fFileNameField= new VariablePathDialogField(adapter);
			fFileNameField.setDialogFieldListener(adapter);
			fFileNameField.setLabelText(NewWizardMessages.getString("SourceAttachmentBlock.filename.varlabel")); //$NON-NLS-1$
			fFileNameField.setButtonLabel(NewWizardMessages.getString("SourceAttachmentBlock.filename.external.varbutton")); //$NON-NLS-1$
			((VariablePathDialogField)fFileNameField).setVariableButtonLabel(NewWizardMessages.getString("SourceAttachmentBlock.filename.variable.button")); //$NON-NLS-1$
					
		} else {
			fFileNameField= new StringButtonDialogField(adapter);
			fFileNameField.setDialogFieldListener(adapter);
			fFileNameField.setLabelText(NewWizardMessages.getString("SourceAttachmentBlock.filename.label")); //$NON-NLS-1$
			fFileNameField.setButtonLabel(NewWizardMessages.getString("SourceAttachmentBlock.filename.external.button")); //$NON-NLS-1$
		
			fInternalButtonField= new SelectionButtonDialogField(SWT.PUSH);
			fInternalButtonField.setDialogFieldListener(adapter);
			fInternalButtonField.setLabelText(NewWizardMessages.getString("SourceAttachmentBlock.filename.internal.button")); //$NON-NLS-1$
			
		}	
	
		// set the old settings
		setDefaults();
	}
	
	public void setDefaults() {
		if (fEntry.getSourceAttachmentPath() != null) {
			fFileNameField.setText(fEntry.getSourceAttachmentPath().toString());
		} else {
			fFileNameField.setText(""); //$NON-NLS-1$
		}	
	}
	
	private boolean isVariableEntry() {
		return fEntry.getEntryKind() == IClasspathEntry.CPE_VARIABLE;
	}
	
	
	/**
	 * Gets the source attachment path chosen by the user
	 */
	public IPath getSourceAttachmentPath() {
		if (fFileNameField.getText().length() == 0) {
			return null;
		}
		return new Path(fFileNameField.getText());
	}

	/**
	 * Gets the source attachment root chosen by the user
	 * Returns null to let JCore automatically detect the root.
	 */
	public IPath getSourceAttachmentRootPath() {
		return null;
	}
	
		
	/**
	 * Creates the control
	 */
	public Control createControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);
		
		fSWTWidget= parent;
		
		Composite composite= new Composite(parent, SWT.NONE);	
		
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 4;		
		composite.setLayout(layout);
		
		int widthHint= converter.convertWidthInCharsToPixels(isVariableEntry() ? 50 : 60);
		
		
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 4;
		
		Label message= new Label(composite, SWT.LEFT);
		message.setLayoutData(gd);
		message.setText(NewWizardMessages.getFormattedString("SourceAttachmentBlock.message", fEntry.getPath().lastSegment())); //$NON-NLS-1$
				
		if (isVariableEntry()) {
			DialogField.createEmptySpace(composite, 1);
			gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
			gd.widthHint= widthHint;
			gd.horizontalSpan= 2;
			Label desc= new Label(composite, SWT.LEFT + SWT.WRAP);
			desc.setText(NewWizardMessages.getString("SourceAttachmentBlock.filename.description")); //$NON-NLS-1$
			desc.setLayoutData(gd);
			DialogField.createEmptySpace(composite, 1);
		}
		// archive name field
		fFileNameField.doFillIntoGrid(composite, 4);
		LayoutUtil.setWidthHint(fFileNameField.getTextControl(null), widthHint);
		LayoutUtil.setHorizontalGrabbing(fFileNameField.getTextControl(null));
		
		if (!isVariableEntry()) {
			// aditional 'browse workspace' button for normal jars
			DialogField.createEmptySpace(composite, 3);	
			fInternalButtonField.doFillIntoGrid(composite, 1);
		} else {
			// label that shows the resolved path for variable jars
			DialogField.createEmptySpace(composite, 1);	
			fFullPathResolvedLabel= new CLabel(composite, SWT.LEFT);
			fFullPathResolvedLabel.setText(getResolvedLabelString(fFileNameField.getText(), true));
			fFullPathResolvedLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
			DialogField.createEmptySpace(composite, 2);			
		}
		
		fFileNameField.postSetFocusOnDialogField(parent.getDisplay());
				
		WorkbenchHelp.setHelp(composite, IJavaHelpContextIds.SOURCE_ATTACHMENT_BLOCK);
		return composite;
	}
	
		
	private class SourceAttachmentAdapter implements IStringButtonAdapter, IDialogFieldListener {
		
		// -------- IStringButtonAdapter --------
		public void changeControlPressed(DialogField field) {
			attachmentChangeControlPressed(field);			
		}
		
		// ---------- IDialogFieldListener --------
		public void dialogFieldChanged(DialogField field) {
			attachmentDialogFieldChanged(field);
		}
	}
	
	private void attachmentChangeControlPressed(DialogField field) {
		if (field == fFileNameField) {
			IPath jarFilePath= chooseExtJarFile();
			if (jarFilePath != null) {
				fFileNameField.setText(jarFilePath.toString());
			}
		}	
	}
	
	// ---------- IDialogFieldListener --------

	private void attachmentDialogFieldChanged(DialogField field) {
		if (field == fFileNameField) {
			fNameStatus= updateFileNameStatus();
		} else if (field == fInternalButtonField) {
			IPath jarFilePath= chooseInternalJarFile();
			if (jarFilePath != null) {
				fFileNameField.setText(jarFilePath.toString());
			}
			return;
		}
		doStatusLineUpdate();
	}	
		
	private void doStatusLineUpdate() {
		fFileNameField.enableButton(canBrowseFileName());
		
		// set the resolved path for variable jars
		if (fFullPathResolvedLabel != null) {
			fFullPathResolvedLabel.setText(getResolvedLabelString(fFileNameField.getText(), true));
		}
		
		IStatus status= StatusUtil.getMostSevere(new IStatus[] { fNameStatus });
		fContext.statusChanged(status);
	}
	
	private boolean canBrowseFileName() {
		if (!isVariableEntry()) {
			return true;
		}
		// to browse with a variable JAR, the variable name must point to a directory
		if (fFileVariablePath != null) {
			return fFileVariablePath.toFile().isDirectory();
		}
		return false;
	}
	
	private String getResolvedLabelString(String path, boolean osPath) {
		IPath resolvedPath= getResolvedPath(new Path(path));
		if (resolvedPath != null) {
			if (osPath) {
				return resolvedPath.toOSString();
			} else {
				return resolvedPath.toString();
			}
		}
		return ""; //$NON-NLS-1$
	}	
	
	private IPath getResolvedPath(IPath path) {
		if (path != null) {
			String varName= path.segment(0);
			if (varName != null) {
				IPath varPath= JavaCore.getClasspathVariable(varName);
				if (varPath != null) {
					return varPath.append(path.removeFirstSegments(1));
				}
			}
		}
		return null;
	}	
	
	private IStatus updateFileNameStatus() {
		StatusInfo status= new StatusInfo();
		fResolvedFile= null;
		fFileVariablePath= null;
		
		String fileName= fFileNameField.getText();
		if (fileName.length() == 0) {
			// no source attachment
			return status;
		} else {
			if (!Path.EMPTY.isValidPath(fileName)) {
				status.setError(NewWizardMessages.getString("SourceAttachmentBlock.filename.error.notvalid")); //$NON-NLS-1$
				return status;
			}
			IPath filePath= new Path(fileName);
			IPath resolvedPath;
			if (isVariableEntry()) {
				if (filePath.getDevice() != null) {
					status.setError(NewWizardMessages.getString("SourceAttachmentBlock.filename.error.deviceinpath")); //$NON-NLS-1$
					return status;
				}
				String varName= filePath.segment(0);
				if (varName == null) {
					status.setError(NewWizardMessages.getString("SourceAttachmentBlock.filename.error.notvalid")); //$NON-NLS-1$
					return status;
				}
				fFileVariablePath= JavaCore.getClasspathVariable(varName);
				if (fFileVariablePath == null) {
					status.setError(NewWizardMessages.getString("SourceAttachmentBlock.filename.error.varnotexists")); //$NON-NLS-1$
					return status;
				}
				resolvedPath= fFileVariablePath.append(filePath.removeFirstSegments(1));
				
				if (resolvedPath.isEmpty()) {
					status.setWarning(NewWizardMessages.getString("SourceAttachmentBlock.filename.warning.varempty")); //$NON-NLS-1$
					return status;
				}
				File file= resolvedPath.toFile();
				if (!file.exists()) {				
					String message= NewWizardMessages.getFormattedString("SourceAttachmentBlock.filename.error.filenotexists", resolvedPath.toOSString()); //$NON-NLS-1$
					status.setWarning(message);
					return status;
				}						
				fResolvedFile= file;
				
			} else {
				File file= filePath.toFile();
				IResource res= fWorkspaceRoot.findMember(filePath);
				if (res != null) {
					file= res.getLocation().toFile();
				}
				if (!file.exists()) {
					String message=  NewWizardMessages.getFormattedString("SourceAttachmentBlock.filename.error.filenotexists", filePath.toString()); //$NON-NLS-1$
					status.setError(message);
					return status;
				}
				fResolvedFile= file;
			}
			
		}
		return status;
	}
	
	/*
	 * Opens a dialog to choose a jar from the file system.
	 */
	private IPath chooseExtJarFile() {
		IPath currPath= new Path(fFileNameField.getText());
		if (currPath.isEmpty()) {
			currPath= fEntry.getPath();
		}		
		if (isVariableEntry()) {
			IPath resolvedPath= getResolvedPath(currPath);
			File initialSelection= resolvedPath != null ? resolvedPath.toFile() : null;
			
			String currVariable= currPath.segment(0);
			JARFileSelectionDialog dialog= new JARFileSelectionDialog(getShell(), false, true);
			dialog.setTitle(NewWizardMessages.getString("SourceAttachmentBlock.extvardialog.title")); //$NON-NLS-1$
			dialog.setMessage(NewWizardMessages.getString("SourceAttachmentBlock.extvardialog.description")); //$NON-NLS-1$
			dialog.setInput(fFileVariablePath.toFile());
			dialog.setInitialSelection(initialSelection);
			if (dialog.open() == JARFileSelectionDialog.OK) {
				File result= (File) dialog.getResult()[0];
				IPath returnPath= new Path(result.getPath()).makeAbsolute();
				return modifyPath(returnPath, currVariable);
			}
		} else {
			
			if (ArchiveFileFilter.isArchivePath(currPath)) {
				currPath= currPath.removeLastSegments(1);
			}
		
			FileDialog dialog= new FileDialog(getShell());
			dialog.setText(NewWizardMessages.getString("SourceAttachmentBlock.extjardialog.text")); //$NON-NLS-1$
			dialog.setFilterExtensions(new String[] {"*.jar;*.zip"}); //$NON-NLS-1$
			dialog.setFilterPath(currPath.toOSString());
			String res= dialog.open();
			if (res != null) {
				return new Path(res).makeAbsolute();
			}
		}
		return null;
	}

	/*
	 * Opens a dialog to choose an internal jar.
	 */	
	private IPath chooseInternalJarFile() {
		String initSelection= fFileNameField.getText();
		
		Class[] acceptedClasses= new Class[] { IFolder.class, IFile.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);
	
		ViewerFilter filter= new ArchiveFileFilter(null, false);

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		IResource initSel= null;
		if (initSelection.length() > 0) {
			initSel= fWorkspaceRoot.findMember(new Path(initSelection));
		}
		if (initSel == null) {
			initSel= fWorkspaceRoot.findMember(fEntry.getPath());
		}

		FolderSelectionDialog dialog= new FolderSelectionDialog(getShell(), lp, cp);
		dialog.setAllowMultiple(false);
		dialog.setValidator(validator);
		dialog.addFilter(filter);
		dialog.setTitle(NewWizardMessages.getString("SourceAttachmentBlock.intjardialog.title")); //$NON-NLS-1$
		dialog.setMessage(NewWizardMessages.getString("SourceAttachmentBlock.intjardialog.message")); //$NON-NLS-1$
		dialog.setInput(fWorkspaceRoot);
		dialog.setInitialSelection(initSel);
		if (dialog.open() == ElementTreeSelectionDialog.OK) {
			IResource res= (IResource) dialog.getFirstResult();
			return res.getFullPath();
		}
		return null;
	}
	
	private Shell getShell() {
		if (fSWTWidget != null) {
			return fSWTWidget.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();			
	}
	
	/**
	 * Takes a path and replaces the beginning with a variable name
	 * (if the beginning matches with the variables value)
	 */
	private IPath modifyPath(IPath path, String varName) {
		if (varName == null || path == null) {
			return null;
		}
		if (path.isEmpty()) {
			return new Path(varName);
		}
		
		IPath varPath= JavaCore.getClasspathVariable(varName);
		if (varPath != null) {
			if (varPath.isPrefixOf(path)) {
				path= path.removeFirstSegments(varPath.segmentCount());
			} else {
				path= new Path(path.lastSegment());
			}
		} else {
			path= new Path(path.lastSegment());
		}
		return new Path(varName).append(path);
	}


	/**
	 * Creates a runnable that sets the source attachment by modifying the project's classpath.
	 * @deprecated use getRunnable() instead
	 */
	public IRunnableWithProgress getRunnable(final IJavaProject jproject, final Shell shell) {
		fProject= jproject;
		return getRunnable(shell);
	}

	/**
	 * Creates a runnable that sets the source attachment by modifying the
	 * project's classpath or updating a container.
	 */
	public IRunnableWithProgress getRunnable(final Shell shell) {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {				
				try {
					attachSource(shell, monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};
	}	
	
	
	protected void attachSource(final Shell shell, IProgressMonitor monitor) throws CoreException {
		boolean isExported= fEntry.isExported();
		IClasspathEntry newEntry;
		if (fEntry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
			newEntry= JavaCore.newVariableEntry(fEntry.getPath(), getSourceAttachmentPath(), getSourceAttachmentRootPath(), isExported);
		} else {
			newEntry= JavaCore.newLibraryEntry(fEntry.getPath(), getSourceAttachmentPath(), getSourceAttachmentRootPath(), isExported);
		}
		if (fContainerPath != null) {
			updateContainerClasspath(fProject, fContainerPath, newEntry, monitor);
		} else {
			updateProjectClasspath(shell, fProject, newEntry, monitor);
		}
	}

	private void updateContainerClasspath(IJavaProject jproject, IPath containerPath, IClasspathEntry newEntry, IProgressMonitor monitor) throws CoreException {
		IClasspathContainer container= JavaCore.getClasspathContainer(containerPath, jproject);
		IClasspathEntry[] entries= container.getClasspathEntries();
		IClasspathEntry[] newEntries= new IClasspathEntry[entries.length];
		for (int i= 0; i < entries.length; i++) {
			IClasspathEntry curr= entries[i];
			if (curr.getEntryKind() == newEntry.getEntryKind() && curr.getPath().equals(newEntry.getPath())) {
				newEntries[i]= newEntry;
			} else {
				newEntries[i]= curr;
			}
		}
		IClasspathContainer updatedContainer= new UpdatedClasspathContainer(container, newEntries);
			
		ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
		initializer.requestClasspathContainerUpdate(containerPath, jproject, updatedContainer);
		monitor.worked(1);
	}

	private void updateProjectClasspath(Shell shell, IJavaProject jproject, IClasspathEntry newEntry, IProgressMonitor monitor) throws JavaModelException {
		IClasspathEntry[] oldClasspath= jproject.getRawClasspath();
		int nEntries= oldClasspath.length;
		ArrayList newEntries= new ArrayList(nEntries + 1);
		int entryKind= newEntry.getEntryKind();
		IPath jarPath= newEntry.getPath();
		boolean found= false;
		for (int i= 0; i < nEntries; i++) {
			IClasspathEntry curr= oldClasspath[i];
			if (curr.getEntryKind() == entryKind && curr.getPath().equals(jarPath)) {
				// add modified entry
				newEntries.add(newEntry);
				found= true;
			} else {
				newEntries.add(curr);
			}
		}
		if (!found) {
			if (newEntry.getSourceAttachmentPath() == null || !putJarOnClasspathDialog(shell)) {
				return;
			}
			// add new
			newEntries.add(newEntry);			
		}
		IClasspathEntry[] newClasspath= (IClasspathEntry[]) newEntries.toArray(new IClasspathEntry[newEntries.size()]);
		jproject.setRawClasspath(newClasspath, monitor);
	}
	
	private boolean putJarOnClasspathDialog(Shell shell) {
		final boolean[] result= new boolean[1];
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				String title= NewWizardMessages.getString("SourceAttachmentBlock.putoncpdialog.title"); //$NON-NLS-1$
				String message= NewWizardMessages.getString("SourceAttachmentBlock.putoncpdialog.message"); //$NON-NLS-1$
				result[0]= MessageDialog.openQuestion(JavaPlugin.getActiveWorkbenchShell(), title, message);
			}
		});
		return result[0];
	}
	
	
}