import { StrictMode } from "react";
import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "../mocks/server";
import { mockSummary } from "../mocks/handlers";
import type { PagedResponse, RecordIdsResponse, TrafficRecord, TrafficRecordSummary } from "../../types/api";

// Stands in for the Leaflet map instance, to check how the map is framed.
const mapProbe = vi.hoisted(() => ({ fitBounds: vi.fn() }));

vi.mock("react-leaflet", () => ({
  MapContainer: ({ children }: { children: ReactNode }) => <div data-testid="map-container">{children}</div>,
  TileLayer: () => <div data-testid="tile-layer" />,
  Polyline: ({ children }: { children: ReactNode }) => <div data-testid="map-polyline">{children}</div>,
  Popup: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  useMap: () => ({ fitBounds: mapProbe.fitBounds })
}));

// The props each chart's y axis was rendered with, to check how its numbers are shown.
const chartProbe = vi.hoisted(() => ({
  yAxis: [] as Array<{ width?: number; tickFormatter?: (value: number) => string }>
}));

vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  AreaChart: ({ children }: { children?: ReactNode }) => <div data-testid="area-chart">{children}</div>,
  BarChart: ({ children }: { children?: ReactNode }) => <div data-testid="bar-chart">{children}</div>,
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: (props: { width?: number; tickFormatter?: (value: number) => string }) => {
    chartProbe.yAxis.push(props);
    return null;
  },
  Tooltip: () => null,
  Bar: () => <div data-testid="bar-series" />,
  Cell: () => null,
  Area: () => null
}));

import App from "../../App";

describe("App", () => {
  beforeEach(() => {
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:test");
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
    mapProbe.fitBounds.mockClear();
  });

  it("renders the dashboard and connection state", async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/SmartTrafficFlow/i)).toBeInTheDocument();
      expect(screen.getByText(/Conectado/i)).toBeInTheDocument();
      expect(screen.getByText(/Veículos capturados/i)).toBeInTheDocument();
    });
  });

  it("shows the connection as lost, not as connected, when the last load failed", async () => {
    server.use(
      http.get("http://localhost:8080/api/traffic-records/summary", () =>
        HttpResponse.json({ message: "backend caiu" }, { status: 500 })
      )
    );

    render(<App />);

    expect(await screen.findByText(/Sem conexão/i)).toBeInTheDocument();
    expect(screen.queryByText(/^Conectado$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Sincronizando$/i)).not.toBeInTheDocument();
  });

  it("shows the connection as lost when the backend cannot be reached", async () => {
    server.use(http.get("http://localhost:8080/api/traffic-records/summary", () => HttpResponse.error()));

    render(<App />);

    expect(await screen.findByText(/Sem conexão/i)).toBeInTheDocument();
    expect(screen.queryByText(/^Conectado$/i)).not.toBeInTheDocument();
  });

  it("shows syncing while loading and connected only after a successful load", async () => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get("http://localhost:8080/api/traffic-insights", async () => {
        await gate;
        return HttpResponse.json({ insights: [] });
      })
    );

    render(<App />);

    expect(await screen.findByText(/^Sincronizando$/i)).toBeInTheDocument();
    expect(screen.queryByText(/^Conectado$/i)).not.toBeInTheDocument();

    release();

    expect(await screen.findByText(/^Conectado$/i)).toBeInTheDocument();
  });

  it("goes back to connected when a later load succeeds after a failure", async () => {
    const user = userEvent.setup();
    let failing = true;
    server.use(
      http.get("http://localhost:8080/api/traffic-records/summary", () =>
        failing ? HttpResponse.json({ message: "backend caiu" }, { status: 500 }) : HttpResponse.json(mockSummary)
      )
    );

    render(<App />);
    expect(await screen.findByText(/Sem conexão/i)).toBeInTheDocument();

    failing = false;
    await user.click(screen.getByRole("button", { name: /Formulários e registros/i }));
    await user.click(await screen.findByLabelText(/Selecionar registro rec-1/i));
    await user.click(screen.getByRole("button", { name: /Aplicar seleção/i }));
    await user.click(screen.getByRole("button", { name: /Página de insights/i }));

    expect(await screen.findByText(/^Conectado$/i)).toBeInTheDocument();
  });

  it("starts on the insights view and can switch back to it", async () => {
    const user = userEvent.setup();
    render(<App />);

    const insightsButton = await screen.findByRole("button", { name: /Página de insights/i });
    const workspaceButton = screen.getByRole("button", { name: /Formulários e registros/i });

    expect(insightsButton).toHaveAttribute("aria-pressed", "true");
    expect(workspaceButton).toHaveAttribute("aria-pressed", "false");

    await user.click(workspaceButton);
    expect(await screen.findByText(/Adicionar registro de tráfego/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Página de insights/i }));
    expect(await screen.findByText(/Veículos capturados/i)).toBeInTheDocument();
  });

  it("switches to the workspace view", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));

    expect(await screen.findByText(/Adicionar registro de tráfego/i)).toBeInTheDocument();
  });

  it("searches and selects a street before saving a record", async () => {
    const user = userEvent.setup();
    let capturedStreetOsmWayId: number | null = null;
    server.use(
      http.post("http://localhost:8080/api/traffic-records", async ({ request }) => {
        const body = (await request.json()) as { streetOsmWayId: number };
        capturedStreetOsmWayId = body.streetOsmWayId;
        return HttpResponse.json(
          {
            id: "rec-created",
            timestamp: "2024-06-17T08:00:00Z",
            roadType: "ARTERIAL",
            vehicleVolume: 140,
            eventType: null,
            weather: null,
            streetId: "street-created",
            streetOsmWayId: body.streetOsmWayId,
            streetName: "Avenida Central"
          },
          { status: 201 }
        );
      })
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));

    const saveButton = await screen.findByRole("button", { name: /Salvar registro/i });
    expect(saveButton).toBeDisabled();

    await user.type(screen.getByRole("searchbox", { name: /Rua/i }), "Aven");

    const option = await screen.findByRole("button", { name: /Avenida Central/i });
    await user.click(option);

    expect(screen.getByText(/Rua selecionada \(OSM\): 101/i)).toBeInTheDocument();
    expect(saveButton).toBeEnabled();

    await user.click(saveButton);

    await waitFor(() => expect(capturedStreetOsmWayId).toBe(101));
  });

  it("shows a validation message, instead of crashing, when the date is cleared", async () => {
    const user = userEvent.setup();
    const createdBodies: { timestamp: string }[] = [];
    server.use(
      http.post("http://localhost:8080/api/traffic-records", async ({ request }) => {
        const body = (await request.json()) as { timestamp: string; streetOsmWayId: number };
        createdBodies.push(body);
        return HttpResponse.json(
          {
            id: "rec-created",
            timestamp: body.timestamp,
            roadType: "ARTERIAL",
            vehicleVolume: 140,
            eventType: null,
            weather: null,
            streetId: "street-created",
            streetOsmWayId: body.streetOsmWayId,
            streetName: "Avenida Central"
          },
          { status: 201 }
        );
      })
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await user.type(screen.getByRole("searchbox", { name: /Rua/i }), "Aven");
    await user.click(await screen.findByRole("button", { name: /Avenida Central/i }));

    const dateInput = screen.getByLabelText(/Data e hora/i);
    fireEvent.change(dateInput, { target: { value: "" } });

    expect(await screen.findByText(/Informe a data e a hora do registro/i)).toBeInTheDocument();
    expect(screen.queryByText(/Invalid time value/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Salvar registro/i }));
    expect(createdBodies).toHaveLength(0);
    expect(screen.getByText(/Informe a data e a hora do registro/i)).toBeInTheDocument();

    fireEvent.change(dateInput, { target: { value: "2026-11-03T10:15" } });
    expect(screen.queryByText(/Informe a data e a hora do registro/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Salvar registro/i }));
    await waitFor(() => expect(createdBodies).toHaveLength(1));
    expect(createdBodies[0]?.timestamp).toBe(new Date("2026-11-03T10:15").toISOString());
  });

  it("shows record creation errors without breaking the workspace", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("http://localhost:8080/api/traffic-records", () =>
        HttpResponse.json({ message: "Dados inválidos" }, { status: 400 })
      )
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await user.type(screen.getByRole("searchbox", { name: /Rua/i }), "Aven");
    await user.click(await screen.findByRole("button", { name: /Avenida Central/i }));
    await user.click(await screen.findByRole("button", { name: /Salvar registro/i }));

    expect(await screen.findByText(/Dados inválidos/i)).toBeInTheDocument();
  });

  it("shows the insight placeholder when the backend returns no insights", async () => {
    server.use(http.get("http://localhost:8080/api/traffic-insights", () => HttpResponse.json({ insights: [] })));

    render(<App />);

    expect(await screen.findByText(/Aguardando registros suficientes/i)).toBeInTheDocument();
  });

  it("applies record selection and refreshes filtered analytics", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));

    await user.click(await screen.findByLabelText(/Selecionar registro rec-1/i));
    await user.click(screen.getByRole("button", { name: /Aplicar seleção/i }));

    expect(await screen.findByText(/1 selecionado\(s\), 1 aplicado\(s\)/i)).toBeInTheDocument();
  });

  it("filters the records table by current street data", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));

    const filterInput = await screen.findByRole("searchbox", { name: /Filtro/i });
    await user.type(filterInput, "Rodovia Norte");

    // The map also names streets now, so look only inside the table.
    const table = screen.getByRole("table");
    expect(await within(table).findByText(/Rodovia Norte/i)).toBeInTheDocument();
    await waitFor(() => expect(within(table).queryByText(/Avenida Central/i)).not.toBeInTheDocument());
  });

  it("shows an empty-state row when the table filter matches nothing", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));

    const filterInput = await screen.findByRole("searchbox", { name: /Filtro/i });
    await user.type(filterInput, "Sem correspondencia");

    expect(await screen.findByText(/Nenhum registro corresponde ao filtro atual/i)).toBeInTheDocument();
  });

  it("runs a simulation", async () => {
    const user = userEvent.setup();
    let capturedRecordsToGenerate: number | null = null;
    server.use(
      http.post("http://localhost:8080/api/simulations/generate", async ({ request }) => {
        const body = (await request.json()) as { recordsToGenerate: number; scenarioName: string };
        capturedRecordsToGenerate = body.recordsToGenerate;
        return HttpResponse.json(
          Array.from({ length: body.recordsToGenerate }, (_, index) => ({
            id: `sim-${index + 1}`,
            timestamp: "2024-06-18T08:00:00Z",
            roadType: "LOCAL",
            vehicleVolume: 80 + index,
            eventType: body.scenarioName,
            weather: "SUNNY",
            streetId: `sim-street-${index + 1}`,
            streetOsmWayId: 800 + index,
            streetName: `Rua Simulada ${index + 1}`
          }))
        );
      })
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await user.click(await screen.findByRole("button", { name: /Executar simulação/i }));

    await waitFor(() => expect(capturedRecordsToGenerate).toBe(18));
  });

  it("paginates records and keeps staged selection across pages", async () => {
    const user = userEvent.setup();
    const pagedRecords = Array.from({ length: 22 }, (_, index) => ({
      id: `page-rec-${index + 1}`,
      timestamp: `2024-06-${String((index % 9) + 10).padStart(2, "0")}T08:00:00Z`,
      roadType: index % 2 === 0 ? "ARTERIAL" : "LOCAL",
      vehicleVolume: 100 + index,
      eventType: index % 3 === 0 ? "RUSH_HOUR" : null,
      weather: index % 2 === 0 ? "SUNNY" : "RAIN",
      streetId: `street-${index + 1}`,
      streetOsmWayId: 1000 + index,
      streetName: `Rua ${index + 1}`
    })) satisfies TrafficRecord[];

    server.use(
      http.get("http://localhost:8080/api/traffic-records", ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get("page") ?? "0");
        const size = Number(url.searchParams.get("size") ?? "20");
        const start = page * size;
        return HttpResponse.json({
          items: pagedRecords.slice(start, start + size),
          page,
          size,
          totalItems: pagedRecords.length,
          totalPages: Math.ceil(pagedRecords.length / size)
        } satisfies PagedResponse<TrafficRecord>);
      }),
      http.get("http://localhost:8080/api/traffic-records/summary", () =>
        HttpResponse.json({
          recordCount: pagedRecords.length,
          totalVehicleVolume: pagedRecords.reduce((sum, record) => sum + record.vehicleVolume, 0),
          uniqueStreetCount: pagedRecords.length,
          averageVehicleVolume: Math.round(
            pagedRecords.reduce((sum, record) => sum + record.vehicleVolume, 0) / pagedRecords.length
          ),
          latestTimestamp: pagedRecords[0]?.timestamp ?? null
        } satisfies TrafficRecordSummary)
      )
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await user.click(await screen.findByLabelText("Selecionar registro page-rec-1"));
    await user.click(screen.getByRole("button", { name: /Próxima página/i }));

    expect(await screen.findByLabelText(/Selecionar registro page-rec-21/i)).toBeInTheDocument();
    expect(screen.getByText(/Página 2 de 2/i)).toBeInTheDocument();
    expect(screen.getByText(/1 selecionado\(s\), 0 aplicado\(s\)/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Página anterior/i }));

    const firstCheckbox = await screen.findByLabelText("Selecionar registro page-rec-1");
    expect(firstCheckbox).toBeChecked();
  });

  it("selects and clears all filtered records across pages", async () => {
    const user = userEvent.setup();
    const pagedRecords = Array.from({ length: 22 }, (_, index) => ({
      id: `page-rec-${index + 1}`,
      timestamp: `2024-06-${String((index % 9) + 10).padStart(2, "0")}T08:00:00Z`,
      roadType: index % 2 === 0 ? "ARTERIAL" : "LOCAL",
      vehicleVolume: 100 + index,
      eventType: index % 3 === 0 ? "RUSH_HOUR" : null,
      weather: index % 2 === 0 ? "SUNNY" : "RAIN",
      streetId: `street-${index + 1}`,
      streetOsmWayId: 1000 + index,
      streetName: `Rua ${index + 1}`
    })) satisfies TrafficRecord[];
    const pagesRequested: number[] = [];
    const idsRequests: string[] = [];

    server.use(
      http.get("http://localhost:8080/api/traffic-records", ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get("page") ?? "0");
        const size = Number(url.searchParams.get("size") ?? "20");
        pagesRequested.push(page);
        const start = page * size;
        return HttpResponse.json({
          items: pagedRecords.slice(start, start + size),
          page,
          size,
          totalItems: pagedRecords.length,
          totalPages: Math.ceil(pagedRecords.length / size)
        } satisfies PagedResponse<TrafficRecord>);
      }),
      http.get("http://localhost:8080/api/traffic-records/ids", ({ request }) => {
        idsRequests.push(new URL(request.url).search);
        return HttpResponse.json({
          recordIds: pagedRecords.map((record) => record.id),
          total: pagedRecords.length,
          limit: 10000,
          truncated: false
        } satisfies RecordIdsResponse);
      }),
      http.get("http://localhost:8080/api/traffic-records/summary", () =>
        HttpResponse.json({
          recordCount: pagedRecords.length,
          totalVehicleVolume: pagedRecords.reduce((sum, record) => sum + record.vehicleVolume, 0),
          uniqueStreetCount: pagedRecords.length,
          averageVehicleVolume: Math.round(
            pagedRecords.reduce((sum, record) => sum + record.vehicleVolume, 0) / pagedRecords.length
          ),
          latestTimestamp: pagedRecords[0]?.timestamp ?? null
        } satisfies TrafficRecordSummary)
      )
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await user.click(screen.getByRole("button", { name: /Selecionar\/limpar filtrados/i }));

    await waitFor(() => expect(screen.getByText(/22 selecionado\(s\), 0 aplicado\(s\)/i)).toBeInTheDocument());

    // All the ids come from one request; no other page of the table was fetched to collect them.
    expect(idsRequests).toEqual([""]);
    expect(pagesRequested).toEqual([0]);

    await user.click(screen.getByRole("button", { name: /Próxima página/i }));

    const page21Checkbox = await screen.findByLabelText("Selecionar registro page-rec-21");
    expect(page21Checkbox).toBeChecked();

    await user.click(screen.getByRole("button", { name: /Selecionar\/limpar filtrados/i }));

    await waitFor(() => expect(screen.getByText(/0 selecionado\(s\), 0 aplicado\(s\)/i)).toBeInTheDocument());

    expect(screen.getByLabelText("Selecionar registro page-rec-21")).not.toBeChecked();

    await user.click(screen.getByRole("button", { name: /Página anterior/i }));

    const firstCheckbox = await screen.findByLabelText("Selecionar registro page-rec-1");
    expect(firstCheckbox).not.toBeChecked();
  });

  it("collects the ids of the current filter in one request and warns when the list was cut", async () => {
    const user = userEvent.setup();
    const idsRequests: string[] = [];
    server.use(
      http.get("http://localhost:8080/api/traffic-records/ids", ({ request }) => {
        idsRequests.push(new URL(request.url).search);
        return HttpResponse.json({
          recordIds: ["rec-1", "rec-2"],
          total: 12345,
          limit: 10000,
          truncated: true
        } satisfies RecordIdsResponse);
      })
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await user.type(await screen.findByRole("searchbox", { name: /Filtro/i }), "Rodovia");
    await screen.findByText(/1 encontrados para esta busca/i);
    await user.click(screen.getByRole("button", { name: /Selecionar\/limpar filtrados/i }));

    expect(
      await screen.findByText("Selecionados os 10.000 registros mais recentes de 12.345 encontrados.")
    ).toBeInTheDocument();
    expect(screen.getByText(/2 selecionado\(s\), 0 aplicado\(s\)/i)).toBeInTheDocument();
    expect(idsRequests).toEqual(["?query=Rodovia"]);

    // Clearing the filtered records removes the warning along with the selection.
    await user.click(screen.getByRole("button", { name: /Selecionar\/limpar filtrados/i }));
    await waitFor(() => expect(screen.getByText(/0 selecionado\(s\), 0 aplicado\(s\)/i)).toBeInTheDocument());
    expect(screen.queryByText(/Selecionados os 10.000 registros mais recentes/i)).not.toBeInTheDocument();
  });

  it("does not warn when every filtered record fit in the list", async () => {
    const user = userEvent.setup();

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await screen.findByLabelText(/Selecionar registro rec-1/i);
    await user.click(screen.getByRole("button", { name: /Selecionar\/limpar filtrados/i }));

    expect(await screen.findByText(/2 selecionado\(s\), 0 aplicado\(s\)/i)).toBeInTheDocument();
    expect(screen.queryByText(/Selecionados os .* registros mais recentes/i)).not.toBeInTheDocument();
  });

  it("clears the cut warning when the selection is cleared", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("http://localhost:8080/api/traffic-records/ids", () =>
        HttpResponse.json({
          recordIds: ["rec-1"],
          total: 500,
          limit: 1,
          truncated: true
        } satisfies RecordIdsResponse)
      )
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await screen.findByLabelText(/Selecionar registro rec-1/i);
    await user.click(screen.getByRole("button", { name: /Selecionar\/limpar filtrados/i }));
    expect(await screen.findByText(/Selecionados os 1 registros mais recentes de 500 encontrados/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^Limpar seleção$/i }));

    expect(screen.queryByText(/Selecionados os 1 registros mais recentes/i)).not.toBeInTheDocument();
  });

  it("keeps the dashboard stable when the backend returns no records", async () => {
    server.use(
      http.get("http://localhost:8080/api/traffic-records", () =>
        HttpResponse.json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })
      ),
      http.get("http://localhost:8080/api/traffic-records/summary", () =>
        HttpResponse.json({
          recordCount: 0,
          totalVehicleVolume: 0,
          uniqueStreetCount: 0,
          averageVehicleVolume: 0,
          latestTimestamp: null
        } satisfies TrafficRecordSummary)
      )
    );

    render(<App />);

    expect(await screen.findByText(/Conectado/i)).toBeInTheDocument();
    expect(screen.getByText(/Veículos capturados/i)).toBeInTheDocument();
  });

  it("exports csv", async () => {
    const user = userEvent.setup();
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Exportar CSV/i }));

    expect(await screen.findByText(/Exportação preparada no formato CSV/i)).toBeInTheDocument();
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalledTimes(1);
  });

  it("renders the JSON export action", async () => {
    render(<App />);

    expect(await screen.findByRole("button", { name: /Exportar JSON/i })).toBeInTheDocument();
  });

  it("formats the y axis numbers compactly and leaves room for them, so they are not cut", async () => {
    chartProbe.yAxis.length = 0;

    render(<App />);
    await screen.findByText(/^Conectado$/i);

    await waitFor(() => expect(chartProbe.yAxis.length).toBeGreaterThanOrEqual(3));
    // The number formatter separates the abbreviation with a no-break space; compare with a plain space.
    const tick = (axis: { tickFormatter?: (value: number) => string }, value: number) =>
      axis.tickFormatter?.(value).replace(/\s/g, " ");

    for (const axis of chartProbe.yAxis) {
      expect(axis.tickFormatter).toBeTypeOf("function");
      expect(tick(axis, 0)).toBe("0");
      expect(tick(axis, 950)).toBe("950");
      expect(tick(axis, 1234)).toBe("1,2 mil");
      expect(tick(axis, 999_000)).toBe("999 mil");
      expect(tick(axis, 1_500_000)).toBe("1,5 mi");
      expect(axis.width).toBeGreaterThanOrEqual(56);
    }
  });

  it("renders accessibility anchors used by the current layout", async () => {
    render(<App />);

    expect(await screen.findByText(/Pular para o conteúdo/i)).toBeInTheDocument();
    expect(document.getElementById("main-content")).not.toBeNull();
    expect(document.querySelectorAll("[aria-live='polite']").length).toBeGreaterThan(0);
  });

  it("ignores a slow dashboard response that arrives after a newer one", async () => {
    const user = userEvent.setup();
    let releaseSlowResponse!: () => void;
    const slowResponse = new Promise<void>((resolve) => {
      releaseSlowResponse = resolve;
    });
    server.use(
      http.get("http://localhost:8080/api/traffic-records/summary", async () => {
        await slowResponse;
        return HttpResponse.json({
          recordCount: 111,
          totalVehicleVolume: 9999,
          uniqueStreetCount: 5,
          averageVehicleVolume: 90,
          latestTimestamp: "2024-06-17T17:30:00Z"
        } satisfies TrafficRecordSummary);
      })
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await user.click(await screen.findByLabelText(/Selecionar registro rec-1/i));
    await user.click(screen.getByRole("button", { name: /Aplicar seleção/i }));
    expect(await screen.findByText(/^1 registros no contexto atual/i)).toBeInTheDocument();

    releaseSlowResponse();
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(screen.getByText(/^1 registros no contexto atual/i)).toBeInTheDocument();
    expect(screen.queryByText(/111 registros no contexto atual/i)).not.toBeInTheDocument();
  });

  it("ignores a slow records page that arrives after a newer filter", async () => {
    const user = userEvent.setup();
    const pagedRecords = Array.from({ length: 22 }, (_, index) => ({
      id: `page-rec-${index + 1}`,
      timestamp: "2024-06-17T08:00:00Z",
      roadType: "LOCAL",
      vehicleVolume: 100 + index,
      eventType: null,
      weather: null,
      streetId: `street-${index + 1}`,
      streetOsmWayId: 1000 + index,
      streetName: `Rua ${index + 1}`
    })) satisfies TrafficRecord[];
    let releaseSecondPage!: () => void;
    const secondPage = new Promise<void>((resolve) => {
      releaseSecondPage = resolve;
    });

    server.use(
      http.get("http://localhost:8080/api/traffic-records", async ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get("page") ?? "0");
        const size = Number(url.searchParams.get("size") ?? "20");
        const query = (url.searchParams.get("query") ?? "").toLowerCase();
        if (page === 1) {
          await secondPage;
        }
        const filtered = pagedRecords.filter((record) => !query || (record.streetName ?? "").toLowerCase().includes(query));
        return HttpResponse.json({
          items: filtered.slice(page * size, page * size + size),
          page,
          size,
          totalItems: filtered.length,
          totalPages: Math.ceil(filtered.length / size)
        } satisfies PagedResponse<TrafficRecord>);
      })
    );

    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
    await screen.findByLabelText("Selecionar registro page-rec-1");
    await user.click(screen.getByRole("button", { name: /Próxima página/i }));
    await user.type(await screen.findByRole("searchbox", { name: /Filtro/i }), "Rua 22");

    expect(await screen.findByText(/Página 1 de 1/i)).toBeInTheDocument();

    releaseSecondPage();
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(screen.getByText(/Página 1 de 1/i)).toBeInTheDocument();
    expect(screen.queryByText(/Página 2 de 2/i)).not.toBeInTheDocument();
  });

  describe("map", () => {
    const farAwayFeature = {
      type: "Feature" as const,
      properties: {
        recordId: "rec-2",
        streetId: "street-2",
        streetOsmWayId: 202,
        streetName: "Rodovia Norte",
        vehicleVolume: 250,
        trafficLevel: "HIGH" as const,
        color: "#e04545"
      },
      geometry: {
        type: "LineString" as const,
        coordinates: [
          [-46.9, -23.1],
          [-46.8, -23.2]
        ] as [number, number][]
      }
    };

    it("frames the map on the streets it shows", async () => {
      render(<App />);
      await screen.findByText(/^Conectado$/i);

      await waitFor(() =>
        expect(mapProbe.fitBounds).toHaveBeenCalledWith(
          [
            [-23.5505, -46.6333],
            [-23.5512, -46.6321]
          ],
          expect.objectContaining({ padding: [24, 24] })
        )
      );
    });

    it("frames the map again when the selection changes the data", async () => {
      const user = userEvent.setup();
      server.use(
        http.post("http://localhost:8080/api/traffic-map/filter", () =>
          HttpResponse.json({
            type: "FeatureCollection",
            features: [farAwayFeature],
            meta: { scope: "selection", limit: null, count: 1 }
          })
        )
      );

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
      await user.click(await screen.findByLabelText(/Selecionar registro rec-2/i));
      mapProbe.fitBounds.mockClear();
      await user.click(screen.getByRole("button", { name: /Aplicar seleção/i }));

      await waitFor(() =>
        expect(mapProbe.fitBounds).toHaveBeenLastCalledWith(
          [
            [-23.1, -46.9],
            [-23.2, -46.8]
          ],
          expect.objectContaining({ padding: [24, 24] })
        )
      );
    });

    it("does not frame anything when there is nothing to show", async () => {
      server.use(
        http.get("http://localhost:8080/api/traffic-map", () =>
          HttpResponse.json({
            type: "FeatureCollection",
            features: [],
            meta: { scope: "latest", limit: 250, count: 0 }
          })
        )
      );

      render(<App />);

      expect(await screen.findByText(/Ainda não há registros para plotar no mapa/i)).toBeInTheDocument();
      expect(mapProbe.fitBounds).not.toHaveBeenCalled();
    });

    it("tells that the map shows the most recent records when nothing is selected", async () => {
      server.use(
        http.get("http://localhost:8080/api/traffic-map", () =>
          HttpResponse.json({
            type: "FeatureCollection",
            features: [farAwayFeature],
            meta: { scope: "latest", limit: 250, count: 250 }
          })
        )
      );

      render(<App />);

      expect(await screen.findByText("Mostrando os 250 registros mais recentes")).toBeInTheDocument();
    });

    it("tells how many selected records the map shows", async () => {
      const user = userEvent.setup();
      server.use(
        http.post("http://localhost:8080/api/traffic-map/filter", () =>
          HttpResponse.json({
            type: "FeatureCollection",
            features: [farAwayFeature],
            meta: { scope: "selection", limit: null, count: 2 }
          })
        )
      );

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
      await user.click(await screen.findByLabelText(/Selecionar registro rec-1/i));
      await user.click(screen.getByRole("button", { name: /Aplicar seleção/i }));

      expect(await screen.findByText("Mostrando 2 registros selecionados")).toBeInTheDocument();
    });

    it("uses the singular when the map shows a single record", async () => {
      render(<App />);

      expect(await screen.findByText("Mostrando o registro mais recente")).toBeInTheDocument();
    });
  });

  describe("when the page opens", () => {
    // Records every API request and lets it continue to the regular handlers (a handler that returns nothing
    // passes the request on). A request counts as completed when it was not cancelled by the page.
    function trackRequests() {
      const sent = new Map<string, number>();
      const completed = new Map<string, number>();
      server.use(
        http.all("http://localhost:8080/api/*", async ({ request }) => {
          const url = new URL(request.url);
          const key = `${request.method} ${url.pathname}${url.search}`;
          sent.set(key, (sent.get(key) ?? 0) + 1);
          await new Promise((resolve) => setTimeout(resolve, 0));
          if (!request.signal.aborted) {
            completed.set(key, (completed.get(key) ?? 0) + 1);
          }
        })
      );
      return { sent, completed };
    }

    it("sends each request once", async () => {
      const { sent, completed } = trackRequests();

      render(<App />);
      expect(await screen.findByText(/^Conectado$/i)).toBeInTheDocument();
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(Object.fromEntries(completed)).toEqual(Object.fromEntries(sent));
      expect(Object.fromEntries(sent)).toEqual({
        "GET /api/traffic-records/summary": 1,
        "GET /api/traffic-stats?groupBy=hour": 1,
        "GET /api/traffic-stats?groupBy=weekday": 1,
        "GET /api/traffic-stats?groupBy=roadType": 1,
        "GET /api/traffic-insights": 1,
        "GET /api/traffic-map": 1,
        "GET /api/traffic-records?page=0&size=20": 1
      });
    });

    it("completes each request once even when React runs the effects twice (StrictMode in development)", async () => {
      const { sent, completed } = trackRequests();

      render(
        <StrictMode>
          <App />
        </StrictMode>
      );
      expect(await screen.findByText(/^Conectado$/i)).toBeInTheDocument();
      await new Promise((resolve) => setTimeout(resolve, 100));

      const duplicated = [...completed.entries()].filter(([, times]) => times > 1);
      expect(duplicated).toEqual([]);
      expect(Object.keys(Object.fromEntries(completed))).toEqual(Object.keys(Object.fromEntries(sent)));
    });
  });

  it("shows backend errors without crashing", async () => {
    server.use(
      http.get("http://localhost:8080/api/traffic-records/summary", () =>
        HttpResponse.json({ message: "backend caiu" }, { status: 500 })
      )
    );

    render(<App />);

    expect(await screen.findByText(/backend caiu/i)).toBeInTheDocument();
    expect(screen.getByText(/Situação/i)).toBeInTheDocument();
  });
});
