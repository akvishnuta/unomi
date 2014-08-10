package org.oasis_open.wemi.context.server.plugins.request.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.cdi.api.Properties;
import org.ops4j.pax.cdi.api.Property;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request parameter to a user property
 * @todo add support for multi-valued parameters or storing values as a list
 */
@ApplicationScoped
@OsgiServiceProvider
@Properties({
    @Property(name = "consequenceExecutorId", value = "requestParameterToUserProperty")
})
public class RequestParameterToUserPropertyConsequence implements ConsequenceExecutor {
    public boolean execute(Consequence consequence, User user, Object context) {
        boolean changed = false;
        Event event = (Event) context;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        String requestParameterName = (String) consequence.getParameterValues().get("requestParameterName");
        String userPropertyName = (String) consequence.getParameterValues().get("userPropertyName");
        String requestParameterValue = httpServletRequest.getParameter(requestParameterName);
        if (requestParameterValue != null) {
            if (user.getProperty(userPropertyName) == null || !user.getProperty(userPropertyName).equals(requestParameterValue)) {
                user.setProperty(userPropertyName, requestParameterValue);
                changed = true;
            }
        }
        return changed;
    }
}