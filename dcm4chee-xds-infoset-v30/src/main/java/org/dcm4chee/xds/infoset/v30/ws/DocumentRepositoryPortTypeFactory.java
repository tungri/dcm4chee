package org.dcm4chee.xds.infoset.v30.ws;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.soap.SOAPBinding;

import org.dcm4chee.xds.infoset.v30.ws.DocumentRepositoryPortType;
import org.dcm4chee.xds.infoset.v30.ws.DocumentRepositoryService;
import org.jboss.ws.core.ConfigProvider;

public class DocumentRepositoryPortTypeFactory {

	protected static DocumentRepositoryService service = null;
	
	static {
		// Creating an instance of DocumentRepositoryService is expensive, so do it once and
		// reuse the same instance.  Due to a JBossWS problem (JBWS-2681), create one instance of every
		// endpoint.  The end result is that all metadata will be generated once, serially.
		service = new DocumentRepositoryService();
		service.getDocumentRepositoryPortSoap11();
		service.getDocumentRepositoryPortSoap12();
	}
	
	public static DocumentRepositoryPortType getDocumentRepositoryPortSoap11(String endpointAddress, String action, String messageId) {
		DocumentRepositoryPortType port = service.getDocumentRepositoryPortSoap11();
		configurePort(port, endpointAddress, action, messageId);
		return port;
	}

	public static DocumentRepositoryPortType getDocumentRepositoryPortSoap12(String endpointAddress, String action, String messageId) {
		DocumentRepositoryPortType port = service.getDocumentRepositoryPortSoap12();
		configurePort(port, endpointAddress, action, messageId);
		return port;
	}
	
	public static void configurePort(DocumentRepositoryPortType port, String endpointAddress, String action, String messageId) {
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
