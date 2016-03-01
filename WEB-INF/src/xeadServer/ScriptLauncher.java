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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


//import xeadDriver.XFTableEvaluator;
////import xeadDriver.XFExecutable;
////import xeadDriver.XFScriptable;
////import xeadDriver.XFTableOperator;
import xeadDriver.XFUtility;

public class ScriptLauncher implements XFExecutable, XFScriptable {
	private org.w3c.dom.Element functionElement_ = null;
	private HashMap<String, Object> parmMap_ = null;
	private HashMap<String, Object> returnMap_ = new HashMap<String, Object>();
	private Session session_ = null;
//	private boolean instanceIsAvailable_ = true;
	private boolean errorHasOccured = false;
	private int programSequence;
	private StringBuffer processLog = new StringBuffer();
	private ScriptEngine scriptEngine;
	private Bindings engineScriptBindings;
	private ByteArrayOutputStream exceptionLog;
	private PrintStream exceptionStream;
	private String exceptionHeader = "";
	private HashMap<String, Object> variantMap = new HashMap<String, Object>();

//	public ScriptLauncher(org.w3c.dom.Element functionElement, Session session) {
//		functionElement_ = functionElement;
//		session_ = session;
//	}

	public ScriptLauncher(String functionID, Session session) {
		org.w3c.dom.Element element;
		session_ = session;

		functionElement_ = null;
		NodeList functionElementList = session_.getFunctionList();
		for (int i = 0; i < functionElementList.getLength(); i++) {
			element = (org.w3c.dom.Element)functionElementList.item(i);
			if (element.getAttribute("ID").equals(functionID)
					&& element.getAttribute("Type").equals("XF000")) {
				functionElement_ = element;
				break;
			}
		}
		if (functionElement_ == null) {
			exceptionHeader = "Invalid function ID: " + functionID;
			errorHasOccured = true;
			closeFunction();
		}
	}

	@Override
	public void callFunction(String functionID) {
		try {
			returnMap_ = session_.executeFunction(functionID, parmMap_);
		} catch (Exception e) {
			exceptionHeader = e.getMessage();
			errorHasOccured = true;
			closeFunction();
		}
		if (returnMap_.get("RETURN_CODE").equals("99")) {
			errorHasOccured = true;
		}
	}

	@Override
	public void cancelWithException(Exception e) {
		e.printStackTrace(exceptionStream);
		this.rollback();
		errorHasOccured = true;
		closeFunction();
	}

	@Override
	public void cancelWithMessage(String message) {
		returnMap_.put("RETURN_MESSAGE", message);
		this.rollback();
		errorHasOccured = true;
		closeFunction();
	}

	@Override
	public void cancelWithScriptException(ScriptException e, String scriptName) {
		if (scriptName.equals("")) {
			exceptionHeader = "'Script error in the function'\n";
		} else {
			exceptionHeader = "'" + scriptName + "' Script error\n";
		}
		e.printStackTrace(exceptionStream);
		this.rollback();
		errorHasOccured = true;
		closeFunction();
	}

	@Override
	public void setErrorAndCloseFunction() {
		errorHasOccured = true;
		closeFunction();
	}

	@Override
	public void commit() {
		session_.commit(true, processLog);
	}

	@Override
	public XFTableOperator createTableOperator(String sqlText) {
		return new XFTableOperator(session_, processLog, sqlText);
	}

	@Override
	public XFTableOperator createTableOperator(String operation, String tableID) {
		XFTableOperator operator = null;
		try {
			operator = new XFTableOperator(session_, processLog, operation, tableID);
		} catch (Exception e) {
			e.printStackTrace(exceptionStream);
			errorHasOccured = true;
			closeFunction();
		}
		return operator;
	}

	@Override
	public XFTableEvaluator createTableEvaluator(String tableID) {
		return new XFTableEvaluator(this, tableID, session_);
	}

	@Override
	public Object getFieldObjectByID(String arg0, String arg1) {
		return null;
	}

	@Override
	public HashMap<String, Object> getParmMap() {
		return parmMap_;
	}

	@Override
	public StringBuffer getProcessLog() {
		return processLog;
	}

	@Override
	public HashMap<String, Object> getReturnMap() {
		return returnMap_;
	}

	@Override
	public PrintStream getExceptionStream() {
		return exceptionStream;
	}

	@Override
	public void rollback() {
		session_.commit(false, processLog);
	}

	@Override
	public void setProcessLog(String text) {
		XFUtility.appendLog(text, processLog);
	}

	@Override
	public void endProgress() {
	}

	@Override
	public HashMap<String, Object> execute(HashMap<String, Object> parmMap) {
		try {

			////////////////////////
			// Process parameters //
			////////////////////////
			parmMap_ = parmMap;
			if (parmMap_ == null) {
				parmMap_ = new HashMap<String, Object>();
			}
			returnMap_.clear();
			returnMap_.putAll(parmMap_);
			returnMap_.put("RETURN_CODE", "00"); //Default//
			returnMap_.put("RESULT", "OK"); //Default//

			/////////////////////////
			// Initialize variants //
			/////////////////////////
			//instanceIsAvailable_ = false;
			errorHasOccured = false;
			exceptionLog = new ByteArrayOutputStream();
			exceptionStream = new PrintStream(exceptionLog);
			exceptionHeader = "";
			processLog.delete(0, processLog.length());
			variantMap.clear();
			scriptEngine = session_.getScriptEngineManager().getEngineByName("js");
			engineScriptBindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
			
			///////////////////////////////////
			// Run Script and close function //
			///////////////////////////////////
			runScript();

		} catch(ScriptException e) {
			cancelWithScriptException(e, "");
		} catch (Exception e) {
			cancelWithException(e);
//		} finally {
//			instanceIsAvailable_ = true;
		}

		return returnMap_;
	}

	public Object getVariant(String variantID) {
		if (variantMap.containsKey(variantID)) {
			return variantMap.get(variantID);
		} else {
			return "";
		}
	}

	public void setVariant(String variantID, Object value) {
		variantMap.put(variantID, value);
	}

	public void runScript() throws ScriptException, Exception {

		/////////////////////////////////
		// Write log to start function //
		/////////////////////////////////
		programSequence = session_.writeLogOfFunctionStarted(functionElement_.getAttribute("ID"), functionElement_.getAttribute("Name"));

		engineScriptBindings.clear();
		engineScriptBindings.put("instance", (XFScriptable)this);
		engineScriptBindings.put("session", session_);
		String scriptText = XFUtility.substringLinesWithTokenOfEOL(functionElement_.getAttribute("Script"), "\n");
		if (!scriptText.equals("")) {
			StringBuffer bf = new StringBuffer();
			bf.append(scriptText);
			bf.append(session_.getScriptFunctions());
			scriptEngine.eval(bf.toString());
		}
		closeFunction();
	}

	void closeFunction() {
		/////////////////////////////////////////////////////////
		// Function type XF000 does not commit expressly. This //
		// means programmer is responsible to code committing. //
		/////////////////////////////////////////////////////////
	
		if (errorHasOccured) {
			returnMap_.put("RETURN_CODE", "99");
			this.rollback();
		}
		if (returnMap_.get("RETURN_MESSAGE") != null && !returnMap_.get("RETURN_MESSAGE").equals("")) {
			setProcessLog(returnMap_.get("RETURN_MESSAGE").toString());
		}
		//instanceIsAvailable_ = true;

		/////////////////////////////////
		// Write log to close function //
		/////////////////////////////////
		String errorLog = "";
		if (exceptionLog.size() > 0 || !exceptionHeader.equals("")) {
			errorLog = exceptionHeader + exceptionLog.toString();
		}
		String wrkStr = processLog.toString();
		session_.writeLogOfFunctionClosed(programSequence, returnMap_.get("RETURN_CODE").toString(), wrkStr, errorLog);
	}

	@Override
	public HashMap<String, Object> execute(Element arg0, HashMap<String, Object> arg1) {
		return null;
	}

	@Override
	public String getFunctionID() {
		return functionElement_.getAttribute("ID");
	}

	@Override
	public void incrementProgress() {
	}

	@Override
	public boolean isAvailable() {
		//return instanceIsAvailable_;
		return true;
	}

	@Override
	public void setFunctionSpecifications(Element arg0) throws Exception {
	}

	@Override
	public void startProgress(String arg0, int arg1) {
	}
}
