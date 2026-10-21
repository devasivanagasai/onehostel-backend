// Seeds the bootstrap super admin that matches the user in keycloak/onehostel-realm.json
// (same keycloakUserId, username and email). All other accounts are created top-down from the app
// (SuperAdmin -> Owner -> Manager -> Security/Tenant).
// Field codes follow UserDoc / OneHostelBaseDoc in oh-access-core (collection C0001 = USERS).
// Safe to run repeatedly: the document is upserted by _id.

const target = db.getSiblingDB('onehostel_db');
const now = new Date();

function seedUser(u) {
  const access = {
    AS0001: u.orgId,            // orgId (none for the platform-wide super admin)
    AS0002: u.hostelId,         // hostelId
    AS0003: u.role,             // role
    AS0004: [],                 // permissions
    AS0005: true                // isPrimary
  };
  Object.keys(access).forEach(k => access[k] === undefined && delete access[k]);

  const doc = {
    _id: u.id,
    US0001: u.role === 'SUPER_ADMIN',                     // isAdmin
    US0002: [u.role],                                     // roleIds
    US0003: u.hostelId,                                   // hostelId
    US0004: u.roomNumber,                                 // roomNumber
    US0005: { MN0001: '+91', MN0002: u.mobile },          // mobileNumber
    US0006: u.email,                                      // email
    US0007: false,                                        // isLocked
    US0008: 'ACTIVE',                                     // status
    US0010: u.bedId,                                      // bedId
    US0015: u.role === 'SUPER_ADMIN',                     // superAdmin
    US0019: u.keycloakUserId,                             // keycloakUserId (same id as in the realm file)
    US0020: u.userName,                                   // userName
    US0022: u.firstName,                                  // firstName
    US0023: u.lastName,                                   // lastName
    US0024: u.id,                                         // userId
    US0025: [access],                                     // userAccessScopes
    US0026: access,                                       // primaryAccess
    is_deleted: false,
    created_at: now,
    updated_at: now,
    created_by: 'seed',
    updated_by: 'seed',
    _class: 'com.onehostel.core.model.UserDoc'
  };
  Object.keys(doc).forEach(k => doc[k] === undefined && delete doc[k]);

  target.getCollection('C0001').replaceOne({ _id: u.id }, doc, { upsert: true });
  print(`Seeded ${u.userName} (${u.role})`);
}

seedUser({ id: 'USR-SUPER-001', keycloakUserId: '6f1c2a9e-0000-4000-8000-000000000002', role: 'SUPER_ADMIN',
  userName: 'super.admin', email: 'super.admin@onehostel.local', firstName: 'Super', lastName: 'Admin', mobile: '9000000002' });
