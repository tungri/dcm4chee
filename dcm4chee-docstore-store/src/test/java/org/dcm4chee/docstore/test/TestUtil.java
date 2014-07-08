package org.dcm4chee.docstore.test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.util.ByteArrayDataSource;

import org.dcm4chee.docstore.Availability;
import org.dcm4chee.docstore.BaseDocument;
import org.dcm4chee.docstore.DataHandlerVO;
import org.dcm4chee.docstore.DocumentStore;
import org.dcm4chee.docstore.DocumentStoreNoPoolTest;
import org.dcm4chee.docstore.spi.BaseDocumentStorage;
import org.dcm4chee.docstore.spi.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import junit.framework.TestCase;

public class TestUtil {

    public static DataSource DUMMY_PLAIN_DATA_SOURCE;
    public static DataSource DUMMY_XML_DATA_SOURCE;
    public static final String DOMAIN_TEST = "TEST";
    public static final String POOL1 = "pool1";
    public static final String UNKNOWN = "unknown";
    public static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String DOC_CONTENT_DUMMY = "DUMMY";
    public static final String DOC_CONTENT_DUMMY_XML = "<DUMMY>";
    public static final String DUMMY_DOC_UID = "1.1.1.1.1.1";
    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String MIME_TEXT_XML = "text/xml";
    public static final String MIME_TEXT_HTML = "text/html";
    
    private static Logger log = LoggerFactory.getLogger( DocumentStoreNoPoolTest.class );
    
    static {
        try {
            DUMMY_PLAIN_DATA_SOURCE = new ByteArrayDataSource(TestUtil.DOC_CONTENT_DUMMY, TestUtil.MIME_TEXT_PLAIN);
            DUMMY_XML_DATA_SOURCE = new ByteArrayDataSource(TestUtil.DOC_CONTENT_DUMMY_XML, TestUtil.MIME_TEXT_XML);
        } catch (IOException e) {
            log.error("Can't initialize Datasource for DocStore Tests!",e);
        }
    }

    public static void checkNames( DocumentStorage store, String msg, List<String> names) {
        String n = store.getName();
        TestCase.assertTrue("Name of DocumentStorage "+msg+" is incorrect! name:"+n, names.contains(n));
    }

    public static void checkDocContent(InputStream docIs, DataSource ds) throws IOException {
        InputStream expectedIs = ds.getInputStream();
        try {
            if ( !(docIs instanceof BufferedInputStream) ) {
                docIs = new BufferedInputStream(docIs);
            }
            if ( !(expectedIs instanceof BufferedInputStream) ) {
                expectedIs = new BufferedInputStream(expectedIs);
            }
            int b1, b2;
            long pos = 0;
            while ( (b1 = docIs.read()) != -1 ) {
                b2 = expectedIs.read();
                if ( b2 == -1 ) {
                    TestCase.fail("Document content is longer as expected! current pos:"+getPositionString(pos)+" remaining:"+docIs.available() );
                } else if (b1 != b2 ) {
                    TestCase.fail("Difference found at pos "+getPositionString(pos)+"!expected:"+Integer.toHexString(b2)+"h  found:"+Integer.toHexString(b1)+"h");
                }
                pos++;
            }
            b2 = expectedIs.read();
            if ( b2 != -1 ) {
                TestCase.fail("Document ist truncated at pos "+getPositionString(pos)+"! remaining:"+expectedIs.available());
            }
        } finally {
            try {
                docIs.close();
                expectedIs.close();
            } catch (Exception ignore) {}
        }
    }

    private static String getPositionString(long pos) {
        return pos+" ("+Long.toHexString(pos)+"h)";
    }

    public static BaseDocument createDummyDocument(DocumentStore docStore, String docUid, String mime) throws IOException {
        BaseDocument doc = docStore.createDocument(docUid, mime);
        OutputStream out = doc.getOutputStream();
        out.write(TestUtil.DOC_CONTENT_DUMMY.getBytes());
        out.close();
        return doc;
    }

    public static void setStorageAvailabilty(boolean avail) {
        long free = avail ? 100000000l : 0l;
        DummyDFCommandMBean.setFreeSpace("store", free);
        DummyDFCommandMBean.setFreeSpace("pool1_1", free);
        DummyDFCommandMBean.setFreeSpace("pool1_2", free);
        DummyDFCommandMBean.setFreeSpace("pool2_1", free);
        DummyDFCommandMBean.setFreeSpace("pool2_2", free);
    }

    public static DocumentStorage getDummyStorage(String name) {
        return new BaseDocumentStorage(name) {
            public BaseDocument createDocument(String docUid, String mime)
            throws IOException {
                return null;
            }

            public Availability getAvailabilty(String docUid) {
                return Availability.NONEEXISTENT;
            }

            public Availability getStorageAvailability() {
                return Availability.NONEEXISTENT;
            }

            public String getStorageType() {
                return "TEST_DUMMY";
            }

            public BaseDocument retrieveDocument(String docUid) throws IOException {
                return null;
            }

            public BaseDocument retrieveDocument(String docUid, String mime)
            throws IOException {
                return null;
            }

            public BaseDocument storeDocument(String docUid, DataHandler xdsDoc)
            throws IOException {
                return null;
            }

            public BaseDocument[] storeDocuments(Set<DataHandlerVO> docs)
                    throws IOException {
                return null;
            }

        };
    }

    public static void deleteDir(File f, boolean delThis) {
        if ( f.isDirectory()) {
            for ( File f1 : f.listFiles() ) {
                deleteDir(f1, true);
            }
        }
        if ( delThis && f.delete() ) {
            log.debug("Deleted:"+f);
        }
    }    

}
