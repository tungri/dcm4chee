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

package org.dcm4chex.archive.codec;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.dcm4che.util.BufferedOutputStream;
import org.dcm4cheri.image.ImageWriterFactory;
import org.dcm4cheri.imageio.plugins.PatchJpegLSImageOutputStream;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.ejb.jdbc.FileInfo;

import EDU.oswego.cs.dl.util.concurrent.FIFOSemaphore;
import EDU.oswego.cs.dl.util.concurrent.Semaphore;

import com.sun.media.imageio.plugins.jpeg2000.J2KImageWriteParam;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 16270 $ $Date:: 2011-11-22#$
 * @since 11.06.2004
 * 
 */
public abstract class CompressCmd extends CodecCmd {

    private static int maxConcurrentCompress = 1;
    private static Semaphore compressSemaphore = new FIFOSemaphore(maxConcurrentCompress);
    private static AtomicInteger nrOfConcurrentCompress = new AtomicInteger();
    private static final byte[] ITEM_TAG = { (byte) 0xfe, (byte) 0xff,
            (byte) 0x00, (byte) 0xe0 };
    private static final String[] DERIVED_PRIMARY = { "DERIVED", "PRIMARY" };
    private static final Dataset LOSSY_COMPRESSION =
            newCodeItem("113040", "DCM", "Lossy Compression");
    private static final Dataset UNCOMPRESSED_PREDECESSOR =
            newCodeItem("121320", "DCM", "Uncompressed predecessor");

    private static Dataset newCodeItem(String value, String schemeDesignator,
            String meaning) {
        Dataset item = DcmObjectFactory.getInstance().newDataset();
        item.putSH(Tags.CodeValue, value);
        item.putSH(Tags.CodingSchemeDesignator, schemeDesignator);
        item.putLO(Tags.CodeMeaning, meaning);
        return item;
    }

    private static class Jpeg2000 extends CompressCmd {

        public Jpeg2000(Dataset ds, String tsuid) {
            super(ds, tsuid);
        }

        public void coerceDataset(Dataset ds) {
            if (samples == 3) {
                ds.putUS(Tags.PlanarConfiguration, 0);
                ds.putCS(Tags.PhotometricInterpretation, YBR_RCT);
            }
        }

        protected void initWriteParam(ImageWriteParam param) {
            if (param instanceof J2KImageWriteParam) {
                J2KImageWriteParam j2KwParam = (J2KImageWriteParam) param;
                j2KwParam.setWriteCodeStreamOnly(true);
            }
        }
    }

    private static class JpegLossless extends CompressCmd {

        public JpegLossless(Dataset ds, String tsuid) {
            super(ds, tsuid);
        }

        public void coerceDataset(Dataset ds) {
            if (samples == 3) {
                ds.putUS(Tags.PlanarConfiguration, 0);
            }
        }

        protected void initWriteParam(ImageWriteParam param) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(JPEG_LOSSLESS);
        }
    };

    private static class JpegLS extends CompressCmd {

        public JpegLS(Dataset ds, String tsuid) {
            super(ds, tsuid);
        }

        public void coerceDataset(Dataset ds) {
            if (samples == 3) {
                ds.putUS(Tags.PlanarConfiguration, 0);
            }

        }

        protected void initWriteParam(ImageWriteParam param) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(JPEG_LS);
        }
    }

    private static class JpegLossy extends CompressCmd {

        private final float quality;
        private final String derivationDescription;
        private final float compressionRatio;
        private final String iuid;
        private final String suid;

        public JpegLossy(Dataset ds, String tsuid, float quality,
                String derivationDescription, float compressionRatio,
                String iuid, String suid) throws CompressionFailedException {
            super(ds, tsuid);
            if (suid != null && iuid == null)
                throw new IllegalArgumentException(
                        "New Series Instance UID requires new SOP Instance UID");
            if (samples == 3 ? !photometricInterpretation.equals("RGB")
                    : !photometricInterpretation.equals("MONOCHROME2")
                    && !photometricInterpretation.equals("MONOCHROME1"))
                throw new CompressionFailedException(
                        "JPEG Lossy compression of "
                        + photometricInterpretation
                        + " images not supported");
            if (hasOverlayDataInPixelData(ds))
                throw new CompressionFailedException(
                        "JPEG Lossy compression of images with overlay data" +
                        " in the Image Pixel Data not supported");
            this.quality = quality;
            this.derivationDescription = derivationDescription;
            this.compressionRatio = compressionRatio;
            this.iuid = iuid;
            this.suid = suid;
        }

        private static boolean hasOverlayDataInPixelData(Dataset ds) {
            for (int i = 0; i < 16; i++) {
                int g = i << 17;
                if (ds.contains(Tags.OverlayRows | g)
                        && !ds.contains(Tags.OverlayData | g))
                    return true;
            }
            return false;
        }

        public void coerceDataset(Dataset ds) {
            if (samples == 3) {
                ds.putUS(Tags.PlanarConfiguration, 0);
                ds.putCS(Tags.PhotometricInterpretation, YBR_FULL_422);
            }
            ds.putUS(Tags.BitsStored, bitsUsed());
            ds.putCS(Tags.LossyImageCompression, "01");
            ds.putCS(Tags.LossyImageCompressionMethod, "ISO_10918_1");
            ds.putDS(Tags.LossyImageCompressionRatio, compressionRatio);
            if (iuid != null) {
                updateImageType(ds);
                updateDerivationDescription(ds);
                updateDerivationCodeSequence(ds);
                updateSourceImageSequence(ds);
                ds.putUI(Tags.SOPInstanceUID, iuid);
                if (suid != null)
                    ds.putUI(Tags.SeriesInstanceUID, suid);
            }
        }

        private void updateImageType(Dataset ds) {
            String[] imageType = ds.getStrings(Tags.ImageType);
            if (imageType == null || imageType.length == 0)
                imageType = DERIVED_PRIMARY;
            else
                imageType[0] = DERIVED_PRIMARY[0];
            ds.putCS(Tags.ImageType, imageType);
        }

        private void updateDerivationDescription(Dataset ds) {
            StringBuilder desc = new StringBuilder(32);
            String olddesc = ds.getString(Tags.DerivationDescription);
            if (olddesc != null) {
                desc.append(olddesc).append("; ");
            }
            desc.append(derivationDescription);
            ds.putST(Tags.DerivationDescription, desc.toString());
        }

        private void updateDerivationCodeSequence(Dataset ds) {
            DcmElement codes = ds.get(Tags.DerivationCodeSeq);
            if (codes == null)
                codes = ds.putSQ(Tags.DerivationCodeSeq);
            codes.addItem(LOSSY_COMPRESSION);
        }

        private void updateSourceImageSequence(Dataset ds) {
            DcmElement sourceImages = ds.get(Tags.SourceImageSeq);
            if (sourceImages == null)
                sourceImages = ds.putSQ(Tags.SourceImageSeq);
            Dataset sourceImage = sourceImages.addNewItem();
            sourceImage.putUI(Tags.RefSOPClassUID, 
                    ds.getString(Tags.SOPClassUID));
            sourceImage.putUI(Tags.RefSOPInstanceUID,
                    ds.getString(Tags.SOPInstanceUID));
            sourceImage.putSQ(Tags.PurposeOfReferenceCodeSeq)
                    .addItem(UNCOMPRESSED_PREDECESSOR);
        }

        protected void initWriteParam(ImageWriteParam param) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType("JPEG");
            param.setCompressionQuality(quality);
        }

        protected int bitsUsed() {
            return Math.min(12, bitsStored);
        }
    };

    public static void setMaxConcurrentCompression(int maxConcurrentCompress) {
        compressSemaphore = new FIFOSemaphore(maxConcurrentCompress);
        CompressCmd.maxConcurrentCompress = maxConcurrentCompress;
    }

    public static int getMaxConcurrentCompression() {
        return CompressCmd.maxConcurrentCompress;
    }

    private int bitmask() {
        return 0xffff >>> (bitsAllocated - bitsUsed());
    }

    public static byte[] compressFile(File inFile, File outFile, String tsuid,
            int[] planarConfiguration, int[] pxdataVR, byte[] buffer)
            throws Exception {
        log.info("M-READ file:" + inFile);
        InputStream in = new BufferedInputStream(new FileInputStream(inFile));
        try {
            DcmParser p = DcmParserFactory.getInstance().newDcmParser(in);
            final DcmObjectFactory of = DcmObjectFactory.getInstance();
            Dataset ds = of.newDataset();
            p.setDcmHandler(ds.getDcmHandler());
            p.parseDcmFile(FileFormat.DICOM_FILE, Tags.PixelData);
            if (planarConfiguration != null && planarConfiguration.length != 0)
                planarConfiguration[0] = ds.getInt(Tags.PlanarConfiguration, 0);
            if (pxdataVR != null && pxdataVR.length != 0)
                pxdataVR[0] = p.getReadVR();
            FileMetaInfo fmi = of.newFileMetaInfo(ds, tsuid);
            ds.setFileMetaInfo(fmi);
            log.info("M-WRITE file:" + outFile);
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestOutputStream dos = new DigestOutputStream(
                    new FileOutputStream(outFile), md);
            BufferedOutputStream bos = new BufferedOutputStream(dos, buffer);
            try {
                DcmDecodeParam decParam = p.getDcmDecodeParam();
                DcmEncodeParam encParam = DcmEncodeParam.valueOf(tsuid);
                CompressCmd compressCmd = CompressCmd.createCompressCmd(ds,
                        tsuid);
                compressCmd.coerceDataset(ds);
                ds.writeFile(bos, encParam);
                ds.writeHeader(bos, encParam, Tags.PixelData, VRs.OB, -1);
                int read = compressCmd.compress(decParam.byteOrder, in, bos,
                        null);
                ds.writeHeader(bos, encParam, Tags.SeqDelimitationItem,
                        VRs.NONE, 0);
                skipFully(in, p.getReadLength() - read);
                p.parseDataset(decParam, -1);
                ds.subSet(Tags.PixelData, -1).writeDataset(bos, encParam);
            } finally {
                bos.close();
            }
            return md.digest();
        } finally {
            in.close();
        }
    }

    public static byte[] compressFileJPEGLossy(File inFile, File outFile,
            int[] planarConfiguration, int[] pxdataVR, float quality,
            String derivationDescription, float estimatedCompressionRatio,
            float[] actualCompressionRatio, String iuid, String suid,
            byte[] buffer, Dataset ds, FileInfo fileInfo) throws Exception {
        if (suid != null && iuid == null)
            throw new IllegalArgumentException(
                    "New Series Instance UID requires new SOP Instance UID");
        log.info("M-READ file:" + inFile);
        InputStream in = new BufferedInputStream(new FileInputStream(inFile));
        try {
            DcmParser p = DcmParserFactory.getInstance().newDcmParser(in);
            if (ds == null)
                ds = DcmObjectFactory.getInstance().newDataset();
            p.setDcmHandler(ds.getDcmHandler());
            p.parseDcmFile(FileFormat.DICOM_FILE, Tags.PixelData);
            if (planarConfiguration != null && planarConfiguration.length != 0)
                planarConfiguration[0] = ds.getInt(Tags.PlanarConfiguration, 0);
            if (pxdataVR != null && pxdataVR.length != 0)
                pxdataVR[0] = p.getReadVR();
            if (fileInfo != null) {
                DatasetUtils.fromByteArray(fileInfo.patAttrs,
                        DatasetUtils.fromByteArray(fileInfo.studyAttrs,
                        DatasetUtils.fromByteArray(fileInfo.seriesAttrs,
                        DatasetUtils.fromByteArray(fileInfo.instAttrs, ds))));
            }
            CompressCmd compressCmd = createJPEGLossyCompressCmd(ds, quality,
                        derivationDescription, estimatedCompressionRatio,
                        iuid, suid);
            compressCmd.coerceDataset(ds);
            String tsuid = compressCmd.getTransferSyntaxUID();
            FileMetaInfo fmi = DcmObjectFactory.getInstance()
                    .newFileMetaInfo(ds, tsuid);
            ds.setFileMetaInfo(fmi);
            log.info("M-WRITE file:" + outFile);
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestOutputStream dos = new DigestOutputStream(
                    new FileOutputStream(outFile), md);
            BufferedOutputStream bos = new BufferedOutputStream(dos, buffer);
            try {
                DcmDecodeParam decParam = p.getDcmDecodeParam();
                DcmEncodeParam encParam = DcmEncodeParam.valueOf(tsuid);
                ds.writeFile(bos, encParam);
                ds.writeHeader(bos, encParam, Tags.PixelData, VRs.OB, -1);
                int read = compressCmd.compress(decParam.byteOrder, in, bos,
                        actualCompressionRatio);
                ds.writeHeader(bos, encParam, Tags.SeqDelimitationItem,
                        VRs.NONE, 0);
                skipFully(in, p.getReadLength() - read);
                p.parseDataset(decParam, -1);
                ds.subSet(Tags.PixelData, -1).writeDataset(bos, encParam);
            } finally {
                bos.close();
            }
            return md.digest();
        } finally {
            in.close();
        }
    }

    private static void skipFully(InputStream in, int n) throws IOException {
        int remaining = n;
        int skipped = 0;
        while (remaining > 0) {
            if ((skipped = (int) in.skip(remaining)) == 0) {
                throw new EOFException();
            }
            remaining -= skipped;
        }
    }

    public static CompressCmd createCompressCmd(Dataset ds, String tsuid) {
        if (UIDs.JPEG2000Lossless.equals(tsuid)) {
            return new Jpeg2000(ds, tsuid);
        }
        if (UIDs.JPEGLSLossless.equals(tsuid)) {
            return new JpegLS(ds, tsuid);
        }
        if (UIDs.JPEGLossless.equals(tsuid)
                || UIDs.JPEGLossless14.equals(tsuid)) {
            return new JpegLossless(ds, tsuid);
        }
        throw new IllegalArgumentException("tsuid:" + tsuid);
    }

    public static CompressCmd createJPEGLossyCompressCmd(Dataset ds,
            float quality, String derivationDescription, float ratio,
            String iuid, String suid) throws CompressionFailedException {
        return new JpegLossy(ds,
                ds.getInt(Tags.BitsAllocated, 8) > 8
                    ? UIDs.JPEGExtended 
                    : UIDs.JPEGBaseline,
                quality, derivationDescription, ratio, iuid, suid);
    }

    
    protected CompressCmd(Dataset ds, String tsuid) {
        super(ds, tsuid);
    }

    public abstract void coerceDataset(Dataset ds);

    protected abstract void initWriteParam(ImageWriteParam param);

    public int compress(ByteOrder byteOrder, InputStream in, OutputStream out,
            float[] compressionRatio) throws Exception {
        long t1 = System.currentTimeMillis();
        ImageWriter w = null;
        BufferedImage bi = null;
        boolean codecSemaphoreAquired = false;
        boolean compressSemaphoreAquired = false;
        long end = 0;
        int pixelDataLength;
        try {
            if (log.isDebugEnabled()) {
                log.debug("acquire codec semaphore:");
                log.debug("#####codecSemaphore.permits():"+codecSemaphore.permits());
                log.debug("#####compressSemaphore.permits():"+compressSemaphore.permits());
            }
            codecSemaphore.acquire();
            codecSemaphoreAquired = true;
            compressSemaphore.acquire();
            compressSemaphoreAquired = true;
            if (log.isDebugEnabled()) 
                log.debug("codec semaphore acquired after "+(System.currentTimeMillis() - t1)+"ms!");
            log.info("start compression of image: " + rows + "x" + columns
                    + "x" + frames + " (current codec tasks: compress&decompress:" + (nrOfConcurrentCodec.incrementAndGet())+
                    " compress:"+(nrOfConcurrentCompress.incrementAndGet())+")");
            t1 = System.currentTimeMillis();
            ImageOutputStream ios = new MemoryCacheImageOutputStream(out);
            ios.setByteOrder(ByteOrder.LITTLE_ENDIAN);
            ImageWriterFactory f = ImageWriterFactory.getInstance();
            w = f.getWriterForTransferSyntax(tsuid);
            boolean patchJpegLS = bitsAllocated == 16
                    && UIDs.JPEGLSLossless.equals(tsuid)
                    && f.patchJAIJpegLS();
            ImageWriteParam wParam = w.getDefaultWriteParam();
            initWriteParam(wParam);
            bi = getBufferedImage();
            WritableRaster raster = bi.getRaster();
            DataBuffer db = raster.getDataBuffer();
            ios.write(ITEM_TAG);
            ios.writeInt(0);
            for (int i = 0; i < frames; ++i) {
                log.debug("start compression of frame #" + (i + 1));
                ios.write(ITEM_TAG);
                long mark = ios.getStreamPosition();
                ios.writeInt(0);
                switch (dataType) {
                case DataBuffer.TYPE_BYTE:
                    read(in, ((DataBufferByte) db).getBankData());
                    break;
                case DataBuffer.TYPE_SHORT:
                    read(byteOrder, in, ((DataBufferShort) db).getBankData());
                    break;
                case DataBuffer.TYPE_USHORT:
                    read(byteOrder, in, ((DataBufferUShort) db).getBankData());
                    break;
                default:
                    throw new RuntimeException("dataType:" + db.getDataType());
                }
                w.setOutput(patchJpegLS
                        ? new PatchJpegLSImageOutputStream(ios)
                        : ios);
                w.write(null, new IIOImage(bi, null, null), wParam);
                end = ios.getStreamPosition();
                if ((end & 1) != 0) {
                    ios.write(0);
                    ++end;
                }
                ios.seek(mark);
                ios.writeInt((int) (end - mark - 4));
                ios.seek(end);
                ios.flush();
            }
        } finally {
            if (w != null)
                w.dispose();
            if (bi != null)
                biPool.returnBufferedImage(bi);
            long t2 = System.currentTimeMillis();
            pixelDataLength = frameLength * frames;
            nrOfConcurrentCompress.decrementAndGet();
            nrOfConcurrentCodec.decrementAndGet();
            log.info("finished compression " + ((float) pixelDataLength / end)
                    + " : 1 in " + (t2 - t1) + "ms." + " (remaining codec tasks: compress&decompress:"+nrOfConcurrentCodec+
                    " compress:"+nrOfConcurrentCompress+")");
            if (compressSemaphoreAquired) {
                compressSemaphore.release();
            }
            if (codecSemaphoreAquired) {
                log.debug("release codec semaphore");
                codecSemaphore.release();
            }
        }
        if (compressionRatio != null && compressionRatio.length > 0)
            compressionRatio[0] =
                (float) pixelDataLength * bitsUsed() / bitsAllocated / end;
        return pixelDataLength;
    }

    private void read(ByteOrder byteOrder, InputStream in, short[][] data)
            throws IOException {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
            readLE(in, data);
        else
            readBE(in, data);
    }

    private void readLE(InputStream in, short[][] data) throws IOException {
        int lo, hi;
        int bitmask = bitmask();
        for (int i = 0; i < data.length; i++) {
            short[] bank = data[i];
            for (int j = 0; j < bank.length; j++) {
                lo = in.read();
                hi = in.read();
                if ((lo | hi) < 0)
                    throw new EOFException();
                bank[j] = (short) (((lo & 0xff) + (hi << 8)) & bitmask);
            }
        }
    }

    private void readBE(InputStream in, short[][] data) throws IOException {
        int bitmask = bitmask();
        int lo, hi;
        for (int i = 0; i < data.length; i++) {
            short[] bank = data[i];
            for (int j = 0; j < bank.length; j++) {
                hi = in.read();
                lo = in.read();
                if ((lo | hi) < 0)
                    throw new EOFException();
                bank[j] = (short) (((lo & 0xff) + (hi << 8)) & bitmask);
            }
        }
    }

    private void read(InputStream in, byte[][] data) throws IOException {
        int read;
        for (int i = 0; i < data.length; i++) {
            byte[] bank = data[i];
            for (int toread = bank.length; toread > 0;) {
                read = in.read(bank, bank.length - toread, toread);
                if (read == -1)
                    throw new EOFException(
                            "Length of pixel matrix is too short!");
                toread -= read;
            }
        }
    }
}
