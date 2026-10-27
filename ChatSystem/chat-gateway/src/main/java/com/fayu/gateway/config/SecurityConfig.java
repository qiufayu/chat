package com.fayu.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;

import java.security.KeyPair;

@Configuration
public class SecurityConfig {

    @Bean
    public KeyPair jwtSigner(JwtProperties jwtProperties){
        // 获取秘钥工长
        KeyStoreKeyFactory keyStoreKeyFactory = new KeyStoreKeyFactory(jwtProperties.getLocation(),
                jwtProperties.getPassword().toCharArray());
        //读取钥匙对
        return keyStoreKeyFactory.getKeyPair(jwtProperties.getAlias(),
                jwtProperties.getPassword().toCharArray());
    }
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

}
