import { startTransition, useDeferredValue, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { MapContainer, Polyline, Popup, TileLayer } from "react-leaflet";
import {
  API_BASE_URL,
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
} from "./lib/api";
import type {
  CreateTrafficRecordRequest,
  MapFeatureCollection,
  MapPoint,
  PagedResponse,
  SimulationRequest,
  TrafficInsightResponse,
  TrafficRecord,
  TrafficRecordSummary,
  TrafficStatsResponse
} from "./types/api";

type DashboardState = {
  summary: TrafficRecordSummary;
  byHour: TrafficStatsResponse;
  byWeekday: TrafficStatsResponse;
  byRoadType: TrafficStatsResponse;
  insights: TrafficInsightResponse;
  mapData: MapFeatureCollection;
};

type RecordsRequestState = {
  page: number;
  query: string;
};

type ChartCardProps = {
  eyebrow: string;
  title: string;
  tone: "signal" | "ember" | "ink";
  stats: TrafficStatsResponse;
  chart: "bar" | "area";
};

type ViewMode = "home" | "workspace";

const emptyStats: TrafficStatsResponse = { labels: [], values: [] };
const emptyInsights: TrafficInsightResponse = { insights: [] };
const emptySummary: TrafficRecordSummary = {
  recordCount: 0,
  totalVehicleVolume: 0,
  uniqueStreetCount: 0,
  averageVehicleVolume: 0,
  latestTimestamp: null
};
const emptyRecordsPage: PagedResponse<TrafficRecord> = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0
};

const recordTemplate: CreateTrafficRecordRequest = {
  timestamp: new Date().toISOString(),
  roadType: "ARTERIAL",
  vehicleVolume: 140,
  eventType: "",
  weather: "",
  streetOsmWayId: 0
};

const simulationTemplate: SimulationRequest = {
  recordsToGenerate: 18,
  scenarioName: "Varredura do Corredor Noturno"
};

const roadTypeOptions = ["LOCAL", "ARTERIAL", "HIGHWAY"];
const chartPalette = ["#f25c3a", "#123c63", "#ffb24a", "#5f7c6f", "#1b1b1b", "#c9772b"];

function formatLabel(value: string) {
  return value.replace(/_/g, " ").toLowerCase().replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
}

function formatCompactNumber(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    notation: "compact",
    maximumFractionDigits: 1
  }).format(value);
}

function formatDateTime(iso: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(iso));
}

function formatDatetimeLocalValue(iso: string) {
  const date = new Date(iso);
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
}

const timestampRequiredMessage = "Informe a data e a hora do registro.";

/** The date typed in the field, or null when it is empty or not a valid date. */
function parseTimestampInput(value: string): Date | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function downloadBlob(blob: Blob, filename: string) {
  const href = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = href;
  anchor.download = filename;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(href), 120);
}

function buildChartData(stats: TrafficStatsResponse) {
  return stats.labels.map((label, index) => ({
    label: formatLabel(label),
    value: stats.values[index] ?? 0
  }));
}

function getPeakLabel(stats: TrafficStatsResponse) {
  if (stats.labels.length === 0) {
    return "Sem dados";
  }

  let peakIndex = 0;
  stats.values.forEach((value, index) => {
    if (value > (stats.values[peakIndex] ?? 0)) {
      peakIndex = index;
    }
  });

  return formatLabel(stats.labels[peakIndex] ?? "Desconhecido");
}

function getLatestTimestamp(timestamp: string | null) {
  if (!timestamp) {
    return "Sem registros recebidos";
  }
  return formatDateTime(timestamp);
}

function areSameSelection(left: string[], right: string[]) {
  if (left.length !== right.length) {
    return false;
  }
  const leftSet = new Set(left);
  return right.every((id) => leftSet.has(id));
}

function ChartCard({ eyebrow, title, tone, stats, chart }: ChartCardProps) {
  const data = buildChartData(stats);
  const areaTone = tone === "signal" ? "#f25c3a" : tone === "ember" ? "#ffb24a" : "#123c63";

  return (
    <section className={`card chart-card chart-card--${tone}`}>
      <div className="section-heading">
        <p>{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      <div className="chart-shell" aria-label={title}>
        <ResponsiveContainer width="100%" height={240}>
          {chart === "bar" ? (
            <BarChart data={data} margin={{ top: 12, right: 16, left: 8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(18, 60, 99, 0.12)" vertical={false} />
              <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={10} />
              <YAxis tickLine={false} axisLine={false} tickMargin={10} width={44} />
              <Tooltip
                cursor={{ fill: "rgba(242, 92, 58, 0.08)" }}
                contentStyle={{ borderRadius: 18, border: "1px solid rgba(18, 60, 99, 0.12)" }}
              />
              <Bar dataKey="value" radius={[12, 12, 0, 0]}>
                {data.map((entry, index) => (
                  <Cell key={`${entry.label}-${index}`} fill={chartPalette[index % chartPalette.length]} />
                ))}
              </Bar>
            </BarChart>
          ) : (
            <AreaChart data={data} margin={{ top: 12, right: 16, left: 8, bottom: 0 }}>
              <defs>
                <linearGradient id={`gradient-${tone}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={areaTone} stopOpacity={0.6} />
                  <stop offset="95%" stopColor={areaTone} stopOpacity={0.05} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(18, 60, 99, 0.12)" vertical={false} />
              <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={10} />
              <YAxis tickLine={false} axisLine={false} tickMargin={10} width={44} />
              <Tooltip
                cursor={{ stroke: "rgba(18, 60, 99, 0.15)", strokeWidth: 1 }}
                contentStyle={{ borderRadius: 18, border: "1px solid rgba(18, 60, 99, 0.12)" }}
              />
              <Area
                type="monotone"
                dataKey="value"
                stroke={areaTone}
                strokeWidth={3}
                fill={`url(#gradient-${tone})`}
              />
            </AreaChart>
          )}
        </ResponsiveContainer>
      </div>
    </section>
  );
}

type TrafficMapCardProps = {
  eyebrow: string;
  title: string;
  mapData: MapFeatureCollection;
  emptyText: string;
};

function toLeafletPositions(point: MapPoint) {
  return point.geometry.coordinates.map(([lng, lat]) => [lat, lng] as [number, number]);
}

function getMapCenter(mapData: MapFeatureCollection): [number, number] {
  const first = mapData.features[0];
  if (!first || first.geometry.coordinates.length === 0) {
    return [-23.5505, -46.6333];
  }

  const [lng, lat] = first.geometry.coordinates[0];
  return [lat, lng];
}

function TrafficMapCard({ eyebrow, title, mapData, emptyText }: TrafficMapCardProps) {
  return (
    <section className="card map-card reveal reveal-4">
      <div className="section-heading">
        <p>{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      {mapData.features.length === 0 ? (
        <p className="map-empty">{emptyText}</p>
      ) : (
        <div className="map-shell">
          <MapContainer center={getMapCenter(mapData)} zoom={12} scrollWheelZoom={false} zoomControl={true}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {mapData.features.map((point) => (
              <Polyline
                key={`${point.properties.recordId}-${point.properties.streetId}`}
                positions={toLeafletPositions(point)}
                pathOptions={{ color: point.properties.color, weight: 6, opacity: 0.8 }}
              >
                <Popup>
                  <strong>{point.properties.streetName}</strong>
                  <br />
                  Volume: {point.properties.vehicleVolume}
                  <br />
                  Nível: {point.properties.trafficLevel}
                </Popup>
              </Polyline>
            ))}
          </MapContainer>
        </div>
      )}
    </section>
  );
}

export default function App() {
  const [dashboard, setDashboard] = useState<DashboardState>({
    summary: emptySummary,
    byHour: emptyStats,
    byWeekday: emptyStats,
    byRoadType: emptyStats,
    insights: emptyInsights,
    mapData: { type: "FeatureCollection", features: [] }
  });
  const [recordsPage, setRecordsPage] = useState<PagedResponse<TrafficRecord>>(emptyRecordsPage);
  const [recordsRequest, setRecordsRequest] = useState<RecordsRequestState>({ page: 0, query: "" });
  const [appliedRecordIds, setAppliedRecordIds] = useState<string[]>([]);
  const [stagedRecordIds, setStagedRecordIds] = useState<string[]>([]);
  const [streetQuery, setStreetQuery] = useState("");
  const [streetOptions, setStreetOptions] = useState<{ id: string; osmWayId: number; name: string }[]>([]);
  const [streetSearchLoading, setStreetSearchLoading] = useState(false);
  const [recordForm, setRecordForm] = useState<CreateTrafficRecordRequest>(recordTemplate);
  // The date field keeps the text the person typed (empty while cleared); it only becomes an ISO date on save.
  const [timestampInput, setTimestampInput] = useState(() => formatDatetimeLocalValue(new Date().toISOString()));
  const [timestampError, setTimestampError] = useState<string | null>(null);
  const [simulationForm, setSimulationForm] = useState<SimulationRequest>(simulationTemplate);
  const [tableQuery, setTableQuery] = useState("");
  const [statusMessage, setStatusMessage] = useState("Conectando à central de tráfego.");
  const [loading, setLoading] = useState(true);
  // "Conectado" is only true while the last dashboard load succeeded.
  const [lastLoadSucceeded, setLastLoadSucceeded] = useState(false);
  const [recordsLoading, setRecordsLoading] = useState(true);
  const [bulkSelectionLoading, setBulkSelectionLoading] = useState(false);
  const [busyAction, setBusyAction] = useState<"record" | "simulation" | "export" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activeView, setActiveView] = useState<ViewMode>("home");
  const deferredQuery = useDeferredValue(tableQuery);
  const deferredStreetQuery = useDeferredValue(streetQuery);

  // Each load takes a number. A response only updates the screen if its number is still the latest one,
  // so a slow, old response never overwrites a newer one.
  const dashboardRequestId = useRef(0);
  const recordsRequestId = useRef(0);

  async function refreshDashboard(recordIds: string[] = appliedRecordIds) {
    const requestId = ++dashboardRequestId.current;
    const isLatest = () => requestId === dashboardRequestId.current;
    setLoading(true);
    setError(null);

    try {
      const [summary, byHour, byWeekday, byRoadType, insights, mapData] = await Promise.all([
        getTrafficRecordSummary(recordIds),
        getTrafficStats("hour", recordIds),
        getTrafficStats("weekday", recordIds),
        getTrafficStats("roadType", recordIds),
        getTrafficInsights(recordIds),
        getTrafficMap(recordIds)
      ]);

      if (!isLatest()) {
        return;
      }

      startTransition(() => {
        setDashboard({ summary, byHour, byWeekday, byRoadType, insights, mapData });
      });

      setLastLoadSucceeded(true);
      setStatusMessage(`Conexão ativa estabelecida com ${API_BASE_URL}.`);
    } catch (caughtError) {
      if (!isLatest()) {
        return;
      }

      const message =
        caughtError instanceof ApiError ? caughtError.message : "A central não conseguiu alcançar o backend.";
      setLastLoadSucceeded(false);
      setError(message);
      setStatusMessage("O backend precisa de atenção.");
    } finally {
      if (isLatest()) {
        setLoading(false);
      }
    }
  }

  async function refreshRecordsPage(page: number, query: string) {
    const requestId = ++recordsRequestId.current;
    const isLatest = () => requestId === recordsRequestId.current;
    setRecordsLoading(true);
    setError(null);

    try {
      const nextPage = await getTrafficRecords({ page, size: emptyRecordsPage.size, query });
      if (!isLatest()) {
        return;
      }

      startTransition(() => {
        setRecordsPage(nextPage);
      });
    } catch (caughtError) {
      if (!isLatest()) {
        return;
      }

      const message =
        caughtError instanceof ApiError ? caughtError.message : "Não foi possível carregar os registros paginados.";
      setError(message);
    } finally {
      if (isLatest()) {
        setRecordsLoading(false);
      }
    }
  }

  useEffect(() => {
    void refreshDashboard([]);
  }, []);

  useEffect(() => {
    void refreshRecordsPage(recordsRequest.page, recordsRequest.query);
  }, [recordsRequest]);

  useEffect(() => {
    const nextQuery = deferredQuery.trim();
    setRecordsRequest((current) =>
      current.page === 0 && current.query === nextQuery ? current : { ...current, page: 0, query: nextQuery }
    );
  }, [deferredQuery]);

  useEffect(() => {
    const query = deferredStreetQuery.trim();
    if (query.length < 2) {
      setStreetOptions([]);
      setStreetSearchLoading(false);
      return;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(() => {
      setStreetSearchLoading(true);
      void (async () => {
        try {
          const response = await searchStreets(query, 20, 0);
          if (!cancelled) {
            setStreetOptions(response.items);
          }
        } catch {
          if (!cancelled) {
            setStreetOptions([]);
          }
        } finally {
          if (!cancelled) {
            setStreetSearchLoading(false);
          }
        }
      })();
    }, 300);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [deferredStreetQuery]);

  async function handleRecordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const timestamp = parseTimestampInput(timestampInput);
    if (!timestamp) {
      setTimestampError(timestampRequiredMessage);
      return;
    }

    setBusyAction("record");
    setError(null);

    try {
      await createTrafficRecord({
        ...recordForm,
        timestamp: timestamp.toISOString()
      });
      setStatusMessage("Novo registro de tráfego adicionado ao painel.");
      await Promise.all([refreshDashboard(), refreshRecordsPage(0, recordsRequest.query)]);
      setRecordsRequest((current) => ({ ...current, page: 0 }));
    } catch (caughtError) {
      const message =
        caughtError instanceof ApiError ? caughtError.message : "Não foi possível criar o registro de tráfego.";
      setError(message);
    } finally {
      setBusyAction(null);
    }
  }

  async function handleSimulationSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusyAction("simulation");
    setError(null);

    try {
      const generated = await generateSimulation(simulationForm);
      setStatusMessage(`A simulação gerou ${generated.length} novos registros.`);
      await Promise.all([refreshDashboard(), refreshRecordsPage(0, recordsRequest.query)]);
      setRecordsRequest((current) => ({ ...current, page: 0 }));
    } catch (caughtError) {
      const message =
        caughtError instanceof ApiError ? caughtError.message : "Não foi possível gerar os registros da simulação.";
      setError(message);
    } finally {
      setBusyAction(null);
    }
  }

  async function handleExport(format: "csv" | "json") {
    setBusyAction("export");
    setError(null);

    try {
      const blob = await getExport(format);
      downloadBlob(blob, format === "csv" ? "traffic-data.csv" : "traffic-data.json");
      setStatusMessage(`Exportação preparada no formato ${format.toUpperCase()}.`);
    } catch (caughtError) {
      const message =
        caughtError instanceof ApiError ? caughtError.message : "Não foi possível exportar os dados de tráfego.";
      setError(message);
    } finally {
      setBusyAction(null);
    }
  }

  async function applySelection() {
    setAppliedRecordIds(stagedRecordIds);
    await refreshDashboard(stagedRecordIds);
  }

  function isRecordSelected(recordId: string) {
    return stagedRecordIds.includes(recordId);
  }

  function toggleRecordSelection(recordId: string) {
    setStagedRecordIds((current) =>
      current.includes(recordId) ? current.filter((id) => id !== recordId) : [...current, recordId]
    );
  }

  async function getAllFilteredRecordIds(query: string) {
    const firstPage = await getTrafficRecords({ page: 0, size: emptyRecordsPage.size, query });
    const recordIds = firstPage.items.map((record) => record.id);

    if (firstPage.totalPages <= 1) {
      return recordIds;
    }

    const remainingPages = await Promise.all(
      Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
        getTrafficRecords({ page: index + 1, size: firstPage.size, query })
      )
    );

    remainingPages.forEach((page) => {
      recordIds.push(...page.items.map((record) => record.id));
    });

    return recordIds;
  }

  async function toggleAllFilteredSelection() {
    if (recordsPage.totalItems === 0) {
      return;
    }

    setBulkSelectionLoading(true);
    setError(null);

    try {
      const filteredRecordIds = await getAllFilteredRecordIds(recordsRequest.query);
      if (filteredRecordIds.length === 0) {
        return;
      }

      setStagedRecordIds((current) => {
        const filteredRecordIdSet = new Set(filteredRecordIds);
        const allSelected = filteredRecordIds.every((id) => current.includes(id));
        return allSelected
          ? current.filter((id) => !filteredRecordIdSet.has(id))
          : Array.from(new Set([...current, ...filteredRecordIds]));
      });
    } catch (caughtError) {
      const message =
        caughtError instanceof ApiError ? caughtError.message : "Não foi possível atualizar a seleção filtrada.";
      setError(message);
    } finally {
      setBulkSelectionLoading(false);
    }
  }

  function clearSelection() {
    setStagedRecordIds([]);
  }

  function selectStreetOption(osmWayId: number, name: string) {
    setRecordForm((current) => ({ ...current, streetOsmWayId: osmWayId }));
    setStreetQuery(name);
    setStreetOptions([]);
  }
  const analyticsContextLabel =
    appliedRecordIds.length === 0
      ? "Visualizando base completa."
      : `Visualizando ${appliedRecordIds.length} registros selecionados.`;
  const hasPendingSelectionChanges = !areSameSelection(stagedRecordIds, appliedRecordIds);
  const currentPage = recordsPage.totalPages === 0 ? 1 : recordsPage.page + 1;
  const totalPages = recordsPage.totalPages === 0 ? 1 : recordsPage.totalPages;
  const hasRecords = recordsPage.totalItems > 0;
  const hasPreviousPage = recordsPage.page > 0;
  const hasNextPage = recordsPage.page + 1 < recordsPage.totalPages;

  const summaryCards = [
    {
      label: "Veículos capturados",
      value: formatCompactNumber(dashboard.summary.totalVehicleVolume),
      meta: "Volume acumulado no contexto atual"
    },
    {
      label: "Janela de pico",
      value: getPeakLabel(dashboard.byHour),
      meta: "Maior concentração nos dados atuais"
    },
    {
      label: "Ruas mapeadas",
      value: String(dashboard.summary.uniqueStreetCount),
      meta: "Corredores monitorados no contexto atual"
    },
    {
      label: "Carga média",
      value: formatCompactNumber(dashboard.summary.averageVehicleVolume),
      meta: "Veículos por registro no contexto atual"
    }
  ];

  const insightList =
    dashboard.insights.insights.length > 0
      ? dashboard.insights.insights
      : [
          "Aguardando registros suficientes para destacar insights narrativos.",
          "Alimente a base manualmente ou execute uma simulação para gerar sinal."
        ];

  const isHomeView = activeView === "home";
  const heroTitle = isHomeView
    ? "Inteligência de tráfego com ritmo editorial e estrutura de central operacional."
    : "Registre ocorrências, execute cenários e acompanhe o fluxo operacional em uma área dedicada.";
  const heroText = isHomeView
    ? "O novo frontend organiza os dados viários ao vivo como uma central visual: cabeçalhos editoriais, cards táteis, formulários prontos para operação e um mapa conectado ao contrato real do backend."
    : "A área de operação concentra formulários e registros em uma página própria, mantendo o mesmo acabamento visual e deixando a navegação entre leitura e ação mais direta.";

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Pular para o conteúdo
      </a>
      <div className="ambient ambient--left" />
      <div className="ambient ambient--right" />

      <header className="hero">
        <div className={`hero-copy reveal reveal-1 ${isHomeView ? "" : "hero-copy--workspace"}`.trim()}>
          <p className="kicker">SmartTrafficFlow / Central de Sinais Urbanos</p>
          <h1>{heroTitle}</h1>
          <p className="hero-text">{heroText}</p>
          <div className="hero-actions">
            <button type="button" className="primary-button" onClick={() => void handleExport("csv")}>
              Exportar CSV
            </button>
            <button type="button" className="ghost-button" onClick={() => void handleExport("json")}>
              Exportar JSON
            </button>
          </div>
        </div>

        <aside className="hero-panel reveal reveal-2">
          <div className="view-switcher" aria-label="Navegação principal">
            <button
              type="button"
              className={`view-switcher__button ${isHomeView ? "view-switcher__button--active" : ""}`}
              aria-pressed={isHomeView}
              onClick={() => setActiveView("home")}
            >
              Página de insights
            </button>
            <button
              type="button"
              className={`view-switcher__button ${!isHomeView ? "view-switcher__button--active" : ""}`}
              aria-pressed={!isHomeView}
              onClick={() => setActiveView("workspace")}
            >
              Formulários e registros
            </button>
          </div>
          <div className="status-strip">
            <span
              className={`status-dot ${loading ? "status-dot--loading" : ""} ${
                !loading && !lastLoadSucceeded ? "status-dot--offline" : ""
              }`.trim()}
            />
            <strong>Pulso ao vivo</strong>
            <span>{loading ? "Sincronizando" : lastLoadSucceeded ? "Conectado" : "Sem conexão"}</span>
          </div>
          <div className="hero-metrics">
            <div>
              <span className="metric-label">Última entrada</span>
              <strong>{getLatestTimestamp(dashboard.summary.latestTimestamp)}</strong>
            </div>
            <div>
              <span className="metric-label">Endpoint</span>
              <strong>{API_BASE_URL}</strong>
            </div>
          </div>
          <p className="hero-note">
            Feito para leitura rápida, não para uma coleção genérica de widgets. O layout prioriza hierarquia, ritmo
            e clareza operacional.
          </p>
        </aside>
      </header>

      <main id="main-content" className="main-grid">
        {isHomeView ? (
          <>
            <section className="summary-grid reveal reveal-2">
              {summaryCards.map((card) => (
                <article key={card.label} className="card summary-card">
                  <p>{card.label}</p>
                  <h2>{card.value}</h2>
                  <span>{card.meta}</span>
                </article>
              ))}
            </section>

            <section className="card bulletin-card reveal reveal-3">
              <div className="section-heading">
                <p>Boletim operacional</p>
                <h2>Notas da central</h2>
              </div>
              <div className="bulletin-copy">
                <p>
                  Registros, pontos do mapa, estatísticas e insights são carregados diretamente do backend em Spring
                  Boot. Se a API falhar, a interface expõe o problema em vez de mascará-lo com placeholders.
                </p>
                <div className="badge-row">
                  <span className="pill">React + Vite</span>
                  <span className="pill">API Spring integrada</span>
                  <span className="pill">Mapeamento com Leaflet</span>
                </div>
              </div>
              <div className="status-box" aria-live="polite">
                <strong>Situação</strong>
                <p>{error ?? `${statusMessage} ${analyticsContextLabel}`}</p>
              </div>
            </section>

            <div className="charts-grid reveal reveal-3">
              <ChartCard eyebrow="Cadência de volume" title="Curva horária de pressão" tone="signal" chart="area" stats={dashboard.byHour} />
              <ChartCard eyebrow="Padrão semanal" title="Distribuição por dia útil" tone="ink" chart="bar" stats={dashboard.byWeekday} />
              <ChartCard eyebrow="Mistura viária" title="Divisão por tipo de via" tone="ember" chart="bar" stats={dashboard.byRoadType} />
            </div>

            <TrafficMapCard
              eyebrow="Visão da rede"
              title="Monitor do mapa de tráfego"
              mapData={dashboard.mapData}
              emptyText="Selecione registros em “Formulários e registros” para visualizar as ruas coloridas."
            />

            <section className="card insight-card reveal reveal-4">
              <div className="section-heading section-heading--centered">
                <p>Camada narrativa</p>
                <h2>Insights gerados</h2>
              </div>
              <ol className="insight-list">
                {insightList.map((insight) => (
                  <li key={insight}>{insight}</li>
                ))}
              </ol>
            </section>
          </>
        ) : (
          <>
            <section className="card workspace-intro reveal reveal-3">
              <div className="section-heading">
                <p>Área operacional</p>
                <h2>Formulários e registros</h2>
              </div>
              <div className="workspace-intro__body">
                <p>
                  Esta página concentra a captura manual, a geração de cenários e o painel tabular de registros para
                  uma rotina mais direta de operação.
                </p>
                <div className="status-box" aria-live="polite">
                  <strong>Situação</strong>
                  <p>{error ?? statusMessage}</p>
                </div>
              </div>
            </section>

            <section className="form-grid reveal reveal-4">
              <section className="card form-card">
                <div className="section-heading">
                  <p>Entrada de campo</p>
                  <h2>Adicionar registro de tráfego</h2>
                </div>
                <form className="stack-form" onSubmit={handleRecordSubmit}>
                  <label>
                    Data e hora
                    <input
                      type="datetime-local"
                      value={timestampInput}
                      aria-invalid={timestampError ? true : undefined}
                      onChange={(event) => {
                        setTimestampInput(event.target.value);
                        setTimestampError(parseTimestampInput(event.target.value) ? null : timestampRequiredMessage);
                      }}
                    />
                    {timestampError ? (
                      <p className="form-error" role="alert">
                        {timestampError}
                      </p>
                    ) : null}
                  </label>
                  <label>
                    Tipo de via
                    <select
                      value={recordForm.roadType}
                      onChange={(event) =>
                        setRecordForm((current) => ({ ...current, roadType: event.target.value }))
                      }
                    >
                      {roadTypeOptions.map((option) => (
                        <option key={option} value={option}>
                          {option}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Volume de veículos
                    <input
                      type="number"
                      min="1"
                      value={recordForm.vehicleVolume}
                      onChange={(event) =>
                        setRecordForm((current) => ({
                          ...current,
                          vehicleVolume: Number(event.target.value)
                        }))
                      }
                    />
                  </label>
                  <label>
                    Rua
                    <input
                      type="search"
                      value={streetQuery}
                      onChange={(event) => {
                        setStreetQuery(event.target.value);
                        setRecordForm((current) => ({ ...current, streetOsmWayId: 0 }));
                      }}
                      placeholder="Digite pelo menos 2 letras para buscar"
                    />
                    {streetSearchLoading ? <p className="form-hint">Buscando ruas...</p> : null}
                    {streetOptions.length > 0 ? (
                      <div className="street-search-results">
                        {streetOptions.map((street) => (
                          <button
                            key={street.id}
                            type="button"
                            className="street-search-results__item"
                            onClick={() => selectStreetOption(street.osmWayId, street.name)}
                          >
                            {street.name}
                          </button>
                        ))}
                      </div>
                    ) : null}
                    <p className="form-hint">
                      {recordForm.streetOsmWayId
                        ? `Rua selecionada (OSM): ${recordForm.streetOsmWayId}`
                        : "Nenhuma rua selecionada ainda."}
                    </p>
                  </label>
                  <label>
                    Tipo de evento
                    <input
                      type="text"
                      value={recordForm.eventType}
                      onChange={(event) =>
                        setRecordForm((current) => ({ ...current, eventType: event.target.value }))
                      }
                      placeholder="Obra, show, incidente"
                    />
                  </label>
                  <label>
                    Clima
                    <input
                      type="text"
                      value={recordForm.weather}
                      onChange={(event) =>
                        setRecordForm((current) => ({ ...current, weather: event.target.value }))
                      }
                      placeholder="Céu limpo, chuva, neblina"
                    />
                  </label>
                  <button
                    type="submit"
                    className="primary-button"
                    disabled={busyAction === "record" || !recordForm.streetOsmWayId}
                  >
                    {busyAction === "record" ? "Salvando registro..." : "Salvar registro"}
                  </button>
                </form>
              </section>

              <section className="card form-card form-card--accent">
                <div className="section-heading">
                  <p>Motor de cenários</p>
                  <h2>Gerar tráfego simulado</h2>
                </div>
                <form className="stack-form" onSubmit={handleSimulationSubmit}>
                  <label>
                    Nome do cenário
                    <input
                      type="text"
                      value={simulationForm.scenarioName}
                      onChange={(event) =>
                        setSimulationForm((current) => ({ ...current, scenarioName: event.target.value }))
                      }
                    />
                  </label>
                  <label>
                    Registros a gerar
                    <input
                      type="number"
                      min="1"
                      max="250"
                      value={simulationForm.recordsToGenerate}
                      onChange={(event) =>
                        setSimulationForm((current) => ({
                          ...current,
                          recordsToGenerate: Number(event.target.value)
                        }))
                      }
                    />
                  </label>
                  <div className="simulation-note">
                    <strong>Uso sugerido</strong>
                    <p>Popular demos rapidamente, validar estados dos gráficos ou testar exportações e insights.</p>
                  </div>
                  <button
                    type="submit"
                    className="ghost-button ghost-button--dark"
                    disabled={busyAction === "simulation"}
                  >
                    {busyAction === "simulation" ? "Gerando..." : "Executar simulação"}
                  </button>
                </form>
              </section>
            </section>

            <TrafficMapCard
              eyebrow="Mapa operacional"
              title="Ruas selecionadas"
              mapData={dashboard.mapData}
              emptyText="Selecione registros abaixo para plotar as ruas nesta visão e também na página de insights."
            />

            <section className="card table-card reveal reveal-5">
              <div className="table-header">
                <div className="section-heading">
                  <p>Painel de tráfego</p>
                  <h2>Registros recebidos</h2>
                </div>
                <label className="search-field">
                  <span>Filtro</span>
                  <input
                    type="search"
                    value={tableQuery}
                    onChange={(event) => setTableQuery(event.target.value)}
                    placeholder="Buscar por rua, tipo de via, clima"
                  />
                </label>
              </div>

              <div className="table-pagination-summary" aria-live="polite">
                <span>{formatCompactNumber(dashboard.summary.recordCount)} registros no contexto atual</span>
                <span>{formatCompactNumber(recordsPage.totalItems)} encontrados para esta busca</span>
                <span>
                  Página {currentPage} de {totalPages}
                </span>
              </div>

              <div className="table-actions">
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => void toggleAllFilteredSelection()}
                  disabled={!hasRecords || recordsLoading || bulkSelectionLoading}
                >
                  {bulkSelectionLoading ? "Atualizando filtrados..." : "Selecionar/limpar filtrados"}
                </button>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={clearSelection}
                  disabled={stagedRecordIds.length === 0}
                >
                  Limpar seleção
                </button>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => void applySelection()}
                  disabled={!hasPendingSelectionChanges}
                >
                  Aplicar seleção
                </button>
                <span className="table-selection-count">
                  {stagedRecordIds.length} selecionado(s), {appliedRecordIds.length} aplicado(s)
                </span>
              </div>
              {hasPendingSelectionChanges ? (
                <p className="table-selection-note">Há mudanças pendentes. Clique em “Aplicar seleção”.</p>
              ) : null}

              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Selecionar</th>
                      <th>Data e hora</th>
                      <th>Via</th>
                      <th>Volume</th>
                      <th>Rua</th>
                      <th>Clima</th>
                      <th>Evento</th>
                    </tr>
                  </thead>
                  <tbody>
                    {!hasRecords && !recordsLoading ? (
                      <tr>
                        <td colSpan={7} className="empty-row">
                          Nenhum registro corresponde ao filtro atual.
                        </td>
                      </tr>
                    ) : recordsLoading ? (
                      <tr>
                        <td colSpan={7} className="empty-row">
                          Carregando página de registros...
                        </td>
                      </tr>
                    ) : (
                      recordsPage.items.map((record) => (
                        <tr key={record.id}>
                          <td>
                            <input
                              type="checkbox"
                              checked={isRecordSelected(record.id)}
                              onChange={() => toggleRecordSelection(record.id)}
                              aria-label={`Selecionar registro ${record.id}`}
                            />
                          </td>
                          <td>{formatDateTime(record.timestamp)}</td>
                          <td>{formatLabel(record.roadType)}</td>
                          <td>{record.vehicleVolume}</td>
                          <td>{record.streetName ?? "Não atribuída"}</td>
                          <td>{record.weather ?? "n/d"}</td>
                          <td>{record.eventType ?? "n/d"}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <div className="table-pagination-actions">
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => setRecordsRequest((current) => ({ ...current, page: current.page - 1 }))}
                  disabled={!hasPreviousPage || recordsLoading}
                >
                  Página anterior
                </button>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => setRecordsRequest((current) => ({ ...current, page: current.page + 1 }))}
                  disabled={!hasNextPage || recordsLoading}
                >
                  Próxima página
                </button>
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
