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
 * Agfa HealthCare.
 * Portions created by the Initial Developer are Copyright (C) 2010
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below.
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
import org.dcm4chex.archive.common.UPSState;
import org.dcm4chex.archive.common.Priority;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.jdbc.Match.Node;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Apr 19, 2010
 */
public class UPSQueryCmd extends BaseDSQueryCmd {

    public static int transactionIsolationLevel = 0;
    public static int blobAccessType = Types.LONGVARBINARY;

    private static final String[] FROM = { "UPS" };

    private static final String[] SELECT = { "Patient.encodedAttributes",
            "UPS.encodedAttributes"};

    private static final String ITEM_CODE = "item_code";

    public UPSQueryCmd(Dataset keys, boolean fuzzyMatchingOfPN,
            boolean noMatchForNoValue) throws SQLException, DcmServiceException {
        super(keys, true, noMatchForNoValue, transactionIsolationLevel);
        try {
            AttributeFilter patAttrFilter = AttributeFilter.getPatientAttributeFilter();
            defineColumnTypes(new int[] { blobAccessType, blobAccessType });
            String s;
            // ensure keys contains (8,0005) for use as result filter
            if (!keys.contains(Tags.SpecificCharacterSet)) {
                keys.putCS(Tags.SpecificCharacterSet);
            }
            sqlBuilder.setSelect(SELECT);
            sqlBuilder.setFrom(FROM);
            sqlBuilder.setLeftJoin(getLeftJoin());
            sqlBuilder.addListOfUidMatch(null, "UPS.sopInstanceUID",
                    SqlBuilder.TYPE1,
                    keys.getStrings(Tags.SOPInstanceUID));
            if ((s = keys.getString(Tags.UPSState)) != null) {
                sqlBuilder.addIntValueMatch(null, "UPS.stateAsInt",
                        SqlBuilder.TYPE1,
                        UPSState.toInt(s));
            }
            s = keys.getString(Tags.SPSPriority);
            if (s != null) {
                sqlBuilder.addIntValueMatch(null, "UPS.priorityAsInt",
                        SqlBuilder.TYPE1,
                        Priority.toInt(s));
            }
            sqlBuilder.addWildCardMatch(null, "UPS.procedureStepLabel",
                    SqlBuilder.TYPE1,
                    keys.getStrings(Tags.ProcedureStepLabel));
            sqlBuilder.addWildCardMatch(null, "UPS.worklistLabel",
                    SqlBuilder.TYPE1,
                    keys.getStrings(Tags.WorklistLabel));
            sqlBuilder.addRangeMatch(null, "UPS.scheduledStartDateTime",
                    SqlBuilder.TYPE1,
                    keys.getDateRange(Tags.SPSStartDateAndTime));
            sqlBuilder.addRangeMatch(null, "UPS.expectedCompletionDateTime",
                    type2,
                    keys.getDateRange(Tags.ExpectedCompletionDateAndTime));
            sqlBuilder.addCodeMatch(ITEM_CODE,
                    keys.getItem(Tags.ScheduledWorkitemCodeSeq));
            addNestedCodeMatch(Tags.ScheduledProcessingApplicationsCodeSeq,
                    new String[]{ "UPS.pk", "rel_ups_appcode.ups_fk"},
                    new String[]{ "rel_ups_appcode", "Code" },
                    new String[]{ "rel_ups_appcode.appcode_fk", "Code.pk"});
            addNestedCodeMatch(Tags.ScheduledStationNameCodeSeq,
                    new String[]{ "UPS.pk", "rel_ups_devname.ups_fk"},
                    new String[]{ "rel_ups_devname", "Code" },
                    new String[]{ "rel_ups_devname.devname_fk", "Code.pk"});
            addNestedCodeMatch(Tags.ScheduledStationClassCodeSeq,
                    new String[]{ "UPS.pk", "rel_ups_devclass.ups_fk"},
                    new String[]{ "rel_ups_devclass", "Code" },
                    new String[]{ "rel_ups_devclass.devclass_fk", "Code.pk"});
            addNestedCodeMatch(Tags.ScheduledStationGeographicLocationCodeSeq,
                    new String[]{ "UPS.pk", "rel_ups_devloc.ups_fk"},
                    new String[]{ "rel_ups_devloc", "Code" },
                    new String[]{ "rel_ups_devloc.devloc_fk", "Code.pk"});
            addNestedCodeMatch(Tags.ScheduledHumanPerformersSeq,
                    Tags.HumanPerformerCodeSeq, 
                    new String[] { "UPS.pk", "rel_ups_performer.ups_fk" },
                    new String[] { "rel_ups_performer", "Code" },
                    new String[] { "rel_ups_performer.performer_fk", "Code.pk" });
            addRefRequestMatch();
            sqlBuilder.addRefSOPMatch(
                    new String[]{ "UPS.pk", "UPSRelatedPS.ups_fk"},
                    "UPSRelatedPS", "UPSRelatedPS.refSOPClassUID", 
                    "UPSRelatedPS.refSOPInstanceUID",
                    keys.getItem(Tags.RelatedProcedureStepSeq), type2);
    // TODO Tags.ReplacedProcedureStepSeq not yet defined
    //        sqlBuilder.addRefSOPMatch(
    //                new String[]{ "UPS.pk", "UPSReplacedPS.ups_fk"},
    //                "UPSReplacedPS", "UPSReplacedPS.refSOPClassUID", 
    //                "UPSReplacedPS.refSOPInstanceUID",
    //                keys.getItem(Tags.Tags.ReplacedProcedureStepSeq), type2);
    
            sqlBuilder.addWildCardMatch(null, "UPS.admissionID",
                    type2,
                    keys.getStrings(Tags.AdmissionID));
            Dataset issuer = keys.getItem(Tags.IssuerOfAdmissionIDSeq);
            if (issuer != null) {
                sqlBuilder.addSingleValueMatch(null,
                        "UPS.issuerOfAdmissionIDLocalNamespaceEntityID",
                        type2,
                        issuer.getString(Tags.LocalNamespaceEntityID));
                sqlBuilder.addSingleValueMatch(null,
                        "UPS.issuerOfAdmissionIDUniversalEntityID",
                        type2,
                        issuer.getString(Tags.UniversalEntityID));
            }
            if (sqlBuilder.addWildCardMatch(null, "Patient.patientId",
                    type2,
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
            sqlBuilder.addRangeMatch(null, "Patient.patientBirthDate", type2,
                    keys.getString(Tags.PatientBirthDate));
            sqlBuilder.addWildCardMatch(null, "Patient.patientSex", type2,
                    patAttrFilter.getStrings(keys, Tags.PatientSex));
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

    private void addRefRequestMatch() {
        Dataset item = keys.getItem(Tags.RefRequestSeq);
        if (item == null || item.isEmpty())
            return;
        
        SqlBuilder subQuery = new SqlBuilder();
        subQuery.setSelect1();
        String[] from = { "UPSRequest" };
        subQuery.setFrom(from);
        subQuery.addFieldValueMatch(null, "UPS.pk", false, null,
                "UPSRequest.ups_fk");
        boolean universalMatch = subQuery.addWildCardMatch(null,
                "UPSRequest.requestedProcedureId", type2,
                item.getStrings(Tags.RequestedProcedureID)) == null;
        universalMatch = subQuery.addWildCardMatch(null,
                "UPSRequest.accessionNumber", type2,
                item.getStrings(Tags.AccessionNumber)) == null
                && universalMatch;
        universalMatch = subQuery.addWildCardMatch(null,
                "UPSRequest.confidentialityCode", type2,
                item.getStrings(Tags.ConfidentialityCode)) == null
                && universalMatch;
        universalMatch = subQuery.addWildCardMatch(null,
                "UPSRequest.requestingService", type2,
                item.getStrings(Tags.RequestingService)) == null
                && universalMatch;

        if (universalMatch)
            return;

        if (!type2) {
            sqlBuilder.addCorrelatedSubquery(subQuery);
        } else {
            SqlBuilder subQuery2 = new SqlBuilder();
            subQuery2.setSelect1();
            subQuery2.setFrom(from);
            subQuery2.addFieldValueMatch(null, "UPS.pk", false, null, 
                    "UPSRequest.ups_fk");
            Match match2 = new Match.Subquery(subQuery2, null, null);
            Node notNode = new  Match.Node(null, true);
            notNode.addMatch(match2);
            Node orMatch = sqlBuilder.addNodeMatch(" OR", false);
            orMatch.addMatch(new Match.Subquery(subQuery, null, null));
            orMatch.addMatch(notNode);
        }
    }

    private void addNestedCodeMatch(int tag, String[] parentRelation,
            String[] tables, String[] relations) {
        addNestedCodeMatch(keys.getItem(tag), parentRelation, tables, relations);
    }

    private void addNestedCodeMatch(int tag1, int tag2, String[] parentRelation,
            String[] tables, String[] relations) {
        Dataset item = keys.getItem(tag1);
        if (item != null)
            addNestedCodeMatch(item.getItem(tag2), parentRelation, tables, relations);
    }

    private void addNestedCodeMatch(Dataset item, String[] parentRelation,
            String[] tables, String[] relations) {
        sqlBuilder.addNestedCodeMatch(parentRelation, tables, relations, item, 
                type2);
    }

    private String[] getLeftJoin() {
        boolean workitem;
        int index = 4;
        if (workitem = SqlBuilder.isCodeMatch(
                keys.getItem(Tags.ScheduledWorkitemCodeSeq)))
            index += 4;
        String[] leftJoin = new String[index];
        leftJoin[0] = "Patient";
        leftJoin[1] = null;
        leftJoin[2] = "UPS.patient_fk";
        leftJoin[3] = "Patient.pk";
        index = 4;
        if (workitem) {
            leftJoin[index++] = "Code";
            leftJoin[index++] = ITEM_CODE;
            leftJoin[index++] = "UPS.code_fk";
            leftJoin[index++] = "Code.pk";
        }
        return leftJoin;
    }

    public Dataset getDataset() throws SQLException {
        Dataset ds = DcmObjectFactory.getInstance().newDataset();
        DatasetUtils.fromByteArray( rs.getBytes(1), ds);
        DatasetUtils.fromByteArray( rs.getBytes(2), ds);
        adjustDataset(ds, keys);
        return ds.subSet(keys);
    }
}
