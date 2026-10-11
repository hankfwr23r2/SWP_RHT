package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "CATEGORY")
public class Category {
    @Id
    @Column(name = "CategoryID", nullable = false)
    private Integer id;

    @Size(max = 255)
    @Nationalized
    @Column(name = "CategoryName")
    private String categoryName;

    @Nationalized
    @Lob
    @Column(name = "description")
    private String description;


}