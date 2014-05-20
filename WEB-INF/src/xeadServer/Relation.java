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

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.swing.JOptionPane;

public class Relation implements Serializable {
	private static final long serialVersionUID = 1L;
	private ArrayList<String> fieldIDList = new ArrayList<String>();
	private ArrayList<ArrayList<Object>> fieldValueList = new ArrayList<ArrayList<Object>>();
	private int currentRowIndex;
	private String tableID_ = "";

	public Relation(ResultSet rset) {
		try {
			currentRowIndex = -1;
			ResultSetMetaData metaData = rset.getMetaData();
			//
			tableID_ = metaData.getTableName(1).toUpperCase();
			//
			int columnCount = metaData.getColumnCount();
			for (int i=0; i<columnCount; i++) {
				fieldIDList.add(metaData.getColumnName(i+1).toUpperCase());
			}
			//
			Object value;
			ArrayList<Object> valueList;
			int fieldIndex;
			while (rset.next()) {
				valueList = new ArrayList<Object>();
				fieldIndex = 1;
				while (fieldIndex <= columnCount) {
					value = rset.getObject(fieldIndex);
					valueList.add(value);
					fieldIndex++;
				}
				fieldValueList.add(valueList);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public boolean setRowIndex(int index) {
		currentRowIndex = index;
		if (fieldValueList.size() > 0
				&& currentRowIndex >= 0
				&& currentRowIndex < fieldValueList.size()) {
			return true;
		} else {
			currentRowIndex = -1;
			return false;
		}
	}
	
	public boolean next() {
		currentRowIndex++;
		if (fieldValueList.size() > 0
				&& currentRowIndex >= 0
				&& currentRowIndex < fieldValueList.size()) {
			return true;
		} else {
			currentRowIndex = -1;
			return false;
		}
	}
	
	public boolean previous() {
		currentRowIndex--;
		if (fieldValueList.size() > 0
				&& currentRowIndex >= 0
				&& currentRowIndex < fieldValueList.size()) {
			return true;
		} else {
			currentRowIndex = -1;
			return false;
		}
	}

	public Object getValueOf(String fieldID) throws Exception {
		Object value = null;
		if (fieldValueList.size() > 0
				&& currentRowIndex >= 0
				&& currentRowIndex < fieldValueList.size()) {
			ArrayList<Object> valueList = fieldValueList.get(currentRowIndex);
			int fieldIndex = fieldIDList.indexOf(fieldID);
			if (fieldIndex == -1) {
				String message = "Field not contained in the result set is required.\n" + tableID_ + ", " + fieldID;
				JOptionPane.showMessageDialog(null, message);
				throw new Exception(message);
			} else {
				value = valueList.get(fieldIndex);
			}
		}
		if (value == null) {
			value = "";
		}
		return value;
	}

	public boolean hasValueOf(String fieldID) {
		return fieldIDList.contains(fieldID);
	}

	public int getIndexOfFieldId(String fieldID) {
		return fieldIDList.indexOf(fieldID);
	}

	public String getFieldIdOfIndex(int index) {
		return fieldIDList.get(index);
	}

	public int getRowCount() {
		return fieldValueList.size();
	}

	public int getColumnCount() {
		return fieldIDList.size();
	}
}

//public class Relation implements Serializable {
//	private static final long serialVersionUID = 1L;
//	private ArrayList<String> fieldIDList = new ArrayList<String>();
//	private ArrayList<ArrayList<Object>> fieldValueList = new ArrayList<ArrayList<Object>>();
//	private int currentRowIndex;
//
//	public Relation(ResultSet rset) {
//		try {
//			currentRowIndex = -1;
//			ResultSetMetaData metaData = rset.getMetaData();
//			int columnCount = metaData.getColumnCount();
//			for (int i=0; i<columnCount; i++) {
//				fieldIDList.add(metaData.getColumnName(i+1));
//			}
//			Object value;
//			ArrayList<Object> valueList;
//			int fieldIndex;
//			int rowIndex = 0;
//			while (rset.next()) {
//				fieldIndex = 1;
//				valueList = new ArrayList<Object>();
//				while (fieldIndex <= columnCount) {
//					value = rset.getObject(fieldIndex);
//					valueList.add(value);
//					fieldIndex++;
//				}
//				fieldValueList.add(valueList);
//				rowIndex++;
//			}
//		} catch (SQLException e) {
//			e.printStackTrace();
//		}
//	}
//	
//	public boolean setRowIndex(int index) {
//		currentRowIndex = index;
//		if (fieldValueList.size() > 0
//				&& currentRowIndex >= 0
//				&& currentRowIndex < fieldValueList.size()) {
//			return true;
//		} else {
//			currentRowIndex = -1;
//			return false;
//		}
//	}
//	
//	public boolean next() {
//		currentRowIndex++;
//		if (fieldValueList.size() > 0
//				&& currentRowIndex >= 0
//				&& currentRowIndex < fieldValueList.size()) {
//			return true;
//		} else {
//			currentRowIndex = -1;
//			return false;
//		}
//	}
//	
//	public boolean previous() {
//		currentRowIndex--;
//		if (fieldValueList.size() > 0
//				&& currentRowIndex >= 0
//				&& currentRowIndex < fieldValueList.size()) {
//			return true;
//		} else {
//			currentRowIndex = -1;
//			return false;
//		}
//	}
//
//	public Object getObject(String fieldID) {
//		Object value = null;
//		if (fieldValueList.size() > 0
//				&& currentRowIndex >= 0
//				&& currentRowIndex < fieldValueList.size()) {
//			ArrayList<Object> valueList = fieldValueList.get(currentRowIndex);
//			int fieldIndex = fieldIDList.indexOf(fieldID);
//			if (fieldIndex != -1) {
//				value = valueList.get(fieldIndex);
//			}
//		}
//		return value;
//	}
//
//	public int getIndexOfFieldId(String fieldID) {
//		return fieldIDList.indexOf(fieldID);
//	}
//
//	public String getFieldIdOfIndex(int index) {
//		return fieldIDList.get(index);
//	}
//
//	public Object getObjectOfRow(int row, String fieldID) {
//		Object value = null;
//		if (row >= 0 && row < fieldValueList.size()) { 
//			ArrayList<Object> valueList = fieldValueList.get(row);
//			int fieldIndex = fieldIDList.indexOf(fieldID);
//			if (fieldIndex != -1) {
//				value = valueList.get(fieldIndex);
//			}
//		}
//		return value;
//	}
//
//	public int getRowCount() {
//		return fieldValueList.size();
//	}
//
//	public int getColumnCount() {
//		return fieldIDList.size();
//	}
//
//}
