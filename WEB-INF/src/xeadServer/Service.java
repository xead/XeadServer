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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import xeadDriver.Session;

public class Service extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/////////////////////////////
	// APPLICATION INFORMATION //
	/////////////////////////////
	public static final String APPLICATION_NAME  = "XEAD Server/ WEB-Service Controler";
	public static final String VERSION  = "1.0.0";

	/////////////////////
	// GLOBAL VARIANTS //
	/////////////////////
	private String fileName = "";
	private String userID = "";
	private String password = "";
	private ArrayList<String> sessionTimeList = new ArrayList<String>();
	private ArrayList<String> sessionIDList = new ArrayList<String>();
	private ArrayList<String> sessionFunctionIDList = new ArrayList<String>();
	private ArrayList<String> sessionResultList = new ArrayList<String>();
	
	public void init() throws ServletException {
		super.init();
		fileName = getServletConfig().getInitParameter("SystemDefinition");
		userID = getServletConfig().getInitParameter("UserID");
		password = getServletConfig().getInitParameter("Password");
	}
	
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {
			String function = "";
			if (req.getParameter("function") != null) {
				function = req.getParameter("function");
			}

			if (function.equals("sessionStatus")) {
				res.setContentType("text/html; charset=UTF-8");
				StringBuffer sb = new StringBuffer();
				sb.append("<html>");
				sb.append("<head>");
				sb.append("<title>XEAD Server/ WEB-Service Controler</title>");
				sb.append("</head>");
				sb.append("<body>");
				sb.append("<h3>WEB-Service Session Log</h3>");
				sb.append("<table border='2' cellpadding='2'>");
				sb.append("<tr style='background:#ccccff'><th>Date and Time</th><th>Session ID</th><th>Function ID</th><th>Result</th></tr>");
				if (sessionIDList.size() == 0) {
					sb.append("<tr><td colspan='4'>(No requests received yet although the service controler is ready.)</td></tr>");
				} else {
					for (int i = 0; i < sessionIDList.size(); i++) {
						sb.append("<tr><td>" + sessionTimeList.get(i) + "</td><td>" + sessionIDList.get(i) + "</td><td>" + sessionFunctionIDList.get(i) + "</td><td>" + sessionResultList.get(i) + "</td></tr>");
					}
				}
				sb.append("</table>");
				sb.append("</body>");
				sb.append("</html>");
				PrintWriter out = res.getWriter();
				out.print(sb.toString());
				out.close();
			}
		} catch (Exception e) {	
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			/////////////////////////////////////////////
			// Setup and start session for WEB-Service //
			/////////////////////////////////////////////
			Session session = new Session(fileName, userID, password, VERSION);

			/////////////////////////////
			// Get function definition //
			/////////////////////////////
			String functionID = "";
			if (request.getParameter("function") != null) {
				functionID = request.getParameter("function");
			}
			org.w3c.dom.Element element;
			org.w3c.dom.Element functionElement = null;
			NodeList functionElementList = session.getFunctionList();
			for (int i = 0; i < functionElementList.getLength(); i++) {
				element = (org.w3c.dom.Element)functionElementList.item(i);
				if (element.getAttribute("ID").equals(functionID)
						&& element.getAttribute("Type").equals("XF000")) {
					functionElement = element;
					break;
				}
			}
			if (functionElement == null) {

				//////////////////////////////////////////////
				// Close Session without executing function //
				//////////////////////////////////////////////
				session.closeSession(true);

				///////////////////////////////////
				// Write Session Log for Browser //
				///////////////////////////////////
				Calendar cal = Calendar.getInstance();
				sessionTimeList.add(cal.getTime().toString());
				sessionIDList.add(session.getSessionID());
				sessionFunctionIDList.add(functionID);
				sessionResultList.add("Invalid function requested.");

				///////////////////////////////
				// Setup and return response //
				///////////////////////////////
				response.setContentType("text/html; charset=UTF-8");
				PrintWriter out = response.getWriter();
				out.print("Invalid function ID requested : '" + functionID + "'");
				out.close();

			} else {

				/////////////////////////////////////////////////////////
				// Construct ScriptLauncher for the function requested //
				/////////////////////////////////////////////////////////
				ScriptLauncher launcher = new ScriptLauncher(functionElement, session);

				/////////////////////////////////////////////////
				// Setup parameters and execute ScriptLauncher //
				/////////////////////////////////////////////////
				HashMap<String, Object> paramsMap = new HashMap<String, Object>();
				Enumeration<String> names = request.getParameterNames();
				while (names.hasMoreElements()){
					String name = names.nextElement();
					if (!name.equals("function")) {
						String[] values = request.getParameterValues(name);
						if (values.length == 1) {
							paramsMap.put(name.toUpperCase(), values[0]);
						} else {
							if (values.length >= 2) {
								paramsMap.put(name.toUpperCase(), values);
							}
						}
					}
				}
				HashMap<String, Object> returnMap = launcher.execute(paramsMap);
				session.closeSession(true);

				///////////////////////////////////
				// Write Session Log for Browser //
				///////////////////////////////////
				Calendar cal = Calendar.getInstance();
				sessionTimeList.add(cal.getTime().toString());
				sessionIDList.add(session.getSessionID());
				sessionFunctionIDList.add(functionID);
				sessionResultList.add(returnMap.get("RETURN_CODE").toString());

				/////////////////////////////////
				// Setup response to be return //
				/////////////////////////////////
				Object result = returnMap.get("RESULT");
				if (result instanceof JSONObject) {
					response.setContentType("application/json");
					JSONObject jsonObj = (JSONObject)result;
					String resultString = jsonObj.toString();
					PrintWriter writer = response.getWriter();
					writer.print(resultString);
					writer.close();
				}
				if (result instanceof Document) {
					response.setContentType("text/xml");
					Document document = (Document)result;
					OutputFormat outputFormat = new OutputFormat(document);
					outputFormat.setEncoding("UTF-8");
					PrintWriter writer = response.getWriter();
					XMLSerializer xmlSerializer = new XMLSerializer(writer, outputFormat);
					xmlSerializer.serialize(document.getDocumentElement());
					writer.close();
				}
				if (result instanceof String) {
					response.setContentType("text/plain");
					String resultString = (String)result;
					PrintWriter writer = response.getWriter();
					writer.print(resultString);
					writer.close();
				}
			}
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}	

