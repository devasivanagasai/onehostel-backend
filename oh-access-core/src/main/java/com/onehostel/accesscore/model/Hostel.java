package com.onehostel.accesscore.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.accesscore.model.helper.HostelAbstractDocument;
import com.onehostel.common.constants.CollectionNames;
import com.onehostel.common.constants.CommonEnumHelper.Status;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = CollectionNames.HOSTELS)
@CompoundIndex(name = "organization_name_unique", def = "{'organizationId': 1, 'name': 1}", unique = true)
public class Hostel extends HostelAbstractDocument {

    private String name;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private String contactPhone;
    private Status status;
}
