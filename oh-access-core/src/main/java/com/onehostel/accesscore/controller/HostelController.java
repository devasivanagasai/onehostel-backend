package com.onehostel.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.constants.ServiceConstants;
import com.onehostel.controller.helper.HostelCoreController;
import com.onehostel.dto.hostel.HostelRequest;
import com.onehostel.dto.hostel.HostelView;
import com.onehostel.service.HostelService;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Hostels")
@RestController
@RequestMapping(ServiceConstants.HOSTEL_BASE)
public class HostelController extends HostelCoreController<HostelRequest, HostelView> {

    public HostelController(HostelService service) {
        super(service);
    }
}
