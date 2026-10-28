package com.example.autodoc.test;

import com.example.autodoc.test.SequenceModel.Arrow;
import com.example.autodoc.test.SequenceModel.Item;
import com.example.autodoc.test.SequenceModel.Lane;
import com.example.autodoc.test.SequenceModel.Message;
import com.example.autodoc.test.SequenceModel.Note;
import com.example.autodoc.test.SequenceModel.Par;
import com.example.autodoc.test.SequenceModel.Participant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * A step on another participant gets a call and a return (a failed return, with the exception name, on
 * failure), a step on the same participant becomes a self-message, notes become notes, and overlapping
 * siblings form a par block.
 */
final class SequenceModelBuilder {

    static final String UPSTREAM = "UPSTREAM";

    private final Map<String, String> labels;
    private final AutodocSettings settings;

    SequenceModelBuilder(Map<String, String> labels, AutodocSettings settings) {
        this.labels = labels;
        this.settings = settings;
    }

    /**
     * The first root received the request; later roots are exception handlers, which run after it has
     * closed and render on its participant before the final arrow. The final arrow carries the status the
     * caller actually got. With no roots, the request was rejected before any step ran.
     */
    SequenceModel build(List<FlowRecorder.Step> roots, int status, String requestLine, Exception resolved) {
        String receiver = roots.isEmpty() ? settings.entryParticipant() : roots.get(0).participant;
        List<String> participants = new ArrayList<>(List.of(UPSTREAM, receiver));
        List<Item> items = new ArrayList<>();

        if (roots.isEmpty()) {
            items.add(new Message(UPSTREAM, receiver, requestLine, Arrow.CALL));
            items.add(new Note(List.of(receiver), "Rejected before controller — " + rejection(resolved)));
        } else {
            FlowRecorder.Step entry = roots.get(0);
            addNotes(entry, UPSTREAM, receiver, items);
            items.add(new Message(UPSTREAM, receiver, entry.action, Arrow.CALL));
            addSiblings(entry.children, receiver, participants, items);
            for (FlowRecorder.Step handler : roots.subList(1, roots.size())) {
                addStep(handler, receiver, participants, items);
            }
        }
        items.add(new Message(receiver, UPSTREAM, status + " " + HttpStatus.valueOf(status).getReasonPhrase(),
                Arrow.REPLY));
        return new SequenceModel(participants.stream().map(p -> new Participant(p, displayName(p))).toList(), items);
    }

    static String rejection(Exception resolved) {
        if (resolved instanceof MethodArgumentNotValidException invalid) {
            return invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
        return resolved == null ? "no flow steps recorded" : resolved.getClass().getSimpleName();
    }

    private String displayName(String participant) {
        if (UPSTREAM.equals(participant)) {
            return settings.upstreamLabel();
        }
        String label = labels.get(participant);
        if (label == null) {
            throw new IllegalStateException("No @FlowParticipant declares id " + participant);
        }
        return label;
    }

    private void addStep(FlowRecorder.Step step, String from, List<String> participants, List<Item> items) {
        String to = step.participant;
        if (!participants.contains(to)) {
            participants.add(to);
        }
        addNotes(step, from, to, items);
        items.add(new Message(from, to, step.action, Arrow.CALL));
        addSiblings(step.children, to, participants, items);
        if (!from.equals(to)) {
            Throwable error = step.context.getError();
            items.add(error == null
                    ? new Message(to, from, "response", Arrow.REPLY)
                    : new Message(to, from, error.getClass().getSimpleName(), Arrow.FAILED));
        }
    }

    /** A step's notes are evaluated before it runs, so they come just before its call. */
    private static void addNotes(FlowRecorder.Step step, String from, String to, List<Item> items) {
        List<String> over = from.equals(to) || UPSTREAM.equals(from) ? List.of(to) : List.of(from, to);
        step.notes.forEach(note -> items.add(new Note(over, note.text())));
    }

    /** Children in start order; runs of overlapping calls become a par block sorted by action. */
    private void addSiblings(List<FlowRecorder.Step> children, String from, List<String> participants,
                             List<Item> items) {
        List<FlowRecorder.Step> ordered = children.stream()
                .sorted(Comparator.comparingLong(s -> s.startSeq)).toList();
        int i = 0;
        while (i < ordered.size()) {
            List<FlowRecorder.Step> group = new ArrayList<>(List.of(ordered.get(i)));
            long groupEnd = ordered.get(i).stopSeq;
            int j = i + 1;
            while (j < ordered.size() && ordered.get(j).startSeq < groupEnd) {
                group.add(ordered.get(j));
                groupEnd = Math.max(groupEnd, ordered.get(j).stopSeq);
                j++;
            }
            if (group.size() == 1) {
                addStep(group.get(0), from, participants, items);
            } else {
                group.sort(Comparator.comparing(s -> s.action));
                List<Lane> lanes = new ArrayList<>();
                for (FlowRecorder.Step step : group) {
                    List<Item> lane = new ArrayList<>();
                    addStep(step, from, participants, lane);
                    lanes.add(new Lane(step.action, lane));
                }
                items.add(new Par(lanes));
            }
            i = j;
        }
    }
}
