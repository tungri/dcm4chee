/* ***** BEGIN LICENSE BLOCK *****
 * Copyright (c) 2013 by AGFA HealthCare
 *
 * This file is part of dcm4che.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chex.archive.hsm.module.uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

import com.jcraft.jsch.*;

import org.apache.log4j.Logger;

/**
 * @author Kianusch.Sayah-Karadji@agfa.com
 * @version $Revision: 1.3 $
 * @since 07.08.2013
 */

public class Ssh {
    private static Logger log = Logger.getLogger(HSMURIModule.class);

    private static final byte[] ackMsg = { 0 };

    private static final byte[] nackMsg = { 2, '\n' };

    private static void closeQuietly(InputStream in, OutputStream out, FileInputStream fis, FileOutputStream fos) {
        try {
            if (fis != null)
                fis.close();
        } catch (IOException e) {
        }
        try {
            if (fos != null)
                fos.close();
        } catch (IOException e) {
        }
        try {
            if (out != null)
                out.close();
        } catch (IOException e) {
        }
        try {
            if (in != null)
                in.close();
        } catch (IOException e) {
        }
    }

    private static Session connect(final String uri, String identity) throws URISyntaxException, JSchException {
        int pos = uri.indexOf("://");
        if (pos == -1) {
            throw new URISyntaxException("Could not parse:", uri);
        }

        String dst = null;

        int pos2 = uri.indexOf('/', pos + 3);
        if (pos2 > -1) {
            dst = uri.substring(pos + 3, pos2);
        } else {
            dst = uri.substring(pos + 3);
        }

        String username = null;
        String password = null;
        String host = null;
        int port = 22;

        pos = dst.indexOf('@');
        if (pos != -1) {
            username = dst.substring(0, pos);
            host = dst.substring(pos + 1);

            pos = username.indexOf(':');
            if (pos != -1) {
                password = username.substring(pos + 1);
                username = username.substring(0, pos);
            }

            pos = host.indexOf(':');
            if (pos != -1) {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
        } else {
            throw new URISyntaxException("Could not parse:", uri);
        }

        JSch jsch = new JSch();
        JSch.setConfig("StrictHostKeyChecking", "no");
        if (password == null && identity != null && (!identity.equals("NONE")))
            jsch.addIdentity(identity);
        Session session = jsch.getSession(username, host, port);
        if (password != null)
            session.setPassword(password);
        session.connect();
        return session;
    }

    private static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be
        // 0 for success,
        // 1 for error,
        // 2 for fatal error,
        // -1

        // // next 4 lines are for speed
        // if (b == 0)
        // return b;
        // if (b == -1)
        // return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            } while (c != '\n');
            log.error(sb.toString());
        }
        return b;
    }

    private static void sendCommand(OutputStream out, InputStream in, String command) throws IOException, JSchException {
        out.write(command.getBytes());
        out.flush();
        if (checkAck(in) != 0) {
            throw new JSchException();
        }
    }

    private static String getPath(final String uri, final boolean source) throws URISyntaxException {
        int pos = uri.indexOf('/', uri.indexOf("://") + 3);

        if (pos == -1) {
            if (source != true)
                return "/";
            throw new URISyntaxException("URI should not end with '/'.", uri);
        }

        String path = uri.substring(pos);

        if (path.equals("/~")) {
            if (source != true)
                return ".";
            throw new URISyntaxException("URI should not end with '~'.", uri);
        }

        if (path.endsWith("/")) {
            if (path.equals("/~/") && (source != true))
                return "./";
            throw new URISyntaxException("URI should not end with '/'.", uri);
        }

        if (path.startsWith("/~/"))
            return path.substring(3);

        return path;
    }

    public static void exec(final String cmdUri, final String identity) throws URISyntaxException, JSchException,
            IOException {
        Session session = null;
        try {
            session = connect(cmdUri, identity);
            if (session != null) {
                exec(session, cmdUri.substring(cmdUri.indexOf('/', cmdUri.indexOf("://") + 3) + 1));
            }
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private static void exec(final Session session, final String cmd) throws JSchException, IOException {
        InputStream in = null;
        Channel channel = session.openChannel("exec");
        boolean foo = false;

        try {
            ((ChannelExec) channel).setCommand(cmd);

            channel.setInputStream(null);
            // ((ChannelExec) channel).setErrStream(System.err);
            in = channel.getInputStream();

            // channel.setInputStream(System.in);
            // channel.setOutputStream(System.out);

            // FileOutputStream fos=new FileOutputStream("/tmp/stderr");
            // ((ChannelExec)channel).setErrStream(fos);
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    System.out.print(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    if (channel.getExitStatus() != 0) {
                        foo = true;
                    }
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ee) {
                }
            }
        } finally {
            closeQuietly(in, null, null, null);
            channel.disconnect();
        }
        if (foo) {
            throw new JSchException("SSH Exec Error");
        }
    }

    public static void scpCopyTo(String source, String destinationUrl, String destination, String identity, long retention, boolean setAccessTimeAfterSetReadonly)
            throws URISyntaxException, JSchException, IOException {
        String basePath = getPath(destinationUrl, false);

        Session session = connect(destinationUrl, identity);
        Channel channel = session.openChannel("exec");

        FileInputStream fis = null;
        OutputStream out = null;
        InputStream in = null;

        try {
            String dDir = new File(destination).getParent();
            String dFile = new File(destination).getName();

            String command = "scp "+(retention>0 ? "-p " :"")+"-t -r " + basePath;
            ((ChannelExec) channel).setCommand(command);
            out = channel.getOutputStream();
            in = channel.getInputStream();

            channel.connect();

            if (checkAck(in) != 0) {
                throw new JSchException();
            }

            // prepend remote directory with "."
            if (dDir == null) {
                dDir = ".";
            } else {
                dDir = "./" + dDir;
            }

            int cnt = 0;
            // create and decent into directory structure - and count decent
            String[] folders = dDir.split("/");
            for (String folder : folders) {
                if (folder.length() > 0) {
                    cnt++;
                    sendCommand(out, in, "D0755 0 " + folder + "\n");
                }
            }

            File _lfile = new File(source);
            long filesize = _lfile.length();
            if (retention>0) {
                sendCommand(out, in, "T " + retention + " 0 " + retention + " 0\n");
            }
            
//          sendCommand(out, in, "C"+(setAccessTimeAfterSetReadonly?"0444":"0755")+" " + filesize + " " + dFile + "\n");
            sendCommand(out, in, "C0444 " + filesize + " " + dFile + "\n");

            fis = new FileInputStream(source);
            byte[] buf = new byte[1024];
            while (true) {
                int len = fis.read(buf, 0, buf.length);
                if (len <= 0)
                    break;
                out.write(buf, 0, len); // out.flush();
            }

            out.write(ackMsg, 0, 1);
            out.flush();

            if (checkAck(in) != 0) {
                throw new JSchException();
            }

            // remotly "close" open directories
            while (cnt-- > 0) {
                sendCommand(out, in, "E\n");
            }

        } finally {
            closeQuietly(in, out, fis, null);
            channel.disconnect();
            session.disconnect();
        }
    }

    public static void scpCopyFrom(final String source, final String destination, String identity) throws Exception {
        String sPath = getPath(source, true);

        Session session = connect(source, identity);

        Channel channel = session.openChannel("exec");

        FileOutputStream fos = null;
        OutputStream out = null;
        InputStream in = null;
        try {
            String dDir = new File(destination).getParent() + "/";
            // create destination
            if (!new File(dDir).exists()) {
                new File(dDir).mkdirs();
            }

            String command = "scp -f " + sPath;
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            out = channel.getOutputStream();
            in = channel.getInputStream();

            channel.connect();

            out.write(ackMsg, 0, 1);
            out.flush();

            byte[] buf = new byte[1024];

            long filesize = scpGetHeader(in);

            if (filesize > -1) {
                out.write(ackMsg, 0, 1);
                out.flush();

                fos = new FileOutputStream(destination);
                int foo;
                while (true) {
                    if (buf.length < filesize)
                        foo = buf.length;
                    else
                        foo = (int) filesize;
                    foo = in.read(buf, 0, foo);
                    if (foo < 0) {
                        // error
                        break;
                    }
                    fos.write(buf, 0, foo);
                    filesize -= foo;
                    if (filesize == 0L)
                        break;
                }

                if (checkAck(in) != 0) {
                    throw new Exception();
                }

                out.write(ackMsg, 0, 1);
                out.flush();
            }
        } finally {
            closeQuietly(in, out, null, fos);
            channel.disconnect();
            session.disconnect();
        }
    }

    public static long scpFileLength(String uri, String identity) throws Exception {
        String sPath = getPath(uri, true);

        Session session = connect(uri, identity);
        Channel channel = session.openChannel("exec");

        OutputStream out = null;
        InputStream in = null;

        long filesize = -1L;

        try {
            String command = "scp -f " + sPath;
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            out = channel.getOutputStream();
            in = channel.getInputStream();

            channel.connect();

            out.write(ackMsg, 0, 1);
            out.flush();

            filesize = scpGetHeader(in);

            // After getting filesize, we do not care about file content - so we
            // send an error message to abort the connection
            out.write(nackMsg, 0, 2);
            out.flush();
        } finally {
            closeQuietly(in, out, null, null);
            channel.disconnect();
            session.disconnect();
        }

        return filesize;
    }

    private static long scpGetHeader(InputStream in) throws IOException {
        long filesize = 0;
        byte[] buf = new byte[512];

        if (checkAck(in) != 'C') {
            return -1L;
        }

        // read '0644 '
        in.read(buf, 0, 5);

        while (true) {
            if (in.read(buf, 0, 1) < 0) {
                // error
                break;
            }
            if (buf[0] == ' ')
                break;
            filesize = filesize * 10L + (long) (buf[0] - '0');
        }

        // skip filename
        for (int i = 0;; i++) {
            in.read(buf, i, 1);
            if (buf[i] == (byte) 0x0a) {
                break;
            }
        }

        return filesize;
    }

    public static void sftpCopyTo(String source, String destinationUrl, String destination, String identity, long retention, boolean setAccessTimeAfterSetReadonly)
            throws Exception {
        String basePath = getPath(destinationUrl, false);

        if (basePath.endsWith("/") && (basePath.length() > 1)) {
            throw new URISyntaxException("Destination SSH/SFTP-URL (" + destinationUrl + ") should not end with '/'.",
                    destinationUrl);
        }

        Session session = connect(destinationUrl, identity);
        String dDir = new File(destination).getParent();
        String dFile = new File(destination).getName();

        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp channelSftp = (ChannelSftp) channel;
        channelSftp.cd(basePath);
        if (dDir != null) {
            String[] folders = dDir.split("/");
            for (String folder : folders) {
                if (folder.length() > 0) {
                    try {
                        channelSftp.cd(folder);
                    } catch (SftpException e) {
                        channelSftp.mkdir(folder);
                        channelSftp.cd(folder);
                    }
                }
            }
        }
        channelSftp.put(source, dFile);
        if (setAccessTimeAfterSetReadonly)
            channelSftp.chmod(292, dFile); 
        if (retention>0)
            channelSftp.setMtime(dFile, (int) retention);
        if (!setAccessTimeAfterSetReadonly)
            channelSftp.chmod(292, dFile); 
        channelSftp.disconnect();
        channel.disconnect();
        session.disconnect();
    }

    public static void sftpCopyFrom(String source, String destination, String identity) throws Exception {
        String src = getPath(source, true);

        Session session = connect(source, identity);
        String dDir = new File(destination).getParent() + "/";
        if (!new File(dDir).exists()) {
            new File(dDir).mkdirs();
        }

        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp channelSftp = (ChannelSftp) channel;
        channelSftp.get(src, destination);
        channelSftp.disconnect();
        channel.disconnect();
        session.disconnect();
    }

    public static long sftpFileLength(String source, String identity) throws Exception {
        String src = getPath(source, true);

        Session session = connect(source, identity);
        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp channelSftp = (ChannelSftp) channel;
        long fsize = -1L;
        try {
            SftpATTRS attrs = channelSftp.lstat(src);
            fsize = attrs.getSize();
        } catch (Exception e) {
        }
        channelSftp.disconnect();
        channel.disconnect();
        session.disconnect();
        return fsize;
    }
}