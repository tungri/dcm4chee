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
package org.dcm4chee.docstore.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.dcm4chee.docstore.Availability;
import org.jboss.mx.util.MBeanServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemInfo {

    private static Logger log = LoggerFactory.getLogger( FileSystemInfo.class );

    private static ObjectName dfCmdName;

    private static MBeanServer server;

    /** JDK6 File.getUsableSpace(), if available. */
    private static Method jdk6getUsableSpace = null;
    static {
        try {
            jdk6getUsableSpace = File.class.getMethod("getUsableSpace", (Class[]) null);
        } catch (Exception ignore) {}        
    }
    
    public static final void disableJDK6Support() {
        jdk6getUsableSpace = null;
    }

    public static ObjectName getFilesystemMgtName() {
        return dfCmdName;
    }

    public static void setDFCmdServiceName(String name) {
        try {
            dfCmdName = new ObjectName(name);
        } catch (Exception e) {
            log.error("Cant set FilesystemMgtName! name:"+name);
        }
    }

    public static long freeSpace(String path) throws IOException, InstanceNotFoundException, MBeanException, ReflectionException {
        if (jdk6getUsableSpace != null) {
            try {
                long l = ((Long) jdk6getUsableSpace.invoke(new File(path), (Object[]) null)).longValue();
                if (l != 0) //Workaround for JDK6 bug (filesystem > 4TB value is always 0!
                    return l;
            } catch (Exception ignore) {
                log.warn("freeSpace using JDK6 getUsableSpace throws exception! try to get free space via DFCommand service!");
            }
        }
        if ( getServer().isRegistered(dfCmdName) ) {
            return ((Long)getServer().invoke(dfCmdName, "freeSpace",
                new Object[] {path},
                new String[] {String.class.getName()})).longValue();
        } else {
            return -1l;
        }
    }

    public static Availability getFileSystemAvailability(File baseDir, long minFree) {
        if ( ! baseDir.isDirectory() ) {
            log.warn(baseDir+" is not a directory! Set Availability to UNAVAILABLE!");
            return Availability.UNAVAILABLE;
        } else {
            try {
                long free = freeSpace(baseDir.getPath());
                log.debug("check Filesystem availability for doc store! path:"+baseDir.getPath()+ " free:"+free);
                if ( free == -1 ) {
                    log.warn("Availability can't be checked! Set to ONLINE anyway!");
                    return Availability.ONLINE;
                }
                return free < minFree ? Availability.UNAVAILABLE : Availability.ONLINE;
            } catch (Exception x) {
                log.error("Can not get free space for "+baseDir+" ! Set Availability to UNAVAILABLE!",x);
                return Availability.UNAVAILABLE;
            }
        }
    }

    private static MBeanServer getServer() {
        if ( server == null ) {
            server = MBeanServerLocator.locate();
        }
        return server;
    }

}
