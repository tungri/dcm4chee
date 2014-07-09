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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jboss.system.ServiceMBeanSupport;
import org.jboss.system.pm.XMLAttributePersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A MBean service to manage configurations of JMX services.
 * <p>
 * This service can be used to save and load jmx attributes for a configurable set of mbeans.
 * <p>
 * 
 * @author franz.willer@agfa.com
 * @version $Revision$ $Date$
 * @since 05.09.2007
 */
public class ConfigBackupService extends ServiceMBeanSupport {

    private String versionPrefix = "DCM4CHEE_";
    
    private static final String NEW_LINE = System.getProperty("line.separator", "\n");

    /**
     * Map defining services and their attributes to save.
     * key is service name (ObjectName format); value list of attribute names
     */
    private Map attributesToStore = new LinkedHashMap();
    
    private String serviceDomainPrefix;
	
    static Logger log = LoggerFactory.getLogger(ConfigBackupService.class);

    /**
     * Get the attributes that should be saved for a configuration.
     * <p>
     * <dl>
     * <dt>Format: &lt;MBean&gt;:&lt;attributeList&gt;</dt>
     * <dd>MBean: Either the full ObjectName or the service name when <code>serviceDomainPrefix</code> can be used.</dd>
     * <dd>attributeList:attribute names separated by ',' or '*' for all attributes of the MBean.
     * </dl>
     * @return
     */
    public String getAttributesToStore() {
	return toString(attributesToStore);
    }

    public void setAttributesToStore(String attributesToStore) {
        this.attributesToStore = toMap(attributesToStore.trim());
    }
    
    public String getVersionPrefix() {
        return versionPrefix;
    }

    public void setVersionPrefix(String versionPrefix) {
        this.versionPrefix = versionPrefix.trim();
    }

    /**
     * Get the default domain prefix.
     * <p>
     * This prefix allow the use of short service names in attributesToStore instead of the full qualified ObjectName.
     * <p>
     * e.g.:'dcm4chee.archive:service='
     * @return
     */
    public String getServiceDomainPrefix() {
	return serviceDomainPrefix;
    }

    public void setServiceDomainPrefix(String serviceDomainPrefix) {
	this.serviceDomainPrefix = serviceDomainPrefix.trim();
    }

    protected String toString(Map map) {
        if (map == null || map.isEmpty())
            return "";
        StringBuffer sb = new StringBuffer();
        Object key;
        List attrNames;
        for (Iterator iter = map.keySet().iterator(); iter.hasNext() ;) {
        	key = iter.next();
            sb.append(key).append(':');
            attrNames = (List) map.get(key);
            if ( attrNames == null || attrNames.size() == 0 ) {
            	sb.append('*');
            } else {
            	Iterator iter1 = attrNames.iterator();
            	sb.append( iter1.next() );
            	for ( ; iter1.hasNext() ; ) {
            		sb.append(',').append(iter1.next());
            	}
            }
            sb.append(NEW_LINE);
        }
        return sb.toString();
    }

    /**
     * Parse String to get map with service name as key and list of attributes as value.
     * <p/>
     * The key can be either a full qualified ObjectName (dcm4chee.xds:service=DocumentStoreService) or
     * a short name when service is within current serviceDomainPrefix.<br/>
     * e.g.: serviceDomainPrefix='dcm4chee.archive:service='
     * full ObjectName: 'dcm4chee.xds:service=DocumentStoreService:*' -&gt; key = 'dcm4chee.xds:service=DocumentStoreService'
     * short name: 'XDS-I:*' -&gt; key = 'XDS-I' -&gt; full ObjectName: 'dcm4chee.archive:service=XDS-I'
     */
    protected Map toMap( String s ) {
    	Map map = new LinkedHashMap();
    	int pos;
        String token,key,attrs;
        List attrList;
        for ( StringTokenizer st = new StringTokenizer(s, " \t\r\n;") ; st.hasMoreTokens() ; ) {
        	token = st.nextToken();
        	pos = token.lastIndexOf(':'); 
        	attrList = new ArrayList();
        	if ( pos != -1 ) {
	        	key = token.substring(0,pos);
	        	attrs = token.substring(++pos);
	        	if ( !"*".equals(attrs) ) {
	        		for ( StringTokenizer st1 = new StringTokenizer(attrs, ",") ; st1.hasMoreTokens() ; ) {
	        			attrList.add(st1.nextToken());
	        		}
        		}
        	} else {
        		key = token;
        	}
        	map.put(key, attrList);
        } 
        return map;
    }
    
    /**
     * Save attributes defined in 'attributesToStore' for given name.
     * <p>
     * Call Persistence Manager store method for each service defined in attributesToStore.
     * 
     * @param configName
     * @throws Exception
     */
    public String save(String configName) throws Exception {
    	XMLAttributePersistenceManager apm = new XMLAttributePersistenceManager();
    	apm.create(versionPrefix+configName, null);
    	String key;
    	ObjectName name;
    	List attrList;
        StringBuilder sb = new StringBuilder("Save configuration ")
        .append(configName).append(":\n");
    	int count = 0;
    	for ( Iterator iter = attributesToStore.keySet().iterator() ; iter.hasNext() ; ) {
    	    key = (String) iter.next();
    	    name = getObjectName(key);
    	    try {
        	attrList = (List) attributesToStore.get(key);
            	String[] attrNames = getAttributeNames(name, attrList);
            	AttributeList attributes = server.getAttributes(name, attrNames);
            	apm.store(name.getDomain()+"_"+name.getKeyPropertyListString(), attributes);
            	count++;
    	    } catch (Exception x) {
		log.debug("Failed to save configuration of "+name+" !", x);
		sb.append("WARNING: Attributes of ").append(name).append(" couldn't be saved!\n");
    	    }
    	}
    	if (count > 0)
    	    sb.append("Attributes of ").append(count).append(" services saved!");
    	return sb.toString();
    }

    /**
     * Load a configuration for given name.
     * 
     * @param configName
     * @throws Exception
     */
    public String load(String configName) throws Exception {
    	XMLAttributePersistenceManager apm = new XMLAttributePersistenceManager();
    	apm.create(versionPrefix+configName, null);
    	String key;
    	ObjectName name;
    	AttributeList attributes;
        StringBuilder sb = new StringBuilder("Load configuration ")
        .append(configName).append(":\n");
        int count = 0;
    	for ( Iterator iter = attributesToStore.keySet().iterator() ; iter.hasNext() ; ) {
    	    key = (String) iter.next();
    	    name = getObjectName(key);
            try {
                attributes = apm.load(name.getDomain()+"_"+name.getKeyPropertyListString());
                server.setAttributes(name, attributes);
                count++;
            } catch (Exception x) {
                log.debug("Failed to load configuration of "+name+" !", x);
                sb.append("WARNING: Attributes of ").append(name).append(" couldn't be updated!\n");
            }
    	}
        if (count > 0)
            sb.append("Attributes of ").append(count).append(" services loaded!");
        return sb.toString();
    }

    /**
     * Get the ObjectName for given String.
     * <p>
     * <code>key</code> can be either a full ObjectName or only the 'service' name.
     * In this case the serviceDomainPrefix is used to get a full qualified ObjectName String.
     * @param key
     * @return
     * @throws MalformedObjectNameException
     */
    private ObjectName getObjectName(String key)
			throws MalformedObjectNameException {
        ObjectName name;
        name = key.indexOf(':') == -1 ? new ObjectName(serviceDomainPrefix+key) : new ObjectName(key);
        return name;
    }
    
    /**
     * Get either the attribute names defined in attrList or 
     * all attribute names of the service specified with name.
     * 
     * @param name
     * @param attrList
     * @return
     * @throws Exception
     */
    private String[] getAttributeNames(ObjectName name, List attrList) throws Exception {
	String[] attrNames;
	if ( attrList != null && attrList.size() > 0) {
	    attrNames = (String[]) attrList.toArray(new String[attrList.size()]);
	} else {
	    MBeanAttributeInfo[] attrInfos = server.getMBeanInfo(name).getAttributes();
	    attrNames = new String[attrInfos.length];
	    for ( int i = 0 ; i < attrNames.length ; i++ ) {
	        attrNames[i] = attrInfos[i].getName();
	    }
	}
	return attrNames;
    }
    
}
