import { api } from './client';
import type {
  Metric,
  MetricScope,
  Page,
  Project,
  Run,
  RunListItem,
  RunParametersRequest,
  SamplesPayload,
  Scenario,
  ScenarioDefinition,
  ScenarioSummary,
  User,
  ValidationResponse,
} from './types';

export const authApi = {
  me: () => api.get<User>('/auth/me'),
  register: (email: string, password: string) => api.post<User>('/auth/register', { email, password }),
  login: (email: string, password: string) => api.post<User>('/auth/login', { email, password }),
  logout: () => api.post<void>('/auth/logout'),
};

export const projectsApi = {
  list: (page = 0) => api.get<Page<Project>>(`/projects?page=${page}&size=50`),
  get: (id: string) => api.get<Project>(`/projects/${id}`),
  create: (name: string, description?: string) => api.post<Project>('/projects', { name, description }),
  remove: (id: string) => api.delete(`/projects/${id}`),
};

export const scenariosApi = {
  list: (projectId: string) => api.get<Page<ScenarioSummary>>(`/projects/${projectId}/scenarios?size=100`),
  get: (id: string) => api.get<Scenario>(`/scenarios/${id}`),
  create: (projectId: string, name: string, definition?: ScenarioDefinition) =>
    api.post<Scenario>(`/projects/${projectId}/scenarios`, { name, definition }),
  save: (id: string, name: string, definition: ScenarioDefinition) =>
    api.put<Scenario>(`/scenarios/${id}`, { name, definition }),
  duplicate: (id: string) => api.post<Scenario>(`/scenarios/${id}/duplicate`),
  remove: (id: string) => api.delete(`/scenarios/${id}`),
  validate: (id: string, params?: RunParametersRequest) =>
    api.post<ValidationResponse>(`/scenarios/${id}/validate`, params),
};

export const runsApi = {
  create: (scenarioId: string, params: RunParametersRequest) => api.post<Run>(`/scenarios/${scenarioId}/runs`, params),
  list: (scenarioId: string) => api.get<Page<RunListItem>>(`/scenarios/${scenarioId}/runs?size=20`),
  get: (id: string) => api.get<Run>(`/runs/${id}`),
  cancel: (id: string) => api.post<Run>(`/runs/${id}/cancel`),
  metrics: (id: string, scope?: MetricScope) =>
    api.get<Metric[]>(`/runs/${id}/metrics${scope ? `?scope=${scope}` : ''}`),
  samples: (id: string) => api.get<SamplesPayload>(`/runs/${id}/samples`),
  csvUrl: (id: string) => `/api/v1/runs/${id}/export.csv`,
};
