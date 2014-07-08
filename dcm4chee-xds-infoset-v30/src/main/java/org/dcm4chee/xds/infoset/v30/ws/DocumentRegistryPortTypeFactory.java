package org.dcm4chee.xds.infoset.v30.ws;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.soap.SOAPBinding;

import org.dcm4chee.xds.infoset.v30.ws.DocumentRegistryService;
import org.jboss.ws.core.ConfigProvider;

public class DocumentRegistryPortTypeFactory {

	protected static DocumentRegistryService service = null;
	
	static {
		// Creating an instance of DocumentRegistryService is expensive, so do it once and
		// reuse the same instance.  Due to a JBossWS problem (JBWS-2681), create one instance of every
		// endpoint.  The end result is that all metadata will be generated once, serially.
		service = new DocumentRegistryService();
		service.getDocumentRegistryPortSoap11();
		service.getDocumentRegistryPortSoap12();
	}

	public static DocumentRegistryPortType getDocumentRegistryPortSoap12() {
		return service.getDocumentRegistryPortSoap12();
	}

	public static DocumentRegistryPortType getDocumentRegistryPortSoap12(String endpointAddress, String action, String messageId) {
		DocumentRegistryPortType port = service.getDocumentRegistryPortSoap12();
		configurePort(port, endpointAddress, action, messageId);
		return port;
	}

	public static void configurePort(DocumentRegistryPortType port, String endpointAddress, String action, String messageId) {
		BindingProvider bindingProvider = (BindingProvider)port;
		ConfigProvider configProvider = (ConfigProvider)port;
		SOAPBinding soapBinding = (SOAPBinding)bindingProvider.getBinding();
		soapBinding.setMTOMEnabled(true);
		
		// NOTE: The correct way to support WSAddressing on the client
		// is to do this call:
		// configProvider.setConfigName("Standard WSAddressing Client");
		// However, due to a JBoss bug (http://jira.jboss.com/jira/browse/JBWS-1880)
		// we must add a custom handler to force the injection of WSAddressing 
		// attributes, as done in the next 3 lines.
		List<Handler> customHandlerChain = new ArrayList<Handler>();
		customHandlerChain.add(new WSAddressingHandler(endpointAddress, action, messageId));
		soapBinding.setHandlerChain(customHandlerChain);
		 
		Map<String, Object> reqCtx = bindingProvider.getRequestContext();
		reqCtx.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointAddress);
	}	
}
