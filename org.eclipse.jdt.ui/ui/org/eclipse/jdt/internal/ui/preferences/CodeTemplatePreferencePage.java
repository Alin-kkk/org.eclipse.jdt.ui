package org.eclipse.jdt.internal.ui.preferences;

import java.io.File;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.corext.template.CodeTemplates;
import org.eclipse.jdt.internal.corext.template.Template;
import org.eclipse.jdt.internal.corext.template.TemplateMessages;
import org.eclipse.jdt.internal.corext.template.TemplateSet;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.template.TemplateContentProvider;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

public class CodeTemplatePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	
	private static class CodeTemplateLabelProvider extends LabelProvider implements ITableLabelProvider {
	
		public Image getColumnImage(Object element, int columnIndex) {
			return null;
		}
	
		public String getColumnText(Object element, int columnIndex) {
			Template template = (Template) element;
			
			switch (columnIndex) {
				case 0:
					return template.getName();
				case 1:
					return template.getDescription();
				default:
					return null;
			}
		}
	}

	// preference store keys
	private static final String PREF_FORMAT_TEMPLATES= PreferenceConstants.TEMPLATES_USE_CODEFORMATTER;

	private final String[] CONTEXTS= new String[] { "codetemplate" };

	private CodeTemplates fTemplates;

	private TableViewer fTableViewer;
	//private Button fAddButton; //changed
	private Button fEditButton;
	private Button fImportButton;
	private Button fExportButton;
	private Button fExportAllButton;
	//private Button fRemoveButton; //changed
	//private Button fEnableAllButton;
	//private Button fDisableAllButton;

	private SourceViewer fPatternViewer;
	//private Button fFormatButton;
	
	public CodeTemplatePreferencePage() {
		super();
		
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		setDescription(TemplateMessages.getString("CodeTemplatePreferencePage.message")); //$NON-NLS-1$

		fTemplates= CodeTemplates.getInstance();
	}

	/*
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite ancestor) {	
		Composite parent= new Composite(ancestor, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		parent.setLayout(layout);				

        Composite innerParent= new Composite(parent, SWT.NONE);
        GridLayout innerLayout= new GridLayout();
        innerLayout.numColumns= 2;
        innerLayout.marginHeight= 0;
        innerLayout.marginWidth= 0;
        innerParent.setLayout(innerLayout);
        GridData gd= new GridData(GridData.FILL_BOTH);
        gd.horizontalSpan= 2;
        innerParent.setLayoutData(gd);

		Table table= new Table(innerParent, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
		
		GridData data= new GridData(GridData.FILL_BOTH);
		data.widthHint= convertWidthInCharsToPixels(3);
//		data.heightHint= convertHeightInCharsToPixels(10);
		table.setLayoutData(data);
				
		table.setHeaderVisible(true);
		table.setLinesVisible(true);		

		TableLayout tableLayout= new TableLayout();
		table.setLayout(tableLayout);

		TableColumn column1= new TableColumn(table, SWT.NONE);		
		column1.setText(TemplateMessages.getString("CodeTemplatePreferencePage.column.name")); //$NON-NLS-1$

/*		TableColumn column2= new TableColumn(table, SWT.NONE); //changed
		column2.setText(TemplateMessages.getString("CodeTemplatePreferencePage.column.context")); //$NON-NLS-1$
*/	
		TableColumn column3= new TableColumn(table, SWT.NONE);
		column3.setText(TemplateMessages.getString("CodeTemplatePreferencePage.column.description")); //$NON-NLS-1$

				
		fTableViewer= new TableViewer(table);		
		fTableViewer.setLabelProvider(new CodeTemplateLabelProvider());
		fTableViewer.setContentProvider(new TemplateContentProvider());

/*		fTableViewer.setSorter(new ViewerSorter() { //changed
			public int compare(Viewer viewer, Object object1, Object object2) {
				if ((object1 instanceof Template) && (object2 instanceof Template)) {
					Template left= (Template) object1;
					Template right= (Template) object2;
					int result= left.getName().compareToIgnoreCase(right.getName());
					if (result != 0)
						return result;
					return left.getDescription().compareToIgnoreCase(right.getDescription());
				}
				return super.compare(viewer, object1, object2);
			}
			
			public boolean isSorterProperty(Object element, String property) {
				return true;
			}
		});
*/		
		fTableViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent e) {
				edit();
			}
		});
		
		fTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				selectionChanged1();
			}
		});

/*		fTableViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				Template template= (Template) event.getElement();
				template.setEnabled(event.getChecked());
			}
		});
*/
		Composite buttons= new Composite(innerParent, SWT.NONE);
		buttons.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		buttons.setLayout(layout);
		
		/*
		fAddButton= new Button(buttons, SWT.PUSH);
		fAddButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.new")); //$NON-NLS-1$
		fAddButton.setLayoutData(getButtonGridData(fAddButton));
		fAddButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				add();
			}
		});
		*/

		fEditButton= new Button(buttons, SWT.PUSH);
		fEditButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.edit")); //$NON-NLS-1$
		fEditButton.setLayoutData(getButtonGridData(fEditButton));
		fEditButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				edit();
			}
		});

		/*
		fRemoveButton= new Button(buttons, SWT.PUSH);
		fRemoveButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.remove")); //$NON-NLS-1$
		fRemoveButton.setLayoutData(getButtonGridData(fRemoveButton));
		fRemoveButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				remove();
			}
		});
		*/
				
		fImportButton= new Button(buttons, SWT.PUSH);
		fImportButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.import")); //$NON-NLS-1$
		fImportButton.setLayoutData(getButtonGridData(fImportButton));
		fImportButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				import_();
			}
		});

		fExportButton= new Button(buttons, SWT.PUSH);
		fExportButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.export")); //$NON-NLS-1$
		fExportButton.setLayoutData(getButtonGridData(fExportButton));
		fExportButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				export();
			}
		});

		fExportAllButton= new Button(buttons, SWT.PUSH);
		fExportAllButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.export.all")); //$NON-NLS-1$
		fExportAllButton.setLayoutData(getButtonGridData(fExportAllButton));
		fExportAllButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				exportAll();
			}
		});		
		/*
		fEnableAllButton= new Button(buttons, SWT.PUSH);
		fEnableAllButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.enable.all")); //$NON-NLS-1$
		fEnableAllButton.setLayoutData(getButtonGridData(fEnableAllButton));
		fEnableAllButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				enableAll(true);
			}
		});

		fDisableAllButton= new Button(buttons, SWT.PUSH);
		fDisableAllButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.disable.all")); //$NON-NLS-1$
		fDisableAllButton.setLayoutData(getButtonGridData(fDisableAllButton));
		fDisableAllButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				enableAll(false);
			}
		});
		*/
		fPatternViewer= createViewer(parent);
		
		/*fFormatButton= new Button(parent, SWT.CHECK);
		fFormatButton.setText(TemplateMessages.getString("CodeTemplatePreferencePage.use.code.formatter")); //$NON-NLS-1$
        GridData gd1= new GridData();
        gd1.horizontalSpan= 2;
        fFormatButton.setLayoutData(gd1);
		IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
		fFormatButton.setSelection(prefs.getBoolean(PREF_FORMAT_TEMPLATES));        
        */

		fTableViewer.setInput(fTemplates);
//		fTableViewer.setAllChecked(false);
//		fTableViewer.setCheckedElements(getEnabledTemplates());		



		updateButtons();
		configureTableResizing(innerParent, buttons, table, column1, column3);

		WorkbenchHelp.setHelp(parent, IJavaHelpContextIds.TEMPLATE_PREFERENCE_PAGE);
		
		return parent;
	}
    
     /**
     * Correctly resizes the table so no phantom columns appear
     */
    private static void configureTableResizing(final Composite parent, final Composite buttons, final Table table, final TableColumn column1, final TableColumn column3) {
        parent.addControlListener(new ControlAdapter() {
            public void controlResized(ControlEvent e) {
                Rectangle area= parent.getClientArea();
                Point preferredSize= table.computeSize(SWT.DEFAULT, SWT.DEFAULT);
                int width= area.width - 2 * table.getBorderWidth();
                if (preferredSize.y > area.height) {
                    // Subtract the scrollbar width from the total column width
                    // if a vertical scrollbar will be required
                    Point vBarSize = table.getVerticalBar().getSize();
                    width -= vBarSize.x;
                }
                width -= buttons.getSize().x;
                Point oldSize= table.getSize();
                if (oldSize.x > width) {
                    // table is getting smaller so make the columns
                    // smaller first and then resize the table to
                    // match the client area width
                    column1.setWidth(width/3);
                    column3.setWidth(width - (column1.getWidth()));
                    table.setSize(width, area.height);
                } else {
                    // table is getting bigger so make the table
                    // bigger first and then make the columns wider
                    // to match the client area width
                    table.setSize(width, area.height);
                    column1.setWidth(width / 4);
                    column3.setWidth(width - (column1.getWidth()));
                 }
            }
        });
    }
    
	/*
	private Template[] getEnabledTemplates() {
		Template[] templates= fTemplates.getTemplates();
		
		List list= new ArrayList(templates.length);
		
		for (int i= 0; i != templates.length; i++)
			if (templates[i].isEnabled())
				list.add(templates[i]);
				
		return (Template[]) list.toArray(new Template[list.size()]);
	}
	*/
	private SourceViewer createViewer(Composite parent) {
		Label label= new Label(parent, SWT.NONE);
		label.setText(TemplateMessages.getString("CodeTemplatePreferencePage.preview")); //$NON-NLS-1$
		GridData data= new GridData();
		data.horizontalSpan= 2;
		label.setLayoutData(data);
		
		SourceViewer viewer= new SourceViewer(parent, null, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();
		IDocument document= new Document();
		IDocumentPartitioner partitioner= tools.createDocumentPartitioner();
		document.setDocumentPartitioner(partitioner);
		partitioner.connect(document);		
		viewer.configure(new JavaSourceViewerConfiguration(tools, null));
		viewer.setEditable(false);
		viewer.setDocument(document);
		viewer.getTextWidget().setBackground(getShell().getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
	
		Font font= JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT);
		viewer.getTextWidget().setFont(font);
		
		Control control= viewer.getControl();
		data= new GridData(GridData.FILL_BOTH);
        data.horizontalSpan= 2;
		data.heightHint= convertHeightInCharsToPixels(5);
		control.setLayoutData(data);
		
		return viewer;
	}
	
	private static GridData getButtonGridData(Button button) {
		GridData data= new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint= SWTUtil.getButtonWidthHint(button);
		data.heightHint= SWTUtil.getButtonHeigthHint(button);
	
		return data;
	}
	
	private void selectionChanged1() {		
		IStructuredSelection selection= (IStructuredSelection) fTableViewer.getSelection();

		if (selection.size() == 1) {
			Template template= (Template) selection.getFirstElement();
			fPatternViewer.getTextWidget().setText(template.getPattern());
		} else {		
			fPatternViewer.getTextWidget().setText(""); //$NON-NLS-1$
		}
		
		updateButtons();
	}
	
	private void updateButtons() {
		int selectionCount= ((IStructuredSelection) fTableViewer.getSelection()).size();
		//int itemCount= fTableViewer.getTable().getItemCount();
		
		fEditButton.setEnabled(selectionCount == 1);
		fExportButton.setEnabled(selectionCount > 0);
		//fRemoveButton.setEnabled(selectionCount > 0 && selectionCount <= itemCount);
		//fEnableAllButton.setEnabled(itemCount > 0);
		//fDisableAllButton.setEnabled(itemCount > 0);
	}
	/*
	private void add() {		
		
		Template template= new Template();

		ContextTypeRegistry registry=ContextTypeRegistry.getInstance();
		ContextType type= registry.getContextType("java"); //$NON-NLS-1$
		
		String contextTypeName;
		if (type != null)
			contextTypeName= type.getName();
		else {
			Iterator iterator= registry.iterator();
			contextTypeName= (String) iterator.next();
		}
		template.setContext(contextTypeName); //$NON-NLS-1$
		
		EditTemplateDialog dialog= new EditTemplateDialog(getShell(), template, false, false);
		if (dialog.open() == EditTemplateDialog.OK) {
			fTemplates.add(template);
			fTableViewer.refresh();
//			fTableViewer.setChecked(template, template.isEnabled());
			fTableViewer.setSelection(new StructuredSelection(template));			
		}
	}
	*/

	private void edit() {
		IStructuredSelection selection= (IStructuredSelection) fTableViewer.getSelection();

		Object[] objects= selection.toArray();		
		if ((objects == null) || (objects.length != 1))
			return;
		
		Template template= (Template) selection.getFirstElement();
		edit(template);
	}

	private void edit(Template template) {
		Template newTemplate= new Template(template);
		EditTemplateDialog dialog= new EditTemplateDialog(getShell(), newTemplate, true, false, CONTEXTS);
		if (dialog.open() == EditTemplateDialog.OK) {
			// changed
			template.setDescription(newTemplate.getDescription());
			template.setPattern(newTemplate.getPattern());
			fTableViewer.refresh(template);
//			fTableViewer.setChecked(template, template.isEnabled());
			fTableViewer.setSelection(new StructuredSelection(template));			
		}
	}
		
	private void import_() {
		FileDialog dialog= new FileDialog(getShell());
		dialog.setText(TemplateMessages.getString("CodeTemplatePreferencePage.import.title")); //$NON-NLS-1$
		dialog.setFilterExtensions(new String[] {TemplateMessages.getString("CodeTemplatePreferencePage.import.extension")}); //$NON-NLS-1$
		String path= dialog.open();
		
		if (path == null)
			return;
		
		try {
			fTemplates.addFromFile(new File(path));
			
			fTableViewer.refresh();
//			fTableViewer.setAllChecked(false);
//			fTableViewer.setCheckedElements(getEnabledTemplates());

		} catch (CoreException e) {			
			openReadErrorDialog(e);
		}
	}
	
	private void exportAll() {
		export(fTemplates);	
	}

	private void export() {
		IStructuredSelection selection= (IStructuredSelection) fTableViewer.getSelection();
		Object[] templates= selection.toArray();
		
		TemplateSet templateSet= new TemplateSet(fTemplates.getTemplateTag());
		for (int i= 0; i != templates.length; i++)
			templateSet.add((Template) templates[i]);
		
		export(templateSet);
	}
	
	private void export(TemplateSet templateSet) {
		FileDialog dialog= new FileDialog(getShell(), SWT.SAVE);
		dialog.setText(TemplateMessages.getFormattedString("CodeTemplatePreferencePage.export.title", new Integer(templateSet.getTemplates().length))); //$NON-NLS-1$
		dialog.setFilterExtensions(new String[] {TemplateMessages.getString("CodeTemplatePreferencePage.export.extension")}); //$NON-NLS-1$
		dialog.setFileName(TemplateMessages.getString("CodeTemplatePreferencePage.export.filename")); //$NON-NLS-1$
		String path= dialog.open();
		
		if (path == null)
			return;
		
		File file= new File(path);		

		if (!file.exists() || confirmOverwrite(file)) {
			try {
				templateSet.saveToFile(file);			
			} catch (CoreException e) {			
				JavaPlugin.log(e);
				openWriteErrorDialog(e);
			}		
		}
	}

	private boolean confirmOverwrite(File file) {
		return MessageDialog.openQuestion(getShell(),
			TemplateMessages.getString("CodeTemplatePreferencePage.export.exists.title"), //$NON-NLS-1$
			TemplateMessages.getFormattedString("CodeTemplatePreferencePage.export.exists.message", file.getAbsolutePath())); //$NON-NLS-1$
	}
	/*
	private void remove() {
		IStructuredSelection selection= (IStructuredSelection) fTableViewer.getSelection();

		Iterator elements= selection.iterator();
		while (elements.hasNext()) {
			Template template= (Template) elements.next();
			fTemplates.remove(template);
		}

		fTableViewer.refresh();
	}
	
	private void enableAll(boolean enable) {
		Template[] templates= fTemplates.getTemplates();
		for (int i= 0; i != templates.length; i++)
			templates[i].setEnabled(enable);		
			
		fTableViewer.setAllChecked(enable);
	}
	*/
	/*
	 * @see IWorkbenchPreferencePage#init(IWorkbench)
	 */
	public void init(IWorkbench workbench) {}

	/*
	 * @see Control#setVisible(boolean)
	 */
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible)
			setTitle(TemplateMessages.getString("CodeTemplatePreferencePage.title")); //$NON-NLS-1$
	}
	
	/*
	 * @see PreferencePage#performDefaults()
	 */
	protected void performDefaults() {
		//IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
		//fFormatButton.setSelection(prefs.getDefaultBoolean(PREF_FORMAT_TEMPLATES));

		try {
			fTemplates.restoreDefaults();
		} catch (CoreException e) {
			JavaPlugin.log(e);
			openReadErrorDialog(e);
		}
		
		// refresh
		fTableViewer.refresh();
//		fTableViewer.setAllChecked(false);
//		fTableViewer.setCheckedElements(getEnabledTemplates());		
	}

	/*
	 * @see PreferencePage#performOk()
	 */	
	public boolean performOk() {
		//IPreferenceStore prefs= JavaPlugin.getDefault().getPreferenceStore();
		//prefs.setValue(PREF_FORMAT_TEMPLATES, fFormatButton.getSelection());

		try {
			fTemplates.save();
		} catch (CoreException e) {
			JavaPlugin.log(e);
			openWriteErrorDialog(e);
		}
		
		//JavaPlugin.getDefault().savePluginPreferences();
		return super.performOk();
	}	
	
	/*
	 * @see PreferencePage#performCancel()
	 */
	public boolean performCancel() {
		try {
			fTemplates.reset();			
		} catch (CoreException e) {
			JavaPlugin.log(e);
			openReadErrorDialog(e);
		}

		return super.performCancel();
	}
	
	private void openReadErrorDialog(CoreException e) {
		ErrorDialog.openError(getShell(),
			TemplateMessages.getString("CodeTemplatePreferencePage.error.read.title"), //$NON-NLS-1$
			null, e.getStatus());
	}
	
	private void openWriteErrorDialog(CoreException e) {
		ErrorDialog.openError(getShell(),
			TemplateMessages.getString("CodeTemplatePreferencePage.error.write.title"), //$NON-NLS-1$
			null, e.getStatus());		
	}
		
}
