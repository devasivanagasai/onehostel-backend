package com.onehostel.core.service;

import java.util.List;

import com.onehostel.core.dto.CommonRequestObject;
import com.onehostel.core.model.helper.OneHostelBaseDoc;

public interface OneHostelCoreService<T extends OneHostelBaseDoc, REQ, RES> {

    RES create(CommonRequestObject<REQ> request);

    RES findById(String id);

    RES update(String id, CommonRequestObject<REQ> request);

    void delete(String id);

    List<RES> findAll();
}