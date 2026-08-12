package com.ximofam.graduation_project.users.repositories.projections;

import java.util.UUID;

public interface UserSimpleProjection {
    UUID getId();

    String getUsername();

    String getAvatarUrl();
}
