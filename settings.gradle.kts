rootProject.name = "onehostel-backend"

// Composite build: each library and service is its own Gradle build. Libraries are substituted
// into the services automatically by their coordinates (com.onehostel:<name>).
//
// Service port register (each new service takes the next free number):
//   9001  onehostel-gateway      edge: JWT verification, identity headers, routing
//   9002  onehostel-access-core  auth, users, roles, privileges, workflow
//   9004  onehostel-resident     residents, admission, check-in/out, guardians
//   9005  onehostel-property     hostel -> block -> floor -> room -> bed
//   9006  onehostel-billing      fee plans, invoices, payments, refunds
// Libraries (no port): onehostel-tenancy, onehostel-common, onehostel-scheduler-core

includeBuild("build-logic")

includeBuild("onehostel-tenancy")
includeBuild("onehostel-common")
includeBuild("onehostel-scheduler-core")

includeBuild("onehostel-gateway")
includeBuild("onehostel-access-core")
includeBuild("onehostel-resident")
includeBuild("onehostel-property")
includeBuild("onehostel-billing")
