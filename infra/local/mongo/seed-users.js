// Seeds the bootstrap super admin that matches the user in keycloak/onehostel-realm.json
// (same keycloakUserId, username, email, and user_id attribute = _id). All other accounts are created
// top-down from the app (SuperAdmin -> Owner -> Manager -> Security/Tenant).
// Fields follow User / BaseDocument in onehostel-access-core (database onehostel_access, collection users).
// Safe to run repeatedly: the document is upserted by _id.

const target = db.getSiblingDB('onehostel_access');
const now = new Date();

function seedUser(u) {
  const access = { organizationId: u.organizationId, hostelId: u.hostelId, role: u.role, permissions: [] };
  Object.keys(access).forEach(k => access[k] === undefined && delete access[k]);

  const doc = {
    _id: u.id,                          // ULID; also the user_id attribute in Keycloak
    organizationId: u.organizationId,   // none for the platform-wide super admin
    username: u.username,
    email: u.email,
    mobileNumber: { countryCode: '+91', number: u.mobile },
    keycloakUserId: u.keycloakUserId,   // same id as in the realm file
    firstName: u.firstName,
    lastName: u.lastName,
    status: 'ACTIVE',
    locked: false,
    primaryAccess: access,
    accessScopes: [access],
    deleted: false,
    version: NumberLong('0'),
    createdAt: now,
    updatedAt: now,
    createdBy: 'seed',
    updatedBy: 'seed',
    _class: 'com.onehostel.access.model.User'
  };
  Object.keys(doc).forEach(k => doc[k] === undefined && delete doc[k]);

  target.getCollection('users').replaceOne({ _id: u.id }, doc, { upsert: true });
  print(`Seeded ${u.username} (${u.role})`);
}

seedUser({ id: '00000000000000000000000001', keycloakUserId: '6f1c2a9e-0000-4000-8000-000000000002', role: 'SUPER_ADMIN',
  username: 'super.admin', email: 'super.admin@onehostel.local', firstName: 'Super', lastName: 'Admin', mobile: '9000000002' });
