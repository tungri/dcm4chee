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
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.DcmServiceException;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.common.SPSStatus;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 16583 $ $Date: 2012-02-08 12:36:32 +0000 (Wed, 08 Feb 2012) $
 * @since 10.02.2004
 */
public class MWLQueryCmd extends BaseDSQueryCmd {
    
    private static final String[] FROM = { "Patient", "MWLItem" };
    private static final String[] SELECT = { "Patient.encodedAttributes",
            "MWLItem.encodedAttributes" };
    private static final String[] RELATIONS = { "Patient.pk",
            "MWLItem.patient_fk" };

    private static final int[] MATCHING_KEYS = new int[] {
        Tags.RequestedProcedureID,
		Tags.AccessionNumber,
		Tags.StudyInstanceUID,
		Tags.PatientID,
		Tags.IssuerOfPatientID,
		Tags.PatientName,
		Tags.SPSSeq
		};
    private static final int[] MATCHING_SPS_SQ_KEYS = new int[] {
        Tags.SPSStatus,
		Tags.SPSID,
		Tags.SPSStartDate, Tags.SPSStartTime,
		Tags.Modality,
		Tags.ScheduledPerformingPhysicianName,
		Tags.ScheduledStationAET,
		Tags.ScheduledStationName,
		};
    
    public static int transactionIsolationLevel = 0;
    public static int blobAccessType = Types.LONGVARBINARY;

    protected static final DcmObjectFactory dof = DcmObjectFactory.getInstance();
    
    /**
     * @param ds
     * @throws SQLException
     * @throws DcmServiceException 
     */
    public MWLQueryCmd(Dataset keys, boolean fuzzyMatchingOfPN,
            boolean noMatchForNoValue) throws SQLException, DcmServiceException {
        super(keys, true, noMatchForNoValue, transactionIsolationLevel);
        try {
            AttributeFilter patAttrFilter = AttributeFilter.getPatientAttributeFilter();
            defineColumnTypes(new int[] { blobAccessType, blobAccessType });
            // ensure keys contains (8,0005) for use as result filter
            if (!keys.contains(Tags.SpecificCharacterSet)) {
                keys.putCS(Tags.SpecificCharacterSet);
            }
            sqlBuilder.setSelect(SELECT);
            sqlBuilder.setFrom(FROM);
            sqlBuilder.setRelations(RELATIONS);
            Dataset spsItem = keys.getItem(Tags.SPSSeq);
            if (spsItem != null) {
                sqlBuilder.addListOfIntMatch(null, "MWLItem.spsStatusAsInt",
                            SqlBuilder.TYPE1, 
                            SPSStatus.toInts(spsItem.getStrings(Tags.SPSStatus)));
                sqlBuilder.addWildCardMatch(null, "MWLItem.spsId",
                        SqlBuilder.TYPE1,
                        spsItem.getStrings(Tags.SPSID));
                sqlBuilder.addRangeMatch(null, "MWLItem.spsStartDateTime",
                        SqlBuilder.TYPE1,
                        spsItem.getDateTimeRange(Tags.SPSStartDate,
                                Tags.SPSStartTime));
                sqlBuilder.addWildCardMatch(null, "MWLItem.modality",
                        SqlBuilder.TYPE1,
                        spsItem.getStrings(Tags.Modality));
                sqlBuilder.addWildCardMatch(null, "MWLItem.scheduledStationAET",
                        SqlBuilder.TYPE1,
                        spsItem.getStrings(Tags.ScheduledStationAET));
                sqlBuilder.addWildCardMatch(null, "MWLItem.scheduledStationName",
                        type2,
                        spsItem.getStrings(Tags.ScheduledStationName));
                if (fuzzyMatchingOfPN)
                    try {
                        sqlBuilder.addPNFuzzyMatch(
                                new String[] {
                                    "MWLItem.performingPhysicianFamilyNameSoundex",
                                    "MWLItem.performingPhysicianGivenNameSoundex" },
                                type2,
                                keys.getString(Tags.ScheduledPerformingPhysicianName));
                    } catch (IllegalArgumentException ex) {
                        throw new DcmServiceException(
                                Status.IdentifierDoesNotMatchSOPClass,
                                ex.getMessage() + ": " + keys.get(Tags.ScheduledPerformingPhysicianName));
                    }
                else
                    sqlBuilder.addPNMatch(
                            new String[] {
                                "MWLItem.performingPhysicianName",
                                "MWLItem.performingPhysicianIdeographicName",
                                "MWLItem.performingPhysicianPhoneticName"},
                            true, // TODO make ICASE configurable
                            type2,
                            spsItem.getString(Tags.ScheduledPerformingPhysicianName));
            }
            sqlBuilder.addWildCardMatch(null, "MWLItem.requestedProcedureId",
                    SqlBuilder.TYPE1,
                    keys.getStrings(Tags.RequestedProcedureID));
            sqlBuilder.addWildCardMatch(null, "MWLItem.accessionNumber",
                    type2,
                    keys.getStrings(Tags.AccessionNumber));
            sqlBuilder.addListOfStringMatch(null, "MWLItem.studyIuid",
                    SqlBuilder.TYPE1,
                    keys.getStrings(Tags.StudyInstanceUID));
            if (sqlBuilder.addWildCardMatch(null, "Patient.patientId",
                    SqlBuilder.TYPE1,
                    patAttrFilter.getStrings(keys, Tags.PatientID)) != null)
                sqlBuilder.addSingleValueMatch(null, "Patient.issuerOfPatientId",
                        type2,
                        patAttrFilter.getString(keys, Tags.IssuerOfPatientID));
            if (fuzzyMatchingOfPN)
                try {
                    sqlBuilder.addPNFuzzyMatch(
                            new String[] {
                                "Patient.patientFamilyNameSoundex",
                                "Patient.patientGivenNameSoundex" },
                            type2,
                            keys.getString(Tags.PatientName));
                } catch (IllegalArgumentException ex) {
                    throw new DcmServiceException(
                            Status.IdentifierDoesNotMatchSOPClass,
                            ex.getMessage() + ": " + keys.get(Tags.PatientName));
                }
            else
                sqlBuilder.addPNMatch(
                        new String[] {
                            "Patient.patientName",
                            "Patient.patientIdeographicName",
                            "Patient.patientPhoneticName"},
                        type2,
                        patAttrFilter.isICase(Tags.PatientName),
                        keys.getString(Tags.PatientName));
        } catch (SQLException x) {
            close();
            throw x;
        } catch (DcmServiceException x) {
            close();
            throw x;
        } catch (RuntimeException x) {
            close();
            throw x;
        }
    }

    public Dataset getDataset() throws SQLException {
        Dataset ds = DcmObjectFactory.getInstance().newDataset();    
        Dataset dsPat = DcmObjectFactory.getInstance().newDataset(); //It seems that Oracle has problems to read 1st BLOB after 2nd! 
        DatasetUtils.fromByteArray(rs.getBytes(1), dsPat); //patient
        DatasetUtils.fromByteArray(rs.getBytes(2), ds); //mwl item
        ds.putAll(dsPat);
        adjustDataset(ds, keys);
        return ds.subSet(keys);
    }
}