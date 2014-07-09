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

package org.dcm4chex.archive.ejb.interfaces;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;

import org.dcm4cheri.util.StringUtils;

/**
 * <description>
 *
 * @see <related>
 * @author  <a href="mailto:gunter@tiani.com">gunter zeilinger</a>
 * @version $Revision: 15649 $ $Date: 2011-07-05 10:59:35 +0000 (Tue, 05 Jul 2011) $
 * @since July 23, 2002
 *
 * <p><b>Revisions:</b>
 *
 * <p><b>yyyymmdd author:</b>
 * <ul>
 * <li> explicit fix description (no line numbers but methods) go
 *            beyond the cvs commit message
 * </ul>
 */
public class AEDTO implements Serializable {

    private static final long serialVersionUID = -6925789594958392496L;

    // Variables -----------------------------------------------------
    private long pk = -1L;
    private String title;
    private String hostName;
    private int port = -1;
    private String cipherSuites;
    private String issuerOfPatientID;
    private String issuerOfAccessionNumber;
    private String userID;
    private String passwd;
    private String fsGroupID;
    private String group;
    private String desc;
    private String wadoURL;
    private String stationName;
    private String institution;
    private String department;
    private byte[] vendorData;
    private boolean installed = true;
    

    public final long getPk() {
        return pk;
    }

    public final void setPk(long pk) {
        this.pk = pk;
    }

    public final String getTitle() {
        return title;
    }
    
    public final void setTitle(String title) {
        this.title = title;
    }

    public final String getHostName() {
        return hostName;
    }

    public final void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getPort() {
        return port;
    }

    public final void setPort(int port) {
        this.port = port;
    }

    public String[] getCipherSuites() {
        return StringUtils.split(cipherSuites, ',');
    }

    public final String getCipherSuitesAsString() {
        return cipherSuites;
    }

    public final void setCipherSuitesAsString(String cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    public boolean isTLS() {
        return cipherSuites != null && cipherSuites.length() != 0;
    }
    
    public final String getIssuerOfPatientID() {
        return issuerOfPatientID;
    }
    
    public final void setIssuerOfPatientID(String issuer) {
        this.issuerOfPatientID = issuer;
    }

    public String[] getIssuerOfAccessionNumber() {
        return StringUtils.split(issuerOfAccessionNumber, '^');
    }

    public final String getIssuerOfAccessionNumberAsString() {
        return issuerOfAccessionNumber;
    }

    public final void setIssuerOfAccessionNumberAsString(String issuerOfAccessionNumber) {
        this.issuerOfAccessionNumber = issuerOfAccessionNumber;
    }

    public final String getUserID() {
        return userID;        
    }
    
    public final void setUserID(String userID) {
        this.userID = userID;
    }

    public final String getPassword() {
        return passwd;        
    }
    
    public final void setPassword(String passwd) {
        this.passwd = passwd;
    }

    public final String getFileSystemGroupID() {
        return fsGroupID;
    }
    
    public final void setFileSystemGroupID(String fsGroupID) {
        this.fsGroupID = fsGroupID;
    }

    public final String getGroup() {
        return group;        
    }
    
    public final void setGroup(String group) {
        this.group = group;
    }

    public final String getDescription() {
        return desc;        
    }

    public final void setDescription(String desc) {
        this.desc = desc;
    }

    public final String getStationName() {
        return stationName;
    }
    
    public final void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public final String getInstitution() {
        return institution;
    }
    
    public final void setInstitution(String institution) {
        this.institution = institution;
    }

    public final String getDepartment() {
        return department;
    }
    
    public final void setDepartment(String department) {
        this.department = department;
    }

    public final byte[] getVendorData() {
        return vendorData;
    }

    public final void setVendorData(byte[] vendorData) {
        this.vendorData = vendorData;
    }

    public Properties getVendorProperties(Properties defaults)
            throws IOException {
        Properties properties = new Properties(defaults);
        if (vendorData != null) {
            properties.load(new ByteArrayInputStream(vendorData));
        }
        return properties;
    }

    public void setVendorProperties(Properties properties) {
        if (properties == null) {
            vendorData = null;
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                properties.store(out , null);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            vendorData = out.size() == 0 ? null : out.toByteArray();
        }
    }

    public final boolean isInstalled() {
        return installed;
    }
    
    public final void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public final String getWadoURL() {
        return wadoURL;
    }

    public final void setWadoURL(String wadoURL) {
        this.wadoURL = wadoURL;
    }

    public String toString() {
        return (isTLS() ? "dicom-tls://" : "dicom://")
            + title
            + '@'
            + hostName
            + ':'
            + port;
    }
}
