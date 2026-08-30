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
    // 已知泄露的默认密钥（曾硬编码于 JwtProperties）。任何环境一旦退回它即可被伪造身份，
    // 故显式拒绝，作为"删除默认值"后的双保险，防止有人重新加回默认。
    private static final String LEAKED_DEFAULT_SECRET = "TechDevHubDefaultSecretKeyForJwtAuth2026";

    @PostConstruct
    public void init() {
        String secret = jwtProperties.getSecretKey();
        // fail-fast：密钥缺失/过短/使用已泄露默认均直接拒启（fail-closed）。
        // HS256 要求 ≥256bit；生产必须通过 TECHDEVHUB_JWT_SECRETKEY 注入强密钥。
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT secret 未配置或长度不足 32 字符，请设置 TECHDEVHUB_JWT_SECRETKEY");
        }
        if (LEAKED_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT secret 不可使用已泄露的默认密钥 TechDevHubDefaultSecretKeyForJwtAuth2026，"
                            + "请通过 TECHDEVHUB_JWT_SECRETKEY 注入独立强密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
                    .getPayload();
        }catch (ExpiredJwtException e){
            throw new BusinessException(ErrorCode.TOKENEXPIRED);
        }catch (JwtException | IllegalArgumentException e){
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
    public Long getUserId(String token){
        String subject = parseToken(token).getSubject();
        try{
            return Long.valueOf(subject);
        }catch (NumberFormatException e){
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * 读取 token 中的 isAdmin claim（登录时按 user_info.status==1 写入）。
     * 老 token（不含该 claim）或解析失败一律视为非管理员，向下兼容。
     */
    public Boolean getIsAdmin(String token){
        Object value = parseToken(token).get("isAdmin");
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }
}
