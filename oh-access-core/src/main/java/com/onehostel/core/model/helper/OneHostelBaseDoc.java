package com.onehostel.core.model.helper;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class OneHostelBaseDoc implements Serializable {

    @Id
    private String id;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;

    @CreatedBy
    @Field("created_by")
    private String createdBy;

    @LastModifiedBy
    @Field("updated_by")
    private String updatedBy;

    @Version
    private Long version;

    @Field("is_deleted")
    private boolean isDeleted = false;

    @Field("deleted_at")
    private Instant deletedAt;

    @Field("deleted_by")
    private String deletedBy;

    public void markDeleted(String deletedByUserId) {
        this.isDeleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
    }
}