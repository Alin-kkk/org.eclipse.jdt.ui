/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.resources.IResource;

import org.eclipse.ui.dialogs.PropertyPage;

import org.eclipse.jdt.core.IClasspathEntry;import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.JavaPlugin;import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaUIMessages;


/**
 * This is a dummy PropertyPage for JavaElements.
 * Copied from the ResourceInfoPage
 */
public class JavaElementInfoPage extends PropertyPage {
	protected Control createContents(Composite parent) {

		// ensure the page has no special buttons
		noDefaultAndApplyButton();

		IJavaElement element= (IJavaElement)getElement();
		
		IResource resource= null;
		try {
			resource= element.getUnderlyingResource(); 
		} catch (JavaModelException e) {
			JavaPlugin.getDefault().logErrorStatus("Creating ElementInfoPage", e.getStatus()); //$NON-NLS-1$
		}
		
		Composite composite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));

		Label nameLabel= new Label(composite, SWT.NONE);
		nameLabel.setText(JavaUIMessages.getString("JavaElementInfoPage.nameLabel")); //$NON-NLS-1$

		Label nameValueLabel= new Label(composite, SWT.NONE);
		nameValueLabel.setText(element.getElementName());

		if (resource != null) {
			// path label
			Label pathLabel= new Label(composite, SWT.NONE);
			pathLabel.setText(JavaUIMessages.getString("JavaElementInfoPage.resource_path")); //$NON-NLS-1$

			// path value label
			Label pathValueLabel= new Label(composite, SWT.NONE);
			pathValueLabel.setText(resource.getFullPath().toString());
		}
		if (element instanceof ICompilationUnit) {
			ICompilationUnit unit= (ICompilationUnit)element;
			Label packageLabel= new Label(composite, SWT.NONE);
			packageLabel.setText(JavaUIMessages.getString("JavaElementInfoPage.package")); //$NON-NLS-1$
			Label packageName= new Label(composite, SWT.NONE);
			packageName.setText(unit.getParent().getElementName());
			
		} else if (element instanceof IPackageFragment) {
			IPackageFragment packageFragment= (IPackageFragment)element;
			Label packageContents= new Label(composite, SWT.NONE);
			packageContents.setText(JavaUIMessages.getString("JavaElementInfoPage.package_contents")); //$NON-NLS-1$
			Label packageContentsType= new Label(composite, SWT.NONE);
			try {
				if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) 
					packageContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.source")); //$NON-NLS-1$
				else
					packageContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.binary")); //$NON-NLS-1$
			} catch (JavaModelException e) {
				packageContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.not_present")); //$NON-NLS-1$
			}
		} else if (element instanceof IPackageFragmentRoot) {
			Label rootContents= new Label(composite, SWT.NONE);
			rootContents.setText(JavaUIMessages.getString("JavaElementInfoPage.classpath_entry_kind")); //$NON-NLS-1$
			Label rootContentsType= new Label(composite, SWT.NONE);
			try {
				IClasspathEntry entry= JavaModelUtil.getRawClasspathEntry((IPackageFragmentRoot)element);
				if (entry != null) {
					switch (entry.getEntryKind()) {
						case IClasspathEntry.CPE_SOURCE:
							rootContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.source")); break; //$NON-NLS-1$
						case IClasspathEntry.CPE_PROJECT:
							rootContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.project")); break; //$NON-NLS-1$
						case IClasspathEntry.CPE_LIBRARY:
							rootContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.library")); break; //$NON-NLS-1$
						case IClasspathEntry.CPE_VARIABLE:
							rootContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.variable")); //$NON-NLS-1$
							Label varPath= new Label(composite, SWT.NONE);
							varPath.setText(JavaUIMessages.getString("JavaElementInfoPage.variable_path")); //$NON-NLS-1$
							Label varPathVar= new Label(composite, SWT.NONE);
							varPathVar.setText(entry.getPath().makeRelative().toString());							
							break;
					}
				} else {
					rootContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.not_present")); //$NON-NLS-1$
				}
			} catch (JavaModelException e) {
				rootContentsType.setText(JavaUIMessages.getString("JavaElementInfoPage.not_present")); //$NON-NLS-1$
			}
		} else if (element instanceof IJavaProject) {
			Label packageLabel= new Label(composite, SWT.NONE);
			packageLabel.setText(JavaUIMessages.getString("JavaElementInfoPage.location")); //$NON-NLS-1$
			Label packageName= new Label(composite, SWT.NONE);
			packageName.setText(((IJavaProject)element).getProject().getLocation().toOSString());
		}
		
		return composite;
	}

	/**
	 */
	protected boolean doOk() {
		// nothing to do - read-only page
		return true;
	}

}
