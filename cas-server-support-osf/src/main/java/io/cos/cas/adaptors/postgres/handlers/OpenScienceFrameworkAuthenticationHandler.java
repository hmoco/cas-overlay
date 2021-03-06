/*
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.cos.cas.adaptors.postgres.handlers;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkGuid;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkTimeBasedOneTimePassword;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkUser;
import io.cos.cas.adaptors.postgres.daos.OpenScienceFrameworkDaoImpl;
import io.cos.cas.authentication.LoginNotAllowedException;
import io.cos.cas.authentication.OneTimePasswordFailedLoginException;
import io.cos.cas.authentication.OneTimePasswordRequiredException;
import io.cos.cas.authentication.OpenScienceFrameworkCredential;

import io.cos.cas.authentication.ShouldNotHappenException;
import io.cos.cas.authentication.oath.TotpUtils;
import org.jasig.cas.authentication.AccountDisabledException;
import org.jasig.cas.authentication.Credential;
import org.jasig.cas.authentication.HandlerResult;
import org.jasig.cas.authentication.PreventedException;
import org.jasig.cas.authentication.handler.NoOpPrincipalNameTransformer;
import org.jasig.cas.authentication.handler.PrincipalNameTransformer;
import org.jasig.cas.authentication.handler.support.AbstractPreAndPostProcessingAuthenticationHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.bcrypt.BCrypt;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.validation.constraints.NotNull;


/**
 * The Open Science Framework Authentication handler.
 *
 * @author Michael Haselton
 * @author Longze Chen
 * @since 4.1.0
 */
public class OpenScienceFrameworkAuthenticationHandler extends AbstractPreAndPostProcessingAuthenticationHandler
        implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenScienceFrameworkAuthenticationHandler.class);

    private static final int TOTP_INTERVAL = 30;
    private static final int TOTP_WINDOW = 1;

    // user status
    private static final String USER_ACTIVE = "ACTIVE";
    private static final String USER_NOT_CONFIRMED = "NOT_CONFIRMED";
    private static final String USER_NOT_CLAIMED = "NOT_CLAIMED";
    private static final String USER_MERGED = "MERGED";
    private static final String USER_DISABLED = "DISABLED";
    private static final String USER_STATUS_UNKNOWN = "UNKNOWN";

    @NotNull
    private PrincipalNameTransformer principalNameTransformer = new NoOpPrincipalNameTransformer();

    @NotNull
    private OpenScienceFrameworkDaoImpl openScienceFrameworkDao;

    /** Default Constructor. */
    public OpenScienceFrameworkAuthenticationHandler() {}

    /**
     * @param principalNameTransformer the principal name transformer.
     */
    public void setPrincipalNameTransformer(final PrincipalNameTransformer principalNameTransformer) {
        this.principalNameTransformer = principalNameTransformer;
    }

    /**
     * @param openScienceFrameworkDao the open science framework data access object
     */
    public void setOpenScienceFrameworkDao(final OpenScienceFrameworkDaoImpl openScienceFrameworkDao) {
        this.openScienceFrameworkDao = openScienceFrameworkDao;
    }

    @Override
    public void afterPropertiesSet() throws Exception {}

    @Override
    protected final HandlerResult doAuthentication(final Credential credential)
            throws GeneralSecurityException, PreventedException {
        final OpenScienceFrameworkCredential osfCredential = (OpenScienceFrameworkCredential) credential;
        if (osfCredential.getUsername() == null) {
            throw new AccountNotFoundException("Username is null.");
        }
        final String transformedUsername = principalNameTransformer.transform(osfCredential.getUsername());
        if (transformedUsername == null) {
            throw new AccountNotFoundException("Transformed username is null.");
        }
        osfCredential.setUsername(transformedUsername);
        return authenticateInternal(osfCredential);
    }

    /**
     * Authenticates an Open Science Framework credential.
     *
     * @param credential the credential object bearing the username, password, etc...
     *
     * @return HandlerResult resolved from credential on authentication success or null if no principal could be resolved
     * from the credential.
     *
     * @throws GeneralSecurityException On authentication failure.
     * @throws PreventedException On the indeterminate case when authentication is prevented.
     */
    protected final HandlerResult authenticateInternal(final OpenScienceFrameworkCredential credential)
            throws GeneralSecurityException, PreventedException {

        final String username = credential.getUsername().toLowerCase();
        final String plainTextPassword = credential.getPassword();
        final String verificationKey = credential.getVerificationKey();
        final String oneTimePassword = credential.getOneTimePassword();

        final OpenScienceFrameworkUser user = openScienceFrameworkDao.findOneUserByEmail(username);
        if (user == null) {
            throw new AccountNotFoundException(username + " not found with query");
        }

        Boolean validPassphrase = Boolean.FALSE;
        final String userStatus = verifyUserStatus(user);

        if (credential.isRemotePrincipal()) {
            // verified through remote principals
            validPassphrase = Boolean.TRUE;
        } else if (verificationKey != null && verificationKey.equals(user.getVerificationKey())) {
            // verified by verification key
            validPassphrase = Boolean.TRUE;
        } else if (plainTextPassword != null && verifyPassword(plainTextPassword, user.getPassword())) {
            // verified by password
            validPassphrase = Boolean.TRUE;
        }
        if (!validPassphrase) {
            throw new FailedLoginException(username + ": invalid remote authentication, verification key or password");
        }

        final OpenScienceFrameworkTimeBasedOneTimePassword timeBasedOneTimePassword
                = openScienceFrameworkDao.findOneTimeBasedOneTimePasswordByOwnerId(user.getId());

        // if the user has set up two factors authentication
        if (timeBasedOneTimePassword != null
                && timeBasedOneTimePassword.getTotpSecret() != null
                && timeBasedOneTimePassword.isConfirmed()
                && !timeBasedOneTimePassword.isDeleted()) {
            // if no one time password is provided in credential, redirect to `casOtpLoginView`
            if (oneTimePassword == null) {
                throw new OneTimePasswordRequiredException("Time-based One Time Password required");
            }
            // verify one time password
            try {
                final Long longOneTimePassword = Long.valueOf(oneTimePassword);
                if (!TotpUtils.checkCode(timeBasedOneTimePassword.getTotpSecretBase32(), longOneTimePassword, TOTP_INTERVAL, TOTP_WINDOW)) {
                    throw new OneTimePasswordFailedLoginException(username + " invalid time-based one time password");
                }
            } catch (final Exception e) {
                throw new OneTimePasswordFailedLoginException(username + ": invalid time-based one time password");
            }
        }

        // Check user's status, and only ACTIVE user can sign in
        if (USER_NOT_CONFIRMED.equals(userStatus)) {
            throw new LoginNotAllowedException(username + " is not registered");
        } else if (USER_DISABLED.equals(userStatus)) {
            throw new AccountDisabledException(username + " is disabled");
        } else if (USER_NOT_CLAIMED.equals(userStatus)) {
            throw new ShouldNotHappenException(username + " is not claimed");
        } else if (USER_MERGED.equals(userStatus)) {
            throw new ShouldNotHappenException("Cannot log in to a merged user " + username);
        } else if (USER_STATUS_UNKNOWN.equals(userStatus)) {
            throw new ShouldNotHappenException(username + " is not active: unknown status");
        }
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", user.getUsername());
        attributes.put("givenName", user.getGivenName());
        attributes.put("familyName", user.getFamilyName());

        // CAS returns the user's GUID to OSF
        // Note: GUID is recommended. Do not use user's pimary key or username.
        final OpenScienceFrameworkGuid guid = openScienceFrameworkDao.findGuidByUser(user);
        return createHandlerResult(credential, this.principalFactory.createPrincipal(guid.getGuid(), attributes), null);
    }

    /**
     * {@inheritDoc}
     * @return True if credential is a {@link OpenScienceFrameworkCredential}, false otherwise.
     */
    @Override
    public boolean supports(final Credential credential) {
        return credential instanceof OpenScienceFrameworkCredential;
    }



    /**
     * Verify User Status.
     *
     *  USER_ACTIVE:
     *      authentication succeed
     *  USER_NOT_CONFIRMED:
     *      inform the user that the account is not confirmed and provide a resend confirmation link
     *  USER_DISABLED:
     *      inform the user that the account is disable and that they can contact OSF support
     *  USER_MERGED, USER_NOT_CLAIMED and USER_STATUS_UNKNOWN:
     *      these is not suppose to happen, ask user to contact OSF support
     *
     * @param user the OSF user
     * @return the user status
     */
    private String verifyUserStatus(final OpenScienceFrameworkUser user) {
        // An active user must be registered, claimed, not disabled, not merged and has a not null/None password.
        // Only active user can pass the verification.
        if (user.isActive()) {
            logger.info("User Status Check: {}", USER_ACTIVE);
            return USER_ACTIVE;
        } else {
            // If the user instance is not claimed, it is also not registered and not confirmed.
            // It can be either an unclaimed contributor or a new user pending confirmation.
            if (!user.isClaimed() && !user.isRegistered() && !user.isConfirmed()) {
                if (isUnusablePassword(user.getPassword())) {
                    // If the user instance has an unusable password, it must be an unclaimed contributor.
                    logger.info("User Status Check: {}", USER_NOT_CLAIMED);
                    return USER_NOT_CLAIMED;
                } else if (checkPasswordPrefix(user.getPassword())) {
                    // If the user instance has a password with a valid prefix, it must be a unconfirmed user who
                    // has registered for a new account.
                    logger.info("User Status Check: {}", USER_NOT_CONFIRMED);
                    return USER_NOT_CONFIRMED;
                }
            }
            // If the user instance is merged by another user, it is registered, confirmed and claimed.
            // `.merged_by` field being not null is a sufficient condition.
            // However, its username is set to GUID and password is set to unusable.
            if (user.isMerged()) {
                logger.info("User Status Check: {}", USER_MERGED);
                return USER_MERGED;
            }
            // If the user instance is disabled, it is also not registered but claimed.
            // `.date_disabled` field being not null is a sufficient condition.
            // However, it still has the username and password.
            // When the user tries to login, an account disabled message will be displayed.
            if (user.isDisabled()) {
                logger.info("User Status Check: {}", USER_DISABLED);
                return USER_DISABLED;
            }

            // Other status combinations are considered UNKNOWN
            logger.info("User Status Check: {}", USER_STATUS_UNKNOWN);
            return USER_STATUS_UNKNOWN;
        }
    }

    /**
     * Verify Password. `bcrypt$` (backward compatibility) and `bcrypt_sha256$` are the only two valid prefix.
     *
     * @param plainTextPassword the plain text password provided by the user
     * @param userPasswordHash the password hash stored in database
     * @return True if verified, False otherwise
     */
    private boolean verifyPassword(final String plainTextPassword, final String userPasswordHash) {

        String password, passwordHash;

        try {
            if (userPasswordHash.startsWith("bcrypt$")) {
                // django.contrib.auth.hashers.BCryptPasswordHasher
                passwordHash = userPasswordHash.split("bcrypt\\$")[1];
                password = plainTextPassword;
            } else if(userPasswordHash.startsWith("bcrypt_sha256$")) {
                // django.contrib.auth.hashers.BCryptSHA256PasswordHasher
                passwordHash = userPasswordHash.split("bcrypt_sha256\\$")[1];
                password = sha256HashPassword(plainTextPassword);
            } else {
                // invalid password hash prefix
                return false;
            }
            passwordHash = updateBCryptHashIdentifier(passwordHash);
            return password != null && passwordHash != null && BCrypt.checkpw(password, passwordHash);
        } catch (final Exception e) {
            // Do not log stack trace which may contain user's plaintext password
            logger.error(String.format("CAS has encountered a problem when verifying the password: %s.", e.toString()));
            return false;
        }
    }

    /**
     * Check if the password hash is "django-unusable".
     *
     * @param passwordHash the password hash
     * @return true if unusable, false otherwise
     */
    private boolean isUnusablePassword(final String passwordHash) {
        return passwordHash == null || passwordHash.startsWith("!");
    }

    /**
     * Check if the password hash bears a valid prefix.
     *
     * @param passwordHash the password hash
     * @return true if usable, false otherwise
     */
    private boolean checkPasswordPrefix(final String passwordHash) {
        return passwordHash != null && (passwordHash.startsWith("bcrypt$") || passwordHash.startsWith("bcrypt_sha256$"));
    }

    /**
     * Hash the password using SHA256, the first step for BCryptSHA256.
     * This is dependent on django.contrib.auth.hashers.BCryptSHA256PasswordHasher.
     *
     * @param password the plain text password provided by user
     * @return the password hash in String or null
     */
    private String sha256HashPassword(final String password) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] sha256HashedPassword = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            final StringBuilder builder = new StringBuilder();
            for (final byte b : sha256HashedPassword) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (final Exception e) {
            // Do not log stack trace which may contain user's plaintext password
            logger.error(String.format("CAS has encountered a problem when sha256-hashing the password: %s.", e.toString()));
            return null;
        }
    }

    /**
     * Update BCrypt Hash Identifier for Compatibility.
     *
     * Spring's BCrypt implements the specification and is not vulnerable to OpenBSD's `u_int8_t` overflow issue. How-
     * ever, it only recognizes `$2$` or `$2a$` identifier for a password BCrypt hash. The solution is to replace `$2b$`
     * or `$2y$` with `$2a` in the hash before calling `BCrypt.checkpw()`. This is correct and secure.
     *
     * @param passwordHash the password hash by BCrypt or BCryptSHA256
     * @return the spring compatible hash string or null
     */
    private String updateBCryptHashIdentifier(final String passwordHash) {
        try {
            if (passwordHash.charAt(2) != '$') {
                final StringBuilder builder = new StringBuilder(passwordHash);
                builder.setCharAt(2, 'a');
                return builder.toString();
            }
            return passwordHash;
        } catch (final Exception e) {
            // Do not log stack trace which may contain user's plaintext password
            logger.error(String.format("CAS has encountered a problem when updating password hash identifier: %s.", e.toString()));
            return null;
        }
    }
}
