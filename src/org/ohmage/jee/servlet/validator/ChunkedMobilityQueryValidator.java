/*******************************************************************************
 * Copyright 2011 The Regents of the University of California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohmage.jee.servlet.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.ohmage.request.InputKeys;
import org.ohmage.util.CookieUtils;

/**
 * Validator for inbound data to the chunked mobility query.
 * 
 * @author selsky
 */
public class ChunkedMobilityQueryValidator extends AbstractHttpServletRequestValidator {
	private static Logger _logger = Logger.getLogger(MobilityQueryValidator.class);
	private List<String> _parameterList;
	
	public ChunkedMobilityQueryValidator() {
		_parameterList = new ArrayList<String>(Arrays.asList(new String[]{"end_date","start_date","user","client"}));
	}
	
	public boolean validate(HttpServletRequest httpRequest) throws MissingAuthTokenException {
		// Get the authentication / session token from the header.
		List<String> tokens = CookieUtils.getCookieValue(httpRequest.getCookies(), InputKeys.AUTH_TOKEN);
		if(tokens.size() == 0) {
			throw new MissingAuthTokenException("The required authentication / session token is missing.");
		}
		else if(tokens.size() > 1) {
			throw new MissingAuthTokenException("More than one authentication / session token was found in the request.");
		}
		
		Map<String,String[]> parameterMap = getParameterMap(httpRequest); 
		
		// Check for missing or extra parameters
		if(parameterMap.size() != _parameterList.size()) {				
			_logger.warn("an incorrect number of parameters was found for a mobility query: " + parameterMap.size());
			return false;
		}
		
		// Check for duplicate parameter values
		Iterator<?> iterator = parameterMap.keySet().iterator();
		
		while(iterator.hasNext()) {
			String key = (String) iterator.next();
			String[] valuesForKey = (String[]) parameterMap.get(key);
			
			if(valuesForKey.length != 1) {
				_logger.warn("an incorrect number of values (" + valuesForKey.length + ") was found for parameter " + key);
				return false;
			}
		}
		
		// Check for parameters with unknown names
		if(containsUnknownParameter(parameterMap, _parameterList)) {
			return false;
		}
		
		String startDate = (String) httpRequest.getParameter("start_date");
		String endDate = (String) httpRequest.getParameter("end_date");
		String user = (String) httpRequest.getParameter("user");
		String client = (String) httpRequest.getParameter("client");
		
		// Check for abnormal lengths (buffer overflow attack)
		
		if(greaterThanLength("startDate", "start_date", startDate, 10) 
		   || greaterThanLength("endDate", "end_date", endDate, 10)		
		   || greaterThanLength("client", "client",client, 250)
		   || greaterThanLength("userName", "user", user, 15)) {
			
			_logger.warn("found an input parameter that exceeds its allowed length");
			return false;
		}
		
		return true;
	}
}
