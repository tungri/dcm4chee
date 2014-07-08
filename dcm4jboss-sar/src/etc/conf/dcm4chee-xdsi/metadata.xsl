<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" indent="yes"/>

<!--
  The output contain a <property name="name" value="value" /> element for each property.
  
  Known properties: 
  docTitle:                   Title (<Name> element in ExtrinsicObject, 
                                 if not set, use code meaning of concept name code sequence of manifest)
  submissionSetTitle:         Title (<Name> element in SubmissionSet RegistryPackage). 
                              Note: only used if no manifest is exported (e.g create folder) 
                              
  comments                    Comments (<Description> element in SubmissionSet RegistryPackage)
  description:                Description (<Description> element in ExtrinsicObject)
  languageCode:               Slot languageCode in ExtrinsicObject
  srcPatientID:               Source Patient ID (Slot sourcePatientId and PID-3 value in Slot sourcePatientInfo)
  
  submissionTime              default is current time
  uniqueId                    Submissinset UniqueId (default: new generated UID)
  sourceId
  
  authorPerson
  authorSpeciality
  authorInstitution
  authorRole  
  authorRoleDisplayName 
  
  folder.uniqueId:            UID of new Folder
  folder.name:                Name of Folder (<Name> element in Folder RegistryPackage)
  folder.comments             (Description element in Folder RegistryPackage
  xdsfolder.uniqueId          XDSFolder.uniqueId (Default: generated)
  folderCode
  folderCodeDN
  folderCodeCodingSchemeOID
  folder_assoc.uniqueId:      List of uniqueIds of existing folder separated by '|'
  
  Codes:
  classCode
  classCodeDisplayName
  classCodeCodingSchemeOID
  
  confidentialityCode
  confidentialityCodeDN
  confidentialityCodeCodingSchemeOID
  
  healthCareFacilityTypeCode
  healthCareFacilityTypeCodeDN
  healthCareFacilityTypeCodeCodingSchemeOID
  
  practiceSettingCode
  practiceSettingCodeDN
  practiceSettingCodeCodingSchemeOID
  
  contentTypeCode
  contentTypeCodeDN
  contentTypeCodeCodingSchemeOID
  
  typeCode
  typeCodeDN
  typeCodeCodingSchemeOID
  
  Formatcode properties are only used if document mimetype != application/dicom
  formatCode.<mime>  
  formatCodeDN.<mime>
  formatCodeCodingSchemeOID.<mime>
  
  eventCodeList (Format: codeValue^codeMeaning^codeDesignator[|codeValue^codeMeaning^codeDesignator[|..]]
  eventCodeListCodingSchemeOID (default codeDesignator, used if codeDesignator in eventCodeList is missing)
  
  XSLT_URL:                   Optional URL to a XSL stylesheet for processinf XDS metadata.
-->

  <xsl:template match="/">
    <properties>
      <property name="docTitle" >
        <xsl:attribute name="value"><xsl:value-of select="/dataset/attr[@tag='00400275']/item[1]/attr[@tag='00400007']"/></xsl:attribute>
      </property>  
      <property name="submissionSetTitle" value="TEST_SUBMTITLE"/> 
    </properties>
  </xsl:template>
 
</xsl:stylesheet>