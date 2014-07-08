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
 * Java(TM), hosted at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
 * Joe Foraci <jforaci@users.sourceforge.net>
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

package org.dcm4che.image;

import java.nio.ByteOrder;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che.data.Dataset;
import org.dcm4cheri.image.PixelDataFactoryImpl;

/**
 * @author <a href="mailto:gunter@tiani.com">gunter zeilinger</a>
 * @author <a href="mailto:joseph@tiani.com">joseph foraci</a>
 * @since July 2003
 * @version $Revision: 3922 $ $Date: 2005-10-05 16:26:16 +0000 (Wed, 05 Oct 2005) $
 * @see "DICOM Part 5: Data Structures and Encoding, Section 8. 'Encoding of Pixel,
 *      Overlay and Waveform Data', Annex D"
 */
public abstract class PixelDataFactory
{
    public PixelDataFactory()
    {
    }

    public static PixelDataFactory getInstance()
    {
        return new PixelDataFactoryImpl();
    }

    /**
     * Creates a new <code>PixelDataReader</code> instance, initialized by the
     * <code>Dataset</code> and backed by the <code>ImageInputStream</code>.
     * Any changes to the <code>ImageInputStream</code> will be seen by the
     * <code>PixelDataReader</code> instance and will have undefined effects upon
     * the next read.
     */
    public abstract PixelDataReader newReader(PixelDataDescription desc, ImageInputStream iis);
    public abstract PixelDataReader newReader(Dataset dataset, ImageInputStream iis, ByteOrder byteOrder, int pixelDataVr);

    /**
     * Creates a new <code>PixelDataWriter</code> instance, initialized by the
     * <code>Dataset</code> and backed by the <code>ImageOutputStream</code>.
     * Any changes to the <code>ImageOutputStream</code> will be seen by the
     * <code>PixelDataWriter</code> instance and will have undefined effects upon
     * the next read.
     */
    public abstract PixelDataWriter newWriter(int[][][] data, boolean containsOverlayData, PixelDataDescription desc, ImageOutputStream ios);
    public abstract PixelDataWriter newWriter(int[][][] data, boolean containsOverlayData, Dataset dataset, ImageOutputStream ios, ByteOrder byteOrder, int pixelDataVr);
}