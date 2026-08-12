package com.ximofam.graduation_project.common.helpers.models;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@SQLRestriction("deleted_at IS NULL")
public class SoftDeleteModel extends BaseModel {
    @Column(name = "deleted_at")
    protected Instant deletedAt;
}