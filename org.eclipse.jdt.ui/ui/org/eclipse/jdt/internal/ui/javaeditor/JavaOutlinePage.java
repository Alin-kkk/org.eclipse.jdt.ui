package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.texteditor.IUpdate;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.actions.ContextMenuGroup;
import org.eclipse.jdt.internal.ui.actions.GenerateGroup;
import org.eclipse.jdt.internal.ui.actions.OpenHierarchyAction;
import org.eclipse.jdt.internal.ui.actions.RetargetActionIDs;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDropAdapter;
import org.eclipse.jdt.internal.ui.dnd.LocalSelectionTransfer;
import org.eclipse.jdt.internal.ui.dnd.TransferDragSourceListener;
import org.eclipse.jdt.internal.ui.dnd.TransferDropTargetListener;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDragAdapter;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDropAdapter;
import org.eclipse.jdt.internal.ui.refactoring.actions.IRefactoringAction;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringGroup;
import org.eclipse.jdt.internal.ui.reorg.ReorgGroup;
import org.eclipse.jdt.internal.ui.search.JavaSearchGroup;
import org.eclipse.jdt.internal.ui.util.OpenTypeHierarchyUtil;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.MemberFilterActionGroup;
import org.eclipse.jdt.internal.ui.viewsupport.OverrideAdornmentProvider;
import org.eclipse.jdt.internal.ui.viewsupport.StandardJavaUILabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.JavaElementSorter;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.OpenActionGroup;
import org.eclipse.jdt.ui.actions.ShowActionGroup;


/**
 * The content outline page of the Java editor. The viewer implements a proprietary
 * update mechanism based on Java model deltas. It does not react on domain changes.
 * It is specified to show the content of ICompilationUnits and IClassFiles.
 * Pulishes its context menu under <code>JavaPlugin.getDefault().getPluginId() + ".outliner"</code>.
 */
class JavaOutlinePage extends Page implements IContentOutlinePage {

			/**
			 * The element change listener of the java outline viewer.
			 * @see IElementChangedListener
			 */
			class ElementChangedListener implements IElementChangedListener {
				
				public void elementChanged(final ElementChangedEvent e) {
					Display d= getControl().getDisplay();
					if (d != null) {
						d.asyncExec(new Runnable() {
							public void run() {
								IJavaElementDelta delta= findElement( (ICompilationUnit) fInput, e.getDelta());
								if (delta != null && fOutlineViewer != null) {
									fOutlineViewer.reconcile(delta);
								}
							}
						});
					}
				}
				
				protected IJavaElementDelta findElement(ICompilationUnit unit, IJavaElementDelta delta) {
					
					if (delta == null || unit == null)
						return null;
					
					IJavaElement element= delta.getElement();
					
					if (unit.equals(element))
						return delta;
					
					if (element.getElementType() > IJavaElement.CLASS_FILE)
						return null;
						
					IJavaElementDelta[] children= delta.getAffectedChildren();
					if (children == null || children.length == 0)
						return null;
						
					for (int i= 0; i < children.length; i++) {
						IJavaElementDelta d= findElement(unit, children[i]);
						if (d != null)
							return d;
					}
					
					return null;
				}
			};
			
			/**
			 * Content provider for the children of an ICompilationUnit or
			 * an IClassFile
			 * @see ITreeContentProvider
			 */
			class ChildrenProvider implements ITreeContentProvider {
				
				private ElementChangedListener fListener;
				private JavaOutlineErrorTickUpdater fErrorTickUpdater;
				
				protected boolean matches(IJavaElement element) {
					if (element.getElementType() == IJavaElement.METHOD) {
						String name= element.getElementName();
						return (name != null && name.indexOf('<') >= 0);
					}
					return false;
				}
				
				protected IJavaElement[] filter(IJavaElement[] children) {
					boolean initializers= false;
					for (int i= 0; i < children.length; i++) {
						if (matches(children[i])) {
							initializers= true;
							break;
						}
					}
							
					if (!initializers)
						return children;
						
					Vector v= new Vector();
					for (int i= 0; i < children.length; i++) {
						if (matches(children[i]))
							continue;
						v.addElement(children[i]);
					}
					
					IJavaElement[] result= new IJavaElement[v.size()];
					v.copyInto(result);
					return result;
				}
				
				public Object[] getChildren(Object parent) {
					if (parent instanceof IParent) {
						IParent c= (IParent) parent;
						try {
							return filter(c.getChildren());
						} catch (JavaModelException x) {
							JavaPlugin.getDefault().logErrorStatus(JavaEditorMessages.getString("JavaOutlinePage.error.ChildrenProvider.getChildren.message1"), x.getStatus()); //$NON-NLS-1$
						}
					}
					return new Object[0];
				}
				
				public Object[] getElements(Object parent) {
					return getChildren(parent);
				}
				
				public Object getParent(Object child) {
					if (child instanceof IJavaElement) {
						IJavaElement e= (IJavaElement) child;
						return e.getParent();
					}
					return null;
				}
				
				public boolean hasChildren(Object parent) {
					if (parent instanceof IParent) {
						IParent c= (IParent) parent;
						try {
							IJavaElement[] children= filter(c.getChildren());
							return (children != null && children.length > 0);
						} catch (JavaModelException x) {
							JavaPlugin.getDefault().logErrorStatus(JavaEditorMessages.getString("JavaOutlinePage.error.ChildrenProvider.hasChildren.message1"), x.getStatus()); //$NON-NLS-1$
						}
					}
					return false;
				}
				
				public boolean isDeleted(Object o) {
					return false;
				}
				
				public void dispose() {
					if (fListener != null) {
						JavaCore.removeElementChangedListener(fListener);
						fListener= null;
					}
					if (fErrorTickUpdater != null) {
						fErrorTickUpdater.setAnnotationModel(null);
						fErrorTickUpdater= null;
					}					
				}
				
				/*
				 * @see IContentProvider#inputChanged(Viewer, Object, Object)
				 */
				public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
					boolean isCU= (newInput instanceof ICompilationUnit);
									
					if (isCU && fListener == null) {
						fListener= new ElementChangedListener();
						JavaCore.addElementChangedListener(fListener);
						fErrorTickUpdater= new JavaOutlineErrorTickUpdater(fOutlineViewer);
						fErrorTickUpdater.setAnnotationModel(fEditor.getDocumentProvider().getAnnotationModel(fEditor.getEditorInput()));
					} else if (isCU && fErrorTickUpdater != null) {
						fErrorTickUpdater.setAnnotationModel(fEditor.getDocumentProvider().getAnnotationModel(fEditor.getEditorInput()));
					} else if (!isCU && fListener != null) {
						JavaCore.removeElementChangedListener(fListener);
						fListener= null;
						
						fErrorTickUpdater.setAnnotationModel(null);
						fErrorTickUpdater= null;
					}
				}
			};
			
			
			class JavaOutlineViewer extends TreeViewer {
				
				/**
				 * Indicates an item which has been reused. At the point of
				 * its reuse it has been expanded. This field is used to
				 * communicate between <code>internalExpandToLevel</code> and
				 * <code>reuseTreeItem</code>.
				 */
				private Item fReusedExpandedItem;
				
				public JavaOutlineViewer(Tree tree) {
					super(tree);
					setAutoExpandLevel(ALL_LEVELS);
				}
				
				/**
				 * Investigates the given element change event and if affected incrementally
				 * updates the outline.
				 */
				public void reconcile(IJavaElementDelta delta) {
					if (getSorter() == null) {
						Widget w= findItem(fInput);
						if (w != null)
							update(w, delta);
					} else {
						// just for now
						refresh();
					}
				}
				
				/*
				 * @see TreeViewer#internalExpandToLevel
				 */
				protected void internalExpandToLevel(Widget node, int level) {
					if (node instanceof Item) {
						Item i= (Item) node;
						if (i.getData() instanceof IJavaElement) {
							IJavaElement je= (IJavaElement) i.getData();
							if (je.getElementType() == IJavaElement.IMPORT_CONTAINER || isInnerType(je)) {
								if (i != fReusedExpandedItem) {
									setExpanded(i, false);
									return;
								}
							}
						}
					}
					super.internalExpandToLevel(node, level);
				}
								
				protected void reuseTreeItem(Item item, Object element) {
					
					// remove children
					Item[] c= getChildren(item);
					if (c != null && c.length > 0) {
						
						if (getExpanded(item))
							fReusedExpandedItem= item;
						
						for (int k= 0; k < c.length; k++) {
							if (c[k].getData() != null)
								disassociate(c[k]);
							c[k].dispose();
						}
					}
					
					updateItem(item, element);
					updatePlus(item, element);					
					internalExpandToLevel(item, ALL_LEVELS);
					
					fReusedExpandedItem= null;
				}
				
				protected boolean mustUpdateParent(IJavaElementDelta delta, IJavaElement element) {
					if (element instanceof IMethod) {
						if ((delta.getKind() & IJavaElementDelta.ADDED) != 0) {
							try {
								return JavaModelUtil.isMainMethod((IMethod)element);
							} catch (JavaModelException e) {
								JavaPlugin.log(e.getStatus());
							}
						}
						return "main".equals(element.getElementName()); //$NON-NLS-1$
					}
					return false;
				}
				
				protected ISourceRange getSourceRange(IJavaElement element) throws JavaModelException {
					if (element instanceof IMember)
						return ((IMember) element).getNameRange();
					if (element instanceof ISourceReference)
						return ((ISourceReference) element).getSourceRange();
					return null;
				}
				
				protected boolean overlaps(ISourceRange range, int start, int end) {
					return start <= (range.getOffset() + range.getLength() - 1) && range.getOffset() <= end;
				}
				
				protected boolean filtered(IJavaElement parent, IJavaElement child) {
					
					Object[] result= new Object[] { child };
					ViewerFilter[] filters= getFilters();
					for (int i= 0; i < filters.length; i++) {
						result= filters[i].filter(this, parent, result);
						if (result.length == 0)
							return true;
					}
					
					return false;
				}
				
				protected void update(Widget w, IJavaElementDelta delta) {
					
					Item item;
					
					IJavaElement parent= delta.getElement();
					IJavaElementDelta[] affected= delta.getAffectedChildren();
					Item[] children= getChildren(w);

					boolean doUpdateParent= false;
										
					Vector deletions= new Vector();
					Vector additions= new Vector();				

					for (int i= 0; i < affected.length; i++) {
					    IJavaElementDelta affectedDelta= affected[i];
						IJavaElement affectedElement= affectedDelta.getElement();
						int status= affected[i].getKind();

						// find tree item with affected element
						int j;
						for (j= 0; j < children.length; j++)
						    if (affectedElement.equals(children[j].getData()))
						    	break;
						
						if (j == children.length) {
							// addition
							if ((status & IJavaElementDelta.CHANGED) != 0 &&							
								(affectedDelta.getFlags() & IJavaElementDelta.F_MODIFIERS) != 0 &&
								!filtered(parent, affectedElement))
							{
								additions.addElement(affectedDelta);
							}
							continue;
						}

						item= children[j];

						// removed						    
						if ((status & IJavaElementDelta.REMOVED) != 0) {
							deletions.addElement(item);
							doUpdateParent= doUpdateParent || mustUpdateParent(affectedDelta, affectedElement);

						// changed						    
						} else if ((status & IJavaElementDelta.CHANGED) != 0) {
							int change= affectedDelta.getFlags();
							doUpdateParent= doUpdateParent || mustUpdateParent(affectedDelta, affectedElement);
							
							if ((change & IJavaElementDelta.F_MODIFIERS) != 0) {
								if (filtered(parent, affectedElement))
									deletions.addElement(item);
								else
									updateItem(item, affectedElement);
							}
							
							if ((change & IJavaElementDelta.F_CONTENT) != 0)
								updateItem(item, affectedElement);
								
							if ((change & IJavaElementDelta.F_CHILDREN) != 0)
								update(item, affectedDelta);															    
						}
					}
					
					// find all elements to add
					IJavaElementDelta[] add= delta.getAddedChildren();
					if (additions.size() > 0) {
						IJavaElementDelta[] tmp= new IJavaElementDelta[add.length + additions.size()];
						System.arraycopy(add, 0, tmp, 0, add.length);
						for (int i= 0; i < additions.size(); i++)
							tmp[i + add.length]= (IJavaElementDelta) additions.elementAt(i);
						add= tmp;
					}
					
					// add at the right position
					go2: for (int i= 0; i < add.length; i++) {
						
						try {
							
							IJavaElement e= add[i].getElement();
							if (filtered(parent, e))
								continue go2;
								
							doUpdateParent= doUpdateParent || mustUpdateParent(add[i], e);
							ISourceRange rng= getSourceRange(e);
							int start= rng.getOffset();
							int end= start + rng.getLength() - 1;
							
							Item last= null;
							item= null;
							children= getChildren(w);
							
							for (int j= 0; j < children.length; j++) {
								item= children[j];
								IJavaElement r= (IJavaElement) item.getData();
								
								if (r == null) {
									// parent node collapsed and not be opened before -> do nothing
									continue go2;
								}
								
									
								try {
									rng= getSourceRange(r);
									if (overlaps(rng, start, end)) {
										
										// be tolerant if the delta is not correct, or if 
										// the tree has been updated other than by a delta
										reuseTreeItem(item, e);
										continue go2;
										
									} else if (rng.getOffset() > start) {
										
										if (last != null && deletions.contains(last)) {
											// reuse item
											deletions.removeElement(last);
											reuseTreeItem(last, (Object) e);
										} else {
											// nothing to reuse
											createTreeItem(w, (Object) e, j);
										}
										continue go2;
									}
									
								} catch (JavaModelException x) {
									// stumbled over deleted element
								}
								
								last= item;
							}
						
							// add at the end of the list
							if (last != null && deletions.contains(last)) {
								// reuse item
								deletions.removeElement(last);
								reuseTreeItem(last, e);
							} else {
								// nothing to reuse
								createTreeItem(w, e, -1);
							}
						
						} catch (JavaModelException x) {
							// the element to be added is not present -> don't add it
						}
					}
					
					
					// remove items which haven't been reused
					Enumeration e= deletions.elements();
					while (e.hasMoreElements()) {
						item= (Item) e.nextElement();
						disassociate(item);
						item.dispose();
					}
					
					if (doUpdateParent)
						updateItem(w, delta.getElement());
				}					
			};
				
			class LexicalSortingAction extends Action {
				
				private JavaElementSorter fSorter= new JavaElementSorter();			

				public LexicalSortingAction() {
					super();
					
					setText(JavaEditorMessages.getString("JavaOutlinePage.Sort.label")); //$NON-NLS-1$
					JavaPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif"); //$NON-NLS-1$
					
					boolean checked= JavaPlugin.getDefault().getPreferenceStore().getBoolean("LexicalSortingAction.isChecked"); //$NON-NLS-1$
					valueChanged(checked, false);
				}
				
				public void run() {
					valueChanged(isChecked(), true);
				}
				
				private void valueChanged(boolean on, boolean store) {
					setChecked(on);
					fOutlineViewer.setSorter(on ? fSorter : null);
					
					setToolTipText(on ? JavaEditorMessages.getString("JavaOutlinePage.Sort.tooltip.checked") : JavaEditorMessages.getString("JavaOutlinePage.Sort.tooltip.unchecked")); //$NON-NLS-2$ //$NON-NLS-1$
					setDescription(on ? JavaEditorMessages.getString("JavaOutlinePage.Sort.description.checked") : JavaEditorMessages.getString("JavaOutlinePage.Sort.description.unchecked")); //$NON-NLS-2$ //$NON-NLS-1$
					
					if (store)
						JavaPlugin.getDefault().getPreferenceStore().setValue("LexicalSortingAction.isChecked", on); //$NON-NLS-1$
				}
			};

			
	private IJavaElement fInput;
	private String fContextMenuID;
	private Menu fMenu;
	private JavaOutlineViewer fOutlineViewer;
	private JavaEditor fEditor;
	
	private MemberFilterActionGroup fMemberFilterActionGroup;
		
	private ListenerList fSelectionChangedListeners= new ListenerList();
	private Hashtable fActions= new Hashtable();
	private ContextMenuGroup[] fActionGroups;
	
	private TogglePresentationAction fTogglePresentation;
	private ToggleTextHoverAction fToggleTextHover;
	private GotoErrorAction fPreviousError;
	private GotoErrorAction fNextError;
	private TextOperationAction fShowJavadoc;
	
	private CompositeActionGroup fStandardActionGroups;
	
	public JavaOutlinePage(String contextMenuID, JavaEditor editor) {
		super();
		
		Assert.isNotNull(editor);
		
		fContextMenuID= contextMenuID;
		fEditor= editor;
		
		fTogglePresentation= new TogglePresentationAction();
		fToggleTextHover= new ToggleTextHoverAction();
		fPreviousError= new GotoErrorAction("PreviousError.", false); //$NON-NLS-1$		
		fNextError= new GotoErrorAction("NextError.", true); //$NON-NLS-1$
		fShowJavadoc= (TextOperationAction) fEditor.getAction("ShowJavaDoc"); //$NON-NLS-1$
		
		fTogglePresentation.setEditor(editor);
		fToggleTextHover.setEditor(editor);
		fPreviousError.setEditor(editor);
		fNextError.setEditor(editor);
	}
	
	/* (non-Javadoc)
	 * Method declared on Page
	 */
	public void init(IPageSite pageSite) {
		super.init(pageSite);
	}
	/*
	 * @see ISelectionProvider#addSelectionChangedListener(ISelectionChangedListener)
	 */
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		if (fOutlineViewer != null)
			fOutlineViewer.addSelectionChangedListener(listener);
		else
			fSelectionChangedListeners.add(listener);
	}
	
	/*
	 * @see ISelectionProvider#removeSelectionChangedListener(ISelectionChangedListener)
	 */
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		if (fOutlineViewer != null)
			fOutlineViewer.removeSelectionChangedListener(listener);
		else
			fSelectionChangedListeners.remove(listener);
	}
	
	/*
	 * @see ISelectionProvider#setSelection(ISelection)
	 */
	public void setSelection(ISelection selection) {
		if (fOutlineViewer != null)
			fOutlineViewer.setSelection(selection);		
	}	
	
	/*
	 * @see ISelectionProvider#getSelection()
	 */
	public ISelection getSelection() {
		if (fOutlineViewer == null)
			return StructuredSelection.EMPTY;
		return fOutlineViewer.getSelection();
	}
	
	private void registerToolbarActions() {
		
		IToolBarManager toolBarManager= getSite().getActionBars().getToolBarManager();
		if (toolBarManager != null) {
			
			Action action= new LexicalSortingAction();
			toolBarManager.add(action);
			
			fMemberFilterActionGroup= new MemberFilterActionGroup(fOutlineViewer, "JavaOutlineViewer"); //$NON-NLS-1$
			fMemberFilterActionGroup.contributeToToolBar(toolBarManager);
		}
	}
	
	/*
	 * @see IPage#createControl
	 */
	public void createControl(Composite parent) {
		
		Tree tree= new Tree(parent, SWT.MULTI);

		StandardJavaUILabelProvider lprovider= new StandardJavaUILabelProvider(
			StandardJavaUILabelProvider.DEFAULT_TEXTFLAGS |  JavaElementLabels.F_APP_TYPE_SIGNATURE,
			StandardJavaUILabelProvider.DEFAULT_IMAGEFLAGS,
			StandardJavaUILabelProvider.getAdornmentProviders(true, new OverrideAdornmentProvider())
		);

		fOutlineViewer= new JavaOutlineViewer(tree);		
		fOutlineViewer.setContentProvider(new ChildrenProvider());
		fOutlineViewer.setLabelProvider(new DecoratingLabelProvider(
			lprovider, PlatformUI.getWorkbench().getDecoratorManager()));
		
		Object[] listeners= fSelectionChangedListeners.getListeners();
		for (int i= 0; i < listeners.length; i++) {
			fSelectionChangedListeners.remove(listeners[i]);
			fOutlineViewer.addSelectionChangedListener((ISelectionChangedListener) listeners[i]);
		}
				
		MenuManager manager= new MenuManager(fContextMenuID, fContextMenuID);
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				contextMenuAboutToShow(manager);
			}
		});
		fMenu= manager.createContextMenu(tree);
		tree.setMenu(fMenu);
		
		getSite().registerContextMenu(JavaPlugin.getDefault().getPluginId() + ".outline", manager, fOutlineViewer); //$NON-NLS-1$
		getSite().setSelectionProvider(fOutlineViewer);

		// we must create the groups after we have set the selection provider to the site
		fStandardActionGroups= new CompositeActionGroup(new ActionGroup[] {
				new OpenActionGroup(this), new ShowActionGroup(this), new GenerateActionGroup(this)});
				
		// register global actions
		IActionBars bars= getSite().getActionBars();
		
		bars.setGlobalActionHandler(RetargetActionIDs.SHOW_PREVIOUS_PROBLEM, fPreviousError);
		bars.setGlobalActionHandler(RetargetActionIDs.SHOW_NEXT_PROBLEM, fNextError);
		bars.setGlobalActionHandler(RetargetActionIDs.SHOW_JAVA_DOC, fShowJavadoc);
		bars.setGlobalActionHandler(IJavaEditorActionConstants.TOGGLE_PRESENTATION, fTogglePresentation);
		bars.setGlobalActionHandler(IJavaEditorActionConstants.TOGGLE_TEXT_HOVER, fToggleTextHover);
		
		fStandardActionGroups.fillActionBars(bars);

		IStatusLineManager statusLineManager= getSite().getActionBars().getStatusLineManager();
		if (statusLineManager != null) {
			StatusBarUpdater updater= new StatusBarUpdater(statusLineManager);
			fOutlineViewer.addSelectionChangedListener(updater);
		}
		
		registerToolbarActions();
				
		fActionGroups= new ContextMenuGroup[] { new GenerateGroup(), new JavaSearchGroup(), new ReorgGroup() };

		ReorgGroup.addGlobalReorgActions(getSite().getActionBars(), fOutlineViewer);
		
		fOutlineViewer.setInput(fInput);	
		fOutlineViewer.getControl().addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				handleKeyReleased(e);
			}
		});
		
		initDragAndDrop();
	}

	public void dispose() {
		
		if (fEditor == null)
			return;
			
		fEditor.outlinePageClosed();
		fEditor= null;
		
		Object[] listeners= fSelectionChangedListeners.getListeners();
		for (int i= 0; i < listeners.length; i++)
			fSelectionChangedListeners.remove(listeners[i]);
		fSelectionChangedListeners= null;
		
		if (fMenu != null && !fMenu.isDisposed()) {
			fMenu.dispose();
			fMenu= null;
		}
		
		super.dispose();
	}
	
	public Control getControl() {
		if (fOutlineViewer != null)
			return fOutlineViewer.getControl();
		return null;
	}
	
	public void setInput(IJavaElement inputElement) {
		fInput= inputElement;	
		if (fOutlineViewer != null)
			fOutlineViewer.setInput(fInput);
	}
		
	public void select(ISourceReference reference) {
		if (fOutlineViewer != null) {
			
			ISelection s= fOutlineViewer.getSelection();
			if (s instanceof IStructuredSelection) {
				IStructuredSelection ss= (IStructuredSelection) s;
				List elements= ss.toList();
				if (!elements.contains(reference)) {
					s= (reference == null ? StructuredSelection.EMPTY : new StructuredSelection(reference));
					fOutlineViewer.setSelection(s, true);
				}
			}
		}
	}
	
	public void setAction(String actionID, IAction action) {
		Assert.isNotNull(actionID);
		if (action == null)
			fActions.remove(actionID);
		else
			fActions.put(actionID, action);
	}
	
	public IAction getAction(String actionID) {
		Assert.isNotNull(actionID);
		return (IAction) fActions.get(actionID);
	}

	/**
	 * Convenience method to add the action installed under the given actionID to the
	 * specified group of the menu.
	 */
	protected void addAction(IMenuManager menu, String group, String actionID) {
		IAction action= getAction(actionID);
		if (action != null) {
			if (action instanceof IUpdate)
				((IUpdate) action).update();
				
			if (action.isEnabled()) {
		 		IMenuManager subMenu= menu.findMenuUsingPath(group);
		 		if (subMenu != null)
		 			subMenu.add(action);
		 		else
		 			menu.appendToGroup(group, action);
			}
		}
	}
	 
	private void addRefactoring(IMenuManager menu){
		MenuManager refactoring= new MenuManager(JavaEditorMessages.getString("JavaOutlinePage.ContextMenu.refactoring.label")); //$NON-NLS-1$
		ContextMenuGroup.add(refactoring, new ContextMenuGroup[] { new RefactoringGroup() }, fOutlineViewer);
		if (!refactoring.isEmpty())
			menu.appendToGroup(IContextMenuConstants.GROUP_REORGANIZE, refactoring);
	}
	
	private void addOpenPerspectiveItem(IMenuManager menu) {
		ISelection s= getSelection();
		if (s.isEmpty() || ! (s instanceof IStructuredSelection))
			return;

		IStructuredSelection selection= (IStructuredSelection)s;
		if (selection.size() != 1)
			return;
			
		Object element= selection.getFirstElement();
		if (!(element instanceof IType))
			return;
		IType[] input= {(IType)element};
		IWorkbenchWindow w= getSite().getWorkbenchWindow();
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, new OpenHierarchyAction(w, input));
	}

	protected void contextMenuAboutToShow(IMenuManager menu) {
		
		JavaPlugin.createStandardGroups(menu);

		if (OrganizeImportsAction.canActionBeAdded(getSelection())) {
			addAction(menu, IContextMenuConstants.GROUP_REORGANIZE, "OrganizeImports"); //$NON-NLS-1$
		}
				
		addAction(menu, IContextMenuConstants.GROUP_OPEN, "OpenImportDeclaration"); //$NON-NLS-1$
		addAction(menu, IContextMenuConstants.GROUP_SHOW, "ShowInPackageView"); //$NON-NLS-1$
				
		ContextMenuGroup.add(menu, fActionGroups, fOutlineViewer);
		// XXX fill the show menu. This is a workaround until we have converted all context menus to the
		// new action groups.
		fStandardActionGroups.get(1).fillContextMenu(menu);
		
		addRefactoring(menu);
		addOpenPerspectiveItem(menu);	
	}
	
	/*
	 * @see Page#setFocus()
	 */
	public void setFocus() {
		if (fOutlineViewer != null)
			fOutlineViewer.getControl().setFocus();
	}
	
	/**
	 * Checkes whether a given Java element is an inner type.
	 */
	private boolean isInnerType(IJavaElement element) {
		
		if (element.getElementType() == IJavaElement.TYPE) {
			IJavaElement parent= element.getParent();
			int type= parent.getElementType();
			return (type != IJavaElement.COMPILATION_UNIT && type != IJavaElement.CLASS_FILE);
		}
		
		return false;		
	}
	
	/**
 	 * Handles key events in viewer.
 	 */
	private void handleKeyReleased(KeyEvent event) {
		
		if (event.stateMask != 0)
			return;
		
		IAction action= null;
		if (event.character == SWT.DEL) {
			action= getAction("DeleteElement"); //$NON-NLS-1$
			if (action instanceof IRefactoringAction){
				//special case - DeleteAction is not a ISelectionChangedListener
				((IRefactoringAction)action).update();
				if (! action.isEnabled())
					return;
			}	
		}
			
		if (action != null && action.isEnabled())
			action.run();
	}
	private void initDragAndDrop() {
		int ops= DND.DROP_COPY | DND.DROP_MOVE;
		Transfer[] transfers= new Transfer[] {
			LocalSelectionTransfer.getInstance()
			};
		
		// Drop Adapter
		TransferDropTargetListener[] dropListeners= new TransferDropTargetListener[] {
			new SelectionTransferDropAdapter(fOutlineViewer)
		};
		fOutlineViewer.addDropSupport(ops, transfers, new DelegatingDropAdapter(dropListeners));
		
		// Drag Adapter
		Control control= fOutlineViewer.getControl();
		TransferDragSourceListener[] dragListeners= new TransferDragSourceListener[] {
			new SelectionTransferDragAdapter(fOutlineViewer)
		};
		DragSource source= new DragSource(control, ops);
		// Note, that the transfer agents are set by the delegating drag adapter itself.
		source.addDragListener(new DelegatingDragAdapter(dragListeners));
	}}