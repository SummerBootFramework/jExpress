/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License and you have no
 * policy prohibiting employee contributions back to this file (unless the contributor to this
 * file is your current or retired employee). You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.summerboot.jexpress.security.auth;

import javax.naming.NamingException;
import org.summerboot.jexpress.integration.ldap.LdapAgent;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class LDAPAuthenticator extends BootAuthenticator<Long> {

    @Override
    protected Caller authenticate(String usename, String password, Long metaData, AuthenticatorListener listener, final ServiceContext context) throws NamingException {
        try (LdapAgent ldap = LdapAgent.build()) {
            return ldap.authenticateUser(usename, password, listener);
        }
    }
}