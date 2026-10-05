import type {
  CreateTrafficRecordRequest,
  MapFeatureCollection,
  PagedResponse,
  SimulationRequest,
  StreetSearchResponse,
  TrafficInsightResponse,
  TrafficRecord,
  TrafficRecordSummary,
  TrafficStatsResponse
} from "../types/api";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });

  if (!response.ok) {
    const text = await response.text();
    const message = parseApiErrorMessage(text) || `Request failed with status ${response.status}`;
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return (await response.json()) as T;
  }

  return (await response.text()) as T;
}

export function getTrafficRecords(params?: { page?: number; size?: number; query?: string }) {
  const searchParams = new URLSearchParams();
  searchParams.set("page", String(params?.page ?? 0));
  searchParams.set("size", String(params?.size ?? 20));
  const query = params?.query?.trim();
  if (query) {
    searchParams.set("query", query);
  }

  return request<PagedResponse<TrafficRecord>>(`/traffic-records?${searchParams.toString()}`);
}

export function createTrafficRecord(payload: CreateTrafficRecordRequest) {
  return request<TrafficRecord>("/traffic-records", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function getTrafficRecordSummary(recordIds?: string[]) {
  if (recordIds && recordIds.length > 0) {
    return request<TrafficRecordSummary>("/traffic-records/summary/filter", {
      method: "POST",
      body: JSON.stringify({ recordIds })
    });
  }

  return request<TrafficRecordSummary>("/traffic-records/summary");
}

function buildRecordIdsQuery(recordIds?: string[]) {
  if (!recordIds || recordIds.length === 0) {
    return "";
  }

  return `&recordIds=${encodeURIComponent(recordIds.join(","))}`;
}

export function getTrafficStats(groupBy: string, recordIds?: string[]) {
  if (recordIds && recordIds.length > 0) {
    return request<TrafficStatsResponse>("/traffic-stats/filter", {
      method: "POST",
      body: JSON.stringify({ groupBy, recordIds })
    });
  }

  return request<TrafficStatsResponse>(
    `/traffic-stats?groupBy=${encodeURIComponent(groupBy)}${buildRecordIdsQuery(recordIds)}`
  );
}

export function getTrafficInsights(recordIds?: string[]) {
  if (recordIds && recordIds.length > 0) {
    return request<TrafficInsightResponse>("/traffic-insights/filter", {
      method: "POST",
      body: JSON.stringify({ recordIds })
    });
  }

  const query = !recordIds || recordIds.length === 0 ? "" : `?recordIds=${encodeURIComponent(recordIds.join(","))}`;
  return request<TrafficInsightResponse>(`/traffic-insights${query}`);
}

export function generateSimulation(payload: SimulationRequest) {
  return request<TrafficRecord[]>("/simulations/generate", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function getTrafficMap(recordIds: string[]) {
  if (recordIds.length === 0) {
    return Promise.resolve<MapFeatureCollection>({ type: "FeatureCollection", features: [] });
  }

  return request<MapFeatureCollection>("/traffic-map/filter", {
    method: "POST",
    body: JSON.stringify({ recordIds })
  });
}

export function searchStreets(query: string, limit = 20, offset = 0) {
  const params = new URLSearchParams({
    q: query,
    limit: String(limit),
    offset: String(offset)
  });
  return request<StreetSearchResponse>(`/streets/search?${params.toString()}`);
}

export async function getExport(format: "csv" | "json"): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}/exports?format=${format}`);
  if (!response.ok) {
    const text = await response.text();
    const message = parseApiErrorMessage(text) || `Export failed with status ${response.status}`;
    throw new ApiError(message, response.status);
  }
  return response.blob();
}

function parseApiErrorMessage(text: string) {
  if (!text) {
    return "";
  }

  try {
    const parsed = JSON.parse(text) as { message?: string };
    return parsed.message ?? text;
  } catch {
    return text;
  }
}

export { API_BASE_URL };
