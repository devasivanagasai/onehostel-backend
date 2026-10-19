package com.onehostel.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;

import com.onehostel.constants.CommonEnumHelper.EntityType;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.CommonEnumHelper.UserStatus;
import com.onehostel.model.HostelCommonSearch;
import com.onehostel.model.User;
import com.onehostel.repository.HostelCommonSearchRepository;

class UserSearchMirrorTest {

    @Test
    void userFieldsGoToTheirSlotsAndSecretsNever() {
        User tenant = new User("Priya Sharma", "priya.sharma@onehostel.com", "$2a$secret-hash", Role.HOSTEL_TENANT);
        tenant.setId("u1");
        tenant.setPasscodeHash("$2a$passcode-hash");
        tenant.setPhoneNumber("9701234567");
        tenant.setOrganizationId("org-1");
        tenant.setHostelId("h1");
        tenant.setRoomNumber("101");
        tenant.setBedId("ROOM-101-BED-A");
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setLocked(true);

        HostelCommonSearch row = UserSearchMapper.toSearchObject(tenant);

        assertThat(row.getEntityEnum()).isEqualTo(EntityType.USER);
        assertThat(row.getEntityModelKey()).isEqualTo("u1");
        assertThat(row.getOrganizationId()).isEqualTo("org-1");
        assertThat(row.getText1()).isEqualTo("u1");
        assertThat(row.getText2()).isEqualTo("HOSTEL_TENANT");
        assertThat(row.getText3()).isEqualTo("ACTIVE");
        assertThat(row.getText4()).isEqualTo("true");
        assertThat(row.getText5()).isEqualTo("priya.sharma@onehostel.com");
        assertThat(row.getText8()).isEqualTo("+91");
        assertThat(row.getText9()).isEqualTo("9701234567");
        assertThat(row.getText10()).isEqualTo("Priya Sharma");
        assertThat(row.getText11()).isEqualTo("h1");
        assertThat(row.getText12()).isEqualTo("ROOM-101-BED-A");
        assertThat(row.getText13()).isEqualTo("101");
        assertThat(row.getList1()).containsExactly("HOSTEL_READ");
        assertThat(row.toString() + row.getText6() + row.getText7()).doesNotContain("$2a$");
    }

    @Test
    void upsertReusesTheExistingRow() {
        HostelCommonSearchRepository repository = mock(HostelCommonSearchRepository.class);
        HostelCommonSearch existing = new HostelCommonSearch();
        existing.setId("row-1");
        when(repository.findByEntityModelKeyAndEntityEnum("u1", EntityType.USER)).thenReturn(Optional.of(existing));

        HostelCommonSearch row = new HostelCommonSearch();
        row.setEntityEnum(EntityType.USER);
        row.setEntityModelKey("u1");
        new SearchMirrorService(repository).upsert(row);

        ArgumentCaptor<HostelCommonSearch> saved = ArgumentCaptor.forClass(HostelCommonSearch.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("row-1");
        assertThat(saved.getValue().getUpdatedDateTime()).isNotNull();
    }

    @Test
    void listenerMirrorsSavesAndDeletesById() {
        SearchMirrorService mirror = mock(SearchMirrorService.class);
        UserSearchSyncListener listener = new UserSearchSyncListener(mirror);
        User user = new User("Rajesh Kumar", "rajesh.kumar@onehostel.com", "hash", Role.HOSTEL_OWNER);
        user.setId("u2");

        listener.onAfterSave(new AfterSaveEvent<>(user, new Document(), "C0001"));
        verify(mirror).upsert(any(HostelCommonSearch.class));

        listener.onAfterDelete(new AfterDeleteEvent<>(new Document("_id", "u2"), User.class, "C0001"));
        verify(mirror).delete(EntityType.USER, "u2");
    }

    @Test
    void bulkDeleteFiltersAreNotMistakenForIds() {
        SearchMirrorService mirror = mock(SearchMirrorService.class);
        UserSearchSyncListener listener = new UserSearchSyncListener(mirror);

        listener.onAfterDelete(new AfterDeleteEvent<>(new Document("_id", new Document("$in", "x")), User.class, "C0001"));

        verify(mirror, never()).delete(any(), any());
    }
}
