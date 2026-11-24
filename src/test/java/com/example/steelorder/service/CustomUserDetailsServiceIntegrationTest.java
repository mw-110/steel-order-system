package com.example.steelorder.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomUserDetailsServiceIntegrationTest {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("統合: 既存ユーザーをロードすると UserDetails にロールが設定される")
    void loadUserByUsername_returnsUserDetailsWithRole() {
        UserDetails user = customUserDetailsService.loadUserByUsername("sales01");

        assertThat(user.getUsername()).isEqualTo("sales01");
        assertThat(user.getPassword()).isNotBlank();
        assertThat(user.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_SALES");
    }

    @Test
    @DisplayName("統合: 存在しないユーザー名では UsernameNotFoundException")
    void loadUserByUsername_throwsWhenUserNotFound() {
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown-user"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ユーザーが見つかりません");
    }
}
