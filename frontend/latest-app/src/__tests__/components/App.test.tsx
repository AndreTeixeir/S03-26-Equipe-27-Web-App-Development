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
  MapContainer: ({ children, zoomControl }: { children: ReactNode; zoomControl?: boolean }) => (
    <div data-testid="map-container" data-zoom-control={String(zoomControl)}>
      {children}
    </div>
  ),
  ZoomControl: ({ zoomInTitle, zoomOutTitle }: { zoomInTitle?: string; zoomOutTitle?: string }) => (
    <div data-testid="zoom-control" data-zoom-in={zoomInTitle} data-zoom-out={zoomOutTitle} />
  ),
  TileLayer: () => <div data-testid="tile-layer" />,
  Polyline: ({ children }: { children: ReactNode }) => <div data-testid="map-polyline">{children}</div>,
  Popup: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  useMap: () => ({ fitBounds: mapProbe.fitBounds })
}));

// The props each chart's y axis was rendered with, to check how its numbers are shown.
const chartProbe = vi.hoisted(() => ({
  yAxis: [] as Array<{ width?: number; tickFormatter?: (value: number) => string }>,
  seriesNames: [] as Array<string | undefined>
}));

vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  AreaChart: ({ children, data }: { children?: ReactNode; data?: { label: string }[] }) => (
    <div data-testid="area-chart" data-labels={JSON.stringify((data ?? []).map((point) => point.label))}>
      {children}
    </div>
  ),
  BarChart: ({ children, data }: { children?: ReactNode; data?: { label: string }[] }) => (
    <div data-testid="bar-chart" data-labels={JSON.stringify((data ?? []).map((point) => point.label))}>
      {children}
    </div>
  ),
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: (props: { width?: number; tickFormatter?: (value: number) => string }) => {
    chartProbe.yAxis.push(props);
    return null;
  },
  Tooltip: () => null,
  Bar: (props: { name?: string }) => {
    chartProbe.seriesNames.push(props.name);
    return <div data-testid="bar-series" />;
  },
  Cell: () => null,
  Area: (props: { name?: string }) => {
    chartProbe.seriesNames.push(props.name);
    return null;
  }
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

  describe("export", () => {
    it("says how many records were exported", async () => {
      const user = userEvent.setup();
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Exportar CSV/i }));

      expect(await screen.findByText(/Exportação preparada no formato CSV: 2 registros\./i)).toBeInTheDocument();
    });

    it("warns when the file was cut at the backend ceiling", async () => {
      const user = userEvent.setup();
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
      server.use(
        http.get("http://localhost:8080/api/exports", () =>
          HttpResponse.json([], { headers: { "X-Total-Records": "12345", "X-Exported-Records": "10000" } })
        )
      );

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Exportar JSON/i }));

      expect(
        await screen.findByText(/Exportados os 10\.000 registros mais recentes de 12\.345\./i)
      ).toBeInTheDocument();
    });

    it("exports the applied selection with a POST, sending the ids in the body", async () => {
      const user = userEvent.setup();
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
      const posts: { url: string; body: unknown }[] = [];
      let gets = 0;
      server.use(
        http.get("http://localhost:8080/api/exports", () => {
          gets += 1;
          return HttpResponse.json([]);
        }),
        http.post("http://localhost:8080/api/exports/filter", async ({ request }) => {
          posts.push({ url: request.url, body: await request.json() });
          return HttpResponse.json([{ id: "rec-1" }], {
            headers: { "X-Total-Records": "1", "X-Exported-Records": "1" }
          });
        })
      );

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
      await user.click(await screen.findByLabelText(/Selecionar registro rec-1/i));
      await user.click(screen.getByRole("button", { name: /Aplicar seleção/i }));
      await screen.findByText(/1 selecionado\(s\), 1 aplicado\(s\)/i);
      await user.click(screen.getByRole("button", { name: /Exportar JSON/i }));

      expect(await screen.findByText(/Exportação preparada no formato JSON: 1 registro selecionado\./i)).toBeInTheDocument();
      expect(posts).toEqual([
        { url: "http://localhost:8080/api/exports/filter?format=json", body: { recordIds: ["rec-1"] } }
      ]);
      expect(gets).toBe(0);
    });

    it("does not use a selection that was not applied yet", async () => {
      const user = userEvent.setup();
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
      let posts = 0;
      server.use(
        http.post("http://localhost:8080/api/exports/filter", () => {
          posts += 1;
          return HttpResponse.json([]);
        })
      );

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
      await user.click(await screen.findByLabelText(/Selecionar registro rec-1/i));
      await user.click(screen.getByRole("button", { name: /Exportar CSV/i }));

      expect(await screen.findByText(/Exportação preparada no formato CSV: 2 registros\./i)).toBeInTheDocument();
      expect(posts).toBe(0);
    });

    it("downloads the file with the right name", async () => {
      const user = userEvent.setup();
      const downloads: string[] = [];
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
        downloads.push(this.download);
      });

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Exportar CSV/i }));
      await user.click(await screen.findByRole("button", { name: /Exportar JSON/i }));

      await waitFor(() => expect(downloads).toEqual(["traffic-data.csv", "traffic-data.json"]));
    });

    it("shows the error of a selection that is too large", async () => {
      const user = userEvent.setup();
      server.use(
        http.post("http://localhost:8080/api/exports/filter", () =>
          HttpResponse.json(
            { message: "A seleção tem 10001 registros e o limite por exportação é 10000. Reduza a seleção." },
            { status: 400 }
          )
        )
      );

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
      await user.click(await screen.findByLabelText(/Selecionar registro rec-1/i));
      await user.click(screen.getByRole("button", { name: /Aplicar seleção/i }));
      await screen.findByText(/1 selecionado\(s\), 1 aplicado\(s\)/i);
      await user.click(screen.getByRole("button", { name: /Exportar CSV/i }));

      expect(await screen.findByText(/O limite por exportação é 10000|limite por exportação é 10000/i)).toBeInTheDocument();
    });
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

  describe("texts", () => {
    const hours = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, "0"));
    const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

    function chartLabels(title: string): string[] {
      const chart = within(screen.getByLabelText(title)).getByTestId(/-chart$/);
      return JSON.parse(chart.getAttribute("data-labels") ?? "[]") as string[];
    }

    function serveStats(byGroup: Record<string, { labels: string[]; values: number[] }>) {
      server.use(
        http.get("http://localhost:8080/api/traffic-stats", ({ request }) => {
          const groupBy = new URL(request.url).searchParams.get("groupBy") ?? "";
          return HttpResponse.json(byGroup[groupBy] ?? { labels: [], values: [] });
        })
      );
    }

    it("abbreviates the weekdays and titles the weekly chart 'Distribuição por dia da semana'", async () => {
      serveStats({
        hour: { labels: hours, values: hours.map(() => 0) },
        weekday: { labels: weekdays, values: [10, 20, 30, 40, 50, 60, 70] },
        roadType: { labels: ["LOCAL"], values: [5] }
      });

      render(<App />);
      await screen.findByText(/^Conectado$/i);

      expect(screen.getByText("Distribuição por dia da semana")).toBeInTheDocument();
      expect(screen.queryByText(/por dia útil/i)).not.toBeInTheDocument();
      expect(chartLabels("Distribuição por dia da semana")).toEqual(["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"]);
    });

    it("names the road types in Portuguese in the chart", async () => {
      serveStats({
        hour: { labels: hours, values: hours.map(() => 0) },
        weekday: { labels: weekdays, values: [1, 1, 1, 1, 1, 1, 1] },
        roadType: { labels: ["LOCAL", "ARTERIAL", "HIGHWAY", "UNKNOWN"], values: [4, 3, 2, 1] }
      });

      render(<App />);
      await screen.findByText(/^Conectado$/i);

      expect(chartLabels("Divisão por tipo de via")).toEqual([
        "Via local",
        "Via arterial",
        "Via expressa",
        "Não informado"
      ]);
    });

    it("keeps the hours as numbers on the hourly chart", async () => {
      serveStats({
        hour: { labels: hours, values: hours.map((_, hour) => hour) },
        weekday: { labels: weekdays, values: [1, 1, 1, 1, 1, 1, 1] },
        roadType: { labels: ["LOCAL"], values: [1] }
      });

      render(<App />);
      await screen.findByText(/^Conectado$/i);

      expect(chartLabels("Curva horária de pressão")).toEqual(hours);
    });

    it("shows the peak window as 18h", async () => {
      serveStats({
        hour: { labels: hours, values: hours.map((_, hour) => (hour === 18 ? 900 : 10)) },
        weekday: { labels: weekdays, values: [1, 1, 1, 1, 1, 1, 1] },
        roadType: { labels: ["LOCAL"], values: [1] }
      });

      render(<App />);
      await screen.findByText(/^Conectado$/i);

      const card = screen.getByText("Janela de pico").closest("article") as HTMLElement;
      expect(within(card).getByRole("heading", { level: 2 })).toHaveTextContent("18h");
    });

    it("shows 'Sem dados' as the peak window when every hour is zero", async () => {
      serveStats({
        hour: { labels: hours, values: hours.map(() => 0) },
        weekday: { labels: weekdays, values: [0, 0, 0, 0, 0, 0, 0] },
        roadType: { labels: [], values: [] }
      });

      render(<App />);
      await screen.findByText(/^Conectado$/i);

      const card = screen.getByText("Janela de pico").closest("article") as HTMLElement;
      expect(within(card).getByRole("heading", { level: 2 })).toHaveTextContent("Sem dados");
    });

    it("calls the street card 'Ruas com registros', with its new description", async () => {
      render(<App />);
      await screen.findByText(/^Conectado$/i);

      expect(screen.getByText("Ruas com registros")).toBeInTheDocument();
      expect(screen.getByText("Ruas distintas nos registros do contexto atual")).toBeInTheDocument();
      expect(screen.queryByText(/Ruas mapeadas/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Corredores monitorados/i)).not.toBeInTheDocument();
    });

    it("writes 'Horário' with an accent in the insights it receives", async () => {
      server.use(
        http.get("http://localhost:8080/api/traffic-insights", () =>
          HttpResponse.json({ insights: ["Horário de pico: 17h.", "Tipo de via com maior volume: Via expressa."] })
        )
      );

      render(<App />);

      expect(await screen.findByText("Horário de pico: 17h.")).toBeInTheDocument();
      expect(screen.getByText("Tipo de via com maior volume: Via expressa.")).toBeInTheDocument();
    });

    it("names the series 'Veículos', so the tooltip does not show 'value'", async () => {
      chartProbe.seriesNames.length = 0;

      render(<App />);
      await screen.findByText(/^Conectado$/i);

      await waitFor(() => expect(chartProbe.seriesNames.length).toBeGreaterThanOrEqual(3));
      expect(new Set(chartProbe.seriesNames)).toEqual(new Set(["Veículos"]));
    });

    it("shows road types, weather and the traffic level in Portuguese in the table and in the map popup", async () => {
      const user = userEvent.setup();

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));

      const table = screen.getByRole("table");
      expect(await within(table).findByText("Via arterial")).toBeInTheDocument();
      expect(within(table).getByText("Via expressa")).toBeInTheDocument();
      expect(within(table).getByText("Ensolarado")).toBeInTheDocument();
      expect(within(table).getByText("Chuva")).toBeInTheDocument();
      expect(within(table).queryByText("SUNNY")).not.toBeInTheDocument();
      expect(screen.getByText(/Nível: Médio/)).toBeInTheDocument();
      expect(screen.queryByText(/MEDIUM/)).not.toBeInTheDocument();
    });

    it("lists the road types of the form in Portuguese but still sends the codes", async () => {
      const user = userEvent.setup();

      render(<App />);
      await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));

      expect(screen.getByRole("option", { name: "Via local" })).toHaveValue("LOCAL");
      expect(screen.getByRole("option", { name: "Via arterial" })).toHaveValue("ARTERIAL");
      expect(screen.getByRole("option", { name: "Via expressa" })).toHaveValue("HIGHWAY");
    });

    it("translates the zoom buttons of the map", async () => {
      render(<App />);
      await screen.findByText(/^Conectado$/i);

      expect(screen.getByTestId("map-container")).toHaveAttribute("data-zoom-control", "false");
      expect(screen.getByTestId("zoom-control")).toHaveAttribute("data-zoom-in", "Aproximar");
      expect(screen.getByTestId("zoom-control")).toHaveAttribute("data-zoom-out", "Afastar");
    });
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

  describe("map legend placement", () => {
    // jsdom does not measure layout, so this fixes the structure that caused the defect. The frame (.map-shell) has
    // overflow: hidden and the map fills 100% of its height. Anything else inside the frame makes the content taller
    // than the frame: the end of the map (with the OpenStreetMap credit) is cut and, when the map takes focus on a
    // click, the browser scrolls the frame and the legend above the map disappears.
    async function openMapPage(page: "insights" | "records") {
      const user = userEvent.setup();
      render(<App />);
      if (page === "records") {
        await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
      }
      const legend = await screen.findByText("Mostrando o registro mais recente");
      const frame = screen.getByTestId("map-container").closest(".map-shell");
      expect(frame).not.toBeNull();
      return { legend, frame: frame as HTMLElement };
    }

    it.each([
      ["insights", "insights" as const],
      ["records", "records" as const]
    ])("keeps only the map inside the frame that clips it (%s page)", async (_name, page) => {
      const { frame } = await openMapPage(page);

      expect(frame.children).toHaveLength(1);
      expect(frame.firstElementChild).toBe(screen.getByTestId("map-container"));
    });

    it.each([
      ["insights", "insights" as const],
      ["records", "records" as const]
    ])("shows the legend outside the frame that clips the map (%s page)", async (_name, page) => {
      const { legend, frame } = await openMapPage(page);

      expect(legend.closest(".map-shell")).toBeNull();
      expect(frame.contains(legend)).toBe(false);
      expect(legend.closest(".map-card")).toBe(frame.closest(".map-card"));
    });
  });

  describe("map color legend", () => {
    const colorLegendName = "Cores do mapa";

    it.each([
      ["insights", "insights" as const],
      ["records", "records" as const]
    ])("explains the three colors under the map, outside the frame that clips it (%s page)", async (_name, page) => {
      const user = userEvent.setup();
      render(<App />);
      if (page === "records") {
        await user.click(await screen.findByRole("button", { name: /Formulários e registros/i }));
      }

      const legend = await screen.findByRole("list", { name: colorLegendName });
      const items = within(legend).getAllByRole("listitem");
      expect(items).toHaveLength(3);

      const expected = [
        ["Baixo", "até 100 veículos", "#2BA36A"],
        ["Médio", "de 101 a 200 veículos", "#FFB24A"],
        ["Alto", "acima de 200 veículos", "#E04545"]
      ] as const;
      expected.forEach(([label, range, color], index) => {
        const item = items[index] as HTMLElement;
        expect(within(item).getByText(label)).toBeInTheDocument();
        expect(within(item).getByText(range)).toBeInTheDocument();
        expect(item.querySelector(".map-color-legend__swatch")).toHaveStyle({ backgroundColor: color });
      });

      const frame = screen.getByTestId("map-container").closest(".map-shell") as HTMLElement;
      expect(legend.closest(".map-shell")).toBeNull();
      expect(frame.contains(legend)).toBe(false);
      expect(legend.closest(".map-card")).toBe(frame.closest(".map-card"));
    });

    it("does not show the color legend when the map is empty", async () => {
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
      expect(screen.queryByRole("list", { name: colorLegendName })).not.toBeInTheDocument();
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
