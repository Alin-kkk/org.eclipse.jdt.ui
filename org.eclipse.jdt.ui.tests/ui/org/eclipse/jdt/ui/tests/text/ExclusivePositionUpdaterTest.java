/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.text;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;

import org.eclipse.jdt.internal.ui.text.link.ExclusivePositionUpdater;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * @author tei
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class ExclusivePositionUpdaterTest extends TestCase {
	
	private ExclusivePositionUpdater fUpdater;
	private static final String CATEGORY= "testcategory";
	private Position fPos;
	private IDocument fDoc;
	/*
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		fUpdater= new ExclusivePositionUpdater(CATEGORY);
		fDoc= new Document("ccccccccccccccccccccccccccccccccccccccccccccc");
		fPos= new Position(5, 5);
		fDoc.addPositionUpdater(fUpdater);
		fDoc.addPositionCategory(CATEGORY);
		fDoc.addPosition(CATEGORY, fPos);
	}

	public void testDeleteAfter() throws BadLocationException {
		fDoc.replace(20, 2, null);
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(5, fPos.length);
	}
	
	public void testAddAfter() throws BadLocationException {
		fDoc.replace(20, 0, "yy");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(5, fPos.length);
	}
	
	public void testDeleteBefore() throws BadLocationException {
		fDoc.replace(2, 2, null);
		Assert.assertEquals(3, fPos.offset);
		Assert.assertEquals(5, fPos.length);
	}

	public void testAddBefore() throws BadLocationException {
		fDoc.replace(2, 0, "yy");
		Assert.assertEquals(7, fPos.offset);
		Assert.assertEquals(5, fPos.length);
	}

	public void testAddRightBefore() throws BadLocationException {
		fDoc.replace(5, 0, "yy");
		Assert.assertEquals(7, fPos.offset);
		Assert.assertEquals(5, fPos.length);
	}

	public void testDeleteRightBefore() throws BadLocationException {
		fDoc.replace(5, 2, "");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(3, fPos.length);
	}

	public void testAddRightAfter() throws BadLocationException {
		fDoc.replace(10, 0, "yy");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(5, fPos.length);
	}

	public void testDeleteRightAfter() throws BadLocationException {
		fDoc.replace(10, 2, "");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(5, fPos.length);
	}

	public void testAddWithin() throws BadLocationException {
		fDoc.replace(6, 0, "yy");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(7, fPos.length);
	}

	public void testDeleteWithin() throws BadLocationException {
		fDoc.replace(6, 2, "");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(3, fPos.length);
	}

	public void testReplaceLeftBorder() throws BadLocationException {
		fDoc.replace(4, 2, "yy");
		Assert.assertEquals(6, fPos.offset);
		Assert.assertEquals(4, fPos.length);
	}

	public void testReplaceRightBorder() throws BadLocationException {
		fDoc.replace(9, 2, "yy");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(4, fPos.length);
	}

	public void testDeleteOverRightBorder() throws BadLocationException {
		fDoc.replace(9, 2, "");
		Assert.assertEquals(5, fPos.offset);
		Assert.assertEquals(4, fPos.length);
	}
	
	public void testDeleted() throws BadLocationException {
		fDoc.replace(4, 7, null);
		Assert.assertTrue(fPos.isDeleted);
	}

	public void testReplaced() throws BadLocationException {
		fDoc.replace(4, 7, "yyyyyyy");
		Assert.assertTrue(fPos.isDeleted);
	}

}
