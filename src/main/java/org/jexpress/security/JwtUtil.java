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
package org.jexpress.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JwtUtil {

    public static String buildSigningKey(SignatureAlgorithm signatureAlgorithm) {
        final Key signingKey = Keys.secretKeyFor(signatureAlgorithm);
        return EncryptorUtil.keyToString(signingKey);
    }

    /**
     * <code>1. generate keypair: openssl genrsa -des3 -out keypair.pem 4096</code>
     * <code>2. export public key: openssl rsa -in keypair.pem -outform PEM -pubout -out public.pem</code>
     * <code>3. export private key: openssl rsa -in keypair.pem -out private_unencrypted.pem -outform PEM</code>
     * <code>4. encrypt and convert private key from PKCS#1 to PKCS#8: openssl pkcs8 -topk8 -inform PEM -outform PEM -in private_unencrypted.pem -out private.pem</code>
     *
     * @param signatureAlgorithm
     * @return
     */
    public static KeyPair buildSigningParsingKeyPair(SignatureAlgorithm signatureAlgorithm) {
        return Keys.keyPairFor(signatureAlgorithm);
    }

    public static Key parseSigningKey(String encodedKey) {
        //return EncryptorUtil.keyFromString(sk, signatureAlgorithm.getJcaName()); "HmacSHA256"
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return Keys.hmacShaKeyFor(decodedKey);
    }

    @Deprecated
    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, String jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT_091(signatureAlgorithm, keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }

    @Deprecated
    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, String jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        byte[] key = Base64.getDecoder().decode(jwtSigningKey);
        return createJWT_091(signatureAlgorithm, keyAlgorithm, key, builder, timeUnit, ttl);
    }

    @Deprecated
    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, byte[] jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT_091(signatureAlgorithm, keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }

    @Deprecated
    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, byte[] jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        Key privateKey = new SecretKeySpec(jwtSigningKey, 0, jwtSigningKey.length, keyAlgorithm);
        return createJWT_091(signatureAlgorithm, privateKey, builder, timeUnit, ttl);
    }

    @Deprecated
    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, Key privateKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT_091(signatureAlgorithm, privateKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }

    @Deprecated
    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, Key privateKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        //0. We will sign our JWT with our ApiKey secret
        //byte[] apiKeySecretBytes = parseSigningKey(jwtRootSigningKeyString);
        //The JWT signature algorithm we will be using to sign the token
        //Key signingKey = new SecretKeySpec(jwtSigningKey, signatureAlgorithm.getJcaName());

        //1. set ecpire time
        setJwtExpireTime(builder, timeUnit, ttl);

        //2. Let's set the JWT Claims
        builder.setIssuedAt(new Date());
        builder.signWith(signatureAlgorithm, privateKey);

        //3. Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }

    public static String createJWT(String keyAlgorithm, String jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT(keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }

    public static String createJWT(String keyAlgorithm, String jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        byte[] key = Base64.getDecoder().decode(jwtSigningKey);
        return createJWT(keyAlgorithm, key, builder, timeUnit, ttl);
    }

    public static String createJWT(String keyAlgorithm, byte[] jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT(keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }

    public static String createJWT(String keyAlgorithm, byte[] jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        Key privateKey = new SecretKeySpec(jwtSigningKey, 0, jwtSigningKey.length, keyAlgorithm);
        return createJWT(privateKey, builder, timeUnit, ttl);
    }

    public static void setJwtExpireTime(JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        if (ttl <= 0) {
            return;
        }
        long ttlMilsec = timeUnit.toMillis(ttl);
        if (ttlMilsec > 0) {// no expire if toMillis overflow
            long expireTimeMilsec = System.currentTimeMillis() + ttlMilsec;
            if (expireTimeMilsec > 0) {// no expire if (nowMillis + ttlMillis) overflow
                Date exp = new Date(expireTimeMilsec);
                builder.setExpiration(exp);
            }
        }
    }

    public static String createJWT(Key privateKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT(privateKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }

    public static String createJWT(Key privateKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        //0. We will sign our JWT with our ApiKey secret
        //byte[] apiKeySecretBytes = parseSigningKey(jwtRootSigningKeyString);
        //The JWT signature algorithm we will be using to sign the token
        //Key signingKey = new SecretKeySpec(jwtSigningKey, signatureAlgorithm.getJcaName());

        //1. set ecpire time
        setJwtExpireTime(builder, timeUnit, ttl);

        //2. Let's set the JWT Claims
        builder.setIssuedAt(new Date());
        //builder.signWith(signatureAlgorithm, privateKey);
        builder.signWith(privateKey);

        //3. Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }

    public static Jws<Claims> parseJWT(Key jwtRootSigningKey, String token) {
        JwtParser parser = Jwts.parserBuilder() // (1)
                .setSigningKey(jwtRootSigningKey) // (2)
                .build(); // (3)
        return parser.parseClaimsJws(token); // (4)
//        JwsHeader h = ret.getHeader();
//        return ret.getBody();
    }

    public static Jws<Claims> parseJWT(byte[] jwtRootSigningKey, String token) {
        JwtParser parser = Jwts.parserBuilder() // (1)
                .setSigningKey(jwtRootSigningKey) // (2)
                .build(); // (3)
        return parser.parseClaimsJws(token); // (4)
    }

    public static Jws<Claims> parseJWT(JwtParser parser, String token) {
        return parser.parseClaimsJws(token); // (4)
    }

}
