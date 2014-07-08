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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dcm4chee.docstore.spi.DocumentStorage;
import org.dcm4chee.docstore.spi.file.DocumentFileStorage;
import org.dcm4chee.docstore.test.DocStoreTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentStorageRegistryTest extends DocStoreTestBase {

    private static Logger log = LoggerFactory.getLogger( DocumentStorageRegistryTest.class );

    public DocumentStorageRegistryTest() {
        super();
    }

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }

    public void testGetDocumentStorageOfType() {
        assertNotNull("Store TestStore(Type:SimpleFileStorage) not found",registry.getDocumentStorageOfType("SimpleFileStorage", "TestStore"));
        assertNotNull("Store PoolStore_1_1(Type:SimpleFileStorage) not found",registry.getDocumentStorageOfType("SimpleFileStorage", "PoolStore_1_1"));
        assertNotNull("Store PoolStore_1_2(Type:SimpleFileStorage) not found",registry.getDocumentStorageOfType("SimpleFileStorage", "PoolSt2re_1_1"));
        assertNotNull("Store PoolStore_2_1(Type:SimpleFileStorage) not found",registry.getDocumentStorageOfType("SimpleFileStorage", "PoolStore_2_1"));
        assertNotNull("Store PoolStore_2_2(Type:SimpleFileStorage) not found",registry.getDocumentStorageOfType("SimpleFileStorage", "PoolSt2re_2_1"));
        assertNotNull("Unconfigured Store TestStore1(Type:SimpleFileStorage) not found!",registry.getDocumentStorageOfType("SimpleFileStorage", "TestStore1"));
        assertNull("Store with unknown type (TestStore(Type:unknown) found!",registry.getDocumentStorageOfType("unknown", "TestStore"));

    }

    public void testGetAllDocumentStorageProviderNames() {
        Set<String> providers = registry.getAllDocumentStorageProviderNames();
        assertTrue("No DocumentStorageProvider found!",providers.size()>0);
        assertTrue("DocumentStorageProvider "+DocumentFileStorage.STORAGE_TYPE+" not available!", providers.contains(DocumentFileStorage.STORAGE_TYPE));
        assertFalse("Unknown DocumentStorageProvider 'unknown' found!", providers.contains("unknown"));
        assertFalse("DocumentStorageProvider list contains null!",providers.contains(null));
    }

    public void testGetDocumentStorage() {
        assertNotNull("getDocumentStorage failed for TestStore! Domain:Test", registry.getDocumentStorage("TEST", "TestStore") );
        assertNotNull("getDocumentStorage failed for PoolStore_1_1! Domain:POOL", registry.getDocumentStorage("POOL", "PoolStore_1_1") );
        assertNotNull("getDocumentStorage failed for PoolStore_1_2! Domain:POOL", registry.getDocumentStorage("POOL", "PoolStore_1_2") );
        assertNotNull("getDocumentStorage failed for PoolStore_2_1! Domain:POOL", registry.getDocumentStorage("POOL", "PoolStore_2_1") );
        assertNotNull("getDocumentStorage failed for PoolStore_2_2! Domain:POOL", registry.getDocumentStorage("POOL", "PoolStore_2_2") );
        assertNull("getDocumentStorage return value for PoolStore_1_1 from Domain TEST!", registry.getDocumentStorage("TEST", "PoolStore_1_1") );
        assertNull("getDocumentStorage return value for PoolStore_2_1 from Domain TEST!", registry.getDocumentStorage("TEST", "PoolStore_2_1") );
        assertNull("getDocumentStorage return value for TestStore from Domain POOL!", registry.getDocumentStorage("POOL", "TestStore") );
        //unknown domain
        assertNull("getDocumentStorage return value for TestStore from unknown Domain 'UNKNOWN'!", registry.getDocumentStorage("UNKNOWN", "TestStore") );
        //check handling of null values
        assertNull("getDocumentStorage return value for TestStore from Domain null!", registry.getDocumentStorage(null, "TestStore") );
        assertNotNull("getDocumentStorage doesn't get default storage of domain! (null from Domain TEST)!", registry.getDocumentStorage("TEST", null) );
        assertNull("getDocumentStorage return value for null from Domain null!", registry.getDocumentStorage( null, null) );
    }

    public void testGetDocumentStorageDomains() {
        Set<String> domains = registry.getDocumentStorageDomains();
        assertTrue("Domain TEST not found!", domains.contains("TEST"));
        assertTrue("Domain POOL not found!", domains.contains("POOL"));
        assertFalse("Domain UNKNOWN found even it is not configured!", domains.contains("UNKNOWN"));
    }

    public void testGetDocumentStoragesString() {
        Collection<DocumentStorage> stores = registry.getDocumentStorages("TEST");
        assertEquals("Number of stores incorrect for Domain TEST!",1, stores.size());
        assertEquals("Incorrect name of store for Domain TEST!","TestStore", stores.iterator().next().getName());

        stores = registry.getDocumentStorages("POOL");
        assertEquals("Number of stores incorrect for Domain POOL!",4, stores.size());
        ArrayList<String> l = new ArrayList<String>(4);
        l.add("PoolStore_1_1");l.add("PoolStore_1_2");
        l.add("PoolStore_2_1");l.add("PoolStore_2_2");
        String n;
        for ( DocumentStorage st : stores) {
            n = st.getName();
            assertTrue("Incorrect storage name '"+n+"' in Domain POOL!", l.contains(n));
        }
    }

    public void testGetDocumentStoragesSetOfFeature() {
        Set<Feature> features = new HashSet<Feature>();
        features.add(Feature.CACHE);
        Map<String, Set<DocumentStorage>> storesByDomain = registry.getDocumentStorages(features );
        assertEquals("Number of Domains incorrect for Features [CACHE]!",1, storesByDomain.size());
        Set<DocumentStorage> stores = storesByDomain.get("TEST");
        ArrayList<String> names = new ArrayList<String>(2);
        names.add("TestStore");
        checkListOfStores(stores, "Domain TEST for Features [cache]", names);

        features.clear();
        features.add( new Feature("TEST_RETRIEVE", null));
        storesByDomain = registry.getDocumentStorages(features );
        assertEquals("Number of Domains incorrect for Features [TEST_RETRIEVE]!",2, storesByDomain.size());
        checkListOfStores(stores, "Domain TEST for Features [TEST_RETRIEVE]", names);

        stores = storesByDomain.get("POOL");
        names.clear();names.add("PoolStore_1_1");names.add("PoolStore_1_2");
        checkListOfStores(stores, "Domain POOL for Features [TEST_RETRIEVE]", names);

        features.clear();
        features.add( new Feature("RID_RETRIEVE", null));
        storesByDomain = registry.getDocumentStorages(features );
        assertEquals("Number of Domains incorrect for Features [RID_RETRIEVE]!",1, storesByDomain.size());
        stores = storesByDomain.get("POOL");
        names.clear();names.add("PoolStore_2_1");names.add("PoolStore_2_2");
        checkListOfStores(stores, "Domain POOL for Features [RID_RETRIEVE]", names);

        features.clear();
        features.add( new Feature("NOT_CONFIGURED", null));
        storesByDomain = registry.getDocumentStorages(features );
        assertTrue("Found storage for unconfigured feature (NOT_CONFIGURED)!", storesByDomain.isEmpty());

        features.clear();
        storesByDomain = registry.getDocumentStorages(features );
        assertTrue("Found storage for empty feature list!", storesByDomain.isEmpty());
    }

    public void testGetDocumentStoragesOfPool() {
        String pool = "pool1";
        Set<DocumentStorage> stores = registry.getDocumentStoragesOfPool(pool);
        ArrayList<String> names = new ArrayList<String>(2);
        names.add("PoolStore_1_1");names.add("PoolStore_1_2");
        checkListOfStores(stores, pool, names);
        
        pool="pool2";
        stores = registry.getDocumentStoragesOfPool(pool);
        names.clear();names.add("PoolStore_2_1");names.add("PoolStore_2_2");
        checkListOfStores(stores, pool, names);
    }
    
    private void checkListOfStores(Set<DocumentStorage> stores, String msg, List<String> names) {
        assertNotNull("No storages in "+msg+" found!",stores);
        assertEquals("Number of storages in "+msg+" is incorrect!",names.size(), stores.size());
        String n;
        for ( DocumentStorage st : stores) {
            n = st.getName();
            assertTrue("Name of DocumentStorage in "+msg+" is incorrect! name:"+n, names.contains(n));
        }
    }
}
