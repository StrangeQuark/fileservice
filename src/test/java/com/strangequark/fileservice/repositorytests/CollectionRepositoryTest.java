package com.strangequark.fileservice.repositorytests;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collection.CollectionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class CollectionRepositoryTest {
    static {
        System.setProperty("ENCRYPTION_KEY", "AA1A2A8C0E4F76FB3C13F66225AAAC42");
    }

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private CollectionRepository collectionRepository;

    Collection collection;

    @BeforeEach
    void setup() {
        collection = new Collection("Test collection");

        testEntityManager.persistAndFlush(collection);
    }

    @Test
    void findByNameTest() {
        Assertions.assertTrue(collectionRepository.findByName("Test collection").isPresent());
    }

    @Test
    void findAllTest() {
        List<Collection> collections = collectionRepository.findAll();

        Assertions.assertFalse(collections.isEmpty());
    }
}
