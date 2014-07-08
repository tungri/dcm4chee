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
 * Portions created by the Initial Developer are Copyright (C) 2005
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

package org.dcm4chex.archive.hsm.module.castor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.hsm.module.AbstractHSMModule;
import org.dcm4chex.archive.hsm.module.HSMException;
import org.dcm4chex.archive.util.FileUtils;

import com.caringo.client.ResettableFileInputStream;
import com.caringo.client.ScspClient;
import com.caringo.client.ScspDate;
import com.caringo.client.ScspDeleteConstraint;
import com.caringo.client.ScspExecutionException;
import com.caringo.client.ScspHeaders;
import com.caringo.client.ScspLifepoint;
import com.caringo.client.ScspQueryArgs;
import com.caringo.client.ScspResponse;

/**
 * @author Daniel Chaffee dan.chaffee@gmail.com
 * @version $Revision:  $ $Date: $
 * @since Sep 06, 2013
 */

public class CAStorHSMModule extends AbstractHSMModule
{
    /**
     * The log4j logger used by this class.
     */
    private static Logger logger = Logger.getLogger(CAStorHSMModule.class);

    /**
     * The hostname of the CAStor server (Primary Access Node).
     */
    private String hostname;
    /**
     * The port of the CAStor server for SCSP communications.
     */
    private int port;
    /**
     * The maximum connection pool size used by the SCSP client. The recommended
     * value is the number of threads multiplied by the number of CAStor cluster
     * nodes.
     */
    private int maxConnectionPoolSize;
    /**
     * The maximum number of retries that the SCSP client is allowed.
     */
    private int maxRetries;
    /**
     * The connection timeout (in seconds) used by the SCSP client.
     */
    private int connectionTimeout;
    /**
     * The pool timeout (in seconds) used by the SCSP client.
     */
    private int poolTimeout;
    /**
     * The locator retry timeout (in seconds) used by the SCSP client.
     */
    private int locatorRetryTimeout;
    /**
     * The SCSP client.
     */
    private ScspClient client;

    /**
     * The directory where study tarballs are temporarily saved after they are
     * retrieved from CAStor.
     */
    private File incomingDir;
    /**
     * The directory (represented by an absolute path) where study tarballs are
     * temporarily saved before they are retrieved from CAStor.
     */
    private File absoluteIncomingDir;
    /**
     * The directory where study tarballs are temporarily saved before they are
     * sent to CAStor.
     */
    private File outgoingDir;
    /**
     * The directory (represented by an absolute path) where study tarballs are
     * temporarily saved before they are sent to CAStor.
     */
    private File absoluteOutgoingDir;

    /**
     * The period of time (in milliseconds) for which a study must remain in
     * nearline storage before being deleted.
     */
    private long retentionPeriod;

    static {
        // Suppress the very annoying Apache HTTPClient wire content dump
        Logger.getLogger("httpclient.wire.content").setLevel(Level.INFO);
    }

    /**
     * Return the hostname of the CAStor sever (the Primary Access Node).
     * 
     * @return The CAStor server hostname.
     */
    public String getHostname() {
        return hostname;
    }
    /**
     * Set the hostname of the CAStor sever (the Primary Access Node).
     * 
     * @param aHostname
     *            The desired CAStor server hostname.
     */
    public void setHostname(String aHostname) {
        if (!aHostname.equalsIgnoreCase(hostname)) {
            hostname = aHostname;
            destroyClient();
        }
    }

    /**
     * Return the port of the CAStor server for SCSP communications.
     * 
     * @return The CAStor server port.
     */
    public int getPort() {
        return port;
    }
    /**
     * Set the port of the CAStor server for SCSP communications.
     * 
     * @param aPort The desired port.
     */
    public void setPort(int aPort) {
        if (port != aPort) {
            if (aPort < 1 && aPort > 65535)
                throw new IllegalArgumentException("Invalid port number! Must be 1 - 65535");
            port = aPort;
            destroyClient();
        }
    }


    /**
     * Return the maximum connection pool size used by the SCSP client.
     * 
     * @return The maximum connection pool size.
     */
    public int getMaxConnectionPoolSize() {
        return maxConnectionPoolSize;
    }

    /**
     * Set the maximum connection pool size used by the SCSP client.
     * 
     * @param aPoolSize The desired maximum connection pool size.
     */
    public void setMaxConnectionPoolSize(int aPoolSize) {
        if (maxConnectionPoolSize != aPoolSize) {
            maxConnectionPoolSize = aPoolSize;
            destroyClient();
        }
    }

    /**
     * Return the maximum number of retries that the SCSP client is allowed.
     * 
     * @return The maximum number of retries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Set the maximum number of retries that the SCSP client is allowed.
     * 
     * @param aMaxRetries The desired maximum number of retries.
     */
    public void setMaxRetries(int aMaxRetries) {
        if (maxRetries != aMaxRetries) {
            maxRetries = aMaxRetries;
            destroyClient();
        }
    }

    /**
     * Return the connection timeout used by the SCSP client.
     * 
     * @return The connection timeout (in seconds).
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Set the connection timeout used by the SCSP client.
     * 
     * @param aConnectionTimeout The desired connection timeout (in seconds).
     */
    public void setConnectionTimeout(int aConnectionTimeout) {
        if (connectionTimeout != aConnectionTimeout) {
            connectionTimeout = aConnectionTimeout;
            destroyClient();
        }
    }

    /**
     * Return the pool timeout used by the SCSP client.
     * 
     * @return The pool timeout (in seconds).
     */
    public int getPoolTimeout() {
        return poolTimeout;
    }

    /**
     * Set the pool timeout used by the SCSP client.
     * 
     * @param aPoolTimeout
     *            The desired pool timeout (in seconds).
     */
    public void setPoolTimeout(int aPoolTimeout) {
        if (poolTimeout != aPoolTimeout) {
            poolTimeout = aPoolTimeout;
            destroyClient();
        }
    }

    /**
     * Return the locator retry timeout used by the SCSP client.
     * 
     * @return The locator retry timeout (in seconds).
     */
    public int getLocatorRetryTimeout() {
        return locatorRetryTimeout;
    }

    /**
     * Set the locator retry timeout used by the SCSP client.
     * 
     * @param aLocatorTimeout The desired locator retry timeout (in seconds).
     */
    public void setLocatorRetryTimeout(int aLocatorTimeout) {
        if (locatorRetryTimeout != aLocatorTimeout) {
            locatorRetryTimeout = aLocatorTimeout;
            destroyClient();
        }
    }

    /**
     * Return the path to the directory where study tarballs are temporarily
     * saved after they are retrieved from CAStor.
     * 
     * @return The path to the directory for incoming tarballs.
     */
    public String getIncomingDir() {
        return incomingDir.getPath();
    }

    /**
     * Set the path to the directory where study tarballs are temporarily saved
     * after they are retrieved from CAStor.
     * 
     * @param anIncomingDir
     *            The path to the desired directory for the incoming tarballs.
     */
    public void setIncomingDir(String anIncomingDir) {
        incomingDir = new File(anIncomingDir);
        absoluteIncomingDir = FileUtils.resolve(incomingDir);
    }

    /**
     * Return the path to the directory where study tarballs are temporarily
     * saved before they are sent to CAStor.
     * 
     * @return The path to the directory for outgoing tarballs.
     */
    public String getOutgoingDir() {
        return outgoingDir.getPath();
    }

    /**
     * Set the path to the directory where study tarballs are temporarily saved
     * before they are sent to CAStor.
     * 
     * @param anOutgoingDir
     *            The path to the desired directory for outgoing tarballs.
     */
    public void setOutgoingDir(String anOutgoingDir) {
        outgoingDir = new File(anOutgoingDir);
        absoluteOutgoingDir = FileUtils.resolve(outgoingDir);
    }

    /**
     * Return a human-readable representation of the retention period used by
     * the implemented retention policy.
     * 
     * @return The retention period.
     */
    public String getRetentionPeriod()
    {
        return RetryIntervalls.formatInterval(retentionPeriod);
    }

    /**
     * Set the retention period used by the implemented retention policy.
     * 
     * @param aPeriod
     *            A string representation of the retention period, e.g. "52w" or
     *            "365d".
     */
    public void setRetentionPeriod(String aPeriod) {
        retentionPeriod = RetryIntervalls.parseInterval(aPeriod);
    }

    /**
     * Initialize and start the SCSP client.
     * 
     * @throws RuntimeException
     */
    private void createClient() throws RuntimeException {
        String[] hosts = { hostname };
        client = new ScspClient(
            hosts,
            port,
            maxConnectionPoolSize,
            maxRetries,
            connectionTimeout,
            poolTimeout,
            locatorRetryTimeout);
        logger.info("SCSP client created for CAStor server " + hostname + ":" + port);

        try {
            client.start();
            logger.info("CAStor client started");
        } catch (IOException e) {
            throw new RuntimeException("Could not start CAStor client", e);
        }
    }

    /**
     * Terminate and destroy the SCSP client.
     */
    private void destroyClient() {
        if (client != null) {
            try {
                client.stop();
                logger.info("CAStor client terminated");
            } catch (Exception e) {
                logger.warn( "CAStor client may not have terminated - destroy anyway", e);
            }
            client = null;
        }
    }

    /**
     * The method that is called by the FileCopy service when it fails to copy a
     * study tarball to nearline storage (i.e. CAStor) using this HSM module.
     * 
     * @param file
     *            The tar file that has failed to be copied to nearline storage.
     * @param fsID
     *            The file system ID for nearline storage.
     * @param filePath
     *            The relative path to the study tarball in nearline storage.
     * @throws HSMException
     */
    @Override
    public void failedHSMFile(File file, String fsID, String filePath)
        throws HSMException {
        logger.error("failedHSMFile called with file=" + file + ", fsID="
            + fsID + ", filePath=" + filePath);
    }

    /**
     * The method that is called by the TarRetriever service to retrieve a study
     * tarball from nearline storage (i.e. CAStor).
     * 
     * @param fsID
     *            The file system ID for nearline storage.
     * @param filePath
     *            The relative path to the study tarball in nearline storage.
     * @return The retrieved tar file.
     * @throws HSMException
     */
    @Override
    public File fetchHSMFile(String fsID, String filePath) throws HSMException {
        logger.debug("fetchHSMFile called with fsID=" + fsID + ", filePath="
            + filePath);

        // First, we have to make sure that the path for storing the received
        // tar file exists. File.mkdirs() will create the missing directories
        if (absoluteIncomingDir.mkdirs()) {
            // One or more directories have been created.
            logger.info("M-WRITE " + absoluteIncomingDir);
        }

        // Then we create an empty tar file in the incoming directory
        File tarFile = null;
        try {
            // Name the tar file with prefix "hsm_" and suffix ".tar"
            tarFile = File.createTempFile("hsm_", ".tar", absoluteIncomingDir);
        } catch (IOException e) {
            throw new HSMException("Failed to create temp file in "
                + absoluteIncomingDir, e);
        }

        // We open the created tar file in write mode
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tarFile);
        } catch (FileNotFoundException e) {
            throw new HSMException("Could not open file " + tarFile, e);
        }

        // filePath is just the object UUID
        String uuid = filePath;

        logger .info("Downloading object " + uuid + " from CAStor as " + tarFile);

        // Read the CAStor object and fill in the temporary tar file
        try {
            if (client == null) {
                createClient();
            }

            // The first parameter for ScspClient.read (String UUID) is not used
            // when requesting a named object and hence is set to empty
            ScspResponse response = client.read(
                uuid,
                "",
                fos,
                new ScspQueryArgs(),
                new ScspHeaders());

            switch (response.getHttpStatusCode()) {
            case HttpStatus.SC_OK:
                break;

            default:
                logger .error("Unexpected READ response: " + response.toString());
            }
        } catch (ScspExecutionException e) {
            throw new HSMException("Could not read CAStor object " + uuid, e);
        } catch (Exception e) {
            throw new HSMException("Could not retrieve " + filePath
                + " from nearline storage", e);
        } finally {
            // Always close the opened file
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    logger.error( "Could not close output stream for " + tarFile, e);
                }
            }
        }

        return tarFile;
    }

    /**
     * The method that is called by the TarRetriever and SyncFileStatus services
     * when they finish fetching a study tarball from nearline storage (i.e.
     * CAStor) using this HSM module.
     * 
     * @param fsID
     *            The file system ID for nearline storage.
     * @param filePath
     *            The relative path to the study tarball in nearline storage.
     * @param file
     *            The retrieved tar file.
     * @throws HSMException
     * @see {@link org.dcm4chex.archive.hsm.module.AbstractHSMModule#fetchHSMFileFinished(String, String, File)}
     */
    @Override
    public void fetchHSMFileFinished(String fsID, String filePath, File file)
        throws HSMException {
        logger.debug("fetchHSMFileFinished called with fsID=" + fsID
            + ", filePath=" + filePath + ", file=" + file);
        logger.info("M-DELETE " + file);
        file.delete();
    }

    /**
     * The method that is called by the FileCopy service when it is about to
     * pack the study files into a tarball, in order to copy it to nearline
     * storage (i.e. CAStor) later on.
     * 
     * @param fsID
     *            The file system ID for nearline storage.
     * @param filePath
     *            The relative path to the study files in online storage.
     * @return The tar file to create (i.e. this <code>File</code> object
     *         indicates the name and location for the tarball).
     * @throws HSMException
     */
    @Override
    public File prepareHSMFile(String fsID, String filePath)
        throws HSMException {
        logger.debug("prepareHSMFile called with fsID=" + fsID + ", filePath="
            + filePath);
        // filePath looks like
        // "<year>/<month>/<day>/<hour>/<study_hash>/<series_hash>-<msecs>.tar"
        // There is no need to create the entire directory structure for this
        // tarball in the outgoing directory, so we only take the file name
        return new File(absoluteOutgoingDir, new File(filePath).getName());
    }

    /**
     * The method that is called by the SyncFileStatus service to check the
     * status of a study tarball in nearline storage (i.e. on CAStor).
     * 
     * @param fsID
     *            The file system ID for nearline storage.
     * @param filePath
     *            The relative path to the study tarball in nearline storage.
     * @param userInfo
     *            The user information for the tar file.
     * @return A <code>FileStatus</code> constant that denotes the status of the
     *         queried file.
     * @throws HSMException
     */
    @Override
    public Integer queryStatus(String fsID, String filePath, String userInfo)
        throws HSMException {
        logger.debug("queryStatus called with fsID=" + fsID + ", filePath="
            + filePath + ", userInfo=" + userInfo);

        // filePath is just the object UUID
        String uuid = filePath;

        logger.debug("Querying CAStor for object " + uuid);

        try {
            if (client == null) {
                createClient();
            }

            // The first parameter for ScspClient.info (String UUID) is not used
            // when requesting a named object and hence is set to empty
            ScspResponse response = client.info(
                uuid,
                "",
                new ScspQueryArgs(),
                new ScspHeaders());
            switch (response.getHttpStatusCode()) {
            case HttpStatus.SC_OK:
                return FileStatus.ARCHIVED;

            case HttpStatus.SC_NOT_FOUND:
                break;

            default:
                logger .error("Unexpected INFO response: " + response.toString());
            }
        } catch (ScspExecutionException e) {
            throw new HSMException(
                "Could not query CAStor for object " + uuid,
                e);
        } catch (Exception e) {
            throw new HSMException("Could not get the status of " + filePath
                + " in nearline storage", e);
        }

        return FileStatus.DEFAULT;
    }

    /**
     * The method that is called by the FileCopy service to copy a study tarball
     * to nearline storage (i.e. CAStor).
     * 
     * @param file
     *            The tar file to copy.
     * @param fsID
     *            The file system ID for nearline storage.
     * @param filePath
     *            The relative path to the study tarball in online storage.
     * @return The relative path to the study files in nearline storage.
     * @throws HSMException
     */
    @Override
    public String storeHSMFile(File file, String fsID, String filePath)
        throws HSMException {
        logger.debug("storeHSMFile called with file=" + file + ", fsID=" + fsID
            + ", filePath=" + filePath);

        // Open the tar file in read mode
        ResettableFileInputStream fis = null;
        try {
            fis = new ResettableFileInputStream(file);
        } catch (IOException e) {
            throw new HSMException("Could not open file " + file, e);
        }

        String newFilePath = null;
        ScspHeaders scspHeaders = new ScspHeaders();
        addStudyLifepoint(scspHeaders, file, filePath);

        logger.info("Uploading " + file + " to CAStor");

        // Read the tar file and write the data to the CAStor object
        try {
            if (client == null) {
                createClient();
            }

            ScspResponse response = client.write(
                "",
                fis,
                file.length(),
                new ScspQueryArgs(),
                scspHeaders);
            switch (response.getHttpStatusCode()) {
            case HttpStatus.SC_CREATED:
            case HttpStatus.SC_ACCEPTED:
                newFilePath = extractUUIDFromScspResponse(response);
                break;

            default:
                logger.error("Unexpected WRITE response: "
                    + response.toString());
            }
        } catch (ScspExecutionException e) {
            throw new HSMException("Could not upload " + file + " to CAStor", e);
        } catch (Exception e) {
            throw new HSMException("Could not store " + filePath
                + " in nearline storage", e);
        } finally {
            // Always close the opened file
            if (fis != null) {
                try  {
                    fis.close();
                } catch (IOException e) {
                    logger.error("Could not close input stream for " + file, e);
                }
            }

            // Delete the temporary tar file
            if (!file.delete()) {
                logger.warn("Could not delete temporary file: " + file);
            }
        }

        return newFilePath;
    }

    /**
     * Extract the object UUID from an SCSP response (e.g. an SCSP READ
     * response).
     * 
     * @param response
     *            The SCSP response which is expected to contain a UUID.
     * @return The extracted UUID, or <code>null</code> if extraction fails.
     */
    private static String extractUUIDFromScspResponse(ScspResponse response) {
        String uuid = null;

        try {
            uuid = response.getResponseHeaders()
                .getHeaderValues("Content-UUID").get(0);
        } catch (Exception e) {
            logger .error("Could not extract the UUID from the SCSP response", e);
            logger.error(response.toString());
        }

        return uuid;
    }

    /**
     * Add to a SCSP WRITE request the appropriate lifepoint header for the
     * study tarball to be stored on CAStor.
     * 
     * @param headers
     *            An existing set of SCSP headers to which the lifepoint header
     *            will be added.
     * @param file
     *            The study tar file that is going to be stored on CAStor.
     * @param filePath
     *            The relative path to the study files in online storage.
     */
    private void addStudyLifepoint(ScspHeaders headers, File file, String filePath) {
        // Obtain the date before which the study tarball must remain in
        // nearline storage
        ScspDate deletionDate = new ScspDate(getStudyDeletionDate(file, filePath));
        logger.info("Lifepoint for " + filePath
                + " in nearline storage has been set to "
                + deletionDate.toString());

        // The lifepoint header that needs to be included in the SCSP WRITE
        // request is
        // "Lifepoint: [<earliest date/time of deletion in GMT>] deletable=no",
        // which prevents the study tarball from being deleted from CAStor
        // before the specified point of time
        ScspLifepoint noDeleteBeforeDate = new ScspLifepoint(
            deletionDate,
            ScspDeleteConstraint.NOT_DELETABLE);
        headers.addLifepoint(
            noDeleteBeforeDate.getEndPolicyDate(),
            noDeleteBeforeDate.getDeleteConstraint(),
            noDeleteBeforeDate.getMinReps());
    }

    /**
     * Obtain the point of time before which the given study must remain in
     * nearline storage (i.e. on CAStor). The current implementation is
     * only a proof-of-concept; the real retention policy may not be this
     * simple.
     * 
     * @param file
     *            The study tar file that is going to be stored on CAStor.
     * @param filePath
     *            The relative path to the study files in online storage.
     * @return The <code>Date</code> object that represents the earliest
     *         date/time of deletion for the study.
     */
    private Date getStudyDeletionDate(File file, String filePath) {
        // Obtain a calendar object whose time is set to the current date and
        // time
        Calendar calendar = Calendar.getInstance();
        // TODO: Replace the following with implementation of an actual
        // retention policy
        // A simple retention policy - retain the study for the specified period
        // of time
        calendar.setTimeInMillis(calendar.getTimeInMillis() + retentionPeriod);

        return calendar.getTime();
    }

}