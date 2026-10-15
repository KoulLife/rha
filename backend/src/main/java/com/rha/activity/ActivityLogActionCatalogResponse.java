package com.rha.activity;

import java.util.List;

public record ActivityLogActionCatalogResponse(List<Category> categories) {

    public record Category(String code, String name, List<Action> actions) {
    }

    public record Action(String code, String name) {
    }
}
