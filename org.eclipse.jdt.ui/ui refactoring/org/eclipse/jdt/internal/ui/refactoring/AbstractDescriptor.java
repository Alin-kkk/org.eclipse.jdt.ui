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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;

import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.expressions.ExpressionTagNames;

import org.eclipse.jface.util.Assert;


public abstract class AbstractDescriptor {

	protected IConfigurationElement fConfigurationElement;
	protected Expression fExpression;
	
	protected static final String ID= "id"; //$NON-NLS-1$
	protected static final String OBJECT_STATE= "objectState";  //$NON-NLS-1$
	protected static final String CLASS= "class"; //$NON-NLS-1$
	
	protected AbstractDescriptor(IConfigurationElement element) {
		fConfigurationElement= element;
	}
	
	public String getId() {
		return fConfigurationElement.getAttribute(ID);
	}
	
	public boolean matches(Object element) throws CoreException {
		Expression exp= getExpression();
		if (exp.evaluate(new EvaluationContext(null, element)) == EvaluationResult.FALSE)
			return false;
		return true;
	}
	
	public Expression getExpression() throws CoreException {
		if (fExpression == null)
			fExpression= createExpression(fConfigurationElement);
		return fExpression;
	}
	
	public void clear() {
		fExpression= null;
	}
		
	protected Expression createExpression(IConfigurationElement element) throws CoreException {
		IConfigurationElement[] children= element.getChildren(ExpressionTagNames.ENABLEMENT);
		if (children.length == 0)
			return Expression.FALSE;
		// TODO we should add some sort of syntax check and throw an core exception in this case
		Assert.isTrue(children.length == 1);
		return ExpressionConverter.getDefault().perform(children[0]);
	} 
}
