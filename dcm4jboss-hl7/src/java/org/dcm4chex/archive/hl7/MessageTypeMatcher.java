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
package org.dcm4chex.archive.hl7;

import java.util.List;
import java.util.StringTokenizer;

import org.dom4j.Document;
import org.dom4j.Element;
import org.regenstrief.xhl7.HL7XMLLiterate;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Mar 18, 2009
 */
class MessageTypeMatcher {

    private String messageType;
    private String triggerEvent;
    private String[] segment;
    private int[] field;
    private String[] value;

    public MessageTypeMatcher(String pattern) {
        int messageTypeEnd = pattern.indexOf('^');
        if (messageTypeEnd < 0) {
            throw new IllegalArgumentException(pattern);
        }
        messageType = pattern.substring(0, messageTypeEnd);
        int triggerEventEnd = pattern.indexOf('[', messageTypeEnd+1);
        if (triggerEventEnd == -1) {
            triggerEvent = pattern.substring(messageTypeEnd+1);
            segment = null;
            field = null;
            value = null;
        } else {
            int valueEnd = pattern.length()-1;
            if (pattern.charAt(valueEnd) != ']') {
                throw new IllegalArgumentException(pattern);
            }
            triggerEvent = pattern.substring(messageTypeEnd+1,
                    triggerEventEnd);
            StringTokenizer st =  new StringTokenizer(pattern.substring(triggerEventEnd+1, valueEnd), "|");
            int nrOfConditions = st.countTokens();
            segment = new String[nrOfConditions];
            field = new int[nrOfConditions];
            value = new String[nrOfConditions];
            String cond;
            int fieldEnd;
            for( int i = 0 ;st.hasMoreTokens(); i++) {
                cond = st.nextToken();
                if (cond.length() < 7) {
                    throw new IllegalArgumentException(pattern);
                }
                if ( cond.charAt(3) != '-') {
                    throw new IllegalArgumentException(pattern);                    
                }
                fieldEnd = cond.indexOf('=');
                if (fieldEnd == -1) {
                    throw new IllegalArgumentException(pattern);                    
                }
                segment[i] = cond.substring(0,3); 
                try {
                    field[i] = Integer.parseInt(
                            cond.substring(4, fieldEnd));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(pattern);
                }
                if (field[i] <= 0) {
                    throw new IllegalArgumentException(pattern);
                }
                value[i] = cond.substring(fieldEnd+1);
            }
        }
    }

    public StringBuffer toString(StringBuffer sb) {
        sb.append(messageType).append('^').append(triggerEvent);
        if (segment != null) {
            sb.append('[');
            for ( int i = 0, len = segment.length ; i < len ; i++ ) {
                sb.append(segment[i]).append('-').append(field[i])
                    .append('=').append(value[i]).append('|');
            }
            sb.setLength(sb.length()-1);
            sb.append(']');
        }
        return sb;
    }

    public String toString() {
        return toString(new StringBuffer()).toString();
    }

    public boolean match(MSH msh, Document msg) {
        if (!messageType.equals(msh.messageType)
                || !triggerEvent.equals(msh.triggerEvent)) {
            return false;
        }
        if (segment == null) {
            return true;
        }
        for ( int i = 0, len = segment.length ; i < len ; i++) {
            Element seg = msg.getRootElement().element(segment[i]);
            if (seg == null) {
                return false;
            }
            List<Element> fds = seg.elements(HL7XMLLiterate.TAG_FIELD);
            if (fds.size() <= field[i]) {
                return false;
            }
            Element fd = fds.get(field[i]-1);
            if (!value[i].equals(maskNull(fd.getText())) ) {
                return false;
            }
        }
        return true;
    }

    private static String maskNull(String s) {
        return s != null ? s : "";
    }
}
