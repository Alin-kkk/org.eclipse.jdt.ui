/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.packageview;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.IPath;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.ISetSelectionTarget;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.part.ViewPart;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.IPackagesViewPart;
import org.eclipse.jdt.ui.JavaElementContentProvider;
import org.eclipse.jdt.ui.JavaElementSorter;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDropAdapter;
import org.eclipse.jdt.internal.ui.dnd.LocalSelectionTransfer;
import org.eclipse.jdt.internal.ui.dnd.ResourceTransferDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.TransferDragSourceListener;
import org.eclipse.jdt.internal.ui.dnd.TransferDropTargetListener;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.JarEntryEditorInput;
import org.eclipse.jdt.internal.ui.preferences.AppearancePreferencePage;
import org.eclipse.jdt.internal.ui.preferences.JavaBasePreferencePage;
import org.eclipse.jdt.internal.ui.util.JavaUIHelp;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTreeViewer;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;



/**
 * The ViewPart for the ProjectExplorer. It listens to part activation events.
 * When selection linking with the editor is enabled the view selection tracks
 * the active editor page. Similarly when a resource is selected in the packages
 * view the corresponding editor is activated. 
 */

public class PackageExplorerPart extends ViewPart implements ISetSelectionTarget, IMenuListener, IPackagesViewPart,  IPropertyChangeListener {
	
	public final static String VIEW_ID= JavaUI.ID_PACKAGES;
				
	// Persistance tags.
	static final String TAG_SELECTION= "selection"; //$NON-NLS-1$
	static final String TAG_EXPANDED= "expanded"; //$NON-NLS-1$
	static final String TAG_ELEMENT= "element"; //$NON-NLS-1$
	static final String TAG_PATH= "path"; //$NON-NLS-1$
	static final String TAG_VERTICAL_POSITION= "verticalPosition"; //$NON-NLS-1$
	static final String TAG_HORIZONTAL_POSITION= "horizontalPosition"; //$NON-NLS-1$
	static final String TAG_FILTERS = "filters"; //$NON-NLS-1$
	static final String TAG_FILTER = "filter"; //$NON-NLS-1$
	static final String TAG_SHOWLIBRARIES = "showLibraries"; //$NON-NLS-1$
	static final String TAG_SHOWBINARIES = "showBinaries"; //$NON-NLS-1$

	private JavaElementPatternFilter fPatternFilter= new JavaElementPatternFilter();
	private LibraryFilter fLibraryFilter= new LibraryFilter();

	private PackageExplorerActionGroup fActionSet;
	private ProblemTreeViewer fViewer; 
	private Menu fContextMenu;		
	
	private IMemento fMemento;	
	private ISelectionChangedListener fSelectionListener;
	
	private String fWorkingSetName;
	
	
	private IPartListener fPartListener= new IPartListener() {
		public void partActivated(IWorkbenchPart part) {
			if (part instanceof IEditorPart)
				editorActivated((IEditorPart) part);
		}
		public void partBroughtToTop(IWorkbenchPart part) {
		}
		public void partClosed(IWorkbenchPart part) {
		}
		public void partDeactivated(IWorkbenchPart part) {
		}
		public void partOpened(IWorkbenchPart part) {
		}
	};
	
	private ITreeViewerListener fExpansionListener= new ITreeViewerListener() {
		public void treeCollapsed(TreeExpansionEvent event) {
		}
		
		public void treeExpanded(TreeExpansionEvent event) {
			Object element= event.getElement();
			if (element instanceof ICompilationUnit || 
				element instanceof IClassFile)
				expandMainType(element);
		}
	};

	
	public PackageExplorerPart() { 
	}

	/* (non-Javadoc)
	 * Method declared on IViewPart.
	 */
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		fMemento= memento;
	}
	
	/** 
	 * Initializes the default preferences
	 */
	public static void initDefaults(IPreferenceStore store) {
		store.setDefault(TAG_SHOWLIBRARIES, true);
		store.setDefault(TAG_SHOWBINARIES, true);
	}

	/**
	 * Returns the package explorer part of the active perspective. If 
	 * there isn't any package explorer part <code>null</code> is returned.
	 */
	public static PackageExplorerPart getFromActivePerspective() {
		IViewPart view= JavaPlugin.getActivePage().findView(VIEW_ID);
		if (view instanceof PackageExplorerPart)
			return (PackageExplorerPart)view;
		return null;	
	}
	
	/**
	 * Makes the package explorer part visible in the active perspective. If there
	 * isn't a package explorer part registered <code>null</code> is returned.
	 * Otherwise the opened view part is returned.
	 */
	public static PackageExplorerPart openInActivePerspective() {
		try {
			return (PackageExplorerPart)JavaPlugin.getActivePage().showView(VIEW_ID);
		} catch(PartInitException pe) {
			return null;
		}
	} 
		
	 public void dispose() {
		if (fContextMenu != null && !fContextMenu.isDisposed())
			fContextMenu.dispose();
		getSite().getPage().removePartListener(fPartListener);
		JavaPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this);
		if (fViewer != null)
			fViewer.removeTreeListener(fExpansionListener);
		
		if (fActionSet != null)	
			fActionSet.dispose();
		super.dispose();	
	}
	/**
	 * Implementation of IWorkbenchPart.createPartControl(Composite)
	 */
	public void createPartControl(Composite parent) {
		fViewer= new ProblemTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		
		boolean showCUChildren= AppearancePreferencePage.showCompilationUnitChildren();
		boolean reconcile= JavaBasePreferencePage.reconcileJavaViews();
		fViewer.setContentProvider(new JavaElementContentProvider(showCUChildren, reconcile));
		
		JavaPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);
		
		ILabelProvider labelProvider= 
			new AppearanceAwareLabelProvider(
				AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED,
				AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS,
				AppearanceAwareLabelProvider.getDecorators(true, null)
			);
		
		fViewer.setLabelProvider(new DecoratingLabelProvider(
			labelProvider, PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator())
		);
		fViewer.setSorter(new JavaElementSorter());
		fViewer.addFilter(new EmptyInnerPackageFilter());
		fViewer.setUseHashlookup(true);
		fViewer.addFilter(fPatternFilter);
		fViewer.addFilter(fLibraryFilter);

		MenuManager menuMgr= new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(this);
		fContextMenu= menuMgr.createContextMenu(fViewer.getTree());
		fViewer.getTree().setMenu(fContextMenu);
		
		// Register viewer with site. This must be done before making the actions.
		IWorkbenchPartSite site= getSite();
		site.registerContextMenu(menuMgr, fViewer);
		site.setSelectionProvider(fViewer);
		site.getPage().addPartListener(fPartListener);
		
		if(fMemento != null) 
			restoreFilters();
		else
			initFilterFromPreferences();
		
		
		makeActions(); // call before registering for selection changes
		
		// Set input after filter and sorter has been set. This avoids resorting
		// and refiltering.
		fViewer.setInput(findInputElement());
		initDragAndDrop();
		initKeyListener();
			
		fSelectionListener= new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				handleSelectionChanged(event);
			}
		};
		fViewer.addSelectionChangedListener(fSelectionListener);
		
		fViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				fActionSet.handleDoubleClick(event);
			}
		});
		
		fViewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent event) {
				fActionSet.handleOpen(event);
			}
		});

		IStatusLineManager slManager= getViewSite().getActionBars().getStatusLineManager();
		fViewer.addSelectionChangedListener(new StatusBarUpdater(slManager));
		fViewer.addTreeListener(fExpansionListener);
	
		if (fMemento != null)
			restoreState(fMemento);
		fMemento= null;

		// Set help for the view 
		JavaUIHelp.setHelp(fViewer, IJavaHelpContextIds.PACKAGES_VIEW);
		
		fillActionBars();

		updateTitle();
	}

	private void fillActionBars() {
		IActionBars actionBars= getViewSite().getActionBars();
		fActionSet.fillActionBars(actionBars);		
	}
	
	private Object findInputElement() {
		Object input= getSite().getPage().getInput();
		if (input instanceof IWorkspace) { 
			return JavaCore.create(((IWorkspace)input).getRoot());
		} else if (input instanceof IContainer) {
			return JavaCore.create((IContainer)input);
		}
		//1GERPRT: ITPJUI:ALL - Packages View is empty when shown in Type Hierarchy Perspective
		// we can't handle the input
		// fall back to show the workspace
		return JavaCore.create(JavaPlugin.getWorkspace().getRoot());	
	}
	
	/**
	 * Answer the property defined by key.
	 */
	public Object getAdapter(Class key) {
		if (key.equals(ISelectionProvider.class))
			return fViewer;
		return super.getAdapter(key);
	}

	/**
	 * Returns the tool tip text for the given element.
	 */
	String getToolTipText(Object element) {
		String result;
		if (!(element instanceof IResource)) {
			result= JavaElementLabels.getTextLabel(element, AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS);		
		} else {
			IPath path= ((IResource) element).getFullPath();
			if (path.isRoot()) {
				result= PackagesMessages.getString("PackageExplorer.title"); //$NON-NLS-1$
			} else {
				result= path.makeRelative().toString();
			}
		}
		
		if (fWorkingSetName == null)
			return result;

		String wsstr= PackagesMessages.getFormattedString("PackageExplorer.toolTip", new String[] { fWorkingSetName }); //$NON-NLS-1$
		if (result.length() == 0)
			return wsstr;
		return PackagesMessages.getFormattedString("PackageExplorer.toolTip2", new String[] { result, fWorkingSetName }); //$NON-NLS-1$
	}
	
	public String getTitleToolTip() {
		if (fViewer == null)
			return super.getTitleToolTip();
		return getToolTipText(fViewer.getInput());
	}
	
	/**
	 * @see IWorkbenchPart#setFocus()
	 */
	public void setFocus() {
		fViewer.getTree().setFocus();
	}

	/**
	 * Returns the shell to use for opening dialogs.
	 * Used in this class, and in the actions.
	 */
	private Shell getShell() {
		return fViewer.getTree().getShell();
	}

	/**
	 * Returns the selection provider.
	 */
	private ISelectionProvider getSelectionProvider() {
		return fViewer;
	}
	
	/**
	 * Returns the current selection.
	 */
	private ISelection getSelection() {
		return fViewer.getSelection();
	}
	  
	//---- Action handling ----------------------------------------------------------
	
	/**
	 * Called when the context menu is about to open. Override
	 * to add your own context dependent menu contributions.
	 */
	public void menuAboutToShow(IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);
		
		fActionSet.setContext(new ActionContext(getSelection()));
		fActionSet.fillContextMenu(menu);
		fActionSet.setContext(null);
	}

	private void makeActions() {
		fActionSet= new PackageExplorerActionGroup(this);
	}
	
	private boolean isSelectionOfType(ISelection s, Class clazz, boolean considerUnderlyingResource) {
		if (! (s instanceof IStructuredSelection) || s.isEmpty())
			return false;
		
		IStructuredSelection selection= (IStructuredSelection)s;
		Iterator iter= selection.iterator();
		while (iter.hasNext()) {
			Object o= iter.next();
			if (clazz.isInstance(o))
				return true;
			if (considerUnderlyingResource) {
				if (! (o instanceof IJavaElement))
					return false;
				IJavaElement element= (IJavaElement)o;
				Object resource= element.getAdapter(IResource.class);
				if (! clazz.isInstance(resource))
					return false;
			}
		}
		return true;	
	}
	
	//---- Event handling ----------------------------------------------------------
	
	private void initDragAndDrop() {
		int ops= DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK;
		Transfer[] transfers= new Transfer[] {
			LocalSelectionTransfer.getInstance(), 
			ResourceTransfer.getInstance(),
			FileTransfer.getInstance()};
		
		// Drop Adapter
		TransferDropTargetListener[] dropListeners= new TransferDropTargetListener[] {
			new SelectionTransferDropAdapter(fViewer),
			new FileTransferDropAdapter(fViewer)
		};
		fViewer.addDropSupport(ops | DND.DROP_DEFAULT, transfers, new DelegatingDropAdapter(dropListeners));
		
		// Drag Adapter
		Control control= fViewer.getControl();
		TransferDragSourceListener[] dragListeners= new TransferDragSourceListener[] {
			new SelectionTransferDragAdapter(fViewer),
			new ResourceTransferDragAdapter(fViewer),
			new FileTransferDragAdapter(fViewer)
		};
		DragSource source= new DragSource(control, ops);
		// Note, that the transfer agents are set by the delegating drag adapter itself.
		source.addDragListener(new DelegatingDragAdapter(dragListeners) {
			public void dragStart(DragSourceEvent event) {
				IStructuredSelection selection= (IStructuredSelection)getSelection();
				for (Iterator iter= selection.iterator(); iter.hasNext(); ) {
					if (iter.next() instanceof IMember) {
						setPossibleListeners(new TransferDragSourceListener[] {new SelectionTransferDragAdapter(fViewer)});
						break;
					}
				}
				super.dragStart(event);
			}
		});
	}

	/**
	 * Handles selection changed in viewer.
	 * Updates global actions.
	 * Links to editor (if option enabled)
	 */
	private void handleSelectionChanged(SelectionChangedEvent event) {
		IStructuredSelection selection= (IStructuredSelection) event.getSelection();
		fActionSet.handleSelectionChanged(event);
		// if (OpenStrategy.getOpenMethod() != OpenStrategy.SINGLE_CLICK)
		linkToEditor(selection);
	}

	public void selectReveal(ISelection selection) {
		ISelection javaSelection= convertSelection(selection);
	 	fViewer.setSelection(javaSelection, true);
	 	if (javaSelection != selection && !javaSelection.equals(fViewer.getSelection()))
	 		fViewer.setSelection(selection);
	}

	private ISelection convertSelection(ISelection s) {
		if (!(s instanceof IStructuredSelection))
			return s;
			
		Object[] elements= ((StructuredSelection)s).toArray();
		if (!containsResources(elements))
			return s;
				
		for (int i= 0; i < elements.length; i++) {
			Object o= elements[i];
			if (o instanceof IResource) {
				IJavaElement jElement= JavaCore.create((IResource)o);
				if (jElement != null) 
					elements[i]= jElement;
			}
		}
		
		return new StructuredSelection(elements);
	}
	
	private boolean containsResources(Object[] elements) {
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] instanceof IResource)
				return true;
		}
		return false;
	}
	
	public void selectAndReveal(Object element) {
		selectReveal(new StructuredSelection(element));
	}
	
	/**
	 * Returns whether the preference to link selection to active editor is enabled.
	 */
	boolean isLinkingEnabled() {
		return JavaBasePreferencePage.linkPackageSelectionToEditor();
	}

	/**
	 * Links to editor (if option enabled)
	 */
	private void linkToEditor(IStructuredSelection selection) {	
		Object obj= selection.getFirstElement();
		Object element= null;

		if (selection.size() == 1) {
			IEditorPart part= EditorUtility.isOpenInEditor(obj);
			if (part != null) {
				IWorkbenchPage page= getSite().getPage();
				page.bringToTop(part);
				if (obj instanceof IJavaElement) 
					EditorUtility.revealInEditor(part, (IJavaElement) obj);
			}
		}
	}

	private IResource getResourceFor(Object element) {
		if (element instanceof IJavaElement) {
			if (element instanceof IWorkingCopy) {
				IWorkingCopy wc= (IWorkingCopy)element;
				IJavaElement original= wc.getOriginalElement();
				if (original != null)
					element= original;
			}
			try {
				element= ((IJavaElement)element).getUnderlyingResource();
			} catch (JavaModelException e) {
				return null;
			}
		}
		if (!(element instanceof IResource) || ((IResource)element).isPhantom()) {
			return null;
		}
		return (IResource)element;
	}
	
	public void saveState(IMemento memento) {
		if (fViewer == null) {
			// part has not been created
			if (fMemento != null) //Keep the old state;
				memento.putMemento(fMemento);
			return;
		}
		saveExpansionState(memento);
		saveSelectionState(memento);
		// commented out because of http://bugs.eclipse.org/bugs/show_bug.cgi?id=4676
		//saveScrollState(memento, fViewer.getTree());
		savePatternFilterState(memento);
		saveFilterState(memento);
		fActionSet.saveState(memento);
	}

	protected void saveFilterState(IMemento memento) {
		boolean showLibraries= getLibraryFilter().getShowLibraries();
		String show= "true"; //$NON-NLS-1$
		if (!showLibraries)
			show= "false"; //$NON-NLS-1$
		memento.putString(TAG_SHOWLIBRARIES, show);
	}

	protected void savePatternFilterState(IMemento memento) {
		String filters[] = getPatternFilter().getPatterns();
		if(filters.length > 0) {
			IMemento filtersMem = memento.createChild(TAG_FILTERS);
			for (int i = 0; i < filters.length; i++){
				IMemento child = filtersMem.createChild(TAG_FILTER);
				child.putString(TAG_ELEMENT,filters[i]);
			}
		}
	}

	protected void saveScrollState(IMemento memento, Tree tree) {
		ScrollBar bar= tree.getVerticalBar();
		int position= bar != null ? bar.getSelection() : 0;
		memento.putString(TAG_VERTICAL_POSITION, String.valueOf(position));
		//save horizontal position
		bar= tree.getHorizontalBar();
		position= bar != null ? bar.getSelection() : 0;
		memento.putString(TAG_HORIZONTAL_POSITION, String.valueOf(position));
	}

	protected void saveSelectionState(IMemento memento) {
		Object elements[]= ((IStructuredSelection) fViewer.getSelection()).toArray();
		if (elements.length > 0) {
			IMemento selectionMem= memento.createChild(TAG_SELECTION);
			for (int i= 0; i < elements.length; i++) {
				IMemento elementMem= selectionMem.createChild(TAG_ELEMENT);
				// we can only persist JavaElements for now
				Object o= elements[i];
				if (o instanceof IJavaElement)
					elementMem.putString(TAG_PATH, ((IJavaElement) elements[i]).getHandleIdentifier());
			}
		}
	}

	protected void saveExpansionState(IMemento memento) {
		Object expandedElements[]= fViewer.getVisibleExpandedElements();
		if (expandedElements.length > 0) {
			IMemento expandedMem= memento.createChild(TAG_EXPANDED);
			for (int i= 0; i < expandedElements.length; i++) {
				IMemento elementMem= expandedMem.createChild(TAG_ELEMENT);
				// we can only persist JavaElements for now
				Object o= expandedElements[i];
				if (o instanceof IJavaElement)
					elementMem.putString(TAG_PATH, ((IJavaElement) expandedElements[i]).getHandleIdentifier());
			}
		}
	}


	void restoreState(IMemento memento) {
		restoreExpansionState(memento);
		restoreSelectionState(memento);
		// commented out because of http://bugs.eclipse.org/bugs/show_bug.cgi?id=4676
		//restoreScrollState(memento, fViewer.getTree());
		fActionSet.restoreState(memento);		
	}

	protected void restoreScrollState(IMemento memento, Tree tree) {
		ScrollBar bar= tree.getVerticalBar();
		if (bar != null) {
			try {
				String posStr= memento.getString(TAG_VERTICAL_POSITION);
				int position;
				position= new Integer(posStr).intValue();
				bar.setSelection(position);
			} catch (NumberFormatException e) {
				// ignore, don't set scrollposition
			}
		}
		bar= tree.getHorizontalBar();
		if (bar != null) {
			try {
				String posStr= memento.getString(TAG_HORIZONTAL_POSITION);
				int position;
				position= new Integer(posStr).intValue();
				bar.setSelection(position);
			} catch (NumberFormatException e) {
				// ignore don't set scroll position
			}
		}
	}

	protected void restoreSelectionState(IMemento memento) {
		IMemento childMem;
		childMem= memento.getChild(TAG_SELECTION);
		if (childMem != null) {
			ArrayList list= new ArrayList();
			IMemento[] elementMem= childMem.getChildren(TAG_ELEMENT);
			for (int i= 0; i < elementMem.length; i++) {
				Object element= JavaCore.create(elementMem[i].getString(TAG_PATH));
				if (element != null)
					list.add(element);
			}
			fViewer.setSelection(new StructuredSelection(list));
		}
	}

	protected void restoreExpansionState(IMemento memento) {
		IMemento childMem= memento.getChild(TAG_EXPANDED);
		if (childMem != null) {
			ArrayList elements= new ArrayList();
			IMemento[] elementMem= childMem.getChildren(TAG_ELEMENT);
			for (int i= 0; i < elementMem.length; i++) {
				Object element= JavaCore.create(elementMem[i].getString(TAG_PATH));
				if (element != null)
					elements.add(element);
			}
			fViewer.setExpandedElements(elements.toArray());
		}
	}
	
	/**
	 * Create the KeyListener for doing the refresh on the viewer.
	 */
	private void initKeyListener() {
		fViewer.getControl().addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent event) {
				fActionSet.handleKeyEvent(event);
			}
		});
	}

	/**
	 * An editor has been activated.  Set the selection in this Packages Viewer
	 * to be the editor's input, if linking is enabled.
	 */
	void editorActivated(IEditorPart editor) {
		if (!isLinkingEnabled())  
			return;
		Object input= getElementOfInput(editor.getEditorInput());
		Object element= null;
		
		if (input instanceof IFile) 
			element= JavaCore.create((IFile)input);
			
		if (element == null) // try a non Java resource
			element= input;
			
		if (element != null) {
			// if the current selection is a child of the new
			// selection then ignore it.
			IStructuredSelection oldSelection= (IStructuredSelection)getSelection();
			if (oldSelection.size() == 1) {
				Object o= oldSelection.getFirstElement();
				if (o instanceof IJavaElement) {
					ICompilationUnit cu= (ICompilationUnit)((IJavaElement)o).getAncestor(IJavaElement.COMPILATION_UNIT);
					if (cu != null) {
						if (cu.isWorkingCopy())
							cu= (ICompilationUnit)cu.getOriginalElement();
						if ( element.equals(cu))
							return;
					}

					IClassFile cf= (IClassFile)((IJavaElement)o).getAncestor(IJavaElement.CLASS_FILE);
					if (cf != null && element.equals(cf))
						return;
				}
			}
			ISelection newSelection= new StructuredSelection(element);
			if (!fViewer.getSelection().equals(newSelection)) {
				try {
					fViewer.removeSelectionChangedListener(fSelectionListener);
					fViewer.setSelection(newSelection);
				} finally {
					fViewer.addSelectionChangedListener(fSelectionListener);
				}
			}
		}
	}
	
	/**
	 * A compilation unit or class was expanded, expand
	 * the main type.  
	 */
	void expandMainType(Object element) {
		try {
			IType type= null;
			if (element instanceof ICompilationUnit) {
				ICompilationUnit cu= (ICompilationUnit)element;
				IType[] types= cu.getTypes();
				if (types.length > 0)
					type= types[0];
			}
			else if (element instanceof IClassFile) {
				IClassFile cf= (IClassFile)element;
				type= cf.getType();
			}			
			if (type != null) {
				final IType type2= type;
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					ctrl.getDisplay().asyncExec(new Runnable() {
						public void run() {
							Control ctrl= fViewer.getControl();
							if (ctrl != null && !ctrl.isDisposed()) 
								fViewer.expandToLevel(type2, 1);
						}
					}); 
				}
			}
		} catch(JavaModelException e) {
			// no reveal
		}
	}
	
	/**
	 * Returns the element contained in the EditorInput
	 */
	Object getElementOfInput(IEditorInput input) {
		if (input instanceof IClassFileEditorInput)
			return ((IClassFileEditorInput)input).getClassFile();
		else if (input instanceof IFileEditorInput)
			return ((IFileEditorInput)input).getFile();
		else if (input instanceof JarEntryEditorInput)
			return ((JarEntryEditorInput)input).getStorage();
		return null;
	}
	
	/**
 	 * Returns the Viewer.
 	 */
	TreeViewer getViewer() {
		return fViewer;
	}
	
	/**
 	 * Returns the pattern filter for this view.
 	 * @return the pattern filter
 	 */
	JavaElementPatternFilter getPatternFilter() {
		return fPatternFilter;
	}
	
	/**
 	 * Returns the library filter for this view.
 	 * @return the library filter
 	 */
	LibraryFilter getLibraryFilter() {
		return fLibraryFilter;
	}



	void restoreFilters() {
		IMemento filtersMem= fMemento.getChild(TAG_FILTERS);
		if(filtersMem != null) {	
			IMemento children[]= filtersMem.getChildren(TAG_FILTER);
			String filters[]= new String[children.length];
			for (int i = 0; i < children.length; i++) {
				filters[i]= children[i].getString(TAG_ELEMENT);
			}
			getPatternFilter().setPatterns(filters);
		} else {
			getPatternFilter().setPatterns(new String[0]);
		}
		//restore library
		String show= fMemento.getString(TAG_SHOWLIBRARIES);
		if (show != null)
			getLibraryFilter().setShowLibraries(show.equals("true")); //$NON-NLS-1$
		else
			initLibraryFilterFromPreferences();
	}
	
	void initFilterFromPreferences() {
		initLibraryFilterFromPreferences();
	}

	void initLibraryFilterFromPreferences() {
		JavaPlugin plugin= JavaPlugin.getDefault();
		boolean show= plugin.getPreferenceStore().getBoolean(TAG_SHOWLIBRARIES);
		getLibraryFilter().setShowLibraries(show);
	}


	
	boolean isExpandable(Object element) {
		if (fViewer == null)
			return false;
		return fViewer.isExpandable(element);
	}

	void setWorkingSetName(String workingSetName) {
		fWorkingSetName= workingSetName;
	}
	
	/**
	 * Updates the title text and title tool tip.
	 * Called whenever the input of the viewer changes.
	 */ 
	void updateTitle() {		
		Object input= fViewer.getInput();
		String viewName= getConfigurationElement().getAttribute("name"); //$NON-NLS-1$
		if (input == null
			|| (input instanceof IJavaModel)) {
			setTitle(viewName);
			setTitleToolTip(""); //$NON-NLS-1$
		} else {
			String inputText= JavaElementLabels.getTextLabel(input, AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS);
			String title= PackagesMessages.getFormattedString("PackageExplorer.argTitle", new String[] { viewName, inputText }); //$NON-NLS-1$
			setTitle(title);
			setTitleToolTip(getToolTipText(input));
		} 
	}
	
	/**
	 * Sets the decorator for the package explorer.
	 *
	 * @param decorator a label decorator or <code>null</code> for no decorations.
	 * @deprecated To be removed
	 */
	public void setLabelDecorator(ILabelDecorator decorator) {
	}
	
	/*
	 * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		if (fViewer == null)
			return;
		
		boolean refreshViewer= false;
	
		if (event.getProperty() == AppearancePreferencePage.SHOW_CU_CHILDREN) {
			IActionBars actionBars= getViewSite().getActionBars();
			fActionSet.fillToolBar(actionBars.getToolBarManager());
			actionBars.updateActionBars();
			
			boolean showCUChildren= AppearancePreferencePage.showCompilationUnitChildren();
			((JavaElementContentProvider)fViewer.getContentProvider()).setProvideMembers(showCUChildren);
			
			refreshViewer= true;
		}

		if (refreshViewer)
			fViewer.refresh();
	}
}
