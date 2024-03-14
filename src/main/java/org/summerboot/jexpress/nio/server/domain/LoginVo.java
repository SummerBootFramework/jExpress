package org.summerboot.jexpress.nio.server.domain;

public class LoginVo {
    protected String username;
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
