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

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.PIDWithIssuer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Dec 17, 2010
 */
class AdjustPatientID {

    private final Pattern pidPattern;
    private final String requestedIssuer;
    private final boolean includeOtherPIDs;
    private final Set<PIDWithIssuer> pidWithIssuers;

    public AdjustPatientID(Dataset keys, Set<PIDWithIssuer> pidWithIssuers) {
        this.includeOtherPIDs = keys.contains(Tags.OtherPatientIDSeq);
        this.pidPattern = toPattern(keys.getString(Tags.PatientID));
        this.requestedIssuer = keys.getString(Tags.IssuerOfPatientID);
        this.pidWithIssuers = new HashSet<PIDWithIssuer>(pidWithIssuers);
    }

    private Pattern toPattern(String pid) {
        if (pid == null || pid.equals("*"))
            return null;

        StringBuilder regex = new StringBuilder();
        for (StringTokenizer stk = new StringTokenizer(pid, "*?", true);
                stk.hasMoreTokens();) {
            String tk = stk.nextToken();
            if (tk.equals("*"))
                regex.append(".*");
            else if (tk.equals("?"))
                regex.append('.');
            else
                regex.append("\\Q").append(tk).append("\\E");
        }
        return Pattern.compile(regex.toString());
    }

    public void adjust(Dataset ds) {
        String pid = ds.getString(Tags.PatientID);
        String issuer = ds.getString(Tags.IssuerOfPatientID);
        pidWithIssuers.add(new PIDWithIssuer(pid, issuer));
        DcmElement opidsq = ds.get(Tags.OtherPatientIDSeq);
        if (opidsq != null) {
            for (int i = 0, n = opidsq.countItems(); i < n; i++) {
                Dataset item = opidsq.getItem(i);
                pidWithIssuers.add(new PIDWithIssuer(
                        item.getString(Tags.PatientID),
                        item.getString(Tags.IssuerOfPatientID)));
            }
            ds.remove(Tags.OtherPatientIDSeq);
        }
        ds.putLO(Tags.PatientID); // nullify
        ds.putLO(Tags.IssuerOfPatientID, requestedIssuer);
        boolean foundMatching = false;
        for (PIDWithIssuer pwi : pidWithIssuers) {
            if (foundMatching
                    || !(matchIssuer(pwi.issuer) && matchPID(pwi.pid))) {
               addOtherPatientID(ds, pwi.pid, pwi.issuer);
            } else {
                ds.putLO(Tags.PatientID, pwi.pid);
                ds.putLO(Tags.IssuerOfPatientID, pwi.issuer);
                foundMatching = true;
            }
        }
    }

    private boolean matchPID(String pid) {
        return pidPattern == null || pid == null
                || pidPattern.matcher(pid).matches();
    }

    private boolean matchIssuer(String issuer) {
        return requestedIssuer == null || issuer == null
                || requestedIssuer.equals(issuer);
    }

    private void addOtherPatientID(Dataset ds, String pid, String issuer) {
        if (!includeOtherPIDs)
            return;

        DcmElement opidsq = ds.get(Tags.OtherPatientIDSeq);
        if (opidsq == null)
            opidsq = ds.putSQ(Tags.OtherPatientIDSeq);
        Dataset item = opidsq.addNewItem();
        item.putLO(Tags.PatientID, pid);
        item.putLO(Tags.IssuerOfPatientID, issuer);
    }

}
