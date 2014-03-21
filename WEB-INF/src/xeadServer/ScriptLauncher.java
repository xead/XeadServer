package xeadServer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.w3c.dom.Element;
import xeadDriver.XFExecutable;
import xeadDriver.XFScriptable;
import xeadDriver.XFTableOperator;
import xeadDriver.XFUtility;

public class ScriptLauncher implements XFExecutable, XFScriptable {
	private static final long serialVersionUID = 1L;
	private org.w3c.dom.Element functionElement_ = null;
	private HashMap<String, Object> parmMap_ = null;
	private HashMap<String, Object> returnMap_ = new HashMap<String, Object>();
	private xeadDriver.Session session_ = null;
	private boolean instanceIsAvailable_ = true;
	private boolean errorHasOccured = false;
	private int programSequence;
	private StringBuffer processLog = new StringBuffer();
	private ScriptEngine scriptEngine;
	private Bindings engineScriptBindings;
	private ByteArrayOutputStream exceptionLog;
	private PrintStream exceptionStream;
	private String exceptionHeader = "";

	public ScriptLauncher(org.w3c.dom.Element functionElement, xeadDriver.Session session) {
		functionElement_ = functionElement;
		session_ = session;
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
		errorHasOccured = true;
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
			instanceIsAvailable_ = false;
			errorHasOccured = false;
			exceptionLog = new ByteArrayOutputStream();
			exceptionStream = new PrintStream(exceptionLog);
			exceptionHeader = "";
			processLog.delete(0, processLog.length());
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
		} finally {
			instanceIsAvailable_ = true;
		}

		return returnMap_;
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
		instanceIsAvailable_ = true;

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
		return instanceIsAvailable_;
	}

	@Override
	public void setFunctionSpecifications(Element arg0) throws Exception {
	}

	@Override
	public void startProgress(String arg0, int arg1) {
	}
}
