package org.dcm4chee.docstore;

import java.io.IOException;
import java.io.OutputStream;

import javax.activation.DataHandler;

import org.dcm4chee.docstore.test.DocStoreTestBase;
import org.dcm4chee.docstore.test.TestUtil;

public class BaseDocumentTest extends DocStoreTestBase {

    private static final String DOC_UID_2 = "docUid_2";
    private static final String DOC_UID_1 = "docUid_1";
    private static final DataHandler dh = new DataHandler(TestUtil.DUMMY_PLAIN_DATA_SOURCE);
    private BaseDocument docNull = new BaseDocument(null, null, null, null, -1, null);
    private BaseDocument doc1 = new BaseDocument(DOC_UID_1, TestUtil.MIME_TEXT_HTML, dh, Availability.ONLINE, 12345, TestUtil.getDummyStorage("test1"));
    private BaseDocument doc2 = new BaseDocument(DOC_UID_2, TestUtil.MIME_TEXT_XML, dh, Availability.UNAVAILABLE, 11111, TestUtil.getDummyStorage("test2"));

    public BaseDocumentTest() throws IOException {
        super();
        initDocStore();
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testGetDocumentUID() {
        assertNull("docNull: documentUID must be null!", docNull.getDocumentUID());
        assertEquals("doc1: Wrong documentUID!", DOC_UID_1, doc1.getDocumentUID());
        assertEquals("doc2: Wrong documentUID!", DOC_UID_2, doc2.getDocumentUID());
    }

    public void testGetMimeType() {
        assertNull("docNull: MimeType must be null!", docNull.getMimeType());
        assertEquals("doc1: Wrong MimeType!", TestUtil.MIME_TEXT_HTML, doc1.getMimeType());
        assertEquals("doc2: Wrong MimeType!", TestUtil.MIME_TEXT_XML, doc2.getMimeType());
    }

    public void testGetSize() {
        assertEquals("docNull: Wrong size!", -1, docNull.getSize());
        assertEquals("doc1: Wrong size!", 12345, doc1.getSize());
        assertEquals("doc2: Wrong size!", 11111, doc2.getSize());
    }

    public void testGetDataHandler() {
        assertNull("docNull: DataHandler must be null!", docNull.getDataHandler());
        doc1 = new BaseDocument(DOC_UID_1, TestUtil.MIME_TEXT_HTML, dh, Availability.ONLINE, 12345, TestUtil.getDummyStorage("test1"));        assertNotNull("doc1: DataHandler must NOT be null!", doc1.getDataHandler());
        assertEquals("doc1: Wrong DataHandler!", dh, doc1.getDataHandler());
        assertNotNull("doc2: DataHandler must NOT be null!", doc2.getDataHandler());
        assertEquals("doc2: Wrong DataHandler!", dh, doc2.getDataHandler());
    }

    public void testGetHash() {
        assertNull("docNull: hash must be null!", docNull.getHash());
        assertNull("doc1: hash must be null!", doc1.getHash());
        assertNull("doc2: hash must be null!", doc2.getHash());
        String hash = "123456789";
        doc1.setHash(hash);
        assertEquals("doc1: Wrong hashValue!", hash, doc1.getHash());
    }

    public void testGetInputStream() throws IOException {
        assertNull("docNull: InputStream must be null!", docNull.getInputStream());
        BaseDocument doc1 = TestUtil.createDummyDocument(
                docStore, "test.inputstream.1.1", TestUtil.MIME_TEXT_XML);
        assertNotNull("doc1: InputStream must NOT be null!", doc1.getInputStream());
        long size = dh.getInputStream().available();
        assertEquals("doc1: Wrong InputStream size!", size, doc1.getInputStream().available());
        assertNotNull("doc2: InputStream must NOT be null!", doc2.getInputStream());
        assertEquals("doc2: Wrong InputStream size!", size, doc2.getInputStream().available());
        assertFalse("Each getInputStream call must return a new InputStream!", doc2.getInputStream() == doc2.getInputStream() );
    }

    public void testGetOutputStream() throws IOException {
        assertNull("docNull: OutputStream must be null!", docNull.getOutputStream());
        BaseDocument doc1 = docStore.createDocument("test.outputstream.1.1", TestUtil.MIME_TEXT_PLAIN);
        OutputStream os = doc1.getOutputStream();
        assertNotNull("doc1: getOutputStream must NOT be null!", os);
        os.write(TestUtil.DOC_CONTENT_DUMMY.getBytes());
        os.close();
        assertEquals("doc1: Incorrect size of stored bytes:", TestUtil.DOC_CONTENT_DUMMY.length(), doc1.getInputStream().available());
        OutputStream os1 = doc1.getOutputStream();
        assertNotNull("doc1: second getOutputStream must NOT be null!", os1);
        assertTrue("2nd getOutputStream call must return them same OutputStream!", os == os1);
        try {
            os.write(TestUtil.DOC_CONTENT_DUMMY_XML.getBytes());
            fail("try to write in 2nd getOutputStream must fail! Is alraedy closed!");
        } catch (Exception ignore){}
        BaseDocument doc2 = docStore.createDocument("test.outputstream.1.2", TestUtil.MIME_TEXT_PLAIN);
        try {
            for ( int i = 0 ; i < 256 ; i++) {
                doc2.getOutputStream().write(i);
            }
            doc2.getOutputStream().close();
        } catch (Exception x) {
            fail("Reuse of documents getOutputStream fails with Exception!"+x);
        }
        assertEquals("doc2: Incorrect size of stored bytes:", 256, doc2.getInputStream().available());
    }

    public void testGetAvailability() {
        assertNull("docNull: Availability must be null!", docNull.getAvailability());
        assertEquals("doc1: Wrong Availability!", Availability.ONLINE, doc1.getAvailability());
        assertEquals("doc2: Wrong Availability!", Availability.UNAVAILABLE, doc2.getAvailability());
    }

    public void testGetStorage() {
        assertNull("docNull: Storage must be null!", docNull.getStorage());
        assertNotNull("doc1: Storage is null!", doc1.getStorage());
        assertEquals("doc1: Wrong Storage!", "test1", doc1.getStorage().getName());
        assertNotNull("doc2: Storage is null!", doc2.getStorage());
        assertEquals("doc2: Wrong Storage!", "test2", doc2.getStorage().getName());
    }

}
