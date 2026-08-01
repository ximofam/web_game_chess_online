package com.ximofam.graduation_project.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class LuaScriptConfig {

    private String loadScript(String path) {
        try {
            return org.springframework.util.StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load script: " + path, e);
        }
    }

    private String getScriptWithUtils(String mainScriptPath, String... utils) {
        StringBuilder script = new StringBuilder();
        for (String util : utils) {
            script.append(loadScript(util)).append("\n");
        }
        script.append(loadScript(mainScriptPath));
        return script.toString();
    }

    @Bean
    public RedisScript<Long> presenceConnectScript() {
        return RedisScript.of(new ClassPathResource("lua/scripts/presence_connect.lua"), Long.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> presenceDisconnectScript() {
        return RedisScript.of(new ClassPathResource("lua/scripts/presence_disconnect.lua"), List.class);
    }

    @Bean
    public RedisScript<Long> presenceSetStatusScript() {
        return RedisScript.of(new ClassPathResource("lua/scripts/presence_set_status.lua"), Long.class);
    }

    @Bean
    public RedisScript<Long> createRoomScript() {
        return RedisScript.of(getScriptWithUtils("lua/scripts/create_room.lua", "lua/utils/status.lua", "lua/utils/rooms.lua", "lua/utils/user_presence.lua"), Long.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> searchLobbyScript() {
        return RedisScript.of(new ClassPathResource("lua/scripts/search_lobby.lua"), List.class);
    }

    @Bean
    public RedisScript<Long> joinRoomScript() {
        return RedisScript.of(getScriptWithUtils("lua/scripts/join_room.lua", "lua/utils/status.lua", "lua/utils/rooms.lua", "lua/utils/user_presence.lua"), Long.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> leaveRoomScript() {
        return RedisScript.of(getScriptWithUtils("lua/scripts/leave_room.lua", "lua/utils/status.lua", "lua/utils/rooms.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> deleteRoomScript() {
        return RedisScript.of(new ClassPathResource("lua/scripts/delete_room.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> playerReadyScript() {
        return RedisScript.of(getScriptWithUtils("lua/scripts/player_ready.lua", "lua/utils/status.lua", "lua/utils/rooms.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> startGameScript() {
        return RedisScript.of(getScriptWithUtils("lua/scripts/start_game.lua", "lua/utils/status.lua", "lua/utils/rooms.lua"), List.class);
    }
}
