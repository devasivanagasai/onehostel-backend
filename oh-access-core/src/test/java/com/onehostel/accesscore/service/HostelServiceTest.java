package com.onehostel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.CommonEnumHelper.Status;
import com.onehostel.dto.helper.CommonRequestObject;
import com.onehostel.dto.helper.ListFilter;
import com.onehostel.dto.hostel.HostelRequest;
import com.onehostel.dto.hostel.HostelView;
import com.onehostel.exception.ErrorCode;
import com.onehostel.exception.OneHostelException;
import com.onehostel.model.Hostel;
import com.onehostel.security.AuthenticatedUser;

/** Tenancy and write rules of HostelAbstractService, exercised through HostelService. */
class HostelServiceTest {

    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser("a1", "admin@x.com", Role.SUPER_ADMIN, null, null, null);
    private static final AuthenticatedUser OWNER =
            new AuthenticatedUser("o1", "owner@x.com", Role.HOSTEL_OWNER, "org-a", null, null);
    private static final AuthenticatedUser TENANT =
            new AuthenticatedUser("t1", "tenant@x.com", Role.HOSTEL_TENANT, "org-a", null, null);

    private MongoTemplate mongoTemplate;
    private HostelService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        service = new HostelService(mongoTemplate);
        when(mongoTemplate.insert(any(Hostel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.save(any(Hostel.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ownerCreatesInTheOrganizationFromTheirToken() {
        HostelView view = service.create(request(null, null, null), OWNER);

        assertThat(view.organizationId()).isEqualTo("org-a");
        assertThat(view.status()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void ownerCannotCreateInAnotherOrganization() {
        assertFails(() -> service.create(request(null, null, "org-b"), OWNER), ErrorCode.FORBIDDEN);
        verify(mongoTemplate, never()).insert(any(Hostel.class));
    }

    @Test
    void adminMustNameTheOrganization() {
        assertFails(() -> service.create(request(null, null, null), ADMIN), ErrorCode.BAD_REQUEST);
        assertThat(service.create(request(null, null, "org-b"), ADMIN).organizationId()).isEqualTo("org-b");
    }

    @Test
    void tenantCanReadButNotWrite() {
        assertFails(() -> service.create(request(null, null, null), TENANT), ErrorCode.FORBIDDEN);
        assertFails(() -> service.delete("h1", TENANT), ErrorCode.FORBIDDEN);

        when(mongoTemplate.findOne(any(Query.class), eq(Hostel.class))).thenReturn(stored("h1", "org-a", 0L));
        assertThat(service.inquire("h1", TENANT).id()).isEqualTo("h1");
    }

    @Test
    void accountWithoutOrganizationIsRefused() {
        AuthenticatedUser unlinked = new AuthenticatedUser("o2", "o2@x.com", Role.HOSTEL_OWNER, null, null, null);
        assertFails(() -> service.inquire("h1", unlinked), ErrorCode.FORBIDDEN);
    }

    @Test
    void readsAreScopedToTheUsersOrganization() {
        // Mongo finds nothing because the query includes organizationId = org-a.
        assertFails(() -> service.inquire("h-of-org-b", OWNER), ErrorCode.ENTITY_NOT_FOUND);

        Query query = capturedFindOne();
        assertThat(query.getQueryObject().toJson()).contains("\"organizationId\": \"org-a\"").contains("h-of-org-b");
    }

    @Test
    void adminReadsAreNotScoped() {
        when(mongoTemplate.findOne(any(Query.class), eq(Hostel.class))).thenReturn(stored("h1", "org-b", 0L));

        service.inquire("h1", ADMIN);

        assertThat(capturedFindOne().getQueryObject().toJson()).doesNotContain("organizationId");
    }

    @Test
    void staleVersionIsRejected() {
        when(mongoTemplate.findOne(any(Query.class), eq(Hostel.class))).thenReturn(stored("h1", "org-a", 3L));

        assertFails(() -> service.update(request("h1", 2L, null), OWNER), ErrorCode.OPTIMISTIC_LOCK_FAILED);
        verify(mongoTemplate, never()).save(any(Hostel.class));
    }

    @Test
    void updateKeepsTenancy() {
        when(mongoTemplate.findOne(any(Query.class), eq(Hostel.class))).thenReturn(stored("h1", "org-a", 3L));

        HostelView view = service.update(request("h1", 3L, "org-b"), OWNER);

        assertThat(view.organizationId()).isEqualTo("org-a");
    }

    @Test
    void duplicateNameInTheSameOrganizationIsRejected() {
        when(mongoTemplate.exists(any(Query.class), eq(Hostel.class))).thenReturn(true);

        assertFails(() -> service.create(request(null, null, null), OWNER), ErrorCode.DUPLICATE_RECORD);
    }

    @Test
    void keywordIsMatchedLiterallyWithinTheOrganization() {
        when(mongoTemplate.find(any(Query.class), eq(Hostel.class))).thenReturn(List.of());
        CommonRequestObject<ListFilter> request = CommonRequestObject.of(new ListFilter("a.*b", null));

        service.getAll(request, OWNER);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Hostel.class));
        String json = captor.getValue().getQueryObject().toJson();
        assertThat(json).contains("org-a").contains("\\\\Qa.*b\\\\E");
        assertThat(captor.getValue().getLimit()).isEqualTo(10);
    }

    private Query capturedFindOne() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(captor.capture(), eq(Hostel.class));
        return captor.getValue();
    }

    private static HostelRequest request(String id, Long version, String organizationId) {
        return new HostelRequest(id, version, organizationId, " Sunrise Hostel ", "12 MG Road", "Bengaluru",
                "Karnataka", "560001", "9876543210", null);
    }

    private static Hostel stored(String id, String organizationId, Long version) {
        Hostel hostel = new Hostel();
        hostel.setId(id);
        hostel.setOrganizationId(organizationId);
        hostel.setVersion(version);
        hostel.setName("Sunrise Hostel");
        return hostel;
    }

    private static void assertFails(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(OneHostelException.class)
                .extracting(ex -> ((OneHostelException) ex).getErrorCode())
                .isEqualTo(expected);
    }
}
