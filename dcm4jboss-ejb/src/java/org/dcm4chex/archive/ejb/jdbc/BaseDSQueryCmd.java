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
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
 * Franz Willer <franz.willer@gwi-ag.com>
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

package org.dcm4chex.archive.ejb.jdbc;

import java.sql.SQLException;
import java.util.Iterator;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.VRs;

/**
 * @author <a href="mailto:gunter@tiani.com">Gunter Zeilinger</a>
 * @version $Revision: 14902 $ $Date: 2011-02-15 15:39:30 +0000 (Tue, 15 Feb 2011) $
 */
public abstract class BaseDSQueryCmd extends BaseReadCmd {


    protected final Dataset keys;

    protected final SqlBuilder sqlBuilder = new SqlBuilder();

    protected final boolean filterResult;

    protected final boolean type2;

    protected BaseDSQueryCmd(Dataset keys, boolean filterResult,
            boolean noMatchForNoValue, int transactionIsolationLevel)
            throws SQLException {
        super(JdbcProperties.getInstance().getDataSource(),
                transactionIsolationLevel);
        this.keys = keys;
        this.filterResult = filterResult;
        this.type2 = noMatchForNoValue ? SqlBuilder.TYPE1 : SqlBuilder.TYPE2;
    }


    public void execute() throws SQLException {
        try {
            execute(sqlBuilder.getSql());
        } catch (RuntimeException re) {
            close(); // prevent leaking DB Connection
            throw re;
        }
    }
    
    @SuppressWarnings("unchecked")
    protected void adjustDataset(Dataset ds, Dataset keys) {
        for (Iterator<DcmElement> it = keys.iterator(); it.hasNext();) {
            DcmElement key = it.next();
            final int tag = key.tag();
            if (tag == Tags.SpecificCharacterSet || Tags.isPrivate(tag))
                continue;

            final int vr = key.vr();
            DcmElement el = ds.get(tag);
            if (el == null) {
                ds.putXX(tag, vr);
                continue;
            }
            if (vr == VRs.SQ) {
                DcmElement filteredEl = null;
                Dataset keyItem = key.getItem();
                if (keyItem != null) {
                    if (el.isEmpty()) {
                        el.addNewItem();
                    } else if (filterResult && !keyItem.isEmpty()) {
                        filteredEl = ds.putSQ(tag);
                    }
                    for (int i = 0, n = el.countItems(); i < n; ++i) {
                        Dataset item = el.getItem(i);
                        adjustDataset(item, keyItem);
                        if (filteredEl != null) {
                            filteredEl.addItem(item = item.subSet(keyItem));
                        }
                    }
                }
            }
        }
    }
    
 }