package com.onehostel.common.helper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.bson.Document;

import com.onehostel.common.model.AuditLog.FieldChange;

/**
 * Field-level difference between two stored documents (as Mongo wrote them), flattened to dotted paths
 * and PII-masked. Bookkeeping fields (_id, version, audit stamps) are left out.
 */
public final class AuditDiff {

    private static final Set<String> IGNORED = Set.of("_id", "_class", "version", "createdAt", "createdBy",
            "updatedAt", "updatedBy");

    private AuditDiff() {
    }

    public static Map<String, FieldChange> diff(Document before, Document after) {
        Map<String, Object> flatBefore = flatten(before);
        Map<String, Object> flatAfter = flatten(after);
        Set<String> paths = new TreeSet<>(flatBefore.keySet());
        paths.addAll(flatAfter.keySet());

        Map<String, FieldChange> changes = new LinkedHashMap<>();
        for (String path : paths) {
            Object oldValue = flatBefore.get(path);
            Object newValue = flatAfter.get(path);
            if (!Objects.equals(oldValue, newValue)) {
                changes.put(path, new FieldChange(PiiMasker.mask(path, oldValue), PiiMasker.mask(path, newValue)));
            }
        }
        return changes;
    }

    private static Map<String, Object> flatten(Document document) {
        Map<String, Object> flat = new LinkedHashMap<>();
        if (document != null) {
            flatten("", document, flat);
        }
        return flat;
    }

    private static void flatten(String prefix, Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (prefix.isEmpty() && IGNORED.contains(entry.getKey())) {
                continue;
            }
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Document nested) {
                flatten(path, nested, target);
            } else {
                target.put(path, entry.getValue());
            }
        }
    }
}
