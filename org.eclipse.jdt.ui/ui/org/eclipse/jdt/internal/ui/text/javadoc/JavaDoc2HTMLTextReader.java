package org.eclipse.jdt.internal.ui.text.javadoc;


/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */


import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.jdt.internal.ui.text.SubstitutionTextReader;



/**
 * Processes JavaDoc tags.
 */
public class JavaDoc2HTMLTextReader extends SubstitutionTextReader {
	
	
	static private class Pair {
		String fTag;
		String fContent;
		
		Pair(String tag, String content) {
			fTag= tag;
			fContent= content;
		}
	};
	
	private static final String[] TAGS= new String[] {
		"@author", 		//$NON-NLS-1$
		"@deprecated",	//$NON-NLS-1$
		"@exception",		//$NON-NLS-1$
		"@param",			//$NON-NLS-1$
		"@return", 			//$NON-NLS-1$
		"@see",				//$NON-NLS-1$
		"@serial",			//$NON-NLS-1$
		"@serialData",		//$NON-NLS-1$
		"@serialField",		//$NON-NLS-1$
		"@since",			//$NON-NLS-1$
		"@throws",			//$NON-NLS-1$
		"@version"			//$NON-NLS-1$
	};
	private static final int MAX_TAG_LENGTH= "@serialField".length();//$NON-NLS-1$
	
	private List fParameters;
	private String fReturn;
	private List fExceptions;
	private List fSees;
	private List fRest; // list of Pair objects
	
	public JavaDoc2HTMLTextReader(Reader reader) {
		super(new PushbackReader(reader, MAX_TAG_LENGTH));
	}
	
	private int getTag(StringBuffer buffer) throws IOException {
		int c= nextChar();
		while (c != -1 && Character.isLetter((char) c)) {
			buffer.append((char) c);
			c= nextChar();
		}
		return c;
	}
	
	private int getContent(StringBuffer buffer, char stopChar) throws IOException {
		int c= nextChar();
		while (c != -1 && c != stopChar) {
			buffer.append((char) c);
			c= nextChar();
		}
		return c;
	}
	
	private int getContentUntilNextTag(StringBuffer buffer) throws IOException {
		int c= nextChar();
		boolean tagStarterRead= (c == '@'); //optimization - don't look for tags if @ not read 
		while (c != -1) {
			buffer.append((char) c);
			int endingTagIndex= tagStarterRead ? findEndingTag(buffer) : -1;
			if (endingTagIndex != -1) {
				unread(TAGS[endingTagIndex], buffer);
				return nextChar();
			}	
			c= nextChar();
			tagStarterRead= tagStarterRead || (c == '@');
		}
		return c;
	}
	
	private void unread(String tag, StringBuffer buffer) throws IOException {
		PushbackReader reader= ((PushbackReader) getReader());
		char[] chars= tag.toCharArray();
		for (int i= chars.length -1; i >= 0; i--)
			reader.unread(chars[i]);
		buffer.setLength(buffer.length() - chars.length);
	}
	
	private int findEndingTag(StringBuffer buffer) {
		String s= buffer.toString();
		for (int  i= 0; i < TAGS.length; i++) {
			if (s.endsWith(TAGS[i]))
				return i;			
		}
		return -1;
	}
	
	private String subsituteQualification(String qualification) {
		return qualification.replace('#', '.');
	}
	
	private void printDefinitions(StringBuffer buffer, List list, boolean firstword) {
		Iterator e= list.iterator();
		while (e.hasNext()) {
			String s= (String) e.next();
			buffer.append("<dd>"); //$NON-NLS-1$
			if (!firstword)
				buffer.append(s);
			else {
				buffer.append("<b>"); //$NON-NLS-1$
				
				int i= 0;
				while (i < s.length() && Character.isLetterOrDigit(s.charAt(i))) { ++i; }
				if (i < s.length()) {
					buffer.append(s.substring(0, i));
					buffer.append("</b>"); //$NON-NLS-1$
					buffer.append(s.substring(i));
				} else {
					buffer.append("</b>"); //$NON-NLS-1$
				}
			}
			buffer.append("</dd>"); //$NON-NLS-1$
		}
	}
	
	private void print(StringBuffer buffer, String tag, List elements, boolean firstword) {
		if ( !elements.isEmpty()) {
			buffer.append("<dt>"); //$NON-NLS-1$
			buffer.append(tag);
			buffer.append("</dt>"); //$NON-NLS-1$
			printDefinitions(buffer, elements, firstword);
		}
	}
	
	private void print(StringBuffer buffer, String tag, String content) {
		if  (content != null) {
			buffer.append("<dt>"); //$NON-NLS-1$
			buffer.append(tag);
			buffer.append("</dt>"); //$NON-NLS-1$
			buffer.append("<dd>"); //$NON-NLS-1$
			buffer.append(content);
			buffer.append("</dd>"); //$NON-NLS-1$
		}
	}
	
	private void printRest(StringBuffer buffer) {
		if ( !fRest.isEmpty()) {
			Iterator e= fRest.iterator();
			while (e.hasNext()) {
				Pair p= (Pair) e.next();
				buffer.append("<dt>"); //$NON-NLS-1$
				if (p.fTag != null)
					buffer.append(p.fTag);
				buffer.append("</dt>"); //$NON-NLS-1$
				buffer.append("<dd>"); //$NON-NLS-1$
				if (p.fContent != null)
					buffer.append(p.fContent);
				buffer.append("</dd>"); //$NON-NLS-1$
			}
		}
	}
	
	private String printSimpleTag() {
		StringBuffer buffer= new StringBuffer();
		buffer.append("<dl>"); //$NON-NLS-1$
		print(buffer, JavaDocMessages.getString("JavaDoc2HTMLTextReader.parameters.section"), fParameters, true); //$NON-NLS-1$
		print(buffer, JavaDocMessages.getString("JavaDoc2HTMLTextReader.returns.section"), fReturn); //$NON-NLS-1$
		print(buffer, JavaDocMessages.getString("JavaDoc2HTMLTextReader.throws.section"), fExceptions, false); //$NON-NLS-1$
		print(buffer, JavaDocMessages.getString("JavaDoc2HTMLTextReader.see.section"), fSees, false); //$NON-NLS-1$
		printRest(buffer);
		buffer.append("</dl>"); //$NON-NLS-1$
		
		return buffer.toString();
	}
	
	private void handleTag(String tag, String tagContent) {
		
		tagContent= tagContent.trim();
		
		if ("@param".equals(tag)) //$NON-NLS-1$
			fParameters.add(tagContent);
		else if ("@return".equals(tag)) //$NON-NLS-1$
			fReturn= tagContent;
		else if ("@exception".equals(tag)) //$NON-NLS-1$
			fExceptions.add(tagContent);
		else if ("@throws".equals(tag)) //$NON-NLS-1$
			fExceptions.add(tagContent);
		else if ("@see".equals(tag)) //$NON-NLS-1$
			fSees.add(subsituteQualification(tagContent));
		else if (tagContent != null)
			fRest.add(new Pair(tag, tagContent));
	}
	
	/*
	 * A '@' has been read. Process a jdoc tag
	 */ 			
	private String processSimpleTag() throws IOException {
		
		fParameters= new ArrayList();
		fExceptions= new ArrayList();
		fSees= new ArrayList();
		fRest= new ArrayList();
		
		StringBuffer buffer= new StringBuffer();
		int c= '@';
		while (c != -1) {
		
			buffer.setLength(0);
			buffer.append((char) c);
			c= getTag(buffer);
			String tag= buffer.toString();			
			
			buffer.setLength(0);
			if (c != -1) {
				buffer.append((char) c);
				c= getContentUntilNextTag(buffer);
			}
			
			handleTag(tag, buffer.toString());
		}
		
		return printSimpleTag();
	}
	
	private String printBlockTag(String tag, String tagContent) {
		
		if ("@link".equals(tag) || "@linkplain".equals(tag)) { //$NON-NLS-1$ //$NON-NLS-2$
			
			StringTokenizer tokenizer= new StringTokenizer(tagContent);
			int count= tokenizer.countTokens();
			
			if (count == 1)
				return subsituteQualification(tokenizer.nextToken()); // return  reference part of link
			
			if (count == 2) {
				tokenizer.nextToken(); // skip reference part of link
				return tokenizer.nextToken(); // return label part of link
			}
			
			// invalid syntax
		}
		
		return null;
	}
		
	/*
	 * A '{' has been read. Process a block tag
	 */ 			
	private String processBlockTag() throws IOException {
		
		int c= nextChar();
		
		if (c != '@') {
			StringBuffer buffer= new StringBuffer();
			buffer.append('{');
			buffer.append((char) c);
			return buffer.toString();
		}
		
		StringBuffer buffer= new StringBuffer();
		if (c != -1) {
			
			buffer.setLength(0);
			buffer.append((char) c);
			
			c= getTag(buffer);
			String tag= buffer.toString();
			
			buffer.setLength(0);
			if (c != -1) {
				buffer.append((char) c);
				c= getContent(buffer, '}');
			}
			
			return printBlockTag(tag, buffer.toString());
		}
		
		return null;
	}
	
	/*
	 * @see SubstitutionTextReaderr#computeSubstitution(int)
	 */
	protected String computeSubstitution(int c) throws IOException {
		if (c == '@')
			return processSimpleTag();
		
		if (c == '{')
			return processBlockTag();
			
		return null;
	}
}