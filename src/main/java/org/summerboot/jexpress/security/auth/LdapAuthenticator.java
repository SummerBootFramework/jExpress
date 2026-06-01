/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.security.auth;

import org.summerboot.jexpress.api.auth.Caller;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.boot.lifecycle.auth.AuthenticatorListener;
import org.summerboot.jexpress.integration.ldap.LdapAgent;

import javax.naming.NamingException;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class LdapAuthenticator<M> extends BootAuthenticator {

    @Override
    protected Caller authenticate(String username, String password, Object metaData, AuthenticatorListener listener, final SessionContext context) throws NamingException {
        try (LdapAgent ldap = LdapAgent.build()) {
            return ldap.authenticateUser(username, password, listener);
        }
    }
}
