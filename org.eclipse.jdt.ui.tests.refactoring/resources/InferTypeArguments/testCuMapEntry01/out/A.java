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
package p;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

class A {
	void sets() {
		Map<String, Integer> map= new HashMap<String, Integer>();
		map.put("key", new Integer(17));
		Iterator<Entry<String, Integer>> iter= map.entrySet().iterator();
		Entry<String, Integer> entry= iter.next();
	}
}
