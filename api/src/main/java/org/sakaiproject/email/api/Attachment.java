/**********************************************************************************
 * Copyright 2008 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.email.api;

import java.io.File;

/**
 * Holds an attachment for an email message. The attachment will be included with the message.
 *
 * TODO: Make available for attachments to be stored in CHS.
 *
 * @author <a href="mailto:carl.hall@et.gatech.edu">Carl Hall</a>
 */
public class Attachment
{
	/**
	 * files to associated to this attachment
	 */
	private final File file;
	private final String filename;

	/**
	 * The Content-Type and Content-Disposition MIME headers to be sent with the attachment.
	 * Can be <code>null</code>.
	 */
	private final String contentType;
	private final String contentDisposition;
	public enum ContentDisposition {INLINE, ATTACHMENT}

	public Attachment(File file, String filename)
	{
		this.file = file;
		this.filename = filename;
		this.contentType = null;
		this.contentDisposition = null;
	}

	/**
	 * Creates an Attachment with some of the MIME headers specified.
	 *
	 * @param file        the file
	 * @param filename    the filename
	 * @param contentType the Content-Type header, can be <code>null</code>
	 * @param disposition the Content-Disposition header, can be <code>null</code>
	 */
	public Attachment(File file, String filename, String contentType, ContentDisposition disposition)
	{
		this.file = file;
		this.filename = filename;
		this.contentType = contentType;
		this.contentDisposition = disposition == null ? null : disposition.toString().toLowerCase();
	}

	/**
	 * Get the file associated to this attachment
	 *
	 * @return
	 */
	public File getFile()
	{
		return file;
	}

	/**
	 * Get the name of the attached file.
	 *
	 * @return
	 */
	public String getFilename()
	{
		return filename;
	}


	/**
	 * The Content-Type MIME header for the attachment, can be <code>null</code>.
	 *
	 * @return the Content-Type header
	 */
	public String getContentTypeHeader()
    {
        return contentType;
    }

	/**
	 * The Content-Disposition MIME header for the attachment, can be <code>null</code>.
	 *
	 * @return the Content-Disposition header
	 */
	public String getContentDispositionHeader()
    {
        return contentDisposition;
    }

}
