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

package io.cos.cas.adaptors.postgres.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The Open Science Framework Institution.
 *
 * @author Longze Chen
 * @since 4.1.o
 */
@Entity
@Table(name = "osf_institution")
public class OpenScienceFrameworkInstitution {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenScienceFrameworkInstitution.class);

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    /**
     * The `objectId` serves as a unique and fixed identifier for each institution.
     * It comes from `node.institution_id` (mongo) to `osf_institution._id` (postgres).
     * Methods such as `getId()`, `findInstitutionById()` and `findInstitutionLogoutUrlById()` refer to this `objectId`.
     */
    @Column(name = "_id")
    private String objectId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "login_url")
    private String loginUrl;

    @Column(name = "logout_url")
    private String logoutUrl;

    @Column(name = "is_deleted")
    private Boolean deleted;

    /** Default Constructor. */
    public OpenScienceFrameworkInstitution() {}

    /**
     * Returns the `objectId` (institution id) instead of `id` (postgres pk).
     * @return the object id
     */
    public String getId() {
        return objectId;
    }

    public String getName() {
        return name;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    @Override
    public String toString() {
        return String.format("OpenScienceFrameworkInstitution [_id=%s, name=%s]", objectId, name);
    }
}
