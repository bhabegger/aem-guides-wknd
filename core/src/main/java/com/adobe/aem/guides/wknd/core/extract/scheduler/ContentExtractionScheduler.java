package com.adobe.aem.guides.wknd.core.extract.scheduler;

import com.adobe.aem.guides.wknd.core.extract.service.ContentExtractionService;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component(service = Runnable.class, immediate = true)
public class ContentExtractionScheduler implements Runnable  {
    private static final Logger LOG = LoggerFactory.getLogger(ContentExtractionScheduler.class);
    private static final String JOB_NAME = "content-extraction-job";

    private static final String JOB_CRON = "0/30 * * * * ?";

    @Reference
    private Scheduler scheduler;

    @Reference
    private ContentExtractionService extractionService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Activate
    protected void activate() {
        try {
            scheduler.schedule(this, scheduler
                    .EXPR(JOB_CRON).name(JOB_NAME).onLeaderOnly(true)
            );
            LOG.info("Scheduled content extraction with cron [{}]", JOB_CRON);
        } catch (Exception e) {
            LOG.error("Failed to schedule content extraction job", e);
        }
    }

    @Deactivate
    protected void deactivate() {
        scheduler.unschedule(JOB_NAME);
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) {
            LOG.warn("Previous extraction still running; skipping this execution");
            return;
        }
        try {
            int count = extractionService.runExtraction();
            LOG.info("Content extraction completed, processed {} items", count);
        } catch (Exception e) {
            LOG.error("Error during content extraction run", e);
        } finally {
            running.set(false);
        }
    }
}
