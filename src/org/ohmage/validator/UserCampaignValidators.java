/*******************************************************************************
 * Copyright 2012 The Regents of the University of California
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
package org.ohmage.validator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.domain.campaign.Campaign;
import org.ohmage.exception.ValidationException;
import org.ohmage.request.InputKeys;
import org.ohmage.util.StringUtils;

/**
 * Class for validating username and campaign pair values.
 * 
 * @author John Jenkins
 */
public class UserCampaignValidators {
	private static final Logger LOGGER = Logger.getLogger(UserCampaignValidators.class);
	
	/**
	 * Default constructor. Private so that it cannot be instantiated.
	 */
	private UserCampaignValidators() {}
	
	/**
	 * Validates a String that should be a list of username, campaign role 
	 * pairs. Because a user can have more than one role in a campaign, the
	 * result is a map of usernames to a set of campaign roles. It is a set 
	 * instead of a list to prevent duplicate roles for the same user.
	 * 
	 * @param userAndCampaignRoleList A String representing a list of username
	 * 								  and campaign role pairs. The pairs should
	 * 								  be separated by 
	 * 								  {@value org.ohmage.request.InputKeys#LIST_ITEM_SEPARATOR}s
	 * 								  and the username and campaign role should
	 * 								  be separated by
	 * 								  {@value org.ohmage.request.InputKeys#ENTITY_ROLE_SEPARATOR}s.
	 * 
	 * @return A Map of usernames to a list of the campaign roles associated 
	 * 		   with them from the string list or null if the string is null,
	 * 		   whitespace only, or contains only separators and no meaningful
	 * 		   information.
	 * 
	 * @throws ValidationException Thrown if the list is invalid, any of the
	 * 							   pairs are invalid, or either the username or
	 * 							   class role are invalid.
	 */
	public static Map<String, Set<Campaign.Role>> validateUserAndCampaignRole(
			final String userAndCampaignRoleList) throws ValidationException {
		
		LOGGER.info("Validating a list of user and class role pairs.");
		
		// If it's null or empty, return null.
		if(StringUtils.isEmptyOrWhitespaceOnly(userAndCampaignRoleList)) {
			return null;
		}
		
		// Create the resulting object which will initially be empty.
		Map<String, Set<Campaign.Role>> result = new HashMap<String, Set<Campaign.Role>>();
		// Split the parameterized value into its pairs.
		String[] userAndRoleArray = userAndCampaignRoleList.split(InputKeys.LIST_ITEM_SEPARATOR);
		
		// For each of these pairs,
		for(int i = 0; i < userAndRoleArray.length; i++) {
			String currUserAndRole = userAndRoleArray[i].trim();
			
			// If the pair is empty, i.e. there were two list item separators
			// in a row, then skip it.
			if((! StringUtils.isEmptyOrWhitespaceOnly(currUserAndRole)) && 
					(! currUserAndRole.equals(InputKeys.ENTITY_ROLE_SEPARATOR))) {
				String[] userAndRole = currUserAndRole.split(InputKeys.ENTITY_ROLE_SEPARATOR);
				
				// If the pair isn't actually a pair, fail with an invalid 
				// campaign role error.
				if(userAndRole.length != 2) {
					throw new ValidationException(
							ErrorCode.CAMPAIGN_INVALID_ROLE, 
							"The user campaign-role list is invalid: " + 
								currUserAndRole);
				}
				
				// Validate the actual elements in the pair.
				String username = UserValidators.validateUsername(userAndRole[0].trim());
				if(username == null) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The username in the username, campaign role pair is missing: " + 
								currUserAndRole);
				}
				
				Campaign.Role role = CampaignValidators.validateRole(userAndRole[1].trim());
				if(role == null) {
					throw new ValidationException(
							ErrorCode.CAMPAIGN_INVALID_ROLE, 
							"The campaign role in the username, campaign role pair is missing: " + 
								currUserAndRole);
				}
				
				// Add the role to the list of roles.
				Set<Campaign.Role> roles = result.get(username);
				if(roles == null) {
					roles = new HashSet<Campaign.Role>();
					result.put(username, roles);
				}
				roles.add(role);
			}
		}
		
		// If the list is empty, return null.
		if(result.size() == 0) {
			return null;
		}
		else {
			return result;
		}
	}
}
