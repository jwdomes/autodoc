package com.example.flowcap.test;

import com.example.flowcap.FlowParticipant;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads participant ids and labels from {@link FlowParticipant} constants. */
public final class FlowParticipants {

    record Declaration(String id, String label, String field) {
    }

    /** Participant id to label, ordered by id. */
    public static Map<String, String> labels(JavaClasses classes) {
        Map<String, String> labels = new TreeMap<>();
        for (Declaration d : declarations(classes)) {
            if (labels.put(d.id(), d.label()) != null) {
                throw new IllegalStateException("Participant id declared twice: " + d.id());
            }
        }
        return labels;
    }

    static List<Declaration> declarations(JavaClasses classes) {
        List<Declaration> declarations = new ArrayList<>();
        for (var javaClass : classes) {
            for (JavaField field : javaClass.getFields()) {
                field.tryGetAnnotationOfType(FlowParticipant.class).ifPresent(participant ->
                        declarations.add(new Declaration(constantValue(field), participant.label(), field.getFullName())));
            }
        }
        return declarations;
    }

    private static String constantValue(JavaField field) {
        try {
            Field reflected = field.reflect();
            reflected.setAccessible(true);
            return (String) reflected.get(null);
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
            throw new IllegalStateException("@FlowParticipant must be on a static String constant: " + field.getFullName(), e);
        }
    }

    private FlowParticipants() {
    }
}
