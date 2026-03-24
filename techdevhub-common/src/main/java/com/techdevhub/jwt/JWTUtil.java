package com.techdevhub.jwt;


import com.techdevhub.config.JwtProperties;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@RequiredArgsConstructor
public class JWTUtil {
    private final JwtProperties jwtProperties;
    private SecretKey secretKey;
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }
    public String gengerateToken(Long userId){
        return gengerateToken(userId, Map.of());
    }
    public String gengerateToken(Long userId, Map<String, Object> claims){
        if(userId == null) {
            throw new BusinessException(ErrorCode.TOKEN_GENGERATE_FAILED);
        }
        Date now = new Date();
        Date expireAt = new Date(now.getTime()+ jwtProperties.getExpiration());
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(secretKey)
                .compact();
    }

    private String removeTokenPrefix(String token){
        if(!StringUtils.hasText(token)){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String trimmedToken = token.trim();
        if(StringUtils.hasText(jwtProperties.getTokenPrefix()) && trimmedToken.startsWith(jwtProperties.getTokenPrefix())){
            return trimmedToken.substring(jwtProperties.getTokenPrefix().length()).trim();
        }
        return trimmedToken;
    }

    public Claims parseToken(String token){
        String actualToken = removeTokenPrefix(token);
        try{
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(actualToken)
                    .getBody();
        }catch (ExpiredJwtException e){
            throw new BusinessException(ErrorCode.TOKENEXPIRED);
        }catch (JwtException | IllegalArgumentException e){
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    public Boolean validateToken(String token){
        parseToken(token);
        return true;
    }
    public Long getUserId(String token){
        String subject = parseToken(token).getSubject();
        try{
            return Long.valueOf(subject);
        }catch (NumberFormatException e){
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
