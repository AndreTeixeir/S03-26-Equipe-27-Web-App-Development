import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "../mocks/server";
import type { PagedResponse, TrafficRecord, TrafficRecordSummary } from "../../types/api";

vi.mock("react-leaflet", () => ({
  MapContainer: ({ children }: { children: ReactNode }) => <div data-testid="map-container">{children}</div>,
  TileLayer: () => <div data-testid="tile-layer" />,
  Polyline: ({ children }: { children: ReactNode }) => <div data-testid="map-polyline">{children}</div>,
  Popup: ({ children }: { children: ReactNode }) => <div>{children}</div>
}));

vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  AreaChart: () => <div data-testid="area-chart" />,
  BarChart: () => <div data-testid="bar-chart" />,
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: () => null,
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
  });

  it("renders the dashboard and connection state", async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/SmartTrafficFlow/i)).toBeInTheDocument();
      expect(screen.getByText(/Conectado/i)).toBeInTheDocument();
      expect(screen.getByText(/Veículos capturados/i)).toBeInTheDocument();
    });
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

    expect(await screen.findByText(/Rodovia Norte/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText(/Avenida Central/i)).not.toBeInTheDocument());
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
    await user.click(screen.getByRole("button", { name: /Selecionar\/limpar filtrados/i }));

    await waitFor(() => expect(screen.getByText(/22 selecionado\(s\), 0 aplicado\(s\)/i)).toBeInTheDocument());

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
