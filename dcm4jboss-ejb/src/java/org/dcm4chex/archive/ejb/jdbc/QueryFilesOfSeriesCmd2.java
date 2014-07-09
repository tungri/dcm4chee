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
 * Agfa HealthCare.
 * Portions created by the Initial Developer are Copyright (C) 2006-2008
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.MD5;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since May 6, 2009
 */
public class QueryFilesOfSeriesCmd2 extends BaseReadCmd {

    private static final String SQL = "SELECT f.pk, f.filepath, f.file_md5, "
            + "f.file_status, fs.fs_group_id, fs.dirpath, fs.retrieve_aet, "
            + "fs.availability, fs.user_info, i.sop_cuid, i.sop_iuid, "
            + "s.num_instances, i.ext_retr_aet "
            + "FROM files f, filesystem fs, instance i, series s "
            + "WHERE f.filesystem_fk = fs.pk AND f.instance_fk = i.pk "
            + "AND i.series_fk = s.pk AND s.series_iuid=?";

    public static int transactionIsolationLevel = 0;

    private int numberOfSeriesRelatedInstances = 0;

    public QueryFilesOfSeriesCmd2(String seriuid, int fetchSize) throws SQLException {
        super(JdbcProperties.getInstance().getDataSource(),
                transactionIsolationLevel, SQL);
        ((PreparedStatement) stmt).setString(1, seriuid);
        setFetchSize(fetchSize);
        execute();
    }

    public int getNumberOfSeriesRelatedInstances() {
        return numberOfSeriesRelatedInstances;
    }

    public Map<String, List<FileDTO>> getFileDTOsByIUID() throws SQLException {
        Map<String, List<FileDTO>> map = new HashMap<String, List<FileDTO>>();
        try {
            while (next()) {
                FileDTO fileDTO = getFileDTO();
                if (fileDTO.getFileStatus() < 0) {
                    continue;
                }
                String iuid = fileDTO.getSopInstanceUID();
                List<FileDTO> list = map.get(iuid);
                if (list == null) {
                    map.put(iuid, list = new ArrayList<FileDTO>());
                }
                list.add(fileDTO);
            }
        } finally {
            close();
        }
        return map;
    }

    private FileDTO getFileDTO() throws SQLException {
        FileDTO dto = new FileDTO();
        dto.setPk(rs.getLong(1));
        dto.setFilePath(rs.getString(2));
        dto.setFileMd5(MD5.toBytes(rs.getString(3)));
        dto.setFileStatus(rs.getInt(4));
        dto.setFileSystemGroupID(rs.getString(5));
        dto.setDirectoryPath(rs.getString(6));
        dto.setRetrieveAET(rs.getString(7));
        dto.setAvailability(rs.getInt(8));
        dto.setUserInfo(rs.getString(9));
        dto.setSopClassUID(rs.getString(10));
        dto.setSopInstanceUID(rs.getString(11));
        numberOfSeriesRelatedInstances = rs.getInt(12);
        dto.setExternalRetrieveAET(rs.getString(13));
        return dto;
    }
}
