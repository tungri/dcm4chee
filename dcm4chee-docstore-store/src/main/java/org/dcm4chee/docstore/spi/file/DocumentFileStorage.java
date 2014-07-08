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
package org.dcm4chee.docstore.spi.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.dcm4chee.docstore.Availability;
import org.dcm4chee.docstore.BaseDocument;
import org.dcm4chee.docstore.DataHandlerVO;
import org.dcm4chee.docstore.DocumentStore;
import org.dcm4chee.docstore.Feature;
import org.dcm4chee.docstore.spi.BaseDocumentStorage;
import org.dcm4chee.docstore.util.FileSystemInfo;
import org.jboss.system.server.ServerConfigLocator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentFileStorage extends BaseDocumentStorage {

    private static final String UNKNOWN_MIME = "application/octet-stream";
    private static final String DEFAULT_MIME_FILENAME = "default.mime";
    public static final String STORAGE_TYPE = "SimpleFileStorage";
    private static final String DEFAULT_BASE_DIR = "store/docs";
    private static final String HASH_EXTENSION = ".hash";

    private int[] directoryTree = new int[]{347, 331, 317};

    private File baseDir;
    private Availability currentAvailabilty;
    private long minFree  = 10000000l;

    private boolean computeHash = true;
    private long lastCheck;
    private int checkIntervall = 600000;

    private static Logger log = LoggerFactory.getLogger( DocumentFileStorage.class );

    public DocumentFileStorage() {
        this(STORAGE_TYPE);
    }

    public DocumentFileStorage(String name) {
        super(name);
        this.addFeature( Feature.CREATE_EMPTY_DOCUMENT );
        this.addFeature( Feature.MULTI_MIME );
        this.addFeature(new Feature("SHA1", "Generation of SHA1 hash") );
    }

    public void init(String initString) {
        String basedir = null;
        String s = null;
        try {
            Properties p = this.readInitAsProperties(initString);
            basedir = p.getProperty("BASE_DIR");
            s = p.getProperty("minFree");
            if ( s != null ) {
                minFree = Long.parseLong(s);
            }
            s = p.getProperty("checkIntervall");
            if ( s != null ) {
                checkIntervall = Integer.parseInt(s.trim());
            }
            s = p.getProperty("disableHash");
            if ( s != null && s.equalsIgnoreCase("true") ) {
                computeHash = false;
            }
            s = p.getProperty("dfCmdName", "dcm4chee.archive:service=dfcmd");
            FileSystemInfo.setDFCmdServiceName(s);
        } catch (IOException e) {
            log.error("Cant initialize DocumentFileStorage!", e);
            throw new IllegalArgumentException("Initialization of DocumentFileStorage failed! initString"+initString);
        } catch (NumberFormatException e) {
            log.warn("Illegal minFree value! ("+s+")! Use default:"+minFree,e);
        }
        setBaseDir(basedir == null ? DEFAULT_BASE_DIR : basedir);
        log.info("DocumentFileStorage initialized! :"+this);
    }

    public void setBaseDir(String dirName) {
        File dir = new File(dirName);
        if ( dir.isAbsolute() ) {
            baseDir = dir;
        } else {
            try {
                File serverHomeDir = ServerConfigLocator.locate().getServerHomeDir();
                baseDir = new File(serverHomeDir, dir.getPath());
            } catch (Throwable x) {
                log.debug("Error getting Server Home Directory! Use current working directory instead! BaseDir:"+dir.getAbsolutePath());
                baseDir = dir;
            }
        }
        if ( !baseDir.exists() ) {
            baseDir.mkdirs();
        }
    }
    
    public File getBaseDir() {
        return baseDir;
    }

    public Availability getStorageAvailability() {
        log.debug("getStorageAvailability called! currentAvailabilty:"+currentAvailabilty);
        return currentAvailabilty == null || System.currentTimeMillis() - lastCheck > checkIntervall ? 
                checkAvailabilty() : currentAvailabilty;
    }

    public Availability checkAvailabilty() {
        log.debug("checkAvailabilty called! currentAvailabilty:"+currentAvailabilty);
        Availability oldAvail = currentAvailabilty;
        currentAvailabilty = FileSystemInfo.getFileSystemAvailability(baseDir, minFree);
        if ( oldAvail == null || !oldAvail.equals(currentAvailabilty)) {
            this.notifyAvailabilityChanged(oldAvail, currentAvailabilty);
        }
        log.debug("checkAvailabilty done! currentAvailabilty:"+currentAvailabilty);
        lastCheck = System.currentTimeMillis();
        return currentAvailabilty;
    }

    public boolean deleteDocument(String docUID) {
        boolean b = false;
        File docPath = getDocumentPath(docUID);
        log.debug("deleteDocument docPath:"+docPath);
        if ( docPath.exists() ) {
            String[] docFiles = null;
            if ( getNumberOfListeners() > 0 ) {
                docFiles = docPath.list();
            }
            b = deleteFile(docPath);
            log.debug("docPath deleted?:"+b);
            if ( b && docFiles != null) {
                for ( String f : docFiles ) {
                    log.debug(" docFile:"+f);
                    if ( ! DEFAULT_MIME_FILENAME.equals(f))
                        notifyDeleted(new BaseDocument(docUID, f, null, null, -1, null));
                }
            }
            purgeDocumentPath(docPath.getParentFile());
        }
        return b;
    }

    private boolean deleteFile(File f) {
        if ( f.isDirectory()) {
            File[] files = f.listFiles();
            for ( int i = 0; i < files.length ; i++ ) {
                deleteFile(files[i]);
            }
        }
        log.info("M-DELETE DocumentStorage file:"+f);
        return f.delete();
    }

    private void purgeDocumentPath(File dir) {
        if (dir == null || dir.equals(baseDir))
            return;
        File[] files = dir.listFiles();
        if (files != null && files.length == 0) {
            dir.delete();
            purgeDocumentPath(dir.getParentFile());
        }
    }

    public Availability getAvailabilty(String docUid) {
        File f = getDocumentPath(docUid);
        return f.exists() ? Availability.ONLINE : Availability.NONEEXISTENT;
    }

    public String getRetrieveURL(String docUid) {
        return null;
    }

    public String getStorageType() {
        return STORAGE_TYPE;
    }

    public BaseDocument retrieveDocument(String docUid) throws IOException {
        return retrieveDocument(docUid, null);
    }

    public BaseDocument retrieveDocument(String docUid, String mime) throws IOException {
        log.debug("RetrieveDocument docUid:"+docUid+" mime:"+mime);
        File docPath = getDocumentPath(docUid);
        BaseDocument doc = null;
        String[] m = new String[]{mime};
        if ( docPath.exists() ) {
            File f = getDocumentFile(docPath, m);
            log.debug("docFile:"+f+" exists:"+f.exists());
            if ( f.exists() ){
                doc = new BaseDocument(docUid, m[0], new DataHandler(new FileDataSource(f)), 
                        Availability.ONLINE, f.length(), this);
                byte[] ba = readFile(getHashFile(f));
                if ( ba != null ) {
                    doc.setHash(new String(ba));
                }
                notifyRetrieved(doc);
            }
        }
        return doc;
    }

    public BaseDocument createDocument(String docUid, String mime) throws IOException {
        if ( mime == null || mime.trim().length() < 1 ) mime = UNKNOWN_MIME;
        File docPath = getDocumentPath(docUid);
        File defaultMime = getMimeFile(docPath);
        if ( ! defaultMime.exists() ) {
            writeFile(getMimeFile(docPath),mime.getBytes());
        }
        File docFile = getDocumentFile(docPath, new String[]{mime});
        if ( docFile.exists() ) {
            throw new IOException("Create empty document failed! Document already exists! docUid:"+docUid+" mime:"+mime);
        }
        log.debug("M-CREATE: Empty document file created:"+docFile);
        BaseDocument doc = new BaseDocument(docUid, mime, 
                new DataHandler(new FileDataSource(docFile)), Availability.UNAVAILABLE, docFile.length(), this);
        notifyCreated(doc);
        return doc;
    }
    
    public boolean setHash(BaseDocument doc, String hash) {
        if ( hash != null ) {
            try {
                File docFile = getDocumentFile(getDocumentPath(doc.getDocumentUID()), new String[]{doc.getMimeType()});
                if ( docFile.exists() ) {
                    File hashFile = getHashFile(docFile);
                    if ( ! hashFile.exists() ) {
                        writeFile(getHashFile(docFile), hash.getBytes());
                        return true;
                    }
                    log.error("Hash File already exists for document! docUID:"+doc.getDocumentUID());
                } else {
                    log.error("setHash called for non existing document! docUID:"+doc.getDocumentUID());
                }
            } catch (IOException e) {
                log.error("Error write Hash File for docUid:"+doc.getDocumentUID()+"! hash:"+hash, e);
            }
        }
        return false;
    }

    public BaseDocument[] storeDocuments(Set<DataHandlerVO> dhVOs)
        throws IOException {
        BaseDocument[] docs = new BaseDocument[dhVOs.size()];
        int i = 0;
        try {
            for ( DataHandlerVO vo : dhVOs ) {
                docs[i++] = storeDocument(vo.getUid(), vo.getDataHandler());
            }
        } catch ( Exception x) {
            log.error("storeDocuments failed! RollBack "+(--i)+" Documents!",x);
            for ( int j = 0 ; j < i ; j++ ) {
                log.debug("   Rollback storeDocuments! ("+j+") Delete Document:"+docs[j]);
                if ( docs[j] != null )
                    this.deleteDocument(docs[j].getDocumentUID());
            }
            IOException iox = new IOException("storeDocuments failed!");
            iox.initCause(x);
            throw iox;
        }
        return docs;
    }

    public BaseDocument storeDocument(String docUid, DataHandler dh) throws IOException {
        File docPath = getDocumentPath(docUid);
        log.debug("#### Document Path:"+docPath);
        log.debug("#### Document Path exist?:"+docPath.exists());
        try {
            String[] mime = new String[]{dh.getContentType()};
            File docFile = this.getDocumentFile(docPath, mime);
            log.debug("#### Document File:"+docFile);
            log.debug("#### Document File exist?:"+docFile.exists());
            if ( docFile.exists() )
                return null;
            byte[] digest = writeFile(docFile, dh);
            File mimeFile = getMimeFile(docPath);
            if ( !mimeFile.exists() ) {
                writeFile(mimeFile,mime[0].getBytes());
            }
            BaseDocument doc = new BaseDocument(docUid, mime[0], 
                    new DataHandler(new FileDataSource(docFile)), Availability.ONLINE, docFile.length(), this);
            if ( digest != null ) {
                doc.setHash(DocumentStore.toHexString(digest));
                writeFile(getHashFile(docFile), doc.getHash().getBytes());
            }
            notifyStored(doc);
            return doc;
        } catch (NoSuchAlgorithmException x) {
            log.error("Store of document "+docUid+" failed! Can't calculate hash value!",x);
            throw new IOException("Store of document "+docUid+" failed! Unknown Hash Algorithm!");
        } finally {
            try {
                dh.getInputStream().close();
            } catch (Exception ignore) {
                log.warn("Error closing InputStream of DataHandler! Ignored",ignore );
            }
        }
    }

    private byte[] writeFile(File f, DataHandler dh) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = null;
        OutputStream out = null;
        if ( !f.exists() ) {
            log.debug("#### Write File:"+f);
            try {
                f.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(f);
                if (computeHash) {
                    md = MessageDigest.getInstance("SHA1");
                    out = new DigestOutputStream(fos, md);
                } else {
                    log.debug("SHA1 feature is disabled!");
                    out = fos;
                }
                dh.writeTo(out);
                log.debug("#### File written:"+f+" exists:"+f.exists());
            } finally {
                if ( out != null )
                    try {
                        out.close();
                    } catch (IOException ignore) {
                        log.error("Ignored error during close!",ignore);
                    }

            }
        }
        return md == null ? null : md.digest();
    }

    private void writeFile(File f, byte[] ba) throws IOException {
        FileOutputStream fos = null;
        try {
            f.getParentFile().mkdirs();
            fos = new FileOutputStream(f);
            fos.write(ba);
        } finally {
            if ( fos != null ) {
                try {
                    fos.close();
                } catch (IOException ignore) {
                    log.warn("Cant close FileOutputStream! ignored! reason:"+ignore);
                }
            }
        }
    }
    private byte[] readFile(File f) throws IOException {
        if ( !f.exists() )
            return null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            byte[] ba = new byte[fis.available()];
            fis.read(ba);
            return ba;
        } finally {
            if ( fis != null ) {
                try {
                    fis.close();
                } catch (IOException ignore) {
                    log.warn("Cant close FileInputStream! ignored! reason:"+ignore);
                }
            }
        }
    }

    private File getDocumentPath(String docUid) {
        if ( baseDir == null ) {
            setBaseDir(DEFAULT_BASE_DIR);
            log.warn("DocumentFileStorage not initialized! Set default Base Directory:"+baseDir);
        }
        log.debug("getDocumentPath for "+docUid+" for DocumentStorage "+this.getName()+". baseDir:"+baseDir);
        return new File( baseDir, getFilepath(docUid) );
    }

    /**
     * Get the File object for the Document file for given docPath and mime type.
     * <p/>
     * docPath... Path built with documentUID<br/>
     * mime...... String array with one element containing the content type (mime).<br/>
     * If the element is <code>null</code> (unknown mime) it will be set either to the content of default mime file (if present)
     * or to <code>UNKNOWN_MIME</code>.
     * 
     * @param docPath   File of the base directory to a document
     * @param mime      String[] with one element containing mime type.
     * @return
     * @throws IOException
     */
    private File getDocumentFile(File docPath, String[] mime) throws IOException {
        if ( mime[0] == null || mime[0].trim().length() < 1 ) {
            byte[] ba = readFile(getMimeFile(docPath));
            mime[0] = ba != null ? new String(ba) : UNKNOWN_MIME;
        }
        String m = mime[0].replace('/', '_');
        return new File(docPath, URLEncoder.encode(m, "UTF-8"));
    }

    private File getMimeFile(File docPath) {
        return new File(docPath.getAbsolutePath(), DEFAULT_MIME_FILENAME);
    }


    private String getFilepath(String uid) {
        if (directoryTree == null) 
            return uid;
        StringBuffer sb = new StringBuffer();
        int hash = uid.hashCode();
        int modulo;
        for (int i = 0; i < directoryTree.length; i++) {
            if (directoryTree[i] == 0) {
                sb.append(Integer.toHexString(hash)).append(File.separatorChar);
            } else {
                modulo = hash % directoryTree[i];
                if (modulo < 0) {
                    modulo *= -1;
                }
                sb.append(modulo).append(File.separatorChar);
            }
        }
        sb.append(uid);
        return sb.toString();
    }

    private File getHashFile(File f) {
        return new File(f.getAbsolutePath()+HASH_EXTENSION);
    }
    
    public String toString() {
        return super.toString()+" baseDir:"+baseDir+"(minFree:"+this.minFree+") "+
        this.getStorageAvailability();
    }

}
