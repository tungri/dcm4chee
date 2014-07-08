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
package org.dcm4chee.docstore.spi;

import java.io.IOException;
import java.util.Set;

import javax.activation.DataHandler;

import org.dcm4chee.docstore.Availability;
import org.dcm4chee.docstore.BaseDocument;
import org.dcm4chee.docstore.DataHandlerVO;
import org.dcm4chee.docstore.DocumentStorageListener;
import org.dcm4chee.docstore.Feature;


public interface DocumentStorage {
    /**
     * Initialize this DocumentStore instance.
     * <p/>
     * The meaning/effect of <code>initString</code> is implementation specific.
     * @param initString Initialization String
     */
    void init(String initString);

    /**
     * Type of Storage
     * @return StorageType
     */
    String getStorageType();

    /**
     * Name of this DocumentStorage instance.
     * <p/>
     * A unique name for a concrete DocumentStorage instance.
     * 
     * @return name
     */
    String getName();

    /**
     * Description of the DocumentStorage instance.
     * @return Description
     */
    String getDescription();
    void setDescription(String desc);

    /**
     * Get list of features of this storage instance.
     * <p/>
     * A feature can be either implementation specific (fix for all instances) or configured per instance.<br/>
     * 
     * @return List of supported features
     */
    Set<Feature> getFeatures();

    /**
     * Add a Feature to this storage.
     * @param feature
     */
    void addFeature(Feature feature);

    /**
     * Checks if this Storage implementation supports given feature.
     * 
     * @param feature Feature that must be supported.
     * @return true if feature is supported.
     */
    boolean hasFeature(Feature feature);

    /**
     * Checks if this Storage implementation supports all features of given feature list.
     * 
     * @param features List of features that must be supported.
     * @return true if all features of the list are supported by this instance.
     */
    boolean matchFeatures(Set<Feature> features);

    /**
     * Get Availability of this storage to store new documents.
     * <p/>
     * <dl>
     * <dt>One of following Availability:</dt>
     * <dd>  ONLINE      Storage locally available.</dd>
     * <dd>  NEARLINE    Storage remote available.</dd>
     * <dd>  UNAVAILABLE Storage not available or no space left.</dd>
     * </dl>
     * @return
     */
    Availability getStorageAvailability();

    /**
     * Store a document with given document UID and content given by DataHandler.
     * <p/>
     * The mime type of the document is derived from DataHandler.
     *  
     * @param docUid Unique ID of the document
     * @param doc DataHandler with document data and contentType (mime type)
     * @return Document
     * @throws IOException
     */
    BaseDocument storeDocument(String docUid, DataHandler doc) throws IOException;

    /**
     * Store a set of documents in one transaction.
     * <p/>
     * @param docs Array of Document DataHandler Value Objects
     * @return Documents
     * @throws IOException
     */
    BaseDocument[] storeDocuments(Set<DataHandlerVO> docs) throws IOException;

    /**
     * Create a new empty document with given UID and mime type.
     * <p/>
     * This method can be used to provide a OutputStream for the document to write the document content.
     * <p/>
     * The result is a initialized Document object that must provide a OutputStream.
     * Therefore it is not specified if a persistence (empty) object will be created with this method.
     * <p/>
     * Return null if this method is not supported.
     * Implementation which support this method should also have Feature.CREATE_EMPTY_DOCUMENT ('createEmptyDoc').
     * 
     * @param docUid Unique ID of the document.
     * @param mime Mime Type
     * @return empty Document with DataHandler or null if this method is not supported.
     * @throws IOException
     */
    BaseDocument createDocument(String docUid, String mime) throws IOException;

    /**
     * Store a Hash value to given document.
     * <p/>
     * Used to store a hash value for a document that is created by createDocument and stored externally.
     * <p/>
     * <dl><dt>Should return false:</dt>
     * <dd>1) Store of hash values is not supported by storage implementation.</dd>
     * <dd>2) Document doesn't exist.</dd>
     * <dd>3) Document has already a hash value.</dd>
     *  
     * @param doc  BaseDocument (usually from createDocument, with docUid and mime to reference document).
     * @param hash Hash value as Hex String.
     * @return     true if hash value is stored, false when this method isn't supported or a failure occurs.
     */
    boolean setHash(BaseDocument doc, String hash);

    /**
     * Get a URL to retrieve a document for given document UID.
     * <p/>
     * Return null if the implementation does not support URL access to documents.
     * <p/>
     * The document may not exist or be only locally retrievable by this URL!
     * 
     * @param docUid DocumentURL
     * @return URL to retrieve document for given UID or null
     */
    String getRetrieveURL(String docUid);

    /**
     * Return Document for given UID or null if document does not exist.
     * @param docUid Unique ID
     * @return Document or null.
     * @throws IOException
     */
    BaseDocument retrieveDocument(String docUid) throws IOException;

    /**
     * Return Document for given UID and MIME type or null if document does not exist.
     * <p/>
     * If <code>mime is null</code> and the storage has multiple documents with different MIME types
     * the first (initial) stored document will be returned.
     * 
     * @param docUid 	Unique ID
     * @param mime 		MIME type
     * @return Document or null.
     * @throws IOException
     */
    BaseDocument retrieveDocument(String docUid, String mime) throws IOException;

    /**
     * Return availability of document in this Storage instance.
     * 
     * @param docUid Unique ID 
     * @return
     */
    Availability getAvailabilty(String docUid);

    /**
     * Set Committed state of given document UID.
     * <p/>
     * 
     * @param docUid
     * @return true if committed state is set.
     */
    boolean commitDocument(String docUid);

    /**
     * Delete given document.
     * <p/>
     * A implementation may not support the deletion of documents and have to return always false in this case.
     * <p/>
     * If the implementation supports commitDocument the deletion should be only
     * @param docUid
     * @return true if document is deleted in this storage.
     */
    boolean deleteDocument(String docUid);

    /**
     * Add a DocumentStorageListener to this DocumentStorage.
     * 
     * @param listener
     * @return
     */
    boolean addStorageListener(DocumentStorageListener listener);
}
