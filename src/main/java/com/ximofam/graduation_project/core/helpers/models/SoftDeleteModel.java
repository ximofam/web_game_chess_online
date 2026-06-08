package com.ximofam.graduation_project.core.helpers.models;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@SQLDelete(sql = "UPDATE {h-domain} SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class SoftDeleteModel extends BaseModel {
    @Column(name = "deleted_at")
    protected Instant deletedAt;

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}