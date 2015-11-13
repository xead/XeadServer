package xeadServer;

/*
 * Copyright (c) 2015 WATANABE kozo <qyf05466@nifty.com>,
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
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
	public static final String APPLICATION_NAME  = "X-TEA Server/ DB Method Controler";
	public static final String VERSION  = "1.1.0";

	///////////////////////////
	// DB DRIVER CLASS NAMES //
	///////////////////////////
	private static final String DRIVER_DERBY = "org.apache.derby.jdbc.ClientDriver";
	private static final String DRIVER_MYSQL = "com.mysql.jdbc.Driver";
	private static final String DRIVER_POSTGRESQL = "org.postgresql.Driver";
	private static final String DRIVER_ORACLE = "oracle.jdbc.driver.OracleDriver";
	private static final String DRIVER_SQLSERVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
	private static final String DRIVER_ACCESS = "net.ucanaccess.jdbc.UcanaccessDriver";
	private static final String DRIVER_H2 = "org.h2.Driver";

	/////////////////////
	// GLOBAL VARIANTS //
	/////////////////////
	private ArrayList<String> databaseIDList = new ArrayList<String>();
	private ArrayList<Integer> errorCountList = new ArrayList<Integer>();
	private ArrayList<Integer> clearedCountList = new ArrayList<Integer>();
	private ArrayList<Boolean> databaseReadOnlyEnableList = new ArrayList<Boolean>();
	private ArrayList<String> lastCommandList = new ArrayList<String>();
	private ArrayList<BasicDataSource> dataSourceList = new ArrayList<BasicDataSource>();
	private HashMap<String, Connection> manualCommitConnectionMapBySession = new HashMap<String, Connection>();
	private HashMap<String, String> manualCommitLastCommandMapBySession = new HashMap<String, String>();
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); 
	
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
		errorCountList.add(0);
		clearedCountList.add(0);
		dataSource = new BasicDataSource();
		dataSource.setDriverClassName((String)dbOptionMap.get("DriverClassName"));
		dataSource.setUsername((String)dbOptionMap.get("UserName"));
		dataSource.setPassword((String)dbOptionMap.get("Password"));
		dataSource.setUrl((String)dbOptionMap.get("Url"));
		dataSource.setMaxActive((Integer)dbOptionMap.get("MaxActive"));
		dataSource.setMaxIdle((Integer)dbOptionMap.get("MaxIdle"));
		dataSource.setMaxWait((Integer)dbOptionMap.get("MaxWait"));
		dataSource.setDefaultAutoCommit(false);
		dataSource.setDefaultReadOnly(false);
		dataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		dataSourceList.add(dataSource);
		databaseReadOnlyEnableList.add((Boolean)dbOptionMap.get("ReadOnlyEnabled"));
		lastCommandList.add("");

		///////////////////////////////////////
		// Setup Connection Pool for Sub-DBs //
		///////////////////////////////////////
		NodeList subDBList = domDocument.getElementsByTagName("SubDB");
		for (int i = 0; i < subDBList.getLength(); i++) {
			dbOptionMap = getSubDbOptionMap((org.w3c.dom.Element)subDBList.item(i), currentFolder);
			databaseIDList.add((String)dbOptionMap.get("ID"));
			errorCountList.add(0);
			clearedCountList.add(0);
			dataSource = new BasicDataSource();
			dataSource.setDriverClassName((String)dbOptionMap.get("DriverClassName"));
			dataSource.setUsername((String)dbOptionMap.get("UserName"));
			dataSource.setPassword((String)dbOptionMap.get("Password"));
			dataSource.setUrl((String)dbOptionMap.get("Url"));
			dataSource.setMaxActive((Integer)dbOptionMap.get("MaxActive"));
			dataSource.setMaxIdle((Integer)dbOptionMap.get("MaxIdle"));
			dataSource.setMaxWait((Integer)dbOptionMap.get("MaxWait"));
			dataSource.setDefaultAutoCommit(true);
			dataSource.setDefaultReadOnly(true);
			dataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			dataSourceList.add(dataSource);
			databaseReadOnlyEnableList.add((Boolean)dbOptionMap.get("ReadOnlyEnabled"));
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
		map.put("MaxWait", 5000); //DEFAULT//
		
		map.put("ID", "");
		String dbName = element.getAttribute("DatabaseName");
		dbName = dbName.replace("<CURRENT>", currentFolder);
		map.put("Url", dbName);
		map.put("DriverClassName", getDriverClassName(dbName));
		map.put("UserName", element.getAttribute("DatabaseUser"));
		map.put("Password", element.getAttribute("DatabasePassword"));
		if (dbName.contains("jdbc:postgresql")) {
			map.put("ReadOnlyEnabled", false);
		} else {
			map.put("ReadOnlyEnabled", true);
		}

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
			if (key.equals("MaxWait")) {
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
		map.put("MaxWait", 5000); //DEFAULT//
		
		map.put("ID", element.getAttribute("ID"));
		String dbName = element.getAttribute("Name");
		dbName = dbName.replace("<CURRENT>", currentFolder);
		map.put("Url", dbName);
		map.put("DriverClassName", getDriverClassName(dbName));
		map.put("UserName", element.getAttribute("User"));
		map.put("Password", element.getAttribute("Password"));
		if (dbName.contains("jdbc:postgresql")) {
			map.put("ReadOnlyEnabled", false);
		} else {
			map.put("ReadOnlyEnabled", true);
		}

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
			if (key.equals("MaxWait")) {
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
	    if (url.contains("jdbc:derby")) {
	    	name = DRIVER_DERBY;
	    }
	    if (url.contains("jdbc:mysql")) {
	    	name = DRIVER_MYSQL;
	    }
	    if (url.contains("jdbc:postgresql")) {
	    	name = DRIVER_POSTGRESQL;
	    }
	    if (url.contains("jdbc:oracle")) {
	    	name = DRIVER_ORACLE;
	    }
	    if (url.contains("jdbc:sqlserver")) {
	    	name = DRIVER_SQLSERVER;
	    }
	    if (url.contains("jdbc:ucanaccess")) {
	    	name = DRIVER_ACCESS;
	    }
	    if (url.contains("jdbc:h2")) {
	    	name = DRIVER_H2;
	    }
	    return name;
	}
	
	///////////////////////////////////////////////////////
	// Processing "Get" requests to return the test page //
	///////////////////////////////////////////////////////
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {
			String sessionID = "";
			String lastCommand = "";
			res.setContentType("text/html; charset=UTF-8");
			StringBuffer sb = new StringBuffer();
			sb.append("<html>");
			sb.append("<head>");
			sb.append("<title>X-TEA Server/ DB-Method Controler</title>");
			sb.append("</head>");
			sb.append("<body>");
			sb.append("<h3>X-TEA Server(");
			sb.append(VERSION);
			sb.append(") Database Connections</h3>");

			///////////////////////////////////////
			// Number of active/idle connections //
			///////////////////////////////////////
			sb.append("<table border='2' cellpadding='2'>");
			sb.append("<tr style='background:#ccccff'><th>DB ID</th><th>MaxActive</th><th>MaxIdle</th><th>MaxWait</th><th>Active</th><th>Idle</th><th>Error</th><th>Cleared</th></tr>");
			for (int i = 0; i < databaseIDList.size(); i++) {
				if (databaseIDList.get(i).equals("")) {
					sb.append("<tr><td>Main</td><td align='right'>"
							+ dataSourceList.get(i).getMaxActive()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getMaxIdle()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getMaxWait()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getNumActive()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getNumIdle()
							+ "</td><td align='right'>"
							+ errorCountList.get(i) 
							+ "</td><td align='right'>"
							+ clearedCountList.get(i) 
							+ "</td></tr>");
				} else {
					sb.append("<tr><td>" + databaseIDList.get(i) + "</td><td align='right'>"
							+ dataSourceList.get(i).getMaxActive()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getMaxIdle()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getMaxWait()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getNumActive()
							+ "</td><td align='right'>"
							+ dataSourceList.get(i).getNumIdle()
							+ "</td><td align='right'>"
							+ errorCountList.get(i) 
							+ "</td><td align='right'>"
							+ clearedCountList.get(i) 
							+ "</td></tr>");
				}
			}
			sb.append("</table><br>");

			//////////////////////////////////
			// Connection status by session //
			//////////////////////////////////
			int rowNumber = 0;
			sb.append("<table border='2' cellpadding='2'>");
			sb.append("<tr style='background:#ccccff'><th>No.</th><th>DB ID</th><th>Session ID</th><th>Last Command Processed</th></tr>");
			for (int i = 0; i < databaseIDList.size(); i++) {
				if (databaseIDList.get(i).equals("")) {
					rowNumber++;
					lastCommand = lastCommandList.get(i);
					sb.append("<tr><td align='right'>" + rowNumber + "</td><td>Main</td><td>"
							+ "*SYSTEM" + "</td><td>" + lastCommand + "</td></tr>");

					for (Iterator<String> it = manualCommitConnectionMapBySession.keySet().iterator(); it.hasNext(); ) {
						rowNumber++;
						sessionID = it.next();
						lastCommand = manualCommitLastCommandMapBySession.get(sessionID);
						sb.append("<tr><td align='right'>" + rowNumber + "</td><td>Main</td><td>"
								+ sessionID + "</td><td>" + lastCommand + "</td></tr>");
					}

				} else {
					rowNumber++;
					lastCommand = lastCommandList.get(i);
					sb.append("<tr><td align='right'>" + rowNumber + "</td><td>" + databaseIDList.get(i) + "</td><td>"
							+ "*SYSTEM" + "</td><td>" + lastCommand + "</td></tr>");
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
		res.setContentType("text/html; charset=UTF-8");

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

			///////////////////////////////////////
			// Close connection for this session //
			///////////////////////////////////////
			String sessionID = getSessionIDFromMethod(parmMethod);
			if (!sessionID.equals("")) {
				try {
					closeConnectionForSession(sessionID, "ROLLBACK");
				} catch (Exception e1) {
				}
			}

			//////////////////////////
			// Handle Error Message //
			//////////////////////////
			StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
			lastCommandList.add(databaseIDList.indexOf(parmDBID), parmMethod + "<br>Exception: " + e.getMessage() + "<br>" + stringWriter.toString());
			errorCountList.add(databaseIDList.indexOf(parmDBID), errorCountList.get(databaseIDList.indexOf(parmDBID)) + 1);
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error:<br>"+e.getMessage());
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
					if (databaseReadOnlyEnableList.get(databaseIDList.indexOf(dbID))) {
						conn.setReadOnly(true);
					}
					if (conn.isClosed()) {
						throw new Exception("DB-connection for this session is already closed.");
					} else {
						stmt = conn.createStatement();
						rset = stmt.executeQuery(method);
						relation = new Relation(rset);
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

		return relation;
	}

	/////////////////////////////////////
	// Retrieve Session ID from Method //
	/////////////////////////////////////
	private String getSessionIDFromMethod(String method) {
		String sessionID = "";
		String methodWork = method.replaceAll(" ", "").toUpperCase();
		if (methodWork.contains("(NRSESSION,")) {
			int pos1 = methodWork.indexOf("VALUES('");
			if (pos1 > 0) {
				int pos2 = methodWork.indexOf("'", pos1+8);
				sessionID = methodWork.substring(pos1+8, pos2);
			}
		}
		return sessionID;
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
					if (conn.isClosed()) {
						throw new Exception("DB-connection for this session is already closed.");
					} else {
						if (databaseReadOnlyEnableList.get(databaseIDList.indexOf(dbID))) {
							conn.setReadOnly(false);
						}
						stmt = conn.createStatement();
						count = stmt.executeUpdate(method);
					}
				}

			} finally {
				if(conn != null && sessionID.equals("")) {
					conn.commit();
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
						if (conn.isClosed()) {
							throw new Exception("DB-connection for this session is already closed.");
						} else {
							if (databaseReadOnlyEnableList.get(databaseIDList.indexOf(dbID))) {
								conn.setReadOnly(true);
							}
							stmt = conn.createStatement();
							rset = stmt.executeQuery(method);
							if (rset.next()) {
								count = rset.getInt(1);
							}
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
		BasicDataSource dataSource = dataSourceList.get(databaseIDList.indexOf(dbID));
		Date currentDate = new Date(); 
		String currentTime = " (" + sdf.format(currentDate) + ")";

		//////////////////////////////////////////////////
		// Close the oldest connection if active in max //
		//////////////////////////////////////////////////
		if ((dataSource.getNumActive() + 1) >= dataSource.getMaxActive()) {
			int pos1, pos2;
			String wrkStr, oldestSessionID = "";
			Date dateTime;
			long currentTimeLong = currentDate.getTime();
			long timeLong;
			long diffWork, diffTime = 0;
			List<String> listLastCommand = new ArrayList<String>(manualCommitLastCommandMapBySession.values());
			List<String> listSessionID = new ArrayList<String>(manualCommitLastCommandMapBySession.keySet());
			for (int i=0; i < listLastCommand.size(); i++) {
				pos1 = listLastCommand.get(i).lastIndexOf(" (");
				pos2 = listLastCommand.get(i).lastIndexOf(")");
				if (pos1 > 0 && pos2 > 0) {
					wrkStr = listLastCommand.get(i).substring(pos1+2, pos2);
					try {
						dateTime = sdf.parse(wrkStr);
						timeLong = dateTime.getTime();
						diffWork = currentTimeLong - timeLong;
						if (diffWork > diffTime) {
							diffTime = diffWork;
							oldestSessionID = listSessionID.get(i);
						}
					} catch (ParseException e1) {
					}
				}
			}
			if (diffTime > 300000 && !oldestSessionID.equals("")) {
				try {
					closeConnectionForSession(oldestSessionID, "ROLLBACK");
					clearedCountList.add(databaseIDList.indexOf(dbID), clearedCountList.get(databaseIDList.indexOf(dbID)) + 1);
				} catch (Exception e1) {
				}
			}
		}

		//////////////////////////////////
		// Get the connection available //
		//////////////////////////////////
		if (dbID.equals("")) {
			if (sessionID.equals("")) {
				connection = dataSource.getConnection();
				lastCommandList.add(databaseIDList.indexOf(dbID), command + currentTime);
			} else {
				if (manualCommitConnectionMapBySession.containsKey(sessionID)) {
					connection = manualCommitConnectionMapBySession.get(sessionID);
				} else {
					connection = dataSource.getConnection();
					manualCommitConnectionMapBySession.put(sessionID, connection);
				}
				manualCommitLastCommandMapBySession.put(sessionID, command + currentTime);
			}
		} else {
			connection = dataSource.getConnection();
			if (databaseReadOnlyEnableList.get(databaseIDList.indexOf(dbID))) {
				connection.setReadOnly(true);
			}
			lastCommandList.add(databaseIDList.indexOf(dbID), command + currentTime);
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
				conn.commit();
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
					manualCommitLastCommandMapBySession.remove(sessionID);
					connection.close();
				}
			}
		}
	}
}
