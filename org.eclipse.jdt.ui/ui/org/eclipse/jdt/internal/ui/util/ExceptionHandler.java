/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.internal.ui.IJavaUIStatus;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Shows an error dialog for exceptions that contain an <code>IStatus</code>.
 * If the throwable passed to the methods is of a kind that the methods can handle, 
 * the error dialog is shown and <code>true</code> is returned. Otherwise <code>false
 * </code>is returned, and the client has to handle the error itself. If the passed
 * throwable is of type <code>InvocationTargetException</code> the wrapped excpetion
 * is considered.
 */
public class ExceptionHandler {

	private static ExceptionHandler fgInstance= new ExceptionHandler();
	
	/**
	 * Shows an error dialog for exceptions that contain an <code>IStatus</code>.
	 * This method appends "title" and "message" to the given resource prefix to fetch the
	 * title and message parameters for the error dialog.
	 * Example: resourcePrefix= "org.eclipse.jdt.ui.copy.error." -> "org.eclipse.jdt.ui.copy.error.title".
	 * 
	 * @deprecated Use handle(Throwable, String, String) instead
	 */
	public static boolean handle(Throwable t, ResourceBundle bundle, String resourcePrefix) {
		return handle(t, JavaPlugin.getActiveWorkbenchShell(), bundle, resourcePrefix); 
	}
	
	/**
	 * Shows an error dialog for exceptions that contain an <code>IStatus</code>.
	 * This method appends "title" and "message" to the given resource prefix to fetch the
	 * title and message parameters for the error dialog.
	 * Example: resourcePrefix= "org.eclipse.jdt.ui.copy.error." -> "org.eclipse.jdt.ui.copy.error.title".
	 * 
	 * @deprecated Use handle(Throwable, Shell, String, String) instead	 * 
	 */
	public static boolean handle(Throwable t, Shell shell, ResourceBundle bundle, String resourcePrefix) {
		return handle(t, shell, getResourceString(bundle, resourcePrefix+"title"), getResourceString(bundle, resourcePrefix+"message")); 
	}
	
	/**
	 * Shows an error dialog for exceptions that contain an <code>IStatus</code>.
	 */
	public static boolean handle(Throwable t, String title, String message) {
		return handle(t, JavaPlugin.getActiveWorkbenchShell(), title, message);	
	}
	
	/**
	 * Shows an error dialog for exceptions that contain an <code>IStatus</code>.
	 */
	public static boolean handle(Throwable t, Shell shell, String title, String message) {
		if (fgInstance == null)
			return false;
		return fgInstance.perform(t, shell, title, message);	
	}
	
	/**
	 * Logs the given exception using the platforms logging mechanism.
	 * This method appends "message" to the given resource prefix to fetch the
	 * message parameter for logging.
	 * 
	 * @deprecated Use log(Throwable, String) instead
	 */
	public static void log(Throwable t, ResourceBundle bundle, String prefix) {
		log(t, getResourceString(bundle, prefix+"message")); 
	}
	
	/**
	 * Logs the given exception using the platforms logging mechanism.
	 */
	public static void log(Throwable t, String message) {
		JavaPlugin.log(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 
			IJavaUIStatus.INTERNAL_ERROR, message, t));
	}
	
	/**
	 * Actually displays the error message. Subclasses may override the method to
	 * perform their own error handling.
	 */
	public boolean perform(Throwable t, Shell shell, String title, String message) {
		if (t instanceof InvocationTargetException) {
			t= ((InvocationTargetException)t).getTargetException();
		}
		if (handleCoreException(t, shell, title, message)) {
			return true;
		}
		return handleCriticalExceptions(t, shell, title, message);
	}

	protected boolean handleCoreException(Throwable t, Shell shell, String title, String message) {
		IStatus status= null;
		if (t instanceof CoreException) {
			status= ((CoreException)t).getStatus();
			if (status != null)
				ErrorDialog.openError(shell, title, message, status);
			else
				displayMessageDialog(t, shell, title, message);
			return true;
		}
		return false;
	}
	
	protected boolean handleCriticalExceptions(Throwable t, Shell shell, String title, String message) {
		if (t instanceof RuntimeException || t instanceof Error) {
			log(t, message);
			displayMessageDialog(t, shell, title, message);
			return true;
		}
		return false;	
	}

	/**
	 * Shows the error in a message dialog
	 */
	protected void displayMessageDialog(Throwable t, Shell shell, String title, String message) {
		StringWriter msg= new StringWriter();
		if (message != null) {
			msg.write(message);
			msg.write("\n\n");
		}
		if (t.getMessage() == null || t.getMessage().length() == 0)
			msg.write(t.toString());
		else
			msg.write(t.getMessage());
		MessageDialog.openError(shell, title, msg.toString());			
	}
	
	/**
	 * Shows a dialog containing the stack trace of the exception
	 */
	public static void showStackTraceDialog(Throwable t, Shell shell, String title) {
		StringWriter writer= new StringWriter();
		t.printStackTrace(new PrintWriter(writer));
		MessageDialog.openError(shell, title, writer.toString());
	}	
		
	private static String getResourceString(ResourceBundle bundle, String key) {
		try {
			return bundle.getString(key);
		} catch (MissingResourceException e) {
			return key;
		}
	}	
}