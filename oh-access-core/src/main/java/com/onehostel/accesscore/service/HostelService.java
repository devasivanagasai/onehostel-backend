package com.onehostel.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.onehostel.constants.CommonEnumHelper.Status;
import com.onehostel.dto.hostel.HostelRequest;
import com.onehostel.dto.hostel.HostelView;
import com.onehostel.exception.ErrorCode;
import com.onehostel.model.Hostel;
import com.onehostel.service.helper.HostelAbstractService;

@Service
public class HostelService extends HostelAbstractService<Hostel, HostelRequest, HostelView> {

    public HostelService(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Hostel.class);
    }

    @Override
    public String entityLabel() {
        return "Hostel";
    }

    @Override
    public Hostel dtoToEntity(HostelRequest dto) {
        Hostel hostel = new Hostel();
        updateEntity(dto, hostel);
        if (hostel.getStatus() == null) {
            hostel.setStatus(Status.ACTIVE);
        }
        return hostel;
    }

    @Override
    public void updateEntity(HostelRequest dto, Hostel hostel) {
        hostel.setName(dto.name().trim());
        hostel.setAddressLine(dto.addressLine().trim());
        hostel.setCity(dto.city().trim());
        hostel.setState(dto.state().trim());
        hostel.setPincode(dto.pincode());
        hostel.setContactPhone(dto.contactPhone());
        if (dto.status() != null) {
            hostel.setStatus(dto.status());
        }
    }

    @Override
    public HostelView entityToVO(Hostel hostel) {
        return HostelView.from(hostel);
    }

    /** Hostel names are unique within an organization (also enforced by a unique index). */
    @Override
    public void validate(HostelRequest dto, Hostel existing, String organizationId) {
        Criteria criteria = Criteria.where("organizationId").is(organizationId).and("name").is(dto.name().trim());
        if (existing != null) {
            criteria = criteria.and("id").ne(existing.getId());
        }
        if (mongoTemplate.exists(new Query(criteria), Hostel.class)) {
            throw ErrorCode.DUPLICATE_RECORD.raiseWithMessage(
                    "A hostel named \"" + dto.name().trim() + "\" already exists.");
        }
    }

    @Override
    public Set<String> sortableFields() {
        return Set.of("name", "city", "createdDateTime", "updatedDateTime");
    }

    @Override
    public List<String> keywordFields() {
        return List.of("name", "city", "pincode");
    }
}
