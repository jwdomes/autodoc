package com.example.flowcap.test;

import com.example.flowcap.test.SequenceModel.Item;
import com.example.flowcap.test.SequenceModel.Message;
import com.example.flowcap.test.SequenceModel.Note;
import com.example.flowcap.test.SequenceModel.Par;
import java.util.List;

/** A sequence model as Mermaid sequenceDiagram text. */
final class MermaidSequence {

    /** @param comment shown as a %% line under the header, e.g. the scenario description; null for none */
    static String render(SequenceModel model, String comment) {
        StringBuilder out = new StringBuilder("sequenceDiagram\n");
        if (comment != null) {
            out.append("    %% ").append(comment).append('\n');
        }
        model.participants().forEach(p -> out.append("    participant %s as %s\n".formatted(p.id(), escape(p.label()))));
        items(model.items(), "    ", out);
        return out.toString();
    }

    private static void items(List<Item> items, String indent, StringBuilder out) {
        for (Item item : items) {
            switch (item) {
                case Message m -> out.append(indent).append("%s%s%s: %s\n".formatted(m.from(), arrow(m), m.to(),
                        escape(m.text())));
                case Note n -> out.append(indent).append("Note over %s: %s\n".formatted(String.join(",", n.over()),
                        escape(n.text())));
                case Par p -> {
                    for (int k = 0; k < p.lanes().size(); k++) {
                        out.append(indent).append(k == 0 ? "par " : "and ").append(escape(p.lanes().get(k).label()))
                                .append('\n');
                        items(p.lanes().get(k).items(), indent + "    ", out);
                    }
                    out.append(indent).append("end\n");
                }
            }
        }
    }

    private static String arrow(Message m) {
        return switch (m.arrow()) {
            case CALL -> "->>";
            case REPLY -> "-->>";
            case FAILED -> "--x";
        };
    }

    private static String escape(String text) {
        return text.replace(";", "#59;").replace("\"", "#quot;");
    }

    private MermaidSequence() {
    }
}
