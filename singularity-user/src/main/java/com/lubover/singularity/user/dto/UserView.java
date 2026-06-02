package com.lubover.singularity.user.dto;

import com.lubover.singularity.user.entity.User;

public class UserView {

    private Long id;
    private String username;
    private String nickname;
    private String role;

    public static UserView from(User user) {
        UserView view = new UserView();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setRole(user.getRole());
        return view;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
