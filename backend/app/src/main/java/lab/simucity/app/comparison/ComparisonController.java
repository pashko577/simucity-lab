package lab.simucity.app.comparison;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lab.simucity.app.comparison.ComparisonDtos.BatchComparisonRequest;
import lab.simucity.app.comparison.ComparisonDtos.BatchComparisonResponse;
import lab.simucity.app.comparison.ComparisonDtos.ComparisonRequest;
import lab.simucity.app.comparison.ComparisonDtos.ComparisonResponse;
import lab.simucity.app.identity.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/comparisons")
class ComparisonController {

    private final ComparisonService service;
    private final BatchComparisonService batchService;

    ComparisonController(ComparisonService service, BatchComparisonService batchService) {
        this.service = service;
        this.batchService = batchService;
    }

    /** Compara dos lotes (varias semillas) con intervalos de confianza. */
    @PostMapping("/batches")
    BatchComparisonResponse compareBatches(@AuthenticationPrincipal AppUserPrincipal user,
            @Valid @RequestBody BatchComparisonRequest body) {
        return batchService.compare(user.id(), body.baseBatchId(), body.candidateBatchId());
    }

    /** POST (sección 13) aunque no guarda nada: el cuerpo identifica las dos ejecuciones a comparar. */
    @PostMapping
    ComparisonResponse compare(@AuthenticationPrincipal AppUserPrincipal user,
            @Valid @RequestBody ComparisonRequest body) {
        return service.compare(user.id(), body.baseRunId(), body.candidateRunId());
    }
}
