package org.eclipse.jdt.junit.util;

import java.io.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;

/**
 * <code>FileUtil</code> contains methods to create and
 * delete files and projects.
 */
public class FileUtil {

	/**
	 * Creates a new project.
	 * 
	 * @param name the project name
	 */
	public static IProject createProject(String name) 
		throws CoreException
	{
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = ws.getRoot();
		IProject proj = root.getProject(name);
		if (!proj.exists())
			proj.create(null);
		if (!proj.isOpen())
			proj.open(null);
		return proj;
	}

	/**
	 * Deletes a project.
	 * 
	 * @param proj the project
	 */
	public static void deleteProject(IProject proj) 
		throws CoreException
	{
		proj.delete(true, null);
	}
	
	/**
	 * Creates a new file in a project.
	 * 
	 * @param name the new file name
	 * @param proj the existing project
	 * @return the new file
	 */
	public static IFile createFile(String name, IProject proj) 
		throws CoreException
	{
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = ws.getRoot();
		IFile file = proj.getFile(name);
		if (!file.exists()) {
			String str = " ";
			InputStream in = new ByteArrayInputStream(str.getBytes());
			file.create(in, true, null);
		}
		return file;
	}
	
}

