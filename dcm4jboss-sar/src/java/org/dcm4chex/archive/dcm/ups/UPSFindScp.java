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

package org.dcm4chex.archive.dcm.ups;

import java.io.IOException;
import java.sql.SQLException;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseListener;
import org.dcm4che.net.ExtNegotiation;
import org.dcm4chex.archive.common.UPSState;
import org.dcm4chex.archive.ejb.jdbc.UPSQueryCmd;
import org.jboss.logging.Logger;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Apr 19, 2010
 */
class UPSFindScp extends DcmServiceBase {

    private static final int FUZZY_MATCHING = 2;

    private final UPSScpService service;

    private Logger log;

    public UPSFindScp(UPSScpService service) {
        this.service = service;
        this.log = service.getLog();
    }

    @Override
    protected MultiDimseRsp doCFind(ActiveAssociation assoc, Dimse rq,
            Command rspCmd) throws IOException, DcmServiceException {
        Association a = assoc.getAssociation();
        Command rqCmd = rq.getCommand();
        Dataset rqData = rq.getDataset();
        if (rqData == null)
            throw new DcmServiceException(Status.MistypedArgument,
                    "C-FIND-RQ without Data Set");
        log.debug("Identifier:\n");
        log.debug(rqData);
        checkCFindRQ(rqData);
        try {
            service.logDicomQuery(a, rqCmd.getAffectedSOPClassUID(),
                    rqData);
            boolean fuzzyMatchingOfPN = fuzzyMatchingOfPN(
                    a.getAcceptedExtNegotiation(
                            rqCmd.getAffectedSOPClassUID()));
            UPSQueryCmd queryCmd = 
                    new UPSQueryCmd(rqData, fuzzyMatchingOfPN,
                            service.isNoMatchForNoValue());
            queryCmd.setFetchSize(service.getFetchSize()).execute();
            return new MultiCFindRsp(queryCmd);
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            service.getLog().error("Query DB failed:", e);
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    private boolean fuzzyMatchingOfPN(ExtNegotiation extNeg) {
        byte[] info;
        return extNeg != null 
                && (info = extNeg.info()).length > FUZZY_MATCHING
                && info[FUZZY_MATCHING] != 0;
    }

   private void checkCFindRQ(Dataset rqData) throws DcmServiceException {
        String s = rqData.getString(Tags.UPSState);
        if (s != null)
            UPSScpService.upsStateAsInt(s);
    }

    private class MultiCFindRsp implements MultiDimseRsp {
        private final UPSQueryCmd queryCmd;
        private boolean canceled = false;

        public MultiCFindRsp(UPSQueryCmd queryCmd) {
            this.queryCmd = queryCmd;
        }

        public DimseListener getCancelListener() {
            return new DimseListener() {
                public void dimseReceived(Association assoc, Dimse dimse) {
                    canceled = true;
                }
            };
        }

        public Dataset next(ActiveAssociation assoc, Dimse rq, Command rspCmd)
            throws DcmServiceException
        {
            if (canceled) {
                rspCmd.putUS(Tags.Status, Status.Cancel);
                return null;
            }
            try {
                if (!queryCmd.next()) {
                    rspCmd.putUS(Tags.Status, Status.Success);
                    return null;
                }
                rspCmd.putUS(Tags.Status, Status.Pending);
                Dataset rspData = queryCmd.getDataset();
                                log.debug("Identifier:\n");
                                log.debug(rspData);
                return rspData;
            } catch (SQLException e) {
                service.getLog().error("Retrieve DB record failed:", e);
                throw new DcmServiceException(Status.ProcessingFailure, e);                
            } catch (Exception e) {
                service.getLog().error("Corrupted DB record:", e);
                throw new DcmServiceException(Status.ProcessingFailure, e);                
            }
        }

        public void release() {
            queryCmd.close();
        }
    }
}
