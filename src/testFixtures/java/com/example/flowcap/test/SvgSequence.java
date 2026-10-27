package com.example.flowcap.test;

import static com.example.flowcap.test.SvgText.LINE;
import static com.example.flowcap.test.SvgText.num;
import static com.example.flowcap.test.SvgText.rect;
import static com.example.flowcap.test.SvgText.text;
import static com.example.flowcap.test.SvgText.width;

import com.example.flowcap.test.SequenceModel.Arrow;
import com.example.flowcap.test.SequenceModel.Item;
import com.example.flowcap.test.SequenceModel.Lane;
import com.example.flowcap.test.SequenceModel.Message;
import com.example.flowcap.test.SequenceModel.Note;
import com.example.flowcap.test.SequenceModel.Par;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sequence model drawn as SVG, in the spirit of Mermaid's layout: participant boxes with dashed lifelines,
 * one row per message or note, and a frame around each par block.
 */
final class SvgSequence {

    private static final double MARGIN = 20;
    private static final double PAD = 10;
    private static final double BOX_HEIGHT = 36;
    private static final double MIN_BOX_WIDTH = 100;
    private static final double BOX_GAP = 30;
    private static final double SELF_LOOP = 30;
    private static final double MESSAGE_ROW = 40;
    private static final double SELF_ROW = 48;
    private static final double NOTE_ROW = 36;
    private static final double FRAME_PAD = 12;

    private final List<SequenceModel.Participant> participants;
    private final Map<String, Integer> column = new HashMap<>();
    private final double[] boxWidth;
    private final double[] center;
    private double minX;
    private double maxX;

    private SvgSequence(SequenceModel model) {
        participants = model.participants();
        boxWidth = new double[participants.size()];
        center = new double[participants.size()];
        for (int i = 0; i < participants.size(); i++) {
            column.put(participants.get(i).id(), i);
            boxWidth[i] = Math.max(MIN_BOX_WIDTH, width(participants.get(i).label()) + 2 * PAD);
        }
        placeColumns(model.items());
    }

    static String render(SequenceModel model) {
        return new SvgSequence(model).draw(model.items());
    }

    /** Adjacent boxes don't touch, and every message label fits between the lifelines it joins. */
    private void placeColumns(List<Item> items) {
        for (int i = 1; i < center.length; i++) {
            center[i] = center[i - 1] + (boxWidth[i - 1] + boxWidth[i]) / 2 + BOX_GAP;
        }
        List<double[]> needs = new ArrayList<>();   // {left column, right column, distance}
        collectNeeds(items, needs);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (double[] need : needs) {
                int left = (int) need[0];
                int right = (int) need[1];
                double deficit = need[2] - (center[right] - center[left]);
                if (deficit > 0.01) {
                    for (int k = right; k < center.length; k++) {
                        center[k] += deficit;
                    }
                    moved = true;
                }
            }
        }
        minX = center[0] - boxWidth[0] / 2;
        int last = center.length - 1;
        maxX = center[last] + boxWidth[last] / 2;
        widen(items);
        double shift = MARGIN - minX;
        for (int i = 0; i < center.length; i++) {
            center[i] += shift;
        }
        minX += shift;
        maxX += shift;
    }

    private void collectNeeds(List<Item> items, List<double[]> needs) {
        for (Item item : items) {
            switch (item) {
                case Message m when m.self() -> {
                    int c = column.get(m.from());
                    if (c + 1 < center.length) {
                        needs.add(new double[] {c, c + 1, selfExtent(m) + boxWidth[c + 1] / 2 + PAD});
                    }
                }
                case Message m -> {
                    int a = column.get(m.from());
                    int b = column.get(m.to());
                    needs.add(new double[] {Math.min(a, b), Math.max(a, b), width(m.text()) + 2 * PAD});
                }
                case Note n -> {
                    if (n.over().size() == 2) {
                        int a = column.get(n.over().get(0));
                        int b = column.get(n.over().get(1));
                        needs.add(new double[] {Math.min(a, b), Math.max(a, b), 0});
                    }
                }
                case Par p -> p.lanes().forEach(lane -> collectNeeds(lane.items(), needs));
            }
        }
    }

    /** Grows the canvas to fit self-messages, notes and frames that reach past the outer boxes. */
    private void widen(List<Item> items) {
        for (Item item : items) {
            double[] extent = extent(item);
            minX = Math.min(minX, extent[0]);
            maxX = Math.max(maxX, extent[1]);
        }
    }

    private double[] extent(Item item) {
        return switch (item) {
            case Message m when m.self() -> new double[] {center[column.get(m.from())],
                    center[column.get(m.from())] + selfExtent(m)};
            case Message m -> new double[] {Math.min(center[column.get(m.from())], center[column.get(m.to())]),
                    Math.max(center[column.get(m.from())], center[column.get(m.to())])};
            case Note n -> noteBox(n);
            case Par p -> {
                double lo = Double.MAX_VALUE;
                double hi = -Double.MAX_VALUE;
                for (Lane lane : p.lanes()) {
                    for (Item inner : lane.items()) {
                        double[] e = extent(inner);
                        lo = Math.min(lo, e[0]);
                        hi = Math.max(hi, e[1]);
                    }
                    hi = Math.max(hi, lo + width("[" + lane.label() + "]") + 60);
                }
                yield new double[] {lo - FRAME_PAD, hi + FRAME_PAD};
            }
        };
    }

    private static double selfExtent(Message m) {
        return SELF_LOOP + 8 + width(m.text()) + PAD;
    }

    /** {left, right} of a note's box. */
    private double[] noteBox(Note n) {
        double a = center[column.get(n.over().get(0))];
        double b = center[column.get(n.over().get(n.over().size() - 1))];
        double lo = Math.min(a, b) - (n.over().size() == 2 ? PAD * 2 : 0);
        double hi = Math.max(a, b) + (n.over().size() == 2 ? PAD * 2 : 0);
        double needed = width(n.text()) + 2 * PAD;
        if (hi - lo < needed) {
            double mid = (lo + hi) / 2;
            lo = mid - needed / 2;
            hi = mid + needed / 2;
        }
        return new double[] {lo, hi};
    }

    private String draw(List<Item> items) {
        StringBuilder body = new StringBuilder();
        double top = MARGIN + BOX_HEIGHT + 10;
        double bottom = rows(items, top, body);
        double height = bottom + 10 + MARGIN;
        double canvasWidth = maxX + MARGIN;

        StringBuilder out = new StringBuilder(SvgText.open(canvasWidth, height));
        for (int i = 0; i < participants.size(); i++) {
            out.append("<line x1=\"%1$s\" y1=\"%2$s\" x2=\"%1$s\" y2=\"%3$s\" stroke=\"#999999\" stroke-dasharray=\"4 4\"/>\n"
                    .formatted(num(center[i]), num(MARGIN + BOX_HEIGHT), num(bottom + 10)));
        }
        for (int i = 0; i < participants.size(); i++) {
            out.append(rect(center[i] - boxWidth[i] / 2, MARGIN, boxWidth[i], BOX_HEIGHT, "#ECECFF", "#9370DB",
                    " rx=\"3\""));
            out.append(text(center[i], MARGIN + BOX_HEIGHT / 2 + 4.5, "middle", participants.get(i).label()));
        }
        return out.append(body).append("</svg>\n").toString();
    }

    /** Draws items from y downwards and returns the y after the last one. */
    private double rows(List<Item> items, double y, StringBuilder out) {
        for (Item item : items) {
            switch (item) {
                case Message m when m.self() -> {
                    double x = center[column.get(m.from())];
                    out.append("<path d=\"M%1$s,%2$s H%3$s V%4$s H%1$s\" fill=\"none\" stroke=\"%5$s\" marker-end=\"url(#arrow)\"/>\n"
                            .formatted(num(x), num(y + 14), num(x + SELF_LOOP), num(y + 32), LINE));
                    out.append(text(x + SELF_LOOP + 8, y + 27, "start", m.text()));
                    y += SELF_ROW;
                }
                case Message m -> {
                    double from = center[column.get(m.from())];
                    double to = center[column.get(m.to())];
                    out.append(text((from + to) / 2, y + 18, "middle", m.text()));
                    out.append("<line x1=\"%s\" y1=\"%s\" x2=\"%s\" y2=\"%s\" stroke=\"%s\"%s marker-end=\"url(#%s)\"/>\n"
                            .formatted(num(from), num(y + 26), num(to), num(y + 26), LINE,
                                    m.arrow() == Arrow.CALL ? "" : " stroke-dasharray=\"6 4\"",
                                    m.arrow() == Arrow.FAILED ? "cross" : "arrow"));
                    y += MESSAGE_ROW;
                }
                case Note n -> {
                    double[] box = noteBox(n);
                    out.append(rect(box[0], y + 6, box[1] - box[0], 24, "#FFF5AD", "#AAAA33", ""));
                    out.append(text((box[0] + box[1]) / 2, y + 22.5, "middle", n.text()));
                    y += NOTE_ROW;
                }
                case Par p -> y = par(p, y, out);
            }
        }
        return y;
    }

    private double par(Par p, double y, StringBuilder out) {
        double[] frame = extent(p);
        StringBuilder lanes = new StringBuilder();
        double cursor = y;
        for (int k = 0; k < p.lanes().size(); k++) {
            Lane lane = p.lanes().get(k);
            if (k > 0) {
                lanes.append("<line x1=\"%s\" y1=\"%s\" x2=\"%s\" y2=\"%s\" stroke=\"#666666\" stroke-dasharray=\"3 3\"/>\n"
                        .formatted(num(frame[0]), num(cursor + 4), num(frame[1]), num(cursor + 4)));
            }
            lanes.append(text(frame[0] + 50, cursor + 20, "start", "[" + lane.label() + "]"));
            cursor = rows(lane.items(), cursor + 24, lanes);
        }
        double end = cursor + 8;
        out.append(rect(frame[0], y + 4, frame[1] - frame[0], end - y - 4, "none", "#666666", ""))
                .append(rect(frame[0], y + 4, 40, 22, "#EEEEEE", "#666666", ""))
                .append(text(frame[0] + 20, y + 19.5, "middle", "par"))
                .append(lanes);
        return end + 6;
    }
}
