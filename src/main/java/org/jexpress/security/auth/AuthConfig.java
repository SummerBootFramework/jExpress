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
package org.jexpress.security.auth;

import org.jexpress.boot.config.AbstractJExpressConfig;
import org.jexpress.boot.config.ConfigUtil;
import org.jexpress.boot.config.annotation.Config;
import org.jexpress.boot.config.annotation.Memo;
import org.jexpress.integration.ldap.LdapAgent;
import org.jexpress.integration.ldap.LdapSSLConnectionFactory;
import org.jexpress.security.JwtUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.operator.OperatorCreationException;
import org.jexpress.security.EncryptorUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class AuthConfig extends AbstractJExpressConfig {

    public static final AuthConfig CFG = new AuthConfig();

    public static void main(String[] args) {
        String t = generateTemplate(AuthConfig.class);
        System.out.println(t);
    }

    @Override
    public void shutdown() {
    }

    //1.1 LDAP settings
    @Memo(title = "1.1 LDAP connection settings")
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

    @Config(key = "ldap.baseDN")
    private volatile String ldapBaseDN;

    @Config(key = "ldap.bindingUserDN", required = false)
    private volatile String bindingUserDN;

    @JsonIgnore
    @Config(key = "ldap.bindingPassword", validate = Config.Validate.Encrypted, required = false)
    private volatile String bindingPassword;
    @Config(key = "ldap.TenantGroupName", required = false)
    private volatile String ldapTenantGroupName;

    //1.2 LDAP Client keystore
    @Memo(title = "1.2 LDAP Client keystore")
    @Config(key = "ldap.ssl.protocol", defaultValue = "TLSv1.3")
    private volatile String ldapTLSProtocol;
    @JsonIgnore
    @Config(key = "ldap.ssl.KeyStore", StorePwdKey = "ldap.ssl.KeyStorePwd",
            AliasKey = "ldap.ssl.KeyAlias", AliasPwdKey = "ldap.ssl.KeyPwd", required = false)
    private volatile KeyManagerFactory kmf;

    //1.3 LDAP Client truststore
    @Memo(title = "1.3 LDAP Client truststore")
    @JsonIgnore
    @Config(key = "ldap.ssl.TrustStore", StorePwdKey = "ldap.ssl.TrustStorePwd", required = false)
    private volatile TrustManagerFactory tmf;

    private volatile Properties ldapConfig;

    //2. JWT
    @Memo(title = "2. JWT")
    @Config(key = "jwt.asymmetric.SigningKeyFile", required = false,
            desc = "Path to an encrypted RSA private key file in PKCS#8 format with minimal 2048 key size. To generate the keypair manually:\n"
            + "1. generate keypair: openssl genrsa -des3 -out keypair.pem 4096 \n"
            + "2. export public key: openssl rsa -in keypair.pem -outform PEM -pubout -out public.pem \n"
            + "3. export private key: openssl rsa -in keypair.pem -out private_unencrypted.pem -outform PEM \n"
            + "4. encrypt and convert private key from PKCS#1 to PKCS#8: openssl pkcs8 -topk8 -inform PEM -outform PEM -in private_unencrypted.pem -out private.pem")
    private volatile File privateKeyFile;

    @JsonIgnore
    @Config(key = "jwt.asymmetric.SigningKeyPwd", validate = Config.Validate.Encrypted, required = false,
            desc = "The password of this private key")
    private volatile String privateKeyPwd;

    @Config(key = "jwt.asymmetric.ParsingKeyFile", required = false,
            desc = "Path to the public key file corresponding to this private key")
    private volatile File publicKeyFile;

    @JsonIgnore
    @Config(key = "jwt.symmetric.key", validate = Config.Validate.Encrypted, required = false,
            desc = "HMAC-SHA key for bothe signing and parsing, it will be ignored when asymmetric one is specified.\n"
            + "Use this command to generate this key: java -jar <app>.jar -jwt <HS256, HS384, HS512>")
    private volatile String symmetricKey;

    @JsonIgnore
    private volatile Key jwtSigningKey;
    @JsonIgnore
    private volatile JwtParser jwtParser;

    @Config(key = "jwt.ttl.minutes", defaultValue = "1440")
    private volatile int jwtTTLMinutes;

    @Config(key = "jwt.issuer", required = false)
    private volatile String jwtIssuer;

    //3. Role mapping
    @Memo(title = "3. Role mapping",
            desc = "Map the role with user group (no matter the group is defined in LDAP or DB)",
            format = "roles.<role name>.groups=csv list\n"
            + "roles.<role name>.users=csv list",
            example = "the following example maps one group(AppAdmin_Group) and two users(johndoe, janejoe) to a role(AppAdmin)\n"
            + "roles.AppAdmin.groups=AppAdmin_Group\n"
            + "roles.AppAdmin.users=johndoe, janejoe")
    private Map<String, RoleMapping> roles;

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, OperatorCreationException, GeneralSecurityException {
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
        if (symmetricKey != null) {
            //jwtSigningKey = EncryptorUtil.keyFromString(jwtSigningKeyString, jwtSignatureAlgorithm.getJcaName());
            jwtSigningKey = JwtUtil.parseSigningKey(symmetricKey);
            jwtParser = Jwts.parserBuilder() // (1)
                    .setSigningKey(jwtSigningKey) // (2)
                    .build(); // (3)
        }
        //File rootFolder = cfgFile.getParentFile().getParentFile();
        if (privateKeyFile != null) {
            jwtSigningKey = EncryptorUtil.loadPrivateKey(privateKeyFile, privateKeyPwd.toCharArray());
        }
        if (publicKeyFile != null) {
            PublicKey publicKey = EncryptorUtil.loadPublicKey(EncryptorUtil.KeyFileType.PKCS12, publicKeyFile);
            jwtParser = Jwts.parserBuilder() // (1)
                    .setSigningKey(publicKey) // (2)
                    .build(); // (3)
        }

        // 3. Cache TTL
        //jwtTTL = TimeUnit.MINUTES.toMillis(jwtTTLMinutes);
        //userTTL = TimeUnit.MINUTES.toMillis(userTTL);
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

    @JsonIgnore
    public Key getJwtSigningKey() {
        return jwtSigningKey;
    }

    @JsonIgnore
    public JwtParser getJwtParser() {
        return jwtParser;
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public int getJwtTTLMinutes() {
        return jwtTTLMinutes;
    }

    public RoleMapping getRole(String role) {
        return roles.get(role);
    }

    public Map<String, RoleMapping> getRoles() {
        return roles;
    }

    public Set<String> getRoleNames() {
        return Set.copyOf(roles.keySet());
    }
}
