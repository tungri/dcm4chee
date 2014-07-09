package org.dcm4chex.archive.util;

import java.util.Iterator;

import javax.xml.transform.Templates;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObject;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.VRs;
import org.dcm4che.net.Association;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for performing coercion related functions.
 */
public class CoercionUtils {

	private static final Logger log = LoggerFactory.getLogger(CoercionUtils.class);
	
	private CoercionUtils() {} // no make
	
	/**
	 * Builds and returns a {@code Dataset} that contains a set of modified attributes
	 * based on the coercion rules specified in the stylesheet. Note that the return
	 * object does not contain any of the original {@code Dataset} values.
	 * @param a the association for this transaction which is used to identify the calling and called AE
	 * @param in data that the coercion is applied to
	 * @param stylesheet the stylesheet defining the coercion rules
	 * @return the set of modified attributes, or null if coercion failed due to an error
	 */
	public static Dataset getCoercionAttributesFor(Association a, Dataset in, Templates stylesheet) {
        if (stylesheet == null) {
            return null;
        }
        Dataset out = DcmObjectFactory.getInstance().newDataset();
        try {
            XSLTUtils.xslt(in, stylesheet, a, out);
        }
        catch (Exception e) {
            log.error("Attribute coercion failed:", e);
            return null;
        }
        return out;
    }
	
	/**
	 * Builds and returns a {@code Dataset} that contains a set of modified attributes
	 * based on the coercion rules specified in the {@code TransformerHandler}. Note that
	 * the return object does not contain any of the original {@code Dataset} values.
	 * @param in data that the coercion is applied to
	 * @param th the {@code TransformerHandler} defining the coercion rules
	 * @return the set of modified attributes, or null if coercion failed due to an error
	 */
	public static Dataset getCoercionAttributesFor(Dataset in, TransformerHandler th) {
        if (th == null) {
            return null;
        }
        Dataset out = DcmObjectFactory.getInstance().newDataset();
        try {
            XSLTUtils.xslt(in, th, out);
        }
        catch (Exception e) {
            log.error("Attribute coercion failed:", e);
            return null;
        }
        return out;
    }
	
	/**
	 * Applies the coerced values to the {@code DcmObject}.
	 * @param ds the {@code DcmObject} to apply the coerced values to
	 * @param coerce the {@code DcmObject} holding the coerced values to apply
	 */
	public static void coerceAttributes(DcmObject ds, DcmObject coerce) {
        coerceAttributes(ds, coerce, null);
    }
	
	/**
	 * Applies the coerced values to the {@code DcmObject}.
	 * @param ds the {@code DcmObject} to apply the coerced values to
	 * @param coerce the {@code DcmObject} holding the coerced values to apply
	 * @param parent the parent {@code DcmElement} in the coerced {@code DcmObject} - used
	 *        for recursive calls - can be null
	 */
	@SuppressWarnings("unchecked")
	private static void coerceAttributes(DcmObject ds, DcmObject coerce, DcmElement parent) {
        boolean coerced = false;
        for (Iterator<DcmElement> it = coerce.iterator(); it.hasNext();) {
            DcmElement el = it.next();
            DcmElement oldEl = ds.get(el.tag());
            if (el.isEmpty()) {
                coerced = oldEl != null && !oldEl.isEmpty();
                if (oldEl == null || coerced) {
                    ds.putXX(el.tag(), el.vr());
                }
            } else {
                Dataset item;
                DcmElement sq = oldEl;
                switch (el.vr()) {
                case VRs.SQ:
                    coerced = oldEl != null && sq.vr() != VRs.SQ;
                    if (oldEl == null || coerced) {
                        sq = ds.putSQ(el.tag());
                    }
                    for (int i = 0, n = el.countItems(); i < n; ++i) {
                        item = sq.getItem(i);
                        if (item == null) {
                            item = sq.addNewItem();
                        }
                        Dataset coerceItem = el.getItem(i);
                        coerceAttributes(item, coerceItem, el);
                        if (!coerceItem.isEmpty()) {
                            coerced = true;
                        }
                    }
                    break;
                case VRs.OB:
                case VRs.OF:
                case VRs.OW:
                case VRs.UN:
                    if (el.hasDataFragments()) {
                        coerced = true;
                        sq = ds.putXXsq(el.tag(), el.vr());
                        for (int i = 0, n = el.countItems(); i < n; ++i) {
                            sq.addDataFragment(el.getDataFragment(i));
                        }
                        break;
                    }
                    // fall through
                default:
                    coerced = oldEl != null && !oldEl.equals(el);
                    if (oldEl == null || coerced) {
                        ds.putXX(el.tag(), el.vr(), el.getByteBuffer());
                    }
                    break;
                }
            }
            if (coerced) {
                log.info(parent == null ? ("Coerce " + oldEl + " to " + el)
                                : ("Coerce " + oldEl + " to " + el
                                        + " in item of " + parent));
            } else {
                if (oldEl == null && log.isDebugEnabled()) {
                    log.debug(parent == null ? ("Add " + el) : ("Add " + el
                            + " in item of " + parent));
                }
                it.remove();
            }
        }
    }
}
