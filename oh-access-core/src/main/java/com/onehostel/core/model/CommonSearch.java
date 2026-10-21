package com.onehostel.core.model;

import java.io.Serializable;
import java.util.List;

import com.onehostel.core.model.helper.OneHostelBaseDoc;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.onehostel.core.constants.CollectionNames;
import com.onehostel.core.constants.EnumHelper.EntityTypeEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@CompoundIndexes({
        // Primary query pattern: entityEnum + organizationId — used by every getAll query
        @CompoundIndex(name = "idx_entity_org",
                def = "{'entityEnum': 1, 'organizationId': 1}"),

        // Entity + org + descending updatedAt — covers sorted list queries
        @CompoundIndex(name = "idx_entity_org_updated",
                def = "{'entityEnum': 1, 'organizationId': 1, 'updated_at': -1}"),

        // Fast lookup for entity updates by key and entity type
        @CompoundIndex(name = "idx_modelkey_entity",
                def = "{'entityModelKey': 1, 'entityEnum': 1}"),

        // Hostel-level query pattern: covers hostel-scoped lookups
        @CompoundIndex(name = "idx_entity_hostel",
                def = "{'entityEnum': 1, 'hostelId': 1}")
})
@Document(collection = CollectionNames.COMMON_SEARCH)
public class CommonSearch extends OneHostelBaseDoc implements Serializable {

    @JsonIgnore
    private static final long serialVersionUID = 1L;


    @Indexed
    private EntityTypeEnum entityEnum;

    @Indexed
    private String entityModelKey;

    @Indexed
    private String organizationId;

    @Indexed
    private String hostelId;

    private String text1;
    private String text2;
    private String text3;
    private String text4;
    private String text5;
    private String text6;
    private String text7;
    private String text8;
    private String text9;
    private String text10;
    private String text11;
    private String text12;
    private String text13;
    private String text14;
    private String text15;
    private String text16;
    private String text17;
    private String text18;
    private String text19;
    private String text20;
    private String text21;
    private String text22;
    private String text23;
    private String text24;
    private String text25;
    private String text26;
    private String text27;
    private String text28;
    private String text29;
    private String text30;
    private String text31;
    private String text32;
    private String text33;
    private String text34;
    private String text35;

    private Object object1;
    private Object object2;
    private Object object3;
    private Object object4;
    private Object object5;
    private Object object6;
    private Object object7;
    private Object object8;

    private List<String> list1;
    private List<String> list2;
    private List<String> list3;

    private Decimal128 num1;

    private Boolean boolean1;

    private String assignedUserId;
}