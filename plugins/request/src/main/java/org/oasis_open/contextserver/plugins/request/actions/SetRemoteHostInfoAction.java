package org.oasis_open.contextserver.plugins.request.actions;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;

import javax.annotation.PostConstruct;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by toto on 18/08/14.
 */
public class SetRemoteHostInfoAction implements ActionExecutor {

    public static final Pattern IPV4 = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");

    private DatabaseReader databaseReader;
    private String pathToGeoLocationDatabase;

    public void setPathToGeoLocationDatabase(String pathToGeoLocationDatabase) {
        this.pathToGeoLocationDatabase = pathToGeoLocationDatabase;
    }

    @Override
    public boolean execute(Action action, Event event) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get(Event.HTTP_REQUEST_ATTRIBUTE);
        if (httpServletRequest == null) {
            return false;
        }
        Session session = event.getSession();
        if (session == null) {
            return false;
        }

        session.setProperty("remoteAddr", httpServletRequest.getRemoteAddr());
        session.setProperty("remoteHost", httpServletRequest.getRemoteHost());

        try {
            if (!httpServletRequest.getRemoteAddr().equals("127.0.0.1") && IPV4.matcher(httpServletRequest.getRemoteAddr()).matches()) {
                ipLookup(httpServletRequest.getRemoteAddr(), session);
            } else if (httpServletRequest.getParameter("remoteAddr") != null && httpServletRequest.getParameter("remoteAddr").length() > 0) {
                ipLookup(httpServletRequest.getParameter("remoteAddr"), session);
            } else {
                session.setProperty("countryCode", "CH");
                session.setProperty("countryName", "Switzerland");
                session.setProperty("city", "Geneva");
                Map<String, Double> location = new HashMap<String, Double>();
                location.put("lat", 46.1884341);
                location.put("lon", 6.1282508);
                session.setProperty("location", location);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();
        ReadableUserAgent agent = parser.parse(httpServletRequest.getHeader("User-Agent"));
        session.setProperty("operatingSystemFamily", agent.getOperatingSystem().getFamilyName());
        session.setProperty("operatingSystemName", agent.getOperatingSystem().getName());
        session.setProperty("userAgentName", agent.getName());
        session.setProperty("userAgentVersion", agent.getVersionNumber().toVersionString());
        session.setProperty("deviceCategory", agent.getDeviceCategory().getName());

        return true;
    }

    private boolean ipLookup(String remoteAddr, Session session) {
        boolean result = false;
        if (databaseReader != null) {
            result = ipLookupInDatabase(remoteAddr, session);
        }
        if (!result) {
            result = ipLookupInFreeWebService(remoteAddr, session);
        }
        return result;
    }

    private boolean ipLookupInFreeWebService(String remoteAddr, Session session) {
        final URL url;
        InputStream inputStream = null;
        try {
            url = new URL("http://www.telize.com/geoip/" + remoteAddr);
            inputStream = url.openConnection().getInputStream();
            JsonReader reader = Json.createReader(inputStream);
            JsonObject location = (JsonObject) reader.read();
            session.setProperty("countryCode", location.getString("country_code"));
            session.setProperty("countryName", location.getString("country"));
            session.setProperty("city", location.getString("city"));

            Map<String, Double> locationMap = new HashMap<String, Double>();
            locationMap.put("lat", location.getJsonNumber("latitude").doubleValue());
            locationMap.put("lon", location.getJsonNumber("longitude").doubleValue());
            session.setProperty("location", locationMap);
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    @PostConstruct
    public void postConstruct() {
        // A File object pointing to your GeoIP2 or GeoLite2 database
        if (pathToGeoLocationDatabase == null) {
            return;
        }
        File database = new File(pathToGeoLocationDatabase);
        if (!database.exists()) {
            return;
        }

        // This creates the DatabaseReader object, which should be reused across
        // lookups.
        try {
            this.databaseReader = new DatabaseReader.Builder(database).build();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean ipLookupInDatabase(String remoteAddr, Session session) {
        if (databaseReader == null) {
            return false;
        }

        // Replace "city" with the appropriate method for your database, e.g.,
        // "country".
        CityResponse cityResponse = null;
        try {
            cityResponse = databaseReader.city(InetAddress.getByName(remoteAddr));
            session.setProperty("countryCode", cityResponse.getCountry().getIsoCode());
            session.setProperty("countryName", cityResponse.getCountry().getName());
            session.setProperty("city", cityResponse.getCity().getName());

            Map<String, Double> locationMap = new HashMap<String, Double>();
            locationMap.put("lat", cityResponse.getLocation().getLatitude());
            locationMap.put("lon", cityResponse.getLocation().getLongitude());
            session.setProperty("location", locationMap);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeoIp2Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}