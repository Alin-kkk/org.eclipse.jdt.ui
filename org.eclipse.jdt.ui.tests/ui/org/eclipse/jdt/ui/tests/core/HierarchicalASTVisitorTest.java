package org.eclipse.jdt.ui.tests.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;

public class HierarchicalASTVisitorTest extends TestCase {
	private static class TestHierarchicalASTVisitor extends HierarchicalASTVisitor {


/***BEGIN <REGION TO BE UPDATED IN RESPONSE TO ASTNode HEIRARCHY CHANGES>**************************/
	/*******************************************************************************
	 * Whereas the other parts of this test should be relatively static,
	 * this portion of the file must be maintained in response to
	 * changes that occur in the ASTNode heirarchy (and, thus, the ASTVisitor).
	 * Such changes would include addition or removal of node types to or from
	 * the heirarchy, or changes in the superclass/subclass relationships
	 * among node classes.  Such changes necessitate, also, changes in the
	 * HierarchicalASTVisitor itself, whose structure and behaviour this test
	 * verifies.
	 * 
	 * The changes that must be made to this file in response to such changes in
	 * the ASTNode heirarchy are localized here and limited to maintenance of the
	 * following set of visitXX(XX node) implementations and superVisit(XX node).
	 * There should be one such pair for each non-leaf ASTNode descendant class,
	 * including ASTNode itself. 
	 * 
	 *******************************************************************************/
	
		/* Here, each visitXX(..) method (but no visit(..)) method
		 * is overridden to call registerCall(XX.class), and
		 * a void superVisit(XX node) method is provided, which simply
		 * calls super.visitXX(..);
		 * 
		 * (The methods here correspond only to non-leaf classes)
		 */
		
			
		public boolean visitASTNode(ASTNode node) {
			registerCall(ASTNode.class);
			return false;
		}
		public void superVisit(ASTNode node) {
			super.visitASTNode(node);
		}
		
		public boolean visitExpression(Expression node) {
			registerCall(Expression.class);
			return false;
		}
		public void superVisit(Expression node) {
			super.visitExpression(node);
		}
		
		public boolean visitName(Name node) {
			registerCall(Name.class);
			return false;
		}
		public void superVisit(Name node) {
			super.visitName(node);	
		}
		
		public boolean visitBodyDeclaration(BodyDeclaration node) {
			registerCall(BodyDeclaration.class);
			return false;
		}
		public void superVisit(BodyDeclaration node) {
			super.visitBodyDeclaration(node);
		}
		
		public boolean visitType(Type node) {
			registerCall(Type.class);
			return false;
		}
		public void superVisit(Type node) {
			super.visitType(node);
		}
				
		public boolean visitStatement(Statement node) {
			registerCall(Statement.class);
			return false;
		}
		public void superVisit(Statement node) {
			super.visitStatement(node);
		}
				
		public boolean visitVariableDeclaration(VariableDeclaration node) {
			registerCall(VariableDeclaration.class);
			return false;
		}
		public void superVisit(VariableDeclaration node) {
			super.visitVariableDeclaration(node);
		}
/***END <REGION TO BE UPDATED IN RESPONSE TO ASTNode HEIRARCHY CHANGES>**************************/


		/**
		 * Verifies the visitXX(XX) method or visit(XX) method in HierarchicalASTVisitor
		 * calls visitYY(YY), where XX is the name of <code>clazz</code> and 
		 * YY is the name of the superclass of clazz.
		 * 
		 * <code>clazz</code> must be a <b>proper</b> descendant of ASTNode (<code>clazz</code> is not ASTNode).		 */
		private static void checkMethodCallsSuperclassMethod(Class clazz, boolean isLeaf) {
			Assert.isTrue(ASTNode.class.isAssignableFrom(clazz));
			Assert.isTrue(!ASTNode.class.equals(clazz));
			
			TestHierarchicalASTVisitor visitor= new TestHierarchicalASTVisitor();
			visitor._checkMethodCallsSuperclassMethod(clazz, isLeaf);		
		}
	
		/**
		 * This class must have certain methods corresponding to the
		 * ASTNode descendant class <code>clazz</code>.
		 * This method reflectively verifies that they are present.		 */
		private static boolean hasRequiredMethodsForNonLeaf(Class clazz) {
			Assert.isTrue(ASTNode.class.isAssignableFrom(clazz));
			try {
				TestHierarchicalASTVisitor.class.getDeclaredMethod(
				    getNonLeafMethodNameFor(clazz),
				    new Class[] { clazz }
				);
				TestHierarchicalASTVisitor.class.getDeclaredMethod(
				    "superVisit",
				    new Class[] { clazz }
				);
			} catch (NoSuchMethodException e) {
				return false;
			}
			return true;
		}

		private Class fNodeClassForCalledMethod= null;
		
		private void _checkMethodCallsSuperclassMethod(Class clazz, boolean isLeaf) {
			/* Invoke a method which will result in the execution of
			 * HierarchicalASTVisitor's implementation of visitXX(XX) or
			 * visit(XX), where XX is the name of clazz.
			 * 
			 * If clazz is a leaf, we can invoke visit(XX) directly.
			 * Otherwise, we must invoke superVisit(XX), (in this class)
			 * which calls super.visitXX(XX), since visitXX(XX) is
			 * overridden in this class.
			 * 
			 * The parameter passed to visit(XX) or superVisit(XX)
			 * is null.  If there ever was any requirement that the
			 * parameter to visit(XX) or visitXX(XX) methods, be
			 * non-null, we would simply have to reflectively instantiate an
			 * appropriately typed node.
			 */
			try {
				Method method= 
				    TestHierarchicalASTVisitor.class.getMethod(
				        isLeaf ? getLeafMethodNameFor(clazz) : "superVisit",
				        new Class[] { clazz }
					);
				method.invoke(this, new Object[] { null });
			} catch (NoSuchMethodException e) {
				/* This should have already been discovered by
				 * hasRequiredMethodsForNonLeaf(..)
				 */
				 e.printStackTrace();
				Assert.isTrue(false); 
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				Assert.isTrue(false);
			} catch (InvocationTargetException e) {
				e.printStackTrace();
				Assert.isTrue(false);
			}
			
			/*
			 * Verify that the method invokation caused
			 * a call to visitYY(YY), where YY is the
			 * name of the superclass of clazz.
			 * (Also, verify that no other visitYY(YY)
			 * method was called).			 */
			checkSuperclassMethodCalled(clazz, isLeaf);
		}
		private void checkSuperclassMethodCalled(Class clazz, boolean isLeaf) {
			Assert.isNotNull(clazz.getSuperclass());
			/**
			 * This class' implementations of the visitYY(YY) methods cause
			 * fNodeClassForCalledMethod to be set to YY.class.
			 * Such an implementation will be the one executed when
			 * a visitXX(XX) or visit(XX) implementation in HierarchicalASTVisitor
			 * calls the visitYY(YY) method corresponding to XX's superclass, YY.
			 * We check here that fNodeClassForCalledMethod was set
			 * to the superclass of clazz.			 */
			assertTrue(
			    getSuperMethodNotCalledMessageFor(clazz, isLeaf),
			    clazz.getSuperclass().equals(fNodeClassForCalledMethod)
			);
		}
		private String getSuperMethodNotCalledMessageFor(Class clazz, boolean isLeaf) {
			return   getMethodSignatureFor(clazz, isLeaf)
			        + " in HierarchicalASTVisitor should call "
			        + getMethodSignatureFor(clazz.getSuperclass(), false)
			        + ", the visitor method for its superclass.";
		}

		private void registerCall(Class nodeClassForMethod) {
			assertNull("The invocation of a visit(XX) or visitXX(XX) method in HierarchicalASTVisitor has caused more than one other visitXX(XX) method to be called.  Every visit(XX) or visitXX(XX) method in HierarchicalASTVisitor, except visitASTNode(..), should simply call visitYY(YY), where YY is the superclass of XX.", fNodeClassForCalledMethod);
			fNodeClassForCalledMethod= nodeClassForMethod;
		}
	}
	
	
	private static final Class THIS_CLASS= HierarchicalASTVisitorTest.class;
	
	private Set fLeaves;
	
	public HierarchicalASTVisitorTest(String name) {
		super(name);
	}
		
	public static Test suite() {
		return new TestSuite(THIS_CLASS);
	}
	
	public void test() {
		fLeaves= getLeafASTNodeDescendants();
		Set allASTNodeDescendants= computeAllDescendantsFromLeaves(
		                                    fLeaves.iterator(),
		                                    ASTNode.class
		                                );
		
		checkAllMethodsForHeirarchyExist(allASTNodeDescendants.iterator());
		checkMethodsCallSuperclassMethod(allASTNodeDescendants.iterator());
	}
	
	private boolean isLeaf(Class clazz) {
		return fLeaves.contains(clazz);	
	}
	
	/**
	 * For both HierarchicalASTVisitor and a subsequent part of this test to be correct,
	 * HierarchicalASTVisitor and TestHierarchicalASTVisitor must declare certain methods,
	 * each one corresponding to a class in the ASTNode heirarchy.  Specifically, 
	 * HierarchicalASTVisitor must declare a method corresponding to each class in the heirarchy,
	 * whereas TestHierarchicalASTVisitor must declare a pair of methods for each non-leaf
	 * class in the ASTNode heirarchy.
	 * 
	 * This method verifies that these required methods exist, and suggests the updates
	 * that are needed to properly maintain the set of methods.	 */
	private void checkAllMethodsForHeirarchyExist(Iterator heirarchyClasses) {
		while(heirarchyClasses.hasNext()) {
			Class descendant= (Class) heirarchyClasses.next();
			checkHeirarchicalASTVisitorMethodExistsFor(descendant);
			if(!isLeaf(descendant))
				assertTrue("This test must be updated, since TestHierarchicalASTVisitor, a class declared within this test class, is missing a method corresponding to non-leaf node class " + getSimpleName(descendant),
				           TestHierarchicalASTVisitor.hasRequiredMethodsForNonLeaf(descendant));
		}		
	}
	
	/**
	 * All visit(XX) and visitXX(XX) implementations in HierarchicalASTVisitor, each corresponding to
	 * a class XX, must call visitYY(YY), where YY is the superclass of YY, unless XX is ASTNode.
	 * This method verifies this using reflection and a contrived subclass of HierarchicalASTVisitor,
	 * TestHierarchicalASTVisitor.	 */
	private void checkMethodsCallSuperclassMethod(Iterator heirarchyClasses) {
		while(heirarchyClasses.hasNext()) {
			Class descendant= (Class) heirarchyClasses.next();
			if(!ASTNode.class.equals(descendant))
				TestHierarchicalASTVisitor.checkMethodCallsSuperclassMethod(descendant, isLeaf(descendant));
		}	
	}
	
	private void checkHeirarchicalASTVisitorMethodExistsFor(Class nodeClass) {
		try {
			Assert.isTrue(ASTNode.class.isAssignableFrom(nodeClass));
			HierarchicalASTVisitor.class.getDeclaredMethod(
			    getMethodNameFor(nodeClass),
			    new Class[] { nodeClass }
			);
		} catch (NoSuchMethodException e) {
			String signature= getMethodNameFor(nodeClass) + "(" + getSimpleName(nodeClass) + ")";
			assertTrue("HierarchicalASTVisitor must be updated to reflect a change in the ASTNode heirarchy.  No method " + signature + " was found in HierarchicalASTVisitor.", false);
		}
	}
	
	private String getMethodNameFor(Class nodeClass) {
		return getMethodNameFor(nodeClass, isLeaf(nodeClass));
	}
	private static String getMethodNameFor(Class nodeClass, boolean isLeaf) {
		if(isLeaf)
			return getLeafMethodNameFor(nodeClass);
		else
			return getNonLeafMethodNameFor(nodeClass);
	}
	private static String getLeafMethodNameFor(Class nodeClass) {
		return "visit";
	}
	private static String getNonLeafMethodNameFor(Class nodeClass) {
		return "visit" + getSimpleName(nodeClass);	
	}
	
	private static String getSimpleName(Class clazz) {
		String qualified= clazz.getName();
		return qualified.substring(qualified.lastIndexOf('.') + 1);
	}
	
	private static String getMethodSignatureFor(Class clazz, boolean isLeaf) {
		return getMethodNameFor(clazz, isLeaf) + "(" + getSimpleName(clazz) + ")";
	}
	
	/**
	 * Finds the set of all descendants of <code>root</code> which are not proper descendants
	 * of a class in the sequence <code>leaves</code>.  This will include <code>root</code>
	 * and all the elements of <code>leaves</code>.	 */
	private static Set computeAllDescendantsFromLeaves(Iterator leaves, Class root) {
		Set all= new HashSet();
		while(leaves.hasNext()) {
			Class leaf= (Class) leaves.next();
			addAllAncestorsInclusive(leaf, root, all);
		}
		return all;
	}
	
	private static void addAllAncestorsInclusive(Class from, Class to, Set set) {
		Assert.isTrue(to.isAssignableFrom(from));
		Assert.isTrue(!from.isInterface());
		Assert.isTrue(!to.isInterface());
		
		Class ancestor= from;
		while(!ancestor.equals(to)) {
			set.add(ancestor);
			ancestor= ancestor.getSuperclass();
			if(ancestor == null) {
				Assert.isTrue(false);
				/* not expected, given assertions passed above */
			}
		}
		set.add(to);
	}
	
	/**
	 * Returns all the leaf node classes (classes with no subclasses) in the
	 * ASTNode heirarchy. Since every non-leaf ASTNode descendant (incl. ASTNode)
	 * is abstract, the set of leaf ASTNode descendants is the set of concrete
	 * ASTNode descendants.
	 * 
	 * If ASTVisitor is maintained, this set will be the set of classes for which
	 * ASTVisitor has visit(..) methods.  We use this property to compute the set,
	 * which means that we are as up-to-date as ASTVisitor (to be more
	 * "up-to-date" would be to require something that HierarchicalASTVisitor would,
	 * semantically, not be able to provide anyway!).	 */
	private static Set getLeafASTNodeDescendants() {
		Set result= new HashSet();
		Method[] methods= ASTVisitor.class.getMethods();
		for(int i= 0; i < methods.length; i++) {
			Method method= methods[i];
			if(isVisitMethod(method)) {
				result.add(method.getParameterTypes()[0]);
			}
		}
		return result;
	}
	private static boolean isVisitMethod(Method method) {
		if(!"visit".equals(method.getName())) return false;
		
		Class[] parameters= method.getParameterTypes();
		return    parameters.length == 1 
		        && ASTNode.class.isAssignableFrom(parameters[0]);
	}
}
