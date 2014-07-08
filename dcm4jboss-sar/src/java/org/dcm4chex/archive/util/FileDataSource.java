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

package org.dcm4chex.archive.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.imageio.stream.FileImageInputStream;
import javax.management.JMException;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.dcm4che.net.DataSource;
import org.dcm4che.util.UIDGenerator;
import org.dcm4chex.archive.codec.DecompressCmd;
import org.jboss.logging.Logger;

/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 18004 $
 * @since 18.09.2003
 */
public class FileDataSource implements DataSource {

    private static final Logger log = Logger.getLogger(FileDataSource.class);

    private static final int SOI = 0xffd8;
    private static final int SOF55 = 0xfff7;
    private static final int LSE = 0xfff8;
    private static final int SOS = 0xffda;
    private static final byte[] LSE_13 = {
        (byte) 0xff, (byte) 0xf8, (byte) 0x00, (byte) 0x0D,
        (byte) 0x01, 
        (byte) 0x1f, (byte) 0xff,
        (byte) 0x00, (byte) 0x22,  // T1 = 34
        (byte) 0x00, (byte) 0x83,  // T2 = 131
        (byte) 0x02, (byte) 0x24,  // T3 = 548
        (byte) 0x00, (byte) 0x40,
        (byte) 0xff, (byte) 0xda
    };
    private static final byte[] LSE_14 = {
        (byte) 0xff, (byte) 0xf8, (byte) 0x00, (byte) 0x0D,
        (byte) 0x01, 
        (byte) 0x3f, (byte) 0xff,
        (byte) 0x00, (byte) 0x42, // T1 = 66
        (byte) 0x01, (byte) 0x03, // T2 = 259
        (byte) 0x04, (byte) 0x44, // T3 = 1092
        (byte) 0x00, (byte) 0x40,
        (byte) 0xff, (byte) 0xda
    };
    private static final byte[] LSE_15 = {
        (byte) 0xff, (byte) 0xf8, (byte) 0x00, (byte) 0x0D,
        (byte) 0x01, 
        (byte) 0x7f, (byte) 0xff,
        (byte) 0x00, (byte) 0x82, // T1 = 130
        (byte) 0x02, (byte) 0x03, // T2 = 515
        (byte) 0x08, (byte) 0x84, // T3 = 2180
        (byte) 0x00, (byte) 0x40,
        (byte) 0xff, (byte) 0xda
    };
    private static final byte[] LSE_16 = {
        (byte) 0xff, (byte) 0xf8, (byte) 0x00, (byte) 0x0D,
        (byte) 0x01, 
        (byte) 0xff, (byte) 0xff,
        (byte) 0x01, (byte) 0x02, // T1 = 258
        (byte) 0x04, (byte) 0x03, // T2 = 1027
        (byte) 0x11, (byte) 0x04, // T3 = 4356
        (byte) 0x00, (byte) 0x40,
        (byte) 0xff, (byte) 0xda
    };

    private static Dataset defaultContributingEquipment;

    static {
        FileDataSource.defaultContributingEquipment =
                DcmObjectFactory.getInstance().newDataset();
        Dataset purpose = defaultContributingEquipment
                .putSQ(Tags.PurposeOfReferenceCodeSeq).addNewItem();
        purpose.putLO(Tags.CodeValue, "109105");
        purpose.putSH(Tags.CodingSchemeDesignator, "DCM");
        purpose.putLO(Tags.CodeMeaning, "Frame Extracting Equipment");
        defaultContributingEquipment.putLO(Tags.Manufacturer, "dcm4che.org");
    }
    private final File file;
    private final Dataset mergeAttrs;
    private final byte[] buffer;

    /** if true use Dataset.writeFile instead of writeDataset */
    private boolean writeFile = false;
    private boolean withoutPixeldata = false;
    private boolean excludePrivate = false;
    private int[] simpleFrameList;
    private int[] calculatedFrameList;
    private Dataset contributingEquipment =
            FileDataSource.defaultContributingEquipment;

    private boolean patchJpegLS = true;
    private String patchJpegLSImplCUID = "1.2.40.0.13.1.1";
    private String patchJpegLSNewImplCUID = "1.2.40.0.13.1.1.1";

	private DatasetUpdater datasetUpdater;

    public FileDataSource(File file, Dataset mergeAttrs, byte[] buffer) {
        this.file = file;
        this.mergeAttrs = mergeAttrs;
        this.buffer = buffer;
    }

    public FileDataSource(File file, Dataset mergeAttrs, byte[] buffer, DatasetUpdater datasetUpdater) {
    	this(file, mergeAttrs, buffer);
    	this.datasetUpdater = datasetUpdater;
    	
	}

	public static final Dataset getDefaultContributingEquipment() {
        return defaultContributingEquipment;
    }

    public static final void setDefaultContributingEquipment(
            Dataset contributingEquipment) {
        FileDataSource.defaultContributingEquipment = contributingEquipment;
    }

    /**
     * @return Returns the writeFile.
     */
    public final boolean isWriteFile() {
        return writeFile;
    }

    /**
     * Set the write method (file or net).
     * <p>
     * If true, this datasource use writeFile instead of writeDataset. Therefore
     * the FileMetaInfo will be only written if writeFile is set to true
     * explicitly!
     * 
     * @param writeFile
     *                The writeFile to set.
     */
    public final void setWriteFile(boolean writeFile) {
        this.writeFile = writeFile;
    }

    public final boolean isWithoutPixeldata() {
        return withoutPixeldata;
    }

    public final void setWithoutPixeldata(boolean withoutPixelData) {
        this.withoutPixeldata = withoutPixelData;
    }

    public final boolean isExcludePrivate() {
        return excludePrivate;
    }

    public final void setExcludePrivate(boolean excludePrivate) {
        this.excludePrivate = excludePrivate;
    }

    public final void setSimpleFrameList(int[] simpleFrameList) {
        if (simpleFrameList != null) {
            if (calculatedFrameList != null) {
                throw new IllegalStateException();
            }
            if (simpleFrameList.length == 0) {
                throw new IllegalArgumentException();
            }
            for (int i = 0; i < simpleFrameList.length; i++) {
                if (simpleFrameList[i] <= 0) {
                    throw new IllegalArgumentException();
                }
                if (i != 0 && simpleFrameList[i]
                           <= simpleFrameList[i-1]) {
                    throw new IllegalArgumentException();
                }
            }
        }
        this.simpleFrameList = simpleFrameList;
    }

    public final void setCalculatedFrameList(int[] calculatedFrameList) {
        if (calculatedFrameList != null) {
            if (simpleFrameList != null) {
                throw new IllegalStateException();
            }
            if (calculatedFrameList.length == 0) {
                throw new IllegalArgumentException();
            }
            if (calculatedFrameList.length % 3 != 0) {
                throw new IllegalArgumentException();
            }
            for (int i = 0; i < calculatedFrameList.length; i++) {
                if (calculatedFrameList[i] <= 0) {
                    throw new IllegalArgumentException();
                }
                switch (i % 3) {
                case 0:
                    if (i != 0 && calculatedFrameList[i]
                               <= calculatedFrameList[i-2]) {
                        throw new IllegalArgumentException();
                    }
                    break;
                case 1:
                    if (i != 0 && calculatedFrameList[i]
                               < calculatedFrameList[i-1]) {
                        throw new IllegalArgumentException();
                    }
                    break;
                }
            }
        }
        this.calculatedFrameList = calculatedFrameList;
    }

    public final void setContributingEquipment(Dataset contributingEquipment) {
        this.contributingEquipment = contributingEquipment;
    }

    public final boolean isPatchJpegLS() {
        return patchJpegLS;
    }

    public final void setPatchJpegLS(boolean patchJpegLS) {
        this.patchJpegLS = patchJpegLS;
    }

    public final String getPatchJpegLSImplCUID() {
        return patchJpegLSImplCUID;
    }

    public final void setPatchJpegLSImplCUID(String patchJpegLSImplCUID) {
        this.patchJpegLSImplCUID = patchJpegLSImplCUID;
    }

    public final String getPatchJpegLSNewImplCUID() {
        return patchJpegLSNewImplCUID;
    }

    public final void setPatchJpegLSNewImplCUID(String patchJpegLSNewImplCUID) {
        this.patchJpegLSNewImplCUID = patchJpegLSNewImplCUID;
    }

    public final File getFile() {
        return file;
    }

    public final Dataset getMergeAttrs() {
        return mergeAttrs;
    }

    public void writeTo(OutputStream out, String tsUID) throws IOException {
        log.info("M-READ file:" + file);
        boolean withoutPixeldata1 = withoutPixeldata 
                || UIDs.NoPixelData.equals(tsUID)
                || UIDs.NoPixelDataDeflate.equals(tsUID);
        DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)));
        FileImageInputStream fiis = null;
        try {
            Dataset ds = DcmObjectFactory.getInstance().newDataset();
            DcmParser parser = DcmParserFactory.getInstance().newDcmParser(dis);
            parser.setDcmHandler(ds.getDcmHandler());
            parser.parseDcmFile(null, Tags.PixelData);
            boolean hasPixelData = parser.getReadTag() == Tags.PixelData;
            DcmDecodeParam dcmDecodeParam = parser.getDcmDecodeParam();
            if (!hasPixelData && !parser.hasSeenEOF()) {
                parser.unreadHeader();
                parser.parseDataset(dcmDecodeParam, -1);
            }
            ds.putAll(mergeAttrs);
            // hook to perform any other dataset updates after the file has been read from the file system
            if (datasetUpdater != null) {
            	ds = datasetUpdater.updateDataset(ds);
            }            
            FileMetaInfo fmi = ds.getFileMetaInfo();
            if (fmi == null) {
                fmi = DcmObjectFactory.getInstance()
                        .newFileMetaInfo(ds, UIDs.ImplicitVRLittleEndian);
                ds.setFileMetaInfo(fmi);
            }
            String tsOrig = fmi.getTransferSyntaxUID();
            if (tsUID == null)
                tsUID = tsOrig;
            boolean patchJpegLS = this.patchJpegLS
                    && !withoutPixeldata1
                    && tsUID.equals(UIDs.JPEGLSLossless)
                    && (patchJpegLSImplCUID == null
                            || patchJpegLSImplCUID.equals(
                            fmi.getImplementationClassUID()))
                    && ds.getInt(Tags.BitsAllocated, 0) == 16;
            if (writeFile) {
                if (!(withoutPixeldata1 
                        || tsUID.equals(tsOrig)
                        || tsUID.equals(UIDs.ImplicitVRLittleEndian))) {
                    tsUID = UIDs.ExplicitVRLittleEndian;
                    patchJpegLS = false;
                }
                fmi.putUI(Tags.TransferSyntaxUID, tsUID);

                if (patchJpegLS && patchJpegLSNewImplCUID != null)
                    fmi.putUI(Tags.ImplementationClassUID,
                            patchJpegLSNewImplCUID);
            }
            log.debug("using transfersyntx:" + tsUID);
            DcmEncodeParam enc = DcmEncodeParam.valueOf(tsUID);
            if (!hasPixelData) {
                log.debug("Dataset:\n");
                log.debug(ds);
                write(ds, out, enc);
                return;
            }
            int pixelDataLen = parser.getReadLength();
            boolean encapsulated = pixelDataLen == -1;
            int framesInFile = ds.getInt(Tags.NumberOfFrames, 1);
            if (simpleFrameList != null) {
                if (simpleFrameList[simpleFrameList.length - 1] > framesInFile) {
                    throw new RequestedFrameNumbersOutOfRangeException();
                }
            } else if (calculatedFrameList != null) {
                if (calculatedFrameList[0] > framesInFile) {
                    throw new RequestedFrameNumbersOutOfRangeException();
                }
                simpleFrameList = calculateFrameList(framesInFile);
            }
            if (framesInFile == 1) {
                simpleFrameList = null;
            }
            if (simpleFrameList != null) {
                addFrameExtractionSeq(ds);
                addContributingEquipmentSeq(ds);
                adjustNumberOfFrames(ds);
                ds.putUI(Tags.SOPInstanceUID,
                        UIDGenerator.getInstance().createUID());
            }
            if (withoutPixeldata1) {
                // skip Pixel Data
                if (!encapsulated) {
                    dis.skipBytes(pixelDataLen);
                } else {
                    do {
                        parser.parseHeader();
                        dis.skipBytes(parser.getReadLength());
                    } while (parser.getReadTag() == Tags.Item);
                }
                // parse attributes after Pixel Data
                parser.parseDataset(dcmDecodeParam, -1);
                log.debug("Dataset:\n");
                log.debug(ds);
                write(ds, out, enc);
                return;
            }
            if (encapsulated && !enc.encapsulated) {
                DecompressCmd.adjustPhotometricInterpretation(ds, tsOrig);
            }
            log.debug("Dataset:\n");
            log.debug(ds);
            write(ds, out, enc);
            if (!encapsulated) {
                // copy native Pixel Data
                if (simpleFrameList == null) {
                    ds.writeHeader(out, enc, Tags.PixelData, VRs.OW,
                            pixelDataLen);
                    copyBytes(dis, out, pixelDataLen, buffer);
                } else {
                    int frameLength = pixelDataLen / framesInFile;
                    int newPixelDataLength =
                        frameLength * simpleFrameList.length;
                    ds.writeHeader(out, enc, Tags.PixelData, VRs.OW,
                            (newPixelDataLength+1)&~1);
                    int frameIndex = 0;
                    for (int i = 0; i < simpleFrameList.length; i++) {
                        while (++frameIndex < simpleFrameList[i]) {
                            dis.skipBytes(frameLength);
                        }
                        copyBytes(dis, out, frameLength, buffer);
                    }
                    if ((newPixelDataLength & 1) != 0)
                        out.write(0);
                    // ignore attributes after Pixel Data
                    return;
                }
            } else if (enc.encapsulated) {
                // copy encapsulated Pixel Data
                ds.writeHeader(out, enc, Tags.PixelData, VRs.OB, -1);
                parser.parseHeader();
                int itemlen = parser.getReadLength();
                if (simpleFrameList == null && !patchJpegLS) {
                    // copy Basic Offset Table
                    ds.writeHeader(out, enc, parser.getReadTag(),
                            VRs.NONE, itemlen);
                    copyBytes(dis, out, itemlen, buffer);
                } else {
                    // write empty Basic Offset Table
                    ds.writeHeader(out, enc, Tags.Item, VRs.NONE, 0);
                    // skip Basic Offset Table
                    dis.skipBytes(itemlen);
                }
                if (simpleFrameList == null) {
                    parser.parseHeader();
                    while (!parser.hasSeenEOF() && parser.getReadTag() == Tags.Item) {
                        itemlen = parser.getReadLength();
                        copyItem(patchJpegLS, dis, ds, out, enc, itemlen);
                        parser.parseHeader();
                    };
                    ds.writeHeader(out, enc, Tags.SeqDelimitationItem,
                            VRs.NONE, 0);
                    dis.skipBytes(parser.getReadLength());
                } else {
                    // WARN frames spanning multiple data fragments not supported
                    // assume one item per frame
                    int frameIndex = 0;
                    for (int i = 0; i < simpleFrameList.length; i++) {
                        parser.parseHeader();
                        itemlen = parser.getReadLength();
                        while (++frameIndex < simpleFrameList[i]) {
                            dis.skipBytes(itemlen);
                            parser.parseHeader();
                            itemlen = parser.getReadLength();
                        }
                        copyItem(patchJpegLS, dis, ds, out, enc, itemlen);
                    }
                    ds.writeHeader(out, enc, Tags.SeqDelimitationItem,
                            VRs.NONE, 0);
                    // ignore attributes after Pixel Data
                    return;
                }
            } else {
                // decompress encapsulated Pixel Data
                dis.close();
                dis = null;
                fiis = new FileImageInputStream(file);
                fiis.seek(parser.getStreamPosition());
                parser = DcmParserFactory.getInstance().newDcmParser(fiis);
                parser.setDcmHandler(ds.getDcmHandler());
                DecompressCmd cmd = createDecompressCmd(ds, tsOrig, parser);
                cmd.setSimpleFrameList(simpleFrameList);
                int newPixelDataLen = cmd.getPixelDataLength();
                ds.writeHeader(out, enc, Tags.PixelData, VRs.OW,
                        (newPixelDataLen+1)&~1);
                try {
                    cmd.decompress(enc.byteOrder, out);
                } catch (IOException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException("Decompression failed:", e);
                }
                if ((newPixelDataLen&1) != 0)
                    out.write(0);
            }
            // parse attributes after Pixel Data
            parser.parseDataset(dcmDecodeParam, -1);
            ds.subSet(Tags.PixelData, -1).writeDataset(out, enc);
        } catch (JMException jme) {
            throw new RuntimeException("Unable to update dataset:", jme);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch (IOException ignore) {
            }
            try {
                if (fiis != null) {
                    fiis.close();
                }
            } catch (IOException ignore) {
            }
        }
    }
    

    private void copyItem(boolean patchJpegLS, DataInputStream dis,
            Dataset ds, OutputStream out, DcmEncodeParam enc, int itemlen)
            throws IOException {
        if (patchJpegLS) {
            byte[] jpegheader = buffer;
            dis.readFully(jpegheader, 0, 17);
            byte[] lse = selectLSE(jpegheader);
            if (lse == null) {
                ds.writeHeader(out, enc, Tags.Item, VRs.NONE, itemlen);
                out.write(jpegheader, 0, 17);
                copyBytes(dis, out, itemlen - 17, buffer);
            } else {
                ds.writeHeader(out, enc, Tags.Item, VRs.NONE, itemlen + 16);
                out.write(jpegheader, 0, 15);
                out.write(lse);
                copyBytes(dis, out, itemlen - 17, buffer);
                out.write(0);
            }
        } else {
            ds.writeHeader(out, enc, Tags.Item, VRs.NONE, itemlen);
            copyBytes(dis, out, itemlen, buffer);
        }
    }

    private static int toInt(byte[] b, int off) {
        return (b[off] & 0xff) << 8 | (b[off+1] & 0xff);
    }

    private static byte[] selectLSE(byte[] jpegheader) {
        if (toInt(jpegheader, 0) != SOI) {
            log.warn("SOI marker is missing - do not patch JPEG LS");
            return null;
        }
        if (toInt(jpegheader, 2) != SOF55) {
            log.warn("SOI marker is not followed by JPEG-LS SOF marker "
                    + "- do not patch JPEG LS");
            return null;
        }
        if (toInt(jpegheader, 4) != 11) {
            log.warn("unexpected length of JPEG-LS SOF marker segment "
                    + "- do not patch JPEG LS");
            return null;
        }
        int marker = toInt(jpegheader, 15);
        if (marker != SOS) {
            log.warn(marker == LSE
                ? "contains already LSE marker segment "
                    + "- do not patch JPEG LS"
                : "JPEG-LS SOF marker segment is not followed by SOS marker "
                    + "- do not patch JPEG LS");
            return null;
        }
        switch (jpegheader[6]) {
        case 13:
            log.info("Patch JPEG LS 13-bit with "
                    + "LSE segment(T1=34, T2=131, T3=548)");
            return LSE_13;
        case 14:
            log.info("Patch JPEG LS 14-bit with "
                    + "LSE segment(T1=66, T2=259, T3=1092)");
            return LSE_14;
        case 15:
            log.info("Patch JPEG LS 15-bit with "
                    + "LSE segment(T1=130, T2=515, T3=2180)");
            return LSE_15;
        case 16:
            log.info("Patch JPEG LS 16-bit with "
                    + "LSE segment(T1=258, T2=1027, T3=4356)");
            return LSE_16;
        }
        return null;
    }

    private void adjustNumberOfFrames(Dataset ds) {
        ds.putIS(Tags.NumberOfFrames, simpleFrameList.length);
        DcmElement src = ds.remove(Tags.PerFrameFunctionalGroupsSeq);
        if (src != null) {
            DcmElement dest = ds.putSQ(Tags.PerFrameFunctionalGroupsSeq);
            for (int i = 0; i < simpleFrameList.length; i++) {
                dest.addItem(src.getItem(simpleFrameList[i]-1));
            }
        }
    }

    private void addContributingEquipmentSeq(Dataset ds) {
        if (contributingEquipment != null) {
            getOrPutSQ(ds, Tags.ContributingEquipmentSeq)
                    .addItem(contributingEquipment);
        }
    }

    private void addFrameExtractionSeq(Dataset ds) {
        DcmElement seq = getOrPutSQ(ds, Tags.FrameExtractionSeq);
        Dataset item = seq.addNewItem();
        item.putUI(Tags.MultiFrameSourceSOPInstanceUID,
                ds.getString(Tags.SOPInstanceUID));
        if (calculatedFrameList != null) {
            item.putUL(Tags.CalculatedFrameList, calculatedFrameList);
        } else {
            item.putUL(Tags.SimpleFrameList, simpleFrameList);
        }
     }

    private DcmElement getOrPutSQ(Dataset ds, int tag) {
        DcmElement seq = ds.putSQ(tag);
        return seq != null ? seq : ds.putSQ(tag);
    }

    private int[] calculateFrameList(int frames) {
        int[] src = new int[frames];
        int length = 0;
        addFrame:
        for (int i = 0; i < calculatedFrameList.length;) {
            for (int f = calculatedFrameList[i++],
                    last = calculatedFrameList[i++],
                    step = calculatedFrameList[i++];
                    f <= last; f += step) {
                if (f > frames) {
                    break addFrame;
                }
                src[length++] = f;
            }
        }
        int[] dest = new int[length];
        System.arraycopy(src, 0, dest, 0, length);
        return dest;
    }

    private void write(Dataset ds, OutputStream out, DcmEncodeParam enc)
            throws IOException {
    	Dataset dsOut = filterDataset(ds);
    	
        if (writeFile) {
			dsOut.writeFile(out, enc);
        } else {
            dsOut.writeDataset(out, enc);
        }
    }
    
    protected Dataset filterDataset(Dataset dataset) {
    	Dataset filteredDataset;
    	
    	if (excludePrivate) {
    		filteredDataset = dataset.excludePrivate();
    		
    		if (writeFile) {
    			filteredDataset.setFileMetaInfo(dataset.getFileMetaInfo());
    		}
    	} else {
    		filteredDataset = dataset;
    	}
    	
    	return filteredDataset;
    }

    private void copyBytes(InputStream is, OutputStream out, int totLen,
            byte[] buffer) throws IOException {
        for (int len, toRead = totLen; toRead > 0; toRead -= len) {
            len = is.read(buffer, 0, Math.min(toRead, buffer.length));
            if (len == -1) {
                throw new EOFException();
            }
            out.write(buffer, 0, len);
        }
    }

	protected DecompressCmd createDecompressCmd(Dataset ds, String tsuid,
			DcmParser parser) throws IOException {
		return new DecompressCmd(ds, tsuid, parser);
	}
}
