package org.dcm4chex.archive.common;

import static org.apache.log4j.Logger.getLogger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ejb.EJBException;
import javax.ejb.FinderException;

import org.apache.log4j.Logger;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.util.AETs;

/**
 * Centralizes the business logic for updating a Study's derived fields.
 */
public abstract class StudyDerivedFieldsUpdater {
    private static final Logger log = getLogger(StudyDerivedFieldsUpdater.class);

    /**
     * Updates all of the derived fields that are managed by this class.
     */
    public void updateDerivedFields() {
        updateNumberOfStudyRelatedSeries();
        updateNumberOfStudyRelatedInstances();
        updateRetrieveAETs();
        updateExternalRetrieveAET();
        updateAvailability();
        updateModalitiesInStudy();
        updateSOPClassesInStudy();
    }

    /**
     * Update the availability derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateAvailability() {
        int availability;
        try {
            availability = getNumberOfStudyRelatedInstances() > 0 ? deriveAvailability()
                    : Availability.UNAVAILABLE;
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        int prevAvailability = getAvailability();
        if (availability == prevAvailability) {
            return false;
        }
        setAvailability(availability);
        if (log.isDebugEnabled()) {
            log.debug("update Availability of Study[pk=" + getPk() + ", uid="
                    + getStudyIuid() + "] from "
                    + Availability.toString(prevAvailability) + " to "
                    + Availability.toString(availability));
        }
        return true;
    }

    /**
     * Update the externalRetrieveAet derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateExternalRetrieveAET() {
        String aet = null;
        if (getNumberOfStudyRelatedInstances() > 0) {
            Set<String> eAetSet;
            try {
                eAetSet = deriveExternalRetrieveAETs();
            } catch (FinderException e) {
                throw new EJBException(e);
            }
            if (eAetSet.size() == 1)
                aet = (String) eAetSet.iterator().next();
        }
        if (aet == null ? getExternalRetrieveAET() == null : aet
                .equals(getExternalRetrieveAET())) {
            return false;
        }
        setExternalRetrieveAET(aet);
        return true;
    }

    /**
     * Update the modalitiesInStudy derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateModalitiesInStudy() {
        String mds = "";
        if (getNumberOfStudyRelatedInstances() > 0) {
            Set<String> c;
            try {
                c = deriveModalitiesInStudies();
            } catch (FinderException e) {
                throw new EJBException(e);
            }
            if (c.remove(null))
                log.warn("Study[iuid=" + getStudyIuid()
                        + "] contains Series with unspecified Modality");
            if (!c.isEmpty()) {
                Iterator<String> it = c.iterator();
                StringBuffer sb = new StringBuffer((String) it.next());
                while (it.hasNext())
                    sb.append('\\').append(it.next());
                mds = sb.toString();
            }
        }
        if (mds.equals(getModalitiesInStudy())) {
            return false;
        }
        setModalitiesInStudy(mds);
        return true;
    }

    /**
     * Update the numberOfStudyRelatedInstances derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateNumberOfStudyRelatedInstances() {
        int numI;
        try {
            numI = deriveNumberOfStudyRelatedInstances();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        if (getNumberOfStudyRelatedInstances() == numI) {
            return false;
        }
        setNumberOfStudyRelatedInstances(numI);
        return true;
    }

    /**
     * Update the numberOfStudyRelatedSeries derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateNumberOfStudyRelatedSeries() {
        int numS;
        try {
            numS = deriveNumberOfStudyRelatedSeries();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        if (getNumberOfStudyRelatedSeries() == numS) {
            return false;
        }
        setNumberOfStudyRelatedSeries(numS);
        return true;
    }

    /**
     * Update the retrieveAets derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateRetrieveAETs() {
        String aets = null;
        if (getNumberOfStudyRelatedInstances() > 0) {
            Set<String> seriesAets;
            try {
                seriesAets = deriveSeriesRetrieveAETs();
            } catch (FinderException e) {
                throw new EJBException(e);
            }
            Iterator<String> it = seriesAets.iterator();
            aets = (String) it.next();
            while (aets != null && it.hasNext()) {
                aets = AETs.common(aets, (String) it.next());
            }
        }
        if (aets == null ? getRetrieveAETs() == null : aets
                .equals(getRetrieveAETs())) {
            return false;
        }
        setRetrieveAETs(aets);
        return true;
    }

    /**
     * Update the sopClassesInStudy derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateSOPClassesInStudy() {
        Set<String> newSet;
        try {
            newSet = deriveSOPClassesInStudies();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        String oldStr = getSopClassesInStudy();
        if (oldStr == null) {
            if (newSet.isEmpty()) {
                return false;
            }
        } else {
            Set<String> oldSet = new HashSet<String>(Arrays.asList(StringUtils
                    .split(oldStr, '\\')));
            if (newSet.equals(oldSet)) {
                return false;
            }
            if (newSet.isEmpty()) {
                setSopClassesInStudy(null);
                return true;
            }
        }
        String[] newStrs = (String[]) newSet.toArray(new String[newSet.size()]);
        setSopClassesInStudy(StringUtils.toString(newStrs, '\\'));
        return true;
    }

    // the following methods should be overridden by subclasses to provide
    // access to the Study's persistent (derived) fields.
    
    protected abstract void setSopClassesInStudy(String uids);

    protected abstract String getSopClassesInStudy();

    protected abstract void setRetrieveAETs(String aets);

    protected abstract String getRetrieveAETs();

    protected abstract long getPk();

    protected abstract String getStudyIuid();

    protected abstract String getModalitiesInStudy();

    protected abstract void setModalitiesInStudy(String mds);

    protected abstract String getExternalRetrieveAET();

    protected abstract void setExternalRetrieveAET(String aet);

    protected abstract int getNumberOfStudyRelatedInstances();

    protected abstract void setNumberOfStudyRelatedInstances(int numI);

    protected abstract int getAvailability();

    protected abstract void setAvailability(int availability);

    protected abstract int getNumberOfStudyRelatedSeries();

    protected abstract void setNumberOfStudyRelatedSeries(int numS);

    protected abstract int deriveAvailability() throws FinderException;

    protected abstract Set<String> deriveExternalRetrieveAETs()
            throws FinderException;

    protected abstract Set<String> deriveModalitiesInStudies()
            throws FinderException;

    protected abstract int deriveNumberOfStudyRelatedInstances()
            throws FinderException;

    protected abstract int deriveNumberOfStudyRelatedSeries()
            throws FinderException;

    protected abstract Set<String> deriveSeriesRetrieveAETs()
            throws FinderException;

    protected abstract Set<String> deriveSOPClassesInStudies()
            throws FinderException;
}
