package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.RetargetAction;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.RetargetTextEditorAction;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.structureselection.StructureSelectionAction;
import org.eclipse.jdt.internal.ui.viewsupport.ImageImageDescriptor;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.actions.JdtActionConstants;



public class CompilationUnitEditorActionContributor extends BasicEditorActionContributor {
	
	
			/**
			 * Extends retarget action to make sure that state required for a toolbar actions is
			 * also copied over from the actual action handler.
			 */
			private static class RetargetToolbarAction extends RetargetAction {
				
				private String fDefaultLabel;
				
				public RetargetToolbarAction(ResourceBundle bundle, String prefix, String actionId, boolean checkStyle) {
					super(actionId, getLabel(bundle, prefix));
					fDefaultLabel= getText();
					if (checkStyle)
						setChecked(true);
				}
				
				private static String getLabel(ResourceBundle bundle, String prefix) {
					final String labelKey= "label"; //$NON-NLS-1$
					try {
						return bundle.getString(prefix + labelKey);
					} catch (MissingResourceException e) {
						return labelKey;
					}
				}
				
				/*
				 * @see RetargetAction#propogateChange(PropertyChangeEvent)
				 */
				protected void propogateChange(PropertyChangeEvent event) {
					if (ENABLED.equals(event.getProperty())) {
						Boolean bool= (Boolean) event.getNewValue();
						setEnabled(bool.booleanValue());
					} else if (TEXT.equals(event.getProperty()))
						setText((String) event.getNewValue());
					else if (TOOL_TIP_TEXT.equals(event.getProperty()))
						setToolTipText((String) event.getNewValue());
					else if (CHECKED.equals(event.getProperty())) {
						Boolean bool= (Boolean) event.getNewValue();
						setChecked(bool.booleanValue());
					}
				}
		
				/*
				 * @see RetargetAction#setActionHandler(IAction)
				 */
				protected void setActionHandler(IAction newHandler) {
					
					// default behavior
					super.setActionHandler(newHandler);
					
					// update all the remaining issues
					if (newHandler != null) {
						setText(newHandler.getText());
						setToolTipText(newHandler.getToolTipText());
						setDescription(newHandler.getDescription());				
						setImageDescriptor(newHandler.getImageDescriptor());
						setHoverImageDescriptor(newHandler.getHoverImageDescriptor());
						setDisabledImageDescriptor(newHandler.getDisabledImageDescriptor());
						setMenuCreator(newHandler.getMenuCreator());
						if (newHandler.getStyle() == IAction.AS_CHECK_BOX)
							setChecked(newHandler.isChecked());
					} else {
						setText(fDefaultLabel);
						setToolTipText(fDefaultLabel);
						setDescription(fDefaultLabel);
						setChecked(false);
					}
				}
			};
	
	
	private IWorkbenchPage fPage;
	private List fRetargetToolbarActions= new ArrayList();
	
	private RetargetTextEditorAction fStructureSelectEnclosingAction;
	private RetargetTextEditorAction fStructureSelectNextAction;
	private RetargetTextEditorAction fStructureSelectPreviousAction;
	private RetargetTextEditorAction fStructureSelectHistoryAction;	
	
	protected TogglePresentationAction fTogglePresentation;
	protected ToggleTextHoverAction fToggleTextHover;
	protected GotoErrorAction fPreviousError;
	protected GotoErrorAction fNextError;
	
	
	public CompilationUnitEditorActionContributor() {
		super();
		
		ResourceBundle b= JavaEditorMessages.getResourceBundle();
		
		// retarget actions usually fetched form the active part or editor
		RetargetAction a= new RetargetToolbarAction(b, "TogglePresentation.", IJavaEditorActionConstants.TOGGLE_PRESENTATION, true); //$NON-NLS-1$
		JavaPluginImages.setToolImageDescriptors(a, "segment_edit.gif"); //$NON-NLS-1$
		fRetargetToolbarActions.add(a);
		markAsPartListener(a);
		
		a= new RetargetToolbarAction(b, "ToggleTextHover.", IJavaEditorActionConstants.TOGGLE_TEXT_HOVER, true); //$NON-NLS-1$
		JavaPluginImages.setToolImageDescriptors(a, "jdoc_hover_edit.gif"); //$NON-NLS-1$
		fRetargetToolbarActions.add(a);
		markAsPartListener(a);
		
		a= new RetargetToolbarAction(b, "PreviousError.", ITextEditorActionConstants.PREVIOUS, false); //$NON-NLS-1$
		a.setImageDescriptor(JavaPluginImages.DESC_TOOL_GOTO_PREV_ERROR);
		fRetargetToolbarActions.add(a);
		markAsPartListener(a);
		
		a= new RetargetToolbarAction(b, "NextError.", ITextEditorActionConstants.NEXT, false); //$NON-NLS-1$
		a.setImageDescriptor(JavaPluginImages.DESC_TOOL_GOTO_NEXT_ERROR);
		fRetargetToolbarActions.add(a);
		markAsPartListener(a);
		
		fStructureSelectEnclosingAction= new RetargetTextEditorAction(b, "StructureSelectEnclosing."); //$NON-NLS-1$
		fStructureSelectNextAction= new RetargetTextEditorAction(b, "StructureSelectNext."); //$NON-NLS-1$
		fStructureSelectPreviousAction= new RetargetTextEditorAction(b, "StructureSelectPrevious."); //$NON-NLS-1$
		fStructureSelectHistoryAction= new RetargetTextEditorAction(b, "StructureSelectHistory."); //$NON-NLS-1$
		
		
		// actions that are "contributed" to editors, they are consider belonging to the active editor
		fTogglePresentation= new TogglePresentationAction();
		fToggleTextHover= new ToggleTextHoverAction();
		fPreviousError= new GotoErrorAction("PreviousError.", false); //$NON-NLS-1$
		fPreviousError.setImageDescriptor(JavaPluginImages.DESC_TOOL_GOTO_PREV_ERROR);
		fNextError= new GotoErrorAction("NextError.", true); //$NON-NLS-1$
		fNextError.setImageDescriptor(JavaPluginImages.DESC_TOOL_GOTO_NEXT_ERROR);
	}
	
	public void init(IActionBars bars) {
		super.init(bars);
		// register actions that have a dynamic editor. 
		bars.setGlobalActionHandler(IJavaEditorActionConstants.TOGGLE_PRESENTATION, fTogglePresentation);
		bars.setGlobalActionHandler(IJavaEditorActionConstants.TOGGLE_TEXT_HOVER, fToggleTextHover);
		bars.setGlobalActionHandler(ITextEditorActionConstants.NEXT, fNextError);
		bars.setGlobalActionHandler(ITextEditorActionConstants.PREVIOUS, fPreviousError);
	}

	/*
	 * @see EditorActionBarContributor#contributeToMenu(IMenuManager)
	 */
	public void contributeToMenu(IMenuManager menu) {		
		super.contributeToMenu(menu);
		IMenuManager editMenu= menu.findMenuUsingPath(IWorkbenchActionConstants.M_EDIT);
		if (editMenu != null) {
			MenuManager structureSelection= new MenuManager(JavaEditorMessages.getString("ExpandSelectionMenu.label")); //$NON-NLS-1$
			structureSelection.add(fStructureSelectEnclosingAction);
			structureSelection.add(fStructureSelectNextAction);
			structureSelection.add(fStructureSelectPreviousAction);
			structureSelection.add(fStructureSelectHistoryAction);
			editMenu.appendToGroup(IContextMenuConstants.GROUP_OPEN, structureSelection);
		}
	}
	
	/*
	 * @see EditorActionBarContributor#contributeToToolBar(IToolBarManager)
	 */
	public void contributeToToolBar(IToolBarManager tbm) {
		tbm.add(new Separator());
		Iterator e= fRetargetToolbarActions.iterator();
		while (e.hasNext())
			tbm.add((IAction) e.next());
	}
	
	/*
	 * @see IEditorActionBarContributor#setActiveEditor(IEditorPart)
	 */
	public void setActiveEditor(IEditorPart part) {
		super.setActiveEditor(part);
		
		ITextEditor textEditor= null;
		if (part instanceof ITextEditor)
			textEditor= (ITextEditor) part;
			
		fTogglePresentation.setEditor(textEditor);
		fToggleTextHover.setEditor(textEditor);
		fPreviousError.setEditor(textEditor);
		fNextError.setEditor(textEditor);
		
		fStructureSelectEnclosingAction.setAction(getAction(textEditor, StructureSelectionAction.ENCLOSING));
		fStructureSelectNextAction.setAction(getAction(textEditor, StructureSelectionAction.NEXT));
		fStructureSelectPreviousAction.setAction(getAction(textEditor, StructureSelectionAction.PREVIOUS));
		fStructureSelectHistoryAction.setAction(getAction(textEditor, StructureSelectionAction.HISTORY));		

		IActionBars bars= getActionBars();		
		
		// Source menu.
		bars.setGlobalActionHandler(JdtActionConstants.COMMENT, getAction(textEditor, "Comment")); //$NON-NLS-1$
		bars.setGlobalActionHandler(JdtActionConstants.UNCOMMENT, getAction(textEditor, "Uncomment")); //$NON-NLS-1$
		bars.setGlobalActionHandler(JdtActionConstants.FORMAT, getAction(textEditor, "Format")); //$NON-NLS-1$

		// Navigate menu	
		if (part instanceof CompilationUnitEditor) {
			CompilationUnitEditor cuEditor= (CompilationUnitEditor)part;
			ActionGroup group= cuEditor.getActionGroup();
			if (group != null)
				group.fillActionBars(bars);
		}
	}
}