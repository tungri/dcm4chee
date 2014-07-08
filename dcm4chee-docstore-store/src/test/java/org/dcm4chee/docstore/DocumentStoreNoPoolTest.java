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
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import org.dcm4chee.docstore.spi.DocumentStorage;
import org.dcm4chee.docstore.spi.file.DocumentFileStorage;
import org.dcm4chee.docstore.test.DocStoreTestBase;
import org.dcm4chee.docstore.test.DummyDFCommandMBean;
import org.dcm4chee.docstore.test.TestUtil;
import org.dcm4chee.docstore.util.FileSystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentStoreNoPoolTest extends DocStoreTestBase {

    private static Logger log = LoggerFactory.getLogger( DocumentStoreNoPoolTest.class );

    public DocumentStoreNoPoolTest() throws IOException {
        super();
        initDocStore();
    }

    public void testGetDocStorageFromPool() {
        assertNull("Found DocumentStorage for pool null in domain TEST!", docStore.getDocStorageFromPool(null));
        assertNotNull("Found no DocumentStorage for pool 'pool1' in Domain TEST (no pool)!", docStore.getDocStorageFromPool(TestUtil.POOL1));
        assertNull("Found DocumentStorage for pool 'unknown' in Domain TEST (no pool)!", docStore.getDocStorageFromPool(TestUtil.UNKNOWN));
    }

    public void testSelectDocStorageFromPoolOrDomain() {
        DocumentStorage store = docStore.selectDocStorageFromPoolOrDomain(null);
        assertNotNull("No Storage found for pool null or in domain TEST",store);
        assertEquals("Wrong storage found for pool null in domain TEST", "TestStore", store.getName());
        store = docStore.selectDocStorageFromPoolOrDomain(TestUtil.POOL1);
        assertNotNull("No Storage found for pool 'pool1' or in domain TEST",store);
        ArrayList<String> names = new ArrayList<String>(2);
        names.add("PoolStore_1_1"); names.add("PoolStore_1_2");
        TestUtil.checkNames(store, "for pool 'pool1' in domain TEST", names);
        store = docStore.selectDocStorageFromPoolOrDomain("pool2");
        assertNotNull("No Storage found for pool 'pool2' or in domain TEST",store);
        names.clear(); names.add("PoolStore_2_1"); names.add("PoolStore_2_2");
        TestUtil.checkNames(store, "for pool 'pool2' in domain TEST", names);
        store = docStore.selectDocStorageFromPoolOrDomain("pool3");
        assertNotNull("No Storage found for (unknown) pool pool3 or in domain TEST",store);
        assertEquals("Wrong storage found for (unknown) pool pool3 in domain TEST", "TestStore", store.getName());
    }

    public void testSelectStorageByAvailability() {
        FileSystemInfo.disableJDK6Support(); //to force DummyDFCommandMBean usage!
        Collection<DocumentStorage> stores = new ArrayList<DocumentStorage>(5);
        DocumentStorage store = docStore.selectStorageByAvailability(null);
        assertNull("Storage found for list of DocumentStorage=null", store);
        store = docStore.selectStorageByAvailability(stores);
        assertNull("Storage found for empty list of DocumentStorage", store);
        stores = registry.getDocumentStorages(TestUtil.DOMAIN_TEST);
        store = docStore.selectStorageByAvailability(stores);
        assertEquals("Wrong storage found in domain TEST", "TestStore", store == null ? null : store.getName());

        DummyDFCommandMBean.setFreeSpace("pool1_1", 100000000);
        DummyDFCommandMBean.setFreeSpace("pool1_2", 0);
        DummyDFCommandMBean.setFreeSpace("pool2_1", 0);
        DummyDFCommandMBean.setFreeSpace("pool2_2", 0);
        stores = registry.getDocumentStorages("POOL");
        store = docStore.selectStorageByAvailability(stores);
        assertEquals("Wrong storage found in domain POOL! available:1_1", "PoolStore_1_1", store == null ? null : store.getName());
        DummyDFCommandMBean.setFreeSpace("pool1_1", 0);
        DummyDFCommandMBean.setFreeSpace("pool1_2", 100000000);
        store = docStore.selectStorageByAvailability(stores);
        assertEquals("Wrong storage found in domain POOL! available:1_2", "PoolStore_1_2", store == null ? null : store.getName());
        DummyDFCommandMBean.setFreeSpace("pool1_2", 0);
        DummyDFCommandMBean.setFreeSpace("pool2_1", 100000000);
        store = docStore.selectStorageByAvailability(stores);
        assertEquals("Wrong storage found in domain POOL! available:2_1", "PoolStore_2_1", store == null ? null : store.getName());
        DummyDFCommandMBean.setFreeSpace("pool2_1", 0);
        DummyDFCommandMBean.setFreeSpace("pool2_2", 100000000);
        store = docStore.selectStorageByAvailability(stores);
        assertEquals("Wrong storage found in domain POOL! available:2_2", "PoolStore_2_2", store == null ? null : store.getName());
        DummyDFCommandMBean.setFreeSpace("pool2_2", 0);
        store = docStore.selectStorageByAvailability(stores);
        assertNotNull("No Storage found for list of unavailable DocumentStorages", store);
        assertTrue("Found storage is not unavailable!", store.getStorageAvailability().equals(Availability.UNAVAILABLE));
    }

    public void testGetRetrieveDocStorages() {
        Set<DocumentStorage> stores = docStore.getRetrieveDocStorages();
        assertNotNull("No Storage found for retrieve (No Retrieve Features)", stores);
        assertEquals("Wrong number of storages found for retrieve (No Retrieve Features)",1,stores.size());
        Set<Feature> features = new HashSet<Feature>();
        features.add(new Feature("TEST_RETRIEVE", ""));
        docStore.setRetrieveFeatures(features);
        stores = docStore.getRetrieveDocStorages();
        assertNotNull("No Storage found for retrieve (TEST_RETRIEVE)", stores);
        assertEquals("Wrong number of storages found for retrieve (TEST_RETRIEVE)",3,stores.size());
        features.clear(); features.add(new Feature("RID_RETRIEVE", ""));
        docStore.setRetrieveFeatures(features);
        stores = docStore.getRetrieveDocStorages();
        assertNotNull("No Storage found for retrieve (RID_RETRIEVE)", stores);
        assertEquals("Wrong number of storages found for retrieve (RID_RETRIEVE)",3,stores.size());
        features.clear(); features.add(new Feature("NO_RETRIEVE", ""));
        docStore.setRetrieveFeatures(features);
        stores = docStore.getRetrieveDocStorages();
        assertNotNull("No Storage found for retrieve (NO_RETRIEVE)", stores);
        assertEquals("Wrong number of storages found for retrieve (NO_RETRIEVE)",1,stores.size());
        features.clear(); 
        features.add(new Feature("TEST_RETRIEVE", ""));
        features.add(new Feature("RID_RETRIEVE", ""));
        docStore.setRetrieveFeatures(features);
        stores = docStore.getRetrieveDocStorages();
        assertNotNull("No Storage found for retrieve (RID_RETRIEVE,TEST_RETRIEVE)", stores);
        assertEquals("Wrong number of storages found for retrieve (RID_RETRIEVE,TEST_RETRIEVE) Should only return stores from domain (TestStore)!!",1,stores.size());
    }

    public void testGetNamedDocStorage() {
        DocumentStorage store = docStore.getNamedDocStorage("TestStore");
        assertNotNull("No Storage found with name 'TestStore'", store);
        assertEquals("Wrong name of storage", "TestStore", store.getName());
        store = docStore.getNamedDocStorage("PoolStore_1_1");
        assertNull("Storage found with name 'PoolStore_1_1' from other domain!", store);
        store = docStore.getNamedDocStorage(TestUtil.UNKNOWN);
        assertNull("Unknown Storage found with name 'unknown'!", store);
    }

    public void testGetAvailability() throws IOException {
        Availability avail = docStore.getAvailability(TestUtil.UNKNOWN);
        assertEquals("Wrong Availability of document UID=unknown", Availability.NONEEXISTENT, avail);
        TestUtil.createDummyDocument(docStore, TestUtil.DUMMY_DOC_UID, TestUtil.MIME_TEXT_PLAIN);
        avail = docStore.getAvailability(TestUtil.DUMMY_DOC_UID);
        assertEquals("Wrong Availability of document UID="+TestUtil.DUMMY_DOC_UID, Availability.ONLINE, avail);
    }

    public void testGetDocument() throws IOException {
        BaseDocument doc = docStore.getDocument(TestUtil.UNKNOWN, TestUtil.MIME_TEXT_PLAIN);
        assertNull("Document found for non existing document! uid:"+TestUtil.UNKNOWN, doc);
        String docUid = "get.document.1";
        TestUtil.createDummyDocument(docStore, docUid, TestUtil.MIME_TEXT_PLAIN);
        
        checkDocument(docUid, TestUtil.MIME_TEXT_PLAIN, TestUtil.MIME_TEXT_PLAIN, TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        doc = docStore.getDocument(docUid, "application/dicom");
        assertNull("Document found for existing document with wrong mime type! uid:"+docUid, doc);
        doc = docStore.getDocument(docUid, null);
        checkDocument(docUid, null, TestUtil.MIME_TEXT_PLAIN, TestUtil.DUMMY_PLAIN_DATA_SOURCE);
    }

    public void testCreateDocument() throws IOException {
        String docUidBase = "create.test.1.";
        int idx = 0;
        TestUtil.createDummyDocument(docStore, docUidBase+idx++, TestUtil.MIME_TEXT_PLAIN);
        String illegalChars = "12\\33/23צה\00";
        try {
            TestUtil.createDummyDocument(docStore, illegalChars, TestUtil.MIME_TEXT_PLAIN);
            fail("Illegal docUID '"+illegalChars+"' doesn't throw a Exception!");
        } catch (Exception ignore) {}
        TestUtil.createDummyDocument(docStore, docUidBase+idx++, null);
        TestUtil.createDummyDocument(docStore, docUidBase+idx++, "");
        TestUtil.createDummyDocument(docStore, docUidBase+idx++, "application/dicom+xml");
        BaseDocument doc;
        String uid;
        for ( int i = 0 ; i < idx ; i++) {
            uid = docUidBase+i;
            doc = docStore.getDocument(uid, null);
            assertNotNull("Created Document not found! uid:"+uid, doc);
            assertEquals("Wrong document UID in document!", uid, doc.getDocumentUID());
            TestUtil.checkDocContent(doc.getInputStream(), TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        }
    }

    public void testCreateDocumentSameUID() throws IOException {
        String docUid = "create.sameUid.1.1";
        checkCreateSameDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        checkCreateSameDocument(docUid, TestUtil.MIME_TEXT_XML);
        checkCreateSameDocument(docUid, null);
        //document with unknown mime already exists because of last check!
        try { 
            TestUtil.createDummyDocument(docStore, docUid, "");
            fail("createDocument should throw a Exception when Document already exists! docUid:"+docUid+" mime:''");
        } catch ( IOException ignore) {}
        //MIME_APPLICATION_OCTET_STREAM is used for 'unknown mime type!
        try { 
            TestUtil.createDummyDocument(docStore, docUid, TestUtil.MIME_APPLICATION_OCTET_STREAM);
            fail("createDocument should throw a Exception when Document already exists! docUid:"+docUid+" mime:"+TestUtil.MIME_APPLICATION_OCTET_STREAM);
        } catch ( IOException ignore) {}
    }

    private void checkCreateSameDocument(String docUid, String mime)
            throws IOException {
        TestUtil.createDummyDocument(docStore, docUid, mime);
        try {
            TestUtil.createDummyDocument(docStore, docUid, mime);
            fail("createDocument should throw a Exception when Document already exists! docUid:"+docUid+" mime:"+mime);
        } catch ( IOException ignore) {}
    }
    
    public void testStoreDocumentStringDataHandler() throws IOException {
        String uid = "store.string.test.1.1";
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        docStore.storeDocument(uid, dh);
        checkDocument(uid, null, TestUtil.MIME_TEXT_PLAIN, TestUtil.DUMMY_PLAIN_DATA_SOURCE);
    }
    public void testStoreDocumentFileDataHandler() throws IOException {
        String uid = "store.file.test.1.1";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL urlXml = cl.getResource("test_content.xml");
        URL urlXml1 = cl.getResource("test_content1.xml");
        DataHandler dh = new DataHandler(urlXml);
        docStore.storeDocument(uid, dh);
        checkDocument(uid, null, null, dh.getDataSource());

        String uid1 = "store.file.test.1.2";
        DataHandler dh1 = new DataHandler(urlXml1);
        docStore.storeDocument(uid1, dh1);
        checkDocument(uid1, null, null, dh1.getDataSource());
    }
    public void testStoreDocumentSameUID() throws IOException {
        String uid = "store.sameUID.1.1";
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        BaseDocument docSaved = docStore.storeDocument(uid, dh);
        docSaved = docStore.storeDocument(uid, dh);
        docSaved = docStore.storeDocument(uid, dh);
        assertNull("storeDocument doesn't return null for same UID!", docSaved);
        checkDocument(uid, null, TestUtil.MIME_TEXT_PLAIN, TestUtil.DUMMY_PLAIN_DATA_SOURCE);
    }
    
    public void testStoreDocumentSameUIDDiffMime() throws IOException {
        String uid = "store.sameUIDdiffMime.1.1";
        DataHandler dhPlain = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        BaseDocument docSaved = docStore.storeDocument(uid, dhPlain);
        docSaved = docStore.storeDocument(uid, dhPlain);
        DataHandler dhXml = new DataHandler(TestUtil.DUMMY_XML_DATA_SOURCE);
        docSaved = docStore.storeDocument(uid, dhXml);
        assertNotNull("storeDocument return null for same UID but different MIME!", docSaved);

        checkDocument(uid, TestUtil.MIME_TEXT_PLAIN, TestUtil.MIME_TEXT_PLAIN, TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        checkDocument(uid, TestUtil.MIME_TEXT_XML, TestUtil.MIME_TEXT_XML, TestUtil.DUMMY_XML_DATA_SOURCE);
        checkDocument(uid, null, TestUtil.MIME_TEXT_PLAIN, TestUtil.DUMMY_PLAIN_DATA_SOURCE);
    }

    public void testDeleteDocument() throws IOException {
        String docUid = "delete.test.1.1";
        TestUtil.createDummyDocument(docStore, docUid, TestUtil.MIME_TEXT_PLAIN);
        TestUtil.createDummyDocument(docStore, docUid, TestUtil.MIME_TEXT_XML);
        TestUtil.createDummyDocument(docStore, docUid, null);
        assertEquals("Wrong Availabilty of created document!", Availability.ONLINE, docStore.getAvailability(docUid) );
        assertTrue("deleteDocument should return true if a document is deleted!",docStore.deleteDocument(docUid));
        assertEquals("Wrong Availabilty of deleted document!", Availability.NONEEXISTENT, docStore.getAvailability(docUid) );
        assertFalse("deleteDocument should return false if document is already deleted!",docStore.deleteDocument(docUid));
        assertFalse("deleteDocument should return false if document does not exist in any storage of current domain!",docStore.deleteDocument(TestUtil.UNKNOWN));
    }

    public void testCreateDocumentInPool() throws IOException {
        String docUid = "create.pool.1.1";
        TestUtil.setStorageAvailabilty(true);
        DocumentStorage store = docStore.selectDocStorageFromPoolOrDomain(TestUtil.POOL1);
        assertEquals("Wrong availability of storage from pool1!", Availability.ONLINE, store.getStorageAvailability() );
        assertEquals("Wrong availability of document in pool1 storage! should not exist! docUid"+docUid, Availability.NONEEXISTENT, store.getAvailabilty(docUid) );
        BaseDocument doc = docStore.createDocument(TestUtil.POOL1, docUid, TestUtil.MIME_TEXT_PLAIN);
        OutputStream os = doc.getOutputStream();
        os.write(TestUtil.DOC_CONTENT_DUMMY.getBytes());
        os.close();
        assertEquals("Wrong availability of document in pool1 storage! should exist! docUid"+docUid, Availability.ONLINE, store.getAvailabilty(docUid) );
        assertEquals("Wrong availability of document with no retrieve features set! should not be available! docUid"+docUid, Availability.NONEEXISTENT, docStore.getAvailability(docUid) );
        Set<Feature> features = new HashSet<Feature>();
        features.add(new Feature("TEST_RETRIEVE", ""));
        docStore.setRetrieveFeatures(features);
        assertEquals("Wrong availability of document with TEST_RETRIEVE feature set! should be available! docUid"+docUid, Availability.ONLINE, docStore.getAvailability(docUid) );
        checkDocument(docUid, null, TestUtil.MIME_TEXT_PLAIN, null);
    }

    public void testStoreDocumentInPool() throws IOException {
        String docUid = "store.pool.1.1";
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        docStore.storeDocument(TestUtil.POOL1, docUid, dh);
        Set<Feature> features = new HashSet<Feature>();
        features.add(new Feature("TEST_RETRIEVE", ""));
        docStore.setRetrieveFeatures(features);
        checkDocument(docUid, null, TestUtil.MIME_TEXT_PLAIN, TestUtil.DUMMY_PLAIN_DATA_SOURCE);
    }

    public void testStoreDocumentsNoPool() throws IOException {
        String docUid = "storeDocuments.nopool.";
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        Set<DataHandlerVO> docs = new HashSet<DataHandlerVO>();
        int nrDocs = 3;
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            docs.add(new DataHandlerVO(docUid+i, dh));
        }
        docStore.storeDocuments(null, docs );
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            checkDocument(docUid+i, null, null, null);
        }
    }
    
    public void testStoreDocumentsPool1() throws IOException {
        String docUid = "storeDocuments.pool1.";
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        Set<DataHandlerVO> docs = new HashSet<DataHandlerVO>();
        int nrDocs = 3;
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            docs.add(new DataHandlerVO(docUid+i, dh));
        }
        docStore.storeDocuments(TestUtil.POOL1, docs );
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            checkDocument(docUid+i, null, null, null);
        }
    }
    
    public void testStoreDocumentsUnknownPool() throws IOException {
        String docUid = "storeDocuments.unknown.pool.";
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        Set<DataHandlerVO> docs = new HashSet<DataHandlerVO>();
        int nrDocs = 3;
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            docs.add(new DataHandlerVO(docUid+i, dh));
        }
        docStore.storeDocuments("unknown", docs );
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            checkDocument(docUid+i, null, null, null);
        }
    }

    public void testStoreDocumentsFailed() throws IOException {
        String docUid = "storeDocuments.failed.";
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        Set<DataHandlerVO> docs = new HashSet<DataHandlerVO>();
        int nrDocs = 3;
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            docs.add(new DataHandlerVO(docUid+i, i<2 ? dh : null));
        }
        Logger logDFS = LoggerFactory.getLogger(DocumentFileStorage.class.getName());
        try {
            docStore.storeDocuments(null, docs );
            fail("storeDocuments must throw a NullPointerException!");
        } catch ( Exception ignore ) {}
        for ( int i = 0 ; i < nrDocs ; i++ ) {
            BaseDocument doc = docStore.getDocument(docUid+i, null);
            assertNull("No Document should exist if storeDocuments failed! uid:"+docUid+i, doc);
        }
    }
    
    public void testCommitDocument() {
        String docUid = "commit.pool.1.1";
        assertTrue("Commit not implemented and should return always true!", docStore.commitDocument(TestUtil.POOL1, docUid));
    }

    public void testDeleteDocumentStringString() throws IOException {
        String docUid = "delete.pool.1.1";
        assertFalse("Delete of nonexistent document should return false!", docStore.deleteDocument(TestUtil.POOL1, docUid));
        DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
        docStore.storeDocument(TestUtil.POOL1, docUid, dh);
        assertTrue("Delete existent document should return true!", docStore.deleteDocument(TestUtil.POOL1, docUid));
    }

    public void testCommitDocumentfromStorages() {
        String docUid = "commit.storages.1.1";
        Collection<DocumentStorage> c = registry.getDocumentStorages(TestUtil.DOMAIN_TEST);
        assertTrue("Commit not implemented and should return always true!",docStore.commitDocumentfromStorages(docUid, c));
    }

    public void testToHexString() {
        try {
            DocumentStore.toHexString(null);
            fail("toHexString(null) must throw NPE");
        } catch (NullPointerException ignore) {}
        String msg = "toHexString return unexpected value!";
        assertEquals(msg, "", DocumentStore.toHexString(new byte[0]) );
        assertEquals(msg, "39", DocumentStore.toHexString("9".getBytes()) );
        assertEquals(msg, "3732", DocumentStore.toHexString("72".getBytes()) );
        int charIdx;
        char[] hexDigits = "0123456789abcdef".toCharArray();
        byte b;
        for ( int len = 1 ; len < 21 ; len++){
            byte[] ba = new byte[len];
            char[] expected = new char[len<<1];
            Arrays.fill(expected, '0');
            charIdx = 0;
            for ( int idx = 0 ; idx < len ; idx++) {
                for ( int i = 0 ; i < 256 ; i++) {
                    b= (byte) i;
                    ba[idx] = b;
                    expected[charIdx++]=hexDigits[(b & 0xf0)>>>4];
                    expected[charIdx--]=hexDigits[b & 0x0f];
                    assertEquals(msg, new String(expected), DocumentStore.toHexString(ba));
                }
                charIdx++;charIdx++;
            }
        }
    }

    private void checkDocument(String uid, String retrMime,
            String expectedMime, DataSource expectedContent) throws IOException {
        BaseDocument doc = docStore.getDocument(uid, retrMime);
        assertNotNull("Stored Document not found ("+retrMime+")! uid:"+uid, doc);
        assertEquals("Wrong document UID in document!", uid, doc.getDocumentUID());
        if ( expectedMime != null) {
            assertEquals("Wrong mime type in document!", expectedMime, doc.getMimeType());
        }
        if ( expectedContent != null ) {
            TestUtil.checkDocContent(doc.getInputStream(), expectedContent);
        }
    }
}
