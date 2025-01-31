package org.summerboot.jexpress.nio.server.domain;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class LoginVo {
    @NotNull
    @NotEmpty
    protected String username;

    @NotNull
    @NotEmpty
    protected String password;

    public LoginVo() {
    }

    public LoginVo(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
