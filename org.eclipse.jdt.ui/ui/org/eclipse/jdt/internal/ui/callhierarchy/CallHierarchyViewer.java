/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation 
 * 			(report 36180: Callers/Callees view)
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IWorkbenchPartSite;

class CallHierarchyViewer extends TreeViewer {
    private CallHierarchyViewPart fPart;

    private OpenLocationAction fOpen;

    /**
     * @param parent
     */
    CallHierarchyViewer(Composite parent, CallHierarchyViewPart part) {
        super(new Tree(parent, SWT.SINGLE));

        fPart = part;

        getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
        setUseHashlookup(true);
        setContentProvider(new CallHierarchyContentProvider());
        setLabelProvider(new CallHierarchyLabelProvider());

        fOpen= new OpenLocationAction(part.getSite());
        addOpenListener(new IOpenListener() {
            public void open(OpenEvent event) {
                fOpen.run();
            }
        });

        setInput(TreeRoot.EMPTY_TREE);
    }

    /**
     * @param wrapper
     */
    void setMethodWrapper(MethodWrapper wrapper) {
        setInput(getTreeRoot(wrapper));

        expandToLevel(2);
        getTree().setFocus();
        getTree().setSelection(new TreeItem[] { getTree().getItems()[0] });
    }

    CallHierarchyViewPart getPart() {
        return fPart;
    }

    /**
     *
     */
    void setFocus() {
        getControl().setFocus();
    }

    boolean isInFocus() {
        return getControl().isFocusControl();
    }

    /**
     * @param keyListener
     */
    void addKeyListener(KeyListener keyListener) {
        getControl().addKeyListener(keyListener);
    }

    /**
     * Wraps the root of a MethodWrapper tree in a dummy root in order to show
     * it in the tree.
     *
     * @param root The root of the MethodWrapper tree.
     * @return A new MethodWrapper which is a dummy root above the specified root.
     */
    private TreeRoot getTreeRoot(MethodWrapper root) {
        TreeRoot dummyRoot = new TreeRoot(root);

        return dummyRoot;
    }

    void setJavaLabelFormat(int format) {
        ((CallHierarchyLabelProvider) getLabelProvider()).setJavaLabelFormat(format);
        refresh();
    }

    /**
     * Attaches a contextmenu listener to the tree
     */
    void initContextMenu(IMenuListener menuListener, String popupId, IWorkbenchPartSite viewSite) {
        MenuManager menuMgr= new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(menuListener);
        Menu menu= menuMgr.createContextMenu(getTree());
        getTree().setMenu(menu);
        viewSite.registerContextMenu(popupId, menuMgr, this);
    }
}
