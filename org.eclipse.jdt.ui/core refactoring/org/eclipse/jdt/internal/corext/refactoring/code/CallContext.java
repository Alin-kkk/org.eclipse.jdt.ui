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
package org.eclipse.jdt.internal.corext.refactoring.code;

import org.eclipse.jdt.internal.corext.codemanipulation.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.CodeScopeBuilder;

public class CallContext {

	public String[] arguments;
	public String receiver; 
	public boolean receiverIsStatic;
	public CodeScopeBuilder.Scope scope;
	public int callMode;
	public ImportRewrite importer;

	public CallContext(CodeScopeBuilder.Scope s, int cm, ImportRewrite i) {
		super();
		scope= s;
		callMode= cm;
		importer= i;
	}
}
