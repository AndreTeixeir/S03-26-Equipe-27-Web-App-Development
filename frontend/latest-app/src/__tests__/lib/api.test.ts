import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "../mocks/server";
import {
  ApiError,
  createTrafficRecord,
  generateSimulation,
  getExport,
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

  it("returns an empty feature collection when no record ids are passed", async () => {
    await expect(getTrafficMap([])).resolves.toEqual({ type: "FeatureCollection", features: [] });
  });

  it("loads filtered map data using POST", async () => {
    const mapData = await getTrafficMap(["rec-1"]);
    expect(mapData.features[0]?.properties.streetName).toBe("Avenida Central");
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
    const blob = await getExport("csv");
    expect(blob.type).toBe("text/csv");
    await expect(readBlobText(blob)).resolves.toBe("id,timestamp\nrec-1,2024-06-17T08:00:00Z\n");
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
