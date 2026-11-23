package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Quốc gia (ISO 3166-1 alpha-2).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "country")
public class Country {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", length = 2, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", length = 3, nullable = false)
    private String defaultCurrency;
}
