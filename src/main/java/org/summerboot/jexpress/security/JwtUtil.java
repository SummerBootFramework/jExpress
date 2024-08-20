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
package org.summerboot.jexpress.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import io.jsonwebtoken.security.SignatureAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JwtUtil {

    public static String buildSigningKey(MacAlgorithm signatureAlgorithm) {
        SecretKey key = signatureAlgorithm.key().build();
        return EncryptorUtil.keyToString(key);
    }

    public static Key parseSigningKey(String encodedKey) {
        //return EncryptorUtil.keyFromString(sk, signatureAlgorithm.getJcaName()); "HmacSHA256"
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return Keys.hmacShaKeyFor(decodedKey);
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
        return signatureAlgorithm.keyPair().build();
    }

//    @Deprecated
//    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, String jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
//        JwtBuilder builder = Jwts.builder()
//                .setId(id)
//                .setIssuer(issuer)
//                .setSubject(subject)
//                .setAudience(audience);
//        return createJWT_091(signatureAlgorithm, keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
//    }
//
//    @Deprecated
//    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, String jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
//        byte[] key = Base64.getDecoder().decode(jwtSigningKey);
//        return createJWT_091(signatureAlgorithm, keyAlgorithm, key, builder, timeUnit, ttl);
//    }
//
//    @Deprecated
//    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, byte[] jwtSigningKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
//        JwtBuilder builder = Jwts.builder()
//                .setId(id)
//                .setIssuer(issuer)
//                .setSubject(subject)
//                .setAudience(audience);
//        return createJWT_091(signatureAlgorithm, keyAlgorithm, jwtSigningKey, builder, TimeUnit.SECONDS, ttlSeconds);
//    }

//    @Deprecated
//    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, String keyAlgorithm, byte[] jwtSigningKey, JwtBuilder builder, TimeUnit timeUnit, int ttl) {
//        Key privateKey = new SecretKeySpec(jwtSigningKey, 0, jwtSigningKey.length, keyAlgorithm);
//        return createJWT_091(signatureAlgorithm, privateKey, builder, timeUnit, ttl);
//    }
//
//    @Deprecated
//    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, Key privateKey, String id, String issuer, String subject, String audience, int ttlSeconds) {
//        JwtBuilder builder = Jwts.builder()
//                .setId(id)
//                .setIssuer(issuer)
//                .setSubject(subject)
//                .setAudience(audience);
//        return createJWT_091(signatureAlgorithm, privateKey, builder, TimeUnit.SECONDS, ttlSeconds);
//    }
//
//    @Deprecated
//    public static String createJWT_091(SignatureAlgorithm signatureAlgorithm, Key privateKey, JwtBuilder builder, TimeUnit timeUnit, Duration ttl) {
//        //0. We will sign our JWT with our ApiKey secret
//        //byte[] apiKeySecretBytes = parseSigningKey(jwtRootSigningKeyString);
//        //The JWT signature algorithm we will be using to sign the token
//        //Key signingKey = new SecretKeySpec(jwtSigningKey, signatureAlgorithm.getJcaName());
//
//        //1. set ecpire time
//        setJwtExpireTime(builder, ttl);
//
//        //2. Let's set the JWT Claims
//        builder.setIssuedAt(new Date());
//        builder.signWith(signatureAlgorithm, privateKey);
//
//        //3. Builds the JWT and serializes it to a compact, URL-safe string
//        return builder.compact();
//    }

    public static String createJWT(String keyAlgorithm, String jwtSigningKey, int ttlSeconds, String id, String issuer, String subject, Collection<String> audience) {
        JwtBuilder builder = Jwts.builder()
                .id(id)
                .issuer(issuer)
                .subject(subject);
        builder.audience().add(audience);
        return createJWT(keyAlgorithm, jwtSigningKey, builder, Duration.ofSeconds(ttlSeconds));
    }

    public static String createJWT(String keyAlgorithm, String jwtSigningKey, JwtBuilder builder, Duration ttl) {
        byte[] key = Base64.getDecoder().decode(jwtSigningKey);
        return createJWT(keyAlgorithm, key, builder, ttl);
    }

    public static String createJWT(String keyAlgorithm, byte[] jwtSigningKey, int ttlSeconds, String id, String issuer, String subject, Collection<String> audience) {
        JwtBuilder builder = Jwts.builder()
                .id(id)
                .issuer(issuer)
                .subject(subject);
        builder.audience().add(audience);
        return createJWT(keyAlgorithm, jwtSigningKey, builder, Duration.ofSeconds(ttlSeconds));
    }

    public static String createJWT(String keyAlgorithm, byte[] jwtSigningKey, JwtBuilder builder, Duration ttl) {
        Key privateKey = new SecretKeySpec(jwtSigningKey, 0, jwtSigningKey.length, keyAlgorithm);
        return createJWT(privateKey, builder, ttl);
    }

    public static void setJwtExpireTime(JwtBuilder builder, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        long ttlMilsec = ttl.toMillis();
        if (ttlMilsec > 0) {// no expire if toMillis overflow
            long expireTimeMilsec = System.currentTimeMillis() + ttlMilsec;
            if (expireTimeMilsec > 0) {// no expire if (nowMillis + ttlMillis) overflow
                Date exp = new Date(expireTimeMilsec);
                builder.expiration(exp);
            }
        }
    }

    public static String createJWT(Key privateKey, int ttlSeconds, String id, String issuer, String subject, Collection<String> audience) {
        JwtBuilder builder = Jwts.builder()
                .id(id)
                .issuer(issuer)
                .subject(subject);
        builder.audience().add(audience);
        return createJWT(privateKey, builder, Duration.ofSeconds(ttlSeconds));
    }

    public static String createJWT(Key privateKey, JwtBuilder builder, Duration ttl) {
        setJwtExpireTime(builder, ttl);
        builder.issuedAt(new Date()).signWith(privateKey);
        // Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }

    public static Jws<Claims> parseJWT(Key verifyKey, String token) {
        if (verifyKey instanceof SecretKey) {
            return parseJWT((SecretKey) verifyKey, token);
        } else if (verifyKey instanceof PublicKey) {
            return parseJWT((PublicKey) verifyKey, token);
        } else {
            throw new IllegalArgumentException("Unsupported Key type: " + verifyKey.getClass().getName());
        }
    }

    public static Jws<Claims> parseJWT(SecretKey jwtRootSigningKey, String token) {
        JwtParser parser = Jwts.parser() // (1)
                .verifyWith(jwtRootSigningKey) // (2)
                .build(); // (3)
        return parseJWT(parser, token); // (4)
    }

    public static Jws<Claims> parseJWT(PublicKey publicKey, String token) {
        JwtParser parser = Jwts.parser() // (1)
                .verifyWith(publicKey) // (2)
                .build(); // (3)
        return parseJWT(parser, token); // (4)
    }

    /*public static Jws<Claims> parseJWT(byte[] jwtRootSigningKey, String token) {
        JwtParser parser = Jwts.parser() // (1)
                .verifyWith(jwtRootSigningKey) // (2)
                .build(); // (3)
        return parser.parseClaimsJws(token); // (4)
    }*/

    public static Jws<Claims> parseJWT(JwtParser parser, String token) {
        //return parser.parseClaimsJws(token); // (4)
        return parser.parseSignedClaims(token);
    }

}
