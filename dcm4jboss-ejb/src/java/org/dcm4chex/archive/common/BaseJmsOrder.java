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
 * Portions created by the Initial Developer are Copyright (C) 2005
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

package org.dcm4chex.archive.common;

import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;

/**
 * This is abstract JMS order message that provides failure counting feature
 * 
 * @author fang.yang@agfa.com
 * @version $Revision: 17576 $ $Date: 2013-01-08 19:00:52 +0000 (Tue, 08 Jan 2013) $
 * @since April 4, 2006
 */
public abstract class BaseJmsOrder implements Serializable {

    private static final long serialVersionUID = -2427617218391383019L;
    protected static long counter = 0;
    private String id;
    private transient String originalIdString;
    private int failureCount = 0;
    private Throwable throwable = null;  // Remember last exception happened
    private String origQueueName = null; // The original queue
    private Properties orderProperties = null;
    public static final char PROPERTY_DELIMITER = ';';
    public static final char PROPERTY_DELIMITER_ESCAPE = '\\';

    /**
     * Imposing a reasonable limit on the amount of information that is included in the 
     * context specific property collection. Note that a hard-limit of 65535 bytes exist
     * due to the internal use of {@link DataOutput#writeUTF(String)} when messages are
     * created. A character will be written using between one and three bytes depending
     * on the character. This means that a safe hard-limit value for MAX_PROPERTIES_SIZE
     * could be 21845 (65535 / 3) if desired.
     */
    private final int MAX_PROPERTIES_SIZE = 1000;
    private int propertiesSize = 0;
    
    public BaseJmsOrder()
    {
        id = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()) + counter++;
        orderProperties = new Properties();
    }
    
    public final int getFailureCount() {
        return failureCount;
    }

    public final void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
    	this.throwable = throwable;
    }
    
    public String toIdString()
    {
    	if ( originalIdString == null ) {
    		originalIdString = getClass().getName() + "@" + id + "@" + failureCount;
    	}
    	return originalIdString;
    }

    protected String getOrderDetails() { return ""; };

    /**
     * Copies all context specific JMS Order properties or attributes to the supplied
     * {@code Properties} instance.
     */
    public void copyOrderPropertiesTo(Properties p) {
        if ( p != null )
        {
            p.putAll(orderProperties);
        }
    }
    
    /**
     * Allows each derived type to implement a strategy for processing context
     * specific properties or attributes that have relevant meaning to this JMS order.
     * The default behaviour provided by this base class is to process noting. 
     * @param properties generic set of (@code Object}s to be processed by a derived class. 
     */
    public void processOrderProperties(Object... properties) {};
    
    /**
     * Context specific properties or attributes that have relevant meaning to this
     * JMS order. The property is added if both the name and property supplied are
     * not null.
     * @param name the name to be placed into this property list
     * @param property the property corresponding to name
     * @return true if the entry was added, false otherwise
     */
    public boolean setOrderProperty(String name, String property) {
        if ( propertiesSize >= MAX_PROPERTIES_SIZE ) return false;
        
        final String propDelim = String.valueOf(PROPERTY_DELIMITER);
        final String propEscape = String.valueOf(PROPERTY_DELIMITER_ESCAPE);
        
        if ( name != null && property != null ) {
            // storing with delimiter surrounded values is a strategy that allows us to optimise the
            // message selector that finds an *exact* matching string within a larger string
            String delimitedProperty = propDelim + property.replace(propDelim, 
                    propEscape + propDelim) + propDelim;
            
            int size = name.length() + delimitedProperty.length();
            
            if ( propertiesSize + size >= MAX_PROPERTIES_SIZE ) {
                return false;
            }
            
            propertiesSize += size;
            
            orderProperties.setProperty(name, delimitedProperty);
            return true;
        }
        return false;
    }
    
    /**
     * Context specific properties or attributes that have relevant meaning to this
     * JMS order. The properties are added if both the name and properties supplied are
     * not null and the properties varargs is not empty. Properties are concatenated
     * using the ';' character. Any property that contains the ';' character, will be
     * modified to have that character escaped with the '\' character, so "My;Value"
     * will become "My\;Value".
     * @param name the name to be placed into this property list
     * @param property the array of properties corresponding to name
     * @return true if the entry was added, false otherwise
     */
    public boolean setOrderMultiProperty(String name, String... properties) {
        if ( propertiesSize >= MAX_PROPERTIES_SIZE ) return false;
        
        final String propDelim = String.valueOf(PROPERTY_DELIMITER);
        final String propEscape = String.valueOf(PROPERTY_DELIMITER_ESCAPE);
        
        if ( name != null && properties != null && properties.length > 0 ) {
            int combinedPropertiesSize = name.length() + 1; // 1 for initial delimiter
            
            StringBuffer sb = new StringBuffer();
            sb.append(PROPERTY_DELIMITER);
            for ( String property : properties ) {
                String delimitedProperty = property.replace(propDelim, 
                        propEscape + propDelim) + propDelim;
                
                combinedPropertiesSize += delimitedProperty.length();
                if ( propertiesSize + combinedPropertiesSize >= MAX_PROPERTIES_SIZE ) {
                    break;
                }
                
                sb.append(delimitedProperty);
            }
            
            propertiesSize += combinedPropertiesSize;
            
            // storing with delimiter surrounded values is a strategy that allows us to optimise the
            // message selector that finds an *exact* matching string within a larger string
            orderProperties.setProperty(name, sb.toString());
            return true;
        }
        return false;
    }
    
    /**
     * Set the original queue name, only the first time
     * 
     * @param queueName
     */
    public void setQueueName(String queueName) {		
        if(origQueueName == null)
            origQueueName = queueName;
    }

    public String getQueueName()
    {
        return origQueueName;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(toIdString() + "[");

        String orderDetails = getOrderDetails();
        if ( orderDetails.length() > 0) {
           sb.append(orderDetails);
        }
        sb.append(", failures=").append(failureCount);
        sb.append("]");

        return sb.toString();
    }

    public String toLongString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("\tInternal ID: ").append(toIdString()).append("\n");
        String orderDetails = getOrderDetails();
        if ( orderDetails.length() > 0) {
           sb.append("\tDetails: ").append(orderDetails).append("\n");
        }
        sb.append("\tOriginal queue name: ").append(origQueueName).append("\n");
        if ( orderProperties.size() > 0 ) {
            sb.append("\tOrder properties: ");
            for ( Enumeration<?> names = orderProperties.propertyNames(); names.hasMoreElements(); ) {
                String key = (String) names.nextElement();
                String property = orderProperties.getProperty(key);
                property = property.substring(1, property.length() - 1); // strip outer delimiters
                sb.append(key).append("=").append(property).append(", ");
            }
            sb.setCharAt(sb.length() - 2, '\n');
        }
        sb.append("\tFailure count: ").append(failureCount).append("\n");
        if(throwable != null)
        {
            StringWriter sw = new StringWriter(); 
            throwable.printStackTrace( new PrintWriter( sw ) ); 
            sb.append("\tException caught: ").append(sw.toString()).append("\n");
        }
        return sb.toString();
    }

    /*
     * Provide for deserialization of objects persisted against an earlier class definition.
     */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		if ( orderProperties == null ) {
			orderProperties = new Properties();
		}
	}
}
