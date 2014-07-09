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

package org.dcm4chex.archive.ejb.jdbc;

import java.sql.SQLException;
import java.sql.Types;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.DcmServiceException;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 16583 $ $Date: 2012-02-08 12:36:32 +0000 (Wed, 08 Feb 2012) $
 * @since 10.02.2004
 */
public class GPPPSQueryCmd extends BaseDSQueryCmd {

    public static int transactionIsolationLevel = 0;
    public static int blobAccessType = Types.LONGVARBINARY;

    private static final String[] FROM = { "Patient", "GPPPS"};

    private static final String[] SELECT = { "Patient.encodedAttributes",
            "GPPPS.encodedAttributes"};

    private static final String[] RELATIONS = { "Patient.pk",
    		"GPPPS.patient_fk"};

    /**
     * @param ds
     * @throws SQLException
     */
    public GPPPSQueryCmd(Dataset keys) throws SQLException {
        super(keys, false, true, transactionIsolationLevel);
        try {
            AttributeFilter patAttrFilter = AttributeFilter.getPatientAttributeFilter();
            defineColumnTypes(new int[] { blobAccessType, blobAccessType });
            // ensure keys contains (8,0005) for use as result filter
            sqlBuilder.setSelect(SELECT);
            sqlBuilder.setFrom(FROM);
            sqlBuilder.setRelations(RELATIONS);
            sqlBuilder.addListOfStringMatch(null, "GPPPS.sopIuid",
                    SqlBuilder.TYPE1,
                    keys.getStrings(Tags.SOPInstanceUID) );
            sqlBuilder.addListOfStringMatch(null, "Patient.patientId",
                    SqlBuilder.TYPE1,
                    patAttrFilter.getStrings(keys, Tags.PatientID) );
            sqlBuilder.addPNMatch(new String[] {
                    "Patient.patientName",
                    "Patient.patientIdeographicName",
                    "Patient.patientPhoneticName"},
                    SqlBuilder.TYPE2,
                    patAttrFilter.isICase(Tags.PatientName),
                    keys.getString(Tags.PatientName));
            sqlBuilder.addRangeMatch(null, "GPPPS.ppsStartDateTime",
                    SqlBuilder.TYPE1,
                    keys.getDateTimeRange(Tags.PPSStartDate,Tags.PPSStartTime));
            sqlBuilder.addListOfStringMatch(null, "GPPPS.ppsStatusAsInt",
                    SqlBuilder.TYPE1,
                    keys.getStrings(Tags.PPSStatus));
        } catch (SQLException x) {
            close();
            throw x;
        } catch (RuntimeException x) {
            close();
            throw x;
        }
    }

    public Dataset getDataset() throws SQLException {
        Dataset ds = DcmObjectFactory.getInstance().newDataset();       
        DatasetUtils.fromByteArray( rs.getBytes(1), ds);
        DatasetUtils.fromByteArray( rs.getBytes(2), ds);
        return ds;
    }
    
 
}