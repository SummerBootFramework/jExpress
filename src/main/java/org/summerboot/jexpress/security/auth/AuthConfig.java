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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.apache.tika.utils.StringUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.integration.ldap.LdapAgent;
import org.summerboot.jexpress.integration.ldap.LdapSSLConnectionFactory1;
import org.summerboot.jexpress.security.EncryptorUtil;
import org.summerboot.jexpress.security.JwtUtil;

import javax.crypto.SecretKey;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
//@ImportResource(BootConstant.FILE_CFG_AUTH)
public class AuthConfig extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(AuthConfig.class);
        System.out.println(t);
    }

    public static final AuthConfig cfg = new AuthConfig();

    protected AuthConfig() {
        reset();
    }

    @Override
    protected void reset() {
        ldapSSLConnectionFactoryClassName = LdapSSLConnectionFactory1.class.getName();
    }

    @Override
    public AuthConfig temp() {
        AuthConfig temp = (AuthConfig) super.temp();
        AuthConfig current = cfg;//AuthConfig.instance(AuthConfig.class);
        temp.addDeclareRoles(current.getDeclareRoles());
        return temp;
    }

    @Override
    public void shutdown() {
    }

    //1.1 LDAP settings
    @ConfigHeader(title = "1.1 LDAP connection settings")
    @Config(key = "ldap.type.AD", defaultValue = "false",
            desc = "set it true only when LDAP is implemented by Microsoft Active Directory (AD)\n"
                    + "false when use others like Open LDAP, IBM Tivoli, Apache")
    protected volatile boolean typeAD;

    @Config(key = "ldap.host",
            desc = "LDAP will be disabled when host is not provided")
    protected volatile String ldapHost;

    @Config(key = "ldap.port",
            desc = "LDAP 389, LDAP over SSL 636, AD global 3268, AD global over SSL 3269")
    protected volatile int ldapPort;

    @Config(key = "ldap.baseDN")
    protected volatile String ldapBaseDN;

    @Config(key = "ldap.bindingUserDN")
    protected volatile String bindingUserDN;

    @JsonIgnore
    @Config(key = "ldap.bindingPassword", validate = Config.Validate.Encrypted)
    protected volatile String bindingPassword;

    @Config(key = "ldap.PasswordAlgorithm", defaultValue = "SHA3-256")
    protected volatile String passwordAlgorithm;

    @Config(key = "ldap.schema.TenantGroup.ou")
    protected volatile String ldapScheamTenantGroupOU;

    //1.2 LDAP Client keystore
    @ConfigHeader(title = "1.2 LDAP Client keystore")
    @JsonIgnore
    @Config(key = "ldap.ssl.KeyStore", StorePwdKey = "ldap.ssl.KeyStorePwd",
            AliasKey = "ldap.ssl.KeyAlias", AliasPwdKey = "ldap.ssl.KeyPwd",
            desc = DESC_KMF)
    protected volatile KeyManagerFactory kmf;

    @Config(key = "ldap.ssl.protocol", defaultValue = "TLSv1.3", desc = "Valid values: TLSv1.2, TLSv1.3. Blank value = plaintext no SSL/TLS")
    protected volatile String ldapTLSProtocol;

    @Config(key = "ldap.SSLConnectionFactoryClass")
    protected volatile String ldapSSLConnectionFactoryClassName;

    //1.3 LDAP Client truststore
    @ConfigHeader(title = "1.3 LDAP Client truststore")
    @Config(key = "ldap.ssl.TrustStore", StorePwdKey = "ldap.ssl.TrustStorePwd",
            desc = DESC_TMF)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    protected volatile Properties ldapConfig;

    //2. JWT
    protected static final String KEY_privateKeyFile = "jwt.asymmetric.SigningKeyFile";
    protected static final String KEY_privateKeyPwd = "jwt.asymmetric.SigningKeyPwd";
    protected static final String KEY_publicKeyFile = "jwt.asymmetric.ParsingKeyFile";

    protected static final String JWT_PRIVATE_KEY_FILE = "jwt_private.key";
    protected static final String JWT_PUBLIC_KEY_FILE = "jwt_public.key";

    @ConfigHeader(title = "2. JWT",
            example = "To generate the keypair manually:\n"
                    + "step1. generate keypair: openssl genrsa -des3 -out keypair.pem 4096 \n"
                    + "step2. export public key: openssl rsa -in keypair.pem -outform PEM -pubout -out " + JWT_PUBLIC_KEY_FILE + " \n"
                    + "step3. export private key: openssl rsa -in keypair.pem -out private_unencrypted.pem -outform PEM \n"
                    + "step4. encrypt and convert private key from PKCS#1 to PKCS#8: openssl pkcs8 -topk8 -inform PEM -outform PEM -in private_unencrypted.pem -out " + JWT_PRIVATE_KEY_FILE)
    @Config(key = KEY_privateKeyFile,
            desc = "Path to an encrypted RSA private key file in PKCS#8 format with minimal 2048 key size",
            callbackMethodName4Dump = "generateTemplate_privateKeyFile")
    protected volatile File privateKeyFile;

    protected void generateTemplate_privateKeyFile(StringBuilder sb) {
        sb.append(KEY_privateKeyFile + "=" + JWT_PRIVATE_KEY_FILE + "\n");
        generateTemplate = true;
    }

    @JsonIgnore
    @Config(key = KEY_privateKeyPwd, validate = Config.Validate.Encrypted,
            desc = "The password of this private key",
            callbackMethodName4Dump = "generateTemplate_privateKeyPwd")
    protected volatile String privateKeyPwd;

    protected void generateTemplate_privateKeyPwd(StringBuilder sb) {
        sb.append(KEY_privateKeyPwd + DEFAULT_DEC_VALUE);
    }

    @Config(key = KEY_publicKeyFile,
            desc = "Path to the public key file corresponding to this private key",
            callbackMethodName4Dump = "generateTemplate_publicKeyFile")
    protected volatile File publicKeyFile;

    protected void generateTemplate_publicKeyFile(StringBuilder sb) {
        sb.append(KEY_publicKeyFile + "=" + JWT_PUBLIC_KEY_FILE + "\n");
    }

    @JsonIgnore
    @Config(key = "jwt.symmetric.key", validate = Config.Validate.Encrypted,
            desc = "HMAC-SHA key for bothe signing and parsing, it will be ignored when asymmetric one is specified.\n"
                    + "Use this command to generate this key: java -jar <app>.jar -jwt <HS256, HS384, HS512>")
    protected volatile String symmetricKey;

    @JsonIgnore
    protected volatile Key jwtSigningKey;

    @JsonIgnore
    protected volatile JwtParser jwtParser;

    @Config(key = "jwt.ttl.minutes", defaultValue = "1440")
    protected volatile int jwtTTLMinutes;

    @Config(key = "jwt.issuer")
    protected volatile String jwtIssuer;

    //3. Role mapping
    @ConfigHeader(title = "3. Role mapping",
            desc = "Map the role (defined as @RolesAllowed({\"AppAdmin\"})) with user group (no matter the group is defined in LDAP or DB)",
            format = "roles.<role name>.groups=csv list of groups\n"
                    + "roles.<role name>.users=csv list of users",
            example = "the following example maps one group(AppAdmin_Group) and two users(johndoe, janejoe) to a role(AppAdmin)\n"
                    + "roles.AppAdmin.groups=AppAdmin_Group\n"
                    + "roles.AppAdmin.users=johndoe, janejoe",
            callbackMethodName4Dump = "generateTemplate_DumpRoleMapping")
    protected Map<String, RoleMapping> roles = new HashMap<>();

    /**
     * called by @ConfigHeader.callbackMethodName4Dump value
     *
     * @param sb
     */
    protected void generateTemplate_DumpRoleMapping(StringBuilder sb) {
        for (String role : declareRoles) {
            sb.append("roles.").append(role).append(".groups=<LDAP.").append(role).append("GroupName>\n");
            sb.append("#roles.").append(role).append(".users=<LDAP.").append(role).append("UserName>\n");
        }
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException, OperatorCreationException, GeneralSecurityException {
        // 1. LDAP Client keystore
        if (ldapHost != null) {
            // 1.1 LDAP Client keystore
            boolean isSSLEnabled = !StringUtils.isBlank(ldapTLSProtocol);
            if (isSSLEnabled) {
                //LdapSSLConnectionFactory1.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), ldapTLSProtocol);
                //ldapSSLConnectionFactoryClassName = LdapSSLConnectionFactory1.class.getName();
                String key = "ldap.SSLConnectionFactoryClass";
                try {
                    Class<?> sslFactoryClass = Class.forName(ldapSSLConnectionFactoryClassName);
                    Method method = sslFactoryClass.getMethod("init", KeyManagerFactory.class, TrustManagerFactory.class, String.class);
                    method.invoke(null, kmf, tmf, ldapTLSProtocol);
                } catch (ClassNotFoundException ex) {
                    helper.addError("invalid \"" + key + ", error=" + ex);
                } catch (NoSuchMethodException ex) {
                    helper.addError("invalid \"" + key + "missing method: public static void init(KeyManagerFactory kmf, TrustManagerFactory tmf, String protocol), error=" + ex);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    helper.addError("invalid \"" + key + "failed to invoke method: public static void init(KeyManagerFactory kmf, TrustManagerFactory tmf, String protocol), error=" + ex);
                }
            }
            //1.2 LDAP info
            ldapConfig = LdapAgent.buildCfg(ldapHost, ldapPort, isSSLEnabled, ldapSSLConnectionFactoryClassName, ldapTLSProtocol, bindingUserDN, bindingPassword);
        }
        // 2. JWT        
        if (symmetricKey != null) {
            //jwtSigningKey = EncryptorUtil.keyFromString(jwtSigningKeyString, jwtSignatureAlgorithm.getJcaName());
            jwtSigningKey = JwtUtil.parseSigningKey(symmetricKey);
            jwtParser = Jwts.parser() // (1)
                    .verifyWith((SecretKey) jwtSigningKey) // (2)
                    .build(); // (3)
        }
        //File rootFolder = cfgFile.getParentFile().getParentFile();
        if (privateKeyFile != null) {
            createIfNotExist(JWT_PRIVATE_KEY_FILE, JWT_PRIVATE_KEY_FILE);
            jwtSigningKey = EncryptorUtil.loadPrivateKey(privateKeyFile, privateKeyPwd.toCharArray());
        }
        if (publicKeyFile != null) {
            createIfNotExist(JWT_PUBLIC_KEY_FILE, JWT_PUBLIC_KEY_FILE);
            PublicKey publicKey = EncryptorUtil.loadPublicKey(EncryptorUtil.KeyFileType.PKCS12, publicKeyFile);
            jwtParser = Jwts.parser() // (1)
                    .verifyWith(publicKey) // (2)
                    .build(); // (3)
        }

        // 3. Cache TTL
        //jwtTTL = TimeUnit.MINUTES.toMillis(jwtTTLMinutes);
        //userTTL = TimeUnit.MINUTES.toMillis(userTTL);
        // 4. Role mapping
        Set<Object> keys = props.keySet();
        Map<String, RoleMapping> rolesTemp = new HashMap<>();
        keys.forEach((key) -> {
            String name = key.toString();
            if (name.startsWith("roles.")) {
                String[] names = name.split("\\.");
                String roleName = names[1];
                if (!declareRoles.contains(roleName)) {
                    helper.addError("Undefined role: (\"" + roleName + "\") is not defined in any @Controller @RolesAllowed(" + declareRoles + ") - line: " + key + "=" + props.getProperty(key.toString()));
                }
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

        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
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

    public String getLdapScheamTenantGroupOU() {
        return ldapScheamTenantGroupOU;
    }

    public String getPasswordAlgorithm() {
        return passwordAlgorithm;
    }

    public void setPasswordAlgorithm(String passwordAlgorithm) {
        this.passwordAlgorithm = passwordAlgorithm;
    }

    public String getLdapSSLConnectionFactoryClassName() {
        return ldapSSLConnectionFactoryClassName;
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

    @JsonIgnore
    public String getBindingPassword() {
        return bindingPassword;
    }

    @JsonIgnore
    public KeyManagerFactory getKmf() {
        return kmf;
    }

    @JsonIgnore
    public TrustManagerFactory getTmf() {
        return tmf;
    }

    @JsonIgnore
    public File getPrivateKeyFile() {
        return privateKeyFile;
    }

    @JsonIgnore
    public String getPrivateKeyPwd() {
        return privateKeyPwd;
    }

    @JsonIgnore
    public File getPublicKeyFile() {
        return publicKeyFile;
    }

    @JsonIgnore
    public String getSymmetricKey() {
        return symmetricKey;
    }

    //@Deprecated - should use annotation jakarta.annotation.security.DeclareRoles
//    public Set<String> getRoleNames() {
//        return Set.copyOf(roles.keySet());
//    }
    protected final Set<String> declareRoles = new TreeSet<>();

    public void addDeclareRoles(Set<String> scanedDeclareRoles) {
        this.declareRoles.addAll(Set.copyOf(scanedDeclareRoles));
    }

    public Set<String> getDeclareRoles() {
        return Set.copyOf(declareRoles);
    }

}
