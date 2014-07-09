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

package org.dcm4chex.archive.dcm.movescu;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.dcm4chex.archive.config.DicomPriority;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.jdbc.QueryForwardCmd;
import org.dcm4chex.archive.mbean.SchedulerDelegate;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author Franz Willer <franz.willer@gmail.com>
 * @version $Revision$ $Date$
 * @since Sep 4, 2009
 */

public class ForwardByQueryService extends ServiceMBeanSupport implements
NotificationListener {

    private static final String NONE = "NONE";
    private static final String LF = System.getProperty("line.separator", "\n");

    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    private long pollInterval = 0L;
    private long delay = 0L;
    private int limit = 2000;
    private long lastSeriesPk = 0;
    private int fetchSize;
    
    private String calledAET;
    private int forwardPriority = 0;

    private Integer schedulerID;
    private String timerIDForwardPolling;
    private boolean isRunning;

    private ObjectName moveScuServiceName;
    
    private String sql;
    private QueryForwardCmd sqlCmd;
    private boolean sqlIsValid = false;
    String lastCheckResult = null;    
    
    public final String getCalledAET() {
        return calledAET;
    }

    public final void setCalledAET(String calledAET) {
        this.calledAET = calledAET;
    }

    public final String getPollInterval() {
        return RetryIntervalls.formatIntervalZeroAsNever(pollInterval);
    }

    public void setPollInterval(String interval) throws Exception {
        long l = RetryIntervalls.parseIntervalOrNever(interval);
        if (l != pollInterval) {
            this.pollInterval = l;
            if (getState() == STARTED) {
                scheduler.stopScheduler(timerIDForwardPolling, schedulerID, this);
                schedulerID = scheduler.startScheduler(timerIDForwardPolling,
                        pollInterval, this);
            }
        }
    }
    
    public String getQuery() {
        return sql+LF;
    }
    
    public void setQuery(String newSql) throws SQLException {
        newSql = newSql.trim().replaceAll("\\s\\s+", " ");;
        if (!newSql.equals(sql)) {
            sql = newSql;
            if ( this.getState() == STARTED) 
                try {
                    checkSQL(newSql);
                    updateCmd();
                } catch ( Throwable t) {
                    log.error("Query String nor valid!", t);
                }
        }
    }
    
    public String getLastQueryCheckResult() {
        return lastCheckResult == null ? "NOT CHECKED" : lastCheckResult;
    }

    public boolean isRunning() {
        return isRunning;
    }
        
    public long getLastSeriesPk() {
        return lastSeriesPk;
    }

    private void checkSQL(String sql) throws SQLException {
        sqlIsValid = false;
        String sqlUC = sql.toUpperCase();
        if ( sqlUC.indexOf("DELETE ") != -1 ) {
            lastCheckResult = "DELETE is not allowed in this SQL statement!";
            throw new IllegalArgumentException(lastCheckResult);
        }
        if ( sqlUC.indexOf("UPDATE ") != -1 ) {
            lastCheckResult = "UPDATE is not allowed in this SQL statement!";
            throw new IllegalArgumentException(lastCheckResult);
        }
        try {
            QueryForwardCmd cmd = QueryForwardCmd.getInstance(sql, this.limit > 0 ? 1 : 0, 1);
            cmd.setUpdateDatabaseMaxRetries(1);
            Map<String, List<String>> chk = cmd.getSeriesIUIDs(sql.indexOf('?') != -1 ? System.currentTimeMillis() : null, 0l);
            log.debug("CheckSQL: QueryForwardCmd.getSeriesIUIDs done with result:"+chk);
            if ( chk != null && !chk.isEmpty()) {
                if ( chk.keySet().iterator().next().indexOf('.') != -1 ) {
                    lastCheckResult = "Wrong result! RetrieveAET contains '.'! Query must return series.series_iuid, series.retrieve_aets as first columns!";
                    throw new IllegalArgumentException(lastCheckResult);
                }
                List<String> l = chk.values().iterator().next();
                if ( !l.isEmpty() && l.iterator().next().indexOf('.') == -1) {
                    lastCheckResult = "Wrong result! Series Instance UID does NOT contain a '.'! Query must return series.series_iuid, series.retrieve_aets as first columns!";
                    throw new IllegalArgumentException(lastCheckResult);
                }
            }
        } catch (SQLException x) {
            lastCheckResult = x.getCause() == null ? x.toString() : x.getCause().toString();
            throw x;
        }
        lastCheckResult="OK";
        sqlIsValid = true;
    }

    private void updateCmd() throws SQLException {
        if (log.isDebugEnabled())
            log.debug("update QueryForwardCmd when state = 3. state:"+getState()+" limit:"+limit+"\nSQL:"+sql);
        if ( this.getState() == STARTED) {
            if ( sqlCmd != null ) {
                sqlCmd.close();
            }
            sqlCmd = QueryForwardCmd.getInstance(sql, limit, fetchSize);
        }
    }

    public String getDelay() {
        return delay < 0 ? NONE : RetryIntervalls.formatInterval(delay);
    }

    public void setDelay(String delay) {
        this.delay = NONE.equals(delay) ? -1 : RetryIntervalls.parseInterval(delay);
    }

    public final String getForwardPriority() {
        return DicomPriority.toString(forwardPriority);
    }

    public final void setForwardPriority(String forwardPriority) {
        this.forwardPriority = DicomPriority.toCode(forwardPriority);
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) throws SQLException {
        if ( this.limit != limit ) {
            this.limit = limit;
            updateCmd();
        }
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public String getTimerIDForwardByQuery() {
        return timerIDForwardPolling;
    }

    public void setTimerIDForwardByQuery(
            String timerID) {
        this.timerIDForwardPolling = timerID;
    }

    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public final ObjectName getMoveScuServiceName() {
        return moveScuServiceName;
    }

    public final void setMoveScuServiceName(ObjectName moveScuServiceName) {
        this.moveScuServiceName = moveScuServiceName;
    }
    
    public void handleNotification(Notification notification, Object handback) {
        synchronized(this) {
            if (isRunning) {
                log.info("FileCopyByQuery is already running!");
                return;
            }
            isRunning = true;
        }
        new Thread(new Runnable(){
            public void run() {
                try {
                    doCheckForward();
                } catch (Exception e) {
                    log.error("Check for Pending Series Stored failed:", e);
                } finally {
                    isRunning = false;
                }
            }}).start();
    }
    
    public int checkForward() {
        synchronized(this) {
            if (isRunning) {
                log.info("ForwardByQuery is already running!");
                return -1;
            }
            isRunning = true;
        }
        return doCheckForward();
    }
    
    public int doCheckForward() {
        if (lastCheckResult == null) {
            try {
                checkSQL(sql);
                if (sqlCmd != null) { 
                    sqlCmd.close();
                }
                sqlCmd = QueryForwardCmd.getInstance(sql, limit, fetchSize);
            } catch ( Throwable t) {
                log.error("Check of SQL statement failed!",t);
            }
        }
        if (!sqlIsValid) {
            log.warn("SQL is not marked to be valid! checkForward is disabled!");
            isRunning = false;
            return -1;
        }
        int nrOfSeries = 0;
        try {
            long scheduledTime = 0L;
            Map<String,List<String>> orders = sqlCmd.getSeriesIUIDs(delay < 0 ? null : new Long(System.currentTimeMillis()-delay), lastSeriesPk);
            log.info("Found "+orders.size()+" MoveOrder! lastSeriesPk:"+lastSeriesPk);
            lastSeriesPk = orders.isEmpty() ? 0 : sqlCmd.getLastSeriesPk();
            MoveOrder order;
            List<String> series;
            for ( Map.Entry<String, List<String>> entry : orders.entrySet() ) {
                series = entry.getValue();
                nrOfSeries += series.size();
                for (int i = 0, len = series.size() ; i < len ; i++) {
                    order = new MoveOrder(entry.getKey(), calledAET, forwardPriority, null, null, series.get(i), null);
                    this.scheduleMove(order, scheduledTime);
                }
            }
            return nrOfSeries;
        } catch (Exception e) {
            log.error("Failed to check for forward:", e);
            return 0;
        } finally {
            isRunning = false;
        }
    }
    
    public String showSQL() {
        if (lastCheckResult == null) {
            try {
                checkSQL(sql);
                if (sqlCmd != null) { 
                    sqlCmd.close();
                }
                sqlCmd = QueryForwardCmd.getInstance(sql, limit, fetchSize);
            } catch ( Throwable t) {
                return "Check of SQL statement failed!";
            }
        }
        return !sqlIsValid ? "SQL is not marked to be valid!" : sqlCmd.formatSql();
    }

    protected void scheduleMove(MoveOrder order, long scheduledTime) {
        try {
           server.invoke(moveScuServiceName, "scheduleMoveOrder", new Object[] {
                    order, new Long(scheduledTime) },
                    new String[] { MoveOrder.class.getName(), long.class.getName() });
        } catch (Exception e) {
            log.error("Schedule Move failed:", e);
        }
    }
    
    protected void startService() throws Exception {
        schedulerID = scheduler.startScheduler(timerIDForwardPolling,
                pollInterval, this);
    }

    protected void stopService() throws Exception {
        scheduler.stopScheduler(timerIDForwardPolling, schedulerID, this);
        super.stopService();
    }
}
