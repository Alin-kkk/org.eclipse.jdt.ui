/*
 * (c) Copyright IBM Corp. 2000, 2001, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.refactoring;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.CompareViewerSwitchingPane;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

public class ComparePreviewer extends CompareViewerSwitchingPane implements IPreviewViewer {
	
	/**
	 * An input element for the <code>ComparePreviewer</code> class. It manages the left
	 * and right hand side input stream for the actual compare viewer as well as value indicating
	 * the type of the input streams. Example type values are: <code>"java"</code> for input
	 * stream containing Java source code, or "gif" for input stream containing gif files.
	 */
	public static class CompareInput {
		/** The left hand side */
		public InputStream left;
		/** The right hand side */
		public InputStream right;
		/** The input streams' type */
		public String type;
		/** The change element */
		public ChangeElement element;
		public CompareInput(ChangeElement e, String l, String r, String t) {
			this(e, new ByteArrayInputStream(l.getBytes()), new ByteArrayInputStream(r.getBytes()), t);
		}
		public CompareInput(ChangeElement e, InputStream l, InputStream r, String t) {
			Assert.isNotNull(e);
			Assert.isNotNull(l);
			Assert.isNotNull(r);
			Assert.isNotNull(t);
			element= e;
			left= l;
			right= r;
			type= t;
		}
	}
	
	/** A flag indicating that the input elements of a compare viewer a of type Java */
	public static final String JAVA_TYPE= "java";
	/** A flag indicating that the input elements of a compare viewer a of type text */
	public static final String TEXT_TYPE= "txt";

	private static class CompareElement implements ITypedElement, IStreamContentAccessor {
		private InputStream fContent;
		private String fType;
		public CompareElement(InputStream content, String type) {
			fContent= content;
			fType= type;
		}
		public String getName() {
			return "Compare element name";
		}
		public Image getImage() {
			return null;
		}
		public String getType() {
			return fType;
		}
		public InputStream getContents() throws CoreException {
			return fContent;
		}
	}
		
	private CompareConfiguration fCompareConfiguration;
	private ChangeElementLabelProvider fLabelProvider;
	private CompareInput fCompareInput;
	
	public ComparePreviewer(Composite parent) {
		super(parent, SWT.BORDER | SWT.FLAT, true);
		fCompareConfiguration= new CompareConfiguration();
		fCompareConfiguration.setLeftEditable(false);
		fCompareConfiguration.setLeftLabel("Original Source");
		fCompareConfiguration.setRightEditable(false);
		fCompareConfiguration.setRightLabel("Refactored Source");
		fLabelProvider= new ChangeElementLabelProvider(
			JavaElementLabelProvider.SHOW_POST_QUALIFIED| JavaElementLabelProvider.SHOW_SMALL_ICONS);
	}
	
	public Control getControl() {
		return this;
	}
	
	public void refresh() {
		getViewer().refresh();
	}
	
	protected Viewer getViewer(Viewer oldViewer, Object input) {
		return CompareUI.findContentViewer(oldViewer, (ICompareInput)input, this, fCompareConfiguration);
	}
	
	public void setInput(Object input) {
		if (input instanceof CompareInput) {
			fCompareInput= (CompareInput)input;
			super.setInput(new DiffNode(
				new CompareElement(fCompareInput.left, fCompareInput.type),
				new CompareElement(fCompareInput.right, fCompareInput.type)));
		} else {
			fCompareInput= null;
			super.setInput(input);
		}
	}
	
	public void setText(String text) {
		if (fCompareInput == null) {
			super.setText(text);
			setImage(null);
			return;
		}
		setImage(fLabelProvider.getImage(fCompareInput.element));
		super.setText(fLabelProvider.getText(fCompareInput.element));
	}
}