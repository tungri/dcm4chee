package org.dcm4chee.docstore;

import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.activation.DataHandler;

import org.dcm4chee.docstore.spi.DocumentStorage;
import org.dcm4chee.docstore.test.DocStoreTestBase;
import org.dcm4chee.docstore.test.TestUtil;

public class DocumentStorageHashSupportTest extends DocStoreTestBase {

    private static final DataHandler dhPlainText = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
    private static final DataHandler dhXmlText = new DataHandler(TestUtil.DUMMY_XML_DATA_SOURCE);

    public DocumentStorageHashSupportTest() throws IOException {
        super();
        initDocStore();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSetHash() throws IOException, NoSuchAlgorithmException {
        String docUid = "docUid_testSetHash";
        String docUidEmpty = docUid+"_empty";
        BaseDocument doc1 = docStore.createDocument(docUidEmpty, TestUtil.MIME_TEXT_PLAIN);
        DocumentStorage store = doc1.getStorage();
        BaseDocument doc = new BaseDocument(docUid, TestUtil.MIME_TEXT_PLAIN, null, null, 0, store);
        assertFalse("No hashValue! Should return false", store.setHash(doc, "hash"));
        assertFalse("No Document! Should return false", store.setHash(doc, "dummy"));
        doc = docStore.createDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        String hash = writeDocWithHash(doc, dhPlainText);
        assertFalse("Document already has Hash value! Should return false", store.setHash(doc, "dummy"));
        dhPlainText.writeTo(doc1.getOutputStream());
        doc1.getOutputStream().close();
        assertTrue("Document exists and has no Hash value! Should return true", store.setHash(doc1, "dummy"));
    }
    
    public void testCreateDocumentWithHash() throws IOException, NoSuchAlgorithmException {
        String docUid = "docUid_CreateDocumentWithHash";
        BaseDocument doc = docStore.createDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        String hash = writeDocWithHash(doc, dhPlainText);
        BaseDocument docr = docStore.getDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        assertNotNull("Document not found! "+docUid, docr);
        assertNotNull("No Hash Value in retrieved document! docUid:"+docUid, docr.getHash());
        assertEquals("Wrong hash value! docUid:"+docUid, hash, docr.getHash());
    }

    public void testCreateMimeDocumentsWithHash() throws IOException, NoSuchAlgorithmException {
        String docUid = "docUid_CreateMimeDocumentsWithHash";
        BaseDocument doc1 = docStore.createDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        BaseDocument doc2 = docStore.createDocument(docUid, TestUtil.MIME_TEXT_XML);
        String hash1 = writeDocWithHash(doc1, dhPlainText);
        BaseDocument docr1 = docStore.getDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        assertNotNull("Document not found! "+docUid, docr1);
        assertNotNull("No Hash Value in retrieved document! docUid:"+docUid, docr1.getHash());
        assertEquals("Wrong hash value! docUid:"+docUid, hash1, docr1.getHash());
        
        String hash2 = writeDocWithHash(doc2, dhXmlText);
        BaseDocument docr2 = docStore.getDocument(docUid, TestUtil.MIME_TEXT_XML);
        assertNotNull("Document not found! "+docUid, docr2);
        assertNotNull("No Hash Value in retrieved document! docUid:"+docUid, docr2.getHash());
        assertEquals("Wrong hash value! docUid:"+docUid, hash2, docr2.getHash());
        assertFalse("Hash of different documents must be different:"+docUid, hash1.equals(hash2));
    }

    private String writeDocWithHash(BaseDocument doc, DataHandler dh)
    throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        DigestOutputStream dos = new DigestOutputStream(doc.getOutputStream(), md);
        dh.writeTo(dos);
        dos.close();
        String hash = DocumentStore.toHexString(md.digest());
        assertTrue("Hash value was not set! "+doc.getDocumentUID(), doc.getStorage().setHash(doc, hash));
        return hash;
    }

    public void testStoreDocument() throws IOException, NoSuchAlgorithmException {
        String docUid = "docUid_StoreDocument";
        BaseDocument doc = docStore.storeDocument(docUid, dhPlainText);
        BaseDocument docr = docStore.getDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        assertNotNull("Document not found! "+docUid, docr);
        assertNotNull("No Hash Value in retrieved document! docUid:"+docUid, docr.getHash());
        assertEquals("Wrong hash value! docUid:"+docUid, doc.getHash(), docr.getHash());
    }

    public void testStoreMimeDocuments() throws IOException, NoSuchAlgorithmException {
        String docUid = "docUid_StoreMimeDocuments";
        BaseDocument doc1 = docStore.storeDocument(docUid, dhPlainText);
        BaseDocument doc2 = docStore.storeDocument(docUid, dhXmlText);
        BaseDocument docr1 = docStore.getDocument(docUid, TestUtil.MIME_TEXT_PLAIN);
        assertNotNull("Document not found! "+docUid, docr1);
        assertNotNull("No Hash Value in retrieved document! docUid:"+docUid, docr1.getHash());
        assertEquals("Wrong hash value! docUid:"+docUid, doc1.getHash(), docr1.getHash());
        
        BaseDocument docr2 = docStore.getDocument(docUid, TestUtil.MIME_TEXT_XML);
        assertNotNull("Document not found! "+docUid, docr2);
        assertNotNull("No Hash Value in retrieved document! docUid:"+docUid, docr2.getHash());
        assertEquals("Wrong hash value! docUid:"+docUid, doc2.getHash(), docr2.getHash());
        assertFalse("Hash of different document content must be different:"+docUid, doc1.getHash().equals(doc2.getHash()));
    }
    
}
