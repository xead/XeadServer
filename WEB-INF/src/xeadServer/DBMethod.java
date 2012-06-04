package xeadServer;

/*
 * Copyright (c) 2012 WATANABE kozo <qyf05466@nifty.com>,
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
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

public class DBMethod extends HttpServlet {
	private static final long serialVersionUID = 1L;
	public static final String APPLICATION_NAME  = "XEAD Server 1.1";
	public static final String FULL_VERSION  = "V1.R1.M3";
	public static final String PRODUCT_NAME = "XEAD[zi:d] Server";
	public static final String COPYRIGHT = "Copyright 2012 DBC,Ltd.";
	public static final String URL_DBC = "http://homepage2.nifty.com/dbc/";
	private static final String JNDI_PREFIX = "java:comp/env/jdbc/";
	private ArrayList<String> databaseIDList = new ArrayList<String>();
	private ArrayList<DataSource> dataSourceList = new ArrayList<DataSource>();
	private HashMap<String, Connection> manualCommitConnectionMap = new HashMap<String, Connection>();

    //////////////////
	// Initializing //
	//////////////////
	public void init() throws ServletException {
		String datasource, subDBIDs, id;

		super.init();

		try {
			///////////////////////////
			// Setup Initial Context //
			///////////////////////////
			Context initialContext = new InitialContext();

			/////////////////////////////////////////
			// Get parameter value of "DataSource" //
			/////////////////////////////////////////
			datasource = getServletConfig().getInitParameter("DataSource");
			
			/////////////////////////////////////////////////
			// Get dataSource for Main-DB(its id is blank) //
			/////////////////////////////////////////////////
			databaseIDList.add("");
			dataSourceList.add((DataSource)initialContext.lookup(JNDI_PREFIX + datasource));

			//////////////////////////////////////////////
			// Get dataSource for Sub-DBs with their id //
			//////////////////////////////////////////////
			subDBIDs = getServletConfig().getInitParameter("SubDB");
			if (subDBIDs != null && !subDBIDs.equals("")) {
				StringTokenizer tokenizer = new StringTokenizer(subDBIDs, ",");
				while (tokenizer.hasMoreTokens()) {
					id = tokenizer.nextToken();
					databaseIDList.add(id);
					dataSourceList.add((DataSource)initialContext.lookup(JNDI_PREFIX + datasource + id));
				}
			}

			///////////////////////////
			// Close Initial Context //
			///////////////////////////
			initialContext.close();

		} catch (NamingException e) {
			e.printStackTrace();
		}
	}
	
	///////////////////////////////////////////////////////
	// Processing "Get" requests to return the test page //
	///////////////////////////////////////////////////////
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {
			Connection con;
			String sessionID = "";
			res.setContentType("text/html; charset=UTF-8");
			StringBuffer sb = new StringBuffer();
			sb.append("<html>");
			sb.append("<head>");
			sb.append("<title>XEAD Server Status</title>");
			sb.append("</head>");
			sb.append("<body>");
			sb.append("<h3>XEAD Server Database Connections</h3>");
			sb.append("<table border='2' cellpadding='2'>");
			sb.append("<tr style='background:#ccccff'><th>DB ID</th><th>Session ID</th><th>Connection</th></tr>");
			for (int i = 0; i < databaseIDList.size(); i++) {
				if (databaseIDList.get(i).equals("")) {
					try {
						con = dataSourceList.get(i).getConnection();
						if (con == null) {
							sb.append("<tr><td>(Main)</td><td>*BLANK</td><td>INVALID</td></tr>");
						} else {
							//if (con.isValid(0)) {
							sb.append("<tr><td>(Main)</td><td>*BLANK</td><td>VALID</td></tr>");
						}
					} catch (Exception e) {
						sb.append("<tr><td>(Main)</td><td>*BLANK</td><td>INVALID</td></tr>");
					}
					for (Iterator<String> it = manualCommitConnectionMap.keySet().iterator(); it.hasNext(); ) {
						sessionID = it.next();
						con = manualCommitConnectionMap.get(sessionID);
						if (con == null) {
							sb.append("<tr><td>Main</td><td>" + sessionID + "</td><td>INVALID</td></tr>");
						} else {
							sb.append("<tr><td>Main</td><td>" + sessionID + "</td><td>VALID</td></tr>");
						}
					}
				} else {
					try {
						con = dataSourceList.get(i).getConnection();
						if (con == null) {
							sb.append("<tr><td>" + databaseIDList.get(i) + "</td><td>*BLANK</td><td>INVALID</td></tr>");
						} else {
							sb.append("<tr><td>" + databaseIDList.get(i) + "</td><td>*BLANK</td><td>VALID</td></tr>");
						}
					} catch (Exception e) {
						sb.append("<tr><td>" + databaseIDList.get(i) + "</td><td>*BLANK</td><td>INVALID</td></tr>");
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
		//
		if (req.getParameter("SESSION") != null) {
			parmSessionID = req.getParameter("SESSION");
		}
		if (req.getParameter("METHOD") != null) {
			parmMethod = req.getParameter("METHOD");
		}
		if (req.getParameter("DB") != null) {
			parmDBID = req.getParameter("DB");
		}
		//
		res.setContentType("text/html; charset=UTF-8");
		//
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
//			if (parmMethod.toUpperCase().equals("STATUS")) {
//				StringBuffer buf = new StringBuffer();
//				buf.append("Session ID of Manual-Commit connection:\n");
//				buf.append(manualCommitConnectionMap.keySet());
//				PrintWriter out = res.getWriter();
//				out.println(buf.toString());
//				out.close();
//			}
			if (parmMethod.toUpperCase().equals("IP")) {
				PrintWriter out = res.getWriter();
				out.print(req.getRemoteAddr());
				out.close();
			}
		} catch (Exception e) {	
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
	
	private Relation getResultOfSelect(String sessionID, String method, String dbID) throws Exception {
		Connection conn = null;
		Statement stmt = null;
		ResultSet rset = null;
		Relation relation = null;
		//
		if (method.toUpperCase().startsWith("SELECT ")) {
			try {
				conn = getConnectionForSession(sessionID, dbID);
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
		//
		return relation;
	}
	
	private int getCountOfRecordProcessed(String sessionID, String method, String dbID) throws Exception {
		Connection conn = null;
		Statement stmt = null;
		ResultSet rset = null;
		int count = -1;
		//
		if (method.toUpperCase().startsWith("INSERT ")
			|| method.toUpperCase().startsWith("UPDATE ")
			|| method.toUpperCase().startsWith("DELETE ")) {
			try {
				conn = getConnectionForSession(sessionID, dbID);
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
					conn = getConnectionForSession(sessionID, dbID);
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
		//
		return count;
	}
	
	private Connection getConnectionForSession(String sessionID, String dbID) throws Exception {
		Connection connection = null;
		//
		DataSource dataSource = dataSourceList.get(databaseIDList.indexOf(dbID));
		if (dbID.equals("")) {
			if (sessionID.equals("")) {
				connection = dataSource.getConnection();
				connection.setAutoCommit(true);
			} else {
				if (manualCommitConnectionMap.containsKey(sessionID)) {
					connection = manualCommitConnectionMap.get(sessionID);
				} else {
					connection = dataSource.getConnection();
					connection.setAutoCommit(false);
					manualCommitConnectionMap.put(sessionID, connection);
				}
			}
		} else {
			connection = dataSource.getConnection();
			connection.setReadOnly(true);
		}
		//
		return connection;
	}
	
	private void callProcedure(String sessionID, String method, String dbID) throws Exception {
		Connection conn = null;
		Statement stmt = null;
		//
		try {
			conn = getConnectionForSession(sessionID, dbID);
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
	
	private void closeConnectionForSession(String sessionID, String method) throws Exception {
		if (!sessionID.equals("")) {
			if (method.toUpperCase().equals("COMMIT") || method.toUpperCase().equals("ROLLBACK")) {
				if (manualCommitConnectionMap.containsKey(sessionID)) {
					Connection connection = manualCommitConnectionMap.get(sessionID);
					if (method.toUpperCase().equals("COMMIT")) {
						connection.commit();
					}
					if (method.toUpperCase().equals("ROLLBACK")) {
						connection.rollback();
					}
					manualCommitConnectionMap.remove(sessionID);
					connection.close();
				}
			}
		}
	}
}
