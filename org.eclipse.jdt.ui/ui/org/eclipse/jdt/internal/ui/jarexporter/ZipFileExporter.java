package org.eclipse.jdt.internal.ui.jarexporter;

/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 2000
 */
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

/**
 *	Exports resources to a .zip file
 */
/*package*/
class ZipFileExporter {
	private ZipOutputStream outputStream;
	private StringBuffer manifestContents;

	private boolean generateManifestFile= false;
	private boolean useCompression= true;

	// constants
	private static final String newline= "\r\n"; // is this platform dependent?
	private static final String manifestMagic= "Manifest-Version: 1.0" + newline + newline;
	private static final String nameLabel= "Name: ";
	private static final String digestsLabel= "Digest-Algorithms: SHA MD5" + newline;
	private static final String shaLabel= "SHA-Digest: ";
	private static final String md5Label= "MD5-Digest: ";
	private static final String manifestPath= "meta-inf/Manifest.mf";
	/**
	 *	Create an instance of this class.
	 *
	 *	@param filename java.lang.String
	 *	@param compress boolean
	 *	@param includeManifestFile boolean
	 *	@exception java.io.IOException
	 */
	public ZipFileExporter(String filename, boolean compress, boolean includeManifestFile) throws IOException {
		outputStream= new ZipOutputStream(new FileOutputStream(filename));
		useCompression= compress;
		generateManifestFile= includeManifestFile;

		if (generateManifestFile)
			manifestContents= new StringBuffer(manifestMagic);
	}

	/**
	 *  Create a new entry in the manifest file being constructed.
	 *
	 *  @param pathname java.lang.String
	 *  @param file org.eclipse.core.resources.IFile
	 *  @exception java.io.IOException
	 *  @exception org.eclipse.core.runtime.CoreException
	 */
	protected void appendToManifest(String pathname, IFile file) throws IOException, CoreException {
		StringBuffer manifestEntry= new StringBuffer();
		manifestEntry.append(nameLabel);
		manifestEntry.append(pathname);
		manifestEntry.append(newline);
		manifestEntry.append(digestsLabel);
		manifestEntry.append(shaLabel);

		byte[] fileContents= null;

		// we don't have to EnsureLocal because it was already done in #write
		InputStream contentStream= file.getContents(false);
		Reader in= new InputStreamReader(contentStream);
		int chunkSize= contentStream.available();
		StringBuffer buffer= new StringBuffer(chunkSize);
		char[] readBuffer= new char[chunkSize];
		int n= in.read(readBuffer);
		while (n > 0) {
			buffer.append(readBuffer);
			n= in.read(readBuffer);
		}
		contentStream.close();
		fileContents= buffer.toString().getBytes();

		try {
			byte[] hashValue= MessageDigest.getInstance("SHA").digest(fileContents);
			manifestEntry.append(Base64Encoder.encode(hashValue));
			manifestEntry.append(newline);
			manifestEntry.append(md5Label);
			hashValue= MessageDigest.getInstance("MD5").digest(fileContents);
			manifestEntry.append(Base64Encoder.encode(hashValue));
			manifestEntry.append(newline + newline);
		} catch (NoSuchAlgorithmException e) {
			// should never happen
			return;
		}
		manifestContents.append(manifestEntry.toString());
	}

	/**
	 *	Do all required cleanup now that we're finished with the
	 *	currently-open .zip
	 *
	 *	@exception java.io.IOException
	 */
	public void finished() throws IOException {
		if (generateManifestFile)
			writeManifestFile();

		outputStream.close();
	}

	/**
	 *  Write the passed resource to the current archive
	 *
	 *  @param resource org.eclipse.core.resources.IFile
	 *  @param destinationPath java.lang.String
	 *  @exception java.io.IOException
	 *  @exception org.eclipse.core.runtime.CoreException
	 */
	public void write(IFile resource, String destinationPath) throws IOException, CoreException {
		ByteArrayOutputStream output= null;
		InputStream contentStream= null;

		try {
			output= new ByteArrayOutputStream();
			contentStream= resource.getContents(false);
			int chunkSize= contentStream.available();
			byte[] readBuffer= new byte[chunkSize];
			int n= contentStream.read(readBuffer);

			while (n > 0) {
				output.write(readBuffer);
				n= contentStream.read(readBuffer);
			}
		} finally {
			if (output != null)
				output.close();
			if (contentStream != null)
				contentStream.close();
		}

		write(destinationPath, output.toByteArray());
		if (generateManifestFile)
			appendToManifest(destinationPath, resource);
	}

	/**
	 *	Create a new ZipEntry with the passed pathname and contents, and write it
	 *	to the current archive
	 *
	 *	@param pathname java.lang.String
	 *	@param contents byte[]
	 *	@exception java.io.IOException
	 */
	protected void write(String pathname, byte[] contents) throws IOException {
		ZipEntry newEntry= new ZipEntry(pathname);

		// if the contents are being compressed then we get the below for free.
		if (!useCompression) {
			newEntry.setMethod(ZipEntry.STORED);
			newEntry.setSize(contents.length);
			CRC32 checksumCalculator= new CRC32();
			checksumCalculator.update(contents);
			newEntry.setCrc(checksumCalculator.getValue());
		}

		outputStream.putNextEntry(newEntry);
		outputStream.write(contents);
		outputStream.closeEntry();
	}

	/**
	 *	Write the constructed manifest.mf file to the current archive
	 *
	 *	@exception java.io.IOException
	 */
	protected void writeManifestFile() throws IOException {
		write(manifestPath, manifestContents.toString().getBytes());
	}

}
