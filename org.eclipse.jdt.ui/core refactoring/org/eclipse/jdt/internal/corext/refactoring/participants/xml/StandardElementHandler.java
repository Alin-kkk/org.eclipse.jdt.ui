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
package org.eclipse.jdt.internal.corext.refactoring.participants.xml;

import org.eclipse.core.runtime.IConfigurationElement;

public class StandardElementHandler implements IElementHandler {
	
	private static final IElementHandler INSTANCE= new StandardElementHandler();
	
	public static IElementHandler getInstance() {
		return INSTANCE;
	}
	
	private StandardElementHandler() {
	}

	public Expression create(IConfigurationElement element, ExpressionParser creator) {
		String name= element.getName();
		if (PropertyExpression.NAME.equals(name)) {
			return new PropertyExpression(element);
		} else if (ObjectStateExpression.NAME.equals(name)) {
			ObjectStateExpression result= new ObjectStateExpression(element);
			creator.processChildren(result, element);
			return result;
		} else if (OrExpression.NAME.equals(name)) {
			OrExpression result= new OrExpression();
			creator.processChildren(result, element);
			return result;
		} else if (AndExpression.NAME.equals(name)) {
			AndExpression result= new AndExpression();
			creator.processChildren(result, element);
			return result;
		} else if (NotExpression.NAME.equals(name)) {
			return new NotExpression(creator.parse(element.getChildren()[0]));
		}
		return null;
	}
}
