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
package org.ohmage.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import jbcrypt.BCrypt;
import net.tanesha.recaptcha.ReCaptchaImpl;
import net.tanesha.recaptcha.ReCaptchaResponse;

import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.cache.PreferenceCache;
import org.ohmage.domain.Clazz;
import org.ohmage.domain.User;
import org.ohmage.domain.UserInformation;
import org.ohmage.domain.UserInformation.UserPersonal;
import org.ohmage.domain.UserSummary;
import org.ohmage.domain.campaign.Campaign;
import org.ohmage.exception.CacheMissException;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.DomainException;
import org.ohmage.exception.ServiceException;
import org.ohmage.query.IImageQueries;
import org.ohmage.query.IUserCampaignQueries;
import org.ohmage.query.IUserClassQueries;
import org.ohmage.query.IUserImageQueries;
import org.ohmage.query.IUserQueries;
import org.ohmage.query.impl.QueryResultsList;
import org.ohmage.util.StringUtils;

import com.sun.mail.smtp.SMTPTransport;

/**
 * This class contains the services for users.
 * 
 * @author John Jenkins
 */
public final class UserServices {
	private static final String MAIL_PROTOCOL = "smtp";
	private static final String MAIL_PROPERTY_HOST = 
			"mail." + MAIL_PROTOCOL + ".host";
	private static final String MAIL_PROPERTY_AUTH =
			"mail." + MAIL_PROTOCOL + ".auth";
	private static final String MAIL_PROPERTY_USERNAME = 
			"mail." + MAIL_PROTOCOL + ".username";
	private static final String MAIL_PROPERTY_PASSWORD =
			"mail." + MAIL_PROTOCOL + ".password";
	private static final String MAIL_PROPERTY_REGISTRATION_ADDRESS_FROM =
			"mail.registration.address.from";
	private static final String MAIL_PROPERTY_REGISTRATION_SUBJECT =
			"mail.registration.subject";
	private static final String MAIL_PROPERTY_REGISTRATION_TEXT =
			"mail.registration.text";
	
	private static final String MAIL_REGISTRATION_TEXT_TOS = "<_TOS_>";
	private static final String MAIL_REGISTRATION_TEXT_REGISTRATION_ID =
			"<_REGISTRATION_ID_>";
	
	private static final long REGISTRATION_DURATION = 1000 * 60 * 60 * 4;
	
	private static UserServices instance;
	private static Session smtpSession;
	
	private IUserQueries userQueries;
	private IUserCampaignQueries userCampaignQueries;
	private IUserClassQueries userClassQueries;
	private IUserImageQueries userImageQueries;
	private IImageQueries imageQueries;
	
	/**
	 * Default constructor. Privately instantiated via dependency injection
	 * (reflection).
	 * 
	 * @throws IllegalStateException if an instance of this class already
	 * exists
	 * 
	 * @throws IllegalArgumentException if iUserQueries or iUserClassQueries
	 * or iUserCampaignQueries is null
	 */
	private UserServices(IUserQueries iUserQueries, 
			IUserCampaignQueries iUserCampaignQueries, IUserClassQueries iUserClassQueries,
			IUserImageQueries iUserImageQueries, IImageQueries iImageQueries) {
		
		if(instance != null) {
			throw new IllegalStateException("An instance of this class already exists.");
		}
		instance = this;
		
		if(iUserQueries == null) {
			throw new IllegalArgumentException("An instance of IUserQueries is required.");
		}
		if(iUserCampaignQueries == null) {
			throw new IllegalArgumentException("An instance of IUserCampaignQueries is required.");
		}
		if(iUserClassQueries == null) {
			throw new IllegalArgumentException("An instance of IUserClassQueries is required.");
		}
		if(iUserImageQueries == null) {
			throw new IllegalArgumentException("An instance of IUserImageQueries is required.");
		}
		if(iImageQueries == null) {
			throw new IllegalArgumentException("An instance of IIimageQueries is required.");
		}
		
		userQueries = iUserQueries;
		userCampaignQueries = iUserCampaignQueries;
		userClassQueries = iUserClassQueries;
		userImageQueries = iUserImageQueries;
		imageQueries = iImageQueries;
		
		Properties sessionProperties = new Properties();
		try {
			sessionProperties.load(
					new FileInputStream(
							System.getProperty("webapp.root") + 
							"/WEB-INF/properties/mail.smtp.properties"));
		} 
		catch(FileNotFoundException e) {
			throw new IllegalStateException(
					"The JavaMail properties file is missing.", 
					e);
		} 
		catch(IOException e) {
			throw new IllegalStateException(
					"Error reading the JavaMail properties file.", 
					e);
		}
		smtpSession = Session.getDefaultInstance(sessionProperties);
	}
	
	/**
	 * @return  Returns the singleton instance of this class.
	 */
	public static UserServices instance() {
		return instance;
	}

	
	/**
	 * Creates a new user.
	 * 
	 * @param username The username for the new user.
	 * 
	 * @param password The password for the new user.
	 * 
	 * @param emailAddress The user's email address or null.
	 * 
	 * @param admin Whether or not the user should initially be an admin.
	 * 
	 * @param enabled Whether or not the user should initially be enabled.
	 * 
	 * @param newAccount Whether or not the new user must change their password
	 * 					 before using any other APIs.
	 * 
	 * @param campaignCreationPrivilege Whether or not the new user is allowed
	 * 									to create campaigns.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void createUser(
			final String username, 
			final String password, 
			final String emailAddress,
			final Boolean admin, 
			final Boolean enabled, 
			final Boolean newAccount, 
			final Boolean campaignCreationPrivilege)
			throws ServiceException {
		
		try {
			String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			
			userQueries.createUser(username, hashedPassword, emailAddress, admin, enabled, newAccount, campaignCreationPrivilege);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Registers the user in the system by first creating the user whose 
	 * account is disabled. It then creates an entry in the registration cache
	 * with the key for the user to activate their account. Finally, it sends 
	 * an email to the user with a link that includes the activation key.
	 * 
	 * @param username The username of the new user.
	 * 
	 * @param password The plain-text password for the new user.
	 * 
	 * @param emailAddress The email address for the user.
	 * 
	 * @throws ServiceException There was a configuration issue with the mail
	 * 							server or if there was an issue in the Query
	 * 							layer.
	 */
	public void createUserRegistration(
			final String username,
			final String password,
			final String emailAddress) 
			throws ServiceException {
		
		try {
			// Generate a random registration ID from the username and some 
			// random bits.
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			digest.update(username.getBytes());
			digest.update(UUID.randomUUID().toString().getBytes());
			byte[] digestBytes = digest.digest();
			
			StringBuffer buffer = new StringBuffer();
	        for(int i = 0; i < digestBytes.length; i++) {
	        	buffer.append(
	        			Integer.toString(
	        					(digestBytes[i] & 0xff) + 0x100, 16)
	        						.substring(1));
	        }
			String registrationId = buffer.toString();
			
			// Hash the password.
			String hashedPassword = 
					BCrypt.hashpw(
							password, 
							BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			
			// Create the user in the database with all of its connections.
			userQueries.createUserRegistration(
					username, 
					hashedPassword, 
					emailAddress, 
					registrationId.toString());
			
			// Send an email to the user to confirm their email.
			try {
				SMTPTransport transport = 
						(SMTPTransport) smtpSession.getTransport(
								MAIL_PROTOCOL);

				Boolean auth = 
						StringUtils.decodeBoolean(
								smtpSession.getProperty(MAIL_PROPERTY_AUTH));
				if((auth != null) && auth) {
					transport.connect(
							smtpSession.getProperty(MAIL_PROPERTY_HOST), 
							smtpSession.getProperty(MAIL_PROPERTY_USERNAME), 
							smtpSession.getProperty(MAIL_PROPERTY_PASSWORD));
				}
				else {
					transport.connect();
				}
				
				MimeMessage message = new MimeMessage(smtpSession);
				
				// Add the recipient.
				message.setRecipient(
						Message.RecipientType.TO, 
						new InternetAddress(emailAddress));
				
				// Add the sender.
				message.setFrom(
						new InternetAddress(
								smtpSession.getProperty(
										MAIL_PROPERTY_REGISTRATION_ADDRESS_FROM)));
				
				// Set the subject.
				message.setSubject(
						smtpSession.getProperty(
								MAIL_PROPERTY_REGISTRATION_SUBJECT));
				
				String registrationText =
						smtpSession.getProperty(
								MAIL_PROPERTY_REGISTRATION_TEXT);
				
				registrationText =
						registrationText.replace(
								MAIL_REGISTRATION_TEXT_TOS, 
								"Terms of Service");
				
				registrationText =
						registrationText.replace(
								MAIL_REGISTRATION_TEXT_REGISTRATION_ID, 
								registrationId.toString());
				
				message.setText(registrationText);
				
				transport.sendMessage(message, message.getAllRecipients());
			}
			catch(NoSuchProviderException e) {
				throw new ServiceException(
						"There is no provider for SMTP. " +
							"This means the library has changed as it has built-in support for SMTP.",
						e);
			}
			catch(AuthenticationFailedException e) {
				throw new ServiceException(
						"The mail credentials were incorrect.",
						e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"There was an error while connecting to the mail server or sending the message.",
						e);
			}
			catch(IllegalStateException e) {
				throw new ServiceException(
						"The transport is already connected, which should never be the case.",
						e);
			}
		}
		catch(NoSuchAlgorithmException e) {
			throw new ServiceException("The hashing algorithm is unknown.", e);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		} 
	}
	
	/**
	 * Verifies that the given captcha information is valid.
	 * 
	 * @param remoteAddr The address of the remote host.
	 * 
	 * @param challenge The challenge value.
	 * 
	 * @param response The response value.
	 * 
	 * @throws ServiceException Thrown if the private key is missing or if the
	 * 							response is invalid.
	 */
	public void verifyCaptcha(
			final String remoteAddr,
			final String challenge,
			final String response)
			throws ServiceException {
		
		ReCaptchaImpl reCaptcha = new ReCaptchaImpl();
		try {
			reCaptcha.setPrivateKey(
					PreferenceCache.instance().lookup(
							PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE));
		}
		catch(CacheMissException e) {
			throw new ServiceException(
					"The ReCaptcha key is missing from the preferences: " +
						PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE,
					e);
		}
		
		ReCaptchaResponse reCaptchaResponse = 
				reCaptcha.checkAnswer(remoteAddr, challenge, response);
		
		if(! reCaptchaResponse.isValid()) {
			throw new ServiceException(
					ErrorCode.SERVER_INVALID_CAPTCHA,
					"The reCaptcha response was invalid.");
		}
	}
	
	/**
	 * Checks that a user's existence matches that of 'shouldExist'.
	 * 
	 * @param username The username of the user in question.
	 * 
	 * @param shouldExist Whether or not the user should exist.
	 * 
	 * @throws ServiceException Thrown if there was an error, if the user 
	 * 							exists but shouldn't, or if the user doesn't
	 * 							exist but should.
	 */
	public void checkUserExistance(final String username, 
			final boolean shouldExist) throws ServiceException {
		
		try {
			if(userQueries.userExists(username)) {
				if(! shouldExist) {
					throw new ServiceException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The following user already exists: " + username);
				}
			}
			else {
				if(shouldExist) {
					throw new ServiceException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The following user does not exist: " + username);
				}
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Checks that a Collection of users' existence matches that of 
	 * 'shouldExist'.
	 * 
	 * @param usernames A Collection of usernames to check that each exists.
	 * 
	 * @param shouldExist Whether or not all of the users should exist or not.
	 * 
	 * @throws ServiceException Thrown if there was an error, if one of the 
	 * 							users should have existed and didn't, or if one 
	 * 							of the users shouldn't exist but does.
	 */
	public void verifyUsersExist(final Collection<String> usernames, 
			final boolean shouldExist) throws ServiceException {
		
		for(String username : usernames) {
			checkUserExistance(username, shouldExist);
		}
	}
	
	/**
	 * Checks if the user is an admin.
	 * 
	 * @return Returns true if the user is an admin; false if not or there is
	 * 		   an error.
	 * 
	 * @throws ServiceException Thrown if there was an error or if the user is
	 * 							not an admin.
	 */
	public void verifyUserIsAdmin(final String username) 
			throws ServiceException {
		
		try {
			if(! userQueries.userIsAdmin(username)) {
				throw new ServiceException(
						ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
						"The user is not an admin."
					);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that the user can create campaigns.
	 * 
	 * @param username The username of the user whose campaign creation ability
	 * 				   is being checked.
	 * 
	 * @throws ServiceException Thrown if there is an error or if the user is
	 * 							not allowed to create campaigns.
	 */
	public void verifyUserCanCreateCampaigns(final String username) 
			throws ServiceException {
		
		try {
			if(! userQueries.userCanCreateCampaigns(username)) {
				throw new ServiceException(
						ErrorCode.CAMPAIGN_INSUFFICIENT_PERMISSIONS, 
						"The user does not have permission to create new campaigns.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Validates that a registration ID still exists, hasn't been used, and 
	 * hasn't expired.
	 * 
	 * @param registrationId The registration's unique identifier.
	 * 
	 * @throws ServiceException The registration doesn't exist or is invalid or
	 * 							there was an error.
	 */
	public void validateRegistrationId(
			final String registrationId) 
			throws ServiceException {
		
		try {
			if(! userQueries.registrationIdExists(registrationId)) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"No such registration ID exists.");
			}
			
			if(userQueries.getRegistrationAcceptedDate(registrationId) != null) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"This registration ID has already been used to activate an account.");
			}
			
			long earliestTime = (new Date()).getTime() - REGISTRATION_DURATION;
			
			if(userQueries.getRegistrationRequestedDate(registrationId).getTime() < earliestTime) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"The registration ID has expired.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that a given user is allowed to read the personal information
	 * about a group of users.
	 * 
	 * @param username The username of the reader.
	 * 
	 * @param usernames The usernames of the readees.
	 * 
	 * @throws ServiceException There was an error or the user is not allowed 
	 * 							to read the personal information of one or more
	 * 							of the users.
	 */
	public void verifyUserCanReadUsersPersonalInfo(
			final String username, final Collection<String> usernames) 
			throws ServiceException {
		
		if((usernames == null) || (usernames.size() == 0) ||
				((usernames.size() == 1) && 
				 usernames.iterator().next().equals(username))) {
			return;
		}
		
		Set<String> supervisorCampaigns = 
			UserCampaignServices.instance().getCampaignsForUser(username, 
					null, null, null, null, null, null, 
					Campaign.Role.SUPERVISOR);
		
		Set<String> privilegedClasses = 
			UserClassServices.instance().getClassesForUser(
					username, 
					Clazz.Role.PRIVILEGED);
		
		for(String currUsername : usernames) {
			if(UserCampaignServices.instance().getCampaignsForUser( 
					currUsername, supervisorCampaigns, privilegedClasses, 
					null, null, null, null, null).size() == 0) {
				
				throw new ServiceException(
						ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
						"The user is not allowed to view personal information about a user in the list: " + 
							currUsername);
			}
		}
	}

	/**
	 * Verifies that if the user already has personal information in which it 
	 * is acceptable to update any combination of the pieces or that they 
	 * supplied all necessary pieces to update the information.
	 * 
	 * @param username The username of the user whose personal information is
	 * 				   being queried.
	 * 
	 * @param firstName The new first name of the user or null if the first 
	 * 					name is not being updated.
	 * 
	 * @param lastName The new last name of the user or null if the last name 
	 * 				   is not being updated.
	 * 
	 * @param organization The new organization of the user or null if the
	 * 					   organization is not being updated.
	 * 
	 * @param personalId The new personal ID of the user or null if the 
	 * 					 personal ID is not being updated.
	 * 
	 * @throws ServiceException The user doesn't have personal information in
	 * 							the system and is attempting to update some 
	 * 							fields but not all of them. If the user doesn't
	 * 							have personal information already, they must
	 * 							create a new one with all of the information. 
	 * 							Or there was an error.
	 */
	public void verifyUserHasOrCanCreatePersonalInfo(
			final String username, 
			final String firstName,
			final String lastName,
			final String organization,
			final String personalId) 
			throws ServiceException {
		
		// If the user already has personal information, then they are allowed
		// to edit it as they wish.
		try {
			if(userQueries.userHasPersonalInfo(username)) {
				return;
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// If they are all null and the user isn't trying to update the 
		// personal information, then that is fine.
		if((firstName == null) &&
				(lastName == null) &&
				(organization == null) &&
				(personalId == null)) {
			
			return;
		}
		
		if(firstName == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_FIRST_NAME_VALUE, 
					"The user doesn't have personal information yet, and a first name is necessary to create one.");
		}
		else if(lastName == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_LAST_NAME_VALUE, 
					"The user doesn't have personal information yet, and a last name is necessary to create one.");
		}
		else if(organization == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_ORGANIZATION_VALUE, 
					"The user doesn't have personal information yet, and an organization is necessary to create one.");
		}
		else if(personalId == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_PERSONAL_ID_VALUE, 
					"The user doesn't have personal information yet, and a personal ID is necessary to create one.");
		}
	}
	
	/**
	 * Searches through all of the users in the system and returns those that
	 * match the criteria. All Object parameters are optional except
	 * 'requesterUsername'; by passing a null value, it will be omitted from 
	 * the search.
	 * 
	 * @param requesterUsername The username of the user making this request.
	 * 
	 * @param usernames Limits the results to only those whose username is in
	 * 					this list.
	 * 
	 * @param emailAddress Limits the results to only those users whose email
	 * 					   address matches this value.
	 * 
	 * @param admin Limits the results to only those users whose admin value
	 * 				matches this value.
	 * 
	 * @param enabled Limits the results to only those user whose enabled value
	 * 				  matches this value.
	 * 
	 * @param newAccount Limits the results to only those users whose new 
	 * 					 account value matches this value.
	 * 
	 * @param campaignCreationPrivilege Limits the results to only those 
	 * 									users whose campaign creation privilege
	 * 									matches this value.
	 * 
	 * @param firstName Limits the results to only those that have personal 
	 * 					information and their first name equals this value.
	 * 
	 * @param partialLastName Limits the results to only those users that have 
	 * 						  personal information and their last name matches 
	 * 						  this value.
	 * 
	 * @param partialOrganization Limits the results to only those users that 
	 * 							  have personal information and their 
	 * 							  organization value matches this value.
	 * 
	 * @param partialPersonalId Limits the results to only those users that 
	 * 							have personal information and their personal ID
	 * 							matches this value.
	 * 
	 * @param numToSkip The number of results to skip.
	 * 
	 * @param numToReturn The number of results to return.
	 * 
	 * @param results The user information for the users that matched the
	 * 				  criteria.
	 * 
	 * @return The number of usernames that matched the given criteria.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public long getUserInformation(
			final String requesterUsername,
			final Collection<String> usernames,
			final String emailAddress,
			final Boolean admin,
			final Boolean enabled,
			final Boolean newAccount,
			final Boolean canCreateCampaigns,
			final String firstName,
			final String lastName,
			final String organization,
			final String personalId,
			final Collection<String> campaignIds,
			final Collection<String> classIds,
			final long numToSkip,
			final long numToReturn,
			final List<UserInformation> results) 
			throws ServiceException {
		
		try {
			QueryResultsList<UserInformation> result =
					userQueries.getUserInformation(
							requesterUsername,
							usernames, 
							null, 
							emailAddress, 
							admin, 
							enabled, 
							newAccount, 
							canCreateCampaigns, 
							firstName, 
							lastName, 
							organization, 
							personalId,
							campaignIds,
							classIds,
							false, 
							numToSkip, 
							numToReturn);
			
			results.addAll(result.getResults());
			
			return result.getTotalNumResults();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Searches through all of the users in the system and returns those that
	 * match the criteria. All Object parameters are optional except 
	 * 'requesterUsername'; by passing a null value, it will be omitted from 
	 * the search. 
	 * 
	 * @param requesterUsername The username of the user making this request.
	 * 
	 * @param partialUsername Limits the results to only those users whose 
	 * 						  username contain this value.
	 * 
	 * @param partialEmailAddress Limits the results to only those users whose
	 * 							  email address contains this value.
	 * 
	 * @param admin Limits the results to only those usernames that belong to
	 * 				users whose admin value matches this one.
	 * 
	 * @param enabled Limits the results to only those usernames that belong to
	 * 				  users whose enabled value matches this one.
	 * 
	 * @param newAccount Limits the results to only those usernames that belong
	 * 					 to users whose new account value matches this one.
	 * 
	 * @param campaignCreationPrivilege Limits the results to only those 
	 * 									usernames that belong to users whose	
	 * 									campaign creation privilege matches 
	 * 									this one.
	 * 
	 * @param partialFirstName Limits the results to only those usernames that
	 * 						   belong to users that have personal information
	 * 						   and their first name contains this value.
	 * 
	 * @param partialLastName Limits the results to only those usernames that
	 * 						  belong to users that have personal information 
	 * 						  and their last name contains this value.
	 * 
	 * @param partialOrganization Limits the results to only those usernames
	 * 							  that belong to users that have personal 
	 * 							  information and their organization value 
	 * 							  contains this value.
	 * 
	 * @param partialPersonalId Limits the results to only those usernames that
	 * 							belong to users that have personal information
	 * 							and their personal ID contains this value.
	 * 
	 * @param numToSkip The number of results to skip.
	 * 
	 * @param numToReturn The number of results to return.
	 * 
	 * @param results The user information for the users that matched the
	 * 				  criteria. This cannot be null and will be populated with
	 * 				  the results.
	 * 
	 * @return The number of usernames that matched the given criteria.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public long userSearch(
			final String requesterUsername,
			final String partialUsername,
			final String partialEmailAddress,
			final Boolean admin,
			final Boolean enabled,
			final Boolean newAccount,
			final Boolean campaignCreationPrivilege,
			final String partialFirstName,
			final String partialLastName,
			final String partialOrganization,
			final String partialPersonalId,
			final int numToSkip,
			final int numToReturn,
			final Collection<UserInformation> results)
			throws ServiceException {
		
		try {
			QueryResultsList<UserInformation> result =
					userQueries.getUserInformation(
							requesterUsername,
							null, 
							partialUsername, 
							partialEmailAddress, 
							admin, 
							enabled, 
							newAccount, 
							campaignCreationPrivilege, 
							partialFirstName, 
							partialLastName, 
							partialOrganization, 
							partialPersonalId, 
							null,
							null,
							true, 
							numToSkip, 
							numToReturn);
			
			try {
				for(UserInformation currResult : result.getResults()) {
					currResult.addCampaigns(
							userCampaignQueries.getCampaignAndRolesForUser(
									currResult.getUsername()));
				
					currResult.addClasses(
							userClassQueries.getClassAndRoleForUser(
									currResult.getUsername()));
				}
			}
			catch(DomainException e) {
				throw new ServiceException(e);
			}
			
			results.addAll(result.getResults());

			return result.getTotalNumResults();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Gathers the summary about a user.
	 * 
	 * @param username The username of the user whose summary is being 
	 * 				   requested.
	 * 
	 * @return Returns a UserSummary object that contains the necessary
	 * 		   information about a user.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public UserSummary getUserSummary(final String username)
			throws ServiceException {
		
		try {
			// Get the campaigns and their names for the requester.
			Map<String, String> campaigns = userCampaignQueries.getCampaignIdsAndNameForUser(username);
						
			// Get the requester's campaign roles for each of the campaigns.
			Set<Campaign.Role> campaignRoles = new HashSet<Campaign.Role>();
			for(String campaignId : campaigns.keySet()) {
				campaignRoles.addAll(
						userCampaignQueries.getUserCampaignRoles(
								username, 
								campaignId));
			}

			// Get the classes and their names for the requester.
			Map<String, String> classes = userClassQueries.getClassIdsAndNameForUser(username);
			
			// Get the requester's class roles for each of the classes.
			Set<Clazz.Role> classRoles = new HashSet<Clazz.Role>();
			for(String classId : classes.keySet()) {
				classRoles.add(
						userClassQueries.getUserClassRole(classId, username));
			}
			
			// Get campaign creation privilege.
			try {
				return new UserSummary(
						userQueries.userIsAdmin(username), 
						userQueries.userCanCreateCampaigns(username),
						campaigns,
						campaignRoles,
						classes,
						classRoles);
			} 
			catch(DomainException e) {
				throw new ServiceException(e);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the personal information for all of the users in the list.
	 * 
	 * @param usernames The usernames.
	 * 
	 * @return A map of usernames to personal information or null if no 
	 * 		   personal information is available.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public Map<String, UserPersonal> gatherPersonalInformation(
			final Collection<String> usernames) throws ServiceException {
		
		try {
			Map<String, UserPersonal> result = 
				new HashMap<String, UserPersonal>(usernames.size());
			
			for(String username : usernames) {
				result.put(username, userQueries.getPersonalInfoForUser(username));
			}
			
			return result;
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Updates a user's account information.
	 * 
	 * @param username The username of the user whose information is to be
	 * 				   updated.
	 * 
	 * @param emailAddress The new email address for the user. A null value 
	 * 					   indicates that this field should not be updated.
	 * 
	 * @param admin Whether or not the user should be an admin. A null value
	 * 			    indicates that this field should not be updated.
	 * 
	 * @param enabled Whether or not the user's account should be enabled. A
	 * 				  null value indicates that this field should not be
	 * 				  updated.
	 * 
	 * @param newAccount Whether or not the user should be required to change
	 * 					 their password. A null value indicates that this field
	 * 					 should not be updated.
	 * 
	 * @param campaignCreationPrivilege Whether or not the user should be 
	 * 									allowed to create campaigns. A null
	 * 									Value indicates that this field should
	 * 									not be updated.
	 * 
	 * @param firstName The user's new first name. A null value indicates that
	 * 					this field should not be updated.
	 * 
	 * @param lastName The users's last name. A null value indicates that this
	 * 				   field should not be updated.
	 * 
	 * @param organization The user's new organization. A null value indicates
	 * 					   that this field should not be updated.
	 * 
	 * @param personalId The user's new personal ID. A null value indicates 
	 * 					 that this field should not be updated.
	 * 
	 * @param deletePersonalInfo Whether or not to delete the user's personal
	 * 							 information.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void updateUser(
			final String username, 
			final String emailAddress,
			final Boolean admin, 
			final Boolean enabled, 
			final Boolean newAccount, 
			final Boolean campaignCreationPrivilege, 
			final String firstName,
			final String lastName,
			final String organization,
			final String personalId,
			final boolean deletePersonalInfo) 
			throws ServiceException {
		
		try {
			userQueries.updateUser(
					username, 
					emailAddress,
					admin, 
					enabled, 
					newAccount, 
					campaignCreationPrivilege,
					firstName,
					lastName,
					organization,
					personalId,
					deletePersonalInfo);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Updates the user's password.
	 * 
	 * @param username The username of the user whose password is being 
	 * 				   updated.
	 * 
	 * @param plaintextPassword The plaintext password for the user.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void updatePassword(final String username, 
			final String plaintextPassword) throws ServiceException {
		
		try {
			String hashedPassword = BCrypt.hashpw(plaintextPassword, BCrypt.gensalt(13));
			
			userQueries.updateUserPassword(username, hashedPassword);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Activates a user's account by updating the enabled status to true and
	 * updates the registration table's entry.
	 * 
	 * @param registrationId The registration's unique identifier.
	 * 
	 * @throws DataAccessException There was an error.
	 */
	public void activateUser(
			final String registrationId)
			throws ServiceException {
		
		try {
			userQueries.activateUser(registrationId);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Deletes all of the users from the Collection.
	 * 
	 * @param usernames A Collection of usernames of the users to delete.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void deleteUser(final Collection<String> usernames) 
			throws ServiceException {
		// First, retrieve the path information for all of the images 
		// associated with each user.
		Collection<URL> imageUrls = new HashSet<URL>();
		try {
			for(String username : usernames) {
				imageUrls.addAll(
					userImageQueries.getImageUrlsFromUsername(username));
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
				
		try {
			userQueries.deleteUsers(usernames);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// If the transaction succeeded, delete all of the images from the 
		// disk.
		for(URL imageUrl : imageUrls) {
			imageQueries.deleteImageDiskOnly(imageUrl);
		}
	}
}
