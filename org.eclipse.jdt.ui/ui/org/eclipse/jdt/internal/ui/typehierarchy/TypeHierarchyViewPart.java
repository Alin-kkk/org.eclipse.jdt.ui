/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.ToolBar;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.OpenWithMenu;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.ViewPart;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.ITypeHierarchyViewPart;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.OpenEditorActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;
import org.eclipse.jdt.ui.actions.ShowActionGroup;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.AddMethodStubAction;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDropAdapter;
import org.eclipse.jdt.internal.ui.dnd.LocalSelectionTransfer;
import org.eclipse.jdt.internal.ui.dnd.TransferDragSourceListener;
import org.eclipse.jdt.internal.ui.dnd.TransferDropTargetListener;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDragAdapter;
import org.eclipse.jdt.internal.ui.preferences.JavaBasePreferencePage;
import org.eclipse.jdt.internal.ui.util.OpenTypeHierarchyUtil;
import org.eclipse.jdt.internal.ui.viewsupport.IProblemChangedListener;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;

/**
 * view showing the supertypes/subtypes of its input.
 */
public class TypeHierarchyViewPart extends ViewPart implements ITypeHierarchyViewPart {
	
	public static final int VIEW_ID_TYPE= 2;
	public static final int VIEW_ID_SUPER= 0;
	public static final int VIEW_ID_SUB= 1;
	
	public static final int VIEW_ORIENTATION_VERTICAL= 0;
	public static final int VIEW_ORIENTATION_HORIZONTAL= 1;
	public static final int VIEW_ORIENTATION_SINGLE= 2;
	
	private static final String DIALOGSTORE_HIERARCHYVIEW= "TypeHierarchyViewPart.hierarchyview";	 //$NON-NLS-1$
	private static final String DIALOGSTORE_VIEWORIENTATION= "TypeHierarchyViewPart.orientation";	 //$NON-NLS-1$

	private static final String TAG_INPUT= "input"; //$NON-NLS-1$
	private static final String TAG_VIEW= "view"; //$NON-NLS-1$
	private static final String TAG_ORIENTATION= "orientation"; //$NON-NLS-1$
	private static final String TAG_RATIO= "ratio"; //$NON-NLS-1$
	private static final String TAG_SELECTION= "selection"; //$NON-NLS-1$
	private static final String TAG_VERTICAL_SCROLL= "vertical_scroll"; //$NON-NLS-1$
	
	private static final String GROUP_FOCUS= "group.focus"; //$NON-NLS-1$

	// the selected type in the hierarchy view
	private IType fSelectedType;
	// input element or null
	private IJavaElement fInputElement;
	
	// history of inut elements. No duplicates
	private ArrayList fInputHistory;
	
	private IMemento fMemento;
	
	private TypeHierarchyLifeCycle fHierarchyLifeCycle;
	private ITypeHierarchyLifeCycleListener fTypeHierarchyLifeCycleListener;
		
	private MethodsViewer fMethodsViewer;
			
	private int fCurrentViewerIndex;
	private TypeHierarchyViewer[] fAllViewers;
	
	private SelectionProviderMediator fSelectionProviderMediator;
	private ISelectionChangedListener fSelectionChangedListener;
	
	private boolean fIsEnableMemberFilter;
	
	private SashForm fTypeMethodsSplitter;
	private PageBook fViewerbook;
	private PageBook fPagebook;
	
	private Label fNoHierarchyShownLabel;
	private Label fEmptyTypesViewer;
	
	private ViewForm fTypeViewerViewForm;
	private ViewForm fMethodViewerViewForm;
	
	private CLabel fMethodViewerPaneLabel;
	private JavaUILabelProvider fPaneLabelProvider;
	
	private IDialogSettings fDialogSettings;
	
	private ToggleViewAction[] fViewActions;
	
	private HistoryDropDownAction fHistoryDropDownAction;
	
	private ToggleOrientationAction[] fToggleOrientationActions;
	private int fCurrentOrientation;
	
	private EnableMemberFilterAction fEnableMemberFilterAction;
	private AddMethodStubAction fAddStubAction;
	private FocusOnTypeAction fFocusOnTypeAction;
	private FocusOnSelectionAction fFocusOnSelectionAction;
	
	private IPartListener fPartListener;
	
	private CompositeActionGroup fActionGroups;
	private CCPActionGroup fCCPActionGroup;
	
	public TypeHierarchyViewPart() {
		fSelectedType= null;
		fInputElement= null;
		
		fHierarchyLifeCycle= new TypeHierarchyLifeCycle();
		fHierarchyLifeCycle.setReconciled(JavaBasePreferencePage.reconcileJavaViews());
		fTypeHierarchyLifeCycleListener= new ITypeHierarchyLifeCycleListener() {
			public void typeHierarchyChanged(TypeHierarchyLifeCycle typeHierarchy, IType[] changedTypes) {
				doTypeHierarchyChanged(typeHierarchy, changedTypes);
			}
		};
		fHierarchyLifeCycle.addChangedListener(fTypeHierarchyLifeCycleListener);
		
		fIsEnableMemberFilter= false;
		
		fInputHistory= new ArrayList();
		fAllViewers= null;
				
		fViewActions= new ToggleViewAction[] {
			new ToggleViewAction(this, VIEW_ID_TYPE),
			new ToggleViewAction(this, VIEW_ID_SUPER),
			new ToggleViewAction(this, VIEW_ID_SUB)
		};
		
		fDialogSettings= JavaPlugin.getDefault().getDialogSettings();
		
		fHistoryDropDownAction= new HistoryDropDownAction(this);
		fHistoryDropDownAction.setEnabled(false);
		
		fToggleOrientationActions= new ToggleOrientationAction[] {
			new ToggleOrientationAction(this, VIEW_ORIENTATION_VERTICAL),
			new ToggleOrientationAction(this, VIEW_ORIENTATION_HORIZONTAL),
			new ToggleOrientationAction(this, VIEW_ORIENTATION_SINGLE)
		};
			
		fEnableMemberFilterAction= new EnableMemberFilterAction(this, false);
		
		fFocusOnTypeAction= new FocusOnTypeAction(this);
		
		fPaneLabelProvider= new JavaUILabelProvider();
		
		fAddStubAction= new AddMethodStubAction();
		fFocusOnSelectionAction= new FocusOnSelectionAction(this);	
	
		fPartListener= new IPartListener() {
			public void partActivated(IWorkbenchPart part) {
				if (part instanceof IEditorPart)
					editorActivated((IEditorPart) part);
			}
			public void partBroughtToTop(IWorkbenchPart part) {}
			public void partClosed(IWorkbenchPart part) {}
			public void partDeactivated(IWorkbenchPart part) {}
			public void partOpened(IWorkbenchPart part) {}
		};
		
		fSelectionChangedListener= new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				doSelectionChanged(event);
			}
		};
		
	}	
		
	/**
	 * Adds the entry if new. Inserted at the beginning of the history entries list.
	 */		
	private void addHistoryEntry(IJavaElement entry) {
		if (fInputHistory.contains(entry)) {
			fInputHistory.remove(entry);
		}
		fInputHistory.add(0, entry);
		fHistoryDropDownAction.setEnabled(true);
	}
	
	private void updateHistoryEntries() {
		for (int i= fInputHistory.size() - 1; i >= 0; i--) {
			IJavaElement type= (IJavaElement) fInputHistory.get(i);
			if (!type.exists()) {
				fInputHistory.remove(i);
			}
		}
		fHistoryDropDownAction.setEnabled(!fInputHistory.isEmpty());
	}
	
	/**
	 * Goes to the selected entry, without updating the order of history entries.
	 */	
	public void gotoHistoryEntry(IJavaElement entry) {
		if (fInputHistory.contains(entry)) {
			updateInput(entry);
		}
	}	
	
	/**
	 * Gets all history entries.
	 */
	public IJavaElement[] getHistoryEntries() {
		if (fInputHistory.size() > 0) {
			updateHistoryEntries();
		}
		return (IJavaElement[]) fInputHistory.toArray(new IJavaElement[fInputHistory.size()]);
	}
	
	/**
	 * Sets the history entries
	 */
	public void setHistoryEntries(IJavaElement[] elems) {
		fInputHistory.clear();
		for (int i= 0; i < elems.length; i++) {
			fInputHistory.add(elems[i]);
		}
		updateHistoryEntries();
	}
	
	/**
	 * Selects an member in the methods list or in the current hierarchy.
	 */	
	public void selectMember(IMember member) {
		ICompilationUnit cu= member.getCompilationUnit();
		if (cu != null && cu.isWorkingCopy()) {
			member= (IMember) cu.getOriginal(member);
			if (member == null) {
				return;
			}
		}
		if (member.getElementType() != IJavaElement.TYPE) {
			if (fHierarchyLifeCycle.isReconciled() && cu != null) {
				try {
					member= (IMember) EditorUtility.getWorkingCopy(member);
					if (member == null) {
						return;
					}
				} catch (JavaModelException e) {
					JavaPlugin.log(e);
					return;
				}
			}
			Control methodControl= fMethodsViewer.getControl();
			if (methodControl != null && !methodControl.isDisposed()) {
				methodControl.setFocus();
			}

			fMethodsViewer.setSelection(new StructuredSelection(member), true);
		} else {
			Control viewerControl= getCurrentViewer().getControl();
			if (viewerControl != null && !viewerControl.isDisposed()) {
				viewerControl.setFocus();
			}
			getCurrentViewer().setSelection(new StructuredSelection(member), true);
		}	
	}	


	/**
	 * @deprecated
	 */	
	public IType getInput() {
		if (fInputElement instanceof IType) {
			return (IType) fInputElement;
		}
		return null;
	}
	
	/**
	 * Sets the input to a new type
	 * @deprecated 
	 */
	public void setInput(IType type) {
		setInputElement(type);
	}	
	
	/**
	 * Returns the input element of the type hierarchy.
	 * Can be of type <code>IType</code> or <code>IPackageFragment</code>
	 */	
	public IJavaElement getInputElement() {
		return fInputElement;
	}			
		

	/**
	 * Sets the input to a new element.
	 */	
	public void setInputElement(IJavaElement element) {
		if (element != null) {
			if (element instanceof IMember) {
				if (element.getElementType() != IJavaElement.TYPE) {
					element= ((IMember) element).getDeclaringType();
				}				
				ICompilationUnit cu= ((IMember) element).getCompilationUnit();
				if (cu != null && cu.isWorkingCopy()) {
					element= cu.getOriginal(element);
					if (!element.exists()) {
						MessageDialog.openError(getSite().getShell(), TypeHierarchyMessages.getString("TypeHierarchyViewPart.error.title"), TypeHierarchyMessages.getString("TypeHierarchyViewPart.error.message")); //$NON-NLS-1$ //$NON-NLS-2$
						return;
					}
				}
			} else {
				int kind= element.getElementType();
				if (kind != IJavaElement.JAVA_PROJECT && kind != IJavaElement.PACKAGE_FRAGMENT_ROOT && kind != IJavaElement.PACKAGE_FRAGMENT) {
					element= null;
					JavaPlugin.logErrorMessage("Invalid type hierarchy input type.");//$NON-NLS-1$
				}
			}
		}	
		if (element != null && !element.equals(fInputElement)) {
			addHistoryEntry(element);
		}
			
		updateInput(element);
	}
	
	/**
	 * Changes the input to a new type
	 */
	private void updateInput(IJavaElement inputElement) {
		IJavaElement prevInput= fInputElement;
		
		fInputElement= inputElement;
		if (fInputElement == null) {	
			clearInput();
		} else {			
			try {
				fHierarchyLifeCycle.ensureRefreshedTypeHierarchy(fInputElement);
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				clearInput();
				return;
			}
				
			fPagebook.showPage(fTypeMethodsSplitter);
			if (inputElement.getElementType() != IJavaElement.TYPE) {
				setView(VIEW_ID_TYPE);
			}
			// turn off member filtering
			setMemberFilter(null);
			fIsEnableMemberFilter= false;
			if (!fInputElement.equals(prevInput)) {
				updateHierarchyViewer();
			}
			IType root= getSelectableType(fInputElement);
			internalSelectType(root, true);
			updateMethodViewer(root);
			updateToolbarButtons();
			updateTitle();
			enableMemberFilter(false);
		}
	}
	
	private void clearInput() {
		fInputElement= null;
		fHierarchyLifeCycle.freeHierarchy();
		
		updateHierarchyViewer();
		updateToolbarButtons();
	}

	/*
	 * @see IWorbenchPart#setFocus
	 */	
	public void setFocus() {
		fPagebook.setFocus();
	}

	/*
	 * @see IWorkbenchPart#dispose
	 */	
	public void dispose() {
		fHierarchyLifeCycle.freeHierarchy();
		fHierarchyLifeCycle.removeChangedListener(fTypeHierarchyLifeCycleListener);
		fPaneLabelProvider.dispose();
		getSite().getPage().removePartListener(fPartListener);

		if (fActionGroups != null)
			fActionGroups.dispose();
		super.dispose();
	}
			
	private Control createTypeViewerControl(Composite parent) {
		fViewerbook= new PageBook(parent, SWT.NULL);
				
		KeyListener keyListener= createKeyListener();
						
		// Create the viewers
		TypeHierarchyViewer superTypesViewer= new SuperTypeHierarchyViewer(fViewerbook, fHierarchyLifeCycle, this);
		initializeTypesViewer(superTypesViewer, keyListener, IContextMenuConstants.TARGET_ID_SUPERTYPES_VIEW);
		
		TypeHierarchyViewer subTypesViewer= new SubTypeHierarchyViewer(fViewerbook, fHierarchyLifeCycle, this);
		initializeTypesViewer(subTypesViewer, keyListener, IContextMenuConstants.TARGET_ID_SUBTYPES_VIEW);
		
		TypeHierarchyViewer vajViewer= new TraditionalHierarchyViewer(fViewerbook, fHierarchyLifeCycle, this);
		initializeTypesViewer(vajViewer, keyListener, IContextMenuConstants.TARGET_ID_HIERARCHY_VIEW);

		fAllViewers= new TypeHierarchyViewer[3];
		fAllViewers[VIEW_ID_SUPER]= superTypesViewer;
		fAllViewers[VIEW_ID_SUB]= subTypesViewer;
		fAllViewers[VIEW_ID_TYPE]= vajViewer;
		
		int currViewerIndex;
		try {
			currViewerIndex= fDialogSettings.getInt(DIALOGSTORE_HIERARCHYVIEW);
			if (currViewerIndex < 0 || currViewerIndex > 2) {
				currViewerIndex= VIEW_ID_TYPE;
			}
		} catch (NumberFormatException e) {
			currViewerIndex= VIEW_ID_TYPE;
		}
			
		fEmptyTypesViewer= new Label(fViewerbook, SWT.LEFT);
		
		for (int i= 0; i < fAllViewers.length; i++) {
			fAllViewers[i].setInput(fAllViewers[i]);
		}
		
		// force the update
		fCurrentViewerIndex= -1;
		setView(currViewerIndex);
				
		return fViewerbook;
	}
	
	private KeyListener createKeyListener() {
		return new KeyAdapter() {
			public void keyReleased(KeyEvent event) {
				if (event.stateMask == 0) {
					if (event.keyCode == SWT.F5) {
						updateHierarchyViewer();
						return;
					} else if (event.character == SWT.DEL){
						if (fCCPActionGroup.getDeleteAction().isEnabled())
							fCCPActionGroup.getDeleteAction().run();
						return;	
					}
 				}
				viewPartKeyShortcuts(event);					
			}
		};		
	}
	

	private void initializeTypesViewer(final TypeHierarchyViewer typesViewer, KeyListener keyListener, String cotextHelpId) {
		typesViewer.getControl().setVisible(false);
		typesViewer.getControl().addKeyListener(keyListener);
		typesViewer.initContextMenu(new IMenuListener() {
			public void menuAboutToShow(IMenuManager menu) {
				fillTypesViewerContextMenu(typesViewer, menu);
			}
		}, cotextHelpId,	getSite());
		typesViewer.addSelectionChangedListener(fSelectionChangedListener);
	}
	
	private Control createMethodViewerControl(Composite parent) {
		fMethodsViewer= new MethodsViewer(parent, fHierarchyLifeCycle, this);
		fMethodsViewer.initContextMenu(new IMenuListener() {
			public void menuAboutToShow(IMenuManager menu) {
				fillMethodsViewerContextMenu(menu);
			}
		}, IContextMenuConstants.TARGET_ID_MEMBERS_VIEW, getSite());
		fMethodsViewer.addSelectionChangedListener(fSelectionChangedListener);
		
		Control control= fMethodsViewer.getTable();
		control.addKeyListener(createKeyListener());
		
		return control;
	}
	
	private void initDragAndDrop() {
		Transfer[] transfers= new Transfer[] { LocalSelectionTransfer.getInstance() };
		int ops= DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK;

		for (int i= 0; i < fAllViewers.length; i++) {
			addDragAdapters(fAllViewers[i], ops, transfers);
			addDropAdapters(fAllViewers[i], ops | DND.DROP_DEFAULT, transfers);
		}	
		addDragAdapters(fMethodsViewer, ops, transfers);

		//dnd on empty hierarchy
		DropTarget dropTarget = new DropTarget(fNoHierarchyShownLabel, ops | DND.DROP_DEFAULT);
		dropTarget.setTransfer(transfers);
		dropTarget.addDropListener(new TypeHierarchyTransferDropAdapter(this, fAllViewers[0]));
	}
	
	private void addDropAdapters(AbstractTreeViewer viewer, int ops, Transfer[] transfers){
		TransferDropTargetListener[] dropListeners= new TransferDropTargetListener[] {
			new TypeHierarchyTransferDropAdapter(this, viewer)
		};
		viewer.addDropSupport(ops, transfers, new DelegatingDropAdapter(dropListeners));
	}

	private void addDragAdapters(StructuredViewer viewer, int ops, Transfer[] transfers){
		Control control= viewer.getControl();
		TransferDragSourceListener[] dragListeners= new TransferDragSourceListener[] {
			new SelectionTransferDragAdapter(viewer)
		};
		DragSource source= new DragSource(control, ops);
		// Note, that the transfer agents are set by the delegating drag adapter itself.
		source.addDragListener(new DelegatingDragAdapter(dragListeners));		
	}	
	
	private void viewPartKeyShortcuts(KeyEvent event) {
		if (event.stateMask == SWT.CTRL) {
			if (event.character == '1') {
				setView(VIEW_ID_TYPE);
			} else if (event.character == '2') {
				setView(VIEW_ID_SUPER);
			} else if (event.character == '3') {
				setView(VIEW_ID_SUB);
			}
		}	
	}
	
		
	/**
	 * Returns the inner component in a workbench part.
	 * @see IWorkbenchPart#createPartControl
	 */
	public void createPartControl(Composite container) {
						
		fPagebook= new PageBook(container, SWT.NONE);
						
		// page 1 of pagebook (viewers)

		fTypeMethodsSplitter= new SashForm(fPagebook, SWT.VERTICAL);
		fTypeMethodsSplitter.setVisible(false);

		fTypeViewerViewForm= new ViewForm(fTypeMethodsSplitter, SWT.NONE);
				
		Control typeViewerControl= createTypeViewerControl(fTypeViewerViewForm);
		fTypeViewerViewForm.setContent(typeViewerControl);
				
		fMethodViewerViewForm= new ViewForm(fTypeMethodsSplitter, SWT.NONE);
		fTypeMethodsSplitter.setWeights(new int[] {35, 65});
		
		Control methodViewerPart= createMethodViewerControl(fMethodViewerViewForm);
		fMethodViewerViewForm.setContent(methodViewerPart);
		
		fMethodViewerPaneLabel= new CLabel(fMethodViewerViewForm, SWT.NONE);
		fMethodViewerViewForm.setTopLeft(fMethodViewerPaneLabel);
				
		ToolBar methodViewerToolBar= new ToolBar(fMethodViewerViewForm, SWT.FLAT | SWT.WRAP);
		fMethodViewerViewForm.setTopCenter(methodViewerToolBar);
		
		// page 2 of pagebook (no hierarchy label)
		fNoHierarchyShownLabel= new Label(fPagebook, SWT.TOP + SWT.LEFT + SWT.WRAP);
		fNoHierarchyShownLabel.setText(TypeHierarchyMessages.getString("TypeHierarchyViewPart.empty")); //$NON-NLS-1$	
		
		MenuManager menu= new MenuManager();
		menu.add(fFocusOnTypeAction);
		fNoHierarchyShownLabel.setMenu(menu.createContextMenu(fNoHierarchyShownLabel));
		
		fPagebook.showPage(fNoHierarchyShownLabel);

		int orientation;
		try {
			orientation= fDialogSettings.getInt(DIALOGSTORE_VIEWORIENTATION);
			if (orientation < 0 || orientation > 2) {
				orientation= VIEW_ORIENTATION_VERTICAL;
			}
		} catch (NumberFormatException e) {
			orientation= VIEW_ORIENTATION_VERTICAL;
		}
		// force the update
		fCurrentOrientation= -1;
		// will fill the main tool bar
		setOrientation(orientation);
		
		// set the filter menu items
		IActionBars actionBars= getViewSite().getActionBars();
		IMenuManager viewMenu= actionBars.getMenuManager();
		for (int i= 0; i < fToggleOrientationActions.length; i++) {
			viewMenu.add(fToggleOrientationActions[i]);
		}
		viewMenu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		
	
		// fill the method viewer toolbar
		ToolBarManager lowertbmanager= new ToolBarManager(methodViewerToolBar);
		lowertbmanager.add(fEnableMemberFilterAction);			
		lowertbmanager.add(new Separator());
		fMethodsViewer.contributeToToolBar(lowertbmanager);
		lowertbmanager.update(true);
							
		// selection provider
		int nHierarchyViewers= fAllViewers.length; 
		Viewer[] trackedViewers= new Viewer[nHierarchyViewers + 1];
		for (int i= 0; i < nHierarchyViewers; i++) {
			trackedViewers[i]= fAllViewers[i];
		}
		trackedViewers[nHierarchyViewers]= fMethodsViewer;
		fSelectionProviderMediator= new SelectionProviderMediator(trackedViewers);
		IStatusLineManager slManager= getViewSite().getActionBars().getStatusLineManager();
		fSelectionProviderMediator.addSelectionChangedListener(new StatusBarUpdater(slManager));
		
		getSite().setSelectionProvider(fSelectionProviderMediator);
		getSite().getPage().addPartListener(fPartListener);
		
		IJavaElement input= determineInputElement();
		if (fMemento != null) {
			restoreState(fMemento, input);
		} else if (input != null) {
			setInputElement(input);
		} else {
			setViewerVisibility(false);
		}

		WorkbenchHelp.setHelp(fPagebook, IJavaHelpContextIds.TYPE_HIERARCHY_VIEW);
		
		fActionGroups= new CompositeActionGroup(new ActionGroup[] {
				new OpenEditorActionGroup(this), 
				new OpenViewActionGroup(this), 
				new ShowActionGroup(this), 
				fCCPActionGroup= new CCPActionGroup(this), 
				new RefactorActionGroup(this),
				new GenerateActionGroup(this),
				new JavaSearchActionGroup(this)});
		
		fActionGroups.fillActionBars(getViewSite().getActionBars());
		
		initDragAndDrop();		
	}


	/**
	 * called from ToggleOrientationAction.
	 * @param orientation VIEW_ORIENTATION_SINGLE, VIEW_ORIENTATION_HORIZONTAL or VIEW_ORIENTATION_VERTICAL
	 */	
	public void setOrientation(int orientation) {
		if (fCurrentOrientation != orientation) {
			boolean methodViewerNeedsUpdate= false;
			
			if (fMethodViewerViewForm != null && !fMethodViewerViewForm.isDisposed()
					&& fTypeMethodsSplitter != null && !fTypeMethodsSplitter.isDisposed()) {
				if (orientation == VIEW_ORIENTATION_SINGLE) {
					fMethodViewerViewForm.setVisible(false);
					enableMemberFilter(false);
					updateMethodViewer(null);
				} else {
					if (fCurrentOrientation == VIEW_ORIENTATION_SINGLE) {
						fMethodViewerViewForm.setVisible(true);
						methodViewerNeedsUpdate= true;
					}
					boolean horizontal= orientation == VIEW_ORIENTATION_HORIZONTAL;
					fTypeMethodsSplitter.setOrientation(horizontal ? SWT.HORIZONTAL : SWT.VERTICAL);
				}
				updateMainToolbar(orientation);
				fTypeMethodsSplitter.layout();
			}
			for (int i= 0; i < fToggleOrientationActions.length; i++) {
				fToggleOrientationActions[i].setChecked(orientation == fToggleOrientationActions[i].getOrientation());
			}
			fCurrentOrientation= orientation;
			if (methodViewerNeedsUpdate) {
				updateMethodViewer(fSelectedType);
			}
			fDialogSettings.put(DIALOGSTORE_VIEWORIENTATION, orientation);
		}
	}
	
		
	private void updateMainToolbar(int orientation) {
		IActionBars actionBars= getViewSite().getActionBars();
		IToolBarManager tbmanager= actionBars.getToolBarManager();	
				
		if (orientation == VIEW_ORIENTATION_HORIZONTAL) {
			clearMainToolBar(tbmanager);
			ToolBar typeViewerToolBar= new ToolBar(fTypeViewerViewForm, SWT.FLAT | SWT.WRAP);
			fillMainToolBar(new ToolBarManager(typeViewerToolBar));
			fTypeViewerViewForm.setTopLeft(typeViewerToolBar);
		} else {
			fTypeViewerViewForm.setTopLeft(null);
			fillMainToolBar(tbmanager);
		}
	}
	
	private void fillMainToolBar(IToolBarManager tbmanager) {
		tbmanager.removeAll();
		tbmanager.add(fHistoryDropDownAction);
		for (int i= 0; i < fViewActions.length; i++) {
			tbmanager.add(fViewActions[i]);
		}
		tbmanager.update(false);	
	}

	private void clearMainToolBar(IToolBarManager tbmanager) {
		tbmanager.removeAll();
		tbmanager.update(false);		
	}	
	
	
	/**
	 * Creates the context menu for the hierarchy viewers
	 */
	private void fillTypesViewerContextMenu(TypeHierarchyViewer viewer, IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);
		
		menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, new Separator(GROUP_FOCUS));
		// viewer entries
		viewer.contributeToContextMenu(menu);
//		IStructuredSelection selection= (IStructuredSelection)viewer.getSelection();
//		if (JavaBasePreferencePage.openTypeHierarchyInPerspective()) {
//			addOpenPerspectiveItem(menu, selection);
//		}
//		addOpenWithMenu(menu, selection);
		
		if (fFocusOnSelectionAction.canActionBeAdded())
			menu.appendToGroup(GROUP_FOCUS, fFocusOnSelectionAction);
		menu.appendToGroup(GROUP_FOCUS, fFocusOnTypeAction);

		fActionGroups.setContext(new ActionContext(getSite().getSelectionProvider().getSelection()));
		fActionGroups.fillContextMenu(menu);
		fActionGroups.setContext(null);

//		
//		ContextMenuGroup.add(menu, new ContextMenuGroup[] { new BuildGroup(this, false)}, viewer);
//		
//		// XXX workaround until we have fully converted the code to use the new action groups
//		fActionGroups.get(2).fillContextMenu(menu);		
//		fActionGroups.get(3).fillContextMenu(menu);		
//		fActionGroups.get(4).fillContextMenu(menu);		
	}

	/**
	 * Creates the context menu for the method viewer
	 */	
	private void fillMethodsViewerContextMenu(IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);
		// viewer entries
		fMethodsViewer.contributeToContextMenu(menu);
		if (fSelectedType != null &&  fAddStubAction.init(fSelectedType, fMethodsViewer.getSelection())) {
			menu.appendToGroup(IContextMenuConstants.GROUP_REORGANIZE, fAddStubAction);
		}
		fActionGroups.setContext(new ActionContext(getSite().getSelectionProvider().getSelection()));
		fActionGroups.fillContextMenu(menu);
		fActionGroups.setContext(null);
		
		
//		//menu.appendToGroup(IContextMenuConstants.GROUP_REORGANIZE, new JavaReplaceWithEditionAction(fMethodsViewer));	
//		addOpenWithMenu(menu, (IStructuredSelection)fMethodsViewer.getSelection());
//		ContextMenuGroup.add(menu, new ContextMenuGroup[] { new BuildGroup(this, false)}, fMethodsViewer);
//		
//		// XXX workaround until we have fully converted the code to use the new action groups
//		fActionGroups.get(2).fillContextMenu(menu);
//		fActionGroups.get(3).fillContextMenu(menu);		
//		fActionGroups.get(4).fillContextMenu(menu);		
	}
	
	private void addOpenWithMenu(IMenuManager menu, IStructuredSelection selection) {
		// If one file is selected get it.
		// Otherwise, do not show the "open with" menu.
		if (selection.size() != 1)
			return;

		Object element= selection.getFirstElement();
		if (!(element instanceof IJavaElement))
			return;
		IResource resource= null;
		try {
			resource= ((IJavaElement)element).getUnderlyingResource();	
		} catch(JavaModelException e) {
			// ignore
		}
		if (!(resource instanceof IFile))
			return; 

		// Create a menu flyout.
		MenuManager submenu= new MenuManager(TypeHierarchyMessages.getString("TypeHierarchyViewPart.menu.open")); //$NON-NLS-1$
		submenu.add(new OpenWithMenu(getSite().getPage(), (IFile) resource));

		// Add the submenu.
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, submenu);
	}

	/**
	 * Toggles between the empty viewer page and the hierarchy
	 */
	private void setViewerVisibility(boolean showHierarchy) {
		if (showHierarchy) {
			fViewerbook.showPage(getCurrentViewer().getControl());
		} else {
			fViewerbook.showPage(fEmptyTypesViewer);
		}
	}
	
	/**
	 * Sets the member filter. <code>null</code> disables member filtering.
	 */	
	private void setMemberFilter(IMember[] memberFilter) {
		Assert.isNotNull(fAllViewers);
		for (int i= 0; i < fAllViewers.length; i++) {
			fAllViewers[i].setMemberFilter(memberFilter);
		}
	}	
	
	private IType getSelectableType(IJavaElement elem) {
		ISelection sel= null;
		if (elem.getElementType() != IJavaElement.TYPE) {
			return (IType) getCurrentViewer().getTreeRootType();
		} else {
			return (IType) elem;
		}
	}
	
	private void internalSelectType(IMember elem, boolean reveal) {	
		TypeHierarchyViewer viewer= getCurrentViewer();
		viewer.removeSelectionChangedListener(fSelectionChangedListener);
		viewer.setSelection(elem != null ? new StructuredSelection(elem) : StructuredSelection.EMPTY, reveal);
		viewer.addSelectionChangedListener(fSelectionChangedListener);
	}
	
	private void internalSelectMember(IMember member) {
		fMethodsViewer.removeSelectionChangedListener(fSelectionChangedListener);
		fMethodsViewer.setSelection(member != null ? new StructuredSelection(member) : StructuredSelection.EMPTY);
		fMethodsViewer.addSelectionChangedListener(fSelectionChangedListener);
	}	
	
	/**
	 * When the input changed or the hierarchy pane becomes visible,
	 * <code>updateHierarchyViewer<code> brings up the correct view and refreshes
	 * the current tree
	 */
	private void updateHierarchyViewer() {
		if (fInputElement == null) {
			fPagebook.showPage(fNoHierarchyShownLabel);
		} else {
			if (getCurrentViewer().containsElements() != null) {
				Runnable runnable= new Runnable() {
					public void run() {
						getCurrentViewer().updateContent(); // refresh
					}
				};
				BusyIndicator.showWhile(getDisplay(), runnable);
				if (!isChildVisible(fViewerbook, getCurrentViewer().getControl())) {
					setViewerVisibility(true);
				}	
			} else {							
				fEmptyTypesViewer.setText(TypeHierarchyMessages.getFormattedString("TypeHierarchyViewPart.nodecl", fInputElement.getElementName()));				 //$NON-NLS-1$
				setViewerVisibility(false);
			}
		}
	}
	
	private void updateMethodViewer(IType input) {
		if (input != fMethodsViewer.getInput() && !fIsEnableMemberFilter && fCurrentOrientation != VIEW_ORIENTATION_SINGLE) {
			if (input != null) {
				fMethodViewerPaneLabel.setText(fPaneLabelProvider.getText(input));
				fMethodViewerPaneLabel.setImage(fPaneLabelProvider.getImage(input));
			} else {
				fMethodViewerPaneLabel.setText(""); //$NON-NLS-1$
				fMethodViewerPaneLabel.setImage(null);
			}
			fMethodsViewer.setInput(input);
		}
	}
	
	private void doSelectionChanged(SelectionChangedEvent e) {
		if (e.getSelectionProvider() == fMethodsViewer) {
			methodSelectionChanged(e.getSelection());
		} else {
			typeSelectionChanged(e.getSelection());
		}
	}
	
	
	
	private void methodSelectionChanged(ISelection sel) {
		if (sel instanceof IStructuredSelection) {
			List selected= ((IStructuredSelection)sel).toList();
			int nSelected= selected.size();
			if (fIsEnableMemberFilter) {
				IMember[] memberFilter= null;
				if (nSelected > 0) {
					memberFilter= new IMember[nSelected];
					selected.toArray(memberFilter);
				}
				setMemberFilter(memberFilter);
				updateHierarchyViewer();
				updateTitle();
				internalSelectType(fSelectedType, true);	
			}
			if (nSelected == 1) {
				revealElementInEditor(selected.get(0), fMethodsViewer);
			}
		}
	}
	
	private void typeSelectionChanged(ISelection sel) {
		if (sel instanceof IStructuredSelection) {
			List selected= ((IStructuredSelection)sel).toList();
			int nSelected= selected.size();
			if (nSelected != 0) {
				List types= new ArrayList(nSelected);
				for (int i= nSelected-1; i >= 0; i--) {
					Object elem= selected.get(i);
					if (elem instanceof IType && !types.contains(elem)) {
						types.add(elem);
					}
				}
				if (types.size() == 1) {
					fSelectedType= (IType) types.get(0);
					updateMethodViewer(fSelectedType);
				} else if (types.size() == 0) {
					// method selected, no change
				}
				if (nSelected == 1) {
					revealElementInEditor(selected.get(0), getCurrentViewer());
				}
			} else {
				fSelectedType= null;
				updateMethodViewer(null);
			}
		}
	}
	
	private void revealElementInEditor(Object elem, Viewer originViewer) {
		// only allow revealing when the type hierarchy is the active pagae
		// no revealing after selection events due to model changes
		if (getSite().getPage().getActivePart() != this) {
			return;
		}
		
		if (fSelectionProviderMediator.getViewerInFocus() != originViewer) {
			return;
		}
		
		IEditorPart editorPart= EditorUtility.isOpenInEditor(elem);
		if (editorPart != null && (elem instanceof IJavaElement)) {
			try {
				getSite().getPage().removePartListener(fPartListener);
				EditorUtility.openInEditor(elem, false);
				EditorUtility.revealInEditor(editorPart, (IJavaElement) elem);
				getSite().getPage().addPartListener(fPartListener);
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
	}
	
	private Display getDisplay() {
		if (fPagebook != null && !fPagebook.isDisposed()) {
			return fPagebook.getDisplay();
		}
		return null;
	}		
	
	private boolean isChildVisible(Composite pb, Control child) {
		Control[] children= pb.getChildren();
		for (int i= 0; i < children.length; i++) {
			if (children[i] == child && children[i].isVisible())
				return true;
		}
		return false;
	}
	
	private void updateTitle() {
		String viewerTitle= getCurrentViewer().getTitle();
		
		String tooltip;
		String title;
		if (fInputElement != null) {
			String[] args= new String[] { viewerTitle, JavaElementLabels.getElementLabel(fInputElement, JavaElementLabels.ALL_DEFAULT) };
			title= TypeHierarchyMessages.getFormattedString("TypeHierarchyViewPart.title", args); //$NON-NLS-1$
			tooltip= TypeHierarchyMessages.getFormattedString("TypeHierarchyViewPart.tooltip", args); //$NON-NLS-1$
		} else {
			title= viewerTitle;
			tooltip= viewerTitle;
		}
		setTitle(title);
		setTitleToolTip(tooltip);
	}
	
	private void updateToolbarButtons() {
		boolean isType= fInputElement instanceof IType;
		for (int i= 0; i < fViewActions.length; i++) {
			ToggleViewAction action= fViewActions[i];
			if (action.getViewerIndex() == VIEW_ID_TYPE) {
				action.setEnabled(fInputElement != null);
			} else {
				action.setEnabled(isType);
			}
		}
	}
		
	/**
	 * Sets the current view (see view id)
	 * called from ToggleViewAction. Must be called after creation of the viewpart.
	 */	
	public void setView(int viewerIndex) {
		Assert.isNotNull(fAllViewers);
		if (viewerIndex < fAllViewers.length && fCurrentViewerIndex != viewerIndex) {			
			fCurrentViewerIndex= viewerIndex;
			
			updateHierarchyViewer();
			if (fInputElement != null) {
				ISelection currSelection= getCurrentViewer().getSelection();
				if (currSelection == null || currSelection.isEmpty()) {
					internalSelectType(getSelectableType(fInputElement), false);
					currSelection= getCurrentViewer().getSelection();
				}
				if (!fIsEnableMemberFilter) {
					typeSelectionChanged(currSelection);
				}
			}		
			updateTitle();
					
			fDialogSettings.put(DIALOGSTORE_HIERARCHYVIEW, viewerIndex);
			getCurrentViewer().getTree().setFocus();
		}
		for (int i= 0; i < fViewActions.length; i++) {
			ToggleViewAction action= fViewActions[i];
			action.setChecked(fCurrentViewerIndex == action.getViewerIndex());
		}
	}

	/**
	 * Gets the curret active view index.
	 */		
	public int getViewIndex() {
		return fCurrentViewerIndex;
	}
	
	private TypeHierarchyViewer getCurrentViewer() {
		return fAllViewers[fCurrentViewerIndex];
	}

	/**
	 * called from EnableMemberFilterAction.
	 * Must be called after creation of the viewpart.
	 */	
	public void enableMemberFilter(boolean on) {
		if (on != fIsEnableMemberFilter) {
			fIsEnableMemberFilter= on;
			if (!on) {
				IType methodViewerInput= (IType) fMethodsViewer.getInput();
				setMemberFilter(null);
				updateHierarchyViewer();
				updateTitle();
			
				if (methodViewerInput != null && getCurrentViewer().isElementShown(methodViewerInput)) {
					// avoid that the method view changes content by selecting the previous input
					internalSelectType(methodViewerInput, true);
				} else if (fSelectedType != null) {
					// choose a input that exists
					internalSelectType(fSelectedType, true);
					updateMethodViewer(fSelectedType);
				}
			} else {
				methodSelectionChanged(fMethodsViewer.getSelection());
			}
		}
		fEnableMemberFilterAction.setChecked(on);
	}
	
	/**
	 * Called from ITypeHierarchyLifeCycleListener.
	 * Can be called from any thread
	 */
	private void doTypeHierarchyChanged(final TypeHierarchyLifeCycle typeHierarchy, final IType[] changedTypes) {
		Display display= getDisplay();
		if (display != null) {
			display.asyncExec(new Runnable() {
				public void run() {
					if (fPagebook != null && !fPagebook.isDisposed()) {
						doTypeHierarchyChangedOnViewers(changedTypes);
					}
				}
			});
		}
	}
	
	private void doTypeHierarchyChangedOnViewers(IType[] changedTypes) {
		if (fHierarchyLifeCycle.getHierarchy() == null || !fHierarchyLifeCycle.getHierarchy().exists()) {
			clearInput();
		} else {
			if (changedTypes == null) {
				// hierarchy change
				try {
					fHierarchyLifeCycle.ensureRefreshedTypeHierarchy(fInputElement);
				} catch (JavaModelException e) {
					JavaPlugin.log(e.getStatus());
					clearInput();
					return;
				}
				updateHierarchyViewer();
			} else {
				// elements in hierarchy modified
				if (getCurrentViewer().isMethodFiltering()) {
					if (changedTypes.length == 1) {
						getCurrentViewer().refresh(changedTypes[0]);
					} else {
						updateHierarchyViewer();
					}
				} else {
					getCurrentViewer().update(changedTypes, new String[] { IBasicPropertyConstants.P_TEXT, IBasicPropertyConstants.P_IMAGE } );
				}
			}
			fMethodsViewer.refresh();
		}
	}	
	

	
	/**
	 * Determines the input element to be used initially .
	 */	
	private IJavaElement determineInputElement() {
		Object input= getSite().getPage().getInput();
		if (input instanceof IJavaElement) { 
			IJavaElement elem= (IJavaElement) input;
			if (elem instanceof IMember) {
				return elem;
			} else {
				int kind= elem.getElementType();
				if (kind == IJavaElement.JAVA_PROJECT || kind == IJavaElement.PACKAGE_FRAGMENT_ROOT || kind == IJavaElement.PACKAGE_FRAGMENT) {
					return elem;
				}
			}
		} 
		return null;	
	}
	
	/*
	 * @see IViewPart#init
	 */
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		fMemento= memento;
	}	
	
	/*
	 * @see ViewPart#saveState(IMemento)
	 */
	public void saveState(IMemento memento) {
		if (fPagebook == null) {
			// part has not been created
			if (fMemento != null) { //Keep the old state;
				memento.putMemento(fMemento);
			}
			return;
		}
		if (fInputElement != null) {
			String handleIndentifier=  fInputElement.getHandleIdentifier();
			if (fInputElement instanceof IType) {
				ITypeHierarchy hierarchy= fHierarchyLifeCycle.getHierarchy();
				if (hierarchy != null && hierarchy.getSubtypes((IType) fInputElement).length > 1000) {
					// for startup performance reasons do not try to recover huge hierarchies
					handleIndentifier= null;
				}
			}
			memento.putString(TAG_INPUT, handleIndentifier);
		}		
		memento.putInteger(TAG_VIEW, getViewIndex());
		memento.putInteger(TAG_ORIENTATION, fCurrentOrientation);	
		int weigths[]= fTypeMethodsSplitter.getWeights();
		int ratio= (weigths[0] * 1000) / (weigths[0] + weigths[1]);
		memento.putInteger(TAG_RATIO, ratio);
		
		ScrollBar bar= getCurrentViewer().getTree().getVerticalBar();
		int position= bar != null ? bar.getSelection() : 0;
		memento.putInteger(TAG_VERTICAL_SCROLL, position);

		IJavaElement selection= (IJavaElement)((IStructuredSelection) getCurrentViewer().getSelection()).getFirstElement();
		if (selection != null) {
			memento.putString(TAG_SELECTION, selection.getHandleIdentifier());
		}
			
		fMethodsViewer.saveState(memento);
	}
	
	/**
	 * Restores the type hierarchy settings from a memento.
	 */
	private void restoreState(IMemento memento, IJavaElement defaultInput) {
		IJavaElement input= defaultInput;
		String elementId= memento.getString(TAG_INPUT);
		if (elementId != null) {
			input= JavaCore.create(elementId);
			if (input != null && !input.exists()) {
				input= null;
			}
		}
		setInputElement(input);

		Integer viewerIndex= memento.getInteger(TAG_VIEW);
		if (viewerIndex != null) {
			setView(viewerIndex.intValue());
		}
		Integer orientation= memento.getInteger(TAG_ORIENTATION);
		if (orientation != null) {
			setOrientation(orientation.intValue());
		}
		Integer ratio= memento.getInteger(TAG_RATIO);
		if (ratio != null) {
			fTypeMethodsSplitter.setWeights(new int[] { ratio.intValue(), 1000 - ratio.intValue() });
		}
		ScrollBar bar= getCurrentViewer().getTree().getVerticalBar();
		if (bar != null) {
			Integer vScroll= memento.getInteger(TAG_VERTICAL_SCROLL);
			if (vScroll != null) {
				bar.setSelection(vScroll.intValue());
			}
		}
		
		String selectionId= memento.getString(TAG_SELECTION);
		// do not restore type hierarchy contents
//		if (selectionId != null) {
//			IJavaElement elem= JavaCore.create(selectionId);
//			if (getCurrentViewer().isElementShown(elem) && elem instanceof IMember) {
//				internalSelectType((IMember)elem, false);
//			}
//		}
		fMethodsViewer.restoreState(memento);
	}
	
	/**
	 * Link selection to active editor.
	 */
	private void editorActivated(IEditorPart editor) {
		if (!JavaBasePreferencePage.linkTypeHierarchySelectionToEditor()) {
			return;
		}
		if (fInputElement == null) {
			// no type hierarchy shown
			return;
		}
		
		IJavaElement elem= (IJavaElement)editor.getEditorInput().getAdapter(IJavaElement.class);
		try {
			TypeHierarchyViewer currentViewer= getCurrentViewer();
			if (elem instanceof IClassFile) {
				IType type= ((IClassFile)elem).getType();
				if (currentViewer.isElementShown(type)) {
					internalSelectType(type, true);
					updateMethodViewer(type);
				}
			} else if (elem instanceof ICompilationUnit) {
				IType[] allTypes= ((ICompilationUnit)elem).getAllTypes();
				for (int i= 0; i < allTypes.length; i++) {
					if (currentViewer.isElementShown(allTypes[i])) {
						internalSelectType(allTypes[i], true);
						updateMethodViewer(allTypes[i]);
						return;
					}
				}
			}	
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
		
	}	
	

}