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
 * Portions created by the Initial Developer are Copyright (C) 2006-2008
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
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
package org.dcm4chex.archive.ejb.session;

import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Feb 24, 2009
 * 
 * @ejb.bean name="AvailabilityUpdate" type="Stateless" view-type="remote"
 *           jndi-name="ejb/AvailabilityUpdate"
 * @ejb.transaction-type type="Container"
 * @ejb.transaction type="Required"
 * 
 * @ejb.ejb-ref ejb-name="Instance" ref-name="ejb/Instance" view-type="local"
 */
public abstract class AvailabilityUpdateBean implements SessionBean {

    private InstanceLocalHome instHome;

    public void setSessionContext(SessionContext ctx) {
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            this.instHome = (InstanceLocalHome) jndiCtx
                .lookup("java:comp/env/ejb/Instance");
        } catch (NamingException e) {
            throw new EJBException(e);
        } finally {
            if (jndiCtx != null) {
                try {
                    jndiCtx.close();
                } catch (NamingException ignore) {
                }
            }
        }
    }

    public void unsetSessionContext() {
        instHome = null;
     }

     /**
      * @ejb.interface-method
      */
     public int updateAvailability(Dataset ian) {
         int count = 0;
         StudyLocal[] study = new StudyLocal[1];
         DcmElement refSeriesSq = ian.get(Tags.RefSeriesSeq);
         for (int i = 0, nS = refSeriesSq.countItems(); i < nS; i++) {
             count +=  updateAvailabilityOfSeries(refSeriesSq.getItem(i), study);
         }
         if (study[0] != null) {
             study[0].updateAvailability();
         }
         return count;
     }

    private int updateAvailabilityOfSeries(Dataset refSeries,
            StudyLocal[] study) {
        int count = 0;
        SeriesLocal[] series = new SeriesLocal[1];
        DcmElement refSOPSq = refSeries.get(Tags.RefSOPSeq);
        for (int i = 0, nI = refSOPSq.countItems(); i < nI; i++) {
            count +=  updateAvailabilityOfInstance(refSOPSq.getItem(i), series);
        }
        if (series[0] != null) {
            if (series[0].updateAvailability() && study[0] == null) {
                study[0] = series[0].getStudy();
            }
        }
        return count;
     }

    private int updateAvailabilityOfInstance(Dataset refSop,
            SeriesLocal[] series) {
        InstanceLocal inst;
        try {
            inst = instHome.findBySopIuid(
                    refSop.getString(Tags.RefSOPInstanceUID));
        } catch (ObjectNotFoundException e) {
            return 0;
        } catch (FinderException e) {
            throw new EJBException(e);
        }

        if (!refSop.getString(Tags.RetrieveAET)
                .equals(inst.getExternalRetrieveAET())) {
            return 0;
        }

        if (!inst.updateAvailability(Availability.toInt(
                refSop.getString(Tags.InstanceAvailability)))) {
            return 0;
        }

        if (series[0] == null) {
            series[0] = inst.getSeries();
        }
        return 1;
    }

}
