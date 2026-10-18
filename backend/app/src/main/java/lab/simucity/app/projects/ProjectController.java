package lab.simucity.app.projects;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lab.simucity.app.identity.AppUserPrincipal;
import lab.simucity.app.projects.ProjectDtos.CreateProjectRequest;
import lab.simucity.app.projects.ProjectDtos.ProjectResponse;
import lab.simucity.app.projects.ProjectDtos.UpdateProjectRequest;
import lab.simucity.app.shared.PageRequests;
import lab.simucity.app.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/projects")
class ProjectController {

    private static final Sort RECENT_FIRST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));

    private final ProjectService service;
    private final PageRequests pageRequests;

    ProjectController(ProjectService service, PageRequests pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @PostMapping
    ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AppUserPrincipal user,
            @Valid @RequestBody CreateProjectRequest body) {
        Project project = service.create(user.id(), body);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.getId()))
                .body(ProjectResponse.from(project));
    }

    @GetMapping
    PageResponse<ProjectResponse> list(@AuthenticationPrincipal AppUserPrincipal user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(user.id(), pageRequests.of(page, size, RECENT_FIRST)),
                ProjectResponse::from);
    }

    @GetMapping("/{id}")
    ProjectResponse get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return ProjectResponse.from(service.getOwned(user.id(), id));
    }

    @PatchMapping("/{id}")
    ProjectResponse update(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest body) {
        return ProjectResponse.from(service.update(user.id(), id, body));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
