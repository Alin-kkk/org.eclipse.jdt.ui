/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * A registry for all extensions to the
 * <code>org.eclipse.jdt.ui.javaCompletionProposalComputer</code>
 * extension point.
 * 
 * @since 3.2
 */
public final class CompletionProposalComputerRegistry {

	private static final String EXTENSION_POINT= "javaCompletionProposalComputer"; //$NON-NLS-1$
	
	/** The singleton instance. */
	private static CompletionProposalComputerRegistry fgSingleton= null;
	
	/**
	 * Returns the default computer registry.
	 * <p>
	 * TODO keep this or add some other singleton, e.g. JavaPlugin?
	 * </p>
	 * 
	 * @return the singleton instance
	 */
	public static synchronized CompletionProposalComputerRegistry getDefault() {
		if (fgSingleton == null) {
			fgSingleton= new CompletionProposalComputerRegistry();
		}
		
		return fgSingleton;
	}
	
	/**
	 * The sets of descriptors, grouped by partition type (key type:
	 * {@link String}, value type:
	 * {@linkplain List List&lt;CompletionProposalComputerDescriptor&gt;}).
	 */
	private final Map fDescriptorsByPartition= new HashMap();
	/**
	 * Unmodifiable versions of the sets stored in
	 * <code>fDescriptorsByPartition</code> (key type: {@link String},
	 * value type:
	 * {@linkplain List List&lt;CompletionProposalComputerDescriptor&gt;}).
	 */
	private final Map fPublicDescriptorsByPartition= new HashMap();
	/**
	 * All descriptors (element type:
	 * {@link CompletionProposalComputerDescriptor}).
	 */
	private final List fDescriptors= new ArrayList();
	/**
	 * Unmodifiable view of <code>fDescriptors</code>
	 */
	private final List fPublicDescriptors= Collections.unmodifiableList(fDescriptors);
	
	private final List fCategories= new ArrayList();
	private final List fPublicCategories= Collections.unmodifiableList(fCategories);
	/**
	 * <code>true</code> if this registry has been loaded.
	 */
	private boolean fLoaded= false;

	/**
	 * Creates a new instance.
	 */
	public CompletionProposalComputerRegistry() {
	}

	/**
	 * Returns the list of {@link CompletionProposalComputerDescriptor}s describing all extensions
	 * to the <code>javaCompletionProposalComputer</code> extension point for the given partition
	 * type.
	 * <p>
	 * A valid partition is either one of the constants defined in
	 * {@link org.eclipse.jdt.ui.text.IJavaPartitions} or
	 * {@link org.eclipse.jface.text.IDocument#DEFAULT_CONTENT_TYPE}. An empty list is returned if
	 * there are no extensions for the given partition.
	 * </p>
	 * <p>
	 * The returned list is read-only and is sorted in the order that the extensions were read in.
	 * There are no duplicate elements in the returned list. The returned list may change if plug-ins
	 * are loaded or unloaded while the application is running or if an extension violates the API
	 * contract of {@link org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer}. When
	 * computing proposals, it is therefore imperative to copy the returned list before iterating
	 * over it.
	 * </p>
	 * 
	 * @param partition
	 *        the partition type for which to retrieve the computer descriptors
	 * @return the list of extensions to the <code>javaCompletionProposalComputer</code> extension
	 *         point (element type: {@link CompletionProposalComputerDescriptor})
	 */
	List getProposalComputerDescriptors(String partition) {
		ensureExtensionPointRead();
		List result= (List) fPublicDescriptorsByPartition.get(partition);
		return result != null ? result : Collections.EMPTY_LIST;
	}

	/**
	 * Returns the list of {@link CompletionProposalComputerDescriptor}s describing all extensions
	 * to the <code>javaCompletionProposalComputer</code> extension point.
	 * <p>
	 * The returned list is read-only and is sorted in the order that the extensions were read in.
	 * There are no duplicate elements in the returned list. The returned list may change if plug-ins
	 * are loaded or unloaded while the application is running or if an extension violates the API
	 * contract of {@link org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer}. When
	 * computing proposals, it is therefore imperative to copy the returned list before iterating
	 * over it.
	 * </p>
	 * 
	 * @return the list of extensions to the <code>javaCompletionProposalComputer</code> extension
	 *         point (element type: {@link CompletionProposalComputerDescriptor})
	 */
	List getProposalComputerDescriptors() {
		ensureExtensionPointRead();
		return fPublicDescriptors;
	}
	
	/**
	 * Returns the list of proposal categories contributed to the
	 * <code>javaCompletionProposalComputer</code> extension point.
	 * <p>
	 * <p>
	 * The returned list is read-only and is sorted in the order that the extensions were read in.
	 * There are no duplicate elements in the returned list. The returned list may change if
	 * plug-ins are loaded or unloaded while the application is running.
	 * </p>
	 * 
	 * @return list of proposal categories contributed to the
	 *         <code>javaCompletionProposalComputer</code> extension point (element type:
	 *         {@link CompletionProposalCategory})
	 */
	public List getProposalCategories() {
		ensureExtensionPointRead();
		return fPublicCategories;
	}

	/**
	 * Ensures that the extensions are read and stored in
	 * <code>fDescriptorsByPartition</code>.
	 */
	private void ensureExtensionPointRead() {
		boolean reload;
		synchronized (this) {
			reload= !fLoaded;
			fLoaded= true;
		}
		if (reload)
			reload();
	}

	/**
	 * Reloads the extensions to the extension point.
	 * <p>
	 * This method can be called more than once in order to reload from
	 * a changed extension registry.
	 * </p>
	 */
	public void reload() {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		List elements= new ArrayList(Arrays.asList(registry.getConfigurationElementsFor(JavaPlugin.getPluginId(), EXTENSION_POINT)));
		
		Map map= new HashMap();
		List all= new ArrayList();
		
		List categories= getCategories(elements);
		for (Iterator iter= elements.iterator(); iter.hasNext();) {
			IConfigurationElement element= (IConfigurationElement) iter.next();
			try {
				CompletionProposalComputerDescriptor desc= new CompletionProposalComputerDescriptor(element, this, categories);
				Set partitions= desc.getPartitions();
				for (Iterator it= partitions.iterator(); it.hasNext();) {
					String partition= (String) it.next();
					List list= (List) map.get(partition);
					if (list == null) {
						list= new ArrayList();
						map.put(partition, list);
					}
					list.add(desc);
				}
				all.add(desc);
				
			} catch (InvalidRegistryObjectException x) {
				/*
				 * Element is not valid any longer as the contributing plug-in was unloaded or for
				 * some other reason. Do not include the extension in the list and inform the user
				 * about it.
				 */
				Object[] args= {element.toString()};
				String message= MessageFormat.format(JavaTextMessages.CompletionProposalComputerRegistry_invalid_message, args);
				IStatus status= new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, message, x);
				informUser(status);
			}
		}
		
		synchronized (this) {
			fCategories.clear();
			fCategories.addAll(categories);
			
			Set partitions= map.keySet();
			fDescriptorsByPartition.keySet().retainAll(partitions);
			fPublicDescriptorsByPartition.keySet().retainAll(partitions);
			for (Iterator it= partitions.iterator(); it.hasNext();) {
				String partition= (String) it.next();
				List old= (List) fDescriptorsByPartition.get(partition);
				List current= (List) map.get(partition);
				if (old != null) {
					old.clear();
					old.addAll(current);
				} else {
					fDescriptorsByPartition.put(partition, current);
					fPublicDescriptorsByPartition.put(partition, Collections.unmodifiableList(current));
				}
			}
			
			fDescriptors.clear();
			fDescriptors.addAll(all);
		}
	}

	private List getCategories(List elements) {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		String preference= store.getString(PreferenceConstants.CODEASSIST_EXCLUDED_CATEGORIES);
		Set disabled= new HashSet();
		StringTokenizer tok= new StringTokenizer(preference, "\0");  //$NON-NLS-1$
		while (tok.hasMoreTokens())
			disabled.add(tok.nextToken());
		Map ordered= new HashMap();
		preference= store.getString(PreferenceConstants.CODEASSIST_CATEGORY_ORDER);
		tok= new StringTokenizer(preference, "\0"); //$NON-NLS-1$
		while (tok.hasMoreTokens()) {
			StringTokenizer inner= new StringTokenizer(tok.nextToken(), ":"); //$NON-NLS-1$
			String id= inner.nextToken();
			int rank= Integer.parseInt(inner.nextToken());
			ordered.put(id, new Integer(rank));
		}
		
		List categories= new ArrayList();
		for (Iterator iter= elements.iterator(); iter.hasNext();) {
			IConfigurationElement element= (IConfigurationElement) iter.next();
			try {
				if (element.getName().equals("proposalCategory")) { //$NON-NLS-1$
					iter.remove(); // remove from list to leave only computers
					
					CompletionProposalCategory category= new CompletionProposalCategory(element, this);
					categories.add(category);
					category.setIncluded(!disabled.contains(category.getId()));
					Integer rank= (Integer) ordered.get(category.getId());
					if (rank != null) {
						int r= rank.intValue();
						boolean separate= r < 0xffff;
						category.setSeparateCommand(separate);
						category.setSortOrder(r);
					}
				}
			} catch (InvalidRegistryObjectException x) {
				/*
				 * Element is not valid any longer as the contributing plug-in was unloaded or for
				 * some other reason. Do not include the extension in the list and inform the user
				 * about it.
				 */
				Object[] args= {element.toString()};
				String message= MessageFormat.format(JavaTextMessages.CompletionProposalComputerRegistry_invalid_message, args);
				IStatus status= new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, message, x);
				informUser(status);
			}
		}
		return categories;
	}

	/**
	 * Remove the descriptor from the set of managed descriptors.
	 * Nothing happens if <code>descriptor</code> is not managed by
	 * the receiver.
	 * 
	 * @param descriptor the descriptor to be removed
	 * @param status a status object that will be logged
	 */
	void remove(CompletionProposalComputerDescriptor descriptor, IStatus status) {
		Set partitions= descriptor.getPartitions();
		for (Iterator it= partitions.iterator(); it.hasNext();) {
			String partition= (String) it.next();
			List descriptors= (List) fDescriptorsByPartition.get(partition);
			if (descriptors != null)
				descriptors.remove(descriptor);
		}
		informUser(status);
	}

	private void informUser(IStatus status) {
		JavaPlugin.log(status);
		String title= JavaTextMessages.CompletionProposalComputerRegistry_error_dialog_title;
		String message= status.getMessage();
		MessageDialog.openError(JavaPlugin.getActiveWorkbenchShell(), title, message);
	}
}
