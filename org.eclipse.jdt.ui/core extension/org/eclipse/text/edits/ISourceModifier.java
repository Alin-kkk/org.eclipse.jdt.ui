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
package org.eclipse.text.edits;

/**
 * A source modifier can be used to modify the source of
 * a move or copy edit before it gets inserted at the target 
 * position. This is useful if the text to be  copied has to 
 * be modified before it is inserted without changing the 
 * original source.
 * 
 * @since 3.0
 */
public interface ISourceModifier {
	/**
	 * Returns the modification to be done to the passed
	 * string in form of replace edits. The caller of this
	 * method is responsible to apply the returned edits
	 * to the passed source.
	 * 
	 * @param source the source to be copied or moved
	 * @return an array of <code>ReplaceEdits</code>
	 *  describing the modifications
	 */
	public ReplaceEdit[] getModifications(String source);
	
	/**
	 * Creates a copy of this source modifier object. The copy will
	 * be used in a different text edit object. So it should be 
	 * created in a way that is doesn't conflict with other text edits
	 * refering to this source modifier.
	 */
	public ISourceModifier copy();
}
