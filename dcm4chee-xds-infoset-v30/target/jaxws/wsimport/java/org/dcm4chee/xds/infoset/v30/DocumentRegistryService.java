
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
@WebServiceClient(name = "DocumentRegistry_Service", targetNamespace = "urn:ihe:iti:xds-b:2007", wsdlLocation = "file:/C:/dcm4chee/SRC/dcm4che-svn-18306-dcm4chee-dcm4chee-xds-infoset-tags-DCM4CHEE_XDS_1_0_3/dcm4chee-xds-infoset-v30/src/wsdl/registry.wsdl")
public class DocumentRegistryService
    extends Service
{

    private final static URL DOCUMENTREGISTRYSERVICE_WSDL_LOCATION;

    static {
        URL url = null;
        try {
            url = new URL("file:/C:/dcm4chee/SRC/dcm4che-svn-18306-dcm4chee-dcm4chee-xds-infoset-tags-DCM4CHEE_XDS_1_0_3/dcm4chee-xds-infoset-v30/src/wsdl/registry.wsdl");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        DOCUMENTREGISTRYSERVICE_WSDL_LOCATION = url;
    }

    public DocumentRegistryService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public DocumentRegistryService() {
        super(DOCUMENTREGISTRYSERVICE_WSDL_LOCATION, new QName("urn:ihe:iti:xds-b:2007", "DocumentRegistry_Service"));
    }

    /**
     * 
     * @return
     *     returns DocumentRegistryPortType11
     */
    @WebEndpoint(name = "DocumentRegistry_Port_Soap11")
    public DocumentRegistryPortType11 getDocumentRegistryPortSoap11() {
        return (DocumentRegistryPortType11)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRegistry_Port_Soap11"), DocumentRegistryPortType11.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns DocumentRegistryPortType11
     */
    @WebEndpoint(name = "DocumentRegistry_Port_Soap11")
    public DocumentRegistryPortType11 getDocumentRegistryPortSoap11(WebServiceFeature... features) {
        return (DocumentRegistryPortType11)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRegistry_Port_Soap11"), DocumentRegistryPortType11.class);
    }

    /**
     * 
     * @return
     *     returns DocumentRegistryPortType12
     */
    @WebEndpoint(name = "DocumentRegistry_Port_Soap12")
    public DocumentRegistryPortType12 getDocumentRegistryPortSoap12() {
        return (DocumentRegistryPortType12)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRegistry_Port_Soap12"), DocumentRegistryPortType12.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns DocumentRegistryPortType12
     */
    @WebEndpoint(name = "DocumentRegistry_Port_Soap12")
    public DocumentRegistryPortType12 getDocumentRegistryPortSoap12(WebServiceFeature... features) {
        return (DocumentRegistryPortType12)super.getPort(new QName("urn:ihe:iti:xds-b:2007", "DocumentRegistry_Port_Soap12"), DocumentRegistryPortType12.class);
    }

}
