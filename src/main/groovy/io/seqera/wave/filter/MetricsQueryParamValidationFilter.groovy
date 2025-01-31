package io.seqera.wave.filter

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
import org.reactivestreams.Publisher

@CompileStatic
@Filter(["/v1alpha2/metrics/**", "/v1alpha3/metrics/**/**"])
@Requires(property = 'wave.metrics.enabled', value = 'true')
class MetricsQueryParamValidationFilter implements HttpServerFilter {

    static final private Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}")

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

        Map<String, List<String>> queryParams = request.getParameters().asMap()

        final String date = queryParams.get("date")?[0]
        final String arch = queryParams.get("arch")?[0]

        if(date && !DATE_PATTERN.matcher(date).matches()) {
            throw new BadRequestException('date format should be yyyy-MM-dd')
        } else if (arch && !ContainerPlatform.ALLOWED_ARCH.contains(arch)) {
            throw new BadRequestException('arch should be one of ' + ContainerPlatform.ALLOWED_ARCH)
        }

        return chain.proceed(request)
    }
}

