import { http, HttpResponse } from "msw";
import type {
  MapFeatureCollection,
  PagedResponse,
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
    "Horario com maior volume agregado: 17h.",
    "Tipo de via com maior volume agregado: HIGHWAY."
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

function buildPagedRecords(request: Request): PagedResponse<TrafficRecord> {
  const url = new URL(request.url);
  const page = Number(url.searchParams.get("page") ?? "0");
  const size = Number(url.searchParams.get("size") ?? "20");
  const query = url.searchParams.get("query")?.toLowerCase().trim() ?? "";
  const filteredItems = query
    ? mockRecords.filter((record) =>
        [record.roadType, record.streetName, record.weather, record.eventType]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query))
      )
    : mockRecords;
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
  http.post(`${BASE}/traffic-map/filter`, async ({ request }) => {
    const body = (await request.json()) as { recordIds?: string[] };
    if (!body.recordIds?.length) {
      return HttpResponse.json({ type: "FeatureCollection", features: [] });
    }
    return HttpResponse.json(mockMapData);
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
  http.get(`${BASE}/exports`, ({ request }) => {
    const format = new URL(request.url).searchParams.get("format");
    if (format === "csv") {
      return new HttpResponse("id,timestamp\nrec-1,2024-06-17T08:00:00Z\n", {
        headers: {
          "Content-Type": "text/csv"
        }
      });
    }
    if (format === "json") {
      return new HttpResponse(JSON.stringify([{ id: "rec-1" }]), {
        headers: {
          "Content-Type": "application/json"
        }
      });
    }
    return HttpResponse.json({ message: "Formato inválido" }, { status: 400 });
  })
];
