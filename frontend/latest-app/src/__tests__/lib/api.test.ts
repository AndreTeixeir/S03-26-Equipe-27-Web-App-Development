import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "../mocks/server";
import {
  ApiError,
  createTrafficRecord,
  generateSimulation,
  getExport,
  getFilteredRecordIds,
  getTrafficInsights,
  getTrafficMap,
  getTrafficRecordSummary,
  getTrafficRecords,
  getTrafficStats,
  searchStreets
} from "../../lib/api";

const BASE = "http://localhost:8080/api";

// jsdom's Blob has no text(), while the Blob returned by fetch in Node does; read either one.
function readBlobText(blob: Blob): Promise<string> {
  if (typeof blob.text === "function") {
    return blob.text();
  }

  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

describe("api client", () => {
  it("loads traffic records", async () => {
    const records = await getTrafficRecords();
    expect(records.items).toHaveLength(2);
    expect(records.items[0]?.streetName).toBe("Avenida Central");
    expect(records.totalItems).toBe(2);
  });

  it("returns an empty list when the records endpoint is empty", async () => {
    server.use(
      http.get(`${BASE}/traffic-records`, () =>
        HttpResponse.json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })
      )
    );

    await expect(getTrafficRecords()).resolves.toEqual({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  });

  it("passes pagination and query params for records", async () => {
    let capturedUrl = "";
    server.use(
      http.get(`${BASE}/traffic-records`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ items: [], page: 1, size: 10, totalItems: 0, totalPages: 0 });
      })
    );

    await getTrafficRecords({ page: 1, size: 10, query: "Rodovia" });

    expect(capturedUrl).toContain("page=1");
    expect(capturedUrl).toContain("size=10");
    expect(capturedUrl).toContain("query=Rodovia");
  });

  it("creates a traffic record using streetOsmWayId", async () => {
    const record = await createTrafficRecord({
      timestamp: "2024-06-17T08:00:00Z",
      roadType: "ARTERIAL",
      vehicleVolume: 120,
      eventType: "RUSH_HOUR",
      weather: "SUNNY",
      streetOsmWayId: 101
    });

    expect(record.streetOsmWayId).toBe(101);
  });

  it("sends create requests as json", async () => {
    let capturedType = "";
    server.use(
      http.post(`${BASE}/traffic-records`, ({ request }) => {
        capturedType = request.headers.get("content-type") ?? "";
        return HttpResponse.json({
          id: "rec-created",
          timestamp: "2024-06-17T08:00:00Z",
          roadType: "ARTERIAL",
          vehicleVolume: 120,
          eventType: "RUSH_HOUR",
          weather: "SUNNY",
          streetId: "street-1",
          streetOsmWayId: 101,
          streetName: "Avenida Central"
        });
      })
    );

    await createTrafficRecord({
      timestamp: "2024-06-17T08:00:00Z",
      roadType: "ARTERIAL",
      vehicleVolume: 120,
      eventType: "RUSH_HOUR",
      weather: "SUNNY",
      streetOsmWayId: 101
    });

    expect(capturedType).toContain("application/json");
  });

  it("surfaces record creation validation errors", async () => {
    server.use(http.post(`${BASE}/traffic-records`, () => HttpResponse.json({ message: "Dados inválidos" }, { status: 400 })));

    await expect(
      createTrafficRecord({
        timestamp: "2024-06-17T08:00:00Z",
        roadType: "ARTERIAL",
        vehicleVolume: 120,
        eventType: "RUSH_HOUR",
        weather: "SUNNY",
        streetOsmWayId: 101
      })
    ).rejects.toMatchObject({ status: 400, message: "Dados inválidos" } satisfies Partial<ApiError>);
  });

  it("loads stats without a selection using GET", async () => {
    const stats = await getTrafficStats("hour");
    expect(stats.labels).toEqual(["08", "17", "12"]);
  });

  it("loads filtered stats using POST", async () => {
    const stats = await getTrafficStats("hour", ["rec-1"]);
    expect(stats.values).toEqual([120, 250, 80]);
  });

  it("surfaces invalid stats requests", async () => {
    await expect(getTrafficStats("invalid")).rejects.toMatchObject({ status: 400 } satisfies Partial<ApiError>);
  });

  it("loads insights without a selection using GET", async () => {
    const insights = await getTrafficInsights();
    expect(insights.insights).toHaveLength(2);
  });

  it("loads an empty insights response", async () => {
    server.use(http.get(`${BASE}/traffic-insights`, () => HttpResponse.json({ insights: [] })));

    await expect(getTrafficInsights()).resolves.toEqual({ insights: [] });
  });

  it("loads filtered insights using POST", async () => {
    const insights = await getTrafficInsights(["rec-1"]);
    expect(insights.insights[0]).toMatch(/17h/);
  });

  it("loads record summary without a selection using GET", async () => {
    const summary = await getTrafficRecordSummary();
    expect(summary.recordCount).toBe(2);
    expect(summary.totalVehicleVolume).toBe(370);
  });

  it("loads record summary with a selection using POST", async () => {
    const summary = await getTrafficRecordSummary(["rec-1"]);
    expect(summary.recordCount).toBe(1);
    expect(summary.totalVehicleVolume).toBe(120);
  });

  it("loads the most recent records for the map using GET when no record ids are passed", async () => {
    const mapData = await getTrafficMap([]);

    expect(mapData.meta).toEqual({ scope: "latest", limit: 250, count: 1 });
    expect(mapData.features[0]?.properties.streetName).toBe("Avenida Central");
  });

  it("asks the backend for the latest records instead of answering an empty map locally", async () => {
    let requested = "";
    server.use(
      http.get(`${BASE}/traffic-map`, ({ request }) => {
        requested = `${request.method} ${new URL(request.url).pathname}`;
        return HttpResponse.json({ type: "FeatureCollection", features: [] });
      })
    );

    await getTrafficMap([]);

    expect(requested).toBe("GET /api/traffic-map");
  });

  it("loads filtered map data using POST", async () => {
    const mapData = await getTrafficMap(["rec-1"]);
    expect(mapData.features[0]?.properties.streetName).toBe("Avenida Central");
    expect(mapData.meta?.scope).toBe("selection");
  });

  it("collects the ids of the filtered records in one request", async () => {
    const all = await getFilteredRecordIds("");
    const highways = await getFilteredRecordIds("  Rodovia ");

    expect(all.recordIds).toEqual(["rec-1", "rec-2"]);
    expect(all.truncated).toBe(false);
    expect(highways.recordIds).toEqual(["rec-2"]);
    expect(highways.total).toBe(1);
  });

  it("sends the filter text in the query string and leaves it out when empty", async () => {
    const searches: string[] = [];
    server.use(
      http.get(`${BASE}/traffic-records/ids`, ({ request }) => {
        searches.push(new URL(request.url).search);
        return HttpResponse.json({ recordIds: [], total: 0, limit: 10000, truncated: false });
      })
    );

    await getFilteredRecordIds("   ");
    await getFilteredRecordIds("norte & sul");

    expect(searches).toEqual(["", "?query=norte+%26+sul"]);
  });

  it("surfaces errors from the filtered ids request", async () => {
    server.use(
      http.get(`${BASE}/traffic-records/ids`, () => HttpResponse.json({ message: "Parâmetro inválido" }, { status: 400 }))
    );

    await expect(getFilteredRecordIds("x")).rejects.toMatchObject({ status: 400, message: "Parâmetro inválido" });
  });

  it("searches streets", async () => {
    const response = await searchStreets("Aven");
    expect(response.items[0]?.osmWayId).toBe(101);
  });

  it("generates simulation records", async () => {
    const records = await generateSimulation({ recordsToGenerate: 3, scenarioName: "Teste" });
    expect(records).toHaveLength(3);
  });

  it("surfaces simulation validation errors", async () => {
    await expect(generateSimulation({ recordsToGenerate: 251, scenarioName: "Excesso" })).rejects.toMatchObject({
      status: 400
    } satisfies Partial<ApiError>);
  });

  it("downloads csv export as blob", async () => {
    const { blob } = await getExport("csv");
    expect(blob.type).toContain("text/csv");
    const text = await readBlobText(blob);
    expect(text).toContain("id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName\n");
    expect(text).toContain("rec-1,2024-06-17T08:00:00Z,ARTERIAL,120,RUSH_HOUR,SUNNY,street-1,101,Avenida Central\n");
  });

  it("exports the real records as json, with the same fields as the csv", async () => {
    const { blob } = await getExport("json");

    const records = JSON.parse(await readBlobText(blob)) as Array<Record<string, unknown>>;
    expect(records).toHaveLength(2);
    expect(Object.keys(records[0] ?? {})).toEqual([
      "id",
      "timestamp",
      "roadType",
      "vehicleVolume",
      "eventType",
      "weather",
      "streetId",
      "streetOsmWayId",
      "streetName"
    ]);
    expect(records[1]?.streetName).toBe("Rodovia Norte");
  });

  it("reads how many records exist and how many were exported from the response headers", async () => {
    server.use(
      http.get(`${BASE}/exports`, () =>
        HttpResponse.json([], { headers: { "X-Total-Records": "12345", "X-Exported-Records": "10000" } })
      )
    );

    const result = await getExport("json");

    expect(result.total).toBe(12345);
    expect(result.exported).toBe(10000);
  });

  it("reports unknown totals as null when the response has no headers", async () => {
    server.use(http.get(`${BASE}/exports`, () => new HttpResponse("id\n", { headers: { "Content-Type": "text/csv" } })));

    const result = await getExport("csv");

    expect(result.total).toBeNull();
    expect(result.exported).toBeNull();
  });

  it("sends the selected ids in the body of a POST, never in the URL", async () => {
    const seen: { method: string; url: string; body: unknown }[] = [];
    server.use(
      http.post(`${BASE}/exports/filter`, async ({ request }) => {
        seen.push({ method: request.method, url: request.url, body: await request.json() });
        return HttpResponse.json([], { headers: { "X-Total-Records": "2", "X-Exported-Records": "2" } });
      })
    );

    const result = await getExport("json", ["rec-1", "rec-2"]);

    expect(seen).toHaveLength(1);
    expect(seen[0]?.method).toBe("POST");
    expect(seen[0]?.url).toBe(`${BASE}/exports/filter?format=json`);
    expect(seen[0]?.body).toEqual({ recordIds: ["rec-1", "rec-2"] });
    expect(result.exported).toBe(2);
  });

  it("exports only the selected records", async () => {
    const { blob } = await getExport("json", ["rec-2"]);

    const records = JSON.parse(await readBlobText(blob)) as Array<{ id: string }>;
    expect(records.map((record) => record.id)).toEqual(["rec-2"]);
  });

  it("uses the GET route when the selection is empty", async () => {
    let method = "";
    server.use(
      http.get(`${BASE}/exports`, ({ request }) => {
        method = request.method;
        return HttpResponse.json([]);
      })
    );

    await getExport("json", []);

    expect(method).toBe("GET");
  });

  it("surfaces errors of the selection export", async () => {
    server.use(
      http.post(`${BASE}/exports/filter`, () =>
        HttpResponse.json({ message: "A seleção tem 4 registros e o limite por exportação é 3." }, { status: 400 })
      )
    );

    await expect(getExport("csv", ["rec-1"])).rejects.toMatchObject({
      status: 400,
      message: "A seleção tem 4 registros e o limite por exportação é 3."
    });
  });

  it("explains failures in Portuguese when the backend sends no message", async () => {
    server.use(
      http.get(`${BASE}/traffic-records`, () => new HttpResponse(null, { status: 502 })),
      http.get(`${BASE}/exports`, () => new HttpResponse(null, { status: 500 })),
      http.post(`${BASE}/exports/filter`, () => new HttpResponse(null, { status: 503 }))
    );

    await expect(getTrafficRecords()).rejects.toMatchObject({
      status: 502,
      message: "A requisição falhou (HTTP 502)."
    });
    await expect(getExport("csv")).rejects.toMatchObject({
      status: 500,
      message: "A exportação falhou (HTTP 500)."
    });
    await expect(getExport("csv", ["rec-1"])).rejects.toMatchObject({
      status: 503,
      message: "A exportação falhou (HTTP 503)."
    });
  });

  it("surfaces export errors", async () => {
    server.use(http.get(`${BASE}/exports`, () => HttpResponse.json({ message: "Formato inválido" }, { status: 400 })));

    await expect(getExport("csv")).rejects.toMatchObject({
      status: 400,
      message: "Formato inválido"
    } satisfies Partial<ApiError>);
  });

  it("surfaces API errors and status", async () => {
    server.use(
      http.get(`${BASE}/traffic-records`, () => HttpResponse.json({ message: "falhou" }, { status: 503 }))
    );

    await expect(getTrafficRecords()).rejects.toMatchObject({
      message: "falhou",
      status: 503
    } satisfies Partial<ApiError>);
  });
});
