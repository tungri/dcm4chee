package hipl.firstservletproject.servlets;
import java.awt.Robot;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

//import ij.plugin.DICOM;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.omg.CORBA.portable.InputStream;

//import com.sun.javafx.geom.Rectangle;
//import com.sun.javafx.tk.Toolkit;

//import fr.apteryx.imageio.dicom.DicomMetadata;
//import fr.apteryx.imageio.dicom.DicomReader;
//import fr.apteryx.imageio.dicom.Tag;
//import ij.plugin.DICOM;
//import java.awt.image.BufferedImage;
//import java.io.BufferedInputStream;
//import java.io.DataInputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.PrintWriter;
//import java.util.Enumeration;
//import java.util.Iterator;
//import java.util.List;
//import javax.imageio.ImageIO;
//import javax.imageio.ImageReader;
//import javax.imageio.stream.FileImageInputStream;
//import javax.servlet.ServletContext;
//import javax.servlet.ServletException;
//import javax.servlet.ServletOutputStream;
//import javax.servlet.annotation.WebServlet;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import jdcm.DicomElement;
//import jdcm.DicomFile;
//import jdcm.DicomGroup;
//import jdcm.DicomSet;
//import org.apache.commons.fileupload.FileItem;
//import org.apache.commons.fileupload.FileUploadException;
//import org.apache.commons.fileupload.disk.DiskFileItemFactory;
//import org.apache.commons.fileupload.servlet.ServletFileUpload;
//import fr.apteryx.imageio.dicom.DicomMetadata;
//import fr.apteryx.imageio.dicom.DicomReader;
//import fr.apteryx.imageio.dicom.Tag;


/**
 * Servlet implementation class FirstServlet
 */
@WebServlet(description = "My First Servlet", urlPatterns = { "/FirstServlet" , "/FirstServlet.do"},
			initParams = {@WebInitParam(name="id",value="1"),@WebInitParam(name="name",value="pankaj")})

public class FirstServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	public static final String HTML_START = "<html><body>";
	public static final String HTML_END = "</body></html>";
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public FirstServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		
		//System.out.println("Succesfully read the image");
		/*Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("dicom");
		//DicomReader reader = (DicomReader)readers.next();
		DicomReader reader = (DicomReader) readers.next();
		try{
		reader.setInput(new FileImageInputStream(new File("C:/Users/Satya/Pictures/SampelImage.dcm")));
		
		DicomMetadata dmd = reader.getDicomMetadata();
		//BufferedImage image = reader.read(0);
		System.out.println("Succesfully read the image");
		String patient_id = dmd.getAttributeString(Tag.PatientID);	
		System.out.println(patient_id);
		} catch (IOException e) {
			System.out.println("Exception in reading");
		}*/
		
		try {
			String line;
		//Runtime runtime = Runtime.getRuntime();
			String[] cmd = { "C:/Program Files/RadiAntViewer32bit/RadiAntViewer.exe"};
	    Process process = Runtime.getRuntime().exec(cmd);
	    //process.waitFor();
	    //Process process = new ProcessBuilder("C:/Program Files/ImageJ/ImageJ.exe").start();
	    System.out.println("Successfully executed exe");
		//Process process = new ProcessBuilder("C:/Program Files/ImageJ/ImageJ.exe").start();
	    BufferedReader bri = new BufferedReader (new InputStreamReader(process.getInputStream()));
		//InputStream is = process.getInputStream();
	    BufferedReader bre = new BufferedReader (new InputStreamReader(process.getErrorStream()));
		System.out.println("Successfully got input stream form process");
		
		while ((line = bri.readLine()) != null) {
	        System.out.println(line);
	      }
	      bri.close();
	      System.out.println("bri closed");
	      while ((line = bre.readLine()) != null) {
	        System.out.println(line);
	      }
	      bre.close();
	      //process.waitFor();
	      System.out.println("Done.");
		/*InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
		String line;

		System.out.printf("Output of running %s is:", "C:/Program Files/ImageJ/ImageJ.exe");

		while ((line = br.readLine()) != null) {
		  System.out.println(line);
		  }*/
	      
		
		} catch (Exception e) {
			System.out.println("Exception in running exe");
			e.printStackTrace();
		}
		
		PrintWriter out = response.getWriter();
		Date date = new Date();
		out.println(HTML_START+"<h1>Hi There!</h1><br/><h2>Date="+date +"</h2>"+HTML_END);
		
		out.println("<html><head>"+
		"<title>Application Executer</title>"+
		"<HTA:APPLICATION ID=\" oMyApp \"" +
		"APPLICATIONNAME=\"Application Executer\" " + 
		"BORDER=\"no\"" +
		"CAPTION=\"no\"" +
		"SHOWINTASKBAR=\"yes\""+
	    "SINGLEINSTANCE=\"yes\""+
	    "SYSMENU=\"yes\""+
	    "SCROLL=\"no\""+
	    "WINDOWSTATE=\"normal\">"+
    "\"<script type=\"text/javascript\" language=\"javascript\">" +
        "function RunFile() {" +
		"WshShell = new ActiveXObject(\"WScript.Shell\");"+
		"WshShell.Run(\"c:/windows/system32/notepad.exe\", 1, true);"+
        "}"+
    "</script>"+ 
"</head>"+
"<body>"+
	"<input type=\"button\" value=\"Run Notepad\" onclick=\"RunFile();\"/>\""+
"</body>"+
"</html>");
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		
	}

}
