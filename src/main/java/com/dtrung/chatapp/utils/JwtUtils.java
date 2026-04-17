package com.dtrung.chatapp.utils;

import com.dtrung.chatapp.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final RsaKeyProperties rsaKeyProperties;
    private final JwtEncoder jwtEncoder;

    @Value("${jwt.expiration-time}")
    protected Long EXPIRATION_TIME;

    public String generateToken(User user) {
        JwtClaimsSet jwtClaimsSet =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .expiresAt(Instant.now().plus(EXPIRATION_TIME, ChronoUnit.SECONDS))
                        .subject(getSubject(user))
                        .issuedAt(Instant.now())
                        .issuer("dtrung")
                        .claim("authorities", user.getAuthorities().stream().map(Object::toString).toList())
                        .build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSet)).getTokenValue();
    }
    private String getSubject(User user) {
        if(!user.getEmail().isBlank()) {
            return user.getEmail();
        }else{
            return user.getPhoneNumber();
        }
    }
    public SignedJWT validateToken(String token) throws ParseException, JOSEException {
        JWSVerifier jwsVerifier = new RSASSAVerifier(rsaKeyProperties.rsaPublicKey());
        SignedJWT signedJWT = SignedJWT.parse(token);
        boolean verified = signedJWT.verify(jwsVerifier);
        if (!verified) {
            throw new ParseException("Invalid JWT", 0);
        }
        return signedJWT;
    }
    public String getSubject(String token) throws ParseException, JOSEException {
        SignedJWT signedJWT = validateToken(token);
        return signedJWT.getJWTClaimsSet().getSubject();
    }
}
