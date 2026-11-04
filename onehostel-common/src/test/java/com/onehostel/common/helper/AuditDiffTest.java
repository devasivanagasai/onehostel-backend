package com.onehostel.common.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.onehostel.common.model.AuditLog.FieldChange;

class AuditDiffTest {

    @Test
    void reportsChangedFieldsAsDottedPathsAndSkipsBookkeeping() {
        Document before = new Document("_id", "1").append("version", 1).append("name", "Sunrise")
                .append("address", new Document("city", "Hyderabad"));
        Document after = new Document("_id", "1").append("version", 2).append("name", "Sunrise")
                .append("address", new Document("city", "Pune"));

        Map<String, FieldChange> changes = AuditDiff.diff(before, after);

        assertThat(changes).containsOnlyKeys("address.city");
        assertThat(changes.get("address.city")).isEqualTo(new FieldChange("Hyderabad", "Pune"));
    }

    @Test
    void masksPersonalDataAndRemovesSecrets() {
        Document before = new Document("mobileNumber", "9848012345").append("passwordHash", "abc");
        Document after = new Document("mobileNumber", "9701234567").append("passwordHash", "def")
                .append("email", "priya.sharma@onehostel.com");

        Map<String, FieldChange> changes = AuditDiff.diff(before, after);

        assertThat(changes.get("mobileNumber")).isEqualTo(new FieldChange("******2345", "******4567"));
        assertThat(changes.get("passwordHash")).isEqualTo(new FieldChange(PiiMasker.REDACTED, PiiMasker.REDACTED));
        assertThat(changes.get("email")).isEqualTo(new FieldChange(null, "p***@onehostel.com"));
    }
}
