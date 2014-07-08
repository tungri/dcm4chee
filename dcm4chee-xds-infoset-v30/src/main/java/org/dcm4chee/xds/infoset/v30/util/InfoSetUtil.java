package org.dcm4chee.xds.infoset.v30.util;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import org.dcm4chee.xds.infoset.v30.ClassificationType;
import org.dcm4chee.xds.infoset.v30.ExternalIdentifierType;
import org.dcm4chee.xds.infoset.v30.ExtrinsicObjectType;
import org.dcm4chee.xds.infoset.v30.InternationalStringType;
import org.dcm4chee.xds.infoset.v30.LocalizedStringType;
import org.dcm4chee.xds.infoset.v30.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds.infoset.v30.RegistryObjectType;
import org.dcm4chee.xds.infoset.v30.RegistryPackageType;
import org.dcm4chee.xds.infoset.v30.SlotType1;
import org.dcm4chee.xds.infoset.v30.SubmitObjectsRequest;
import org.dcm4chee.xds.infoset.v30.ProvideAndRegisterDocumentSetRequestType.Document;
import org.w3c.dom.Node;

public class InfoSetUtil {

    private static JAXBContext jaxbContext;

    public static Map<String, SlotType1> getSlotsFromRegistryObject(RegistryObjectType ro) {
        List<SlotType1> slots = ro.getSlot();
        Map<String, SlotType1> slotByName = new HashMap<String, SlotType1>(slots.size());
        if (slots != null) {
            for (SlotType1 slot : slots) {
                String slotName = slot.getName();
                slotByName.put(slotName, slot);
            }
        }
        return slotByName;
    }
    public static Map<String, ClassificationType> getClassificationsFromRegistryObject(RegistryObjectType ro) {
        List<ClassificationType> classifications = ro.getClassification();
        Map<String, ClassificationType> clBySchema = new HashMap<String, ClassificationType>(classifications.size());
        if (classifications != null) {
            for (ClassificationType cl : classifications) {
                clBySchema.put(cl.getClassificationScheme(), cl);
            }
        }
        return clBySchema;
    }

    public static String getExternalIdentifierValue(String urn, RegistryObjectType ro) {
        List<ExternalIdentifierType> l = ro.getExternalIdentifier();
        ExternalIdentifierType ei;
        for ( Iterator iter = l.iterator() ; iter.hasNext() ; ) {
            ei = (ExternalIdentifierType) iter.next();
            if ( ei.getIdentificationScheme().equals(urn)) {
                return ei.getValue();
            }
        }
        return null;
    }
    public static String setExternalIdentifierValue(String urn, String value, RegistryObjectType ro) {
        List<ExternalIdentifierType> l = ro.getExternalIdentifier();
        ExternalIdentifierType ei;
        for ( Iterator iter = l.iterator() ; iter.hasNext() ; ) {
            ei = (ExternalIdentifierType) iter.next();
            if ( ei.getIdentificationScheme().equals(urn)) {
                String oldValue = ei.getValue();
                ei.setValue(value);
                return oldValue;
            }
        }
        ei = new ExternalIdentifierType();
        ei.setIdentificationScheme(urn);
        ei.setValue(value);
        l.add(ei);
        return null;
    }

    public static RegistryPackageType getRegistryPackage(SubmitObjectsRequest so, String classificationUUID) {
        List list = so.getRegistryObjectList().getIdentifiable();
        String id = null;
        Object o;
        if ( classificationUUID != null ) {
            ClassificationType ct;
            for ( Iterator iter = list.iterator(); iter.hasNext() ; ) {
                o = ((JAXBElement) iter.next()).getValue();
                if ( o instanceof ClassificationType) {
                    ct = (ClassificationType) o;
                    if ( classificationUUID.equals( ct.getClassificationNode())) {
                        id = ct.getClassifiedObject();
                        break;
                    }
                }
            }
        }
        RegistryPackageType rp;
        for ( Iterator iter = list.iterator(); iter.hasNext() ; ) {
            o = ((JAXBElement) iter.next()).getValue();
            if ( o instanceof RegistryPackageType) {
                rp = (RegistryPackageType) o;
                if ( id == null || id.equals( rp.getId())) {
                    return rp;
                }
            }
        }
        return null;
    }

    public static Map getExtrinsicObjects(SubmitObjectsRequest so) {
        Map map = new HashMap();
        List list = so.getRegistryObjectList().getIdentifiable();
        Object o;
        ExtrinsicObjectType extrObj;
        for ( Iterator iter = list.iterator(); iter.hasNext() ; ) {
            o = ((JAXBElement) iter.next()).getValue();
            if ( o instanceof ExtrinsicObjectType) {
                extrObj = (ExtrinsicObjectType) o;
                map.put(extrObj.getId(), extrObj);

            }
        }
        return map;
    }

    public static Map getDocuments(ProvideAndRegisterDocumentSetRequestType req) {
        List docs = req.getDocument();
        Map map = new HashMap(docs.size());
        Document doc;
        for ( Iterator iter = docs.iterator() ; iter.hasNext() ;) {
            doc = (Document) iter.next();
            map.put(doc.getId(), doc);
        }
        return map;
    }

    public static Source getSourceForObject(Object o) throws JAXBException {
        return new StreamSource(new ByteArrayInputStream(marshallObject(o, false).getBytes()));
    }

    public static String marshallObject(Object o, boolean indent) throws JAXBException {
        StringWriter sw = new StringWriter();
        Marshaller m = getJAXBContext().createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.valueOf(indent));
        m.marshal(o, sw);
        String s = sw.toString();
        return s;
    }
    
    public static Node getNodeForObject(Object o) throws JAXBException {
        Marshaller m = getJAXBContext().createMarshaller();
        DOMResult res = new DOMResult(); 
        m.marshal(o, res);
        return res.getNode();
    }
    public static org.w3c.dom.Document getDocumentForObject(Object o) throws JAXBException {
        Node node = InfoSetUtil.getNodeForObject(o);
        return node == null ? null :
              (node instanceof org.w3c.dom.Document) ? (org.w3c.dom.Document) node : node.getOwnerDocument();
    }
    
    public static Object node2Object(Node node) throws JAXBException {
        Unmarshaller um=getJAXBContext().createUnmarshaller();
        return um.unmarshal(node);
    }

    public static Object unmarshal(File f) throws JAXBException {
        Unmarshaller um=getJAXBContext().createUnmarshaller();
        return um.unmarshal(f);
    }

    public static void writeObject(Object o, OutputStream os, boolean indent) throws JAXBException {
        Marshaller m = getJAXBContext().createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.valueOf(indent));
        m.marshal(o, os);
    }

    public static JAXBContext getJAXBContext() throws JAXBException {
        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds.infoset.v30:org.dcm4chee.xds.infoset.v21");
        }
        return jaxbContext;
    }

    public static String getLongSlotValue(List<String> values) {
        if ( values == null ) { 
            return null;
        }
        if (values.size() == 1 ) {
            String s = values.get(0);
            if (Character.isDigit(s.charAt(0)) ) {
                s = s.substring(2);
            }
            return s;
        }
        String[] sa = new String[values.size()];
        StringBuffer sb = new StringBuffer();
            for ( String s : values ) {
                try {
                    sa[(int) s.charAt(0)-0x31] = s.substring(2);
                } catch ( Exception x) {
                    throw new IllegalArgumentException("LONG Slot Value contains Invalid Value: :"+s);
                }
            }
            for ( int i = 0 ; i < sa.length ; i++) {
                sb.append(sa[i]);
            }
        return sb.toString();
    }
    
    public static String getSlotValue(List<SlotType1> slots, String slotName, String def) {
        for (SlotType1 slot : slots) {
            if ( slot.getName().equals(slotName)) {
                List<String> l = slot.getValueList().getValue();
                return l.isEmpty() ? def : l.get(0);
            }
        }
        return def;
    }

    public static String getString(InternationalStringType is, String def) {
        if ( is == null ) return def;
        List<LocalizedStringType> ls = is.getLocalizedString();
        return ls.isEmpty() ? def : ls.get(0).getValue();
    }
    
    public static String getLocalizedString(List<LocalizedStringType> lst, String lang, String def) {
        if ( lst != null ) {
            for ( LocalizedStringType ls : lst ) {
                if ( lang == null || lang.equals(ls.getLang()) ) {
                    return ls.getValue();
                } else {
                    def = ls.getValue();
                }
            }
        }
        return def;
    }
}
