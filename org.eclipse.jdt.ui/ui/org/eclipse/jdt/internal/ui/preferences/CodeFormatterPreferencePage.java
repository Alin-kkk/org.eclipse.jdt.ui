/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.preferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.help.DialogPageContextComputer;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.formatter.CodeFormatter;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.util.TabFolderLayout;

/*
 * The page for setting code formatter options
 */
public class CodeFormatterPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	// Preference store keys, see JavaCore.getOptions
	private static final String PREF_NEWLINE_OPENING_BRACES= "org.eclipse.jdt.core.formatter.newline.openingBrace";
	private static final String PREF_NEWLINE_CONTROL_STATEMENT= "org.eclipse.jdt.core.formatter.newline.controlStatement";
	private static final String PREF_NEWLINE_CLEAR_ALL= "org.eclipse.jdt.core.formatter.newline.clearAll";
	private static final String PREF_NEWLINE_ELSE_IF= "org.eclipse.jdt.core.formatter.newline.elseIf";
	private static final String PREF_NEWLINE_EMPTY_BLOCK= "org.eclipse.jdt.core.formatter.newline.emptyBlock";
	private static final String PREF_LINE_SPLIT= "org.eclipse.jdt.core.formatter.lineSplit";	
	private static final String PREF_STYLE_COMPACT_ASSIGNEMENT= "org.eclipse.jdt.core.formatter.style.assignment";	
	private static final String PREF_TAB_CHAR= "org.eclipse.jdt.core.formatter.tabulation.char";	
	private static final String PREF_TAB_SIZE= "org.eclipse.jdt.core.formatter.tabulation.size";

	// values
	private static final String INSERT= "insert";
	private static final String DO_NOT_INSERT= "do not insert";
	
	private static final String COMPACT= "compact";
	private static final String NORMAL= "normal";
	
	private static final String TAB= "tab";
	private static final String SPACE= "space";
	
	private static final String CLEAR_ALL= "clear all";
	private static final String PRESERVE_ONE= "preserve one";
	

	private static String[] getAllKeys() {
		return new String[] {
			PREF_NEWLINE_OPENING_BRACES, PREF_NEWLINE_CONTROL_STATEMENT, PREF_NEWLINE_CLEAR_ALL,
			PREF_NEWLINE_ELSE_IF, PREF_NEWLINE_EMPTY_BLOCK, PREF_LINE_SPLIT,
			PREF_STYLE_COMPACT_ASSIGNEMENT, PREF_TAB_CHAR, PREF_TAB_SIZE
		};	
	}
	
	/**
	 * Gets the currently configured tab size
	 */
	public static int getTabSize() {
		String string= (String) JavaCore.getOptions().get(PREF_TAB_SIZE);
		return getIntValue(string, 4);
	}
	
	/**
	 * Gets the current compating assignement configuration
	 */	
	public static boolean isCompactingAssignment() {
		return COMPACT.equals(JavaCore.getOptions().get(PREF_STYLE_COMPACT_ASSIGNEMENT));
	}
	
	/**
	 * Gets the current compating assignement configuration
	 */	
	public static boolean useSpaces() {
		return SPACE.equals(JavaCore.getOptions().get(PREF_TAB_CHAR));
	}	
	
	
	private static int getIntValue(String string, int dflt) {
		try {
			return Integer.parseInt(string);
		} catch (NumberFormatException e) {
		}
		return dflt;
	}	
		
	
	/**
	 * Initializes the current options (read from preference store)
	 */
	public static void initDefaults(IPreferenceStore store) {
		Hashtable hashtable= JavaCore.getDefaultOptions();
		Hashtable currOptions= JavaCore.getOptions();
		String[] allKeys= getAllKeys();
		for (int i= 0; i < allKeys.length; i++) {
			String key= allKeys[i];
			String defValue= (String) hashtable.get(key);
			if (defValue != null) {
				store.setDefault(key, defValue);
			} else {
				JavaPlugin.logErrorMessage("CodeFormatterPreferencePage: value is null: " + key);
			}
			// update the JavaCore options from the pref store
			String val= store.getString(key);
			if (val != null) {
				currOptions.put(key, val);
			}			
		}
		JavaCore.setOptions(currOptions);
	}

	private static class ControlData {
		private String fKey;
		private String[] fValues;
		
		public ControlData(String key, String[] values) {
			fKey= key;
			fValues= values;
		}
		
		public String getKey() {
			return fKey;
		}
		
		public String getValue(boolean selection) {
			int index= selection ? 0 : 1;
			return fValues[index];
		}
		
		public String getValue(int index) {
			return fValues[index];
		}		
		
		public int getSelection(String value) {
			for (int i= 0; i < fValues.length; i++) {
				if (value.equals(fValues[i])) {
					return i;
				}
			}
			throw new IllegalArgumentException();
		}
	}
	
	private Hashtable fWorkingValues;

	private ArrayList fCheckBoxes;
	private ArrayList fTextBoxes;
	
	private SelectionListener fButtonSelectionListener;
	private ModifyListener fTextModifyListener;
	
	private String fPreviewText;
	private IDocument fPreviewDocument;
	
	private Text fTabSizeTextBox;
	private SourceViewer fSourceViewer;
	

	public CodeFormatterPreferencePage() {
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		setDescription(JavaUIMessages.getString("CodeFormatterPreferencePage.description")); //$NON-NLS-1$
	
		fWorkingValues= JavaCore.getOptions();
		fCheckBoxes= new ArrayList();
		fTextBoxes= new ArrayList();
		
		fButtonSelectionListener= new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {}

			public void widgetSelected(SelectionEvent e) {
				if (!e.widget.isDisposed()) {
					controlChanged((Button) e.widget);
				}
			}
		};
		
		fTextModifyListener= new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if (!e.widget.isDisposed()) {
					textChanged((Text) e.widget);
				}
			}
		};
		
		fPreviewDocument= new Document();
		fPreviewText= loadPreviewFile("CodeFormatterPreviewCode.txt");	//$NON-NLS-1$	
	}

	/*
	 * @see IWorkbenchPreferencePage#init()
	 */	
	public void init(IWorkbench workbench) {
	}

	/*
	 * @see PreferencePage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		WorkbenchHelp.setHelp(getControl(), new DialogPageContextComputer(this, IJavaHelpContextIds.CODEFORMATTER_PREFERENCE_PAGE));
	}	

	/*
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayout(layout);
				
			
		TabFolder folder= new TabFolder(composite, SWT.NONE);
		folder.setLayout(new TabFolderLayout());	
		folder.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		String[] insertNotInsert= new String[] { INSERT, DO_NOT_INSERT };
		
		layout= new GridLayout();
		layout.numColumns= 2;
		
		Composite newlineComposite= new Composite(folder, SWT.NULL);
		newlineComposite.setLayout(layout);

		String label= JavaUIMessages.getString("CodeFormatterPreferencePage.newline_opening_braces.label"); //$NON-NLS-1$
		addCheckBox(newlineComposite, label, PREF_NEWLINE_OPENING_BRACES, insertNotInsert);	
		
		label= JavaUIMessages.getString("CodeFormatterPreferencePage.newline_control_statement.label"); //$NON-NLS-1$
		addCheckBox(newlineComposite, label, PREF_NEWLINE_CONTROL_STATEMENT, insertNotInsert);	

		label= JavaUIMessages.getString("CodeFormatterPreferencePage.newline_clear_lines"); //$NON-NLS-1$
		addCheckBox(newlineComposite, label, PREF_NEWLINE_CLEAR_ALL, new String[] { CLEAR_ALL, PRESERVE_ONE } );	

		label= JavaUIMessages.getString("CodeFormatterPreferencePage.newline_else_if.label"); //$NON-NLS-1$
		addCheckBox(newlineComposite, label, PREF_NEWLINE_ELSE_IF, insertNotInsert);	

		label= JavaUIMessages.getString("CodeFormatterPreferencePage.newline_empty_block.label"); //$NON-NLS-1$
		addCheckBox(newlineComposite, label, PREF_NEWLINE_EMPTY_BLOCK, insertNotInsert);	
		
		layout= new GridLayout();
		layout.numColumns= 2;	
		
		Composite lineSplittingComposite= new Composite(folder, SWT.NULL);
		lineSplittingComposite.setLayout(layout);

		label= JavaUIMessages.getString("CodeFormatterPreferencePage.split_line.label"); //$NON-NLS-1$
		addTextField(lineSplittingComposite, label, PREF_LINE_SPLIT);

		layout= new GridLayout();
		layout.numColumns= 2;	
		
		Composite styleComposite= new Composite(folder, SWT.NULL);
		styleComposite.setLayout(layout);
		
		label= JavaUIMessages.getString("CodeFormatterPreferencePage.style_compact_assignement.label"); //$NON-NLS-1$
		addCheckBox(styleComposite, label, PREF_STYLE_COMPACT_ASSIGNEMENT, new String[] { COMPACT, NORMAL } );		

		label= JavaUIMessages.getString("CodeFormatterPreferencePage.tab_char.label"); //$NON-NLS-1$
		addCheckBox(styleComposite, label, PREF_TAB_CHAR, new String[] { TAB, SPACE } );		

		label= JavaUIMessages.getString("CodeFormatterPreferencePage.tab_size.label"); //$NON-NLS-1$
		fTabSizeTextBox= addTextField(styleComposite, label, PREF_TAB_SIZE);		

		TabItem item= new TabItem(folder, SWT.NONE);
		item.setText(JavaUIMessages.getString("CodeFormatterPreferencePage.tab.newline.tabtitle")); //$NON-NLS-1$
		item.setImage(JavaPluginImages.get(JavaPluginImages.IMG_OBJS_IMPDECL));
		item.setControl(newlineComposite);

		item= new TabItem(folder, SWT.NONE);
		item.setText(JavaUIMessages.getString("CodeFormatterPreferencePage.tab.linesplit.tabtitle")); //$NON-NLS-1$
		item.setImage(JavaPluginImages.get(JavaPluginImages.IMG_OBJS_CFILE));
		item.setControl(lineSplittingComposite);
		
		item= new TabItem(folder, SWT.NONE);
		item.setText(JavaUIMessages.getString("CodeFormatterPreferencePage.tab.style.tabtitle")); //$NON-NLS-1$
		item.setImage(JavaPluginImages.get(JavaPluginImages.IMG_OBJS_SEARCH_REF));
		item.setControl(styleComposite);		
		
		fSourceViewer= createPreview(parent);
			
		updatePreview();
					
		return composite;
	}
	
	private SourceViewer createPreview(Composite parent) {
		SourceViewer previewViewer= new SourceViewer(parent, null, SWT.V_SCROLL | SWT.H_SCROLL);
		JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();
		previewViewer.configure(new JavaSourceViewerConfiguration(tools, null));
		previewViewer.getTextWidget().setFont(JFaceResources.getFontRegistry().get(JFaceResources.TEXT_FONT));
		previewViewer.getTextWidget().setTabs(getIntValue((String) fWorkingValues.get(PREF_TAB_SIZE), 0));
		previewViewer.setEditable(false);
		previewViewer.setDocument(fPreviewDocument);
		Control control= previewViewer.getControl();
		GridData gdata= new GridData(GridData.FILL_BOTH);
		gdata.widthHint= convertWidthInCharsToPixels(80);
		gdata.heightHint= convertHeightInCharsToPixels(15);
		control.setLayoutData(gdata);
		return previewViewer;
	}

	
	private Button addCheckBox(Composite parent, String label, String key, String[] values) {
		ControlData data= new ControlData(key, values);
		
		GridData gd= new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan= 2;
		
		Button checkBox= new Button(parent, SWT.CHECK);
		checkBox.setText(label);
		checkBox.setData(data);
		checkBox.setLayoutData(gd);
		
		String currValue= (String)fWorkingValues.get(key);	
		checkBox.setSelection(data.getSelection(currValue) == 0);
		checkBox.addSelectionListener(fButtonSelectionListener);
		
		fCheckBoxes.add(checkBox);
		return checkBox;
	}
	
	private Text addTextField(Composite parent, String label, String key) {	
		Label labelControl= new Label(parent, SWT.NONE);
		labelControl.setText(label);
		labelControl.setLayoutData(new GridData());
				
		Text textBox= new Text(parent, SWT.BORDER | SWT.SINGLE);
		textBox.setData(key);
		textBox.setLayoutData(new GridData());
		
		String currValue= (String)fWorkingValues.get(key);	
		textBox.setText(String.valueOf(getIntValue(currValue, 1)));
		textBox.setTextLimit(3);
		textBox.addModifyListener(fTextModifyListener);

		GridData gd= new GridData();
		gd.widthHint= convertWidthInCharsToPixels(5);
		textBox.setLayoutData(gd);

		fTextBoxes.add(textBox);
		return textBox;
	}	
	
	private void controlChanged(Button button) {
		ControlData data= (ControlData) button.getData();
		boolean selection= button.getSelection();
		String newValue= data.getValue(selection);	
		fWorkingValues.put(data.getKey(), newValue);
		updatePreview();
		
		if (PREF_TAB_CHAR.equals(data.getKey())) {
			updateStatus(new StatusInfo());
			if (selection) {
				fTabSizeTextBox.setText((String)fWorkingValues.get(PREF_TAB_SIZE));
			}
		}
	}
	
	private void textChanged(Text textControl) {
		String key= (String) textControl.getData();
		String number= textControl.getText();
		IStatus status= validatePositiveNumber(number);
		if (!status.matches(IStatus.ERROR)) {
			fWorkingValues.put(key, number);
		}
		if (PREF_TAB_SIZE.equals(key)) {
			fSourceViewer.getTextWidget().setTabs(getIntValue(number, 0));
		}		
		updateStatus(status);
		updatePreview();
	}
		
	
	/*
	 * @see IPreferencePage#performOk()
	 */
	public boolean performOk() {
		String[] allKeys= getAllKeys();
		// preserve other options
		// store in JCore and the preferences
		Hashtable actualOptions= JavaCore.getOptions();
		IPreferenceStore store= getPreferenceStore();
		for (int i= 0; i < allKeys.length; i++) {
			String key= allKeys[i];
			String val=  (String) fWorkingValues.get(key);
			actualOptions.put(key, val);
			store.setValue(key, val);
		}
		JavaCore.setOptions(actualOptions);
		return super.performOk();
	}	
	
	/*
	 * @see PreferencePage#performDefaults()
	 */
	protected void performDefaults() {
		fWorkingValues= JavaCore.getDefaultOptions();
		updateControls();
		super.performDefaults();
	}

	private String loadPreviewFile(String filename) {
		String separator= System.getProperty("line.separator"); //$NON-NLS-1$
		StringBuffer btxt= new StringBuffer(512);
		BufferedReader rin= null;
		try {
			rin= new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(filename)));
			String line;
			while ((line= rin.readLine()) != null) {
				btxt.append(line);
				btxt.append(separator);
			}
		} catch (IOException io) {
			JavaPlugin.log(io);
		} finally {
			if (rin != null) {
				try { rin.close(); } catch (IOException e) {}
			}
		}
		return btxt.toString();
	}


	private void updatePreview() {
		fPreviewDocument.set(new CodeFormatter(fWorkingValues).format(fPreviewText));
	}	
	
	private void updateControls() {
		// update the UI
		for (int i= fCheckBoxes.size() - 1; i >= 0; i--) {
			Button curr= (Button) fCheckBoxes.get(i);
			ControlData data= (ControlData) curr.getData();
					
			String currValue= (String) fWorkingValues.get(data.getKey());	
			curr.setSelection(data.getSelection(currValue) == 0);			
		}
		for (int i= fTextBoxes.size() - 1; i >= 0; i--) {
			Text curr= (Text) fTextBoxes.get(i);
			String key= (String) curr.getData();		
			String currValue= (String) fWorkingValues.get(key);
			curr.setText(currValue);
		}
	}
	
	private IStatus validatePositiveNumber(String number) {
		StatusInfo status= new StatusInfo();
		if (number.length() == 0) {
			status.setError(JavaUIMessages.getString("CodeFormatterPreferencePage.empty_input"));
		} else {
			try {
				int value= Integer.parseInt(number);
				if (value < 0) {
					status.setError(JavaUIMessages.getFormattedString("CodeFormatterPreferencePage.invalid_input", number));
				}
			} catch (NumberFormatException e) {
				status.setError(JavaUIMessages.getFormattedString("CodeFormatterPreferencePage.invalid_input", number));
			}
		}
		return status;
	}
			
	
	private void updateStatus(IStatus status) {
		if (!status.matches(IStatus.ERROR)) {
			// look if there are more severe errors
			for (int i= 0; i < fTextBoxes.size(); i++) {
				Text curr= (Text) fTextBoxes.get(i);
				if (!(curr == fTabSizeTextBox && usesTabs())) {
					IStatus currStatus= validatePositiveNumber(curr.getText());
					status= StatusUtil.getMoreSevere(currStatus, status);
				}
			}
		}	
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}
	
	private boolean usesTabs() {
		return TAB.equals(fWorkingValues.get(PREF_TAB_CHAR));
	}
		

}


