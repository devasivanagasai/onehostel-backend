package com.onehostel.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.constants.CollectionNames;
import com.onehostel.constants.CommonEnumHelper.EntityType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Denormalized search row: one per mirrored record, in generic indexed slots, so list and
 * lookup screens query a single collection. What each slot holds depends on {@code entityEnum};
 * see the entity's mapper (e.g. UserSearchMapper). Written only by SearchMirrorService.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = CollectionNames.COMMON_SEARCH)
@CompoundIndexes({
        @CompoundIndex(name = "entity_key_unique", def = "{'entityEnum': 1, 'entityModelKey': 1}", unique = true),
        @CompoundIndex(name = "entity_org_updated", def = "{'entityEnum': 1, 'organizationId': 1, 'updatedDateTime': -1}"),
        @CompoundIndex(name = "entity_text5", def = "{'entityEnum': 1, 'text5': 1}"),
        @CompoundIndex(name = "entity_text9", def = "{'entityEnum': 1, 'text9': 1}")
})
public class HostelCommonSearch {

    @Id
    private String id;

    private EntityType entityEnum;

    /** Id of the mirrored record. */
    private String entityModelKey;

    private String organizationId;

    private String hostelId;

    private Instant updatedDateTime;

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

    private List<String> list1;
    private List<String> list2;
    private List<String> list3;

    private Double num1;
}
