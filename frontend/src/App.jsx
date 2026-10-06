import { useEffect, useMemo, useState } from 'react';

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
  hostCount: 1,
  hostMips: 2000,
  hostPes: 4,
  hostRamMb: 16384,
  hostBandwidthMbps: 10000,
  hostStorageGb: 1000,
  schedulingInterval: 1
};

const emptyVmForm = {
  id: '',
  mips: 1500,
  pes: 2,
  ramMb: 4096,
  bandwidthMbps: 1000,
  storageGb: 100
};

const emptyCloudletForm = {
  id: '',
  length: 15000,
  pes: 1,
  fileSize: 300,
  outputSize: 300,
  ramRequirementMb: 2048
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

function validateResourceConfig(resourceForm) {
  if (!resourceForm.name || !resourceForm.name.trim()) return 'Resource name is required.';
  if (!Number.isFinite(resourceForm.hostCount) || resourceForm.hostCount < 1) return 'Host count must be at least 1.';
  if (!Number.isFinite(resourceForm.hostMips) || resourceForm.hostMips < 1000) return 'Host MIPS must be at least 1000.';
  if (!Number.isFinite(resourceForm.hostPes) || resourceForm.hostPes < 1) return 'Host PEs must be at least 1.';
  if (!Number.isFinite(resourceForm.hostRamMb) || resourceForm.hostRamMb < 1024) return 'Host RAM must be at least 1024 MB.';
  if (!Number.isFinite(resourceForm.hostBandwidthMbps) || resourceForm.hostBandwidthMbps < 1000) return 'Host bandwidth must be at least 1000 Mbps.';
  if (!Number.isFinite(resourceForm.hostStorageGb) || resourceForm.hostStorageGb < 100) return 'Host storage must be at least 100 GB.';
  if (!Number.isFinite(resourceForm.schedulingInterval) || resourceForm.schedulingInterval <= 0) return 'Scheduling interval must be greater than 0.';
  return null;
}

function validateVmConfig(vmForm) {
  if (!vmForm.id || !vmForm.id.trim()) return 'VM ID is required.';
  if (!Number.isFinite(vmForm.mips) || vmForm.mips < 500) return 'VM MIPS must be at least 500.';
  if (!Number.isFinite(vmForm.pes) || vmForm.pes < 1) return 'VM PEs must be at least 1.';
  if (!Number.isFinite(vmForm.ramMb) || vmForm.ramMb < 512) return 'VM RAM must be at least 512 MB.';
  if (!Number.isFinite(vmForm.bandwidthMbps) || vmForm.bandwidthMbps < 100) return 'VM bandwidth must be at least 100 Mbps.';
  if (!Number.isFinite(vmForm.storageGb) || vmForm.storageGb < 10) return 'VM storage must be at least 10 GB.';
  return null;
}

function validateCloudletConfig(cloudletForm) {
  if (!cloudletForm.id || !cloudletForm.id.trim()) return 'Cloudlet ID is required.';
  if (!Number.isFinite(cloudletForm.length) || cloudletForm.length < 1000) return 'Cloudlet length must be at least 1000.';
  if (!Number.isFinite(cloudletForm.pes) || cloudletForm.pes < 1) return 'Cloudlet PEs must be at least 1.';
  if (!Number.isFinite(cloudletForm.fileSize) || cloudletForm.fileSize < 100) return 'Cloudlet file size must be at least 100.';
  if (!Number.isFinite(cloudletForm.outputSize) || cloudletForm.outputSize < 100) return 'Cloudlet output size must be at least 100.';
  if (!Number.isFinite(cloudletForm.ramRequirementMb) || cloudletForm.ramRequirementMb < 256) return 'Cloudlet RAM requirement must be at least 256 MB.';
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
    } catch (error) {
      setStatus(error.message);
    }
  };

  useEffect(() => {
    refreshAll();
  }, []);

  const pendingCloudlets = useMemo(
    () => cloudlets.filter((item) => item.status === 'Pending' || item.status === 'Submitted').length,
    [cloudlets]
  );

  const experimentSummary = experiments.length
    ? experiments[experiments.length - 1]
    : null;

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
        body: JSON.stringify(resourceForm)
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
        body: JSON.stringify(vmForm)
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
        body: JSON.stringify(cloudletForm)
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

  const renderDashboard = () => (
    <div className="page-block">
      <div className="header-row">
        <h2>Dashboard Overview</h2>
        <button className="primary-button" onClick={runExperiment} disabled={isLoading || vms.length === 0 || cloudlets.length === 0 || resources.length === 0}>
          Run Experiment
        </button>
      </div>

      {dashboard && (vms.length || cloudlets.length || resources.length || experiments.length) ? (
        <>
          <div className="stat-grid">
            <div className="stat-card">
              <span className="label">Active VMs</span>
              <strong>{dashboard.activeVms ?? vms.length}</strong>
            </div>
            <div className="stat-card">
              <span className="label">Pending Tasks</span>
              <strong>{dashboard.pendingTasks ?? pendingCloudlets}</strong>
            </div>
            <div className="stat-card">
              <span className="label">Completed Tasks</span>
              <strong>{dashboard.completedTasks ?? cloudlets.filter((item) => item.status === 'Completed').length}</strong>
            </div>
            <div className="stat-card">
              <span className="label">Resource Status</span>
              <strong>{dashboard.resourceStatus ?? 'No runtime data available'}</strong>
            </div>
          </div>

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
        <input type="text" placeholder="VM ID" value={vmForm.id} onChange={(e) => setVmForm({ ...vmForm, id: e.target.value })} />
        <input type="number" min="500" placeholder="MIPS" value={vmForm.mips} onChange={(e) => setVmForm({ ...vmForm, mips: Number(e.target.value) })} />
        <input type="number" min="1" placeholder="PEs" value={vmForm.pes} onChange={(e) => setVmForm({ ...vmForm, pes: Number(e.target.value) })} />
        <input type="number" min="512" placeholder="RAM (MB)" value={vmForm.ramMb} onChange={(e) => setVmForm({ ...vmForm, ramMb: Number(e.target.value) })} />
        <input type="number" min="100" placeholder="Bandwidth" value={vmForm.bandwidthMbps} onChange={(e) => setVmForm({ ...vmForm, bandwidthMbps: Number(e.target.value) })} />
        <input type="number" min="10" placeholder="Storage (GB)" value={vmForm.storageGb} onChange={(e) => setVmForm({ ...vmForm, storageGb: Number(e.target.value) })} />
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
                  <td>{vm.utilization ? `${vm.utilization}%` : '0%'}</td>
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
        <input type="text" placeholder="Cloudlet ID" value={cloudletForm.id} onChange={(e) => setCloudletForm({ ...cloudletForm, id: e.target.value })} />
        <input type="number" min="1000" placeholder="Length" value={cloudletForm.length} onChange={(e) => setCloudletForm({ ...cloudletForm, length: Number(e.target.value) })} />
        <input type="number" min="1" placeholder="PEs" value={cloudletForm.pes} onChange={(e) => setCloudletForm({ ...cloudletForm, pes: Number(e.target.value) })} />
        <input type="number" min="100" placeholder="File Size" value={cloudletForm.fileSize} onChange={(e) => setCloudletForm({ ...cloudletForm, fileSize: Number(e.target.value) })} />
        <input type="number" min="100" placeholder="Output Size" value={cloudletForm.outputSize} onChange={(e) => setCloudletForm({ ...cloudletForm, outputSize: Number(e.target.value) })} />
        <input type="number" min="256" placeholder="RAM Requirement (MB)" value={cloudletForm.ramRequirementMb} onChange={(e) => setCloudletForm({ ...cloudletForm, ramRequirementMb: Number(e.target.value) })} />
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
        <input type="text" placeholder="Configuration name" value={resourceForm.name} onChange={(e) => setResourceForm({ ...resourceForm, name: e.target.value })} />
        <input type="number" min="1" placeholder="Host count" value={resourceForm.hostCount} onChange={(e) => setResourceForm({ ...resourceForm, hostCount: Number(e.target.value) })} />
        <input type="number" min="1000" placeholder="Host MIPS" value={resourceForm.hostMips} onChange={(e) => setResourceForm({ ...resourceForm, hostMips: Number(e.target.value) })} />
        <input type="number" min="1" placeholder="Host PEs" value={resourceForm.hostPes} onChange={(e) => setResourceForm({ ...resourceForm, hostPes: Number(e.target.value) })} />
        <input type="number" min="1024" placeholder="Host RAM (MB)" value={resourceForm.hostRamMb} onChange={(e) => setResourceForm({ ...resourceForm, hostRamMb: Number(e.target.value) })} />
        <input type="number" min="1000" placeholder="Bandwidth" value={resourceForm.hostBandwidthMbps} onChange={(e) => setResourceForm({ ...resourceForm, hostBandwidthMbps: Number(e.target.value) })} />
        <input type="number" min="100" placeholder="Storage (GB)" value={resourceForm.hostStorageGb} onChange={(e) => setResourceForm({ ...resourceForm, hostStorageGb: Number(e.target.value) })} />
        <input type="number" min="0.1" step="0.1" placeholder="Scheduling interval" value={resourceForm.schedulingInterval} onChange={(e) => setResourceForm({ ...resourceForm, schedulingInterval: Number(e.target.value) })} />
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
          <div className="stat-grid metrics-grid">
            {experiments.map((experiment) => (
              <div key={experiment.id} className="stat-card metric-card">
                <span className="label">{experiment.algorithm}</span>
                <strong>{formatNumber(experiment.metrics?.makespan ?? 0)} s</strong>
                <small>Makespan</small>
              </div>
            ))}
          </div>
          <div className="chart-block">
            <h3>Experiment Metrics</h3>
            <div className="chart-list">
              {experiments.map((experiment) => (
                <div key={experiment.id} className="chart-row">
                  <span>{experiment.algorithm}</span>
                  <div className="bar-track">
                    <div className="bar-fill" style={{ width: `${Math.min(100, (experiment.metrics?.makespan ?? 0) / 10)}%` }} />
                  </div>
                  <span>{formatNumber(experiment.metrics?.makespan ?? 0)} s</span>
                </div>
              ))}
            </div>
          </div>
        </>
      ) : (
        <div className="empty-state">No experiment data available for visualization.</div>
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
                <strong>{formatNumber(experimentSummary.metrics?.makespan ?? 0)} s</strong>
              </div>
              <div className="stat-card">
                <span className="label">CPU Utilization</span>
                <strong>{formatNumber(experimentSummary.metrics?.cpuUtilization ?? 0)}%</strong>
              </div>
              <div className="stat-card">
                <span className="label">RAM Utilization</span>
                <strong>{formatNumber(experimentSummary.metrics?.ramUtilization ?? 0)}%</strong>
              </div>
              <div className="stat-card">
                <span className="label">Response Time</span>
                <strong>{formatNumber(experimentSummary.metrics?.responseTime ?? 0)} s</strong>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
