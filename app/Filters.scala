import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import security.NonceFilter
import util.HtmlCompressionFilter

class Filters @Inject()(
                         csrfFilters: CSRFFilter,
                         securityHeadersFilter: SecurityHeadersFilter,
                         nonceFilter: NonceFilter,
                         compressionFilter: HtmlCompressionFilter
                       ) extends DefaultHttpFilters(csrfFilters, nonceFilter, securityHeadersFilter, compressionFilter)
