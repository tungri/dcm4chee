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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * @author Franz Willer <franz.willer@gmail.com>
 * @version $Revision$ $Date$
 * @since June 12, 2013
 */
public final class QueryXdsPublishCmd {

    public static int transactionIsolationLevel = 0;
    private List<String> paramNameToIndex;
    private BaseSQLCmd cmd;
    
    private int proposedExecutionStartHour = -1;
    private int proposedExecutionEndHour = -1;
    
    private static String MAIN_SQL_LEFT = "SELECT s.pk, p.pk, p.docentry_uid FROM study s LEFT JOIN published_study p ON s.pk = p.study_fk WHERE s.pk in (";
    private static String MAIN_SQL_RIGHT = ") AND p.pk IS NULL OR p.status = 1";
    private static int MAIN_QUERY_LEN = MAIN_SQL_LEFT.length() + MAIN_SQL_RIGHT.length();
    
    private static final Logger log = Logger.getLogger(QueryXdsPublishCmd.class);
    
    public QueryXdsPublishCmd(String sql, int fetchSize, int limit) throws SQLException {
        cmd = new BaseSQLCmd( prepareSQL(sql), fetchSize, limit, null);
        cmd.setUpdateDatabaseMaxRetries(1);
    }
    
    public List<PublishStudy> getPublishStudies(Map<String, Object> params) throws SQLException {
        if (cmd.stmt == null) 
            cmd.open();
        List<PublishStudy> studyPks = new ArrayList<PublishStudy>();
        try {
            setParams(params);
            cmd.execute();
            boolean hasPublishedStudyPk = cmd.rs.getMetaData().getColumnCount() > 1;
            boolean hasDocEntryUID = hasPublishedStudyPk && cmd.rs.getMetaData().getColumnCount() > 2;
            while (cmd.next()) {
                studyPks.add(new PublishStudy(cmd.rs.getObject(1) != null ? cmd.rs.getLong(1) : null, 
                        hasPublishedStudyPk ? cmd.rs.getLong(2) : null,
                        hasDocEntryUID ? cmd.rs.getString(3) : null));
            }
        } finally {
            try {
                cmd.close();
            } catch (Exception ignore) {
                log.warn("Error closing connection!");
            }
        }
        return studyPks;
    }

    private String prepareSQL(String sql) throws SQLException {
        sql = configureProposedExecutionHours(sql);
        boolean nativeQry = sql.charAt(0) == '@';
        int pos = sql.indexOf(':');
        StringBuilder sb;
        if (nativeQry) {
            sql = sql.substring(1);
            if (pos == -1)
                return sql;
            sb = new StringBuilder(sql.length());
        } else {
            sb = new StringBuilder(sql.length()+MAIN_QUERY_LEN);
            sb.append(MAIN_SQL_LEFT);
        }
        if (pos != -1) {
            paramNameToIndex = new ArrayList<String>();
            int paraIdx = 1;
            int pos2 = 0;
            String paramName;
            for (; pos > 0 ; pos = sql.indexOf(':', pos2)) {
                sb.append(sql.substring(pos2, pos)).append('?');
                pos2 = sql.indexOf(' ', ++pos);
                if (pos2 == -1)
                    pos2 = sql.endsWith(";") ? sql.length()-1 : sql.length();
                paramName = sql.substring(pos, pos2);
                log.debug("Found parameter "+paramName+" as #"+paraIdx);
                paramNameToIndex.add(paramName);
            }
            if (pos2 < sql.length())
                sb.append(sql.substring(pos2));
            if (!nativeQry)
                sb.append(MAIN_SQL_RIGHT);
            log.debug("Prepared SQL:"+sb);
            return sb.toString();
        }
        log.debug("No Parameters in Query!");
        return sb.append(sql).append(MAIN_SQL_RIGHT).toString();
    }
    
    private String configureProposedExecutionHours(String sql) {
        if (sql.charAt(0)=='[') {
            int pos = sql.indexOf(']', 1);
            int hypen = sql.indexOf('-',1);
            if (pos == -1 || hypen == -1)
                throw new IllegalArgumentException("Wrong format of ProposedExecutionHours! Must be '[<startHour>-<endHour>]'");
            proposedExecutionStartHour = Integer.parseInt(sql.substring(1, hypen));
            proposedExecutionEndHour = Integer.parseInt(sql.substring(++hypen, pos));
            sql = sql.substring(++pos).trim();
        }
        return sql;
    }
    
    public boolean checkProposedExecutionHours() {
        if (proposedExecutionStartHour == -1)
            return true;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return proposedExecutionStartHour <= proposedExecutionEndHour ?
                hour >= proposedExecutionStartHour && hour <= proposedExecutionEndHour :
                hour >= proposedExecutionStartHour || hour <= proposedExecutionEndHour;    
    }

    protected void setParams(Map<String, Object> params) throws SQLException {
        if ( params != null & paramNameToIndex != null) {
            PreparedStatement pstmt = (PreparedStatement) cmd.stmt;
            int idx = 1;
            for (String name : paramNameToIndex) {
                setParamValue(pstmt, idx++, name, params.get(name));
            }
        }
    }


    private void setParamValue(PreparedStatement pstmt, int idx, String name, Object value) throws SQLException {
        if (log.isDebugEnabled())
            log.debug("Set parameter "+name+" at "+idx+" to:"+value);
        if (value == null) {
            throw new NullPointerException("SQL Parameter "+name+" is null!");
        }
        if (value instanceof Timestamp) {
            pstmt.setTimestamp(idx, (Timestamp)value);
        } else if (value instanceof Boolean) {
            pstmt.setBoolean(idx, (Boolean)value);
        } else if (value instanceof Integer) {
            pstmt.setInt(idx, (Integer)value);
        } else if (value instanceof Long) {
            pstmt.setLong(idx, (Long)value);
        } else if (value instanceof String) {
            pstmt.setString(idx, (String)value);
        } else {
            pstmt.setObject(idx, value);
        }
    }

    public class PublishStudy {
        Long studyPk;
        Long publishedStudyPk;
        String docEntryUID;
        
        public PublishStudy(Long studyPk, Long publishedStudyPk, String docEntryUID) {
            this.studyPk = studyPk;
            this.publishedStudyPk = publishedStudyPk;
            this.docEntryUID = docEntryUID;
        }
        
        public Long getStudyPk() {
            return studyPk;
        }

        public Long getPublishedStudyPk() {
            return publishedStudyPk;
        }
        
        public String getDocumentEntryUID() {
            return docEntryUID;
        }
    }
    
    public String toString() {
        return cmd.getSQL();
    }
}
