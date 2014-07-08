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
 * Agfa-Gevaert Group.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
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

package org.dcm4chex.archive.hl7;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.dcm4che.util.Executer;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.SAXContentHandler;
import org.jboss.system.ServiceMBeanSupport;
import org.regenstrief.xhl7.HL7XMLReader;
import org.regenstrief.xhl7.HL7XMLWriter;
import org.regenstrief.xhl7.XMLWriter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Franz Willer <franz.willer@gmail.com>
 * @version $Revision: $ $Date: $
 * @since Jul 30, 2009
 */
public class HL7CmdService extends ServiceMBeanSupport implements NotificationListener {

    private static final String NONE = "NONE";
    private String cmdXslPath;
    private ObjectName hl7ServerName;
    private TemplatesDelegate templates = new TemplatesDelegate(this);
    
    public final String getCmdStylesheet() {
        return cmdXslPath == null ? NONE : cmdXslPath;
    }

    public void setCmdStylesheet(String path) {
        this.cmdXslPath = NONE.equals(path) ? null : path;
    }
    
    public final ObjectName getHL7ServerName() {
        return hl7ServerName;
    }

    public final void setHL7ServerName(ObjectName hl7ServerName) {
        this.hl7ServerName = hl7ServerName;
    } 

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }
    

    protected void startService() throws Exception {
        server.addNotificationListener(hl7ServerName, this,
                HL7ServerService.NOTIF_FILTER, null);
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(hl7ServerName, this,
                HL7ServerService.NOTIF_FILTER, null);
    }
    
    public void handleNotification(Notification notif, Object handback) {
        if ( cmdXslPath == null ) return;
        Object[] hl7msg = (Object[]) notif.getUserData();
        Document hl7doc = (Document) hl7msg[1];
        String cmd = null;
        try {
            File xslFile = FileUtils.toExistingFile(cmdXslPath);
            Transformer t = templates.getTemplates(xslFile).newTransformer();
            ByteArrayOutputStream out = new ByteArrayOutputStream(256);
            XMLWriter xmlWriter = new HL7XMLWriter( new OutputStreamWriter(out, "ISO-8859-1"));
            t.transform(new DocumentSource(hl7doc), new StreamResult(out));
            cmd = out.toString();
            log.info("CMD for HL7 message:"+cmd);
        } catch (TransformerException e) {
            log.error("Failed to transform HL7 message into command", e);
            return;
        } catch (FileNotFoundException e) {
            log.error("No such stylesheet: " + cmdXslPath);
            return;
        } catch (UnsupportedEncodingException e) {
            log.error("Encoding not supported!", e);
            return;
        } catch (Throwable t) {
            log.error("Uncaught Exception!", t);
            return;
        }
        try {
            if ( cmd == null || cmd.trim().length() == 0 || cmd.startsWith(NONE)) {
                if ( log.isDebugEnabled() ) {log.debug("No command for HL7 message! "+new MSH(hl7doc)); }
            } else {
                log.info("Perform command " + cmd);
                Executer ex = new Executer(cmd);
                int exit = -1;
                try {
                    exit = ex.waitFor();
                } catch (InterruptedException e) {
                }
                if (exit != 0) {
                    throw new IOException("Non-zero exit code(" + exit
                            + ") of " + cmd);
                }
            }
        } catch (Exception e) {
            log.error("Command " + cmd + " failed!", e);
        }            
    }
}
