package hipl.firstservletproject.servlets;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.GenericServlet;
import javax.servlet.ServletException;


/**
 * Servlet implementation class FileViewServlet
 */
//@WebServlet("/FileViewServlet")

@WebServlet(description = "My First Servlet", urlPatterns = { "/FileViewServlet" , "/FileViewServlet.do"},
initParams = {@WebInitParam(name="id",value="1"),@WebInitParam(name="name",value="pankaj")})
public class FileViewServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	public static final String HTML_START = "<html><body>";
	public static final String HTML_END = "</body></html>";
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public FileViewServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@SuppressWarnings("null")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		PrintWriter out = response.getWriter();
		Date date = new Date();
		out.println(HTML_START+"<h2>Hi There!</h2><br/><h3>Date="+date +"</h3>");
		
		try {
			PrintWriter out1 = response.getWriter();
			//Date date = new Date();
			//out.println(HTML_START+"<h2>Hi There!</h2><br/><h3>Date="+date +"</h3>");
			
	        /*response.setContentType("application/json");
	        response.setCharacterEncoding("UTF-8");
			response.setContentType(HTML_START);*/
	        //PrintWriter out1 = response.getWriter();
			response.setContentType("text/html");
			
	        File Dir = new File ("C:/Users/Satya/Downloads/apache-tomcat-7.0.54/tmpfiles");
	        File[] listOfFiles = Dir.listFiles();
	        
	        //out1.println(HTML_START);  
	        for (int i = 0; i < listOfFiles.length; i++) {
	            if (listOfFiles[i].isFile()) {
	              out1.println("File " + listOfFiles[i].getName() + "<br/>");
	            } else if (listOfFiles[i].isDirectory()) {
	              out1.println("Directory " + listOfFiles[i].getName() + "br");
	            }
	          } 
	        out1.println(HTML_END); 
	        
			//File pdfFolder = new File(request.getSession().getServletContext().getRealPath("C:/Users/Satya/Downloads/apache-tomcat-7.0.54/tmpfiles"));

	        //for (File pdf : pdfFolder.listFiles()) { // Line 27
	            //out1.println(pdf.getName());
	        //}
	    } catch (IOException e) {
	        GenericServlet exceptionlog = null;
			exceptionlog.log(e.getMessage());
	    }
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
	}

}
