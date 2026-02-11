package com.adobe.aem.guides.wknd.core.extract.service;

import com.adobe.aem.guides.wknd.core.extract.model.ContentSearchEntry;
import org.apache.sling.api.resource.*;

import java.util.Objects;

import static com.adobe.aem.guides.wknd.core.extract.service.ContentExtractionServiceImpl.BASE_PATH;

public class ContentSearchPersistor {
    private final ResourceResolver resourceResolver;
    private final static String SEARCH_PATH =  BASE_PATH + "/search";

    public ContentSearchPersistor(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public void persist(ContentSearchEntry entry) throws PersistenceException {
        Resource entryResources = ResourceUtil.getOrCreateResource(resourceResolver, SEARCH_PATH + entry.path(), "nt:unstructured", "", false);
        ModifiableValueMap mvm = Objects.requireNonNull(entryResources.adaptTo(ModifiableValueMap.class));
        mvm.put("path", entry.path());

        inject(mvm, "title", entry.title());
        inject(mvm, "description", entry.description());
        inject(mvm, "content", entry.content());
    }

    private static <T> void inject(ModifiableValueMap mvm, String property, T value) {
        if(value != null) {
            mvm.put(property, value);
        }
    }
}
