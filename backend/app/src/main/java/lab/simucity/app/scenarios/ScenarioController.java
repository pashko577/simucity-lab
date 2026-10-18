package lab.simucity.app.scenarios;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lab.simucity.app.identity.AppUserPrincipal;
import lab.simucity.app.scenarios.ScenarioDtos.DuplicateRequest;
import lab.simucity.app.scenarios.ScenarioDtos.RunParametersRequest;
import lab.simucity.app.scenarios.ScenarioDtos.ScenarioRequest;
import lab.simucity.app.scenarios.ScenarioDtos.ScenarioResponse;
import lab.simucity.app.scenarios.ScenarioDtos.ScenarioSummary;
import lab.simucity.app.scenarios.ScenarioDtos.ValidationResponse;
import lab.simucity.app.shared.PageRequests;
import lab.simucity.app.shared.PageResponse;

@RestController
@RequestMapping("/api/v1")
class ScenarioController {

    private static final Sort RECENT_FIRST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));

    private final ScenarioService service;
    private final PageRequests pageRequests;

    ScenarioController(ScenarioService service, PageRequests pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @PostMapping("/projects/{projectId}/scenarios")
    ResponseEntity<ScenarioResponse> create(@AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID projectId, @Valid @RequestBody ScenarioRequest body) {
        SavedScenario scenario = service.create(user.id(), projectId, body);
        return ResponseEntity.created(URI.create("/api/v1/scenarios/" + scenario.getId()))
                .body(service.toResponse(scenario));
    }

    /** Listado sin la definición completa (puede ser grande). */
    @GetMapping("/projects/{projectId}/scenarios")
    PageResponse<ScenarioSummary> list(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(user.id(), projectId, pageRequests.of(page, size, RECENT_FIRST)),
                ScenarioSummary::from);
    }

    @GetMapping("/scenarios/{id}")
    ScenarioResponse get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return service.toResponse(service.getOwned(user.id(), id));
    }

    @PutMapping("/scenarios/{id}")
    ScenarioResponse replace(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
            @Valid @RequestBody ScenarioRequest body) {
        return service.toResponse(service.replace(user.id(), id, body));
    }

    @PostMapping("/scenarios/{id}/duplicate")
    ResponseEntity<ScenarioResponse> duplicate(@AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID id, @Valid @RequestBody(required = false) DuplicateRequest body) {
        SavedScenario copy = service.duplicate(user.id(), id, body != null ? body.name() : null);
        return ResponseEntity.created(URI.create("/api/v1/scenarios/" + copy.getId()))
                .body(service.toResponse(copy));
    }

    @DeleteMapping("/scenarios/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Valida con el motor sin ejecutar. Siempre 200: el resultado indica si es válido y por qué. */
    @PostMapping("/scenarios/{id}/validate")
    ValidationResponse validate(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
            @Valid @RequestBody(required = false) RunParametersRequest params) {
        return ValidationResponse.from(service.validate(user.id(), id, params));
    }
}
