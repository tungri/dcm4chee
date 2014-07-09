<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%@ page import="java.sql.*" %>
<%@ page import="java.io.*" %> 
<%@page import ="javax.servlet.*" %>
<%@page import= "javax.servlet.jsp.*" %>
<%@ page language="java" %>
<%
JspWriter wr = pageContext.getOut();
Connection con=null;
PreparedStatement pst=null;
try
{
    //Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
    ///con = DriverManager.getConnection("jdbc:odbc:ruhi","system","system");
     //pst=con.prepareStatement("insert into repository values(?,?,?,?,?)");
    File file=new File("C:/Users/Satya/Pictures/Desert.jpg");
    FileInputStream fis=new FileInputStream(file);
    /*pst.setString(1,"Ship");
    pst.setString(2,"Detroit");
    pst.setString(3,"yes");
    pst.setString(4,"CompleteProduct");*/
    pst.setBinaryStream(1 ,fis, (int)fis.available());
     int i = pst.executeUpdate();
    if(i!=0){
      wr.write("image inserted successfully");
    }
    else{
      wr.write("problem in image insertion");
    }  
  }
  catch (Exception e){
    System.out.println(e);
  }
%> 