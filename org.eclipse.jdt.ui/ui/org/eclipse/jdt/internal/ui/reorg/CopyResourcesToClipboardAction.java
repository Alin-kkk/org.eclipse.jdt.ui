package org.eclipse.jdt.internal.ui.reorg;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;

import org.eclipse.swt.SWTError;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.part.ResourceTransfer;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.internal.corext.refactoring.Assert;

public class CopyResourcesToClipboardAction extends SelectionDispatchAction {

	private static final String fgLineDelim= System.getProperty("line.separator"); //$NON-NLS-1$
	private Clipboard fClipboard;
	
	protected CopyResourcesToClipboardAction(IWorkbenchSite site, Clipboard clipboard) {
		super(site);
		Assert.isNotNull(clipboard);
		setText(ReorgMessages.getString("CopyResourcesToClipboardAction.copy"));//$NON-NLS-1$
		fClipboard= clipboard;
	}

	protected void selectionChanged(IStructuredSelection selection) {
		setEnabled(canOperateOn(selection));
	}

	public void run(IStructuredSelection selection) {
		IResource[] resources= getSelectedResources(selection);
		try{
			getClipboard().setContents(
				new Object[] { 
						resources, 
						getFileLocations(resources), 
						getFileNamesText(resources)}, 
				new Transfer[] { 
						ResourceTransfer.getInstance(), 
						FileTransfer.getInstance(), 
						TextTransfer.getInstance()});
		} catch (SWTError e){
			if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD)
				throw e;
			if (MessageDialog.openQuestion(getShell(), "Problem Copying to Clipboard", "There was a problem when accessing the system clipboard. Retry?"))
				run(selection);
		}				
	}
	
	public static boolean canOperateOn(IStructuredSelection selection){
		if (StructuredSelectionUtil.hasNonResources(selection)) 
			return false;
		
		//XXX must exclude it here - nasty
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			Object each= iter.next();
			if (each instanceof IPackageFragment && ((IPackageFragment)each).isDefaultPackage())
				return false;
		}
		
		IResource[] selectedResources= StructuredSelectionUtil.getResources(selection);
		if (selectedResources.length == 0)
			return false;
		
		if (! areResourcesOfValidType(selectedResources))
			return false;

		if (ClipboardActionUtil.isOneOpenProject(selectedResources))
			return true;
		
		if (! haveCommonParent(selectedResources))
			return false;
		
		SelectionDispatchAction ca= ReorgActionFactory.createDnDCopyAction(selection.toList(), ClipboardActionUtil.getFirstResource(selection));
		ca.update();
		return ca.isEnabled();
	}
	
	private static boolean areResourcesOfValidType(IResource[] resources){
		boolean onlyProjectsSelected= ClipboardActionUtil.resourcesAreOfType(resources, IResource.PROJECT);
		boolean onlyFilesFoldersSelected= ClipboardActionUtil.resourcesAreOfType(resources, IResource.FILE | IResource.FOLDER);

		if (!onlyFilesFoldersSelected && !onlyProjectsSelected)
			return false;
		if (onlyFilesFoldersSelected && onlyProjectsSelected)
			return false;
	
		return true;
	}
	
	private static boolean haveCommonParent(IResource[] resources){
		if (haveCommonParentAsResources(resources))
			return true;
			
		/* 
		 * special case - must be able to select packages:
		 * p
		 * p.q
		 */
		if (! ClipboardActionUtil.resourcesAreOfType(resources, IResource.FOLDER)) 
			return false;

		IPackageFragment[] packages= getPackages(resources); 
		if (packages.length != resources.length)
			return false;
			
		IJavaElement firstJavaParent= packages[0].getParent();
		if (firstJavaParent == null)
			return false;
		
		for (int i= 0; i < packages.length; i++) {
			if (! firstJavaParent.equals(packages[i].getParent()))
				return false;	
		}	
		return true;	
	}
	
	private static IPackageFragment[] getPackages(IResource[] resources){
		List packages= new ArrayList(resources.length);
		for (int i= 0; i < resources.length; i++) {
			IJavaElement element= JavaCore.create(resources[i]);
			if (element != null && element.getElementType() == IJavaElement.PACKAGE_FRAGMENT)
				packages.add(element);
		}
		return (IPackageFragment[]) packages.toArray(new IPackageFragment[packages.size()]);
	}
	
	private static boolean haveCommonParentAsResources(IResource[] resources){
		IContainer firstParent = resources[0].getParent();
		if (firstParent == null) 
			return false;
	
		for (int i= 0; i < resources.length; i++) {
			if (!resources[i].getParent().equals(firstParent)) 
				return false;
		}
		return true;
	}

	private IResource[] getSelectedResources(IStructuredSelection selection) {
		return StructuredSelectionUtil.getResources(selection);
	}

	private static String getFileNamesText(IResource[] resources) {
		ILabelProvider labelProvider= getLabelProvider();
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < resources.length; i++) {
			if (i > 0)
				buf.append(fgLineDelim);	
			buf.append(getName(resources[i], labelProvider));
		}
		return buf.toString();
	}
	
	private static ILabelProvider getLabelProvider(){
		return new JavaElementLabelProvider(
			JavaElementLabelProvider.SHOW_VARIABLE
			+ JavaElementLabelProvider.SHOW_PARAMETERS
			+ JavaElementLabelProvider.SHOW_TYPE
		);		
	}

	private static String getName(IResource resource, ILabelProvider labelProvider){
		IJavaElement javeElement= JavaCore.create(resource);
		if (javeElement == null)
			return labelProvider.getText(resource);	
		else
			return labelProvider.getText(javeElement);
	}
	
	private static String[] getFileLocations(IResource[] resources) {
		String[] fileLocations= new String[resources.length];
		for (int i= 0; i < resources.length; i++) {
			fileLocations[i]= resources[i].getLocation().toOSString();
		}
		return fileLocations;
	}

	private Clipboard getClipboard() {
		return fClipboard;
	}
}
