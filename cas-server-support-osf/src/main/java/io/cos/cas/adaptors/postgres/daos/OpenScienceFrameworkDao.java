/*
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
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

package io.cos.cas.adaptors.postgres.daos;

import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkApiOauth2Application;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkApiOauth2PersonalAccessToken;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkApiOauth2Scope;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkGuid;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkInstitution;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkTimeBasedOneTimePassword;
import io.cos.cas.adaptors.postgres.models.OpenScienceFrameworkUser;

import java.util.List;

/**
 * The Open Science Framework Data Access Object Interface.
 *
 * @author Longze Chen
 * @since 4.1.0
 */
public interface OpenScienceFrameworkDao {

    /**
     * Find one user by username (primary email).
     *
     * @param username the username
     * @return OpenScienceFrameworkUser or null
     */
    OpenScienceFrameworkUser findOneUserByUsername(final String username);

    /**
     * Find one user by user's emails, including username.
     *
     * @param email the user's email
     * @return OpenScienceFrameworkUser or null
     */
    OpenScienceFrameworkUser findOneUserByEmail(final String email);

    /**
     * Find one "time based one-time password" by owner.
     *
     * @param ownerId the owner id
     * @return OpenScienceFrameworkTimeBasedOneTimePassword or null
     */
    OpenScienceFrameworkTimeBasedOneTimePassword findOneTimeBasedOneTimePasswordByOwnerId(final Integer ownerId);

    /**
     * Find one institution by institution id, a.k.a `objectId` @"_id".
     *
     * @param id the institution id
     * @return OpenScienceFrameworkInstitution or null
     */
    OpenScienceFrameworkInstitution findOneInstitutionById(final String id);

    /**
     * Find a list of institutions that support institution login.
     *
     * @return a list of institutions
     */
    List<OpenScienceFrameworkInstitution> findAllInstitutions();

    /**
     * Find one scope by scope name.
     *
     * @param name the scope name
     * @return OpenScienceFrameworkApiOauth2Scope or null
     */
    OpenScienceFrameworkApiOauth2Scope findOneScopeByName(final String name);

    /**
     * Find one personal access token by token id.
     *
     * @param tokenId the token id
     * @return OpenScienceFrameworkApiOauth2PersonalAccessToken or null
     */
    OpenScienceFrameworkApiOauth2PersonalAccessToken findOnePersonalAccessTokenByTokenId(final String tokenId);

    /**
     * Find a list of active oauth applications.
     *
     * @return OpenScienceFrameworkApiOauth2Application List or null
     */
    List<OpenScienceFrameworkApiOauth2Application> findOauthApplications();

    /**
     * Find the GUID object asscociated with a User.
     *
     * @param user the user
     * @return the GUID object
     */
    OpenScienceFrameworkGuid findGuidByUser(final OpenScienceFrameworkUser user);
}
