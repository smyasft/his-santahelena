package co.santahelena.his.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import co.santahelena.his.security.MfaInterceptor;
import co.santahelena.his.security.PermisoInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final MfaInterceptor mfa;
    private final PermisoInterceptor permisos;

    public WebConfig(MfaInterceptor mfa, PermisoInterceptor permisos) {
        this.mfa = mfa;
        this.permisos = permisos;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] libres = {"/css/**", "/js/**", "/img/**", "/error", "/login", "/registro", "/favicon.ico"};
        registry.addInterceptor(mfa).addPathPatterns("/**").excludePathPatterns(libres);
        registry.addInterceptor(permisos).addPathPatterns("/**").excludePathPatterns(libres);
    }
}
