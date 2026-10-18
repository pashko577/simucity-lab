package lab.simucity.app.runs;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lab.simucity.app.identity.AppUserPrincipal;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.app.runs.RunDtos.ProjectRunItem;
import lab.simucity.app.runs.RunDtos.RunListItem;
import lab.simucity.app.runs.RunDtos.RunResponse;
import lab.simucity.app.scenarios.ScenarioDtos.RunParametersRequest;
import lab.simucity.app.shared.PageRequests;
import lab.simucity.app.shared.PageResponse;
import lab.simucity.engine.metrics.MetricScope;

@RestController
@RequestMapping("/api/v1")
class RunController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RunService service;
    private final PageRequests pageRequests;

    RunController(RunService service, PageRequests pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    /** 202: la ejecución queda en cola. El cliente consulta GET /runs/{id} hasta que termine. */
    @PostMapping("/scenarios/{scenarioId}/runs")
    ResponseEntity<RunResponse> create(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID scenarioId,
            @Valid @RequestBody RunParametersRequest body) {
        SimulationRunRecord run = service.create(user.id(), scenarioId, body);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/runs/" + run.getId()))
                .body(service.toResponse(run));
    }

    @GetMapping("/scenarios/{scenarioId}/runs")
    PageResponse<RunListItem> list(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(user.id(), scenarioId, pageRequests.of(page, size, NEWEST_FIRST)),
                RunListItem::from);
    }

    /** Para el comparador: por defecto solo las terminadas con éxito, que son las comparables. */
    @GetMapping("/projects/{projectId}/runs")
    PageResponse<ProjectRunItem> listInProject(@AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID projectId, @RequestParam(defaultValue = "COMPLETED") List<RunStatus> status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(service.listInProject(user.id(), projectId, status,
                pageRequests.of(page, size, NEWEST_FIRST)), r -> r);
    }

    @GetMapping("/runs/{id}")
    RunResponse get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return service.toResponse(service.getOwned(user.id(), id));
    }

    @PostMapping("/runs/{id}/cancel")
    RunResponse cancel(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return service.toResponse(service.cancel(user.id(), id));
    }

    @GetMapping("/runs/{id}/metrics")
    List<MetricDto> metrics(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
            @RequestParam(required = false) MetricScope scope) {
        return service.metrics(user.id(), id, scope);
    }

    /** Muestras compactas para la animación: {"edgeIds": [...], "samples": [{"t":..., "vehicles":[...]}]}. */
    @GetMapping(value = "/runs/{id}/samples", produces = MediaType.APPLICATION_JSON_VALUE)
    String samples(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return service.samplesJson(user.id(), id);
    }

    @GetMapping("/runs/{id}/export.csv")
    ResponseEntity<String> exportCsv(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        SimulationRunRecord run = service.getOwned(user.id(), id);
        String csv = RunCsvExporter.export(run, service.metrics(user.id(), id, null));
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("simucity-run-" + id + ".csv").build().toString())
                .body(csv);
    }
}
