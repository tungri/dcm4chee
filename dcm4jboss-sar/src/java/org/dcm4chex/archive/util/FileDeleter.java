package org.dcm4chex.archive.util;

import static java.lang.String.format;
import static org.dcm4chex.archive.util.FileUtils.delete;
import static org.dcm4chex.archive.util.FileUtils.toFile;

import java.io.File;

import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Local;
import org.jboss.logging.Logger;

public class FileDeleter {
	private final Logger log;

	public FileDeleter(Logger log) {
		this.log = log;
	}

	public int deleteFiles(FileSystemMgt2Local fsMgt,
			Iterable<FileDTO> fileDTOs) {
		int deletedFilesCount = 0;
		
		for (FileDTO fileDTO : fileDTOs) {
			String directoryPath = fileDTO.getDirectoryPath();
			File file = toFile(directoryPath, fileDTO.getFilePath());

			if (delete(file, true, directoryPath)) {
				try {
					fsMgt.deletePrivateFile(fileDTO.getPk());
					
					deletedFilesCount++;
                } catch (Exception e) {
                    log.warn(format("Failed to remove file record [pk = %s] from DB", fileDTO.getPk()), e);
                    log.info(format("-> Keep file [%s] on file system", file));
                }
			} else {
				log.warn(format("Failed to remove file [%s]", file));
				log.info(format("-> Keep file record [pk = %s] in DB",
						fileDTO.getPk()));
			}
		}

		return deletedFilesCount;
	}
}
