/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
 * Franz Willer <franz.willer@gwi-ag.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.dcm4chee.docstore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataHandler;

import org.dcm4chee.docstore.spi.DocumentStorage;

public class BaseDocument {
    private String docUid, mime;
    private DataHandler dh;
    private Availability availability;
    private long size;
    private String hash;
    private DocumentStorage storage;
    private OutputStream out;

    public BaseDocument(String docUid, String mime, DataHandler dh, Availability availability, long size, DocumentStorage storage) {
        this.docUid = docUid;
        this.mime = mime;
        this.dh = dh;
        this.availability = availability;
        this.size = size;
        this.storage = storage;
    }

    public String getDocumentUID() {
        return docUid;
    }

    public String getMimeType() {
        return mime;
    }

    public long getSize() {
        return size;
    }

    public DataHandler getDataHandler() {
        return dh;
    }

    public String getHash() {
        return hash;
    }

    public InputStream getInputStream() throws IOException {
        return dh == null ? null : dh.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        if (out == null && dh != null) {
            out = dh.getOutputStream(); //ensure that only one OutputStream is used! 
        }
        return out;
    }

    public Availability getAvailability() {
        return availability;
    }

    public DocumentStorage getStorage() {
        return storage;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
    
    public String toString() {
        return "BaseDocument: docUid:"+docUid+" mime:"+mime+" size:"+size+"\nAvailability:"+availability+ "hash:"+hash;
    }
}
