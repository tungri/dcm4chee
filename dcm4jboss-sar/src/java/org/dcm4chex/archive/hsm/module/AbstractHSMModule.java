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

package org.dcm4chex.archive.hsm.module;

import java.io.ByteArrayOutputStream;
import java.io.File;

import javax.management.ObjectName;

import org.dcm4che.util.Executer;
import org.dcm4cheri.util.StringUtils;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author franz.willer@gmail.com
 * @version $Revision:  $ $Date: $
 * @since Aug 17, 2010
 */
public abstract class AbstractHSMModule extends ServiceMBeanSupport {

    public static final String NONE = "NONE";    
    protected ObjectName fileCopyServiceName;

    public ObjectName getFileCopyServiceName() {
        return fileCopyServiceName;
    }

    public void setFileCopyServiceName(ObjectName fileCopyServiceName) {
        this.fileCopyServiceName = fileCopyServiceName;
    }

    public abstract File prepareHSMFile(String fsID, String filePath) throws HSMException;

    public abstract String storeHSMFile(File file, String fsID, String filePath) throws HSMException;
    
    public abstract void failedHSMFile(File file, String fsID, String filePath) throws HSMException;

    /**
     * 
     * @param fsID      File System ID
     * @param filePath  Full path of file within given fsID
     * @return
     */
    public abstract File fetchHSMFile(String fsID, String filePath) throws HSMException;
    public abstract void fetchHSMFileFinished(String fsID, String filePath, File file) throws HSMException;
    
    public abstract Integer queryStatus(String fsID, String filePath, String userInfo) throws HSMException;
    
    protected String stripTarIdentifier(String s) {
        return s.startsWith("tar:") ? s.substring(4) : s;
    }
    
    protected String[] str2cmd(String cmd) {
        if (NONE.equalsIgnoreCase(cmd)) {
            return null;
        } else {
            String[] a = StringUtils.split(cmd, '%');
            try {
                String[] b = new String[a.length + a.length - 1];
                b[0] = a[0];
                for (int i = 1; i < a.length; i++) {
                    String s = a[i];
                    b[2 * i - 1] = ("%" + s.charAt(0)).intern();
                    b[2 * i] = s.substring(1);
                }
                return b;
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException(cmd);
            }
        }
    }

    protected String cmd2str(String[] cmd) {
        if (cmd == null) {
            return NONE;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < cmd.length; i++) {
            sb.append(cmd[i]);
        }
        return sb.toString();
    }

    protected void doCommand(String cmd, ByteArrayOutputStream stdout, String info) throws HSMException {
        int exit = -1;
        try {
            log.info(info+": "+cmd);
            Executer ex = new Executer(cmd, stdout, null);
            exit = ex.waitFor();
        } catch (Exception x) {
            throw new HSMException(info+" failed!", x);
        }
        if (exit != 0) {
            throw new HSMException(info+" failed! Non-zero exit code("+exit+") of "+cmd);
        }
    }
    
}
