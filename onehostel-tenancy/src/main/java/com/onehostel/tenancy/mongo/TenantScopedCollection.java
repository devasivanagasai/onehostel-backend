package com.onehostel.tenancy.mongo;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.onehostel.tenancy.constants.TenancyEnums.TenancyMode;
import com.onehostel.tenancy.exception.TenantBoundaryViolationException;
import com.onehostel.tenancy.helper.TenantContext;
import com.onehostel.tenancy.model.TenantOwned;

/**
 * A MongoCollection whose filters are AND-ed with the caller's tenant filter at the BSON level.
 * Everything MongoTemplate, repositories, derived queries and the fluent API do ends up here,
 * so a feature that forgets the organizationId criterion still cannot reach another tenant's data.
 *
 * <ul>
 *   <li>find, countDocuments, distinct: tenant and ancestors</li>
 *   <li>update*, replaceOne, delete*, findOneAnd*: the tenant only</li>
 *   <li>aggregate: a $match on the tenant goes first (a $lookup into another tenant collection is NOT scoped)</li>
 *   <li>insertOne/insertMany: organizationId is checked, and stamped when missing</li>
 *   <li>bulkWrite, estimatedDocumentCount, watch, mapReduce: refused in ENFORCE unless the context is unscoped</li>
 * </ul>
 */
final class TenantScopedCollection implements InvocationHandler {

    private static final Set<String> READS = Set.of("find", "countDocuments", "distinct");
    private static final Set<String> WRITES = Set.of("updateOne", "updateMany", "replaceOne", "deleteOne",
            "deleteMany", "findOneAndDelete", "findOneAndUpdate", "findOneAndReplace");
    private static final Set<String> REFUSED = Set.of("bulkWrite", "estimatedDocumentCount", "watch", "mapReduce");

    private final MongoCollection<?> delegate;
    private final String collectionName;
    private final TenantBoundary boundary;

    private TenantScopedCollection(MongoCollection<?> delegate, TenantBoundary boundary) {
        this.delegate = delegate;
        this.collectionName = delegate.getNamespace().getCollectionName();
        this.boundary = boundary;
    }

    @SuppressWarnings("unchecked")
    static <T> MongoCollection<T> wrap(MongoCollection<T> collection, TenantBoundary boundary) {
        if (Proxy.isProxyClass(collection.getClass())
                && Proxy.getInvocationHandler(collection) instanceof TenantScopedCollection) {
            return collection;
        }
        return (MongoCollection<T>) Proxy.newProxyInstance(TenantScopedCollection.class.getClassLoader(),
                new Class<?>[] {MongoCollection.class}, new TenantScopedCollection(collection, boundary));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        Object[] arguments = args == null ? new Object[0] : args;

        if (method.getDeclaringClass() == Object.class || boundary.modeFor(collectionName) == TenancyMode.OFF) {
            return rewrapIfCollection(call(method, arguments));
        }
        if (READS.contains(name)) {
            return withFilter(method, arguments, false);
        }
        if (WRITES.contains(name)) {
            return withFilter(method, arguments, true);
        }
        if (name.equals("aggregate")) {
            return aggregate(method, arguments);
        }
        if (name.equals("insertOne") || name.equals("insertMany")) {
            checkInserts(arguments);
            return call(method, arguments);
        }
        if (REFUSED.contains(name)) {
            if (boundary.modeFor(collectionName) == TenancyMode.ENFORCE && !TenantContext.isUnscoped()) {
                throw new TenantBoundaryViolationException(name + " on tenant collection " + collectionName
                        + " cannot be tenant-scoped; run it inside TenantContext.callUnscoped with explicit organizationId filters");
            }
            return call(method, arguments);
        }
        return rewrapIfCollection(call(method, arguments));
    }

    /** Puts the tenant filter in the filter position, calling the overload with a filter when there is none. */
    private Object withFilter(Method method, Object[] args, boolean write) throws Throwable {
        int filterIndex = sessionOffset(method) + (method.getName().equals("distinct") ? 1 : 0);
        Class<?>[] types = method.getParameterTypes();
        boolean hasFilter = filterIndex < types.length && types[filterIndex] == Bson.class;
        Object original = hasFilter ? args[filterIndex] : null;

        Optional<Document> tenant = boundary.tenantFilter(collectionName, method.getName(), original, write);
        if (tenant.isEmpty()) {
            return call(method, args);
        }
        Bson scoped = original == null ? tenant.get() : Filters.and((Bson) original, tenant.get());
        if (hasFilter) {
            Object[] scopedArgs = args.clone();
            scopedArgs[filterIndex] = scoped;
            return call(method, scopedArgs);
        }
        // find(), find(Class), countDocuments(), distinct(field, Class), ... -> the same call with a filter
        List<Class<?>> withFilterTypes = new ArrayList<>(Arrays.asList(types));
        withFilterTypes.add(filterIndex, Bson.class);
        List<Object> withFilterArgs = new ArrayList<>(Arrays.asList(args));
        withFilterArgs.add(filterIndex, scoped);
        Method overload = MongoCollection.class.getMethod(method.getName(), withFilterTypes.toArray(Class<?>[]::new));
        return call(overload, withFilterArgs.toArray());
    }

    private Object aggregate(Method method, Object[] args) throws Throwable {
        int pipelineIndex = sessionOffset(method);
        Optional<Document> tenant = boundary.tenantFilter(collectionName, "aggregate", null, false);
        if (tenant.isEmpty()) {
            return call(method, args);
        }
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", tenant.get()));
        for (Object stage : (List<?>) args[pipelineIndex]) {
            pipeline.add((Bson) stage);
        }
        Object[] scopedArgs = args.clone();
        scopedArgs[pipelineIndex] = pipeline;
        return call(method, scopedArgs);
    }

    private void checkInserts(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof List<?> documents) {
                documents.forEach(this::checkInsert);
            } else if (arg instanceof Map<?, ?>) {
                checkInsert(arg);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkInsert(Object document) {
        if (!(document instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> fields = (Map<String, Object>) document;
        Object current = fields.get(TenantOwned.TENANT_FIELD);
        String tenantId = boundary.tenantForWrite(collectionName, current == null ? null : current.toString());
        if (tenantId != null && current == null) {
            fields.put(TenantOwned.TENANT_FIELD, tenantId);
        }
    }

    private Object rewrapIfCollection(Object result) {
        // withReadPreference(), withDocumentClass(), ... return a new collection that must stay scoped
        return result instanceof MongoCollection<?> collection ? wrap(collection, boundary) : result;
    }

    private Object call(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static int sessionOffset(Method method) {
        Class<?>[] types = method.getParameterTypes();
        return types.length > 0 && types[0] == ClientSession.class ? 1 : 0;
    }
}
