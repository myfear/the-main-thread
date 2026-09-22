package com.themainthread.noir.model;

import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
@Indexed
public class Book extends PanacheEntity {

    @FullTextField(analyzer = "english")
    @KeywordField(name = "title_sort", sortable = Sortable.YES, normalizer = "sort")
    public String title;

    @ManyToOne
    @JsonIgnore
    public Author author;
}
