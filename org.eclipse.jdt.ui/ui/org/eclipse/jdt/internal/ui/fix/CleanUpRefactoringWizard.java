/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.formatter.FormattingContextProperties;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.formatter.IContentFormatterExtension;
import org.eclipse.jface.text.formatter.IFormattingContext;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ContainerCheckedTreeViewer;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaElementSorter;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.formatter.JavaPreview;
import org.eclipse.jdt.internal.ui.text.comment.CommentFormattingContext;

public class CleanUpRefactoringWizard extends RefactoringWizard {
	
	private static final String CLEAN_UP_WIZARD_SETTINGS_SECTION_ID= "CleanUpWizard"; //$NON-NLS-1$
	
	private interface ISelectionChangeListener {
		void selectionChanged(ICleanUp cleanUp, int flag, boolean selection);
	}
	
	private class SelectCUPage extends UserInputWizardPage {

		private ContainerCheckedTreeViewer fTreeViewer;

		public SelectCUPage(String name) {
			super(name);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
		 */
		public void createControl(Composite parent) {
			Composite composite= new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout());
			
			createViewer(composite);
			setControl(composite);
			
			Dialog.applyDialogFont(composite);
		}
		
		private TreeViewer createViewer(Composite parent) {
			fTreeViewer= new ContainerCheckedTreeViewer(parent, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
			GridData gd= new GridData(GridData.FILL_BOTH);
			gd.widthHint= convertWidthInCharsToPixels(40);
			gd.heightHint= convertHeightInCharsToPixels(15);
			fTreeViewer.getTree().setLayoutData(gd);
			fTreeViewer.setLabelProvider(new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_SMALL_ICONS));
			fTreeViewer.setContentProvider(new StandardJavaElementContentProvider());
			fTreeViewer.setSorter(new JavaElementSorter());
			fTreeViewer.addFilter(new ViewerFilter() {

				public boolean select(Viewer viewer, Object parentElement, Object element) {
					if (element instanceof IJavaElement) {
						IJavaElement jElement= (IJavaElement)element;
						return !jElement.isReadOnly();
					} else {
						return false;
					}
				}
				
			});
			IJavaModel create= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
			fTreeViewer.setInput(create);
			checkElements(fTreeViewer, (CleanUpRefactoring)getRefactoring());
			return fTreeViewer;
		}
		
		private void checkElements(CheckboxTreeViewer treeViewer, CleanUpRefactoring refactoring) {
			ICompilationUnit[] compilationUnits= refactoring.getCompilationUnits();
			for (int i= 0; i < compilationUnits.length; i++) {
				ICompilationUnit compilationUnit= compilationUnits[i];
				treeViewer.expandToLevel(compilationUnit, 0);
				treeViewer.setChecked(compilationUnit, true);
			}
			if (compilationUnits.length > 0)
				treeViewer.setSelection(new StructuredSelection(smallestCommonParents(compilationUnits)), true);
		}
		
		private IJavaElement[] smallestCommonParents(IJavaElement[] elements) {
			if (elements.length == 1) {
				return elements;
			} else {
				List parents= new ArrayList();
				boolean hasParents= false;
				
				IJavaElement parent= getParent(elements[0]);
				if (parent == null) {
					parent= elements[0];
				} else {
					hasParents= true;
				}
				parents.add(parent);
				
				for (int i= 1; i < elements.length; i++) {
					parent= getParent(elements[i]);
					if (getParent(elements[i - 1]) != parent) {
						if (parent == null) {
							parent= elements[i];
						} else {
							hasParents= true;
						}
						if (!parents.contains(parent)) {
							parents.add(parent);
						}
					}
				}
				
				IJavaElement[] parentsArray= (IJavaElement[])parents.toArray(new IJavaElement[parents.size()]);
				if (hasParents) {
					return smallestCommonParents(parentsArray);
				}
				return parentsArray;
			}
		}
		
		private IJavaElement getParent(IJavaElement element) {
			if (element instanceof ICompilationUnit) {
				return element.getParent();
			} else if (element instanceof IPackageFragment){
				return element.getParent().getParent();
			} else {
				return element.getParent();
			}
		}

		protected boolean performFinish() {
			initializeRefactoring();
			return super.performFinish();
		}
	
		public IWizardPage getNextPage() {
			initializeRefactoring();
			return super.getNextPage();
		}

		private void initializeRefactoring() {
			CleanUpRefactoring refactoring= (CleanUpRefactoring)getRefactoring();
			refactoring.clearCompilationUnits();
			Object[] checkedElements= fTreeViewer.getCheckedElements();
			for (int i= 0; i < checkedElements.length; i++) {
				if (checkedElements[i] instanceof ICompilationUnit)
					refactoring.addCompilationUnit((ICompilationUnit)checkedElements[i]);
			}
			if (!refactoring.hasCleanUps()) {
				ICleanUp[] cleanUps= createAllCleanUps();
				for (int i= 0; i < cleanUps.length; i++) {
					refactoring.addCleanUp(cleanUps[i]);
				}
			}
		}
		
		private ICleanUp[] createAllCleanUps() {
			IDialogSettings section= getCleanUpWizardSettings();
			
			ICleanUp[] result= new ICleanUp[8];
			result[0]= new CodeStyleCleanUp(section);
			result[1]= new ControlStatementsCleanUp(section);
			result[2]= new UnusedCodeCleanUp(section);
			result[3]= new Java50CleanUp(section);
			result[4]= new StringCleanUp(section);
			result[5]= new PotentialProgrammingProblemsCleanUp(section);
			result[6]= new UnnecessaryCodeCleanUp(section);
			result[7]= new ExpressionsCleanUp(section);
			
			return result;
		}
	}
	
	private class SelectCleanUpPage extends UserInputWizardPage {
		
		private class TabFolderLayout extends Layout {

			protected Point computeSize (Composite composite, int wHint, int hHint, boolean flushCache) {
				if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT)
					return new Point(wHint, hHint);
					
				Control [] children = composite.getChildren ();
				int count = children.length;
				int maxWidth = 0, maxHeight = 0;
				for (int i=0; i<count; i++) {
					Control child = children [i];
					Point pt = child.computeSize (SWT.DEFAULT, SWT.DEFAULT, flushCache);
					maxWidth = Math.max (maxWidth, pt.x);
					maxHeight = Math.max (maxHeight, pt.y);
				}
				
				if (wHint != SWT.DEFAULT)
					maxWidth= wHint;
				if (hHint != SWT.DEFAULT)
					maxHeight= hHint;
				
				return new Point(maxWidth, maxHeight);	
				
			}
			
			protected void layout (Composite composite, boolean flushCache) {
				Rectangle rect= composite.getClientArea();
			
				Control[] children = composite.getChildren();
				for (int i = 0; i < children.length; i++) {
					children[i].setBounds(rect);
				}
			}
		}
		
		private class FlagConfigurationButton {
			
			private final int fFlag;
			private final ICleanUp fCleanUp;
			private final String fLabel;
			private final int fStyle;
			private Button fButton;
			private final List fSelectionChangeListeners;
			
			public FlagConfigurationButton(ICleanUp cleanUp, int flag, String label, int style) {
				fCleanUp= cleanUp;
				fFlag= flag;
				fLabel= label;
				fStyle= style;
				fSelectionChangeListeners= new ArrayList();
			}
			
			public void createButton(Composite parent) {
				fButton= new Button(parent, fStyle);
				fButton.setText(fLabel);
				fButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
				fButton.setSelection(fCleanUp.isFlag(fFlag));
				fButton.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						boolean selection= ((Button)e.getSource()).getSelection();
						fCleanUp.setFlag(fFlag, selection);
						for (Iterator iter= fSelectionChangeListeners.iterator(); iter.hasNext();) {
							ISelectionChangeListener listener= (ISelectionChangeListener)iter.next();
							listener.selectionChanged(fCleanUp, fFlag, selection);	
						}
					}
				});
			}
				
			public Button getButton() {
				return fButton;
			}
			
			public void addSelectionChangeListener(ISelectionChangeListener listener) {
				fSelectionChangeListeners.add(listener);
			}
			
			public void select() {
				fButton.setSelection(true);
			}
			
			public void deselect() {
				fButton.setSelection(false);
			}
			
			public boolean isSelected() {
				return fButton.getSelection();
			}

			public void disable() {
				fButton.setEnabled(false);
			}

			public void enable() {
				fButton.setEnabled(true);
			}

			public int getFlag() {
				return fFlag;
			}

			public ICleanUp getCleanUp() {
				return fCleanUp;
			}

			public void enableFlag() {
				if (!fCleanUp.isFlag(fFlag)) {
					internalSetFlag(true);
				}
			}
			
			public void disableFlag() {
				if (fCleanUp.isFlag(fFlag)) {
					internalSetFlag(false);
				}
			}
			
			public void enableDefault() {
				int defaultFlag= fCleanUp.getDefaultFlag();
				if ((defaultFlag & fFlag) != 0) {
					enableFlag();
				} else {
					disableFlag();
				}
			}
			
			public void selectDefault() {
				int defaultFlag= fCleanUp.getDefaultFlag();
				if ((defaultFlag & fFlag) != 0) {
					select();
				} else {
					deselect();
				}
			}

			private void internalSetFlag(boolean b) {
				fCleanUp.setFlag(fFlag, b);
				for (Iterator iter= fSelectionChangeListeners.iterator(); iter.hasNext();) {
					ISelectionChangeListener listener= (ISelectionChangeListener)iter.next();
					listener.selectionChanged(fCleanUp, fFlag, b);	
				}
			}

			public boolean isRadio() {
				return (fStyle & SWT.RADIO) != 0;
			}
		}
		
		private class FlagConfigurationGroup {

			private final String fLabel;
			private final FlagConfigurationButton[] fButtons;
			private final int fStyle;
			private int fUIFlags;
			private Button fControlButton;

			public FlagConfigurationGroup(String label, FlagConfigurationButton[] buttons, int style, IDialogSettings settings) {
				fLabel= label;
				fButtons= buttons;
				fStyle= style;
				fUIFlags= loadSettings(settings);
			}
			
			public void enableDefaults() {
				boolean hasDefaultSelection= false;
				for (int i= 0; i < fButtons.length; i++) {
					if ((fButtons[i].getCleanUp().getDefaultFlag() & fButtons[i].getFlag()) != 0) {
						hasDefaultSelection= true;
					}
				}
				if (hasDefaultSelection) {
					select();
					for (int i= 0; i < fButtons.length; i++) {
						fButtons[i].enableDefault();
						fButtons[i].selectDefault();
					}
				} else {
					deselect();
					for (int i= 0; i < fButtons.length; i++) {
						fButtons[i].disableFlag();
					}
				}
			}
			
			public void disable() {
				fControlButton.setEnabled(false);
				for (int i= 0; i < fButtons.length; i++) {
					fButtons[i].disable();
				}
			}
			
			public void createButton(Composite parent) {
				final boolean isVertical= (fStyle & SWT.VERTICAL) != 0;
				
				fControlButton= new Button(parent, SWT.CHECK);
				fControlButton.setText(fLabel);
				fControlButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
				
				Composite sub= new Composite(parent, SWT.NONE);
				sub.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
				GridLayout layout= new GridLayout(isVertical?1:fButtons.length, false);
				layout.marginHeight= 0;
				layout.marginWidth= 0;
				sub.setLayout(layout);
				
				for (int i= 0; i < fButtons.length; i++) {
					FlagConfigurationButton config= fButtons[i];
					config.createButton(sub);
					Button button= config.getButton();
					if (i == 0 || isVertical) {//indent
						GridData data= (GridData)button.getLayoutData();
						data.horizontalIndent= 20;
					}
					config.addSelectionChangeListener(new ISelectionChangeListener() {
						public void selectionChanged(ICleanUp cleanUp, int flag, boolean selection) {
							setFlag(flag, selection);
							
							if (hasSelectedButtons()) {
								select();
							} else {
								deselect();
							}
						}
					});
				}
				
				if (hasSelectedButtons()) {
					select();
					initUIFlags();
				} else {
					deselect();
					restoreFromUIFlags();
				}
				
				fControlButton.addSelectionListener(new SelectionAdapter() {

					public void widgetSelected(SelectionEvent e) {
						if (!((Button)e.getSource()).getSelection()) {
							deselect();
							for (int i= 0; i < fButtons.length; i++) {
								fButtons[i].disableFlag();
							}
							for (int i= 0; i < fButtons.length; i++) {
								if (fButtons[i].isSelected())
									setFlag(fButtons[i].getFlag(), true);
							}
						} else {
							select();
							for (int i= 0; i < fButtons.length; i++) {
								if (fButtons[i].isSelected())
									fButtons[i].enableFlag();
							}
						}
					}
					
				});
				
			}
			
			private void restoreFromUIFlags() {
				boolean hasCheck= false;
				for (int i= 0; i < fButtons.length; i++) {
					boolean flag= isFlag(fButtons[i].getFlag());
					if (flag) {
						if ((fStyle & SWT.CHECK) != 0) {
							fButtons[i].select();
						} else if (!hasCheck) {
							fButtons[i].select();
						}
						hasCheck= true;
					} else {
						fButtons[i].deselect();
					}
				}
				if ((fStyle & SWT.RADIO) != 0 && !hasCheck) {
					fButtons[0].select();
				}
			}

			private void initUIFlags() {
				for (int i= 0; i < fButtons.length; i++) {
					setFlag(fButtons[i].getFlag(), fButtons[i].isSelected());
				}
			}

			private void deselect() {
				fControlButton.setSelection(false);
				for (int j= 0; j < fButtons.length; j++) {
					fButtons[j].disable();
				}
			}

			private void select() {
				fControlButton.setSelection(true);
				for (int j= 0; j < fButtons.length; j++) {
					fButtons[j].enable();
				}
			}

			private boolean hasSelectedButtons() {
				for (int j= 0; j < fButtons.length; j++) {
					if (fButtons[j].getCleanUp().isFlag(fButtons[j].getFlag()))
						return true;
				}
				return false;
			}

			private void setFlag(int flag, boolean b) {
				if (!isFlag(flag) && b) {
					fUIFlags |= flag;
				} else if (isFlag(flag) && !b) {
					fUIFlags &= ~flag;
				}
			}
			
			private boolean isFlag(int flag) {
				return (fUIFlags & flag) != 0;
			}

			public void saveSettings(IDialogSettings settings) {
				String sectionName= getSectionName();
				IDialogSettings section= settings.getSection(sectionName);
				if (section == null)
					section= settings.addNewSection(sectionName);

				section.put("uiFlags", fUIFlags); //$NON-NLS-1$
			}
			
			private int loadSettings(IDialogSettings settings) {
				String sectionName= getSectionName();
				IDialogSettings section= settings.getSection(sectionName);
				if (section == null)
					return 0;
				
				return section.getInt("uiFlags"); //$NON-NLS-1$
			}
			
			private String getSectionName() {
				StringBuffer buf= new StringBuffer(fLabel);
				for (int i=buf.length()-1;i>=0;i--) {
					if (buf.charAt(i) == '&' || buf.charAt(i) == ' ')
						buf.deleteCharAt(i);
				}
				return buf.toString();
			}
		}
		
		private class CleanUpPreview extends JavaPreview {

			private final ICleanUp[] fPreviewCleanUps;
			private boolean fUpdateBlocked;

			public CleanUpPreview(Composite parent, Map map, ICleanUp[] cleanUps) {
				super(map, parent);
				fPreviewCleanUps= cleanUps;
				fUpdateBlocked= false;
			}

			/**
			 * {@inheritDoc}
			 */
			protected void doFormatPreview() {
			
				StringBuffer buf= new StringBuffer();
				for (int i= 0; i < fPreviewCleanUps.length; i++) {
					buf.append(fPreviewCleanUps[i].getPreview());
					buf.append("\n"); //$NON-NLS-1$
				}
				format(buf.toString());
			}
			
			private void format(String text) {
		        if (text == null) {
		            fPreviewDocument.set(""); //$NON-NLS-1$
		            return;
		        }
		        fPreviewDocument.set(text);
				
				fSourceViewer.setRedraw(false);
				final IFormattingContext context = new CommentFormattingContext();
				try {
					final IContentFormatter formatter =	fViewerConfiguration.getContentFormatter(fSourceViewer);
					if (formatter instanceof IContentFormatterExtension) {
						final IContentFormatterExtension extension = (IContentFormatterExtension) formatter;
						context.setProperty(FormattingContextProperties.CONTEXT_PREFERENCES, fWorkingValues);
						context.setProperty(FormattingContextProperties.CONTEXT_DOCUMENT, Boolean.valueOf(true));
						extension.format(fPreviewDocument, context);
					} else
						formatter.format(fPreviewDocument, new Region(0, fPreviewDocument.getLength()));
				} catch (Exception e) {
					final IStatus status= new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IJavaStatusConstants.INTERNAL_ERROR, 
						MultiFixMessages.CleanUpRefactoringWizard_formatterException_errorMessage, e); 
					JavaPlugin.log(status);
				} finally {
				    context.dispose();
				    fSourceViewer.setRedraw(true);
				}
			}

			public void resumeUpdate() {
				fUpdateBlocked= false;
			}

			public void blockUpdate() {
				fUpdateBlocked= true;
			}

			public boolean isUpdateSuspended() {
				return fUpdateBlocked;
			}
		}
		
		private ICleanUp[] fCleanUps;
		private List fConfigurationGroups;
		private int fTotalCleanUpsCount, fSelectedCleanUpsCount;
		private Label fStatusLine;
		
		public SelectCleanUpPage(String name) {
			super(name);
			fConfigurationGroups= new ArrayList();
		}
		
		public void createControl(Composite parent) {
			Composite composite= new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout(1, true));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			TabFolder tabFolder= new TabFolder(composite, SWT.NONE);
			tabFolder.setLayout(new TabFolderLayout());
			tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			fStatusLine= new Label(composite, SWT.NONE);
			fStatusLine.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false));
			
			createGroups(tabFolder);
			
			fStatusLine.setText(Messages.format(MultiFixMessages.CleanUpRefactoringWizard_statusLineText, new Object[] {new Integer(fSelectedCleanUpsCount), new Integer(fTotalCleanUpsCount)}));
			
			setControl(composite);
			Dialog.applyDialogFont(composite);

			PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.SELECT_CLEAN_UPS_PAGE);
		}
		
		private void createGroups(TabFolder parent) {
			CleanUpRefactoring refactoring= (CleanUpRefactoring)getRefactoring();
			IJavaProject[] projects= refactoring.getProjects();
			final IJavaProject project;
			if (projects.length == 1) {
				project= projects[0];
			} else {
				project= null;
			}
			
			IDialogSettings section= getCleanUpWizardSettings();
			
			fCleanUps= new ICleanUp[8];
			
			ScrolledComposite codeStyleTab= createTab(parent, MultiFixMessages.CleanUpRefactoringWizard_CodeStyleSection_description);
			Composite codeStyle= fillCodeStyleTab(codeStyleTab, project, section);
			codeStyleTab.setContent(codeStyle);
			codeStyleTab.setMinSize(codeStyle.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			
			ScrolledComposite unnecessaryCodeTab= createTab(parent, MultiFixMessages.CleanUpRefactoringWizard_UnnecessaryCode_tabLabel);
			Composite unnecessaryCode= fillUnnecessaryCodeTab(unnecessaryCodeTab, project, section);
			unnecessaryCodeTab.setContent(unnecessaryCode);
			unnecessaryCodeTab.setMinSize(unnecessaryCode.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			
			ScrolledComposite missingCodeTab= createTab(parent, MultiFixMessages.CleanUpRefactoringWizard_MissingCode_tabLabel);
			Composite missingCode= fillMissingCodeTab(missingCodeTab, project, section);
			missingCodeTab.setContent(missingCode);
			missingCodeTab.setMinSize(missingCode.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		}
		
		private Composite fillCodeStyleTab(Composite parent, final IJavaProject project, IDialogSettings settings) {
			Composite composite = new SashForm(parent, SWT.HORIZONTAL);
			composite.setLayout(new GridLayout(2, false));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			Composite left= new Composite(composite, SWT.NONE);
			left.setLayout(new GridLayout(1, false));
			left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
			
			Composite groups= new Composite(left, SWT.NONE);
			groups.setLayout(new GridLayout(1, false));
			groups.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
			
			//Member accesses group
			CodeStyleCleanUp codeStyleCleanUp= new CodeStyleCleanUp(settings);
			Composite group= createGroup(groups, MultiFixMessages.CleanUpRefactoringWizard_memberAccesses_sectionDescription);
			
			FlagConfigurationButton addThisField= new FlagConfigurationButton(codeStyleCleanUp, CodeStyleCleanUp.QUALIFY_FIELD_ACCESS, MultiFixMessages.CleanUpRefactoringWizard_qualifyNonStaticField_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton addThisMethod= new FlagConfigurationButton(codeStyleCleanUp, CodeStyleCleanUp.QUALIFY_METHOD_ACCESS, MultiFixMessages.CleanUpRefactoringWizard_qualifyNonStaticMethod_checkBoxLabel, SWT.CHECK);
			FlagConfigurationGroup  addThisGroup= new FlagConfigurationGroup(MultiFixMessages.CodeStyleCleanUp_useThis_checkBoxLabel, new FlagConfigurationButton[] {addThisField, addThisMethod}, SWT.CHECK | SWT.VERTICAL, settings);
			addThisGroup.createButton(group);
			FlagConfigurationButton nonStatic= new FlagConfigurationButton(codeStyleCleanUp, CodeStyleCleanUp.CHANGE_NON_STATIC_ACCESS_TO_STATIC, MultiFixMessages.CodeStyleCleanUp_changeNonStatic_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton indirect= new FlagConfigurationButton(codeStyleCleanUp, CodeStyleCleanUp.CHANGE_INDIRECT_STATIC_ACCESS_TO_DIRECT, MultiFixMessages.CodeStyleCleanUp_changeIndirect_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton qualifyStatic= new FlagConfigurationButton(codeStyleCleanUp, CodeStyleCleanUp.QUALIFY_STATIC_FIELD_ACCESS, MultiFixMessages.CodeStyleCleanUp_addStaticQualifier_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton qualifyStaticMethod= new FlagConfigurationButton(codeStyleCleanUp, CodeStyleCleanUp.QUALIFY_STATIC_METHOD_ACCESS, MultiFixMessages.CleanUpRefactoringWizard_qualifyStaticMethod_checkBoxLabel, SWT.CHECK);
			FlagConfigurationGroup staticGroup= new FlagConfigurationGroup(MultiFixMessages.CodeStyleCleanUp_useDeclaring_checkBoxLabel, new FlagConfigurationButton[] {qualifyStatic, qualifyStaticMethod, indirect, nonStatic}, SWT.CHECK | SWT.VERTICAL, settings);
			staticGroup.createButton(group);

			//Control statements group
			ControlStatementsCleanUp controlStatementsCleanUp= new ControlStatementsCleanUp(settings);
			group= createGroup(groups, MultiFixMessages.CleanUpRefactoringWizard_controlStatements_sectionDescription);
			
			FlagConfigurationButton addBlock= new FlagConfigurationButton(controlStatementsCleanUp, ControlStatementsCleanUp.ADD_BLOCK_TO_CONTROL_STATEMENTS, MultiFixMessages.ControlStatementsCleanUp_always_checkBoxLabel, SWT.RADIO);
			FlagConfigurationButton removeBlock= new FlagConfigurationButton(controlStatementsCleanUp, ControlStatementsCleanUp.REMOVE_UNNECESSARY_BLOCKS, MultiFixMessages.ControlStatementsCleanUp_removeIfPossible_checkBoxLabel, SWT.RADIO);
			FlagConfigurationGroup blockGroup= new FlagConfigurationGroup(MultiFixMessages.ControlStatementsCleanUp_useBlocks_checkBoxLabel, new FlagConfigurationButton[] {addBlock, removeBlock}, SWT.RADIO | SWT.HORIZONTAL, settings);
			blockGroup.createButton(group);
		
			FlagConfigurationButton convertLoop= new FlagConfigurationButton(controlStatementsCleanUp, ControlStatementsCleanUp.CONVERT_FOR_LOOP_TO_ENHANCED_FOR_LOOP, MultiFixMessages.ControlStatementsCleanUp_convertLoops_checkBoxLabel, SWT.CHECK);
			convertLoop.createButton(group);
			
			//Expressions group
			ExpressionsCleanUp expressionsCleanUp= new ExpressionsCleanUp(settings);
			group= createGroup(groups, MultiFixMessages.CleanUpRefactoringWizard_expressions_sectionDescription);
			
			FlagConfigurationButton addParanoic= new FlagConfigurationButton(expressionsCleanUp, ExpressionsCleanUp.ADD_PARANOIC_PARENTHESIS, MultiFixMessages.ExpressionsCleanUp_addParanoiac_checkBoxLabel, SWT.RADIO);
			FlagConfigurationButton removeParanoic= new FlagConfigurationButton(expressionsCleanUp, ExpressionsCleanUp.REMOVE_UNNECESSARY_PARENTHESIS, MultiFixMessages.ExpressionsCleanUp_removeUnnecessary_checkBoxLabel, SWT.RADIO);
			FlagConfigurationGroup parenthesisGroup= new FlagConfigurationGroup(MultiFixMessages.ExpressionsCleanUp_parenthesisAroundConditions_checkBoxLabel, new FlagConfigurationButton[] {addParanoic, removeParanoic}, SWT.RADIO | SWT.HORIZONTAL, settings);
			parenthesisGroup.createButton(group);
			
			FlagConfigurationButton[] flagConfigurationButtons= new FlagConfigurationButton[] {addThisField, addThisMethod, nonStatic, indirect, qualifyStatic, qualifyStaticMethod, addBlock, removeBlock, convertLoop, addParanoic, removeParanoic};
			
			CleanUpPreview preview= addPreview(composite, new ICleanUp[] {codeStyleCleanUp, controlStatementsCleanUp, expressionsCleanUp}, flagConfigurationButtons);
			
			if (project != null && !JavaModelUtil.is50OrHigher(project)) {
				convertLoop.disable();
				convertLoop.deselect();
				addEnableButtonsGroup(left, flagConfigurationButtons, new FlagConfigurationGroup[] {blockGroup, parenthesisGroup}, new FlagConfigurationButton[] {convertLoop}, preview);
			} else {
				addEnableButtonsGroup(left, flagConfigurationButtons, new FlagConfigurationGroup[] {blockGroup, parenthesisGroup}, new FlagConfigurationButton[0], preview);
			}
			
			addSelectionCounter(flagConfigurationButtons);
			
			
			fCleanUps[0]= codeStyleCleanUp;
			fCleanUps[1]= controlStatementsCleanUp;
			fCleanUps[2]= expressionsCleanUp;
			
			fConfigurationGroups.add(addThisGroup);
			fConfigurationGroups.add(staticGroup);
			fConfigurationGroups.add(blockGroup);
			fConfigurationGroups.add(parenthesisGroup);
			
			return composite;
		}
		
		private Composite fillUnnecessaryCodeTab(ScrolledComposite parent, final IJavaProject project, IDialogSettings section) {
			Composite composite= new SashForm(parent, SWT.HORIZONTAL);
			composite.setLayout(new GridLayout(1, false));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			Composite left= new Composite(composite, SWT.NONE);
			left.setLayout(new GridLayout(1, false));
			left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
			
			Composite groups= new Composite(left, SWT.NONE);
			groups.setLayout(new GridLayout(1, false));
			groups.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
			
			//Unused code group
			UnusedCodeCleanUp unusedCodeCleanUp= new UnusedCodeCleanUp(section);
			Composite group= createGroup(groups, MultiFixMessages.CleanUpRefactoringWizard_UnusedCodeSection_description);
			
			FlagConfigurationButton imports= new FlagConfigurationButton(unusedCodeCleanUp, UnusedCodeCleanUp.REMOVE_UNUSED_IMPORTS, MultiFixMessages.UnusedCodeCleanUp_unusedImports_checkBoxLabel, SWT.CHECK);
			imports.createButton(group);
			FlagConfigurationButton types= new FlagConfigurationButton(unusedCodeCleanUp, UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_TYPES, MultiFixMessages.UnusedCodeCleanUp_unusedTypes_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton contructors= new FlagConfigurationButton(unusedCodeCleanUp, UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_CONSTRUCTORS, MultiFixMessages.UnusedCodeCleanUp_unusedConstructors_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton methods= new FlagConfigurationButton(unusedCodeCleanUp, UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_METHODS, MultiFixMessages.UnusedCodeCleanUp_unusedMethods_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton fields= new FlagConfigurationButton(unusedCodeCleanUp, UnusedCodeCleanUp.REMOVE_UNUSED_PRIVATE_FIELDS, MultiFixMessages.UnusedCodeCleanUp_unusedFields_checkBoxLabel, SWT.CHECK);
			FlagConfigurationGroup membersGroup= new FlagConfigurationGroup(MultiFixMessages.UnusedCodeCleanUp_unusedPrivateMembers_checkBoxLabel, new FlagConfigurationButton[] {types, contructors, fields, methods}, SWT.CHECK | SWT.HORIZONTAL, section);
			membersGroup.createButton(group);
			FlagConfigurationButton locals= new FlagConfigurationButton(unusedCodeCleanUp, UnusedCodeCleanUp.REMOVE_UNUSED_LOCAL_VARIABLES, MultiFixMessages.UnusedCodeCleanUp_unusedLocalVariables_checkBoxLabel, SWT.CHECK);
			locals.createButton(group);

			//Unnecessary code group
			UnnecessaryCodeCleanUp unnecessaryCodeCleanUp= new UnnecessaryCodeCleanUp(section);
			StringCleanUp stringCleanUp= new StringCleanUp(section);
			group= createGroup(groups, MultiFixMessages.CleanUpRefactoringWizard_UnnecessaryCode_section );
			
			FlagConfigurationButton casts= new FlagConfigurationButton(unnecessaryCodeCleanUp, UnnecessaryCodeCleanUp.REMOVE_UNUSED_CAST, MultiFixMessages.UnusedCodeCleanUp_unnecessaryCasts_checkBoxLabel, SWT.CHECK);
			casts.createButton(group);
			FlagConfigurationButton nlsTags= new FlagConfigurationButton(stringCleanUp, StringCleanUp.REMOVE_UNNECESSARY_NLS_TAG, MultiFixMessages.StringCleanUp_RemoveNLSTag_label, SWT.CHECK);
			nlsTags.createButton(group);
			
			FlagConfigurationButton[] flagConfigurationButtons= new FlagConfigurationButton[] {imports, types, contructors, methods, locals, fields, casts, nlsTags};
			
			CleanUpPreview preview= addPreview(composite, new ICleanUp[] {unusedCodeCleanUp, unnecessaryCodeCleanUp, stringCleanUp}, flagConfigurationButtons);
			
			addEnableButtonsGroup(left, flagConfigurationButtons, new FlagConfigurationGroup[0], new FlagConfigurationButton[0], preview);
			
			addSelectionCounter(flagConfigurationButtons);

			fCleanUps[3]= unusedCodeCleanUp;
			fCleanUps[6]= unnecessaryCodeCleanUp;
			fCleanUps[7]= stringCleanUp;
			
			fConfigurationGroups.add(membersGroup);
			
			return composite;
		}

		private Composite fillMissingCodeTab(ScrolledComposite parent, final IJavaProject project, IDialogSettings section) {
			Composite composite= new SashForm(parent, SWT.HORIZONTAL);
			composite.setLayout(new GridLayout(2, false));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			Composite left= new Composite(composite, SWT.NONE);
			left.setLayout(new GridLayout(1, false));
			left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
			
			Composite groups= new Composite(left, SWT.NONE);
			groups.setLayout(new GridLayout(1, false));
			groups.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
			
			//Java50Fix Group
			Java50CleanUp java50CleanUp= new Java50CleanUp(section);
			Composite group= createGroup(groups, MultiFixMessages.CleanUpRefactoringWizard_Annotations_sectionName);
			
			FlagConfigurationButton override= new FlagConfigurationButton(java50CleanUp, Java50CleanUp.ADD_OVERRIDE_ANNOATION, MultiFixMessages.Java50CleanUp_override_checkBoxLabel, SWT.CHECK);
			FlagConfigurationButton deprecated= new FlagConfigurationButton(java50CleanUp, Java50CleanUp.ADD_DEPRECATED_ANNOTATION, MultiFixMessages.Java50CleanUp_deprecated_checkBoxLabel, SWT.CHECK);
			FlagConfigurationGroup annotations= new FlagConfigurationGroup(MultiFixMessages.Java50CleanUp_addMissingAnnotations_checkBoxLabel, new FlagConfigurationButton[] {override, deprecated}, SWT.CHECK | SWT.VERTICAL, section);
			annotations.createButton(group);

			//Potential Programming Problems Group
			PotentialProgrammingProblemsCleanUp potentialProgrammingProblemsCleanUp= new PotentialProgrammingProblemsCleanUp(section);
			group= createGroup(groups, MultiFixMessages.CleanUpRefactoringWizard_PotentialProgrammingProblems_description);
			
			FlagConfigurationButton hash= new FlagConfigurationButton(potentialProgrammingProblemsCleanUp, PotentialProgrammingProblemsCleanUp.ADD_CALCULATED_SERIAL_VERSION_ID, MultiFixMessages.PotentialProgrammingProblemsCleanUp_Generated_radioButton_name, SWT.RADIO);
			FlagConfigurationButton defaultId= new FlagConfigurationButton(potentialProgrammingProblemsCleanUp, PotentialProgrammingProblemsCleanUp.ADD_DEFAULT_SERIAL_VERSION_ID, MultiFixMessages.PotentialProgrammingProblemsCleanUp_Default_radioButton_name, SWT.RADIO);
			FlagConfigurationGroup serial= new FlagConfigurationGroup(MultiFixMessages.PotentialProgrammingProblemsCleanUp_AddSerialId_section_name, new FlagConfigurationButton[] {hash, defaultId}, SWT.RADIO | SWT.HORIZONTAL, section);
			serial.createButton(group);
			
			FlagConfigurationButton[] flagConfigurationButtons= new FlagConfigurationButton[] {override, deprecated, hash, defaultId};
			
			CleanUpPreview preview= addPreview(composite, new ICleanUp[] {java50CleanUp, potentialProgrammingProblemsCleanUp}, flagConfigurationButtons);
			
			if (project != null && !JavaModelUtil.is50OrHigher(project)) {
				override.deselect();
				override.disableFlag();
				deprecated.deselect();
				deprecated.disableFlag();
				annotations.disable();
				addEnableButtonsGroup(left, flagConfigurationButtons, new FlagConfigurationGroup[] {serial}, new FlagConfigurationButton[] {override, deprecated}, preview);
			} else {
				addEnableButtonsGroup(left, flagConfigurationButtons, new FlagConfigurationGroup[] {serial}, new FlagConfigurationButton[0], preview);
			}
			
			addSelectionCounter(flagConfigurationButtons);
			
			fCleanUps[4]= java50CleanUp;
			fCleanUps[5]= potentialProgrammingProblemsCleanUp;
			
			fConfigurationGroups.add(annotations);
			fConfigurationGroups.add(serial);
			
			return composite;
		}
		
		private CleanUpPreview addPreview(Composite parent, ICleanUp[] cleanUps, FlagConfigurationButton[] flagConfigurationButtons) {
			Composite composite= new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout(1, true));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			Label label= new Label(composite, SWT.NONE);
			composite.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
			label.setText(MultiFixMessages.CleanUpRefactoringWizard_previewLabel_text);
			
			IJavaProject javaProject= ((CleanUpRefactoring)getRefactoring()).getProjects()[0];
			final CleanUpPreview preview= new CleanUpPreview(composite, javaProject.getOptions(true), cleanUps);
			
			GridData gd= new GridData(SWT.FILL, SWT.FILL, true, true);
			gd.widthHint= 0;
			gd.heightHint=0;
			preview.getControl().setLayoutData(gd);
			
			preview.update();
			for (int i= 0; i < flagConfigurationButtons.length; i++) {
				flagConfigurationButtons[i].addSelectionChangeListener(new ISelectionChangeListener() {

					public void selectionChanged(ICleanUp cleanUp, int flag, boolean selection) {
						if (!preview.isUpdateSuspended())
							preview.update();
					}
				});
			}
			return preview;
		}
		
		private void addSelectionCounter(FlagConfigurationButton[] configs) {
			for (int i= 0; i < configs.length; i++) {
				FlagConfigurationButton config= configs[i];
				if (config.getCleanUp().isFlag(config.getFlag()))
					fSelectedCleanUpsCount++;
				config.addSelectionChangeListener(new ISelectionChangeListener() {
					public void selectionChanged(ICleanUp cleanUp, int flag, boolean selection) {
						if (selection) {
							fSelectedCleanUpsCount++;
						} else {
							fSelectedCleanUpsCount--;
						}
						fStatusLine.setText(Messages.format(MultiFixMessages.CleanUpRefactoringWizard_statusLineText, new Object[] {new Integer(fSelectedCleanUpsCount), new Integer(fTotalCleanUpsCount)}));
					}
					
				});
			}
			fTotalCleanUpsCount+=configs.length;
		}

		private void addEnableButtonsGroup(Composite parent, final FlagConfigurationButton[] configButtons, final FlagConfigurationGroup[] radioGroups, final FlagConfigurationButton[] disabledButtons, final CleanUpPreview preview) {
			Composite down= new Composite(parent, SWT.NONE);
			down.setLayout(new GridLayout(2, false));
			down.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			
			Composite space= new Composite(down, SWT.NONE);
			space.setLayout(new GridLayout(1, true));
			space.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			Composite buttons= new Composite(down, SWT.NONE);
			buttons.setLayout(new GridLayout(3, true));
			buttons.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
			
			Button all= new Button(buttons, SWT.PUSH);
			all.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			all.setText(MultiFixMessages.CleanUpRefactoringWizard_EnableAllButton_label);
			all.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					preview.blockUpdate();
					for (int i= 0; i < configButtons.length; i++) {
						FlagConfigurationButton config= configButtons[i];
						boolean isDisabled= false;
						for (int j= 0; j < disabledButtons.length; j++) {
							if (config == disabledButtons[j])
								isDisabled= true;
						}
						if (!isDisabled) {
							if (config.isRadio()) {
								if (config.isSelected()) {
									config.enableFlag();
								}
							} else {
								config.enableFlag();
								config.select();
							}
						}
					}
					preview.resumeUpdate();
					preview.update();
				}	
			});
			
			Button none= new Button(buttons, SWT.PUSH);
			none.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			none.setText(MultiFixMessages.CleanUpRefactoringWizard_DisableAllButton_label);
			none.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					preview.blockUpdate();
					for (int i= 0; i < configButtons.length; i++) {
						FlagConfigurationButton config= configButtons[i];
						config.disableFlag();
						if (!config.isRadio()) {
							config.deselect();
						}
					}
					preview.resumeUpdate();
					preview.update();
				}	
			});
			
			Button def= new Button(buttons, SWT.PUSH);
			def.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			def.setText(MultiFixMessages.CleanUpRefactoringWizard_EnableDefaultsButton_label);
			def.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					preview.blockUpdate();
					for (int i= 0; i < configButtons.length; i++) {
						FlagConfigurationButton config= configButtons[i];
						if (!config.isRadio()) {
							boolean isDisabled= false;
							for (int j= 0; j < disabledButtons.length; j++) {
								if (config == disabledButtons[j])
									isDisabled= true;
							}
							if (!isDisabled) {
								config.enableDefault();
								config.selectDefault();
							}
						}
					}
					for (int i= 0; i < radioGroups.length; i++) {
						FlagConfigurationGroup group= radioGroups[i];
						group.enableDefaults();
					}
					preview.resumeUpdate();
					preview.update();
				}	
			});
		}

		private ScrolledComposite createTab(TabFolder parent, String label) {
			TabItem csTab= new TabItem(parent, SWT.NONE);
			csTab.setText(label);
			
			ScrolledComposite scrolled= new ScrolledComposite(parent, SWT.H_SCROLL | SWT.V_SCROLL);
			scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			scrolled.setLayout(new GridLayout(1, false));
			scrolled.setExpandHorizontal(true);
			scrolled.setExpandVertical(true);			
			csTab.setControl(scrolled);
			return scrolled;
		}

		private Composite createGroup(Composite parent, String description) {
			
			Group group= new Group(parent, SWT.NONE);
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			group.setLayout(new GridLayout(1, true));
			group.setText(description);
			
			return group;
		}

		protected boolean performFinish() {
			initializeRefactoring();
			storeSettings();
			return super.performFinish();
		}
	
		public IWizardPage getNextPage() {
			initializeRefactoring();
			storeSettings();
			return super.getNextPage();
		}
		
		private void storeSettings() {
			if (fCleanUps != null) {
				IDialogSettings settings= getCleanUpWizardSettings();
				for (int i= 0; i < fCleanUps.length; i++) {
					fCleanUps[i].saveSettings(settings);
				}
				for (Iterator iter= fConfigurationGroups.iterator(); iter.hasNext();) {
					FlagConfigurationGroup group= (FlagConfigurationGroup)iter.next();
					group.saveSettings(settings);
				}
			}
		}

		private void initializeRefactoring() {
			if (fCleanUps != null) {
				CleanUpRefactoring refactoring= (CleanUpRefactoring)getRefactoring();
				refactoring.clearCleanUps();
				for (int i= 0; i < fCleanUps.length; i++) {
					refactoring.addCleanUp(fCleanUps[i]);
				}
			}
		}
		
	}
	
	private final boolean fShowCUPage;
	private final boolean fShowCleanUpPage;
	
	public CleanUpRefactoringWizard(CleanUpRefactoring refactoring, int flags, boolean showCUPage, boolean showCleanUpPage) {
		super(refactoring, flags);
		fShowCUPage= showCUPage;
		fShowCleanUpPage= showCleanUpPage;
		setDefaultPageTitle(MultiFixMessages.CleanUpRefactoringWizard_PageTitle);
		setWindowTitle(MultiFixMessages.CleanUpRefactoringWizard_WindowTitle);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	protected void addUserInputPages() {
		if (fShowCUPage) {
			SelectCUPage selectCUPage= new SelectCUPage(MultiFixMessages.CleanUpRefactoringWizard_SelectCompilationUnitsPage_name);
			if (fShowCleanUpPage) {
				selectCUPage.setMessage(MultiFixMessages.CleanUpRefactoringWizard_SelectCompilationUnitsPage_message);
			} else {
				ICleanUp[] cleanUps= ((CleanUpRefactoring)getRefactoring()).getCleanUps();
				if (cleanUps.length == 1) {
					ICleanUp cleanUp= cleanUps[0];
					String[] descriptions= cleanUp.getDescriptions();
					if (descriptions.length == 1) {
						selectCUPage.setMessage(Messages.format(MultiFixMessages.CleanUpRefactoringWizard_SelectCompilationUnitsPage_preSingleSelect_message, descriptions[0]));
					} else {
						selectCUPage.setMessage(MultiFixMessages.CleanUpRefactoringWizard_SelectCompilationUnitsPage_preSelect_message);
					}
				} else {
					selectCUPage.setMessage(MultiFixMessages.CleanUpRefactoringWizard_SelectCompilationUnitsPage_preSelect_message);
				}
			}
			addPage(selectCUPage);
		}
		
		if (fShowCleanUpPage){
			SelectCleanUpPage selectSolverPage= new SelectCleanUpPage(MultiFixMessages.CleanUpRefactoringWizard_SelectCleanUpsPage_name);
			if (fShowCUPage) {
				selectSolverPage.setMessage(MultiFixMessages.CleanUpRefactoringWizard_SelectCleanUpsPage_message);
			} else {
				ICompilationUnit[] compilationUnits= ((CleanUpRefactoring)getRefactoring()).getCompilationUnits();
				if (compilationUnits.length == 1) {
					String label= JavaElementLabels.getElementLabel(compilationUnits[0], JavaElementLabels.ALL_DEFAULT);
					selectSolverPage.setMessage(Messages.format(MultiFixMessages.CleanUpRefactoringWizard_SelectCleanUpsPage_preSingleSelect_message, label));
				} else {
					selectSolverPage.setMessage(MultiFixMessages.CleanUpRefactoringWizard_SelectCleanUpsPage_message);
				}
			}
			addPage(selectSolverPage);
		}
	}
	
	private static IDialogSettings getCleanUpWizardSettings() {
		IDialogSettings settings= JavaPlugin.getDefault().getDialogSettings();
		IDialogSettings section= settings.getSection(CLEAN_UP_WIZARD_SETTINGS_SECTION_ID);
		if (section == null) {
			section= settings.addNewSection(CLEAN_UP_WIZARD_SETTINGS_SECTION_ID);
		}
		return section;
	}


}
