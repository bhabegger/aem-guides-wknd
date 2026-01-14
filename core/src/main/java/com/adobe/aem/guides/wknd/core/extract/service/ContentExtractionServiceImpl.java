package com.adobe.aem.guides.wknd.core.extract.service;

import com.adobe.aem.guides.wknd.core.extract.model.ContentSearchEntry;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.sling.api.resource.*;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.time.Instant;
import java.util.*;

import static org.apache.sling.api.resource.ResourceResolverFactory.USER_IMPERSONATION;

@Component(
        service = ContentExtractionService.class,
        reference = {
                @Reference(
                        service = ServiceUserMapped.class,
                        target = "(subServiceName=content-extract)",
                        name = "content-extract"
                )
        }
)
public class ContentExtractionServiceImpl implements ContentExtractionService {

    private static final Logger LOG = LoggerFactory.getLogger(ContentExtractionServiceImpl.class);
    public static final String BASE_PATH = "/var/content-extract";
    private static final String STATE_PATH   = BASE_PATH + "/state";
    private static final String SUBSERVICE = "content-extract";
    private static final String SEARCH_XPATH = "/jcr:root/content//element(*, cq:Page)[@jcr:created > '%s' or @jcr:lastModified > '%s']";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public int runExtraction() {
        try {
            ResourceResolver resourceResolver = getServiceResolver();
            Instant now = Instant.now();
            Instant lastRun = loadLastRun(resourceResolver);
            LOG.info("Running content extraction using state at {} since last successful run: {}", STATE_PATH, lastRun);
            Session session = resourceResolver.adaptTo(Session.class);
            QueryManager queryManager = Objects.requireNonNull(session).getWorkspace().getQueryManager();
            ContentSearchCollector collector = new ContentSearchCollector(session);
            ContentSearchPersistor persistor = new ContentSearchPersistor(resourceResolver);

            var it = findModifiedPagesSince(queryManager, lastRun);
            var count = 0;
            while(it.hasNext()) {
                Node page = it.nextNode();
                LOG.info("Processing page: {}", page.getPath());
                ContentSearchEntry entry = collector.collect(page);
                persistor.persist(entry);

                count++;
            }

            saveLastRun(resourceResolver, now);
            resourceResolver.commit();
            return count;
        } catch (LoginException e) {
            LOG.error("Failed to login as service user", e);
            return 0;
        } catch (PersistenceException | RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private static void saveLastRun(ResourceResolver resourceResolver, Instant now) throws PersistenceException {
        Resource lastRunResource = ResourceUtil.getOrCreateResource(resourceResolver, STATE_PATH, "nt:unstructured", "", false);
        ModifiableValueMap mvm = Objects.requireNonNull(lastRunResource.adaptTo(ModifiableValueMap.class));
        mvm.put("lastRun", calendarOf(now));
    }

    private static Calendar calendarOf(Instant now) {
       var cal = Calendar.getInstance();
       cal.setTimeInMillis(now.toEpochMilli());
       return cal;
    }

    private static Instant loadLastRun(ResourceResolver resourceResolver) {
        var lastPathResource = resourceResolver.getResource(STATE_PATH);
        if(lastPathResource == null) {
            return Instant.ofEpochSecond(0);
        }
        ValueMap map = lastPathResource.getValueMap();
        return Optional
                .ofNullable(map.get("lastRun", Calendar.class))
                .map(Calendar::toInstant)
                .orElse(Instant.ofEpochSecond(0));
    }

    private static NodeIterator findModifiedPagesSince(QueryManager qm, Instant since) throws RepositoryException {
        String jcrSince = jcrDateOf(since);
        String xpath = SEARCH_XPATH.formatted(jcrSince, jcrSince);
        Query query = qm.createQuery(xpath, "xpath");
        QueryResult result = query.execute();
        var nodes = result.getNodes();
        LOG.info("##### Found {} pages modified since {}", nodes.getSize(), since);
        return nodes;
    }

    private static String jcrDateOf(Instant since) throws RepositoryException {
        return ValueFactoryImpl.getInstance()
                .createValue(calendarOf(since)).getString();
    }

    private ResourceResolver getServiceResolver() throws LoginException {
        var rr = resourceResolverFactory.getServiceResourceResolver(
                Collections.singletonMap(
                        ResourceResolverFactory.SUBSERVICE, SUBSERVICE
                )
        );

        LOG.info("Service resolver user: {}", rr.getUserID());
        LOG.info("Service resolver user-impersonation: {}", rr.getAttribute(USER_IMPERSONATION));

        Resource root = rr.getResource("/");
        Resource apps = rr.getResource("/apps");
        Resource libs = rr.getResource("/libs");
        Resource content = rr.getResource("/content");
        Resource var = rr.getResource("/var");

        LOG.info("Visible roots: /={} /apps={} /libs={} /content={} /var={}",
                root != null, apps != null, libs != null, content != null, var != null);
        return rr;
    }

    @Activate
    protected void activate() {
        LOG.info("Content extraction service activated");
    }
}
