package org.eclipse.jdt.internal.ui.reorg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.refactoring.Assert;

class SourceReferenceUtil {
	
	//no instances
	private SourceReferenceUtil(){}
	
	static IFile getFile(ISourceReference ref) throws JavaModelException{
		ICompilationUnit unit= getCompilationUnit(ref);
		if (! unit.isWorkingCopy()) 
			return (IFile)unit.getCorrespondingResource();
		return (IFile)unit.getOriginalElement().getCorrespondingResource();
	}
	
	static ICompilationUnit getCompilationUnit(ISourceReference o){
		Assert.isTrue(! (o instanceof IClassFile));
		
		if (o instanceof IMember)
			return ((IMember)o).getCompilationUnit();
		if (o instanceof IImportDeclaration){
			IImportDeclaration im= (IImportDeclaration)o;
			return (ICompilationUnit)im.getParent().getParent();
		}
		if (o instanceof ICompilationUnit)
			return (ICompilationUnit)o;
	   
	   return (ICompilationUnit)((IJavaElement)o).getParent();
	}	
	
	private static boolean hasParentInSet(IJavaElement elem, Set set){
		IJavaElement parent= elem.getParent();
		while (parent != null) {
			if (set.contains(parent))	
				return true;
			parent= parent.getParent();	
		}
		return false;
	}
	
	static ISourceReference[] removeAllWithParentsSelected(ISourceReference[] elems){
		Set set= new HashSet(Arrays.asList(elems));
		List result= new ArrayList(elems.length);
		for (int i= 0; i < elems.length; i++) {
			ISourceReference elem= elems[i];
			if (! (elem instanceof IJavaElement))
				result.add(elem);
			else{	
				if (! hasParentInSet(((IJavaElement)elem), set))
					result.add(elem);
			}	
		}
		return (ISourceReference[]) result.toArray(new ISourceReference[result.size()]);
	}
	
	/**
	 * @return IFile -> List of ISourceReference (elements from that file)	
	 */
	static Map groupByFile(ISourceReference[] elems) throws JavaModelException{
		Map map= new HashMap();
		for (int i= 0; i < elems.length; i++) {
			ISourceReference elem= elems[i];
			IFile file= SourceReferenceUtil.getFile(elem);
			if (! map.containsKey(file))
				map.put(file, new ArrayList());
			((List)map.get(file)).add(elem);
		}
		return map;
	}	
}

