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

package org.dcm4chex.archive.hsm;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;

import javax.ejb.CreateException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.dcm4che.data.Dataset;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.ContentEdit;
import org.dcm4chex.archive.ejb.interfaces.ContentEditHome;
import org.dcm4chex.archive.ejb.jdbc.QueryFilecopyCmd;
import org.dcm4chex.archive.mbean.SchedulerDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.HomeFactoryException;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author Franz Willer <franz.willer@gmail.com>
 * @version $Revision$ $Date$
 * @since Oct 4, 2010
 */

public class FileCopyByQueryService extends ServiceMBeanSupport implements NotificationListener {

    private static final String NONE = "NONE";
    private static final String LF = System.getProperty("line.separator", "\n");

    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    private long pollInterval = 0L;
    private long delay = 0L;
    private int limit = 2000;
    private long lastSeriesPk = 0;
    private int fetchSize;
    
    private Integer schedulerID;
    private String timerIDFilecopyPolling;
    private boolean isRunning;

    private ObjectName filecopyServiceName;
    
    private String sql;
    private QueryFilecopyCmd sqlCmd;
    private boolean sqlIsValid = false;
    String lastCheckResult = null;
    
    private ContentEdit contentEdit;
    
    public final String getPollInterval() {
        return RetryIntervalls.formatIntervalZeroAsNever(pollInterval);
    }

    public void setPollInterval(String interval) throws Exception {
        long l = RetryIntervalls.parseIntervalOrNever(interval);
        if (l != pollInterval) {
            this.pollInterval = l;
            if (getState() == STARTED) {
                scheduler.stopScheduler(timerIDFilecopyPolling, schedulerID, this);
                schedulerID = scheduler.startScheduler(timerIDFilecopyPolling,
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
                    log.error("Query String not valid!", t);
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
            QueryFilecopyCmd cmd = QueryFilecopyCmd.getInstance(sql, this.limit > 0 ? 1 : 0, 1);
            cmd.setUpdateDatabaseMaxRetries(1);
            List<Long> chk = cmd.getSeriesPKs(sql.indexOf('?') != -1 ? System.currentTimeMillis() : null, lastSeriesPk);
            if (log.isDebugEnabled()) 
                log.debug("CheckSQL: QueryFilecopyCmd.getSeriesIUIDs done with result:"+chk);
        } catch (SQLException x) {
            lastCheckResult = x.getCause() == null ? x.toString() : x.getCause().toString();
            throw x;
        }
        lastCheckResult="OK";
        sqlIsValid = true;
    }

    private void updateCmd() throws SQLException {
        if (log.isDebugEnabled())
            log.debug("update QueryFilecopyCmd when state = 3. state:"+getState()+" limit:"+limit+"\nSQL:"+sql);
        if ( this.getState() == STARTED) {
            if ( sqlCmd != null ) {
                sqlCmd.close();
            }
            sqlCmd = QueryFilecopyCmd.getInstance(sql, limit, fetchSize);
        }
    }

    public String getDelay() {
        return delay < 0 ? NONE : RetryIntervalls.formatInterval(delay);
    }

    public void setDelay(String delay) {
        this.delay = NONE.equals(delay) ? -1 : RetryIntervalls.parseInterval(delay);
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

    public String getTimerIDFilecopyByQuery() {
        return timerIDFilecopyPolling;
    }

    public void setTimerIDFilecopyByQuery(
            String timerID) {
        this.timerIDFilecopyPolling = timerID;
    }

    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public final ObjectName getFilecopyServiceName() {
        return filecopyServiceName;
    }

    public final void setFilecopyServiceName(ObjectName name) {
        this.filecopyServiceName = name;
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
                    doCheckFilecopy();
                } catch (Exception e) {
                    log.error("Check for Pending Series Stored failed:", e);
                } finally {
                    isRunning = false;
                }
            }}).start();
    }
    
    public int checkFilecopy() {
        synchronized(this) {
            if (isRunning) {
                log.info("FilecopyByQuery is already running!");
                return -1;
            }
            isRunning = true;
        }
        return doCheckFilecopy();
    }
    
    public int doCheckFilecopy() {
        if (lastCheckResult == null) {
            try {
                checkSQL(sql);
                if (sqlCmd != null) { 
                    sqlCmd.close();
                }
                sqlCmd = QueryFilecopyCmd.getInstance(sql, limit, fetchSize);
            } catch ( Throwable t) {
                log.error("Check of SQL statement failed!",t);
            }
        }
        if (!sqlIsValid) {
            log.warn("SQL is not marked to be valid! checkFilecopy is disabled!");
            isRunning = false;
            return -1;
        }
        int nrOfOrders = 0;
        int notScheduledOrders = 0;
        try {
            log.debug("SQL used to find series for copy:"+sqlCmd.getSQL());
            List<Long> seriesPks = sqlCmd.getSeriesPKs(delay < 0 ? null : new Long(System.currentTimeMillis()-delay), lastSeriesPk);
            log.info("Found "+seriesPks.size()+" Series for FileCopy! lastSeriesPk:"+lastSeriesPk);
            Dataset ian;
            for (int i=0, len=seriesPks.size() ; i < len ; i++) {
                ian = lookupContentEdit().getStudyMgtDatasetForSeries(new long[]{seriesPks.get(i)});
                if (log.isDebugEnabled()) {
                    log.debug("IAN for series pk="+seriesPks.get(i)+" IAN:");log.debug(ian);
                }
                if (ian != null) {
                    if (scheduleFilecopyOrder(ian))
                        nrOfOrders++;
                    else
                        notScheduledOrders++;
                }
            }
            if (notScheduledOrders > 0)
                log.warn(notScheduledOrders+" Order(s) of FileCopyByQuery service not scheduled! Please check configuration in FileCopy service!");
            lastSeriesPk = seriesPks.size() > 0 ? seriesPks.get(seriesPks.size()-1) : 0L;
            return nrOfOrders;
        } catch (Exception e) {
            log.error("Error while checking series for Filecopy! Already scheduled filecopy orders:"+nrOfOrders, e);
            return nrOfOrders;
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
                sqlCmd = QueryFilecopyCmd.getInstance(sql, limit, fetchSize);
            } catch ( Throwable t) {
                return "Check of SQL statement failed!";
            }
        }
        return !sqlIsValid ? "SQL is not marked to be valid!" : sqlCmd.formatSql();
    }

    protected boolean scheduleFilecopyOrder(Dataset ian) {
        try {
           return (Boolean) server.invoke(filecopyServiceName, "scheduleByIAN", new Object[] {
                    ian, -1L },
                    new String[] { Dataset.class.getName(), long.class.getName() });
        } catch (Exception e) {
            log.error("Schedule FileCopy Order failed:", e);
            return false;
        }
    }
    
    protected void startService() throws Exception {
        schedulerID = scheduler.startScheduler(timerIDFilecopyPolling,
                pollInterval, this);
    }

    protected void stopService() throws Exception {
        scheduler.stopScheduler(timerIDFilecopyPolling, schedulerID, this);
        super.stopService();
    }
    
    private ContentEdit lookupContentEdit() throws HomeFactoryException, RemoteException, CreateException {
        if (contentEdit != null)
            return contentEdit;
        ContentEditHome home = (ContentEditHome) EJBHomeFactory.getFactory()
            .lookup(ContentEditHome.class, ContentEditHome.JNDI_NAME);
        contentEdit = home.create();
        return contentEdit;
    }

}
