package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;

/**
 * A number of routines for working with JavaElements in editors
 *
 * Use 'isOpenInEditor' to test if an element is already open in a editor  
 * Use 'openInEditor' to force opening an element in a editor
 * With 'getWorkingCopy' you get the working copy (element in the editor) of an element
 */
public class EditorUtility {
	
	
	public static boolean isEditorInput(Object element, IEditorPart editor) {
		if (editor != null) {
			try {
				return editor.getEditorInput().equals(getEditorInput(element));
			} catch (JavaModelException x) {
				JavaPlugin.log(x.getStatus());
			}
		}
		return false;
	}
	
	/** 
	 * Tests if a cu is currently shown in an editor
	 * @return the IEditorPart if shown, null if element is not open in an editor
	 */	
	public static IEditorPart isOpenInEditor(Object inputElement) {
		IEditorInput input= null;
		
		try {
			input= getEditorInput(inputElement);
		} catch (JavaModelException x) {
			JavaPlugin.log(x.getStatus());
		}
		
		if (input != null) {
			IWorkbenchPage p= JavaPlugin.getDefault().getActivePage();
			if (p != null) {
				IEditorPart[] editors= p.getEditors();
				for (int i= 0; i < editors.length; i++) {
					if (input.equals(editors[i].getEditorInput()))
						return editors[i];
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Opens a Java editor for an element (IJavaElement, IFile, IStorage...)
	 * The editor is activated by default
	 * @return the IEditorPart or null if wrong element type or opening failed
	 */
	public static IEditorPart openInEditor(Object inputElement) throws JavaModelException, PartInitException {
		return openInEditor(inputElement, true);
	}
		
	/**
	 * Opens a Java editor for an element (IJavaElement, IFile, IStorage...)
	 * @return the IEditorPart or null if wrong element type or opening failed
	 */
	public static IEditorPart openInEditor(Object inputElement, boolean activate) throws JavaModelException, PartInitException {
		
		if (inputElement instanceof IFile)
			return openInEditor((IFile) inputElement);
		
		IEditorInput input= getEditorInput(inputElement);
		if (input != null)
			return openInEditor(input, getEditorID(input, inputElement), activate);
			
		return null;
	}
	
	/** 
	 * Selects a Java Element in an editor
	 */	
	public static void revealInEditor(IEditorPart part, IJavaElement element) {
		if (element != null && part instanceof JavaEditor) {
			((JavaEditor) part).setSelection(element);
		}
	}
	
	private static IEditorPart openInEditor(IFile file) throws PartInitException {
		if (file != null) {
			IWorkbenchPage p= JavaPlugin.getDefault().getActivePage();
			if (p != null) {
				IEditorPart e= p.openEditor(file);
				p.activate(e);
				return e;
			}
		}
		return null;
	}
	
	private static IEditorPart openInEditor(IEditorInput input, String editorID, boolean activate) throws PartInitException {
		if (input != null) {
			IWorkbenchPage p= JavaPlugin.getDefault().getActivePage();
			if (p != null)
				return p.openEditor(input, editorID, activate);
		}
		return null;
	}
	
	/**
	 *@deprecated	Made it public again for java debugger UI.
	 */
	public static String getEditorID(IEditorInput input, Object inputObject) {
		
		IEditorRegistry registry= PlatformUI.getWorkbench().getEditorRegistry();
		
//		// check whether favorit editor as been remembered
//		if (input instanceof IFileEditorInput) {
//			String id= ((IFileEditorInput) input).getFile().getPersistentPropert(EDITOR_KEY);
//			if (id != null)
//				return id;
//		}
		
		// get the default editor		
		IEditorDescriptor descriptor= registry.getDefaultEditor(input.getName());
		if (descriptor != null)
			return descriptor.getId();

		return null;
	}
	
	private static IEditorInput getEditorInput(IJavaElement element) throws JavaModelException {
		while (element != null) {
			if (element instanceof IWorkingCopy && ((IWorkingCopy) element).isWorkingCopy()) 
				element= ((IWorkingCopy) element).getOriginalElement();
				
			if (element instanceof ICompilationUnit) {
				ICompilationUnit unit= (ICompilationUnit) element;
					IResource resource= unit.getUnderlyingResource();
					if (resource instanceof IFile)
						return new FileEditorInput((IFile) resource);
			}
			
			if (element instanceof IClassFile)
				return new InternalClassFileEditorInput((IClassFile) element);
			
			element= element.getParent();
		}
		
		return null;
	}	

	public static IEditorInput getEditorInput(Object input) throws JavaModelException {
		if (input instanceof IJavaElement)
			return getEditorInput((IJavaElement) input);
			
		if (input instanceof IFile) 
			return new FileEditorInput((IFile) input);
		
		if (input instanceof IStorage) 
			return new JarEntryEditorInput((IStorage)input);
	
		return null;
	}
	
	/**
	 * If the current active editor edits a java element return it, else
	 * return null
	 */
	public static IJavaElement getActiveEditorJavaInput() {
		IWorkbenchPage page= JavaPlugin.getActivePage();
		if (page != null) {
			IEditorPart part= page.getActiveEditor();
			if (part != null) {
				IEditorInput editorInput= part.getEditorInput();
				if (editorInput != null) {
					return (IJavaElement)editorInput.getAdapter(IJavaElement.class);
				}
			}
		}
		return null;	
	}
	
	/** 
	 * Gets the working copy of an compilation unit opened in an editor
	 * @param part the editor part
	 * @param cu the original compilation unit (or another working copy)
	 * @return the working copy of the compilation unit, or null if not found
	 */	
	public static ICompilationUnit getWorkingCopy(ICompilationUnit cu) throws JavaModelException {
		if (cu == null)
			return null;
		if (cu.isWorkingCopy())
			return cu;
			
		return (ICompilationUnit)cu.findSharedWorkingCopy(JavaUI.getBufferFactory());
	}
	
	/** 
	 * Gets the working copy of an member opened in an editor
	 *
	 * @param member the original member or a member in a working copy
	 * @return the corresponding member in the shared working copy or <code>null</code> if not found
	 */	
	public static IMember getWorkingCopy(IMember member) throws JavaModelException {
		ICompilationUnit cu= member.getCompilationUnit();
		if (cu != null) {
			ICompilationUnit workingCopy= getWorkingCopy(cu);
			if (workingCopy != null) {
				return JavaModelUtil.findMemberInCompilationUnit(workingCopy, member);
			}
		}
		return null;
	}
	
	/**
	 * Returns the compilation unit for the given java element.
	 * @param element the java element whose compilation unit is searched for
	 * @return the compilation unit of the given java element
	 */
	private static ICompilationUnit getCompilationUnit(IJavaElement element) {
		
		if (element == null)
			return null;
			
		if (element instanceof IMember)
			return ((IMember) element).getCompilationUnit();
		
		int type= element.getElementType();
		if (IJavaElement.COMPILATION_UNIT == type)
			return (ICompilationUnit) element;
		if (IJavaElement.CLASS_FILE == type)
			return null;
			
		return getCompilationUnit(element.getParent());
	}
	
	/** 
	 * Returns the working copy of the given java element.
	 * @param javaElement the javaElement for which the working copyshould be found
	 * @param reconcile indicates whether the working copy must be reconcile prior to searching it
	 * @return the working copy of the given element or <code>null</code> if none
	 */	
	public static IJavaElement getWorkingCopy(IJavaElement element, boolean reconcile) throws JavaModelException {
		ICompilationUnit unit= getCompilationUnit(element);
		if (unit == null)
			return null;
			
		if (unit.isWorkingCopy())
			return element;
			
		ICompilationUnit workingCopy= getWorkingCopy(unit);
		if (workingCopy != null) {
			if (reconcile) {
				synchronized (workingCopy) {
					workingCopy.reconcile();
					return JavaModelUtil.findInCompilationUnit(workingCopy, element);
				}
			} else {
					return JavaModelUtil.findInCompilationUnit(workingCopy, element);
			}
		}
		
		return null;
	}
}