package com.battle.code.data;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SensitiveDataProperties.class)
public class SensitiveDataConfig {
}
