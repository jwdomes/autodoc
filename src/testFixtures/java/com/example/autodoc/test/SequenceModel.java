package com.example.autodoc.test;

import java.util.List;

/** One scenario's sequence diagram, independent of how it's drawn (Mermaid text or SVG). */
record SequenceModel(List<Participant> participants, List<Item> items) {

    record Participant(String id, String label) {
    }

    sealed interface Item permits Message, Note, Par {
    }

    enum Arrow { CALL, REPLY, FAILED }

    record Message(String from, String to, String text, Arrow arrow) implements Item {

        boolean self() {
            return from.equals(to);
        }
    }

    /** @param over one participant id, or two for a note spanning both */
    record Note(List<String> over, String text) implements Item {
    }

    /** Calls that overlapped in time, one lane per call. */
    record Par(List<Lane> lanes) implements Item {
    }

    record Lane(String label, List<Item> items) {
    }
}
