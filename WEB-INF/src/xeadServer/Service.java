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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class Service extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/////////////////////////////
	// APPLICATION INFORMATION //
	/////////////////////////////
	public static final String APPLICATION_NAME  = "X-TEA Server/ WEB-Service Controler";
	public static final String VERSION  = "1.0.3";

	/////////////////////
	// GLOBAL VARIANTS //
	/////////////////////
	private String fileName = "";
	private String fixedUserID = "";
	private String fixedPassword = "";
	private String dateTimeServeletStarted = "";
	private String dateTimeFirstSessionStarted = "";
	private String dateTimeLastSessionStarted = "";
	private int countOfRequestsRejected = 0;
	private int countOfSessionsAborted = 0;
	private int countOfSessionsSucceeded = 0;

	public void init() throws ServletException {
		super.init();
		fileName = getServletConfig().getInitParameter("SystemDefinition");
		fixedUserID = getServletConfig().getInitParameter("User");
		if (fixedUserID == null) {
			fixedUserID = "";
		}
		fixedPassword = getServletConfig().getInitParameter("Password");
		if (fixedPassword == null) {
			fixedPassword = "";
		}
		Calendar cal = Calendar.getInstance();
		dateTimeServeletStarted = cal.getTime().toString();
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {
			res.setContentType("text/html; charset=UTF-8");
			StringBuffer sb = new StringBuffer();
			sb.append("<html>");
			sb.append("<head>");
			sb.append("<title>X-TEA Server/ Web Service Controler</title>");
			sb.append("</head>");
			sb.append("<body>");
			sb.append("<h3>X-TEA Server/ Web Service Controler(");
			sb.append(VERSION);
			sb.append(") Session Status</h3>");
			sb.append("<table border='2' cellpadding='2'>");
			sb.append("<tr><td style='background:#ccccff'>Servelet Started at</td><td>" + dateTimeServeletStarted + "</td></tr>");
			sb.append("<tr><td style='background:#ccccff'>First Session Started at</td><td>" + dateTimeFirstSessionStarted + "</td></tr>");
			sb.append("<tr><td style='background:#ccccff'>Last Session Started at</td><td>" + dateTimeLastSessionStarted + "</td></tr>");
			sb.append("<tr><td style='background:#ccccff'>Count of Requests Rejected</td><td>" + countOfRequestsRejected + "</td></tr>");
			sb.append("<tr><td style='background:#ccccff'>Count of Sessions Succeeded</td><td>" + countOfSessionsSucceeded + "</td></tr>");
			sb.append("<tr><td style='background:#ccccff'>Count of Sessions Aborted</td><td>" + countOfSessionsAborted + "</td></tr>");
			sb.append("</table>");
			sb.append("*Refer to session log for details.");
			sb.append("</body>");
			sb.append("</html>");
			PrintWriter out = res.getWriter();
			out.print(sb.toString());
			out.close();
		} catch (Exception e) {	
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			/////////////////////////////
			// Get userID and password //
			/////////////////////////////
			String userID = request.getParameter("USER");
			String password = request.getParameter("PASSWORD");
			if (userID == null || userID.equals("")) {
				userID = fixedUserID;
			}
			if (password == null || password.equals("")) {
				password = fixedPassword;
			}

			/////////////////////////////////////////////////
			// Reject login if userID or password is blank //
			/////////////////////////////////////////////////
			if (userID.equals("") || password.equals("")) {
				countOfRequestsRejected++;
				response.setContentType("text/html;charset=UTF-8");
				PrintWriter out = response.getWriter();
				out.print("User and password is not specified in Web.xml and http request.");
				out.close();

			} else {

				///////////////////////////////////////////////////
				// Reject login if FUNCTION parameter is missing //
				///////////////////////////////////////////////////
				if (request.getParameter("FUNCTION") == null) {
					countOfRequestsRejected++;
					response.setContentType("text/html;charset=UTF-8");
					PrintWriter out = response.getWriter();
					out.print("Parameter FUNCTION is missing int the request.");
					out.close();

				} else {

					/////////////////////////////////////////////
					// Setup and start session for WEB-Service //
					/////////////////////////////////////////////
					Session session = new Session(fileName, userID, password, VERSION);

					/////////////////////////////////////////////////////////
					// Construct ScriptLauncher for the function requested //
					/////////////////////////////////////////////////////////
					ScriptLauncher launcher = new ScriptLauncher(request.getParameter("FUNCTION"), session);

					////////////////////////////////////////////////////
					// Setup parameters for ScriptLauncher to execute //
					////////////////////////////////////////////////////
					HashMap<String, Object> paramsMap = new HashMap<String, Object>();
					Enumeration<String> names = request.getParameterNames();
					while (names.hasMoreElements()){
						String name = names.nextElement();
						if (!name.equals("FUNCTION")) {
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

					///////////////////////////////////////////
					// Close Session according to the result //
					///////////////////////////////////////////
					session.closeSession(true);
					Calendar cal = Calendar.getInstance();
					if (dateTimeFirstSessionStarted.equals("")) {
						dateTimeFirstSessionStarted = cal.getTime().toString();
					}
					dateTimeLastSessionStarted = cal.getTime().toString();
					if (returnMap.get("RETURN_CODE").toString().equals("99")) {
						countOfSessionsAborted++;
					} else {
						countOfSessionsSucceeded++;
					}

					///////////////////////////////////
					// Setup response to be returned //
					///////////////////////////////////
					Object result = returnMap.get("RESULT");
					if (result instanceof JSONObject) {
						response.setContentType("application/json;charset=UTF-8");
						JSONObject jsonObj = (JSONObject)result;
						String resultString = jsonObj.toString();
						PrintWriter writer = response.getWriter();
						writer.print(resultString);
						writer.close();
					}
					if (result instanceof Document) {
						response.setContentType("text/xml;charset=UTF-8");
						Document document = (Document)result;
						OutputFormat outputFormat = new OutputFormat(document);
						outputFormat.setEncoding("UTF-8");
						PrintWriter writer = response.getWriter();
						XMLSerializer xmlSerializer = new XMLSerializer(writer, outputFormat);
						xmlSerializer.serialize(document.getDocumentElement());
						writer.close();
					}
					if (result instanceof String) {
						response.setContentType(""
								+ "");
						String resultString = (String)result;
						PrintWriter writer = response.getWriter();
						writer.print(resultString);
						writer.close();
					}
				}
			}

		} catch (Exception e) {
			countOfRequestsRejected++;
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}	

