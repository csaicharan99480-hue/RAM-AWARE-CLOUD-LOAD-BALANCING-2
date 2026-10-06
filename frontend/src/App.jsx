import { useEffect, useState } from 'react';

const API_BASE = 'http://localhost:8080';
const NAV_ITEMS = [
  'Dashboard',
  'Virtual Machines',
  'Cloudlets / Tasks',
  'Resources',
  'Scheduling',
  'Experiments',
  'Research Analytics',
  'Monitoring'
];

const emptyResourceForm = {
  name: '',
  hostCount: '',
  hostMips: '',
  hostPes: '',
  hostRamMb: '',
  hostBandwidthMbps: '',
  hostStorageGb: '',
  schedulingInterval: ''
};

const emptyVmForm = {
  id: '',
  mips: '',
  pes: '',
  ramMb: '',
  bandwidthMbps: '',
  storageGb: ''
};

const emptyCloudletForm = {
  id: '',
  length: '',
  pes: '',
  fileSize: '',
  outputSize: '',
  ramRequirementMb: ''
};

const emptyExperimentForm = {
  algorithm: 'ram-aware',
  repetitions: 3
};

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });

  const text = await response.text();
  if (!text) return null;
  const data = JSON.parse(text);
  if (!response.ok) {
    throw new Error(data.error || 'Request failed');
  }
  return data;
}

function formatNumber(value) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '0';
  }
  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function isAtLeast(value, minimum) {
  return value !== '' && Number.isFinite(Number(value)) && Number(value) >= minimum;
}

function isGreaterThanZero(value) {
  return value !== '' && Number.isFinite(Number(value)) && Number(value) > 0;
}

const EXPERIMENT_METRICS = [
  { key: 'makespan', label: 'Makespan', unit: 's' },
  { key: 'executionTime', label: 'Execution time', unit: 's' },
  { key: 'responseTime', label: 'Response time', unit: 's' },
  { key: 'cpuUtilization', label: 'CPU utilization', unit: '%' },
  { key: 'ramUtilization', label: 'RAM utilization', unit: '%' },
  { key: 'loadBalancingEfficiency', label: 'Load-balancing efficiency', unit: '%' }
];

function GroupedMetricChart({ metric, baseline, ramAware }) {
  const plot = { left: 62, right: 516, top: 20, bottom: 230 };
  const plotHeight = plot.bottom - plot.top;
  const values = [baseline, ramAware].filter((value) => value !== null);
  const maxValue = Math.max(0, ...values);
  const scaleMax = maxValue > 0 ? maxValue * 1.1 : 0;
  const ticks = [0, 0.25, 0.5, 0.75, 1];
  const groups = [
    { label: 'Baseline EDLB', value: baseline, color: '#667085', center: 190 },
    { label: 'RAM-Aware EDLB', value: ramAware, color: '#2563EB', center: 390 }
  ];
  const barWidth = 62;

  return (
    <section className="panel chart-block">
      <div className="section-heading">
        <div>
          <span className="eyebrow">CloudSim experiment metric</span>
          <h3>{metric.label}</h3>
        </div>
        <span className="section-note">Unit: {metric.unit}</span>
      </div>
      <div className="chart-scroll">
        <svg
          className="comparison-chart"
          viewBox="0 0 560 300"
          role="img"
          aria-label={`${metric.label} comparison: Baseline EDLB and RAM-Aware EDLB`}
        >
          {ticks.map((tick) => {
            const y = plot.bottom - tick * plotHeight;
            const tickValue = scaleMax * tick;
            return (
              <g key={tick}>
                <line className="chart-gridline" x1={plot.left} x2={plot.right} y1={y} y2={y} />
                <text className="chart-axis-text chart-tick-text" x={plot.left - 10} y={y + 4} textAnchor="end">
                  {formatNumber(tickValue)}
                </text>
              </g>
            );
          })}
          <line className="chart-axis" x1={plot.left} x2={plot.left} y1={plot.top} y2={plot.bottom} />
          <line className="chart-axis" x1={plot.left} x2={plot.right} y1={plot.bottom} y2={plot.bottom} />
          <text className="chart-axis-text" transform="translate(17 126) rotate(-90)" textAnchor="middle">
            {metric.label} ({metric.unit})
          </text>
          {groups.map((group) => {
            const barHeight = scaleMax > 0 && group.value !== null
              ? (group.value / scaleMax) * plotHeight
              : 0;
            const x = group.center - barWidth / 2;
            const y = plot.bottom - barHeight;
            return (
              <g key={group.label}>
                {group.value !== null ? (
                  <>
                    <rect
                      className="chart-bar"
                      x={x}
                      y={y}
                      width={barWidth}
                      height={barHeight}
                      fill={group.color}
                      rx="5"
                    >
                      <title>{`${group.label}: ${formatNumber(group.value)} ${metric.unit}`}</title>
                    </rect>
                    <text className="chart-value-label" x={group.center} y={Math.max(plot.top + 13, y - 8)} textAnchor="middle">
                      {formatNumber(group.value)}
                    </text>
                  </>
                ) : (
                  <text className="chart-missing-label" x={group.center} y={plot.bottom - 12} textAnchor="middle">
                    Unavailable
                  </text>
                )}
                <text className="chart-category-label" x={group.center} y={plot.bottom + 24} textAnchor="middle">
                  {group.label}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
    </section>
  );
}

function validateResourceConfig(resourceForm) {
  if (!resourceForm.name || !resourceForm.name.trim()) return 'Resource name is required.';
  if (!isAtLeast(resourceForm.hostCount, 1)) return 'Host count must be at least 1.';
  if (!isAtLeast(resourceForm.hostMips, 1000)) return 'Host MIPS must be at least 1000.';
  if (!isAtLeast(resourceForm.hostPes, 1)) return 'Host PEs must be at least 1.';
  if (!isAtLeast(resourceForm.hostRamMb, 1024)) return 'Host RAM must be at least 1024 MB.';
  if (!isAtLeast(resourceForm.hostBandwidthMbps, 1000)) return 'Host bandwidth must be at least 1000 Mbps.';
  if (!isAtLeast(resourceForm.hostStorageGb, 100)) return 'Host storage must be at least 100 GB.';
  if (!isGreaterThanZero(resourceForm.schedulingInterval)) return 'Scheduling interval must be greater than 0.';
  return null;
}

function validateVmConfig(vmForm) {
  if (!vmForm.id || !vmForm.id.trim()) return 'VM ID is required.';
  if (!isAtLeast(vmForm.mips, 500)) return 'VM MIPS must be at least 500.';
  if (!isAtLeast(vmForm.pes, 1)) return 'VM PEs must be at least 1.';
  if (!isAtLeast(vmForm.ramMb, 512)) return 'VM RAM must be at least 512 MB.';
  if (!isAtLeast(vmForm.bandwidthMbps, 100)) return 'VM bandwidth must be at least 100 Mbps.';
  if (!isAtLeast(vmForm.storageGb, 10)) return 'VM storage must be at least 10 GB.';
  return null;
}

function validateCloudletConfig(cloudletForm) {
  if (!cloudletForm.id || !cloudletForm.id.trim()) return 'Cloudlet ID is required.';
  if (!isAtLeast(cloudletForm.length, 1000)) return 'Cloudlet length must be at least 1000.';
  if (!isAtLeast(cloudletForm.pes, 1)) return 'Cloudlet PEs must be at least 1.';
  if (!isAtLeast(cloudletForm.fileSize, 100)) return 'Cloudlet file size must be at least 100.';
  if (!isAtLeast(cloudletForm.outputSize, 100)) return 'Cloudlet output size must be at least 100.';
  if (!isAtLeast(cloudletForm.ramRequirementMb, 256)) return 'Cloudlet RAM requirement must be at least 256 MB.';
  return null;
}

function App() {
  const [activeTab, setActiveTab] = useState('Dashboard');
  const [dashboard, setDashboard] = useState(null);
  const [vms, setVms] = useState([]);
  const [cloudlets, setCloudlets] = useState([]);
  const [resources, setResources] = useState([]);
  const [decisions, setDecisions] = useState([]);
  const [experiments, setExperiments] = useState([]);
  const [resourceForm, setResourceForm] = useState(emptyResourceForm);
  const [vmForm, setVmForm] = useState(emptyVmForm);
  const [cloudletForm, setCloudletForm] = useState(emptyCloudletForm);
  const [experimentForm, setExperimentForm] = useState(emptyExperimentForm);
  const [status, setStatus] = useState('Waiting for user input');
  const [resetConfirmation, setResetConfirmation] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const refreshAll = async () => {
    try {
      const [vmData, cloudletData, resourceData, decisionData, experimentData, dashboardData] = await Promise.all([
        apiRequest('/api/vms'),
        apiRequest('/api/cloudlets'),
        apiRequest('/api/resources'),
        apiRequest('/api/scheduling'),
        apiRequest('/api/experiments'),
        apiRequest('/api/dashboard')
      ]);

      setVms(vmData || []);
      setCloudlets(cloudletData || []);
      setResources(resourceData || []);
      setDecisions(decisionData || []);
      setExperiments(experimentData || []);
      setDashboard(dashboardData || {
        activeVms: 0,
        pendingTasks: 0,
        completedTasks: 0,
        resourceStatus: 'No runtime data available',
        latestExperiment: 'No experiments have been configured',
        alerts: ['No runtime data available'],
        recentSchedulingDecisions: []
      });
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  useEffect(() => {
    refreshAll();
  }, []);

  const experimentSummary = experiments.length
    ? experiments[experiments.length - 1]
    : null;
  const latestBaselineExperiment = [...experiments].reverse().find((experiment) => experiment.algorithm === 'baseline');
  const latestRamAwareExperiment = [...experiments].reverse().find((experiment) => experiment.algorithm === 'ram-aware');
  const maxConfiguredVmRam = Math.max(0, ...vms.map((vm) => Number(vm.ramMb) || 0));

  const addResource = async (event) => {
    event.preventDefault();
    const validationError = validateResourceConfig(resourceForm);
    if (validationError) {
      setStatus(validationError);
      return;
    }
    setIsLoading(true);
    try {
      await apiRequest('/api/resources', {
        method: 'POST',
        body: JSON.stringify({
          ...resourceForm,
          hostCount: Number(resourceForm.hostCount),
          hostMips: Number(resourceForm.hostMips),
          hostPes: Number(resourceForm.hostPes),
          hostRamMb: Number(resourceForm.hostRamMb),
          hostBandwidthMbps: Number(resourceForm.hostBandwidthMbps),
          hostStorageGb: Number(resourceForm.hostStorageGb),
          schedulingInterval: Number(resourceForm.schedulingInterval)
        })
      });
      setStatus('Host configuration saved');
      setResourceForm(emptyResourceForm);
      await refreshAll();
    } catch (error) {
      setStatus(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const addVm = async (event) => {
    event.preventDefault();
    const validationError = validateVmConfig(vmForm);
    if (validationError) {
      setStatus(validationError);
      return;
    }
    setIsLoading(true);
    try {
      await apiRequest('/api/vms', {
        method: 'POST',
        body: JSON.stringify({
          ...vmForm,
          mips: Number(vmForm.mips),
          pes: Number(vmForm.pes),
          ramMb: Number(vmForm.ramMb),
          bandwidthMbps: Number(vmForm.bandwidthMbps),
          storageGb: Number(vmForm.storageGb)
        })
      });
      setStatus('Virtual machine created');
      setVmForm(emptyVmForm);
      await refreshAll();
    } catch (error) {
      setStatus(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const addCloudlet = async (event) => {
    event.preventDefault();
    const validationError = validateCloudletConfig(cloudletForm);
    if (validationError) {
      setStatus(validationError);
      return;
    }
    setIsLoading(true);
    try {
      await apiRequest('/api/cloudlets', {
        method: 'POST',
        body: JSON.stringify({
          ...cloudletForm,
          length: Number(cloudletForm.length),
          pes: Number(cloudletForm.pes),
          fileSize: Number(cloudletForm.fileSize),
          outputSize: Number(cloudletForm.outputSize),
          ramRequirementMb: Number(cloudletForm.ramRequirementMb)
        })
      });
      setStatus('Cloudlet added');
      setCloudletForm(emptyCloudletForm);
      await refreshAll();
    } catch (error) {
      setStatus(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const runExperiment = async () => {
    if (!resources.length || !vms.length || !cloudlets.length) {
      setStatus('Add at least one host resource, one VM, and one cloudlet before running an experiment.');
      return;
    }
    if (!Number.isFinite(experimentForm.repetitions) || experimentForm.repetitions < 1) {
      setStatus('Experiment repetitions must be at least 1.');
      return;
    }
    setIsLoading(true);
    try {
      const result = await apiRequest('/api/experiments', {
        method: 'POST',
        body: JSON.stringify({
          algorithm: experimentForm.algorithm,
          repetitions: Number(experimentForm.repetitions) || 1
        })
      });
      setStatus(`Experiment completed using ${result.algorithm}`);
      await refreshAll();
      setActiveTab('Research Analytics');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const resetConfiguration = async () => {
    setIsLoading(true);
    setResetConfirmation('');
    try {
      await apiRequest('/api/reset', { method: 'POST' });
      setVms([]);
      setCloudlets([]);
      setResources([]);
      setDecisions([]);
      setExperiments([]);
      setDashboard({
        activeVms: 0,
        pendingTasks: 0,
        completedTasks: 0,
        resourceStatus: 'No host configuration available',
        latestExperiment: 'No experiments have been configured',
        alerts: ['No runtime data available'],
        recentSchedulingDecisions: []
      });
      setResourceForm(emptyResourceForm);
      setVmForm(emptyVmForm);
      setCloudletForm(emptyCloudletForm);
      setExperimentForm(emptyExperimentForm);
      setActiveTab('Dashboard');
      if (!await refreshAll()) {
        throw new Error('Reset succeeded, but the cleared backend data could not be reloaded.');
      }
      setStatus('All project data has been cleared.');
      setResetConfirmation('All project data has been cleared.');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const renderDashboard = () => (
    <div className="page-block">
      <div className="header-row">
        <h2>Dashboard Overview</h2>
        <div className="dashboard-actions">
          <button
            className="dashboard-reset-button"
            type="button"
            onClick={resetConfiguration}
            disabled={isLoading}
            aria-label="Reset / Clear All"
          >
            Reset / Clear All
          </button>
          <button className="primary-button" onClick={runExperiment} disabled={isLoading || vms.length === 0 || cloudlets.length === 0 || resources.length === 0}>
            Run Experiment
          </button>
        </div>
      </div>
      {resetConfirmation && (
        <p className="reset-confirmation" role="status">
          {resetConfirmation}
        </p>
      )}

      <div className="stat-grid">
        <div className="stat-card">
          <span className="label">Virtual Machines</span>
          <strong>{vms.length}</strong>
        </div>
        <div className="stat-card">
          <span className="label">Tasks</span>
          <strong>{cloudlets.length}</strong>
        </div>
        <div className="stat-card">
          <span className="label">Resources</span>
          <strong>{resources.length}</strong>
        </div>
        <div className="stat-card">
          <span className="label">Experiments</span>
          <strong>{experiments.length}</strong>
        </div>
      </div>

      {dashboard && (vms.length || cloudlets.length || resources.length || experiments.length) ? (
        <>
          <div className="two-column-grid">
            <div className="panel">
              <h3>Latest Experiment Status</h3>
              <p>{dashboard.latestExperiment ?? 'No experiments have been configured'}</p>
            </div>
            <div className="panel">
              <h3>Recent Scheduling Decisions</h3>
              {(dashboard.recentSchedulingDecisions || decisions).length ? (
                <ul className="list muted-list">
                  {(dashboard.recentSchedulingDecisions || decisions).slice(0, 5).map((decision, index) => (
                    <li key={`${decision.cloudletId ?? index}-${decision.selectedVmId ?? 'vm'}`}>
                      {decision.cloudletId} → {decision.selectedVmId} ({decision.decision})
                    </li>
                  ))}
                </ul>
              ) : (
                <p>No runtime data available</p>
              )}
            </div>
          </div>
          {vms.length > 0 && (
            <section className="panel resource-overview">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">Resource profile</span>
                  <h3>Configured VM RAM capacity</h3>
                </div>
                <span className="section-note">Configured capacity · not live utilization</span>
              </div>
              <div className="capacity-list">
                {vms.map((vm) => {
                  const ramMb = Number(vm.ramMb) || 0;
                  const capacityPercent = maxConfiguredVmRam > 0 ? (ramMb / maxConfiguredVmRam) * 100 : 0;
                  return (
                    <div className="capacity-row" key={vm.id}>
                      <span className="capacity-name">{vm.id}</span>
                      <div
                        className="capacity-track"
                        role="img"
                        aria-label={`${vm.id}: ${formatNumber(ramMb)} MB configured RAM`}
                      >
                        <div className="capacity-fill" style={{ width: `${capacityPercent}%` }} />
                      </div>
                      <span className="capacity-value">{formatNumber(ramMb)} MB</span>
                    </div>
                  );
                })}
              </div>
            </section>
          )}
        </>
      ) : (
        <div className="empty-state">No runtime data available</div>
      )}
    </div>
  );

  const renderVmPage = () => (
    <div className="page-block">
      <h2>Virtual Machines</h2>
      <form className="form-grid" onSubmit={addVm}>
        <div className="form-field">
          <label htmlFor="vm-id">VM ID</label>
          <input id="vm-id" type="text" placeholder="Enter VM ID" value={vmForm.id} onChange={(e) => setVmForm({ ...vmForm, id: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="vm-mips">MIPS</label>
          <input id="vm-mips" type="number" min="500" placeholder="Enter MIPS" value={vmForm.mips} onChange={(e) => setVmForm({ ...vmForm, mips: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="vm-pes">PEs</label>
          <input id="vm-pes" type="number" min="1" placeholder="Enter number of PEs" value={vmForm.pes} onChange={(e) => setVmForm({ ...vmForm, pes: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="vm-ram">RAM (MB)</label>
          <input id="vm-ram" type="number" min="512" placeholder="Enter RAM in MB" value={vmForm.ramMb} onChange={(e) => setVmForm({ ...vmForm, ramMb: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="vm-bandwidth">Bandwidth (Mbps)</label>
          <input id="vm-bandwidth" type="number" min="100" placeholder="Enter bandwidth" value={vmForm.bandwidthMbps} onChange={(e) => setVmForm({ ...vmForm, bandwidthMbps: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="vm-storage">Storage (GB)</label>
          <input id="vm-storage" type="number" min="10" placeholder="Enter storage in GB" value={vmForm.storageGb} onChange={(e) => setVmForm({ ...vmForm, storageGb: e.target.value })} />
        </div>
        <button type="submit" className="primary-button" disabled={isLoading}>Create VM</button>
      </form>

      {vms.length ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>VM ID</th>
                <th>MIPS</th>
                <th>PEs</th>
                <th>RAM</th>
                <th>Bandwidth</th>
                <th>Storage</th>
                <th>Status</th>
                <th>Current Cloudlet</th>
                <th>Utilization</th>
              </tr>
            </thead>
            <tbody>
              {vms.map((vm) => (
                <tr key={vm.id}>
                  <td>{vm.id}</td>
                  <td>{formatNumber(vm.mips)}</td>
                  <td>{vm.pes}</td>
                  <td>{formatNumber(vm.ramMb)} MB</td>
                  <td>{formatNumber(vm.bandwidthMbps)} Mbps</td>
                  <td>{formatNumber(vm.storageGb)} GB</td>
                  <td>{vm.status || 'Configured'}</td>
                  <td>{vm.currentCloudlet || 'None'}</td>
                  <td>{typeof vm.utilization === 'number' && Number.isFinite(vm.utilization) ? `${formatNumber(vm.utilization)}%` : 'Unavailable'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">No virtual machines configured.</div>
      )}
    </div>
  );

  const renderCloudletPage = () => (
    <div className="page-block">
      <h2>Cloudlets / Tasks</h2>
      <form className="form-grid" onSubmit={addCloudlet}>
        <div className="form-field">
          <label htmlFor="cloudlet-id">Cloudlet ID</label>
          <input id="cloudlet-id" type="text" placeholder="Enter Cloudlet ID" value={cloudletForm.id} onChange={(e) => setCloudletForm({ ...cloudletForm, id: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="cloudlet-length">Length</label>
          <input id="cloudlet-length" type="number" min="1000" placeholder="Enter cloudlet length" value={cloudletForm.length} onChange={(e) => setCloudletForm({ ...cloudletForm, length: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="cloudlet-pes">PEs</label>
          <input id="cloudlet-pes" type="number" min="1" placeholder="Enter number of PEs" value={cloudletForm.pes} onChange={(e) => setCloudletForm({ ...cloudletForm, pes: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="cloudlet-file-size">File Size</label>
          <input id="cloudlet-file-size" type="number" min="100" placeholder="Enter file size" value={cloudletForm.fileSize} onChange={(e) => setCloudletForm({ ...cloudletForm, fileSize: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="cloudlet-output-size">Output Size</label>
          <input id="cloudlet-output-size" type="number" min="100" placeholder="Enter output size" value={cloudletForm.outputSize} onChange={(e) => setCloudletForm({ ...cloudletForm, outputSize: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="cloudlet-ram">Required RAM (MB)</label>
          <input id="cloudlet-ram" type="number" min="256" placeholder="Enter required RAM" value={cloudletForm.ramRequirementMb} onChange={(e) => setCloudletForm({ ...cloudletForm, ramRequirementMb: e.target.value })} />
        </div>
        <button type="submit" className="primary-button" disabled={isLoading}>Create Cloudlet</button>
      </form>

      {cloudlets.length ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Cloudlet ID</th>
                <th>Length</th>
                <th>PEs</th>
                <th>RAM Req.</th>
                <th>File Size</th>
                <th>Output Size</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {cloudlets.map((cloudlet) => (
                <tr key={cloudlet.id}>
                  <td>{cloudlet.id}</td>
                  <td>{formatNumber(cloudlet.length)}</td>
                  <td>{cloudlet.pes}</td>
                  <td>{formatNumber(cloudlet.ramRequirementMb)} MB</td>
                  <td>{formatNumber(cloudlet.fileSize)}</td>
                  <td>{formatNumber(cloudlet.outputSize)}</td>
                  <td>{cloudlet.status || 'Pending'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">No cloudlets available.</div>
      )}
    </div>
  );

  const renderResourcesPage = () => (
    <div className="page-block">
      <h2>Resources</h2>
      <form className="form-grid" onSubmit={addResource}>
        <div className="form-field">
          <label htmlFor="resource-name">Configuration Name</label>
          <input id="resource-name" type="text" placeholder="Enter configuration name" value={resourceForm.name} onChange={(e) => setResourceForm({ ...resourceForm, name: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="resource-host-count">Number of Hosts</label>
          <input id="resource-host-count" type="number" min="1" placeholder="Enter number of hosts" value={resourceForm.hostCount} onChange={(e) => setResourceForm({ ...resourceForm, hostCount: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="resource-host-mips">Host MIPS</label>
          <input id="resource-host-mips" type="number" min="1000" placeholder="Enter host MIPS" value={resourceForm.hostMips} onChange={(e) => setResourceForm({ ...resourceForm, hostMips: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="resource-host-pes">Host PEs</label>
          <input id="resource-host-pes" type="number" min="1" placeholder="Enter host PEs" value={resourceForm.hostPes} onChange={(e) => setResourceForm({ ...resourceForm, hostPes: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="resource-host-ram">Host RAM (MB)</label>
          <input id="resource-host-ram" type="number" min="1024" placeholder="Enter RAM in MB" value={resourceForm.hostRamMb} onChange={(e) => setResourceForm({ ...resourceForm, hostRamMb: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="resource-host-storage">Host Storage (GB)</label>
          <input id="resource-host-storage" type="number" min="100" placeholder="Enter storage in GB" value={resourceForm.hostStorageGb} onChange={(e) => setResourceForm({ ...resourceForm, hostStorageGb: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="resource-host-bandwidth">Host Bandwidth (Mbps)</label>
          <input id="resource-host-bandwidth" type="number" min="1000" placeholder="Enter bandwidth" value={resourceForm.hostBandwidthMbps} onChange={(e) => setResourceForm({ ...resourceForm, hostBandwidthMbps: e.target.value })} />
        </div>
        <div className="form-field">
          <label htmlFor="resource-scheduling-interval">Scheduling Interval</label>
          <input id="resource-scheduling-interval" type="number" min="0.1" step="0.1" placeholder="Enter interval" value={resourceForm.schedulingInterval} onChange={(e) => setResourceForm({ ...resourceForm, schedulingInterval: e.target.value })} />
        </div>
        <button type="submit" className="primary-button" disabled={isLoading}>Save Host Configuration</button>
      </form>

      {resources.length ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Hosts</th>
                <th>MIPS</th>
                <th>PEs</th>
                <th>RAM</th>
                <th>Bandwidth</th>
                <th>Storage</th>
              </tr>
            </thead>
            <tbody>
              {resources.map((resource) => (
                <tr key={resource.id || resource.name}>
                  <td>{resource.name}</td>
                  <td>{resource.hostCount}</td>
                  <td>{resource.hostMips}</td>
                  <td>{resource.hostPes}</td>
                  <td>{resource.hostRamMb} MB</td>
                  <td>{resource.hostBandwidthMbps} Mbps</td>
                  <td>{resource.hostStorageGb} GB</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">No host configuration available.</div>
      )}
    </div>
  );

  const renderSchedulingPage = () => (
    <div className="page-block">
      <h2>Scheduling</h2>
      {decisions.length ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Cloudlet</th>
                <th>Run #</th>
                <th>Selected VM</th>
                <th>Required RAM</th>
                <th>Available RAM</th>
                <th>Completion Time</th>
                <th>Decision</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              {decisions.map((decision, index) => (
                <tr key={`${decision.cloudletId}-${index}`}>
                  <td>{decision.cloudletId}</td>
                  <td>{decision.runNumber ?? 'Unavailable'}</td>
                  <td>{decision.selectedVmId}</td>
                  <td>{decision.requiredRamMb} MB</td>
                  <td>{decision.availableRamMb} MB</td>
                  <td>{formatNumber(decision.estimatedCompletionTime)} s</td>
                  <td className={decision.decision === 'REJECTED' ? 'status-red' : 'status-green'}>{decision.decision}</td>
                  <td>{decision.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">No scheduling decisions recorded.</div>
      )}
    </div>
  );

  const renderExperimentPage = () => (
    <div className="page-block">
      <h2>Experiments</h2>
      <div className="panel experiment-box">
        <label>
          Algorithm
          <select value={experimentForm.algorithm} onChange={(e) => setExperimentForm({ ...experimentForm, algorithm: e.target.value })}>
            <option value="baseline">Baseline</option>
            <option value="ram-aware">RAM-aware</option>
          </select>
        </label>
        <label>
          Runs
          <input type="number" min="1" value={experimentForm.repetitions} onChange={(e) => setExperimentForm({ ...experimentForm, repetitions: Number(e.target.value) })} />
        </label>
        <button className="primary-button" onClick={runExperiment} disabled={isLoading || vms.length === 0 || cloudlets.length === 0 || resources.length === 0}>
          Run Experiment
        </button>
      </div>

      {experiments.length ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Experiment ID</th>
                <th>Algorithm</th>
                <th>Timestamp</th>
                <th>Runs</th>
                <th>Completed</th>
                <th>Rejected</th>
                <th>Failed</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {experiments.map((experiment) => (
                <tr key={experiment.id}>
                  <td>{experiment.id.slice(0, 8)}</td>
                  <td>{experiment.algorithm}</td>
                  <td>{new Date(experiment.timestamp).toLocaleString()}</td>
                  <td>{experiment.runs}</td>
                  <td>{experiment.completedCloudlets ?? 'Unavailable'}</td>
                  <td>{experiment.rejectedCloudlets ?? 'Unavailable'}</td>
                  <td>{experiment.failedCloudlets ?? 'Unavailable'}</td>
                  <td>{experiment.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">No experiments have been configured.</div>
      )}
    </div>
  );

  const renderAnalyticsPage = () => (
    <div className="page-block">
      <h2>Research Analytics</h2>
      {experiments.length ? (
        <>
          <section className="panel">
            <div className="section-heading">
              <div>
                <span className="eyebrow">CloudSim results</span>
                <h3>Cloudlet outcomes by experiment</h3>
              </div>
              <span className="section-note">Counts returned by the backend</span>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Algorithm</th>
                    <th>Runs</th>
                    <th>Completed</th>
                    <th>Rejected</th>
                    <th>Failed</th>
                  </tr>
                </thead>
                <tbody>
                  {experiments.map((experiment) => (
                    <tr key={`${experiment.id}-outcomes`}>
                      <td>{experiment.algorithm}</td>
                      <td>{experiment.runs ?? 'Unavailable'}</td>
                      <td>{experiment.completedCloudlets ?? 'Unavailable'}</td>
                      <td>{experiment.rejectedCloudlets ?? 'Unavailable'}</td>
                      <td>{experiment.failedCloudlets ?? 'Unavailable'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <div className="chart-legend" aria-label="Algorithm legend">
            <span><i className="legend-swatch baseline-swatch" />Baseline EDLB</span>
            <span><i className="legend-swatch ram-aware-swatch" />RAM-Aware EDLB</span>
          </div>
          <div className="analytics-grid">
            {EXPERIMENT_METRICS.map((metric) => {
              const rawBaseline = latestBaselineExperiment?.metrics?.[metric.key];
              const rawRamAware = latestRamAwareExperiment?.metrics?.[metric.key];
              const baseline = rawBaseline !== null && rawBaseline !== undefined && Number.isFinite(Number(rawBaseline))
                ? Number(rawBaseline)
                : null;
              const ramAware = rawRamAware !== null && rawRamAware !== undefined && Number.isFinite(Number(rawRamAware))
                ? Number(rawRamAware)
                : null;
              if (baseline === null && ramAware === null) return null;
              return (
                <GroupedMetricChart
                  key={metric.key}
                  metric={metric}
                  baseline={baseline}
                  ramAware={ramAware}
                />
              );
            })}
          </div>
          {!EXPERIMENT_METRICS.some((metric) => {
            const baseline = latestBaselineExperiment?.metrics?.[metric.key];
            const ramAware = latestRamAwareExperiment?.metrics?.[metric.key];
            return [baseline, ramAware].some((value) => value !== null && value !== undefined && Number.isFinite(Number(value)));
          }) && (
            <div className="empty-state">
              <strong>No comparable experiment metrics available</strong>
              <span>Run Baseline EDLB and RAM-Aware EDLB experiments to view the comparison.</span>
            </div>
          )}
        </>
      ) : (
        <div className="empty-state">
          <strong>No data available</strong>
          <span>Run an experiment to generate CloudSim results.</span>
        </div>
      )}
    </div>
  );

  const renderMonitoringPage = () => (
    <div className="page-block">
      <h2>Monitoring</h2>
      <div className="two-column-grid">
        <div className="panel">
          <h3>System State</h3>
          <p>{resources.length ? 'Configured host resources are available' : 'No host configuration available'}</p>
          <p>{vms.length ? `${vms.length} VM(s) are configured` : 'No virtual machines configured.'}</p>
          <p>{cloudlets.length ? `${cloudlets.length} cloudlet(s) are available` : 'No cloudlets available.'}</p>
        </div>
        <div className="panel">
          <h3>Alerts</h3>
          <ul className="list muted-list">
            {(dashboard?.alerts && dashboard.alerts.length) ? dashboard.alerts.map((alert) => <li key={alert}>{alert}</li>) : <li>No runtime data available</li>}
          </ul>
        </div>
      </div>
    </div>
  );

  const renderActiveTab = () => {
    switch (activeTab) {
      case 'Virtual Machines':
        return renderVmPage();
      case 'Cloudlets / Tasks':
        return renderCloudletPage();
      case 'Resources':
        return renderResourcesPage();
      case 'Scheduling':
        return renderSchedulingPage();
      case 'Experiments':
        return renderExperimentPage();
      case 'Research Analytics':
        return renderAnalyticsPage();
      case 'Monitoring':
        return renderMonitoringPage();
      default:
        return renderDashboard();
    }
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-box">
          <h1>CloudSim Control</h1>
        </div>
        <nav>
          {NAV_ITEMS.map((item) => (
            <button
              key={item}
              className={activeTab === item ? 'nav-item active' : 'nav-item'}
              onClick={() => setActiveTab(item)}
            >
              {item}
            </button>
          ))}
        </nav>
      </aside>

      <main className="content">
        <header className="topbar">
          <div>
            <small>RAM-Aware Dynamic Load Balancing</small>
            <h2>{activeTab}</h2>
          </div>
          <div className="status-badge">{status}</div>
        </header>

        {renderActiveTab()}

        {experimentSummary && (
          <div className="footer-panel panel">
            <h3>Latest Experiment Summary</h3>
            <div className="stat-grid compact-grid">
              <div className="stat-card">
                <span className="label">Makespan</span>
                <strong>{Number.isFinite(experimentSummary.metrics?.makespan) ? `${formatNumber(experimentSummary.metrics.makespan)} s` : 'Unavailable'}</strong>
              </div>
              <div className="stat-card">
                <span className="label">CPU Utilization</span>
                <strong>{Number.isFinite(experimentSummary.metrics?.cpuUtilization) ? `${formatNumber(experimentSummary.metrics.cpuUtilization)}%` : 'Unavailable'}</strong>
              </div>
              <div className="stat-card">
                <span className="label">RAM Utilization</span>
                <strong>{Number.isFinite(experimentSummary.metrics?.ramUtilization) ? `${formatNumber(experimentSummary.metrics.ramUtilization)}%` : 'Unavailable'}</strong>
              </div>
              <div className="stat-card">
                <span className="label">Response Time</span>
                <strong>{Number.isFinite(experimentSummary.metrics?.responseTime) ? `${formatNumber(experimentSummary.metrics.responseTime)} s` : 'Unavailable'}</strong>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
