package org.dcm4chex.archive.hsm.module.dicey;


import java.io.File;
import java.io.IOException;
import org.apache.log4j.Logger;

public class FileIOTimeOut {
    private static Logger log = Logger.getLogger(FileIOTimeOut.class);
    private static final int CHUNKSIZE = 1024 * 1024 * 4; // 4MB chunks

    private static final int WAIT_TIME = 100;
    
    public static void copy(File source, File destination, int timeOut)
    throws IOException {
        try {
            TransferThread ioThread = new TransferThread(source, destination, CHUNKSIZE);			
            ioThread.start();
            log.debug("Thread started");
            long waitTS = System.currentTimeMillis()+(timeOut * 1000); 
            long totalSize = 0L;
            totalSize = destination.length();


            while ( ! ioThread.stop || ioThread.getState() != Thread.State.TERMINATED ) { // if not
                // already
                // terminated
                if (log.isDebugEnabled()) 
                    log.debug("Timeout: "+(waitTS - System.currentTimeMillis())+"ms remaining.");
                if (System.currentTimeMillis() > waitTS) {
                    // Output filesize did not change for n seconds, therefore
                    // interrupt thread
                    log.debug("TimeOut reached");	
                    ioThread.inputChannel.close();
                    ioThread.outputChannel.close();
                    ioThread.stop = true;
                    ioThread.interrupt();				

                    throw new IOException("TimeOut reached");
                } else {
                    try {
                        Thread.sleep(WAIT_TIME);
                        if (destination.length() > totalSize) {
                            totalSize = destination.length();
                            waitTS = System.currentTimeMillis()+(timeOut * 1000);
                            log.debug("Size:" + totalSize);
                        } else
                            log.debug("Size not changed:" + totalSize);
                    } catch (InterruptedException e) {
                        throw new IOException("Thread Aborted");
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("FileCopy Not successfull",e);
        }
    }

}
