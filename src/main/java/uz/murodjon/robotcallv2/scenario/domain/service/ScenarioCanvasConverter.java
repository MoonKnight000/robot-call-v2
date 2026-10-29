package uz.murodjon.robotcallv2.scenario.domain.service;

import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;

import java.util.*;

/**
 * Bidirectional converter between {@link ScenarioDefinition} and {@link CanvasGraphDto}.
 *
 * <p>Enables drag-and-drop Visual Studio editors (React Flow, Vue Flow, Rete.js) to render,
 * edit, auto-layout and serialize state-machine call scripts.
 */
public final class ScenarioCanvasConverter {

    private static final double START_X = 40.0;
    private static final double START_Y = 40.0;
    private static final double STAGE_COL_WIDTH = 360.0;
    private static final double STAGE_ROW_HEIGHT = 180.0;
    private static final double GLOBAL_NODE_X = -340.0;
    private static final double GLOBAL_NODE_ROW_HEIGHT = 150.0;

    private ScenarioCanvasConverter() {
    }

    /**
     * Converts a {@link Scenario} entity to a complete {@link CanvasGraphDto}.
     */
    public static CanvasGraphDto toCanvasGraph(Scenario scenario) {
        if (scenario == null) {
            return new CanvasGraphDto(null, null, null, null, List.of(), List.of(),
                    CanvasViewportDto.defaultViewport(), List.of("Scenario is null"), false);
        }
        return toCanvasGraph(scenario.id(), scenario.scenarioKey(), scenario.name(), scenario.version(),
                scenario.definition());
    }

    /**
     * Converts a {@link ScenarioDefinition} to a complete visual canvas graph with nodes,
     * directed edges, and auto-layout coordinates.
     */
    public static CanvasGraphDto toCanvasGraph(Long scenarioId, String scenarioKey, String name,
                                               Integer version, ScenarioDefinition def) {
        if (def == null) {
            return new CanvasGraphDto(scenarioId, scenarioKey, name, version, List.of(), List.of(),
                    CanvasViewportDto.defaultViewport(), List.of("Scenario definition is null"), false);
        }

        List<CanvasNodeDto> nodes = new ArrayList<>();
        List<CanvasEdgeDto> edges = new ArrayList<>();

        // 1. Entry / Start Node
        Map<String, Object> startData = new LinkedHashMap<>();
        startData.put("title", name != null ? name : "Start");
        if (def.callPurpose() != null) startData.put("callPurpose", def.callPurpose());
        if (def.disclosureText() != null) startData.put("disclosureText", def.disclosureText());
        if (def.rolePrompt() != null) startData.put("rolePrompt", def.rolePrompt());
        if (def.guardrails() != null) startData.put("guardrails", def.guardrails());

        nodes.add(new CanvasNodeDto("start", "start", new CanvasPositionDto(START_X, START_Y), startData));

        // Compute layout ranks for stages
        List<StageDef> stages = def.stages() != null ? def.stages() : List.of();
        Map<String, Integer> ranks = computeTopologicalRanks(stages);
        Map<Integer, Integer> rankRowCounts = new HashMap<>();

        // 2. Stage Nodes and Edges
        for (StageDef stage : stages) {
            int rank = ranks.getOrDefault(stage.id(), 0);
            int row = rankRowCounts.getOrDefault(rank, 0);
            rankRowCounts.put(rank, row + 1);

            double posX = START_X + STAGE_COL_WIDTH + (rank * STAGE_COL_WIDTH);
            double posY = START_Y + (row * STAGE_ROW_HEIGHT);

            Map<String, Object> stageData = new LinkedHashMap<>();
            stageData.put("title", formatStageTitle(stage.id()));
            stageData.put("stageId", stage.id());
            if (stage.purpose() != null) stageData.put("purpose", stage.purpose());
            if (stage.staticLine() != null) stageData.put("staticLine", stage.staticLine());
            if (stage.emotion() != null) stageData.put("emotion", stage.emotion());
            if (stage.model() != null) stageData.put("model", stage.model());
            if (stage.allowedTools() != null) stageData.put("allowedTools", stage.allowedTools());
            if (stage.requiredOutcomes() != null) stageData.put("requiredOutcomes", stage.requiredOutcomes());
            if (stage.branches() != null) stageData.put("branches", stage.branches());

            nodes.add(new CanvasNodeDto(stage.id(), "stage", new CanvasPositionDto(posX, posY), stageData));

            // Outgoing transition edges
            if (stage.allowedTransitions() != null) {
                for (String target : stage.allowedTransitions()) {
                    String condition = stage.transitionConditions() != null ? stage.transitionConditions().get(target) : null;
                    String edgeId = "e_" + stage.id() + "_to_" + target;
                    String label = condition != null && !condition.isBlank() ? condition : target;
                    Map<String, Object> edgeData = new LinkedHashMap<>();
                    if (condition != null) edgeData.put("condition", condition);

                    edges.add(new CanvasEdgeDto(edgeId, stage.id(), target, label, "transition", edgeData));
                }
            }

            // Outgoing branch edges
            if (stage.branches() != null) {
                int branchIndex = 0;
                for (StageBranch branch : stage.branches()) {
                    String edgeId = "e_branch_" + stage.id() + "_" + branch.goTo() + "_" + branchIndex++;
                    String label = !branch.operator().needsFact() ? "else"
                            : branch.fact() + " " + branch.operator() + (branch.value() != null ? " " + branch.value() : "");
                    Map<String, Object> branchData = new LinkedHashMap<>();
                    branchData.put("fact", branch.fact());
                    branchData.put("operator", branch.operator().name());
                    if (branch.value() != null) branchData.put("value", branch.value());
                    branchData.put("branch", true);

                    edges.add(new CanvasEdgeDto(edgeId, stage.id(), branch.goTo(), label, "branch", branchData));
                }
            }
        }

        // Link Start node to first stage
        if (!stages.isEmpty()) {
            edges.add(new CanvasEdgeDto("e_start_to_" + stages.get(0).id(), "start", stages.get(0).id(),
                    "Begins call", "default", Map.of()));
        }

        // 3. Global Nodes (Interrupt FAQ side lane)
        if (def.globalNodes() != null) {
            int gnIndex = 0;
            for (GlobalNode gn : def.globalNodes()) {
                String gnId = "gn_" + gnIndex;
                Map<String, Object> gnData = new LinkedHashMap<>();
                gnData.put("question", gn.question());
                gnData.put("answer", gn.answer());

                double gnY = START_Y + (gnIndex * GLOBAL_NODE_ROW_HEIGHT);
                nodes.add(new CanvasNodeDto(gnId, "global_node", new CanvasPositionDto(GLOBAL_NODE_X, gnY), gnData));
                gnIndex++;
            }
        }

        // 4. Validate Definition
        List<String> errors = ScenarioValidator.validate(def);

        return new CanvasGraphDto(
                scenarioId,
                scenarioKey,
                name,
                version,
                nodes,
                edges,
                CanvasViewportDto.defaultViewport(),
                errors,
                errors.isEmpty()
        );
    }

    /**
     * Converts an edited visual {@link CanvasGraphDto} back into a valid {@link ScenarioDefinition}.
     *
     * @param canvas the edited canvas graph from the frontend
     * @param fallback existing definition to preserve prompts/webhooks not on canvas
     * @return structured scenario definition
     */
    public static ScenarioDefinition toScenarioDefinition(CanvasGraphDto canvas, ScenarioDefinition fallback) {
        if (canvas == null || canvas.nodes() == null) {
            return fallback;
        }

        List<CanvasNodeDto> nodes = canvas.nodes();
        List<CanvasEdgeDto> edges = canvas.edges() != null ? canvas.edges() : List.of();

        // 1. Start node data
        CanvasNodeDto startNode = nodes.stream()
                .filter(n -> "start".equalsIgnoreCase(n.type()) || "start".equalsIgnoreCase(n.id()))
                .findFirst()
                .orElse(null);

        String callPurpose = fallback != null ? fallback.callPurpose() : null;
        String disclosureText = fallback != null ? fallback.disclosureText() : null;
        String rolePrompt = fallback != null ? fallback.rolePrompt() : null;
        List<String> guardrails = fallback != null ? fallback.guardrails() : null;

        if (startNode != null && startNode.data() != null) {
            Map<String, Object> data = startNode.data();
            if (data.containsKey("callPurpose")) callPurpose = asString(data.get("callPurpose"));
            if (data.containsKey("disclosureText")) disclosureText = asString(data.get("disclosureText"));
            if (data.containsKey("rolePrompt")) rolePrompt = asString(data.get("rolePrompt"));
            if (data.containsKey("guardrails")) guardrails = asStringList(data.get("guardrails"));
        }

        // 2. Identify all stage nodes
        List<CanvasNodeDto> stageNodes = nodes.stream()
                .filter(n -> "stage".equalsIgnoreCase(n.type()))
                .toList();

        // Map outgoing edges per stage
        Map<String, List<CanvasEdgeDto>> outgoingEdges = new LinkedHashMap<>();
        for (CanvasEdgeDto edge : edges) {
            outgoingEdges.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
        }

        List<StageDef> reconstructedStages = new ArrayList<>();
        for (CanvasNodeDto stageNode : stageNodes) {
            String stageId = stageNode.id();
            Map<String, Object> data = stageNode.data() != null ? stageNode.data() : Map.of();

            String purpose = asString(data.get("purpose"));
            String staticLine = asString(data.get("staticLine"));
            String emotion = asString(data.get("emotion"));
            String model = asString(data.get("model"));
            List<String> allowedTools = asStringList(data.get("allowedTools"));
            List<String> requiredOutcomes = asStringList(data.get("requiredOutcomes"));

            List<CanvasEdgeDto> out = outgoingEdges.getOrDefault(stageId, List.of());

            // Extract transitions and conditions from edges
            List<String> allowedTransitions = new ArrayList<>();
            Map<String, String> transitionConditions = new LinkedHashMap<>();
            List<StageBranch> branches = new ArrayList<>();

            for (CanvasEdgeDto edge : out) {
                if ("branch".equalsIgnoreCase(edge.type())) {
                    StageBranch branch = extractBranch(edge);
                    if (branch != null) {
                        branches.add(branch);
                    }
                } else if (!"start".equalsIgnoreCase(edge.target())) {
                    if (!allowedTransitions.contains(edge.target())) {
                        allowedTransitions.add(edge.target());
                    }
                    String condition = null;
                    if (edge.data() != null && edge.data().containsKey("condition")) {
                        condition = asString(edge.data().get("condition"));
                    } else if (edge.label() != null && !edge.label().equalsIgnoreCase(edge.target())) {
                        condition = edge.label();
                    }
                    if (condition != null && !condition.isBlank()) {
                        transitionConditions.put(edge.target(), condition.trim());
                    }
                }
            }

            // Also merge branches declared in node data if any
            if (data.containsKey("branches") && data.get("branches") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        String fact = asString(m.get("fact"));
                        String opStr = asString(m.get("operator"));
                        String val = asString(m.get("value"));
                        String goTo = asString(m.get("goTo"));
                        if (fact != null && opStr != null && goTo != null) {
                            try {
                                BranchOperator op = BranchOperator.valueOf(opStr.trim().toUpperCase(Locale.ROOT));
                                StageBranch b = new StageBranch(fact, op, val, goTo);
                                if (!branches.contains(b)) {
                                    branches.add(b);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }

            // This canvas carries no node type, global condition or action; they are kept from the saved stage.
            StageDef saved = fallback != null ? fallback.findStage(stageId) : null;
            reconstructedStages.add(new StageDef(
                    stageId,
                    purpose != null ? purpose : stageId,
                    allowedTransitions,
                    allowedTools != null ? allowedTools : List.of(),
                    emotion,
                    model,
                    branches.isEmpty() ? null : branches,
                    transitionConditions.isEmpty() ? null : transitionConditions,
                    staticLine,
                    requiredOutcomes.isEmpty() ? null : requiredOutcomes,
                    saved != null ? saved.type() : null,
                    stageNode.position() != null
                            ? new StagePosition(stageNode.position().x(), stageNode.position().y()) : null,
                    saved != null ? saved.globalCondition() : null,
                    saved != null ? saved.action() : null
            ));
        }

        // 3. Global Nodes
        List<GlobalNode> globalNodes = new ArrayList<>();
        List<CanvasNodeDto> globalNodeList = nodes.stream()
                .filter(n -> "global_node".equalsIgnoreCase(n.type()))
                .toList();

        for (CanvasNodeDto gn : globalNodeList) {
            Map<String, Object> data = gn.data() != null ? gn.data() : Map.of();
            String q = asString(data.get("question"));
            String a = asString(data.get("answer"));
            if (q != null && !q.isBlank() && a != null && !a.isBlank()) {
                globalNodes.add(new GlobalNode(q.trim(), a.trim()));
            }
        }

        return new ScenarioDefinition(
                reconstructedStages,
                fallback != null ? fallback.factSchema() : List.of(),
                fallback != null ? fallback.tools() : List.of(),
                fallback != null ? fallback.outcomeSchema() : List.of(),
                rolePrompt != null ? rolePrompt : (fallback != null ? fallback.rolePrompt() : ""),
                guardrails != null ? guardrails : (fallback != null ? fallback.guardrails() : List.of()),
                disclosureText,
                fallback != null ? fallback.factWebhook() : null,
                fallback != null ? fallback.styleExamples() : null,
                globalNodes.isEmpty() ? (fallback != null ? fallback.globalNodes() : null) : globalNodes,
                callPurpose,
                fallback != null ? fallback.startStageId() : null,
                fallback != null ? fallback.transitionMode() : null
        );
    }

    /**
     * Recalculates canvas layout coordinates for all nodes using topological DAG ranking.
     */
    public static CanvasGraphDto autoLayout(CanvasGraphDto graph) {
        if (graph == null || graph.nodes() == null || graph.nodes().isEmpty()) {
            return graph;
        }

        List<CanvasNodeDto> stageNodes = graph.nodes().stream()
                .filter(n -> "stage".equalsIgnoreCase(n.type()))
                .toList();

        // Build adjacency from edges
        Map<String, List<String>> adj = new HashMap<>();
        if (graph.edges() != null) {
            for (CanvasEdgeDto e : graph.edges()) {
                if (!"start".equalsIgnoreCase(e.source())) {
                    adj.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e.target());
                }
            }
        }

        // Rank by BFS from root stage
        Map<String, Integer> ranks = new HashMap<>();
        if (!stageNodes.isEmpty()) {
            String rootId = stageNodes.get(0).id();
            ranks.put(rootId, 0);
            Queue<String> queue = new ArrayDeque<>();
            queue.add(rootId);

            while (!queue.isEmpty()) {
                String curr = queue.poll();
                int currRank = ranks.get(curr);
                for (String neighbor : adj.getOrDefault(curr, List.of())) {
                    if (!ranks.containsKey(neighbor)) {
                        ranks.put(neighbor, currRank + 1);
                        queue.add(neighbor);
                    }
                }
            }
        }

        Map<Integer, Integer> rankRowCounts = new HashMap<>();
        List<CanvasNodeDto> repositionedNodes = new ArrayList<>();

        int globalNodeCount = 0;
        for (CanvasNodeDto node : graph.nodes()) {
            if ("start".equalsIgnoreCase(node.type()) || "start".equalsIgnoreCase(node.id())) {
                repositionedNodes.add(new CanvasNodeDto(node.id(), node.type(),
                        new CanvasPositionDto(START_X, START_Y), node.data()));
            } else if ("stage".equalsIgnoreCase(node.type())) {
                int rank = ranks.getOrDefault(node.id(), 0);
                int row = rankRowCounts.getOrDefault(rank, 0);
                rankRowCounts.put(rank, row + 1);

                double x = START_X + STAGE_COL_WIDTH + (rank * STAGE_COL_WIDTH);
                double y = START_Y + (row * STAGE_ROW_HEIGHT);

                repositionedNodes.add(new CanvasNodeDto(node.id(), node.type(),
                        new CanvasPositionDto(x, y), node.data()));
            } else if ("global_node".equalsIgnoreCase(node.type())) {
                double x = GLOBAL_NODE_X;
                double y = START_Y + (globalNodeCount * GLOBAL_NODE_ROW_HEIGHT);
                repositionedNodes.add(new CanvasNodeDto(node.id(), node.type(),
                        new CanvasPositionDto(x, y), node.data()));
                globalNodeCount++;
            } else {
                repositionedNodes.add(node);
            }
        }

        return new CanvasGraphDto(
                graph.scenarioId(),
                graph.scenarioKey(),
                graph.name(),
                graph.version(),
                repositionedNodes,
                graph.edges(),
                graph.viewport() != null ? graph.viewport() : CanvasViewportDto.defaultViewport(),
                graph.validationErrors(),
                graph.valid()
        );
    }

    private static Map<String, Integer> computeTopologicalRanks(List<StageDef> stages) {
        Map<String, Integer> ranks = new HashMap<>();
        if (stages.isEmpty()) {
            return ranks;
        }

        String root = stages.get(0).id();
        ranks.put(root, 0);

        Map<String, List<String>> adj = new HashMap<>();
        for (StageDef s : stages) {
            List<String> next = new ArrayList<>();
            if (s.allowedTransitions() != null) next.addAll(s.allowedTransitions());
            if (s.branches() != null) {
                for (StageBranch b : s.branches()) {
                    next.add(b.goTo());
                }
            }
            adj.put(s.id(), next);
        }

        Queue<String> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            int currRank = ranks.get(curr);
            for (String neighbor : adj.getOrDefault(curr, List.of())) {
                if (!ranks.containsKey(neighbor)) {
                    ranks.put(neighbor, currRank + 1);
                    queue.add(neighbor);
                }
            }
        }

        // Any disconnected stages get placed at the end
        int maxRank = ranks.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (StageDef s : stages) {
            if (!ranks.containsKey(s.id())) {
                ranks.put(s.id(), ++maxRank);
            }
        }

        return ranks;
    }

    private static StageBranch extractBranch(CanvasEdgeDto edge) {
        if (edge.data() == null) {
            return null;
        }
        String fact = asString(edge.data().get("fact"));
        String opStr = asString(edge.data().get("operator"));
        String val = asString(edge.data().get("value"));
        if (fact == null || opStr == null) {
            return null;
        }
        try {
            BranchOperator op = BranchOperator.valueOf(opStr.trim().toUpperCase(Locale.ROOT));
            return new StageBranch(fact, op, val, edge.target());
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatStageTitle(String stageId) {
        if (stageId == null || stageId.isBlank()) return "";
        String[] parts = stageId.toLowerCase(Locale.ROOT).split("[_-]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o == null) return new ArrayList<>();
        if (o instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of(o.toString());
    }
}
