package xeadServer;

/*
 * Copyright (c) 2015 WATANABE kozo <qyf05466@nifty.com>,
 * All rights reserved.
 *
 * This file is part of XEAD Driver.
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

import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.security.*;
import java.net.URI;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;

import org.apache.xerces.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import xeadDriver.XFUtility;
import xeadDriver.XFHttpRequest;
import xeadDriver.XFTextFileOperator;

public class Session extends Object {
	private String systemName = "";
	private String systemVersion = "";
	private String sessionID = "";
	private String sessionStatus = "";
	private String processorVersion = "";
	private boolean noErrorsOccured = true;
	private String databaseName = "";
	private String databaseUser = "";
	private String databasePassword = "";
	private String appServerName = "";
	private String fileName = "";
	private String userID = "";
	private String userName = "";
	private String userEmployeeNo = "";
	private String userEmailAddress = "";
	private String digestAlgorithmForUser = "MD5";
	private int countOfExpandForUser = 1;
	private boolean isValueSaltedForUser = false;
	private String userTable = "";
	private String variantsTable = "";
	private String userVariantsTable = "";
	private String sessionTable = "";
	private String sessionDetailTable = "";
	private String numberingTable = "";
	private String calendarTable = "";
	public String taxTable = "";
	private String exchangeRateAnnualTable = "";
	private String exchangeRateMonthlyTable = "";
	private String imageFileFolder = "";
	private File outputFolder = null;
	private String dateFormat = "";
	public String systemFont = "";
	private int sqProgram = 0;
	private String currentFolder = "";
	private String loginScript = "";
	private String scriptFunctions = "";
	private String smtpHost = "";
	private String smtpPort = "";
	private String smtpUser = "";
	private String smtpPassword = "";
	private String smtpAdminEmail = "";

	private Connection connectionManualCommit = null;
	private Connection connectionAutoCommit = null;
	private ArrayList<String> subDBIDList = new ArrayList<String>();
	private ArrayList<String> subDBNameList = new ArrayList<String>();
	private ArrayList<String> subDBUserList = new ArrayList<String>();
	private ArrayList<String> subDBPasswordList = new ArrayList<String>();
	private ArrayList<Connection> subDBConnectionList = new ArrayList<Connection>();

	private Calendar calendar = GregorianCalendar.getInstance();
	private org.w3c.dom.Document domDocument;
//	private DigestAdapter digestAdapter = null;
	private NodeList functionList = null;
	private NodeList tableList = null;
	private ArrayList<String> offDateList = new ArrayList<String>();
	private ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
	private Bindings globalScriptBindings = null;
	private ScriptEngine scriptEngine = null;
	private DOMParser domParser = new DOMParser();
	private org.w3c.dom.Document responseDoc = null;
	private HttpGet httpGet = new HttpGet();
	private static final String ZIP_URL = "http://zip.cgis.biz/xml/zip.php?";
	private HashMap<String, String> attributeMap = new HashMap<String, String>();

	public Session(String fileName, String user, String password, String xeadServerVersion) throws Exception {
		if (parseSystemDefinition(fileName)) {
			if (setupSessionVariants()) {
				if (isAuthenticatedUser(user, password)) {
					processorVersion = "S" + xeadServerVersion;
					writeLogAndStartSession();
				} else {
					throw new Exception("User or password is invalid.");
				}
			} else {
				throw new Exception("System definition file is invalid to setup session.");
			}
		} else {
			throw new Exception("Failed to parse the system definition file.");
		}
	}

	boolean isAuthenticatedUser(String user, String password) throws Exception {
		StringBuffer statementBuf;
		XFTableOperator operator;
		boolean validated = false;

		if (!user.equals("") && !password.equals("")) {
			statementBuf = new StringBuffer();
			statementBuf.append("select * from ");
			statementBuf.append(getTableNameOfUserVariants());
			statementBuf.append(" where IDUSERKUBUN = 'KBCALENDAR'");
			operator = new XFTableOperator(this, null, statementBuf.toString(), true);
			if (!operator.next()) {
				throw new Exception(XFUtility.RESOURCE.getString("LogInError5"));
			} else {
				if (getSystemVariantString("LOGIN_PERMITTED").equals("")) {
					throw new Exception(XFUtility.RESOURCE.getString("LogInError4"));
				} else {
					if (getSystemVariantString("LOGIN_PERMITTED").equals("F")) {
						throw new Exception(XFUtility.RESOURCE.getString("LogInError3"));
					} else {

						/////////////////////////////////////////////////////
						// Setup select-statement to check login authority //
						/////////////////////////////////////////////////////
						//String passwordDigested = getDigestAdapter().digest(password);
						String passwordDigested = getDigestedValueForUser(user, password);
						statementBuf = new StringBuffer();
						statementBuf.append("select * from ");
						statementBuf.append(getTableNameOfUser());
						statementBuf.append(" where IDUSER = '") ;
						statementBuf.append(user) ;
						statementBuf.append("' and TXPASSWORD = '") ;
						statementBuf.append(passwordDigested);
						statementBuf.append("'") ;

						///////////////////////////////////////////////
						// Execute select-statement retrying 3 times //
						///////////////////////////////////////////////
						int retryCount = 0;
						while (retryCount < 3) {
							try {
								retryCount++;
								operator = new XFTableOperator(this, null, statementBuf.toString(), true);
								if (operator.next()) {
									Date resultDateFrom = null;
									Date resultDateThru = null;
									Date today = new Date();
									resultDateFrom = (java.util.Date)operator.getValueOf("DTVALID");
									resultDateThru = (java.util.Date)operator.getValueOf("DTEXPIRE");
									if (today.after(resultDateFrom)) {
										if (resultDateThru == null || today.before(resultDateThru)) {
											userID = user;
											userName = operator.getValueOf("TXNAME").toString().trim();
											userEmployeeNo = operator.getValueOf("NREMPLOYEE").toString().trim();
											userEmailAddress = operator.getValueOf("TXEMAIL").toString().trim();
											validated = true;
										}
									}
								}
								retryCount = 3;
							} catch(Exception e) {
								if (retryCount < 3) {
									Thread.sleep(1000);
								} else {
									throw e;
								}
							}
						}
					}
				}
			}
		}
		return validated;
	}

	////////////////////////////////////////////////////////////////
	// Parse XML formatted data into DOM with file name requested //
	////////////////////////////////////////////////////////////////
	private boolean parseSystemDefinition(String fileName) throws Exception {
		if (fileName.startsWith("http:")
				|| fileName.startsWith("https:")
				|| fileName.startsWith("file:")) {
				URL url = new URL(fileName);
				URLConnection connection = url.openConnection();
				InputStream inputStream = connection.getInputStream();
				domParser.parse(new InputSource(inputStream));
				domDocument = domParser.getDocument();
				return true;
		} else {
			File xeafFile = new File(fileName);
			if (xeafFile.exists()) {
				currentFolder = xeafFile.getParent();
				domParser.parse(new InputSource(new FileInputStream(fileName)));
				domDocument = domParser.getDocument();
				return true;
			} else {
				throw new Exception(XFUtility.RESOURCE.getString("SessionError21") + fileName + XFUtility.RESOURCE.getString("SessionError22"));
			}
		}
	}

	///////////////////////////////////////////////////////////
	// Setup session variants according to system definition //
	///////////////////////////////////////////////////////////
	private boolean setupSessionVariants() throws Exception {
		NodeList nodeList = domDocument.getElementsByTagName("System");
		org.w3c.dom.Element element = (org.w3c.dom.Element)nodeList.item(0);
		systemName = element.getAttribute("Name");
		systemVersion = element.getAttribute("Version");
		dateFormat = element.getAttribute("DateFormat");
		systemFont = element.getAttribute("SystemFont");
		if (systemFont.equals("")) {
			systemFont = "SanSerif";
		}
		calendar.setLenient(false);

		/////////////////
		// Hash Format //
		/////////////////
		String hashFormat = element.getAttribute("HashFormat");
		if (!hashFormat.equals("")) {
			try {
				StringTokenizer workTokenizer = new StringTokenizer(hashFormat, ";" );
				digestAlgorithmForUser = workTokenizer.nextToken(); //Default:MD5
				if (!digestAlgorithmForUser.equals("MD5")) {
					new DigestAdapter(digestAlgorithmForUser);
				}
				if (workTokenizer.hasMoreTokens()) {
					countOfExpandForUser = Integer.parseInt(workTokenizer.nextToken()); //Default:1
				}
				if (workTokenizer.hasMoreTokens()) {
					isValueSaltedForUser = Boolean.parseBoolean(workTokenizer.nextToken()); //Default:false
				}
			} catch (Exception e) {}
		}
		
		////////////////////
		// System Folders //
		////////////////////
		String wrkStr = element.getAttribute("ImageFileFolder"); 
		if (wrkStr.equals("")) {
			imageFileFolder = currentFolder + File.separator;
		} else {
			if (wrkStr.contains("<CURRENT>")) {
				imageFileFolder = wrkStr.replace("<CURRENT>", currentFolder) + File.separator;
			} else {
				imageFileFolder = wrkStr + File.separator;
			}
		}
		wrkStr = element.getAttribute("OutputFolder"); 
		if (wrkStr.equals("")) {
			outputFolder = null;
		} else {
			if (wrkStr.contains("<CURRENT>")) {
				wrkStr = wrkStr.replace("<CURRENT>", currentFolder);
			}
			outputFolder = new File(wrkStr);
			if (!outputFolder.exists()) {
				outputFolder = null;
			}
		}

		///////////////////////////
		// System control tables //
		///////////////////////////
		userTable = element.getAttribute("UserTable");
		variantsTable = element.getAttribute("VariantsTable");
		userVariantsTable = element.getAttribute("UserVariantsTable");
		numberingTable = element.getAttribute("NumberingTable");
		sessionTable = element.getAttribute("SessionTable");
		sessionDetailTable = element.getAttribute("SessionDetailTable");
		taxTable = element.getAttribute("TaxTable");
		calendarTable = element.getAttribute("CalendarTable");
		exchangeRateAnnualTable = element.getAttribute("ExchangeRateAnnualTable");
		exchangeRateMonthlyTable = element.getAttribute("ExchangeRateMonthlyTable");

		////////////////////
		// System Scripts //
		////////////////////
		loginScript = XFUtility.substringLinesWithTokenOfEOL(element.getAttribute("LoginScript"), "\n");
		scriptFunctions = XFUtility.substringLinesWithTokenOfEOL(element.getAttribute("ScriptFunctions"), "\n");

		////////////////////////////////
		// Function List / Table List //
		////////////////////////////////
		functionList = domDocument.getElementsByTagName("Function");
		tableList = domDocument.getElementsByTagName("Table");

		//////////////////////////
		// Main / Sub Databases //
		//////////////////////////
		databaseName = element.getAttribute("DatabaseName");
		if (databaseName.contains("<CURRENT>")) {
			databaseName = databaseName.replace("<CURRENT>", currentFolder);
		}
		databaseUser = element.getAttribute("DatabaseUser");
		databasePassword = element.getAttribute("DatabasePassword");
		org.w3c.dom.Element subDBElement;
		NodeList subDBList = domDocument.getElementsByTagName("SubDB");
		for (int i = 0; i < subDBList.getLength(); i++) {
			subDBElement = (org.w3c.dom.Element)subDBList.item(i);
			subDBIDList.add(subDBElement.getAttribute("ID"));
			subDBUserList.add(subDBElement.getAttribute("User"));
			subDBPasswordList.add(subDBElement.getAttribute("Password"));
			wrkStr = subDBElement.getAttribute("Name");
			if (wrkStr.contains("<CURRENT>")) {
				wrkStr = wrkStr.replace("<CURRENT>", currentFolder);
			}
			subDBNameList.add(wrkStr);
		}


		///////////////////
		// DB-Method URL //
		///////////////////
		if (!element.getAttribute("AppServerName").equals("")) {
			appServerName = element.getAttribute("AppServerName");
		}
		if (appServerName.equals("")) {
			boolean isOkay = setupConnectionToDatabase(true);
			if (!isOkay) {
				return false;
			}
		}

		/////////////////////////
		// SMTP Configurations //
		/////////////////////////
		smtpHost = element.getAttribute("SmtpHost");
		smtpPort = element.getAttribute("SmtpPort");
		smtpUser = element.getAttribute("SmtpUser");
		smtpPassword = element.getAttribute("SmtpPassword");
		smtpAdminEmail = element.getAttribute("SmtpAdminEmail");

//		/////////////////////////////
//		// Setup MD5-Hash Digester //
//		/////////////////////////////
//		digestAdapter = new DigestAdapter("MD5");

		////////////////////////////////////////
		// Return if variants setup succeeded //
		////////////////////////////////////////
		return true;
	}

	public boolean setupConnectionToDatabase(boolean isToStartSession) throws Exception {
		///////////////////////////////////////////////////////////////////////////////
		// Setup committing connections.                                             //
		// Note that default isolation level of JavaDB is TRANSACTION_READ_COMMITTED //
		///////////////////////////////////////////////////////////////////////////////
		XFUtility.loadDriverClass(databaseName);
		connectionManualCommit = DriverManager.getConnection(databaseName, databaseUser, databasePassword);
		connectionManualCommit.setAutoCommit(false);
		connectionAutoCommit = DriverManager.getConnection(databaseName, databaseUser, databasePassword);
		connectionAutoCommit.setAutoCommit(true);

		////////////////////////////////////////////////////////
		// Setup read-only connections for Sub-DB definitions //
		////////////////////////////////////////////////////////
		Connection subDBConnection = null;
		subDBConnectionList.clear();
		for (int i = 0; i < subDBIDList.size(); i++) {
			XFUtility.loadDriverClass(subDBNameList.get(i));
			subDBConnection = DriverManager.getConnection(subDBNameList.get(i), subDBUserList.get(i), subDBPasswordList.get(i));
			subDBConnection.setReadOnly(true);
			subDBConnectionList.add(subDBConnection);
		}
		return true;
	}

	
	private void writeLogAndStartSession() throws ScriptException, Exception {
		/////////////////////////////////
		// Setup session no and status //
		/////////////////////////////////
		sessionID = this.getNextNumber("NRSESSION");
		sessionStatus = "ACT";

		//////////////////////////////////////////
		// insert a new record to session table //
		//////////////////////////////////////////
		String sql = "insert into " + sessionTable
		+ " (NRSESSION, IDUSER, DTLOGIN, TXIPADDRESS, VLVERSION, KBSESSIONSTATUS) values ("
		+ "'" + sessionID + "'," + "'" + userID + "'," + "CURRENT_TIMESTAMP,"
		+ "'" + getIpAddress() + "','" + processorVersion + "','" + sessionStatus + "')";
		XFTableOperator operator = new XFTableOperator(this, null, sql, true);
		operator.execute();

		//////////////////////////////////////
		// setup off date list for calendar //
		//////////////////////////////////////
		operator = new XFTableOperator(this, null, "select * from " + calendarTable, true);
		while (operator.next()) {
			offDateList.add(operator.getValueOf("KBCALENDAR").toString() + ";" +operator.getValueOf("DTOFF").toString());
		}

		////////////////////////////////////////////////////
		// setup global bindings and execute login-script //
		////////////////////////////////////////////////////
		globalScriptBindings = scriptEngineManager.getBindings();
		globalScriptBindings.put("session", this);
		scriptEngine = scriptEngineManager.getEngineByName("js");
		if (!loginScript.equals("")) {
			scriptEngine.eval(loginScript);
		}
	}

	private String getIpAddress() {
		String value = "N/A";
		HttpPost httpPost = null;
		HttpClient httpClient = null;

		try {
			InetAddress ip = InetAddress.getLocalHost();
			value = ip.getHostAddress();

			if (!getAppServerName().equals("")) {
				httpPost = new HttpPost(getAppServerName());
				List<NameValuePair> objValuePairs = new ArrayList<NameValuePair>(1);
				objValuePairs.add(new BasicNameValuePair("METHOD", "IP"));
				httpPost.setEntity(new UrlEncodedFormEntity(objValuePairs, "UTF-8"));  

				try {
					httpClient = new DefaultHttpClient();
					HttpResponse response = httpClient.execute(httpPost);
					HttpEntity responseEntity = response.getEntity();
					if (responseEntity != null) {
						value = EntityUtils.toString(responseEntity) + " - " + ip.getHostAddress();
					}
				} catch(Exception e) {
				} finally {
					if (httpClient != null) {
						httpClient.getConnectionManager().shutdown();
					}
				}
			}
		} catch(Exception e) {
		} finally {
			if (httpPost != null) {
				httpPost.abort();
			}
		}

		org.w3c.dom.Element ipAddressField = getFieldElement(sessionTable, "TXIPADDRESS");
		int ipAddressSize = Integer.parseInt(ipAddressField.getAttribute("Size"));
		if (value.length() > ipAddressSize) {
			value = value.substring(0, ipAddressSize);
		}

		return value;
	}

	public ScriptEngineManager getScriptEngineManager() {
		return scriptEngineManager;
	}

	public String getTimeStamp() {
		return XFUtility.getUserExpressionOfUtilDate(null, "", true);
	}

	public String getToday() {
		return getToday("");
	}

	public String getToday(String dateFormat) {
		return XFUtility.getUserExpressionOfUtilDate(null, dateFormat, false);
	}

	public String getTodayTime(String dateFormat) {
		return XFUtility.getUserExpressionOfUtilDate(null, dateFormat, true);
	}

	public String getThisMonth() {
		SimpleDateFormat dfm = new SimpleDateFormat("yyyyMM");
		Calendar cal = Calendar.getInstance();
		return dfm.format(cal.getTime());
	}

	public String getErrorOfAccountDate(String dateValue) {
		String message = "";
		try {
			int yyyyMSeq = getSystemVariantInteger("ALLOWED_FISCAL_MONTH");
			if (yyyyMSeq > 200000 && yyyyMSeq < 210000) {
				String date = dateValue.replace("/", "");
				date = date.replace("-", "");
				if (date.length() >= 8) {
					date = date.substring(0, 8);
					int yyyy = getFYearOfDate(date);
					int mSeq = getMSeqOfDate(dateValue);
					int yyyyMSeqTarget = yyyy * 100 + mSeq;
					if (yyyyMSeqTarget < yyyyMSeq) {
						message = XFUtility.RESOURCE.getString("FunctionError49");
					}
				}
			}
		} catch (NumberFormatException e) {
		} 
		return message;
	}

	public String formatTime(int timeFrom) {
		if (timeFrom > 9999) {
			//JOptionPane.showMessageDialog(null, "Invalid time value.");
			return null;
		} else {
			return formatTime(Integer.toString(timeFrom));
		}
	}

	public String formatTime(String timeFrom) {
		if (timeFrom.contains(":")) {
			return timeFrom;
		} else {
			if (timeFrom.length() > 4) {
				//JOptionPane.showMessageDialog(null, "Invalid time value.");
				return null;
			} else {
				String wrkStr = "00:00";
				if (timeFrom.length() == 1) {
					wrkStr = "00:" + "0"+ timeFrom;
				}
				if (timeFrom.length() == 2) {
					wrkStr = "00:" + timeFrom;
				}
				if (timeFrom.length() == 3) {
					wrkStr = "0" + timeFrom.substring(0, 1) + ":" + timeFrom.substring(1, 3);
				}
				if (timeFrom.length() == 4) {
					wrkStr = timeFrom.substring(0, 2) + ":" + timeFrom.substring(2, 4);
				}
				return wrkStr;
			}
		}
	}

	public String getOffsetTime(int timeFrom, int minutes) {
		if (timeFrom > 9999) {
			//JOptionPane.showMessageDialog(null, "Invalid time format.");
			return null;
		} else {
			String strTimeFrom = Integer.toString(timeFrom);
			if (strTimeFrom.length() == 1) {
				strTimeFrom = "00:" + "0"+ strTimeFrom;
			}
			if (strTimeFrom.length() == 2) {
				strTimeFrom = "00:" + strTimeFrom;
			}
			if (strTimeFrom.length() == 3) {
				strTimeFrom = "0" + strTimeFrom.substring(0, 1) + ":" + strTimeFrom.substring(1, 3);
			}
			if (strTimeFrom.length() == 4) {
				strTimeFrom = strTimeFrom.substring(0, 2) + ":" + strTimeFrom.substring(2, 4);
			}
			return getOffsetTime(strTimeFrom, minutes);
		}
	}

	private String getOffsetTime(String hhmmFrom, int minutes) {
		if (!hhmmFrom.contains(":")) {
			if (hhmmFrom.length() == 1) {
				hhmmFrom = "00:" + "0"+ hhmmFrom;
			}
			if (hhmmFrom.length() == 2) {
				hhmmFrom = "00:" + hhmmFrom;
			}
			if (hhmmFrom.length() == 3) {
				hhmmFrom = "0" + hhmmFrom.substring(0, 1) + ":" + hhmmFrom.substring(1, 3);
			}
			if (hhmmFrom.length() == 4) {
				hhmmFrom = hhmmFrom.substring(0, 2) + ":" + hhmmFrom.substring(2, 4);
			}
		}
		double days = 0;
		double hh = Double.parseDouble(hhmmFrom.substring(0,2));
		double mm = Double.parseDouble(hhmmFrom.substring(3,5)) + minutes;

		if (mm >= 60) {
			hh = hh + Math.ceil(mm / 60);
			mm = mm % 60;
		}
		if (mm <= -60) {
			hh = hh + Math.ceil(mm / 60);
			mm = (mm % 60) * -1;
		} else {
			if (mm < 0) {
				hh = hh + - 1;
				mm = mm + 60;
			}
		}

		if (hh >= 24) {
			days = Math.ceil(hh / 24);
			hh = hh % 24;
		}
		if (hh <= -24) {
			days = Math.ceil(hh / 24);
			hh = (hh % 24) * -1;
		} else {
			if (hh < 0) {
				days = days - 1;
				hh = hh + 24;
			}
		}

		String strDays = Double.toString(days).replace(".0", "");
		String strHH = Double.toString(hh).replace(".0", "");
		if (hh < 10) {
			strHH = "0" + strHH;
		}
		String strMM = Double.toString(mm).replace(".0", "");
		if (mm < 10) {
			strMM = "0" + strMM;
		}
		return strDays + ":" + strHH + ":" + strMM;//-days:hh:mm//
	}

	public String getOffsetDateTime(String dateFrom, int timeFrom, int minutes) {
		return getOffsetDateTime(dateFrom, timeFrom, minutes, 0, "00");
	}

	public String getOffsetDateTime(String dateFrom, int timeFrom, int minutes, int countType) {
		return getOffsetDateTime(dateFrom, timeFrom, minutes, countType, "00");
	}
	
	public String getOffsetDateTime(String dateFrom, int timeFrom, int minutes, int countType, String kbCalendar) {
		if (timeFrom > 9999) {
			//JOptionPane.showMessageDialog(null, "Invalid time format.");
			return null;
		} else {
			String strTimeFrom = Integer.toString(timeFrom);
			if (strTimeFrom.length() == 1) {
				strTimeFrom = "00:" + "0"+ strTimeFrom;
			}
			if (strTimeFrom.length() == 2) {
				strTimeFrom = "00:" + strTimeFrom;
			}
			if (strTimeFrom.length() == 3) {
				strTimeFrom = "0" + strTimeFrom.substring(0, 1) + ":" + strTimeFrom.substring(1, 3);
			}
			if (strTimeFrom.length() == 4) {
				strTimeFrom = strTimeFrom.substring(0, 2) + ":" + strTimeFrom.substring(2, 4);
			}
			return getOffsetDateTime(dateFrom, strTimeFrom, minutes, countType, kbCalendar);
		}
	}

	public String getOffsetDateTime(String dateFrom, String timeFrom, int minutes, int countType) {
		return getOffsetDateTime(dateFrom, timeFrom, minutes, countType, "00");
	}

	public String getOffsetDateTime(String dateFrom, String timeFrom, int minutes) {
		return getOffsetDateTime(dateFrom, timeFrom, minutes, 0, "00");
	}

	public String getOffsetDateTime(String dateFrom, String timeFrom, int minutes, int countType, String kbCalendar) {
		if (!timeFrom.contains(":")) {
			if (timeFrom.length() == 1) {
				timeFrom = "00:" + "0"+ timeFrom;
			}
			if (timeFrom.length() == 2) {
				timeFrom = "00:" + timeFrom;
			}
			if (timeFrom.length() == 3) {
				timeFrom = "0" + timeFrom.substring(0, 1) + ":" + timeFrom.substring(1, 3);
			}
			if (timeFrom.length() == 4) {
				timeFrom = timeFrom.substring(0, 2) + ":" + timeFrom.substring(2, 4);
			}
		}
		String dateTime = "";
		String daysHhMm = getOffsetTime(timeFrom, minutes); //timeFrom format is hh:mm//
		StringTokenizer workTokenizer = new StringTokenizer(daysHhMm, ":" );//-days:hh:mm//
		String date;
		String hh;
		String mm;
		try {
			int days = Integer.parseInt(workTokenizer.nextToken());
			date = getOffsetDate(dateFrom, days, countType);
			hh = workTokenizer.nextToken();
			mm = workTokenizer.nextToken();
			dateTime = date + " " + hh + ":" + mm;
		} catch (NumberFormatException e) {
			//JOptionPane.showMessageDialog(null, "Failed to get offset date-time.\n" + e.getMessage());
		}
		return dateTime; //yyyy-mm-dd hh:mm//
	}
	public String getOffsetDate(String dateFrom, int days, int countType) {
		return getOffsetDate(dateFrom, days, countType, "00");
	}

	public String getOffsetDate(String dateFrom, int days, int countType, String kbCalendar) {
		String offsetDate = "";
		Date workDate;
		SimpleDateFormat dfm = new SimpleDateFormat("yyyy-MM-dd");
		//
		dateFrom = dateFrom.replaceAll("-", "").replaceAll("/", "").trim();
		//
		int y = Integer.parseInt(dateFrom.substring(0,4));
		int m = Integer.parseInt(dateFrom.substring(4,6));
		int d = Integer.parseInt(dateFrom.substring(6,8));
		Calendar cal = Calendar.getInstance();
		cal.set(y, m-1, d);
		//
		if (countType == 0) {
			cal.add(Calendar.DATE, days);
		}
		//
		if (countType == 1) {
			if (days >= 0) {
				for (int i = 0; i < days; i++) {
					cal.add(Calendar.DATE, 1);
					workDate = cal.getTime();
					if (offDateList.contains(kbCalendar + ";" + dfm.format(workDate))) {
						days++;
					}
				}
			} else {
				days = days * -1;
				for (int i = 0; i < days; i++) {
					cal.add(Calendar.DATE, -1);
					workDate = cal.getTime();
					if (offDateList.contains(kbCalendar + ";" + dfm.format(workDate))) {
						days++;
					}
				}
			}
		}
		//
		workDate = cal.getTime();
		offsetDate = dfm.format(workDate);
		//
		return offsetDate;
	}

	public String getOffsetYearMonth(String yearMonthFrom, int months) {
		String offsetYearMonth = "";
		try {
			int year = Integer.parseInt(yearMonthFrom.substring(0,4));
			int month = Integer.parseInt(yearMonthFrom.substring(4,6));
			if (months > 0) {
				for (int i = 0; i < months; i++) {
					month++;
					if (month > 12) {
						month = 1;
						year++;
					}
				}
			} else {
				for (int i = 0; i > months; i--) {
					month--;
					if (month < 1) {
						month = 12;
						year--;
					}
				}
			}
			if (month >= 10) {
				offsetYearMonth = Integer.toString(year) + Integer.toString(month);
			} else {
				offsetYearMonth = Integer.toString(year) + "0" + Integer.toString(month);
			}
		} catch (NumberFormatException e) {
		}
		return offsetYearMonth;
	}

	public int getDaysBetweenDates(String strDateFrom, String strDateThru, int countType) {
		return getDaysBetweenDates(strDateFrom, strDateThru, countType, "00");
	}

	public int getDaysBetweenDates(String strDateFrom, String strDateThru, int countType, String kbCalendar) {
		int days = 0;
		int y, m, d;
		Date dateFrom, dateThru;
		SimpleDateFormat dfm = new SimpleDateFormat("yyyy-MM-dd");
		Calendar cal = Calendar.getInstance();
		//
		strDateFrom = strDateFrom.replaceAll("-", "").replaceAll("/", "").trim();
		y = Integer.parseInt(strDateFrom.substring(0,4));
		m = Integer.parseInt(strDateFrom.substring(4,6));
		d = Integer.parseInt(strDateFrom.substring(6,8));
		cal.set(y, m-1, d, 0, 0, 0);
		dateFrom = cal.getTime();
		//
		strDateThru = strDateThru.replaceAll("-", "").replaceAll("/", "").trim();
		y = Integer.parseInt(strDateThru.substring(0,4));
		m = Integer.parseInt(strDateThru.substring(4,6));
		d = Integer.parseInt(strDateThru.substring(6,8));
		cal.set(y, m-1, d, 0, 0, 0);
		dateThru = cal.getTime();
		//
		if (countType == 0) {
			long diff = dateThru.getTime() - dateFrom.getTime();
			days = (int)(diff / 86400000); 
		}
		//
		if (countType == 1) {
			//
			if (dateThru.getTime() == dateFrom.getTime()) {
				days = 0;
			}
			//
			if (dateThru.getTime() > dateFrom.getTime()) {
				y = Integer.parseInt(strDateFrom.substring(0,4));
				m = Integer.parseInt(strDateFrom.substring(4,6));
				d = Integer.parseInt(strDateFrom.substring(6,8));
				cal.set(y, m-1, d, 0, 0, 0);
				long timeThru = dateThru.getTime();
				long timeWork = dateFrom.getTime();
				while (timeThru > timeWork) {
					cal.add(Calendar.DATE, 1);
					if (!offDateList.contains(kbCalendar + ";" + dfm.format(cal.getTime()))) {
						days++;
					}
					timeWork = cal.getTime().getTime();
				}
			}
			//
			if (dateThru.getTime() < dateFrom.getTime()) {
				y = Integer.parseInt(strDateThru.substring(0,4));
				m = Integer.parseInt(strDateThru.substring(4,6));
				d = Integer.parseInt(strDateThru.substring(6,8));
				cal.set(y, m-1, d, 0, 0, 0);
				long timeWork = dateThru.getTime();
				long timeFrom = dateFrom.getTime();
				while (timeFrom > timeWork) {
					cal.add(Calendar.DATE, 1);
					if (!offDateList.contains(kbCalendar + ";" + dfm.format(cal.getTime()))) {
						days++;
					}
					timeWork = cal.getTime().getTime();
				}
				days = days * -1;
			}
		}
		//
		return days;
	}

	public boolean isValidDate(String date) {
		boolean result = true;
		String argDate = date.replaceAll("[^0-9]","").trim();
		try {
			int y = Integer.parseInt(argDate.substring(0,4));
			int m = Integer.parseInt(argDate.substring(4,6));
			int d = Integer.parseInt(argDate.substring(6,8));
			calendar.set(y, m-1, d);
			calendar.getTime();
		} catch (Exception e) {
			result = false;
		}
		return result;
	}

	public boolean isValidDateFormat(String date, String separator) {
		boolean result = false;
		if (isValidDate(date)
				&& date.length() == 10
				&& date.substring(4, 5).equals(separator)
				&& date.substring(7, 8).equals(separator)) {
			result = true;
		}
		return result;
	}

	public boolean isOffDate(String date) {
		return isOffDate(date, "00");
	}

	public boolean isOffDate(String date, String kbCalendar) {
		return offDateList.contains(kbCalendar + ";" + date);
	}

	//public boolean isClientSession() {
	//	return isClientSession;
	//}

	public boolean isValidTime(String time, String format) {
		boolean result = false;
		try {
			if (format.toUpperCase().equals("HH:MM")) {
				if (time.length() == 5) {
					if (time.substring(2, 3).equals(":")) {
						int hour = Integer.parseInt(time.substring(0,2));
						int min = Integer.parseInt(time.substring(3,5));
						if (hour >= 0 && hour <= 24
								&& min >= 0 && min <= 60) {
							result = true;
						}
					}
				}
			}
			if (format.toUpperCase().equals("HH:MM:SS")) {
				if (time.length() >= 7 && time.length() <= 12) {
					if (time.substring(2, 3).equals(":")
							&& time.substring(5, 6).equals(":")) {
						int hour = Integer.parseInt(time.substring(0,2));
						int min = Integer.parseInt(time.substring(3,5));
						float sec = Float.parseFloat(time.substring(6,time.length()));
						if (hour >= 0 && hour <= 24
								&& min >= 0 && min < 60
								&& sec >= 0 && sec < 60) {
							result = true;
						}
					}
				}
			}
		} catch (Exception e) {
		}
		return result;
	}

	public Object getMinutesBetweenTimes(int timeFrom, int timeThru) {
		if (timeFrom > 9999 || timeThru > 9999) {
			//JOptionPane.showMessageDialog(null, "Invalid time format.");
			return null;
		} else {
			String strTimeFrom = Integer.toString(timeFrom);
			if (strTimeFrom.length() == 1) {
				strTimeFrom = "00:" + "0"+ strTimeFrom;
			}
			if (strTimeFrom.length() == 2) {
				strTimeFrom = "00:" + strTimeFrom;
			}
			if (strTimeFrom.length() == 3) {
				strTimeFrom = "0" + strTimeFrom.substring(0, 1) + ":" + strTimeFrom.substring(1, 3);
			}
			if (strTimeFrom.length() == 4) {
				strTimeFrom = strTimeFrom.substring(0, 2) + ":" + strTimeFrom.substring(2, 4);
			}
			String strTimeThru = Integer.toString(timeThru);
			if (strTimeThru.length() == 1) {
				strTimeThru = "00:" + "0"+ strTimeThru;
			}
			if (strTimeThru.length() == 2) {
				strTimeThru = "00:" + strTimeThru;
			}
			if (strTimeThru.length() == 3) {
				strTimeThru = "0" + strTimeThru.substring(0, 1) + ":" + strTimeThru.substring(1, 3);
			}
			if (strTimeThru.length() == 4) {
				strTimeThru = strTimeThru.substring(0, 2) + ":" + strTimeThru.substring(2, 4);
			}
			return getMinutesBetweenTimes(strTimeFrom, strTimeThru);
		}
	}

	public Object getMinutesBetweenTimes(String timeFrom, String timeThru) {
		int minDiff = 0;
		try {
			if (timeFrom.substring(2, 3).equals(":") && timeThru.substring(2, 3).equals(":")) {
				int hourFrom = Integer.parseInt(timeFrom.substring(0,2));
				int minFrom = Integer.parseInt(timeFrom.substring(3,5));
				int hourThru = Integer.parseInt(timeThru.substring(0,2));
				int minThru = Integer.parseInt(timeThru.substring(3,5));
				minDiff = ((hourThru - hourFrom) * 60) + (minThru - minFrom);
			} else {
				//JOptionPane.showMessageDialog(null, "Invalid time format.");
				return null;
			}
		} catch (Exception e) {
			//JOptionPane.showMessageDialog(null, "Invalid time format.\n" + e.getMessage());
			return null;
		}
		return minDiff;
	}

	public String getNextNumber(String numberID) {
		String nextNumber = "";
		/////////////////////////////////////////////////////////////
		// Getting next number and updating number with counted-up //
		/////////////////////////////////////////////////////////////
		try {
			String sql = "select * from " + numberingTable + " where IDNUMBER = '" + numberID + "'";
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			if (operator.next()) {
				nextNumber = getFormattedNextNumber(
						Integer.parseInt(operator.getValueOf("NRCURRENT").toString()),
						Integer.parseInt(operator.getValueOf("NRNUMDIGIT").toString()),
						operator.getValueOf("TXPREFIX").toString().trim(),
						operator.getValueOf("FGWITHCD").toString());
				int number = countUpNumber(
						Integer.parseInt(operator.getValueOf("NRCURRENT").toString()),
						Integer.parseInt(operator.getValueOf("NRNUMDIGIT").toString()));
				sql = "update " + numberingTable + 
				" set NRCURRENT = " + number +
				", UPDCOUNTER = " + (Integer.parseInt(operator.getValueOf("UPDCOUNTER").toString()) + 1) +
				" where IDNUMBER = '" + numberID + "'" +
				" and UPDCOUNTER = " + Integer.parseInt(operator.getValueOf("UPDCOUNTER").toString());
				operator = new XFTableOperator(this, null, sql, true);
				operator.execute();
			} else {
				//JOptionPane.showMessageDialog(null, XFUtility.RESOURCE.getString("SessionError13") + numberID + XFUtility.RESOURCE.getString("SessionError14"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return nextNumber;
	}

	public void setNextNumber(String numberID, int nextNumber) {
		try {
			String sql = "select * from " + numberingTable + " where IDNUMBER = '" + numberID + "'";
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			if (operator.next()) {
				sql = "update " + numberingTable + 
				" set NRCURRENT = " + nextNumber +
				", UPDCOUNTER = " + (Integer.parseInt(operator.getValueOf("UPDCOUNTER").toString()) + 1) +
				" where IDNUMBER = '" + numberID + "'" +
				" and UPDCOUNTER = " + Integer.parseInt(operator.getValueOf("UPDCOUNTER").toString());
				operator = new XFTableOperator(this, null, sql, true);
				operator.execute();
			} else {
				//JOptionPane.showMessageDialog(null, XFUtility.RESOURCE.getString("SessionError13") + numberID + XFUtility.RESOURCE.getString("SessionError14"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getFormattedNextNumber(int number, int digit, String prefix, String withCD) {
		String wrkStr;
		String nextNumber = "";
		int wrkInt, wrkSum;
		//
		DecimalFormat decimalFormat = new DecimalFormat();
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < digit; i++) {
			buf.append("0");
		}
		decimalFormat.applyPattern(buf.toString());
		String outputdata = decimalFormat.format(number);
		//
		if (withCD.equals("T")) {
			wrkSum = 0;
			for (int i = 0; i < outputdata.length(); i++) {
				wrkStr = outputdata.substring(i, i+1);
				wrkInt = Integer.parseInt(wrkStr);
				if ((wrkInt % 2) == 1) {
					wrkInt = wrkInt * 3;
				}
				wrkSum = wrkSum + wrkInt;
			}
			wrkInt = wrkSum % 10;
			wrkInt = 10 - wrkInt; //Check Digit with Modulus 10//
			nextNumber = prefix + outputdata + wrkInt;
		} else {
			nextNumber = prefix + outputdata;
		}
		//
		return nextNumber;
	}

	private int countUpNumber(int number, int digit) {
		number++;
		if (digit == 1 && number == 10) {
			number = 1;
		}
		if (digit == 2 && number == 100) {
			number = 1;
		}
		if (digit == 3 && number == 1000) {
			number = 1;
		}
		if (digit == 4 && number == 10000) {
			number = 1;
		}
		if (digit == 5 && number == 100000) {
			number = 1;
		}
		if (digit == 6 && number == 1000000) {
			number = 1;
		}
		if (digit == 7 && number == 10000000) {
			number = 1;
		}
		if (digit == 8 && number == 100000000) {
			number = 1;
		}
		if (digit == 9 && number == 1000000000) {
			number = 1;
		}
		return number;
	}

	public String getSystemVariantString(String itemID) {
		String strValue = "";
		String sql = "";
		try {
			sql = "select * from " + variantsTable + " where IDVARIANT = '" + itemID + "'";
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			if (operator.next()) {
				strValue = operator.getValueOf("TXVALUE").toString().trim();
				if (strValue.contains("<CURRENT>")) {
					strValue = strValue.replace("<CURRENT>", currentFolder);
				}
			}
		} catch (Exception e) {
			//JOptionPane.showMessageDialog(null, "Accessing to the system variant table failed.\n" + e.getMessage());
		}
		return strValue;
	}

	public int getSystemVariantInteger(String itemID) {
		String strValue = "";
		int intValue = 0;
		try {
			String sql = "select * from " + variantsTable + " where IDVARIANT = '" + itemID + "'";
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			if (operator.next()) {
				strValue = operator.getValueOf("TXVALUE").toString().trim();
				if (operator.getValueOf("TXTYPE").toString().trim().equals("NUMBER")) {
					intValue = Integer.parseInt(strValue);
				} else {
					//JOptionPane.showMessageDialog(null, "Value of system Variant with ID '" + itemID + "' is not number.");
				}
			}
		} catch (Exception e) {
			//JOptionPane.showMessageDialog(null, "Accessing to the system variant table failed.\n" + e.getMessage());
		}
		return intValue;
	}

	public float getSystemVariantFloat(String itemID) {
		String strValue = "";
		float floatValue = (float)0.0;
		try {
			String sql = "select * from " + variantsTable + " where IDVARIANT = '" + itemID + "'";
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			if (operator.next()) {
				strValue = operator.getValueOf("TXVALUE").toString().trim();
				if (operator.getValueOf("TXTYPE").toString().trim().equals("NUMBER")) {
					floatValue = Float.parseFloat(strValue);
				} else {
					//JOptionPane.showMessageDialog(null, "Value of system Variant with ID '" + itemID + "' is not number.");
				}
			}
		} catch (Exception e) {
			//JOptionPane.showMessageDialog(null, "Accessing to the system variant table failed.\n" + e.getMessage());
		}
		return floatValue;
	}

	public void setSystemVariant(String itemID, String value) {
		try {
			String sql = "select * from " + variantsTable + " where IDVARIANT = '" + itemID + "'";
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			if (operator.next()) {
				sql = "update " + variantsTable + 
				" set TXVALUE = '" + value + "'" +
				", UPDCOUNTER = " + (Integer.parseInt(operator.getValueOf("UPDCOUNTER").toString()) + 1) +
				" where IDVARIANT = '" + itemID + "'" +
				" and UPDCOUNTER = " + Integer.parseInt(operator.getValueOf("UPDCOUNTER").toString());
				operator = new XFTableOperator(this, null, sql, true);
				operator.execute();
			} else {
				sql = "insert into " + variantsTable
				+ " (IDVARIANT, TXNAME, TXTYPE, TXVALUE) values ("
				+ "'" + itemID + "', '" + itemID.substring(0, 10) + "', 'STRING', '" + value + "')";
				operator = new XFTableOperator(this, null, sql, true);
				operator.execute();
			}
		} catch (Exception e) {
			//JOptionPane.showMessageDialog(null, "Accessing to the system variant table failed.\n" + e.getMessage());
		}
	}

	public float getAnnualExchangeRate(String currencyCode, int fYear, String type) {
		float rateReturn = 0;
		if (currencyCode.equals(getSystemVariantString("SYSTEM_CURRENCY"))) {
			rateReturn = 1;
		} else {
			try {
				String sql = "select * from " + exchangeRateAnnualTable
				+ " where KBCURRENCY = '" + currencyCode
				+ "' and DTNEND = " + fYear;
				XFTableOperator operator = new XFTableOperator(this, null, sql, true);
				if (operator.next()) {
					rateReturn = Float.parseFloat(operator.getValueOf("VLRATEM").toString());
					if (type.equals("TTB")) {
						rateReturn = Float.parseFloat(operator.getValueOf("VLRATEB").toString());
					}
					if (type.equals("TTS")) {
						rateReturn = Float.parseFloat(operator.getValueOf("VLRATES").toString());
					}
				} else {
					//JOptionPane.showMessageDialog(null, "Annual exchange rate not found for '" + currencyCode + "'," + fYear + "."+ "\nSQL: " + sql);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return rateReturn;
	}

	public float getMonthlyExchangeRate(String currencyCode, String date, String type) {
		return getMonthlyExchangeRate(currencyCode, getFYearOfDate(date), getMSeqOfDate(date), type);
	}

	public float getMonthlyExchangeRate(String currencyCode, int fYear, int mSeq, String type) {
		float rateReturn = 0;
		if (currencyCode.equals(getSystemVariantString("SYSTEM_CURRENCY"))) {
			rateReturn = 1;
		} else {
			try {
				String sql = "select * from " + exchangeRateMonthlyTable
				+ " where KBCURRENCY = '" + currencyCode
				+ "' and DTNEND = " + fYear
				+ " and DTMSEQ = " + mSeq;
				XFTableOperator operator = new XFTableOperator(this, null, sql, true);
				if (operator.next()) {
					rateReturn = Float.parseFloat(operator.getValueOf("VLRATEM").toString());
					if (type.equals("TTB")) {
						rateReturn = Float.parseFloat(operator.getValueOf("VLRATEB").toString());
					}
					if (type.equals("TTS")) {
						rateReturn = Float.parseFloat(operator.getValueOf("VLRATES").toString());
					}
				}
				if (rateReturn == 0) {
					rateReturn = getAnnualExchangeRate(currencyCode, fYear, type);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return rateReturn;
	}

	public int getTaxAmount(String date, int amount) {
		int fromDate = 0;
		int taxAmount = 0;
		if (date != null && !date.equals("")) {
			int targetDate = Integer.parseInt(date.replaceAll("-", "").replaceAll("/", ""));
			float rate = 0;
			try {
				String sql = "select * from " + taxTable + " order by DTSTART DESC";
				XFTableOperator operator = new XFTableOperator(this, null, sql, true);
				while (operator.next()) {
					fromDate = Integer.parseInt(operator.getValueOf("DTSTART").toString().replaceAll("-", ""));
					if (targetDate >= fromDate) {
						rate = Float.parseFloat(operator.getValueOf("VLTAXRATE").toString());
						break;
					}
				}
				taxAmount = (int)Math.floor(amount * rate);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return taxAmount;
	}

	public int getMSeqOfDate(String parmDate) {
		int mSeq = 0;
		if (!parmDate.equals("")) {
			int month, date;
			parmDate = parmDate.replaceAll("-", "").replaceAll("/", "").trim();
			parmDate = parmDate.replaceAll("/", "");
			month = Integer.parseInt(parmDate.substring(4,6));
			date = Integer.parseInt(parmDate.substring(6,8));
			//
			boolean isWithinMonth = false;
			int startMonth = 1;
			int lastDay = 31;
			int value1 = getSystemVariantInteger("FIRST_MONTH");
			if (value1 != 0) {
				startMonth = value1;
			}
			int value2 = getSystemVariantInteger("LAST_DAY");
			if(value2 != 0) {
				lastDay = value2;
			}
			//
			if (lastDay >= 31) {
				if ((month == 1 && date <= 31) 
						|| (month == 2 && date <= 29)
						|| (month == 3 && date <= 31)
						|| (month == 4 && date <= 30)
						|| (month == 5 && date <= 31)
						|| (month == 6 && date <= 30)
						|| (month == 7 && date <= 31)
						|| (month == 8 && date <= 31)
						|| (month == 9 && date <= 30)
						|| (month == 10 && date <= 31)
						|| (month == 11 && date <= 30)
						|| (month == 12 && date <= 31)) {
					isWithinMonth = true;
				}
			}
			if (lastDay == 30) {
				if ((month == 1 && date <= 30) 
						|| (month == 2 && date <= 29)
						|| (month == 3 && date <= 30)
						|| (month == 4 && date <= 30)
						|| (month == 5 && date <= 30)
						|| (month == 6 && date <= 30)
						|| (month == 7 && date <= 30)
						|| (month == 8 && date <= 30)
						|| (month == 9 && date <= 30)
						|| (month == 10 && date <= 30)
						|| (month == 11 && date <= 30)
						|| (month == 12 && date <= 30)) {
					isWithinMonth = true;
				}
			}
			if (lastDay <= 29 && date <= lastDay) {
				isWithinMonth = true;
			}
			//
			if (isWithinMonth) {
				mSeq = month - startMonth + 1;
			} else {
				mSeq = month - startMonth + 2;
			}
			if (mSeq <= 0) {
				mSeq = mSeq + 12;
			}
		}
		return mSeq;
	}

	public int getFYearOfDate(String parmDate) {
		int fYear = 0;
		int mSeq = 0;
		if (!parmDate.equals("") && parmDate != null) {
			int month, date;
			parmDate = parmDate.replaceAll("-", "").replaceAll("/", "").trim();
			fYear = Integer.parseInt(parmDate.substring(0,4));
			month = Integer.parseInt(parmDate.substring(4,6));
			date = Integer.parseInt(parmDate.substring(6,8));
			//
			boolean isWithinMonth = false;
			int startMonth = 1;
			int lastDay = 31;
			int value1 = getSystemVariantInteger("FIRST_MONTH");
			if (value1 != 0) {
				startMonth = value1;
			}
			int value2 = getSystemVariantInteger("LAST_DAY");
			if(value2 != 0) {
				lastDay = value2;
			}
			//
			if (lastDay >= 31) {
				if ((month == 1 && date <= 31) 
						|| (month == 2 && date <= 29)
						|| (month == 3 && date <= 31)
						|| (month == 4 && date <= 30)
						|| (month == 5 && date <= 31)
						|| (month == 6 && date <= 30)
						|| (month == 7 && date <= 31)
						|| (month == 8 && date <= 31)
						|| (month == 9 && date <= 30)
						|| (month == 10 && date <= 31)
						|| (month == 11 && date <= 30)
						|| (month == 12 && date <= 31)) {
					isWithinMonth = true;
				}
			}
			if (lastDay == 30) {
				if ((month == 1 && date <= 30) 
						|| (month == 2 && date <= 29)
						|| (month == 3 && date <= 30)
						|| (month == 4 && date <= 30)
						|| (month == 5 && date <= 30)
						|| (month == 6 && date <= 30)
						|| (month == 7 && date <= 30)
						|| (month == 8 && date <= 30)
						|| (month == 9 && date <= 30)
						|| (month == 10 && date <= 30)
						|| (month == 11 && date <= 30)
						|| (month == 12 && date <= 30)) {
					isWithinMonth = true;
				}
			}
			if (lastDay <= 29 && date <= lastDay) {
				isWithinMonth = true;
			}
			//
			if (isWithinMonth) {
				mSeq = month - startMonth + 1;
			} else {
				mSeq = month - startMonth + 2;
			}
			if (mSeq <= 0) {
				fYear = fYear - 1;
			}
		}
		return fYear;
	}

	public String getYearMonthOfFYearMSeq(String fYearMSeq) {
		String resultYear = "";
		String resultMonth = "";
		int workInt;
		int startMonth = getSystemVariantInteger("FIRST_MONTH");
		int fYear = Integer.parseInt(fYearMSeq.substring(0, 4)); 
		int mSeq = Integer.parseInt(fYearMSeq.substring(4, 6)); 
		//
		workInt = startMonth + mSeq - 1;
		if (workInt > 12) {
			workInt = workInt - 12;
			resultMonth = Integer.toString(workInt);
			workInt = fYear + 1;
			resultYear = Integer.toString(workInt);
		} else {
			resultMonth = Integer.toString(workInt);
			workInt = fYear;
			resultYear = Integer.toString(workInt);
		}
		//
		return resultYear + resultMonth;
	}

	public void closeSession(boolean isToWriteLogAndClose) {

		///////////////////////
		// Write session log //
		///////////////////////
		if (isToWriteLogAndClose) {
			if (noErrorsOccured) {
				sessionStatus = "END";
			} else {
				sessionStatus = "ERR";
			}
			try {
				String sql = "update " + sessionTable
				+ " set DTLOGOUT=CURRENT_TIMESTAMP, KBSESSIONSTATUS='"
				+ sessionStatus + "' where " + "NRSESSION='" + sessionID + "'";
				XFTableOperator operator = new XFTableOperator(this, null, sql, true);
				operator.execute();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		////////////////////////////////
		// Commit pending transaction //
		////////////////////////////////
		this.commit(true, null);

		///////////////////////////////////////////
		// Execute login-script to close session //
		///////////////////////////////////////////
		if (scriptEngine != null && !loginScript.equals("")) {
			try {
				scriptEngine.eval(loginScript);
			} catch (ScriptException e) {
				e.printStackTrace();
			}
		}

		///////////////////////////////
		// Close local DB connection //
		///////////////////////////////
		if (appServerName.equals("")) {
			try {
				connectionManualCommit.close();
				connectionAutoCommit.close();
				if (databaseName.contains("jdbc:derby")) {
					DriverManager.getConnection("jdbc:derby:;shutdown=true");
				}
			} catch (SQLException e) {
				if (databaseName.contains("jdbc:derby")
						&& e.getSQLState() != null
						&& !e.getSQLState().equals("XJ015")) {
					e.printStackTrace();
				}
			}
		}
	}

	public int writeLogOfFunctionStarted(String functionID, String functionName) {
		sqProgram++;
		try {
			String sql = "insert into " + sessionDetailTable
			+ " (NRSESSION, SQPROGRAM, IDMENU, IDPROGRAM, TXPROGRAM, DTSTART, KBPROGRAMSTATUS) values ("
			+ "'" + this.getSessionID() + "'," + sqProgram + "," + "'**'," + "'" + functionID + "'," + "'" + functionName + "'," + "CURRENT_TIMESTAMP,'')";
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			operator.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sqProgram;
	}

	public void writeLogOfFunctionClosed(int sqProgramOfFunction, String programStatus, String tableOperationLog, String errorLog) {
		String logString = "";
		StringBuffer bf = new StringBuffer();

		if (tableOperationLog.length() > 3000) {
			tableOperationLog = "... " + tableOperationLog.substring(tableOperationLog.length()-3000, tableOperationLog.length());
		}
		bf.append(tableOperationLog.replace("'", "\""));
		if (!errorLog.equals("")) {
			bf.append("\n<ERROR LOG>\n");
			bf.append(errorLog.replace("'", "\""));
		}
		logString = bf.toString();
		if (logString.length() > 3800) {
			logString = logString.substring(0, 3800) + " ...";
		}

		////////////////////////////////////////////////////////////////////////////
		// Note that value of MS Access Date field is expressed like #2000-01-01# //
		////////////////////////////////////////////////////////////////////////////
		if (databaseName.contains("ucanaccess:")) {
			if (logString.contains("#")) {
				logString = logString.replaceAll("#", "\"");
			}
		}

		if (programStatus.equals("99")) {
			noErrorsOccured = false;
		}

		try {
			String sql = "update " + sessionDetailTable
			+ " set DTEND=CURRENT_TIMESTAMP, KBPROGRAMSTATUS='"
			+ programStatus + "', TXERRORLOG='"
			+ logString + "' where " + "NRSESSION='"
			+ this.getSessionID() + "' and "
			+ "SQPROGRAM=" + sqProgramOfFunction;
			XFTableOperator operator = new XFTableOperator(this, null, sql, true);
			operator.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getAppServerName() {
		return appServerName;
	}

	public String getSubDBName(String id) {
		return subDBNameList.get(subDBIDList.indexOf(id));
	}

	public int getSubDBListSize() {
		return subDBNameList.size();
	}

	public String getSubDBName(int index) {
		return subDBNameList.get(index);
	}

	public String getSubDBUser(int index) {
		return subDBUserList.get(index);
	}

	public String getSubDBPassword(int index) {
		return subDBPasswordList.get(index);
	}

	public Connection getConnectionManualCommit() {
		return connectionManualCommit;
	}

	public Connection getConnectionAutoCommit() {
		return connectionAutoCommit;
	}

	public Connection getConnectionReadOnly(String id) {
		return subDBConnectionList.get(subDBIDList.indexOf(id));
	}

	public void commit() {
		this.commit(true, null);
	}

	public void commit(boolean isCommit, StringBuffer logBuf) {
		if (appServerName.equals("")) {
			try {
				if (isCommit) {
					connectionManualCommit.commit();
					if (logBuf != null) {
						XFUtility.appendLog("Local-commit succeeded.", logBuf);
					}
				} else {
					connectionManualCommit.rollback();
					if (logBuf != null) {
						XFUtility.appendLog("Local-rollback succeeded.", logBuf);
					}
				}
			} catch (SQLException e) {
				//JOptionPane.showMessageDialog(null, e.getMessage());
				if (logBuf != null) {
					XFUtility.appendLog(e.getMessage(), logBuf);
				}
			}
		} else {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = null;
			try {
				httpPost = new HttpPost(appServerName);
				List<NameValuePair> objValuePairs = new ArrayList<NameValuePair>(2);
				objValuePairs.add(new BasicNameValuePair("SESSION", sessionID));
				if (isCommit) {
					objValuePairs.add(new BasicNameValuePair("METHOD", "COMMIT"));
				} else {
					objValuePairs.add(new BasicNameValuePair("METHOD", "ROLLBACK"));
				}
				httpPost.setEntity(new UrlEncodedFormEntity(objValuePairs, "UTF-8"));  
				//
				HttpResponse response = httpClient.execute(httpPost);
				HttpEntity responseEntity = response.getEntity();
				if (logBuf != null) {
					if (responseEntity == null) {
						XFUtility.appendLog("Response is NULL.", logBuf);
					} else {
						XFUtility.appendLog(EntityUtils.toString(responseEntity), logBuf);
					}
				}
			} catch (Exception e) {
				String msg = "";
				if (isCommit) {
					msg = "Connection failed to commit with the servlet '" + appServerName + "'";
				} else {
					msg = "Connection failed to rollback with the servlet '" + appServerName + "'";
				}
				//JOptionPane.showMessageDialog(null, msg);
				if (logBuf != null) {
					XFUtility.appendLog(msg + "\n" + e.getMessage(), logBuf);
				}
			} finally {
				httpClient.getConnectionManager().shutdown();
				if (httpPost != null) {
					httpPost.abort();
				}
			}
		}
	}

	public Object requestWebService(String uri) {
		return requestWebService(uri, "UTF-8");
	}
	public Object requestWebService(String uri, String encoding) {
		Object response = null;
		HttpResponse httpResponse = null;
		InputStream inputStream = null;
		HttpClient httpClient = new DefaultHttpClient();
		try {
			httpGet.setURI(new URI(uri));
			httpResponse = httpClient.execute(httpGet);
//			String contentType = httpResponse.getEntity().getContentType().getValue();
//			if (contentType.contains("text/xml")) {
//				inputStream = httpResponse.getEntity().getContent();
//				domParser.parse(new InputSource(inputStream));
//				response = domParser.getDocument();
//			}
//			if (contentType.contains("application/json")) {
//				String text = EntityUtils.toString(httpResponse.getEntity());
//				if (text.startsWith("[")) {
//					response = new JSONArray(text);
//				} else {
//					response = new JSONObject(text);
//				}
//			}
//			if (contentType.contains("text/plain")) {
//				response = EntityUtils.toString(httpResponse.getEntity());
//			}
			response = EntityUtils.toString(httpResponse.getEntity(), encoding);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(null, XFUtility.RESOURCE.getString("FunctionMessage53") + "\n" + ex.getMessage());
		} finally {
			httpClient.getConnectionManager().shutdown();
			try {
				if (inputStream != null) {
					inputStream.close();
				}
			} catch(Exception e) {}
		}
		return response;
	}
	public XFHttpRequest createWebServiceRequest(String uri) {
		return new XFHttpRequest(uri, "UTF-8");
	}
	public XFHttpRequest createWebServiceRequest(String uri, String encoding) {
		return new XFHttpRequest(uri, encoding);
	}

	public Document parseStringToGetXmlDocument(String data) throws Exception {
		return parseStringToGetXmlDocument(data, "UTF-8");
	}
	public Document parseStringToGetXmlDocument(String data, String encoding) throws Exception {
		domParser.parse(new InputSource(new ByteArrayInputStream(data.getBytes(encoding))));
		return domParser.getDocument();
	}
	public Document createXmlDocument() throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    DocumentBuilder db = dbf.newDocumentBuilder();
	    return db.newDocument();
	}
	public Document createXmlDocument(String name) throws Exception {
	    Document document = createXmlDocument();
	    org.w3c.dom.Element element = document.createElement(name);
	    document.appendChild(element);
	    return document;
	}
	public org.w3c.dom.Element createXmlNode(Document document, String name) throws Exception {
		return document.createElement(name);
	}
	public org.w3c.dom.Element getXmlNode(Document document, String name) throws Exception {
		NodeList elementList = document.getElementsByTagName(name);
		return (org.w3c.dom.Element)elementList.item(0);
	}
	public org.w3c.dom.Element getXmlNode(org.w3c.dom.Element element, String name) throws Exception {
		NodeList elementList = element.getElementsByTagName(name);
		return (org.w3c.dom.Element)elementList.item(0);
	}
	public ArrayList<org.w3c.dom.Element> getXmlNodeList(Document document, String name) throws Exception {
		ArrayList<org.w3c.dom.Element> elementList = new ArrayList<org.w3c.dom.Element>();
		NodeList nodeList = document.getElementsByTagName(name);
		for (int i = 0; i < nodeList.getLength(); i++) {
			elementList.add((org.w3c.dom.Element)nodeList.item(i));
		}
		return elementList;
	}
	public ArrayList<org.w3c.dom.Element> getXmlNodeList(org.w3c.dom.Element element, String name) throws Exception {
		ArrayList<org.w3c.dom.Element> elementList = new ArrayList<org.w3c.dom.Element>();
		NodeList nodeList = element.getElementsByTagName(name);
		for (int i = 0; i < nodeList.getLength(); i++) {
			elementList.add((org.w3c.dom.Element)nodeList.item(i));
		}
		return elementList;
	}
	public String getXmlNodeContent(org.w3c.dom.Element element, String name) throws Exception {
		org.w3c.dom.Element workElement = getXmlNode(element, name);
		return workElement.getTextContent();
	}

	public JSONObject createJsonObject(String text) throws Exception {
		return new JSONObject(text);
	}
	public JSONObject createJsonObject() throws Exception {
		return new JSONObject();
	}
	public JSONArray createJsonArray(String text) throws Exception {
		return new JSONArray(text);
	}
	public JSONObject getJsonObject(JSONObject object, String name) throws Exception {
		return object.getJSONObject(name);
	}
	public JSONArray getJsonArray(JSONObject object, String name) throws Exception {
		return object.getJSONArray(name);
	}
	public JSONObject getJsonObject(JSONArray array, int index) throws Exception {
		return array.getJSONObject(index);
	}
	
	public String getDigestedValueForUser(String user, String value) {
		if (isValueSaltedForUser) {
			return getDigestedValue(value, digestAlgorithmForUser, countOfExpandForUser, user);
		} else {
			return getDigestedValue(value, digestAlgorithmForUser, countOfExpandForUser, "");
		}
	}
	public String getDigestedValueForUser(String value) {
		if (isValueSaltedForUser) {
			return getDigestedValue(value, digestAlgorithmForUser, countOfExpandForUser, userID);
		} else {
			return getDigestedValue(value, digestAlgorithmForUser, countOfExpandForUser, "");
		}
	}
	public String getDigestedValue(String value, String algorithm) {
		return getDigestedValue(value, algorithm, 1, "");
	}
	public String getDigestedValue(String value, String algorithm, int expand, String salt) {
		String digestedValue = "";
		int count = expand - 1;
		try {
			DigestAdapter adapter = new DigestAdapter(algorithm);
			digestedValue = adapter.digest(value + salt);
			for (int i=0;i<count;i++) {
				digestedValue = adapter.digest(digestedValue + salt);
			}
		} catch (NoSuchAlgorithmException e) {
			return digestedValue;
		}
		return digestedValue;
	}

	public String getAddressFromZipNo(String zipNo) {
		String value = "";
		String zipNo_ = zipNo.replace("-", "");
		HttpResponse response = null;
		InputStream inputStream = null;
		HttpClient httpClient = new DefaultHttpClient();
		try {
			httpGet.setURI(new URI(ZIP_URL + "zn=" + zipNo_));
			response = httpClient.execute(httpGet);  
			if (response.getStatusLine().getStatusCode() < 400){
				inputStream = response.getEntity().getContent();
				domParser.parse(new InputSource(inputStream));
				responseDoc = domParser.getDocument();
				org.w3c.dom.Element rootNode = (org.w3c.dom.Element)responseDoc.getElementsByTagName("ZIP_result").item(0);
				if (rootNode.getElementsByTagName("value").getLength() == 0) {
					//JOptionPane.showMessageDialog(null, XFUtility.RESOURCE.getString("FunctionMessage54") + "\n" + zipNo);
				} else {
					org.w3c.dom.Element stateNode = (org.w3c.dom.Element)rootNode.getElementsByTagName("value").item(4);
					org.w3c.dom.Element cityNode = (org.w3c.dom.Element)rootNode.getElementsByTagName("value").item(5);
					org.w3c.dom.Element addressNode = (org.w3c.dom.Element)rootNode.getElementsByTagName("value").item(6);
					org.w3c.dom.Element companyNode = (org.w3c.dom.Element)rootNode.getElementsByTagName("value").item(7);
					String state = stateNode.getAttribute("state");
					String city = cityNode.getAttribute("city");
					String address = addressNode.getAttribute("address");
					if (address.equals("none")) {
						address = "";
					}
					String company = companyNode.getAttribute("company");
					if (company.equals("none")) {
						company = "";
					}
					value = state + city + address + company;
				}
			}  
		} catch (Exception ex) {
			//JOptionPane.showMessageDialog(null, XFUtility.RESOURCE.getString("FunctionMessage53") + "\n" + ex.getMessage());
		} finally {
			httpClient.getConnectionManager().shutdown();
			try {
				if (inputStream != null) {
					inputStream.close();
				}
			} catch(IOException e) {}
		}
		return value;
	}

	public String getDateFormat() {
		return dateFormat;
	}

	public HashMap<String, Object> executeFunction(String functionID, HashMap<String, Object> parmMap) throws Exception {
		ScriptLauncher launcher =new ScriptLauncher(functionID, this);
		return launcher.execute(parmMap);
	}

	public String executeProgram(String pgmName) {
		String message = "";
		try {
			Runtime rt = Runtime.getRuntime();
			Process p = rt.exec(pgmName);
			if (p != null) {
				int count = 0;
				String result = "";
				StringBuffer buf = new StringBuffer();
				InputStream is = p.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				while ((result = br.readLine()) != null) {
					if (count > 0) {
						buf.append("\n");
					}
					buf.append(result);
					count++;
				}
				message = buf.toString();
			}
		} catch (IOException ex) {
			//ex.printStackTrace();
			//JOptionPane.showMessageDialog(null, ex.getMessage());
		}
		return message;
	}

	public String getImageFileFolder() {
		return imageFileFolder;
	}
	
	public File createTempFile(String functionID, String extension) throws IOException {
		String header = "XeadDriver_";
		//
		if (extension.equals(".pdf")) {
			header = "PDF_" + header;
		}
		if (extension.equals(".xls")) {
			header = "XLS_" + header;
		}
		if (extension.equals(".log")) {
			header = "LOG_" + header;
		}
		//
		File tempFile = File.createTempFile(header + functionID + "_", extension, outputFolder);
		//
		if (outputFolder == null) {
			tempFile.deleteOnExit();
		}
		//
		return tempFile;
	}

	public boolean existsFile(String fileName) {
		File file = new File(fileName);
		return file.exists();
	}

	public boolean deleteFile(String fileName) {
			File file = new File(fileName);
			return file.delete();
	}

	public boolean renameFile(String currentName, String newName) {
			File currentFile = new File(currentName);
			File newFile = new File(newName);
			return currentFile.renameTo(newFile);
	}

	public XFTextFileOperator createTextFileOperator(String operation, String fileName, String separator) {
		return createTextFileOperator(operation, fileName, separator, "");
	}

	public XFTextFileOperator createTextFileOperator(String operation, String fileName, String separator, String charset) {
		File file = new File(fileName);
		if (operation.equals("Read") && !file.exists()) {
			//JOptionPane.showMessageDialog(null, XFUtility.RESOURCE.getString("SessionError21") + fileName + XFUtility.RESOURCE.getString("SessionError22"));
			return null;
		} else {
			return new XFTextFileOperator(operation, fileName, separator, charset);
		}
	}
	
	public XFTableOperator createTableOperator(String oparation, String tableID) {
		XFTableOperator operator = null;
		try {
			operator = new XFTableOperator(this, null, oparation, tableID, true);
		} catch (Exception e) {
		}
		return operator;
	}

	public XFTableOperator createTableOperator(String sqlText) {
		return new XFTableOperator(this, null, sqlText, true);
	}

	org.w3c.dom.Document getDomDocument() {
		return domDocument;
	}

	public void sendMail(String addressFrom, String addressTo, String addressCc, 
			String subject, String message,
			String fileName, String attachedName, String charset) {
		try{
			Properties props = new Properties();
			props.put("mail.smtp.host", smtpHost);
			props.put("mail.host", smtpHost);
			props.put("mail.from", addressFrom);
			if (!smtpPassword.equals("")) {
				props.setProperty("mail.smtp.auth", "true");
			}
			if (!smtpPort.equals("")) {
				props.put("mail.smtp.port", smtpPort);
			}
			javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance(props, null);
			MimeMessage mailObj = new MimeMessage(mailSession);
			InternetAddress[] toList = new InternetAddress[1];
			toList[0] = new InternetAddress(addressTo);
			mailObj.setRecipients(Message.RecipientType.TO, toList);
			InternetAddress[] ccList = new InternetAddress[1];
			ccList[0] = new InternetAddress(addressCc);
			mailObj.setRecipients(Message.RecipientType.CC, ccList);
			mailObj.setFrom(new InternetAddress(addressFrom));
			mailObj.setSentDate(new Date());
			if (charset.equals("")) {
				mailObj.setSubject(subject);
			} else {
				mailObj.setSubject(subject, charset);
			}
			//
			MimeBodyPart bodyMessage = new MimeBodyPart();
			MimeBodyPart bodyAttachedFile = new MimeBodyPart();
			if (charset.equals("")) {
				bodyMessage.setText(message);
			} else {
				bodyMessage.setText(message, charset);
			}
			if (!fileName.equals("")) {
				FileDataSource fds = new FileDataSource(fileName);
				bodyAttachedFile.setDataHandler(new DataHandler(fds));
				if (!attachedName.equals("")) {
					if (charset.equals("")) {
						bodyAttachedFile.setFileName(MimeUtility.encodeWord(attachedName));
					} else {
						bodyAttachedFile.setFileName(MimeUtility.encodeWord(attachedName, charset, "B"));
					}
				}
			}
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(bodyMessage);
			if (!fileName.equals("")) {
				multipart.addBodyPart(bodyAttachedFile);
			}
			mailObj.setContent(multipart);
			//
			if (smtpPassword.equals("")) {
				Transport.send(mailObj);
			} else {
				Transport tp = mailSession.getTransport("smtp");
				tp.connect(smtpHost, smtpUser, smtpPassword);
				tp.sendMessage(mailObj, toList);
			}
		}catch(Exception e){
			//JOptionPane.showMessageDialog(null, "Sending mail with subject '" + subject + "' failed.\n\n" + e.getMessage());
		}
	}

//	DigestAdapter getDigestAdapter() {
//		return digestAdapter;
//	}

	String getSystemName() {
		return systemName;
	}

	String getVersion() {
		return systemVersion;
	}

	public String getAdminEmail() {
		return smtpAdminEmail;
	}

	String getTableNameOfUser() {
		return userTable;
	}

	String getTableNameOfVariants() {
		return variantsTable;
	}

	String getTableNameOfUserVariants() {
		return userVariantsTable;
	}

	String getTableNameOfNumbering() {
		return numberingTable;
	}

	String getTableNameOfCalendar() {
		return calendarTable;
	}

	int getNextSQPROGRAM() {
		sqProgram++;
		return sqProgram;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public String getDatabaseUser() {
		return databaseUser;
	}

	public String getDatabasePassword() {
		return databasePassword;
	}

	public void compressTable(String tableID) throws Exception {
		StringBuffer statementBuf;
		org.w3c.dom.Element element;
		Statement statement = null;
		HttpPost httpPost = null;
		String msg = "";
		//
		if (databaseName.contains("jdbc:derby:")) {
			try {
				if (appServerName.equals("")) {
					statement = connectionManualCommit.createStatement();
				}
				//
				for (int i = 0; i < tableList.getLength(); i++) {
					//
					element = (org.w3c.dom.Element)tableList.item(i);
					if (element.getAttribute("ID").startsWith(tableID) || tableID.equals("")) {
						//
						statementBuf = new StringBuffer();
						statementBuf.append("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE('");
						statementBuf.append(databaseUser);
						statementBuf.append("', '") ;
						statementBuf.append(element.getAttribute("ID"));
						statementBuf.append("', 1)") ;
						//
						//////////////////////////////////////
						// Execute procedure by auto-commit //
						//////////////////////////////////////
						if (appServerName.equals("")) {
							statement.executeUpdate(statementBuf.toString());
						} else {
							HttpClient httpClient = new DefaultHttpClient();
							try {
								httpPost = new HttpPost(appServerName);
								List<NameValuePair> objValuePairs = new ArrayList<NameValuePair>(1);
								objValuePairs.add(new BasicNameValuePair("METHOD", statementBuf.toString()));
								httpPost.setEntity(new UrlEncodedFormEntity(objValuePairs, "UTF-8"));  
								HttpResponse response = httpClient.execute(httpPost);
								HttpEntity responseEntity = response.getEntity();
								if (responseEntity == null) {
									msg = "Compressing table " + element.getAttribute("ID") + " failed.";
									//JOptionPane.showMessageDialog(null, msg);
									throw new Exception(msg);
								}
							} finally {
								httpClient.getConnectionManager().shutdown();
								if (httpPost != null) {
									httpPost.abort();
								}
							}
						}
					}
				}
			} catch (SQLException e) {
				//JOptionPane.showMessageDialog(null, "Compressing table " + tableID + " failed.\n" + e.getMessage());
				throw new Exception(e.getMessage());
			} catch (Exception e) {
				//JOptionPane.showMessageDialog(null, "Compressing table " + tableID + " failed.\n" + e.getMessage());
				throw new Exception(e.getMessage());
			} finally {
				if (appServerName.equals("")) {
					try {
						if (statement != null) {
							statement.close();
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public String getSessionID() {
		return sessionID;
	}

	public String getStatus() {
		return sessionStatus;
	}

	public String getFileName() {
		return fileName;
	}

	public String getUserID() {
		return userID;
	}

	public String getUserName() {
		return userName;
	}

	public String getUserEmployeeNo() {
		return userEmployeeNo;
	}

	public String getUserEmailAddress() {
		return userEmailAddress;
	}

	public String getAttribute(String id) {
		return attributeMap.get(id);
	}

	public String getSystemProperty(String id) {
		return System.getProperty(id);
	}

	public void setAttribute(String id, String value) {
		attributeMap.put(id.trim(), value.trim());
	}

	public NodeList getFunctionList() {
		return functionList;
	}

	public String getScriptFunctions() {
		return scriptFunctions;
	}

	public String getFunctionName(String functionID) {
		org.w3c.dom.Element workElement;
		String functionName = "";
		//
		for (int k = 0; k < functionList.getLength(); k++) {
			workElement = (org.w3c.dom.Element)functionList.item(k);
			if (workElement.getAttribute("ID").equals(functionID)) {
				functionName = workElement.getAttribute("Name");
				break;
			}
		}
		//
		return functionName;
	}

	org.w3c.dom.Element getTablePKElement(String tableID) {
		org.w3c.dom.Element element1, element2;
		org.w3c.dom.Element element3 = null;
		NodeList nodeList;
		for (int i = 0; i < tableList.getLength(); i++) {
			element1 = (org.w3c.dom.Element)tableList.item(i);
			if (element1.getAttribute("ID").equals(tableID)) {
				nodeList = element1.getElementsByTagName("Key");
				for (int j = 0; j < nodeList.getLength(); j++) {
					element2 = (org.w3c.dom.Element)nodeList.item(j);
					if (element2.getAttribute("Type").equals("PK")) {
						element3 = element2;
						break;
					}
				}
				break;
			}
		}
		return element3;
	}

	org.w3c.dom.Element getTableElement(String tableID) {
		org.w3c.dom.Element element1;
		org.w3c.dom.Element element2 = null;
		for (int i = 0; i < tableList.getLength(); i++) {
			element1 = (org.w3c.dom.Element)tableList.item(i);
			if (element1.getAttribute("ID").equals(tableID)) {
				element2 = element1;
				break;
			}
		}
		return element2;
	}

	public String getTableName(String tableID) {
		String tableName = "";
		org.w3c.dom.Element element1;
		org.w3c.dom.Element element2 = null;
		for (int i = 0; i < tableList.getLength(); i++) {
			element1 = (org.w3c.dom.Element)tableList.item(i);
			if (element1.getAttribute("ID").equals(tableID)) {
				element2 = element1;
				tableName = element2.getAttribute("Name");
				break;
			}
		}
		return tableName;
	}

	public NodeList getTableNodeList() {
		return tableList;
	}

	public org.w3c.dom.Element getFieldElement(String tableID, String fieldID) {
		org.w3c.dom.Element element1, element2;
		org.w3c.dom.Element element3 = null;
		NodeList nodeList;
		for (int i = 0; i < tableList.getLength(); i++) {
			element1 = (org.w3c.dom.Element)tableList.item(i);
			if (element1.getAttribute("ID").equals(tableID)) {
				nodeList = element1.getElementsByTagName("Field");
				for (int j = 0; j < nodeList.getLength(); j++) {
					element2 = (org.w3c.dom.Element)nodeList.item(j);
					if (element2.getAttribute("ID").equals(fieldID)) {
						element3 = element2;
						break;
					}
				}
				break;
			}
		}
		return element3;
	}
}

class DigestAdapter {
	private MessageDigest digest_;

	public DigestAdapter(String algorithm) throws NoSuchAlgorithmException {
		digest_ = MessageDigest.getInstance(algorithm);
	}

	public synchronized String digest(String str) {
		return toHexString(digestArray(str));
	}

	public synchronized byte[] digestArray(String str) {
		byte[] hash = digest_.digest(str.getBytes());
		digest_.reset();
		return hash;
	}

	private String toHexString(byte[] arr) {
		StringBuffer buff = new StringBuffer(arr.length * 2);
		for (int i = 0; i < arr.length; i++) {
			String b = Integer.toHexString(arr[i] & 0xff);
			if (b.length() == 1) {
				buff.append("0");
			}
			buff.append(b);
		}
		return buff.toString();
	}
}