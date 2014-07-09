/* ***** BEGIN LICENSE BLOCK *****
 * Copyright (c) 2013 by AGFA HealthCare
 *
 * This file is part of dcm4che.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chex.archive.hsm.module.uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

/**
 * @author Kianusch.Sayah-Karadji@agfa.com based on work from
 *         Gunter.Zeilinger@tiani.com
 * @version $Revision: 1.3 $
 * @since 07.08.2003
 */

public abstract class Cifs {
    private static void copy(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] b = new byte[8192];
            int n = 0;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
        } finally {
            try {
                in.close();
            } catch (IOException e) {
            }
            try {
                out.close();
            } catch (IOException e) {
            }
        }
    }

    public static final void copyTo(final String source, final String destination, final long retention, final boolean setAccessTimeAfterSetReadonly) throws Exception {
            String dDir = new SmbFile(destination).getParent();
            if (!new SmbFile(dDir).exists()) {
                new SmbFile(dDir).mkdirs();
            }
            SmbFile file=new SmbFile(destination);
            copy(new FileInputStream(new File(source)), new SmbFileOutputStream(file));
            if (setAccessTimeAfterSetReadonly)
                file.setReadOnly();
            if (retention>0) {
                file.setCreateTime(retention*1000);
                file.setLastModified(retention*1000);
            }
            if (!setAccessTimeAfterSetReadonly)
                file.setReadOnly();
    }

    public static final void copyFrom(final String source, final String destination) throws Exception {
            String dDir = new File(destination).getParent() + '/';
            if (!new File(dDir).exists()) {
                new File(dDir).mkdirs();
            }
            copy(new SmbFileInputStream(new SmbFile(source)), new FileOutputStream(new File(destination)));
    }

    public static final long fileLength(final String source) throws Exception {
        if (new SmbFile(source).exists()) {
            return new SmbFile(source).length();
        }
        return -1L;
    }
}