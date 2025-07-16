// Integration file: Auth

package com.strangequark.fileservice.repositorytests;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collectionuser.CollectionUser;
import com.strangequark.fileservice.collectionuser.CollectionUserRepository;
import com.strangequark.fileservice.collectionuser.CollectionUserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class CollectionUserRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private CollectionUserRepository collectionUserRepository;

    CollectionUser collectionUser;
    Collection collection;
    UUID userId;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        collection = new Collection("Test collection");
        collectionUser = new CollectionUser(collection, userId, CollectionUserRole.OWNER);

        testEntityManager.persistAndFlush(collection);
        testEntityManager.persistAndFlush(collectionUser);
    }

    @Test
    void findByUserIdAndCollectionIdTest() {
        CollectionUser c = collectionUserRepository.findByUserIdAndCollectionId(userId, collection.getId());

        Assertions.assertNotEquals(null, c);
        Assertions.assertEquals(collectionUser, c);
    }

    @Test
    void findCollectionsByUserIdTest() {
        List<Collection> collections = collectionUserRepository.findCollectionsByUserId(userId);

        Assertions.assertEquals(1, collections.size());
        Assertions.assertTrue(collections.contains(collection));
    }

    @Test
    void deleteCollectionUserTest() {
        collectionUserRepository.deleteCollectionUser(userId, collection.getId());

        Assertions.assertTrue(collection.getCollectionUsers().isEmpty());
        Assertions.assertNull(collectionUserRepository.findByUserIdAndCollectionId(userId, collection.getId()));
    }
}
