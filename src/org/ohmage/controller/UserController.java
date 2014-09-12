package org.ohmage.controller;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.ohmage.auth.provider.Provider;
import org.ohmage.auth.provider.ProviderRegistry;
import org.ohmage.bin.MultiValueResult;
import org.ohmage.bin.OhmletBin;
import org.ohmage.bin.OhmletInvitationBin;
import org.ohmage.bin.StreamBin;
import org.ohmage.bin.SurveyBin;
import org.ohmage.bin.UserBin;
import org.ohmage.bin.UserInvitationBin;
import org.ohmage.domain.auth.AuthorizationToken;
import org.ohmage.domain.exception.AuthenticationException;
import org.ohmage.domain.exception.InsufficientPermissionsException;
import org.ohmage.domain.exception.InvalidArgumentException;
import org.ohmage.domain.exception.UnknownEntityException;
import org.ohmage.domain.ohmlet.Ohmlet;
import org.ohmage.domain.ohmlet.Ohmlet.SchemaReference;
import org.ohmage.domain.ohmlet.OhmletInvitation;
import org.ohmage.domain.ohmlet.OhmletReference;
import org.ohmage.domain.ohmlet.OhmletReferenceView;
import org.ohmage.domain.stream.Stream;
import org.ohmage.domain.survey.Survey;
import org.ohmage.domain.user.ProviderUserInformation;
import org.ohmage.domain.user.Registration;
import org.ohmage.domain.user.User;
import org.ohmage.domain.user.UserInvitation;
import org.ohmage.javax.servlet.filter.AuthFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * <p>
 * The controller for all requests to the list of users.
 * </p>
 *
 * @author John Jenkins
 */
@Controller
@RequestMapping(UserController.ROOT_MAPPING)
public class UserController extends OhmageController {
	/**
	 * The root API mapping for this Servlet.
	 */
	public static final String ROOT_MAPPING = "/people";

	/**
	 * The path key for the user's unique identifier.
	 */
	public static final String KEY_USER_ID = "user_id";

	/**
	 * The parameter key for provider identifiers.
	 */
	public static final String PARAMETER_PROVIDER = "provider";
	/**
	 * The parameter key for provider access tokens.
	 */
	public static final String PARAMETER_ACCESS_TOKEN = "access_token";

	/**
	 * The parameter key for the Captcha challenge.
	 */
	public static final String PARAMETER_CAPTCHA_CHALLENGE =
		"captcha_challenge";
	/**
	 * The parameter key for the Captcha response.
	 */
	public static final String PARAMETER_CAPTCHA_RESPONSE = "captcha_response";

	/**
	 * The preference key for the Captcha private key.
	 */
	public static final String PREFERENCE_KEY_CAPTCHA_PRIVATE_KEY =
		"ohmage.captcha_private_key";

	/**
	 * The logger for this class.
	 */
	private static final Logger LOGGER =
		LoggerFactory.getLogger(UserController.class.getName());

    /**
     * Creates a new user.
     *
     * @param rootUrl
     *        The root URL of the request that can be used to formulate an
     *        activation link in the validation email.
     *
     * @param password
     *        The new user's plain-text password.
     *
     * @param userInvitationId
     *        An optional user invitation ID that can be used bypass the email
     *        validation.
     *
     * @param userBuilder
     *        The user's information.
     *
     * @return The validated User object as it was saved in the database.
     */
	@RequestMapping(
		value = { "", "/" },
		method = RequestMethod.POST,
		params = { User.JSON_KEY_PASSWORD })
	public static @ResponseBody User createOhmageUser(
	    @ModelAttribute(ATTRIBUTE_REQUEST_URL_ROOT) final String rootUrl,
		/*
		@RequestParam(
			value = PARAMETER_CAPTCHA_CHALLENGE,
			required = true)
			final String captchaChallenge,
		@RequestParam(
			value = PARAMETER_CAPTCHA_RESPONSE,
			required = true)
			final String captchaResponse,
		final HttpServletRequest request,
		*/
		@RequestParam(value = User.JSON_KEY_PASSWORD, required = true)
			final String password,
		@RequestParam(
		    value = UserInvitation.JSON_KEY_INVITATION_ID,
		    required = false)
	        final String userInvitationId,
		@RequestBody
			final User.Builder userBuilder) {

		LOGGER.info("Creating a new user.");

		/*
		LOGGER.info("Validating the captcha information.");
		LOGGER.debug("Building the ReCaptcha validator.");
		ReCaptchaImpl reCaptcha = new ReCaptchaImpl();

		LOGGER.debug("Setting out private key.");
		reCaptcha.setPrivateKey(
			ConfigurationFileImport
				.getCustomProperties()
				.getProperty(PREFERENCE_KEY_CAPTCHA_PRIVATE_KEY));

		LOGGER.debug("Comparing the user's response.");
		ReCaptchaResponse reCaptchaResponse =
			reCaptcha
				.checkAnswer(
					request.getRemoteAddr(),
					captchaChallenge,
					captchaResponse);

		LOGGER.info("Ensuring the response was valid.");
		if(! reCaptchaResponse.isValid()) {
			throw
				new InvalidArgumentException(
					"The reCaptcha response was invalid.");
		}
		*/

		LOGGER.info("Verifying that the root URL was given.");
		if(rootUrl == null) {
		    throw new IllegalStateException("The root URL was not given.");
		}

		LOGGER.info("Verifying that a user supplied a password.");
		if(password == null) {
			throw new InvalidArgumentException("A password was not given.");
		}

		LOGGER.info("Verifying that the user supplied the necessary information.");
		if(userBuilder == null) {
		    throw
		        new InvalidArgumentException(
		            "The user information is missing.");
		}

		LOGGER.info("Hashing the user's password.");
		userBuilder.setPassword(password, true);

		LOGGER.debug("Determining how to validate the user's email address.");
		UserInvitation invitation = null;
		if(userInvitationId == null) {
	        LOGGER.info("Adding the self-registration information.");
    		userBuilder
    		    .setRegistration(
    		        new Registration.Builder(
    		            userBuilder.getId(),
    		            userBuilder.getEmail())
    		        .build());
		}
		else {
	        LOGGER.info("Checking the invitation ID.");
	        invitation =
	            UserInvitationBin
	                .getInstance()
	                .getInvitation(userInvitationId);

	        LOGGER.info("Verifying that the invitation exists.");
	        if(invitation == null) {
	            throw
	                new InvalidArgumentException("The invitation is unknown.");
	        }

	        LOGGER.info("Verifying that the invitation belongs to the same " +
	                    "email address.");
	        if(! invitation.getEmail().equalsIgnoreCase(userBuilder.getEmail())) {
	            throw
	                new InvalidArgumentException(
	                    "The invitation belongs to a different email " +
	                        "address.");
	        }

	        LOGGER.info("Verifying that the invitation is still valid.");
	        if(! invitation.isValid()) {
	            throw
	                new InvalidArgumentException(
	                    "The invitation is no longer valid.");
	        }

            LOGGER.debug("Setting the invitation ID.");
            userBuilder.setInvitationId(userInvitationId);

            // Get the other user invitations for this email address, get the
            // corresponding ohmlet invitations, and add those invitations to
            // the pending invitations for this user object.
            LOGGER.info("Finding other user invitations and adding their ohmlet " +
                        "invitations.");
            for(UserInvitation userInvitation :
                    UserInvitationBin
                        .getInstance()
                        .getInvitations(userBuilder.getEmail())) {

                // Get the corresponding ohmlet invitation.
                OhmletInvitation ohmletInvitation =
                    OhmletInvitationBin
                        .getInstance()
                        .getInvitation(userInvitation.getOhmletInvitationId());

                // Update this ohmlet invitation with a new ohmlet invitation.
                OhmletInvitation updatedOhmletInvitation =
                    (new OhmletInvitation.Builder(ohmletInvitation))
                        .setUsedTimestamp(System.currentTimeMillis())
                        .build();

                // Store the updated ohmlet invitation.
                OhmletInvitationBin
                    .getInstance()
                    .updateInvitation(updatedOhmletInvitation);

                // Add the original ohmlet invitation to this user.
                userBuilder.addOhlmetInvitation(ohmletInvitation);

                // Update the user invitation.
                UserInvitation updatedUserInvitation =
                    (new UserInvitation.Builder(userInvitation))
                        .setUsedTimestamp(System.currentTimeMillis())
                        .build();

                // Store the updated user invitation.
                UserInvitationBin
                    .getInstance()
                    .updateInvitation(updatedUserInvitation);
            }
		}

		LOGGER.debug("Building the user.");
		User validatedUser = userBuilder.build();

        LOGGER.info("Storing the user.");
        try {
            UserBin.getInstance().addUser(validatedUser);
        }
        catch(InvalidArgumentException e) {
            throw
                new InvalidArgumentException(
                    "A user with the given email address already exists.",
                    e);
        }

        if(invitation == null) {
            LOGGER.info("Sending the registration email.");
            validatedUser
                .getRegistration()
                .sendUserRegistrationEmail(

                    // Hack
                    // Remove "/ohmage" from the rootUrl because the front end
                    // is rooted at /. This will break if the front end or web
                    // app are ever rooted somewhere else. Solution: move front
                    // end root URL to config file? For the web app, the rootUrl
                    // is auto-discovered by Spring.

                    rootUrl.substring(0, rootUrl.indexOf("/ohmage")) + UserActivationController.ROOT_MAPPING);
        }

		LOGGER.info("Echoing the user back.");
		return validatedUser;
	}

    /**
     * Creates a user user.
     *
     * @param provider
     *        The provider's internal identifier.
     *
     * @param accessToken
     *        The access token provided by the provider to be used to
     *        authenticate the user.
     *
     * @param userBuilder
     *        The user's information.
     *
     * @return The new user object as represented in the system.
     */
	@RequestMapping(
		value = { "", "/" },
		method = RequestMethod.POST,
        params = { PARAMETER_PROVIDER })
	public static @ResponseBody User createUser(
		@RequestParam(
			value = PARAMETER_PROVIDER,
			required = true)
			final String provider,
		@RequestParam(
			value = PARAMETER_ACCESS_TOKEN,
			required = true)
			final String accessToken,
		@RequestBody
			final User.Builder userBuilder) {

		LOGGER.info("Creating a new provider user.");

        LOGGER.info("Verifying that a provider was given.");
        if(provider == null) {
            throw new InvalidArgumentException("The provider is missing.");
        }

        LOGGER.info("Verifying that an access token was given.");
        if(accessToken == null) {
            throw new InvalidArgumentException("The access token is missing.");
        }

        LOGGER.info("Verifying that the user information was given.");
        if(userBuilder == null) {
            throw
                new InvalidArgumentException(
                    "The user information is missing.");
        }

		LOGGER.debug("Retrieving the provider implementation.");
		Provider providerObject = ProviderRegistry.get(provider);

		LOGGER.info("Building the user's information based on the provider.");
		ProviderUserInformation userInformation =
			providerObject.getUserInformation(accessToken);

		LOGGER.trace("Attaching the provider information to the user.");
		userBuilder
			.addProvider(userInformation.getProviderId(), userInformation);

		LOGGER.trace("Adding the provider-based information's email address to " +
					"the user object.");
		userBuilder.setEmail(userInformation.getEmail());

        // Get the other user invitations for this email address, get the
        // corresponding ohmlet invitations, and add those invitations to
        // the pending invitations for this user object.
        LOGGER.info("Adding the ohmlet invitations to the user.");

        for(UserInvitation userInvitation :
                UserInvitationBin
                        .getInstance()
                        .getInvitations(userBuilder.getEmail())) {

            // Get the corresponding ohmlet invitation.
            OhmletInvitation ohmletInvitation =
                    OhmletInvitationBin
                            .getInstance()
                            .getInvitation(userInvitation.getOhmletInvitationId());

            // Update this ohmlet invitation with a new ohmlet invitation.
            OhmletInvitation updatedOhmletInvitation =
                    (new OhmletInvitation.Builder(ohmletInvitation))
                            .setUsedTimestamp(System.currentTimeMillis())
                            .build();

            // Store the updated ohmlet invitation.
            OhmletInvitationBin
                    .getInstance()
                    .updateInvitation(updatedOhmletInvitation);

            // Add the original ohmlet invitation to this user.
            userBuilder.addOhlmetInvitation(ohmletInvitation);

            // Update the user invitation.
            UserInvitation updatedUserInvitation =
                    (new UserInvitation.Builder(userInvitation))
                            .setUsedTimestamp(System.currentTimeMillis())
                            .build();

            // Store the updated user invitation.
            UserInvitationBin
                    .getInstance()
                    .updateInvitation(updatedUserInvitation);
        }

		LOGGER.debug("Building the user.");
		User user = userBuilder.build();

		LOGGER.info("Storing the user.");
		try {
		    UserBin.getInstance().addUser(user);
		}
		catch(InvalidArgumentException e) {
            throw
                new InvalidArgumentException(
                    "An ohmage account is already associated with this " +
                        "provider-based user.");
		}

		LOGGER.info("Echoing back the user object.");
		return user;
	}

	/**
	 * Retrieves the list of users that are visible to the requesting user.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
	 *
	 * @return The list of users that are visible to the requesting user.
	 */
	@RequestMapping(value = { "", "/" }, method = RequestMethod.GET)
	public static @ResponseBody Set<String> getVisibleUsers(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken) {

		LOGGER.info("Requesting a list of visible users.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

		LOGGER.debug("Create the result list.");
		Set<String> result = new HashSet<String>(1);
		LOGGER.info("If the calling user authenticated themselves, adding them " +
					"to the result list.");
		result.add(user.getId());

		return result;
	}

    /**
     * Retrieves the information about a user.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user whose information is desired.
     *
     * @return The desired user's information.
     */
    @RequestMapping(
        value = "{" + KEY_USER_ID + ":.+" + "}",
        method = RequestMethod.GET)
    public static @ResponseBody User getUserInformation(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_USER_ID) final String userId) {

        LOGGER.info("Requesting information about a user.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the user's unique identifier was given.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }

        // Users are only visible to read their own data at this time.
        LOGGER.info("Verifying that the user is requesting information about " +
                    "themselves.");
        if(! user.getId().equals(userId)) {
            throw
                new InsufficientPermissionsException(
                    "A user may only view their own information.");
        }

        // Pull the user object from the token.
        LOGGER.info("Retrieving the user object.");
        return user;
    }

    /**
     * Retrieves the information about a user populated with the current state
     * of the system.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user whose information is desired.
     *
     * @return The desired user's information.
     */
    @RequestMapping(
        value = "{" + KEY_USER_ID + ":.+" + "}" + "/current",
        method = RequestMethod.GET)
    public static @ResponseBody User getUserInformationPopulated(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_USER_ID) final String userId) {

        LOGGER.info("Requesting information about a user.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the user's unique identifier was given.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }

        // Users are only visible to read their own data at this time.
        LOGGER.info("Verifying that the user is requesting information about " +
                    "themselves.");
        if(! user.getId().equals(userId)) {
            throw
                new InsufficientPermissionsException(
                    "A user may only view their own information.");
        }

        LOGGER.info("Creating a user builder to use to update the fields.");
        User.Builder userBuilder = new User.Builder(user);

        LOGGER.info("Updating the stream references.");
        Map<String, Stream> streamLookup = new HashMap<String, Stream>();
        for(SchemaReference streamRef : user.getStreams()) {
            if(streamRef.getVersion() == null) {
                // Attempt to lookup the value.
                Stream stream = streamLookup.get(streamRef.getSchemaId());

                // If the lookup failed, perform the actual lookup from the
                // database.
                if(stream == null) {
                    // Get the stream.
                    stream =
                        StreamBin
                            .getInstance()
                            .getLatestStream(streamRef.getSchemaId(), false);

                    // Make sure it exists.
                    if(stream == null) {
                        throw new IllegalStateException(
                            "A referenced stream does not exist.");
                    }

                    // Add the entry to our lookup table.
                    streamLookup.put(streamRef.getSchemaId(), stream);
                }

                // Update the stream reference.
                userBuilder.removeStream(streamRef);
                userBuilder
                    .addStream(
                        new SchemaReference(stream));
            }
        }

        MultiValueResult<Stream> ownedStreams = StreamBin.getInstance().getUsersStreams(user.getId(), false);
        for(Stream stream : ownedStreams) {
             userBuilder.addStream(new SchemaReference(stream));
        }

        LOGGER.info("Updating the survey references.");
        Map<String, Survey> surveyLookup = new HashMap<String, Survey>();
        for(SchemaReference surveyRef : user.getSurveys()) {
            if(surveyRef.getVersion() == null) {
                // Attempt to lookup the value.
                Survey survey = surveyLookup.get(surveyRef.getSchemaId());

                // If the lookup failed, perform the actual lookup from the
                // database.
                if(survey == null) {
                    // Get the survey.
                    survey =
                        SurveyBin
                            .getInstance()
                            .getLatestSurvey(surveyRef.getSchemaId(), false);

                    // Make sure it exists.
                    if(survey == null) {
                        throw new IllegalStateException(
                            "A referenced survey does not exist.");
                    }

                    // Add the entry to our lookup table.
                    surveyLookup.put(surveyRef.getSchemaId(), survey);
                }

                // Update the stream reference.
                userBuilder.removeSurvey(surveyRef);
                userBuilder
                    .addSurvey(
                        new SchemaReference(survey));
            }
        }

        MultiValueResult<Survey> ownedSurveys = SurveyBin.getInstance().getUsersSurveys(user.getId(), false);
        for(Survey survey : ownedSurveys) {
             userBuilder.addSurvey(new SchemaReference(survey));
        }

        LOGGER.info("Updating the ohmlet references.");
        for(OhmletReference ohmletRef : user.getOhmlets()) {

            // Get the original ohmlet.
            Ohmlet ohmlet =
                OhmletBin.getInstance().getOhmlet(ohmletRef.getOhmletId());

            // Create a new builder to update this reference.
            OhmletReferenceView.Builder refBuilder =
                new OhmletReferenceView.Builder(new OhmletReference(ohmlet.getId(), ohmlet.getName()));

            // Add each of the referenced streams to the reference builder.
            for(SchemaReference streamRef : ohmlet.getStreams()) {
                // Check if the user is ignoring this stream reference.
                boolean ignored = false;
                for(SchemaReference ignoredStream :
                    ohmletRef.getIgnoredStreams()) {

                    // Check if we have found the right ignored stream.
                    if(streamRef.equals(ignoredStream)) {
                        ignored = true;
                        break;
                    }
                }
                // Now, check if the ignored stream was found.
                if(ignored) {
                    // Skip it.
                    continue;
                }
                // Otherwise, we need to add it to the list of streams.

                // Get the stream reference with the version filled in.
                SchemaReference sanitizedStreamRef = streamRef;
                // If the version isn't present...
                if(streamRef.getVersion() == null || streamRef.getName() == null) {
                    // Attempt to lookup the value.
                    Stream stream = streamLookup.get(streamRef.getSchemaId());

                    // If the lookup failed, perform the actual lookup from the
                    // database.
                    if(stream == null) {
                        // Get the stream.
                        stream =
                            StreamBin
                                .getInstance()
                                .getLatestStream(
                                    streamRef.getSchemaId(),
                                    false);

                        // Make sure it exists.
                        if(stream == null) {
                            throw
                                new IllegalStateException(
                                    "A referenced stream does not exist.");
                        }

                        // Add the entry to our lookup table.
                        streamLookup.put(streamRef.getSchemaId(), stream);
                    }

                    // Update the reference.
                    sanitizedStreamRef =
                        new SchemaReference(stream);
                }

                // Add it to the list of stream references.
                refBuilder.addStream(sanitizedStreamRef);
            }

            // Add each of the referenced surveys to the reference builder.
            for(SchemaReference surveyRef : ohmlet.getSurveys()) {
                // Check if the user is ignoring this survey reference.
                boolean ignored = false;
                for(SchemaReference ignoredSurvey :
                    ohmletRef.getIgnoredSurveys()) {

                    // Check if we have found the right ignored survey.
                    if(surveyRef.equals(ignoredSurvey)) {
                        ignored = true;
                        break;
                    }
                }
                // Now, check if the ignored survey was found.
                if(ignored) {
                    // Skip it.
                    continue;
                }
                // Otherwise, we need to add it to the list of surveys.

                // Get the survey reference with the version filled in.
                SchemaReference sanitizedSurveyRef = surveyRef;
                // If the version isn't present...
                if(surveyRef.getVersion() == null || surveyRef.getName() == null) {
                    // Attempt to lookup the value.
                    Survey survey = surveyLookup.get(surveyRef.getSchemaId());

                    // If the lookup failed, perform the actual lookup from the
                    // database.
                    if(survey == null) {
                        // Get the survey.
                        survey =
                            SurveyBin
                                .getInstance()
                                .getLatestSurvey(
                                    surveyRef.getSchemaId(),
                                    false);

                        // Make sure it exists.
                        if(survey == null) {
                            throw
                                new IllegalStateException(
                                    "A referenced survey does not exist.");
                        }

                        // Add the entry to our lookup table.
                        surveyLookup.put(surveyRef.getSchemaId(), survey);
                    }

                    // Update the reference.
                    sanitizedSurveyRef =
                        new SchemaReference(survey);
                }

                // Add it to the list of survey references.
                refBuilder.addSurvey(sanitizedSurveyRef);
            }

            // Update the ohmlet's definition.
            userBuilder
                .removeOhmlet(ohmlet.getId())
                .addOhmlet(refBuilder.build());
        }

        LOGGER.info("Returning the updated user object.");
        return userBuilder.build();
    }

	/**
	 * Updates a user's password.
	 *
	 * @param userId
	 *        The unique identifier for user whose information is desired.
	 *
	 * @param oldPassword
	 *        The user's current password.
	 *
	 * @param newPassword
	 *        The user's new password.
	 */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_PASSWORD,
		method = RequestMethod.POST,
        consumes = { "text/plain" })
	public static @ResponseBody void updateUserPassword(
		@PathVariable(KEY_USER_ID) final String userId,
		@RequestParam(
			value = User.JSON_KEY_PASSWORD,
			required = true)
			final String oldPassword,
		@RequestBody final String newPassword) {

		LOGGER.info("Updating a user's password.");

		LOGGER.info("Verifying that a user ID was given.");
		if(userId == null) {
		    throw new InvalidArgumentException("The user ID is missing.");
		}

		LOGGER.info("Verifying that the old password was given.");
		if(oldPassword == null) {
		    throw new InvalidArgumentException("The old password is missing.");
		}

		LOGGER.debug("Verifying that the new password is not empty.");
		if((newPassword == null) || (newPassword.length() == 0)) {
			throw new InvalidArgumentException("The new password is missing.");
		}

		LOGGER.debug("Retrieving the user.");
		User user = UserBin.getInstance().getUser(userId);

		LOGGER.info("Verifying that the user exists.");
		if(user == null) {
			throw new UnknownEntityException("The user is unknown.");
		}

		LOGGER.info("Verifying that the old password is correct.");
		if(! user.verifyPassword(oldPassword)) {
			throw new AuthenticationException("The password is incorrect.");
		}

		LOGGER.info("Updating the user's password.");
		user = user.updatePassword(User.hashPassword(newPassword));

		LOGGER.info("Storing the updated user.");
		UserBin.getInstance().updateUser(user);

		// Should we also invalidate all authentication tokens?
	}

    /**
     * Retrieves the set of communities that this user is part of.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The user's unique identifier, which must match the authentication
     *        token.
     *
     * @return The set of communities that this user is part of.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_OHMLETS,
		method = RequestMethod.GET)
	public static @ResponseBody Collection<OhmletReference> getFollowedOhmlets(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId) {

		LOGGER.info("Creating a request for a user to track a stream.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the user's unique identifier was given.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }

		LOGGER.info("Verifying that the user is querying their own profile.");
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only query their own accounts.");
		}

		LOGGER.info("Returning the set of stream references.");
		return user.getOhmlets();
	}

    /**
     * Retrieves the specific information for an ohmlet that the user is
     * following.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The user's unique identifier, which must match the authentication
     *        token.
     *
     * @param ohmletId
     *        The ohmlet's unique identifier.
     *
     * @return The user-specific information about a ohmlet that they are
     *         following.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" +
			User.JSON_KEY_OHMLETS + "/" +
			"{" + Ohmlet.JSON_KEY_ID + "}",
		method = RequestMethod.GET)
	public static @ResponseBody OhmletReference getFollowedOhmlet(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@PathVariable(Ohmlet.JSON_KEY_ID) final String ohmletId) {

		LOGGER.info("Creating a request for a user to track a stream.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the user's unique identifier was given.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }

        LOGGER.info("Verifying that an ohmlet ID was given.");
        if(ohmletId == null) {
            throw new InvalidArgumentException("The ohmlet ID is missing.");
        }

		LOGGER.info("Verifying that the user is reading their own profile.");
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only read their own accounts.");
		}

		LOGGER.info("Retreiving the ohmlet reference.");
		OhmletReference ohmletReference = user.getOhmlet(ohmletId);
		if(ohmletReference == null) {
		    throw
		        new UnknownEntityException(
		            "The user is not following this " +
		                Ohmlet.OHMLET_SKIN +
		                ".");
		}

		LOGGER.info("Returning the set of ohmlet references.");
		return ohmletReference;
	}

    /**
     * Retrieves the specific information for a ohmlet that the user is
     * following.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The user's unique identifier, which must match the authentication
     *        token.
     *
     * @param ohmletId
     *        The ohmlet's unique identifier.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" +
			User.JSON_KEY_OHMLETS + "/" +
			"{" + Ohmlet.JSON_KEY_ID + "}",
		method = RequestMethod.DELETE)
	public static @ResponseBody void leaveOhmlet(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@PathVariable(Ohmlet.JSON_KEY_ID) final String ohmletId) {

		LOGGER.info("Creating a request to disassociate a user with a ohmlet.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the user's unique identifier was given.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }

        LOGGER.info("Verifying that an ohmlet ID was given.");
        if(ohmletId == null) {
            throw new InvalidArgumentException("The ohmlet ID is missing.");
        }

		LOGGER.info("Verifying that the user is updating their own profile.");
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only modify their own accounts.");
		}

		LOGGER.info("Retrieving the ohmlet.");
		Ohmlet ohmlet =
			OhmletBin.getInstance().getOhmlet(ohmletId);

		LOGGER.info("Checking if the ohmlet exists.");
		if(ohmlet != null) {
			LOGGER.info("The " +
						Ohmlet.OHMLET_SKIN +
						" exists, so the user is being removed.");

			LOGGER.info("Removing the user from the ohmlet.");
			Ohmlet updatedOhmlet = ohmlet.removeMember(user.getId());

			LOGGER.info("Removing the user from the ohmlet.");
			OhmletBin.getInstance().updateOhmlet(updatedOhmlet);
		}
		else {
			LOGGER.info("The ohmlet does not exist.");
		}

		LOGGER.info("Removing the ohmlet from the user's profile.");
		User updatedUser = user.leaveOhmlet(ohmletId);

		LOGGER.info("Storing the updated user.");
		UserBin.getInstance().updateUser(updatedUser);
	}

    /**
     * For a specific user, marks a ohmlet's stream as being ignored meaning
     * that, unless followed in another ohmlet, it should not be displayed to
     * the user.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user that should be ignoring the
     *        stream.
     *
     * @param ohmletId
     *        The unique identifier for the ohmlet in which the stream is
     *        referenced.
     *
     * @param streamReference
     *        The reference for the stream that the ohmlet is referencing and
     *        that the user wants to ignore.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" +
			User.JSON_KEY_OHMLETS + "/" +
			"{" + Ohmlet.JSON_KEY_ID + "}" +
			"/" +
			OhmletReference.JSON_KEY_IGNORED_STREAMS,
		method = RequestMethod.POST)
	public static @ResponseBody void ignoreOhmletStream(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@PathVariable(Ohmlet.JSON_KEY_ID) final String ohmletId,
		@RequestBody final SchemaReference streamReference) {

		LOGGER.info("Creating a request for a user to ignore a stream reference " +
					"in a ohmlet.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

		LOGGER.info("Verifying that a stream reference was given.");
		if(streamReference == null) {
		    throw
		        new InvalidArgumentException(
		            "The stream reference is missing.");
		}

		LOGGER.info("Verifying that the user is reading their own profile.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only read their own accounts.");
		}

		LOGGER.debug("Retrieving the ohmlet reference for the user.");
		if(ohmletId == null) {
		    throw new InvalidArgumentException("The ohmlet ID is missing.");
		}
		OhmletReference ohmletReference = user.getOhmlet(ohmletId);

		LOGGER.info("Checking if the user is part of the ohmlet.");
		if(ohmletReference == null) {
			throw
				new InvalidArgumentException(
					"The user is not part of the " +
						Ohmlet.OHMLET_SKIN +
						".");
		}

		LOGGER.debug("Updating the ohmlet reference.");
		OhmletReference newOhmletReference =
		    ohmletReference.ignoreStream(streamReference);

		LOGGER.debug("Updating the user.");
		User newUser = user.upsertOhmlet(newOhmletReference);

		LOGGER.info("Saving the updated user object.");
		UserBin.getInstance().updateUser(newUser);
	}

    /**
     * For a specific user, removes the mark on a ohmlet's stream that was
     * causing it to be ignored. The user should again see this stream. If the
     * user was not ignoring the stream before this call, it will essentially
     * have no impact.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user that should stop ignoring the
     *        stream.
     *
     * @param ohmletId
     *        The unique identifier for the ohmlet in which the stream is
     *        referenced.
     *
     * @param streamReference
     *        The reference for the stream that the ohmlet is referencing and
     *        that the user wants to stop ignoring.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" +
			User.JSON_KEY_OHMLETS + "/" +
			"{" + Ohmlet.JSON_KEY_ID + "}" +
			"/" +
			OhmletReference.JSON_KEY_IGNORED_STREAMS,
		method = RequestMethod.DELETE)
	public static @ResponseBody void stopIgnoringOhmletStream(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@PathVariable(Ohmlet.JSON_KEY_ID) final String ohmletId,
		@RequestBody final SchemaReference streamReference) {

		LOGGER.info("Creating a request for a user to stop ignoring a stream " +
					"reference in a " +
					Ohmlet.OHMLET_SKIN +
					".");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that a stream reference was given.");
        if(streamReference == null) {
            throw
                new InvalidArgumentException(
                    "The stream reference is missing.");
        }

		LOGGER.info("Verifying that the user is reading their own profile.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only read their own accounts.");
		}

		LOGGER.debug("Retrieving the ohmlet reference for the user.");
        if(ohmletId == null) {
            throw new InvalidArgumentException("The ohmlet ID is missing.");
        }
		OhmletReference ohmletReference = user.getOhmlet(ohmletId);

		LOGGER.info("Checking if the user is part of the ohmlet.");
		if(ohmletReference == null) {
			throw
				new InvalidArgumentException(
					"The user is not part of the " +
						Ohmlet.OHMLET_SKIN +
						".");
		}

        LOGGER.debug("Updating the ohmlet reference.");
        OhmletReference newOhmletReference =
            ohmletReference.stopIgnoringStream(streamReference);

        LOGGER.debug("Updating the user.");
        User newUser = user.upsertOhmlet(newOhmletReference);

		LOGGER.info("Updating the user object.");
		UserBin.getInstance().updateUser(newUser);
	}

    /**
     * For a specific user, marks a ohmlet's survey as being ignored meaning
     * that, unless followed in another ohmlet, it should not be displayed to
     * the user.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for user that should be ignoring the
     *        survey.
     *
     * @param ohmletId
     *        The unique identifier for the ohmlet in which the survey is
     *        referenced.
     *
     * @param surveyReference
     *        The reference for the survey that the ohmlet is referencing and
     *        that the user wants to ignore.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" +
			User.JSON_KEY_OHMLETS + "/" +
			"{" + Ohmlet.JSON_KEY_ID + "}" +
			"/" +
			OhmletReference.JSON_KEY_IGNORED_SURVEYS,
		method = RequestMethod.POST)
	public static @ResponseBody void ignoreOhmletSurvey(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@PathVariable(Ohmlet.JSON_KEY_ID) final String ohmletId,
		@RequestBody final SchemaReference surveyReference) {

		LOGGER.info("Creating a request for a user to ignore a survey reference " +
					"in a ohmlet.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that a survey reference was given.");
        if(surveyReference == null) {
            throw
                new InvalidArgumentException(
                    "The survey reference is missing.");
        }

		LOGGER.info("Verifying that the user is reading their own profile.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only read their own accounts.");
		}

		LOGGER.debug("Retrieving the ohmlet reference for the user.");
        if(ohmletId == null) {
            throw new InvalidArgumentException("The ohmlet ID is null.");
        }
		OhmletReference ohmletReference = user.getOhmlet(ohmletId);

		LOGGER.info("Checking if the user is part of the ohmlet.");
		if(ohmletReference == null) {
			throw
				new InvalidArgumentException(
					"The user is not part of the " +
						Ohmlet.OHMLET_SKIN +
						".");
		}

        LOGGER.debug("Updating the ohmlet reference.");
        OhmletReference newOhmletReference =
            ohmletReference.ignoreSurvey(surveyReference);

        LOGGER.debug("Updating the user.");
        User newUser = user.upsertOhmlet(newOhmletReference);

		LOGGER.info("Updating the user object.");
		UserBin.getInstance().updateUser(newUser);
	}

    /**
     * For a specific user, removes the mark on a ohmlet's survey that was
     * causing it to be ignored. The user should again see this survey. If the
     * user was not ignoring the survey before this call, it will essentially
     * have no impact.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user that should stop ignoring the
     *        survey.
     *
     * @param ohmletId
     *        The unique identifier for the ohmlet in which the survey is
     *        referenced.
     *
     * @param surveyReference
     *        The reference for the survey that the ohmlet is referencing and
     *        that the user wants to stop ignoring.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" +
			User.JSON_KEY_OHMLETS + "/" +
			"{" + Ohmlet.JSON_KEY_ID + "}" +
			"/" +
			OhmletReference.JSON_KEY_IGNORED_SURVEYS,
		method = RequestMethod.DELETE)
	public static @ResponseBody void stopIgnoringOhmletSurvey(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@PathVariable(Ohmlet.JSON_KEY_ID) final String ohmletId,
		@RequestBody final SchemaReference surveyReference) {

		LOGGER.info("Creating a request for a user to stop ignoring a survey " +
					"reference in a ohmlet.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that a survey reference was given.");
        if(surveyReference == null) {
            throw
                new InvalidArgumentException(
                    "The survey reference is missing.");
        }

		LOGGER.info("Verifying that the user is reading their own profile.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only read their own accounts.");
		}

		LOGGER.debug("Retrieving the ohmlet reference for the user.");
		if(ohmletId == null) {
		    throw new InvalidArgumentException("The ohmlet ID is null.");
		}
		OhmletReference ohmletReference = user.getOhmlet(ohmletId);

		LOGGER.info("Checking if the user is part of the ohmlet.");
		if(ohmletReference == null) {
			throw
				new InvalidArgumentException(
					"The user is not part of the " +
						Ohmlet.OHMLET_SKIN +
						".");
		}

        LOGGER.debug("Updating the ohmlet reference.");
        OhmletReference newOhmletReference =
            ohmletReference.stopIgnoringSurvey(surveyReference);

        LOGGER.debug("Updating the user.");
        User newUser = user.upsertOhmlet(newOhmletReference);

		LOGGER.info("Updating the user object.");
		UserBin.getInstance().updateUser(newUser);
	}

    /**
     * Allows a user to follow a stream. The user can optionally supply a
     * version. If the version is given, that indicates that the user wishes to
     * follow a specific version of the stream. If a version is not given, that
     * indicates that a user wishes to follow the latest version of the stream.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user that is following this stream.
     *        For now, a user may only update their own profile, so this must
     *        match the authentication token's user.
     *
     * @param streamReference
     *        A reference to the stream that must include the stream's unique
     *        identifier and may include a specific version of the stream.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_STREAMS,
		method = RequestMethod.POST)
	public static @ResponseBody void followStream(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@RequestBody final SchemaReference streamReference) {

		LOGGER.info("Creating a request for a user to track a stream.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

		LOGGER.info("Verifying that the stream reference was given.");
		if(streamReference == null) {
		    throw
		        new InvalidArgumentException(
		            "The stream reference is missing.");
		}

		LOGGER.info("Verifying that the user is updating their own profile.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only modify their own accounts.");
		}

		LOGGER.debug("Retrieving the stream.");
		Stream stream;
		if(streamReference.getVersion() == null) {
			stream =
				StreamBin
					.getInstance()
					.getLatestStream(streamReference.getSchemaId(), false);
		}
		else {
			stream =
				StreamBin
					.getInstance()
					.getStream(
						streamReference.getSchemaId(),
						streamReference.getVersion(),
						false);
		}

		LOGGER.info("Verifying that the stream exists.");
		if(stream == null) {
			throw
				new InvalidArgumentException("The stream does not exist.");
		}

		LOGGER.info("Adding the stream to the list of streams being followed.");
		User updatedUser = user.followStream(streamReference);

		LOGGER.info("Storing the updated user.");
		UserBin.getInstance().updateUser(updatedUser);
	}

    /**
     * Retrieves the set of streams that this user is watching.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The user's unique identifier, which must match the authentication
     *        token.
     *
     * @return The set of stream references that this user is watching.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_STREAMS,
		method = RequestMethod.GET)
	public static @ResponseBody Set<SchemaReference> getFollowedStreams(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId) {

		LOGGER.info("Creating a request for a user to view streams they are " +
					"following.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

		LOGGER.info("Verifying that the user is updating their own profile.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only modify their own accounts.");
		}

		LOGGER.info("Returning the set of stream references.");
		return user.getStreams();
	}

    /**
     * Stops a user from following a stream.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user that is following this stream.
     *        For now, a user may only update their own profile, so this must
     *        match the authentication token's user.
     *
     * @param streamReference
     *        A reference to the stream that must include the stream's unique
     *        identifier and may include a specific version of the stream.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_STREAMS,
		method = RequestMethod.DELETE)
	public static @ResponseBody void forgetStream(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@RequestBody final SchemaReference streamReference) {

		LOGGER.info("Creating a request for a user to stop tracking a stream.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the stream reference was given.");
        if(streamReference == null) {
            throw
                new InvalidArgumentException(
                    "The stream reference is missing.");
        }

		LOGGER.info("Verifying that the user is updating their own profile.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only modify their own accounts.");
		}

		LOGGER.info("Adding the stream to the list of streams being followed.");
		User updatedUser = user.ignoreStream(streamReference);

		LOGGER.info("Storing the updated user.");
		UserBin.getInstance().updateUser(updatedUser);
	}

    /**
     * Allows a user to follow a survey. The user can optionally supply a
     * version. If the version is given, that indicates that the user wishes to
     * follow a specific version of the stream. If a version is not given, that
     * indicates that a user wishes to follow the latest version of the stream.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user that is following this survey.
     *        For now, a user may only update their own profile, so this must
     *        match the authentication token's user.
     *
     * @param surveyReference
     *        A reference to the survey that must include the survey's unique
     *        identifier and may include a specific version of the survey.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_SURVEYS,
		method = RequestMethod.POST)
	public static @ResponseBody void followSurvey(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@RequestBody final SchemaReference surveyReference) {

		LOGGER.info("Creating a request for a user to track a survey.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the survey reference was given.");
        if(surveyReference == null) {
            throw
                new InvalidArgumentException(
                    "The survey reference is missing.");
        }

		LOGGER.info("Verifying that the user is updating their own profile.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only modify their own accounts.");
		}

		LOGGER.debug("Retrieving the survey.");
		Survey survey;
		if(surveyReference.getVersion() == null) {
			survey =
				SurveyBin
					.getInstance()
					.getLatestSurvey(surveyReference.getSchemaId(), false);
		}
		else {
			survey =
				SurveyBin
					.getInstance()
					.getSurvey(
						surveyReference.getSchemaId(),
						surveyReference.getVersion(),
						false);
		}

		LOGGER.info("Verifying that the survey exists.");
		if(survey == null) {
			throw
				new InvalidArgumentException("The survey does not exist.");
		}

		LOGGER.info("Adding the stream to the list of surveys being followed.");
		User updatedUser = user.followSurvey(surveyReference);

		LOGGER.info("Storing the updated user.");
		UserBin.getInstance().updateUser(updatedUser);
	}

    /**
     * Retrieves the set of surveys that this user is watching.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The user's unique identifier, which must match the authentication
     *        token.
     *
     * @return The set of survey references that this user is watching.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_SURVEYS,
		method = RequestMethod.GET)
	public static @ResponseBody Set<SchemaReference> getFollowedSurveys(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId) {

		LOGGER.info("Creating a request for a user to view surveys they are " +
					"following.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

		LOGGER.info("Verifying that the user is updating their own profile.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only modify their own accounts.");
		}

		LOGGER.info("Returning the set of survey references.");
		return user.getSurveys();
	}

    /**
     * Stops a user from following a survey.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param userId
     *        The unique identifier for the user that is following this survey.
     *        For now, a user may only update their own profile, so this must
     *        match the authentication token's user.
     *
     * @param surveyReference
     *        A reference to the survey that must include the survey's unique
     *        identifier and may include a specific version of the survey.
     */
	@RequestMapping(
		value =
			"{" + KEY_USER_ID + ":.+" + "}" + "/" + User.JSON_KEY_SURVEYS,
		method = RequestMethod.DELETE)
	public static @ResponseBody void forgetSurvey(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
		@PathVariable(KEY_USER_ID) final String userId,
		@RequestBody final SchemaReference surveyReference) {

		LOGGER.info("Creating a request for a user to stop tracking a survey.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Verifying that the survey reference was given.");
        if(surveyReference == null) {
            throw
                new InvalidArgumentException(
                    "The survey reference is missing.");
        }

		LOGGER.info("Verifying that the user is updating their own profile.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"Users may only modify their own accounts.");
		}

		LOGGER.info("Adding the stream to the list of surveys being followed.");
		User updatedUser = user.ignoreSurvey(surveyReference);

		LOGGER.info("Storing the updated user.");
		UserBin.getInstance().updateUser(updatedUser);
	}

    /**
     * Disables the user's account.
     *
     * @param userId
     *        The unique identifier for the user whose account is being
     *        disabled.
     *
     * @param password
     *        The user's password to confirm the deletion.
     */
	@RequestMapping(
		value = "{" + KEY_USER_ID + ":.+" + "}",
		method = RequestMethod.DELETE,
		params = { User.JSON_KEY_PASSWORD })
	public static @ResponseBody void deleteUserWithPassword(
		@PathVariable(KEY_USER_ID)
			final String userId,
		@RequestParam(
			value = User.JSON_KEY_PASSWORD,
			required = true)
			final String password) {

		LOGGER.info("Creating a request to delete a user.");

        LOGGER.info("Verifying that the user's unique identifier was given.");
        if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
        }

        LOGGER.info("Verifying that a password was given.");
        if(password == null) {
            throw new InvalidArgumentException("The password is missing.");
        }

		LOGGER.debug("Retreiving the user.");
		User user = UserBin.getInstance().getUser(userId);

		LOGGER.info("Verifying that the user exists.");
		if(user == null) {
		    throw new InvalidArgumentException("The user is unknown.");
		}

		LOGGER.info("Verifying the user's password.");
		if(! user.verifyPassword(password)) {
		    throw new AuthenticationException("The password was incorrect.");
		}

		LOGGER.info("Disabling the user's account.");
		UserBin.getInstance().disableUser(userId);
	}

    /**
     * Disables the user's account.
     *
     * @param userId
     *        The unique identifier for the user whose account is being
     *        disabled.
     *
     * @param providerId
     *        The internal unique identifier of the provider.
     *
     * @param accessToken
     *        A provider-generated access token to authenticate the user and
     *        validate the request.
     */
	@RequestMapping(
		value = "{" + KEY_USER_ID + ":.+" + "}",
		method = RequestMethod.DELETE,
		params = { PARAMETER_PROVIDER, PARAMETER_ACCESS_TOKEN })
	public static @ResponseBody void deleteUserWithProvider(
		@PathVariable(KEY_USER_ID) final String userId,
		@RequestParam(
			value = PARAMETER_PROVIDER,
			required = true)
			final String providerId,
		@RequestParam(
			value = PARAMETER_ACCESS_TOKEN,
			required = true)
			final String accessToken) {

		LOGGER.info("Deleting a user.");

		LOGGER.debug("Verifying that a provider ID was given.");
		if(providerId == null) {
		    throw new InvalidArgumentException("The provider ID is missing.");
		}

        LOGGER.debug("Verifying that an access token was given.");
        if(accessToken == null) {
            throw new InvalidArgumentException("The access token is missing.");
        }

		LOGGER.debug("Retrieving the implementation for this provider.");
		Provider provider = ProviderRegistry.get(providerId);

		LOGGER.info("Retrieving the user based on the access token.");
		ProviderUserInformation userInformation =
			provider.getUserInformation(accessToken);

		LOGGER.info("Retrieving the ohmage account linked with the " +
					"provider-given ID.");
		User user =
			UserBin
				.getInstance()
				.getUserFromProvider(
					userInformation.getProviderId(),
					userInformation.getUserId());
		if(user == null) {
			LOGGER.info("No ohmage account has linked itself with these " +
						"credentials.");
			throw
				new InsufficientPermissionsException(
					"The user has not yet created an ohmage account.");
		}

		LOGGER.info("Verifying that the requesting user is the same as the user " +
					"that is attempting to be deleted.");
		if(userId == null) {
            throw
                new InvalidArgumentException(
                    "The user's unique identifier is missing.");
		}
		if(! user.getId().equals(userId)) {
			throw
				new InsufficientPermissionsException(
					"No user can delete another user's account.");
		}

		LOGGER.info("Disabling the user's account.");
		UserBin.getInstance().disableUser(user.getId());
	}
}