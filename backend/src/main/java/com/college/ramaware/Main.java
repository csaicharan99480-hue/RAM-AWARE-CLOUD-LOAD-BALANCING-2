package com.college.ramaware;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<ResourceConfig> RESOURCE_CONFIGS = new CopyOnWriteArrayList<>();
    private static final List<VmConfig> VM_CONFIGS = new CopyOnWriteArrayList<>();
    private static final List<CloudletConfig> CLOUDLET_CONFIGS = new CopyOnWriteArrayList<>();
    private static final List<SchedulingDecision> SCHEDULING_DECISIONS = new CopyOnWriteArrayList<>();
    private static final List<ExperimentResult> EXPERIMENT_RESULTS = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/health", Main::handleHealth);
        server.createContext("/api/resources", Main::handleResources);
        server.createContext("/api/vms", Main::handleVms);
        server.createContext("/api/cloudlets", Main::handleCloudlets);
        server.createContext("/api/scheduling", Main::handleScheduling);
        server.createContext("/api/experiments", Main::handleExperiments);
        server.createContext("/api/dashboard", Main::handleDashboard);
        server.createContext("/api/analytics", Main::handleAnalytics);
        server.setExecutor(null);
        server.start();
        System.out.println("RAM-aware CloudSim backend running on http://localhost:8080");
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendOptions(exchange);
            return;
        }
        sendJson(exchange, 200, Map.of("status", "ok", "message", "CloudSim backend is running"));
    }

    private static void handleResources(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendOptions(exchange);
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, RESOURCE_CONFIGS);
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            ResourceConfig request = readBody(exchange, ResourceConfig.class);
            if (request == null) {
                sendJson(exchange, 400, Map.of("error", "Request body is required"));
                return;
            }
            ResourceConfig config = new ResourceConfig();
            config.id = request.id == null || request.id.isBlank() ? UUID.randomUUID().toString() : request.id;
            config.name = request.name == null || request.name.isBlank() ? "Host Configuration" : request.name;
            config.hostCount = Math.max(1, request.hostCount);
            config.hostMips = Math.max(1, request.hostMips);
            config.hostPes = Math.max(1, request.hostPes);
            config.hostRamMb = Math.max(1024, request.hostRamMb);
            config.hostBandwidthMbps = Math.max(1000, request.hostBandwidthMbps);
            config.hostStorageGb = Math.max(100, request.hostStorageGb);
            config.schedulingInterval = Math.max(0.1, request.schedulingInterval);
            RESOURCE_CONFIGS.clear();
            RESOURCE_CONFIGS.add(config);
            sendJson(exchange, 201, config);
            return;
        }
        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private static void handleVms(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendOptions(exchange);
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, VM_CONFIGS);
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            VmConfig request = readBody(exchange, VmConfig.class);
            if (request == null) {
                sendJson(exchange, 400, Map.of("error", "Request body is required"));
                return;
            }
            VmConfig vm = new VmConfig();
            vm.id = request.id == null || request.id.isBlank() ? "VM-" + (VM_CONFIGS.size() + 1) : request.id;
            vm.mips = Math.max(500, request.mips);
            vm.pes = Math.max(1, request.pes);
            vm.ramMb = Math.max(512, request.ramMb);
            vm.bandwidthMbps = Math.max(100, request.bandwidthMbps);
            vm.storageGb = Math.max(10, request.storageGb);
            vm.status = "Configured";
            vm.currentCloudlet = "None";
            vm.utilization = 0.0;
            VM_CONFIGS.add(vm);
            sendJson(exchange, 201, vm);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            String id = extractId(exchange);
            VM_CONFIGS.removeIf(vm -> vm.id.equals(id));
            sendJson(exchange, 200, VM_CONFIGS);
            return;
        }
        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private static void handleCloudlets(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendOptions(exchange);
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, CLOUDLET_CONFIGS);
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            CloudletConfig request = readBody(exchange, CloudletConfig.class);
            if (request == null) {
                sendJson(exchange, 400, Map.of("error", "Request body is required"));
                return;
            }
            CloudletConfig cloudlet = new CloudletConfig();
            cloudlet.id = request.id == null || request.id.isBlank() ? "C-" + (CLOUDLET_CONFIGS.size() + 1) : request.id;
            cloudlet.length = Math.max(1000, request.length);
            cloudlet.pes = Math.max(1, request.pes);
            cloudlet.fileSize = Math.max(100, request.fileSize);
            cloudlet.outputSize = Math.max(100, request.outputSize);
            cloudlet.ramRequirementMb = Math.max(256, request.ramRequirementMb);
            cloudlet.status = "Pending";
            CLOUDLET_CONFIGS.add(cloudlet);
            sendJson(exchange, 201, cloudlet);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            String id = extractId(exchange);
            CLOUDLET_CONFIGS.removeIf(c -> c.id.equals(id));
            sendJson(exchange, 200, CLOUDLET_CONFIGS);
            return;
        }
        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private static void handleScheduling(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendOptions(exchange);
            return;
        }
        sendJson(exchange, 200, SCHEDULING_DECISIONS);
    }

    private static void handleExperiments(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendOptions(exchange);
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, EXPERIMENT_RESULTS);
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            ExperimentRequest request = readBody(exchange, ExperimentRequest.class);
            if (request == null) {
                sendJson(exchange, 400, Map.of("error", "Request body is required"));
                return;
            }
            try {
                ExperimentResult result = runExperiment(request);
                EXPERIMENT_RESULTS.add(result);
                sendJson(exchange, 200, result);
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, Map.of("error", ex.getMessage()));
            }
            return;
        }
        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private static void handleDashboard(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendOptions(exchange);
            return;
        }
        DashboardSummary summary = new DashboardSummary();
        summary.activeVms = VM_CONFIGS.size();
        summary.pendingTasks = (int) CLOUDLET_CONFIGS.stream().filter(c -> "Pending".equals(c.status)).count();
        summary.completedTasks = (int) CLOUDLET_CONFIGS.stream().filter(c -> "Completed".equals(c.status)).count();
        summary.resourceStatus = RESOURCE_CONFIGS.isEmpty() ? "No host configuration available" : "Configured";
        summary.latestExperiment = EXPERIMENT_RESULTS.isEmpty() ? "No experiments have been configured" : EXPERIMENT_RESULTS.get(EXPERIMENT_RESULTS.size() - 1).status;
        summary.recentSchedulingDecisions = SCHEDULING_DECISIONS.stream().limit(5).collect(Collectors.toList());
        summary.alerts = SCHEDULING_DECISIONS.isEmpty() ? List.of("No runtime data available") : List.of("Scheduling decisions recorded");
        sendJson(exchange, 200, summary);
    }

    private static void handleAnalytics(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendOptions(exchange);
            return;
        }
        AnalyticsResponse response = new AnalyticsResponse();
        response.available = !EXPERIMENT_RESULTS.isEmpty();
        response.results = EXPERIMENT_RESULTS;
        sendJson(exchange, 200, response);
    }

    private static ExperimentResult runExperiment(ExperimentRequest request) {
        if (VM_CONFIGS.isEmpty()) {
            throw new IllegalArgumentException("No virtual machines configured.");
        }
        if (CLOUDLET_CONFIGS.isEmpty()) {
            throw new IllegalArgumentException("No cloudlets available.");
        }
        if (RESOURCE_CONFIGS.isEmpty()) {
            throw new IllegalArgumentException("No host configuration available.");
        }

        String algorithm = (request.algorithm == null || request.algorithm.isBlank()) ? "ram-aware" : request.algorithm.trim().toLowerCase();
        ResourceConfig hostConfig = RESOURCE_CONFIGS.get(0);
        CloudSim cloudSim = new CloudSim();
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < hostConfig.hostCount; i++) {
            List<Pe> peList = IntStream.range(0, hostConfig.hostPes)
                    .mapToObj(index -> new PeSimple(hostConfig.hostMips))
                    .collect(Collectors.toList());
            hosts.add(new HostSimple(hostConfig.hostRamMb, hostConfig.hostBandwidthMbps, hostConfig.hostStorageGb * 1024L, peList));
        }

        Datacenter datacenter = new DatacenterSimple(cloudSim, hosts, new VmAllocationPolicySimple());
        DatacenterBroker broker = new DatacenterBrokerSimple(cloudSim);
        List<Vm> actualVms = new ArrayList<>();
        for (VmConfig vmConfig : VM_CONFIGS) {
            Vm vm = new VmSimple(vmConfig.mips, vmConfig.pes);
            vm.setRam(vmConfig.ramMb);
            vm.setBw(vmConfig.bandwidthMbps);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            broker.submitVm(vm);
            actualVms.add(vm);
        }

        List<Cloudlet> actualCloudlets = new ArrayList<>();
        for (CloudletConfig cloudletConfig : CLOUDLET_CONFIGS) {
            CloudletSimple cloudlet = new CloudletSimple(cloudletConfig.length, cloudletConfig.pes);
            cloudlet.setFileSize(cloudletConfig.fileSize);
            cloudlet.setOutputSize(cloudletConfig.outputSize);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(1.0));
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(1.0));

            Vm selected = selectVm(actualVms, cloudletConfig, algorithm);
            if (selected == null) {
                SCHEDULING_DECISIONS.add(new SchedulingDecision(
                        cloudletConfig.id,
                        "N/A",
                        cloudletConfig.ramRequirementMb,
                        0,
                        0.0,
                        "REJECTED",
                        "No feasible VM available",
                        algorithm
                ));
                cloudletConfig.status = "Rejected";
                continue;
            }

            broker.bindCloudletToVm(cloudlet, selected);
            broker.submitCloudlet(cloudlet);
            actualCloudlets.add(cloudlet);
            double estimatedTime = estimateCompletionTime(cloudletConfig.length, selected);
            String reason = buildDecisionReason(cloudletConfig, selected, algorithm, estimatedTime);
            String decision = "ACCEPTED";
            SCHEDULING_DECISIONS.add(new SchedulingDecision(
                    cloudletConfig.id,
                    String.valueOf(selected.getId()),
                    cloudletConfig.ramRequirementMb,
                    selected.getRam().getCapacity(),
                    estimatedTime,
                    decision,
                    reason,
                    algorithm
            ));
            cloudletConfig.status = "Submitted";
        }

        cloudSim.start();
        List<Cloudlet> executedCloudlets = actualCloudlets.stream()
                .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS || c.getStatus() == Cloudlet.Status.INEXEC || c.getStatus() == Cloudlet.Status.QUEUED)
                .collect(Collectors.toList());

        ExperimentResult result = new ExperimentResult();
        result.id = UUID.randomUUID().toString();
        result.algorithm = algorithm;
        result.configuration = new ExperimentConfiguration();
        result.configuration.vmCount = VM_CONFIGS.size();
        result.configuration.cloudletCount = CLOUDLET_CONFIGS.size();
        result.configuration.algorithm = algorithm;
        result.configuration.runs = Math.max(1, request.repetitions);
        result.metrics = computeMetrics(executedCloudlets, hosts, cloudSim.clock());
        result.status = "Completed";
        result.timestamp = System.currentTimeMillis();
        result.completedCloudlets = executedCloudlets.size();
        result.failedCloudlets = (int) actualCloudlets.stream().filter(c -> c.getStatus() == Cloudlet.Status.FAILED).count();
        result.runs = Math.max(1, request.repetitions);

        for (Cloudlet cloudlet : executedCloudlets) {
            for (CloudletConfig config : CLOUDLET_CONFIGS) {
                if (config.id.equals(cloudlet.getId() + "")) {
                    config.status = "Completed";
                }
            }
        }

        return result;
    }

    private static Vm selectVm(List<Vm> vms, CloudletConfig cloudletConfig, String algorithm) {
        List<Vm> feasible = vms.stream()
                .filter(vm -> vm.getRam().getCapacity() >= cloudletConfig.ramRequirementMb)
                .collect(Collectors.toList());
        if (feasible.isEmpty()) {
            return null;
        }
        if ("baseline".equals(algorithm)) {
            return feasible.stream()
                    .sorted(Comparator
                            .comparingDouble((Vm vm) -> -vm.getMips())
                            .thenComparingDouble(vm -> estimateCompletionTime(cloudletConfig.length, vm)))
                    .findFirst()
                    .orElse(null);
        }
        return feasible.stream()
                .sorted(Comparator
                        .comparingDouble((Vm vm) -> estimateCompletionTime(cloudletConfig.length, vm))
                        .thenComparingDouble(vm -> -vm.getRam().getCapacity())
                        .thenComparingLong(vm -> vm.getId()))
                .findFirst()
                .orElse(null);
    }

    private static double estimateCompletionTime(long length, Vm vm) {
        if (vm == null || vm.getMips() <= 0) {
            return Double.MAX_VALUE;
        }
        return length / (vm.getMips() * vm.getNumberOfPes());
    }

    private static String buildDecisionReason(CloudletConfig cloudletConfig, Vm selected, String algorithm, double estimatedTime) {
        if ("baseline".equals(algorithm)) {
            return "CPU feasible + best MIPS score + estimated completion time: " + String.format("%.2f", estimatedTime) + "s";
        }
        return "CPU feasible + RAM sufficient + best estimated completion time: " + String.format("%.2f", estimatedTime) + "s";
    }

    private static Metrics computeMetrics(List<Cloudlet> cloudlets, List<Host> hosts, double simulationTime) {
        Metrics metrics = new Metrics();
        double maxFinish = cloudlets.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0.0);
        double totalCpuCapacity = hosts.stream().mapToDouble(Host::getTotalMipsCapacity).sum();
        double totalUsedCpu = cloudlets.stream()
                .filter(cloudlet -> cloudlet.getVm() != null)
                .mapToDouble(cloudlet -> cloudlet.getActualCpuTime() * cloudlet.getVm().getMips() * cloudlet.getNumberOfPes())
                .sum();
        double totalAvailableRam = hosts.stream().mapToDouble(host -> host.getRam().getCapacity()).sum();
        double totalAssignedRam = cloudlets.stream()
                .filter(cloudlet -> cloudlet.getVm() != null)
                .mapToDouble(cloudlet -> cloudlet.getVm().getRam().getCapacity())
                .sum();
        double averageResponse = cloudlets.stream().mapToDouble(cloudlet -> cloudlet.getFinishTime() - cloudlet.getExecStartTime()).average().orElse(0.0);
        double meanLoad = cloudlets.isEmpty() ? 0 : cloudlets.stream().mapToDouble(cloudlet -> cloudlet.getVm() != null ? (double) cloudlet.getVm().getMips() * cloudlet.getNumberOfPes() : 0.0).average().orElse(0.0);
        double variance = cloudlets.isEmpty() ? 0 : cloudlets.stream()
                .mapToDouble(cloudlet -> cloudlet.getVm() != null ? Math.pow((cloudlet.getVm().getMips() * cloudlet.getNumberOfPes()) - meanLoad, 2) : 0.0)
                .average()
                .orElse(0.0);
        double loadBalancingEfficiency = meanLoad == 0 ? 0.0 : Math.max(0.0, 100.0 * (1.0 - Math.sqrt(variance) / meanLoad));

        metrics.makespan = maxFinish;
        metrics.executionTime = cloudlets.stream().mapToDouble(Cloudlet::getActualCpuTime).average().orElse(0.0);
        metrics.cpuUtilization = (simulationTime <= 0 || totalCpuCapacity <= 0) ? 0 : (totalUsedCpu / (totalCpuCapacity * simulationTime)) * 100.0;
        metrics.ramUtilization = (totalAvailableRam <= 0) ? 0 : (totalAssignedRam / totalAvailableRam) * 100.0;
        metrics.responseTime = averageResponse;
        metrics.loadBalancingEfficiency = loadBalancingEfficiency;
        return metrics;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        System.out.println("sendJson status=" + statusCode + " body=" + body);
        ensureCors(exchange);
        byte[] response = OBJECT_MAPPER.writeValueAsBytes(body);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void sendOptions(HttpExchange exchange) throws IOException {
        ensureCors(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    private static void ensureCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static <T> T readBody(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        if (raw.length == 0) {
            return null;
        }
        return OBJECT_MAPPER.readValue(raw, type);
    }

    private static String extractId(HttpExchange exchange) {
        String rawPath = exchange.getRequestURI().getPath();
        String[] parts = rawPath.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    public static class ResourceConfig {
        public String id;
        public String name;
        public int hostCount;
        public int hostMips;
        public int hostPes;
        public long hostRamMb;
        public long hostBandwidthMbps;
        public long hostStorageGb;
        public double schedulingInterval;
    }

    public static class VmConfig {
        public String id;
        public double mips;
        public long pes;
        public long ramMb;
        public long bandwidthMbps;
        public long storageGb;
        public String status;
        public String currentCloudlet;
        public double utilization;
    }

    public static class CloudletConfig {
        public String id;
        public long length;
        public long pes;
        public long fileSize;
        public long outputSize;
        public long ramRequirementMb;
        public String status;
    }

    public static class SchedulingDecision {
        public String cloudletId;
        public String selectedVmId;
        public long requiredRamMb;
        public long availableRamMb;
        public double estimatedCompletionTime;
        public String decision;
        public String reason;
        public String algorithm;

        public SchedulingDecision() {
        }

        public SchedulingDecision(String cloudletId, String selectedVmId, long requiredRamMb, long availableRamMb,
                                 double estimatedCompletionTime, String decision, String reason, String algorithm) {
            this.cloudletId = cloudletId;
            this.selectedVmId = selectedVmId;
            this.requiredRamMb = requiredRamMb;
            this.availableRamMb = availableRamMb;
            this.estimatedCompletionTime = estimatedCompletionTime;
            this.decision = decision;
            this.reason = reason;
            this.algorithm = algorithm;
        }
    }

    public static class ExperimentRequest {
        public String algorithm;
        public int repetitions;
        public String notes;
    }

    public static class ExperimentConfiguration {
        public String algorithm;
        public int vmCount;
        public int cloudletCount;
        public int runs;
    }

    public static class Metrics {
        public double makespan;
        public double executionTime;
        public double cpuUtilization;
        public double ramUtilization;
        public double responseTime;
        public double loadBalancingEfficiency;
    }

    public static class ExperimentResult {
        public String id;
        public long timestamp;
        public String algorithm;
        public ExperimentConfiguration configuration;
        public Metrics metrics;
        public String status;
        public int completedCloudlets;
        public int failedCloudlets;
        public int runs;
    }

    public static class DashboardSummary {
        public int activeVms;
        public int pendingTasks;
        public int completedTasks;
        public String resourceStatus;
        public String latestExperiment;
        public List<SchedulingDecision> recentSchedulingDecisions;
        public List<String> alerts;
    }

    public static class AnalyticsResponse {
        public boolean available;
        public List<ExperimentResult> results;
    }
}
