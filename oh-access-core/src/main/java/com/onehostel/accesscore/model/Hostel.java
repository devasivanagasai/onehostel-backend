package com.onehostel.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.constants.CollectionNames;
import com.onehostel.constants.CommonEnumHelper.Status;
import com.onehostel.model.helper.HostelAbstractDocument;

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
