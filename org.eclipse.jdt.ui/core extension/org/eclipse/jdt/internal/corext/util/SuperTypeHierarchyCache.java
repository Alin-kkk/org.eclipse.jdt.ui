package org.eclipse.jdt.internal.corext.util;

import java.util.ArrayList;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeHierarchyChangedListener;
import org.eclipse.jdt.core.JavaModelException;

public class SuperTypeHierarchyCache {
	
	private static class HierarchyCacheEntry implements ITypeHierarchyChangedListener {
		
		private ITypeHierarchy fTypeHierarchy;
		
		public HierarchyCacheEntry(ITypeHierarchy hierarchy) {
			fTypeHierarchy= hierarchy;
			fTypeHierarchy.addTypeHierarchyChangedListener(this);
		}
		
		public void typeHierarchyChanged(ITypeHierarchy typeHierarchy) {
			synchronized (fgHierarchyCache) {
				freeHierarchy();
			}
		}

		public ITypeHierarchy getTypeHierarchy() {
			return fTypeHierarchy;
		}
		
		public void freeHierarchy() {
			fTypeHierarchy.removeTypeHierarchyChangedListener(this);
			fgHierarchyCache.remove(this);
		}

	}
	

	private static final int CACHE_SIZE= 5;

	private static ArrayList fgHierarchyCache= new ArrayList(CACHE_SIZE);
	private static int fgCacheHits= 0;
	private static int fgCacheMisses= 0;
	private static int fgMaxCreationTime= 0;
	private static int fgTotCreationTime= 0;	
	
	/**
	 * Get a hierarchy for the given type
	 */
	public static ITypeHierarchy getTypeHierarchy(IType type) throws JavaModelException {
		synchronized (fgHierarchyCache) {
			ITypeHierarchy hierarchy= findTypeHierarchyInCache(type);
			if (hierarchy == null) {
				fgCacheMisses++;
				if (fgHierarchyCache.size() >= CACHE_SIZE) {
					HierarchyCacheEntry entry= (HierarchyCacheEntry) fgHierarchyCache.get(0);
					entry.freeHierarchy();
				}
				int startTime= (int) System.currentTimeMillis();				
				hierarchy= type.newSupertypeHierarchy(null);
				int totTime= (int) System.currentTimeMillis() - startTime;
				if (totTime > fgMaxCreationTime) {
					fgMaxCreationTime= totTime;
				}
				fgTotCreationTime+= totTime;
								
				HierarchyCacheEntry newEntry= new HierarchyCacheEntry(hierarchy);
				fgHierarchyCache.add(newEntry);
			} else {
				fgCacheHits++;
			}
			return hierarchy;
		}
	}

	/**
	 * Check if the given type is in the hierarchy
	 */	
	public static boolean hasInCache(IType type) throws JavaModelException {
		return findTypeHierarchyInCache(type) != null;
	}
	
			
	private static ITypeHierarchy findTypeHierarchyInCache(IType type) {
		for (int i= fgHierarchyCache.size() - 1; i>= 0; i--) {
			HierarchyCacheEntry curr= (HierarchyCacheEntry) fgHierarchyCache.get(i);
			ITypeHierarchy hierarchy= curr.getTypeHierarchy();
			if (!hierarchy.exists()) {
				curr.freeHierarchy();
			} else if (hierarchy.getType().equals(type)) {
				return hierarchy;
			}
		}
		return null;
	}
	
	/**
	 * Gets the number of times the hierarchy could be taken from the hierarchy.
	 * @return Returns a int
	 */
	public static int getCacheHits() {
		return fgCacheHits;
	}
	
	/**
	 * Gets the number of times the hierarchy was build. Used for testing.
	 * @return Returns a int
	 */	
	public static int getCacheMisses() {
		return fgCacheMisses;
	}
	
	public static int getMaxCreationTime() {
		return fgMaxCreationTime;
	}
	
	public static int getAvgCreationTime() {
		return (int) fgTotCreationTime / fgCacheMisses;
	}			

}
