package xeadServer;

/*
 * Copyright (c) 2014 WATANABE kozo <qyf05466@nifty.com>,
 * All rights reserved.
 *
 * This file is part of XEAD Server.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the XEAD Project nor the names of its contributors
 *       may be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DBMethod extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/////////////////////////////
	// APPLICATION INFORMATION //
	/////////////////////////////
	public static final String APPLICATION_NAME  = "XEAD Server/ DB Method Controler";
	public static final String VERSION  = "V1.R0.M1";

	///////////////////////////
	// DB DRIVER CLASS NAMES //
	///////////////////////////
	private static final String DRIVER_DERBY = "org.apache.derby.jdbc.ClientDriver";
	private static final String DRIVER_MYSQL = "com.mysql.jdbc.Driver";
	private static final String DRIVER_POSTGRESQL = "org.postgresql.Driver";
	private static final String DRIVER_ORACLE = "oracle.jdbc.driver.OracleDriver";

	/////////////////////
	// GLOBAL VARIANTS //
	/////////////////////
	private ArrayList<String> databaseIDList = new ArrayList<String>();
	private ArrayList<String> lastCommandList = new ArrayList<String>();
	private ArrayList<DataSource> dataSourceList = new ArrayList<DataSource>();
	private HashMap<String, Connection> manualCommitConnectionMapBySession = new HashMap<String, Connection>();
	private HashMap<String, String> manualCommitLastCommandMapBySession = new HashMap<String, String>();
	
    //////////////////
	// Initializing //
	//////////////////
	public void init() throws ServletException {
		String currentFolder = "";
		org.w3c.dom.Document domDocument = null;
		BasicDataSource dataSource;
		HashMap<String, Object> dbOptionMap;
		super.init();

		////////////////////////////////////////////////////////////
		// Get parameter value of "SystemDefinition" to parse DOM //
		////////////////////////////////////////////////////////////
		String fileName = getServletConfig().getInitParameter("SystemDefinition");
		try {
			File xeafFile = new File(fileName);
			if (xeafFile.exists()) {
				currentFolder = xeafFile.getParent();
				DOMParser parser = new DOMParser();
				parser.parse(new InputSource(new FileInputStream(fileName)));
				domDocument = parser.getDocument();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		NodeList nodeList = domDocument.getElementsByTagName("System");
		org.w3c.dom.Element systemElement = (org.w3c.dom.Element)nodeList.item(0);

		///////////////////////////////////////
		// Setup Connection Pool for Main-DB //
		///////////////////////////////////////
		dbOptionMap = getMainDbOptionMap(systemElement, currentFolder);
		databaseIDList.add((String)dbOptionMap.get("ID"));
		dataSource = new BasicDataSource();
		dataSource.setDriverClassName((String)dbOptionMap.get("DriverClassName"));
		dataSource.setUsername((String)dbOptionMap.get("UserName"));
		dataSource.setPassword((String)dbOptionMap.get("Password"));
		dataSource.setUrl((String)dbOptionMap.get("Url"));
		dataSource.setMaxActive((Integer)dbOptionMap.get("MaxActive"));
		dataSource.setMaxIdle((Integer)dbOptionMap.get("MaxIdle"));
		dataSource.setMinIdle((Integer)dbOptionMap.get("MinIdle"));
		dataSource.setDefaultAutoCommit(false);
		dataSource.setDefaultReadOnly(false);
		dataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		dataSourceList.add(dataSource);
		lastCommandList.add("");

		///////////////////////////////////////
		// Setup Connection Pool for Sub-DBs //
		///////////////////////////////////////
		NodeList subDBList = domDocument.getElementsByTagName("SubDB");
		for (int i = 0; i < subDBList.getLength(); i++) {
			dbOptionMap = getSubDbOptionMap((org.w3c.dom.Element)subDBList.item(i), currentFolder);
			databaseIDList.add((String)dbOptionMap.get("ID"));
			dataSource = new BasicDataSource();
			dataSource.setDriverClassName((String)dbOptionMap.get("DriverClassName"));
			dataSource.setUsername((String)dbOptionMap.get("UserName"));
			dataSource.setPassword((String)dbOptionMap.get("Password"));
			dataSource.setUrl((String)dbOptionMap.get("Url"));
			dataSource.setMaxActive((Integer)dbOptionMap.get("MaxActive"));
			dataSource.setMaxIdle((Integer)dbOptionMap.get("MaxIdle"));
			dataSource.setMinIdle((Integer)dbOptionMap.get("MinIdle"));
			dataSource.setDefaultAutoCommit(true);
			dataSource.setDefaultReadOnly(true);
			dataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			dataSourceList.add(dataSource);
			lastCommandList.add("");
		}
	}

    ///////////////////////////////
	// Get DB Options of Main-DB //
	///////////////////////////////
	private HashMap<String, Object> getMainDbOptionMap(org.w3c.dom.Element element, String currentFolder) {
		String options, option, key, value;
		StringTokenizer tokenizer1, tokenizer2;

		HashMap<String, Object> map = new HashMap<String, Object>(); 
		map.put("MaxActive", 10); //DEFAULT//
		map.put("MaxIdle", 10); //DEFAULT//
		map.put("MinIdle", 5); //DEFAULT//
		
		map.put("ID", "");
		String dbName = element.getAttribute("DatabaseName");
		dbName = dbName.replace("<CURRENT>", currentFolder);
		map.put("Url", dbName);
		map.put("DriverClassName", getDriverClassName(dbName));
		map.put("UserName", element.getAttribute("DatabaseUser"));
		map.put("Password", element.getAttribute("DatabasePassword"));

		options = element.getAttribute("DBCP").replaceAll(" ", "");
		tokenizer1 = new StringTokenizer(options, ",");
		while (tokenizer1.hasMoreTokens()) {
			option = tokenizer1.nextToken();
			tokenizer2 = new StringTokenizer(option, "=");
			key = tokenizer2.nextToken();
			value = tokenizer2.nextToken();
			if (key.equals("MaxActive")) {
				map.put(key, Integer.parseInt(value));
			}
			if (key.equals("MaxIdle")) {
				map.put(key, Integer.parseInt(value));
			}
			if (key.equals("MinIdle")) {
				map.put(key, Integer.parseInt(value));
			}
		}

		return map;
	}

    //////////////////////////////
	// Get DB Options of Sub-DB //
	//////////////////////////////
	private HashMap<String, Object> getSubDbOptionMap(org.w3c.dom.Element element, String currentFolder) {
		String options, option, key, value;
		StringTokenizer tokenizer1, tokenizer2;

		HashMap<String, Object> map = new HashMap<String, Object>(); 
		map.put("MaxActive", 10); //DEFAULT//
		map.put("MaxIdle", 10); //DEFAULT//
		map.put("MinIdle", 5); //DEFAULT//
		
		map.put("ID", element.getAttribute("ID"));
		String dbName = element.getAttribute("Name");
		dbName = dbName.replace("<CURRENT>", currentFolder);
		map.put("Url", dbName);
		map.put("DriverClassName", getDriverClassName(dbName));
		map.put("UserName", element.getAttribute("User"));
		map.put("Password", element.getAttribute("Password"));

		options = element.getAttribute("DBCP").replaceAll(" ", "");
		tokenizer1 = new StringTokenizer(options, ",");
		while (tokenizer1.hasMoreTokens()) {
			option = tokenizer1.nextToken();
			tokenizer2 = new StringTokenizer(option, "=");
			key = tokenizer2.nextToken();
			value = tokenizer2.nextToken();
			if (key.equals("MaxActive")) {
				map.put(key, Integer.parseInt(value));
			}
			if (key.equals("MaxIdle")) {
				map.put(key, Integer.parseInt(value));
			}
			if (key.equals("MinIdle")) {
				map.put(key, Integer.parseInt(value));
			}
		}

		return map;
	}

    /////////////////////////////////////////////
	// Get Driver Class Name according to DBMS //
	/////////////////////////////////////////////
	private String getDriverClassName(String url) {
		String name = "";
	    if (url.contains("derby")) {
	    	name = DRIVER_DERBY;
	    }
	    if (url.contains("mysql")) {
	    	name = DRIVER_MYSQL;
	    }
	    if (url.contains("postgresql")) {
	    	name = DRIVER_POSTGRESQL;
	    }
	    if (url.contains("oracle")) {
	    	name = DRIVER_ORACLE;
	    }
	    return name;
	}
	
	///////////////////////////////////////////////////////
	// Processing "Get" requests to return the test page //
	///////////////////////////////////////////////////////
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {
			Connection con;
			String sessionID = "";
			String lastCommand = "";
			res.setContentType("text/html; charset=UTF-8");
			StringBuffer sb = new StringBuffer();
			sb.append("<html>");
			sb.append("<head>");
			sb.append("<title>XEAD Server/ DB-Method Controler</title>");
			sb.append("</head>");
			sb.append("<body>");
			sb.append("<h3>XEAD Server Database Connections</h3>");
			sb.append("<table border='2' cellpadding='2'>");
			sb.append("<tr style='background:#ccccff'><th>DB ID</th><th>Session ID</th><th>Connection</th><th>Last Command Processed</th></tr>");
			for (int i = 0; i < databaseIDList.size(); i++) {
				if (databaseIDList.get(i).equals("")) {
					try {
						lastCommand = lastCommandList.get(i);
						con = dataSourceList.get(i).getConnection();
						if (con == null) {
							sb.append("<tr><td>Main</td><td>*BLANK</td><td>NULL</td><td>" + lastCommand + "</td></tr>");
						} else {
							sb.append("<tr><td>Main</td><td>*BLANK</td><td>READY</td><td>" + lastCommand + "</td></tr>");
						}
					} catch (Exception e) {
						sb.append("<tr><td>Main</td><td>*BLANK</td><td>ERROR: " + e.getMessage() + "</td></tr>");
					}
					for (Iterator<String> it = manualCommitConnectionMapBySession.keySet().iterator(); it.hasNext(); ) {
						sessionID = it.next();
						lastCommand = manualCommitLastCommandMapBySession.get(sessionID);
						con = manualCommitConnectionMapBySession.get(sessionID);
						if (con == null) {
							sb.append("<tr><td>Main</td><td>" + sessionID + "</td><td>NULL</td><td>" + lastCommand + "</td></tr>");
						} else {
							sb.append("<tr><td>Main</td><td>" + sessionID + "</td><td>READY</td><td>" + lastCommand + "</td></tr>");
						}
					}
				} else {
					try {
						lastCommand = lastCommandList.get(i);
						con = dataSourceList.get(i).getConnection();
						if (con == null) {
							sb.append("<tr><td>" + databaseIDList.get(i) + "</td><td>*BLANK</td><td>NULL</td><td>" + lastCommand + "</td></tr>");
						} else {
							sb.append("<tr><td>" + databaseIDList.get(i) + "</td><td>*BLANK</td><td>READY</td><td>" + lastCommand + "</td></tr>");
						}
					} catch (Exception e) {
						sb.append("<tr><td>" + databaseIDList.get(i) + "</td><td>*BLANK</td><td>ERROR: " + e.getMessage() + "</td></tr>");
					}
				}
			}
			sb.append("</table>");
			sb.append("</body>");
			sb.append("</html>");
			PrintWriter out = res.getWriter();
			out.print(new String(sb));
			out.close();
		} catch (Exception e) {	
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	/////////////////////////////////////////////////////////////////
	// Processing "Post" requests to return serialized result sets //
	/////////////////////////////////////////////////////////////////
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		String parmSessionID = "";
		String parmMethod = "";
		String parmDBID = "";

		if (req.getParameter("SESSION") != null) {
			parmSessionID = req.getParameter("SESSION");
		}
		if (req.getParameter("METHOD") != null) {
			parmMethod = req.getParameter("METHOD");
		}
		if (req.getParameter("DB") != null) {
			parmDBID = req.getParameter("DB");
		}

		res.setContentType("text/html; charset=UTF-8");

		try {
			if (parmMethod.toUpperCase().startsWith("SELECT ")) {
				if (parmMethod.toUpperCase().contains("COUNT(")) {
					PrintWriter out = res.getWriter();
					out.print(getCountOfRecordProcessed(parmSessionID, parmMethod, parmDBID));
					out.close();
				} else {
					ObjectOutputStream stream = new ObjectOutputStream(res.getOutputStream());
					Relation relation = getResultOfSelect(parmSessionID, parmMethod, parmDBID);
					stream.writeObject(relation);
					stream.flush();
					stream.close();
				}
			}
			if (parmMethod.toUpperCase().startsWith("INSERT ")
					|| parmMethod.toUpperCase().startsWith("UPDATE ")
					|| parmMethod.toUpperCase().startsWith("DELETE ")) {
				PrintWriter out = res.getWriter();
				out.print(getCountOfRecordProcessed(parmSessionID, parmMethod, parmDBID));
				out.close();
			}
			if (parmMethod.toUpperCase().equals("COMMIT")) {
				closeConnectionForSession(parmSessionID, parmMethod);
				PrintWriter out = res.getWriter();
				out.print("The transaction was commited.");
				out.close();
			}
			if (parmMethod.toUpperCase().equals("ROLLBACK")) {
				closeConnectionForSession(parmSessionID, parmMethod);
				PrintWriter out = res.getWriter();
				out.print("The transaction was rollbacked.");
				out.close();
			}
			if (parmMethod.toUpperCase().startsWith("CALL ")) {
				callProcedure(parmSessionID, parmMethod, parmDBID);
				PrintWriter out = res.getWriter();
				out.print("The procedure was executed.");
				out.close();
			}
			if (parmMethod.toUpperCase().equals("IP")) {
				PrintWriter out = res.getWriter();
				out.print(req.getRemoteAddr());
				out.close();
			}
		} catch (Exception e) {	
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
	
	/////////////////////////////////////////////////
	// Get Relation from Result-Set of the Request //
	/////////////////////////////////////////////////
	private Relation getResultOfSelect(String sessionID, String method, String dbID) throws Exception {
		Connection conn = null;
		Statement stmt = null;
		ResultSet rset = null;
		Relation relation = null;

		if (method.toUpperCase().startsWith("SELECT ")) {
			try {
				conn = getConnectionForSession(sessionID, dbID, method);
				if (conn == null) {
					throw new Exception("DB-connection is not available now.");
				} else {
					conn.setReadOnly(true);
					stmt = conn.createStatement();
					rset = stmt.executeQuery(method);
					relation = new Relation(rset);
				}
			} finally {
				if (conn != null && (sessionID.equals("") || !dbID.equals(""))) {
					conn.close();
				}
				if(stmt != null){
					stmt.close();	
				}		    
				if(rset != null){
					rset.close();
				}
			}
		}

		return relation;
	}
	
	//////////////////////////////////////////////////
	// Get Count of Record Processed of the Request //
	//////////////////////////////////////////////////
	private int getCountOfRecordProcessed(String sessionID, String method, String dbID) throws Exception {
		Connection conn = null;
		Statement stmt = null;
		ResultSet rset = null;
		int count = -1;

		if (method.toUpperCase().startsWith("INSERT ")
			|| method.toUpperCase().startsWith("UPDATE ")
			|| method.toUpperCase().startsWith("DELETE ")) {
			try {
				conn = getConnectionForSession(sessionID, dbID, method);
				if (conn == null) {
					throw new Exception("DB-connection is not available now.");
				} else {
					conn.setReadOnly(false);
					stmt = conn.createStatement();
					count = stmt.executeUpdate(method);
				}
			} finally {
				if(conn != null && sessionID.equals("")) {
					conn.close();
				}
				if(stmt != null){
					stmt.close();	
				}		    
			}
		} else {
			if (method.toUpperCase().contains("COUNT(")) {
				try {
					conn = getConnectionForSession(sessionID, dbID, method);
					if (conn == null) {
						throw new Exception("DB-connection is not available now.");
					} else {
						stmt = conn.createStatement();
						rset = stmt.executeQuery(method);
    					if (rset.next()) {
    						count = rset.getInt(1);
    					}
					}
				} finally {
					if (conn != null && (sessionID.equals("") || !dbID.equals(""))) {
						conn.close();
					}
					if(stmt != null){
						stmt.close();	
					}		    
					if(rset != null){
						rset.close();
					}
				}
			}
		}

		return count;
	}
	
	/////////////////////////////////////////////
	// Get Connection For the specific session //
	/////////////////////////////////////////////
	private Connection getConnectionForSession(String sessionID, String dbID, String command) throws Exception {
		Connection connection = null;
		DataSource dataSource = dataSourceList.get(databaseIDList.indexOf(dbID));
		if (dbID.equals("")) {
			if (sessionID.equals("")) {
				connection = dataSource.getConnection();
				connection.setAutoCommit(true);
				lastCommandList.add(databaseIDList.indexOf(dbID), command);
			} else {
				if (manualCommitConnectionMapBySession.containsKey(sessionID)) {
					connection = manualCommitConnectionMapBySession.get(sessionID);
				} else {
					connection = dataSource.getConnection();
					connection.setAutoCommit(false);
					manualCommitConnectionMapBySession.put(sessionID, connection);
				}
				manualCommitLastCommandMapBySession.put(sessionID, command);
			}
		} else {
			connection = dataSource.getConnection();
			connection.setReadOnly(true);
		}
		return connection;
	}
	
	/////////////////////////////
	// Call Database procedure //
	/////////////////////////////
	private void callProcedure(String sessionID, String method, String dbID) throws Exception {
		Connection conn = null;
		Statement stmt = null;
		try {
			conn = getConnectionForSession(sessionID, dbID, method);
			if (conn == null) {
				throw new Exception("DB-connection is not available now.");
			} else {
				stmt = conn.createStatement();
				stmt.executeUpdate(method);
			}
		} finally {
			if(conn != null && (sessionID.equals("") || !dbID.equals(""))) {
				conn.close();
			}
			if(stmt != null){
				stmt.close();	
			}		    
		}
	}
	
	///////////////////////////////////////////////
	// Close connection for the specific session //
	///////////////////////////////////////////////
	private void closeConnectionForSession(String sessionID, String method) throws Exception {
		if (!sessionID.equals("")) {
			if (method.toUpperCase().equals("COMMIT") || method.toUpperCase().equals("ROLLBACK")) {
				if (manualCommitConnectionMapBySession.containsKey(sessionID)) {
					Connection connection = manualCommitConnectionMapBySession.get(sessionID);
					if (method.toUpperCase().equals("COMMIT")) {
						connection.commit();
					}
					if (method.toUpperCase().equals("ROLLBACK")) {
						connection.rollback();
					}
					manualCommitConnectionMapBySession.remove(sessionID);
					connection.close();
				}
			}
		}
	}
}
