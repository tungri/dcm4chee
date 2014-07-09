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

import java.rmi.RemoteException;

import javax.ejb.CreateException;
import javax.ejb.FinderException;

import org.apache.log4j.Logger;
import org.dcm4chex.archive.ejb.interfaces.FixPatientAttributes;
import org.dcm4chex.archive.ejb.interfaces.FixPatientAttributesHome;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author franz.willer@gwi-ag.com
 * @version $Revision: 2010 $ $Date: 2005-10-06 19:55:27 +0000 (Thu, 06 Oct 2005) $
 * @since 35.03.2005
 *
 */
public class FixPatientAttributesService extends ServiceMBeanSupport {

     private int limitNumberOfPatientsPerTask;

    private static final Logger log = Logger.getLogger(FixPatientAttributesService.class);




    public int getLimitNumberOfPatientsPerTask() {
        return limitNumberOfPatientsPerTask;
    }

    public void setLimitNumberOfPatientsPerTask(int limit) {
        this.limitNumberOfPatientsPerTask = limit;
    }

  
    public int check() throws RemoteException, FinderException, CreateException {
    	return checkPatientAttributes(false);
    }
    public int repair() throws RemoteException, FinderException, CreateException {
     	return checkPatientAttributes(true);
    }
    
    private int checkPatientAttributes(boolean doUpdate) throws RemoteException, FinderException {
    	FixPatientAttributes checker = newFixPatientAttributes();
    	int offset = 0, total = 0;
    	int[] fixed;
		do {
			fixed = checker.checkPatientAttributes(offset,limitNumberOfPatientsPerTask, doUpdate);
			total += fixed[0];
    		offset += limitNumberOfPatientsPerTask;
		} while (fixed[1] == limitNumberOfPatientsPerTask);
    	return total;
    }
    

    private FixPatientAttributes newFixPatientAttributes() {
        try {
        	FixPatientAttributesHome home = (FixPatientAttributesHome) EJBHomeFactory
                    .getFactory().lookup(FixPatientAttributesHome.class,
                    		FixPatientAttributesHome.JNDI_NAME);
            return home.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to access FixPatientAttributes EJB:",
                    e);
        }
    }
    
}