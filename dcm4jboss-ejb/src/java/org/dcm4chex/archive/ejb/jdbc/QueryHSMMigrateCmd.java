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

package org.dcm4chex.archive.ejb.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dcm4chex.archive.common.FileStatus;

/**
 * 
 * @author franz.willer@gmail.com
 * @version $Revision: $ $Date: $
 * @since 25.07.2012
 */
public class QueryHSMMigrateCmd extends BaseReadCmd {
    private static final String ALIAS_SRC = "src";

    public static int transactionIsolationLevel = 0;

    private final SqlBuilder sqlBuilder = new SqlBuilder();

    public QueryHSMMigrateCmd() throws SQLException {
        super(JdbcProperties.getInstance().getDataSource(), transactionIsolationLevel);
    }
    
	
    public Set<String> getTarFilenamesToMigrate(long fsPk, int[] fileStati, 
            boolean lastPksFirst, int limit, int offset) throws SQLException {
        sqlBuilder.setFrom( new String[] {"File"} );
        sqlBuilder.setFieldNamesForSelect( new String[] { getSelectTarFilename("files") });
        sqlBuilder.addIntValueMatch(null, "File.filesystem_fk", false, (int)fsPk);
        addFileStatiMatch(fileStati);
        sqlBuilder.addOrderBy("File.pk", lastPksFirst ? " DESC" : " ASC");
        sqlBuilder.setDistinct(true);
        sqlBuilder.setLimit(limit);
        sqlBuilder.setOffset(offset);
        HashSet<String> result = new HashSet<String>();
        try {
            execute(sqlBuilder.getSql());
            while (next()) {
                result.add(toTarFn(rs.getString(1)));
            }
            return result;
        } finally {
            close();
        }
    }


    private String getSelectTarFilename(String alias) {
        switch(JdbcProperties.getInstance().getDatabase()) {
            case JdbcProperties.PSQL:
            case JdbcProperties.FIREBIRD:
                return "SUBSTRING("+alias+".filepath FROM 1 FOR POSITION('!' IN "+alias+".filepath))";
            case JdbcProperties.ORACLE:
                return "SUBSTR("+alias+".filepath, 1, INSTR("+alias+".filepath,'!'))";
            case JdbcProperties.DB2:
                return "SUBSTR("+alias+".filepath, 1, LOCATE('!', "+alias+".filepath))";
            case JdbcProperties.MSSQL:
                return "SUBSTRING("+alias+".filepath, 1, PATINDEX('%!%', "+alias+".filepath))";
            default:
                return "SUBSTRING("+alias+".filepath FROM 1 FOR LOCATE('!',"+alias+".filepath))";
        }
    }


    /**
     * Return list of tar filenames and file status for source FS and target FS of instances
     * String[]:
     * [0]..source tar filename
     * [1]..source file status
     * [2]..target tar filename
     * [3]..target file status
     * @param lastFilePkForRemove 
     * @param srcFsId
     * @param targetFsId
     * @param lastPksFirst
     * @param limit
     * @return
     * @throws SQLException
     */
    public List<String[]> getTarFilenamesAndStatus(long srcFsPk, long targetFsPk, int[] targetFileStati, 
            int limit, int offset) throws SQLException {
        sqlBuilder.setFrom(new String[] {"File", "File"});
        sqlBuilder.setAliases(new String[] {ALIAS_SRC, null});
        sqlBuilder.setFieldNamesForSelect( new String[] { 
                getSelectTarFilename(ALIAS_SRC),
                "src.file_status",
                getSelectTarFilename("files"),
                "files.file_status"
                });
        sqlBuilder.addIntValueMatch(ALIAS_SRC, "File.filesystem_fk", false, (int)srcFsPk);
        sqlBuilder.addIntValueMatch(null, "File.filesystem_fk", false, (int)targetFsPk);
        sqlBuilder.addFieldValueMatch(ALIAS_SRC, "File.instance_fk", false, null, "File.instance_fk");
        addFileStatiMatch(targetFileStati);
        sqlBuilder.setDistinct(true);
        sqlBuilder.addOrderBy("File.pk", " ASC");
        sqlBuilder.setLimit(limit);
        sqlBuilder.setOffset(offset);
        ArrayList<String[]> result = new ArrayList<String[]>();
        try {
            execute(sqlBuilder.getSql());
            while (next()) {
                result.add(new String[]{toTarFn(rs.getString(1)), FileStatus.toString(rs.getInt(2)), 
                        toTarFn(rs.getString(3)), FileStatus.toString(rs.getInt(4))});
            }
            return result;
        } finally {
            close();
        }
    }

    public List<int[]> countFilesPerStatus(long srcFsPk) throws SQLException {
        sqlBuilder.setFrom(new String[] {"File"});
        sqlBuilder.setSelect(new String[]{"File.fileStatus",
                sqlBuilder.getCountOf(null, false)});
        sqlBuilder.addIntValueMatch(null, "File.filesystem_fk", false, (int)srcFsPk);
        sqlBuilder.setGroupBy("File.fileStatus");
        ArrayList<int[]> result = new ArrayList<int[]>();
        try {
            execute(sqlBuilder.getSql());
            while (next()) {
                result.add(new int[]{rs.getInt(1), rs.getInt(2)});
            }
            return result;
        } finally {
            close();
        }
    }

    public int countFileCopiesOfTarFile(long srcFsPk, String tarFile, long targetFsPk) throws SQLException {
        sqlBuilder.setFrom(new String[] {"File", "File"});
        sqlBuilder.setAliases(new String[] {ALIAS_SRC, null});
        sqlBuilder.setSelect(new String[]{sqlBuilder.getCountOf(null, false)});
        sqlBuilder.addIntValueMatch(ALIAS_SRC, "File.filesystem_fk", false, (int)srcFsPk);
        sqlBuilder.addIntValueMatch(null, "File.filesystem_fk", false, (int)targetFsPk);
        sqlBuilder.addFieldValueMatch(ALIAS_SRC, "File.instance_fk", false, null, "File.instance_fk");
        sqlBuilder.addWildCardMatch(ALIAS_SRC, "File.filePath", false, tarFile+"!*");
        sqlBuilder.addOrderBy("File.pk", " ASC");
        try {
            execute(sqlBuilder.getSql());
            next();
            return rs.getInt(1); 
        } finally {
            close();
        }
    }
    
    private void addFileStatiMatch(int[] fileStati) {
        if (fileStati != null) {
            if (fileStati.length == 1) {
                sqlBuilder.addIntValueMatch(null, "File.fileStatus", false, fileStati[0]);
            } else {
                sqlBuilder.addListOfIntMatch(null, "File.fileStatus", false, fileStati);
            }
        }
    }

    private String toTarFn(String s) {
        return s.endsWith("!") ? s.substring(0,s.length()-1) : s;
    }

}