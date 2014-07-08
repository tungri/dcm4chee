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
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
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

package org.dcm4chex.archive.util;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * ContentHandler Adapter that can be used with several transformations by ignoring start-/endDocument!
 * Therefore forcedStartDocument and forcedEndDocument must be called explicitly.
 * 
 * @author franz.willer@gmail.com
 * @version $Revision: $ $Date: $
 * @since 31.10.2010
 *
 */
public class ContentHandlerAdapter implements ContentHandler {

    private final ContentHandler handler;
    
    private static final Attributes EMPTY_ATTR = new AttributesImpl();
    
    public ContentHandlerAdapter(ContentHandler h) {
        handler = h;
    }
    
    public void forcedStartDocument() throws SAXException {
        handler.startDocument();
    }
    public void forcedEndDocument() throws SAXException {
        handler.endDocument();
    }
 
    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {
    }
    
    public void startElement(String tag) throws SAXException {
        handler.startElement("", tag, tag, EMPTY_ATTR);
    }
    public void endElement(String tag) throws SAXException {
        handler.endElement("", tag, tag);
    }
    
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        handler.characters(ch, start, length);
    }

    public void endElement(String uri, String localName, String name)
            throws SAXException {
        handler.endElement(uri, localName, name);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        handler.endPrefixMapping(prefix);
    }

    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        handler.ignorableWhitespace(ch, start, length);
    }

    public void processingInstruction(String target, String data)
            throws SAXException {
        handler.processingInstruction(target, data);
    }

    public void setDocumentLocator(Locator locator) {
        handler.setDocumentLocator(locator);
    }

    public void skippedEntity(String name) throws SAXException {
        handler.skippedEntity(name);
    }

    public void startElement(String uri, String localName, String name,
            Attributes atts) throws SAXException {
        handler.startElement(uri, localName, name, atts);
    }

    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        handler.startPrefixMapping(prefix, uri);
    }

}