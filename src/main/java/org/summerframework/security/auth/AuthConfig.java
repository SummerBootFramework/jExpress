/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.security.auth;

import org.summerframework.boot.config.AbstractSummerBootConfig;
import org.summerframework.boot.config.ConfigUtil;
import org.summerframework.boot.config.annotation.Config;
import org.summerframework.boot.config.annotation.Memo;
import org.summerframework.integration.ldap.LdapAgent;
import org.summerframework.integration.ldap.LdapSSLConnectionFactory;
import org.summerframework.security.JwtUtil;
import org.summerframework.security.SecurityUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.File;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class AuthConfig extends AbstractSummerBootConfig {

    public static final AuthConfig CFG = new AuthConfig();

    public static void main(String[] args) {
        String t = generateTemplate(AuthConfig.class);
        System.out.println(t);
    }

    @Override
    public void shutdown() {
    }

    //1.1 LDAP settings
    @Memo(title = "1.1 LDAP settings")
    @Config(key = "ldap.type.AD", defaultValue = "false",
            desc = "set it true only when LDAP is implemented by Microsoft Active Directory (AD)\n"
            + "false when use others like Open LDAP, IBM Tivoli, Apache")
    private volatile boolean typeAD = false;

    @Config(key = "ldap.host", required = false,
            desc = "LDAP will be disabled when host is not provided")
    private volatile String ldapHost;

    @Config(key = "ldap.port", required = false,
            desc = "LDAP 389, LDAP over SSL 636, AD global 3268, AD global voer SSL 3269")
    private volatile int ldapPort;

    @Config(key = "ldap.baseDN", defaultValue = "DC=testent,DC=testad,DC=testmre")
    private volatile String ldapBaseDN;

    @Config(key = "ldap.bindingUserDN", required = false)
    private volatile String bindingUserDN;

    @JsonIgnore
    @Config(key = "ldap.bindingPassword", validate = Config.Validate.Encrypted, required = false)
    private volatile String bindingPassword;
    @Config(key = "ldap.TenantGroupName", required = false)
    private volatile String ldapTenantGroupName;

    //1.2 LDAP SSL
    @Memo(title = "1.2 LDAP SSL")
    @Config(key = "ldap.ssl.protocal", defaultValue = "TLSv1.2")
    private volatile String ldapTLSProtocol;
    @JsonIgnore
    @Config(key = "ldap.ssl.KeyStore", StorePwdKey = "ldap.ssl.KeyStorePwd",
            AliasKey = "ldap.ssl.KeyAlias", AliasPwdKey = "ldap.ssl.KeyPwd", required = false)
    private volatile KeyManagerFactory kmf;

    @JsonIgnore
    @Config(key = "ldap.ssl.TrustStore", StorePwdKey = "ldap.ssl.TrustStorePwd", required = false)
    private volatile TrustManagerFactory tmf;

    private volatile Properties ldapConfig;

    //2. JWT
    @Memo(title = "2. JWT")
    @Config(key = "jwt.SignatureAlgorithm", defaultValue = "HS256",
            desc = "valid values = HS256, HS384, HS512, RS256, RS384, RS512, ES256, ES384, ES512, PS256, PS384, PS512")
    private volatile String algorithm;

    @Config(key = "jwt.root.SigningKey.Algorithm", defaultValue = "SHA-2")
    private volatile String keyAlgorithm;

    @JsonIgnore
    @Config(key = "jwt.root.SigningKey", validate = Config.Validate.Encrypted,
            desc = "symmetric key when algorithm is one of the HS256, HS384, HS512\n"
            + "public key when algorithm is one of the RS384, RS512, ES256, ES384, ES512, PS256, PS384, PS512")
    private volatile String jwtRootSigningKeyString;

    private volatile Key jwtRootSigningKey;

    private volatile SignatureAlgorithm jwtSignatureAlgorithm;

    @Config(key = "jwt.issuer", required = false)
    private volatile String jwtIssuer;

    //3. Cache TTL
    @Memo(title = "3. Cache TTL")
    @Config(key = "cache.ttl.jwt.minutes")
    private volatile int jwtTTL;

    @Config(key = "cache.ttl.user.minutes")
    private volatile long userTTL;

    //4. Role mapping
    @Memo(title = "4. Role mapping",
            desc = "Map the role with user group (no matter the group is defined in LDAP or DB)",
            format = "roles.<role name>.groups=csv list\n"
            + "roles.<role name>.users=csv list",
            example = "the following example maps one group(AppAdmin_Group) and two users(johndoe, janejoe) to a role(AppAdmin)\n"
            + "roles.AppAdmin.groups=AppAdmin_Group\n"
            + "roles.AppAdmin.users=johndoe, janejoe")
    private Map<String, RoleMapping> roles;

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 1. LDAP Client keystore
        if (ldapHost != null) {
            // 1.1 LDAP Client keystore
            String ldapSSLConnectionFactoryClassName = null;
            boolean isSSL = kmf != null;
            if (isSSL) {
                LdapSSLConnectionFactory.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), ldapTLSProtocol);
                ldapSSLConnectionFactoryClassName = LdapSSLConnectionFactory.class.getName();
            }
            //1.2 LDAP info
            ldapConfig = LdapAgent.buildCfg(ldapHost, ldapPort, isSSL, ldapSSLConnectionFactoryClassName, ldapTLSProtocol, bindingUserDN, bindingPassword);
        }
        // 2. JWT        
        jwtSignatureAlgorithm = SignatureAlgorithm.forName(algorithm);
        switch (jwtSignatureAlgorithm) {
            case HS256:
            case HS384:
            case HS512:
                jwtRootSigningKey = JwtUtil.parseSigningKey(jwtRootSigningKeyString, keyAlgorithm);
                break;
            case RS256:
            case RS384:
            case RS512:
            case ES256:
            case ES384:
            case ES512:
            case PS256:
            case PS384:
            case PS512:
                Key[] keys = SecurityUtil.parseKeyPair(jwtRootSigningKeyString, null, keyAlgorithm);
                jwtRootSigningKey = keys[0];
                break;
        }

        // 3. Cache TTL
        //jwtTTL = TimeUnit.MINUTES.toMillis(jwtTTL);
        userTTL = TimeUnit.MINUTES.toMillis(userTTL);

        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        // 4. Role mapping
        Set<Object> keys = props.keySet();
        Map<String, RoleMapping> rolesTemp = new HashMap();
        keys.forEach((key) -> {
            String name = key.toString();
            if (name.startsWith("roles.")) {
                String[] names = name.split("\\.");
                String roleName = names[1];
                RoleMapping.Type type = RoleMapping.Type.valueOf(names[2]);
                RoleMapping rm = rolesTemp.get(roleName);
                if (rm == null) {
                    rm = new RoleMapping(roleName);
                    rolesTemp.put(roleName, rm);
                }
                rm.add(type, props.getProperty(key.toString()));
            }
        });
        roles = Map.copyOf(rolesTemp);
    }

    public String getLdapHost() {
        return ldapHost;
    }

    public int getLdapPort() {
        return ldapPort;
    }

    public String getLdapBaseDN() {
        return ldapBaseDN;
    }

    public String getBindingUserDN() {
        return bindingUserDN;
    }

    public String getLdapTenantGroupName() {
        return ldapTenantGroupName;
    }

    public String getLdapTLSProtocol() {
        return ldapTLSProtocol;
    }

    public boolean isTypeAD() {
        return typeAD;
    }

    @JsonIgnore
    public Properties getLdapConfig() {
        return ldapConfig;
    }

    public SignatureAlgorithm getJwtSignatureAlgorithm() {
        return jwtSignatureAlgorithm;
    }

    @JsonIgnore
    Key getJwtRootSigningKey() {
        return jwtRootSigningKey;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public int getJwtTTL() {
        return jwtTTL;
    }

    public long getUserTTL() {
        return userTTL;
    }

    RoleMapping getRole(String role) {
        return roles.get(role);
    }

    Map<String, RoleMapping> getRoles() {
        return roles;
    }
    
    public Set<String> getRoleNames() {
        return Set.copyOf(roles.keySet());
    }
}
