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
package org.eclipse.jdt.internal.corext.refactoring.participants;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class ParticipantDescriptor {
	
	private IConfigurationElement fConfigurationElement;

	private static final String ID= "id"; //$NON-NLS-1$
	private static final String CLASS= "class"; //$NON-NLS-1$
	
	private static final String SCOPE_STATE= "scopeState"; //$NON-NLS-1$
	private static final String OBJECT_STATE= "objectState"; //$NON-NLS-1$

	public ParticipantDescriptor(IConfigurationElement element) {
		fConfigurationElement= element;
	}
	
	public String getId() {
		return fConfigurationElement.getAttribute(ID);
	}
	
	public IStatus checkSyntax() {
		IConfigurationElement[] children= fConfigurationElement.getChildren(SCOPE_STATE);
		switch(children.length) {
			case 0:
				return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.ERROR,
					"Mandantory element <scopeState> missing. Disabling rename participant " + getId(), null);
			case 1:
				break;
			default:
				return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.ERROR,
					"Only one <scopeState> element allowed. Disabling rename participant " + getId(), null);
		}
		children= fConfigurationElement.getChildren(OBJECT_STATE);
		if (children.length > 1) {
			return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.ERROR,
				"Only one <objectState> element allowed. Disabling rename participant " + getId(), null);
		}
		return new Status(IStatus.OK, JavaPlugin.getPluginId(), IStatus.OK, 
			"Syntactically correct rename participant element", null);
	}
	
	public boolean matches(IRefactoringProcessor processor, Object element) throws CoreException {
		IConfigurationElement scopeState= fConfigurationElement.getChildren(SCOPE_STATE)[0];
		Expression exp= new ScopeStateExpression(scopeState);
		if (!exp.evaluate(processor.getScope()))
			return false;
		
		IConfigurationElement[] children= fConfigurationElement.getChildren(OBJECT_STATE);
		if (children.length == 1) {
			IConfigurationElement objectState= children[0];
			exp= new ObjectStateExpression(objectState);
			return exp.evaluate(element);
			
		}
		return true;
	}

	public IRefactoringParticipant createParticipant() throws CoreException {
		return (IRefactoringParticipant)fConfigurationElement.createExecutableExtension(CLASS);
	}
}
