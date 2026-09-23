package com.rescue.mesh.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kết quả kiểm tra tính hợp lệ có cấu trúc của MeshPacket.
 */
public class ValidationResult {

    private final boolean valid;
    private final List<String> violations;

    private ValidationResult(boolean valid, List<String> violations) {
        this.valid = valid;
        this.violations = Collections.unmodifiableList(violations != null ? violations : Collections.emptyList());
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, Collections.emptyList());
    }

    public static ValidationResult fail(List<String> violations) {
        return new ValidationResult(false, violations);
    }

    public static ValidationResult fail(String singleViolation) {
        List<String> list = new ArrayList<>();
        list.add(singleViolation);
        return new ValidationResult(false, list);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getViolations() {
        return violations;
    }

    public String getFirstViolation() {
        return violations.isEmpty() ? null : violations.get(0);
    }

    @Override
    public String toString() {
        return valid ? "ValidationResult{VALID}" : "ValidationResult{INVALID: " + String.join("; ", violations) + "}";
    }
}
