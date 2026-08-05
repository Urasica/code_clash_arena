package com.battle.code.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "cca.security.rate-limit")
@Validated
public class RateLimitProperties {

    @NotNull
    private Duration window = Duration.ofMinutes(1);
    @Min(1)
    private int login = 10;
    @Min(1)
    private int guest = 5;
    @Min(1)
    private int compile = 20;
    @Min(1)
    private int run = 10;

    public Duration getWindow() { return window; }
    public void setWindow(Duration window) { this.window = window; }
    public int getLogin() { return login; }
    public void setLogin(int login) { this.login = login; }
    public int getGuest() { return guest; }
    public void setGuest(int guest) { this.guest = guest; }
    public int getCompile() { return compile; }
    public void setCompile(int compile) { this.compile = compile; }
    public int getRun() { return run; }
    public void setRun(int run) { this.run = run; }
}
