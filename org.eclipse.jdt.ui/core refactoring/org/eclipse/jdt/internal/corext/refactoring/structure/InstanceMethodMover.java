/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportEdit;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.dom.JavaElementMapper;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.Context;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusCodes;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusEntry;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.InstanceMethodMover.Method.Delegation;
import org.eclipse.jdt.internal.corext.refactoring.structure.InstanceMethodMover.Method.MethodEditSession;
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInstanceMethodRefactoring.INewReceiver;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.textmanipulation.MultiTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.RangeMarker;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBufferEditor;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Strings;

class InstanceMethodMover {
	
	private static interface IParameter {
		public ITypeBinding getType();
		
		public String getName();	
	}
	
	private static abstract class NewReceiver implements MoveInstanceMethodRefactoring.INewReceiver {
		private final IJavaProject fDependentProject;
		private final CodeGenerationSettings fCodeGenSettings;
		
		//cache:
		private IType fModelClass;
		
		private NewReceiver(IJavaProject dependentProject, CodeGenerationSettings codeGenSettings) {
			Assert.isNotNull(dependentProject);
			Assert.isNotNull(codeGenSettings);
			
			fDependentProject= dependentProject;
			fCodeGenSettings= codeGenSettings;
		}
		
		public abstract String getName();

		public ITypeBinding getType() {
			return getReceiverClass();	
		}

		public boolean isField() {
			return false;
		}

		public boolean isParameter() {
			return false;
		}

		protected abstract ITypeBinding getReceiverClass();

		protected ICompilationUnit getReceiverClassCU() throws JavaModelException {
			return JavaModelUtil.toWorkingCopy(getReceiverModelClass().getCompilationUnit());
		}
		
		abstract Expression[] getReferencesIn(Method method);
		
		abstract Expression createReferenceForContext(Method context);
		
		IChange moveMethodToMe(Method method, String newMethodName, String originalReceiverParameterName, boolean inlineDelegator, boolean removeDelegator) throws CoreException {
			Assert.isNotNull(method);
			Assert.isNotNull(newMethodName);
			Assert.isNotNull(originalReceiverParameterName);
			Assert.isTrue(inlineDelegator || !removeDelegator);
			Assert.isTrue(Arrays.asList(method.getPossibleNewReceivers()).contains(this));

			CompositeChange composite= new CompositeChange(RefactoringCoreMessages.getString("InstanceMethodMover.move_method")); //$NON-NLS-1$
			composite.add(addMovedMethodToMyClass(method, newMethodName, originalReceiverParameterName));
			composite.add(replaceOriginalMethodBodyWithDelegation(method, newMethodName));
			return composite;
		}
		
		private CompilationUnitChange replaceOriginalMethodBodyWithDelegation(Method originalMethod, String newMethodName) throws CoreException {
			Method.MethodEditSession methodEditSession= originalMethod.createEditSession();	
			methodEditSession.replaceBodyWithDelegation(
				specifyDelegationToNewMethod(originalMethod, newMethodName));

			CompilationUnitChange cuChange= new CompilationUnitChange(RefactoringCoreMessages.getString("InstanceMethodMover.transform_to_delegate"), originalMethod.getDeclaringCU()); //$NON-NLS-1$
			cuChange.addTextEdit(RefactoringCoreMessages.getString("InstanceMethodMover.replace_with_delegation"), methodEditSession.getEdits()); //$NON-NLS-1$
			return cuChange;	
		}
		
		abstract Method.Delegation specifyDelegationToNewMethod(Method originalMethod, String newMethodName);
		
		private CompilationUnitChange addMovedMethodToMyClass(Method originalMethod, String newMethodName, String originalReceiverParameterName) throws CoreException {
			List allTypesUsedWithoutQualification= new ArrayList();
			TextBufferPortion newMethodText= getNewMethodDeclarationText(originalMethod, newMethodName, originalReceiverParameterName, allTypesUsedWithoutQualification);
			return addNewMethodToMyClass(newMethodText.getUnindentedContentIgnoreFirstLine(), allTypesUsedWithoutQualification);
		}
		
		private CompilationUnitChange addNewMethodToMyClass(String newMethodText, List allTypesUsedWithoutQualification) throws CoreException {
			TypeDeclaration myClassDeclaration= getReceiverClassDeclaration();
			ASTRewrite rewrite= new ASTRewrite(myClassDeclaration);
			BodyDeclaration newMethodNode= (BodyDeclaration) rewrite.createPlaceholder(newMethodText, ASTRewrite.METHOD_DECLARATION);
			myClassDeclaration.bodyDeclarations().add(newMethodNode);
			rewrite.markAsInserted(newMethodNode, RefactoringCoreMessages.getString("InstanceMethodMover.create_in_receiver")); //$NON-NLS-1$

			TextBuffer buffer= TextBuffer.create(getReceiverClassCU().getBuffer().getContents());
			MultiTextEdit edit= new MultiTextEdit();
			rewrite.rewriteNode(buffer, edit, null);
			rewrite.removeModifications();

			CompilationUnitChange cuChange= new CompilationUnitChange(RefactoringCoreMessages.getString("InstanceMethodMover.create_in_receiver"), getReceiverClassCU()); //$NON-NLS-1$
			cuChange.addTextEdit(RefactoringCoreMessages.getString("InstanceMethodMover.create_in_receiver"), edit); //$NON-NLS-1$
			cuChange.addTextEdit(
				RefactoringCoreMessages.getString("InstanceMethodMover.add_imports"), //$NON-NLS-1$
				createImportEdit(allTypesUsedWithoutQualification, getReceiverClassCU())
			);
			return cuChange;		
		}

		private TextEdit createImportEdit(List types, ICompilationUnit cu) throws JavaModelException {
			ImportEdit importEdit= new ImportEdit(cu, fCodeGenSettings);
			for(Iterator it= types.iterator(); it.hasNext();)
				importEdit.addImport((ITypeBinding) it.next());
			return importEdit;
		}

		protected TypeDeclaration getReceiverClassDeclaration() throws JavaModelException {
			ASTNode result= JavaElementMapper.perform(getReceiverModelClass(), TypeDeclaration.class);
			Assert.isTrue(result instanceof TypeDeclaration);
			return (TypeDeclaration) result;
		}
		
		private IType getReceiverModelClass() throws JavaModelException {
			if(fModelClass == null)
				fModelClass= computeReceiverModelClass();
			return fModelClass;
		}
	
		private boolean isReceiverModelClassAvailable() throws JavaModelException {
			if(fModelClass == null)
				fModelClass= computeReceiverModelClass();
			return fModelClass != null;
		}
		
		private IType computeReceiverModelClass() throws JavaModelException {
			return getModelClass(getReceiverClass(), fDependentProject);
		}
		
		private boolean isDeclaredInMyCU(Method method) throws JavaModelException {
			return JavaModelUtil.toWorkingCopy(getReceiverClassCU()).equals(JavaModelUtil.toWorkingCopy(method.getDeclaringCU()));
		}
		
		private TextBufferPortion getNewMethodDeclarationText(Method method, String newMethodName, String originalReceiverParameterName, List allTypesUsed) throws CoreException {
			Method.MethodEditSession methodEditSession= method.createEditSession();
			methodEditSession.changeMethodName(newMethodName);
			
			methodEditSession.classQualifyNonInstanceMemberReferences();
			
			if(method.hasSelfReferences()) {
				methodEditSession.addNewFirstParameter(method.getDeclaringClass(), originalReceiverParameterName);
				methodEditSession.replaceSelfReferencesWithReferencesToName(originalReceiverParameterName);				
			}
			
			methodEditSession.replaceNewReceiverReferencesWithSelfReferences(this);
			transformNonReferenceMentionsIn(methodEditSession);
			
			TextBufferPortion result= methodEditSession.getEdittedMethodText();
			allTypesUsed.addAll(methodEditSession.getAllTypesUsedWithoutQualificationInEdittedMethod());
			methodEditSession.clear();
			return result;
		}
		
		abstract void transformNonReferenceMentionsIn(Method.MethodEditSession methodEditSession);
				
		IParameter[] getMovedMethodParameterDescriptions(final Method originalMethod, final String originalReceiverParameterName) {
			Assert.isNotNull(originalMethod);
			Assert.isNotNull(originalReceiverParameterName);
			
			IParameter[] originalMethodParams= originalMethod.getParameters();
			if(!originalMethod.hasSelfReferences())
				return originalMethodParams;
			else {
				IParameter[] result= new IParameter[1 + originalMethodParams.length];
				
				result[0]= new IParameter() {
					public ITypeBinding getType() {
						return originalMethod.getDeclaringClass();
					}
					public String getName() {
						return originalReceiverParameterName;
					}
				};
				
				for(int i= 0; i < originalMethodParams.length; i++)
					result[i + 1]= originalMethodParams[i];
				
				return result;
			}
		}
		
		public int hashCode() {
			Assert.isTrue(false, "hashing of NewReceiver unsupported");//unless specified by subclass //$NON-NLS-1$
			return 0;
		}	
		
		RefactoringStatus checkMoveOfMethodToMe(Method method, String newMethodName, String originalReceiverParameterName, boolean inlineDelegator, boolean removeDelegator) throws JavaModelException {
			Assert.isNotNull(method);
			Assert.isNotNull(newMethodName);
			Assert.isNotNull(originalReceiverParameterName);
			Assert.isTrue(inlineDelegator || ! removeDelegator);
		
			if( ! isReceiverModelClassAvailable())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.to_local_localunsupported"), null, null, RefactoringStatusCodes.CANNOT_MOVE_TO_LOCAL); //$NON-NLS-1$

			// TODO: handle moving to within same cu
			if(isDeclaredInMyCU(method))
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.moving_to_same_cu_unsupported"), null, null, RefactoringStatusCodes.CANNOT_MOVE_TO_SAME_CU); //$NON-NLS-1$
			
			RefactoringStatus result= new RefactoringStatus();
			checkParameterNames(result, method, originalReceiverParameterName);
			if (result.hasFatalError())
				return result;
			result.merge(Checks.validateModifiesFiles(getFilesToBeModified(method)));
			return result;
		}
		
		private void checkParameterNames(RefactoringStatus result, Method method, String originalReceiverParameterName) {
			for (Iterator iter= method.getMethodDeclaration().parameters().iterator(); iter.hasNext();) {
				SingleVariableDeclaration param= (SingleVariableDeclaration) iter.next();
				if (originalReceiverParameterName.equals(param.getName().getIdentifier())){
					Context context= JavaStatusContext.create(method.getDeclaringCU(), param);
					String msg= RefactoringCoreMessages.getFormattedString("InstanceMethodMover.parameter_name_used", new String[]{originalReceiverParameterName}); //$NON-NLS-1$
					int code= RefactoringStatusCodes.PARAM_NAME_ALREADY_USED;
					RefactoringStatusEntry entry= new RefactoringStatusEntry(msg, RefactoringStatus.ERROR, context, null, code); 
					result.addEntry(entry);
					return; //cannot conflict with more than 1
				}	
			}
		}
		
		private IFile[] getFilesToBeModified(Method method) throws JavaModelException {
			IFile file1= getFile(getReceiverClassCU());
			IFile file2= getFile(method.getDeclaringCU());
			if (file1.equals(file2))
				return new IFile[]{file1};
			else	
				return new IFile[]{file1, file2};
		}
		
		private static IFile getFile(ICompilationUnit cunit) throws JavaModelException {
			return ResourceUtil.getFile(cunit);
		}
	}

	private static abstract class VariableNewReceiver extends NewReceiver {
		VariableNewReceiver(IJavaProject dependentProject, CodeGenerationSettings codeGenSettings) {
			super(dependentProject, codeGenSettings);	
		}
		
		protected abstract IVariableBinding getVariable();
		
		public IBinding getBinding(){
			return getVariable();
		}

		protected RefactoringStatus checkVariableNotWrittenInMethod(Method method) {
//			IVariableBinding variable= getVariable();
			return new RefactoringStatus();
		}

		protected ITypeBinding getReceiverClass() {
			return getVariable().getType();
		}
		
		public String getName() {
			return getVariable().getName();
		}
		
		Expression[] getReferencesIn(Method method) {
			return method.getVariableReferences(getVariable());
		}
	}
	
	private static class ParameterNewReceiver extends VariableNewReceiver {
		private final Parameter fParameter;

		ParameterNewReceiver(Parameter parameter, IJavaProject dependentProject, CodeGenerationSettings codeGenSettings) {
			super(dependentProject, codeGenSettings); 
			Assert.isNotNull(parameter);
			Assert.isTrue(parameter.getType().isClass());
			
			fParameter= parameter;	
		}

		public boolean isParameter() {
			return true;	
		}
		
		protected IVariableBinding getVariable() {
			return fParameter.getBinding();	
		}

		Expression createReferenceForContext(Method context) {
			Assert.isTrue(context == fParameter.getMethod());
			return fParameter.createReference();
		}
		
		private Parameter getParameter() {
			return fParameter;
		}
		
		public boolean equals(Object o) {
			if(o == null)
				return false;
			if(!getClass().equals(o.getClass()))
				return false;
			return getParameter().equals(((ParameterNewReceiver) o).getParameter());
		}
		
		public int hashCode() {
			return getParameter().hashCode();
		}

		protected void transformNonReferenceMentionsIn(MethodEditSession methodEditSession) {
			methodEditSession.removeParameter(getParameter());
		}

		Delegation specifyDelegationToNewMethod(Method originalMethod, String newMethodName) {
			Method.Delegation delegation= originalMethod.getPotentialDelegationTo(this);
			delegation.setCalledMethodName(newMethodName);

			boolean hasSelfReferences= originalMethod.hasSelfReferences();

			if(hasSelfReferences)
				delegation.passThisAsArgument(0);

			Parameter[] params= originalMethod.getParameters();
			int argumentIndex= hasSelfReferences ? 1 : 0;
			for(int parameterIndex= 0; parameterIndex < params.length; parameterIndex++) {
				if(!params[parameterIndex].equals(getParameter())) {
					delegation.mapParameterToArgument(parameterIndex, argumentIndex);
					argumentIndex++;
				}
			}

			return delegation;
		}
	}
	
	private static class FieldNewReceiver extends VariableNewReceiver {
		private final IVariableBinding fField;
		
		FieldNewReceiver(IVariableBinding fieldBinding, IJavaProject dependentProject, CodeGenerationSettings codeGenSettings) {
			super(dependentProject, codeGenSettings);
			Assert.isNotNull(fieldBinding);
			Assert.isTrue(fieldBinding.isField());
			
			fField= fieldBinding;	
		}
		
		public boolean isField() {
			return true;	
		}
		
		protected IVariableBinding getVariable() {
			return getField();	
		}
		
		private IVariableBinding getField() {
			return fField;	
		}

		Expression createReferenceForContext(Method context) {
			Assert.isNotNull(context);
			return context.createFieldReference(fField);
		}
		
		public boolean equals(Object o) {
			if(o == null)
				return false;
			if(!getClass().equals(o.getClass()))
				return false;
			return getField().getKey().equals(((FieldNewReceiver) o).getField().getKey());
		}	
		
		public int hashCode() {
			return getField().getKey().hashCode();
		}	

		
		protected void transformNonReferenceMentionsIn(MethodEditSession methodEditSession) {
			//A method to be moved to a FieldNewReceiver contains no non-reference mentions of the new receiver		
		}
	
		Delegation specifyDelegationToNewMethod(Method originalMethod, String newMethodName) {
			Method.Delegation delegation= originalMethod.getPotentialDelegationTo(this);
			delegation.setCalledMethodName(newMethodName);

			boolean hasSelfReferences= originalMethod.hasSelfReferences();

			if(hasSelfReferences)
				delegation.passThisAsArgument(0);

			int numberOfParams= originalMethod.getParameters().length;
			for(int parameterIndex= 0; parameterIndex < numberOfParams; parameterIndex++)
				delegation.mapParameterToArgument(
					parameterIndex, 
					hasSelfReferences ?
						parameterIndex + 1 : parameterIndex
				);

			return delegation;
		}
	}
	
	static class Method {
		static class Delegation {
			private final NewReceiver fNewReceiver;
			private final Method fDelegatingMethod;
			
			private String fCalledMethodName;
			private boolean fPassThis;
			private int fArgumentToPassThisAs;
			
			private final Vector fArgumentToParameterMap= new Vector();
			
			private Delegation(Method delegatingMethod, NewReceiver newReceiver) {
				Assert.isNotNull(newReceiver);
				fNewReceiver= newReceiver;
				fDelegatingMethod= delegatingMethod;
			}
			
			public void setCalledMethodName(String called) {
				Assert.isNotNull(called);
				fCalledMethodName= called;
			}

			public void passThisAsArgument(int argumentIndex) {
				Assert.isTrue(argumentIndex >= 0);
				notifyOfNewArgument(argumentIndex);
				fArgumentToPassThisAs= argumentIndex;
				fPassThis= true;
			}

			private void notifyOfNewArgument(int argumentIndex) {
				if(argumentIndex + 1> fArgumentToParameterMap.size())
					fArgumentToParameterMap.setSize(argumentIndex + 1);	
			}
			
			private int getNumberOfArguments() {
				return fArgumentToParameterMap.size();	
			}

			public void mapParameterToArgument(int parameterIndex, int argumentIndex) {
				Assert.isTrue(parameterIndex >= 0);
				Assert.isTrue(parameterIndex < fDelegatingMethod.getParameters().length);
				Assert.isTrue(argumentIndex >= 0);
				
				notifyOfNewArgument(argumentIndex);						
				fArgumentToParameterMap.set(argumentIndex, new Integer(parameterIndex));
			}
			
			private Method getDelegatingMethod() {
				return fDelegatingMethod;	
			}
			
			private MethodInvocation createDelegatingInvocation() {
				Assert.isTrue(isComplete());
				
				MethodInvocation invocation= fDelegatingMethod.getAST().newMethodInvocation();
				invocation.setExpression(fNewReceiver.createReferenceForContext(fDelegatingMethod));
				invocation.setName(fDelegatingMethod.getAST().newSimpleName(fCalledMethodName));
				
				Parameter[] params= fDelegatingMethod.getParameters();
				for(int i= 0; i < getNumberOfArguments(); i++) {
					if(fPassThis && fArgumentToPassThisAs == i)
						invocation.arguments().add(fDelegatingMethod.getAST().newThisExpression());
					else {
						Integer parameterIndex= (Integer) fArgumentToParameterMap.get(i);
						Assert.isNotNull(parameterIndex);
						Parameter parameter= params[parameterIndex.intValue()];
						invocation.arguments().add(fDelegatingMethod.getAST().newSimpleName(parameter.getName()));
					}
				}
				
				return invocation;
			}			

			private boolean isComplete() {
				return   fCalledMethodName != null
				       && hasAllArguments();
			}
			
			private boolean hasAllArguments() {
				for(int i= 0; i < getNumberOfArguments(); i++)
					if(!hasArgument(i))
						return false;
				return true;
			}
			
			private boolean hasArgument(int i) {
				return   fArgumentToParameterMap.get(i) != null
				       || fPassThis && fArgumentToPassThisAs == i; 
				
			}			
		}		
		
		static class MethodEditSession {
			private final Method fMethod;
			private final ASTRewrite fRewrite;
			
			private TypeReferences fTypeReferences;
			
			private MethodEditSession(Method method) throws JavaModelException {
				Assert.isNotNull(method);
				fMethod= method;
				fRewrite= method.createRewrite();
				fTypeReferences= method.getTypeReferences();
			}
			
			public void replaceSelfReferencesWithReferencesToName(String name) {
				Assert.isNotNull(name);
				
				replaceExplicitThisReferencesWith(name);
				replaceImplicitThisInFieldAccessesWith(name);
				replaceImplicitThisInMethodInvocationsWith(name);
			}

			private boolean replaceExplicitThisReferencesWith(String name) {
				ThisExpression[] thisReferences= fMethod.getExplicitThisReferences();
				for(int i= 0; i < thisReferences.length; i++)
					fRewrite.markAsReplaced(thisReferences[i], thisReferences[i].getAST().newSimpleName(name));
				return thisReferences.length != 0;
			}

			private boolean replaceImplicitThisInFieldAccessesWith(String name) {
				SimpleName[] fieldReferences= fMethod.getImplicitThisFieldAccesses();
				for(int i= 0; i < fieldReferences.length; i++) {
					SimpleName fieldName= fieldReferences[i];
					FieldAccess replacement= fieldName.getAST().newFieldAccess();
					replacement.setExpression(fieldName.getAST().newSimpleName(name));
					replacement.setName(fieldName.getAST().newSimpleName(fieldName.getIdentifier()));
					fRewrite.markAsReplaced(fieldName, replacement);
				}
				return fieldReferences.length != 0;
			}

			private boolean replaceImplicitThisInMethodInvocationsWith(String name) {
				MethodInvocation[] methodInvocations= fMethod.getImplicitThisMethodInvocations();
				for(int i= 0; i < methodInvocations.length; i++) {
					MethodInvocation original= methodInvocations[i];
					Expression newExpression= original.getAST().newSimpleName(name);
					Assert.isTrue(original.getExpression() == null);
					original.setExpression(newExpression);
					fRewrite.markAsInserted(newExpression);
				}
				return methodInvocations.length != 0;
			}
			
			public void replaceNewReceiverReferencesWithSelfReferences(NewReceiver newReceiver) {
				Expression[] newReceiverReferences= newReceiver.getReferencesIn(fMethod);
				for(int i= 0; i < newReceiverReferences.length; i++)
					replaceExpressionWithSelfReference(newReceiverReferences[i]);
			}

			private void replaceExpressionWithSelfReference(Expression expression) {
				Assert.isNotNull(expression);
				
				ASTNode parent= expression.getParent();
				if(parent instanceof MethodInvocation) {
					MethodInvocation invocation= (MethodInvocation) parent;
					if(expression.equals(invocation.getExpression()))
						replaceReceiverWithImplicitThis(invocation);
					else if(invocation.arguments().contains(expression))
						replaceExpressionWithExplicitThis(expression);
					else		
						Assert.isTrue(false, "expression should be an expression for which, syntactically, \"this\" could by substituted, so not the name in a method invocation."); //$NON-NLS-1$
				} else if(parent instanceof FieldAccess) {
					FieldAccess fieldAccess= (FieldAccess) parent;
					Assert.isTrue(expression.equals(fieldAccess.getExpression()), "expression should be an expression for which, syntactically, \"this\" could by substituted, so not the field name in a field access."); //$NON-NLS-1$
					replaceReceiverWithImplicitThis(fieldAccess);
				} else if(parent instanceof QualifiedName) {
					QualifiedName qualifiedName= (QualifiedName) parent;
					Assert.isTrue(isQualifiedNameUsedAsFieldAccessOnObject(qualifiedName, expression), "expression should be an expression for which, syntactically, \"this\" could by substituted."); //$NON-NLS-1$
					replaceReceiverWithImplicitThis(qualifiedName);
				} else
					replaceExpressionWithExplicitThis(expression);
			}
			
			private void replaceReceiverWithImplicitThis(MethodInvocation invocation) {
				MethodInvocation replacement= invocation.getAST().newMethodInvocation();
				replacement.setName((SimpleName) fRewrite.createCopy(invocation.getName()));
				
				fRewrite.markAsReplaced(invocation, replacement);
			}

			private void replaceReceiverWithImplicitThis(FieldAccess fieldAccess) {
				fRewrite.markAsReplaced(fieldAccess, (SimpleName) fRewrite.createCopy(fieldAccess.getName()));			
			}
			
			/**
			 * @param fieldAccess	A QualifiedName representing a field access 
			 */
			private void replaceReceiverWithImplicitThis(QualifiedName fieldAccess) {
				Assert.isTrue(isFieldAccess(fieldAccess));
				fRewrite.markAsReplaced(fieldAccess, (SimpleName) fRewrite.createCopy(fieldAccess.getName()));
			}

			private void replaceExpressionWithExplicitThis(Expression expression) {
				fRewrite.markAsReplaced(expression, expression.getAST().newThisExpression());
			}

			private static boolean isQualifiedNameUsedAsFieldAccessOnObject(QualifiedName fieldAccess, Expression object) {
				return object.equals(fieldAccess.getQualifier()) && isFieldAccess(fieldAccess);	
			}
			
			private static boolean isFieldAccess(QualifiedName qName) {
				IBinding binding= qName.resolveBinding();
				// TODO: null bindings
				Assert.isNotNull(binding);
				return binding instanceof IVariableBinding && ((IVariableBinding) binding).isField();	
			}
			
			public void classQualifyNonInstanceMemberReferences() {
				Name[] references= fMethod.findOutermostNonRightHandDotOperandNamesInBody();
				for(int i= 0; i < references.length; i++) {
					SimpleName leftMost= getLeftmost(references[i]);
					if(isNonInstanceMemberReference(leftMost))
						classQualify(leftMost);
				}
			}

			private boolean isNonInstanceMemberReference(SimpleName name) {
				if (name.getParent() instanceof ClassInstanceCreation)
					return false;
				
				IBinding binding= name.resolveBinding();
				if(binding instanceof ITypeBinding)
					return true;
				if(binding instanceof IMethodBinding)
					return Modifier.isStatic(((IMethodBinding) binding).getModifiers());
				if(binding instanceof IVariableBinding)
					return Modifier.isStatic(((IVariableBinding) binding).getModifiers());
				return false;
			}
			
			private void classQualify(SimpleName name) {
				IBinding nameBinding= name.resolveBinding();
				ITypeBinding declaring= getDeclaringClassIfMember(nameBinding);
				if(declaring == null)
					return;

				fRewrite.markAsReplaced(
					name,
					name.getAST().newQualifiedName(
						getClassNameQualifiedToTopLevel(declaring, name.getAST()),
						(SimpleName) fRewrite.createCopy(name)));
						
				fTypeReferences.addOneReference(getTopLevel(declaring));
				if(nameBinding instanceof ITypeBinding)
					fTypeReferences.removeOneReference((ITypeBinding) nameBinding);
			}
			
			private static ITypeBinding getDeclaringClassIfMember(IBinding binding) {
				if(binding instanceof IMethodBinding)
					return ((IMethodBinding) binding).getDeclaringClass();

				if(binding instanceof IVariableBinding)
					return ((IVariableBinding) binding).getDeclaringClass();

				if(binding instanceof ITypeBinding)
					return ((ITypeBinding) binding).getDeclaringClass();

				return null;
			}	
			
			private static Name getClassNameQualifiedToTopLevel(ITypeBinding clazz, AST ast) {
				Assert.isTrue(!clazz.isAnonymous());
				
				SimpleName clazzName= ast.newSimpleName(clazz.getName());
				if(isTopLevel(clazz))
					return clazzName;
				
				return ast.newQualifiedName(getClassNameQualifiedToTopLevel(clazz.getDeclaringClass(), ast), clazzName);
			}
			
			private static ITypeBinding getTopLevel(ITypeBinding clazz) {
				Assert.isTrue(!clazz.isAnonymous());
				
				ITypeBinding current= clazz;
				while(!isTopLevel(current))
					current= current.getDeclaringClass();
				return current;
			}
			
			/**
			 * Specifically: Does the class have a named declaring class?
			 */
			private static boolean isTopLevel(ITypeBinding clazz) {
				Assert.isNotNull(clazz);
				Assert.isTrue(!clazz.isAnonymous());
				ITypeBinding declaring= clazz.getDeclaringClass();
				return clazz.isLocal() || declaring == null || declaring.isAnonymous();
			} 
			
			public void changeMethodName(String newName) {
				Assert.isNotNull(newName);
				SimpleName originalName= fMethod.getNameNode();
				if (! originalName.getIdentifier().equals(newName))
					fRewrite.markAsReplaced(originalName, originalName.getAST().newSimpleName(newName));
			}
			
			public void addNewFirstParameter(ITypeBinding parameterType, String parameterName) {
				SingleVariableDeclaration newDecl= fMethod.addNewFirstParameter(parameterType, parameterName);
				fRewrite.markAsInserted(newDecl);
				if(parameterType.isClass() || parameterType.isInterface()) {
					Assert.isNotNull(fTypeReferences, "this session has already been destroyed.");	 //$NON-NLS-1$
					fTypeReferences.addOneReference(parameterType);
				}
			}
			
			public void removeParameter(Parameter parameter) {
				fRewrite.markAsRemoved(fMethod.getParameterDeclaration(parameter));
				ITypeBinding parameterType= parameter.getType();
				if(parameterType.isClass() || parameterType.isInterface()) {
					Assert.isNotNull(fTypeReferences, "this session has already been destroyed.");	 //$NON-NLS-1$
					fTypeReferences.removeOneReference(parameterType);
				}
			}			
			
			private Block replaceBody() {
				Block originalBody= fMethod.getBody();
				Block newBody= originalBody.getAST().newBlock();
				fRewrite.markAsReplaced(originalBody, newBody);
				return newBody;	
			}
			
			public void replaceBodyWithDelegation(Delegation delegation) {
				Assert.isTrue(delegation.getDelegatingMethod() == fMethod);
				Block newBody= replaceBody();
				List statements= newBody.statements();
				
				MethodInvocation delegatingInvocation= delegation.createDelegatingInvocation();
				
				Statement delegatingStatement=
					fMethod.hasVoidReturnType() ?
						createExpressionStatement(delegatingInvocation)
						:
						createReturnStatement(delegatingInvocation);
				statements.add(delegatingStatement);
			}

			private Statement createReturnStatement(Expression expression) {
				ReturnStatement returnStatement= expression.getAST().newReturnStatement();
				returnStatement.setExpression(expression);
				return returnStatement;
			}
			
			private Statement createExpressionStatement(Expression expression) {
				return expression.getAST().newExpressionStatement(expression);
			}
			
			public TextBufferPortion getEdittedMethodText() throws CoreException {
				TextBuffer cuBuffer= fMethod.createDeclaringCUBuffer();
				
				MultiTextEdit dummy= getEdits(cuBuffer);
				
				TextRange methodRange= fMethod.createTextRange();
				RangeMarker rangeMarker= new RangeMarker(methodRange);
				TextEdit[] edits= dummy.removeAll();
				for (int i= 0; i < edits.length; i++)
					rangeMarker.add(edits[i]);
				
				MultiTextEdit allEdits= new MultiTextEdit();
				allEdits.add(rangeMarker);
				
				TextBufferEditor editor= new TextBufferEditor(cuBuffer);
				editor.add(allEdits);
				editor.performEdits(null);
				
				return new TextBufferPortion(cuBuffer, methodRange);
			}
			
			public MultiTextEdit getEdits() throws JavaModelException {
				return getEdits(fMethod.createDeclaringCUBuffer());
			}
			
			private MultiTextEdit getEdits(TextBuffer buffer) {
				MultiTextEdit rootEdit= new MultiTextEdit();
				fRewrite.rewriteNode(buffer, rootEdit, null);
				return rootEdit;			
			}
			
			public Collection getAllTypesUsedWithoutQualificationInEdittedMethod() {
				Assert.isNotNull(fTypeReferences, "this session has already been destroyed."); //$NON-NLS-1$
				return fTypeReferences.getTypesReferencedWithoutQualification();
			}			
			
			public void clear() {
				fRewrite.removeModifications();
				fTypeReferences= fMethod.getTypeReferences();	
			}
			
			public void destroy() {
				fRewrite.removeModifications();
				fTypeReferences= null;
			}
		}
		
		private static class TypeReferences extends HierarchicalASTVisitor {
			private HashMap fTypeKeysToUsageCounts= new HashMap();
			private HashMap fTypeKeysToATypeBinding= new HashMap();
			
			public Collection getTypesReferencedWithoutQualification() {
				return fTypeKeysToATypeBinding.values();
			}
			
			public int getNumberOfUnqualifiedReferencesTo(ITypeBinding classOrInterface) {
				Assert.isTrue(classOrInterface.isClass() || classOrInterface.isInterface());
				
				Integer references= getValueFor(classOrInterface);
				if(references == null)
					return 0;
				return references.intValue();
			}
			
			public void addAllReferences(ASTNode tree) {
				tree.accept(this);
			}
			
			public void addOneReference(ITypeBinding classOrInterface) {
				Assert.isTrue(classOrInterface.isClass() || classOrInterface.isInterface());
				registerReference(classOrInterface);					
			}
			
			public void removeOneReference(ITypeBinding classOrInterface) {
				Assert.isTrue(classOrInterface.isClass() || classOrInterface.isInterface());
				
				Integer value= getValueFor(classOrInterface);
				Assert.isTrue(value != null, "invalid argument"); //$NON-NLS-1$
				
				int currentReferences= value.intValue();
				Assert.isTrue(currentReferences > 0);
				
				if(currentReferences == 1)
					unmap(classOrInterface);
				else
					map(
						classOrInterface,
						new Integer(currentReferences - 1)
					);
			}
			
			private void registerReference(ITypeBinding type) {
				Integer referencesSoFar= (Integer) fTypeKeysToUsageCounts.get(type.getKey());
				map(
					type,
					referencesSoFar == null ?
						new Integer(1)
						:
						new Integer(referencesSoFar.intValue() + 1)
				);					
			}
			
			private void map(ITypeBinding binding, Integer value) {
				fTypeKeysToUsageCounts.put(binding.getKey(), value);
				fTypeKeysToATypeBinding.put(binding.getKey(), binding);
			}
			
			private void unmap(ITypeBinding binding) {
				fTypeKeysToUsageCounts.remove(binding.getKey());
				fTypeKeysToATypeBinding.remove(binding.getKey());
			}
			
			private Integer getValueFor(ITypeBinding binding) {
				return (Integer) fTypeKeysToUsageCounts.get(binding.getKey());
			}

			public boolean visit(Name name) {
				SimpleName leftmost= getLeftmost(name);

				IBinding binding= leftmost.resolveBinding();
				if(binding instanceof ITypeBinding)
					registerReference((ITypeBinding) binding);

				return false;
			}
		}		
		
		private final ICompilationUnit fDeclaringCU;
		private final MethodDeclaration fMethodNode;
		private final ITypeBinding fDeclaringClass;
		private final CodeGenerationSettings fCodeGenSettings;
		
		//cache:
		private NewReceiver[] fPossibleNewReceivers;		
		
		static Method create(MethodDeclaration declaration, ICompilationUnit declaringCU, CodeGenerationSettings codeGenSettings) {
			ITypeBinding declaringClass= 	getDeclaringClassBinding(declaration);
			if (declaringClass == null) return null;
			return new Method(declaration, declaringCU, codeGenSettings, declaringClass);
		}
		
		private Method(MethodDeclaration declaration, ICompilationUnit declaringCU, CodeGenerationSettings codeGenSettings, ITypeBinding declaringClass) {
			Assert.isNotNull(declaringCU);
			Assert.isTrue(declaringCU.exists());
			Assert.isNotNull(declaration);
			Assert.isNotNull(codeGenSettings);
			Assert.isNotNull(declaringClass);

			fDeclaringCU= declaringCU;
			fMethodNode= declaration;
			
			fDeclaringClass= declaringClass;
			fCodeGenSettings= codeGenSettings;			
		}
		
		public Expression createFieldReference(IVariableBinding field) {
			Assert.isTrue(field.isField());
			//TODO: Assert.isTrue(isAncestor(field.getDeclaringClass(), getDeclaringClass()))
			//TODO: Assert field not shadowed by field

			if(parameterShadows(field))
				return createThisFieldAccess(field);

			return createFieldName(field);
		}

		private boolean parameterShadows(IVariableBinding field) {
			Parameter[] params= getParameters();
			for(int i= 0; i < params.length; i++)
				if(params[i].getName().equals(field.getName()))
					return true;
			return false;
		}

		private Expression createThisFieldAccess(IVariableBinding field) {
			FieldAccess access= fMethodNode.getAST().newFieldAccess();
			access.setExpression(fMethodNode.getAST().newThisExpression());
			access.setName(createFieldName(field));
			return access;
		}
		
		private SimpleName createFieldName(IVariableBinding field) {
			return fMethodNode.getAST().newSimpleName(field.getName());
		}
		
		private Block getBody() {
			Block body= fMethodNode.getBody();
			Assert.isNotNull(body);
			return body;
		}
		
		MethodDeclaration getMethodDeclaration(){
			return fMethodNode;
		}

		public SingleVariableDeclaration addNewFirstParameter(ITypeBinding parameterType, String parameterName) {
			Assert.isNotNull(parameterType);
			Assert.isNotNull(parameterName);
			Assert.isTrue(parameterType.isClass() || parameterType.isInterface());
			
			SingleVariableDeclaration newDecl= fMethodNode.getAST().newSingleVariableDeclaration();
			newDecl.setType(fMethodNode.getAST().newSimpleType(fMethodNode.getAST().newSimpleName(parameterType.getName())));
			newDecl.setName(fMethodNode.getAST().newSimpleName(parameterName));
			
			fMethodNode.parameters().add(0, newDecl);
			return newDecl;
		}
		
		public Delegation getPotentialDelegationTo(NewReceiver newReceiver) {
			Assert.isNotNull(newReceiver);
			Assert.isTrue(Arrays.asList(getPossibleNewReceivers()).contains(newReceiver));
			return new Delegation(this, newReceiver);
		}
		
		private SimpleName getNameNode() {
			return fMethodNode.getName();
		}

		/**
		 * <code>variable</code> must be a binding from the same AST that this
		 * Method is based on.
		 */
		Name[] getVariableReferences(final IVariableBinding variable) {
			Assert.isNotNull(variable);
			
			final List result= new ArrayList();
			fMethodNode.accept(
				new HierarchicalASTVisitor() {
					public boolean visit(Name name) {
						Assert.isNotNull(name);
						IBinding binding= name.resolveBinding();
						// TODO: assess effect/possibility of null binding
						if(binding == null)
							return true;
						if(!(binding instanceof IVariableBinding))
							return true;
							
						if(   areSameVariable(variable, (IVariableBinding) binding)
						   && !isDeclaredNamePartOfDeclaration(name)
						) {
							result.add(name);
							return false;
						}
						
						return true;
					}
					
					private boolean isDeclaredNamePartOfDeclaration(Name name) {
						ASTNode parent= name.getParent();
						if(!(parent instanceof VariableDeclaration))
							return false;
						if(parent instanceof VariableDeclarationFragment) {
							VariableDeclarationFragment fragment= (VariableDeclarationFragment) parent;
							return name.equals(fragment.getName());
						} else if(parent instanceof SingleVariableDeclaration) {
							SingleVariableDeclaration decl= (SingleVariableDeclaration) parent;
							return name.equals(decl.getName());
						}
						Assert.isTrue(false); return false;
					}
					
					private boolean areSameVariable(IVariableBinding one, IVariableBinding other) {
						/* it would be nice if there was a general way
						 * to compare bindings from different parses,
						 * but there isn't.  getKey() is null for local
						 * variables.
						 */
						return one.equals(other);
					}
				}
			);
			return (Name[]) result.toArray(new Name[result.size()]);
		}

		private static ITypeBinding getDeclaringClassBinding(MethodDeclaration decl) {
			Assert.isNotNull(decl);

			IMethodBinding binding= decl.resolveBinding();
			if (binding == null)
				return null;
			return binding.getDeclaringClass();
		}

		public String getName() {
			return fMethodNode.getName().getIdentifier();	
		}

		private ITypeBinding[] getParameterTypes() {
			Parameter[] params= getParameters();
			
			ITypeBinding[] types= new ITypeBinding[params.length];
			for(int i= 0; i < params.length; i++)
				types[i]= params[i].getType();
			return types;	
		}

		public Parameter[] getParameters() {
			List parameters= new ArrayList();
			for(Iterator it= fMethodNode.parameters().iterator(); it.hasNext();) {
				IVariableBinding paramBinding= ((SingleVariableDeclaration) it.next()).resolveBinding();
				Assert.isNotNull(paramBinding);

				parameters.add(new Parameter(this, paramBinding));
			}
			return (Parameter[]) parameters.toArray(new Parameter[parameters.size()]);
		}

		public ITypeBinding getDeclaringClass() {
			return fDeclaringClass;
		}
		
		public ICompilationUnit getDeclaringCU() {
			return fDeclaringCU;
		}

		private IJavaProject getProject() {
			return getDeclaringCU().getJavaProject();
		}
		
		public RefactoringStatus checkCanBeMoved() {
			if(isStatic())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.no_static_methods"), null, null, RefactoringStatusCodes.CANNOT_MOVE_STATIC); //$NON-NLS-1$
			if(isAbstract())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.single_implementation"), null, null, RefactoringStatusCodes.SELECT_METHOD_IMPLEMENTATION);				 //$NON-NLS-1$
			if(isNative())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.no_native_methods"), null, null, RefactoringStatusCodes.CANNOT_MOVE_NATIVE); //$NON-NLS-1$
			if(isSynchronized())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.no_synchronized_methods"), null, null, RefactoringStatusCodes.CANNOT_MOVE_SYNCHRONIZED);	 //$NON-NLS-1$
			if(isConstructor())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.no_constructors"), null, null, RefactoringStatusCodes.CANNOT_MOVE_CONSTRUCTOR);	 //$NON-NLS-1$
			if(hasSuperReferences())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.uses_super"), null, null, RefactoringStatusCodes.SUPER_REFERENCES_NOT_ALLOWED); //$NON-NLS-1$
			if(refersToEnclosingInstances())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.refers_enclosing_instances"), null, null, RefactoringStatusCodes.ENCLOSING_INSTANCE_REFERENCES_NOT_ALLOWED); //$NON-NLS-1$
			if(mayBeDirectlyRecursive())				
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.potentially_recursive"), null, null, RefactoringStatusCodes.CANNOT_MOVE_RECURSIVE); //$NON-NLS-1$
		
			if (getPossibleNewReceivers().length == 0)
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.getString("InstanceMethodMover.cannot_be_moved"), null, null, RefactoringStatusCodes.NO_NEW_RECEIVERS); //$NON-NLS-1$
			return new RefactoringStatus();
		}

		private boolean hasSuperReferences() {
			class SuperReferenceChecker extends ASTVisitor {
				private boolean fSuperReferencesFound= false;

				public boolean superReferencesFound() {
					return fSuperReferencesFound;
				}

				public boolean visit(SuperFieldAccess node) {
					fSuperReferencesFound= true;
					return false;
				}

				public boolean visit(SuperMethodInvocation node) {
					fSuperReferencesFound= true;
					return false;
				}
			};
			
			SuperReferenceChecker checker= new SuperReferenceChecker();
			fMethodNode.accept(checker);
			return checker.superReferencesFound();
		}

		private boolean isStatic() {
			return Modifier.isStatic(getModifiers());
		}
		
		private boolean isNative() {
			return Modifier.isNative(getModifiers());
		}
		
		private boolean isConstructor() {
			IMethodBinding binding= fMethodNode.resolveBinding();
			//TODO: null bindings
			Assert.isNotNull(binding);
			return binding.isConstructor();			
		}
		
		private boolean isSynchronized() {
			return Modifier.isSynchronized(getModifiers());				
		}
		
		private boolean isAbstract() {
			return getDeclaringClass().isInterface() || Modifier.isAbstract(getModifiers());
		}
		
		private boolean refersToEnclosingInstances() {
			class EnclosingInstanceReferenceChecker extends ASTVisitor {
				private boolean fEnclosingInstanceReferencesFound= false;

				public boolean enclosingInstanceReferencesFound() {
					return fEnclosingInstanceReferencesFound;
				}

				public boolean visit(ThisExpression node) {
					if(node.getQualifier() != null)
						fEnclosingInstanceReferencesFound= true;
					return false;
				}
			};
			
			EnclosingInstanceReferenceChecker checker= new EnclosingInstanceReferenceChecker();
			fMethodNode.accept(checker);
			return checker.enclosingInstanceReferencesFound();
		}
		
		private boolean mayBeDirectlyRecursive() {
			Assert.isTrue(!hasSuperReferences());
			
			class RecursionChecker extends ASTVisitor {
				private boolean fMethodMayBeRecursive= false;
				
				public boolean mayBeRecursive() {
					fMethodNode.accept(this);
					return fMethodMayBeRecursive;	
				}
				
				public boolean visit(MethodInvocation invocation) {
					if(!isSelfSend(invocation))
						return true;
					
					IMethodBinding invokedMethod= invocation.resolveMethodBinding();
					if(hasSameSignature(invokedMethod))
						fMethodMayBeRecursive= true; 
					
					return true;
				}
				
				private boolean hasSameSignature(IMethodBinding other) {
					if(!getName().equals(other.getName()))
						return false;
					return hasSameParameterTypes(other);
				}
				
				private boolean hasSameParameterTypes(IMethodBinding other) {
					ITypeBinding[] mine= getParameterTypes();
					ITypeBinding[] others= other.getParameterTypes();
					if(mine.length != others.length)
						return false;
						
					for(int i= 0; i < mine.length; i++)
						if(!mine[i].getKey().equals(others[i].getKey()))
							return false;
							
					return true;
				}
				
				private boolean isSelfSend(MethodInvocation invocation) {
					if(isStatic(invocation))
						return false;

					Expression receiver= invocation.getExpression();
					return    receiver == null
							|| receiver instanceof ThisExpression;
				}
				
				private boolean isStatic(MethodInvocation invocation) {
					IMethodBinding binding= (IMethodBinding) invocation.getName().resolveBinding();
					// TODO:  deal with or rule out null bindings
					Assert.isNotNull(binding);
					return Modifier.isStatic(binding.getModifiers());
				}					
			};
			
			RecursionChecker checker= new RecursionChecker();
			return checker.mayBeRecursive();
		}
		
		private int getModifiers() {
			return getBinding().getModifiers();						
		}
		
		public NewReceiver[] getPossibleNewReceivers() {
			if(fPossibleNewReceivers == null)
				fPossibleNewReceivers= findPossibleNewReceivers();
			return fPossibleNewReceivers;
		}

		private NewReceiver[] findPossibleNewReceivers() {
			List newReceivers= new ArrayList();
		
			addPossibleParameterNewReceivers(newReceivers);
			addPossibleFieldNewReceivers(newReceivers);
		
			return (NewReceiver[]) newReceivers.toArray(new NewReceiver[newReceivers.size()]);
		}

		private static boolean canAddAsPossibleNewReceiver(ITypeBinding type){
			return type.isClass() && type.isFromSource();
		}
		
		private void addPossibleParameterNewReceivers(List target) {
			Assert.isNotNull(target);

			Parameter[] parameters= getParameters();
			for(int i= 0; i < parameters.length; i++) {
				if(canAddAsPossibleNewReceiver(parameters[i].getType()))
					target.add(new ParameterNewReceiver(parameters[i], getProject(), fCodeGenSettings));
			}
		}

		private void addPossibleFieldNewReceivers(List target) {
			Assert.isNotNull(target);
			IVariableBinding[] fields= findFieldsOfSelfReadButNotWritten();
			for(int i= 0; i < fields.length; i++) {
				if (canAddAsPossibleNewReceiver(fields[i].getType()))
					target.add(new FieldNewReceiver(fields[i], getProject(), fCodeGenSettings));	
			}
		}

		private IVariableBinding[] findFieldsOfSelfReadButNotWritten() {
			Collection result= keepFieldsNotWritten(findReferencedFieldsOfSelf());
			return (IVariableBinding[]) result.toArray(new IVariableBinding[result.size()]);
		}

		/**
		 * @return Set of keys obtained by calling <code>a.getKey()</code> on a
		 * VariableBinding instance <code>a</code>
		 */
		private Set getKeysOfAllWrittenFields() {
			final HashSet writtenFieldKeys= new HashSet();
			
			fMethodNode.accept(
				new ASTVisitor() {
					public boolean visit(Assignment assignment) {
						reportExpressionModified(assignment.getLeftHandSide());
						return true;
					}

					public boolean visit(PostfixExpression node) {
						reportExpressionModified(node.getOperand());
						return true;
					}

					public boolean visit(PrefixExpression node) {
						reportExpressionModified(node.getOperand());
						return false;
					}

					private void reportExpressionModified(Expression exp) {
						IVariableBinding field
							= getFieldBindingIfField(exp);
						if(field != null)
							reportFieldWritten(field);
					}

					private void reportFieldWritten(IVariableBinding field) {
						Assert.isTrue(field.isField());
						writtenFieldKeys.add(field.getKey());
					}
					
					private IVariableBinding getFieldBindingIfField(Expression exp) {
						if(exp instanceof FieldAccess)
							return (IVariableBinding) ((FieldAccess) exp).getName().resolveBinding();
						if(exp instanceof Name) {
							IBinding binding= ((Name) exp).resolveBinding();
							if(binding instanceof IVariableBinding) {
								IVariableBinding variable= (IVariableBinding) binding;
								if(variable.isField())
									return variable;	
							}
						}
						return null;
					}
				}
			);
			return writtenFieldKeys;			
		}

		private Collection keepFieldsNotWritten(Collection fields) {
			Set writtenFieldKeys= getKeysOfAllWrittenFields();
			
			Collection result= new ArrayList();
			for(Iterator it= fields.iterator(); it.hasNext();) {
				IVariableBinding field= (IVariableBinding) it.next();
				Assert.isTrue(field.isField());
				if(!writtenFieldKeys.contains(field.getKey()))
					result.add(field);
			}
			return result;
		}
				
		private Collection findReferencedFieldsOfSelf() {
			final Set fieldKeys= new HashSet();
			final List result= new ArrayList();
			
			fMethodNode.accept(
				new ASTVisitor() {
					public boolean visit(FieldAccess node) {
						if(node.getExpression() instanceof ThisExpression) {
							IVariableBinding field= (IVariableBinding) node.getName().resolveBinding();
							if(field != null)	
								fieldFound(field);
						}
						return true;
					}

					public boolean visit(SimpleName name) {
						IBinding binding= name.resolveBinding();
						if(binding != null)
							if(isImplicitThisFieldAccess(name))
								fieldFound((IVariableBinding) binding);
						return false;
					}
					
					private void fieldFound(IVariableBinding field) {
						Assert.isTrue(field.isField());
						if(!fieldKeys.contains(field.getKey())) {
							fieldKeys.add(field.getKey());
							result.add(field);
						}						
					}
				}
			);
			return result;
		}
		
		private TextBuffer createDeclaringCUBuffer() throws JavaModelException {
			return TextBuffer.create(getDeclaringCU().getBuffer().getContents());	
		}
		
		private TextRange createTextRange() {
			return TextRange.createFromStartAndLength(fMethodNode.getStartPosition(), fMethodNode.getLength());	
		}
		
		private ASTRewrite createRewrite() {
			return new ASTRewrite(fMethodNode);	
		}
		
		public MethodEditSession createEditSession() throws JavaModelException {
			return new MethodEditSession(this);	
		}
		
		private ThisExpression[] getExplicitThisReferences() {
			final List result= new ArrayList();
			fMethodNode.accept(
				new ASTVisitor() {
					public boolean visit(ThisExpression node) {
						result.add(node);
						return false;
					}
				}
			);
			return (ThisExpression[]) result.toArray(new ThisExpression[result.size()]);
		}
		
		private MethodInvocation[] getImplicitThisMethodInvocations() {
			final List result= new ArrayList();
			fMethodNode.accept(
				new ASTVisitor() {
					public boolean visit(MethodInvocation invocation) {
						if(isImplicitThisMethodInvocation(invocation))
							result.add(invocation);
						return true;
					}
					
					private boolean isImplicitThisMethodInvocation(MethodInvocation invocation) {
						return   isInvokedMethodNotStatic(invocation)
						       && invocation.getExpression() == null;
					}
					
					private boolean isInvokedMethodNotStatic(MethodInvocation invocation) {
						IMethodBinding methodBinding= invocation.resolveMethodBinding();
						// TODO: handle null bindings
						Assert.isNotNull(methodBinding);					
						return !Modifier.isStatic(methodBinding.getModifiers());
					}
				}
			);
			return (MethodInvocation[]) result.toArray(new MethodInvocation[result.size()]);
		}

		private SimpleName[] getImplicitThisFieldAccesses() {
			final List result= new ArrayList();
			fMethodNode.accept(
				new ASTVisitor() {
					public boolean visit(SimpleName name) {
						if(isImplicitThisFieldAccess(name))
							result.add(name);
						return false;
					}
				}
			);
			return (SimpleName[]) result.toArray(new SimpleName[result.size()]);
		}			
	
		public boolean hasSelfReferences() {
			// TODO: add caching for these methods
			return   getExplicitThisReferences().length != 0
			       || getImplicitThisMethodInvocations().length != 0
			       || getImplicitThisFieldAccesses().length != 0;
		}		
		
		private MethodDeclaration getDeclaration() {
			return fMethodNode;	
		}
		
		private IMethodBinding getBinding() {
			IMethodBinding binding= fMethodNode.resolveBinding();
			// TODO: null bindings
			Assert.isNotNull(binding);
			return binding;
		}
		
		public ITypeBinding getReturnType() {
			return getBinding().getReturnType();
		}
		
		public boolean hasVoidReturnType() {
			Type returnType= fMethodNode.getReturnType();
			if(!(returnType instanceof PrimitiveType))
				return false;
			return PrimitiveType.VOID.equals(((PrimitiveType) returnType).getPrimitiveTypeCode());
		}

		public SimpleName createParameterReference(Parameter parameter) {
			Assert.isTrue(parameter.getMethod() == this);
			return fMethodNode.getAST().newSimpleName(parameter.getName());
		}
		
		private AST getAST() {
			return fMethodNode.getAST();
		}


		private SingleVariableDeclaration getParameterDeclaration(Parameter parameter) {
			for(Iterator decls= fMethodNode.parameters().iterator(); decls.hasNext();) {
				SingleVariableDeclaration parameterDeclaration= (SingleVariableDeclaration) decls.next();
				if(parameter.getBinding().equals(parameterDeclaration.resolveBinding()))
					return parameterDeclaration;
			}
			Assert.isTrue(false, "Parameter must be a parameter to this method."); //$NON-NLS-1$
			return null;
		}

		private TypeReferences getTypeReferences() {
			TypeReferences result= new TypeReferences();
			result.addAllReferences(fMethodNode);
			return result;
		}		
		
		private Name[] findOutermostNonRightHandDotOperandNamesInBody() {
			final List result= new ArrayList();
			fMethodNode.getBody().accept(
				new HierarchicalASTVisitor() {
					public boolean visit(Name name) {
						if(!isRightDotOperand(name))
							result.add(name);
						return false; 
					}
				}
			);
			return (Name[]) result.toArray(new Name[result.size()]);
		}
		
		public boolean equals(Object o) {
			if(o == null)
				return false;
			if(!getClass().equals(o.getClass()))
				return false;
			return getDeclaration().equals(((Method) o).getDeclaration());	
		}
		
		// general static helpers:
		
		private static SimpleName getLeftmost(Name name) {
			if(name instanceof SimpleName)
				return (SimpleName) name;

			return getLeftmost(((QualifiedName) name).getQualifier());
		}
		
		private static boolean isImplicitThisFieldAccess(SimpleName name) {
			return isInstanceFieldAccess(name) && !isRightDotOperand(name);
		}

		/**
		 * Is the name preceded by a dot?
		 */
		private static boolean isRightDotOperand(Name name) {
			ASTNode parent = name.getParent();
			if(parent instanceof QualifiedName && ((QualifiedName)parent).getName().equals(name))
				return true;
				
			if(parent instanceof FieldAccess && ((FieldAccess)parent).getName().equals(name))
				return true;
			
			if(parent instanceof SuperFieldAccess)
				return true;
			
			if(parent instanceof MethodInvocation) {
				MethodInvocation invocation= (MethodInvocation) parent;
				return invocation.getExpression() != null && invocation.getName().equals(name);
			}
			
			return false;
		}

		private static boolean isInstanceFieldAccess(SimpleName name) {
			IBinding binding= name.resolveBinding();
			// TODO: null bindings assumed OK

			if(!(binding instanceof IVariableBinding))
				return false;

			IVariableBinding variableBinding= (IVariableBinding) binding;
			if(!variableBinding.isField())
				return false;

			return !Modifier.isStatic(variableBinding.getModifiers());
		}		
	}

	private static class Parameter implements IParameter {
		private final Method fMethod;
		private final IVariableBinding fBinding;

		private Parameter(Method method, IVariableBinding binding) {
			Assert.isNotNull(method);
			Assert.isNotNull(binding);
			Assert.isTrue(!binding.isField());

			fMethod= method;
			fBinding= binding;
		}

		public String getName() {
			return fBinding.getName();
		}

		public ITypeBinding getType() {
			return fBinding.getType();
		}

		public Method getMethod() {
			return fMethod;
		}

		public boolean isFinal() {
			return Modifier.isFinal(fBinding.getModifiers());
		}
		
		public SimpleName createReference() {
			return fMethod.createParameterReference(this);	
		}

		public IVariableBinding getBinding() {
			return fBinding;
		}
		
		public boolean equals(Object o) {
			if(o == null)
				return false;
			if(!getClass().equals((o.getClass())))
				return false;
			Parameter otherParam= (Parameter) o;
			return   getName().equals(otherParam.getName())
			       && getMethod().equals(otherParam.getMethod());	
		}	
	}
	
	private static class TextBufferPortion {
		private final TextBuffer fBuffer;
		private final TextRange fRange;
		
		TextBufferPortion(TextBuffer buffer, TextRange range) {
			Assert.isNotNull(buffer);
			Assert.isNotNull(range);
			fBuffer= buffer;
			fRange= range;
		}
		
		public String getContent() {
			return fBuffer.getContent(fRange.getOffset(), fRange.getLength());	
		}
		
		public String getUnindentedContentIgnoreFirstLine() {
			return Strings.changeIndent(
				fBuffer.getContent(fRange.getOffset(), fRange.getLength()),
				fBuffer.getLineIndent(fBuffer.getLineOfOffset(fRange.getOffset()), CodeFormatterUtil.getTabWidth()),
				CodeFormatterUtil.getTabWidth(),
				"", //$NON-NLS-1$
				fBuffer.getLineDelimiter()
			);
		}
	}
	
	private final Method fMethodToMove;

	private NewReceiver fNewReceiver;
	
	private String fNewMethodName;
	private String fOriginalReceiverParameterName;
	
	private boolean fInlineDelegator;
	private boolean fRemoveDelegator;
	
	public static InstanceMethodMover create(MethodDeclaration declaration, ICompilationUnit declarationCU, CodeGenerationSettings codeGenSettings) {
		Method method= Method.create(declaration, declarationCU, codeGenSettings);
		if (method == null) return null;
		return new InstanceMethodMover(declaration, declarationCU, method);
	}

	private InstanceMethodMover(MethodDeclaration declaration, ICompilationUnit declarationCU, Method method) {	
		Assert.isNotNull(method);
		fMethodToMove= method;
		
		fInlineDelegator= true;  //default
		fRemoveDelegator= true;  //default
		fNewMethodName= fMethodToMove.getName();  //default;
		fOriginalReceiverParameterName= getTypeBasedVariableName(fMethodToMove.getDeclaringClass());  //default
	}

	private static String getTypeBasedVariableName(ITypeBinding type) {
		Assert.isNotNull(type);
		String typeName= type.getName();
		Assert.isTrue(typeName.length() != 0);
		
		int uppercasePrefixEnd= getUppercasePrefixEndExclusive(typeName);
		return prefixToLowercase(typeName, uppercasePrefixEnd);
	}
	
	private static int getUppercasePrefixEndExclusive(String string) {
		int i= 0;
		while(i < string.length() && Character.isUpperCase(string.charAt(i)))
			i++;
		return i;
	}
	
	private static String prefixToLowercase(String string, int prefixEndExclusive) {
		String prefix= toLowercase(string.substring(0, prefixEndExclusive));
		String suffix= prefixEndExclusive == string.length() ?
		                   "" //$NON-NLS-1$
		                   :
		                   string.substring(prefixEndExclusive);
		return prefix + suffix;		                   
	}
	
	private static String toLowercase(String string) {
		String result= ""; //$NON-NLS-1$
		for(int i= 0; i < string.length(); i++)
			result += Character.toLowerCase(string.charAt(i));
		return result;
	}
	
	public INewReceiver[] getPossibleNewReceivers() {
		return fMethodToMove.getPossibleNewReceivers();
	}

	/**
	 * @param chosen	Must be a element of the result
	 * of a call to getPossibleNewReceivers()
	 */
	public void chooseNewReceiver(INewReceiver chosen) {
		Assert.isTrue(Arrays.asList(getPossibleNewReceivers()).contains(chosen));
		fNewReceiver= (NewReceiver) chosen;
	}
	
	public String getNewMethodName() {
		return fNewMethodName;
	}
	
	public void setNewMethodName(String newMethodName) {
		Assert.isNotNull(newMethodName);
		fNewMethodName= newMethodName;
	}	

	public String getOriginalReceiverParameterName() {
		return fOriginalReceiverParameterName;
	}
	
	public void setOriginalReceiverParameterName(String originalReceiverParameterName) {
		Assert.isNotNull(originalReceiverParameterName);
		fOriginalReceiverParameterName= originalReceiverParameterName;
	}

	public void setInlineDelegator(boolean inlineDelegator) {
		fInlineDelegator= inlineDelegator;
		checkInvariant();
	}

	public void setRemoveDelegator(boolean removeDelegator) {
		fRemoveDelegator= removeDelegator;
		checkInvariant();
	}
	
	public boolean getInlineDelegator() {
		return fInlineDelegator;
	}
	
	public boolean getRemoveDelegator() {
		return fRemoveDelegator;	
	}

	private void checkInvariant() {
		if(fRemoveDelegator)
			Assert.isTrue(fInlineDelegator);
	}
	
	private static IType getModelClass(ITypeBinding clazz, IJavaProject dependentProject) throws JavaModelException {
		Assert.isTrue(clazz.isClass());
		IType modelClass= (IType) Bindings.findType(clazz, dependentProject);
		if(modelClass == null || !modelClass.exists())
			return null;
			
		Assert.isTrue(modelClass.isClass());
		return modelClass;
	}

	public RefactoringStatus checkInitialState(IProgressMonitor pm) {
		return fMethodToMove.checkCanBeMoved();
	}

	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		Assert.isNotNull(fNewReceiver, "New receiver must be chosen before checkInput(..) is called."); //$NON-NLS-1$
		return fNewReceiver.checkMoveOfMethodToMe(fMethodToMove, fNewMethodName, fOriginalReceiverParameterName, fInlineDelegator, fRemoveDelegator);
	}

	public IChange createChange(IProgressMonitor pm) throws CoreException {
		return fNewReceiver.moveMethodToMe(fMethodToMove, fNewMethodName, fOriginalReceiverParameterName, fInlineDelegator, fRemoveDelegator);
	}
}
