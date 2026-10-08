package com.sanedge.common.adapter.category;

import com.sanedge.common.adapter.model.Category;
import io.smallrye.mutiny.Uni;

public interface CategoryQueryPort {
    Uni<Category> findByCategoryId(String categoryId);
}
