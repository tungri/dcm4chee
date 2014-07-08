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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.ObjectName;

import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.ejb.interfaces.PIXQuery;
import org.dcm4chex.archive.ejb.interfaces.PIXQueryHome;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.jboss.system.ServiceMBeanSupport;

public class PIXQueryService extends ServiceMBeanSupport {

    private ObjectName hl7SendServiceName;
    private String pixQueryName;
    private String pixManager;
    private List<String[]> mockResponse;
    private List<String> issuersOfOnlyOtherPatientIDs;
    private List<String> issuersOfOnlyPrimaryPatientIDs;

    public final String getIssuersOfOnlyPrimaryPatientIDs() {
        return toString(issuersOfOnlyPrimaryPatientIDs);
    }

    public final void setIssuersOfOnlyPrimaryPatientIDs(String s) {
        this.issuersOfOnlyPrimaryPatientIDs = toList(s);
    }

    public final String getIssuersOfOnlyOtherPatientIDs() {
        return toString(issuersOfOnlyOtherPatientIDs);
    }

    public final void setIssuersOfOnlyOtherPatientIDs(String s) {
        issuersOfOnlyOtherPatientIDs = toList(s);
    }

    private String toString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "-";
        }
        Iterator<String> iter = list.iterator();
        StringBuffer sb = new StringBuffer(iter.next());
        while (iter.hasNext()) {
            sb.append(',').append(iter.next());
        }
        return sb.toString();
    }

    private List<String> toList(String s) {
        if (s.trim().equals("-")) {
            return null;
        }
        String[] a = StringUtils.split(s, ',');
        ArrayList<String> list = new ArrayList<String>(a.length);
        for (int i = 0; i < a.length; i++) {
            list.add(a[i].trim());
        }
        return list;
    }

    public final ObjectName getHL7SendServiceName() {
        return hl7SendServiceName;
    }

    public final void setHL7SendServiceName(ObjectName name) {
        this.hl7SendServiceName = name;
    }

    public final String getPIXManager() {
        return pixManager;
    }

    public final void setPIXManager(String pixManager) {
        this.pixManager = pixManager;
    }
    
    public final boolean isPIXManagerLocal() {
        return "LOCAL".equalsIgnoreCase(pixManager);
    }

    public final String getPIXQueryName() {
        return pixQueryName;
    }

    public final void setPIXQueryName(String pixQueryName) {
        this.pixQueryName = pixQueryName;
    }

    public final String getMockResponse() {
        return mockResponse == null ? "-" : pids2cx(mockResponse);
    }
    
    protected List<String[]> getMockResponseInternal() {
        return mockResponse;
    }

    private String pids2cx(List<String[]> pids) {
        StringBuffer sb = new StringBuffer();
        for (Iterator<String[]> iter = pids.iterator(); iter.hasNext();) {
            if (sb.length() > 0) {
                sb.append('~');
            }
            String[] pid = iter.next();
            sb.append(pid[0]).append("^^^").append(pid[1]);
            for (int i = 2; i < pid.length; i++) {
                sb.append('&').append(pid[i]);                
            }
        }
        return sb.toString();
    }

    public final void setMockResponse(String mockResponse) {
        String trim = mockResponse.trim();
        this.mockResponse = "-".equals(trim) ? null : cx2pids(trim);
    }

    private List<String[]> cx2pids(String s) {
        String[] cx = StringUtils.split(s, '~');
        List<String[]> l = new ArrayList<String[]>(cx.length);
        for (int i = 0; i < cx.length; i++) {
            String[] comps = StringUtils.split(s, '^');
            String[] subcomps = StringUtils.split(comps[3], '&');
            String[] pid = new String[1 + subcomps.length];
            pid[0] = comps[0];
            System.arraycopy(subcomps, 0, pid, 1, subcomps.length);
            l.add(pid);
        }
        return l;
    }

    public String showCorrespondingPIDs(String patientID, String issuer) {
        try {
            return pids2cx(queryCorrespondingPIDs(patientID, issuer, null));
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public List<String[]> queryCorrespondingPIDs(String patientID, String issuer,
            String[] domains) throws Exception {
        if (mockResponse != null) {
            return mockResponse;
        }
        if (isPIXManagerLocal()) {
        	return performLocalPIXQuery(patientID, issuer, domains);
        } else {
        	return performRemotePIXQuery(patientID, issuer, domains);
        }
    }

    /**
     * Initiates a local PIX query.
     * 
     * @param patientID
     *   The patient ID to use in the PIX query.  Can contain wildcards.
     * @param issuer
     *   The issuer to use in the PIX query.
     * @param domains
     *   An array of domains used to constrain the results.  If null, use all known domains.
     * @return
     *   If nothing is found, returns an empty list.  Otherwise, returns a list of 
     *   primary patient IDs/issuers (as stored in the patient table) that match
     *   the query criteria and belong in the domains that were passed in.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    protected List<String[]> performLocalPIXQuery(String patientID, String issuer,
            String[] domains) throws Exception {
             if (issuersOfOnlyPrimaryPatientIDs.contains(issuer)) {
                return pixQuery().queryCorrespondingPIDsByPrimaryPatientID(
                        patientID, issuer, domains);
            } else if (issuersOfOnlyOtherPatientIDs.contains(issuer)) {
                return pixQuery().queryCorrespondingPIDsByOtherPatientID(
                        patientID, issuer, domains);
            } else {
                return pixQuery().queryCorrespondingPIDs(
                        patientID, issuer, domains);
            }           
        }
    
    /**
     * Initiates a remote PIX query.
     * 
     * @param patientID
     *   The patient ID to use in the PIX query.  Cannot contain wildcards.
     * @param issuer
     *   The issuer to use in the PIX query.
     * @param domains
     *   An array of domains used to constrain the results.  If null, use all known domains.
     * @return 
     *   If the ID/issuer are unknown to the PIX manager, returns null.  
     *   Otherwise, returns the linkage set (including the query constraints if issuer matches one of the domains).
     * 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    protected List<String[]> performRemotePIXQuery(String patientID, String issuer,
            String[] domains) throws Exception {
    	if (isPIXManagerLocal()) {
    		throw new Exception("No remote PIX manager has been configured");
    	}
        List<String[]> res = (List<String[]>)server.invoke(hl7SendServiceName, "sendQBP_Q23",
                new Object[] {
                    pixManager,
                    pixQueryName,
                    patientID,
                    issuer,
                    domains  },
                new String[] {
                    String.class.getName(),
                    String.class.getName(),
                    String.class.getName(),
                    String.class.getName(),
                    String[].class.getName()
        });
        if( res==null ) {
            return res;
        }
        boolean addOriginal = true;
        for(String[] pid:res) {
            if(pid[0].equals(patientID)&&pid[1].equals(issuer)) {
                addOriginal = false;
                break;
            }
        }
        if(addOriginal && domains != null ) {
            addOriginal = false;
            for(String domain:domains) {
               if(domain.equals(issuer)) {
                   addOriginal = true;
                   break;
               }
           }
        }
        if(addOriginal) {    
            List<String[]> prv = res;
            res=new ArrayList<String[]>();
            res.add(new String[]{patientID, issuer});
            res.addAll(prv);
        }
        return res; 
    }

    protected PIXQuery pixQuery() throws Exception {
        return ((PIXQueryHome) EJBHomeFactory.getFactory().lookup(
                PIXQueryHome.class, PIXQueryHome.JNDI_NAME)).create();
    }
}
