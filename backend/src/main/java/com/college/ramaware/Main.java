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
import org.cloudbus.cloudsim.core.CloudSimEntity;
import org.cloudbus.cloudsim.core.events.SimEvent;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<ResourceConfig> RESOURCE_CONFIGS = new CopyOnWriteArrayList<>();
    private static final List<VmConfig> VM_CONFIGS = new CopyOnWriteArrayList<>();
    private static final List<CloudletConfig> CLOUDLET_CONFIGS = new CopyOnWriteArrayList<>();
    private static final List<SchedulingDecision> SCHEDULING_DECISIONS = new CopyOnWriteArrayList<>();
    private static final List<ExperimentResult> EXPERIMENT_RESULTS = new CopyOnWriteArrayList<>();
    private static final Object CONFIGURATION_LOCK = new Object();
    private static final AtomicBoolean EXPERIMENT_RUNNING = new AtomicBoolean();

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
        server.createContext("/api/reset", Main::handleReset);
        server.setExecutor(new ThreadPoolExecutor(
                4,
                4,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32)));
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
            synchronized (CONFIGURATION_LOCK) {
                if (EXPERIMENT_RUNNING.get()) {
                    sendJson(exchange, 409, Map.of("error", "An experiment is running; configuration cannot be changed."));
                    return;
                }
                if (RESOURCE_CONFIGS.isEmpty()) {
                    RESOURCE_CONFIGS.add(config);
                } else {
                    RESOURCE_CONFIGS.set(0, config);
                }
            }
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
            synchronized (CONFIGURATION_LOCK) {
                if (EXPERIMENT_RUNNING.get()) {
                    sendJson(exchange, 409, Map.of("error", "An experiment is running; configuration cannot be changed."));
                    return;
                }
                VM_CONFIGS.add(vm);
            }
            sendJson(exchange, 201, vm);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            String id = extractId(exchange);
            synchronized (CONFIGURATION_LOCK) {
                if (EXPERIMENT_RUNNING.get()) {
                    sendJson(exchange, 409, Map.of("error", "An experiment is running; configuration cannot be changed."));
                    return;
                }
                VM_CONFIGS.removeIf(vm -> vm.id.equals(id));
            }
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
            synchronized (CONFIGURATION_LOCK) {
                if (EXPERIMENT_RUNNING.get()) {
                    sendJson(exchange, 409, Map.of("error", "An experiment is running; configuration cannot be changed."));
                    return;
                }
                CLOUDLET_CONFIGS.add(cloudlet);
            }
            sendJson(exchange, 201, cloudlet);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            String id = extractId(exchange);
            synchronized (CONFIGURATION_LOCK) {
                if (EXPERIMENT_RUNNING.get()) {
                    sendJson(exchange, 409, Map.of("error", "An experiment is running; configuration cannot be changed."));
                    return;
                }
                CLOUDLET_CONFIGS.removeIf(c -> c.id.equals(id));
            }
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
            synchronized (CONFIGURATION_LOCK) {
                if (!EXPERIMENT_RUNNING.compareAndSet(false, true)) {
                    sendJson(exchange, 409, Map.of("error", "An experiment is already running."));
                    return;
                }
            }
            try {
                ExperimentResult result = runExperiment(request);
                EXPERIMENT_RESULTS.add(result);
                sendJson(exchange, 200, result);
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, Map.of("error", ex.getMessage()));
            } finally {
                synchronized (CONFIGURATION_LOCK) {
                    EXPERIMENT_RUNNING.set(false);
                }
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

    private static void handleReset(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendOptions(exchange);
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        synchronized (CONFIGURATION_LOCK) {
            if (EXPERIMENT_RUNNING.get()) {
                sendJson(exchange, 409, Map.of("error", "An experiment is running; configuration cannot be reset."));
                return;
            }
            RESOURCE_CONFIGS.clear();
            VM_CONFIGS.clear();
            CLOUDLET_CONFIGS.clear();
            SCHEDULING_DECISIONS.clear();
            EXPERIMENT_RESULTS.clear();
        }
        sendJson(exchange, 200, Map.of("status", "cleared"));
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

        String algorithm = (request.algorithm == null || request.algorithm.isBlank())
                ? "ram-aware"
                : request.algorithm.trim().toLowerCase();
        if (!"baseline".equals(algorithm) && !"ram-aware".equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported scheduling algorithm: " + algorithm);
        }
        int repetitions = Math.max(1, request.repetitions);
        List<RunResult> runResults = new ArrayList<>(repetitions);
        for (int runNumber = 1; runNumber <= repetitions; runNumber++) {
            runResults.add(runCloudSim(algorithm, runNumber));
        }

        ExperimentResult result = new ExperimentResult();
        result.id = UUID.randomUUID().toString();
        result.algorithm = algorithm;
        result.configuration = new ExperimentConfiguration();
        result.configuration.vmCount = VM_CONFIGS.size();
        result.configuration.cloudletCount = CLOUDLET_CONFIGS.size();
        result.configuration.algorithm = algorithm;
        result.configuration.runs = repetitions;
        result.metrics = averageMetrics(runResults);
        result.status = "Completed";
        result.timestamp = System.currentTimeMillis();
        result.completedCloudlets = runResults.stream().mapToInt(run -> run.completedCloudlets).sum();
        result.rejectedCloudlets = runResults.stream().mapToInt(run -> run.rejectedCloudlets).sum();
        result.failedCloudlets = runResults.stream().mapToInt(run -> run.failedCloudlets).sum();
        result.runs = repetitions;
        return result;
    }

    private static RunResult runCloudSim(String algorithm, int runNumber) {
        ResourceConfig hostConfig = RESOURCE_CONFIGS.get(0);
        CloudSim cloudSim = new CloudSim();
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < hostConfig.hostCount; i++) {
            List<Pe> peList = IntStream.range(0, hostConfig.hostPes)
                    .mapToObj(index -> new PeSimple(hostConfig.hostMips))
                    .collect(Collectors.toList());
            hosts.add(new HostSimple(
                    hostConfig.hostRamMb,
                    hostConfig.hostBandwidthMbps,
                    hostConfig.hostStorageGb * 1024L,
                    peList));
        }

        new DatacenterSimple(cloudSim, hosts, new VmAllocationPolicySimple());
        DatacenterBroker broker = new DatacenterBrokerSimple(cloudSim);
        List<VmRuntime> vmRuntimes = new ArrayList<>();
        Map<Vm, VmRuntime> runtimeByVm = new IdentityHashMap<>();
        for (VmConfig vmConfig : VM_CONFIGS) {
            Vm vm = new VmSimple(vmConfig.mips, vmConfig.pes);
            vm.setRam(vmConfig.ramMb);
            vm.setBw(vmConfig.bandwidthMbps);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            broker.submitVm(vm);
            VmRuntime runtime = new VmRuntime(vm, vmConfig);
            vmRuntimes.add(runtime);
            runtimeByVm.put(vm, runtime);
            vmConfig.currentCloudlet = "None";
            vmConfig.utilization = null;
        }

        RamUsageTracker ramUsage = new RamUsageTracker();
        List<CloudletRuntime> waitingCloudlets = new ArrayList<>();
        Map<Cloudlet, CloudletRuntime> runtimeByCloudlet = new IdentityHashMap<>();
        for (CloudletConfig cloudletConfig : CLOUDLET_CONFIGS) {
            cloudletConfig.status = "Queued";
            CloudletSimple cloudlet = new CloudletSimple(cloudletConfig.length, cloudletConfig.pes);
            cloudlet.setFileSize(cloudletConfig.fileSize);
            cloudlet.setOutputSize(cloudletConfig.outputSize);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(1.0));
            CloudletRuntime runtime = new CloudletRuntime(cloudlet, cloudletConfig);
            waitingCloudlets.add(runtime);
            runtimeByCloudlet.put(cloudlet, runtime);
        }

        RunScheduler scheduler = new RunScheduler(
                broker, vmRuntimes, waitingCloudlets, ramUsage, algorithm, runNumber);
        new SchedulerDispatchEntity(cloudSim, broker, scheduler, vmRuntimes.size());
        for (CloudletRuntime runtime : waitingCloudlets) {
            runtime.cloudlet.addOnStartListener(info -> {
                runtime.config.status = "Running";
                VmRuntime vmRuntime = runtimeByVm.get(runtime.cloudlet.getVm());
                if (vmRuntime != null) {
                    vmRuntime.activeCloudletIds.add(runtime.config.id);
                    vmRuntime.updateCurrentCloudlet();
                }
            });
            runtime.cloudlet.addOnFinishListener(info -> {
                runtime.config.status = runtime.cloudlet.getStatus() == Cloudlet.Status.SUCCESS
                        ? "Completed"
                        : "Failed";
                VmRuntime vmRuntime = runtimeByVm.get(runtime.cloudlet.getVm());
                if (vmRuntime != null) {
                    vmRuntime.release(runtime.config);
                    vmRuntime.activeCloudletIds.remove(runtime.config.id);
                    vmRuntime.updateCurrentCloudlet();
                }
                ramUsage.release(runtime.config.ramRequirementMb, info.getTime());
                scheduler.dispatchPending();
            });
        }

        cloudSim.start();
        double simulationTime = cloudSim.clock();
        ramUsage.finish(simulationTime);
        List<Cloudlet> completedCloudlets = runtimeByCloudlet.keySet().stream()
                .filter(cloudlet -> cloudlet.getStatus() == Cloudlet.Status.SUCCESS)
                .collect(Collectors.toList());
        int failedCloudlets = 0;
        int rejectedCloudlets = 0;
        for (Map.Entry<Cloudlet, CloudletRuntime> entry : runtimeByCloudlet.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            CloudletConfig config = entry.getValue().config;
            if (cloudlet.getStatus() == Cloudlet.Status.SUCCESS) {
                config.status = "Completed";
            } else if (cloudlet.getStatus() == Cloudlet.Status.FAILED || cloudlet.isFinished()) {
                config.status = "Failed";
                failedCloudlets++;
            } else if (!"Rejected".equals(config.status)) {
                config.status = "Failed";
                failedCloudlets++;
            }
            if ("Rejected".equals(config.status)) {
                rejectedCloudlets++;
            }
        }

        RunResult result = new RunResult();
        result.metrics = computeMetrics(completedCloudlets, vmRuntimes, hosts, simulationTime, ramUsage);
        result.completedCloudlets = completedCloudlets.size();
        result.rejectedCloudlets = rejectedCloudlets;
        result.failedCloudlets = failedCloudlets;
        updateVmUtilization(vmRuntimes, completedCloudlets, simulationTime);
        for (VmRuntime vmRuntime : vmRuntimes) {
            vmRuntime.config.currentCloudlet = "None";
        }
        return result;
    }

    private static double estimateCompletionTime(long length, Vm vm) {
        if (vm == null || vm.getMips() <= 0) {
            return Double.MAX_VALUE;
        }
        return length / (vm.getMips() * vm.getNumberOfPes());
    }

    private static Metrics computeMetrics(
            List<Cloudlet> cloudlets,
            List<VmRuntime> vmRuntimes,
            List<Host> hosts,
            double simulationTime,
            RamUsageTracker ramUsage) {
        Metrics metrics = new Metrics();
        if (cloudlets.isEmpty() || simulationTime <= 0) {
            return metrics;
        }

        double totalHostMips = hosts.stream().mapToDouble(Host::getTotalMipsCapacity).sum();
        double totalHostRamMb = hosts.stream().mapToDouble(host -> host.getRam().getCapacity()).sum();
        double totalCpuWorkMi = 0;
        double responseTimeTotal = 0;
        double executionTimeTotal = 0;
        double makespan = 0;
        Map<VmRuntime, Double> cpuWorkByVm = new HashMap<>();
        for (Cloudlet cloudlet : cloudlets) {
            double cpuWorkMi = (double) cloudlet.getLength() * cloudlet.getNumberOfPes();
            totalCpuWorkMi += cpuWorkMi;
            makespan = Math.max(makespan, cloudlet.getFinishTime());
            executionTimeTotal += cloudlet.getActualCpuTime();
            // All configured cloudlets are available at experiment time zero; finish time includes queueing.
            responseTimeTotal += cloudlet.getFinishTime();
            VmRuntime vmRuntime = findVmRuntime(vmRuntimes, cloudlet.getVm());
            if (vmRuntime != null) {
                cpuWorkByVm.merge(vmRuntime, cpuWorkMi, Double::sum);
            }
        }

        metrics.makespan = makespan;
        metrics.executionTime = executionTimeTotal / cloudlets.size();
        metrics.responseTime = responseTimeTotal / cloudlets.size();
        if (totalHostMips > 0) {
            // Cloudlet length is MI; MIPS x seconds is the host's available MI over the simulated interval.
            metrics.cpuUtilization = totalCpuWorkMi / (totalHostMips * simulationTime) * 100.0;
        }
        if (totalHostRamMb > 0) {
            metrics.ramUtilization = ramUsage.utilizationPercent(totalHostRamMb, simulationTime);
        }

        double sumWork = cpuWorkByVm.values().stream().mapToDouble(Double::doubleValue).sum();
        double sumSquaredWork = vmRuntimes.stream()
                .mapToDouble(runtime -> Math.pow(cpuWorkByVm.getOrDefault(runtime, 0.0), 2))
                .sum();
        if (!vmRuntimes.isEmpty() && sumSquaredWork > 0) {
            // Jain's fairness index over actual completed CPU work, including VMs with no assigned work.
            metrics.loadBalancingEfficiency =
                    (sumWork * sumWork) / (vmRuntimes.size() * sumSquaredWork) * 100.0;
        }
        return metrics;
    }

    private static void updateVmUtilization(
            List<VmRuntime> vmRuntimes, List<Cloudlet> completedCloudlets, double simulationTime) {
        if (simulationTime <= 0) {
            return;
        }
        Map<VmRuntime, Double> cpuWorkByVm = new HashMap<>();
        for (Cloudlet cloudlet : completedCloudlets) {
            VmRuntime vmRuntime = findVmRuntime(vmRuntimes, cloudlet.getVm());
            if (vmRuntime != null) {
                cpuWorkByVm.merge(
                        vmRuntime,
                        (double) cloudlet.getLength() * cloudlet.getNumberOfPes(),
                        Double::sum);
            }
        }
        for (VmRuntime vmRuntime : vmRuntimes) {
            double capacityMips = vmRuntime.vm.getMips() * vmRuntime.vm.getNumberOfPes();
            if (capacityMips > 0) {
                vmRuntime.config.utilization =
                        cpuWorkByVm.getOrDefault(vmRuntime, 0.0) / (capacityMips * simulationTime) * 100.0;
            }
        }
    }

    private static VmRuntime findVmRuntime(List<VmRuntime> vmRuntimes, Vm vm) {
        return vmRuntimes.stream().filter(runtime -> runtime.vm == vm).findFirst().orElse(null);
    }

    private static Metrics averageMetrics(List<RunResult> runResults) {
        Metrics aggregate = new Metrics();
        aggregate.makespan = averageMetric(runResults, metrics -> metrics.makespan);
        aggregate.executionTime = averageMetric(runResults, metrics -> metrics.executionTime);
        aggregate.cpuUtilization = averageMetric(runResults, metrics -> metrics.cpuUtilization);
        aggregate.ramUtilization = averageMetric(runResults, metrics -> metrics.ramUtilization);
        aggregate.responseTime = averageMetric(runResults, metrics -> metrics.responseTime);
        aggregate.loadBalancingEfficiency =
                averageMetric(runResults, metrics -> metrics.loadBalancingEfficiency);
        return aggregate;
    }

    private static Double averageMetric(List<RunResult> runResults, Function<Metrics, Double> metric) {
        return runResults.stream()
                .map(run -> metric.apply(run.metrics))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private static String buildDecisionReason(
            long availableRamMb,
            double availableMips,
            long availablePes,
            double requiredMips,
            double estimatedTime) {
        return "RAM feasible (" + availableRamMb + " MB available); CPU feasible ("
                + availablePes + " PE(s), " + String.format("%.2f", availableMips)
                + " MIPS available; " + String.format("%.2f", requiredMips)
                + " MIPS required); estimated completion time "
                + String.format("%.2f", estimatedTime) + "s.";
    }

    private static class VmRuntime {
        private final Vm vm;
        private final VmConfig config;
        private final Set<String> activeCloudletIds = new LinkedHashSet<>();
        private long allocatedRamMb;
        private long allocatedPes;
        private double allocatedMips;

        private VmRuntime(Vm vm, VmConfig config) {
            this.vm = vm;
            this.config = config;
        }

        private long availableRamMb() {
            return Math.max(0, vm.getRam().getCapacity() - allocatedRamMb);
        }

        private long availablePes() {
            return Math.max(0, vm.getNumberOfPes() - allocatedPes);
        }

        private double availableMips() {
            return Math.max(0, vm.getMips() * vm.getNumberOfPes() - allocatedMips);
        }

        private double requiredMips(CloudletConfig cloudlet) {
            return vm.getMips() * cloudlet.pes;
        }

        private boolean hasTotalCpuCapacity(CloudletConfig cloudlet) {
            return vm.getMips() > 0 && cloudlet.pes <= vm.getNumberOfPes();
        }

        private boolean canRunNow(CloudletConfig cloudlet) {
            double requiredMips = requiredMips(cloudlet);
            return hasTotalCpuCapacity(cloudlet)
                    && cloudlet.pes <= availablePes()
                    && requiredMips <= availableMips() + 1e-9;
        }

        private void reserve(CloudletConfig cloudlet) {
            allocatedRamMb += cloudlet.ramRequirementMb;
            allocatedPes += cloudlet.pes;
            allocatedMips += requiredMips(cloudlet);
        }

        private void release(CloudletConfig cloudlet) {
            allocatedRamMb -= cloudlet.ramRequirementMb;
            allocatedPes -= cloudlet.pes;
            allocatedMips -= requiredMips(cloudlet);
            if (allocatedRamMb < 0 || allocatedPes < 0 || allocatedMips < -1e-9) {
                throw new IllegalStateException("Cloudlet resource reservation underflow for VM " + config.id);
            }
            allocatedMips = Math.max(0, allocatedMips);
        }

        private void updateCurrentCloudlet() {
            if (activeCloudletIds.isEmpty()) {
                config.currentCloudlet = "None";
            } else if (activeCloudletIds.size() == 1) {
                config.currentCloudlet = activeCloudletIds.iterator().next();
            } else {
                config.currentCloudlet = "Multiple";
            }
        }
    }

    private static class CloudletRuntime {
        private final Cloudlet cloudlet;
        private final CloudletConfig config;

        private CloudletRuntime(Cloudlet cloudlet, CloudletConfig config) {
            this.cloudlet = cloudlet;
            this.config = config;
        }
    }

    private static class RunResult {
        private Metrics metrics;
        private int completedCloudlets;
        private int rejectedCloudlets;
        private int failedCloudlets;
    }

    private static class RamUsageTracker {
        private long allocatedRamMb;
        private double lastUpdateTime;
        private double allocatedRamMbSeconds;

        private void advanceTo(double time) {
            allocatedRamMbSeconds += allocatedRamMb * Math.max(0, time - lastUpdateTime);
            lastUpdateTime = Math.max(lastUpdateTime, time);
        }

        private void reserve(long ramMb, double time) {
            advanceTo(time);
            allocatedRamMb += ramMb;
        }

        private void release(long ramMb, double time) {
            advanceTo(time);
            allocatedRamMb -= ramMb;
            if (allocatedRamMb < 0) {
                throw new IllegalStateException("Cloudlet RAM reservation underflow.");
            }
        }

        private void finish(double time) {
            advanceTo(time);
        }

        private double utilizationPercent(double totalRamMb, double simulationTime) {
            return allocatedRamMbSeconds / (totalRamMb * simulationTime) * 100.0;
        }
    }

    private static class RunScheduler {
        private final DatacenterBroker broker;
        private final List<VmRuntime> vmRuntimes;
        private final List<CloudletRuntime> waitingCloudlets;
        private final RamUsageTracker ramUsage;
        private final String algorithm;
        private final int runNumber;

        private RunScheduler(
                DatacenterBroker broker,
                List<VmRuntime> vmRuntimes,
                List<CloudletRuntime> waitingCloudlets,
                RamUsageTracker ramUsage,
                String algorithm,
                int runNumber) {
            this.broker = broker;
            this.vmRuntimes = vmRuntimes;
            this.waitingCloudlets = waitingCloudlets;
            this.ramUsage = ramUsage;
            this.algorithm = algorithm;
            this.runNumber = runNumber;
        }

        private void dispatchPending() {
            List<VmRuntime> createdVms = vmRuntimes.stream()
                    .filter(runtime -> broker.getVmCreatedList().contains(runtime.vm))
                    .collect(Collectors.toList());
            Iterator<CloudletRuntime> iterator = waitingCloudlets.iterator();
            while (iterator.hasNext()) {
                CloudletRuntime runtime = iterator.next();
                String rejection = permanentRejectionReason(runtime.config, createdVms);
                if (rejection != null) {
                    runtime.config.status = "Rejected";
                    SCHEDULING_DECISIONS.add(new SchedulingDecision(
                            runtime.config.id,
                            "N/A",
                            runtime.config.ramRequirementMb,
                            maxAvailableRam(createdVms),
                            0,
                            "REJECTED",
                            rejection,
                            algorithm,
                            runNumber,
                            0,
                            maxAvailableMips(createdVms),
                            0));
                    iterator.remove();
                    continue;
                }

                VmRuntime selected = selectVm(createdVms, runtime.config);
                if (selected == null) {
                    runtime.config.status = "Queued";
                    continue;
                }

                long availableRamMb = selected.availableRamMb();
                double availableMips = selected.availableMips();
                long availablePes = selected.availablePes();
                double requiredMips = selected.requiredMips(runtime.config);
                double estimatedTime = estimateCompletionTime(runtime.config.length, selected.vm);

                selected.reserve(runtime.config);
                ramUsage.reserve(runtime.config.ramRequirementMb, broker.getSimulation().clock());
                runtime.cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(
                        UtilizationModel.Unit.ABSOLUTE,
                        runtime.config.ramRequirementMb));
                broker.submitCloudlet(runtime.cloudlet);
                boolean bound = broker.bindCloudletToVm(runtime.cloudlet, selected.vm);
                if (!bound) {
                    selected.release(runtime.config);
                    ramUsage.release(runtime.config.ramRequirementMb, broker.getSimulation().clock());
                    runtime.config.status = "Failed";
                    SCHEDULING_DECISIONS.add(new SchedulingDecision(
                            runtime.config.id,
                            "N/A",
                            runtime.config.ramRequirementMb,
                            availableRamMb,
                            estimatedTime,
                            "REJECTED",
                            "CloudSim broker refused the Cloudlet-to-VM binding.",
                            algorithm,
                            runNumber,
                            requiredMips,
                            availableMips,
                            availablePes));
                    iterator.remove();
                    continue;
                }

                runtime.config.status = "Submitted";
                SCHEDULING_DECISIONS.add(new SchedulingDecision(
                        runtime.config.id,
                        selected.config.id,
                        runtime.config.ramRequirementMb,
                        availableRamMb,
                        estimatedTime,
                        "ACCEPTED",
                        "baseline".equals(algorithm)
                                ? "Baseline policy selected the first CPU-feasible VM in CloudSim VM order; RAM was not used for admission or ranking."
                                : buildDecisionReason(
                                        availableRamMb, availableMips, availablePes, requiredMips, estimatedTime),
                        algorithm,
                        runNumber,
                        requiredMips,
                        availableMips,
                        availablePes));
                iterator.remove();
            }
        }

        private String permanentRejectionReason(CloudletConfig cloudlet, List<VmRuntime> createdVms) {
            if (createdVms.isEmpty()) {
                return "No VM was successfully created in CloudSim.";
            }
            boolean ramFeasible = !"ram-aware".equals(algorithm) || createdVms.stream()
                    .anyMatch(vm -> vm.vm.getRam().getCapacity() >= cloudlet.ramRequirementMb);
            if (!ramFeasible) {
                return "RAM infeasible: no created VM has "
                        + cloudlet.ramRequirementMb + " MB total RAM.";
            }
            boolean cpuFeasible = createdVms.stream().anyMatch(vm -> vm.hasTotalCpuCapacity(cloudlet));
            if (!cpuFeasible) {
                return "CPU/MIPS infeasible: no created VM has positive MIPS and at least "
                        + cloudlet.pes + " processing element(s).";
            }
            return null;
        }

        private VmRuntime selectVm(List<VmRuntime> candidates, CloudletConfig cloudlet) {
            if ("baseline".equals(algorithm)) {
                return selectBaselineVm(candidates, cloudlet);
            }
            return selectRamAwareVm(candidates, cloudlet);
        }

        private VmRuntime selectBaselineVm(List<VmRuntime> candidates, CloudletConfig cloudlet) {
            return candidates.stream()
                    .filter(vm -> vm.canRunNow(cloudlet))
                    .min(Comparator.comparingLong(vm -> vm.vm.getId()))
                    .orElse(null);
        }

        private VmRuntime selectRamAwareVm(List<VmRuntime> candidates, CloudletConfig cloudlet) {
            return candidates.stream()
                    .filter(vm -> vm.availableRamMb() >= cloudlet.ramRequirementMb)
                    .filter(vm -> vm.canRunNow(cloudlet))
                    .sorted(Comparator
                            .comparingDouble((VmRuntime vm) -> estimateCompletionTime(cloudlet.length, vm.vm))
                            .thenComparing(Comparator.comparingLong(VmRuntime::availableRamMb).reversed())
                            .thenComparingLong(vm -> vm.vm.getId()))
                    .findFirst()
                    .orElse(null);
        }

        private long maxAvailableRam(List<VmRuntime> candidates) {
            return candidates.stream().mapToLong(VmRuntime::availableRamMb).max().orElse(0);
        }

        private double maxAvailableMips(List<VmRuntime> candidates) {
            return candidates.stream().mapToDouble(VmRuntime::availableMips).max().orElse(0);
        }
    }

    private static class SchedulerDispatchEntity extends CloudSimEntity {
        private static final int DISPATCH_AFTER_VM_CREATION = 10001;
        private final DatacenterBroker broker;
        private final RunScheduler scheduler;
        private final int requestedVmCount;

        private SchedulerDispatchEntity(
                CloudSim cloudSim,
                DatacenterBroker broker,
                RunScheduler scheduler,
                int requestedVmCount) {
            super(cloudSim);
            this.broker = broker;
            this.scheduler = scheduler;
            this.requestedVmCount = requestedVmCount;
            setName("RamAwareScheduler");
        }

        @Override
        protected void startInternal() {
            schedule(getSimulation().getMinTimeBetweenEvents(), DISPATCH_AFTER_VM_CREATION);
        }

        @Override
        public void processEvent(SimEvent event) {
            if (event.getTag() != DISPATCH_AFTER_VM_CREATION) {
                return;
            }
            int completedVmRequests = broker.getVmCreatedList().size() + broker.getVmFailedList().size();
            if (completedVmRequests >= requestedVmCount) {
                scheduler.dispatchPending();
                return;
            }
            schedule(getSimulation().getMinTimeBetweenEvents(), DISPATCH_AFTER_VM_CREATION);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        System.out.println("sendJson status=" + statusCode + " body=" + body);
        ensureCors(exchange);
        byte[] response = OBJECT_MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
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
        public volatile String status;
        public volatile String currentCloudlet;
        public volatile Double utilization;
    }

    public static class CloudletConfig {
        public String id;
        public long length;
        public long pes;
        public long fileSize;
        public long outputSize;
        public long ramRequirementMb;
        public volatile String status;
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
        public int runNumber;
        public double requiredMips;
        public double availableMips;
        public long availablePes;

        public SchedulingDecision() {
        }

        public SchedulingDecision(String cloudletId, String selectedVmId, long requiredRamMb, long availableRamMb,
                                 double estimatedCompletionTime, String decision, String reason, String algorithm) {
            this(cloudletId, selectedVmId, requiredRamMb, availableRamMb, estimatedCompletionTime,
                    decision, reason, algorithm, 1, 0, 0, 0);
        }

        public SchedulingDecision(
                String cloudletId,
                String selectedVmId,
                long requiredRamMb,
                long availableRamMb,
                double estimatedCompletionTime,
                String decision,
                String reason,
                String algorithm,
                int runNumber,
                double requiredMips,
                double availableMips,
                long availablePes) {
            this.cloudletId = cloudletId;
            this.selectedVmId = selectedVmId;
            this.requiredRamMb = requiredRamMb;
            this.availableRamMb = availableRamMb;
            this.estimatedCompletionTime = estimatedCompletionTime;
            this.decision = decision;
            this.reason = reason;
            this.algorithm = algorithm;
            this.runNumber = runNumber;
            this.requiredMips = requiredMips;
            this.availableMips = availableMips;
            this.availablePes = availablePes;
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
        public Double makespan;
        public Double executionTime;
        public Double cpuUtilization;
        public Double ramUtilization;
        public Double responseTime;
        public Double loadBalancingEfficiency;
    }

    public static class ExperimentResult {
        public String id;
        public long timestamp;
        public String algorithm;
        public ExperimentConfiguration configuration;
        public Metrics metrics;
        public String status;
        public int completedCloudlets;
        public int rejectedCloudlets;
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
