package com.example.security.config;

import com.example.logging.MDC.MDCFilter;
import com.example.outbound.auth.CustomOAuth2OutConnector;
import com.example.service.auth.jwt.JwtAccessDeniedHandler;
import com.example.service.auth.jwt.JwtAuthenticationEntryPoint;
import com.example.service.auth.jwt.JwtAuthenticationFilter;
import com.example.service.auth.jwt.JwtTokenProvider;
import com.example.service.auth.oauth2.OAuth2AuthenticationFailureHandler;
import com.example.service.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private final CustomOAuth2OutConnector customOAuth2OutConnector;

    private final OAuth2AuthenticationFailureHandler auth2AuthenticationFailureHandler;

    private final OAuth2AuthenticationSuccessHandler auth2AuthenticationSuccessHandler;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfiguration)throws Exception{
        return authConfiguration.getAuthenticationManager();
    }

    private MvcRequestMatcher mvc(HandlerMappingIntrospector introspect, String pattern) {
        return new MvcRequestMatcher(introspect, pattern);
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(HandlerMappingIntrospector introspect) {
        return web -> web
                .httpFirewall(defaultFireWell())
                .ignoring()
                .requestMatchers(
                        mvc(introspect, "/images/**"),
                        mvc(introspect, "/js/**"),
                        mvc(introspect, "/font/**"),
                        mvc(introspect, "/webfonts/**"),
                        mvc(introspect, "/istatic/**"),
                        mvc(introspect, "/main/**"),
                        mvc(introspect, "/webjars/**"),
                        mvc(introspect, "/dist/**"),
                        mvc(introspect, "/plugins/**"),
                        mvc(introspect, "/css/**"),
                        mvc(introspect, "/favicon.ico"),
                        mvc(introspect, "/h2-console/**"),
                        mvc(introspect, "/vendor/**"),
                        mvc(introspect, "/scss/**")
                );
    }

    //chain filter
    @Bean
    public SecurityFilterChain configure(HttpSecurity http, HandlerMappingIntrospector introspect)throws Exception{

        http
                .cors(httpSecurityCorsConfigurer
                        -> httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MDCFilter(), JwtAuthenticationFilter.class)
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry
                        -> authorizationManagerRequestMatcherRegistry
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/member/**").permitAll()
                        .requestMatchers("/api/category/**").permitAll()
                        .requestMatchers("/api/attach/**").permitAll()
                        .requestMatchers("/api/schedule/**").permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(oauth2-> oauth2.userInfoEndpoint(userInfo->userInfo.userService(customOAuth2OutConnector))
                        .successHandler(auth2AuthenticationSuccessHandler)
                        .failureHandler(auth2AuthenticationFailureHandler))
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer -> httpSecurityExceptionHandlingConfigurer
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*")); // 사용할 CRUD 메소드 등록
        configuration.setAllowedHeaders(List.of("*")); // 사용할 Header 등록
        configuration.setExposedHeaders(Arrays.asList("authorization", "refreshToken"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public HttpFirewall defaultFireWell(){
        return new DefaultHttpFirewall();
    }

}
