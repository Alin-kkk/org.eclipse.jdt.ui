package org.eclipse.jdt.internal.ui.text.java;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
  
import java.util.ArrayList;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jdt.core.CompletionRequestorAdapter;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;

/**
 * Bin to collect the proposal of the infrastructure on code assist in a java text.
 */
public class ResultCollector extends CompletionRequestorAdapter {
		
	private final static char[] METHOD_WITH_ARGUMENTS_TRIGGERS= new char[] { '(', '-', ' ' };
	private final static char[] METHOD_TRIGGERS= new char[] { ';', ',', '.', '\t', '[', ' ' };
	private final static char[] TYPE_TRIGGERS= new char[] { '.', '\t', '[', '(', ' ' };
	private final static char[] VAR_TRIGGER= new char[] { '\t', ' ', '=', ';' };

	protected IJavaProject fJavaProject;
	protected ICompilationUnit fCompilationUnit; // set when imports can be added
	protected int fCodeAssistOffset;
	protected int fContextOffset;
	
	private ArrayList fFields=
		new ArrayList(),
		fKeywords= new ArrayList(10), 
		fLabels= new ArrayList(10),
		fMethods= new ArrayList(),
		fModifiers= new ArrayList(10),
		fPackages= new ArrayList(),
		fTypes= new ArrayList(),
		fVariables= new ArrayList();

	private IProblem fLastProblem;	
	private ImageDescriptorRegistry fRegistry= JavaPlugin.getImageDescriptorRegistry();
	
	private ArrayList[] fResults = new ArrayList[] {
		fPackages, fLabels, fModifiers, fKeywords, fTypes, fMethods, fFields, fVariables
	};
	
	private int fUserReplacementLength;

	/*
	 * Is eating code assist enabled or disabled? PR #3666
	 * When eating is enabled, JavaCompletionProposal must be revisited: PR #5533
	 */
	private boolean fPreventEating= true;	
	
	/*
	 * @see ICompletionRequestor#acceptClass
	 */	
	public void acceptClass(char[] packageName, char[] typeName, char[] completionName, int modifiers, int start, int end, int relevance) {
		ImageDescriptor descriptor= JavaElementImageProvider.getTypeImageDescriptor(false, false, modifiers);
		if (Flags.isDeprecated(modifiers))
			descriptor= getDeprecatedDescriptor(descriptor);

		ProposalInfo info= new ProposalInfo(fJavaProject, packageName, typeName);
		fTypes.add(createTypeCompletion(start, end, new String(completionName), descriptor, new String(typeName), new String(packageName), info, relevance));
	}
	
	/*
	 * @see ICompletionRequestor#acceptError
	 */
	public void acceptError(IProblem error) {
		fLastProblem= error;
	}	
	
	/*
	 * @see ICompletionRequestor#acceptField
	 */	
	public void acceptField(
		char[] declaringTypePackageName, char[] declaringTypeName, char[] name,
		char[] typePackageName, char[] typeName, char[] completionName,
		int modifiers, int start, int end, int relevance) {

		ImageDescriptor descriptor= getFieldDescriptor(modifiers);	
		
		StringBuffer nameBuffer= new StringBuffer();
		nameBuffer.append(name);
		if (typeName.length > 0) {
			nameBuffer.append("   "); //$NON-NLS-1$
			nameBuffer.append(typeName);
		}
		if (declaringTypeName != null && declaringTypeName.length > 0) {
			nameBuffer.append(" - "); //$NON-NLS-1$
			nameBuffer.append(declaringTypeName);
		}	
		
		JavaCompletionProposal proposal= createCompletion(start, end, new String(completionName), descriptor, nameBuffer.toString(), relevance);
		proposal.setProposalInfo(new ProposalInfo(fJavaProject, declaringTypePackageName, declaringTypeName, name));
		proposal.setTriggerCharacters(VAR_TRIGGER);
		
		fFields.add(proposal);
	}
	
	/*
	 * @see ICompletionRequestor#acceptInterface
	 */	
	public void acceptInterface(char[] packageName, char[] typeName, char[] completionName, int modifiers, int start, int end, int relevance) {
		ImageDescriptor descriptor= JavaElementImageProvider.getTypeImageDescriptor(true, false, modifiers);
		if (Flags.isDeprecated(modifiers))
			descriptor= getDeprecatedDescriptor(descriptor);

		ProposalInfo info= new ProposalInfo(fJavaProject, packageName, typeName);
		fTypes.add(createTypeCompletion(start, end, new String(completionName), descriptor, new String(typeName), new String(packageName), info, relevance));
	}
	
	/*
	 * @see ICompletionRequestor#acceptAnonymousType
	 */
	public void acceptAnonymousType(char[] superTypePackageName, char[] superTypeName, char[][] parameterPackageNames, char[][] parameterTypeNames, char[][] parameterNames,
		char[] completionName, int modifiers, int completionStart, int completionEnd, int relevance) {

		JavaCompletionProposal	proposal= createAnonymousTypeCompletion(superTypePackageName, superTypeName, parameterTypeNames, parameterNames, completionName, completionStart, completionEnd, relevance);
		proposal.setProposalInfo(new ProposalInfo(fJavaProject, superTypePackageName, superTypeName));
		fTypes.add(proposal);
	}	
	
	/*
	 * @see ICompletionRequestor#acceptKeyword
	 */	
	public void acceptKeyword(char[] keyword, int start, int end, int relevance) {
		String kw= new String(keyword);
		fKeywords.add(createCompletion(start, end, kw, null, kw, relevance));
	}
	
	/*
	 * @see ICompletionRequestor#acceptLabel
	 */	
	public void acceptLabel(char[] labelName, int start, int end, int relevance) {
		String ln= new String(labelName);
		fLabels.add(createCompletion(start, end, ln, null, ln, relevance));
	}
	
	/*
	 * @see ICompletionRequestor#acceptLocalVariable
	 */	
	public void acceptLocalVariable(char[] name, char[] typePackageName, char[] typeName, int modifiers, int start, int end, int relevance) {
		StringBuffer buf= new StringBuffer();
		buf.append(name);
		if (typeName != null) {
			buf.append("    "); //$NON-NLS-1$
			buf.append(typeName);
		}
		JavaCompletionProposal proposal= createCompletion(start, end, new String(name), JavaPluginImages.DESC_OBJS_LOCAL_VARIABLE, buf.toString(), relevance);
		proposal.setTriggerCharacters(VAR_TRIGGER);
		fVariables.add(proposal);
	}
	
	protected String getParameterSignature(char[][] parameterTypeNames, char[][] parameterNames) {
		StringBuffer buf = new StringBuffer();
		if (parameterTypeNames != null) {
			for (int i = 0; i < parameterTypeNames.length; i++) {
				if (i > 0) {
					buf.append(',');
					buf.append(' ');
				}
				buf.append(parameterTypeNames[i]);
				if (parameterNames != null && parameterNames[i] != null) {
					buf.append(' ');
					buf.append(parameterNames[i]);
				}
			}
		}
		return buf.toString();
	}
	
	/*
	 * @see ICompletionRequestor#acceptMethod
	 */
	public void acceptMethod(char[] declaringTypePackageName, char[] declaringTypeName, char[] name,
		char[][] parameterPackageNames, char[][] parameterTypeNames, char[][] parameterNames,
		char[] returnTypePackageName, char[] returnTypeName, char[] completionName, int modifiers,
		int start, int end, int relevance) {
		
		if (completionName == null)
			return;
	
		JavaCompletionProposal proposal= createMethodCallCompletion(declaringTypeName, name, parameterPackageNames, parameterTypeNames, parameterNames, returnTypeName, completionName, modifiers, start, end, relevance);
		boolean isConstructor= returnTypeName == null ? true : returnTypeName.length == 0;
		proposal.setProposalInfo(new ProposalInfo(fJavaProject, declaringTypePackageName, declaringTypeName, name, parameterPackageNames, parameterTypeNames, isConstructor));

		boolean hasOpeningBracket= completionName.length == 0 || (completionName.length > 0 && completionName[completionName.length - 1] == ')');
	
		ProposalContextInformation contextInformation= null;
		if (hasOpeningBracket && parameterTypeNames.length > 0) {
			contextInformation= new ProposalContextInformation();
			contextInformation.setInformationDisplayString(getParameterSignature(parameterTypeNames, parameterNames));		
			contextInformation.setContextDisplayString(proposal.getDisplayString());
			contextInformation.setImage(proposal.getImage());
			int position= (completionName.length == 0) ? fContextOffset : -1;
			contextInformation.setContextInformationPosition(position);
			proposal.setContextInformation(contextInformation);
		}
	
		boolean userMustCompleteParameters= (contextInformation != null && completionName.length > 0);
		char[] triggers= userMustCompleteParameters ? METHOD_WITH_ARGUMENTS_TRIGGERS : METHOD_TRIGGERS;
		proposal.setTriggerCharacters(triggers);
		
		if (userMustCompleteParameters) {
			// set the cursor before the closing bracket
			proposal.setCursorPosition(completionName.length - 1);
		}
		
		fMethods.add(proposal);	
	}

	
	/*
	 * @see ICompletionRequestor#acceptModifier
	 */	
	public void acceptModifier(char[] modifier, int start, int end, int relevance) {
		String mod= new String(modifier);
		fModifiers.add(createCompletion(start, end, mod, null, mod, relevance));
	}
	
	/*
	 * @see ICompletionRequestor#acceptPackage
	 */	
	public void acceptPackage(char[] packageName, char[] completionName, int start, int end, int relevance) {
		fPackages.add(createCompletion(start, end, new String(completionName), JavaPluginImages.DESC_OBJS_PACKAGE, new String(packageName), relevance));
	}
	
	/*
	 * @see ICompletionRequestor#acceptType
	 */	
	public void acceptType(char[] packageName, char[] typeName, char[] completionName, int start, int end, int relevance) {
		ProposalInfo info= new ProposalInfo(fJavaProject, packageName, typeName);
		fTypes.add(createTypeCompletion(start, end, new String(completionName), JavaPluginImages.DESC_OBJS_CLASS, new String(typeName), new String(packageName), info, relevance));
	}
	
	/*
	 * @see ICodeCompletionRequestor#acceptMethodDeclaration
	 */
	public void acceptMethodDeclaration(char[] declaringTypePackageName, char[] declaringTypeName, char[] name, char[][] parameterPackageNames, char[][] parameterTypeNames, char[][] parameterNames, char[] returnTypePackageName, char[] returnTypeName, char[] completionName, int modifiers, int start, int end, int relevance) {
		StringBuffer displayString= getMethodDisplayString(declaringTypeName, name, parameterTypeNames, parameterNames, returnTypeName);
	
		StringBuffer typeName= new StringBuffer();
		if (declaringTypePackageName.length > 0) {
			typeName.append(declaringTypePackageName);
			typeName.append('.');
		}
		typeName.append(declaringTypeName);
		
		String[] paramTypes= new String[parameterTypeNames.length];
		for (int i= 0; i < parameterTypeNames.length; i++) {
			paramTypes[i]= Signature.createTypeSignature(parameterTypeNames[i], true);
		}

		JavaCompletionProposal proposal= new MethodStubCompletionProposal(fJavaProject, fCompilationUnit, typeName.toString(), new String(name), paramTypes, start, getLength(start, end), displayString.toString(), new String(completionName));
		proposal.setImage(getImage(getMemberDescriptor(modifiers)));
		proposal.setProposalInfo(new ProposalInfo(fJavaProject, declaringTypePackageName, declaringTypeName, name, parameterPackageNames, parameterTypeNames, returnTypeName.length == 0));
		proposal.setRelevance(relevance);
		fMethods.add(proposal);
	}
	
	/*
	 * @see ICodeCompletionRequestor#acceptVariableName
	 */
	public void acceptVariableName(char[] typePackageName, char[] typeName, char[] name, char[] completionName, int start, int end, int relevance) {
		// XXX: To be revised
		StringBuffer buf= new StringBuffer();
		buf.append(name);
		if (typeName != null && typeName.length > 0) {
			buf.append(" - "); //$NON-NLS-1$
			buf.append(typeName);
		}
		JavaCompletionProposal proposal= createCompletion(start, end, new String(completionName), null, buf.toString(), relevance);
		proposal.setTriggerCharacters(VAR_TRIGGER);
		fVariables.add(proposal);
	}	
	
	public String getErrorMessage() {
		if (fLastProblem != null)
			return fLastProblem.getMessage();
		return ""; //$NON-NLS-1$
	}

	public JavaCompletionProposal[] getResults() {
		// return unsorted
		int totLen= 0;
		for (int i= 0; i < fResults.length; i++) {
			totLen += fResults[i].size();
		}
		JavaCompletionProposal[] result= new JavaCompletionProposal[totLen];
		int k= 0;
		for (int i= 0; i < fResults.length; i++) {
			ArrayList curr= fResults[i];
			int currLen= curr.size();
			for (int j= 0; j < currLen; j++) {
				JavaCompletionProposal proposal= (JavaCompletionProposal) curr.get(j);
				// for equal relevance, take categories
				proposal.setRelevance(proposal.getRelevance() * 16 + i);
				result[k++]= proposal;
			}
		}		
		return result;
	}

	private StringBuffer getMethodDisplayString(char[] declaringTypeName, char[] name, char[][] parameterTypeNames, char[][] parameterNames, char[] returnTypeName) {
		StringBuffer nameBuffer= new StringBuffer();
		nameBuffer.append(name);
		nameBuffer.append('(');
		if (parameterTypeNames != null && parameterTypeNames.length > 0) {
			nameBuffer.append(getParameterSignature(parameterTypeNames, parameterNames));
		}
		nameBuffer.append(')'); 
		if (returnTypeName != null && returnTypeName.length > 0) {
			nameBuffer.append("  "); //$NON-NLS-1$
			nameBuffer.append(returnTypeName);
		}
		if (declaringTypeName != null && declaringTypeName.length > 0) {
			nameBuffer.append(" - "); //$NON-NLS-1$
			nameBuffer.append(declaringTypeName);
		}
		return nameBuffer;
	}

	protected JavaCompletionProposal createMethodCallCompletion(char[] declaringTypeName, char[] name, char[][] parameterTypePackageNames, char[][] parameterTypeNames, char[][] parameterNames, char[] returnTypeName, char[] completionName, int modifiers, int start, int end, int relevance) {
		ImageDescriptor descriptor= getMemberDescriptor(modifiers);
		StringBuffer nameBuffer= getMethodDisplayString(declaringTypeName, name, parameterTypeNames, parameterNames, returnTypeName);
		return createCompletion(start, end, new String(completionName), descriptor, nameBuffer.toString(), relevance);
	}


	protected JavaCompletionProposal createAnonymousTypeCompletion(char[] declaringTypePackageName, char[] declaringTypeName, char[][] parameterTypeNames, char[][] parameterNames, char[] completionName, int start, int end, int relevance) {
		StringBuffer declTypeBuf= new StringBuffer();
		if (declaringTypePackageName.length > 0) {
			declTypeBuf.append(declaringTypePackageName);
			declTypeBuf.append('.');
		}
		declTypeBuf.append(declaringTypeName);
		
		StringBuffer nameBuffer= new StringBuffer();
		nameBuffer.append(declaringTypeName);
		nameBuffer.append('(');
		if (parameterTypeNames.length > 0) {
			nameBuffer.append(getParameterSignature(parameterTypeNames, parameterNames));
		}
		nameBuffer.append(')');
		nameBuffer.append("  "); //$NON-NLS-1$
		nameBuffer.append(JavaTextMessages.getString("ResultCollector.anonymous_type")); //$NON-NLS-1$
	
		int length= end - start;
		
		return new AnonymousTypeCompletionProposal(fJavaProject, fCompilationUnit, start, length, new String(completionName), nameBuffer.toString(), declTypeBuf.toString(), relevance);
	}

	
	protected JavaCompletionProposal createTypeCompletion(int start, int end, String completion, ImageDescriptor descriptor, String typeName, String containerName, ProposalInfo proposalInfo, int relevance) {
		StringBuffer buf= new StringBuffer(typeName);
		if (containerName != null) {
			buf.append(" - "); //$NON-NLS-1$
			if (containerName.length() > 0) {
				buf.append(containerName);
			} else {
				buf.append(JavaTextMessages.getString("ResultCollector.default_package")); //$NON-NLS-1$
			}
		}
		String name= buf.toString();

		ICompilationUnit cu= null;
		if (containerName != null && fCompilationUnit != null) {
			if (completion.equals(JavaModelUtil.concatenateName(containerName, typeName))) {
				cu= fCompilationUnit;
			}
		}
	
		JavaCompletionProposal proposal= new JavaTypeCompletionProposal(completion, cu, start, getLength(start, end), getImage(descriptor), name, relevance, typeName, containerName);
		proposal.setProposalInfo(proposalInfo);
		proposal.setTriggerCharacters(TYPE_TRIGGERS);
		return proposal;
	}

	protected ImageDescriptor getMemberDescriptor(int modifiers) {
		ImageDescriptor desc= JavaElementImageProvider.getMethodImageDescriptor(false, modifiers);

		if (Flags.isDeprecated(modifiers))
		 	desc= getDeprecatedDescriptor(desc);

		if (Flags.isStatic(modifiers))
			desc= getStaticDescriptor(desc);
		
		return desc;
	}
	
	protected ImageDescriptor getFieldDescriptor(int modifiers) {
		ImageDescriptor desc= JavaElementImageProvider.getFieldImageDescriptor(false, modifiers);

		if (Flags.isDeprecated(modifiers))
		 	desc= getDeprecatedDescriptor(desc);
		 	
		if (Flags.isStatic(modifiers))
			desc= getStaticDescriptor(desc);
		
		return desc;
	}	
	
	protected ImageDescriptor getDeprecatedDescriptor(ImageDescriptor descriptor) {
		Point size= new Point(16, 16);
		return new JavaElementImageDescriptor(descriptor, JavaElementImageDescriptor.WARNING, size);	    
	}
	
	protected ImageDescriptor getStaticDescriptor(ImageDescriptor descriptor) {
		Point size= new Point(16, 16);
		return new JavaElementImageDescriptor(descriptor, JavaElementImageDescriptor.STATIC, size);
	}
	
	protected JavaCompletionProposal createCompletion(int start, int end, String completion, ImageDescriptor descriptor, String name, int relevance) {
		return new JavaCompletionProposal(completion, start, getLength(start, end), getImage(descriptor), name, relevance);
	}

	private int getLength(int start, int end) {
		int length;
		if (fUserReplacementLength == -1) {
			length= fPreventEating ? fCodeAssistOffset - start : end - start;
		} else {
			length= fUserReplacementLength;
			// extend length to begin at start
			if (start < fCodeAssistOffset) {
				length+= fCodeAssistOffset - start;
			}
		}
		return length;
	}
	
	private Image getImage(ImageDescriptor descriptor) {
		return (descriptor == null) ? null : fRegistry.get(descriptor);
	}
	
	/**
	 * Specifies the context of the code assist operation.
	 * @param codeAssistOffset The Offset at which the code assist will be called.
	 * Used to modify the offsets of the created proposals. ('Non Eating')
	 * @param contextOffset The offset at which the context presumable start or -1.
	 * @param jproject The Java project to which the underlying source belongs.
	 * Needed to find types referred.
	 * @param cu The compilation unit that is edited. Used to add import statements.
	 * Can be <code>null</code> if no import statements should be added.
	 */
	public void reset(int codeAssistOffset, int contextOffset, IJavaProject jproject, ICompilationUnit cu) {
		fJavaProject= jproject;
		fCompilationUnit= cu;
		fCodeAssistOffset= codeAssistOffset;
		fContextOffset= contextOffset;
		
		fUserReplacementLength= -1;
		
		fLastProblem= null;
		
		for (int i= 0; i < fResults.length; i++)
			fResults[i].clear();
	}

	/**
	 * Specifies the context of the code assist operation.
	 * @param codeAssistOffset The Offset on which the code assist will be called.
	 * Used to modify the offsets of the created proposals. ('Non Eating')
	 * @param jproject The Java project to which the underlying source belongs.
	 * Needed to find types referred.
	 * @param cu The compilation unit that is edited. Used to add import statements.
	 * Can be <code>null</code> if no import statements should be added.
	 */
	public void reset(int codeAssistOffset, IJavaProject jproject, ICompilationUnit cu) {
		reset(codeAssistOffset, -1, jproject, cu);
	}
	
	/**
	 * If the replacement length is set, it overrides the length returned from
	 * the content assist infrastructure.
	 * Use this setting if code assist is called with a none empty selection.
	 */
	public void setReplacementLength(int length) {
		fUserReplacementLength= length;
	}


	/**
	 * If set, proposals created will not remove characters after the code assist position
	 * @param preventEating The preventEating to set
	 */
	public void setPreventEating(boolean preventEating) {
		fPreventEating= preventEating;
	}

}
