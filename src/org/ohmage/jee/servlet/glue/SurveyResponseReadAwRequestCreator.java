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
package org.ohmage.jee.servlet.glue;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.NDC;
import org.ohmage.request.AwRequest;
import org.ohmage.request.InputKeys;
import org.ohmage.request.SurveyResponseReadAwRequest;
import org.ohmage.util.CookieUtils;


/**
 * Builds an AwRequest for /app/survey_response/read.
 * 
 * @author selsky
 */
public class SurveyResponseReadAwRequestCreator implements AwRequestCreator {
	
	public SurveyResponseReadAwRequestCreator() {
		
	}
	
	public AwRequest createFrom(HttpServletRequest httpRequest) {

		String startDate = httpRequest.getParameter("start_date");
		String endDate = httpRequest.getParameter("end_date");
		String userList = httpRequest.getParameter("user_list");
		String client = httpRequest.getParameter("client");
		String campaignUrn = httpRequest.getParameter("campaign_urn");
		String token = CookieUtils.getCookieValue(httpRequest.getCookies(), InputKeys.AUTH_TOKEN).get(0);
		String promptIdList = httpRequest.getParameter("prompt_id_list");
		String surveyIdList = httpRequest.getParameter("survey_id_list");
		String columnList = httpRequest.getParameter("column_list");
		String outputFormat = httpRequest.getParameter("output_format");
		String prettyPrint = httpRequest.getParameter("pretty_print");
		String suppressMetadata = httpRequest.getParameter("suppress_metadata");
		String privacyState = httpRequest.getParameter("privacy_state");
		String returnId = httpRequest.getParameter("return_id");
		String sortOrder = httpRequest.getParameter("sort_order");
		
		SurveyResponseReadAwRequest awRequest = new SurveyResponseReadAwRequest();
		
		awRequest.setStartDate(startDate);
		awRequest.setEndDate(endDate);
		awRequest.setUserToken(token);
		awRequest.setClient(client);
		awRequest.setCampaignUrn(campaignUrn);
		awRequest.setUserListString(userList);
		awRequest.setPromptIdListString(promptIdList);
		awRequest.setSurveyIdListString(surveyIdList);
		awRequest.setColumnListString(columnList);
		awRequest.setOutputFormat(outputFormat);
		awRequest.setPrettyPrintAsString(prettyPrint);
		awRequest.setSuppressMetadataAsString(suppressMetadata);
		awRequest.setReturnIdAsString(returnId);
		awRequest.setSortOrder(sortOrder);
		awRequest.setPrivacyState(privacyState);
		
		// temporarily using this frankenstein approach before migrating completely to toValidate()
		awRequest.addToValidate(InputKeys.SUPPRESS_METADATA, suppressMetadata, true);
		awRequest.addToValidate(InputKeys.PRETTY_PRINT, prettyPrint, true);
		awRequest.addToValidate(InputKeys.RETURN_ID, returnId, true);
		awRequest.addToValidate(InputKeys.SORT_ORDER, sortOrder, true);
		awRequest.addToValidate(InputKeys.USER_LIST, userList, true);
		
		
        NDC.push("client=" + client); // push the client string into the Log4J NDC for the currently executing thread _ this means that 
                                     // it will be in every log message for the current thread
		return awRequest;
	}
}
