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
package org.eclipse.ltk.core.refactoring.participants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPluginRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;

import org.eclipse.core.expressions.EvaluationContext;

import org.eclipse.ltk.internal.core.refactoring.Assert;
import org.eclipse.ltk.internal.core.refactoring.RefactoringCorePlugin;

/* package */ class ExtensionManager {
	
	private String fName;
		
	private String fParticipantID;
	private List fParticipants;
	
	//---- debuging----------------------------------------
	/*
	private static final boolean EXIST_TRACING;
	static {
		String value= Platform.getDebugOption("org.eclipse.jdt.ui/processor/existTracing"); //$NON-NLS-1$
		EXIST_TRACING= value != null && value.equalsIgnoreCase("true"); //$NON-NLS-1$
	}
	
	private void printTime(long start) {
		System.out.println("[" + fName +  //$NON-NLS-1$
			" extension manager] - existing test: " +  //$NON-NLS-1$
			(System.currentTimeMillis() - start) + " ms"); //$NON-NLS-1$
	}
	*/
	
	public ExtensionManager(String name, String participantId) {
		Assert.isNotNull(name);
		Assert.isNotNull(participantId);
		fName= name;
		fParticipantID= participantId;
	}
	
	public String getName() {
		return fName;
	}
	
	public RefactoringParticipant[] getParticipants(RefactoringProcessor processor, Object[] elements, EvaluationContext evalContext, SharableParticipants shared) throws CoreException {
		if (fParticipants == null)
			init();
		
		List result= new ArrayList();
		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			for (Iterator iter= fParticipants.iterator(); iter.hasNext();) {
				ParticipantDescriptor descriptor= (ParticipantDescriptor)iter.next();
				try {
					if (descriptor.matches(evalContext)) {
						RefactoringParticipant participant= shared.get(descriptor);
						if (participant != null) {
							((ISharableParticipant)participant).addElement(element);
						} else {
							participant= descriptor.createParticipant();
							participant.initialize(processor, element);
							if (participant.isApplicable()) {
								result.add(participant);
								if (participant instanceof ISharableParticipant)
									shared.put(descriptor, participant);
							}
						}
					}
				} catch (CoreException e) {
					RefactoringCorePlugin.log(e.getStatus());
					iter.remove();
				}
			}
		}
		
		return (RefactoringParticipant[])result.toArray(new RefactoringParticipant[result.size()]);
	}

	private void init() {
		IPluginRegistry registry= Platform.getPluginRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor(
			RefactoringCorePlugin.getPluginId(), 
			fParticipantID);
		fParticipants= new ArrayList(ces.length); 
		for (int i= 0; i < ces.length; i++) {
			ParticipantDescriptor descriptor= new ParticipantDescriptor(ces[i]);
			IStatus status= descriptor.checkSyntax();
			switch (status.getSeverity()) {
				case IStatus.ERROR:
					RefactoringCorePlugin.log(status);
					break;
				case IStatus.WARNING:
				case IStatus.INFO:
					RefactoringCorePlugin.log(status);
					// fall through
				default:
					fParticipants.add(descriptor);
			}
		}
	}
}
