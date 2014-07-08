package org.dcm4chee.xds.infoset.v30.util;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.soap.SOAPException;

import org.dcm4chee.xds.infoset.v30.AdhocQueryRequest;
import org.dcm4chee.xds.infoset.v30.AdhocQueryResponse;
import org.dcm4chee.xds.infoset.v30.AdhocQueryType;
import org.dcm4chee.xds.infoset.v30.ObjectFactory;
import org.dcm4chee.xds.infoset.v30.ResponseOptionType;
import org.dcm4chee.xds.infoset.v30.SlotType1;
import org.dcm4chee.xds.infoset.v30.ValueListType;

public class StoredQueryFactory {
    /** Request UID */
    public static final String STORED_QUERY_FIND_DOCUMENTS = "urn:uuid:14d4debf-8f97-4251-9a74-a90016b0af0d";
    public static final String STORED_QUERY_FIND_SUBMISSIONSETS = "urn:uuid:f26abbcb-ac74-4422-8a30-edb644bbc1a9";
    public static final String STORED_QUERY_FIND_FOLDERS = "urn:uuid:958f3006-baad-4929-a4de-ff1114824431";
    public static final String STORED_QUERY_GET_ALL = "urn:uuid:10b545ea-725c-446d-9b95-8aeb444eddf3";
    public static final String STORED_QUERY_GET_DOCUMENTS = "urn:uuid:5c4f972b-d56b-40ac-a5fc-c8ca9b40b9d4";
    public static final String STORED_QUERY_GET_FOLDERS = "urn:uuid:5737b14c-8a1a-4539-b659-e03a34a5e1e4";
    public static final String STORED_QUERY_GET_ASSOC = "urn:uuid:a7ae438b-4bc2-4642-93e9-be891f7bb155";
    public static final String STORED_QUERY_GET_DOC_AND_ASSOC = "urn:uuid:bab9529a-4a10-40b3-a01f-f68a615d247a";
    public static final String STORED_QUERY_GET_SUBMISSIONSETS = "urn:uuid:51224314-5390-4169-9b91-b1980040715a";
    public static final String STORED_QUERY_GET_SUBMISSIONSETS_AND_CONTENT = "urn:uuid:e8e3cb2c-e39c-46b9-99e4-c12f57260b83";
    public static final String STORED_QUERY_GET_FOLDER_AND_CONTENT = "urn:uuid:b909a503-523d-4517-8acf-8e5834dfc4c7";
    public static final String STORED_QUERY_GET_FOLDER_FOR_DOC = "urn:uuid:10cae35a-c7f9-4cf5-b61e-fc3278ffb578";
    public static final String STORED_QUERY_GET_RELATED_DOCS = "urn:uuid:d90e5407-b356-4d91-a89f-873917b4b0e6";

    /** Field UID */
    public static final String QRY_DOCUMENT_ENTRY_PATIENT_ID = "$XDSDocumentEntryPatientId";
    public static final String QRY_DOCUMENT_ENTRY_STATUS = "$XDSDocumentEntryStatus";
    public static final String QRY_FOLDER_STATUS = "$XDSFolderStatus";
    public static final String QRY_FOLDER_ENTRY_UUID = "$XDSFolderEntryUUID";
    public static final String QRY_SUBMISSION_SET_STATUS = "$XDSSubmissionSetStatus";
    public static final String QRY_SUBMISSION_SET_PATIENT_ID = "$XDSSubmissionSetPatientId";
    public static final String QRY_SUBMISSION_SET_ENTRY_UUID = "$XDSSubmissionSetEntryUUID";
    public static final String QRY_PATIENT_ID = "$patientId";
    public static final String QRY_FOLDER_PATIENT_ID = "$XDSFolderPatientId";
    public static final String QRY_UUID = "$uuid";
    public static final String QRY_DOCUMENT_ENTRY_ENTRY_UUID = "$XDSDocumentEntryEntryUUID";
    public static final String QRY_ASSOCIATION_TYPES = "$AssociationTypes";

    /** Status Constants */
    public static final String V3_STATUS_PREFIX = "urn:oasis:names:tc:ebxml-regrep:StatusType:";
    public static final String V3_RESPONSE_SUCCESS = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success";
    public static final String SUBMITTED = "urn:oasis:names:tc:ebxml-regrep:StatusType:Submitted";
    public static final String APPROVED = "urn:oasis:names:tc:ebxml-regrep:StatusType:Approved";
    public static final String DEPRECATED = "urn:oasis:names:tc:ebxml-regrep:StatusType:Deprecated";

    /** Response Option Constants */
    private static final String OBJECT_REF = "ObjectRef";
    private static final String LEAF_CLASS = "LeafClass";
    
    /** Association type constants */
    private static final String ASSOC_TYPE_HASMEMBER = "HasMember";
    private static final String ASSOC_TYPE_RPLC = "RPLC";
    private static final String ASSOC_TYPE_APND = "APND";
    private static final String ASSOC_TYPE_XFRM = "XFRM";
    

    private static ObjectFactory objFac = new ObjectFactory();
    
    private static final List<String> DEFAULT_STATI = new ArrayList<String>(3);
    static {    
        DEFAULT_STATI.add(SUBMITTED);
        DEFAULT_STATI.add(APPROVED);
        DEFAULT_STATI.add(DEPRECATED);
    }
    
    private static final List<String> DEFAULT_ASSOC_TYPES = new ArrayList<String>(3);
    static {    
        DEFAULT_ASSOC_TYPES.add(ASSOC_TYPE_HASMEMBER);
        DEFAULT_ASSOC_TYPES.add(ASSOC_TYPE_RPLC);
        DEFAULT_ASSOC_TYPES.add(ASSOC_TYPE_APND);
        DEFAULT_ASSOC_TYPES.add(ASSOC_TYPE_XFRM);
    }

    public AdhocQueryRequest createFindDocumentsRequest(String patId, List<String> status,
            boolean useLeaf) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_FIND_DOCUMENTS, useLeaf);
        createSlot(rq, QRY_DOCUMENT_ENTRY_PATIENT_ID, patId);
        setStatus(rq, QRY_DOCUMENT_ENTRY_STATUS, status);
        return rq;
    }

    public AdhocQueryRequest createFindFoldersRequest(String patId, List<String> status,
            boolean useLeaf) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_FIND_FOLDERS, useLeaf);
        createSlot(rq, QRY_FOLDER_PATIENT_ID, patId);
        setStatus(rq, QRY_FOLDER_STATUS, status);
        return rq;
    }

    public AdhocQueryRequest createFindSubmissionSetsRequest(String patId, List<String> status, boolean useLeaf) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_FIND_SUBMISSIONSETS, useLeaf);
        createSlot(rq, QRY_SUBMISSION_SET_PATIENT_ID, patId);
        setStatus(rq, QRY_SUBMISSION_SET_STATUS, status);
        return rq;
    }

    public AdhocQueryRequest createGetAllRequest(String patId, List<String> docStatus,
            List<String> submissionSetStatus, List<String> folderStatus) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_ALL, true);
        createSlot(rq, QRY_PATIENT_ID, patId);
        setStatus(rq, QRY_DOCUMENT_ENTRY_STATUS, docStatus);
        setStatus(rq, QRY_SUBMISSION_SET_STATUS, submissionSetStatus);
        setStatus(rq, QRY_FOLDER_STATUS, folderStatus);
        return rq;
    }

    public AdhocQueryRequest createGetDocumentsRequest(List uuids) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_DOCUMENTS, true);
        createSlot(rq, QRY_DOCUMENT_ENTRY_ENTRY_UUID, uuids);
        return rq;
    }


    public AdhocQueryRequest createGetFoldersQuery(List uuids) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_FOLDERS, true);
        createSlot(rq, QRY_FOLDER_ENTRY_UUID, uuids);
        return rq;
    }

    public AdhocQueryRequest createGetAssociationsRequest(List uuids) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_ASSOC, true);
        createSlot(rq, QRY_UUID, uuids);
        return rq;
    }

    public AdhocQueryRequest createGetDocumentsAndAssocsRequest(List uuids)
    throws SOAPException, JAXBException {
       AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_DOC_AND_ASSOC, true);
        createSlot(rq, QRY_DOCUMENT_ENTRY_ENTRY_UUID, uuids);
        return rq;
    }

    public AdhocQueryRequest createGetSubmissionSetsRequest(List uuids) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_SUBMISSIONSETS, true);
        createSlot(rq, QRY_UUID, uuids);
        return rq;
    }

    public AdhocQueryRequest createGetSubmissionSetAndContentsRequest(String uuid) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_SUBMISSIONSETS_AND_CONTENT, true);
        createSlot(rq, QRY_SUBMISSION_SET_ENTRY_UUID, uuid);
        return rq;
    }

    public AdhocQueryRequest createGetFolderAndContentsRequest(String uuid) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_FOLDER_AND_CONTENT, true);
        createSlot(rq, QRY_FOLDER_ENTRY_UUID, uuid);
        return rq;
    }

    public AdhocQueryRequest createGetFoldersForDocumentRequest(String uuid) throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_FOLDER_FOR_DOC, true);
        createSlot(rq, QRY_DOCUMENT_ENTRY_ENTRY_UUID, uuid);
        return rq;
    }

    public AdhocQueryRequest createRelatedDocumentsRequest(String uuid, List assocTypes)
    throws SOAPException, JAXBException {
        AdhocQueryRequest rq = getBasicRequest(STORED_QUERY_GET_RELATED_DOCS, false);
        createSlot(rq, QRY_DOCUMENT_ENTRY_ENTRY_UUID, uuid);
        createSlot(rq, QRY_ASSOCIATION_TYPES, assocTypes == null ? DEFAULT_ASSOC_TYPES : assocTypes);
        return rq;
    }

    
    private AdhocQueryRequest getBasicRequest(String id, boolean useLeafClass) {
        ResponseOptionType respOption = objFac.createResponseOptionType();
        respOption.setReturnType(useLeafClass ? LEAF_CLASS : OBJECT_REF);
        respOption.setReturnComposedObjects(true);
        AdhocQueryRequest request = objFac.createAdhocQueryRequest();
        request.setResponseOption(respOption);
        AdhocQueryType query = objFac.createAdhocQueryType();
        query.setId(id);
        request.setAdhocQuery(query);
        return request;
    }
    
    private void createSlot(AdhocQueryRequest rq, String name, Object value) {
        rq.getAdhocQuery().getSlot().add(createSlot(name, value));
    }
    
    private SlotType1 createSlot(String name, Object value) {
        SlotType1 slot = objFac.createSlotType1();
        slot.setName(name);
        ValueListType valueList = objFac.createValueListType();
        valueList.getValue().add(queryString(value));
        slot.setValueList(valueList);
        return slot;
    }

    private void setStatus(AdhocQueryRequest rq, String name, List<String> values) {
        List<String> stati;
        if (values == null || values.size() < 1) {
            stati = DEFAULT_STATI;
        } else {
            stati = new ArrayList<String>(values.size());
            for ( String s : values) {
                stati.add( s.startsWith(V3_STATUS_PREFIX) ? s : V3_STATUS_PREFIX + s);
            }
        }
        createSlot(rq, name, stati);
    }
    private String queryString(Object o) {
        if (o instanceof List) {
            List<Object> lst = (List<Object>) o;
            StringBuffer buf = new StringBuffer("(");
            buf.append(queryString(lst.get(0)));
            for (int i = 1; i < lst.size(); i++) {
                buf.append(",").append(queryString(lst.get(i)));
            }
            return buf.append(")").toString();
        } else if (o instanceof String[]) {
            String[] lst = (String[]) o;
            StringBuffer buf = new StringBuffer("(");
            buf.append(queryString(lst[0]));
            for (int i = 1; i < lst.length; i++) {
                buf.append(",").append(queryString(lst[i]));
            }
            return buf.append(")").toString();
        }
        return (o instanceof Number) ? o.toString() : "'" + o.toString()+ "'";
    }

    public AdhocQueryResponse createEmptyQueryResponse(String status) {
        AdhocQueryResponse rsp = objFac.createAdhocQueryResponse();
        rsp.setStatus(status);
        return rsp;
    }

}