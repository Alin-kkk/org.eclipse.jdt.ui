package org.eclipse.jdt.ui.tests.refactoring.infra;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;

import org.eclipse.jdt.internal.corext.refactoring.SourceRange;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;

public class TextRangeUtil {

	//no instances
	private TextRangeUtil(){}
	
	public static ISourceRange getSelection(ICompilationUnit cu, int startLine, int startColumn, int endLine, int endColumn) throws Exception{
		int offset= getOffset(cu, startLine, startColumn);
		int end= getOffset(cu, endLine, endColumn);
		return new SourceRange(offset, end - offset);
	}

	public static int getOffset(ICompilationUnit cu, int line, int column) throws Exception{
		TextBuffer tb= TextBuffer.create(cu.getSource());
		int r= tb.getLineInformation(line - 1).getOffset();
		
		int lineTabCount= calculateTabCountInLine(tb.getLineContent(line - 1), column);		
		r += (column - 1) - (lineTabCount * getTabWidth()) + lineTabCount;
		return r ;
	}
	
	private static final int getTabWidth(){
		return 4;
	}
	
	public static int calculateTabCountInLine(String lineSource, int lastCharOffset){
		int acc= 0;
		int charCount= 0;
		for(int i= 0; charCount < lastCharOffset - 1; i++){
			if ('\t' == lineSource.charAt(i)){
				acc++;
				charCount += getTabWidth();
			}	else
				charCount += 1;
		}
		return acc;
	}

}
