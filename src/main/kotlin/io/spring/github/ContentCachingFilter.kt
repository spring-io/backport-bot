package io.spring.github

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.InputStream
import javax.servlet.FilterChain
import javax.servlet.ReadListener
import javax.servlet.ServletInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse

/**
 * Caches the HttpServletRequest.getInputStream() (body) so that it can be read multiple
 * times. This allows Spring Security to read the body once to
 * <a href="https://developer.github.com/webhooks/securing/">perform authentication</a>
 * and Spring MVC to read it again to parse the body for the Controller Objects
 * @author Rob Winch
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ContentCachingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        filterChain.doFilter(CachedRequest(request), response)
    }

    class CachedRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
        private var bytes : ByteArray? = null

        private var delegate: ServletInputStream? = null

        override fun getInputStream(): ServletInputStream {
            if (delegate == null) {
                delegate = super.getInputStream()
                bytes = delegate!!.readBytes()
            }
            return CachedServletInputStream(delegate!!, bytes!!.inputStream())
        }
    }

    class CachedServletInputStream(val sis: ServletInputStream, val cached: InputStream) : ServletInputStream() {
        override fun isReady(): Boolean {
            return sis.isReady
        }

        override fun isFinished(): Boolean {
            return sis.isFinished
        }

        override fun read(): Int {
            return cached.read()
        }

        override fun setReadListener(listener: ReadListener?) {
            sis.setReadListener(listener)
        }
    }
}