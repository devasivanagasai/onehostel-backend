package com.onehostel.service.search;

import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import com.onehostel.constants.CommonEnumHelper.EntityType;
import com.onehostel.model.User;

import lombok.RequiredArgsConstructor;

/**
 * Keeps the user rows of HostelCommonSearch in step with the users collection on every save
 * and delete through Spring Data. Bulk updates (updateMulti) raise no events; code doing those
 * must re-sync itself, as UserMigration does.
 */
@Component
@RequiredArgsConstructor
public class UserSearchSyncListener extends AbstractMongoEventListener<User> {

    private final SearchMirrorService searchMirrorService;

    @Override
    public void onAfterSave(AfterSaveEvent<User> event) {
        searchMirrorService.upsert(UserSearchMapper.toSearchObject(event.getSource()));
    }

    /** Only deletes by id are mirrored; the event carries the delete's filter, not the user. */
    @Override
    public void onAfterDelete(AfterDeleteEvent<User> event) {
        Object id = event.getSource().get("_id");
        if (id != null && !(id instanceof Document)) {
            searchMirrorService.delete(EntityType.USER, id.toString());
        }
    }
}
