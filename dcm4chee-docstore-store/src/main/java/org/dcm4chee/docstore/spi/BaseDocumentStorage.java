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
package org.dcm4chee.docstore.spi;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.dcm4chee.docstore.Availability;
import org.dcm4chee.docstore.BaseDocument;
import org.dcm4chee.docstore.DocumentStorageListener;
import org.dcm4chee.docstore.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseDocumentStorage implements DocumentStorage {

    private String desc;
    private Set<Feature> features = new HashSet<Feature>();
    private String name;
    private HashSet<DocumentStorageListener> listeners = new HashSet<DocumentStorageListener>();

    private static Logger log = LoggerFactory.getLogger( BaseDocumentStorage.class );
    public BaseDocumentStorage() {
    }

    public BaseDocumentStorage(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return desc;
    }
    public void setDescription(String desc) {
        this.desc = desc;
    }

    public Set<Feature> getFeatures() {
        return features;
    }

    public void addFeature( Feature feature) {
        features.add(feature);
    }

    public boolean hasFeature(Feature feature) {
        return this.features.contains(feature);
    }

    public boolean matchFeatures(Set<Feature> features) {
        return this.features.containsAll(features);
    }

    public String getRetrieveURL(String docUid) {
        return null;
    }

    /**
     * 
     */
    public void init(String initString) {
    }

    public boolean setHash(BaseDocument doc, String hash) {
        log.debug("setHash(BaseDocument doc, String hash) not supported!");
        return false;
    }
    
    public boolean commitDocument(String docUid) {
        log.debug("commitDocument:"+docUid+" ignored (with success status true)!");
        return true;
    }

    public boolean deleteDocument(String docUid) {
        log.debug("deleteDocument:"+docUid+" ignored!");
        return false;
    }

    public String toString() {
        return getStorageType()+":"+name+"("+desc+")";
    }

    protected Properties readInitAsProperties(String initString) throws IOException{
        Properties p = new Properties();
        if ( initString != null ) {
            StringTokenizer st = new StringTokenizer(initString);
            String s;
            int pos;
            while ( st.hasMoreTokens()) {
                s = st.nextToken().trim();
                pos = s.indexOf('=');
                if ( pos != -1) {
                    p.setProperty(s.substring(0,pos), s.substring(++pos));
                }
            }
        }
        return p;
    }

    public boolean addStorageListener(DocumentStorageListener listener) {
        return listeners.add(listener);
    }
    
    protected int getNumberOfListeners(){
        return listeners.size();
    }

    protected void notifyStored(BaseDocument doc) {
        for ( DocumentStorageListener l : listeners ) {
            l.documentStored(doc);
        }
    }
    protected void notifyCreated(BaseDocument doc) {
        for ( DocumentStorageListener l : listeners ) {
            l.documentCreated(doc);
        }
    }
    protected void notifyCommitted(BaseDocument doc) {
        for ( DocumentStorageListener l : listeners ) {
            l.documentCommitted(doc);
        }
    }
    protected void notifyDeleted(BaseDocument doc) {
        log.debug("notifyDeleted:"+doc+"\nlisteners"+listeners);
        for ( DocumentStorageListener l : listeners ) {
            l.documentDeleted(doc);
        }
    }
    protected void notifyRetrieved(BaseDocument doc) {
        for ( DocumentStorageListener l : listeners ) {
            l.documentRetrieved(doc);        }
    }
    protected void notifyAvailabilityChanged(Availability oldAvail, Availability newAvail) {
        for ( DocumentStorageListener l : listeners ) {
            l.storageAvailabilityChanged(this, oldAvail, newAvail);        }
    }
}
