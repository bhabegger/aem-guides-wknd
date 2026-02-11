package com.adobe.aem.guides.wknd.core.extract.service;

import com.adobe.aem.guides.wknd.core.extract.model.ContentSearchEntry;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ContentSearchCollector {
    private final Session session;

    public ContentSearchCollector(Session session) {
        this.session = session;
    }

    public ContentSearchEntry collect(Node page) throws RepositoryException {
        Node pageContent = page.getNode("jcr:content");
        var builder = new ContentSearchEntry.Builder();
        builder.path(pageContent.getPath());
        builder.title(extract(pageContent, "jcr:title"));
        builder.description(extract(pageContent, "jcr:description"));

        contentWalk(pageContent, builder);
        return builder.build();
    }

    private void contentWalk(Node candidateNode, ContentSearchEntry.Builder builder) throws RepositoryException {
        if(candidateNode.hasProperty("text")) {
            builder.content(extract(candidateNode, "text"));
        }
        if(candidateNode.hasProperty("fragmentPath") && candidateNode.hasProperty("elementNames")) {
            var fragmentNode = dereference(extract(candidateNode,"fragmentPath"));
            var elementNames = stringValuesOf(candidateNode,"elementNames");
            for(var elementName : elementNames) {
                if(fragmentNode.hasProperty(elementName)) {
                    builder.content(extract(fragmentNode, elementName));
                }
            }
        }
        if(candidateNode.hasNodes()) {
            var childIterator = candidateNode.getNodes();
            while(childIterator.hasNext()) {
                var child = childIterator.nextNode();
                contentWalk(child, builder);
            }
        }
    }

    private List<String> stringValuesOf(Node node, String property) throws RepositoryException {
        if(!node.hasProperty(property)) {
            return List.of();
        }
        if(node.getProperty(property).isMultiple()) {
            return Arrays.stream(node.getProperty(property).getValues())
                    .map(ContentSearchCollector::stringOfValue)
                    .collect(Collectors.toList());
        }
        return List.of(stringOfValue(node.getProperty(property).getValue()));
    }

    private static String stringOfValue(Value value) {
        try {
            return value.getString();
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private Node dereference(String path) throws RepositoryException {
        return session.getNode(path);
    }

    private static String extract(Node node, String property) throws RepositoryException {
        if(node.hasProperty(property)) {
            return node.getProperty(property).getString();
        }
        return null;
    }
}
