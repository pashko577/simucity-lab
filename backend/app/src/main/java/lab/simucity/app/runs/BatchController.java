package lab.simucity.app.runs;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Sort;
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
import jakarta.validation.constraints.NotNull;
import lab.simucity.app.identity.AppUserPrincipal;
import lab.simucity.app.runs.RunDtos.BatchListItem;
import lab.simucity.app.runs.RunDtos.BatchResponse;
import lab.simucity.app.scenarios.ScenarioDtos.RunParametersRequest;
import lab.simucity.app.shared.PageRequests;
import lab.simucity.app.shared.PageResponse;

@RestController
@RequestMapping("/api/v1")
class BatchController {

    /**
     * @param size  número de semillas (2..10); las semillas son {@code seed .. seed + size - 1}
     */
    record BatchRequest(
            @NotNull Integer size,
            @NotNull Double durationSeconds,
            Long seed,
            Double dtSeconds,
            Double sampleIntervalSeconds,
            Double saturationFlowPerLaneVph,
            Double stallThresholdSeconds) {

        RunParametersRequest parameters() {
            return new RunParametersRequest(seed, durationSeconds, dtSeconds, sampleIntervalSeconds,
                    saturationFlowPerLaneVph, stallThresholdSeconds);
        }
    }

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final RunService runs;
    private final BatchService batches;
    private final PageRequests pageRequests;

    BatchController(RunService runs, BatchService batches, PageRequests pageRequests) {
        this.runs = runs;
        this.batches = batches;
        this.pageRequests = pageRequests;
    }

    @PostMapping("/scenarios/{scenarioId}/batches")
    ResponseEntity<BatchResponse> create(@AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID scenarioId, @Valid @RequestBody BatchRequest body) {
        RunBatch batch = runs.createBatch(user.id(), scenarioId, body.size(), body.parameters());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/batches/" + batch.getId()))
                .body(batches.toResponse(batch));
    }

    @GetMapping("/batches/{id}")
    BatchResponse get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return batches.toResponse(batches.getOwned(user.id(), id));
    }

    @PostMapping("/batches/{id}/cancel")
    BatchResponse cancel(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return batches.cancel(user.id(), id);
    }

    @GetMapping("/projects/{projectId}/batches")
    PageResponse<BatchListItem> listInProject(@AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID projectId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(batches.listInProject(user.id(), projectId, pageRequests.of(page, size, NEWEST_FIRST)),
                b -> b);
    }
}
