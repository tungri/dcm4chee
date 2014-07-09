/******************************************
 *                                        *
 *  dcm4che: A OpenSource DICOM Toolkit   *
 *                                        *
 *  Distributable under LGPL license.     *
 *  See terms of license at gnu.org.      *
 *                                        *
 ******************************************/

package org.dcm4chex.archive.common;

import java.util.Arrays;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 16887 $ $Date: 2012-07-06 22:29:39 +0000 (Fri, 06 Jul 2012) $
 * @since Nov 23, 2005
 */
public class FileStatus {

    private static final String[] ENUM = {
        "MIGRATION_FAILED",
    	"QUERY_HSM_FAILED",
    	"MD5_CHECK_FAILED",
    	"VERIFY_COMPRESS_FAILED", 
    	"COMPRESS_FAILED",
    	"DEFAULT", 
    	"TO_ARCHIVE",
    	"ARCHIVED",
        "COMPRESSING",
        "MIGRATED"
    };

    public static final int MIGRATION_FAILED = -5;
    public static final int QUERY_HSM_FAILED = -4;
    public static final int MD5_CHECK_FAILED = -3;
    public static final int VERIFY_COMPRESS_FAILED = -2;
    public static final int COMPRESS_FAILED = -1;
    public static final int DEFAULT = 0;    
    public static final int TO_ARCHIVE = 1;    
    public static final int ARCHIVED = 2;
    public static final int COMPRESSING = 3;
    public static final int MIGRATED = 4;
    
    private static final int OFFSET = -MIGRATION_FAILED;

    public static final String toString(int value) {
        return ENUM[OFFSET + value];
    }

    public static final int toInt(String s) {        
        final int index = Arrays.asList(ENUM).indexOf(s);
        if (index == -1)
            throw new IllegalArgumentException(s);
        return index - OFFSET;
    }
}
