package com.ximofam.graduation_project.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class LuaScriptConfig {

    @Bean
    public RedisScript<Long> presenceConnectScript() {
        return RedisScript.of(new ClassPathResource("scripts/presence_connect.lua"), Long.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> presenceDisconnectScript() {
        return RedisScript.of(new ClassPathResource("scripts/presence_disconnect.lua"), List.class);
    }

    @Bean
    public RedisScript<Long> presenceSetStatusScript() {
        return RedisScript.of(new ClassPathResource("scripts/presence_set_status.lua"), Long.class);
    }

    @Bean
    public RedisScript<Long> createRoomScript() {
        return RedisScript.of(new ClassPathResource("scripts/create_room.lua"), Long.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> searchLobbyScript() {
        return RedisScript.of(new ClassPathResource("scripts/search_lobby.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> joinRoomScript() {
        return RedisScript.of(new ClassPathResource("scripts/join_room.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> leaveRoomScript() {
        return RedisScript.of(new ClassPathResource("scripts/leave_room.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> deleteRoomScript() {
        return RedisScript.of(new ClassPathResource("scripts/delete_room.lua"), List.class);
    }
}
