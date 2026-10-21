package com.onehostel.core.model.helper;

import java.io.Serializable;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.core.constants.EnumHelper.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAccessScope implements Serializable {

    private static final long serialVersionUID = 1L;

    @Field("AS0001")
    private String orgId;

    @Field("AS0002")
    private String hostelId;

    @Field("AS0003")
    private Role role;

    @Field("AS0004")
    private List<String> permissions;

    @Field("AS0005")
    private boolean isPrimary;
}