/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.nls;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;

import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.base.Context;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.base.StringContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBufferEditor;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRegion;

public class NLSRefactoring extends Refactoring {
	
	public static final String KEY= "${key}"; //$NON-NLS-1$
	public static final String PROPERTY_FILE_EXT= ".properties"; //$NON-NLS-1$
	private static final String fgLineDelimiter= System.getProperty("line.separator", "\n"); //$NON-NLS-2$ //$NON-NLS-1$

	private String fAccessorClassName= "Messages";  //$NON-NLS-1$
	
	private boolean fCreateAccessorClass= true;
	private String fProperyFileName= "test"; //simple name //$NON-NLS-1$
	private String fCodePattern;
	private ICompilationUnit fCu;
	private NLSLine[] fLines;
	private NLSSubstitution[] fNlsSubs;
	private IPath fPropertyFilePath;
	private String fAddedImport;
	
	public NLSRefactoring(ICompilationUnit cu){
		Assert.isNotNull(cu);
		fCu= cu;
	}
			
	public void setNlsSubstitutions(NLSSubstitution[] subs){
		Assert.isNotNull(subs);
		fNlsSubs= subs;
	}
	
	/**
	 * sets the import to be added
	 * @param decl must be a valid import declaration
	 * otherwise no import declaration will be added
	 * @see JavaConventions#validateImportDeclaration
	 */
	public void setAddedImportDeclaration(String decl){
		if (JavaConventions.validateImportDeclaration(decl).isOK())
			fAddedImport= decl;
		else
			fAddedImport= null;	
	}
	
	/**
	 * no validation is done
	 * @param pattern Example: "Messages.getString(${key})". Must not be <code>null</code>.
	 * should (but does not have to) contain NLSRefactoring.KEY (default value is $key$)
	 * only the first occurrence of this key will be used
	 */
	public void setCodePattern(String pattern){
		Assert.isNotNull(pattern);
		fCodePattern= pattern;
	}
	
	/**
	 * to show the pattern in the ui
	 */
	public String getCodePattern(){
		if (fCodePattern == null)
			return getDefaultCodePattern();
		return fCodePattern;
	}
	
	public String getDefaultCodePattern(){
		return fAccessorClassName + ".getString(" + KEY + ")"; //$NON-NLS-2$ //$NON-NLS-1$
	}
		
	public ICompilationUnit getCu() {
		return fCu;
	}
	
	public String getName() {
		return NLSMessages.getString("NLSrefactoring.compilation_unit")+ fCu.getElementName() + "\""; //$NON-NLS-2$ //$NON-NLS-1$
	}
	
	/**
	 * sets the list of lines
	 * @param List of NLSLines
	 */
	public void setLines(NLSLine[] lines) {
		Assert.isNotNull(lines);
		fLines= lines;
	}
	
	/**
	 * no validation done here
	 * full path expected
	 * can be null - the default value will be used
	 * to ask what the default value is - use 
	 * getDefaultPropertyFileName to get the file name
	 * getDefaultPropertyPackageName to get the package name
	 */
	public void setPropertyFilePath(IPath path){
		fPropertyFilePath= path;
	}
	
	private IPath getPropertyFilePath() throws JavaModelException{
		if (fPropertyFilePath == null)
			return getDefaultPropertyFilePath();
		return fPropertyFilePath;	
	}
	
	private IPath getDefaultPropertyFilePath() throws JavaModelException{
		IPath cuName= new Path(fCu.getElementName());
		return ResourceUtil.getResource(fCu).getFullPath()
						  .removeLastSegments(cuName.segmentCount())
						  .append(fProperyFileName + PROPERTY_FILE_EXT);
	}
	
	public String getDefaultPropertyFileName(){
		try{
			return getDefaultPropertyFilePath().lastSegment();
		} catch (JavaModelException e){
			return ""; //$NON-NLS-1$
		}	
	}
	
	/**
	 * returns "" in case of JavaModelException caught during calculation
	 */
	public String getDefaultPropertyPackageName(){
		try{
			IPath path= getDefaultPropertyFilePath();
			IResource res= ResourcesPlugin.getWorkspace().getRoot().findMember(path.removeLastSegments(1));
			IJavaElement je= JavaCore.create(res);
			if (je instanceof IPackageFragment)
				return je.getElementName();
			else	
				return ""; //$NON-NLS-1$
		} catch (JavaModelException e){
			return ""; //$NON-NLS-1$
		}	
	}
	
	/**
	 * @see Refactoring#checkActivation(IProgressMonitor)
	 */
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException {
		if (! fCu.exists())
			return RefactoringStatus.createFatalErrorStatus(fCu.getElementName() + NLSMessages.getString("NLSrefactoring.does_not_exist")); //$NON-NLS-1$
		
		if (fCu.isReadOnly())
			return RefactoringStatus.createFatalErrorStatus(fCu.getElementName() + NLSMessages.getString("NLSrefactoring.read_only"));	 //$NON-NLS-1$
		
		if (NLSHolder.create(fCu).getSubstitutions().length == 0)	{
			String message= NLSMessages.getFormattedString("NLSRefactoring.no_strings", fCu.getElementName());//$NON-NLS-1$
			return RefactoringStatus.createFatalErrorStatus(message);
		}	
		
		return new RefactoringStatus();
	}
	
	/**
	 * @see Refactoring#checkInput(IProgressMonitor)
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask(NLSMessages.getString("NLSrefactoring.checking"), 7); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();
			result.merge(checkIfAnythingToDo());
			if (result.hasFatalError())	
				return result;
			pm.worked(1);
			
			result.merge(validateModifiesFiles());
			if (result.hasFatalError())	
				return result;
			pm.worked(1);
			
			result.merge(checkCodePattern());
			pm.worked(1);
			result.merge(checkForDuplicateKeys());
			pm.worked(1);
			result.merge(checkForKeysAlreadyDefined());
			pm.worked(1);
			result.merge(checkKeys());
			pm.worked(1);
			if (!propertyFileExists() && willModifyPropertyFile())
				result.addInfo(NLSMessages.getString("NLSrefactoring.Propfile") + getPropertyFilePath() + NLSMessages.getString("NLSrefactoring.will_be_created")); //$NON-NLS-2$ //$NON-NLS-1$
			pm.worked(1);	
			return result;
		} catch (CoreException e){
			throw new JavaModelException(e);
		} finally {
			pm.done();
		}	
	}

	private IFile[] getAllFilesToModify() throws CoreException{
		List files= new ArrayList(2);
		if (willModifySource()){
			IFile file= ResourceUtil.getFile(fCu);
			if (file != null)
				files.add(file);
		}	
		
		if (willModifyPropertyFile() && propertyFileExists())
			files.add(getPropertyFile());
			
		return (IFile[]) files.toArray(new IFile[files.size()]);
	}
	
	private RefactoringStatus validateModifiesFiles() throws CoreException{
		return Checks.validateModifiesFiles(getAllFilesToModify());
	}
	
	//should stop checking if fatal error
	private RefactoringStatus checkIfAnythingToDo() throws JavaModelException{
		if (willCreateAccessorClass())
			return null;
		if (willModifyPropertyFile())
			return null;
		if (willModifySource())
			return null;	
			
		RefactoringStatus result= new RefactoringStatus();
		result.addFatalError(NLSMessages.getString("NLSrefactoring.nothing_to_do")); //$NON-NLS-1$
		return result;
	}
	
	private boolean propertyFileExists() throws JavaModelException{
		return Checks.resourceExists(getPropertyFilePath());
	}
	
	private RefactoringStatus checkCodePattern(){
		String pattern= getCodePattern();
		RefactoringStatus result= new RefactoringStatus();
		if ("".equals(pattern.trim())) //$NON-NLS-1$
			result.addError(NLSMessages.getString("NLSrefactoring.pattern_empty")); //$NON-NLS-1$
		if (pattern.indexOf(KEY) == -1)
			result.addWarning(NLSMessages.getString("NLSrefactoring.pattern_does_not_contain") + KEY); //$NON-NLS-1$
		if (pattern.indexOf(KEY) != pattern.lastIndexOf(KEY))	
			result.addWarning(NLSMessages.getString("NLSrefactoring.Only_the_first_occurrence_of") + KEY + NLSMessages.getString("NLSrefactoring.will_be_substituted")); //$NON-NLS-2$ //$NON-NLS-1$
		return result;	
	}

	private RefactoringStatus checkForKeysAlreadyDefined() throws JavaModelException {
		if (! propertyFileExists())
			return null;
		RefactoringStatus result= new RefactoringStatus();
		PropertyResourceBundle bundle= getPropertyBundle();
		if (bundle == null)
			return null;
		for (int i= 0; i< fNlsSubs.length; i++){
			String s= getBundleString(bundle, fNlsSubs[i].key);
			if (s != null){
				if (! hasSameValue(s, fNlsSubs[i]))
					result.addFatalError(NLSMessages.getString("NLSrefactoring.key") + fNlsSubs[i].key + NLSMessages.getString("NLSrefactoring.already_exists") + s //$NON-NLS-2$ //$NON-NLS-1$
									+ NLSMessages.getString("NLSrefactoring.different") //$NON-NLS-1$
									+ removeQuotes(fNlsSubs[i].value.getValue())
									+ NLSMessages.getString("NLSrefactoring.on_first_page"));  //$NON-NLS-1$
				else{
					fNlsSubs[i].putToPropertyFile= false;
					result.addWarning(NLSMessages.getString("NLSrefactoring.key") + fNlsSubs[i].key + NLSMessages.getString("NLSrefactoring.already_in_bundle") + s  //$NON-NLS-2$ //$NON-NLS-1$
									 + NLSMessages.getString("NLSrefactoring.same_first_page")); //$NON-NLS-1$
				}	
			}
		}
		return result;
	}
	
	private boolean hasSameValue(String val, NLSSubstitution sub){
		return (val.equals(removeQuotes(sub.value.getValue())));
	}
	
	/**
	 * returns <code>null</code> if not defined
	 */
	private String getBundleString(PropertyResourceBundle bundle, String key){
		try{
			return bundle.getString(key);
		} catch (MissingResourceException e){
			//XXX very inefficient
			return null;	
		}
	}
	
	private PropertyResourceBundle getPropertyBundle() throws JavaModelException{
		InputStream is= getPropertyFileInputStream();
		if (is == null)
			return null;
		try{
			PropertyResourceBundle result= new PropertyResourceBundle(is);
			return result;
		} catch (IOException e1){	
			return null;
		}finally {
			try{
				is.close();
			} catch (IOException e){
				throw new JavaModelException(e, IJavaModelStatusConstants.IO_EXCEPTION);
			}
		}	
	}
	
	private InputStream getPropertyFileInputStream() throws JavaModelException{
		IFile file= ResourcesPlugin.getWorkspace().getRoot().getFile(getPropertyFilePath());
		
		try{
			return file.getContents();
		} catch(CoreException e){
			throw new JavaModelException(e, IJavaModelStatusConstants.CORE_EXCEPTION);
		}
	}
	
	private RefactoringStatus checkForDuplicateKeys() {
		Map map= new HashMap();//String (key) -> Set of NLSSubstitution
		for (int i= 0; i < fNlsSubs.length; i++) {
			NLSSubstitution sub= fNlsSubs[i];
			String key= sub.key;
			if (!map.containsKey(key)){
			 	map.put(key, new HashSet());
			}
			((Set)map.get(key)).add(sub);		
		}
		
		RefactoringStatus result= new RefactoringStatus();
		for (Iterator iter= map.keySet().iterator(); iter.hasNext();) {
			Set subs= (Set)map.get((String) iter.next());
			result.merge(checkForDuplicateKeys(subs));
		}
		return result;
	}
	
	/**
	 * all elements in the parameter must be NLSSubstitutions with
	 * the same key
	 */
	private RefactoringStatus checkForDuplicateKeys(Set subs){
		if (subs.size() <= 1)
			return null;
		
		NLSSubstitution[] toTranslate= getEntriesToTranslate(subs);
		if (toTranslate.length <= 1)
			return null;
		
		for (int i= 0; i < toTranslate.length; i++) {
			toTranslate[i].putToPropertyFile= (i == 0);
		}

		String value= removeQuotes(toTranslate[0].value.getValue());
		for (int i= 0; i < toTranslate.length; i++) {
			NLSSubstitution each= toTranslate[i];
			if (! hasSameValue(value, each))
				return RefactoringStatus.createFatalErrorStatus(NLSMessages.getString("NLSrefactoring.key") + each.key + NLSMessages.getString("NLSrefactoring.duplicated")); //$NON-NLS-2$ //$NON-NLS-1$
		}
		return RefactoringStatus.createWarningStatus(NLSMessages.getString("NLSrefactoring.key") + toTranslate[0].key + NLSMessages.getString("NLSrefactoring.reused") + value) ; //$NON-NLS-2$ //$NON-NLS-1$	
	}
	
	private static NLSSubstitution[] getEntriesToTranslate(Set subs){
		List result= new ArrayList(subs.size());
		for (Iterator iter= subs.iterator(); iter.hasNext();) {
			NLSSubstitution each= (NLSSubstitution) iter.next();
			if (each.task == NLSSubstitution.TRANSLATE)
				result.add(each);
		}
		return (NLSSubstitution[]) result.toArray(new NLSSubstitution[result.size()]);
	}
		
	private RefactoringStatus checkKeys() {
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < fNlsSubs.length; i++)
			checkKey(fNlsSubs[i].key, result, NLSHolder.UNWANTED_STRINGS);
		return result;
	}	
	
	private void checkKey(String key, RefactoringStatus result, String[] unwantedStrings){
		if (key == null)
			result.addFatalError(NLSMessages.getString("NLSrefactoring.null")); //$NON-NLS-1$

		if (key.startsWith("!") || key.startsWith("#")){
			Context context= new StringContext(key, new SourceRange(0, 0));
			result.addWarning("Keys should not start with chartacters '!' or '#'", context);
		}	
			
		if ("".equals(key.trim())) //$NON-NLS-1$
			result.addFatalError(NLSMessages.getString("NLSrefactoring.empty")); //$NON-NLS-1$
		
		//feature in resource bundle - does not work properly if keys have ":"
		for (int i= 0; i < unwantedStrings.length; i++){
			if (key.indexOf(unwantedStrings[i]) != -1)
				result.addError(NLSMessages.getString("NLSrefactoring.key") + key + NLSMessages.getString("NLSrefactoring.should_not_contain") + "'" + unwantedStrings[i] + "'"); //$NON-NLS-4$ //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
		}
	}
	
	private boolean willCreateAccessorClass() throws JavaModelException{
		if (!fCreateAccessorClass)
			return false;
		if (NLSSubstitution.countItems(fNlsSubs, NLSSubstitution.TRANSLATE) == 0)
			return false;
		if (getPackage().getCompilationUnit(getAccessorCUName()).exists())
			return false;
		if (typeNameExistsInPackage(getPackage(), fAccessorClassName))
			return false;
		return (! Checks.resourceExists(getAccessorCUPath()));
	}
	
	private boolean willModifySource(){
		if (NLSSubstitution.countItems(fNlsSubs, NLSSubstitution.SKIP) != fNlsSubs.length)
			return true;
		if (willAddImportDeclaration())
			return true;
		return false;		
	}
	
	private boolean willModifyPropertyFile(){
		return NLSSubstitution.countItems(fNlsSubs, NLSSubstitution.TRANSLATE) > 0;
	}
	
	private boolean willAddImportDeclaration(){
		if (fAddedImport == null)
			return false;
		if ("".equals(fAddedImport.trim())) //$NON-NLS-1$
			return false;	
		if (getCu().getImport(fAddedImport).exists())
			return false;
		if (NLSSubstitution.countItems(fNlsSubs, NLSSubstitution.TRANSLATE) == 0)	
			return false;
		return true;
		//XXX could	avoid creating the import if already imported on demand
	}
	
	// --- changes
	
	/**
	 * @see IRefactoring#createChange(IProgressMonitor)
	 */
	public IChange createChange(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask("", 3); //$NON-NLS-1$
			CompositeChange builder= new CompositeChange();
			
			if (willModifySource())
				builder.add(createSourceModification());
			pm.worked(1);
			
			if (willModifyPropertyFile())
				builder.add(createPropertyFile());
			pm.worked(1);
			
			if (willCreateAccessorClass())
				builder.add(createAccessorCU());
			pm.worked(1);
			
			return builder;
		} catch (CoreException e){
			throw new JavaModelException(e);	
		} finally {
			pm.done();
		}	
	}

	//---- modified source files
			
	private IChange createSourceModification() throws CoreException{
		String message= NLSMessages.getFormattedString("NLSRefactoring.externalize_strings", //$NON-NLS-1$
							fCu.getElementName());
		TextChange change= new CompilationUnitChange(message, fCu); 
		for (int i= 0; i < fNlsSubs.length; i++){
			addNLS(fNlsSubs[i], change);
		}
		if (willAddImportDeclaration())
			addImportDeclaration(change);
		return change;
	}
	
	private void addImportDeclaration(TextChange builder) throws JavaModelException{
		IImportContainer importContainer= getCu().getImportContainer();
		int start;
		if (!importContainer.exists()){
			String packName= ((IPackageFragment)getCu().getParent()).getElementName();
			IPackageDeclaration packageDecl= getCu().getPackageDeclaration(packName);
			if (!packageDecl.exists())
				start= 0;
			else{
				ISourceRange sr= packageDecl.getSourceRange();
				start= sr.getOffset() + sr.getLength() - 1;
			}	
		} else{
			ISourceRange sr= importContainer.getSourceRange();
			start= sr.getOffset() + sr.getLength() - 1;
		}	
			
		String newImportText= fgLineDelimiter + "import " + fAddedImport + ";"; //$NON-NLS-2$ //$NON-NLS-1$
		String name= NLSMessages.getString("NLSrefactoring.add_import_declaration") + fAddedImport; //$NON-NLS-1$
		builder.addTextEdit(name, SimpleTextEdit.createInsert(start + 1, newImportText));
	}

	private void addNLS(NLSSubstitution sub, TextChange builder){
		TextRegion position= sub.value.getPosition();
		String resourceGetter= createResourceGetter(sub.key);
		String text= NLSMessages.getString("NLSrefactoring.extrenalize_string") + sub.value.getValue(); //$NON-NLS-1$
		if (sub.task == NLSSubstitution.TRANSLATE){
			builder.addTextEdit(text, SimpleTextEdit.createReplace(position.getOffset(), position.getLength(), resourceGetter));
		}	
		if (sub.task != NLSSubstitution.SKIP){
			NLSElement element= sub.value;
			String name= NLSMessages.getString("NLSrefactoring.add_tag")+ text + NLSMessages.getString("NLSrefactoring.for_string") + element.getValue(); //$NON-NLS-2$ //$NON-NLS-1$
			builder.addTextEdit(name, createAddTagChange(element));
		}	
	}
	
	//XXX extremelly inefficient way to do it
	private NLSLine findLine(NLSElement element){
		for(int i= 0; i < fLines.length; i++){
			NLSElement[] lineElements= fLines[i].getElements();
			for (int j= 0; j < lineElements.length; j++){
				if (lineElements[j].equals(element))
					return fLines[i];
			}		
		}
		return null;
	}
	
	private int computeIndexInLine(NLSElement element, NLSLine line){
		for (int i= 0; i < line.size(); i++){
			if (line.get(i).equals(element))
				return i;
		};
		Assert.isTrue(false, "element not found in line"); //$NON-NLS-1$
		return -1;
	}
	
	private int computeTagIndex(NLSElement element){
		NLSLine line= findLine(element);
		Assert.isNotNull(line, "line not found for:" + element); //$NON-NLS-1$
		return computeIndexInLine(element, line) + 1; //tags are 1 based
	}
		
	private String createTagText(NLSElement element) {
		return " " + NLSElement.createTagText(computeTagIndex(element)); //$NON-NLS-1$
	}
	
	private TextEdit createAddTagChange(NLSElement element){
		int offset= element.getPosition().getOffset(); //to be changed
		int length= 0;	
		String text = createTagText(element);
		return new AddNLSTagEdit(offset, length, text);
	}
	
	private String createResourceGetter(String key){
		//we just replace the first occurrence of KEY in the pattern
		StringBuffer buff= new StringBuffer(fCodePattern);
		int i= fCodePattern.indexOf(KEY);
		if (i != -1)
			buff.replace(i, i + KEY.length(), "\"" + key + "\""); //$NON-NLS-2$ //$NON-NLS-1$
		return buff.toString();
	}

	//---- resource bundle file
	
	private IChange createPropertyFile() throws JavaModelException{
		if (! propertyFileExists())
			return new CreateTextFileChange(getPropertyFilePath(), createPropertyFileSource());
			
		String name= NLSMessages.getString("NLSrefactoring.Append_to_property_file") + getPropertyFilePath(); //$NON-NLS-1$
		TextChange tfc= new TextFileChange(name, getPropertyFile());
		
		StringBuffer old= new StringBuffer(getOldPropertyFileSource());

		if (needsLineDelimiter(old))
			tfc.addTextEdit(NLSMessages.getString("NLSRefactoring.add_line_delimiter"), SimpleTextEdit.createInsert(old.length(), fgLineDelimiter)); //$NON-NLS-1$
		
		for (int i= 0; i < fNlsSubs.length; i++){
			if (fNlsSubs[i].task == NLSSubstitution.TRANSLATE){
				if (fNlsSubs[i].putToPropertyFile){
					String entry= createEntry(fNlsSubs[i].value, fNlsSubs[i].key).toString();
					String message= NLSMessages.getFormattedString("NLSRefactoring.add_entry", //$NON-NLS-1$
										fNlsSubs[i].key);
					tfc.addTextEdit(message, SimpleTextEdit.createInsert(old.length(), entry));
				}	
			}	
		}	
		return tfc;
	}

	private IFile getPropertyFile() throws JavaModelException {
		return ((IFile)ResourcesPlugin.getWorkspace().getRoot().findMember(getPropertyFilePath()));
	}
	
	private String createPropertyFileSource() throws JavaModelException{
		StringBuffer sb= new StringBuffer();
		sb.append(getOldPropertyFileSource());
		if (needsLineDelimiter(sb))
			sb.append(fgLineDelimiter);
		for (int i= 0; i < fNlsSubs.length; i++){
			if (fNlsSubs[i].task == NLSSubstitution.TRANSLATE){
				if (fNlsSubs[i].putToPropertyFile)		
					sb.append(createEntry(fNlsSubs[i].value, fNlsSubs[i].key));
			}	
		}	
		return sb.toString();
	}
	
	//heuristic only
	private static boolean needsLineDelimiter(StringBuffer sb){
		if (sb.length() == 0)
			return false;
		String s= sb.toString();
		int lastDelimiter= s.lastIndexOf(fgLineDelimiter);
		if (lastDelimiter == -1)
			return true;
		if ("".equals(s.substring(lastDelimiter).trim())) //$NON-NLS-1$
			return false;
		return true;	
	}
	
	private String getOldPropertyFileSource() throws JavaModelException{
		if (! propertyFileExists())
			return ""; //$NON-NLS-1$
		
		//must read the whole contents - don't want to lose comments etc.
		InputStream is= getPropertyFileInputStream();
		String s= NLSUtil.readString(is);
		return s == null ? "": s; //$NON-NLS-1$
	}
	
	private StringBuffer createEntry(NLSElement element, String key){
		StringBuffer sb= new StringBuffer();
		sb.append(key)
		  .append("=") //$NON-NLS-1$
		  .append(convertToPropertyValue(removeQuotes(element.getValue())))
		  .append(fgLineDelimiter);
		return sb;
	}
	
	/*
	 * see 21.6.7 of the spec
	 */
	private static String convertToPropertyValue(String v){
		int firstNonWhiteSpace=findFirstNonWhiteSpace(v);
		if (firstNonWhiteSpace == 0)
			return v;	
		return escapeEachChar(v.substring(0, firstNonWhiteSpace), '\\') + v.substring(firstNonWhiteSpace);
	}
	
	private static String escapeEachChar(String s, char escapeChar){
		char[] chars= new char[s.length() * 2];
		
		for (int i= 0; i < s.length(); i++){
			chars[2*i]= escapeChar;
			chars[2*i + 1]= s.charAt(i);
		}
		return new String(chars);
	}
	
	/**
	 * returns the length if only whitespaces
	 */
	private static int findFirstNonWhiteSpace(String s){
		for (int i= 0; i < s.length(); i++){
			if (! Character.isWhitespace(s.charAt(i)))
				return i;
		}		
		return s.length();
	}
	
	public static String removeQuotes(String s){
			Assert.isTrue(s.startsWith("\"") && s.endsWith("\"")); //$NON-NLS-2$ //$NON-NLS-1$
			return s.substring(1, s.length() - 1);
	} 

	// ------------ accessor class creation
	
	private IChange createAccessorCU() throws JavaModelException{
		return new CreateTextFileChange(getAccessorCUPath(), createAccessorCUSource());	
	} 
	
	private IPackageFragment getPackage(){
		 return (IPackageFragment)fCu.getParent();
	}
		
	private static boolean typeNameExistsInPackage(IPackageFragment pack, String name) throws JavaModelException{
		Assert.isTrue(pack.exists(), "package must exist"); //$NON-NLS-1$
		Assert.isTrue(!pack.isReadOnly(), "package must not be read-only"); //$NON-NLS-1$
		/*
		 * ICompilationUnit.getType expects simple name
		 */  
		if (name.indexOf(".") != -1) //$NON-NLS-1$
			name= name.substring(0, name.indexOf(".")); //$NON-NLS-1$
		ICompilationUnit[] cus= pack.getCompilationUnits();
		for (int i= 0; i < cus.length; i++){
			if (cus[i].getType(name).exists())
				return true;
		}
		return false;
	}
	
	public void setCreateAccessorClass(boolean create){
		fCreateAccessorClass= create;
	}
	
	public boolean getCreateAccessorClass(){
		return fCreateAccessorClass;
	}
	
	public String getAccessorClassName(){
		return fAccessorClassName;
	}
	
	public void setAccessorClassName(String name){
		fAccessorClassName= name;
		Assert.isNotNull(name);
	}
	
	private String getAccessorCUName(){
		return fAccessorClassName + ".java"; //$NON-NLS-1$
	}
	
	private IPath getAccessorCUPath() throws JavaModelException{
		IPath cuName= new Path(fCu.getElementName());
		return ResourceUtil.getResource(fCu).getFullPath()
						  .removeLastSegments(cuName.segmentCount())
						  .append(getAccessorCUName());
	}
	
	//--bundle class source creation
	private String createAccessorCUSource() throws JavaModelException{
		return ToolFactory.createDefaultCodeFormatter(getFormatterOptions()).format(getUnformattedSource(), 0, (int[])null, null);
	}

	private String getUnformattedSource() throws JavaModelException {
		return new StringBuffer()
			.append(createPackageDeclaration())
			.append(fgLineDelimiter)
			.append(createImports())
			.append(fgLineDelimiter)
			.append(createClass())
			.toString();
	}

	private static Map getFormatterOptions() {
		Map formatterOptions= JavaCore.getOptions();
		formatterOptions.put(JavaCore.FORMATTER_LINE_SPLIT, "0"); //$NON-NLS-1$
		return formatterOptions;
	}
	
	private StringBuffer createPackageDeclaration(){
		IPackageFragment pack= getPackage();
		if (pack.isDefaultPackage())
			return new StringBuffer();

		StringBuffer buff= new StringBuffer();
		buff.append("package ") //$NON-NLS-1$
			.append(pack.getElementName())
			.append(";") //$NON-NLS-1$
			.append(fgLineDelimiter);
		return buff;
	}
	
	private StringBuffer createImports(){
		StringBuffer buff= new StringBuffer();
		buff.append("import java.util.MissingResourceException;").append(fgLineDelimiter) //$NON-NLS-1$
			.append("import java.util.ResourceBundle;").append(fgLineDelimiter); //$NON-NLS-1$
		return buff;
	}
	
	private StringBuffer createClass() throws JavaModelException{
		String ld= fgLineDelimiter; //want shorter name
		StringBuffer b= new StringBuffer();
		//XXX should the class be public?
		b.append("public class ").append(fAccessorClassName).append(" {").append(ld) //$NON-NLS-2$ //$NON-NLS-1$
		 .append(ld)
		 .append("private static final String ") //$NON-NLS-1$
		 .append(getBundleStringName())
		 .append("= \"") //$NON-NLS-1$
		 .append(getResourceBundleName()).append("\";").append(NLSElement.createTagText(1)).append(ld) //$NON-NLS-1$
		 .append(ld)
		 .append("private static final ResourceBundle ") //$NON-NLS-1$
		 .append(getResourceBundleConstantName())
		 .append("= ResourceBundle.getBundle(") //$NON-NLS-1$
		 .append(getBundleStringName())
		 .append(");") //$NON-NLS-1$
		 .append(ld)
		 .append(ld)
		 .append(createConstructor())
		 .append(ld)
		 .append(createGetStringMethod())
		 .append("}").append(ld); //$NON-NLS-1$
		return b;
	}
	
	private static String getBundleStringName(){
		return "BUNDLE_NAME";	//$NON-NLS-1$
	}
	
	private static String getResourceBundleConstantName(){
		return "RESOURCE_BUNDLE";//$NON-NLS-1$
	}
	
	private StringBuffer createConstructor(){
		String ld= fgLineDelimiter; //want shorter name
		StringBuffer b= new StringBuffer();
		b.append("private ").append(fAccessorClassName).append("() {").append(ld) //$NON-NLS-2$ //$NON-NLS-1$
		 .append("}").append(ld); //$NON-NLS-1$
		return b;
	}
	
	private StringBuffer createGetStringMethod(){
		String ld= fgLineDelimiter; //want shorter name
		StringBuffer b= new StringBuffer();
		b.append("public static String getString(String key) {").append(ld) //$NON-NLS-1$
		 .append("try {").append(ld) //$NON-NLS-1$
		 .append("return ") //$NON-NLS-1$
		 .append(getResourceBundleConstantName())
		 .append(".getString(key);").append(ld) //$NON-NLS-1$
		 .append("} catch (MissingResourceException e) {").append(ld) //$NON-NLS-1$
		 .append("return '!' + key + '!';").append(ld) //$NON-NLS-1$
		 .append("}").append(ld) //$NON-NLS-1$
		 .append("}").append(ld); //$NON-NLS-1$
		return b;
	}
	
	//together with the .properties extension
	private String getPropertyFileName() throws JavaModelException{
		return getPropertyFilePath().lastSegment();
	}
	
	//extension removed
	private String getPropertyFileSimpleName() throws JavaModelException{
		String fileName= getPropertyFileName();
		return fileName.substring(0, fileName.indexOf(PROPERTY_FILE_EXT));
	}
	
	
	private String getResourceBundleName() throws JavaModelException{
		//remove filename.properties
		IResource res= ResourcesPlugin.getWorkspace().getRoot().findMember(getPropertyFilePath().removeLastSegments(1));
		if (res != null && res.exists()){
			IJavaElement el= JavaCore.create(res);
			if (el instanceof IPackageFragment){
				IPackageFragment p= (IPackageFragment)el;
				if (p.isDefaultPackage())
					return getPropertyFileSimpleName();
				return p.getElementName() + "." + getPropertyFileSimpleName(); //$NON-NLS-1$
			}
		}
		//XXX can we get here?
		IPackageFragment pack= getPackage();
		if (pack.isDefaultPackage())
			return fProperyFileName;
		return pack.getElementName() + "." + fProperyFileName; //$NON-NLS-1$
	}
	
	//-----------
	
	private static class AddNLSTagEdit extends SimpleTextEdit{
		
		AddNLSTagEdit(int offset, int length, String newText){
			super(offset, length, newText);
		}
		
		private AddNLSTagEdit(TextRange range, String text) {
			super(range, text);
		}
		
		/* non Java-doc
		 * @see TextEdit#getCopy
		 */
		public TextEdit copy() {
			return new AddNLSTagEdit(getTextRange().copy(), getText());
		}	
		
		/* non Java-doc
		 * @see TextEdit#connect
		 */
		public void connect(TextBufferEditor editor) throws CoreException {
			TextBuffer buffer= editor.getTextBuffer();
			TextRange range= getTextRange();
			int offset= range.getOffset();
			int lineEndOffset= getLineEndOffset(buffer, range.getOffset());
			if (lineEndOffset != -1)
				offset= lineEndOffset;
			setTextRange(new TextRange(offset, range.getLength()));	
		}
		
		private int getLineEndOffset(TextBuffer buffer, int offset){
			int line= buffer.getLineOfOffset(offset);
			if (line != -1){
				TextRegion info= buffer.getLineInformation(line);
				return info.getOffset() + info.getLength();
			} 
			return -1;
		};
	}
}