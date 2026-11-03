import { http, HttpResponse } from "msw";
import type {
  MapFeatureCollection,
  PagedResponse,
  RecordIdsResponse,
  StreetSearchResponse,
  TrafficInsightResponse,
  TrafficRecord,
  TrafficRecordSummary,
  TrafficStatsResponse
} from "../../types/api";

const BASE = "http://localhost:8080/api";

export const mockRecords: TrafficRecord[] = [
  {
    id: "rec-1",
    timestamp: "2024-06-17T08:00:00Z",
    roadType: "ARTERIAL",
    vehicleVolume: 120,
    eventType: "RUSH_HOUR",
    weather: "SUNNY",
    streetId: "street-1",
    streetOsmWayId: 101,
    streetName: "Avenida Central"
  },
  {
    id: "rec-2",
    timestamp: "2024-06-17T17:30:00Z",
    roadType: "HIGHWAY",
    vehicleVolume: 250,
    eventType: null,
    weather: "RAIN",
    streetId: "street-2",
    streetOsmWayId: 202,
    streetName: "Rodovia Norte"
  }
];

export const mockStats: TrafficStatsResponse = {
  labels: ["08", "17", "12"],
  values: [120, 250, 80]
};

export const mockInsights: TrafficInsightResponse = {
  insights: [
    "Horário de pico: 17h.",
    "Tipo de via com maior volume: Via expressa."
  ]
};

export const mockMapData: MapFeatureCollection = {
  type: "FeatureCollection",
  features: [
    {
      type: "Feature",
      properties: {
        recordId: "rec-1",
        streetId: "street-1",
        streetOsmWayId: 101,
        streetName: "Avenida Central",
        vehicleVolume: 120,
        trafficLevel: "MEDIUM",
        color: "#f25c3a"
      },
      geometry: {
        type: "LineString",
        coordinates: [
          [-46.6333, -23.5505],
          [-46.6321, -23.5512]
        ]
      }
    }
  ]
};

export const mockStreetSearch: StreetSearchResponse = {
  items: [
    { id: "street-1", osmWayId: 101, name: "Avenida Central" },
    { id: "street-2", osmWayId: 202, name: "Rodovia Norte" }
  ],
  limit: 20,
  offset: 0,
  total: 2
};

export const mockSummary: TrafficRecordSummary = {
  recordCount: mockRecords.length,
  totalVehicleVolume: mockRecords.reduce((sum, record) => sum + record.vehicleVolume, 0),
  uniqueStreetCount: new Set(mockRecords.map((record) => record.streetId)).size,
  averageVehicleVolume: Math.round(mockRecords.reduce((sum, record) => sum + record.vehicleVolume, 0) / mockRecords.length),
  latestTimestamp: "2024-06-17T17:30:00Z"
};

function filterRecords(request: Request): TrafficRecord[] {
  const query = new URL(request.url).searchParams.get("query")?.toLowerCase().trim() ?? "";
  return query
    ? mockRecords.filter((record) =>
        [record.roadType, record.streetName, record.weather, record.eventType]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query))
      )
    : mockRecords;
}

function buildPagedRecords(request: Request): PagedResponse<TrafficRecord> {
  const url = new URL(request.url);
  const page = Number(url.searchParams.get("page") ?? "0");
  const size = Number(url.searchParams.get("size") ?? "20");
  const filteredItems = filterRecords(request);
  const start = page * size;
  const items = filteredItems.slice(start, start + size);

  return {
    items,
    page,
    size,
    totalItems: filteredItems.length,
    totalPages: filteredItems.length === 0 ? 0 : Math.ceil(filteredItems.length / size)
  };
}

export const handlers = [
  http.get(`${BASE}/traffic-records`, ({ request }) => HttpResponse.json(buildPagedRecords(request))),
  http.get(`${BASE}/traffic-records/ids`, ({ request }) => {
    const matches = filterRecords(request);
    return HttpResponse.json({
      recordIds: matches.map((record) => record.id),
      total: matches.length,
      limit: 10000,
      truncated: false
    } satisfies RecordIdsResponse);
  }),
  http.get(`${BASE}/traffic-records/summary`, () => HttpResponse.json(mockSummary)),
  http.post(`${BASE}/traffic-records/summary/filter`, async ({ request }) => {
    const body = (await request.json()) as { recordIds?: string[] };
    if (!body.recordIds?.length) {
      return HttpResponse.json(mockSummary);
    }

    const selectedRecords = mockRecords.filter((record) => body.recordIds?.includes(record.id));
    return HttpResponse.json({
      recordCount: selectedRecords.length,
      totalVehicleVolume: selectedRecords.reduce((sum, record) => sum + record.vehicleVolume, 0),
      uniqueStreetCount: new Set(selectedRecords.map((record) => record.streetId)).size,
      averageVehicleVolume:
        selectedRecords.length === 0
          ? 0
          : Math.round(selectedRecords.reduce((sum, record) => sum + record.vehicleVolume, 0) / selectedRecords.length),
      latestTimestamp: selectedRecords[0]?.timestamp ?? null
    } satisfies TrafficRecordSummary);
  }),
  http.post(`${BASE}/traffic-records`, async ({ request }) => {
    const body = (await request.json()) as {
      timestamp: string;
      roadType: TrafficRecord["roadType"];
      vehicleVolume: number;
      eventType: string;
      weather: string;
      streetOsmWayId: number;
    };

    return HttpResponse.json(
      {
        id: "rec-created",
        timestamp: body.timestamp,
        roadType: body.roadType,
        vehicleVolume: body.vehicleVolume,
        eventType: body.eventType || null,
        weather: body.weather || null,
        streetId: "street-created",
        streetOsmWayId: body.streetOsmWayId,
        streetName: "Avenida Criada"
      } satisfies TrafficRecord,
      { status: 201 }
    );
  }),
  http.get(`${BASE}/traffic-stats`, ({ request }) => {
    const groupBy = new URL(request.url).searchParams.get("groupBy");
    if (!["hour", "weekday", "roadType"].includes(groupBy ?? "")) {
      return HttpResponse.json({ message: "groupBy inválido" }, { status: 400 });
    }
    return HttpResponse.json(mockStats);
  }),
  http.post(`${BASE}/traffic-stats/filter`, async ({ request }) => {
    const body = (await request.json()) as { groupBy?: string; recordIds?: string[] };
    if (!body.groupBy || !body.recordIds?.length) {
      return HttpResponse.json({ message: "payload inválido" }, { status: 400 });
    }
    return HttpResponse.json(mockStats);
  }),
  http.get(`${BASE}/traffic-insights`, () => HttpResponse.json(mockInsights)),
  http.post(`${BASE}/traffic-insights/filter`, async ({ request }) => {
    const body = (await request.json()) as { recordIds?: string[] };
    if (!body.recordIds?.length) {
      return HttpResponse.json({ message: "payload inválido" }, { status: 400 });
    }
    return HttpResponse.json(mockInsights);
  }),
  http.get(`${BASE}/traffic-map`, () =>
    HttpResponse.json({
      ...mockMapData,
      meta: { scope: "latest", limit: 250, count: mockMapData.features.length }
    } satisfies MapFeatureCollection)
  ),
  http.post(`${BASE}/traffic-map/filter`, async ({ request }) => {
    const body = (await request.json()) as { recordIds?: string[] };
    if (!body.recordIds?.length) {
      return HttpResponse.json({
        ...mockMapData,
        meta: { scope: "latest", limit: 250, count: mockMapData.features.length }
      } satisfies MapFeatureCollection);
    }
    return HttpResponse.json({
      ...mockMapData,
      meta: { scope: "selection", limit: null, count: mockMapData.features.length }
    } satisfies MapFeatureCollection);
  }),
  http.get(`${BASE}/streets/search`, ({ request }) => {
    const query = new URL(request.url).searchParams.get("q")?.toLowerCase() ?? "";
    const items = mockStreetSearch.items.filter((item) => item.name.toLowerCase().includes(query));
    return HttpResponse.json({ ...mockStreetSearch, items, total: items.length });
  }),
  http.post(`${BASE}/simulations/generate`, async ({ request }) => {
    const body = (await request.json()) as { recordsToGenerate: number; scenarioName: string };
    if (body.recordsToGenerate > 250) {
      return HttpResponse.json({ message: "Máximo 250" }, { status: 400 });
    }
    return HttpResponse.json(
      Array.from({ length: body.recordsToGenerate }, (_, index) => ({
        id: `sim-${index + 1}`,
        timestamp: "2024-06-18T08:00:00Z",
        roadType: "LOCAL" as const,
        vehicleVolume: 80 + index,
        eventType: body.scenarioName,
        weather: "SUNNY",
        streetId: `sim-street-${index + 1}`,
        streetOsmWayId: 800 + index,
        streetName: `Rua Simulada ${index + 1}`
      }))
    );
  }),
  http.get(`${BASE}/exports`, ({ request }) =>
    exportResponse(new URL(request.url).searchParams.get("format"), mockRecords)
  ),
  http.post(`${BASE}/exports/filter`, async ({ request }) => {
    const format = new URL(request.url).searchParams.get("format");
    const body = (await request.json()) as { recordIds?: string[] };
    const selected = body.recordIds?.length
      ? mockRecords.filter((record) => body.recordIds?.includes(record.id))
      : mockRecords;
    return exportResponse(format, selected);
  })
];

const csvHeader = "id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName";

function csvRow(record: TrafficRecord) {
  return [
    record.id,
    record.timestamp,
    record.roadType,
    record.vehicleVolume,
    record.eventType ?? "",
    record.weather ?? "",
    record.streetId ?? "",
    record.streetOsmWayId ?? "",
    record.streetName ?? ""
  ].join(",");
}

/** Same as the backend: the real records, with the totals in headers. The CSV starts with the UTF-8 BOM. */
export function exportResponse(format: string | null, records: TrafficRecord[]) {
  const totals = { "X-Total-Records": String(records.length), "X-Exported-Records": String(records.length) };
  if (format === "csv") {
    return new HttpResponse(`${String.fromCharCode(0xfeff)}${csvHeader}\n${records.map((record) => `${csvRow(record)}\n`).join("")}`, {
      headers: { ...totals, "Content-Type": "text/csv;charset=UTF-8" }
    });
  }
  if (format === "json") {
    return HttpResponse.json(records, { headers: totals });
  }
  return HttpResponse.json({ message: "Formato inválido" }, { status: 400 });
}
