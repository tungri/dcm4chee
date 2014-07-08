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

package org.dcm4chex.archive.mbean;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.rmi.RemoteException;
import java.util.Collection;

import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.AssociationFactory;
import org.dcm4chex.archive.dcm.AbstractScuService;
import org.dcm4chex.archive.ejb.interfaces.AEDTO;

/**
 * <description>
 * 
 * @author <a href="mailto:franz.willer@gwi-ag.com">Franz WIller</a>
 * @since March 24, 2005
 * @version $Revision: 14692 $ $Date: 2011-01-13 13:27:38 +0000 (Thu, 13 Jan 2011) $
 */
public class EchoService extends AbstractScuService {

    private static final int PCID_ECHO = 1;


    public String[] echoAll() throws RemoteException, Exception {
        Collection<AEDTO> aes = aeMgt().findAll();
        String[] sa = new String[aes.size()];
        int i = 0;
        for (AEDTO ae : aes) {
            try {
                sa[i] = ae + " : " + echo(ae, new Integer(3));
            } catch (Exception x) {
                sa[i] = ae + " failed:" + x.getMessage();
            }
            i++;
        }
        return sa;
    }

    public String echo(String aet) throws Exception {
        return echo(aet, 1);
    }
    public String echo(String aet, Integer nrOfTests) {
        try {
            return echo(aeMgt().findByAET(aet), nrOfTests);
        } catch (Throwable e) {
            return "Echo failed:"+e.getMessage();
        }
    }

    public String echo(AEDTO aeData, Integer nrOfTests) throws InterruptedException, IOException  {
        StringWriter swr = new StringWriter(nrOfTests*20+50);
        StringBuffer echoResult = swr.getBuffer();
        echoResult.append("DICOM Echo to ").append(aeData).append(":\n");
       try {
             long t0 = System.currentTimeMillis();
            ActiveAssociation aa = openAssociation(aeData, UIDs.Verification);
            try {
                long t1 = System.currentTimeMillis();
                echoResult.append("Open Association in ").append(t1-t0).append(" ms.\n");
                echo(aa, nrOfTests.intValue(), nrOfTests > 1 ? echoResult : null);
                echoResult.append("Total time for successfully echo ").append(aeData.getTitle());
                if ( nrOfTests > 1 ) {
                    echoResult.append(' ').append(nrOfTests).append(" times");
                }
                echoResult.append(": ").append(System.currentTimeMillis()-t0).append(" ms!");
            } finally {
                try {
                    aa.release(true);
                } catch (Exception e) {
                    log.warn("Failed to release " + aa.getAssociation());
                }
            }
        } catch (Throwable e) {
            log.error("Echo " + aeData + " failed", e);
            echoResult.append("Echo failed! Reason: ").append(e.getMessage());
            e.printStackTrace(new PrintWriter(swr));//write to echoResult
        }
        return echoResult.toString();
    }
    
    private void echo(ActiveAssociation aa, int nrOfTests, StringBuffer echoResult)
             throws InterruptedException, IOException {
        AssociationFactory aFact = AssociationFactory.getInstance();
        DcmObjectFactory oFact = DcmObjectFactory.getInstance();
        long t0, t1, diff;
        int nrOfLT1ms = 0;
        for (int i = 0; i < nrOfTests; i++) {
            t0 = System.currentTimeMillis();
            aa.invoke(aFact.newDimse(PCID_ECHO, 
                    oFact.newCommand().initCEchoRQ(i)), null);
            if ( echoResult != null ) {
                t1 = System.currentTimeMillis();
                diff = t1 - t0;
                if (diff < 1) {
                    nrOfLT1ms++;
                } else {
                    if (nrOfLT1ms > 0) {
                        echoResult.append(nrOfLT1ms).append(" Echoes, each done in less than 1 ms!\n");
                        nrOfLT1ms = 0;
                    }
                    echoResult.append("Echo done in ").append(System.currentTimeMillis() - t0).append(" ms!\n");
                }
            }
        }
        if (nrOfLT1ms > 0)
            echoResult.append(nrOfLT1ms).append(" Echoes, each done in less than 1 ms!\n");
    }

    public String echo(String title, String host, int port, String cipherSuites, int nrOfTests) 
            throws InterruptedException, IOException {
        AEDTO ae = new AEDTO();
        ae.setTitle(title);
        ae.setHostName(host);
        ae.setPort(port);
        ae.setCipherSuitesAsString(cipherSuites);
        return echo(ae,nrOfTests);
    }

    public boolean checkEcho(String title, String host, int port, 
            String cipherSuites) {
        AEDTO ae = new AEDTO();
        ae.setTitle(title);
        ae.setHostName(host);
        ae.setPort(port);
        ae.setCipherSuitesAsString(cipherSuites);
        return checkEcho(ae);
    }
    
    public boolean checkEcho(AEDTO aeData) {
        try {
            ActiveAssociation aa = openAssociation(aeData, UIDs.Verification);
            try {
                echo(aa, 1, null);
                return true;
            } finally {
                try {
                    aa.release(true);
                } catch (Exception e) {
                    log.warn("Failed to release " + aa.getAssociation());
                }
            }
        } catch (Exception e) {
            log.error("Echo " + aeData + " failed", e);
            return false;        
        }
    }
}
