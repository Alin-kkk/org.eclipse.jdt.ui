package org.eclipse.jdt.internal.ui.text.javadoc;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.IJavaColorConstants;

import org.eclipse.jdt.internal.ui.text.AbstractJavaScanner;
import org.eclipse.jdt.internal.ui.text.JavaWhitespaceDetector;




/**
 * A rule based JavaDoc scanner.
 */
public final class JavaDocScanner extends AbstractJavaScanner {
		
		
	/**
	 * A key word detector.
	 */
	static class JavaDocWordDetector implements IWordDetector {

		/**
		 * @see IWordDetector#isWordStart
		 */
		public boolean isWordStart(char c) {
			return (c == '@');
		}

		/**
		 * @see IWordDetector#isWordPart
		 */
		public boolean isWordPart(char c) {
			return Character.isLetter(c);
		}
	};
	
	class TagRule extends SingleLineRule {
		
		/*
		 * @see SingleLineRule
		 */
		public TagRule(IToken token) {
			super("<", ">", token, (char) 0); //$NON-NLS-2$ //$NON-NLS-1$
		}
		
		/*
		 * @see SingleLineRule 
		 */
		public TagRule(IToken token, char escapeCharacter) {
			super("<", ">", token, escapeCharacter); //$NON-NLS-2$ //$NON-NLS-1$
		}
		
		private IToken checkForWhitespace(ICharacterScanner scanner) {
			
			try {
				
				char c= getDocument().getChar(getTokenOffset() + 1);
				if (!Character.isWhitespace(c)) 
					return fToken;
					
			} catch (BadLocationException x) {
			}
			
			return Token.UNDEFINED;
		}
				
		/*
		 * @see PatternRule#evaluate(ICharacterScanner)
		 */
		public IToken evaluate(ICharacterScanner scanner) {
			IToken result= super.evaluate(scanner);
			if (result == fToken)
				return checkForWhitespace(scanner);
			return result;
		}
	};
	
	
	private static String[] fgKeywords= {"@author", "@deprecated", "@exception", "@param", "@return", "@see", "@serial", "@serialData", "@serialField", "@since", "@throws", "@version"}; //$NON-NLS-12$ //$NON-NLS-11$ //$NON-NLS-10$ //$NON-NLS-7$ //$NON-NLS-9$ //$NON-NLS-8$ //$NON-NLS-6$ //$NON-NLS-5$ //$NON-NLS-4$ //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
	
	private static String[] fgTokenProperties= {
		IJavaColorConstants.JAVADOC_KEYWORD,
		IJavaColorConstants.JAVADOC_TAG,
		IJavaColorConstants.JAVADOC_LINK,
		IJavaColorConstants.JAVADOC_DEFAULT
	};			
	
	public JavaDocScanner(IColorManager manager, IPreferenceStore store) {
		super(manager, store);
		initialize();
	}
	
	public IDocument getDocument() {
		return fDocument;
	}
	
	/*
	 * @see AbstractJavaScanner#getTokenProperties()
	 */
	protected String[] getTokenProperties() {
		return fgTokenProperties;
	}

	/*
	 * @see AbstractJavaScanner#createRules()
	 */
	protected List createRules() {
		
		List list= new ArrayList();
		
		// Add rule for tags.
		Token token= getToken(IJavaColorConstants.JAVADOC_TAG);
		list.add(new TagRule(token));
		
		
		// Add rule for links.
		token= getToken(IJavaColorConstants.JAVADOC_LINK);
		list.add(new SingleLineRule("{@link", "}", token)); //$NON-NLS-2$ //$NON-NLS-1$
		
		
		// Add generic whitespace rule.
		list.add(new WhitespaceRule(new JavaWhitespaceDetector()));
		
		
		// Add word rule for keywords.
		token= getToken(IJavaColorConstants.JAVADOC_DEFAULT);
		WordRule wordRule= new WordRule(new JavaDocWordDetector(), token);
		
		token= getToken(IJavaColorConstants.JAVADOC_KEYWORD);
		for (int i= 0; i < fgKeywords.length; i++)
			wordRule.addWord(fgKeywords[i], token);
		list.add(wordRule);
		
		setDefaultReturnToken(getToken(IJavaColorConstants.JAVADOC_DEFAULT));
		return list;
	}
}


