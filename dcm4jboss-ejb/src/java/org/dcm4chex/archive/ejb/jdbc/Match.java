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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.dcm4che.data.PersonName;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;

/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 14959 $ $Date: 2008-10-28 13:34:01 +0100 (Tue, 28 Oct
 *          2008) $
 * @since 25.08.2003
 */
abstract class Match {

    protected String column;
    protected final boolean type2;

    protected Match() {
        this.type2 = false;
    }

    protected Match(String alias, String field, boolean type2) {
        this.column = formatColumn(alias, field);
        this.type2 = type2;
    }

    private static String formatColumn(String alias, String field) {
        String col = JdbcProperties.getInstance().getProperty(field);
        if (col == null)
            throw new IllegalArgumentException("field: " + field);
        if (alias != null) {
            col = alias + col.substring(col.indexOf('.'));
        }
        return col;
    }

    public boolean appendTo(StringBuffer sb) {
        if (isUniveralMatch())
            return false;
        sb.append('(');
        if (type2) {
            sb.append(column);
            sb.append(" IS NULL OR ");
        }
        appendBodyTo(sb);
        sb.append(')');
        return true;
    }

    public abstract boolean isUniveralMatch();

    protected abstract void appendBodyTo(StringBuffer sb);

    static class NULLValue extends Match {
        private boolean inverter;

        public NULLValue(String alias, String field, boolean inverter) {
            super(alias, field, false);
            this.inverter = inverter;
        }

        public boolean isUniveralMatch() {
            return false;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            sb.append(" IS");
            if (inverter)
                sb.append(" NOT");
            sb.append(" NULL");
        }
    }

    static class SingleValue extends Match {
        private final String value;

        public SingleValue(String alias, String field, boolean type2,
                String value) {
            super(alias, field, type2);
            this.value = value;
        }

        public boolean isUniveralMatch() {
            return value == null || value.length() == 0  || value.equals("*");
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            sb.append(" = \'");
            sb.append(value == null ? value : value.replaceAll("'", "''"));
            sb.append('\'');
        }
    }

    static class FieldValue extends Match {
        private final String column1;

        public FieldValue(String alias1, String field1, boolean type2,
                String alias2, String field2) {
            super(alias1, field1, type2);
            column1 = formatColumn(alias2, field2);

        }

        public boolean isUniveralMatch() {
            return column1 == null || column1.length() == 0;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            sb.append(" = ");
            sb.append(column1);
        }
    }

    static class IntValue extends Match {
        private final int value;

        public IntValue(String alias, String field, boolean type2, int value) {
            super(alias, field, type2);
            this.value = value;
        }

        public boolean isUniveralMatch() {
            return false;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            sb.append(" = ");
            sb.append(value);
        }
    }

    static class ListOfInt extends Match {
        private final int[] ints;

        public ListOfInt(String alias, String field, boolean type2, int[] ints) {
            super(alias, field, type2);
            this.ints = ints != null ? (int[]) ints.clone() : new int[0];
        }

        public boolean isUniveralMatch() {
            return ints.length == 0;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            if (ints.length == 1) {
                sb.append(" = ").append(ints[0]);
            } else {
                sb.append(" IN (").append(ints[0]);
                for (int i = 1; i < ints.length; i++) {
                    sb.append(", ").append(ints[i]);
                }
                sb.append(")");
            }
        }
    }

    static class AppendLiteral extends Match {
        private String literal;

        public AppendLiteral(String alias, String field, boolean type2,
                String literal) {
            super(alias, field, type2);
            this.literal = literal;
        }

        public boolean isUniveralMatch() {
            return false;
        }

        public void setLiteral(String s) {
            literal = s;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            sb.append(" ");
            sb.append(literal);
        }
    }

    static class ListOfString extends Match {
        private final String[] strings;

        public ListOfString(String alias, String field, boolean type2,
                String[] strings) {
            super(alias, field, type2);
            this.strings = strings != null ? (String[]) strings.clone()
                    : new String[0];
        }

        public boolean isUniveralMatch() {
            return strings.length == 0 || strings[0].equals("*");
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            if (strings.length == 1) {
                sb.append(" = \'").append(strings[0]).append('\'');
            } else {
                sb.append(" IN ('").append(strings[0]);
                for (int i = 1; i < strings.length; i++) {
                    sb.append("\', \'").append(strings[i]);
                }
                sb.append("\')");
            }
        }
    }

    static class WildCard extends Match {
        private final char[] wc;

        public WildCard(String alias, String field, boolean type2, String wc) {
            super(alias, field, type2);
            this.wc = wc != null ? wc.toCharArray() : new char[0];
        }

        public boolean isUniveralMatch() {
            for (int i = wc.length; --i >= 0;)
                if (wc[i] != '*')
                    return false;
            return true;
        }

        public boolean isLike() {
            for (int i = wc.length; --i >= 0;)
                if (wc[i] == '*' || wc[i] == '?')
                    return true;
            return false;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            final boolean like = isLike();
            sb.append(like ? " LIKE \'" : " = \'");
            char c;
            boolean escape = false;
            for (int i = 0; i < wc.length; i++) {
                switch (c = wc[i]) {
                case '?':
                    c = '_';
                    break;
                case '*':
                    c = '%';
                    break;
                case '\'':
                    sb.append('\'');
                    break;
                case '_':
                case '%':
                    if (like) {
                        sb.append('\\');
                        escape = true;
                    }
                    break;
                }
                sb.append(c);
            }
            sb.append('\'');
            if (escape) {
                sb.append(JdbcProperties.getInstance().getEscape());
            }
        }

    }

    static class Range extends Match {
        private final Date[] range;
        private final String format;

        public Range(String alias, String field, boolean type2, Date[] range,
                String format) {
            super(alias, field, type2);
            this.range = range != null ? (Date[]) range.clone() : null;
            this.format = format;
        }

        public boolean isUniveralMatch() {
            return range == null;
        }

        protected void appendBodyTo(StringBuffer sb) {
            SimpleDateFormat df = new SimpleDateFormat(format);
            sb.append(column);
            if (range[0] == null) {
                sb.append(" <= ");
                sb.append(df.format(range[1]));
            } else if (range[1] == null) {
                sb.append(" >= ");
                sb.append(df.format(range[0]));
            } else {
                sb.append(" BETWEEN ");
                sb.append(df.format(range[0]));
                sb.append(" AND ");
                sb.append(df.format(range[1]));
            }

        }

    }

    static class StringRange extends Match {
        private final String[] range;

        public StringRange(String alias, String field, boolean type2,
                String[] range) {
            super(alias, field, type2);
            this.range = range != null ? (String[]) range.clone() : null;
        }

        public boolean isUniveralMatch() {
            return range == null;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(column);
            if (range[0] != null) {
                sb.append(" >= '");
                sb.append(range[0]);
                if (range[1] != null) {
                    sb.append("' AND ");
                    sb.append(column);
                }
            }
            if (range[1] != null) {
                sb.append(" <= '");
                sb.append(range[1]);
            }
            sb.append('\'');
        }

    }

    static class ModalitiesInStudyMultiNestedMatch extends Match {
        private final String[] mds;

        public ModalitiesInStudyMultiNestedMatch(String alias, String[] mds) {
            super(alias, "Series.modality", false);
            this.mds = mds != null ? mds : new String[0];
        }

        public boolean isUniveralMatch() {
            return mds.length == 0;
        }

        protected void appendBodyTo(StringBuffer sb) {
            JdbcProperties jp = JdbcProperties.getInstance();
            sb.append("EXISTS (SELECT 1 FROM ");
            sb.append(jp.getProperty("Series"));
            sb.append(" WHERE ");
            sb.append(jp.getProperty("Series.study_fk"));
            sb.append(" = ");
            sb.append(jp.getProperty("Study.pk"));
            sb.append(" AND ");
            sb.append(column);
            if (mds.length == 1) {
                sb.append(" = \'").append(mds[0]).append('\'');
            } else {
                sb.append(" IN ('").append(mds[0]);
                for (int i = 1; i < mds.length; i++) {
                    sb.append("\', \'").append(mds[i]);
                }
                sb.append("\')");
            }
            sb.append(')');
        }
    }

    static class ModalitiesInStudyNestedMatch extends Match {
        private final char[] wc;

        public ModalitiesInStudyNestedMatch(String alias, String md) {
            super(alias, "Series.modality", false);
            this.wc = md != null ? md.toCharArray() : new char[0];
        }

        public boolean isUniveralMatch() {
            for (int i = wc.length; --i >= 0;)
                if (wc[i] != '*')
                    return false;
            return true;
        }

        public boolean isLike() {
            for (int i = wc.length; --i >= 0;)
                if (wc[i] == '*' || wc[i] == '?')
                    return true;
            return false;
        }

        protected void appendBodyTo(StringBuffer sb) {
            JdbcProperties jp = JdbcProperties.getInstance();
            sb.append("exists (SELECT 1 FROM ");
            sb.append(jp.getProperty("Series"));
            sb.append(" WHERE ");
            sb.append(jp.getProperty("Series.study_fk"));
            sb.append(" = ");
            sb.append(jp.getProperty("Study.pk"));
            sb.append(" AND ");
            sb.append(column);
            final boolean like = isLike();
            sb.append(like ? " LIKE '" : " = '");
            char c;
            for (int i = 0; i < wc.length; i++) {
                switch (c = wc[i]) {
                case '?':
                    c = '_';
                    break;
                case '*':
                    c = '%';
                    break;
                case '\'':
                    sb.append('\'');
                    break;
                case '_':
                case '%':
                    if (like) {
                        sb.append('\\');
                    }
                    break;
                }
                sb.append(c);
            }
            sb.append("')");
        }
    }

    static class CallingAETsNestedMatch extends Match {
        private final String[] callingAETs;
        private final boolean privTables;

        public CallingAETsNestedMatch(boolean privTables, String[] callingAETs) {
            super(null, privTables ? "PrivateSeries.sourceAET"
                    : "Series.sourceAET", false);
            this.privTables = privTables;
            this.callingAETs = callingAETs != null ? (String[]) callingAETs
                    .clone() : new String[0];
        }

        public boolean isUniveralMatch() {
            return callingAETs.length == 0;
        }

        protected void appendBodyTo(StringBuffer sb) {
            JdbcProperties jp = JdbcProperties.getInstance();
            sb.append("EXISTS (SELECT 1 FROM ");
            sb.append(jp.getProperty(privTables ? "PrivateSeries" : "Series"));
            sb.append(" WHERE ");
            sb.append(jp.getProperty(privTables ? "PrivateSeries.study_fk"
                    : "Series.study_fk"));
            sb.append(" = ");
            sb.append(jp.getProperty(privTables ? "PrivateStudy.pk"
                    : "Study.pk"));
            sb.append(" AND ");
            sb.append(column);
            if (callingAETs.length == 1) {
                sb.append(" = \'").append(callingAETs[0]).append('\'');
            } else {
                sb.append(" IN ('").append(callingAETs[0]);
                for (int i = 1; i < callingAETs.length; i++) {
                    sb.append("\', \'").append(callingAETs[i]);
                }
                sb.append("\')");
            }
            sb.append(" )");
        }
    }

    static class QueryPermissionNestedMatch extends Match {
        private final String[] roles;
        private final boolean patientLevel;
        private final boolean checkForNoStudy;

        public QueryPermissionNestedMatch(boolean patientLevel,
                boolean checkForNoStudy, String[] roles) {
            super(null, "StudyPermission.role", false);
            this.roles = roles;
            this.patientLevel = patientLevel;
            this.checkForNoStudy = checkForNoStudy;
        }

        public boolean isUniveralMatch() {
            return false;
        }

        protected void appendBodyTo(StringBuffer sb) {
            JdbcProperties jp = JdbcProperties.getInstance();
            if (checkForNoStudy) {
                sb.append('(');
                sb.append(jp.getProperty("Study.pk"));
                sb.append(" IS NULL OR ");
            }
            sb.append("EXISTS (SELECT 1 FROM ");
            if (patientLevel) {
                sb.append(jp.getProperty("Study"));
                sb.append(", ");
            }
            sb.append(jp.getProperty("StudyPermission"));
            sb.append(" WHERE ");
            if (patientLevel) {
                sb.append(jp.getProperty("Patient.pk"));
                sb.append(" = ");
                sb.append(jp.getProperty("Study.patient_fk"));
                sb.append(" AND ");
            }
            sb.append(jp.getProperty("Study.studyIuid"));
            sb.append(" = ");
            sb.append(jp.getProperty("StudyPermission.studyIuid"));
            sb.append(" AND ");
            sb.append(jp.getProperty("StudyPermission.action"));
            sb.append(" = 'Q' AND ");
            sb.append(column);
            if (roles.length == 1) {
                sb.append(" = \'").append(roles[0]).append('\'');
            } else {
                sb.append(" IN ('").append(roles[0]);
                for (int i = 1; i < roles.length; i++) {
                    sb.append("\', \'").append(roles[i]);
                }
                sb.append("\')");
            }
            sb.append(')');
            if (checkForNoStudy)
                sb.append(')');
        }
    }

    static class Node extends Match {
        private List<Match> matches = new ArrayList<Match>();
        private final String orORand;
        private final boolean invert;

        public Node(String orORand, boolean invert) {
            this.orORand = orORand;
            this.invert = invert;
        }

        public void addMatch(Match match) {
            matches.add(match);
        }

        public boolean isUniveralMatch() {
            return false;
        }

        protected void appendBodyTo(StringBuffer sb) {
            if (invert)
                sb.append(" NOT");
            sb.append(" ( ");
            Iterator<Match> iter = matches.iterator();
            iter.next().appendTo(sb);
            while (iter.hasNext()) {
                sb.append(orORand);
                iter.next().appendTo(sb);
            }
            sb.append(" )");
        }

        public boolean appendTo(StringBuffer sb) {
            if (matches.isEmpty())
                return false;
            appendBodyTo(sb);
            return true;
        }
    }

    static class Subquery extends Match {
        private final String subQueryStr;

        public Subquery(SqlBuilder subQuery, String field, String alias) {
            subQuery.setSubQueryMode(true);
            StringBuffer sb = new StringBuffer();
            if (field == null) { // correlated
                sb.append("EXISTS (");
            } else {
                String column = JdbcProperties.getInstance().getProperty(field);
                if (column == null)
                    throw new IllegalArgumentException(
                            "field for uncorrelated subquery does not exist: "
                                    + field);
                if (alias != null) {
                    column = alias + column.substring(column.indexOf('.'));
                }
                sb.append(column).append(" IN ");
            }
            sb.append(subQuery.getSql());
            sb.append(")");
            subQueryStr = sb.toString();
        }

        public boolean isUniveralMatch() {
            return false;
        }

        protected void appendBodyTo(StringBuffer sb) {
            sb.append(subQueryStr);
        }

    }

    public static class PNFuzzy extends Match {

        private final boolean type_2;
        private final String[] columns;
        private final String[] soundex;

        public PNFuzzy(String[] fields, boolean type2, PersonName pn) {
            type_2 = type2;
            columns = JdbcProperties.getInstance().getProperties(fields);
            soundex = new String[] {
                    AttributeFilter.toSoundexWithLike(pn, PersonName.FAMILY),
                    AttributeFilter.toSoundexWithLike(pn, PersonName.GIVEN)
            };
        }

        private static String like(String soundex) {
            return soundex != null && soundex.endsWith("%") ? " LIKE '" : " = '";
        }

        @Override
        protected void appendBodyTo(StringBuffer sb) {
            if (soundex[0] != null && soundex[1] != null) {
                String[] like = { like(soundex[0]), like(soundex[1]) };
                if (type_2) {
                    sb.append('(')
                        .append(columns[0])
                        .append(like[0])
                        .append(soundex[0])
                        .append("' OR ")
                        .append(columns[0])
                        .append(" = '*') AND (")
                        .append(columns[1])
                        .append(like[1])
                        .append(soundex[1])
                        .append("' OR ")
                        .append(columns[1])
                        .append(" = '*') OR (")
                        .append(columns[0])
                        .append(like[1])
                        .append(soundex[1])
                        .append("' OR ")
                        .append(columns[0])
                        .append(" = '*') AND (")
                        .append(columns[1])
                        .append(like[0])
                        .append(soundex[0])
                        .append("' OR ")
                        .append(columns[1])
                        .append(" = '*')");
                } else {
                    sb.append(columns[0])
                        .append(like[0])
                        .append(soundex[0])
                        .append("' AND ")
                        .append(columns[1])
                        .append(like[1])
                        .append(soundex[1])
                        .append("' OR ")
                        .append(columns[0])
                        .append(like[1])
                        .append(soundex[1])
                        .append("' AND ")
                        .append(columns[1])
                        .append(like[0])
                        .append(soundex[0])
                        .append("'");
                }
            } else {
                String soundex0 = soundex[soundex[0] != null ? 0 : 1];
                String like0 = like(soundex0);
                sb.append(columns[0])
                    .append(like0)
                    .append(soundex0)
                    .append("' OR ")
                    .append(columns[1])
                    .append(like0)
                    .append(soundex0);
                if (type_2) {
                    sb.append("' OR (")
                        .append(columns[0])
                        .append(" = '*' AND ")
                        .append(columns[1])
                        .append(" = '*')");
                } else {
                    sb.append("'");
                }
            }
        }

        @Override
        public boolean isUniveralMatch() {
            return soundex[0] == null && soundex[1] == null;
        }

    }

}
