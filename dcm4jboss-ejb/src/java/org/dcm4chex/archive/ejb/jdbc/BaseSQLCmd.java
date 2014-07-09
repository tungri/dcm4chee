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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * @author Franz Willer <franz.willer@gmail.com>
 * @version $Revision$ $Date$
 * @since Dec 22, 2010
 */
public class BaseSQLCmd extends BaseReadCmd {

    private static final int SELECT_LEN = 7;
    public static int transactionIsolationLevel = 0;
    
    protected BaseSQLCmd(String sql, int fetchSize, int limit, String checkPkTable) throws SQLException {
        super(JdbcProperties.getInstance().getDataSource(),
                transactionIsolationLevel, prepareSql(sql, limit, checkPkTable));
        setFetchSize(fetchSize < 0 ? limit : fetchSize);
        try {
            close();
        } catch (Throwable t) {
            log.warn("Initial close failed:"+t.getLocalizedMessage());
        }
    }
    
    public static BaseSQLCmd getInstance( String sql, int limit, int fetchSize, String checkPkTable) throws SQLException {
        return new BaseSQLCmd(sql, fetchSize, limit, checkPkTable);
    }
    
    public static String prepareSql(String sql, int limit, String checkPkTable) {
        SqlBuilder sqlBuilder = new SqlBuilder();
        sql = sql.trim();
        if ( sql.endsWith(";")) {
            sql = sql.substring(0, sql.length()-1);
        }
        sql = sql.replaceAll("\\s\\s+", " ");
        log.debug("Original SQL (formatted):"+sql);
        if (limit > 0 ) {
            if (checkPkTable != null)
                sql = addPkCheck(sql, checkPkTable);
            String sql1 = sql.toUpperCase();
            int pos0 = sql1.indexOf("DISTINCT");
            if (pos0 != -1) {
                sqlBuilder.setDistinct(true);
                pos0 += 9;
            } else {
                pos0 = SELECT_LEN;
            }
            int pos1 = sql1.indexOf("FROM");
            StringBuffer sb = new StringBuffer(sql.length()+30);
            sb.append(sql.substring(0, SELECT_LEN));
            sqlBuilder.setLimit(limit);
            String[] fields = toFields(sql.substring(pos0, pos1)); 
            sqlBuilder.setFieldNamesForSelect(fields);
            sqlBuilder.addOrderBy(fields[0], SqlBuilder.ASC);
            sqlBuilder.appendLimitbeforeFrom(sb);
            sb.append(' ');
            int pos2 = sql1.indexOf("FOR READ ONLY", pos1); //DB2?
            if (pos2 > 0) {
                sb.append(sql.substring(pos1, pos2));
                sqlBuilder.appendLimitAtEnd(sb);
                sb.append(' ').append(sql.substring(pos2));
            } else {
                sb.append(sql.substring(pos1));
                sqlBuilder.appendLimitAtEnd(sb);
            }
            log.debug("SQL with LIMIT:"+sb);
            return sb.toString();
        } else {
            return sql;
        }
    }
    
    private static String addPkCheck(String sql, String checkPkTable) {
        checkPkTable = checkPkTable.toUpperCase();
        log.debug("Add "+checkPkTable+".pk check to sql:"+sql);
        String sqlUC = sql.toUpperCase();
        int posFrom = sqlUC.indexOf("FROM");
        posFrom += 5;
        int posTable = sqlUC.indexOf(checkPkTable, posFrom);
        if (posTable == -1) {
            log.warn(checkPkTable+" table not found is SELECT statement! pk check not added!");
            return sql;
        }
        int posWhere = sqlUC.indexOf("WHERE", posTable);
        posWhere += 6;
        if (sqlUC.indexOf("PK > ?", posWhere) != -1) {
            log.info("'pk > ?' found in WHERE clause! Use this as pk check!");
            return sql;
        }
        String alias = sql.substring(posTable, posTable+=6);
        while (sqlUC.charAt(posTable)==' ') 
            posTable++;
        if (sqlUC.charAt(posTable++)=='A' && sqlUC.charAt(posTable++)=='S') {
            posTable++;
            int posEndAlias = posTable;
            while (sqlUC.charAt(posEndAlias) != ',' && sqlUC.charAt(posEndAlias) != ' ')
                posEndAlias++;
            alias = sql.substring(posTable, posEndAlias);
        }
        StringBuffer sb = new StringBuffer(sql.length()+alias.length()+10);
        sb.append(sql.substring(0, posWhere)).append(alias).append(".pk > ? AND ");
        if (sqlUC.lastIndexOf("ORDER BY") == -1) {
            int posDB2 = sqlUC.indexOf("FOR READ ONLY", posWhere);
            if (posDB2 == -1) {
                sb.append(sql.substring(posWhere)).append(" ORDER BY ").append(alias).append(".pk ");
            } else {
                sb.append(sql.subSequence(posWhere, posDB2)).append(" ORDER BY ")
                .append(alias).append(".pk ").append(sql.substring(posDB2));
            }
        } else {
            sb.append(sql.substring(posWhere));
        }
        log.debug("SQL with "+checkPkTable+".pk check:"+sb);
        return sb.toString();
    }

    private static String[] toFields(String s) {
        StringTokenizer st = new StringTokenizer(s, ",");
        String[] fields = new String[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            fields[i++] = st.nextToken();
        }
        return fields;
    }
    
    protected void setParams(Long updatedBefore, long lastPk) throws SQLException {
        if ( updatedBefore != null ) {
            int paraIdx = 1;
            if (new StringTokenizer(sql, "?").countTokens() > 2) {
                log.debug("Set parameter 1 (pk > lastPk) to:"+lastPk);
                ((PreparedStatement) stmt).setLong(paraIdx++, lastPk);
            }
            Timestamp ts = new Timestamp(updatedBefore);
            if ( log.isDebugEnabled() ) 
                log.debug("Set parameter (updatedBefore) to:"+updatedBefore+" Timestamp:"+ts);
            ((PreparedStatement) stmt).setTimestamp(paraIdx, ts);
        } else {
            if (log.isDebugEnabled()) 
                log.debug("Use of updatedBefore WHERE clause disabled! Dont set parameter of prepared statement");
            if (sql.indexOf('?') != -1) {
                log.debug("Set parameter 1 (pk > lastPk) to:"+lastPk);
                ((PreparedStatement) stmt).setLong(1, lastPk);
            }
        }
    }
    
    public String getSQL() {
        return sql;
    }
    
    public String formatSql() {
        if (sql == null)
            return "SQL not set!";
        String sqlUC = sql.toUpperCase();
        StringBuilder sb = new StringBuilder(sql.length()+10);
        int[] pos = new int[]{0,0};
        appendAndSplit(sb, pos, sql, sqlUC,"FROM", null);
        appendAndSplit(sb, pos, sql, sqlUC,"WHERE", null);
        while (appendAndSplit(sb, pos, sql, sqlUC,"EXISTS","NOT EXISTS"));
        appendAndSplit(sb, pos, sql, sqlUC,"ORDER", null);
        sb.append(sql.substring(pos[0]));
        return sb.toString();
    }
    
    private boolean appendAndSplit(StringBuilder sb, int[] pos, String sql, String sqlUC, String splitAt, String altSplitAt) {
        pos[1] = sqlUC.indexOf(splitAt, pos[0]);
        if (altSplitAt != null) {
            int pos2 = sqlUC.indexOf(altSplitAt, pos[0]);
            if (pos2 != -1 && (pos[1] == -1 || pos2 < pos[1])) {
                pos[1] = pos2;
                splitAt = altSplitAt;
            }
        }
        if (pos[1] != -1) {
            sb.append(sql.substring(pos[0], pos[1])).append("\n");
            pos[0] = pos[1];
            pos[1] += splitAt.length();
            sb.append(sql.subSequence(pos[0], pos[1]));
            pos[0] = pos[1];
            return true;
        }
        return false; 
    }
}
