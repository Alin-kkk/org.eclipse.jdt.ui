/*******************************************************************************
 * Copyright (c) 2003 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPluginRegistry;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jdt.internal.corext.Assert;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public abstract class DescriptorManager {
	
	private String fExtensionPoint;
	private AbstractDescriptor[] fExtensions;

	public DescriptorManager(String extensionPoint) {
		fExtensionPoint= extensionPoint;
	}
	
	public AbstractDescriptor getDescriptor(Object element) throws CoreException {
		if (fExtensions == null)
			init();
			
		List candidates= new ArrayList(1);
		for (int i= 0; i < fExtensions.length; i++) {
			AbstractDescriptor descriptor= fExtensions[i];
			if (descriptor.matches(element)) {
				candidates.add(descriptor);
			}
			descriptor.clear();
		}
		if (candidates.size() == 0)
			return null;
		if (candidates.size() == 1)
			return (AbstractDescriptor)candidates.get(0);
		Assert.isTrue(false,"Not support for conflict resolution yet");
		return null;
	}
	
	protected abstract AbstractDescriptor createDescriptor(IConfigurationElement element);
	
	// ---- extension point reading -----------------------------------
	
	private void init() {
		IPluginRegistry registry= Platform.getPluginRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor(
			JavaPlugin.getPluginId(), 
			fExtensionPoint);
		fExtensions= new AbstractDescriptor[ces.length];
		for (int i= 0; i < ces.length; i++) {
			fExtensions[i]= createDescriptor(ces[i]);
		}
	}
}
