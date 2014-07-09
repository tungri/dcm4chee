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

package org.dcm4chex.archive.common;

/**
 * Constants used to identify context specific properties that are associated with a
 * particular {@link BaseJmsOrder}. Please refer to {@link BaseJmsOrder#setOrderProperty(String, String)}
 * for more information.
 * 
 * @version $Revision$ $Date$
 * @since September 8, 2009
 */
public interface JmsOrderProperties {

    public static final String STUDY_INSTANCE_UID = "StudyInstanceUID";
    public static final String SERIES_INSTANCE_UID = "SeriesInstanceUID";
    public static final String SOP_INSTANCE_UID = "SOPInstanceUID";
    public static final String DOCUMENT_IDENTIFIER = "DocumentIdentifier";
    public static final String RETRIEVE_AE_TITLE = "RetrieveAETitle";
    public static final String DESTINATION_AE_TITLE = "DestinationAETitle";
    public static final String ISSUER_OF_PATIENT_ID = "IssuerOfPatientID";
    public static final String PATIENT_ID = "PatientID";
    public static final String CALLED_AE_TITLE = "CalledAETitle";
    public static final String CALLING_AE_TITLE = "CallingAETitle";
    public static final String DOCUMENT_NAMESPACE = "DocumentNamespace";
    public static final String TRIGGER_EVENT = "TriggerEvent";
    public static final String MESSAGE_TYPE = "MessageType";
    public static final String TRANSACTION_UID = "TransactionUID";
}
