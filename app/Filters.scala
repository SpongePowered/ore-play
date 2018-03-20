import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import security.NonceFilter

class Filters @Inject()(csrfFilters: CSRFFilter, securityHeadersFilter: SecurityHeadersFilter, nonceFilter: NonceFilter)
  extends DefaultHttpFilters(csrfFilters, nonceFilter, securityHeadersFilter)
