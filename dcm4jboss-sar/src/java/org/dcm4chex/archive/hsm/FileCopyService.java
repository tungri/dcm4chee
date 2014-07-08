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
 * Portions created by the Initial Developer are Copyright (C) 2005
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

package org.dcm4chex.archive.hsm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.commons.compress.tar.TarEntry;
import org.apache.commons.compress.tar.TarOutputStream;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.util.BufferedOutputStream;
import org.dcm4che.util.MD5Utils;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.BaseJmsOrder;
import org.dcm4chex.archive.config.ForwardingRules;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Home;
import org.dcm4chex.archive.ejb.interfaces.MD5;
import org.dcm4chex.archive.ejb.interfaces.Storage;
import org.dcm4chex.archive.ejb.jdbc.FileInfo;
import org.dcm4chex.archive.ejb.jdbc.QueryCmd;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileUtils;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 18249 $ $Date: 2014-02-21 16:18:22 +0000 (Fri, 21 Feb 2014) $
 * @since Nov 9, 2005
 */
public class FileCopyService extends AbstractFileCopyService {

    private static final String NONE = "NONE";    
    public static final String NEW_LINE = System.getProperty("line.separator", "\n");
    private static final int MD5SUM_ENTRY_LEN = 52;
    
    private ObjectName hsmModuleServicename = null;
    
    public final String getHSMModulServicename() {
        return hsmModuleServicename == null ? NONE : hsmModuleServicename.toString();
    }

    public final void setHSMModulServicename(String name) throws MalformedObjectNameException {
        this.hsmModuleServicename = NONE.equals(name) ? null : ObjectName.getInstance(name);
    }
    
    public String getAvailableHSMModules() {
        try {
            Set names = server.queryNames(new ObjectName("*:service=FileCopyHSMModule,*") , null);
            StringBuilder sb = new StringBuilder(names.size()*40);
            for (Iterator it = names.iterator() ; it.hasNext() ;) {
                sb.append(it.next()).append(NEW_LINE);
            }
            return sb.toString();
        } catch (Exception x) {
            log.error("Failed to get list of available HSM modules!", x);
            return "ERROR";
        }
    }
    
    public boolean isReady() {
        if (getState() == STARTED) {
            try {
                if (hsmModuleServicename == null || 
                    (server.isRegistered(hsmModuleServicename) && 
                     (Integer)server.getAttribute(hsmModuleServicename, "State") == STARTED)) {
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to get state of hsmModuleServicename:"+hsmModuleServicename);
                return false;
            }
        }
        return false;
    }
    
    public boolean copyFilesOfStudy(String studyIUID) throws Exception {
        if (!checkDestinationFsPath(destination)) {
            log.warn("Destination is not configured or doesn't exist! skip this copyFilesOfStudy call!");
            return false;
        }
        log.info("Start copy files of study "+studyIUID);
        Dataset queryDs = DcmObjectFactory.getInstance().newDataset();
        queryDs.putCS(Tags.QueryRetrieveLevel, "SERIES");
        queryDs.putUI(Tags.StudyInstanceUID, studyIUID);
        queryDs.putUI(Tags.SeriesInstanceUID);
        QueryCmd cmd = QueryCmd.create(queryDs, null, true, false, false, false, null);
        try {
            cmd.setFetchSize(getFetchSize()).execute();
            while ( cmd.next() ) {
                doCopyFilesOfSeries(cmd.getDataset().getString(Tags.SeriesInstanceUID));
            }
        } finally {
            cmd.close();
        }
        return true;
    }

    public boolean copyFilesOfSeries(String seriesIUID) throws Exception {
        if (!checkDestinationFsPath(destination)) {
            log.warn("Destination is not configured or doesn't exist! skip this copyFilesOfSeries call!");
            return false;
        }
        return doCopyFilesOfSeries(seriesIUID);
    }
    
    private boolean doCopyFilesOfSeries(String seriesIUID) throws Exception {
        Dataset queryDs = DcmObjectFactory.getInstance().newDataset();
        queryDs.putCS(Tags.QueryRetrieveLevel, "IMAGE");
        queryDs.putUI(Tags.StudyInstanceUID);
        queryDs.putUI(Tags.SeriesInstanceUID, seriesIUID);
        queryDs.putUI(Tags.SOPInstanceUID);
        QueryCmd cmd = QueryCmd.create(queryDs, null, true, false, false, false, null);
        try {
            cmd.execute();
            Dataset ds = null;
            Dataset ian = DcmObjectFactory.getInstance().newDataset();
            Dataset refSeries = ian.putSQ(Tags.RefSeriesSeq).addNewItem();
            DcmElement refSOPs = refSeries.putSQ(Tags.RefSOPSeq);
            while ( cmd.next() ) {
                ds = cmd.getDataset();
                refSeries.putUI(Tags.SeriesInstanceUID, ds.getString(Tags.SeriesInstanceUID));
                refSOPs.addNewItem().putUI(Tags.RefSOPInstanceUID, ds.getString(Tags.SOPInstanceUID));
            }
            if ( ds != null ) {
                ian.putUI(Tags.StudyInstanceUID, ds.getString(Tags.StudyInstanceUID));
                refSeries.putUI(Tags.SeriesInstanceUID, ds.getString(Tags.SeriesInstanceUID));
                schedule(createOrder(ian), 0l);
                log.info("Copy files of series "+seriesIUID+" scheduled!");
                return true;
            } else {
                log.info("No instances found for file copy! seriesIUID:"+seriesIUID);
                log.debug(queryDs);
                return false;
            }
        } finally {
            cmd.close();
        }

    }

    protected BaseJmsOrder createOrder(Dataset ian) {
        FileCopyOrder fileCopyOrder = new FileCopyOrder(ian, ForwardingRules.toAET(destination),
                getRetrieveAETs(), getFetchSize());
        fileCopyOrder.processOrderProperties();
        
        return fileCopyOrder;
    }

    protected void process(BaseJmsOrder order) throws Exception {
        FileCopyOrder fileCopyOrder = (FileCopyOrder)order;
        String destPath = fileCopyOrder.getDestinationFileSystemPath();
        if (!checkDestinationFsPath(destPath)) {
            log.error("Destination file system doesn't exist! Skip this FileCopy order!");
            return;
        }
        List<FileInfo> fileInfos = fileCopyOrder.getFileInfos();
        int removed = removeOfflineOrTarSourceFiles(fileInfos);
        if ( removed > 0 ) 
            log.info(removed+" Files (Offline or on tar FS) removed from FileCopy Order!"+
                    "\nRemaining files to copy:"+fileInfos.size());
        if ( fileInfos.isEmpty() ) {
            log.info("Skip FileCopy Order! No files to copy!");
            return;
        }
        if (destPath.startsWith("tar:")) {
            copyTar(fileInfos, destPath);
        } else {
            copyFiles(fileInfos, destPath);
        }
    }

    private void copyFiles(List<FileInfo> fileInfos, String destPath)
            throws Exception {
        byte[] buffer = new byte[bufferSize];
        Storage storage = getStorageHome().create();
        Exception ex = null;
        MessageDigest digest = null;
        if (verifyCopy)
            digest = MessageDigest.getInstance("MD5");
        for (Iterator<FileInfo> iter = fileInfos.iterator(); iter.hasNext();) {
            FileInfo finfo = iter.next();
            File src = FileUtils.toFile(finfo.basedir + '/' + finfo.fileID);
            File dst = FileUtils.toFile(destPath + '/' + finfo.fileID);
            try {
                copy(src, dst);
                byte[] md5sum0 = finfo.md5 != null ? MD5Utils
                        .toBytes(finfo.md5) : null;
                if (md5sum0 != null && digest != null) {
                    byte[] md5sum = MD5Utils.md5sum(dst, digest, buffer);
                    if (!Arrays.equals(md5sum0, md5sum)) {
                        String prompt = "md5 sum of copy " + dst
                                + " differs from md5 sum in DB for file " + src;
                        log.warn(prompt);
                        throw new IOException(prompt);
                    }
                }
                byte[] origMd5sum0 = finfo.origMd5 != null ? MD5Utils
                        .toBytes(finfo.origMd5) : null;
                storage.storeFile(finfo.sopIUID, finfo.tsUID, destPath,
                        finfo.fileID, (int) finfo.size, md5sum0,
                        origMd5sum0, fileStatus);
                iter.remove();
            } catch (Exception e) {
                dst.delete();
                ex = e;
            }
        }
        if (ex != null)
            throw ex;
    }

    private void copy(File src, File dst) throws IOException {
        try {
            File dir = dst.getParentFile();
            if (dir.mkdirs()) {
                log.info("M-WRITE dir:" + dir);
            }
            log.info("M-WRITE file:" + dst);
            FileUtils.copyFile(src, dst);
        } catch (IOException e) {
            log.error("Copy file "+src+" failed", e);
            throw e;
        }
    }

    private void copyTar(List<FileInfo> fileInfos, String destPath) throws Exception {
        FileInfo file1Info = (FileInfo) fileInfos.get(0);
        String tarPath = mkTarPath(file1Info.fileID);
        String[] tarEntryNames = new String[fileInfos.size()];
        for (int i = 0; i < tarEntryNames.length; i++) {
            tarEntryNames[i] = mkTarEntryName(fileInfos.get(i));
        }
        if (hsmModuleServicename == null) {
            File tarFile = FileUtils.toFile(destPath.substring(4), tarPath);
            mkTar(fileInfos, tarFile, tarEntryNames);
        } else {
            File tarFile = prepareHSMFile(destPath, tarPath);
            try {
                mkTar(fileInfos, tarFile, tarEntryNames);
                tarPath = storeHSMFile(tarFile, destPath, tarPath);
            } catch (Exception x) {
                log.error("Make Tar file failed!",x);
                tarFile.delete();
                failedHSMFile(tarFile,destPath, tarPath);
                throw x;
            }
        }
        Storage storage = getStorageHome().create();
        for (int i = 0; i < tarEntryNames.length; i++) {
            String fileId = tarPath + '!' + tarEntryNames[i];
            FileInfo finfo = fileInfos.get(i);
            byte[] origMd5sum = finfo.origMd5 != null ? MD5Utils
                    .toBytes(finfo.origMd5) : null;
            storage.storeFile(finfo.sopIUID, finfo.tsUID, destPath, fileId,
                    (int) finfo.size, MD5.toBytes(finfo.md5), origMd5sum,
                    fileStatus);
        }
    }

    private File prepareHSMFile(String fsID, String filePath)
            throws InstanceNotFoundException, MBeanException,
            ReflectionException {
        return (File) server.invoke(hsmModuleServicename, "prepareHSMFile", new Object[]{fsID, filePath}, 
                new String[]{String.class.getName(),String.class.getName()});
    }

    private String storeHSMFile(File file, String fsID, String filePath) throws InstanceNotFoundException, MBeanException,
        ReflectionException {
        return (String) server.invoke(hsmModuleServicename, "storeHSMFile", 
                new Object[]{file, fsID, filePath}, 
                new String[]{File.class.getName(),String.class.getName(),String.class.getName()});
    }

    private void failedHSMFile(File file, String fsID, String filePath) throws InstanceNotFoundException, MBeanException,
        ReflectionException {
            server.invoke(hsmModuleServicename, "failedHSMFile", 
                    new Object[]{file, fsID, filePath}, 
                    new String[]{File.class.getName(),String.class.getName(),String.class.getName()});
}
    
    private void mkTar(List<FileInfo> fileInfos, File tarFile,
            String[] tarEntryNames) throws Exception {
        try {
            if (tarFile.getParentFile().mkdirs()) {
                log.info("M-WRITE " + tarFile.getParent());
            }    
            log.info("M-WRITE " + tarFile);
            TarOutputStream tar = new TarOutputStream(
                    new FileOutputStream(tarFile));
            try {
                writeMD5SUM(tar, fileInfos, tarEntryNames);
                for (int i = 0; i < tarEntryNames.length; i++) {
                    writeFile(tar, fileInfos.get(i), tarEntryNames[i]);
                }
            } finally {
                tar.close();
            }
            if (verifyCopy) {
                VerifyTar.verify(tarFile, new byte[bufferSize]);
            }
        } catch (Exception e) {
            log.error("M-DELETE tar file due to an error! "+tarFile);
            tarFile.delete();
            throw e;
        }
    }

    private int removeOfflineOrTarSourceFiles(List<FileInfo> fileInfos) {
        int removed = 0;
        FileInfo fi;
        for (Iterator<FileInfo> iter = fileInfos.iterator(); iter.hasNext();) {
            fi = iter.next();
            if ( fi.availability != Availability.ONLINE || fi.basedir.startsWith("tar:")) {
                removed++;
                iter.remove();
            }
        }
        return removed;
    }

    private void writeMD5SUM(TarOutputStream tar, List<FileInfo> fileInfos,
            String[] tarEntryNames)
            throws IOException {
        byte[] md5sum = new byte[fileInfos.size() * MD5SUM_ENTRY_LEN];
        final TarEntry tarEntry = new TarEntry("MD5SUM");
        tarEntry.setSize(md5sum.length);
        tar.putNextEntry(tarEntry);
        int i = 0;
        for (int j = 0; j < tarEntryNames.length; j++) {
            MD5Utils.toHexChars(MD5.toBytes(fileInfos.get(j).md5), md5sum, i);
            md5sum[i+32] = ' ';
            md5sum[i+33] = ' ';
            System.arraycopy(
                    tarEntryNames[j].getBytes("US-ASCII"), 0, 
                    md5sum, i+34, 17);
            md5sum[i+51] = '\n';
            i += MD5SUM_ENTRY_LEN;
        }
        tar.write(md5sum);
        tar.closeEntry();
    }

    private void writeFile(TarOutputStream tar, FileInfo fileInfo,
            String tarEntryName) 
    throws IOException, FileNotFoundException {
        File file = FileUtils.toFile(fileInfo.basedir, fileInfo.fileID);
        if (file.length() != fileInfo.size) {
            log.error("Filesize doesn't match for file entry:"+fileInfo+"!("+
                    file.length()+" vs. "+fileInfo.size+") skipped!");
            throw new IOException("Filesize doesn't match! file:"+file);
        }
        TarEntry entry = new TarEntry(tarEntryName);
        entry.setSize(fileInfo.size);
        tar.putNextEntry(entry);
        FileInputStream fis = new FileInputStream(file);
        try {
            tar.copyEntryContents(fis);
        } finally {
            fis.close();
        }
        tar.closeEntry();
    }
    
    private String mkTarEntryName(FileInfo fileInfo) {
        StringBuilder sb = new StringBuilder(17);
        sb.append(FileUtils.toHex(fileInfo.seriesIUID.hashCode()));
        sb.append('/');
        sb.append(FileUtils.toHex((int)(fileInfo.pk)));
        return sb.toString();
    }

    private String mkTarPath(String filePath) {
        StringBuffer sb = new StringBuffer(filePath);
        sb.setLength(filePath.lastIndexOf('/'));
        sb.append('-').append(System.currentTimeMillis()%3600000).append(".tar");
        return sb.toString();
    }
    
    private boolean checkDestinationFsPath(String destFsPath) throws Exception {
        if (destFsPath == null)
            return false;
        FileSystemMgt2 mgr = ((FileSystemMgt2Home) EJBHomeFactory.getFactory().lookup(
                FileSystemMgt2Home.class, FileSystemMgt2Home.JNDI_NAME)).create();
        try {
            return mgr.getFileSystem(destFsPath) != null;
        } catch (Exception x) {
            return false;
        }
    }

}
