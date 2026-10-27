package com.example.flowcap.test;

import static com.example.flowcap.test.SvgText.LINE;
import static com.example.flowcap.test.SvgText.SMALL_FONT_SIZE;
import static com.example.flowcap.test.SvgText.escape;
import static com.example.flowcap.test.SvgText.num;
import static com.example.flowcap.test.SvgText.rect;
import static com.example.flowcap.test.SvgText.text;
import static com.example.flowcap.test.SvgText.width;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The static graph drawn as SVG, top to bottom. A step's layer is its longest call path from an entry step;
 * within a layer, steps keep the order a depth-first walk first meets them. Each box shows the step and,
 * smaller, its participant; boxes are colored by participant.
 */
final class SvgGraph {

    private static final double MARGIN = 20;
    private static final double PAD = 12;
    private static final double NODE_HEIGHT = 44;
    private static final double NODE_GAP = 30;
    private static final double LAYER_GAP = 56;
    private static final List<String> FILLS = List.of(
            "#ECECFF", "#E8F5E9", "#FFF3E0", "#E3F2FD", "#FCE4EC", "#F3E5F5", "#E0F7FA", "#FFFDE7");

    record Box(double x, double y, double width) {

        double centerX() {
            return x + width / 2;
        }
    }

    static String render(FlowGraph graph) {
        List<FlowGraph.Node> nodes = graph.nodes();
        Map<String, FlowGraph.Node> byId = new LinkedHashMap<>();
        nodes.forEach(n -> byId.put(n.id(), n));
        Map<String, List<String>> out = new TreeMap<>();
        Set<String> hasIncoming = new HashSet<>();
        for (FlowGraph.Edge e : graph.edges()) {
            out.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            if (!e.from().equals(e.to())) {
                hasIncoming.add(e.to());
            }
        }

        // Depth-first walk from the entry steps: visit order, and back edges to ignore when layering.
        List<String> order = new ArrayList<>();
        Set<List<String>> backEdges = new HashSet<>();
        List<String> starts = new ArrayList<>(byId.keySet().stream().filter(id -> !hasIncoming.contains(id)).sorted().toList());
        byId.keySet().stream().sorted().forEach(starts::add);
        Set<String> onPath = new HashSet<>();
        for (String start : starts) {
            walk(start, out, order, onPath, backEdges);
        }

        Map<String, Integer> layer = new HashMap<>();
        order.forEach(id -> layer.put(id, 0));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (FlowGraph.Edge e : graph.edges()) {
                if (backEdges.contains(List.of(e.from(), e.to())) || e.from().equals(e.to())) {
                    continue;
                }
                if (layer.get(e.to()) < layer.get(e.from()) + 1) {
                    layer.put(e.to(), layer.get(e.from()) + 1);
                    changed = true;
                }
            }
        }

        Map<Integer, List<String>> rows = new TreeMap<>();
        order.forEach(id -> rows.computeIfAbsent(layer.get(id), k -> new ArrayList<>()).add(id));
        Map<String, Double> widths = new HashMap<>();
        nodes.forEach(n -> widths.put(n.id(), Math.max(width(n.text()),
                width(graph.label(n.participant()), SMALL_FONT_SIZE)) + 2 * PAD));
        double canvasInner = rows.values().stream()
                .mapToDouble(row -> row.stream().mapToDouble(widths::get).sum() + NODE_GAP * (row.size() - 1))
                .max().orElse(0);

        Map<String, Box> boxes = new HashMap<>();
        rows.forEach((index, row) -> {
            double rowWidth = row.stream().mapToDouble(widths::get).sum() + NODE_GAP * (row.size() - 1);
            double x = MARGIN + (canvasInner - rowWidth) / 2;
            double y = MARGIN + index * (NODE_HEIGHT + LAYER_GAP);
            for (String id : row) {
                boxes.put(id, new Box(x, y, widths.get(id)));
                x += widths.get(id) + NODE_GAP;
            }
        });

        List<String> participants = nodes.stream().map(FlowGraph.Node::participant).distinct().sorted().toList();
        double height = MARGIN * 2 + rows.size() * NODE_HEIGHT + Math.max(0, rows.size() - 1) * LAYER_GAP;
        StringBuilder svg = new StringBuilder(SvgText.open(canvasInner + 2 * MARGIN, height));
        for (FlowGraph.Edge e : graph.edges()) {
            edge(boxes.get(e.from()), boxes.get(e.to()), e, svg);
        }
        for (FlowGraph.Node n : nodes.stream().sorted(Comparator.comparing(FlowGraph.Node::id)).toList()) {
            Box b = boxes.get(n.id());
            svg.append(rect(b.x(), b.y(), b.width(), NODE_HEIGHT,
                    FILLS.get(participants.indexOf(n.participant()) % FILLS.size()), "#9370DB", " rx=\"4\""));
            svg.append(text(b.centerX(), b.y() + 19, "middle", n.text()));
            svg.append("<text x=\"%s\" y=\"%s\" text-anchor=\"middle\" font-size=\"%s\" fill=\"#555555\">%s</text>\n"
                    .formatted(num(b.centerX()), num(b.y() + 35), num(SMALL_FONT_SIZE),
                            escape(graph.label(n.participant()))));
        }
        return svg.append("</svg>\n").toString();
    }

    private static void walk(String id, Map<String, List<String>> out, List<String> order, Set<String> onPath,
                             Set<List<String>> backEdges) {
        if (order.contains(id)) {
            return;
        }
        order.add(id);
        onPath.add(id);
        for (String next : out.getOrDefault(id, List.of())) {
            if (onPath.contains(next)) {
                backEdges.add(List.of(id, next));
            } else {
                walk(next, out, order, onPath, backEdges);
            }
        }
        onPath.remove(id);
    }

    /** Downward edges leave the bottom and enter the top; others run side to side. */
    private static void edge(Box from, Box to, FlowGraph.Edge e, StringBuilder svg) {
        double x1;
        double y1;
        double x2;
        double y2;
        if (to.y() > from.y()) {
            x1 = from.centerX();
            y1 = from.y() + NODE_HEIGHT;
            x2 = to.centerX();
            y2 = to.y();
        } else {
            boolean right = to.x() > from.x();
            x1 = right ? from.x() + from.width() : from.x();
            y1 = from.y() + NODE_HEIGHT / 2;
            x2 = right ? to.x() : to.x() + to.width();
            y2 = to.y() + NODE_HEIGHT / 2;
        }
        svg.append("<line x1=\"%s\" y1=\"%s\" x2=\"%s\" y2=\"%s\" stroke=\"%s\"%s marker-end=\"url(#arrow)\"/>\n"
                .formatted(num(x1), num(y1), num(x2), num(y2), LINE, e.dashed() ? " stroke-dasharray=\"6 4\"" : ""));
        if (e.dashed() && !e.label().isEmpty()) {
            double mx = (x1 + x2) / 2;
            double my = (y1 + y2) / 2;
            double w = width(e.label(), SMALL_FONT_SIZE) + 8;
            svg.append(rect(mx - w / 2, my - 9, w, 16, "#ffffff", "none", ""));
            svg.append("<text x=\"%s\" y=\"%s\" text-anchor=\"middle\" font-size=\"%s\" fill=\"#555555\">%s</text>\n"
                    .formatted(num(mx), num(my + 3), num(SMALL_FONT_SIZE), escape(e.label())));
        }
    }

    private SvgGraph() {
    }
}
