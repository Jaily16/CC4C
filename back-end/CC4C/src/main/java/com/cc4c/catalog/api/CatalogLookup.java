package com.cc4c.catalog.api;

public interface CatalogLookup {
    boolean languageExists(int languageId);

    boolean courseExists(int courseId);
}
