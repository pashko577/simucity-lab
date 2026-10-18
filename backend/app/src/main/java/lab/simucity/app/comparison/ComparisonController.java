package lab.simucity.app.comparison;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lab.simucity.app.comparison.ComparisonDtos.ComparisonRequest;
import lab.simucity.app.comparison.ComparisonDtos.ComparisonResponse;
import lab.simucity.app.identity.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/comparisons")
class ComparisonController {

    private final ComparisonService service;

    ComparisonController(ComparisonService service) {
        this.service = service;
    }

    /** POST (sección 13) aunque no guarda nada: el cuerpo identifica las dos ejecuciones a comparar. */
    @PostMapping
    ComparisonResponse compare(@AuthenticationPrincipal AppUserPrincipal user,
            @Valid @RequestBody ComparisonRequest body) {
        return service.compare(user.id(), body.baseRunId(), body.candidateRunId());
    }
}
