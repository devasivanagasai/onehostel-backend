package com.onehostel.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.onehostel.core.model.helper.SearchableDocument;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.onehostel.core.constants.CollectionNames;
import com.onehostel.core.constants.EnumHelper.EntityTypeEnum;
import com.onehostel.core.constants.EnumHelper.UserStatus;
import com.onehostel.core.model.helper.MobileNumber;
import com.onehostel.core.model.helper.OneHostelBaseDoc;
import com.onehostel.core.model.helper.UserAccessScope;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_keycloak", def = "{'US0019': 1}"),
        @CompoundIndex(name = "idx_user_hostel_status", def = "{'US0003': 1, 'US0008': 1}")
})
@Document(collection = CollectionNames.USERS)
public class UserDoc extends OneHostelBaseDoc implements SearchableDocument, Serializable {

    @JsonIgnore
    private static final long serialVersionUID = 1L;


    @Field("US0001")
    private boolean isAdmin;

    @Field("US0002")
    private List<String> roleIds;

    @Field("US0003")
    private String hostelId;

    @Field("US0004")
    private String roomNumber;

    @Indexed
    @Field("US0005")
    @NotNull(message = "mobile number is required")
    @JsonPropertyDescription("This represents mobile number")
    private MobileNumber mobileNumber;

    @Indexed
    @Field("US0006")
    @NotEmpty(message = "email is required")
    @JsonPropertyDescription("This represents email id")
    private String email;

    @Field("US0007")
    private boolean isLocked;

    @JsonPropertyDescription("This denotes user status")
    @NotNull(message = "Status is required")
    @Field("US0008")
    private UserStatus status;

    @Field("US0009")
    private List<String> userGroupList;

    @Field("US0010")
    private String bedId;

    @Field("US0011")
    private String staffOnboardId;

    @Field("US0012")
    private Set<String> linkedDevices;

    @Field("US0013")
    private String tokenVoId;

    @Field("US0014")
    private List<String> customerId;

    @Field("US0015")
    private boolean superAdmin;

    @Field("US0016")
    private String buildingBlock;

    @Field("US0017")
    private long lastTokenExpiryTime;

    @Field("US0018")
    private String password;

    @Field("US0019")
    private String keycloakUserId;

    @Field("US0020")
    private String userName;

    @Field("US0021")
    private String guardianDetailsId;

    @Field("US0022")
    private String firstName;

    @Field("US0023")
    private String lastName;

    @Indexed
    @Field("US0024")
    private String userId;

    @Field("US0025")
    private List<UserAccessScope> userAccessScopes;

    @Field("US0026")
    private UserAccessScope primaryAccess;

    @Field("US0027")
    private String profilePicture;

    @Field("US0028")
    private String profileBanner;

    @Field("US0029")
    private String bio;

    @Field("US0030")
    private String hometownState;

    @Field("US0031")
    private String hometownDistrict;

    @Field("US0032")
    private String occupation;

    @Field("US0033")
    private List<String> interests;

    @Field("US0034")
    private LocalDate dateOfBirth;

    @Field("US0035")
    private Boolean dateOfBirthUpdated;

    @Field("US0036")
    private String emergencyContactName;

    @Field("US0037")
    private MobileNumber emergencyContactMobile;

    @Field("US0038")
    private String residentType;

    @Field("US0039")
    private String residentAdmissionId;

    @Field("US0040")
    private Instant passwordChangedAt;

    @Transient
    private List<String> privilegesList;

    @Transient
    private String deviceId;

    @JsonIgnore
    public CommonSearch getSearchObject() {
        CommonSearch cs = new CommonSearch();
        cs.setEntityEnum(EntityTypeEnum.USER);
        cs.setEntityModelKey(this.getId());
        cs.setOrganizationId(this.getPrimaryAccess() != null ? this.getPrimaryAccess().getOrgId() : null);
        cs.setHostelId(this.getHostelId());

        cs.setCreatedAt(this.getCreatedAt());
        cs.setCreatedBy(this.getCreatedBy());
        cs.setUpdatedAt(this.getUpdatedAt());
        cs.setUpdatedBy(this.getUpdatedBy());

        cs.setText1(this.getId());
        cs.setText2(this.getUserId());
        cs.setText3(this.getStatus() != null ? this.getStatus().name() : null);
        cs.setText4(String.valueOf(this.isLocked()));
        cs.setText5(this.getEmail());
        cs.setText6(String.valueOf(this.isSuperAdmin()));

        if (this.getMobileNumber() != null) {
            cs.setText7(this.getMobileNumber().getCountryCode());
            cs.setText8(this.getMobileNumber().getNumber());
        }

        cs.setText9(this.getUserName());
        cs.setText10(this.getFirstName());
        cs.setText11(this.getLastName());
        cs.setText12(this.getRoomNumber());
        cs.setText13(this.getBedId());
        cs.setText14(this.getKeycloakUserId());

        if (this.getRoleIds() != null && !this.getRoleIds().isEmpty()) {
            cs.setList1(this.getRoleIds());
        }

        return cs;
    }
}