package org.eclipse.jdt.internal.ui.reorg;

import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.actions.CopyProjectAction;

import org.eclipse.jdt.ui.actions.UnifiedSite;

import org.eclipse.jdt.internal.corext.refactoring.reorg.CopyRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;

public class JdtCopyAction extends ReorgDestinationAction {

	protected JdtCopyAction(UnifiedSite site) {
		super(site);
	}

	ReorgRefactoring createRefactoring(List elements){
		return new CopyRefactoring(elements);
	}
	
	String getActionName() {
		return ReorgMessages.getString("copyAction.name"); //$NON-NLS-1$
	}
	
	String getDestinationDialogMessage() {
		return ReorgMessages.getString("copyAction.destination.label"); //$NON-NLS-1$
	}
	
	/* non java-doc
	 * @see IRefactoringAction#canOperateOn(IStructuredSelection)
	 */
	public boolean canOperateOn(IStructuredSelection selection) {
		if (ClipboardActionUtil.hasOnlyProjects(selection))
			return selection.size() == 1;
		else
			return super.canOperateOn(selection);
	}
	
	protected  void run(IStructuredSelection selection) {
		if (ClipboardActionUtil.hasOnlyProjects(selection)){
			copyProject(selection);
		}	else {
			super.run(selection);
		}
	}

	private void copyProject(IStructuredSelection selection){
		CopyProjectAction action= new CopyProjectAction(JavaPlugin.getActiveWorkbenchShell());
		action.selectionChanged(selection);
		action.run();
	}

}
