package lab.simucity.app.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Construye paginaciones acotadas: el cliente no puede pedir páginas enormes. */
@Component
public class PageRequests {

    private final int maxPageSize;

    public PageRequests(@Value("${simucity.api.max-page-size}") int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public PageRequest of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BadRequestException("El número de página no puede ser negativo.");
        }
        int boundedSize = Math.clamp(size, 1, maxPageSize);
        return PageRequest.of(page, boundedSize, sort);
    }
}
