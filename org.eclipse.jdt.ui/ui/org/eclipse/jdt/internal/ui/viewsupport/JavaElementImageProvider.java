/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.viewsupport;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.Assert;

import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.model.IWorkbenchAdapter;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.JavaUIMessages;

/**
 * Default strategy of the Java plugin for the construction of Java element icons.
 */
public class JavaElementImageProvider {
	

	/**
	 * Flags for the JavaImageLabelProvider:
	 * Generate images with overlays.
	 */
	public final static int OVERLAY_ICONS= 0x1;

	/**
	 * Generate small sized images.
	 */
	public final static int SMALL_ICONS= 0x2;
	
	/**
	 * Use the 'light' style for rendering types.
	 */	
	public final static int LIGHT_TYPE_ICONS= 0x4;	


	public static final Point SMALL_SIZE= new Point(16, 16);
	public static final Point BIG_SIZE= new Point(22, 16);

	private static ImageDescriptor DESC_OBJ_PROJECT_CLOSED;	
	private static ImageDescriptor DESC_OBJ_PROJECT;	
	private static ImageDescriptor DESC_OBJ_FOLDER;
	{
		ISharedImages images= JavaPlugin.getDefault().getWorkbench().getSharedImages(); 
		DESC_OBJ_PROJECT_CLOSED= images.getImageDescriptor(ISharedImages.IMG_OBJ_PROJECT_CLOSED);
		DESC_OBJ_PROJECT= 		 images.getImageDescriptor(ISharedImages.IMG_OBJ_PROJECT);
		DESC_OBJ_FOLDER= 		 images.getImageDescriptor(ISharedImages.IMG_OBJ_FOLDER);
	}
	
	private ImageDescriptorRegistry fRegistry;
		
	public JavaElementImageProvider() {
		fRegistry= JavaPlugin.getImageDescriptorRegistry();
	}	
		
	/**
	 * Returns the icon for a given element. The icon depends on the element type
	 * and element properties. If configured, overlay icons are constructed for
	 * <code>ISourceReference</code>s.
	 * @param flags Flags as defined by the JavaImageLabelProvider
	 */
	public Image getImageLabel(Object element, int flags) {
		return getImageLabel(computeDescriptor(element, flags));
	}
	
	public Image getImageLabel(ImageDescriptor descriptor){
		if (descriptor == null) 
			return null;	
		return fRegistry.get(descriptor);
	}

	private ImageDescriptor computeDescriptor(Object element, int flags){
		if (element instanceof IJavaElement)
			return getJavaImageDescriptor((IJavaElement) element, flags);
		else if (element instanceof IAdaptable)
			return getWorkbenchImageDescriptor((IAdaptable) element, flags);
		return null;
		
	}
	
	private boolean showOverlayIcons(int flags) {
		return (flags & OVERLAY_ICONS) != 0;
	}
		
	private boolean useSmallSize(int flags) {
		return (flags & SMALL_ICONS) != 0;
	}
	
	private boolean useLightIcons(int flags) {
		return (flags & LIGHT_TYPE_ICONS) != 0;
	}	
	
	/**
	 * Returns an image descriptor for a java element. The descriptor includes overlays, if specified.
	 */
	public ImageDescriptor getJavaImageDescriptor(IJavaElement element, int flags) {
		int adornmentFlags= computeJavaAdornmentFlags(element, flags);
		Point size= useSmallSize(flags) ? SMALL_SIZE : BIG_SIZE;
		return new JavaElementImageDescriptor(getBaseImageDescriptor(element, flags), adornmentFlags, size);
	}

	/**
	 * Returns an image descriptor for a IAdaptable. The descriptor includes overlays, if specified (only error ticks apply).
	 * Returns <code>null</code> if no image could be found.
	 */	
	public ImageDescriptor getWorkbenchImageDescriptor(IAdaptable adaptable, int flags) {
		IWorkbenchAdapter wbAdapter= (IWorkbenchAdapter) adaptable.getAdapter(IWorkbenchAdapter.class);
		if (wbAdapter == null) {
			return null;
		}
		ImageDescriptor descriptor= wbAdapter.getImageDescriptor(adaptable);
		if (descriptor == null) {
			return null;
		}

		Point size= useSmallSize(flags) ? SMALL_SIZE : BIG_SIZE;
		return new JavaElementImageDescriptor(descriptor, 0, size);
	}
	
	// ---- Computation of base image key -------------------------------------------------
	
	/**
	 * Returns an image descriptor for a java element. This is the base image, no overlays.
	 */
	public ImageDescriptor getBaseImageDescriptor(IJavaElement element, int renderFlags) {

		try {			
			int flags;
			switch (element.getElementType()) {	
				case IJavaElement.INITIALIZER:
				case IJavaElement.METHOD:
					IMember member= (IMember) element;
					if (member.getDeclaringType().isInterface())
						return JavaPluginImages.DESC_MISC_PUBLIC;
					
					flags= member.getFlags();
					if (Flags.isPublic(flags))
						return JavaPluginImages.DESC_MISC_PUBLIC;
					if (Flags.isProtected(flags))
						return JavaPluginImages.DESC_MISC_PROTECTED;
					if (Flags.isPrivate(flags))
						return JavaPluginImages.DESC_MISC_PRIVATE;
					
					return JavaPluginImages.DESC_MISC_DEFAULT;
					
				case IJavaElement.FIELD:
					// This is for testing. We are trying to use different icons for fields and methods. But first try was
					// not very successful.
					IField field= (IField)element;
					if (field.getDeclaringType().isInterface())
						return JavaPluginImages.DESC_FIELD_PUBLIC;
					
					flags= field.getFlags();
					if (Flags.isPublic(flags))
						return JavaPluginImages.DESC_FIELD_PUBLIC;
					if (Flags.isProtected(flags))
						return JavaPluginImages.DESC_FIELD_PROTECTED;
					if (Flags.isPrivate(flags))
						return JavaPluginImages.DESC_FIELD_PRIVATE;
					
					return JavaPluginImages.DESC_FIELD_DEFAULT;
					
				case IJavaElement.PACKAGE_DECLARATION:
					return JavaPluginImages.DESC_OBJS_PACKDECL;
				
				case IJavaElement.IMPORT_DECLARATION:
					return JavaPluginImages.DESC_OBJS_IMPDECL;
					
				case IJavaElement.IMPORT_CONTAINER:
					return JavaPluginImages.DESC_OBJS_IMPCONT;
				
				case IJavaElement.TYPE: {
					IType type= (IType) element;
					
					if (useLightIcons(renderFlags)) {
						if (type.isClass())
							return JavaPluginImages.DESC_OBJS_CLASSALT;
						else 
							return JavaPluginImages.DESC_OBJS_INTERFACEALT;
					}					

					IJavaElement parent= type.getParent();
					int typeType= parent != null ? parent.getElementType() : 0;
					flags= type.getFlags();
					if (parent == null || typeType == IJavaElement.COMPILATION_UNIT || typeType == IJavaElement.CLASS_FILE) {
						if (type.isClass())
							return getClassImageDescriptor(flags);
						else
							return getInterfaceImageDescriptor(flags);
					} else {
						if (type.isClass())
							return getInnerClassImageDescriptor(flags);
						else
							return getInnerInterfaceImageDescriptor(flags);
					}
				}

				case IJavaElement.PACKAGE_FRAGMENT_ROOT: {
					IPackageFragmentRoot root= (IPackageFragmentRoot) element;
					if (root.isArchive()) {
						IPath attach= root.getSourceAttachmentPath();
						if (root.isExternal()) {
							if (attach == null) {
								return JavaPluginImages.DESC_OBJS_EXTJAR;
							} else {
								return JavaPluginImages.DESC_OBJS_EXTJAR_WSRC;
							}
						} else {
							if (attach == null) {
								return JavaPluginImages.DESC_OBJS_JAR;
							} else {
								return JavaPluginImages.DESC_OBJS_JAR_WSRC;
							}
						}							
					} else {
						return JavaPluginImages.DESC_OBJS_PACKFRAG_ROOT;
					}
				}
				
				case IJavaElement.PACKAGE_FRAGMENT:
					IPackageFragment fragment= (IPackageFragment)element;
					try {
						// show the folder icon for packages with only non Java resources
						// fix for: 1G5WN0V 
						if (!fragment.hasChildren() && (fragment.getNonJavaResources().length >0)) 
							return DESC_OBJ_FOLDER;
					} catch(JavaModelException e) {
						return DESC_OBJ_FOLDER;
					}
					return JavaPluginImages.DESC_OBJS_PACKAGE;
					
				case IJavaElement.COMPILATION_UNIT:
					return JavaPluginImages.DESC_OBJS_CUNIT;
					
				case IJavaElement.CLASS_FILE:
					/* this is too expensive for large packages
					try {
						IClassFile cfile= (IClassFile)element;
						if (cfile.isClass())
							return JavaPluginImages.IMG_OBJS_CFILECLASS;
						return JavaPluginImages.IMG_OBJS_CFILEINT;
					} catch(JavaModelException e) {
						// fall through;
					}*/
					return JavaPluginImages.DESC_OBJS_CFILE;
					
				case IJavaElement.JAVA_PROJECT: 
					IJavaProject jp= (IJavaProject)element;
					if (jp.getProject().isOpen()) {
						IProject project= jp.getProject();
						IWorkbenchAdapter adapter= (IWorkbenchAdapter)project.getAdapter(IWorkbenchAdapter.class);
						if (adapter != null) {
							ImageDescriptor result= adapter.getImageDescriptor(project);
							if (result != null)
								return result;
						}
						return DESC_OBJ_PROJECT;
					}
					return DESC_OBJ_PROJECT_CLOSED;
					
				case IJavaElement.JAVA_MODEL:
					return JavaPluginImages.DESC_OBJS_JAVA_MODEL;
			}
			
			Assert.isTrue(false, JavaUIMessages.getString("JavaImageLabelprovider.assert.wrongImage")); //$NON-NLS-1$
			return null; //$NON-NLS-1$
		
		} catch (JavaModelException e) {
			if (!e.isDoesNotExist()) {
				JavaPlugin.log(e);
			}
			return JavaPluginImages.DESC_OBJS_GHOST;
		}
	}

	// ---- Methods to compute the adornments flags ---------------------------------
	
	private int computeJavaAdornmentFlags(IJavaElement element, int renderFlags) {
		int flags= 0;
		if (showOverlayIcons(renderFlags) && element instanceof IMember) {
			try {
				IMember member= (IMember) element;
				
				if (element.getElementType() == IJavaElement.METHOD && ((IMethod)element).isConstructor())
					flags |= JavaElementImageDescriptor.CONSTRUCTOR;
					
				int modifiers= member.getFlags();
				if (Flags.isAbstract(modifiers) && confirmAbstract(member))
					flags |= JavaElementImageDescriptor.ABSTRACT;
				if (Flags.isFinal(modifiers) || isInterfaceField(member))
					flags |= JavaElementImageDescriptor.FINAL;
				if (Flags.isSynchronized(modifiers) && confirmSynchronized(member))
					flags |= JavaElementImageDescriptor.SYNCHRONIZED;
				if (Flags.isStatic(modifiers) || isInterfaceField(member))
					flags |= JavaElementImageDescriptor.STATIC;
				
				if (member.getElementType() == IJavaElement.TYPE) {
					if (JavaModelUtil.hasMainMethod((IType) member)) {
						flags |= JavaElementImageDescriptor.RUNNABLE;
					}
				}
			} catch (JavaModelException e) {
				// do nothing. Can't compute runnable adornment or get flags
			}
		}
		return flags;
	}
		
	private boolean confirmAbstract(IMember element) throws JavaModelException {
		// never show the abstract symbol on interfaces or members in interfaces
		if (element.getElementType() == IJavaElement.TYPE) {
			return ((IType) element).isClass();
		}
		return element.getDeclaringType().isClass();
	}
	
	private boolean isInterfaceField(IMember element) throws JavaModelException {
		// always show the final && static symbol on interface fields
		if (element.getElementType() == IJavaElement.FIELD) {
			return element.getDeclaringType().isInterface();
		}
		return false;
	}	
	
	private boolean confirmSynchronized(IJavaElement member) {
		// Synchronized types are allowed but meaningless.
		return member.getElementType() != IJavaElement.TYPE;
	}
	
	public void dispose() {
	}

	private ImageDescriptor getClassImageDescriptor(int flags) {
		if (Flags.isPublic(flags) || Flags.isProtected(flags) || Flags.isPrivate(flags))
			return JavaPluginImages.DESC_OBJS_CLASS;
		else
			return JavaPluginImages.DESC_OBJS_CLASS_DEFAULT;
	}
	
	private ImageDescriptor getInnerClassImageDescriptor(int flags) {
		if (Flags.isPublic(flags))
			return JavaPluginImages.DESC_OBJS_INNER_CLASS;
		else if (Flags.isPrivate(flags))
			return JavaPluginImages.DESC_OBJS_INNER_CLASS_PRIVATE;
		else if (Flags.isProtected(flags))
			return JavaPluginImages.DESC_OBJS_INNER_CLASS_PROTECTED;
		else
			return JavaPluginImages.DESC_OBJS_INNER_CLASS_DEFAULT;
	}
	
	private ImageDescriptor getInterfaceImageDescriptor(int flags) {
		if (Flags.isPublic(flags) || Flags.isProtected(flags) || Flags.isPrivate(flags))
			return JavaPluginImages.DESC_OBJS_INTERFACE;
		else
			return JavaPluginImages.DESC_OBJS_INTERFACE_DEFAULT;
	}
	
	private ImageDescriptor getInnerInterfaceImageDescriptor(int flags) {
		if (Flags.isPublic(flags))
			return JavaPluginImages.DESC_OBJS_INTERFACE;
		else if (Flags.isPrivate(flags))
			return JavaPluginImages.DESC_OBJS_INNER_INTERFACE_PRIVATE;
		else if (Flags.isProtected(flags))
			return JavaPluginImages.DESC_OBJS_INNER_INTERFACE_PROTECTED;
		else
			return JavaPluginImages.DESC_OBJS_INTERFACE_DEFAULT;
	}	
}