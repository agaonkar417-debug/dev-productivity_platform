package com.devflow.kit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One file inside a kit. Both the path and the content may contain {{placeholders}}. */
@Entity
@Table(name = "kit_file")
public class KitFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kit_id")
    private TaskKit kit;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "path_template", nullable = false)
    private String pathTemplate;

    @Column(nullable = false)
    private String content;

    protected KitFile() {
    }

    public KitFile(String pathTemplate, String content) {
        this.pathTemplate = pathTemplate;
        this.content = content;
    }

    public Long getId() { return id; }
    public TaskKit getKit() { return kit; }
    void setKit(TaskKit kit) { this.kit = kit; }
    public int getSortOrder() { return sortOrder; }
    void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getPathTemplate() { return pathTemplate; }
    public String getContent() { return content; }
}
