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
package org.eclipse.jdt.internal.corext.refactoring.code.flow;

import java.util.HashSet;

import org.eclipse.jdt.core.dom.SimpleName;

class BranchFlowInfo extends FlowInfo {
	
	public BranchFlowInfo(SimpleName label, FlowContext context) {
		super(NO_RETURN);
		fBranches= new HashSet(2);
		fBranches.add(makeString(label));
	}
}


