package com.onehostel.access.model;

import java.util.List;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.access.constants.CollectionNames;
import com.onehostel.access.vo.ApiRule;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.tenancy.annotation.GlobalCollection;

import lombok.Getter;
import lombok.Setter;

/**
 * A registered privilege. Synced from PrivilegeCatalog at startup; {@code bit} is its position in the
 * privToken, assigned once and never reused, so tokens issued earlier keep their meaning.
 */
@Getter
@Setter
@Document(CollectionNames.PRIVILEGES)
@GlobalCollection(reason = "The privilege catalogue is the same for every organization")
public class Privilege extends BaseDocument {

    @Indexed(unique = true)
    private String code;

    /** 1-based position in the privToken. */
    @Indexed(unique = true)
    private Integer bit;

    private String name;

    private String module;

    private List<String> uiRoutes;

    private List<ApiRule> apis;

    /** No longer in the catalogue: never granted, but its bit stays taken. */
    private boolean retired;
}
