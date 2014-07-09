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

package org.dcm4chex.archive.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.Association;
import org.dcm4chex.archive.codec.CompressCmd;
import org.dcm4chex.archive.codec.CompressionFailedException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision: 15875 $ $Date: 2011-08-31 20:08:37 +0000 (Wed, 31 Aug 2011) $
 * @since 11.06.2004
 * 
 */
public class CompressionRules {

    static final Logger LOG = Logger.getLogger(CompressionRules.class);

    static final int NONE = 0;

    static final int J2LL = 1;

    static final int JLSL = 2;

    static final int J2KR = 3;

    static final int JPLY = 4;

    static final String[] CODES = { "NONE", "JPLL", "JLSL", "J2KR", "JPLY" };

    static final String[] TSUIDS = { null, UIDs.JPEGLossless,
            UIDs.JPEGLSLossless, UIDs.JPEG2000Lossless, UIDs.JPEGBaseline};

    private final ArrayList list = new ArrayList();

    private static final class Entry {

        final Condition condition;
        final int compression;
        final float quality;
        final String derivationDescription;
        final float ratio;
        
        Entry(Condition condition, int compression, float quality,
                String derivationDescription, float ratio) {
            this.condition = condition;
            this.compression = compression;
            this.quality = quality;
            this.derivationDescription = derivationDescription;
            this.ratio = ratio;
       }
    }

    public CompressionRules(String s) {
        StringTokenizer stk = new StringTokenizer(s, "\r\n;");
        while (stk.hasMoreTokens()) {
            String tk = stk.nextToken().trim();
            if (tk.length() == 0)
                continue;
            try {
                int endCond = tk.indexOf(']') + 1;
                Condition cond = new Condition(tk.substring(0, endCond));
                String codec = tk.substring(endCond);
                int compression;
                float quality = 0.75f;
                float ratio = 5.f;
                String derivationDescription = "JPEG Lossy Compression with quality=0.75";
                if (codec.startsWith("JPLY(")) {
                    if (!codec.endsWith(")"))
                        throw new IllegalArgumentException();
                    int endQuality = codec.indexOf(':');
                    if (endQuality == -1)
                        throw new IllegalArgumentException();
                    int endDesc = codec.indexOf(':',endQuality+1); 
                    if (endDesc == -1)
                        throw new IllegalArgumentException();
                    compression = JPLY;
                    quality = Float.parseFloat(codec.substring(5, endQuality));
                    derivationDescription =
                        codec.substring(endQuality + 1, endDesc);
                    ratio =  Float.parseFloat(
                            codec.substring(endDesc + 1, codec.length()-1));
                } else {
                    compression = Arrays.asList(CODES).indexOf(codec);
                    if (compression == -1)
                        throw new IllegalArgumentException();
                }
                list.add(new Entry(cond, compression, quality, derivationDescription,
                        ratio));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(tk);
            }
        }
    }

    public CompressCmd getCompressFor(Association assoc, Dataset ds) {
        Map param = new HashMap();
        param.put("calling", new String[] { assoc.getCallingAET() });
        param.put("called", new String[] { assoc.getCalledAET() });
        if (ds != null) {
            putIntoIfNotNull(param, "cuid", ds, Tags.SOPClassUID);
            putIntoIfNotNull(param, "pmi", ds, Tags.PhotometricInterpretation);
            putIntoIfNotNull(param, "imgtype", ds, Tags.ImageType);
            putIntoIfNotNull(param, "bodypart", ds, Tags.BodyPartExamined);
        }
        for (Iterator it = list.iterator(); it.hasNext();) {
            Entry e = (Entry) it.next();
            if (e.condition.isTrueFor(param)) {
                try {
                    return (e.compression == NONE)? null : (e.compression == JPLY) 
                            ? CompressCmd.createJPEGLossyCompressCmd(
                                    ds, e.quality, e.derivationDescription,
                                    e.ratio, null, null)
                            : CompressCmd.createCompressCmd(
                                    ds, TSUIDS[e.compression]);
                } catch (CompressionFailedException e1) {
                    LOG.info(e1.getMessage());
                    continue;
                }
            }
        }
        return null;
    }

    private void putIntoIfNotNull(Map param, String key, Dataset ds, int tag) {
        String[] val = ds.getStrings(tag);
        if (val != null && val.length != 0) {
            param.put(key, val);
        }
    }

    public String toString() {
        final String newline = System.getProperty("line.separator", "\n");
        if (list.isEmpty())
            return newline;
        StringBuffer sb = new StringBuffer();
        for (Iterator it = list.iterator(); it.hasNext();) {
            Entry e = (Entry) it.next();
            e.condition.toStringBuffer(sb);
            sb.append(CODES[e.compression]);
            if (e.compression == JPLY) {
                sb.append('(').append(e.quality)
                  .append(':').append(e.derivationDescription)
                  .append(':').append(e.ratio)
                  .append(')');
            }
            sb.append(newline);
        }
        return sb.toString();
    }
}
