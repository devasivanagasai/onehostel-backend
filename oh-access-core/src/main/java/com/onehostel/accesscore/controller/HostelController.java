package com.onehostel.accesscore.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.accesscore.controller.helper.HostelCoreController;
import com.onehostel.accesscore.dto.hostel.HostelRequest;
import com.onehostel.accesscore.dto.hostel.HostelView;
import com.onehostel.accesscore.service.HostelService;
import com.onehostel.common.constants.ServiceConstants;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Hostels")
@RestController
@RequestMapping(ServiceConstants.HOSTEL_BASE)
public class HostelController extends HostelCoreController<HostelRequest, HostelView> {

    public HostelController(HostelService service) {
        super(service);
    }
}
