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
package org.jexpress.integration.ldap;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class LdapAgent implements Closeable {

    private static final Logger log = LogManager.getLogger(LdapAgent.class);
    private static final String DN = "dn";

    protected final Properties cfg;
    protected final String baseDN;
    protected final boolean isAD;
    protected final String tenantGroupName;
    protected LdapContext m_ctx = null;//not thread safe

    public static Properties buildCfg(String host, int port, boolean isSSL, String ldapSSLConnectionFactoryClassName, String sslProtocol, String bindingUserDN, String bindingPassword) {
        Properties tempCfg = new Properties();
        tempCfg.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        String providerUrl = isSSL
                ? "ldaps://" + host + ":" + port
                : "ldap://" + host + ":" + port;
        tempCfg.put(Context.PROVIDER_URL, providerUrl);

        //tempCfg.put(Context.URL_PKG_PREFIXES, "com.sun.jndi.url");
        //tempCfg.put(Context.REFERRAL, "follow");
        //tempCfg.put(LdapContext.CONTROL_FACTORIES, "com.sun.jndi.ldap.ControlFactory");
        if (StringUtils.isNotBlank(bindingUserDN)) {
            tempCfg.put(Context.SECURITY_PRINCIPAL, bindingUserDN);
        }
        if (StringUtils.isNotBlank(bindingPassword)) {
            tempCfg.put(Context.SECURITY_AUTHENTICATION, "simple");//"EXTERNAL" - Principal and credentials will be obtained from the connection
            tempCfg.put(Context.SECURITY_CREDENTIALS, bindingPassword);
        }
        if (isSSL) {// Specify SSL
            tempCfg.put(Context.SECURITY_PROTOCOL, sslProtocol);//"TLSv1.3"
            if (ldapSSLConnectionFactoryClassName != null) {
                tempCfg.put("java.naming.ldap.factory.socket", ldapSSLConnectionFactoryClassName);
            }
        }
//        tempCfg.put("com.sun.jndi.ldap.read.timeout", "20000");
//        tempCfg.put("com.sun.jndi.ldap.connect.timeout", "15000");
        return tempCfg;
    }

    public LdapAgent(Properties cfg, String baseDN, boolean isAD, String tenantGroupName) throws NamingException, IOException {
        this.cfg = cfg;
        this.baseDN = baseDN;
        this.isAD = isAD;
        this.tenantGroupName = tenantGroupName;
        connect();
    }

    public String getBaseDN() {
        return baseDN;
    }

    public String getTenantGroupName() {
        return tenantGroupName;
    }

    @Override
    public void close() throws IOException {
        if (m_ctx != null) {
            log.debug("processing...");
            try {
                m_ctx.close();
            } catch (NamingException ex) {
                log.error("failed to close LDAP ctx", ex);
            } finally {
                m_ctx = null;
            }
            log.debug("success");
        }
    }

    private void connect() throws NamingException, IOException {
        close();
        log.debug(baseDN + ", isAD=" + isAD);
        m_ctx = new InitialLdapContext(cfg, null);
        log.debug("success");
        // Start TLS
//        StartTlsResponse tls = (StartTlsResponse) m_ctx.extendedOperation(new StartTlsRequest());
//        try {
//            SSLSession sess = tls.negotiate();
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
    }

    public String getDN(final String username) throws NamingException {
        String[] dn = queryPersonDN(isAD ? "sAMAccountName" : "uid", username);
        if (dn == null || dn.length < 1) {
            return null;
        }
        return dn[0];
    }

    public String[] queryPersonDN(final String key, final String username) throws NamingException {
        List<Attributes> attrs = queryPerson(key, username);//key =[uid, mail, email, employeeNumber, etc.]
        int size = attrs.size();
        String[] ret = new String[size];
        for (int i = 0; i < size; i++) {
            ret[i] = getAttr(attrs.get(i), DN);
        }
        return ret;
    }

    public List<Attributes> queryPerson(final String key, final String value) throws NamingException {
        //String sFilter = "(&(objectClass=inetOrgPerson)&(" + key + "=" + value + "))";
        //String sFilter = "(&(objectClass=organizationalPerson)&(" + key + "=" + value + "))";
        String objectClass = isAD ? "organizationalPerson" : "inetOrgPerson";
        String sFilter = "(&(objectClass=" + objectClass + ")&(" + key + "=" + value + "))";
        return query(sFilter);
    }

    public List<Attributes> getUserRoleGroups(String userDN) throws NamingException {
        String sFilter = isAD
                ? "(&(objectClass=group)(member=" + userDN + "))"
                : "(&(objectClass=groupOfUniqueNames)(uniqueMember=" + userDN + "))";
        //List<Attributes> roles = query("(&(objectClass=groupOfUniqueNames)(ou:dn:=groups)(uniqueMember=" + userDN + "))");
        List<Attributes> roles = query(sFilter);
        //roles.addAll(tenants);
        return roles;
    }

    public List<Attributes> query(final String sFilter) throws NamingException {
        log.debug(() -> "base=" + baseDN + ", filter=" + sFilter);
        List<Attributes> ret = new ArrayList();
        SearchControls ctlsUser = new SearchControls();
        ctlsUser.setSearchScope(SearchControls.SUBTREE_SCOPE);
        NamingEnumeration<SearchResult> results = m_ctx.search(baseDN, sFilter, ctlsUser);
        while (results.hasMore()) {
            SearchResult sr = results.next();
            //String dn = sr.getName() + "," + sBase;
            String dn = sr.getNameInNamespace();
            Attributes attr = m_ctx.getAttributes(dn);
            ret.add(attr);
            attr.put(DN, dn);
        }
        return ret;
    }

    public String getAttr(Attributes attrs, String id) throws NamingException {
        String ret = null;
        if (attrs != null && id != null) {
            Attribute attr = attrs.get(id);
            if (attr != null) {
                //ret = attr.toString().substring(id.length() + 2);
                ret = attr.getAll().next().toString();
            }
        }
        return ret;
    }

    private List<String>[] parseAddedAndRemoved(List<Attributes> currentGroup, String[] newGroup) throws NamingException {
        List<String> addedList = newGroup == null ? new ArrayList() : new ArrayList(Arrays.asList(newGroup));
        List<String> removedList = new ArrayList();
        List[] ret = {addedList, removedList};

        for (Attributes attrs : currentGroup) {
            String currentGroupDN = getAttr(attrs, DN);
            boolean found = false;
            for (String newGroupDn : addedList) {
                if (newGroupDn.equals(currentGroupDN)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                addedList.remove(currentGroupDN);
            } else {
                removedList.add(currentGroupDN);
            }
        }
        return ret;
    }

    /**
     *
     * @param password
     * @param algorithm MD5, SHA-1, SHA-256 or SHA3-256 see
     * https://en.wikipedia.org/wiki/SHA-3 (section Comparison of SHA functions)
     * @return
     * @throws GeneralSecurityException
     */
    public static String hashMD5Password(String password, String algorithm) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        digest.update(password.getBytes(StandardCharsets.UTF_8));
        byte[] md5 = digest.digest();
        String md5Password = Base64.getEncoder().encodeToString(md5);
        //return "{MD5}" + md5Password;
        return md5Password;
    }
    private static final int SALT_LENGTH = 4;

    public static String generateSSHA(String password) throws NoSuchAlgorithmException {
        return generateSSHA(password, "SHA3-256");
    }

    /**
     *
     * @param _password
     * @param algorithm MD5, SHA-1, SHA-256 or SHA3-256 see
     * https://en.wikipedia.org/wiki/SHA-3 (section Comparison of SHA functions)
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String generateSSHA(String _password, String algorithm) throws NoSuchAlgorithmException {
        byte[] password = _password.getBytes(StandardCharsets.UTF_8);
        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        MessageDigest crypt = MessageDigest.getInstance(algorithm);
        crypt.reset();
        crypt.update(password);
        crypt.update(salt);
        byte[] hash = crypt.digest();

        byte[] hashPlusSalt = new byte[hash.length + salt.length];
        System.arraycopy(hash, 0, hashPlusSalt, 0, hash.length);
        System.arraycopy(salt, 0, hashPlusSalt, hash.length, salt.length);

        return new StringBuilder()//.append("{SSHA}")
                .append(Base64.getEncoder().encodeToString(hashPlusSalt))
                .toString();
    }

    public void authenticate(String dn, String currentPassword) throws NamingException {
//        if (dn == null) {
//            // use root dn
//            dn = "cn=root," + baseDN;
//        }
//        if (currentPassword == null) {
//            currentPassword = accessPassword;
//        }
        try {
            m_ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);   // user dn
            m_ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, currentPassword); // user password
            m_ctx.reconnect(null);
            Control[] controls = m_ctx.getResponseControls();
            if (controls != null) {
                for (Control control : controls) {
                    log.debug("  Control: " + control.getID() + " | crit: " + control.isCritical() + " | val: '" + new String(control.getEncodedValue()) + "'");
                }
            }
        } finally {
            m_ctx.removeFromEnvironment(Context.SECURITY_PRINCIPAL);   // user dn
            m_ctx.removeFromEnvironment(Context.SECURITY_CREDENTIALS); // user password
        }
    }

    public void changePassword(String uid, String currentPassword, String newPassword) throws NamingException, GeneralSecurityException {
        String dn = getDN(uid);
        if (currentPassword != null) {
            authenticate(dn, currentPassword);
        }
        Object pwd = cfg.get(Context.SECURITY_CREDENTIALS);
        String rootCredential = String.valueOf(pwd);
        authenticate("cn=root," + baseDN, rootCredential);
        BasicAttribute ba = new BasicAttribute("userPassword", generateSSHA(newPassword));
        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, ba);
        m_ctx.modifyAttributes(dn, mods);
    }

    public static String n2q(String s) {
        return StringUtils.isBlank(s) ? "?" : s;
    }

    public String createUser(String uid, String pwd, String company, String org, Map<String, String> profile) throws NamingException, GeneralSecurityException {
        String userDN = this.getDN(uid);
        if (userDN != null) {
            throw new NamingException(uid + " exists");
        }

        userDN = new StringBuilder().append("uid=").append(uid)
                .append(",ou=").append(org)
                .append(",o=").append(company)
                .append(",ou=").append(tenantGroupName)
                .append(",").append(baseDN).toString();
        //System.out.println("createUser=" + userDN);
        BasicAttributes entry = new BasicAttributes();
        //ObjectClass attributes
        Attribute oc = new BasicAttribute("objectClass");
        entry.put(oc);
        oc.add("top");
        oc.add("person");
        oc.add("inetOrgPerson");
        oc.add("organizationalPerson");
        //uid:pwd        
        entry.put(new BasicAttribute("uid", uid));
        entry.put(new BasicAttribute("userPassword", generateSSHA(pwd)));
        //profile attributes
//        entry.put(new BasicAttribute("cn", name));
//        entry.put(new BasicAttribute("sn", n2q(lastName)));
//        entry.put(new BasicAttribute("givenName", n2q(firstName)));
//        entry.put(new BasicAttribute("mail", email));
        if (profile != null) {
            profile.forEach((key, value) -> {
                entry.put(new BasicAttribute(key, n2q(value)));
            });
        }

        try {
            m_ctx.createSubcontext(userDN, entry);
        } catch (NameAlreadyBoundException ex) {
            // ignore
        }
        return userDN;
    }

    public String createEntry(String dn, Set<String> objectClasses, Map<String, String> attributes) throws NamingException {
        BasicAttributes entry = new BasicAttributes();
        //ObjectClass attributes
        Attribute oc = new BasicAttribute("objectClass");
        entry.put(oc);
        objectClasses.forEach((objectClass) -> {
            oc.add(objectClass);
        });
        //profile attributes
        attributes.forEach((key, value) -> {
            entry.put(new BasicAttribute(key, n2q(value)));
        });

        try {
            m_ctx.createSubcontext(dn, entry);
        } catch (NameAlreadyBoundException ex) {
            // ignore
        }
        return dn;
    }

    public void updateEntryAttrs(String userDN, Map<String, String> attributes) throws GeneralSecurityException, NamingException {
        //String dn = getDN(userID);
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\tuserDN=").append(userDN);
            attributes.forEach((key, value) -> {
                sb.append("\n\t ").append(key).append("=").append(value);
            });
            log.debug(sb);
        }
        List<ModificationItem> modList = new ArrayList();
        attributes.forEach((key, value) -> {
            modList.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(key, value)));
        });
        int size = modList.size();
        if (size > 0) {
            ModificationItem[] mods = new ModificationItem[size];
            m_ctx.modifyAttributes(userDN, modList.toArray(mods));
        }
    }

    public void deleteUser(String uid) throws NamingException, GeneralSecurityException {
        log.debug(uid);
        List<Attributes> attrs = queryPerson("uid", uid);
        for (Attributes arrt : attrs) {
            String userDN = getAttr(arrt, DN);
            //System.out.println("deling="+userDN);
            updateUserGroups(userDN);
            m_ctx.unbind(userDN);
        }
//        String userDN = getDN(uid);
//        if (userDN != null) {
//            updateUserGroups(userDN);
//            m_ctx.unbind(userDN);
//        }
    }

    public void deleteEntry(String dn) throws NamingException, GeneralSecurityException {
        log.debug(dn);
        if (dn != null) {
            updateUserGroups(dn);
            m_ctx.unbind(dn);
        }
    }

    public void updateUserGroups(String userDN, String... newGroupDnList) throws GeneralSecurityException, NamingException {
        List<String>[] ret = parseAddedAndRemoved(getUserRoleGroups(userDN), newGroupDnList);
        List<String> addedList = ret[0];
        List<String> removedList = ret[1];

        // remove from existing groups
        for (String toBeRemovedGroupDn : removedList) {
            log.debug(userDN + ".remove=" + toBeRemovedGroupDn);
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute("uniqueMember", userDN));
            try {
                m_ctx.modifyAttributes(toBeRemovedGroupDn, mods);
            } catch (Throwable ex) {
                throw new GeneralSecurityException(ex.getMessage() + "\n\tremove: " + userDN + "\n\tfrom: " + toBeRemovedGroupDn, ex);
            }
        }

        // add to new groups
        for (String groupDN : addedList) {
            log.debug(userDN + ".add=" + groupDN);
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("uniqueMember", userDN));
            try {
                m_ctx.modifyAttributes(groupDN, mods);
            } catch (Throwable ex) {
                throw new GeneralSecurityException(ex.getMessage() + "\n\tadd: " + userDN + "\n\tto: " + groupDN, ex);
            }
        }
    }

    public List<Attributes> queryOrganization(final String o) throws NamingException {
        String sFilter;
        if (StringUtils.isBlank(o)) {
            sFilter = "(&(objectClass=organization)&(ou:dn:=" + tenantGroupName + "))";
        } else {
            sFilter = "(&(objectClass=organization)&(ou:dn:=" + tenantGroupName + ")&(o:dn:=" + o + "))";
        }
        return query(sFilter);
    }

    public List<Attributes> queryOrganizationUnit(final String o, final String ou) throws NamingException {
        //String sFilter = "(&(objectClass=organization)&(" + key + "=" + value + "))";
        String sFilter;
        if (StringUtils.isBlank(ou)) {
            sFilter = "(&(objectClass=organizationalUnit)&(ou:dn:=" + tenantGroupName + ")&(o:dn:=" + o + "))";
        } else {
            sFilter = "(&(objectClass=organizationalUnit)&(ou:dn:=" + tenantGroupName + ")&(o:dn:=" + o + ")&(ou:dn:=" + ou + "))";
        }
        return query(sFilter);
    }

    public List<Attributes> queryOrganizationUnitUsers(final String o, final String ou) throws NamingException {
        String sFilter;
        //sFilter = "(&(objectClass=inetOrgPerson)&(ou:dn:=" + tenantGroupName + ")&(o:dn:=" + dn + "))";
        if (StringUtils.isBlank(ou)) {
            sFilter = "(&(objectClass=inetOrgPerson)&(ou:dn:=" + tenantGroupName + ")&(o:dn:=" + o + "))";
        } else {
            sFilter = "(&(objectClass=inetOrgPerson)&(ou:dn:=" + tenantGroupName + ")&(o:dn:=" + o + ")&(ou:dn:=" + ou + "))";
        }
        return query(sFilter);
    }

    public List<String> queryGroupUsers(final String cn) throws NamingException {
        List<String> uids = new ArrayList();
        String sFilter = "(&(objectClass=groupOfUniqueNames)&(ou:dn:=groups)(cn=" + cn + "))";
        List<Attributes> groupAttrs = query(sFilter);
        for (Attributes groupAttr : groupAttrs) {
            Attribute a = groupAttr.get("uniqueMember");
            for (int i = 0; i < a.size(); i++) {
                uids.add((String) a.get(i));
            }
        }
        return uids;
    }
}
