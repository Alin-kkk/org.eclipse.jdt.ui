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
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.NavigateActionGroup;
import org.eclipse.jface.util.Assert;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.actions.ActionGroup;

class SearchViewActionGroup extends CompositeActionGroup {

	public SearchViewActionGroup(IViewPart part) {
		Assert.isNotNull(part);
		setGroups(new ActionGroup[] {
			new JavaSearchActionGroup(part),
			new NavigateActionGroup(new SearchViewAdapter(new SearchViewSiteAdapter(part.getSite())))});
	}
}

