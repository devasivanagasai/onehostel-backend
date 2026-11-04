package com.onehostel.tenancy.model;

/**
 * A document that belongs to one organization. Its collection is tenant-scoped unless the class
 * carries {@link GlobalCollection}. BaseDocument in onehostel-common implements it.
 */
public interface TenantOwned {

    /** Name of the tenant field in every tenant-owned document. */
    String TENANT_FIELD = "organizationId";

    String getId();

    String getOrganizationId();

    void setOrganizationId(String organizationId);
}
