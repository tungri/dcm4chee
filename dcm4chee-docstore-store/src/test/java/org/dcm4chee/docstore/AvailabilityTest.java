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

import java.lang.reflect.Field;

import junit.framework.TestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvailabilityTest extends TestCase {
    private Availability inmemory = Availability.INMEMORY;
    private Availability online = Availability.ONLINE;
    private Availability nearline = Availability.NEARLINE;
    private Availability offline = Availability.OFFLINE;
    private Availability unavailable = Availability.UNAVAILABLE;
    private Availability noneexistent = Availability.NONEEXISTENT;

    private Field[] fields = Availability.class.getFields();
    
    private static Logger log = LoggerFactory.getLogger( AvailabilityTest.class );
    
    public void testHashCode() {
        assertEquals("HashCode of INMEMORY incorrect!", 0, inmemory.hashCode());
        assertEquals("HashCode of ONLINE incorrect!", 1, online.hashCode());
        assertEquals("HashCode of NEARLINE incorrect!", 2, nearline.hashCode());
        assertEquals("HashCode of OFFLINE incorrect!", 3, offline.hashCode());
        assertEquals("HashCode of UNAVAILABLE incorrect!", 4, unavailable.hashCode());
        assertEquals("HashCode of NONEEXISTENT incorrect!", 5, noneexistent.hashCode());
    }

    public void testEqualsObject() throws IllegalArgumentException, IllegalAccessException {
        assertTrue("INMEMORY equals failed!", inmemory.equals(Availability.INMEMORY) );
        assertTrue("ONLINE equals failed!", online.equals(Availability.ONLINE) );
        assertTrue("NEARLINE equals failed!", nearline.equals(Availability.NEARLINE) );
        assertTrue("OFFLINE equals failed!", offline.equals(Availability.OFFLINE) );
        assertTrue("UNAVAILABLE equals failed!", unavailable.equals(Availability.UNAVAILABLE) );
        assertTrue("NONEEXISTENT equals failed!", noneexistent.equals(Availability.NONEEXISTENT) );
        for ( int i = 0 ; i < fields.length ; i++) {
            for ( int j = i ; j < fields.length ; j++) {
                if ( ( fields[i].get(this) instanceof Availability ) && ( fields[j].get(this) instanceof Availability ) ) {
                    if ( i==j ) {
                        assertTrue("Fields are not equal! "+fields[i]+" - "+fields[j], fields[i].get(this).equals(fields[j].get(this)) );
                    } else {
                        assertFalse("Fields are equal! "+fields[i]+" - "+fields[j], fields[i].get(this).equals(fields[j].get(this)) );
                    }
                } else {
                    log.info("Ignore Field compare:"+
                            fields[i].getName()+"="+fields[j].getName());
                }
            }
        }
    }

    public void testCompareTo() throws IllegalArgumentException, IllegalAccessException {
        assertTrue("INMEMORY < ONLINE failed", inmemory.compareTo(online) < 0 );
        assertTrue("ONLINE < NEARLINE failed", online.compareTo(nearline) < 0 );
        assertTrue("NEARLINE < OFFLINE failed", nearline.compareTo(offline) < 0 );
        assertTrue("OFFLINE < UNAVAILABLE failed", offline.compareTo(unavailable) < 0 );
        assertTrue("UNAVAILABLE < NONEEXISTENT failed", unavailable.compareTo(noneexistent) < 0 );
        
        assertTrue("INMEMORY = INMEORY failed", inmemory.compareTo(Availability.INMEMORY) == 0 );
        assertTrue("ONLINE = ONLINE failed", online.compareTo(Availability.ONLINE) == 0 );
        assertTrue("NEARLINE = NEARLINE failed", nearline.compareTo(Availability.NEARLINE) == 0 );
        assertTrue("OFFLINE = OFFLINE failed", offline.compareTo(Availability.OFFLINE) == 0 );
        assertTrue("UNAVAILABLE = UNAVAILABLE failed", unavailable.compareTo(Availability.UNAVAILABLE) == 0 );
        assertTrue("NONEEXISTENT = NONEEXISTENT failed", noneexistent.compareTo(Availability.NONEEXISTENT) == 0 );

        assertTrue("ONLINE > INMEMORY failed", online.compareTo(inmemory) > 0 );
        assertTrue("NEARLINE > ONLINE failed", nearline.compareTo(online) > 0 );
        assertTrue("OFFLINE > NEARLINE failed", offline.compareTo(nearline) > 0 );
        assertTrue("UNAVAILABLE > OFFLINE failed", unavailable.compareTo(offline) > 0 );
        assertTrue("NONEEXISTENT > UNAVAILABLE failed", noneexistent.compareTo(unavailable) > 0 );
        
        Object o;
        for ( Field f : fields ) {
            o = f.get(this);
            if ( o != null && (o instanceof Availability) ) {
                if ( Availability.NONEEXISTENT.compareTo((Availability)o) < 0 ) {
                    fail("Availability > Availability.NONEEXISTENT found! "+f.getName());
                }
            } else {
                log.info("Ignore Availability compare for field:"+f.getName());
            }
        }
    }

    public void testToString() throws IllegalArgumentException, IllegalAccessException {
        for ( Field f : fields ) {
            f.get(this).toString();
        }
    }

}
