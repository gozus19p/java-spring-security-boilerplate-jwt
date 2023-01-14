package com.example.security.demo.service.authentication;

import com.example.security.demo.service.authentication.exception.HttpStatusException;
import com.example.security.demo.service.userdetails.MyUserDetailsService;
import com.example.security.demo.service.userdetails.dto.MyUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author Manuel Gozzi
 */
@Slf4j
@Service
public class AuthenticationService {

    private final MyUserDetailsService myUserDetailsService;

    private final JwtService jwtService;

    @Autowired
    public AuthenticationService(MyUserDetailsService myUserDetailsService, JwtService jwtService) {
        this.myUserDetailsService = myUserDetailsService;
        this.jwtService = jwtService;
    }

    public ResponseEntity<Void> login(HttpServletRequest request) {

        BasicAuthCredentials basicAuthCredentials = this.parseBasicCredentials(request);

        MyUserDetails user = (MyUserDetails) this.myUserDetailsService.loadUserByUsername(basicAuthCredentials.getUsername());
        if (this.myUserDetailsService.getPasswordEncoder().matches(basicAuthCredentials.getPassword(), user.getPassword())) {

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    user.getPassword(),
                    user.getAuthorities()
            );
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(authentication);

            String token = this.jwtService.create(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token))
                    .build();
        } else {

            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials provided");
        }
    }

    public void logout(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null)
            throw new HttpStatusException(HttpStatus.NOT_ACCEPTABLE, "No session found, the user can't log out");

        session.invalidate();
    }

    private BasicAuthCredentials parseBasicCredentials(HttpServletRequest request) {

        String authorization = request.getHeader("Authorization");
        if (authorization == null)
            throw new HttpStatusException(HttpStatus.NOT_ACCEPTABLE, "No authorization header has been provided");

        if (!authorization.startsWith("Basic "))
            throw new HttpStatusException(HttpStatus.NOT_ACCEPTABLE, "The provided authorization is not in form of Basic");

        try {

            String[] usernameAndPassword = new String(
                    Base64.getDecoder()
                            .decode(authorization.replace("Basic ", "")),
                    StandardCharsets.UTF_8
            ).split(":");

            return new BasicAuthCredentials(usernameAndPassword[0], usernameAndPassword[1]);
        } catch (Exception e) {

            log.error(e.getMessage());
            throw new HttpStatusException(HttpStatus.NOT_ACCEPTABLE, "Malformed Basic Authorization has been provided");
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static final class BasicAuthCredentials {

        private String username;

        private String password;
    }
}
