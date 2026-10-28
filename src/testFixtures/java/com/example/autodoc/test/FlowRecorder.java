package com.example.autodoc.test;

import com.example.autodoc.boot.AutodocObservationKeys;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationView;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.springframework.core.env.PropertyResolver;

/**
 * Builds the step tree as observations start and stop. Ignores observations without an autodoc.step key
 * (such as Boot's HTTP observations) but walks through them to find a step's parent.
 */
public class FlowRecorder {

    record Note(long seq, String text) {
    }

    static final class Step {
        final Observation.Context context;
        final String action;
        final String participant;
        final long startSeq;
        volatile long stopSeq = Long.MAX_VALUE;
        final List<Step> children = new CopyOnWriteArrayList<>();
        final List<Note> notes = new CopyOnWriteArrayList<>();

        Step(Observation.Context context, String action, String participant, long startSeq) {
            this.context = context;
            this.action = action;
            this.participant = participant;
            this.startSeq = startSeq;
        }

        /** A note that failed to evaluate, or null. */
        String problem() {
            KeyValue problem = context.getHighCardinalityKeyValue(AutodocObservationKeys.PROBLEM_KEY);
            return problem == null ? null : problem.getValue();
        }

        /** This step and all its descendants, depth first in start order. */
        Stream<Step> flatten() {
            return Stream.concat(Stream.of(this), children.stream()
                    .sorted(Comparator.comparingLong(s -> s.startSeq))
                    .flatMap(Step::flatten));
        }
    }

    private final PropertyResolver properties;
    private final AtomicLong seq = new AtomicLong();
    private final Map<Object, Step> byContext = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<Step> roots = new CopyOnWriteArrayList<>();

    /** A field, not a bean, so Boot doesn't auto-register it a second time. */
    final ObservationHandler<Observation.Context> handler = new ObservationHandler<>() {
        @Override
        public boolean supportsContext(Observation.Context ctx) {
            return true;
        }

        @Override
        public void onStart(Observation.Context ctx) {
            KeyValue action = ctx.getLowCardinalityKeyValue(AutodocObservationKeys.STEP_KEY);
            KeyValue participant = ctx.getLowCardinalityKeyValue(AutodocObservationKeys.PARTICIPANT_KEY);
            if (action == null || participant == null) {
                return;
            }
            Step step = new Step(ctx, action.getValue(), participant.getValue(), seq.incrementAndGet());
            byContext.put(ctx, step);
            Step parent = nearestFlowParent(ctx);
            (parent == null ? roots : parent.children).add(step);
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context ctx) {
            Step step = byContext.get(ctx);
            if (step != null && AutodocObservationKeys.NOTE_EVENT.equals(event.getName())) {
                step.notes.add(new Note(seq.incrementAndGet(), event.getContextualName()));
            }
        }

        @Override
        public void onStop(Observation.Context ctx) {
            Step step = byContext.get(ctx);
            if (step != null) {
                step.stopSeq = seq.incrementAndGet();
            }
        }
    };

    /** @param properties the configuration of the context being recorded, so scenarios can check it */
    public FlowRecorder(PropertyResolver properties) {
        this.properties = properties;
    }

    PropertyResolver properties() {
        return properties;
    }

    List<Step> roots() {
        return roots.stream().sorted(Comparator.comparingLong(s -> s.startSeq)).toList();
    }

    public void clear() {
        roots.clear();
        byContext.clear();
        seq.set(0);
    }

    private Step nearestFlowParent(Observation.ContextView ctx) {
        ObservationView parent = ctx.getParentObservation();
        while (parent != null) {
            Step s = byContext.get(parent.getContextView());
            if (s != null) {
                return s;
            }
            parent = parent.getContextView().getParentObservation();
        }
        return null;
    }
}
