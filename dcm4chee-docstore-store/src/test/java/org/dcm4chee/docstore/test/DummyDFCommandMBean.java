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
package org.dcm4chee.docstore.test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;

import org.jboss.system.ServiceMBeanSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyDFCommandMBean implements DynamicMBean {

    private static Logger log = LoggerFactory.getLogger( DummyDFCommandMBean.class );
    
    private static HashMap<String, Long> freeMap;
    
    public static void setFreeSpace(String f, long free) {
        if ( freeMap == null )
            freeMap = new HashMap<String, Long>(10);
        freeMap.put(f, new Long(free));
    }
    public static void clearFreeSpace() {
        freeMap.clear();
        freeMap = null;
    }
    
    public long freeSpace(String path) throws IOException {
        if ( freeMap == null) return 100000000l;
        Long l = freeMap.get(path);
        if ( l == null ) {
            File f = new File(path);
            l = freeMap.get(f.getName());
        }
        log.debug("******* freeSpace: "+l+" for "+path);
        return l == null ? -1l : l.longValue();
    }

    public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException,
            ReflectionException {
        return null;
    }

    public AttributeList getAttributes(String[] attributes) {
        return null;
    }

    public MBeanInfo getMBeanInfo() {
        MBeanOperationInfo[] ops = null;
        try {
            MBeanOperationInfo op = new MBeanOperationInfo("", this.getClass().getMethod("freeSpace", String.class));
            ops = new MBeanOperationInfo[]{op};
        } catch (Exception e) {
            log.error("Cant create MBeanOperationInfo for freeSpace");
        }
        MBeanInfo info = new MBeanInfo(getClass().getName(), "Dummy DFCommand for unit testing", null, null, ops, null);
        return info;
    }

    public Object invoke(String actionName, Object[] params, String[] signature)
            throws MBeanException, ReflectionException {
        try {
            return new Long(freeSpace((String) params[0]));
        } catch (IOException e) {
            return null;
        }
    }

    public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, InvalidAttributeValueException,
            MBeanException, ReflectionException {
    }

    public AttributeList setAttributes(AttributeList attributes) {
        return null;
    }

}
