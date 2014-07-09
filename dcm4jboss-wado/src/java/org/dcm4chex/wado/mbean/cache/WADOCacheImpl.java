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

package org.dcm4chex.wado.mbean.cache;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.log4j.Logger;
import org.dcm4chex.archive.config.DeleterThresholds;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.util.CacheJournal;
import org.dcm4chex.archive.util.FileSystemUtils;
import org.dcm4chex.archive.util.FileUtils;
import org.dcm4chex.wado.mbean.WADOSupport;

/**
 * @author franz.willer
 * 
 */
public class WADOCacheImpl implements WADOCache {

    private static final int[] EMPTY_INTS = {};

    public static final String DEFAULT_IMAGE_QUALITY = "75";

    /** Buffer size for read/write */
    private static final int BUF_LEN = 65536;

    private static WADOCacheImpl singletonWADO = null;

    protected static Logger log = Logger.getLogger(WADOCacheImpl.class
            .getName());

    private String dataRootDir;

    private String journalRootDir;

    private CacheJournal journal = new CacheJournal();

    private int[] numberOfStudyBags = EMPTY_INTS;

    private DeleterThresholds deleterThresholds = new DeleterThresholds(
            "23:50MB", true);

    /**
     * Flag to indicate if client side redirection should be used if DICOM
     * object is not locally available.
     */
    private boolean clientRedirect = false;

    /**
     * Flag to indicate if caching should be used in case of server side
     * redirect,
     */
    private boolean redirectCaching = true;

    private String imageQuality = DEFAULT_IMAGE_QUALITY;

    protected String imageWriterClass;
    protected String pngImageWriterClass;

    protected WADOCacheImpl() {
    }

    public String getDataRootDir() {
        return dataRootDir;
    }

    public void setDataRootDir(String dataRootDir) {
        journal.setDataRootDir(FileUtils.resolve(new File(dataRootDir)));
        this.dataRootDir = dataRootDir;
    }

    public String getJournalRootDir() {
        return journalRootDir;
    }

    public void setJournalRootDir(String journalRootDir) {
        journal.setJournalRootDir(FileUtils.resolve(new File(journalRootDir)));
        this.journalRootDir = journalRootDir;
    }

    public String getJournalFilePathFormat() {
        return journal.getJournalFilePathFormat();
    }

    public void setJournalFilePathFormat(String journalFilePathFormat) {
        journal.setJournalFilePathFormat(journalFilePathFormat);
    }

    public String getNumberOfStudyBags() {
        if (numberOfStudyBags.length == 0) {
            return "1";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(numberOfStudyBags[0]);
        for (int i = 1; i < numberOfStudyBags.length; i++) {
            sb.append('*').append(numberOfStudyBags[i]);
        }
        return sb.toString();
    }

    public static int leastPrimeAsLargeAs(int target) {
        int prime = target;
        while (!isPrime(prime)) {
            prime++;
        }
        return prime;
    }

    public static boolean isPrime(int candidate) {
        int sqrt = (int) Math.sqrt(candidate) + 1;
        for (int i = 2; i <= sqrt; ++i) {
            if (candidate % i == 0) {
                return false;
            }
        }
        return true;
    }

    public void setNumberOfStudyBags(String s) {
        if (s.trim().equals("1")) {
            numberOfStudyBags = EMPTY_INTS;
            return;
        }
        StringTokenizer tokens = new StringTokenizer(s, "* ");
        int[] tmp = new int[tokens.countTokens()];
        if (tmp.length == 0) {
            throw new IllegalArgumentException(s);
        }
         for (int i = 0; i < tmp.length; i++) {
            int v;
            try {
                v = Integer.parseInt(tokens.nextToken());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(s);
            }
            if (v <= 1) {
                throw new IllegalArgumentException(s);
            }
            v = leastPrimeAsLargeAs(v);
            for (int j = 0; j < i; ++j) {
                if (v == tmp[j]) {
                    v = leastPrimeAsLargeAs(v+1);
                    j = -1;
                }
            }
            tmp[i] = v;
        }
        numberOfStudyBags = tmp;
    }

    public final String getImageQuality() {
        return imageQuality;
    }

    public final void setImageQuality(String imageQuality) {
        int intval = Integer.parseInt(imageQuality);
        if (intval <= 0 || intval > 100) {
            throw new IllegalArgumentException("imageQuality: " + imageQuality
                    + " not between 1 and 100.");
        }
        this.imageQuality = imageQuality;
    }

    public final String getImageWriterClass() {
        return imageWriterClass;
    }

    public void setImageWriterClass(String imageWriterClass) {
        getImageWriterWriter("JPEG", imageWriterClass).dispose();
        this.imageWriterClass = imageWriterClass;
    }

    public final String getPNGImageWriterClass() {
        return pngImageWriterClass;
    }

    public void setPNGImageWriterClass(String imageWriterClass) {
        getImageWriterWriter("PNG", imageWriterClass).dispose();
        this.pngImageWriterClass = imageWriterClass;
    }

    private static WADOCacheImpl createWADOCache() {
        try {
            Class.forName("com.sun.image.codec.jpeg.JPEGImageEncoder");
            return new WADOCacheImplSun();
        } catch (ClassNotFoundException e) {
            log.info("com.sun.image.codec.jpeg.JPEGImageEncoder not available");
            return new WADOCacheImpl();
        }
    }

    /**
     * Returns the singleton instance of WADOCache.
     * 
     * @return
     */
    public static WADOCache getWADOCache() {
        if (singletonWADO == null) {
            singletonWADO = createWADOCache();
        }
        return singletonWADO;
    }

    /**
     * Get a region of an image of special size from cache.
     * <p>
     * This method use a image size (rows and columns) and a region (two points)
     * to search on a special path of this cache.
     * 
     * @param studyUID
     *            Unique identifier of the study.
     * @param seriesUID
     *            Unique identifier of the series.
     * @param instanceUID
     *            Unique identifier of the instance.
     * @param rows
     *            Image height in pixel.
     * @param columns
     *            Image width in pixel.
     * @param region
     *            Image region defined by two points in opposing corners
     * @param windowWidth
     *            Decimal string representing the contrast of the image.
     * @param windowCenter
     *            Decimal string representing the luminosity of the image.
     * @param imageQuality
     *            Integer string (1-100) representing required quality of the
     *            image to be returned within the range 1 to 100
     * 
     * @return The File object of the image if in cache or null.
     */
    public File getImageFile(String studyUID, String seriesUID,
            String instanceUID, String rows, String columns, String region,
            String windowWidth, String windowCenter, String imageQuality,
            String contentType, String suffix) {
        File file = this._getImageFile(rows + "-" + columns + "-" + region
                + "-" + windowWidth + "-" + windowCenter + "-"
                + maskNull(imageQuality, this.imageQuality), studyUID,
                seriesUID, instanceUID, suffix, contentType);
        if (log.isDebugEnabled())
            log.debug("check cache file(exist:" + file.exists() + "):" + file);
        if (file.exists()) {
            try {
                journal.record(file);
            } catch (IOException e) {
                log.warn("Failed to record access to cache file: ", e);
            }
            return file;
        } else {
            return null;
        }
    }

    private static String maskNull(String val, String defval) {
        return val != null ? val : defval;
    }

    /**
     * Put a region of an image of special size to this cache.
     * <p>
     * Stores the image on a special path of this cache.
     * 
     * @param image
     *            The image (with special size)
     * @param studyUID
     *            Unique identifier of the study.
     * @param seriesUID
     *            Unique identifier of the series.
     * @param instanceUID
     *            Unique identifier of the instance.
     * @param rows
     *            Image height in pixel.
     * @param columns
     *            Image width in pixel.
     * @param region
     *            Image region defined by two points in opposing corners
     * @param windowWidth
     *            Decimal string representing the contrast of the image.
     * @param windowCenter
     *            Decimal string representing the luminosity of the image.
     * @param imageQuality
     *            Integer string (1-100) representing required quality of the
     *            image to be returned within the range 1 to 100
     * 
     * @return The File object of the image in this cache.
     * @throws IOException
     */
    public File putImage(BufferedImage image, String studyUID,
            String seriesUID, String instanceUID, String rows, String columns,
            String region, String windowWidth, String windowCenter,
            String imageQuality, String contentType, String suffix) throws IOException {
        imageQuality = maskNull(imageQuality, this.imageQuality);
        File file = this._getImageFile(rows + "-" + columns + "-" + region
                + "-" + windowWidth + "-" + windowCenter + "-" + imageQuality,
                studyUID, seriesUID, instanceUID, suffix, contentType);
        _writeImageFile(image, file, contentType, imageQuality);
        try {
            journal.record(file);
        } catch (IOException e) {
            file.delete();
            throw e;
        }
        return file;
    }

    /**
     * Puts a stream to this cache.
     * 
     * @param stream
     *            The InputStream to store.
     * @param studyUID
     *            Unique identifier of the study.
     * @param seriesUID
     *            Unique identifier of the series.
     * @param instanceUID
     *            Unique identifier of the instance.
     * @param rows
     *            Image height in pixel.
     * @param columns
     *            Image width in pixel.
     * @param region
     *            Rectangular region of the image (defined by two points)
     * @param windowWidth
     *            Decimal string representing the contrast of the image.
     * @param windowCenter
     *            Decimal string representing the luminosity of the image.
     * @param imageQuality
     *            Integer string (1-100) representing required quality of the
     *            image to be returned within the range 1 to 100
     * 
     * @return The stored File object.
     * 
     * @throws IOException
     */
    public File putStream(InputStream stream, String studyUID,
            String seriesUID, String instanceUID, String rows, String columns,
            String region, String windowWidth, String windowCenter,
            String imageQuality, String suffix) throws IOException {
        File file;

        file = this._getImageFile(rows + "-" + columns + "-" + region + "-"
                + windowWidth + "-" + windowCenter + "-"
                + maskNull(imageQuality, imageQuality), studyUID, seriesUID,
                instanceUID, suffix, null);

        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        BufferedOutputStream out = new BufferedOutputStream(
                new FileOutputStream(file));
        byte[] buf = new byte[BUF_LEN];
        try {
            int len = stream.read(buf);
            while (len > 0) {
                out.write(buf, 0, len);
                len = stream.read(buf);
            }
        } finally {
            stream.close();
            out.flush();
            out.close();
        }
        try {
            journal.record(file);
        } catch (IOException e) {
            file.delete();
            throw e;
        }
        return file;
    }

    /**
     * Clears this cache.
     * <p>
     * Remove all images in this cache.
     */
    public void clearCache() {
        journal.clearCache();
    }

    public boolean isEmpty() {
        return journal.isEmpty();
    }

    /**
     * Removes old entries of this chache to free disk space.
     * <p>
     * This method can be called to run on the same thread ( e.g. if started via
     * JMX console) or in a seperate thread (if clean is handled automatically
     * by WADOCache).
     * <DL>
     * <DD>1) check if clean is necessary:
     * <code>showFreeSpace &lt; getMinFreeSpace </code></DD>
     * <DD>2) delete the oldest files until
     * <code>showFreeSpace &gt; getPreferredFreeSpace</code></DD>
     * </DL>
     * 
     * @param background
     *            If true, clean runs in a seperate thread.
     * @throws IOException
     */
    public void freeDiskSpace(boolean background) throws IOException {
        long currFree = showFreeSpace();
        long minFree = getMinFreeSpace();
        if (log.isDebugEnabled())
            log.debug("WADOCache.freeDiskSpace: free:" + currFree
                    + " minFreeSpace:" + minFree);
        final long sizeToDel = minFree - currFree;
        if (sizeToDel > 0L) {
            if (background) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            journal.free(sizeToDel);
                        } catch (IOException e) {
                            log.error("Failed to free disk space: ", e);
                        }
                    }
                });
                t.start();
            } else {
                journal.free(sizeToDel);
            }
        } else {
            if (log.isDebugEnabled())
                log.debug("WADOCache.freeDiskSpace: nothing todo");
        }
    }

    /**
     * Remove cache entries for given study!
     * <p>
     * May delete cache entries for other studies because hashCode directory
     * name is ambiguous!
     */
    public void purgeStudy(String studyIUID) {
        log.info("Delete WADO CACHE Entries for Study:" + studyIUID);
        File f = getStudyDir(studyIUID);
        if (f.exists()) {
            CacheJournal.deleteFileOrDirectory(f);
        }
    }

    public String getDeleterThresholds() {
        return deleterThresholds.toString();
    }

    public void setDeleterThresholds(String s) {
        this.deleterThresholds = new DeleterThresholds(s, false);// does not
        // support
        // time
        // based
        // tresholds
    }

    public long getMinFreeSpace() {
        return deleterThresholds.getDeleterThreshold(Calendar.getInstance())
                .getFreeSize(0l);
    }

    /**
     * Returns current free disk space in bytes.
     * 
     * @return disk space available on the drive where this cache is stored.
     * @throws IOException
     */
    public long showFreeSpace() throws IOException {
        File dir = journal.getDataRootDir();
        long free = FileSystemUtils.freeSpace(dir.getPath());
        log.info("getFreeDiskSpace from :" + dir + " free:" + free);
        return free;
    }

    /**
     * @return Returns the clientRedirect.
     */
    public boolean isClientRedirect() {
        return clientRedirect;
    }

    /**
     * @param clientRedirect
     *            The clientRedirect to set.
     */
    public void setClientRedirect(boolean clientRedirect) {
        this.clientRedirect = clientRedirect;
    }

    /**
     * @return Returns the redirectCaching.
     */
    public boolean isRedirectCaching() {
        return redirectCaching;
    }

    /**
     * @param redirectCaching
     *            The redirectCaching to set.
     */
    public void setRedirectCaching(boolean redirectCaching) {
        this.redirectCaching = redirectCaching;
    }

    /**
     * Returns the File object references the file where the image is placed
     * within this cache.
     * <p>
     * The subdir argument is used to seperate default images and special sized
     * images.
     * <p>
     * The directory names are hex-string values of hashCode from studyUID,
     * seriesUID and instaneUID. (so they are not unique!)
     * <DL>
     * <DT>The File object was build like:</DT>
     * <DD>
     * &lt;root&gt;/6lt;studyUIDasHex&gt;/&lt;seriesUIDasHex&gt;/&lt;
     * instanceUIDasHex
     * &gt;/[&lt;subdir&gt;/&lt;]/&lt;instanceUID&gt;.&lt;ext&gt;</DD>
     * </DL>
     * 
     * @param subdir
     *            The subdirectory
     * @param studyUID
     *            Unique identifier of the study.
     * @param seriesUID
     *            Unique identifier of the series.
     * @param instanceUID
     *            Unique identifier of the instance.
     * @param contentType
     *            TODO
     * 
     * @return
     */
    private File _getImageFile(String subdir, String studyUID,
            String seriesUID, String instanceUID, String suffix,
            String contentType) {
        if (contentType == null)
            contentType = "image/jpg";// use jpg instead of jpeg here because
        // for extension jpeg is set to jpg.
        File file = getStudyDir(studyUID);
        file = new File(file, Integer.toHexString(seriesUID.hashCode()));
        file = new File(file, Integer.toHexString(instanceUID.hashCode()));
        if (subdir != null)
            file = new File(file, subdir);
        String ext = getFileExtension(contentType);
        if (ext.length() < 1)
            file = new File(file, contentType.replace('/', '_'));
        if (suffix != null)
            instanceUID += suffix;
        file = new File(file, instanceUID + ext);
        return file;
    }

    private File getStudyDir(String studyUID) {
        File dir = journal.getDataRootDir();
        int hashCode = studyUID.hashCode();
        for (int prime : numberOfStudyBags) {
            dir = new File(dir, Integer.toString(hashCode % prime));
        }
        return new File(dir, Integer.toHexString(hashCode));
    }

    /**
     * @param contentType
     * @return
     */
    private String getFileExtension(String contentType) {
        int pos = contentType.indexOf("/");
        String ext = "";
        if (pos != -1) {
            ext = contentType.substring(pos + 1);
            if (ext.equalsIgnoreCase("jpeg"))
                ext = "jpg";
            else if (ext.equalsIgnoreCase("png"))
                ext = "png";
            else if (ext.equalsIgnoreCase("png16"))
                ext = "png16";
            else if (ext.equalsIgnoreCase("svg+xml"))
                ext = "svg";
            // do some other mapping here;
            ext = "." + ext;
        }
        return ext.toLowerCase();
    }

    /**
     * Writes an image to the given file.
     * 
     * @param image
     *            The image.
     * @param file
     *            The file within this cache to store the image.
     * 
     * @throws IOException
     */
    private void _writeImageFile(BufferedImage bi, File file, String contentType,
            String imageQuality) throws IOException {
        if (!file.getParentFile().exists()) {
            if (!file.getParentFile().mkdirs()) {
                throw new IOException("Can not create directory:"
                        + file.getParentFile());
            }
        }
        try {
            if (WADOSupport.CONTENT_TYPE_PNG.equals(contentType) || WADOSupport.CONTENT_TYPE_PNG16.equals(contentType)) {
                log.debug("Create PNG for WADO request. file: " + file);
                writePNG(bi, file);
            } else {
                log.debug("Create JPEG (" + imageQuality
                        + " quality) for WADO request. file: " + file);
                    createJPEG(bi, file, Float.parseFloat(imageQuality) / 100);
            }
        } catch (Throwable x) {
            log.error("Can not create Image file for WADO request. file:" + file);
            if (file.exists()) {
                file.delete();
                log.error("Cache File removed:" + file);
            }
            if (x instanceof IOException) {
                throw (IOException) x;
            }
            IOException ioe = new IOException("Failed to write image file ("
                    + file + ")! Reason:" + x.getMessage());
            ioe.initCause(x);
            throw ioe;
        }
    }

    protected void createJPEG(BufferedImage bi, File file, float quality)
            throws IOException {
        writeJPEGwithIIO(bi, new FileOutputStream(file), quality);
    }
    
    public void writeJPEG(BufferedImage bi, OutputStream out, float quality) throws IOException {
        writeJPEGwithIIO(bi,out, quality);
    }
    
    protected void writeJPEGwithIIO(BufferedImage bi, OutputStream out, float quality) throws IOException {
        ImageOutputStream ios = ImageIO.createImageOutputStream(out);
        ImageWriter writer = getImageWriterWriter("JPEG", imageWriterClass);
        try {
            writer.setOutput(ios);
            ImageWriteParam iwparam = writer.getDefaultWriteParam();
            iwparam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            String[] compressionTypes = iwparam.getCompressionTypes();
            if (compressionTypes != null && compressionTypes.length > 0) {
                for (int i = 0; i < compressionTypes.length; i++) {
                    if (compressionTypes[i].compareToIgnoreCase("jpeg") == 0) {
                        iwparam.setCompressionType(compressionTypes[i]);
                        break;
                    }
                }
            }
            iwparam.setCompressionQuality(quality);
            writer.write(null, new IIOImage(bi, null, null), iwparam);
        } finally {
            ios.close();
            out.close();
            writer.dispose();
        }
    }
    

    public void writePNG(BufferedImage bi, Object fileOrStream) throws IOException {
        ImageOutputStream iout = ImageIO.createImageOutputStream(fileOrStream);
        ImageWriter writer = getImageWriterWriter("PNG", pngImageWriterClass);
        try {
            writer.setOutput(iout);
            ImageWriteParam iwparam = writer.getDefaultWriteParam();
            writer.write(null, new IIOImage(bi, null, null), iwparam);
        } finally {
            iout.close();
            writer.dispose();
        }
    }

    private ImageWriter getImageWriterWriter(String formatName, String imageWriterClass) {
        for (Iterator writers = ImageIO.getImageWritersByFormatName(formatName); writers
                .hasNext();) {
            ImageWriter writer = (ImageWriter) writers.next();
            if (imageWriterClass == null || writer.getClass().getName().equals(imageWriterClass)) {
                log.debug("##### Use image writer for "+formatName+":"+writer);
                return writer;
            }
        }
        if (imageWriterClass != null)
            throw new ConfigurationException("No such ImageWriter - " + imageWriterClass);
        throw new ConfigurationException("No ImageWriter found for " + formatName);
    }

    public String showImageWriter(String formatName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Available image writer for ").append(formatName).append(":\n");
        for (Iterator writers = ImageIO.getImageWritersByFormatName(formatName); writers.hasNext();) {
            ImageWriter writer = (ImageWriter) writers.next();
            sb.append(writer.getClass().getName()).append("\n");
        }
        return sb.toString();
    }
}
