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
package org.summerframework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class JwtUtil {

    public static String buildSigningKey(SignatureAlgorithm signatureAlgorithm) {
        final Key privateKey = MacProvider.generateKey(signatureAlgorithm);
        final byte[] secretBytes = privateKey.getEncoded();
        return Base64.getEncoder().encodeToString(secretBytes);
    }

    public static Key parseSigningKey(String encodedKey, String keyAlgorithm) {
        //SHA-256, AES
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, keyAlgorithm);
    }

    public static String createJWT(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, String jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT(signatureAlgorithm, keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }

    public static String createJWT(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, String jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        byte[] key = Base64.getDecoder().decode(jwtSigningKey);
        return createJWT(signatureAlgorithm, keyAlgorithm, key, builder, timeUnit, ttl);
    }

    public static String createJWT(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, byte[] jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT(signatureAlgorithm, keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }
    
    public static String createJWT(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, byte[] jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        Key privateKey = new SecretKeySpec(jwtSigningKey, 0, jwtSigningKey.length, keyAlgorithm);
        return createJWT(signatureAlgorithm, privateKey, builder, timeUnit, ttl);
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

    public static String createJWT(SignatureAlgorithm signatureAlgorithm, Key privateKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
        JwtBuilder builder = Jwts.builder()
                .setId(id)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(audience);
        return createJWT(signatureAlgorithm, privateKey, builder, TimeUnit.SECONDS, ttlSeconds);
    }
    
    public static String createJWT(SignatureAlgorithm signatureAlgorithm, Key privateKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
        //0. We will sign our JWT with our ApiKey secret
        //byte[] apiKeySecretBytes = parseSigningKey(jwtRootSigningKeyString);
        //The JWT signature algorithm we will be using to sign the token
        //Key signingKey = new SecretKeySpec(jwtSigningKey, signatureAlgorithm.getJcaName());

        //1. set ecpire time
        setJwtExpireTime(builder, timeUnit, ttl);

        //2. Let's set the JWT Claims
        builder.signWith(signatureAlgorithm, privateKey);

        //3. Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }

    public static Claims parseJWT(String jwtRootSigningKey, String token) {
        return Jwts.parser()
                .setSigningKey(jwtRootSigningKey)
                .parseClaimsJws(token).getBody();
    }

    public static Claims parseJWT(Key jwtRootSigningKey, String token) {
        return Jwts.parser()
                .setSigningKey(jwtRootSigningKey)
                .parseClaimsJws(token).getBody();
    }

    public static Claims parseJWT(byte[] jwtRootSigningKey, String token) {
        return Jwts.parser()
                .setSigningKey(jwtRootSigningKey)
                .parseClaimsJws(token).getBody();
    }

}
