package com.onehostel.tenancy.mongo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import org.bson.codecs.configuration.CodecRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Wraps the application's MongoDatabaseFactory so every collection it hands out is a
 * {@link TenantScopedCollection}. Installed by TenancyAutoConfiguration around Boot's factory.
 */
public class TenantMongoDatabaseFactory implements MongoDatabaseFactory {

    private final MongoDatabaseFactory delegate;
    private final TenantBoundary boundary;

    public TenantMongoDatabaseFactory(MongoDatabaseFactory delegate, TenantBoundary boundary) {
        this.delegate = delegate;
        this.boundary = boundary;
    }

    /** The unwrapped factory, for code that must bypass tenant scoping (the violation sink). */
    public MongoDatabaseFactory getDelegate() {
        return delegate;
    }

    @Override
    public MongoDatabase getMongoDatabase() throws DataAccessException {
        return scoped(delegate.getMongoDatabase());
    }

    @Override
    public MongoDatabase getMongoDatabase(String dbName) throws DataAccessException {
        return scoped(delegate.getMongoDatabase(dbName));
    }

    @Override
    public PersistenceExceptionTranslator getExceptionTranslator() {
        return delegate.getExceptionTranslator();
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return delegate.getCodecRegistry();
    }

    @Override
    public ClientSession getSession(ClientSessionOptions options) {
        return delegate.getSession(options);
    }

    @Override
    public MongoDatabaseFactory withSession(ClientSession session) {
        return new TenantMongoDatabaseFactory(delegate.withSession(session), boundary);
    }

    @Override
    public boolean isTransactionActive() {
        return delegate.isTransactionActive();
    }

    /** A MongoDatabase whose getCollection() results, and those of its with*() copies, are scoped. */
    private MongoDatabase scoped(MongoDatabase database) {
        return (MongoDatabase) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {MongoDatabase.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(database, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (result instanceof MongoCollection<?> collection) {
                        return TenantScopedCollection.wrap(collection, boundary);
                    }
                    if (result instanceof MongoDatabase copy && copy != database) {
                        return scoped(copy);
                    }
                    return result;
                });
    }
}
