package com.onehostel.service.search;

import com.onehostel.constants.CommonEnumHelper.EntityType;
import com.onehostel.constants.GlobalConstants;
import com.onehostel.model.HostelCommonSearch;
import com.onehostel.model.User;

/**
 * User slots in HostelCommonSearch. Never mirrors secrets (password or passcode hashes).
 *
 * <pre>
 * text1  user id          text6  username          text11 hostel id
 * text2  role             text8  country code      text12 bed id
 * text3  status           text9  mobile number     text13 room number
 * text4  locked flag      text10 full name         list1  permissions
 * text5  email
 * </pre>
 */
public final class UserSearchMapper {

    private UserSearchMapper() {
    }

    public static HostelCommonSearch toSearchObject(User user) {
        HostelCommonSearch row = new HostelCommonSearch();
        row.setEntityEnum(EntityType.USER);
        row.setEntityModelKey(user.getId());
        row.setOrganizationId(user.getOrganizationId());
        row.setHostelId(user.getHostelId());

        row.setText1(user.getId());
        row.setText2(user.getRole() != null ? user.getRole().name() : null);
        row.setText3(user.getStatus() != null ? user.getStatus().name() : null);
        row.setText4(String.valueOf(user.isLocked()));
        row.setText5(user.getEmail());
        row.setText6(user.getUsername());
        row.setText8(user.getPhoneNumber() != null ? GlobalConstants.DEFAULT_COUNTRY_CODE : null);
        row.setText9(user.getPhoneNumber());
        row.setText10(user.getName());
        row.setText11(user.getHostelId());
        row.setText12(user.getBedId());
        row.setText13(user.getRoomNumber());
        row.setList1(user.getRole() != null
                ? user.getRole().permissions().stream().map(Enum::name).sorted().toList()
                : null);
        return row;
    }
}
