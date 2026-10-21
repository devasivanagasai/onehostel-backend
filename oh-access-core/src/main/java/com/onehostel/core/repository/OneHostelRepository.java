package com.onehostel.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import com.onehostel.core.model.helper.OneHostelBaseDoc;

@NoRepositoryBean
public interface OneHostelRepository<T extends OneHostelBaseDoc, ID> extends MongoRepository<T, ID> {

    Optional<T> findByIdAndIsDeletedFalse(ID id);

    List<T> findAllByIsDeletedFalse();
}