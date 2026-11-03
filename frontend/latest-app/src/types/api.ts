export type RoadType = "LOCAL" | "ARTERIAL" | "HIGHWAY" | string;

export interface TrafficRecord {
  id: string;
  timestamp: string;
  roadType: RoadType;
  vehicleVolume: number;
  eventType: string | null;
  weather: string | null;
  streetId: string | null;
  streetOsmWayId: number | null;
  streetName: string | null;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

/** Ids of every record that matches the table filter, most recent first, up to a ceiling. */
export interface RecordIdsResponse {
  recordIds: string[];
  total: number;
  limit: number;
  truncated: boolean;
}

export interface CreateTrafficRecordRequest {
  timestamp: string;
  roadType: string;
  vehicleVolume: number;
  eventType: string;
  weather: string;
  streetOsmWayId: number;
}

export interface TrafficStatsResponse {
  labels: string[];
  values: number[];
}

export interface TrafficInsightResponse {
  insights: string[];
}

export interface TrafficRecordSummary {
  recordCount: number;
  totalVehicleVolume: number;
  uniqueStreetCount: number;
  averageVehicleVolume: number;
  latestTimestamp: string | null;
}

export interface SimulationRequest {
  recordsToGenerate: number;
  scenarioName: string;
}

export interface MapPoint {
  type: "Feature";
  properties: {
    recordId: string;
    streetId: string;
    streetOsmWayId: number;
    streetName: string;
    vehicleVolume: number;
    trafficLevel: "LOW" | "MEDIUM" | "HIGH";
    color: string;
  };
  geometry: {
    type: "LineString";
    coordinates: [number, number][];
  };
}

/** What the map shows: the most recent records up to a ceiling, or the selection (which has no ceiling). */
export interface MapMeta {
  scope: "latest" | "selection";
  limit: number | null;
  count: number;
}

export interface MapFeatureCollection {
  type: "FeatureCollection";
  features: MapPoint[];
  meta?: MapMeta;
}

export interface StreetOption {
  id: string;
  osmWayId: number;
  name: string;
}

export interface StreetSearchResponse {
  items: StreetOption[];
  limit: number;
  offset: number;
  total: number;
}
