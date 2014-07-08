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

import java.io.File;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * @author franz.willer@gmail.com
 * @version $Revision:  $ $Date: $
 * @since June 22, 2012
 */
public class MappedHSMModule extends AbstractHSMModule {

    public static final String NEW_LINE = System.getProperty("line.separator", "\n");
    private static final String STRING = String.class.getName();
    private static final String FILE = File.class.getName();

    HashMap<String, ObjectName> mapping = new HashMap<String, ObjectName>();
        
    protected void startService() throws Exception {}

    protected void stopService() throws Exception {}
    
    public String getModuleMapping() {
        if (mapping.isEmpty())
            return NONE;
        StringBuilder sb = new StringBuilder();
        for (Entry<String, ObjectName> entry : mapping.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(NEW_LINE);
        }
        return sb.toString();
    }
    
    public void setModuleMapping(String s) throws MalformedObjectNameException, NullPointerException {
        s = s.trim();
        if(NONE.equals(s)) {
            mapping.clear();
        } else {
            StringTokenizer st = new StringTokenizer(s, " \t\n\r;");
            int pos;
            HashMap<String, ObjectName> newMap = new HashMap<String, ObjectName>();
            for (String tk ; st.hasMoreElements() ;) {
                tk = st.nextToken();
                pos = tk.indexOf('=');
                if (pos == -1)
                    throw new IllegalArgumentException("Wrong format: must be <FilesystemID>=<HSMModule Service Name>[<NewLine>...]");
                newMap.put(tk.substring(0, pos), new ObjectName(tk.substring(++pos)));
            }
            mapping = newMap;
        }
    }
    
    @Override
    public File prepareHSMFile(String fsID, String filePath) throws HSMException {
        return (File) invoke(fsID, "prepareHSMFile", new Object[]{fsID, filePath}, new String[]{STRING, STRING});
    }

    @Override
    public String storeHSMFile(File file, String fsID, String filePath) throws HSMException {
        return (String)invoke(fsID, "storeHSMFile", new Object[]{file, fsID, filePath}, new String[]{FILE, STRING, STRING});
    }
    
    @Override
    public void failedHSMFile(File file, String fsID, String filePath) throws HSMException {
        invoke(fsID, "failedHSMFile", new Object[]{file, fsID, filePath}, new String[]{FILE, STRING, STRING});
    }

    @Override
    public File fetchHSMFile(String fsID, String filePath) throws HSMException {
        return (File)invoke(fsID, "fetchHSMFile", new Object[]{fsID, filePath}, new String[]{STRING, STRING});
    }

    @Override
    public void fetchHSMFileFinished(String fsID, String filePath, File file) throws HSMException {
        invoke(fsID, "fetchHSMFileFinished", new Object[]{fsID, filePath, file}, new String[]{STRING, STRING, FILE});
    }
    
    @Override
    public Integer queryStatus(String fsID, String filePath, String userInfo) throws HSMException {
        return (Integer)invoke(fsID, "queryStatus", new Object[]{fsID, filePath, userInfo}, new String[]{STRING, STRING, STRING});
    }
    
    private Object invoke(String fsID, String cmd, Object[] params, String[] paramTypes) throws HSMException {
        ObjectName hsmModuleServicename = mapping.get(fsID);
        if (hsmModuleServicename == null) {
            throw new HSMException("Invoke command '"+cmd+"' on mapped HSMModule failed! No mapping for fsID:"+fsID, null,
                    HSMException.ERROR_ON_FILESYSTEM_LEVEL);
        }
        log.debug("Invoke "+cmd+" on MappedHSMModule! module:"+hsmModuleServicename);
        try {
            return server.invoke(hsmModuleServicename, cmd, params, paramTypes);
        } catch (Exception x) {
            if (x instanceof HSMException)
                throw (HSMException)x;
            throw new HSMException("Invoke command '"+cmd+"' on mapped HSMModule failed! HSMModule:"+
                    hsmModuleServicename+" (for fsID:"+fsID+")", x, HSMException.ERROR_ON_FILESYSTEM_LEVEL);
        }
    }
}
