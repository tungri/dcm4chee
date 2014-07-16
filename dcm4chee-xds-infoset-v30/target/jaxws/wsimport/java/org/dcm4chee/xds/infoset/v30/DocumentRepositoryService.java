
package org.dcm4chee.xds.infoset.v30;

import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.1-b03-
 * Generated source version: 2.1
 * 
 */
@WebServiceClient(name = "DocumentRepository_Service", targetNamespace = "urn:ihe:iti:xds-b:2007", wsdlLocation = "file:/C:/dcm4chee/SRC/dcm4che-svn-18306-dcm4chee-dcm4chee-xds-infoset-tags-DCM4CHEE_XDS_1_0_3/dcm4chee-xds-infoset-v30/src/wsdl/repository.wsdl")
public class DocumentRepositoryService
    extends Service
{

    private final static URL DOCUMENTREPOSITORYSERVICE_WSDL_LOCATION;

    static {
        URL url = null;
        try {
            url = new URL("file:/C:/dcm4chee/SRC/dcm4che-svn-18306-dcm4chee-dcm4chee-xds-infoset-tags-DCM4CHEE_XDS_1_0_3/dcm4chee-xds-infoset-v30/src/wsdl/repository.wsdl");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        DOCUMENTREPOSITORYSERVICE_WSDL_LOCATION = url;
    }

    public DocumentRepositoryService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public DocumentRepositoryService() {
        super(DOCUMENTREPOSITORYSERVICE_WSDL_LOCATION, new QName("urn:ihe:iti:xds-b:2007", "DocumentRepository_Service"));
    }

    /**
     * 
     * @return
     *     returns DocumentRepositoryPortType
     */
    @WebEndpoint(name = "DocumentRepository_Port_Soap11")
    public DocumentRepositoryPortType getDocumentRepositoryPortSoap11() {
        return (DocumentRepositoryPortType)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRepository_Port_Soap11"), DocumentRepositoryPortType.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns DocumentRepositoryPortType
     */
    @WebEndpoint(name = "DocumentRepository_Port_Soap11")
    public DocumentRepositoryPortType getDocumentRepositoryPortSoap11(WebServiceFeature... features) {
        return (DocumentRepositoryPortType)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRepository_Port_Soap11"), DocumentRepositoryPortType.class);
    }

    /**
     * 
     * @return
     *     returns DocumentRepositoryPortType
     */
    @WebEndpoint(name = "DocumentRepository_Port_Soap12")
    public DocumentRepositoryPortType getDocumentRepositoryPortSoap12() {
        return (DocumentRepositoryPortType)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRepository_Port_Soap12"), DocumentRepositoryPortType.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns DocumentRepositoryPortType
     */
    @WebEndpoint(name = "DocumentRepository_Port_Soap12")
    public DocumentRepositoryPortType getDocumentRepositoryPortSoap12(WebServiceFeature... features) {
        return (DocumentRepositoryPortType)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRepository_Port_Soap12"), DocumentRepositoryPortType.class);
    }

}