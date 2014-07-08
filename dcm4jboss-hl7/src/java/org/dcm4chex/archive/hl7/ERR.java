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
 * Portions created by the Initial Developer are Copyright (C) 2009
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
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

import org.dom4j.Document;
import org.dom4j.Element;
import org.regenstrief.xhl7.HL7XMLLiterate;


public class ERR {
    public static final String UNKNOWN_KEY_IDENTIFIER = "204";
    
    public final String HL7ErrorCode;
    public final String severity;
    
    public ERR(Document msg) {
        Element err = msg.getRootElement().element("ERR");
        if (err == null) 
                throw new IllegalArgumentException("Missing ERR Segment");
        List<Element> errfds = err.elements(HL7XMLLiterate.TAG_FIELD);
        this.HL7ErrorCode = ACK.fieldAt(errfds,2);
        this.severity     = ACK.fieldAt(errfds,3);
    }
    
    public boolean isUnknownKeyIdentifier() {
        return UNKNOWN_KEY_IDENTIFIER.equals(HL7ErrorCode.trim());
    }
    
    public String toString() {
        return "ERR[HL7ErrorCode=" + HL7ErrorCode
               + ", severity=" + severity
               + "]";
    }
}
