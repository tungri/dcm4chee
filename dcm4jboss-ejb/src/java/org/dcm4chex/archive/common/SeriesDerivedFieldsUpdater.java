package org.dcm4chex.archive.common;

import java.util.Iterator;
import java.util.Set;

import javax.ejb.EJBException;
import javax.ejb.FinderException;

import org.apache.log4j.Logger;
import org.dcm4chex.archive.util.AETs;

/**
 * Centralizes the business logic for updating a Series's derived fields.
 */
public abstract class SeriesDerivedFieldsUpdater {
    private static final Logger log = Logger.getLogger(SeriesDerivedFieldsUpdater.class);

    /**
     * Updates all of the derived fields that are managed by this class.
     */
    public void updateDerivedFields() {
        updateNumberOfSeriesRelatedInstances();
        updateRetrieveAETs();
        updateExternalRetrieveAET();
        updateAvailability();
    }

    /**
     * Update the numberOfSeriesRelatedInstances derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateNumberOfSeriesRelatedInstances() {
        int numI;
        try {
            numI = deriveNumberOfSeriesRelatedInstances();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        if (getNumberOfSeriesRelatedInstances() == numI) {
            return false;
        }
        setNumberOfSeriesRelatedInstances(numI);
        return true;
    }
    
    /**
     * Update the retrieveAets derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateRetrieveAETs() {
        String aets = null;
        int numI = getNumberOfSeriesRelatedInstances();
        if (numI > 0) {
            StringBuffer sb = new StringBuffer();
            Set<String> iAetSet;
            try {
//              iAetSet = getInternalRetrieveAETs(pk);
                iAetSet = deriveInternalRetrieveAETs();
            } catch (FinderException e) {
                throw new EJBException(e);
            }
            Iterator<String> it = iAetSet.iterator();
            aets = (String) it.next();
            while (aets != null && it.hasNext()) {
                aets = AETs.common(aets, (String) it.next());
            }
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
                aets = sb.toString();
            }
        }
        if (aets == null ? getRetrieveAETs() == null
                         : aets.equals(getRetrieveAETs())) {
            return false;
        }
        setRetrieveAETs(aets);
        return true;
    }
    
    /**
     * Update the externalRetrieveAet derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateExternalRetrieveAET() {
        String aet = null;
        if (getNumberOfSeriesRelatedInstances() > 0) {
            Set<String> eAetSet;
            try {
                eAetSet = deriveExternalRetrieveAETs();
            } catch (FinderException e) {
                throw new EJBException(e);
            }
            if (eAetSet.size() == 1)
                aet = (String) eAetSet.iterator().next();
        }
        if (aet == null ? getExternalRetrieveAET() == null 
                        : aet.equals(getExternalRetrieveAET())) {
            return false;
        }       
        setExternalRetrieveAET(aet);
        return true;
    }
    
    /**
     * Update the availability derived field.
     *
     * @return true if the field was updated, false otherwise
     */
    public boolean updateAvailability() {
        int availability;
        try {
            availability = getNumberOfSeriesRelatedInstances() > 0
                    ? deriveAvailability() 
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
            log.debug("update Availability of Series[pk=" + getPk()
                    + ", uid=" + getSeriesIuid() + "] from " 
                    + Availability.toString(prevAvailability) + " to "
                    + Availability.toString(availability));
        }
        return true;
    }
    
    // the following methods should be overridden by subclasses to provide
    // access to the Series' persistent (derived) fields.

    protected abstract long getPk();
    protected abstract String getSeriesIuid();

    protected abstract int getNumberOfSeriesRelatedInstances();
    protected abstract void setNumberOfSeriesRelatedInstances(int numI);
    protected abstract int deriveNumberOfSeriesRelatedInstances()
            throws FinderException;

    protected abstract void setRetrieveAETs(String aets);
    protected abstract String getRetrieveAETs();
    protected abstract Set<String> deriveInternalRetrieveAETs()
            throws FinderException;

    protected abstract String getExternalRetrieveAET();
    protected abstract void setExternalRetrieveAET(String aet);
    protected abstract Set<String> deriveExternalRetrieveAETs()
            throws FinderException;

    protected abstract int getAvailability();
    protected abstract void setAvailability(int availability);
    protected abstract int deriveAvailability() throws FinderException;
}
