/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.net.URL;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.PerformanceStats;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposalComputer;
import org.eclipse.jface.text.contentassist.TextContentAssistInvocationContext;

import org.eclipse.jdt.ui.text.IJavaPartitions;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.osgi.framework.Bundle;

/**
 * The description of an extension to the
 * <code>org.eclipse.jdt.ui.javaCompletionProposalComputer</code>
 * extension point. Instances are immutable and have a unique identifier
 * (see {@link #getId()}).
 * 
 * @since 3.2
 */
public final class CompletionProposalComputerDescriptor {
	/** The extension point name of the partition type attribute. */
	private static final String TYPE= "type"; //$NON-NLS-1$
	/** The extension point name of the class attribute. */
	private static final String CLASS= "class"; //$NON-NLS-1$
	/** The extension point name of the icon attribute. */
	private static final String ICON= "icon"; //$NON-NLS-1$
	/** The extension point name of the activate attribute. */
	private static final String ACTIVATE= "activate"; //$NON-NLS-1$
	/** The extension point name of the partition child elements. */
	private static final String PARTITION= "partition"; //$NON-NLS-1$
	/** Set of Java partition types. */
	private static final Set PARTITION_SET;
	/** The name of the performance event used to trace extensions. */
	private static final String PERFORMANCE_EVENT= JavaPlugin.getPluginId() + "/perf/content_assist/extensions"; //$NON-NLS-1$
	/**
	 * If <code>true</code>, execution time of extensions is measured and extensions may be
	 * disabled if execution takes too long.
	 */
	private static final boolean MEASURE_PERFORMANCE= PerformanceStats.isEnabled(PERFORMANCE_EVENT);
	/* log constants */
	private static final String COMPUTE_COMPLETION_PROPOSALS= "computeCompletionProposals()"; //$NON-NLS-1$
	private static final String COMPUTE_CONTEXT_INFORMATION= "computeContextInformation()"; //$NON-NLS-1$
	
	static {
		Set partitions= new HashSet();
		partitions.add(IDocument.DEFAULT_CONTENT_TYPE);
		partitions.add(IJavaPartitions.JAVA_DOC);
		partitions.add(IJavaPartitions.JAVA_MULTI_LINE_COMMENT);
		partitions.add(IJavaPartitions.JAVA_SINGLE_LINE_COMMENT);
		partitions.add(IJavaPartitions.JAVA_STRING);
		partitions.add(IJavaPartitions.JAVA_CHARACTER);
		
		PARTITION_SET= Collections.unmodifiableSet(partitions);
	}

	/** The identifier of the extension. */
	private final String fId;
	/** The name of the extension. */
	private final String fName;
	/** The class name of the provided <code>ICompletionProposalComputer</code>. */
	private final String fClass;
	/** The activate attribute value. */
	private final boolean fActivate;
	/** The partition of the extension (element type: {@link String}). */
	private final Set fPartitions;
	/** The configuration element of this extension. */
	private final IConfigurationElement fElement;
	/** The ordinal of this descriptor, which is used to define the natural order of descriptor objects constant. */
	private final int fOrdinal;
	/** The registry we are registered with. */
	private final CompletionProposalComputerRegistry fRegistry;
	/** The computer, if instantiated, <code>null</code> otherwise. */
	ICompletionProposalComputer fComputer;
	/** The enablement state (internal). */
	private boolean fEnabled;
	/** The image descriptor for this computer, or <code>null</code> if none specified. */
	private final ImageDescriptor fImage;

	/**
	 * Creates a new descriptor.
	 * 
	 * @param element the configuration element to read
	 * @param ordinal the ordinal of this descriptor
	 * @param registry the computer registry creating this descriptor
	 */
	CompletionProposalComputerDescriptor(IConfigurationElement element, int ordinal, CompletionProposalComputerRegistry registry) throws InvalidRegistryObjectException {
		Assert.isLegal(registry != null);
		Assert.isLegal(element != null);
		
		fRegistry= registry;
		fElement= element;
		IExtension extension= element.getDeclaringExtension();
		fId= extension.getUniqueIdentifier();
		checkNotNull(fId, "id"); //$NON-NLS-1$

		String name= extension.getLabel();
		if (name.length() == 0)
			fName= fId;
		else
			fName= name;
		
		Set partitions= new HashSet();
		IConfigurationElement[] children= element.getChildren(PARTITION);
		if (children.length == 0) {
			fPartitions= PARTITION_SET; // add to all partition types if no partition is configured
		} else {
			for (int i= 0; i < children.length; i++) {
				String type= children[i].getAttributeAsIs(TYPE);
				checkNotNull(type, TYPE);
				partitions.add(type);
			}
			fPartitions= Collections.unmodifiableSet(partitions);
		}
		
		String activateAttribute= element.getAttributeAsIs(ACTIVATE);
		fActivate= Boolean.valueOf(activateAttribute).booleanValue();

		fClass= element.getAttributeAsIs(CLASS);
		checkNotNull(fClass, CLASS);
		
		String icon= element.getAttributeAsIs(ICON);
		ImageDescriptor img= null;
		if (icon != null) {
			Bundle bundle= getBundle();
			if (bundle != null) {
				Path path= new Path(icon);
				URL url= Platform.find(bundle, path);
				img= ImageDescriptor.createFromURL(url);
			}
		}
		fImage= img;
		
		fOrdinal= ordinal;
		fEnabled= true;
	}

	/**
	 * Checks an element that must be defined according to the extension
	 * point schema. Throws an
	 * <code>InvalidRegistryObjectException</code> if <code>obj</code>
	 * is <code>null</code>.
	 */
	private void checkNotNull(Object obj, String attribute) throws InvalidRegistryObjectException {
		if (obj == null) {
			Object[] args= { getId(), fElement.getNamespace(), attribute };
			String message= MessageFormat.format(JavaTextMessages.CompletionProposalComputerDescriptor_illegal_attribute_message, args);
			IStatus status= new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, message, null);
			JavaPlugin.log(status);
			throw new InvalidRegistryObjectException();
		}
	}

	/**
	 * Returns the identifier of the described extension.
	 *
	 * @return Returns the id
	 */
	public String getId() {
		return fId;
	}

	/**
	 * Returns the name of the described extension.
	 * 
	 * @return Returns the name
	 */
	public String getName() {
		return fName;
	}
	
	/**
	 * Returns the partition types of the described extension.
	 * 
	 * @return the set of partition types (element type: {@link String})
	 */
	public Set getPartitions() {
		return fPartitions;
	}
	
	/**
	 * Returns a cached instance of the computer as described in the
	 * extension's xml. The computer is
	 * {@link #createComputer() created} the first time that this method
	 * is called and then cached.
	 * 
	 * @return a new instance of the completion proposal computer as
	 *         described by this descriptor
	 * @throws CoreException if the creation fails
	 * @throws InvalidRegistryObjectException if the extension is not
	 *         valid any longer (e.g. due to plug-in unloading)
	 */
	private synchronized ICompletionProposalComputer getComputer() throws CoreException, InvalidRegistryObjectException {
		if (fComputer == null && (fActivate || isPluginLoaded()))
			fComputer= createComputer();
		return fComputer;
	}

	private boolean isPluginLoaded() {
		Bundle bundle= getBundle();
		return bundle != null && bundle.getState() == Bundle.ACTIVE;
	}

	private Bundle getBundle() {
		String namespace= fElement.getDeclaringExtension().getNamespace();
		Bundle bundle= Platform.getBundle(namespace);
		return bundle;
	}

	/**
	 * Returns a new instance of the computer as described in the
	 * extension's xml. Note that the safest way to access the computer
	 * is by using the
	 * {@linkplain #computeCompletionProposals(TextContentAssistInvocationContext, IProgressMonitor) computeCompletionProposals}
	 * and
	 * {@linkplain #computeContextInformation(TextContentAssistInvocationContext, IProgressMonitor) computeContextInformation}
	 * methods. These delegate the functionality to the contributed
	 * computer, but handle instance creation and any exceptions thrown.
	 * 
	 * @return a new instance of the completion proposal computer as
	 *         described by this descriptor
	 * @throws CoreException if the creation fails
	 * @throws InvalidRegistryObjectException if the extension is not
	 *         valid any longer (e.g. due to plug-in unloading)
	 */
	public ICompletionProposalComputer createComputer() throws CoreException, InvalidRegistryObjectException {
		return (ICompletionProposalComputer) fElement.createExecutableExtension(CLASS);
	}
	
	/**
	 * Safely computes completion proposals through the described extension. If the extension
	 * is disabled, throws an exception or otherwise does not adhere to the contract described in
	 * {@link ICompletionProposalComputer}, an empty list is returned.
	 * 
	 * @param context the invocation context passed on to the extension
	 * @param monitor the progress monitor passed on to the extension
	 * @return the list of computed completion proposals (element type: {@link org.eclipse.jface.text.contentassist.ICompletionProposal})
	 */
	public List computeCompletionProposals(TextContentAssistInvocationContext context, IProgressMonitor monitor) {
		if (!fEnabled)
			return Collections.EMPTY_LIST;
		
		IStatus status;
		try {
			ICompletionProposalComputer computer= getComputer();
			
			PerformanceStats stats= startMeter(context, computer);
			List proposals= computer.computeCompletionProposals(context, monitor);
			stopMeter(stats, COMPUTE_COMPLETION_PROPOSALS);
			
			if (proposals != null)
				return proposals;
			
			status= createAPIViolationStatus(COMPUTE_COMPLETION_PROPOSALS);
		} catch (InvalidRegistryObjectException x) {
			status= createExceptionStatus(x);
		} catch (CoreException x) {
			status= createExceptionStatus(x);
		} catch (RuntimeException x) {
			status= createExceptionStatus(x);
		} finally {
			monitor.done();
		}
		
		fRegistry.remove(this, status);

		return Collections.EMPTY_LIST;
	}

	/**
	 * Safely computes context information objects through the described extension. If the extension
	 * is disabled, throws an exception or otherwise does not adhere to the contract described in
	 * {@link ICompletionProposalComputer}, an empty list is returned.
	 * 
	 * @param context the invocation context passed on to the extension
	 * @param monitor the progress monitor passed on to the extension
	 * @return the list of computed context information objects (element type: {@link org.eclipse.jface.text.contentassist.IContextInformation})
	 */
	public List computeContextInformation(TextContentAssistInvocationContext context, IProgressMonitor monitor) {
		if (!fEnabled)
			return Collections.EMPTY_LIST;
		
		IStatus status;
		try {
			ICompletionProposalComputer computer= getComputer();
			
			PerformanceStats stats= startMeter(context, computer);
			List proposals= computer.computeContextInformation(context, monitor);
			stopMeter(stats, COMPUTE_CONTEXT_INFORMATION);
			
			if (proposals != null)
				return proposals;
			
			status= createAPIViolationStatus(COMPUTE_CONTEXT_INFORMATION);
		} catch (InvalidRegistryObjectException x) {
			status= createExceptionStatus(x);
		} catch (CoreException x) {
			status= createExceptionStatus(x);
		} catch (RuntimeException x) {
			status= createExceptionStatus(x);
		} finally {
			monitor.done();
		}
		
		fRegistry.remove(this, status);
		
		return Collections.EMPTY_LIST;
	}
	
	private void stopMeter(final PerformanceStats stats, String operation) {
		IStatus status;
		if (MEASURE_PERFORMANCE) {
			stats.endRun();
			if (stats.isFailure()) {
				status= createPerformanceStatus(operation);
				fRegistry.remove(this, status);
			}
		}
	}

	private PerformanceStats startMeter(TextContentAssistInvocationContext context, ICompletionProposalComputer computer) {
		final PerformanceStats stats;
		if (MEASURE_PERFORMANCE) {
			stats= PerformanceStats.getStats(PERFORMANCE_EVENT, computer);
			stats.startRun(context.toString());
		} else {
			stats= null;
		}
		return stats;
	}

	private Status createExceptionStatus(InvalidRegistryObjectException x) {
		// extension has become invalid - log & disable
		String disable= createDisableMessage();
		String reason= JavaTextMessages.CompletionProposalComputerDescriptor_reason_invalid;
		return new Status(IStatus.INFO, JavaPlugin.getPluginId(), IStatus.OK, disable + " " + reason, x); //$NON-NLS-1$
	}

	private Status createExceptionStatus(CoreException x) {
		// unable to instantiate the extension - log & disable
		String disable= createDisableMessage();
		String reason= JavaTextMessages.CompletionProposalComputerDescriptor_reason_instantiation;
		return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, disable + " " + reason, x); //$NON-NLS-1$
	}
	
	private Status createExceptionStatus(RuntimeException x) {
		// misbehaving extension - log & disable
		String disable= createDisableMessage();
		String reason= JavaTextMessages.CompletionProposalComputerDescriptor_reason_runime_ex;
		return new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, disable + " " + reason, x); //$NON-NLS-1$
	}

	private Status createAPIViolationStatus(String operation) {
		String disable= createDisableMessage();
		Object[] args= {operation};
		String reason= MessageFormat.format(JavaTextMessages.CompletionProposalComputerDescriptor_reason_API, args);
		return new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, disable + " " + reason, null); //$NON-NLS-1$
	}

	private Status createPerformanceStatus(String operation) {
		String disable= createDisableMessage();
		Object[] args= {operation};
		String reason= MessageFormat.format(JavaTextMessages.CompletionProposalComputerDescriptor_reason_performance, args);
		return new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, disable + " " + reason, null); //$NON-NLS-1$
	}

	private String createDisableMessage() {
		Object[] args= { getName(), getId() };
		String disable= MessageFormat.format( JavaTextMessages.CompletionProposalComputerDescriptor_disabling_message, args);
		return disable;
	}
	
	/**
	 * Returns the ordinal of this descriptor (used to keep the
	 * iteration order of created descriptors constant.
	 * 
	 * @return the ordinal of this descriptor
	 */
	int ordinal() {
		return fOrdinal;
	}

	/**
	 * Sets the enablement of the described extension.
	 * 
	 * @param enable <code>true</code> to enable, <code>false</code> to disable the extension
	 */
	public void setEnabled(boolean enable) {
		fEnabled= enable;
	}

	/**
	 * Returns the enablement state of the described extension.
	 * 
	 * @return the enablement state of the described extension
	 */
	public boolean isEnabled() {
		return fEnabled;
	}
	
	/**
	 * Returns the image descriptor of the described extension.
	 * 
	 * @return the image descriptor of the described extension
	 */
	public ImageDescriptor getImageDescriptor() {
		return fImage;
	}
}