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
package org.dcm4chee.docstore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.imageio.spi.ServiceRegistry;
import javax.xml.parsers.SAXParserFactory;

import org.dcm4chee.docstore.spi.DocumentStorage;
import org.dcm4chee.docstore.spi.DocumentStorageProviderSPI;
import org.jboss.system.server.ServerConfigLocator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DocumentStorageRegistry extends DefaultHandler {

    private static final String CONFIG_URL_PATH = "dcm4chee-docstore/dcm4chee-docstore-cfg.xml";

    private static final String CONFIG_URL = "resource:"+CONFIG_URL_PATH;

    private static final String DEFAULT_INITIAL_CONFIG_URL = "resource:conf/dcm4chee-docstore/dcm4chee-docstore-cfg.xml";

    private Map<String, DocumentStorageProviderSPI> docStorageProviders = new HashMap<String, DocumentStorageProviderSPI>();

    private Map<String,Map<String,DocumentStorage>> domainStores = new HashMap<String, Map<String,DocumentStorage>>();
    private Map<String,Set<DocumentStorage>> storePools = new HashMap<String,Set<DocumentStorage>>();

    private String curDomain;
    private Map<String,DocumentStorage> curDomainStores;
    private String storeType;
    private String name;
    private String desc;
    private DocumentStorageProviderSPI provider;
    private DocumentStorage curStore;
    private StringBuilder initStringSB;

    private static Logger log = LoggerFactory.getLogger( DocumentStorageRegistry.class );

    public DocumentStorageRegistry() {
        init();
    }

    @SuppressWarnings("unchecked")
    public void init() {
        Iterator iter = ServiceRegistry.lookupProviders(DocumentStorageProviderSPI.class);
        DocumentStorageProviderSPI sp;
        while (iter.hasNext()) {
            sp = (DocumentStorageProviderSPI)iter.next();
            docStorageProviders.put(sp.getStorageType(), sp);
        }
        log.debug("docStorageProviders:"+docStorageProviders);
    }

    public DocumentStorage getDocumentStorageOfType(String type, String name) {
        DocumentStorageProviderSPI sp = docStorageProviders.get(type);
        return sp==null ? null : sp.getDocumentStorage(name);
    }

    public Set<String> getAllDocumentStorageProviderNames() {
        return docStorageProviders.keySet();
    }

    /**
     * Get DocumentStorage for given domain and name.
     * <p/>
     * If name is null the first  storage for the domain is returned.
     * 
     * @param domain
     * @param name
     * @return
     */
    public DocumentStorage getDocumentStorage(String domain, String name) {
        Map<String,DocumentStorage> map = this.domainStores.get(domain);
        if ( map == null ) return null;
        if ( name == null) {
            return map.size() > 0 ? map.values().iterator().next() : null;
        } else {
            return map.get(name);
        }
    }
    public Set<String> getDocumentStorageDomains() {
        return domainStores.keySet();
    }

    public Collection<DocumentStorage> getDocumentStorages(String domain) {
        Map<String, DocumentStorage> map = this.domainStores.get(domain);
        return map == null ? null : map.values();
    }

    /**
     * Get DocumentStorage that match with given feature list sorted by domain.
     * <p/>
     * 
     * @param features	List of features that must be supported by a DocumentStorage.
     * @return Map with domain name as key and Set of DomainStorage as value. <br/>Is empty when no DocumentStorage supports features.
     */
    public Map<String,Set<DocumentStorage>> getDocumentStorages(Set<Feature> features) {
        Map<String,Set<DocumentStorage>> map = new HashMap<String,Set<DocumentStorage>>();
        if ( features == null || features.size() < 1 ) {
            log.warn("No Feature List specified! Return empty Map!");
            return map;
        }
        Set<DocumentStorage> l;
        for ( Map.Entry<String,Map<String,DocumentStorage>> entry : domainStores.entrySet()) {
            l = null;
            for ( DocumentStorage store : entry.getValue().values()) {
                if ( store.matchFeatures(features)) {
                    if (l == null) {
                        l = new HashSet<DocumentStorage>();
                        map.put(entry.getKey(), l);
                    }
                    l.add(store);
                }
            }
        }
        return map;
    }
    public Set<DocumentStorage> getDocumentStoragesOfPool(String pool) {
        return pool == null  ? null : storePools.get(pool);
    }

    public void config(String url) {
        if (url==null)
            url = CONFIG_URL;
        log.info("Start configuration of Document Storage with:"+url);
        try {
            URL cfgUrl = new URL(url);
            try {
                cfgUrl.getContent();
            } catch ( Exception x ) {
                cfgUrl = copyDefaultConfigToConf(url);
                url = cfgUrl.toExternalForm();
            }
            SAXParserFactory.newInstance().newSAXParser().parse(url, this);
        } catch (Exception x) {
            log.error("Configuration of DocumentStorages failed!", x);
        }
    }

    /**
     * Copy default configuration file (in dcm4chee-docstore-store jar file) to conf directory.
     * 
     * @param url
     * @return
     * @throws IOException 
     */
    private URL copyDefaultConfigToConf(String url) throws IOException {
        URL defCfgUrl = new URL(DEFAULT_INITIAL_CONFIG_URL);
        URL cfgDirUrl = ServerConfigLocator.locate().getServerConfigURL();
        File outFile = new File( cfgDirUrl.getPath(), CONFIG_URL_PATH);
        outFile.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(outFile);
        DataHandler dh = new DataHandler(defCfgUrl);
        dh.writeTo(fos);
        fos.flush();
        fos.close();
        return outFile.toURL();
    }

    public void startElement(String uri, String localName, String qName,
            Attributes attrs) throws SAXException {
        if (qName.equals("DocumentStoreCfg")) {
            log.debug("Root element DocumentStoreCfg found.");
        } else if (qName.equals("StoreDomain")) {
            curDomain = attrs.getValue("name");
            curDomainStores = new HashMap<String, DocumentStorage>();
        } else if (qName.equals("Store")) {
            storeType = attrs.getValue("type");
            provider = docStorageProviders.get(storeType);
            name = attrs.getValue("name");
            curStore = provider.getDocumentStorage(name);
            curDomainStores.put(name, curStore);
            desc = attrs.getValue("desc");
            if ( desc != null ) {
                curStore.setDescription(desc);
            }
            String pool = attrs.getValue("pool");
            if ( pool != null ) {
                Set<DocumentStorage> l = storePools.get(pool);
                if ( l == null ) {
                    l = new HashSet<DocumentStorage>();
                    storePools.put(pool, l);
                }
                l.add(curStore);
            }
        } else if (qName.equals("Feature")) {
            name = attrs.getValue("name");
            desc = attrs.getValue("desc");
            curStore.addFeature(new Feature(name,desc));
        } else if (qName.equals("init")) {
            log.debug("init start");
            initStringSB = new StringBuilder();
        } else {
            log.debug("unhandled start:"+qName);
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("DocumentStoreCfg")) {
            log.info("Configuration of Document Storage finished!");
            log.debug("docStorageProviders:"+this.docStorageProviders);
            log.debug("domainStores:"+this.domainStores);
            log.debug("storePools:"+this.storePools);
        } else if (qName.equals("StoreDomain")) {
            if ( domainStores.put(curDomain, curDomainStores) != null ){
                log.warn("Storage Configuration for domain "+curDomain+" replaced with "+curDomainStores);
            }
            curDomain = null;
            curDomainStores = null;
        } else if (qName.equals("Store")) {
            curStore = null;
        } else if (qName.equals("init")) {
            curStore.init(initStringSB.toString());
            initStringSB = null;
            log.debug("init end");
        } else {
            log.debug("unhandled end:"+qName);
        }
    }

    public void characters (char ch[], int start, int length) throws SAXException
    {
        if ( initStringSB != null ) {
            if ( log.isDebugEnabled() ) log.debug("initString characters:'"+new String(ch, start, length)+"'");
            initStringSB.append(new String(ch, start, length));
        }
    }

}
