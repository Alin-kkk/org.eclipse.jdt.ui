/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.fix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.ibm.icu.text.Collator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpConfigurationUI;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpTabPage;
import org.eclipse.jdt.internal.ui.preferences.cleanup.ContributedCleanUpTabPage;

/**
 * The clean up registry provides a set of clean ups and there corresponding UI representatives.
 * 
 * @since 3.4
 */
public class CleanUpRegistry {

	private static final class ErrorPage implements ICleanUpConfigurationUI {

		private final Exception fException;

		private ErrorPage(Exception e) {
			fException= e;
		}

		public Composite createContents(Composite parent) {
			Composite result= new Composite(parent, SWT.NONE);
			result.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			result.setLayout(new GridLayout(1, false));

			Text text= new Text(result, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY);
			text.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			text.setText(Messages.format(FixMessages.CleanUpRegistry_ErrorTabPage_description, fException.getLocalizedMessage()));

			return result;
		}

		public int getCleanUpCount() {
			return 0;
		}

		public String getPreview() {
			return FixMessages.CleanUpRegistry_ErrorTabPage_preview;
		}

		public int getSelectedCleanUpCount() {
			return 0;
		}

		public void setOptions(CleanUpOptions options) {
		}
	}

	public static class CleanUpTabPageDescriptor {

		private static final String ATTRIBUTE_ID_CLASS= "class"; //$NON-NLS-1$
		private static final String ATTRIBUTE_ID_NAME= "name"; //$NON-NLS-1$
		private static final String ATTRIBUTE_NAME_KIND= "cleanUpKind"; //$NON-NLS-1$
		
		private final String fName;
		private final IConfigurationElement fElement;
		private int fKind;

		/**
		 * @param element the configuration element
		 */
		public CleanUpTabPageDescriptor(IConfigurationElement element) {
			fElement= element;
			fName= element.getAttribute(ATTRIBUTE_ID_NAME);
			String kind= fElement.getAttribute(ATTRIBUTE_NAME_KIND);
			fKind= getCleanUpKind(kind);
			if (fKind == -1)
				JavaPlugin.logErrorMessage(Messages.format(FixMessages.CleanUpRegistry_WrongKindForConfigurationUI_error, new String[] { element.getContributor().getName(), kind }));
		}

		/**
		 * @return the name of the tab
		 */
		public String getName() {
			return fName;
		}

		/**
		 * @return the kind of clean up
		 */
		public int getKind() {
			return fKind;
		}

		/**
		 * @return new instance of a tab page
		 */
		public CleanUpTabPage createTabPage() {
			try {
				ICleanUpConfigurationUI page= (ICleanUpConfigurationUI)fElement.createExecutableExtension(ATTRIBUTE_ID_CLASS);
				if (page instanceof CleanUpTabPage)
					return (CleanUpTabPage)page;

				return new ContributedCleanUpTabPage(page);
			} catch (final CoreException e) {
				JavaPlugin.log(e);
				return new ContributedCleanUpTabPage(new ErrorPage(e));
			}
		}
	}

	private static class CleanUpDescriptor {

		private static final String ATTRIBUTE_ID_CLASS= "class"; //$NON-NLS-1$
		private static final String ATTRIBURE_ID_RUNAFTER= "runAfter"; //$NON-NLS-1$
		private static final String ATTRIBUTE_ID_ID= "id"; //$NON-NLS-1$

		private final IConfigurationElement fElement;
		private final String fId;
		private final String fRunAfter;

		/**
		 * @param element the configuration element
		 */
		public CleanUpDescriptor(IConfigurationElement element) {
			fElement= element;
			fId= element.getAttribute(ATTRIBUTE_ID_ID);
			fRunAfter= element.getAttribute(ATTRIBURE_ID_RUNAFTER);
		}

		/**
		 * @return the unique id of this clean up
		 */
		public String getId() {
			return fId;
		}

		/**
		 * @return the id of the clean up which must run before this clean up or
		 *         <strong>null</strong> if none specified
		 */
		public String getRunAfter() {
			return fRunAfter;
		}

		/**
		 * @return the clean up or <strong>null</strong> if the clean up could not be instantiated
		 */
		public ICleanUp createCleanUp() {
			try {
				return (ICleanUp)fElement.createExecutableExtension(ATTRIBUTE_ID_CLASS);
			} catch (CoreException e) {
				JavaPlugin.log(e);
				return null;
			}
		}
	}

	private static final class CleanUpInitializerDescriptor {

		private static final String ATTRIBUTE_NAME_CLASS= "class"; //$NON-NLS-1$
		private static final String ATTRIBUTE_NAME_KIND= "cleanUpKind"; //$NON-NLS-1$

		private final IConfigurationElement fElement;

		private final int fKind;

		private ICleanUpOptionsInitializer fOptionsProvider;

		public CleanUpInitializerDescriptor(IConfigurationElement element) {
			fElement= element;
			String kind= fElement.getAttribute(ATTRIBUTE_NAME_KIND);
			fKind= getCleanUpKind(kind);
			if (fKind == -1)
				JavaPlugin.logErrorMessage(Messages.format(FixMessages.CleanUpRegistry_UnknownInitializerKind_errorMessage, new String[] { element.getContributor().getName(), kind }));
		}

		public int getKind() {
			return fKind;
		}

		public ICleanUpOptionsInitializer getOptionsInitializer() {
			if (fOptionsProvider == null) {
				try {
					fOptionsProvider= (ICleanUpOptionsInitializer)fElement.createExecutableExtension(ATTRIBUTE_NAME_CLASS);
				} catch (CoreException e) {
					JavaPlugin.log(e);
					fOptionsProvider= new ICleanUpOptionsInitializer() {
						public void setDefaultOptions(CleanUpOptions options) {
						}
					};
				}
			}
			return fOptionsProvider;
		}
	}
	
	private static final String EXTENSION_POINT_NAME= "cleanUps"; //$NON-NLS-1$
	private static final String CLEANUP_CONFIGURATION_ELEMENT_NAME= "cleanUp"; //$NON-NLS-1$
	private static final String TABPAGE_CONFIGURATION_ELEMENT_NAME= "cleanUpConfigurationUI"; //$NON-NLS-1$

	private static final String CLEAN_UP_INITIALIZER_CONFIGURATION_ELEMENT_NAME= "cleanUpOptionsInitializer"; //$NON-NLS-1$
	private static final String ATTRIBUTE_KIND_TYPE_SAVE_ACTION= "saveAction"; //$NON-NLS-1$
	private static final String ATTRIBUTE_KIND_TYPE_CLEAN_UP= "cleanUp"; //$NON-NLS-1$

	private CleanUpDescriptor[] fCleanUpDescriptors;
	private CleanUpTabPageDescriptor[] fPageDescriptors;

	private CleanUpInitializerDescriptor[] fCleanUpInitializerDescriptors;

	/**
	 * Creates and returns the registered clean ups that don't fail upon creation.
	 * 
	 * @return an array of clean ups
	 */
	public synchronized ICleanUp[] createCleanUps() {
		ensureCleanUpsRegistered();
		ArrayList result= new ArrayList(fCleanUpDescriptors.length);
		for (int i= 0; i < fCleanUpDescriptors.length; i++) {
			ICleanUp cleanUp= fCleanUpDescriptors[i].createCleanUp();
			if (cleanUp != null)
				result.add(cleanUp);
		}
		return (ICleanUp[])result.toArray(new ICleanUp[result.size()]);
	}

	/**
	 * @param kind the kind of clean up for which to retrieve the configuratin pages
	 * 
	 * @return set of clean up tab page descriptors
	 * 
	 * @see CleanUpConstants#DEFAULT_CLEAN_UP_OPTIONS
	 * @see CleanUpConstants#DEFAULT_SAVE_ACTION_OPTIONS
	 */
	public synchronized CleanUpTabPageDescriptor[] getCleanUpTabPageDescriptors(int kind) {
		ensurePagesRegistered();

		ArrayList result= new ArrayList();
		for (int i= 0; i < fPageDescriptors.length; i++) {
			if (fPageDescriptors[i].getKind() == kind)
				result.add(fPageDescriptors[i]);
		}
		return (CleanUpTabPageDescriptor[])result.toArray(new CleanUpTabPageDescriptor[result.size()]);
	}

	/**
	 * Returns the default options for the specified clean up kind.
	 * 
	 * @param kind the kind of clean up for which to retrieve the options
	 * @return the default options
	 * 
	 * @see CleanUpConstants#DEFAULT_CLEAN_UP_OPTIONS
	 * @see CleanUpConstants#DEFAULT_SAVE_ACTION_OPTIONS
	 */
	public MapCleanUpOptions getDefaultOptions(int kind) {
		ensureCleanUpInitializersRegistered();

		CleanUpOptions options= new CleanUpOptions();
		for (int i= 0; i < fCleanUpInitializerDescriptors.length; i++) {
			CleanUpInitializerDescriptor descriptor= fCleanUpInitializerDescriptors[i];
			if (descriptor.getKind() == kind) {
				descriptor.getOptionsInitializer().setDefaultOptions(options);
			}
		}
		MapCleanUpOptions mapCleanUpOptions= new MapCleanUpOptions();
		mapCleanUpOptions.addAll(options);
		return mapCleanUpOptions;
	}

	private synchronized void ensureCleanUpsRegistered() {
		if (fCleanUpDescriptors != null)
			return;

		ArrayList descriptors= new ArrayList();

		IExtensionPoint point= Platform.getExtensionRegistry().getExtensionPoint(JavaPlugin.getPluginId(), EXTENSION_POINT_NAME);
		IConfigurationElement[] elements= point.getConfigurationElements();
		for (int i= 0; i < elements.length; i++) {
			IConfigurationElement element= elements[i];

			if (CLEANUP_CONFIGURATION_ELEMENT_NAME.equals(element.getName())) {
				descriptors.add(new CleanUpDescriptor(element));
			}
		}


		// Make sure we filter those who fail
		for (int i= 0; i < descriptors.size(); i++) {
			ICleanUp cleanUp= ((CleanUpDescriptor)descriptors.get(i)).createCleanUp();
			if (cleanUp == null)
				descriptors.remove(i--);
		}

		fCleanUpDescriptors= (CleanUpDescriptor[])descriptors.toArray(new CleanUpDescriptor[descriptors.size()]);
		sort(fCleanUpDescriptors);
	}

	private static void sort(CleanUpDescriptor[] data) {
		int lastSwapI= -1;
		int lastSwapJ= -1;
		mainLoop: for (int i= 0; i < data.length; i++) {
			String runAfter= data[i].getRunAfter();
			if (runAfter == null)
				continue;
			int jStart= i + 1;
			for (int j= jStart; j < data.length; j++) {
				String jID= data[j].getId();
				if (runAfter.equals(jID)) {
					if (lastSwapI == i && j >= lastSwapJ) {
						JavaPlugin.logErrorMessage("Problem reading cleanUps extensions: cannot satisfy rule for '" + data[i].getId() + "' to runAfter '" + runAfter + "'");   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
						continue mainLoop;
					}
					lastSwapI= i;
					lastSwapJ= j;
					swap(data, i, j);
					i--;
					continue mainLoop;
				}
			}
			for (int j= 0; j < jStart; j++) {
				String jID= data[j].getId();
				if (runAfter.equals(jID))
					continue mainLoop;
			}
			JavaPlugin.logErrorMessage("Problem reading cleanUps extensions: cannot satisfy rule for '" + data[i].getId() + "' to runAfter '" + runAfter + "' because the runAfter clean up does not exist."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	private static void swap(CleanUpDescriptor[] data, int i, int j) {
		CleanUpDescriptor o= data[i];
		data[i]= data[j];
		data[j]= o;
	}

	private synchronized void ensurePagesRegistered() {
		if (fPageDescriptors != null)
			return;
		
		ArrayList result= new ArrayList();

		IExtensionPoint point= Platform.getExtensionRegistry().getExtensionPoint(JavaPlugin.getPluginId(), EXTENSION_POINT_NAME);
		IConfigurationElement[] elements= point.getConfigurationElements();
		for (int i= 0; i < elements.length; i++) {
			IConfigurationElement element= elements[i];

			if (TABPAGE_CONFIGURATION_ELEMENT_NAME.equals(element.getName())) {
				result.add(new CleanUpTabPageDescriptor(element));
			}
		}

		fPageDescriptors= (CleanUpTabPageDescriptor[])result.toArray(new CleanUpTabPageDescriptor[result.size()]);
		Arrays.sort(fPageDescriptors, new Comparator() {
			public int compare(Object o1, Object o2) {
				String name1= ((CleanUpTabPageDescriptor)o1).getName();
				String name2= ((CleanUpTabPageDescriptor)o2).getName();
				return Collator.getInstance().compare(name1.replaceAll("&", ""), name2.replaceAll("&", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}
		});
	}

	private synchronized void ensureCleanUpInitializersRegistered() {
		if (fCleanUpInitializerDescriptors != null)
			return;

		ArrayList result= new ArrayList();

		IExtensionPoint point= Platform.getExtensionRegistry().getExtensionPoint(JavaPlugin.getPluginId(), EXTENSION_POINT_NAME);
		IConfigurationElement[] elements= point.getConfigurationElements();
		for (int i= 0; i < elements.length; i++) {
			IConfigurationElement element= elements[i];

			if (CLEAN_UP_INITIALIZER_CONFIGURATION_ELEMENT_NAME.equals(element.getName())) {
				result.add(new CleanUpInitializerDescriptor(element));
			}
		}

		fCleanUpInitializerDescriptors= (CleanUpInitializerDescriptor[])result.toArray(new CleanUpInitializerDescriptor[result.size()]);
	}

	private static int getCleanUpKind(String kind) {
		if (kind.equals(ATTRIBUTE_KIND_TYPE_CLEAN_UP)) {
			return CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS;
		} else if (kind.equals(ATTRIBUTE_KIND_TYPE_SAVE_ACTION)) {
			return CleanUpConstants.DEFAULT_SAVE_ACTION_OPTIONS;
		} else {
			return -1;
		}
	}

}